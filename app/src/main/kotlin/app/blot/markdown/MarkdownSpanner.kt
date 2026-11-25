package app.blot.markdown

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.Link
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import kotlin.math.max
import kotlin.math.roundToInt

/** Sizes in px derived from the theme's base size; shared by the spanner and the layout. */
internal class MarkdownMetrics(theme: MarkdownTheme, density: Float) {
    val em = theme.baseTextSize * density
    val lineGap = (em * 0.25f).roundToInt()
    val blockGap = (em * 0.6f).roundToInt()
    val quoteBar = max(1, (em * 0.2f).roundToInt())
    val quoteMargin = (em * 0.9f).roundToInt()
    val listIndent = (em * 1.6f).roundToInt()
    val markerGap = em * 0.45f
    val codePadding = (em * 0.6f).roundToInt()
    val ruleHeight = (em * 1.0f).roundToInt()
    val placeholderHeight = (em * 4f).roundToInt()

    fun headingSize(level: Int) = em * HEADING_SCALE[(level - 1).coerceIn(0, 5)]

    companion object {
        private val HEADING_SCALE = floatArrayOf(1.6f, 1.4f, 1.25f, 1.1f, 1.0f, 0.9f)
    }
}

/** Converts a commonmark AST to styled text for [android.text.StaticLayout]. */
internal class MarkdownSpanner(
    private val theme: MarkdownTheme,
    private val metrics: MarkdownMetrics,
    private val widthPx: Int,
    private val imageLoader: MarkdownImageLoader,
    private val basePaint: TextPaint,
) {
    private class Pending(val start: Int, val end: Int, val span: Any, val depth: Int)

    private val sb = SpannableStringBuilder()
    private val pending = ArrayList<Pending>()
    private var depth = 0
    private var margin = 0
    private var bulletLevel = 0

    fun build(source: String): Spanned {
        val document = PARSER.parse(source)
        blocks(document, spaced = true)
        // Outer spans first: a span added later wins for colour (a link inside a quote), and
        // SpannedString keeps insertion order for every reader of the text.
        pending.sortedBy { it.depth }.forEach {
            if (it.end > it.start) sb.setSpan(it.span, it.start, it.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (sb.endsWith('\n')) sb.delete(sb.length - 1, sb.length)
        return SpannedString(sb)
    }

    private fun span(start: Int, span: Any) {
        pending += Pending(start, sb.length, span, depth)
    }

    private fun newline() {
        if (sb.isNotEmpty() && !sb.endsWith('\n')) sb.append('\n')
    }

    private fun blocks(parent: Node, spaced: Boolean) {
        var first = true
        var child = parent.firstChild
        while (child != null) {
            if (child !is TaskListItemMarker && child !is LinkReferenceDefinition) {
                if (!first && spaced) spacer()
                block(child)
                first = false
            }
            child = child.next
        }
    }

    private fun spacer() {
        newline()
        val start = sb.length
        sb.append('\n')
        span(start, SpacerSpan(metrics.blockGap))
    }

    private fun block(node: Node) {
        depth++
        val start = sb.length
        when (node) {
            is Paragraph -> inlines(node)
            is Heading -> {
                inlines(node)
                newline()
                span(start, HeadingSpan(node.level, metrics.headingSize(node.level)))
                span(start, StyleSpan(Typeface.BOLD))
            }
            is BlockQuote -> {
                margin += metrics.quoteMargin
                blocks(node, spaced = true)
                margin -= metrics.quoteMargin
                newline()
                span(start, BlockQuoteSpan(depth, metrics.quoteMargin))
                span(start, ForegroundColorSpan(theme.mutedColor))
            }
            is ListBlock -> list(node)
            is FencedCodeBlock -> codeBlock(node.literal)
            is IndentedCodeBlock -> codeBlock(node.literal)
            is ThematicBreak -> {
                sb.append(OBJECT)
                span(start, RuleSpan(availableWidth(), metrics.ruleHeight))
            }
            is HtmlBlock -> sb.append(node.literal.trimEnd('\n'))
            else -> inlines(node)
        }
        newline()
        depth--
    }

    private fun list(list: ListBlock) {
        val ordered = list as? OrderedList
        val firstNumber = ordered?.markerStartNumber ?: 1
        val delimiter = ordered?.markerDelimiter ?: "."
        val itemCount = generateSequence(list.firstChild) { it.next }.count { it is ListItem }
        val indent = if (ordered == null) {
            metrics.listIndent
        } else {
            val widest = basePaint.measureText("${firstNumber + itemCount - 1}$delimiter")
            max(metrics.listIndent, (widest + metrics.markerGap * 2).roundToInt())
        }
        if (ordered == null) bulletLevel++
        var number = firstNumber
        var item = list.firstChild
        var first = true
        while (item != null) {
            if (item is ListItem) {
                if (!first && !list.isTight) spacer()
                first = false
                depth++
                val start = sb.length
                margin += indent
                blocks(item, spaced = !list.isTight)
                margin -= indent
                newline()
                if (sb.length == start) sb.append('\n')
                val task = item.firstChild as? TaskListItemMarker
                val marker = when {
                    task != null -> ListMarker.Task(task.isChecked)
                    ordered != null -> ListMarker.Number("$number$delimiter")
                    else -> ListMarker.Bullet(bulletLevel - 1)
                }
                span(start, ListItemSpan(depth, indent, marker))
                depth--
                number++
            }
            item = item.next
        }
        if (ordered == null) bulletLevel--
    }

    private fun codeBlock(literal: String) {
        val start = sb.length
        val code = literal.removeSuffix("\n").replace("\t", "    ")
        sb.append(code.ifEmpty { " " })
        newline()
        span(start, CodeBlockSpan(depth, metrics.codePadding))
        span(start, CodeSpan(block = true))
    }

    private fun inlines(parent: Node) {
        var child = parent.firstChild
        while (child != null) {
            inline(child)
            child = child.next
        }
    }

    private fun inline(node: Node) {
        depth++
        val start = sb.length
        when (node) {
            is Text -> sb.append(node.literal)
            is Code -> {
                sb.append(node.literal)
                span(start, CodeSpan(block = false))
            }
            is Emphasis -> {
                inlines(node)
                span(start, StyleSpan(Typeface.ITALIC))
            }
            is StrongEmphasis -> {
                inlines(node)
                span(start, StyleSpan(Typeface.BOLD))
            }
            is Strikethrough -> {
                inlines(node)
                span(start, StrikethroughSpan())
            }
            is Link -> {
                inlines(node)
                span(start, LinkSpan(node.destination, theme.linkColor))
            }
            is Image -> image(node)
            is SoftLineBreak -> sb.append(' ')
            is HardLineBreak -> sb.append('\n')
            is HtmlInline -> sb.append(node.literal)
            else -> inlines(node)
        }
        depth--
    }

    private fun image(node: Image) {
        val start = sb.length
        val alt = textContent(node)
        val bitmap = imageLoader.load(node.destination)
        val width = availableWidth()
        val height = if (bitmap != null && bitmap.width > 0) {
            max(1, (width.toLong() * bitmap.height / bitmap.width).toInt())
        } else {
            metrics.placeholderHeight
        }
        sb.append(OBJECT)
        span(start, ImageSpan(node.destination, alt, bitmap, width, height))
    }

    private fun availableWidth() = max(1, widthPx - margin)

    private fun textContent(node: Node): String = buildString {
        var child = node.firstChild
        while (child != null) {
            when (child) {
                is Text -> append(child.literal)
                is Code -> append(child.literal)
                is SoftLineBreak, is HardLineBreak -> append(' ')
                else -> append(textContent(child))
            }
            child = child.next
        }
    }

    companion object {
        const val OBJECT = '￼'

        /** commonmark parsers are thread-safe and reusable. */
        val PARSER: Parser = Parser.builder()
            .extensions(listOf(StrikethroughExtension.create(), TaskListItemsExtension.create()))
            .build()
    }
}
