package app.screenscratch.canvas

import app.screenscratch.core.model.Element
import app.screenscratch.core.model.Rect
import app.screenscratch.core.model.Stroke
import kotlin.math.max
import kotlin.math.min

/**
 * Whole-stroke eraser geometry. The eraser sweeps a capsule (a segment with a radius) between
 * consecutive samples; a stroke is erased when the capsule touches its filled outline.
 */
object Eraser {
    /**
     * Ids of the strokes in [elements] whose outline the capsule from ([ax], [ay]) to ([bx], [by])
     * with radius [r] touches, in stacking order. Other element types are never erased. Bounds
     * are checked first; only strokes whose bounds meet the capsule's are tested exactly.
     */
    fun hits(
        elements: List<Element>,
        outline: (Stroke) -> FloatArray,
        ax: Float, ay: Float, bx: Float, by: Float, r: Float,
    ): List<String> {
        val box = Rect.fromEdges(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by)).inflate(r)
        val out = ArrayList<String>()
        for (e in elements) {
            if (e !is Stroke || !e.bounds.intersects(box)) continue
            val xy = outline(e)
            if (touches(xy, xy.size / 2, ax, ay, bx, by, r)) out += e.id
        }
        return out
    }

    /**
     * True when the capsule from ([ax], [ay]) to ([bx], [by]) with radius [r] touches the polygon
     * of the first [count] points of [xy], filled with the non-zero winding rule as `Path` fills it.
     */
    fun touches(xy: FloatArray, count: Int, ax: Float, ay: Float, bx: Float, by: Float, r: Float): Boolean {
        if (count == 0) return false
        if (count == 1) return segmentPointDistanceSq(ax, ay, bx, by, xy[0], xy[1]) <= r * r
        if (winding(xy, count, ax, ay) != 0) return true
        val r2 = r * r
        var px = xy[(count - 1) * 2]
        var py = xy[(count - 1) * 2 + 1]
        for (i in 0 until count) {
            val qx = xy[i * 2]
            val qy = xy[i * 2 + 1]
            if (segmentSegmentDistanceSq(ax, ay, bx, by, px, py, qx, qy) <= r2) return true
            px = qx
            py = qy
        }
        return false
    }

    /** Winding number of the closed polygon around ([x], [y]); non-zero means inside. */
    fun winding(xy: FloatArray, count: Int, x: Float, y: Float): Int {
        var w = 0
        var px = xy[(count - 1) * 2]
        var py = xy[(count - 1) * 2 + 1]
        for (i in 0 until count) {
            val qx = xy[i * 2]
            val qy = xy[i * 2 + 1]
            val side = (qx - px) * (y - py) - (x - px) * (qy - py)
            if (py <= y) {
                if (qy > y && side > 0) w++
            } else if (qy <= y && side < 0) {
                w--
            }
            px = qx
            py = qy
        }
        return w
    }

    fun segmentPointDistanceSq(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        val cx = ax + t * dx - px
        val cy = ay + t * dy - py
        return cx * cx + cy * cy
    }

    fun segmentSegmentDistanceSq(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float,
    ): Float {
        if (segmentsIntersect(ax, ay, bx, by, cx, cy, dx, dy)) return 0f
        return min(
            min(segmentPointDistanceSq(ax, ay, bx, by, cx, cy), segmentPointDistanceSq(ax, ay, bx, by, dx, dy)),
            min(segmentPointDistanceSq(cx, cy, dx, dy, ax, ay), segmentPointDistanceSq(cx, cy, dx, dy, bx, by)),
        )
    }

    private fun segmentsIntersect(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float,
    ): Boolean {
        val d1 = cross(cx, cy, dx, dy, ax, ay)
        val d2 = cross(cx, cy, dx, dy, bx, by)
        val d3 = cross(ax, ay, bx, by, cx, cy)
        val d4 = cross(ax, ay, bx, by, dx, dy)
        // Collinear and touching cases are covered by the endpoint distances.
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    private fun cross(ox: Float, oy: Float, ax: Float, ay: Float, bx: Float, by: Float): Float =
        (ax - ox) * (by - oy) - (ay - oy) * (bx - ox)
}
