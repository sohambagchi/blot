package app.blot.markdown

import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `$…$` and `$$…$$` syntax, parsed by [MathExtension] into [MathInline] and [MathBlock]. */
class MathExtensionTest {
    private fun parse(md: String): Node = MarkdownSpanner.PARSER.parse(md)

    private fun Node.all(): List<Node> = buildList {
        var c = firstChild
        while (c != null) {
            add(c)
            addAll(c.all())
            c = c.next
        }
    }

    private fun inlines(md: String) = parse(md).all().filterIsInstance<MathInline>()
    private fun blocks(md: String) = parse(md).all().filterIsInstance<MathBlock>()
    private fun text(md: String) = parse(md).all().filterIsInstance<Text>().joinToString("") { it.literal }

    @Test
    fun inlineMaths() {
        val m = inlines("Area \$\\pi r^2\$ here").single()
        assertEquals("\\pi r^2", m.latex)
        assertEquals(false, m.display)
        assertEquals("Area  here", text("Area \$\\pi r^2\$ here"))
        assertEquals(listOf("a", "b"), inlines("\$a\$ and \$b\$").map { it.latex })
    }

    @Test
    fun doubleDollarsInAParagraphAreDisplayMaths() {
        val m = inlines("Then $$ x = 1 $$ follows.").single()
        assertEquals("x = 1", m.latex)
        assertTrue(m.display)
        assertTrue(blocks("Then $$ x = 1 $$ follows.").isEmpty())
    }

    @Test
    fun dollarAmountsStayText() {
        for (md in listOf("It costs \$5 and \$6.", "\$ x\$", "\$x \$", "\$x\$5", "Pay \$10, get \$20 back", "a \$ b")) {
            assertTrue(md, inlines(md).isEmpty() && blocks(md).isEmpty())
        }
        assertEquals("It costs \$5 and \$6.", text("It costs \$5 and \$6."))
    }

    @Test
    fun loneDoubleDollarStartsAnEmptyBlock() {
        assertTrue(inlines("$$").isEmpty())
        assertEquals("", blocks("$$").single().latex)
    }

    @Test
    fun closingDollarFollowedByADigitIsSkipped() {
        assertEquals("a\$1 + b", inlines("\$a\$1 + b\$ end").single().latex)
    }

    @Test
    fun escapedDollarsAreText() {
        assertTrue(inlines("\\\$x\\\$").isEmpty())
        assertEquals("\$x\$", text("\\\$x\\\$"))
        assertEquals("a \\\$ b", inlines("\$a \\\$ b\$").single().latex)
    }

    @Test
    fun codeNeverHoldsMaths() {
        val doc = parse("`\$x\$` and\n\n```\n\$\$\ny\n\$\$\n```")
        assertTrue(doc.all().none { it is MathInline || it is MathBlock })
        assertEquals("\$x\$", doc.all().filterIsInstance<Code>().single().literal)
        assertEquals("\$\$\ny\n\$\$\n", doc.all().filterIsInstance<FencedCodeBlock>().single().literal)
    }

    @Test
    fun inlineMathsSpansSoftLineBreaks() {
        assertEquals("a +\nb", inlines("\$a +\nb\$").single().latex)
    }

    @Test
    fun displayBlocks() {
        assertEquals("x^2", blocks("$$ x^2 $$").single().latex)
        assertEquals("a \\\\\nb", blocks("$$\na \\\\\nb\n$$").single().latex)
        // Blank lines don't end the block, and markdown inside it stays LaTeX.
        assertEquals("a\n\n- b", blocks("$$\na\n\n- b\n$$\n\nafter").single().latex)
        // The closing $$ may end a line of LaTeX; text after the block is a new paragraph.
        val doc = parse("$$\n\\sum_k k\n= n$$\nnext")
        assertEquals("\\sum_k k\n= n", doc.all().filterIsInstance<MathBlock>().single().latex)
        assertTrue(doc.all().any { it is Paragraph })
    }

    @Test
    fun displayBlockInterruptsAParagraph() {
        val doc = parse("Text\n$$\nx\n$$")
        assertEquals("x", doc.all().filterIsInstance<MathBlock>().single().latex)
        assertEquals("Text", text("Text\n$$\nx\n$$"))
    }
}
