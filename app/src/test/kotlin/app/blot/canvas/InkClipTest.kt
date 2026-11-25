package app.blot.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The front buffer's clip: ink never lands inside an exclusion, rounded corners included. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InkClipTest {
    private val pill = InkExclusion(20f, 20f, 80f, 60f, radius = 10f)
    private val panel = InkExclusion(70f, 40f, 150f, 90f)
    private val exclusions = listOf(pill, panel)

    private fun opaque(b: Bitmap, x: Int, y: Int) = Color.alpha(b.getPixel(x, y)) == 255
    private fun clear(b: Bitmap, x: Int, y: Int) = Color.alpha(b.getPixel(x, y)) == 0

    @Test
    fun clipOutKeepsInkOutsideOnly() {
        val b = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.save()
        InkClip().clipOut(c, exclusions)
        c.drawColor(Color.RED)
        c.restore()
        // Inside each exclusion, and where they overlap: no ink.
        for ((x, y) in listOf(50 to 40, 25 to 40, 50 to 22, 75 to 50, 100 to 70, 149 to 89, 71 to 41)) {
            assertEquals("($x, $y)", true, clear(b, x, y))
        }
        // Outside: ink, including just past the rectangles' edges and in the pill's rounded-off corner.
        for ((x, y) in listOf(10 to 10, 19 to 40, 50 to 61, 151 to 60, 160 to 95, 21 to 21, 78 to 21)) {
            assertEquals("($x, $y)", true, opaque(b, x, y))
        }
    }

    @Test
    fun clearWipesWhatTheBufferHolds() {
        val b = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.BLUE)
        InkClip().clear(c, exclusions)
        assertEquals(true, clear(b, 50, 40))
        assertEquals(true, clear(b, 120, 80))
        assertEquals(true, opaque(b, 5, 5))
        assertEquals(true, opaque(b, 21, 21))
        assertEquals(true, opaque(b, 190, 50))
    }

    @Test
    fun noExclusionsLeavesTheCanvasAlone() {
        val b = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.save()
        InkClip().clipOut(c, emptyList())
        c.drawColor(Color.RED)
        c.restore()
        assertEquals(true, opaque(b, 10, 10))
    }

    @Test
    fun coveringGrowsToWholePixels() {
        assertEquals(InkExclusion(10f, 20f, 101f, 51f, 14f), InkExclusion.covering(10.4f, 20.6f, 100.2f, 50.5f, 14f))
        assertEquals(InkExclusion(-3f, 0f, 5f, 5f, 0f), InkExclusion.covering(-2.5f, 0f, 5f, 5f, -1f))
    }
}
