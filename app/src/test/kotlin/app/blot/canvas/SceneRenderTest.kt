package app.blot.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import app.blot.core.model.Argb
import app.blot.core.model.Arrowhead
import app.blot.core.model.AssetRef
import app.blot.core.model.Element
import app.blot.core.model.Image
import app.blot.core.model.Line
import app.blot.core.model.Note
import app.blot.core.model.PageSetup
import app.blot.core.model.Point
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import app.blot.core.model.Stroke
import app.blot.core.model.StrokePoint
import app.blot.core.model.TextBox
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
import kotlin.math.cos
import kotlin.math.sin

/** Renders a sample note to bitmaps under `app/build/outputs/canvas-render/` and checks a few pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneRenderTest {
    private val outDir = File("build/outputs/canvas-render").apply { mkdirs() }
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")
    private val asset = AssetRef("a".repeat(64), "png")
    private val missing = AssetRef("b".repeat(64), "png")

    private val picture: Bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) for (x in 0 until width) {
            setPixel(x, y, Color.rgb(x * 4, 40 + y * 3, 200 - x * 2))
        }
    }
    private val images = AssetImages { ref, _ -> if (ref == asset) picture else null }

    private fun stroke(id: String, color: Int, width: Float, reported: Boolean = true, f: (Float) -> Triple<Float, Float, Float>): Stroke {
        val n = 80
        val pts = List(n) { i ->
            val (x, y, p) = f(i / (n - 1f))
            StrokePoint(StrokeBuilder.snapXy(x), StrokeBuilder.snapXy(y), StrokeBuilder.snapPressure(p), timeMs = i * 4L)
        }
        return Stroke(id, pts, Argb(color), width, pressureReported = reported)
    }

    private fun sampleElements(): List<Element> = listOf(
        Shape("rect", ShapeKind.RECTANGLE, Rect(40f, 300f, 220f, 120f), Argb(0xFF333333.toInt()), 2f, fill = Argb(0xFFFFF59D.toInt())),
        Shape("oval", ShapeKind.ELLIPSE, Rect(320f, 300f, 200f, 110f), Argb(0xFF1E5BD8.toInt()), 3f, rotation = (PI / 8).toFloat()),
        stroke("wave", 0xFF000000.toInt(), 3f) { t ->
            Triple(40f + t * 480f, 120f + 40f * sin(t * 4 * PI).toFloat(), 0.3f + 0.6f * sin(t * PI).toFloat())
        },
        stroke("loop", 0xFFE53935.toInt(), 4f) { t ->
            val a = t * 2 * PI
            Triple(620f + 60f * cos(a).toFloat(), 140f + 60f * sin(a).toFloat(), 0.2f + 0.8f * t)
        },
        stroke("zig", 0xFF1E5BD8.toInt(), 2.5f) { t ->
            val k = (t * 8).toInt()
            val frac = t * 8 - k
            Triple(40f + t * 480f, 220f + (if (k % 2 == 0) frac else 1 - frac) * 40f, 0.6f)
        },
        stroke("simulated", 0xFF2E7D32.toInt(), 3f, reported = false) { t ->
            Triple(560f + t * 200f, 250f + 30f * sin(t * 2 * PI).toFloat(), 0f)
        },
        Line("arrow", listOf(Point(40f, 470f), Point(300f, 470f), Point(360f, 520f)), Argb(0xFF6A1B9A.toInt()), 2f,
            startArrow = Arrowhead.TRIANGLE, endArrow = Arrowhead.ARROW),
        TextBox("text", Rect(400f, 440f, 300f, 100f), "Plain text box\n**markdown** shows as source", 16f, Argb(0xFF222222.toInt())),
        Image("img", asset, Rect(600f, 330f, 128f, 96f)),
        Image("missing", missing, Rect(740f, 330f, 64f, 64f)),
        stroke("far", 0xFF000000.toInt(), 3f) { t -> Triple(5000f + t * 10f, 5000f, 0.5f) },
    )

    private fun note(page: PageSetup) = Note("n", created = t0, modified = t0, page = page, elements = sampleElements())

    private fun render(note: Note, viewport: Viewport, dark: Boolean, name: String, w: Int = 900, h: Int = 600): Pair<Bitmap, SceneRenderer> {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val r = SceneRenderer(OutlineCache(), images = images)
        r.draw(Canvas(bmp), note, viewport, w, h, dark)
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp to r
    }

    private fun near(expected: Int, actual: Int, tol: Int = 24): Boolean =
        listOf(16, 8, 0).all { abs(((expected shr it) and 0xFF) - ((actual shr it) and 0xFF)) <= tol }

    private fun hex(c: Int) = "%08X".format(c)

    @Test
    fun infiniteCanvasLightAndDark() {
        val n = note(PageSetup.Infinite)
        val v = Viewport(zoom = 1f, offsetX = 20f, offsetY = 0f, density = 1f)
        val (light, r) = render(n, v, dark = false, "infinite-light")
        val (dark, _) = render(n, v, dark = true, "infinite-dark")
        assertEquals("the far stroke is culled", sampleElements().size - 1, r.lastDrawnCount)

        // Background is the paper.
        assertEquals(CanvasPalette.LIGHT.paper, light.getPixel(5, 5))
        assertEquals(CanvasPalette.DARK.paper, dark.getPixel(5, 5))

        // The black wave's start (canvas 40, 120) is black in light and near-white in dark.
        val x = v.toScreenX(40.5f).toInt()
        val y = v.toScreenY(120f).toInt()
        assertTrue(hex(light.getPixel(x, y)), near(0xFF000000.toInt(), light.getPixel(x, y), 40))
        assertTrue(hex(dark.getPixel(x, y)), near(InkColors.darkMode(0xFF000000.toInt()), dark.getPixel(x, y), 40))

        // The rectangle's fill goes through the transform; the image does not.
        val fx = v.toScreenX(150f).toInt()
        val fy = v.toScreenY(360f).toInt()
        assertTrue(near(0xFFFFF59D.toInt(), light.getPixel(fx, fy), 4))
        assertTrue(near(InkColors.darkMode(0xFFFFF59D.toInt()), dark.getPixel(fx, fy), 4))
        val ix = v.toScreenX(664f).toInt()
        val iy = v.toScreenY(378f).toInt()
        assertEquals(hex(light.getPixel(ix, iy)), hex(dark.getPixel(ix, iy)))
        assertTrue(near(picture.getPixel(32, 24), light.getPixel(ix, iy), 12))
    }

    @Test
    fun pagesLightAndDark() {
        val pages = PageSetup.Pages.a4(count = 2)
        val n = note(pages)
        // Zoomed out to show both pages, the gap and the desk around them.
        val v = Viewport.fit(pagesBounds(pages), 900, 1000, density = 1f, margin = 20f)
        val (light, _) = render(n, v, dark = false, "pages-light", 900, 1000)
        val (dark, _) = render(n, v, dark = true, "pages-dark", 900, 1000)
        assertEquals(CanvasPalette.LIGHT.desk, light.getPixel(3, 500))
        assertEquals(CanvasPalette.DARK.desk, dark.getPixel(3, 500))
        val px = v.toScreenX(1200f).toInt()
        val py = v.toScreenY(1000f).toInt()
        assertEquals(CanvasPalette.LIGHT.paper, light.getPixel(px, py))
        assertEquals(CanvasPalette.DARK.paper, dark.getPixel(px, py))
        // The gap between the pages shows the desk (away from the shadow).
        val gx = v.toScreenX(pages.width / 2).toInt()
        val gy = v.toScreenY(pages.height + PageSetup.Pages.PAGE_GAP / 2).toInt()
        assertTrue(hex(light.getPixel(gx, gy)), !near(CanvasPalette.LIGHT.paper, light.getPixel(gx, gy), 2))
    }

    @Test
    fun closeUpOfInk() {
        // 4× on the loop and wave, for judging stroke edges by eye.
        val n = note(PageSetup.Infinite)
        val v = Viewport(zoom = 4f, density = 1f).panBy(-480f * 4 + 100f, -60f * 4 + 50f)
        render(n, v, dark = false, "closeup-light")
        render(n, v, dark = true, "closeup-dark")
    }

    @Test
    fun liveInkMatchesTheFinishedOutlineWidth() {
        // The in-progress ink and the finished outline of the same samples, overlaid: live in red, final in black.
        val w = 600
        val h = 300
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val b = StrokeBuilder(Argb.BLACK, 6f, pressureReported = true)
        for (i in 0 until 120) {
            val t = i / 119f
            b.add(40f + t * 520f, 150f + 80f * sin(t * 3 * PI).toFloat(), 0.2f + 0.8f * t, 0f, 0f, i * 4L)
        }
        val live = LiveInkPainter()
        for (seg in b.segments()) live.draw(c, seg, 0xFFE53935.toInt())
        val r = SceneRenderer(OutlineCache())
        val stroke = b.build("s")!!
        r.drawElement(c, stroke, dark = false)
        File(outDir, "live-vs-final.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Almost no red should show around the black outline.
        var red = 0
        var black = 0
        for (y in 0 until h) for (x in 0 until w) {
            val p = bmp.getPixel(x, y)
            if (near(0xFFE53935.toInt(), p, 30)) red++
            if (near(0xFF000000.toInt(), p, 60)) black++
        }
        assertTrue("red $red vs black $black", red < black / 20)
    }
}
