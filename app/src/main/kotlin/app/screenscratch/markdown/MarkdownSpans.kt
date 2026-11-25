package app.screenscratch.markdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import android.text.style.UpdateAppearance

// None of these spans draw anything. They only shape the layout; MarkdownLayout computes the
// visible decorations (bars, bullets, backgrounds, images) from them, so the same list serves
// canvas drawing and SVG export.

/** Heading size; bold comes from a separate StyleSpan. */
internal class HeadingSpan(val level: Int, private val sizePx: Float) : MetricAffectingSpan() {
    override fun updateMeasureState(paint: TextPaint) {
        paint.textSize = sizePx
    }

    override fun updateDrawState(paint: TextPaint) {
        paint.textSize = sizePx
    }
}

/** Monospace at 90% of the surrounding size; [block] distinguishes fenced/indented code. */
internal class CodeSpan(val block: Boolean) : MetricAffectingSpan() {
    override fun updateMeasureState(paint: TextPaint) = apply(paint)

    override fun updateDrawState(paint: TextPaint) = apply(paint)

    private fun apply(paint: TextPaint) {
        val style = paint.typeface?.style ?: Typeface.NORMAL
        paint.typeface = Typeface.create(Typeface.MONOSPACE, style)
        paint.textSize *= CODE_SCALE
    }

    companion object {
        const val CODE_SCALE = 0.9f
    }
}

internal class LinkSpan(val url: String, private val color: Int) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(paint: TextPaint) {
        paint.color = color
        paint.isUnderlineText = true
    }
}

/**
 * A leading margin that draws nothing. [depth] is the block's nesting depth, so the x offset of
 * its decoration can be computed without relying on the order spans are returned in.
 */
internal abstract class BlockMarginSpan(val depth: Int, val marginPx: Int) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean): Int = marginPx

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout,
    ) = Unit
}

internal class BlockQuoteSpan(depth: Int, marginPx: Int) : BlockMarginSpan(depth, marginPx)

internal sealed interface ListMarker {
    /** [level] counts enclosing bullet lists; it picks disc, circle or square. */
    data class Bullet(val level: Int) : ListMarker
    data class Number(val label: String) : ListMarker
    data class Task(val checked: Boolean) : ListMarker
}

/** Hanging indent for one list item; the marker sits in the margin of its first line. */
internal class ListItemSpan(depth: Int, indentPx: Int, val marker: ListMarker) :
    BlockMarginSpan(depth, indentPx)

/** Inset text and add [paddingPx] above the first and below the last line of a code block. */
internal class CodeBlockSpan(depth: Int, val paddingPx: Int) :
    BlockMarginSpan(depth, paddingPx), LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int,
        fm: Paint.FontMetricsInt,
    ) {
        val spanned = text as android.text.Spanned
        if (start == spanned.getSpanStart(this)) {
            fm.ascent -= paddingPx
            fm.top -= paddingPx
        }
        if (end == spanned.getSpanEnd(this)) {
            fm.descent += paddingPx
            fm.bottom += paddingPx
        }
    }
}

/** Gives a single-newline line a fixed height; used for the gap between blocks. */
internal class SpacerSpan(private val heightPx: Int) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int,
        fm: Paint.FontMetricsInt,
    ) {
        fm.ascent = -heightPx
        fm.top = -heightPx
        fm.descent = 0
        fm.bottom = 0
    }
}

/** A box of fixed size that sits on the baseline; its content is drawn as a decoration. */
internal abstract class BoxSpan(val widthPx: Int, val heightPx: Int) : ReplacementSpan() {
    override fun getSize(
        paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?,
    ): Int {
        if (fm != null) {
            fm.ascent = -heightPx
            fm.top = -heightPx
            fm.descent = 0
            fm.bottom = 0
        }
        return widthPx
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int,
        bottom: Int, paint: Paint,
    ) = Unit
}

internal class RuleSpan(widthPx: Int, heightPx: Int) : BoxSpan(widthPx, heightPx)

/** An image scaled to the available width; [bitmap] null means draw a placeholder. */
internal class ImageSpan(
    val path: String, val alt: String, val bitmap: Bitmap?, widthPx: Int, heightPx: Int,
) : BoxSpan(widthPx, heightPx)
