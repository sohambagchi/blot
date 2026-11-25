package app.blot.ui.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.blot.ui.LAND
import app.blot.ui.PHONE
import app.blot.ui.PORT
import app.blot.ui.shot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the library with the fake data to `app/build/ui-shots/library-*.png`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryShotsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun library(
        name: String,
        w: Int,
        h: Int,
        dark: Boolean,
        fake: FakeLibrary = FakeLibrary(dark = dark),
        setup: FakeLibrary.() -> Unit = {},
    ) {
        fake.setup()
        compose.shot("library-$name", w, h, dark) { Library(fake.state, fake) }
    }

    @Test @Config(qualifiers = LAND)
    fun landLight() = library("land-light", 1569, 941, false)

    @Test @Config(qualifiers = LAND)
    fun landDark() = library("land-dark", 1569, 941, true)

    @Test @Config(qualifiers = PORT)
    fun portLight() = library("port-light", 941, 1569, false)

    @Test @Config(qualifiers = PORT)
    fun portDark() = library("port-dark", 941, 1569, true)

    @Test @Config(qualifiers = LAND)
    fun cardMenu() = library("land-light-card-menu", 1569, 941, false) { setCardMenu("raft") }

    @Test @Config(qualifiers = LAND)
    fun exportDialog() = library("land-light-export", 1569, 941, false) { openExport("raft") }

    @Test @Config(qualifiers = LAND)
    fun newNoteMenu() = library("land-dark-new-note", 1569, 941, true) { setNewNoteOpen(true) }

    @Test @Config(qualifiers = LAND)
    fun settings() = library("land-light-settings", 1569, 941, false) { setSettingsOpen(true) }

    @Test @Config(qualifiers = LAND)
    fun settingsDark() = library("land-dark-settings", 1569, 941, true) { setSettingsOpen(true) }

    @Test @Config(qualifiers = LAND)
    fun empty() = library("land-light-empty", 1569, 941, false, FakeLibrary(withNotes = false))

    @Test @Config(qualifiers = LAND)
    fun noMatch() = library("land-dark-no-match", 1569, 941, true) { setQuery("zebra") }

    @Test @Config(qualifiers = LAND)
    fun notChosen() = library("land-light-not-chosen", 1569, 941, false, FakeLibrary(StorageState.NotChosen))

    @Test @Config(qualifiers = PORT)
    fun permissionLost() = library(
        "port-dark-permission-lost", 941, 1569, true,
        FakeLibrary(StorageState.PermissionLost("Documents/Blot"), dark = true),
    )

    @Test @Config(qualifiers = PHONE)
    fun phone() = library("phone-light", 412, 915, false)

    @Test @Config(qualifiers = LAND)
    fun folderMenu() = library("land-light-folder-menu", 1569, 941, false) { setFolderMenu("ds") }

    @Test @Config(qualifiers = LAND)
    fun newFolderRefused() = library("land-light-new-folder-refused", 1569, 941, false) {
        newFolder()
        createFolder("journal")
    }

    @Test @Config(qualifiers = LAND)
    fun renameFolderDark() = library("land-dark-rename-folder", 1569, 941, true) {
        setFolderDialog(FolderDialog.Rename("jn", "Journal"))
    }

    @Test @Config(qualifiers = LAND)
    fun moveTo() = library("land-light-move-to", 1569, 941, false) {
        setCardMenu("cap")
        setMoveMenu(true)
    }

    @Test @Config(qualifiers = LAND)
    fun moveToDark() = library("land-dark-move-to", 1569, 941, true) {
        moveNote("raft", null)
        selectFolder(null)
        setCardMenu("raft")
        setMoveMenu(true)
    }

    @Test @Config(qualifiers = LAND)
    fun deleteFolder() = library("land-light-delete-folder", 1569, 941, false) {
        setFolderDialog(FolderDialog.Delete("ds", "Distributed systems", 8))
    }

    @Test @Config(qualifiers = LAND)
    fun deleteFolderDark() = library("land-dark-delete-folder", 1569, 941, true) {
        setFolderDialog(FolderDialog.Delete("ds", "Distributed systems", 8))
    }

    @Test @Config(qualifiers = PHONE)
    fun phoneFolderMenu() = library("phone-light-folder-menu", 412, 915, false) { setFolderMenu("cc") }
}
