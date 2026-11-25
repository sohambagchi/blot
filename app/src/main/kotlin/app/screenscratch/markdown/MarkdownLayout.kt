package app.screenscratch.markdown

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import kotlin.math.max

/**
 * Markdown laid out at a fixed width. The one renderer for text boxes: [draw] gives the same
 * result on a hardware canvas, a Bitmap canvas and a `PdfDocument` page (text stays vector),
 * and [lines] plus [decorations] describe the same picture for SVG export.
 *
 * The layout is built lazily and cached; changing an input rebuilds it on next use. Not
 * thread-safe.
 *
 * @param widthPx box width in px; text wraps and images scale to it.
 * @param density px per dp, applied to [MarkdownTheme.baseTextSize] and all derived sizes.
 */
class MarkdownLayout(
    source: String,
    widthPx: Int,
    theme: MarkdownTheme = MarkdownTheme.Light,
    density: Float = 1f,
    imageLoader: MarkdownImageLoader = MarkdownImageLoader.None,
) {
    var source: String = source
        set(value) { if (field != value) { field = value; invalidate() } }
    var widthPx: Int = widthPx
        set(value) { if (field != value) { field = value; invalidate() } }
    var theme: MarkdownTheme = theme
        set(value) { if (field != value) { field = value; invalidate() } }
    var density: Float = density
        set(value) { if (field != value) { field = value; invalidate() } }
    var imageLoader: MarkdownImageLoader = imageLoader
        set(value) { if (field !== value) { field = value; invalidate() } }

    private class Built(
        val text: Spanned,
        val layout: StaticLayout,
        val paint: TextPaint,
        val metrics: MarkdownMetrics,
    ) {
        var decorations: List<MarkdownDecoration>? = null
        var lines: List<MarkdownLine>? = null
    }

    private var built: Built? = null
    private val drawPaint = TextPaint()

    /** Drops the cached layout, e.g. after an image the loader resolves has changed. */
    fun invalidate() {
        built = null
    }

    /** The styled text that is laid out; run offsets index into it. */
    val text: CharSequence get() = built().text

    /** Height in px of the laid-out content. */
    val height: Int get() = built().layout.height

    internal val staticLayout: StaticLayout get() = built().layout

    /** Shapes drawn under the text, in drawing order. */
    val decorations: List<MarkdownDecoration>
        get() = built().let { b -> b.decorations ?: computeDecorations(b).also { b.decorations = it } }

    /** Every laid-out line with its styled text runs. */
    val lines: List<MarkdownLine>
        get() = built().let { b -> b.lines ?: computeLines(b).also { b.lines = it } }

    /** Draws at the canvas origin: decorations first, then the text. */
    fun draw(canvas: Canvas) {
        val b = built()
        for (d in decorations) d.draw(canvas, drawPaint)
        b.layout.draw(canvas)
    }

    private fun built(): Built = built ?: build().also { built = it }

    private fun build(): Built {
        val metrics = MarkdownMetrics(theme, density)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = metrics.em
            color = theme.textColor
            typeface = Typeface.DEFAULT
            // Unhinted, subpixel-positioned glyphs keep line breaks the same at any zoom and in
            // PDF, where text is never hinted.
            hinting = Paint.HINTING_OFF
            isSubpixelText = true
        }
        val width = max(1, widthPx)
        val text = MarkdownSpanner(theme, metrics, width, imageLoader, paint).build(source)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.LTR)
            .setLineSpacing(metrics.lineGap.toFloat(), 1f)
            .setIncludePad(false)
            .setUseLineSpacingFromFallbacks(true)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
        return Built(text, layout, paint, metrics)
    }

    private fun computeLines(b: Built): List<MarkdownLine> {
        val text = b.text
        val layout = b.layout
        val runPaint = TextPaint()
        return (0 until layout.lineCount).map { line ->
            val lineStart = layout.getLineStart(line)
            var end = layout.getLineEnd(line)
            while (end > lineStart && text[end - 1].isWhitespace()) end--
            val runs = ArrayList<TextRun>()
            var i = lineStart
            while (i < end) {
                val next = text.nextSpanTransition(i, end, CharacterStyle::class.java)
                if (text.getSpans(i, next, BoxSpan::class.java).isEmpty()) {
                    styledPaint(b, i, next, runPaint)
                    val style = runStyle(runPaint, text.getSpans(i, next, CodeSpan::class.java).isNotEmpty())
                    val link = text.getSpans(i, next, LinkSpan::class.java).lastOrNull()?.url
                    val x = layout.getPrimaryHorizontal(i)
                    val width = runPaint.advance(text, i, next)
                    val segment = text.subSequence(i, next).toString()
                    val prev = runs.lastOrNull()
                    if (prev != null && prev.style == style && prev.link == link &&
                        prev.start + prev.text.length == i
                    ) {
                        runs[runs.lastIndex] = prev.copy(text = prev.text + segment, width = x + width - prev.x)
                    } else {
                        runs += TextRun(segment, i, x, width, style, link)
                    }
                }
                i = next
            }
            MarkdownLine(
                index = line,
                top = layout.getLineTop(line).toFloat(),
                baseline = layout.getLineBaseline(line).toFloat(),
                bottom = layout.getLineBottom(line).toFloat(),
                left = layout.getParagraphLeft(line).toFloat(),
                runs = runs,
            )
        }
    }

    /** Applies the spans over [start, end) the way TextLine does: metric spans, then the rest. */
    private fun styledPaint(b: Built, start: Int, end: Int, out: TextPaint) {
        out.set(b.paint)
        val spans = b.text.getSpans(start, end, CharacterStyle::class.java)
        for (s in spans) if (s is MetricAffectingSpan && s !is ReplacementSpan) s.updateDrawState(out)
        for (s in spans) if (s !is MetricAffectingSpan) s.updateDrawState(out)
    }

    private fun runStyle(paint: TextPaint, monospace: Boolean): RunStyle {
        val typeface = paint.typeface ?: Typeface.DEFAULT
        return RunStyle(
            textSize = paint.textSize,
            weight = if (paint.isFakeBoldText) max(700, typeface.weight) else typeface.weight,
            italic = typeface.isItalic || paint.textSkewX != 0f,
            monospace = monospace,
            color = paint.color,
            strikethrough = paint.isStrikeThruText,
            underline = paint.isUnderlineText,
        )
    }

    private fun computeDecorations(b: Built): List<MarkdownDecoration> {
        val text = b.text
        val layout = b.layout
        val m = b.metrics
        val em = m.em
        val out = ArrayList<MarkdownDecoration>()

        fun contentBottom(line: Int): Float =
            (layout.getLineBottom(line) - if (line < layout.lineCount - 1) m.lineGap else 0).toFloat()

        fun marginX(line: Int, span: BlockMarginSpan): Float =
            text.getSpans(layout.getLineStart(line), layout.getLineEnd(line), BlockMarginSpan::class.java)
                .filter { it.depth < span.depth }
                .sumOf { it.marginPx }
                .toFloat()

        fun lastLine(span: Any): Int =
            layout.getLineForOffset(max(text.getSpanStart(span), text.getSpanEnd(span) - 1))

        for (span in text.getSpans(0, text.length, CodeBlockSpan::class.java)) {
            val first = layout.getLineForOffset(text.getSpanStart(span))
            val x = marginX(first, span)
            out += MarkdownDecoration.Box(
                x, layout.getLineTop(first).toFloat(), layout.width.toFloat(),
                contentBottom(lastLine(span)), theme.codeBackground, em * 0.3f,
            )
        }

        for (span in text.getSpans(0, text.length, BlockQuoteSpan::class.java)) {
            val first = layout.getLineForOffset(text.getSpanStart(span))
            val x = marginX(first, span)
            out += MarkdownDecoration.Box(
                x, layout.getLineTop(first).toFloat(), x + m.quoteBar,
                contentBottom(lastLine(span)), theme.quoteBarColor, m.quoteBar / 2f,
            )
        }

        val codePaint = TextPaint()
        for (span in text.getSpans(0, text.length, CodeSpan::class.java)) {
            if (span.block) continue
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            for (line in layout.getLineForOffset(start)..lastLine(span)) {
                val segStart = max(start, layout.getLineStart(line))
                var segEnd = minOf(end, layout.getLineEnd(line))
                while (segEnd > segStart && segEnd == layout.getLineEnd(line) && text[segEnd - 1].isWhitespace()) segEnd--
                if (segEnd <= segStart) continue
                styledPaint(b, segStart, segEnd, codePaint)
                val x = layout.getPrimaryHorizontal(segStart)
                val w = codePaint.advance(text, segStart, segEnd)
                val baseline = layout.getLineBaseline(line).toFloat()
                val fm = codePaint.fontMetrics
                val pad = codePaint.textSize * 0.2f
                out += MarkdownDecoration.Box(
                    x - pad, baseline + fm.ascent, x + w + pad, baseline + fm.descent,
                    theme.codeBackground, pad,
                )
            }
        }

        for (span in text.getSpans(0, text.length, RuleSpan::class.java)) {
            val start = text.getSpanStart(span)
            val line = layout.getLineForOffset(start)
            val x = layout.getPrimaryHorizontal(start)
            val cy = layout.getLineBaseline(line) - span.heightPx / 2f
            val half = max(0.5f, em * 0.05f)
            out += MarkdownDecoration.Box(x, cy - half, x + span.widthPx, cy + half, theme.quoteBarColor)
        }

        val xBounds = Rect().also { b.paint.getTextBounds("x", 0, 1, it) }
        for (span in text.getSpans(0, text.length, ListItemSpan::class.java)) {
            val start = text.getSpanStart(span)
            val line = layout.getLineForOffset(start)
            val right = marginX(line, span) + span.marginPx - m.markerGap
            val baseline = layout.getLineBaseline(line).toFloat()
            val quoted = text.getSpans(start, start + 1, BlockQuoteSpan::class.java).isNotEmpty()
            val color = if (quoted) theme.mutedColor else theme.textColor
            when (val marker = span.marker) {
                is ListMarker.Bullet -> {
                    val r = em * 0.15f
                    val cx = right - r * 1.5f
                    val cy = baseline + xBounds.top / 2f
                    out += when (marker.level % 3) {
                        0 -> MarkdownDecoration.Circle(cx, cy, r, color)
                        1 -> MarkdownDecoration.Circle(cx, cy, r - em * 0.035f, color, em * 0.07f)
                        else -> MarkdownDecoration.Box(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f, color)
                    }
                }
                is ListMarker.Number -> {
                    val w = b.paint.measureText(marker.label)
                    out += MarkdownDecoration.Label(marker.label, right - w, baseline, em, color)
                }
                is ListMarker.Task -> {
                    val side = em * 0.8f
                    val cy = baseline - em * 0.36f
                    val left = right - side
                    val top = cy - side / 2
                    val radius = em * 0.15f
                    if (marker.checked) {
                        out += MarkdownDecoration.Box(left, top, right, top + side, theme.linkColor, radius)
                        out += MarkdownDecoration.Polyline(
                            listOf(
                                left + side * 0.24f, top + side * 0.52f,
                                left + side * 0.43f, top + side * 0.71f,
                                left + side * 0.77f, top + side * 0.31f,
                            ),
                            theme.background, em * 0.11f,
                        )
                    } else {
                        val stroke = em * 0.08f
                        out += MarkdownDecoration.Outline(
                            left + stroke / 2, top + stroke / 2, right - stroke / 2, top + side - stroke / 2,
                            theme.mutedColor, stroke, radius,
                        )
                    }
                }
            }
        }

        val labelPaint = TextPaint(b.paint).apply { color = theme.mutedColor }
        for (span in text.getSpans(0, text.length, ImageSpan::class.java)) {
            val start = text.getSpanStart(span)
            val line = layout.getLineForOffset(start)
            val left = layout.getPrimaryHorizontal(start)
            val bottom = layout.getLineBaseline(line).toFloat()
            val top = bottom - span.heightPx
            val right = left + span.widthPx
            val bitmap = span.bitmap
            if (bitmap != null) {
                out += MarkdownDecoration.Image(span.path, bitmap, left, top, right, bottom)
            } else {
                val stroke = max(1f, em * 0.06f)
                out += MarkdownDecoration.Box(left, top, right, bottom, theme.codeBackground, em * 0.3f)
                out += MarkdownDecoration.Outline(
                    left + stroke / 2, top + stroke / 2, right - stroke / 2, bottom - stroke / 2,
                    theme.quoteBarColor, stroke, em * 0.3f,
                )
                val label = TextUtils.ellipsize(
                    span.alt.ifBlank { span.path }, labelPaint, max(0f, span.widthPx - em * 2), TextUtils.TruncateAt.END,
                ).toString()
                if (label.isNotEmpty()) {
                    val w = labelPaint.measureText(label)
                    val baseline = (top + bottom) / 2 - (labelPaint.ascent() + labelPaint.descent()) / 2
                    out += MarkdownDecoration.Label(label, left + (span.widthPx - w) / 2, baseline, em, theme.mutedColor)
                }
            }
        }
        return out
    }
}

/** Unrounded width; `measureText` rounds up to whole pixels. */
private fun TextPaint.advance(text: CharSequence, start: Int, end: Int): Float =
    getRunAdvance(text, start, end, start, end, false, end)
