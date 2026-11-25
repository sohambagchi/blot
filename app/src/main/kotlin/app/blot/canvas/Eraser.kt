package app.blot.canvas

import app.blot.core.edit.PlaneMath
import app.blot.core.edit.Selection
import app.blot.core.model.Element
import app.blot.core.model.Rect
import app.blot.core.model.Stroke
import kotlin.math.max
import kotlin.math.min

/**
 * Eraser geometry. The eraser sweeps a capsule (a segment with a radius) between consecutive
 * samples; an element is erased when the capsule touches its ink ([Selection.inkTouches]).
 */
object Eraser {
    /**
     * Ids of the strokes, shapes and lines in [elements] whose ink the capsule from ([ax], [ay]) to
     * ([bx], [by]) with radius [r] touches, in stacking order. Text boxes and images are never
     * erased. Bounds are checked first; only elements whose bounds meet the capsule's are tested
     * exactly.
     */
    fun hits(
        elements: List<Element>,
        outline: (Stroke) -> FloatArray,
        ax: Float, ay: Float, bx: Float, by: Float, r: Float,
    ): List<String> {
        val box = Rect.fromEdges(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by)).inflate(r)
        val out = ArrayList<String>()
        for (e in elements) {
            if (!e.bounds.intersects(box)) continue
            if (Selection.inkTouches(e, ax, ay, bx, by, r, outline)) out += e.id
        }
        return out
    }

    /**
     * True when the capsule from ([ax], [ay]) to ([bx], [by]) with radius [r] touches the polygon
     * of the first [count] points of [xy], filled with the non-zero winding rule as `Path` fills it.
     */
    fun touches(xy: FloatArray, count: Int, ax: Float, ay: Float, bx: Float, by: Float, r: Float): Boolean =
        PlaneMath.capsuleTouchesPolygon(xy, count, ax, ay, bx, by, r)

    /** Winding number of the closed polygon around ([x], [y]); non-zero means inside. */
    fun winding(xy: FloatArray, count: Int, x: Float, y: Float): Int = PlaneMath.winding(xy, count, x, y)

    fun segmentPointDistanceSq(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Float =
        PlaneMath.segmentPointDistanceSq(ax, ay, bx, by, px, py)

    fun segmentSegmentDistanceSq(
        ax: Float, ay: Float, bx: Float, by: Float,
        cx: Float, cy: Float, dx: Float, dy: Float,
    ): Float = PlaneMath.segmentSegmentDistanceSq(ax, ay, bx, by, cx, cy, dx, dy)
}
