package app.blot.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.blot.core.model.Argb
import app.blot.ui.LAND
import app.blot.ui.preview.DotGrid
import app.blot.ui.theme.QuickInk
import app.blot.ui.theme.BlotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = LAND)
class EditorBehaviourTest {
    @get:Rule
    val compose = createComposeRule()

    private val handle = "Toolbar handle: drag to move, tap to collapse"

    private fun show(editor: InMemoryEditor) {
        compose.setContent {
            BlotTheme(false) {
                Box(Modifier.size(1569.dp, 941.dp)) { EditorChrome(editor.state, editor) { DotGrid() } }
            }
        }
    }

    @Test
    fun handleCollapsesAndEachPartExpandsWithItsOwnArrow() {
        val e = InMemoryEditor()
        show(e)
        compose.onNodeWithContentDescription("Text box").assertExists()
        compose.onNodeWithContentDescription(handle).performClick()
        assertTrue(e.state.toolbarCollapsed)
        assertTrue(e.state.cornerCollapsed)
        compose.onNodeWithContentDescription("Text box").assertDoesNotExist()
        compose.onNodeWithContentDescription("Redo").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pen tools: Pen").assertExists()
        // The compact toolbar has an arrow instead of the handle, so it can't be dragged.
        compose.onNodeWithContentDescription(handle).assertDoesNotExist()
        compose.onNodeWithContentDescription("Expand toolbar").performClick()
        assertFalse(e.state.toolbarCollapsed)
        compose.onNodeWithContentDescription("Text box").assertExists()
        // The corner pills stay compact until their own arrow is tapped.
        compose.onNodeWithContentDescription("Redo").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show redo, background and export").performClick()
        assertFalse(e.state.cornerCollapsed)
        compose.onNodeWithContentDescription("Redo").assertExists()
        compose.onNodeWithContentDescription("Show redo, background and export").assertDoesNotExist()
    }

    @Test
    fun draggingTheHandleToTheLeftEdgeDocksARail() {
        val e = InMemoryEditor()
        show(e)
        compose.onNodeWithContentDescription(handle).performTouchInput {
            down(center)
            moveBy(Offset(-300f, 200f))
            moveBy(Offset(-400f, 100f))
            up()
        }
        compose.waitForIdle()
        val p = e.state.toolbarPlacement
        assertTrue("$p", p is ToolbarPlacement.Docked && p.edge == Edge.Left)
        assertTrue(p!!.vertical)
        assertFalse("a drag is not a tap", e.state.toolbarCollapsed)
    }

    @Test
    fun draggingIntoTheMiddleLeavesItFloating() {
        val e = InMemoryEditor()
        show(e)
        compose.onNodeWithContentDescription(handle).performTouchInput {
            down(center)
            moveBy(Offset(0f, 200f))
            moveBy(Offset(0f, 200f))
            up()
        }
        compose.waitForIdle()
        val p = e.state.toolbarPlacement
        assertTrue("$p", p is ToolbarPlacement.Floating && !p.vertical)
    }

    @Test
    fun continuousWritingCollapsesAndNeverExpands() {
        val e = InMemoryEditor()
        show(e)
        var t = 0L
        while (t < 14_900) {
            e.onWritingActivity(t)
            t += 16
        }
        assertFalse(e.state.toolbarCollapsed)
        while (t < 30_000) {
            e.onWritingActivity(t)
            t += 16
        }
        compose.waitForIdle()
        assertTrue(e.state.toolbarCollapsed)
        compose.onNodeWithContentDescription("Text box").assertDoesNotExist()
        assertTrue(e.state.cornerCollapsed)
        // More writing, and long pauses, leave it collapsed: only its arrow expands it.
        e.onWritingActivity(60_000)
        e.onWritingActivity(90_000)
        assertTrue(e.state.toolbarCollapsed)
        compose.onNodeWithContentDescription("Expand toolbar").performClick()
        assertFalse(e.state.toolbarCollapsed)
        // Expanding mid-spell starts a new spell rather than collapsing again at once.
        e.onWritingActivity(90_016)
        assertFalse(e.state.toolbarCollapsed)
    }

    @Test
    fun tappingTheActiveToolOpensItsMenu() {
        val e = InMemoryEditor()
        show(e)
        compose.onNodeWithContentDescription("Shapes: Rectangle").performClick()
        assertEquals(Tool.Shape, e.state.tool)
        assertNull(e.state.openMenu)
        compose.onNodeWithContentDescription("Shapes: Rectangle").performClick()
        assertEquals(ToolMenu.Shape, e.state.openMenu)
        compose.onNodeWithContentDescription("Hexagon").performClick()
        assertEquals(ShapeKind.Hexagon, e.state.shape)
        assertNull(e.state.openMenu)
        compose.onNodeWithContentDescription("Shapes: Hexagon").assertIsSelected()
    }

    @Test
    fun colourPanelSetsColourWidthAndPenOnly() {
        val e = InMemoryEditor()
        show(e)
        compose.onNodeWithContentDescription("Red ink").performClick()
        assertEquals(QuickInk.Red, e.state.color)
        compose.onNodeWithContentDescription("More colours and stroke width").performClick()
        assertTrue(e.state.colourPanelOpen)
        compose.onNodeWithContentDescription("Hex colour").performTextReplacement("#7a5cff")
        compose.waitForIdle()
        assertEquals(Argb(0xFF7A5CFF.toInt()), e.state.color)
        compose.onNodeWithContentDescription("Hex colour").performTextReplacement("#7a5c")
        compose.waitForIdle()
        assertEquals("partial input keeps the colour", Argb(0xFF7A5CFF.toInt()), e.state.color)
        compose.onNodeWithContentDescription("Stroke width 7").performClick()
        assertEquals(7f, e.state.strokeWidth)
        compose.onNodeWithContentDescription("#2E9A5B").performClick()
        assertEquals(QuickInk.Green, e.state.color)
        compose.onNodeWithContentDescription("Hex colour").performTextReplacement("#7A5CFF")
        compose.onNodeWithContentDescription("Colour wheel").performTouchInput { click(Offset(width - 2f, height / 2f)) }
        compose.waitForIdle()
        // The rim at 3 o'clock is pure red; the tone (full value for #7A5CFF) is kept.
        val picked = ColourMath.rgbToHsv(e.state.color.value)
        assertTrue("$picked", picked.h < 1f || picked.h > 359f)
        assertTrue("$picked", picked.s > 0.95f)
        assertEquals(1f, picked.v)
        assertTrue(e.state.penOnly)
    }

    @Test
    fun exportDialogOptions() {
        val e = InMemoryEditor(EditorUiState(noteTitle = "Raft"))
        show(e)
        compose.onNodeWithContentDescription("Export and share").performClick()
        assertTrue(e.state.exportOpen)
        compose.onNodeWithContentDescription("Close").performClick()
        assertFalse(e.state.exportOpen)
    }

    @Test
    fun sizeLabelFollowsFormatAndScale() {
        val sizes = mapOf(ExportArea.WholeCanvas to androidx.compose.ui.unit.IntSize(1000, 300))
        assertEquals("2000 × 600 px", exportSizeLabel(ExportOptions(), sizes))
        assertEquals("3000 × 900 px", exportSizeLabel(ExportOptions(scale = 3), sizes))
        assertEquals("1 page, sized to fit", exportSizeLabel(ExportOptions(format = ExportFormat.Pdf), sizes))
        assertEquals("Vector, 1000 × 300", exportSizeLabel(ExportOptions(format = ExportFormat.Svg), sizes))
        assertEquals("", exportSizeLabel(ExportOptions(area = ExportArea.Selection), sizes))
    }
}
