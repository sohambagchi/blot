package app.screenscratch.markdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
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
}
