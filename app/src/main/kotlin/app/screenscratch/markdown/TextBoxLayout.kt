package app.screenscratch.markdown

import android.graphics.Canvas
import app.screenscratch.core.model.TextBox
import kotlin.math.roundToInt

/**
 * The layout of a canvas text box, shared by the screen and every export format so they match:
 * canvas units throughout (density 1), the box's font size as the base size, wrapped to the
 * frame's width. [paper] is the page colour the checked task box's tick is drawn in; null keeps
 * the theme's own.
 */
fun textBoxLayout(box: TextBox, color: Int, dark: Boolean, images: MarkdownImageLoader, paper: Int? = null): MarkdownLayout {
    val base = if (dark) MarkdownTheme.Dark else MarkdownTheme.Light
    val theme = base.copy(textColor = color, baseTextSize = box.fontSize, background = paper ?: base.background)
    return MarkdownLayout(box.markdown, textBoxWidth(box), theme, density = 1f, imageLoader = images)
}

/** The width [textBoxLayout] wraps to, in whole canvas units. */
fun textBoxWidth(box: TextBox): Int = box.frame.width.roundToInt().coerceAtLeast(1)

/** Draws [layout] into [box]'s frame, clipped to it. */
fun drawTextBox(canvas: Canvas, box: TextBox, layout: MarkdownLayout) {
    val f = box.frame
    canvas.save()
    canvas.clipRect(f.left, f.top, f.right, f.bottom)
    canvas.translate(f.x, f.y)
    layout.draw(canvas)
    canvas.restore()
}
