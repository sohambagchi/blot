package app.blot.ui.editor

import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import app.blot.ui.LAND
import app.blot.ui.PHONE
import app.blot.ui.theme.BlotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Editing the note title in place: the tablet's back pill and the phone's top bar. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = LAND)
class TitleEditingTest {
    @get:Rule
    val compose = createComposeRule()

    private var canvasDowns = 0
    private lateinit var root: View

    private fun show(editor: InMemoryEditor, phone: Boolean = false) {
        compose.setContent {
            root = LocalView.current
            BlotTheme(false) {
                Box(Modifier.size(if (phone) 412.dp else 1569.dp, if (phone) 915.dp else 941.dp)) {
                    val canvas: @androidx.compose.runtime.Composable () -> Unit = {
                        Box(
                            Modifier.fillMaxSize().testTag("canvas").pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        if (awaitPointerEvent().changes.any { it.pressed && !it.previousPressed }) canvasDowns++
                                    }
                                }
                            },
                        )
                    }
                    if (phone) PhoneEditorChrome(editor.state, editor, content = canvas) else EditorChrome(editor.state, editor, content = canvas)
                }
            }
        }
    }

    private val field get() = compose.onNodeWithContentDescription("Note title")

    @Test
    fun tappingTheTitleEditsItAndDoneCommits() {
        val e = InMemoryEditor(EditorUiState(noteTitle = "Raft", folderName = "Distributed systems"))
        show(e)
        compose.onNodeWithText("Raft").performClick()
        assertTrue(e.state.titleEditing)
        field.assertIsFocused()
        field.performTextReplacement("Raft, day 2")
        field.performImeAction()
        assertEquals("Raft, day 2", e.state.noteTitle)
        assertFalse(e.state.titleEditing)
        compose.onNodeWithText("Raft, day 2").assertExists()
        field.assertDoesNotExist()
    }

    @Test
    fun escapeAndBackCancel() {
        val e = InMemoryEditor(EditorUiState(noteTitle = "Raft"))
        show(e)
        compose.onNodeWithText("Raft").performClick()
        field.performTextReplacement("Paxos")
        field.performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertFalse(e.state.titleEditing)
        assertEquals("Raft", e.state.noteTitle)

        compose.onNodeWithText("Raft").performClick()
        field.performTextReplacement("Paxos")
        // Back is seen before the keyboard would close: it cancels the edit.
        compose.runOnIdle {
            root.dispatchKeyEventPreIme(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            root.dispatchKeyEventPreIme(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        compose.waitForIdle()
        assertFalse(e.state.titleEditing)
        assertEquals("Raft", e.state.noteTitle)
    }

    @Test
    fun aNewNoteStartsInTheEmptyTitleAndWritingLeavesItUntitled() {
        val e = InMemoryEditor(EditorUiState(titleEditing = true))
        show(e)
        field.assertIsFocused()
        compose.onNodeWithText("Untitled").assertExists()
        compose.onNodeWithTag("canvas").performTouchInput { click(Offset(800f, 600f)) }
        compose.waitForIdle()
        assertFalse(e.state.titleEditing)
        assertEquals("", e.state.noteTitle)
        // The tap still reached the canvas.
        assertEquals(1, canvasDowns)
        compose.onNodeWithText("Untitled").assertExists()
    }

    @Test
    fun aTapElsewhereCommitsWhatWasTyped() {
        val e = InMemoryEditor(EditorUiState(titleEditing = true))
        show(e)
        field.performTextReplacement("  Gossip\nprotocols ")
        compose.onNodeWithTag("canvas").performTouchInput { click(Offset(800f, 600f)) }
        compose.waitForIdle()
        assertEquals("Gossip protocols", e.state.noteTitle)
        assertFalse(e.state.titleEditing)
    }

    @Test
    fun tappingTheFieldKeepsItOpen() {
        val e = InMemoryEditor(EditorUiState(noteTitle = "Raft", titleEditing = true))
        show(e)
        field.performClick()
        compose.waitForIdle()
        assertTrue(e.state.titleEditing)
        assertEquals(0, canvasDowns)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun thePhoneTopBarEditsTheTitleToo() {
        val e = InMemoryEditor(EditorUiState())
        show(e, phone = true)
        compose.onNodeWithText("Untitled").performClick()
        assertTrue(e.state.titleEditing)
        field.performTextReplacement("CRDTs")
        field.performImeAction()
        assertEquals("CRDTs", e.state.noteTitle)
        compose.onNodeWithText("CRDTs").assertExists()
    }
}
