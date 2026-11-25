package app.blot.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.blot.ui.LAND
import app.blot.ui.PHONE
import app.blot.ui.theme.BlotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class LibraryFoldersTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(fake: FakeLibrary, w: Int = 1569, h: Int = 941) {
        compose.setContent {
            BlotTheme(false) { Box(Modifier.size(w.dp, h.dp)) { Library(fake.state, fake) } }
        }
    }

    private fun FakeLibrary.count(id: String?) = state.folders.single { it.id == id }.count

    @Test
    fun selectingAFolderFiltersTheNotesAndShowsItsName() {
        val fake = FakeLibrary()
        show(fake)
        assertEquals(8, fake.state.notes.size)
        compose.onNodeWithText("Compilers").performClick()
        compose.waitForIdle()
        assertEquals("cc", fake.state.selectedFolder)
        assertEquals("Compilers", fake.state.heading)
        assertEquals(emptyList<NoteCard>(), fake.state.notes)
        compose.onNodeWithText("No notes here yet").assertExists()

        compose.onNodeWithText("All notes").performClick()
        compose.waitForIdle()
        assertEquals(8, fake.state.notes.size)
        assertEquals(listOf(23, 6, 8, 7, 2), fake.state.folders.map { it.count })
        compose.onNodeWithText("23").assertExists()
    }

    @Test
    fun newFolderShowsWhyANameIsRefusedThenCreatesAndSelectsIt() {
        val fake = FakeLibrary()
        show(fake)
        compose.onNodeWithText("New folder").performClick()
        compose.onNodeWithContentDescription("Folder name").assertIsFocused()

        compose.onNodeWithContentDescription("Folder name").performTextReplacement("journal")
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithText("There's already a folder called “Journal”").assertExists()
        assertEquals(FolderDialog.New("There's already a folder called “Journal”", "journal"), fake.state.folderDialog)

        compose.onNodeWithContentDescription("Folder name").performTextReplacement("Lectures/2026")
        compose.onNodeWithContentDescription("Folder name").performImeAction()
        compose.onNodeWithText("Folder names can't contain /").assertExists()

        compose.onNodeWithContentDescription("Folder name").performTextReplacement("  Lectures ")
        compose.onNodeWithText("Create").performClick()
        compose.waitForIdle()
        assertNull(fake.state.folderDialog)
        val created = fake.state.folders.single { it.name == "Lectures" }
        assertEquals(created.id, fake.state.selectedFolder)
        assertEquals("Lectures", fake.state.heading)
        assertEquals(0, created.count)
    }

    @Test
    fun moveToListsOtherFoldersAndMovesTheNote() {
        val fake = FakeLibrary()
        show(fake)
        compose.onNodeWithContentDescription("More actions for CRDTs").performClick()
        compose.onNodeWithText("Move to…").performClick()
        assertTrue(fake.state.moveMenu)
        // The note is in Distributed systems: that folder isn't offered, the top level is.
        compose.onNodeWithText("Top level").assertExists()
        compose.onAllNodesWithText("Distributed systems").assertCountEquals(2) // the heading and the folder row
        compose.onAllNodesWithText("Journal").assertCountEquals(2)

        compose.onNodeWithText("Move to").performClick()
        assertEquals(false, fake.state.moveMenu)
        compose.onNodeWithText("Move to…").performClick()
        compose.onAllNodesWithText("Journal").onLast().performClick()
        compose.waitForIdle()
        assertNull(fake.state.cardMenu)
        assertEquals(7, fake.state.notes.size)
        assertEquals(8, fake.count("jn"))
        assertEquals(7, fake.count("ds"))

        // A note at the top level is offered every folder, but not the top level.
        fake.moveNote("raft", null)
        compose.onNodeWithText("All notes").performClick()
        compose.onNodeWithContentDescription("More actions for Raft").performClick()
        compose.onNodeWithText("Move to…").performClick()
        compose.onNodeWithText("Top level").assertDoesNotExist()
        compose.onAllNodesWithText("Distributed systems").assertCountEquals(2)
    }

    @Test
    fun deletingAFolderAsksFirstAndMovesItsNotesToTheTopLevel() {
        val fake = FakeLibrary()
        show(fake)
        compose.onNodeWithContentDescription("More actions for folder Distributed systems").performClick()
        assertEquals("ds", fake.state.folderMenu)
        compose.onNodeWithText("Delete").performClick()
        assertEquals(FolderDialog.Delete("ds", "Distributed systems", 8), fake.state.folderDialog)
        compose.onNodeWithText("Its 8 notes move to the top level of your notes folder. No note is deleted.").assertExists()

        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertNull(fake.state.folderDialog)
        assertEquals(8, fake.count("ds"))

        compose.onNodeWithContentDescription("More actions for folder Distributed systems").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete folder").performClick()
        compose.waitForIdle()
        assertTrue(fake.state.folders.none { it.id == "ds" })
        assertNull(fake.state.selectedFolder)
        assertEquals(8, fake.state.notes.size)
        assertTrue(fake.state.notes.all { it.folder == null })
        assertEquals(23, fake.count(null))
    }

    @Test
    fun aLongPressOpensAFolderMenuAndRenameChecksTheName() {
        val fake = FakeLibrary()
        show(fake)
        compose.onNodeWithText("Journal").performTouchInput { longClick() }
        assertEquals("jn", fake.state.folderMenu)
        compose.onNodeWithText("Rename").performClick()
        assertEquals(FolderDialog.Rename("jn", "Journal"), fake.state.folderDialog)
        compose.onNodeWithContentDescription("Folder name").performTextReplacement("SKETCHES")
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithText("There's already a folder called “Sketches”").assertExists()
        compose.onNodeWithContentDescription("Folder name").performTextReplacement("Diary")
        compose.onNodeWithText("Rename").performClick()
        compose.waitForIdle()
        assertEquals("Diary", fake.state.folders.single { it.id == "jn" }.name)
        compose.onNodeWithText("Diary").assertExists()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneChipsSelectFoldersAndOfferANewOne() {
        val fake = FakeLibrary()
        show(fake, 412, 915)
        compose.onNodeWithText("Compilers  6").performClick()
        compose.onNodeWithText("Compilers  6").assertIsSelected()
        assertEquals("cc", fake.state.selectedFolder)
        compose.onNodeWithText("Compilers  6").performTouchInput { longClick() }
        assertEquals("cc", fake.state.folderMenu)
        compose.onNodeWithText("Rename").assertExists()
        fake.setFolderMenu(null)
        compose.waitForIdle()
        fake.newFolder()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Folder name").assertExists()
    }
}
