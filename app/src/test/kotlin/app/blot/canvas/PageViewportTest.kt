package app.blot.canvas

import app.blot.core.model.Note
import app.blot.core.model.Orientation
import app.blot.core.model.PageSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant

class PageViewportTest {
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")
    private val a4 = PageSetup.Pages.a4(Orientation.PORTRAIT, 1)

    private fun controller(): CanvasController =
        CanvasController(Note("n", created = t0, modified = t0, page = a4)).apply { onViewSize(2000, 1200, 1.275f) }

    @Test
    fun addingAPageScrollsToItAtTheSameZoom() {
        val c = controller()
        c.zoomTo(1.5f)
        val before = c.viewport
        c.setPage(a4.copy(count = 2))
        val v = c.viewport
        assertEquals(before.zoom, v.zoom, 0f)
        assertEquals(before.offsetX, v.offsetX, 0f)
        val top = (c.note.value.page as PageSetup.Pages).pageRect(1).y
        assertEquals(CanvasController.FIT_MARGIN_DP * v.density, v.toScreenY(top), 0.01f)
    }

    @Test
    fun removingAPageLeavesTheViewAlone() {
        val c = controller()
        c.setPage(a4.copy(count = 3))
        c.panBy(0f, -300f)
        val before = c.viewport
        c.setPage(a4.copy(count = 2))
        assertEquals(before, c.viewport)
    }

    @Test
    fun changingOrientationStillRefits() {
        val c = controller()
        c.zoomTo(3f)
        c.setPage(PageSetup.Pages.a4(Orientation.LANDSCAPE, 1))
        assertNotEquals(3f, c.viewport.zoom)
    }
}
