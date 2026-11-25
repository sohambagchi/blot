package app.blot.markdown

import android.graphics.Bitmap
import android.graphics.Canvas
import app.blot.canvas.OutlineCache
import app.blot.core.export.ExportBackground
import app.blot.core.export.ExportFormat
import app.blot.core.export.ExportOptions
import app.blot.core.export.ExportRange
import app.blot.core.model.Argb
import app.blot.core.model.Note
import app.blot.core.model.Rect
import app.blot.core.model.TextBox
import app.blot.core.export.ExportAssets
import app.blot.export.NoteExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

/**
 * Renders markdown with maths to `app/build/outputs/markdown/` (PNG light and dark, and SVG) and
 * checks the layout: inline maths wraps with the text, display maths is centred, broken LaTeX
 * shows its source.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MathRenderTest {
    private val outDir = File("build/outputs/markdown").apply { mkdirs() }

    private fun render(theme: MarkdownTheme, name: String, source: String = SAMPLE, width: Int = WIDTH): Pair<MarkdownLayout, Bitmap> {
        val layout = MarkdownLayout(source, width, theme, density = 2f)
        val margin = 40
        val bitmap = Bitmap.createBitmap(width + margin * 2, layout.height + margin * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(theme.background)
        canvas.translate(margin.toFloat(), margin.toFloat())
        layout.draw(canvas)
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return layout to bitmap
    }

    private fun Bitmap.ink(left: Float, top: Float, right: Float, bottom: Float, background: Int): Int {
        var n = 0
        for (y in (top.toInt() + 40).coerceAtLeast(0) until (bottom.toInt() + 40).coerceAtMost(height)) {
            for (x in (left.toInt() + 40).coerceAtLeast(0) until (right.toInt() + 40).coerceAtMost(width)) if (getPixel(x, y) != background) n++
        }
        return n
    }

    @Test
    fun rendersSampleLightAndDark() {
        for ((theme, name) in listOf(MarkdownTheme.Light to "math-light.png", MarkdownTheme.Dark to "math-dark.png")) {
            val (layout, bmp) = render(theme, name)
            val maths = layout.decorations.filterIsInstance<MarkdownDecoration.Math>()
            assertTrue("${maths.size} maths decorations", maths.size >= 10)
            assertTrue(maths.all { it.color == theme.textColor })
            for (m in maths) {
                val b = m.path.bounds
                assertTrue(bmp.ink(m.x + b.left, m.baseline + b.top, m.x + b.right, m.baseline + b.bottom, theme.background) > 0)
            }
            // No LaTeX source survives in the laid-out text, and dollar amounts stay text.
            assertTrue(layout.text.none { it == '\\' })
            assertTrue(layout.text.contains("It costs \$5 and \$6."))
            // Display maths is centred: the quadratic formula's ink is symmetric about the middle.
            val quadratic = maths.maxBy { it.path.bounds.height() }
            val mid = quadratic.x + (quadratic.path.bounds.left + quadratic.path.bounds.right) / 2
            assertTrue("formula centred at $mid", kotlin.math.abs(mid - WIDTH / 2f) < WIDTH * 0.1f)
        }
    }

    @Test
    fun longInlineMathsWrapsAtOperators() {
        val (layout, _) = render(MarkdownTheme.Light, "math-wrap.png", WRAP, width = 360)
        val pieces = layout.decorations.filterIsInstance<MarkdownDecoration.Math>()
        val lines = pieces.map { it.baseline }.distinct()
        assertTrue("maths spread over ${lines.size} lines", lines.size >= 2)
        // Each piece stays inside the width it was laid out at.
        for (p in pieces) assertTrue(p.x + p.path.bounds.right <= 360 + 1)
    }

    @Test
    fun invalidLatexShowsItsSourceUnderlined() {
        val theme = MarkdownTheme.Light
        val (layout, bmp) = render(theme, "math-error.png", "Broken: \$\\frac{1}{\$ and fine \$x^2\$.\n\n$$\\left( x$$")
        assertTrue(layout.text.toString().contains("\$\\frac{1}{\$"))
        assertTrue(layout.text.toString().contains("$$\\left( x$$"))
        val underlines = layout.decorations.filterIsInstance<MarkdownDecoration.Box>().filter { it.color == theme.errorColor }
        assertEquals(2, underlines.size)
        val u = underlines.first()
        assertTrue(bmp.ink(u.left, u.top, u.right, u.bottom + 1, theme.background) > 0)
        assertEquals(1, layout.decorations.count { it is MarkdownDecoration.Math })
    }

    @Test
    fun textBoxSvgCarriesMathsAsPaths() {
        val box = TextBox("t", Rect(20f, 20f, 420f, 10f), SAMPLE, 16f, Argb.BLACK)
        val sized = box.copy(frame = box.frame.copy(height = textBoxLayout(box, Argb.BLACK.value, false, MarkdownImageLoader.None).height.toFloat()))
        val note = Note("n", created = T0, modified = T0, elements = listOf(sized))
        val options = ExportOptions(format = ExportFormat.SVG, range = ExportRange.WholeNote, background = ExportBackground.PAPER, scale = 2f)
        val bytes = ByteArrayOutputStream().also { NoteExporter(OutlineCache()).exportSvg(note, ExportAssets.NONE, options, it) }.toByteArray()
        File(outDir, "math.svg").writeBytes(bytes)
        val svg = String(bytes)
        assertTrue(!svg.contains("foreignObject"))
        val paths = Regex("<path d=\"M[^\"]+\"").findAll(svg).count()
        assertTrue("$paths paths", paths >= 10)
        val png = ByteArrayOutputStream().also { NoteExporter(OutlineCache()).exportPng(note, ExportAssets.NONE, options.copy(format = ExportFormat.PNG), it) }
        File(outDir, "math-export.png").writeBytes(png.toByteArray())
    }

    private companion object {
        const val WIDTH = 820
        val T0: Instant = Instant.parse("2026-11-24T00:00:00Z")

        val SAMPLE = """
            # Maths in a note

            The roots of ${'$'}ax^2 + bx + c = 0${'$'} are

            ${'$'}${'$'}x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}$$

            A sum with limits, ${'$'}\sum_{k=1}^{n} k = \frac{n(n+1)}{2}${'$'}, and in display:

            $$
            \sum_{k=1}^{\infty} \frac{1}{k^2} = \frac{\pi^2}{6}
            $$

            An integral: $$\int_0^1 x^2 \, dx = \left[ \frac{x^3}{3} \right]_0^1 = \frac{1}{3}$$

            A matrix and its determinant:

            $$\det\begin{pmatrix} a & b \\ c & d \end{pmatrix} = ad - bc \qquad \begin{bmatrix} 1 & 0 \\ 0 & 1 \end{bmatrix}$$

            Accents ${'$'}\hat{x}, \bar{y}, \vec{v}, \dot{z}${'$'}, sets ${'$'}\mathbb{R}, \mathcal{L}${'$'}, Greek ${'$'}\alpha\beta\Gamma\Omega${'$'}, ${'$'}\lim_{x \to 0} \frac{\sin x}{x} = 1${'$'}, ${'$'}\prod_{i} p_i${'$'} and text ${'$'}\text{if } x > 0${'$'}.

            The long one wraps: ${'$'}f(x) = a_0 + a_1 x + a_2 x^2 + a_3 x^3 + a_4 x^4 + a_5 x^5 + a_6 x^6 + a_7 x^7 + a_8 x^8${'$'} and the text carries on after it. It costs $5 and $6.
        """.trimIndent()

        val WRAP = "Expand ${'$'}(a+b)^4 = a^4 + 4a^3b + 6a^2b^2 + 4ab^3 + b^4${'$'} by the binomial theorem."
    }
}
