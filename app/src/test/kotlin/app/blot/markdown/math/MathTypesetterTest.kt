package app.blot.markdown.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The ported typesetter on the bundled font: box sizes and positions are plausible. */
@RunWith(RobolectricTestRunner::class)
class MathTypesetterTest {
    private val size = 20f
    private val font = MathFontFile.bundled

    private fun display(latex: String) = MathTypesetter.display(latex, size)
    private fun height(b: MathBox) = b.ascent + b.descent

    /** The top-level displays of [latex] in display style. */
    private fun parts(latex: String): List<MTDisplay> {
        val list = MTMathListBuilder.buildFromString(latex)!!
        return MTTypesetter.createLineWithBreaks(list, MTFont(font, size), MTLineStyle.KMTLineStyleDisplay).first.subDisplays!!
    }

    @Test
    fun fontHasTheMathTableAndAlphabets() {
        assertEquals(1000, font.unitsPerEm)
        assertEquals(250, font.math.constant("AxisHeight"))
        assertEquals(70, font.math.constant("ScriptPercentScaleDown"))
        for (cp in listOf('x'.code, 0x1D465, 0x211D, 0x1D53B, 0x2112, 0x1D4AA, 0x03B1, 0x2211, 0x222B, 0x221A, 0x2192)) {
            val g = font.glyph(cp)
            assertNotEquals("U+%04X".format(cp), 0, g)
            assertTrue("U+%04X has an outline".format(cp), font.outline(g).verbs.isNotEmpty())
            assertTrue(font.advance(g) > 0)
        }
        // The radical has larger variants and an assembly, kept by the subsetter.
        val radical = font.glyph(0x221A)
        assertTrue(font.math.verticalVariants(radical).size > 3)
        assertTrue(font.math.verticalAssembly(radical)!!.isNotEmpty())
    }

    @Test
    fun everySymbolTheParserKnowsIsInTheFont() {
        val missing = MTMathAtom.supportedLatexSymbolNames().filter { name ->
            val nucleus = MTMathAtom.atomForLatexSymbolName(name)!!.nucleus
            nucleus.codePoints().anyMatch { font.glyph(it) == 0 }
        }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun fractionIsTallerThanASymbol() {
        val x = display("x")
        val frac = display("\\frac{a}{b}")
        assertTrue(height(frac) > 2 * height(x))
        assertTrue(frac.descent > x.descent)
        // The bar sits on the maths axis: as much fraction above it as below.
        val axis = font.math.constant("AxisHeight") * size / 1000
        assertEquals(frac.ascent - axis, frac.descent + axis, size * 0.25f)
    }

    @Test
    fun scriptsAreRaisedAndLowered() {
        val (base, sup) = parts("x^2")
        assertEquals(0f, base.position.y, 0.01f)
        assertTrue(sup.position.y > size * 0.2f)
        assertTrue(sup.position.x >= base.width)
        val sub = parts("x_i")[1]
        assertTrue(sub.position.y < 0f)
        val both = parts("x_i^2")
        assertTrue(both[1].position.y > 0f && both[2].position.y < 0f)
        // Scripts are smaller than the base.
        assertTrue(display("x^{xx}").width - display("x").width < display("xx").width)
    }

    @Test
    fun matrixRowsStack() {
        val one = display("\\begin{pmatrix} a \\end{pmatrix}")
        val three = display("\\begin{pmatrix} a \\\\ b \\\\ c \\end{pmatrix}")
        assertTrue(height(three) > 2.5f * height(display("a")))
        assertTrue(height(three) > height(one) * 1.8f)
        val wide = display("\\begin{bmatrix} a & b & c \\end{bmatrix}")
        assertTrue(wide.width > display("\\begin{bmatrix} a \\end{bmatrix}").width + 2 * display("a").width)
    }

    @Test
    fun bigOperatorsTakeLimitsInDisplayStyle() {
        val sum = display("\\sum_{i=1}^{n} i")
        val bare = display("\\sum i")
        assertTrue(height(sum) > height(bare) * 1.4f)
        val inline = MathTypesetter.inline("\\sum_{i=1}^{n} i", size).single()
        assertTrue(height(inline) < height(sum))
        // Display style picks the larger glyph.
        assertTrue(height(bare) > height(MathTypesetter.inline("\\sum i", size).single()))
        for (op in listOf("\\int_0^1 x", "\\prod_{k} a_k", "\\lim_{x \\to 0} f")) assertTrue(op, height(display(op)) > height(display("x")))
    }

    @Test
    fun radicalsAndDelimitersGrowWithTheirContent() {
        assertTrue(display("\\sqrt{x}").width > display("x").width)
        assertTrue(height(display("\\sqrt{\\frac{a}{b}}")) > height(display("\\sqrt{x}")))
        val tall = display("\\left( \\frac{\\frac{a}{b}}{c} \\right)")
        assertTrue(height(tall) >= height(display("\\frac{\\frac{a}{b}}{c}")))
    }

    @Test
    fun inlineMathsBreaksAfterOperatorsAndRelations() {
        val pieces = MathTypesetter.inline("a + b = c", size)
        assertEquals(3, pieces.size)
        assertEquals(MathTypesetter.inline("a+b=c", size).sumOf { it.width.toDouble() }, pieces.sumOf { it.width.toDouble() }, 0.01)
        assertEquals(1, MathTypesetter.inline("\\frac{a+b}{c}", size).size)
        assertEquals(1, MathTypesetter.inline("x", size).size)
    }

    @Test
    fun accentsSitAboveTheirBase() {
        for (a in listOf("hat", "bar", "vec", "dot", "tilde")) {
            assertTrue(a, display("\\$a{x}").ascent > display("x").ascent)
        }
    }

    @Test
    fun primesAndUnicodeAreTypeset() {
        assertTrue(display("f'").width > display("f").width)
        assertTrue(display("α≤β").width > display("α").width * 2)
    }

    @Test
    fun invalidLatexThrows() {
        for (bad in listOf("\\frac{1}{", "\\left( x", "\\nosuchcommand", "x}", "\\begin{pmatrix} a")) {
            assertThrows(bad, MathException::class.java) { display(bad) }
        }
    }

    @Test
    fun oddInputTypesetsOrFailsCleanly() {
        val odd = listOf(
            "\\sqrt", "^", "_", "x^", "\\frac", "\\frac{a}", "\\left", "\\right)", "\\begin{matrix}", "{", "}",
            "\\text", "a^^b", "\\\\", "&", "a & b", "\\begin{pmatrix} a & b \\\\ c \\end{pmatrix}", "\\left(\\right.",
            "\\color{red}{x}", "\\over", "x \\over", "\\mathbb", "\\hat", "''", "\\sum\\limits", "\\limits", "\\end{matrix}",
            "\\begin{nosuch} a \\end{nosuch}", "\\operatorname{x}", "\\displaystyle", "\$", "%", "#", "~", "\u0000", "😀",
        )
        for (latex in odd) {
            for (f in listOf<() -> Any>({ display(latex) }, { MathTypesetter.inline(latex, size) })) {
                try {
                    f()
                } catch (e: MathException) {
                    assertTrue(latex, e.message!!.isNotEmpty())
                }
            }
        }
    }

    @Test
    fun pathMatchesTheBox() {
        val b = display("\\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}")
        val r = b.path.bounds
        assertTrue(r.left >= -1f && r.right <= b.width + 1f)
        assertTrue(r.top >= -b.ascent - 1f && r.bottom <= b.descent + 1f)
        assertTrue(r.width() > b.width * 0.9f)
    }
}
