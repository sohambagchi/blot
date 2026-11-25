package app.blot.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import app.blot.core.model.Argb
import app.blot.core.model.Background
import app.blot.core.model.Note
import app.blot.core.model.PageSetup
import app.blot.core.model.Pattern
import app.blot.core.model.Stroke
import app.blot.core.model.StrokePoint
import app.blot.shell.ThumbnailRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import kotlin.math.sin

/**
 * Renders every background pattern on an infinite canvas and on A4 pages, light and dark, at 100%
 * and 25%, to `app/build/outputs/canvas-render/background-*.png`, and times a full-screen grid.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackgroundRenderTest {
    private val outDir = File("build/outputs/canvas-render").apply { mkdirs() }
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")

    // The tablet: 2000 × 1200 px at density 1.275.
    private val w = 2000
    private val h = 1200
    private val density = 1.275f

    private val ink = Stroke(
        "wave",
        List(60) { i -> StrokePoint(80f + i * 8f, 160f + 40f * sin(i / 6f), 0.6f, timeMs = i * 4L) },
        Argb(0xFF1C1B19.toInt()), 3f,
    )

    private fun note(pattern: Pattern?, page: PageSetup) =
        Note("n", created = t0, modified = t0, page = page, elements = listOf(ink), background = pattern?.let { Background(it) })

    private fun render(note: Note, v: Viewport, dark: Boolean, name: String? = null, r: SceneRenderer = SceneRenderer(OutlineCache())): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        r.draw(Canvas(bmp), note, v, w, h, dark)
        if (name != null) File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun hex(c: Int) = "%08X".format(c)

    @Test
    fun everyPatternRendersOnBothLayouts() {
        val a4 = PageSetup.Pages.a4(count = 2)
        for (pattern in Pattern.entries) for (paged in listOf(false, true)) for (dark in listOf(false, true)) for (zoom in listOf(1f, 0.25f)) {
            val page = if (paged) a4 else PageSetup.Infinite
            val v = Viewport(zoom, 40f, 40f, density)
            val name = "background-${pattern.name.lowercase()}-${if (paged) "a4" else "infinite"}-${if (dark) "dark" else "light"}-${(zoom * 100).toInt()}"
            val bmp = render(note(pattern, page), v, dark, name)
            val palette = CanvasPalette.of(dark)
            val mark = if (pattern == Pattern.DOTS) palette.patternDot else palette.patternLine
            assertTrue(name, (0 until w step 3).any { x -> (0 until h step 3).any { y -> bmp.getPixel(x, y) == mark } })
        }
    }

    @Test
    fun marksSitWhereTheGeometrySays() {
        val v = Viewport(1f, 0f, 0f, 1f)
        val grid = render(note(Pattern.GRID, PageSetup.Infinite), v, false)
        assertEquals(hex(CanvasPalette.LIGHT.patternLine), hex(grid.getPixel(28, 500)))
        assertEquals(hex(CanvasPalette.LIGHT.paper), hex(grid.getPixel(40, 500)))

        // Ruled lines on a page: none above the first, one spacing below the top.
        val pages = PageSetup.Pages.a4()
        val ruled = render(note(Pattern.RULED, pages), v, false)
        assertEquals(hex(CanvasPalette.LIGHT.paper), hex(ruled.getPixel(600, 30)))
        assertEquals(hex(CanvasPalette.LIGHT.patternLine), hex(ruled.getPixel(600, 45)))
        // Nothing on the desk beside the page.
        assertEquals(hex(CanvasPalette.LIGHT.desk), hex(ruled.getPixel(1500, 45)))

        val none = render(note(null, PageSetup.Infinite), v, false)
        assertEquals(hex(CanvasPalette.LIGHT.paper), hex(none.getPixel(28, 500)))
    }

    @Test
    fun darkModeLeavesThePatternOutOfTheInkTransform() {
        val v = Viewport(1f, 0f, 0f, 1f)
        val dark = render(note(Pattern.GRID, PageSetup.Infinite), v, true)
        assertEquals(hex(CanvasPalette.DARK.patternLine), hex(dark.getPixel(28, 500)))
        assertNotEquals(CanvasPalette.DARK.patternLine, InkColors.darkMode(CanvasPalette.LIGHT.patternLine))
    }

    @Test
    fun thumbnailsShowThePatternAndExportPreviewsDont() {
        val n = note(Pattern.GRID, PageSetup.Infinite)
        val renderer = SceneRenderer(OutlineCache())
        val line = CanvasPalette.LIGHT.patternLine
        fun hasLines(b: Bitmap) = (0 until b.width).any { x -> (0 until b.height).any { y -> b.getPixel(x, y) == line } }
        val thumb = ThumbnailRenderer.render(n, false, renderer)!!
        File(outDir, "background-thumbnail.png").outputStream().use { thumb.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(hasLines(thumb))
        assertTrue(!hasLines(ThumbnailRenderer.render(n, false, renderer, pattern = false)!!))
    }

    /** Prints the scene render time with a full-screen pattern; the numbers go in the change notes. */
    @Test
    fun fullScreenSceneTimes() {
        val r = SceneRenderer(OutlineCache())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val lines = StringBuilder()
        for (pattern in listOf(null) + Pattern.entries) for (zoom in listOf(1f, 0.2f)) {
            val n = note(pattern, PageSetup.Infinite)
            val v = Viewport(zoom, 0f, 0f, density)
            repeat(20) { r.draw(canvas, n, v, w, h, false) }
            val runs = 100
            val start = System.nanoTime()
            repeat(runs) { r.draw(canvas, n, v, w, h, false) }
            val ms = (System.nanoTime() - start) / 1e6 / runs
            lines.append("scene ${pattern?.name?.lowercase() ?: "none"} at ${(zoom * 100).toInt()}%%: %.3f ms\n".format(ms))
        }
        println(lines)
        File(outDir, "background-times.txt").writeText(lines.toString())
    }
}
