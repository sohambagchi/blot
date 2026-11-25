package app.blot.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import app.blot.ui.LAND
import app.blot.ui.theme.BlotTheme
import app.blot.ui.theme.ThemeSetting
import app.blot.ui.editor.ExportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = LAND)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryBehaviourTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(fake: FakeLibrary) {
        compose.setContent {
            BlotTheme(false) { Box(Modifier.size(1569.dp, 941.dp)) { Library(fake.state, fake) } }
        }
    }

    @Test
    fun searchFiltersAndShowsNoMatch() {
        val fake = FakeLibrary()
        show(fake)
        compose.onNodeWithContentDescription("Search notes").performTextReplacement("raft")
        compose.waitForIdle()
        assertEquals(listOf("Raft", "Paxos vs Raft"), fake.state.notes.map { it.title })
        compose.onNodeWithContentDescription("Search notes").performTextReplacement("zebra")
        compose.onNodeWithText("No notes match “zebra”").assertExists()
    }

    @Test
    fun cardMenuDeletesAndOpeningReportsTheNote() {
        var opened: NoteCard? = null
        val fake = FakeLibrary(onOpen = { opened = it })
        show(fake)
        compose.onNodeWithContentDescription("More actions for CRDTs").performClick()
        assertEquals("crdt", fake.state.cardMenu)
        compose.onNodeWithText("Delete").performClick()
        assertTrue(fake.state.notes.none { it.id == "crdt" })
        compose.onNodeWithContentDescription("Open Raft").performClick()
        assertEquals("raft", opened?.id)
    }

    @Test
    fun cardMenuExportOpensTheExportDialog() {
        val fake = FakeLibrary()
        show(fake)
        compose.onNodeWithContentDescription("More actions for CRDTs").performClick()
        compose.onNodeWithText("Export PNG").assertDoesNotExist()
        compose.onNodeWithText("Export…").performClick()
        assertEquals("crdt", fake.state.exporting?.id)
        compose.onNodeWithText("Export and share").assertExists()
        // The library has no visible area or selection, so the area choice is hidden.
        compose.onNodeWithText("Visible area").assertDoesNotExist()
        compose.onNodeWithText("PDF").performClick()
        assertEquals(ExportFormat.Pdf, fake.state.exporting?.options?.format)
        compose.onNodeWithText("Save to folder").performClick()
        assertEquals(null, fake.state.exporting)
    }

    @Test
    fun newNoteMenuOffersFourPageKinds() {
        val fake = FakeLibrary()
        show(fake)
        compose.onNodeWithContentDescription("New note").performClick()
        compose.onNodeWithContentDescription("New A4 note").performClick()
        assertEquals(PageKind.A4, fake.state.notes.first().kind)
        assertTrue(!fake.state.newNoteOpen)
    }

    @Test
    fun storageStatesOfferTheRightActions() {
        val fake = FakeLibrary(StorageState.PermissionLost("Documents/Blot"))
        show(fake)
        compose.onNodeWithText("Grant access again").performClick()
        assertEquals(StorageState.Ready("Documents/Blot"), fake.state.storage)
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Dark").performClick()
        assertEquals(ThemeSetting.Dark, fake.state.settings.theme)
        compose.onNodeWithText("Forget").performClick()
        compose.onNodeWithText("Choose folder…").assertExists()
    }

    @Test
    fun cardMenuRenamesANoteAndSearchFindsTheNewTitle() {
        val fake = FakeLibrary()
        show(fake)
        compose.onNodeWithContentDescription("More actions for CRDTs").performClick()
        compose.onNodeWithText("Rename").performClick()
        assertEquals("crdt", fake.state.renaming)
        compose.onNodeWithContentDescription("Note title").assertIsFocused()
        compose.onNodeWithContentDescription("Note title").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("CRDTs")))
        compose.onNodeWithContentDescription("Note title").performTextReplacement("  Conflict-free\nreplicated types ")
        compose.onNodeWithText("Rename").performClick()
        compose.waitForIdle()
        assertEquals(null, fake.state.renaming)
        assertEquals("Conflict-free replicated types", fake.state.notes.single { it.id == "crdt" }.title)
        compose.onNodeWithContentDescription("Search notes").performTextReplacement("replicated")
        compose.waitForIdle()
        assertEquals(listOf("crdt"), fake.state.notes.map { it.id })
    }

    @Test
    fun renamingToBlankLeavesItUntitledAndCancelKeepsTheTitle() {
        val fake = FakeLibrary()
        show(fake)
        compose.onNodeWithContentDescription("More actions for Raft").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithContentDescription("Note title").performTextReplacement("Paxos")
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals("Raft", fake.state.notes.single { it.id == "raft" }.title)

        compose.onNodeWithContentDescription("More actions for Raft").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithContentDescription("Note title").performTextReplacement("   ")
        compose.onNodeWithContentDescription("Note title").performImeAction()
        compose.waitForIdle()
        val card = fake.state.notes.single { it.id == "raft" }
        assertEquals("Untitled", card.title)
        assertTrue(card.untitled)
        // Reopened, an untitled note's field is empty, with "Untitled" as its placeholder.
        compose.onNodeWithContentDescription("More actions for Untitled").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithContentDescription("Note title").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }
}
