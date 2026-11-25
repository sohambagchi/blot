package app.blot.core.edit

import app.blot.core.model.Point
import app.blot.core.model.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Constraints for drawing shapes and lines. */
object Snap {
    /** 15°, Excalidraw's `SHIFT_LOCKING_ANGLE`. */
    const val ANGLE_STEP: Double = PI / 12

    /**
     * ([x], [y]) moved onto the nearest ray from ([ox], [oy]) at a multiple of [step] radians:
     * the projection of the point onto that ray, so the line's length follows the pen.
     *
     * After Excalidraw's `getLockedLinearCursorAlignSize` (MIT, Copyright (c) 2020 Excalidraw,
     * https://github.com/excalidraw/excalidraw), with the projection taken along the ray's unit
     * vector instead of by intersecting two lines, which has no special cases at 0° and 90°.
     */
    fun angle(ox: Float, oy: Float, x: Float, y: Float, step: Double = ANGLE_STEP): Point {
        val dx = (x - ox).toDouble()
        val dy = (y - oy).toDouble()
        if (dx == 0.0 && dy == 0.0) return Point(x, y)
        val a = (atan2(dy, dx) / step).roundToInt() * step
        val ux = cos(a)
        val uy = sin(a)
        val d = dx * ux + dy * uy
        // Exact zeros on the axes, so a snapped horizontal line has equal y.
        val px = if (abs(ux) < 1e-9) 0.0 else d * ux
        val py = if (abs(uy) < 1e-9) 0.0 else d * uy
        return Point((ox + px).toFloat(), (oy + py).toFloat())
    }

    /**
     * The rectangle dragged from ([ax], [ay]) to ([bx], [by]); a square with the drag's longer
     * side when [square], growing from the start corner towards the pen.
     */
    fun dragRect(ax: Float, ay: Float, bx: Float, by: Float, square: Boolean): Rect {
        if (!square) return Rect.fromEdges(ax, ay, bx, by)
        val side = max(abs(bx - ax), abs(by - ay))
        val sx = if (bx < ax) -1f else 1f
        val sy = if (by < ay) -1f else 1f
        return Rect.fromEdges(ax, ay, ax + sx * side, ay + sy * side)
    }
}
