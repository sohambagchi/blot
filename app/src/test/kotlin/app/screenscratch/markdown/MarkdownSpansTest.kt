package app.screenscratch.markdown

import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownSpansTest {
    private fun spanned(md: String, width: Int = 600) = MarkdownLayout(md, width).text as Spanned

    private inline fun <reified T : Any> Spanned.spansOf(): List<T> = getSpans(0, length, T::class.java).toList()

    private fun Spanned.covered(span: Any) = subSequence(getSpanStart(span), getSpanEnd(span)).toString().trimEnd('\n')

    @Test
    fun headingsGetLevelSizeAndBold() {
        val text = spanned("# One\n\n### Three")
        val headings = text.spansOf<HeadingSpan>()
        assertEquals(listOf(1, 3), headings.map { it.level })
        assertEquals("One", text.covered(headings[0]))
        assertEquals("Three", text.covered(headings[1]))
        assertEquals(2, text.spansOf<StyleSpan>().count { it.style == Typeface.BOLD })
        val sizes = MarkdownLayout("# One\n\n### Three\n\nbody", 600).lines
            .filter { it.runs.isNotEmpty() }.map { it.runs[0].style.textSize }
        assertTrue("h1 > h3 > body: $sizes", sizes[0] > sizes[1] && sizes[1] > sizes[2])
    }

    @Test
    fun inlineStyles() {
        val text = spanned("*it* **bo** ~~st~~ `co` [li](https://x.test)")
        assertEquals("it bo st co li", text.toString())
        val styles = text.spansOf<StyleSpan>()
        assertEquals("it", text.covered(styles.single { it.style == Typeface.ITALIC }))
        assertEquals("bo", text.covered(styles.single { it.style == Typeface.BOLD }))
        assertEquals("st", text.covered(text.spansOf<StrikethroughSpan>().single()))
        val code = text.spansOf<CodeSpan>().single()
        assertFalse(code.block)
        assertEquals("co", text.covered(code))
        val link = text.spansOf<LinkSpan>().single()
        assertEquals("li", text.covered(link))
        assertEquals("https://x.test", link.url)
    }

    @Test
    fun fencedAndIndentedCodeBlocks() {
        val text = spanned("```kotlin\nval a = 1\n\tb()\n```\n\n    indented")
        val blocks = text.spansOf<CodeBlockSpan>()
        assertEquals(2, blocks.size)
        assertEquals("val a = 1\n    b()", text.covered(blocks[0]))
        assertEquals("indented", text.covered(blocks[1]))
        assertTrue(text.spansOf<CodeSpan>().all { it.block })
        val layout = MarkdownLayout("```\nx\n```", 600)
        val bg = layout.decorations.filterIsInstance<MarkdownDecoration.Box>().single()
        assertEquals(0f, bg.left)
        assertEquals(600f, bg.right)
        assertTrue(layout.lines[0].left > 0f)
    }

    @Test
    fun blockQuoteIsIndentedAndMuted() {
        val text = spanned("> quoted\n> more\n\nplain")
        val quote = text.spansOf<BlockQuoteSpan>().single()
        assertEquals("quoted more", text.covered(quote))
        val muted = text.spansOf<ForegroundColorSpan>().single()
        assertEquals(MarkdownTheme.Light.mutedColor, muted.foregroundColor)
        val lines = MarkdownLayout("> quoted\n\nplain", 600).lines.filter { it.runs.isNotEmpty() }
        assertTrue(lines[0].left > lines[1].left)
    }

    @Test
    fun linkInsideQuoteKeepsLinkColour() {
        val run = MarkdownLayout("> see [here](u)", 600).lines[0].runs.single { it.link != null }
        assertEquals(MarkdownTheme.Light.linkColor, run.style.color)
        assertTrue(run.style.underline)
    }

    @Test
    fun listMarkers() {
        val text = spanned("- a\n- b\n\n3) c\n4) d\n\n- [ ] todo\n- [x] done")
        val markers = text.spansOf<ListItemSpan>().map { it.marker }
        assertEquals(
            listOf(
                ListMarker.Bullet(0), ListMarker.Bullet(0),
                ListMarker.Number("3)"), ListMarker.Number("4)"),
                ListMarker.Task(false), ListMarker.Task(true),
            ),
            markers,
        )
        assertTrue(text.toString().contains("todo\ndone"))
        val decorations = MarkdownLayout("- [ ] todo\n- [x] done", 600).decorations
        assertEquals(1, decorations.filterIsInstance<MarkdownDecoration.Outline>().size)
        assertEquals(1, decorations.filterIsInstance<MarkdownDecoration.Polyline>().size)
    }

    @Test
    fun nestedListsIndentByLevel() {
        val layout = MarkdownLayout("- one\n  - two\n    - three\n- four", 600, density = 2f)
        val lefts = layout.lines.map { it.left }
        val indent = MarkdownMetrics(MarkdownTheme.Light, 2f).listIndent.toFloat()
        assertEquals(listOf(indent, indent * 2, indent * 3, indent), lefts)
        val levels = (layout.text as Spanned).spansOf<ListItemSpan>().map { (it.marker as ListMarker.Bullet).level }
        assertEquals(listOf(0, 0, 1, 2), levels.sorted())
        val bullets = layout.decorations.filter { it is MarkdownDecoration.Circle || it is MarkdownDecoration.Box }
        assertEquals(4, bullets.size)
    }

    @Test
    fun orderedListIndentFitsWidestNumber() {
        val md = (1..12).joinToString("\n") { "$it. item" }
        val layout = MarkdownLayout(md, 600)
        val lefts = layout.lines.map { it.left }.toSet()
        assertEquals(1, lefts.size)
        val label = layout.decorations.filterIsInstance<MarkdownDecoration.Label>().last()
        assertEquals("12.", label.text)
        assertTrue(label.x >= 0f)
    }

    @Test
    fun thematicBreakAndLineBreaks() {
        val text = spanned("soft\nwrap\n\n---\n\nhard  \nbreak")
        assertEquals(1, text.spansOf<RuleSpan>().size)
        assertTrue(text.toString().startsWith("soft wrap\n"))
        assertTrue(text.toString().endsWith("hard\nbreak"))
    }

    @Test
    fun paragraphsAreSeparatedByASpacer() {
        assertEquals(1, spanned("one\n\ntwo").spansOf<SpacerSpan>().size)
        assertEquals(0, spanned("- a\n- b").spansOf<SpacerSpan>().size)
        assertEquals(1, spanned("- a\n\n- b").spansOf<SpacerSpan>().size)
    }

    @Test
    fun missingImageDrawsPlaceholderWithAlt() {
        val layout = MarkdownLayout("![a cat](assets/ab12.png)", 300) { null }
        val span = (layout.text as Spanned).spansOf<ImageSpan>().single()
        assertNull(span.bitmap)
        assertEquals("assets/ab12.png", span.path)
        val label = layout.decorations.filterIsInstance<MarkdownDecoration.Label>().single()
        assertEquals("a cat", label.text)
        assertTrue(layout.decorations.none { it is MarkdownDecoration.Image })
    }

    @Test
    fun resolvedImageScalesToBoxWidth() {
        val requested = ArrayList<String>()
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val layout = MarkdownLayout("- ![](assets/x.png)", 400) { requested += it; bitmap }
        val image = layout.decorations.filterIsInstance<MarkdownDecoration.Image>().single()
        val indent = layout.lines[0].left
        assertEquals(listOf("assets/x.png"), requested)
        assertEquals(400f - indent, image.right - image.left, 0.5f)
        assertEquals((400f - indent) / 2, image.bottom - image.top, 1f)
        assertTrue(layout.height >= (image.bottom - image.top).toInt())
    }

    @Test
    fun heightGrowsWithContent() {
        val one = MarkdownLayout("line", 300).height
        val two = MarkdownLayout("line\n\nline", 300).height
        val wrapped = MarkdownLayout("word ".repeat(80), 300).height
        assertTrue(one > 0)
        assertTrue(two > one)
        assertTrue(wrapped > two)
    }

    @Test
    fun layoutIsCachedUntilAnInputChanges() {
        val layout = MarkdownLayout("hello", 300)
        val first = layout.staticLayout
        layout.source = "hello"
        layout.widthPx = 300
        layout.theme = MarkdownTheme.Light
        assertSame(first, layout.staticLayout)
        layout.theme = MarkdownTheme.Dark
        assertNotSame(first, layout.staticLayout)
        val second = layout.staticLayout
        layout.widthPx = 200
        assertNotSame(second, layout.staticLayout)
    }

    @Test
    fun mixedLineReportsRunStyles() {
        val layout = MarkdownLayout("plain **bold** *ital* `code` ~~gone~~ [link](https://x.test)", 2000)
        val runs = layout.lines.single().runs.associateBy { it.text.trim() }
        val base = runs.getValue("plain").style
        assertEquals(400, base.weight)
        assertFalse(base.italic || base.monospace || base.strikethrough || base.underline)
        assertEquals(MarkdownTheme.Light.textColor, base.color)
        assertTrue(runs.getValue("bold").style.bold)
        assertTrue(runs.getValue("ital").style.italic)
        val code = runs.getValue("code").style
        assertTrue(code.monospace)
        assertEquals(base.textSize * CodeSpan.CODE_SCALE, code.textSize, 0.01f)
        assertTrue(runs.getValue("gone").style.strikethrough)
        val link = runs.getValue("link")
        assertTrue(link.style.underline)
        assertEquals(MarkdownTheme.Light.linkColor, link.style.color)
        assertEquals("https://x.test", link.link)
        val ordered = layout.lines.single().runs
        assertTrue(ordered.joinToString { "${it.text}@${it.x}+${it.width}" }, ordered.zipWithNext().all { (a, b) -> a.x + a.width <= b.x + 0.5f })
        assertEquals(layout.text.toString(), ordered.joinToString("") { it.text })
    }
}
