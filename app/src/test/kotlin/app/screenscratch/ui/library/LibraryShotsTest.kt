package app.screenscratch.ui.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.screenscratch.ui.LAND
import app.screenscratch.ui.PHONE
import app.screenscratch.ui.PORT
import app.screenscratch.ui.shot
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
        FakeLibrary(StorageState.PermissionLost("Documents/ScreenScratch"), dark = true),
    )

    @Test @Config(qualifiers = PHONE)
    fun phone() = library("phone-light", 412, 915, false)
}
