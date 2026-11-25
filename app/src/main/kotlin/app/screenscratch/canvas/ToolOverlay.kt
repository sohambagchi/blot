package app.screenscratch.canvas

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path

/**
 * Draws what the select tool shows over the note: the lasso or selection rectangle, and the
 * selection's box with its corner handles. The box and handles are a fixed
 * size on screen. Draw it after the scene, on the canvas view's canvas (view pixels). UI thread.
 */
internal class ToolOverlayPainter(private val density: Float) {
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
    }
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    fun draw(canvas: Canvas, c: CanvasController, renderer: SceneRenderer, dark: Boolean) {
        val v = c.viewport
        val g = c.toolGestures
        val accent = if (dark) ACCENT_DARK else ACCENT_LIGHT
        edge.color = accent
        dashed.color = accent
        tint.color = accent and 0x00FFFFFF or (0x18 shl 24)

        if (g.lassoCount > 1) {
            val xy = g.lasso
            path.rewind()
            path.moveTo(v.toScreenX(xy[0]), v.toScreenY(xy[1]))
            for (i in 1 until g.lassoCount) path.lineTo(v.toScreenX(xy[i * 2]), v.toScreenY(xy[i * 2 + 1]))
            path.close()
            canvas.drawPath(path, tint)
            canvas.drawPath(path, dashed)
        }

        g.marquee?.let { r ->
            val l = v.toScreenX(r.left)
            val t = v.toScreenY(r.top)
            val rr = v.toScreenX(r.right)
            val b = v.toScreenY(r.bottom)
            canvas.drawRect(l, t, rr, b, tint)
            canvas.drawRect(l, t, rr, b, dashed)
        }

        val bounds = c.selectionBounds ?: return
        val box = g.transform.rect(bounds)
        val pad = ToolGestures.SELECTION_PAD_DP * density
        val l = v.toScreenX(box.left) - pad
        val t = v.toScreenY(box.top) - pad
        val r = v.toScreenX(box.right) + pad
        val b = v.toScreenY(box.bottom) + pad
        canvas.drawRect(l, t, r, b, edge)
        knob.color = if (dark) KNOB_DARK else KNOB_LIGHT
        val radius = HANDLE_RADIUS_DP * density
        for ((x, y) in listOf(l to t, r to t, r to b, l to b)) {
            canvas.drawCircle(x, y, radius, knob)
            canvas.drawCircle(x, y, radius, edge)
        }
    }

    companion object {
        const val HANDLE_RADIUS_DP = 6f
        const val ACCENT_LIGHT = 0xFF2F6FED.toInt()
        const val ACCENT_DARK = 0xFF8AB0FF.toInt()
        const val KNOB_LIGHT = 0xFFFFFFFF.toInt()
        const val KNOB_DARK = 0xFF1C1C1E.toInt()
    }
}
