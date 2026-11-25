package app.blot.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import app.blot.core.model.Argb
import app.blot.core.model.Rect
import app.blot.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownTextBoxRendererTest {
    private val box = TextBox("t", Rect(10f, 10f, 200f, 80f), "# Title\n\nSome **bold** text", 16f, Argb.BLACK)
    private val canvas = Canvas(Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888))

    @Test
    fun layoutsAreCachedByContentWidthAndTheme() {
        val r = MarkdownTextBoxRenderer()
        r.draw(canvas, box, Argb.BLACK.value)
        r.draw(canvas, box, Argb.BLACK.value)
        assertEquals(1, r.cachedCount)
        r.draw(canvas, box.copy(markdown = "changed"), Argb.BLACK.value)
        r.draw(canvas, box.copy(frame = Rect(10f, 10f, 150f, 80f)), Argb.BLACK.value)
        r.dark = true
        r.draw(canvas, box, 0xFFEEEEEE.toInt())
        assertEquals(4, r.cachedCount)
    }

    @Test
    fun drawsInk() {
        val bmp = Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888)
        MarkdownTextBoxRenderer().draw(Canvas(bmp), box, Argb.BLACK.value)
        var inked = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) if (bmp.getPixel(x, y) ushr 24 != 0) inked++
        assertTrue(inked > 50)
    }

    @Test
    fun drawingFromTwoThreadsIsSafe() {
        val r = MarkdownTextBoxRenderer()
        val pool = Executors.newFixedThreadPool(2)
        val jobs = (0 until 2).map { t ->
            pool.submit {
                val c = Canvas(Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888))
                repeat(50) { i -> r.draw(c, box.copy(markdown = "line ${i % 5} from $t"), Argb.BLACK.value) }
            }
        }
        jobs.forEach { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()
    }
}
