package app.blot.export

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.blot.canvas.CanvasPalette
import app.blot.canvas.InkColors
import app.blot.core.export.ExportBackground
import app.blot.core.export.ExportFormat
import app.blot.core.export.ExportOptions
import app.blot.core.export.ExportPlan
import app.blot.core.export.ExportRange
import app.blot.core.export.SvgNoteWriter
import app.blot.core.model.Background
import app.blot.core.model.PageSetup
import app.blot.core.model.Pattern
import app.blot.core.model.Rect
import app.blot.ui.editor.ExportArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.ceil
import app.blot.ui.editor.ExportOptions as DialogOptions

/** Exports the sample note to `app/build/exports/` as PNG and SVG and checks what came out. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteExportTest {
    private val outDir = File("build/exports").apply { mkdirs() }
    private val exporter = NoteExporter()

    private fun png(note: app.blot.core.model.Note, options: ExportOptions, name: String): Bitmap {
        val bytes = ByteArrayOutputStream().also { exporter.exportPng(note, ExportSamples.assets, options, it) }.toByteArray()
        File(outDir, "$name.png").writeBytes(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun svg(note: app.blot.core.model.Note, options: ExportOptions, name: String): Pair<String, Document> {
        val bytes = ByteArrayOutputStream().also { exporter.exportSvg(note, ExportSamples.assets, options.copy(format = ExportFormat.SVG), it) }.toByteArray()
        File(outDir, "$name.svg").writeBytes(bytes)
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        return String(bytes, Charsets.UTF_8) to doc
    }

    private fun Document.count(name: String) = getElementsByTagNameNS(SvgNoteWriter.SVG_NS, name).length

    private fun near(expected: Int, actual: Int, tol: Int = 40): Boolean =
        listOf(16, 8, 0).all { abs(((expected shr it) and 0xFF) - ((actual shr it) and 0xFF)) <= tol }

    private fun hex(c: Int) = "%08X".format(c)

    /** The pixel at canvas ([x], [y]) of an export of [bounds] at [scale]. */
    private fun Bitmap.at(bounds: Rect, scale: Float, x: Float, y: Float) =
        getPixel(((x - bounds.x) * scale).toInt(), ((y - bounds.y) * scale).toInt())

    @Test
    fun infinitePngLightAndDark() {
        val note = ExportSamples.infinite()
        val bounds = ExportPlan.of(note, ExportOptions()).bounds
        val light = png(note, ExportOptions(), "infinite-light")
        val dark = png(note, ExportOptions(dark = true), "infinite-dark")
        assertEquals(ceil(bounds.width * 2).toInt(), light.width)
        assertEquals(ceil(bounds.height * 2).toInt(), light.height)

        assertEquals(hex(CanvasPalette.LIGHT.paper), hex(light.getPixel(2, 2)))
        assertEquals(hex(CanvasPalette.DARK.paper), hex(dark.getPixel(2, 2)))

        // The black wave's first sample.
        assertTrue(hex(light.at(bounds, 2f, 40f, 80f)), near(0xFF000000.toInt(), light.at(bounds, 2f, 40f, 80f)))
        assertTrue(hex(dark.at(bounds, 2f, 40f, 80f)), near(InkColors.darkMode(0xFF000000.toInt()), dark.at(bounds, 2f, 40f, 80f)))
        // The rectangle's fill is transformed; the image is not.
        assertTrue(near(0xFFFFF59D.toInt(), light.at(bounds, 2f, 150f, 330f), 8))
        assertTrue(near(InkColors.darkMode(0xFFFFF59D.toInt()), dark.at(bounds, 2f, 150f, 330f), 8))
        assertEquals(hex(light.at(bounds, 2f, 624f, 268f)), hex(dark.at(bounds, 2f, 624f, 268f)))
        // Markdown, not source text: the heading's ink is in the text box's top left.
        var ink = 0
        for (y in 405 until 425) for (x in 405 until 520) if (!near(CanvasPalette.LIGHT.paper, light.at(bounds, 2f, x.toFloat(), y.toFloat()), 30)) ink++
        assertTrue("heading ink $ink", ink > 200)
    }

    @Test
    fun transparentDotGridAndSelection() {
        val note = ExportSamples.infinite().copy(background = Background(Pattern.DOTS))
        val t = png(note, ExportOptions(background = ExportBackground.TRANSPARENT, backgroundPattern = true), "infinite-transparent-dots")
        assertEquals(0, t.getPixel(2, 2) ushr 24)
        val bounds = ExportPlan.of(note, ExportOptions()).bounds
        // A dot centre: cells start at the canvas origin.
        assertTrue(hex(t.at(bounds, 2f, 14f, 14f)), (t.at(bounds, 2f, 14f, 14f) ushr 24) > 128)

        val sel = ExportOptions(range = ExportRange.Selection(setOf("oval")), scale = 1f)
        val s = png(note, sel, "selection-oval")
        val ovalBounds = note.element("oval")!!.bounds.inflate(24f)
        assertEquals(ceil(ovalBounds.width).toInt(), s.width)
        // Only the oval: the wave that crosses its box is left out.
        val sb = ExportPlan.of(note, sel)
        assertEquals(listOf("oval"), sb.elements.map { it.id })
    }

    @Test
    fun pageNoteStacksPagesInOnePng() {
        val note = ExportSamples.pages().copy(background = Background(Pattern.DOTS))
        val page = note.page as PageSetup.Pages
        for (dark in listOf(false, true)) {
            val bmp = png(note, ExportOptions(dark = dark, backgroundPattern = true, scale = 1f), if (dark) "pages-dark" else "pages-light")
            val palette = CanvasPalette.of(dark)
            assertEquals(ceil(page.width).toInt(), bmp.width)
            assertEquals(ceil(page.height * 2 + PageSetup.Pages.PAGE_GAP).toInt(), bmp.height)
            // The gap between pages is the desk, and the straddling stroke is clipped out of it.
            val gapY = (page.height + PageSetup.Pages.PAGE_GAP / 2).toInt()
            assertEquals(hex(palette.desk), hex(bmp.getPixel(400, gapY)))
            assertEquals(hex(palette.paper), hex(bmp.getPixel(3, 3)))
        }
        val second = png(note, ExportOptions(range = ExportRange.Pages(1, 1), scale = 1f), "pages-second")
        assertEquals(ceil(page.height).toInt(), second.height)
    }

    @Test
    fun pngStaysWithinPixelBudget() {
        val note = ExportSamples.infinite()
        val out = ByteArrayOutputStream()
        val size = exporter.exportPng(note, ExportSamples.assets, ExportOptions(scale = 3f, maxPixels = 100_000), out)
        assertTrue(size.width.toLong() * size.height <= 100_000)
        assertTrue(size.scale < 3f)
        val bmp = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        assertEquals(size.width, bmp.width)
    }

    @Test
    fun svgSamples() {
        val note = ExportSamples.infinite()
        val (raw, doc) = svg(note, ExportOptions(), "infinite-light")
        svg(note.copy(background = Background(Pattern.DOTS)), ExportOptions(dark = true, backgroundPattern = true), "infinite-dark")
        svg(ExportSamples.pages().copy(background = Background(Pattern.DOTS)), ExportOptions(backgroundPattern = true), "pages-light")
        assertEquals(0, doc.count("foreignObject"))
        // The image element and the markdown image are embedded; the missing image is a placeholder.
        assertEquals(2, doc.count("image"))
        assertEquals(2, Regex("data:image/png;base64,").findAll(raw).count())
        assertTrue("strokes and line parts", doc.count("path") >= 4 + 3 + 1)
        assertTrue(doc.count("tspan") > 10)
        assertTrue(raw.contains("&amp; a "))
        assertTrue(raw.contains("&lt;ok&gt;"))
        assertTrue(raw.contains(">Export test</tspan>"))
        assertFalse(raw.contains("**bold**"))
    }

    @Test
    fun pngWithEachPattern() {
        val base = ExportSamples.pages()
        val page = base.page as PageSetup.Pages
        val palette = CanvasPalette.LIGHT
        val paper = hex(palette.paper)
        // Canvas row 1498 is a dot row (14 + 28 × 53) and lies between grid rows.
        val y = 1498f
        for (pattern in Pattern.entries) {
            val note = base.copy(background = Background(pattern))
            val bmp = png(note, ExportOptions(backgroundPattern = true), "pattern-${pattern.name.lowercase()}")
            val bounds = ExportPlan.of(note, ExportOptions()).bounds
            fun at(x: Float, yy: Float) = hex(bmp.at(bounds, 2f, x, yy))
            when (pattern) {
                Pattern.DOTS -> {
                    assertEquals(hex(palette.patternDot), at(14f, y))
                    assertEquals(paper, at(28f, y))
                }
                Pattern.GRID -> {
                    assertEquals(hex(palette.patternLine), at(28f, y))
                    assertEquals(paper, at(42f, y))
                    assertEquals("no line on the page edge", paper, at(0f, y))
                }
                Pattern.RULED -> {
                    assertEquals("the first rule is one spacing below the top", hex(palette.patternLine), at(600f, 45f))
                    assertEquals(paper, at(600f, 20f))
                    assertEquals(paper, at(600f, page.height - 20f))
                    assertEquals(paper, at(28f, y))
                }
            }
            val plain = png(note, ExportOptions(), "pattern-${pattern.name.lowercase()}-off")
            assertEquals(paper, hex(plain.at(bounds, 2f, 28f, y)))
            assertEquals(paper, hex(plain.at(bounds, 2f, 14f, y)))
        }
    }

    @Test
    fun svgWithEachPatternIsWellFormed() {
        for (pattern in Pattern.entries) {
            for (paged in listOf(false, true)) {
                val note = (if (paged) ExportSamples.pages() else ExportSamples.infinite()).copy(background = Background(pattern, 32f))
                val name = "pattern-${pattern.name.lowercase()}-${if (paged) "pages" else "infinite"}"
                val (raw, doc) = svg(note, ExportOptions(backgroundPattern = true), name)
                val sheets = if (paged) 2 else 1
                if (pattern == Pattern.DOTS) {
                    assertEquals(sheets, doc.count("pattern"))
                    assertTrue(raw.contains("width=\"32\" height=\"32\""))
                } else {
                    assertEquals(0, doc.count("pattern"))
                    assertTrue(name, raw.contains("stroke=\"#E3E0D8\""))
                }
                val (_, none) = svg(note, ExportOptions(), "$name-off")
                assertEquals(0, none.count("pattern"))
            }
        }
    }

    @Test
    fun dialogOptionsMap() {
        val visible = Rect(10f, 20f, 300f, 200f)
        val pdf = exportOptionsFor(
            DialogOptions(format = app.blot.ui.editor.ExportFormat.Pdf, area = ExportArea.VisibleArea, background = app.blot.ui.editor.ExportBackground.Transparent, lightColours = false),
            visible, emptySet(), appDark = true,
        )
        assertEquals(ExportFormat.PDF, pdf.format)
        assertEquals(ExportRange.Area(visible), pdf.range)
        assertEquals(ExportBackground.PAPER, pdf.background)
        assertTrue(pdf.dark)
        val png = exportOptionsFor(DialogOptions(area = ExportArea.Selection, scale = 3), visible, setOf("a"), appDark = true)
        assertEquals(ExportRange.Selection(setOf("a")), png.range)
        assertEquals(3f, png.scale)
        assertFalse("light colours on", png.dark)
        assertFalse(exportOptionsFor(DialogOptions(lightColours = false), visible, emptySet(), appDark = false).dark)
        assertTrue(exportOptionsFor(DialogOptions(backgroundPattern = true), visible, emptySet(), appDark = false).backgroundPattern)
    }

    @Test
    fun sharesThroughTheFileProvider() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val note = ExportSamples.infinite()
        val file = ExportFiles.writeToCache(context, note, ExportSamples.assets, ExportOptions(format = ExportFormat.SVG))
        assertEquals("Export sample.svg", file.name)
        assertTrue(file.length() > 1000)
        val chooser = ExportFiles.shareIntent(context, file, ExportFormat.SVG)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("image/svg+xml", send.type)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.files", uri.authority)
        assertTrue(uri.path!!.startsWith("/exports/"))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
