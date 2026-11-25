package app.blot.canvas

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Draws in-progress ink: each [LiveSegment] is filled as the convex hull of its two end circles,
 * so consecutive segments join without beads where the width changes. The canvas must already
 * be in canvas units. One instance per drawing thread.
 */
class LiveInkPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    fun draw(canvas: Canvas, s: LiveSegment, color: Int) {
        paint.color = color
        hull(s, path)
        canvas.drawPath(path, paint)
    }

    companion object {
        /** Writes the hull of [s]'s end circles into [out]: one closed contour, wound consistently. */
        fun hull(s: LiveSegment, out: Path) {
            out.rewind()
            val dx = s.x1 - s.x0
            val dy = s.y1 - s.y0
            val d = hypot(dx, dy)
            // One circle inside the other: the hull is the larger circle.
            if (d <= abs(s.r1 - s.r0) || d < 1e-4f) {
                if (s.r1 >= s.r0) out.addCircle(s.x1, s.y1, s.r1, Path.Direction.CW)
                else out.addCircle(s.x0, s.y0, s.r0, Path.Direction.CW)
                return
            }
            val ux = dx / d
            val uy = dy / d
            // External tangents: the normal is rotated from the axis by acos((r0 − r1) / d).
            val c = (s.r0 - s.r1) / d
            val sn = sqrt((1 - c * c).coerceAtLeast(0f))
            val n1x = ux * c - uy * sn
            val n1y = uy * c + ux * sn
            val n2x = ux * c + uy * sn
            val n2y = uy * c - ux * sn
            // Tangent quad, then both circles, all clockwise in y-down screen space so the non-zero fill is their union.
            out.moveTo(s.x0 + n2x * s.r0, s.y0 + n2y * s.r0)
            out.lineTo(s.x1 + n2x * s.r1, s.y1 + n2y * s.r1)
            out.lineTo(s.x1 + n1x * s.r1, s.y1 + n1y * s.r1)
            out.lineTo(s.x0 + n1x * s.r0, s.y0 + n1y * s.r0)
            out.close()
            out.addCircle(s.x0, s.y0, s.r0, Path.Direction.CW)
            out.addCircle(s.x1, s.y1, s.r1, Path.Direction.CW)
        }
    }
}
