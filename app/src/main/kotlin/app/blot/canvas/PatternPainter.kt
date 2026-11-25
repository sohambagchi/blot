package app.blot.canvas

import android.graphics.Canvas
import android.graphics.Paint
import app.blot.core.model.Background
import app.blot.core.model.PageSetup
import app.blot.core.model.Pattern
import app.blot.core.model.PatternMarks
import app.blot.core.model.Rect
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws background patterns into a canvas already scaled to canvas units, each kind of mark in
 * one `drawPoints` or `drawLines` call. Marks keep at least [MIN_DOT_RADIUS_PX] and
 * [MIN_LINE_PX] on screen so they don't vanish when zoomed out. One instance per drawing thread.
 */
class PatternPainter {
    private val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val lines = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private var buffer = FloatArray(0)

    /**
     * The marks of [background] on [page] inside [visible], thinned for [pxPerUnit] screen pixels
     * per canvas unit: one per page that [visible] reaches, or one for the infinite canvas.
     */
    fun marks(background: Background, page: PageSetup, visible: Rect, pxPerUnit: Float): List<PatternMarks> {
        val stride = PatternMarks.stride(background.spacing, pxPerUnit)
        if (stride == 0) return emptyList()
        return when (page) {
            PageSetup.Infinite -> listOfNotNull(PatternMarks.of(background, null, visible, stride))
            is PageSetup.Pages -> visiblePages(page, visible).mapNotNull { PatternMarks.of(background, page.pageRect(it), visible, stride) }
        }
    }

    /**
     * Draws [marks] clipped to their area, dots in [dotColor] and lines in [lineColor]. When
     * ([originX], [originY]) gives the output pixel of the canvas origin, lines are a whole number
     * of pixels wide and snapped to the pixel grid, so they stay crisp; NaN leaves them unsnapped,
     * as for vector output.
     */
    fun draw(
        canvas: Canvas,
        marks: PatternMarks,
        pxPerUnit: Float,
        dotColor: Int,
        lineColor: Int,
        originX: Float = Float.NaN,
        originY: Float = Float.NaN,
    ) {
        val a = marks.area
        canvas.save()
        canvas.clipRect(a.left, a.top, a.right, a.bottom)
        when (marks.pattern) {
            Pattern.DOTS -> drawDots(canvas, marks, pxPerUnit, dotColor)
            Pattern.GRID, Pattern.RULED -> drawLines(canvas, marks, pxPerUnit, lineColor, originX, originY)
        }
        canvas.restore()
    }

    private fun drawDots(canvas: Canvas, m: PatternMarks, pxPerUnit: Float, color: Int) {
        val n = m.xs.size * m.ys.size
        if (n == 0) return
        val pts = buffer(n * 2)
        var k = 0
        for (y in m.ys) for (x in m.xs) {
            pts[k++] = x
            pts[k++] = y
        }
        dots.color = color
        dots.strokeWidth = 2 * max(PatternMarks.DOT_RADIUS, MIN_DOT_RADIUS_PX / pxPerUnit)
        // Dots a few pixels across look the same square, and squares draw many times faster.
        dots.strokeCap = if (dots.strokeWidth * pxPerUnit < ROUND_DOT_PX) Paint.Cap.SQUARE else Paint.Cap.ROUND
        canvas.drawPoints(pts, 0, k, dots)
    }

    private fun drawLines(canvas: Canvas, m: PatternMarks, pxPerUnit: Float, color: Int, originX: Float, originY: Float) {
        val n = m.xs.size + m.ys.size
        if (n == 0) return
        val a = m.area
        val pts = buffer(n * 4)
        val snap = !originX.isNaN() && !originY.isNaN() && pxPerUnit.isFinite()
        val px = if (snap) max(MIN_LINE_PX, (PatternMarks.LINE_WIDTH * pxPerUnit).roundToInt().toFloat()) else 0f
        // An odd pixel width centres on a pixel's middle, an even one on a pixel edge.
        val half = if (px.toInt() % 2 == 1) 0.5f else 0f
        fun snapped(u: Float, origin: Float) = if (!snap) u else (floor(u * pxPerUnit + origin - half + 0.5f) + half - origin) / pxPerUnit
        var k = 0
        for (x in m.xs) {
            val sx = snapped(x, originX)
            pts[k++] = sx; pts[k++] = a.top; pts[k++] = sx; pts[k++] = a.bottom
        }
        for (y in m.ys) {
            val sy = snapped(y, originY)
            pts[k++] = a.left; pts[k++] = sy; pts[k++] = a.right; pts[k++] = sy
        }
        lines.color = color
        lines.strokeWidth = if (snap) px / pxPerUnit else max(PatternMarks.LINE_WIDTH, MIN_LINE_PX / pxPerUnit)
        canvas.drawLines(pts, 0, k, lines)
    }

    private fun buffer(size: Int): FloatArray {
        if (buffer.size < size) buffer = FloatArray(size)
        return buffer
    }

    companion object {
        /** Screen pixels. */
        const val MIN_DOT_RADIUS_PX = 0.75f
        const val MIN_LINE_PX = 1f

        /** Screen pixels: narrower dots are drawn as squares. */
        const val ROUND_DOT_PX = 2.5f
    }
}
