package app.screenscratch.ui.editor

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarObstacleTest {
    // A 900 × 700 dp window with the back pill top-left, the undo pill top-right and the chips bottom-right.
    private val back = Rect(16f, 16f, 336f, 68f)
    private val undo = Rect(740f, 16f, 884f, 70f)
    private val chips = Rect(700f, 648f, 884f, 684f)
    private val pills = listOf(back, undo, chips)

    @Test
    fun aTopBarThatFitsBetweenThePillsStays() {
        val (x, y) = ToolbarGeometry.topLeft(null, 200f, 58f, 900f, 700f, pills)
        assertEquals(350f, x)
        assertEquals(ToolbarGeometry.MARGIN, y)
    }

    @Test
    fun aTopBarThatWouldCoverAPillMovesBelowIt() {
        val (_, y) = ToolbarGeometry.topLeft(null, 600f, 58f, 900f, 700f, pills)
        assertEquals(back.bottom + ToolbarGeometry.MARGIN, y)
        // Past every pill it overlaps, whichever is lower.
        val tallUndo = undo.copy(bottom = 120f)
        val (_, wide) = ToolbarGeometry.topLeft(null, 860f, 58f, 900f, 700f, listOf(back, tallUndo))
        assertEquals(tallUndo.bottom + ToolbarGeometry.MARGIN, wide)
    }

    @Test
    fun aBottomBarMovesAboveTheChips() {
        val (_, y) = ToolbarGeometry.topLeft(ToolbarPlacement.Docked(Edge.Bottom, 700f), 400f, 58f, 900f, 700f, pills)
        assertEquals(chips.top - ToolbarGeometry.MARGIN - 58f, y)
    }

    @Test
    fun aRightRailStartsBelowTheUndoPill() {
        val (_, y) = ToolbarGeometry.topLeft(ToolbarPlacement.Docked(Edge.Right, 100f), 58f, 400f, 900f, 700f, pills)
        assertEquals(undo.bottom + ToolbarGeometry.MARGIN, y)
    }
}
