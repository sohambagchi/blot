package app.blot.canvas

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import app.blot.ui.theme.BlotColors
import kotlin.math.hypot

/**
 * Draws what the tools show over the note: the shape or line being drawn, the lasso or selection
 * rectangle, the selection's box with its corner handles and rotate handle, and the readout of a
 * turn or scale being dragged. The chrome is a fixed size on screen. Draw it after the scene, on
 * the canvas view's canvas (view pixels). UI thread.
 *
 * @param spPx pixels per sp, for the readout's text.
 * @param typeface the readout's face (the app's Inter Medium), or null for the default.
 */
internal class ToolOverlayPainter(private val density: Float, spPx: Float = density, typeface: Typeface? = null) {
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
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pillEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = READOUT_TEXT_SP * spPx
        textAlign = Paint.Align.CENTER
        if (typeface != null) this.typeface = typeface
    }
    private val path = Path()
    private val rectF = RectF()
    private val arcBox = RectF()

    fun draw(canvas: Canvas, c: CanvasController, renderer: SceneRenderer, dark: Boolean) {
        val v = c.viewport
        val g = c.toolGestures
        val accent = if (dark) ACCENT_DARK else ACCENT_LIGHT
        edge.color = accent
        dashed.color = accent
        tint.color = accent and 0x00FFFFFF or (0x18 shl 24)

        g.draft?.let { d ->
            canvas.save()
            canvas.translate(v.offsetX, v.offsetY)
            canvas.scale(v.scale, v.scale)
            renderer.drawElement(canvas, d, dark)
            canvas.restore()
        }

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

        val frame = c.selectionFrame()?.transformed(g.transform) ?: return
        val pad = ToolGestures.SELECTION_PAD_DP / v.zoom
        val xy = frame.corners(pad)
        path.rewind()
        path.moveTo(v.toScreenX(xy[0]), v.toScreenY(xy[1]))
        for (i in 1 until 4) path.lineTo(v.toScreenX(xy[i * 2]), v.toScreenY(xy[i * 2 + 1]))
        path.close()
        canvas.drawPath(path, edge)

        // The rotate handle on a stem from the middle of the top edge.
        val topX = v.toScreenX((xy[0] + xy[2]) / 2)
        val topY = v.toScreenY((xy[1] + xy[3]) / 2)
        val h = frame.rotateHandle(pad, ToolGestures.ROTATE_HANDLE_GAP_DP / v.zoom)
        val hx = v.toScreenX(h.x)
        val hy = v.toScreenY(h.y)
        val radius = HANDLE_RADIUS_DP * density
        val rotateRadius = ROTATE_HANDLE_RADIUS_DP * density
        val stem = hypot(hx - topX, hy - topY)
        if (stem > rotateRadius) {
            val k = (stem - rotateRadius) / stem
            canvas.drawLine(topX, topY, topX + (hx - topX) * k, topY + (hy - topY) * k, edge)
        }
        knob.color = if (dark) KNOB_DARK else KNOB_LIGHT
        canvas.drawCircle(hx, hy, rotateRadius, knob)
        canvas.drawCircle(hx, hy, rotateRadius, edge)
        drawTurnGlyph(canvas, hx, hy)

        for (i in 0 until 4) {
            val x = v.toScreenX(xy[i * 2])
            val y = v.toScreenY(xy[i * 2 + 1])
            canvas.drawCircle(x, y, radius, knob)
            canvas.drawCircle(x, y, radius, edge)
        }

        g.readout?.let { drawReadout(canvas, it, c, dark) }
    }

    /** A three-quarter circle with an arrowhead, inside the rotate handle. */
    private fun drawTurnGlyph(canvas: Canvas, cx: Float, cy: Float) {
        val r = 3.5f * density
        arcBox.set(cx - r, cy - r, cx + r, cy + r)
        path.rewind()
        path.addArc(arcBox, -60f, 270f)
        canvas.drawPath(path, edge)
        // The arc ends at −60° + 270° = 210°; the head points along the turn.
        val ex = cx + r * COS_210
        val ey = cy + r * SIN_210
        val a = 2.2f * density
        path.rewind()
        path.moveTo(ex - a, ey - a * 0.2f)
        path.lineTo(ex, ey)
        path.lineTo(ex + a * 0.4f, ey - a)
        canvas.drawPath(path, edge)
    }

    private fun drawReadout(canvas: Canvas, r: Readout, c: CanvasController, dark: Boolean) {
        val v = c.viewport
        val tokens = if (dark) BlotColors.Dark else BlotColors.Light
        val hx = v.toScreenX(r.x)
        val hy = v.toScreenY(r.y)
        val len = hypot(r.dirX, r.dirY)
        val ux = if (len > 0f) r.dirX / len else 0f
        val uy = if (len > 0f) r.dirY / len else -1f
        val textW = label.measureText(r.text)
        val w = textW + 2 * READOUT_PAD_X_DP * density
        val hgt = READOUT_HEIGHT_DP * density
        // Out from the handle along its direction, far enough that the pill clears the knob.
        val reach = READOUT_GAP_DP * density + (kotlin.math.abs(ux) * w + kotlin.math.abs(uy) * hgt) / 2
        // Kept on screen when the handle is near or past an edge.
        val m = READOUT_GAP_DP * density
        val cx = (hx + ux * reach).coerceIn(m + w / 2, maxOf(m + w / 2, canvas.width - m - w / 2))
        val cy = (hy + uy * reach).coerceIn(m + hgt / 2, maxOf(m + hgt / 2, canvas.height - m - hgt / 2))
        rectF.set(cx - w / 2, cy - hgt / 2, cx + w / 2, cy + hgt / 2)
        val corner = hgt / 2
        pill.color = tokens.active.toArgb()
        pill.setShadowLayer(SHADOW_BLUR_DP * density / 2, 0f, SHADOW_DY_DP * density, tokens.shadow.toArgb())
        canvas.drawRoundRect(rectF, corner, corner, pill)
        pill.clearShadowLayer()
        pillEdge.color = tokens.border.toArgb()
        canvas.drawRoundRect(rectF, corner, corner, pillEdge)
        label.color = tokens.activeText.toArgb()
        val fm = label.fontMetrics
        canvas.drawText(r.text, cx, cy - (fm.ascent + fm.descent) / 2, label)
    }

    companion object {
        const val HANDLE_RADIUS_DP = 6f
        const val ROTATE_HANDLE_RADIUS_DP = 8f
        const val READOUT_TEXT_SP = 12f
        const val READOUT_HEIGHT_DP = 24f
        const val READOUT_PAD_X_DP = 9f
        const val READOUT_GAP_DP = 14f
        private const val SHADOW_BLUR_DP = 10f
        private const val SHADOW_DY_DP = 2f
        private const val COS_210 = -0.8660254f
        private const val SIN_210 = -0.5f
        const val ACCENT_LIGHT = 0xFF2F6FED.toInt()
        const val ACCENT_DARK = 0xFF8AB0FF.toInt()
        const val KNOB_LIGHT = 0xFFFFFFFF.toInt()
        const val KNOB_DARK = 0xFF1C1C1E.toInt()
    }
}
