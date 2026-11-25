package app.blot.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.blot.ui.LAND
import app.blot.ui.PHONE
import app.blot.ui.preview.DotGrid
import app.blot.ui.shot
import app.blot.ui.theme.BlotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The long-press selection menu and the pointer's toolbar menu; shots go to `app/build/ui-shots/editor-*-selection-menu.png`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelectionMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private class Recorder(state: EditorUiState) : InMemoryEditor(state) {
        val commands = ArrayList<SelectionCommand>()
        override fun selectionCommand(command: SelectionCommand) {
            commands += command
            super.selectionCommand(command)
        }
    }

    private val selected = EditorUiState(
        noteTitle = "Raft",
        tool = Tool.Select,
        hasSelection = true,
        selectionMenu = SelectionMenu(Rect(600f, 400f, 900f, 560f), canGroup = true),
    )

    private fun show(editor: InMemoryEditor) {
        compose.setContent {
            BlotTheme(false) {
                Box(Modifier.size(1569.dp, 941.dp)) { EditorChrome(editor.state, editor) { DotGrid() } }
            }
        }
    }

    @Test @Config(qualifiers = LAND)
    fun lightShot() {
        val e = InMemoryEditor(selected)
        compose.shot("editor-land-light-selection-menu", 1569, 941, false) { Editor(e.state, e) { DotGrid() } }
    }

    @Test @Config(qualifiers = LAND)
    fun darkShot() {
        val e = InMemoryEditor(selected.copy(selectionMenu = SelectionMenu(Rect(600f, 20f, 900f, 120f), canUngroup = true)))
        compose.shot("editor-land-dark-selection-menu", 1569, 941, true) { Editor(e.state, e) { DotGrid() } }
    }

    @Test @Config(qualifiers = PHONE)
    fun phoneShot() {
        val e = InMemoryEditor(selected.copy(selectionMenu = SelectionMenu(Rect(100f, 300f, 300f, 420f))))
        compose.shot("editor-phone-dark-selection-menu", 412, 915, true) { Editor(e.state, e) { DotGrid() } }
    }

    @Test @Config(qualifiers = LAND)
    fun groupShowsForSeveralAndATapRunsItAndCloses() {
        val e = Recorder(selected)
        show(e)
        for (name in listOf("Bring forward", "Send backward", "Group", "Duplicate", "Delete")) compose.onNodeWithText(name).assertExists()
        compose.onNodeWithText("Ungroup").assertDoesNotExist()
        compose.onNodeWithText("Group").performClick()
        assertEquals(listOf(SelectionCommand.Group), e.commands)
        assertNull(e.state.selectionMenu)
        compose.onNodeWithText("Duplicate").assertDoesNotExist()
    }

    @Test @Config(qualifiers = LAND)
    fun ungroupReplacesGroupForOneGroupAndNeitherShowsForOneElement() {
        val e = Recorder(selected.copy(selectionMenu = SelectionMenu(Rect(600f, 400f, 900f, 560f), canUngroup = true)))
        show(e)
        compose.onNodeWithText("Ungroup").assertExists()
        compose.onNodeWithText("Group").assertDoesNotExist()
        compose.onNodeWithText("Ungroup").performClick()
        assertEquals(listOf(SelectionCommand.Ungroup), e.commands)

        e.state = selected.copy(selectionMenu = SelectionMenu(Rect(600f, 400f, 900f, 560f)))
        compose.waitForIdle()
        compose.onNodeWithText("Group").assertDoesNotExist()
        compose.onNodeWithText("Ungroup").assertDoesNotExist()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(SelectionCommand.Delete, e.commands.last())
    }

    @Test @Config(qualifiers = LAND)
    fun aTapOutsideClosesTheMenuWithoutACommand() {
        val e = Recorder(selected)
        show(e)
        compose.onRoot().performTouchInput { click(Offset(100f, 800f)) }
        assertNull(e.state.selectionMenu)
        assertEquals(emptyList<SelectionCommand>(), e.commands)
    }

    @Test @Config(qualifiers = LAND)
    fun thePenMenuHasPenAndHandAndThePointerMenuLassoAndRectangle() {
        val e = InMemoryEditor(EditorUiState(openMenu = ToolMenu.Pen))
        show(e)
        compose.onNodeWithContentDescription("Pen").assertExists()
        compose.onNodeWithContentDescription("Hand: pan and zoom").assertExists()
        compose.onNodeWithContentDescription("Pointer: select objects").assertDoesNotExist()
        e.selectTool(Tool.Select)
        e.openMenu(ToolMenu.Select)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Pointer: Lasso select").assertExists()
        compose.onNodeWithContentDescription("Rectangle select").performClick()
        assertEquals(SelectMode.Rectangle, e.state.selectMode)
        assertEquals(Tool.Select, e.state.tool)
    }
}
