package app.screenscratch.export

import android.graphics.Canvas
import app.screenscratch.canvas.TextBoxRenderer
import app.screenscratch.core.export.SvgText
import app.screenscratch.core.model.TextBox
import app.screenscratch.markdown.MarkdownDecoration
import app.screenscratch.markdown.MarkdownImageLoader
import app.screenscratch.markdown.MarkdownLayout
import app.screenscratch.markdown.drawTextBox
import app.screenscratch.markdown.textBoxLayout

/**
 * Text boxes for one export, laid out by [textBoxLayout] as on screen. [dark] picks the theme for
 * muted text and code backgrounds; [paper] is the page colour the theme's task tick uses.
 * Not thread-safe; one per export.
 */
internal class MarkdownTextBoxes(
    private val dark: Boolean,
    private val paper: Int,
    private val images: MarkdownImageLoader,
) : TextBoxRenderer {
    private class Key(val box: TextBox, val color: Int) {
        override fun equals(other: Any?) = other is Key && other.box === box && other.color == color
        override fun hashCode() = System.identityHashCode(box) * 31 + color
    }

    private val layouts = HashMap<Key, MarkdownLayout>()

    fun layout(box: TextBox, color: Int): MarkdownLayout = layouts.getOrPut(Key(box, color)) {
        textBoxLayout(box, color, dark, images, paper)
    }

    override fun draw(canvas: Canvas, box: TextBox, color: Int) = drawTextBox(canvas, box, layout(box, color))

    /** The same layout as [draw], as lines and decorations for SVG. */
    fun svgText(box: TextBox, color: Int): SvgText {
        val layout = layout(box, color)
        val lines = layout.lines.map { line ->
            SvgText.TextLine(
                line.baseline,
                line.runs.map { r ->
                    val s = r.style
                    SvgText.Run(
                        r.text, r.x, r.width, s.textSize, s.weight, s.italic, s.monospace, s.color,
                        s.underline, s.strikethrough, r.link,
                    )
                },
            )
        }
        val decorations = layout.decorations.map { d ->
            when (d) {
                is MarkdownDecoration.Box -> SvgText.Decoration.Box(d.left, d.top, d.right, d.bottom, d.color, d.radius)
                is MarkdownDecoration.Outline -> SvgText.Decoration.Outline(d.left, d.top, d.right, d.bottom, d.color, d.strokeWidth, d.radius)
                is MarkdownDecoration.Circle -> SvgText.Decoration.Circle(d.cx, d.cy, d.radius, d.color, d.strokeWidth)
                is MarkdownDecoration.Polyline -> SvgText.Decoration.Polyline(d.points, d.color, d.strokeWidth)
                is MarkdownDecoration.Label -> SvgText.Decoration.Label(d.text, d.x, d.baseline, d.textSize, d.color)
                is MarkdownDecoration.Image -> SvgText.Decoration.Image(d.path, d.left, d.top, d.right, d.bottom)
            }
        }
        return SvgText(lines, decorations)
    }
}
