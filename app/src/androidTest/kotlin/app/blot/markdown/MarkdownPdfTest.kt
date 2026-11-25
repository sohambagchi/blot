package app.blot.markdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.blot.core.export.ExportAssets
import app.blot.core.export.ExportFormat
import app.blot.core.export.ExportOptions
import app.blot.core.model.Argb
import app.blot.core.model.Note
import app.blot.core.model.PageSetup
import app.blot.core.model.Rect
import app.blot.core.model.TextBox
import app.blot.export.NoteExporter
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import kotlin.math.abs

/** PdfDocument and hardware rendering, which Robolectric cannot run. */
@RunWith(AndroidJUnit4::class)
class MarkdownPdfTest {
    private val sample = """
        # Heading

        Text with **bold**, *italic*, `code` and a [link](u).

        - [x] done
        - item
          1. nested

        > quote

        ```
        code block
        ```

        ![missing](assets/none.png)
    """.trimIndent()

    @Test
    fun drawsIntoPdfPage() {
        val layout = MarkdownLayout(sample, 500)
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        page.canvas.translate(48f, 48f)
        layout.draw(page.canvas)
        document.finishPage(page)
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "markdown.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        assertTrue(file.length() > 1000)
    }

    @Test
    fun hardwareCanvasMatchesBitmapCanvas() {
        val layout = MarkdownLayout(sample, 500)
        val software = Bitmap.createBitmap(500, layout.height, Bitmap.Config.ARGB_8888)
        layout.draw(Canvas(software))
        val picture = Picture()
        layout.draw(picture.beginRecording(500, layout.height))
        picture.endRecording()
        // Since API 28 this renders the picture through HWUI into a HARDWARE bitmap.
        val hardware = Bitmap.createBitmap(picture).copy(Bitmap.Config.ARGB_8888, false)
        var differing = 0
        for (y in 0 until software.height) for (x in 0 until software.width) {
            val a = software.getPixel(x, y)
            val b = hardware.getPixel(x, y)
            val delta = maxOf(
                abs(Color.alpha(a) - Color.alpha(b)), abs(Color.red(a) - Color.red(b)),
                abs(Color.green(a) - Color.green(b)), abs(Color.blue(a) - Color.blue(b)),
            )
            if (delta > 64) differing++
        }
        // GPU and CPU anti-aliasing differ along glyph edges only.
        assertTrue("$differing pixels differ", differing < software.width * software.height / 100)
    }

    @Test
    fun textBoxMathsExportsAsDrawnPdfContent() {
        val pages = PageSetup.Pages.a4()
        val r = pages.pageRect(0)
        val md = "Area \$\\pi r^2\$ of a circle.\n\n$$\\int_0^1 x^2 \\, dx = \\frac{1}{3}$$"
        val unsized = TextBox("t", Rect(r.left + 100f, r.top + 100f, 600f, 10f), md, 32f, Argb.BLACK)
        val layout = textBoxLayout(unsized, Argb.BLACK.value, false, MarkdownImageLoader.None)
        val box = unsized.copy(frame = unsized.frame.copy(height = layout.height.toFloat()))
        val t0 = Instant.parse("2026-11-24T00:00:00Z")
        val note = Note("m", "Maths", t0, t0, pages, listOf(box))
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "maths.pdf")
        file.outputStream().use { NoteExporter().exportPdf(note, ExportAssets.NONE, ExportOptions(format = ExportFormat.PDF), it) }

        val maths = layout.decorations.filterIsInstance<MarkdownDecoration.Math>()
        assertTrue("${maths.size} maths decorations", maths.size >= 2)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                pdf.openPage(0).use { page ->
                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val k = bmp.width / r.width
                    for (m in maths) {
                        val b = m.path.bounds
                        val left = ((box.frame.x + m.x + b.left - r.left) * k).toInt()
                        val right = ((box.frame.x + m.x + b.right - r.left) * k).toInt()
                        val top = ((box.frame.y + m.baseline + b.top - r.top) * k).toInt()
                        val bottom = ((box.frame.y + m.baseline + b.bottom - r.top) * k).toInt()
                        var ink = 0
                        for (y in top until bottom) for (x in left until right) if (Color.red(bmp.getPixel(x, y)) < 128) ink++
                        assertTrue("maths at $left,$top has $ink dark pixels", ink > 20)
                    }
                }
            }
        }
    }
}
