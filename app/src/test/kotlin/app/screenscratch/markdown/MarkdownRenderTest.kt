package app.screenscratch.markdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Shader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownRenderTest {
    private val photo: Bitmap = Bitmap.createBitmap(320, 120, Bitmap.Config.ARGB_8888).also {
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 320f, 120f, Color.rgb(240, 140, 60), Color.rgb(60, 120, 220), Shader.TileMode.CLAMP)
        }
        Canvas(it).drawRect(0f, 0f, 320f, 120f, paint)
    }
    private val loader = MarkdownImageLoader { path -> if (path == "assets/photo.png") photo else null }

    private fun outDir() = File(System.getProperty("user.dir")!!, "build/markdown").apply { mkdirs() }

    private fun render(theme: MarkdownTheme, name: String): Pair<MarkdownLayout, Bitmap> {
        val layout = MarkdownLayout(SAMPLE, 820, theme, density = 2f, imageLoader = loader)
        val margin = 40
        val bitmap = Bitmap.createBitmap(820 + margin * 2, layout.height + margin * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(theme.background)
        canvas.translate(margin.toFloat(), margin.toFloat())
        layout.draw(canvas)
        File(outDir(), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return layout to bitmap
    }

    private fun Bitmap.at(x: Float, y: Float) = getPixel(x.roundToInt() + 40, y.roundToInt() + 40)

    private fun Bitmap.inkIn(left: Float, top: Float, right: Float, bottom: Float, background: Int): Int {
        var count = 0
        for (y in (top.toInt() + 40) until (bottom.toInt() + 40)) {
            for (x in (left.toInt() + 40) until (right.toInt() + 40)) if (getPixel(x, y) != background) count++
        }
        return count
    }

    @Test
    fun rendersSampleNoteToBitmap() {
        for ((theme, name) in listOf(MarkdownTheme.Light to "sample-light.png", MarkdownTheme.Dark to "sample-dark.png")) {
            val (layout, bitmap) = render(theme, name)
            val lines = layout.lines

            val heading = lines.first { it.runs.isNotEmpty() }
            val run = heading.runs.first()
            assertTrue(bitmap.inkIn(run.x, heading.top, run.x + run.width, heading.bottom, theme.background) > 100)
            assertEquals(0, bitmap.inkIn(run.x + run.width + 20, heading.top, 800f, heading.bottom, theme.background))

            val code = layout.decorations.filterIsInstance<MarkdownDecoration.Box>().first { it.right == 820f }
            assertEquals(theme.codeBackground, bitmap.at(code.right - 6, (code.top + code.bottom) / 2))

            val bar = layout.decorations.filterIsInstance<MarkdownDecoration.Box>()
                .first { it.color == theme.quoteBarColor && it.right - it.left < 10 }
            assertEquals(theme.quoteBarColor, bitmap.at((bar.left + bar.right) / 2, (bar.top + bar.bottom) / 2))

            val image = layout.decorations.filterIsInstance<MarkdownDecoration.Image>().single()
            val mid = bitmap.at((image.left + image.right) / 2, (image.top + image.bottom) / 2)
            assertTrue(Color.red(mid) > 60 && Color.blue(mid) > 60)

            val check = layout.decorations.filterIsInstance<MarkdownDecoration.Box>().first { it.color == theme.linkColor }
            assertEquals(theme.linkColor, bitmap.at(check.left + 3, check.top + 3))

            assertEquals(1, layout.decorations.filterIsInstance<MarkdownDecoration.Label>().count { it.text == "missing diagram" })
        }
    }

    // Robolectric has no PdfDocument backend (startPage throws "document is closed"), so the
    // real PDF path is covered by MarkdownPdfTest on a device. A PdfDocument page canvas, like a
    // Picture, records draw calls instead of rasterising them; replaying a recorded Picture must
    // give the same pixels as drawing straight into the Bitmap.
    @Test
    fun recordedPictureMatchesDirectBitmapDraw() {
        val layout = MarkdownLayout(SAMPLE, 500, imageLoader = loader)
        val direct = Bitmap.createBitmap(500, layout.height, Bitmap.Config.ARGB_8888)
        layout.draw(Canvas(direct))
        val picture = Picture()
        layout.draw(picture.beginRecording(500, layout.height))
        picture.endRecording()
        val replayed = Bitmap.createBitmap(500, layout.height, Bitmap.Config.ARGB_8888)
        Canvas(replayed).drawPicture(picture)
        assertTrue(direct.sameAs(replayed))
    }

    companion object {
        val SAMPLE = """
            # Weekly notes

            A paragraph with **bold**, *italic*, ~~struck~~, `inline code` and a [link](https://example.com). It is long enough to wrap onto a second line in the box.

            ## Lists

            - First bullet
            - Second bullet
              - Nested bullet with a longer line of text that wraps around under itself
                - Third level
            - Back out

            1. One
            2. Two
            10. Ten

            - [ ] Open task
            - [x] Done task

            > A quote with a [link](u) inside.
            >
            > > And a nested quote.

            ```kotlin
            fun main() {
                println("hi")
            }
            ```

            ---

            ![photo](assets/photo.png)

            ![missing diagram](assets/gone.png)
        """.trimIndent()
    }
}
