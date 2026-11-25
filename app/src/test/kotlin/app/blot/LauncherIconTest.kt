package app.blot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun adaptiveIconHasInkOnPaperAndAMonochromeLayer() {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertNotNull(icon.monochrome)
        val size = 432
        val layers = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(layers).let { c ->
            icon.background.setBounds(0, 0, size, size)
            icon.background.draw(c)
            icon.foreground.setBounds(0, 0, size, size)
            icon.foreground.draw(c)
        }
        File(System.getProperty("user.dir")!!, "build/outputs/icon").apply { mkdirs() }
            .resolve("ic_launcher-layers.png").outputStream().use { layers.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // Viewport units scale by size/108: the corner is paper, the stem (x = 40 before the 0.86 scale about 54) is ink.
        assertEquals(0xFFF6F5F2.toInt(), layers.getPixel(4, 4))
        val stemX = ((54 + (40 - 54) * 0.86) * size / 108).toInt()
        val midY = size / 2
        assertTrue(Color.luminance(layers.getPixel(stemX, midY)) < 0.1f)
    }
}
