package app.blot.canvas

import app.blot.core.model.PageSetup
import app.blot.core.model.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportTest {
    private val eps = 1e-3f

    @Test
    fun mapsBothWays() {
        val v = Viewport(zoom = 2f, offsetX = 30f, offsetY = -12f, density = 1.275f)
        assertEquals(2.55f, v.scale, eps)
        assertEquals(30f + 10f * 2.55f, v.toScreenX(10f), eps)
        assertEquals(-12f + 4f * 2.55f, v.toScreenY(4f), eps)
        assertEquals(10f, v.toCanvasX(v.toScreenX(10f)), eps)
        assertEquals(-7f, v.toCanvasY(v.toScreenY(-7f)), eps)
    }

    @Test
    fun visibleRectIsTheViewInCanvasUnits() {
        val v = Viewport(zoom = 2f, offsetX = -100f, offsetY = 50f, density = 1f)
        assertEquals(Rect(50f, -25f, 400f, 300f), v.visibleRect(800, 600))
    }

    @Test
    fun zoomKeepsTheFocusPointStill() {
        val v = Viewport(zoom = 1.5f, offsetX = 40f, offsetY = 70f, density = 2f)
        val cx = v.toCanvasX(300f)
        val cy = v.toCanvasY(200f)
        val z = v.zoomBy(1.7f, 300f, 200f)
        assertEquals(1.5f * 1.7f, z.zoom, eps)
        assertEquals(300f, z.toScreenX(cx), eps)
        assertEquals(200f, z.toScreenY(cy), eps)
    }

    @Test
    fun zoomIsClampedAndStillKeepsTheFocus() {
        val v = Viewport(zoom = 7f, density = 1f)
        val cx = v.toCanvasX(100f)
        val z = v.zoomBy(10f, 100f, 100f)
        assertEquals(Viewport.MAX_ZOOM, z.zoom, eps)
        assertEquals(100f, z.toScreenX(cx), eps)
        assertEquals(Viewport.MIN_ZOOM, v.zoomBy(1e-4f, 0f, 0f).zoom, eps)
        assertEquals(3f, v.zoomTo(3f, 5f, 5f).zoom, eps)
    }

    @Test
    fun pinchStepKeepsTheGrabbedPointUnderTheFingers() {
        val v = Viewport(zoom = 1f, offsetX = 10f, offsetY = 20f, density = 1.5f)
        // Fingers centred on (200, 150) move to (260, 130) and spread by 1.25.
        val cx = v.toCanvasX(200f)
        val cy = v.toCanvasY(150f)
        val t = v.transform(60f, -20f, 1.25f, 260f, 130f)
        assertEquals(1.25f, t.zoom, eps)
        assertEquals(260f, t.toScreenX(cx), eps)
        assertEquals(130f, t.toScreenY(cy), eps)
    }

    @Test
    fun panMovesContentByPixels() {
        val v = Viewport(zoom = 3f, density = 2f).panBy(12f, -5f)
        assertEquals(12f, v.toScreenX(0f), eps)
        assertEquals(-5f, v.toScreenY(0f), eps)
    }

    @Test
    fun fitCentresTheRectInsideTheMargin() {
        val r = Rect(100f, 200f, 400f, 100f)
        val v = Viewport.fit(r, 1000, 600, density = 2f, margin = 50f)
        // Width-limited: 900 px for 400 units.
        assertEquals(900f / 400f / 2f, v.zoom, eps)
        assertEquals(50f, v.toScreenX(r.left), eps)
        assertEquals(950f, v.toScreenX(r.right), eps)
        assertEquals(300f, v.toScreenY(r.centerY), eps)
    }

    @Test
    fun fitRespectsMaxZoomAndLimits() {
        val tiny = Rect(0f, 0f, 1f, 1f)
        assertEquals(1f, Viewport.fit(tiny, 1000, 1000, 1f, maxZoom = 1f).zoom, eps)
        val huge = Rect(0f, 0f, 1e6f, 1e6f)
        assertEquals(Viewport.MIN_ZOOM, Viewport.fit(huge, 1000, 1000, 1f).zoom, eps)
    }

    @Test
    fun fitWidthPutsTheTopAtTheMargin() {
        val page = PageSetup.Pages.a4().pageRect(0)
        val v = Viewport.fitWidth(page, 2000, density = 1.275f, margin = 30f)
        assertEquals(30f, v.toScreenX(page.left), 0.01f)
        assertEquals(1970f, v.toScreenX(page.right), 0.01f)
        assertEquals(30f, v.toScreenY(page.top), 0.01f)
    }

    @Test
    fun visiblePagesFollowThePageGrid() {
        val pages = PageSetup.Pages(app.blot.core.model.Paper.SCREEN, 100f, 200f, count = 5)
        // Pitch 224: page i spans [224 i, 224 i + 200].
        assertEquals(0..0, visiblePages(pages, Rect(0f, 0f, 100f, 100f)))
        assertEquals(0..1, visiblePages(pages, Rect(0f, 150f, 100f, 100f)))
        assertTrue(visiblePages(pages, Rect(0f, 201f, 100f, 22f)).isEmpty()) // in the gap
        assertEquals(1..4, visiblePages(pages, Rect(0f, 300f, 100f, 1e5f)))
        assertTrue(visiblePages(pages, Rect(150f, 0f, 10f, 1000f)).isEmpty()) // right of the pages
        assertTrue(visiblePages(pages, Rect(0f, -500f, 100f, 100f)).isEmpty()) // above
        assertEquals(Rect(0f, 0f, 100f, 5 * 200f + 4 * 24f), pagesBounds(pages))
    }
}
