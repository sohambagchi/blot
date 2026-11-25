package app.screenscratch.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Arrowhead
import app.screenscratch.core.model.Element
import app.screenscratch.core.model.Line
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
 * Renders what the tools draw to `app/build/outputs/canvas-render/tools-*.png`: every shape and
 * line kind the tools make, the selection box and handles, a move in progress, a lasso and a
 * draft shape, and a partial erase. A few pixels are checked; the images are for looking at.
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

    /** Draws every element of the tools' kinds by driving the controller as the pen would. */
    private fun drawnByTools(): CanvasController {
        val c = CanvasController(Note("n", created = t0, modified = t0))
        c.width = 3f
        c.tool = Tool.SHAPE
        fun drag(x0: Float, y0: Float, x1: Float, y1: Float) {
            c.toolStart(x0, y0)
            c.toolMove(x1, y1)
            c.toolEnd()
        }
        c.color = ink
        drag(20f, 20f, 160f, 110f)
        c.shapeFill = yellow
        drag(190f, 20f, 330f, 110f)
        c.shapeFill = null
        c.constrainShapes = true
        c.color = blue
        drag(360f, 20f, 460f, 70f)
        c.shapeKind = ShapeKind.ELLIPSE
        c.constrainShapes = false
        drag(490f, 20f, 630f, 110f)
        c.constrainShapes = true
        c.shapeFill = yellow
        c.color = red
        drag(660f, 20f, 760f, 70f)

        c.tool = Tool.LINE
        c.color = ink
        c.width = 2.5f
        for ((i, arrows) in LineArrows.entries.withIndex()) {
            c.lineArrows = arrows
            drag(20f, 160f + i * 50f, 260f, 160f + i * 50f + 6f)
        }
        c.arrowhead = Arrowhead.TRIANGLE
        c.color = blue
        c.lineArrows = LineArrows.BOTH
        drag(300f, 160f, 520f, 300f)
        c.lineArrows = LineArrows.END
        c.snapAngles = false
        drag(560f, 160f, 760f, 290f)
        return c
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
    fun shapesAndLines() {
        val c = drawnByTools()
        val els = c.note.value.elements
        assertEquals(5, els.filterIsInstance<Shape>().size)
        assertEquals(5, els.filterIsInstance<Line>().size)
        val light = render(c, "tools-shapes-lines-light")
        render(c, "tools-shapes-lines-dark", dark = true)
        // The filled rectangle's inside, and the square's outline.
        assertTrue(hex(px(light, c, 260f, 65f)), near(yellow.value, px(light, c, 260f, 65f), 4))
        assertTrue(near(blue.value, px(light, c, 360f, 45f), 60))
        // The snapped "none" line is horizontal: its far end is at the start's height.
        val none = els.filterIsInstance<Line>().first()
        assertEquals(none.points[0].y, none.points[1].y)
        assertTrue(near(ink.value, px(light, c, 200f, 160f), 60))
    }

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
    fun lassoAndDraftInProgress() {
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

        c.tool = Tool.SHAPE
        c.shapeKind = ShapeKind.ELLIPSE
        c.color = blue
        c.toolStart(420f, 100f)
        c.toolMove(620f, 260f)
        render(c, "tools-draft-ellipse")
        c.toolCancel()
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
