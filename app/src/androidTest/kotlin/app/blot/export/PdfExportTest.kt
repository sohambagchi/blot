package app.blot.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.blot.core.export.ExportAssets
import app.blot.core.export.ExportFormat
import app.blot.core.export.ExportOptions
import app.blot.core.export.ExportPlan
import app.blot.core.export.ExportRange
import app.blot.core.export.ExportSizes
import app.blot.core.model.Argb
import app.blot.core.model.Arrowhead
import app.blot.core.model.Element
import app.blot.core.model.Line
import app.blot.core.model.Background
import app.blot.core.model.Note
import app.blot.core.model.Pattern
import app.blot.core.model.PageSetup
import app.blot.core.model.Point
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import app.blot.core.model.Stroke
import app.blot.core.model.StrokePoint
import app.blot.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.sin

/** PDF export through the platform PdfDocument, which Robolectric cannot run. */
@RunWith(AndroidJUnit4::class)
class PdfExportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")

    private fun wave(id: String, x: Float, y: Float) = Stroke(
        id, List(50) { i -> StrokePoint(x + i * 8f, y + 30f * sin(i / 5f), 0.6f, timeMs = i * 4L) }, Argb.BLACK, 4f,
    )

    private fun content(dy: Float): List<Element> = listOf(
        wave("w$dy", 60f, dy + 80f),
        Shape("s$dy", ShapeKind.RECTANGLE, Rect(60f, dy + 160f, 200f, 100f), Argb.BLACK, 2f, fill = Argb(0xFFFFF59D.toInt())),
        Line("l$dy", listOf(Point(60f, dy + 320f), Point(400f, dy + 320f)), Argb(0xFF1E5BD8.toInt()), 2f, endArrow = Arrowhead.TRIANGLE),
        TextBox("t$dy", Rect(300f, dy + 160f, 300f, 120f), "# Page\n\nSome **bold** text.", 16f, Argb.BLACK),
    )

    private fun export(note: Note, options: ExportOptions, name: String): File {
        val file = File(context.cacheDir, name)
        file.outputStream().use { NoteExporter().exportPdf(note, ExportAssets.NONE, options.copy(format = ExportFormat.PDF), it) }
        return file
    }

    private fun <T> open(file: File, block: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use(block) }

    @Test
    fun pageNoteGivesOnePdfPagePerNotePage() {
        val pages = PageSetup.Pages.a4(count = 3)
        val elements = (0 until 3).flatMap { content(pages.pageRect(it).top) }
        val note = Note("p", "Pages", t0, t0, pages, elements)
        val file = export(note, ExportOptions(), "pages.pdf")
        open(file) { pdf ->
            assertEquals(3, pdf.pageCount)
            for (i in 0 until 3) {
                pdf.openPage(i).use { page ->
                    assertEquals("A4 width in points", 595, page.width)
                    assertEquals("A4 height in points", 842, page.height)
                    if (i == 0) {
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        // The rectangle's fill at canvas (160, 210).
                        val px = bmp.getPixel((160 * 0.45f * 2).roundToInt(), (210 * 0.45f * 2).roundToInt())
                        assertTrue("%08X".format(px), Color.red(px) > 230 && Color.blue(px) < 200)
                    }
                }
            }
        }
        val second = export(note, ExportOptions(range = ExportRange.Pages(1, 2)), "pages-2-3.pdf")
        open(second) { assertEquals(2, it.pageCount) }
    }

    @Test
    fun infiniteNoteIsOnePageOfItsContent() {
        val note = Note("i", "Infinite", t0, t0, PageSetup.Infinite, content(0f) + wave("far", 2000f, 1500f), Background(Pattern.GRID))
        val file = export(note, ExportOptions(dark = true, backgroundPattern = true), "infinite.pdf")
        val expected = ExportSizes.pdfPage(ExportPlan.of(note, ExportOptions()).bounds)
        open(file) { pdf ->
            assertEquals(1, pdf.pageCount)
            pdf.openPage(0).use { page ->
                assertEquals(expected.width, page.width)
                assertEquals(expected.height, page.height)
            }
        }
        // Text stays text: the PDF carries a font, not just paths.
        assertTrue(String(file.readBytes(), Charsets.ISO_8859_1).contains("/Font"))
    }

    @Test
    fun hugeCanvasIsScaledToTheLargestPdfPage() {
        val note = Note("h", "Huge", t0, t0, PageSetup.Infinite, listOf(wave("a", 0f, 0f), wave("b", 60_000f, 100f)))
        open(export(note, ExportOptions(), "huge.pdf")) { pdf ->
            pdf.openPage(0).use { page -> assertTrue("${page.width}", page.width <= ExportSizes.MAX_PDF_POINTS.toInt()) }
        }
    }
}
