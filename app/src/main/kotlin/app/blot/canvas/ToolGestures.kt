package app.blot.canvas

import app.blot.core.edit.Angles
import app.blot.core.geometry.CurveFit
import app.blot.core.geometry.LineRoutes
import app.blot.core.edit.Selection
import app.blot.core.edit.SelectionEdits
import app.blot.core.edit.Similarity
import app.blot.core.edit.Snap
import app.blot.core.model.Arrowhead
import app.blot.core.model.Element
import app.blot.core.model.Line
import app.blot.core.model.LineRoute
import app.blot.core.model.Point
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.TextBox
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The selection box: [rect] turned by [rotation] radians about its centre, in canvas units. It is
 * a single shape's, text box's or image's own frame, so it turns with it; otherwise the
 * selection's bounds, unturned.
 */
internal data class SelectionFrame(val rect: Rect, val rotation: Float) {
    /** This frame after [t]: its centre moves, it scales, and it turns with the selection. */
    fun transformed(t: Similarity): SelectionFrame =
        if (t.isIdentity) this else SelectionFrame(t.frame(rect), Angles.add(rotation, t.rotation))

    /** Corners grown by [pad], `x0, y0, …` from the top-left clockwise (before turning). */
    fun corners(pad: Float): FloatArray = rect.rotatedCorners(rotation, pad)

    /** Where the rotate handle sits: [gap] beyond the middle of the top edge grown by [pad]. */
    fun rotateHandle(pad: Float, gap: Float): Point {
        val d = rect.height / 2f + pad + gap
        return Point(rect.centerX + d * sin(rotation), rect.centerY - d * cos(rotation))
    }
}

/** A short label the canvas shows next to a handle while it is dragged; [x], [y] is the handle (canvas units). */
internal data class Readout(val text: String, val x: Float, val y: Float, val dirX: Float, val dirY: Float)

/** The texts of [Readout]. */
internal object Readouts {
    /**
     * A turn as whole degrees with a sign, wrapped into (−180°, 180°]: "+15°", "−7°" (a minus
     * sign, U+2212), "0°".
     */
    fun angle(radians: Float): String {
        var d = Math.round(Math.toDegrees(radians.toDouble())).toInt() % 360
        if (d > 180) d -= 360
        if (d <= -180) d += 360
        return when {
            d > 0 -> "+$d°"
            d < 0 -> "−${-d}°"
            else -> "0°"
        }
    }

    /** A scale factor with at most two decimals and no trailing zeros: "1x", "1.1x", "0.95x". */
    fun scale(factor: Float): String =
        java.math.BigDecimal(factor.toDouble()).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "x"
}

/**
 * The select, shape, line and text tools' pen gestures, in canvas units. While a gesture runs
 * the note is unchanged and the canvas view draws [lasso], [marquee], [draft] and the selection
 * through [transform]; the gesture's end applies it as one undoable edit.
 */
internal class ToolGestures(private val c: CanvasController) {
    private sealed class Gesture(val x0: Float, val y0: Float) {
        var maxDistance = 0f
    }
    private class Lasso(x0: Float, y0: Float) : Gesture(x0, y0)
    private class Marquee(x0: Float, y0: Float) : Gesture(x0, y0)
    private class Move(x0: Float, y0: Float, val ids: Set<String>) : Gesture(x0, y0)
    private class Scale(
        x0: Float, y0: Float,
        val ids: Set<String>,
        val anchorX: Float, val anchorY: Float,
        val cornerX: Float, val cornerY: Float,
        val minFactor: Float,
    ) : Gesture(x0, y0)
    private class Rotate(
        x0: Float, y0: Float,
        val ids: Set<String>,
        val cx: Float, val cy: Float,
        /** The frame's own rotation, which snapping lines up to 15° steps. */
        val base: Float,
        var lastAngle: Float,
    ) : Gesture(x0, y0) {
        /** The pen's turn about the centre since pen-down, unwrapped, radians. */
        var turned = 0f
    }
    private class DrawShape(x0: Float, y0: Float, val id: String) : Gesture(x0, y0)
    private class DrawLine(x0: Float, y0: Float, val id: String) : Gesture(x0, y0) {
        /** The drag's samples, `x0, y0, …`, [count] points; a curve runs along them. */
        var samples = floatArrayOf(x0, y0)
        var count = 1
    }
    private class PlaceText(x0: Float, y0: Float) : Gesture(x0, y0)

    /** What a pen-down on the selection lands on. */
    enum class Hit { NONE, ROTATE, CORNER, INSIDE }

    private var gesture: Gesture? = null
    private var lastX = 0f
    private var lastY = 0f

    /** The lasso drawn so far, `x0, y0, …`; [lassoCount] points are valid. */
    var lasso = FloatArray(128)
        private set
    var lassoCount = 0
        private set

    /** The selection rectangle being dragged, or null. */
    var marquee: Rect? = null
        private set

    /** The shape or line being drawn, not yet in the note. */
    var draft: Element? = null
        private set

    /** Applied to the selection while it is moved, scaled or rotated. */
    var transform: Similarity = Similarity.IDENTITY
        private set

    /** The turn or scale being dragged, shown beside its handle; null otherwise. */
    var readout: Readout? = null
        private set

    val active: Boolean get() = gesture != null

    /** Canvas units for [v] dp on screen at the current zoom. */
    private fun dp(v: Float) = v / c.viewport.zoom

    /** What a pen-down at ([x], [y]) (canvas units) on the current selection would grab. */
    fun hitSelection(x: Float, y: Float): Hit {
        val frame = c.selectionFrame() ?: return Hit.NONE
        val pad = dp(SELECTION_PAD_DP)
        val reach = dp(HANDLE_REACH_DP)
        val h = frame.rotateHandle(pad, dp(ROTATE_HANDLE_GAP_DP))
        if (hypot(x - h.x, y - h.y) <= reach) return Hit.ROTATE
        if (nearestCorner(frame, x, y) >= 0) return Hit.CORNER
        return if (frame.rect.containsRotated(frame.rotation, x, y, pad + dp(PICK_DP))) Hit.INSIDE else Hit.NONE
    }

    private fun nearestCorner(frame: SelectionFrame, x: Float, y: Float): Int {
        val corners = frame.corners(dp(SELECTION_PAD_DP))
        var best = -1
        var bestD = dp(HANDLE_REACH_DP)
        for (k in 0 until 4) {
            val d = hypot(x - corners[k * 2], y - corners[k * 2 + 1])
            if (d <= bestD) {
                best = k
                bestD = d
            }
        }
        return best
    }

    fun start(x: Float, y: Float) {
        cancel()
        lastX = x
        lastY = y
        gesture = when (c.tool) {
            Tool.SELECT -> selectStart(x, y)
            Tool.SHAPE -> DrawShape(x, y, Element.newId())
            Tool.LINE -> DrawLine(x, y, Element.newId())
            Tool.TEXT -> PlaceText(x, y)
            else -> null
        }
        if (gesture is Lasso) {
            lassoCount = 0
            addLassoPoint(x, y)
        }
        c.overlayChanged()
    }

    private fun selectStart(x: Float, y: Float): Gesture {
        val ids = c.selection
        val frame = c.selectionFrame()
        val fresh = if (c.selectMode == SelectMode.LASSO) Lasso(x, y) else Marquee(x, y)
        frame ?: return fresh
        val pad = dp(SELECTION_PAD_DP)
        when (hitSelection(x, y)) {
            Hit.ROTATE -> {
                val r = frame.rect
                return Rotate(x, y, ids, r.centerX, r.centerY, frame.rotation, atan2(y - r.centerY, x - r.centerX))
            }
            Hit.CORNER -> {
                val corners = frame.corners(pad)
                val k = nearestCorner(frame, x, y)
                val a = (k + 2) % 4
                val size = max(frame.rect.width, frame.rect.height)
                return Scale(
                    x, y, ids,
                    corners[a * 2], corners[a * 2 + 1], corners[k * 2], corners[k * 2 + 1],
                    minFactor = if (size <= 0f) 1f else min(1f, MIN_SCALED_SIZE / size),
                )
            }
            Hit.INSIDE -> return Move(x, y, ids)
            Hit.NONE -> return fresh
        }
    }

    fun move(x: Float, y: Float) {
        val g = gesture ?: return
        lastX = x
        lastY = y
        g.maxDistance = max(g.maxDistance, hypot(x - g.x0, y - g.y0))
        when (g) {
            is Lasso -> addLassoPoint(x, y)
            is Marquee -> marquee = Rect.fromEdges(g.x0, g.y0, x, y)
            is Move -> transform = Similarity.translate(x - g.x0, y - g.y0)
            is Scale -> {
                val f = scaleOf(g, x, y)
                transform = Similarity.scale(f, g.anchorX, g.anchorY)
                val cx = transform.mapX(g.cornerX, g.cornerY)
                val cy = transform.mapY(g.cornerX, g.cornerY)
                readout = Readout(Readouts.scale(f), cx, cy, g.cornerX - g.anchorX, g.cornerY - g.anchorY)
            }
            is Rotate -> {
                val a = atan2(y - g.cy, x - g.cx)
                g.turned += Angles.normalize(a - g.lastAngle)
                g.lastAngle = a
                val turn = turnOf(g)
                transform = if (turn == 0f) Similarity.IDENTITY else Similarity.rotate(turn, g.cx, g.cy)
                val frame = c.selectionFrame()?.transformed(transform)
                val h = frame?.rotateHandle(dp(SELECTION_PAD_DP), dp(ROTATE_HANDLE_GAP_DP)) ?: Point(x, y)
                readout = Readout(Readouts.angle(turn), h.x, h.y, h.x - g.cx, h.y - g.cy)
            }
            is DrawShape -> draft = if (isTap(g)) null else Shape(
                g.id, c.shapeKind, Snap.dragRect(g.x0, g.y0, x, y, c.constrainShapes), c.color, c.width, c.shapeFill,
            )
            is DrawLine -> {
                addSample(g, x, y)
                draft = if (isTap(g)) null else line(g, x, y)
            }
            is PlaceText -> Unit
        }
        c.overlayChanged()
    }

    // The grabbed corner follows the pen, keeping the offset at which it was grabbed.
    private fun scaleOf(g: Scale, x: Float, y: Float) = SelectionEdits.cornerScale(
        g.anchorX, g.anchorY, g.cornerX, g.cornerY,
        x - (g.x0 - g.cornerX), y - (g.y0 - g.cornerY), g.minFactor,
    )

    private fun turnOf(g: Rotate): Float = if (c.snapAngles) Snap.rotation(g.base, g.turned) else g.turned

    private fun addSample(g: DrawLine, x: Float, y: Float) {
        val n = g.count
        if (hypot(x - g.samples[n * 2 - 2], y - g.samples[n * 2 - 1]) < dp(LINE_SAMPLE_STEP_DP)) return
        if (g.samples.size < (n + 1) * 2) g.samples = g.samples.copyOf(g.samples.size * 2)
        g.samples[n * 2] = x
        g.samples[n * 2 + 1] = y
        g.count = n + 1
    }

    private fun line(g: DrawLine, x: Float, y: Float): Line {
        val route = c.lineRoute
        val points = when (route) {
            LineRoute.STRAIGHT -> listOf(Point(g.x0, g.y0), if (c.snapAngles) Snap.angle(g.x0, g.y0, x, y) else Point(x, y))
            LineRoute.ELBOW -> {
                // Snapping straightens an elbow dragged within half a step of level or plumb.
                val snapped = if (c.snapAngles) Snap.angle(g.x0, g.y0, x, y) else null
                val end = if (snapped != null && (snapped.x == g.x0 || snapped.y == g.y0)) snapped else Point(x, y)
                LineRoutes.elbow(g.x0, g.y0, end.x, end.y)
            }
            LineRoute.CURVE -> {
                val n = g.count
                val last = n * 2 - 2
                val xy = if (g.samples[last] == x && g.samples[last + 1] == y) g.samples.copyOf(n * 2)
                else g.samples.copyOf(n * 2 + 2).also { it[n * 2] = x; it[n * 2 + 1] = y }
                CurveFit.fit(xy, dp(CURVE_TOLERANCE_DP))
            }
        }
        val arrows = c.lineArrows
        return Line(
            g.id,
            points,
            c.color,
            c.width,
            startArrow = if (arrows == LineArrows.BOTH) c.arrowhead else Arrowhead.NONE,
            endArrow = if (arrows == LineArrows.NONE) Arrowhead.NONE else c.arrowhead,
            route = route,
        )
    }

    private fun isTap(g: Gesture) = g.maxDistance < dp(TAP_SLOP_DP)

    fun end() {
        val g = gesture ?: return
        val x = lastX
        val y = lastY
        val tap = isTap(g)
        val committedDraft = draft
        val ring = lasso.copyOf(lassoCount * 2)
        reset()
        when (g) {
            is Lasso -> c.select(if (tap) pickAt(g.x0, g.y0) else Selection.lasso(c.note.value.elements, ring).toSet())
            is Marquee -> c.select(if (tap) pickAt(g.x0, g.y0) else Selection.rectangle(c.note.value.elements, Rect.fromEdges(g.x0, g.y0, x, y)).toSet())
            is Move -> if (tap) {
                val hit = pickAt(g.x0, g.y0)
                if (hit.isNotEmpty() && hit.first() !in g.ids) c.select(hit)
            } else {
                c.transformSelection(g.ids, Similarity.translate(x - g.x0, y - g.y0))
            }
            is Scale -> {
                val f = scaleOf(g, x, y)
                if (!tap && f != 1f) c.transformSelection(g.ids, Similarity.scale(f, g.anchorX, g.anchorY))
            }
            is Rotate -> {
                val turn = turnOf(g)
                if (turn != 0f) c.transformSelection(g.ids, Similarity.rotate(turn, g.cx, g.cy))
            }
            is DrawShape, is DrawLine -> {
                val d = committedDraft
                if (!tap && d != null && (d !is Shape || (d.frame.width > 0f && d.frame.height > 0f))) {
                    c.edit { it.copy(elements = it.elements + d) }
                }
            }
            is PlaceText -> placeText(g, x, y, tap)
        }
        c.overlayChanged()
    }

    private fun placeText(g: PlaceText, x: Float, y: Float, tap: Boolean) {
        if (tap) {
            val hit = c.note.value.elements.lastOrNull { it is TextBox && it.frame.containsRotated(it.rotation, g.x0, g.y0, dp(PICK_DP)) }
            if (hit is TextBox) {
                c.beginTextEdit(hit)
                return
            }
        }
        val size = c.textSize
        val frame = if (tap) {
            Rect(g.x0, g.y0 - size * 0.6f, CanvasController.DEFAULT_TEXT_WIDTH, size * TEXT_LINE_HEIGHT)
        } else {
            Rect(min(g.x0, x), min(g.y0, y), max(abs(x - g.x0), MIN_TEXT_WIDTH), size * TEXT_LINE_HEIGHT)
        }
        c.beginTextEdit(TextBox(Element.newId(), frame, "", size, c.color))
    }

    fun cancel() {
        if (gesture == null) return
        reset()
        c.overlayChanged()
    }

    private fun reset() {
        gesture = null
        lassoCount = 0
        marquee = null
        draft = null
        transform = Similarity.IDENTITY
        readout = null
    }

    private fun pickAt(x: Float, y: Float): Set<String> =
        setOfNotNull(Selection.pick(c.note.value.elements, x, y, dp(PICK_DP), c.outlineCache::outline)?.id)

    private fun addLassoPoint(x: Float, y: Float) {
        val n = lassoCount
        if (n > 0 && hypot(x - lasso[n * 2 - 2], y - lasso[n * 2 - 1]) < dp(LASSO_STEP_DP)) return
        if (lasso.size < (n + 1) * 2) lasso = lasso.copyOf(lasso.size * 2)
        lasso[n * 2] = x
        lasso[n * 2 + 1] = y
        lassoCount = n + 1
    }

    companion object {
        /** A pen that moves less than this (dp on screen) between down and up has tapped. */
        const val TAP_SLOP_DP = 6f

        /** How far from a tap an element's ink may be and still be picked, dp on screen. */
        const val PICK_DP = 6f

        /** The selection box's gap around the selected elements' bounds, dp on screen. */
        const val SELECTION_PAD_DP = 6f

        /** How far from a corner or rotate handle a pen-down grabs it, dp on screen. */
        const val HANDLE_REACH_DP = 22f

        /** How far the rotate handle sits above the selection box's top edge, dp on screen. */
        const val ROTATE_HANDLE_GAP_DP = 28f

        /** Lasso points closer than this to the last one (dp on screen) are skipped. */
        const val LASSO_STEP_DP = 2f

        /** Line drag samples closer than this to the last one (dp on screen) are skipped. */
        const val LINE_SAMPLE_STEP_DP = 2f

        /** How far (dp on screen) a curve's simplified points may stray from the drag. */
        const val CURVE_TOLERANCE_DP = 6f

        /** Scaling stops when the selection's longer side would be smaller than this, canvas units. */
        const val MIN_SCALED_SIZE = 4f

        /** A new text box's height, in font sizes, before it is measured. */
        const val TEXT_LINE_HEIGHT = 1.4f

        /** The narrowest text box a drag makes, canvas units. */
        const val MIN_TEXT_WIDTH = 40f
    }
}

internal fun Rect.contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom
