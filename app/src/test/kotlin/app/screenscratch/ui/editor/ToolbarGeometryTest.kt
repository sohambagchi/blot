package app.screenscratch.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarGeometryTest {
    private val cw = 1569f
    private val ch = 941f
    private val w = 606f
    private val h = 58f

    @Test
    fun defaultIsTopCentreInLandscapeAndLeftRailInPortrait() {
        assertEquals((cw - w) / 2f to 16f, ToolbarGeometry.topLeft(null, w, h, cw, ch))
        assertFalse(ToolbarGeometry.isVertical(null, cw, ch))
        assertEquals(16f to 84f, ToolbarGeometry.topLeft(null, 58f, 606f, 941f, 1569f))
        assertTrue(ToolbarGeometry.isVertical(null, 941f, 1569f))
    }

    @Test
    fun dropNearAnEdgeDocksThere() {
        assertEquals(ToolbarPlacement.Docked(Edge.Top, 500f + w / 2), ToolbarGeometry.drop(500f, 30f, w, h, cw, ch, false))
        assertEquals(ToolbarPlacement.Docked(Edge.Bottom, 400f + w / 2), ToolbarGeometry.drop(400f, ch - h - 20f, w, h, cw, ch, false))
        assertEquals(ToolbarPlacement.Docked(Edge.Left, 300f + h / 2), ToolbarGeometry.drop(10f, 300f, w, h, cw, ch, false))
        assertEquals(ToolbarPlacement.Docked(Edge.Right, 200f + h / 2), ToolbarGeometry.drop(cw - w - 40f, 200f, w, h, cw, ch, false))
    }

    @Test
    fun dropFarFromEdgesFloatsAndKeepsOrientation() {
        val p = ToolbarGeometry.drop(400f, 300f, w, h, cw, ch, false)
        assertEquals(ToolbarPlacement.Floating(400f, 300f, false), p)
        assertFalse(p.vertical)
        assertTrue(ToolbarGeometry.drop(400f, 300f, h, w, 1569f, 1569f, true).vertical)
    }

    @Test
    fun theNearestEdgeWinsInACorner() {
        assertEquals(Edge.Left, (ToolbarGeometry.drop(5f, 40f, w, h, cw, ch, false) as ToolbarPlacement.Docked).edge)
        assertEquals(Edge.Top, (ToolbarGeometry.drop(40f, 5f, w, h, cw, ch, false) as ToolbarPlacement.Docked).edge)
    }

    @Test
    fun dockingToASideMakesARailAndToTopOrBottomAPill() {
        assertTrue(ToolbarPlacement.Docked(Edge.Left, 0f).vertical)
        assertTrue(ToolbarPlacement.Docked(Edge.Right, 0f).vertical)
        assertFalse(ToolbarPlacement.Docked(Edge.Top, 0f).vertical)
        assertFalse(ToolbarPlacement.Docked(Edge.Bottom, 0f).vertical)
    }

    @Test
    fun placementsAreClampedIntoTheWindow() {
        assertEquals(16f to 16f, ToolbarGeometry.topLeft(ToolbarPlacement.Docked(Edge.Top, 0f), w, h, cw, ch))
        assertEquals(cw - w - 16f to ch - h - 16f, ToolbarGeometry.topLeft(ToolbarPlacement.Docked(Edge.Bottom, 5000f), w, h, cw, ch))
        assertEquals(cw - h - 16f to 16f, ToolbarGeometry.topLeft(ToolbarPlacement.Docked(Edge.Right, -10f), h, w, cw, ch))
        // A landscape placement after rotating to portrait stays on screen.
        assertEquals(941f - w - 16f to 16f, ToolbarGeometry.topLeft(ToolbarPlacement.Floating(1200f, -40f, false), w, h, 941f, 1569f))
    }

    @Test
    fun menusOpenAwayFromTheDockedEdge() {
        assertEquals(Edge.Bottom, ToolbarGeometry.opensTowards(null, cw, ch))
        assertEquals(Edge.Right, ToolbarGeometry.opensTowards(null, 941f, 1569f))
        assertEquals(Edge.Top, ToolbarGeometry.opensTowards(ToolbarPlacement.Docked(Edge.Bottom, 0f), cw, ch))
        assertEquals(Edge.Left, ToolbarGeometry.opensTowards(ToolbarPlacement.Docked(Edge.Right, 0f), cw, ch))
        assertEquals(Edge.Top, ToolbarGeometry.opensTowards(ToolbarPlacement.Floating(100f, 800f, false), cw, ch))
        assertEquals(Edge.Left, ToolbarGeometry.opensTowards(ToolbarPlacement.Floating(1300f, 100f, true), cw, ch))
    }
}
