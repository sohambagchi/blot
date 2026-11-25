package app.blot.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import app.blot.core.edit.Similarity
import app.blot.core.geometry.CurveFit
import app.blot.core.geometry.ShapeOutlines
import app.blot.core.model.Argb
import app.blot.core.model.Arrowhead
import app.blot.core.model.Line
import app.blot.core.model.LineRoute
import app.blot.core.model.Note
import app.blot.core.model.Point
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
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
 * Renders every shape kind (outlined, filled, turned and in a square frame) and every line route
 * with arrowheads, drawn by driving the tools as the pen would, to
 * `app/build/outputs/canvas-render/shapes-routes-{light,dark}.png`, plus a curve and an elbow
 * mid-drag. A few pixels are checked; the images are for comparing with the design's glyphs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShapesAndRoutesRenderTest {
    private val outDir = File("build/outputs/canvas-render").apply { mkdirs() }
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")
    private val w = 1560
    private val h = 1080
    private val density = 1.5f

    private val ink = Argb(0xFF222222.toInt())
    private val red = Argb(0xFFE53935.toInt())
    private val blue = Argb(0xFF1E5BD8.toInt())
    private val yellow = Argb(0xFFFFF59D.toInt())

    private val step = 128f
    private val size = 104f
    private fun col(i: Int) = 20f + i * step

    private fun CanvasController.drag(vararg xy: Float) {
        toolStart(xy[0], xy[1])
        for (i in 1 until xy.size / 2) toolMove(xy[i * 2], xy[i * 2 + 1])
        toolEnd()
    }

    /** An arc of samples from ([x0], [y0]) to ([x1], [y1]) bulging by [bulge] to the right of the drag, then back by [wiggle] (an S). */
    private fun arcDrag(x0: Float, y0: Float, x1: Float, y1: Float, bulge: Float, wiggle: Float = 0f): FloatArray {
        val n = 60
        val dx = x1 - x0
        val dy = y1 - y0
        val len = kotlin.math.hypot(dx, dy)
        return FloatArray(n * 2) { i ->
            val t = (i / 2) / (n - 1f)
            val off = bulge * sin(PI * t).toFloat() - wiggle * sin(2 * PI * t).toFloat()
            if (i % 2 == 0) x0 + dx * t - dy / len * off else y0 + dy * t + dx / len * off
        }
    }

    private fun drawnByTools(): CanvasController {
        val c = CanvasController(Note("n", created = t0, modified = t0))
        c.onViewSize(w, h, density)
        c.tool = Tool.SHAPE
        c.width = 3f
        for ((i, kind) in ShapeKind.entries.withIndex()) {
            c.shapeKind = kind
            c.color = ink
            c.shapeFill = null
            c.drag(col(i), 20f, col(i) + size, 20f + size * 0.85f)
            c.color = red
            c.shapeFill = yellow
            c.drag(col(i), 140f, col(i) + size, 140f + size * 0.85f)
            // Drawn, then turned with the pointer's rotate.
            c.color = blue
            c.shapeFill = null
            c.drag(col(i), 270f, col(i) + size, 270f + size * 0.6f)
            val id = c.note.value.elements.last().id
            c.transformSelection(setOf(id), Similarity.rotate(0.35f, col(i) + size / 2, 270f + size * 0.3f))
            // A square frame.
            c.constrainShapes = true
            c.color = ink
            c.drag(col(i), 390f, col(i) + size * 0.8f, 390f + size * 0.5f)
            c.constrainShapes = false
        }

        c.tool = Tool.LINE
        c.width = 2.5f
        c.color = ink
        c.snapAngles = true
        // Straight: none, end, both.
        for ((i, arrows) in LineArrows.entries.withIndex()) {
            c.lineRoute = LineRoute.STRAIGHT
            c.lineArrows = arrows
            c.drag(col(i), 610f, col(i) + size, 520f)
        }
        // Elbows, wide and tall, V and triangle heads.
        c.lineRoute = LineRoute.ELBOW
        c.lineArrows = LineArrows.END
        c.drag(col(3), 610f, col(3) + size, 520f)
        c.arrowhead = Arrowhead.TRIANGLE
        c.lineArrows = LineArrows.BOTH
        c.drag(col(4), 520f, col(4) + size * 0.6f, 615f)
        // Curves: a bow with a V, an S with triangles, and a straight drag.
        c.lineRoute = LineRoute.CURVE
        c.arrowhead = Arrowhead.ARROW
        c.lineArrows = LineArrows.END
        c.color = blue
        c.drag(*arcDrag(col(5), 610f, col(5) + size, 520f, 40f))
        c.arrowhead = Arrowhead.TRIANGLE
        c.lineArrows = LineArrows.BOTH
        c.drag(*arcDrag(col(6), 610f, col(6) + size, 520f, 0f, wiggle = 30f))
        c.lineArrows = LineArrows.END
        c.arrowhead = Arrowhead.ARROW
        c.drag(*arcDrag(col(7), 610f, col(7) + size, 520f, 0f))
        c.select(emptySet())
        return c
    }

    private fun render(c: CanvasController, name: String, dark: Boolean = false): Bitmap {
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
    fun everyShapeAndRouteLightAndDark() {
        val c = drawnByTools()
        val shapes = c.note.value.elements.filterIsInstance<Shape>()
        val lines = c.note.value.elements.filterIsInstance<Line>()
        assertEquals(ShapeKind.entries.size * 4, shapes.size)
        assertEquals(8, lines.size)
        assertEquals(ShapeKind.entries.toSet(), shapes.map { it.kind }.toSet())
        // Constrained frames are square; turned ones keep their turn.
        for (s in shapes.filter { it.frame.top >= 380f }) assertEquals(s.frame.width, s.frame.height, 1e-3f)
        assertEquals(ShapeKind.entries.size, shapes.count { it.rotation == 0.35f })

        val light = render(c, "shapes-routes-light")
        val dark = render(c, "shapes-routes-dark", dark = true)

        val tri = shapes.single { it.kind == ShapeKind.TRIANGLE && it.fill != null }
        // Inside the filled triangle, and outside it in its frame's corner.
        assertTrue(hex(px(light, c, tri.frame.centerX, tri.frame.centerY + 10f)), near(yellow.value, px(light, c, tri.frame.centerX, tri.frame.centerY + 10f), 4))
        assertEquals(CanvasPalette.LIGHT.paper, px(light, c, tri.frame.left + 4f, tri.frame.top + 4f))
        assertEquals(CanvasPalette.DARK.paper, px(dark, c, tri.frame.left + 4f, tri.frame.top + 4f))
        val darkYellow = InkColors.darkMode(yellow.value)
        assertTrue(hex(px(dark, c, tri.frame.centerX, tri.frame.centerY + 10f)), near(darkYellow, px(dark, c, tri.frame.centerX, tri.frame.centerY + 10f), 4))

        // The cylinder shows its top cap's front edge but not the bottom cap's back.
        val cyl = shapes.single { it.kind == ShapeKind.CYLINDER && it.fill == null && it.rotation == 0f && it.frame.top < 100f }
        val ry = ShapeOutlines.cylinderCap(cyl.frame)
        assertTrue(hex(px(light, c, cyl.frame.centerX, cyl.frame.top + 2 * ry)), near(ink.value, px(light, c, cyl.frame.centerX, cyl.frame.top + 2 * ry), 60))
        assertEquals(CanvasPalette.LIGHT.paper, px(light, c, cyl.frame.centerX, cyl.frame.bottom - 2 * ry))

        // The wide elbow's middle segment is vertical, halfway across.
        val elbow = lines.first { it.route == LineRoute.ELBOW }
        assertEquals(4, elbow.points.size)
        assertEquals(elbow.points[1].x, elbow.points[2].x)
        assertTrue(near(ink.value, px(light, c, elbow.points[1].x, (elbow.points[1].y + elbow.points[2].y) / 2), 60))

        // Curves keep a handful of points and their drag's ends.
        val curves = lines.filter { it.route == LineRoute.CURVE }
        assertEquals(3, curves.size)
        for (l in curves) assertTrue(l.points.size in 3..CurveFit.MAX_POINTS)
        assertEquals(Point(col(5), 610f), curves[0].points.first())
        assertEquals(Point(col(5) + size, 520f), curves[0].points.last())
        assertTrue(curves[0].endArrow == Arrowhead.ARROW && curves[1].startArrow == Arrowhead.TRIANGLE)
    }

    @Test
    fun linesMidDrag() {
        val c = CanvasController(Note("n", created = t0, modified = t0))
        c.onViewSize(800, 600, density)
        c.tool = Tool.LINE
        c.color = blue
        c.width = 3f
        c.lineRoute = LineRoute.CURVE
        val xy = arcDrag(40f, 300f, 400f, 120f, 70f)
        c.toolStart(xy[0], xy[1])
        for (i in 1 until 40) c.toolMove(xy[i * 2], xy[i * 2 + 1])
        val draft = c.toolGestures.draft as Line
        assertEquals(LineRoute.CURVE, draft.route)
        assertEquals(Point(xy[78], xy[79]), draft.points.last())
        render(c, "shapes-routes-curve-drafting")
        c.toolCancel()
        assertTrue(c.note.value.elements.isEmpty())

        c.lineRoute = LineRoute.ELBOW
        c.toolStart(60f, 60f)
        c.toolMove(300f, 260f)
        val elbow = c.toolGestures.draft as Line
        assertEquals(LineRoute.ELBOW, elbow.route)
        assertEquals(4, elbow.points.size)
        render(c, "shapes-routes-elbow-drafting")
        // Snapping straightens an elbow dragged nearly level.
        c.toolMove(300f, 66f)
        assertEquals(listOf(Point(60f, 60f), Point(300f, 60f)), (c.toolGestures.draft as Line).points)
        c.snapAngles = false
        c.toolMove(300f, 66f)
        assertEquals(4, (c.toolGestures.draft as Line).points.size)
        c.toolEnd()
        assertEquals(1, c.note.value.elements.size)
    }
}
