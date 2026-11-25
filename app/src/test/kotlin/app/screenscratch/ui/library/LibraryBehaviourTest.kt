package app.screenscratch.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import app.screenscratch.ui.LAND
import app.screenscratch.ui.theme.ScratchTheme
import app.screenscratch.ui.theme.ThemeSetting
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
            ScratchTheme(false) { Box(Modifier.size(1569.dp, 941.dp)) { Library(fake.state, fake) } }
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
        val fake = FakeLibrary(StorageState.PermissionLost("Documents/ScreenScratch"))
        show(fake)
        compose.onNodeWithText("Grant access again").performClick()
        assertEquals(StorageState.Ready("Documents/ScreenScratch"), fake.state.storage)
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Dark").performClick()
        assertEquals(ThemeSetting.Dark, fake.state.settings.theme)
        compose.onNodeWithText("Forget").performClick()
        compose.onNodeWithText("Choose folder…").assertExists()
    }
}
