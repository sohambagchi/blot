package app.screenscratch.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Element
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.Rect
import app.screenscratch.core.model.Shape
import app.screenscratch.core.model.ShapeKind
import app.screenscratch.core.model.Stroke
import app.screenscratch.core.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Renders what the tools draw to `app/build/outputs/canvas-render/tools-*.png`: the selection
 * box and handles, a move and a scale in progress, a lasso, and a partial erase. A few pixels are checked; the images are for looking at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolRenderTest {
    private val outDir = File("build/outputs/canvas-render").apply { mkdirs() }
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")
    private val w = 1200
    private val h = 760
    private val density = 1.5f

    private val ink = Argb(0xFF222222.toInt())
    private val red = Argb(0xFFE53935.toInt())
    private val blue = Argb(0xFF1E5BD8.toInt())
    private val yellow = Argb(0xFFFFF59D.toInt())

    private fun wave(id: String, x0: Float, y0: Float, len: Float, color: Argb = ink, width: Float = 3f): Stroke {
        val n = 60
        return Stroke(
            id,
            List(n) { i ->
                val t = i / (n - 1f)
                StrokePoint(StrokeBuilder.snapXy(x0 + t * len), StrokeBuilder.snapXy(y0 + 12f * sin(t * 3 * PI).toFloat()), 0.5f, timeMs = i * 4L)
            },
            color,
            width,
        )
    }

    private fun render(c: CanvasController, name: String, dark: Boolean = false): Bitmap {
        c.onViewSize(w, h, density)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = SceneRenderer(c.outlineCache)
        r.draw(canvas, c.note.value, c.viewport, w, h, dark, c.scenePreview())
        ToolOverlayPainter(density).draw(canvas, c, r, dark)
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun near(expected: Int, actual: Int, tol: Int = 24): Boolean =
        listOf(16, 8, 0).all { abs(((expected shr it) and 0xFF) - ((actual shr it) and 0xFF)) <= tol }

    private fun hex(c: Int) = "%08X".format(c)

    private fun px(bmp: Bitmap, c: CanvasController, x: Float, y: Float) =
        bmp.getPixel(c.viewport.toScreenX(x).toInt(), c.viewport.toScreenY(y).toInt())

    @Test
    fun selectionHandles() {
        val c = CanvasController(Note("n", created = t0, modified = t0, elements = listOf<Element>(
            wave("a", 100f, 120f, 220f),
            wave("b", 120f, 170f, 180f, red),
            Shape("box", ShapeKind.RECTANGLE, Rect(400f, 80f, 160f, 120f), blue, 3f),
            wave("other", 100f, 380f, 300f),
        )))
        c.onViewSize(w, h, density)
        c.tool = Tool.SELECT
        c.select(setOf("a", "b", "box"))
        val light = render(c, "tools-selection-light")
        render(c, "tools-selection-dark", dark = true)
        val b = c.selectionBounds!!
        // A handle sits on the padded box's top-left corner: its ring is the accent colour.
        val pad = ToolGestures.SELECTION_PAD_DP * density
        val hx = c.viewport.toScreenX(b.left) - pad
        val hy = c.viewport.toScreenY(b.top) - pad
        val ring = light.getPixel((hx + ToolOverlayPainter.HANDLE_RADIUS_DP * density).toInt(), hy.toInt())
        assertTrue(hex(ring), near(ToolOverlayPainter.ACCENT_LIGHT, ring, 70))
        assertEquals(ToolOverlayPainter.KNOB_LIGHT, light.getPixel(hx.toInt(), hy.toInt()))

        // Mid-move: the selection is drawn displaced, the note isn't changed yet.
        c.toolStart(200f, 120f)
        c.toolMove(260f, 300f)
        render(c, "tools-selection-moving")
        c.toolCancel()
        // Mid-scale from the bottom-right handle.
        c.toolStart(b.right + ToolGestures.SELECTION_PAD_DP, b.bottom + ToolGestures.SELECTION_PAD_DP)
        c.toolMove(b.right + 120f, b.bottom + 60f)
        render(c, "tools-selection-scaling")
        c.toolCancel()
    }

    @Test
    fun lassoInProgress() {
        val c = CanvasController(Note("n", created = t0, modified = t0, elements = listOf<Element>(
            wave("a", 100f, 120f, 220f), wave("b", 120f, 220f, 180f, red),
        )))
        c.onViewSize(w, h, density)
        c.tool = Tool.SELECT
        c.toolStart(80f, 90f)
        for (i in 1..40) {
            val a = i / 40.0 * 2 * PI
            c.toolMove(200f - 120f * kotlin.math.cos(a).toFloat(), 160f - 70f * sin(a).toFloat() - 0f)
        }
        render(c, "tools-lasso")
        c.toolEnd()
        render(c, "tools-lasso-selected")
        assertTrue(c.selection.isNotEmpty())
    }

    @Test
    fun partialErase() {
        val c = CanvasController(Note("n", created = t0, modified = t0, elements = listOf<Element>(
            wave("a", 60f, 120f, 500f, width = 5f), wave("b", 60f, 220f, 500f, blue, width = 3f),
        )))
        c.onViewSize(w, h, density)
        c.eraserMode = EraserMode.PARTIAL
        c.beginErase()
        // Two vertical passes and a diagonal one.
        c.eraseAlong(200f, 60f, 200f, 280f, 8f)
        c.eraseAlong(400f, 60f, 420f, 280f, 4f)
        c.eraseAlong(480f, 100f, 560f, 240f, 12f)
        c.endErase()
        render(c, "tools-partial-erase")
        val strokes = c.note.value.elements.filterIsInstance<Stroke>()
        assertTrue("${strokes.size} pieces", strokes.size >= 6)
        val light = render(c, "tools-partial-erase")
        // The cut is clear of ink.
        assertEquals(CanvasPalette.LIGHT.paper, px(light, c, 200f, 120f))
    }
}
