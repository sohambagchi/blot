package app.blot.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import app.blot.R
import app.blot.core.model.Argb
import app.blot.core.model.AssetRef
import app.blot.core.model.Element
import app.blot.core.model.Image
import app.blot.core.model.Note
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import app.blot.core.model.TextBox
import app.blot.ui.theme.BlotColors
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a turned selection mid-rotation and mid-scale, with its rotate handle and the turn and
 * scale readouts, light and dark, to `app/build/outputs/canvas-render/pointer-*.png`. The
 * readouts' pills are checked for the theme's colour; the images are for looking at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PointerRenderTest {
    private val outDir = File("build/outputs/canvas-render").apply { mkdirs() }
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")
    private val w = 1000
    private val h = 700
    private val density = 2f
    private val deg = (PI / 180).toFloat()

    private fun scene() = CanvasController(
        Note(
            "n", created = t0, modified = t0,
            elements = listOf<Element>(
                Shape("box", ShapeKind.RECTANGLE, Rect(120f, 120f, 160f, 90f), Argb(0xFF1E5BD8.toInt()), 3f, fill = Argb(0xFFFFF59D.toInt()), rotation = 20 * deg),
                TextBox("text", Rect(250f, 70f, 150f, 60f), "Turned text box\nsecond line", 16f, Argb(0xFF6A1B9A.toInt()), rotation = -12 * deg),
                Image("img", AssetRef("ab".repeat(32), "png"), Rect(60f, 250f, 100f, 60f), rotation = 35 * deg),
            ),
        ),
    ).apply {
        textMeasurer = { it.frame.height }
        onViewSize(w, h, density)
    }

    private fun render(c: CanvasController, name: String, dark: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = SceneRenderer(c.outlineCache)
        r.draw(canvas, c.note.value, c.viewport, w, h, dark, c.scenePreview())
        val inter = ResourcesCompat.getFont(ApplicationProvider.getApplicationContext(), R.font.inter_medium)
        ToolOverlayPainter(density, density, inter).draw(canvas, c, r, dark)
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun near(expected: Int, actual: Int, tol: Int = 6): Boolean =
        listOf(16, 8, 0).all { abs(((expected shr it) and 0xFF) - ((actual shr it) and 0xFF)) <= tol }

    /** Whether some pixel within [radius] px of the readout's handle, on screen, has the pill's fill. */
    private fun pillNear(bmp: Bitmap, c: CanvasController, dark: Boolean, radius: Int = 120): Boolean {
        val r = c.toolGestures.readout ?: return false
        val fill = (if (dark) BlotColors.Dark else BlotColors.Light).active.toArgb()
        val cx = c.viewport.toScreenX(r.x).toInt()
        val cy = c.viewport.toScreenY(r.y).toInt()
        var hits = 0
        for (y in (cy - radius).coerceAtLeast(0) until (cy + radius).coerceAtMost(h)) {
            for (x in (cx - radius).coerceAtLeast(0) until (cx + radius).coerceAtMost(w)) {
                if (near(fill, bmp.getPixel(x, y))) hits++
            }
        }
        return hits > 200
    }

    @Test
    fun rotatingShowsTheTurn() {
        for (dark in listOf(false, true)) {
            val c = scene()
            c.tool = Tool.SELECT
            c.snapAngles = true
            c.select(setOf("box"))
            val f = c.selectionFrame()!!
            val handle = f.rotateHandle(ToolGestures.SELECTION_PAD_DP, ToolGestures.ROTATE_HANDLE_GAP_DP)
            c.toolStart(handle.x, handle.y)
            // The handle starts at 20° − 90°; drag it on by about 31°, which snaps the box to 45°.
            val a = (20f - 90f + 31f) * deg
            c.toolMove(f.rect.centerX + 80f * cos(a), f.rect.centerY + 80f * sin(a))
            val text = c.toolGestures.readout!!.text
            assertTrue(text, text == "+25°")
            val bmp = render(c, "pointer-rotate-${if (dark) "dark" else "light"}", dark)
            assertTrue("pill near the rotate handle", pillNear(bmp, c, dark))
            c.toolCancel()
        }
    }

    @Test
    fun scalingShowsTheFactor() {
        for (dark in listOf(false, true)) {
            val c = scene()
            c.tool = Tool.SELECT
            c.select(setOf("text"))
            val xy = c.selectionFrame()!!.corners(ToolGestures.SELECTION_PAD_DP)
            c.toolStart(xy[4], xy[5])
            c.toolMove(xy[0] + (xy[4] - xy[0]) * 1.25f, xy[1] + (xy[5] - xy[1]) * 1.25f)
            val text = c.toolGestures.readout!!.text
            assertTrue(text, text == "1.25x")
            val bmp = render(c, "pointer-scale-${if (dark) "dark" else "light"}", dark)
            assertTrue("pill near the corner", pillNear(bmp, c, dark))
            c.toolCancel()
        }
    }

    @Test
    fun aTurnedGroupSelection() {
        val c = scene()
        c.tool = Tool.SELECT
        c.selectAll()
        render(c, "pointer-selection-light", false)
        render(c, "pointer-selection-dark", true)
    }
}
