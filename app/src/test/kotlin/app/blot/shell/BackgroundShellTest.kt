package app.blot.shell

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.blot.canvas.CanvasController
import app.blot.core.model.Background
import app.blot.core.model.Pattern
import app.blot.core.model.Stroke
import app.blot.core.model.StrokePoint
import app.blot.ui.editor.EditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackgroundShellTest {
    private val controller = CanvasController(CanvasController.newNote())
    private val chosen = ArrayList<Background?>()
    private val editor = CanvasEditor(controller, EditorUiState(), onBackgroundChanged = { chosen += it })

    @Test
    fun thePanelSetsTheNotesBackgroundOutsideTheHistory() {
        controller.edit { it.copy(elements = listOf(Stroke("s", listOf(StrokePoint(1f, 1f, 0.5f, timeMs = 0)), app.blot.core.model.Argb.BLACK, 2f))) }
        val grid = Background(Pattern.GRID, 30f)
        editor.setBackground(grid)
        assertEquals(grid, controller.note.value.background)
        assertEquals(grid, editor.uiState.background)
        assertEquals(listOf<Background?>(grid), chosen)

        editor.undo()
        assertTrue("the stroke is undone", controller.note.value.elements.isEmpty())
        assertEquals("the background stays", grid, controller.note.value.background)
        editor.redo()
        assertEquals(grid, controller.note.value.background)

        editor.setExportOpen(true)
        assertEquals(Pattern.GRID, editor.uiState.exportInfo.pattern)

        editor.setBackground(null)
        assertNull(controller.note.value.background)
        assertNull(editor.uiState.background)
    }

    @Test
    fun settingsRememberTheLastChoice() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("background-test", Context.MODE_PRIVATE)
        val s = AppSettings(prefs) { false }
        assertNull(s.background)
        s.background = Background(Pattern.RULED, 34f)
        assertEquals(Background(Pattern.RULED, 34f), AppSettings(prefs) { false }.background)
        s.background = null
        assertNull(AppSettings(prefs) { false }.background)
    }
}
