package app.blot.core.edit

import app.blot.core.geometry.Polyline
import app.blot.core.model.Element
import app.blot.core.model.Image
import app.blot.core.model.Line
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.Stroke
import app.blot.core.model.TextBox
import kotlin.math.max
import kotlin.math.min

/**
 * Which elements a lasso or a tap selects, tested against what each element draws rather than
 * its bounds. Bounds are only the first, cheap filter.
 *
 * The lasso rule is ported from xnotes' `SelectionMath` (MIT, Copyright (c) 2026 Shardul Vikram
 * Singh, https://github.com/shardulvs/xnotes-android): an element is in when some of what it
 * draws lies inside the loop, or comes within its ink's reach of the loop's line. What an element
 * draws is a stroke's sample path, a shape's outline or a line's centre line (flattened from
 * their paths), or a text box's or image's frame. The bounds pre-filter follows Excalidraw's
 * `lasso/utils.ts` (MIT, Copyright (c) 2020 Excalidraw, https://github.com/excalidraw/excalidraw).
 */
object Selection {
    /**
     * What an element draws: polylines (`x0, y0, x1, y1, …`), each closed or open as [closed]
     * says, and how far its ink spreads either side.
     */
    class Drawn(val lines: List<FloatArray>, val closed: List<Boolean>, val reach: Float) {
        constructor(lines: List<FloatArray>, closed: Boolean, reach: Float) : this(lines, lines.map { closed }, reach)
    }

    fun drawnOf(e: Element): Drawn = when (e) {
        is Stroke -> Drawn(listOf(FloatArray(e.points.size * 2) { if (it % 2 == 0) e.points[it / 2].x else e.points[it / 2].y }), false, e.width / 2f)
        is Shape -> shapeOutline(e).let { pl -> Drawn(pl.map { it.xy }, pl.map { it.closed }, e.width / 2f) }
        is Line -> Drawn(listOf(e.centreline.flatten().first().xy), false, e.width / 2f)
        is TextBox -> Drawn(listOf(e.frame.rotatedCorners(e.rotation)), true, 0f)
        is Image -> Drawn(listOf(e.frame.rotatedCorners(e.rotation)), true, 0f)
    }

    /** A shape's outline flattened and rotated: its closed silhouette and any open inner edges. */
    fun shapeOutline(s: Shape): List<Polyline> = s.outline.flatten(s.rotation, s.frame.centerX, s.frame.centerY)

    private fun corners(r: Rect) = floatArrayOf(r.left, r.top, r.right, r.top, r.right, r.bottom, r.left, r.bottom)

    /**
     * Ids of [elements] the closed loop [ring] (`x0, y0, …`, at least three points) selects, in
     * stacking order.
     */
    fun lasso(elements: List<Element>, ring: FloatArray): List<String> {
        val n = ring.size / 2
        if (n < 3) return emptyList()
        var l = Float.POSITIVE_INFINITY
        var t = Float.POSITIVE_INFINITY
        var r = Float.NEGATIVE_INFINITY
        var b = Float.NEGATIVE_INFINITY
        for (i in 0 until n) {
            l = min(l, ring[i * 2]); r = max(r, ring[i * 2])
            t = min(t, ring[i * 2 + 1]); b = max(b, ring[i * 2 + 1])
        }
        val box = Rect.fromEdges(l, t, r, b)
        val out = ArrayList<String>()
        for (e in elements) {
            if (!e.bounds.intersects(box)) continue
            if (meets(ring, n, drawnOf(e), e.bounds)) out += e.id
        }
        return out
    }

    /** Ids of [elements] the rectangle [rect] selects, by the lasso's rule. */
    fun rectangle(elements: List<Element>, rect: Rect): List<String> = lasso(elements, corners(rect))

    private fun meets(ring: FloatArray, n: Int, drawn: Drawn, area: Rect): Boolean {
        for (line in drawn.lines) {
            if (line.isNotEmpty() && PlaneMath.insideEvenOdd(ring, n, line[0], line[1])) return true
        }
        for ((k, line) in drawn.lines.withIndex()) {
            val m = line.size / 2
            for (i in 0 until n) {
                val j = (i + 1) % n
                // Only loop edges near the element can touch it.
                if (min(ring[i * 2], ring[j * 2]) > area.right || max(ring[i * 2], ring[j * 2]) < area.left ||
                    min(ring[i * 2 + 1], ring[j * 2 + 1]) > area.bottom || max(ring[i * 2 + 1], ring[j * 2 + 1]) < area.top
                ) continue
                if (PlaneMath.polylineNear(line, m, drawn.closed[k], ring[i * 2], ring[i * 2 + 1], ring[j * 2], ring[j * 2 + 1], drawn.reach)) return true
            }
        }
        return false
    }

    /**
     * True when a tap at ([x], [y]) with tolerance [tol] lands on [e]: within [tol] of a stroke's
     * filled [outline] (perfect-freehand polygon, `x0, y0, …`), of a shape's or line's ink, inside
     * a filled shape, or inside a text box's or image's frame.
     */
    fun hits(e: Element, x: Float, y: Float, tol: Float, outline: (Stroke) -> FloatArray): Boolean {
        if (!e.bounds.inflate(tol).intersects(Rect(x, y, 0f, 0f))) return false
        return when (e) {
            is Stroke -> {
                val xy = outline(e)
                PlaneMath.capsuleTouchesPolygon(xy, xy.size / 2, x, y, x, y, tol)
            }
            is Shape -> {
                val outline = shapeOutline(e)
                (e.fill != null && outline.any { it.closed && PlaneMath.insideEvenOdd(it.xy, it.count, x, y) }) ||
                    outline.any { PlaneMath.polylineNear(it.xy, it.count, it.closed, x, y, x, y, e.width / 2f + tol) }
            }
            is Line -> {
                val xy = drawnOf(e).lines[0]
                PlaneMath.polylineNear(xy, xy.size / 2, false, x, y, x, y, e.width / 2f + tol)
            }
            is TextBox -> e.frame.containsRotated(e.rotation, x, y, tol)
            is Image -> e.frame.containsRotated(e.rotation, x, y, tol)
        }
    }

    /** The topmost element of [elements] that a tap at ([x], [y]) lands on; see [hits]. */
    fun pick(elements: List<Element>, x: Float, y: Float, tol: Float, outline: (Stroke) -> FloatArray): Element? =
        elements.lastOrNull { hits(it, x, y, tol, outline) }

    /** The union of the bounds of the elements of [elements] whose ids are in [ids], or null if none are. */
    fun bounds(elements: List<Element>, ids: Set<String>): Rect? {
        var out: Rect? = null
        for (e in elements) if (e.id in ids) out = out?.union(e.bounds) ?: e.bounds
        return out
    }
}
