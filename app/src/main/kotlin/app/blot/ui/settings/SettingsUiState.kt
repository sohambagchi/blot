package app.blot.ui.settings

import androidx.compose.runtime.Immutable
import app.blot.ui.theme.ThemeSetting

/** The settings the app keeps: theme and the pen's side-button roles; [version] is shown under About. */
@Immutable
data class SettingsUiState(
    val theme: ThemeSetting = ThemeSetting.System,
    /** False: primary erases while held, secondary switches to the pointer. True swaps them. */
    val swapSideButtons: Boolean = false,
    val version: String = "",
    /** Developer setting: the low-latency buffer holds the whole note while inking, not just the ink. */
    val frontBufferWholeNote: Boolean = false,
)

/** The debug screens reachable from the developer section under About. */
enum class DebugScreen { PenTest, CanvasTest, UiPreview }

/** Settings intents, including the storage folder's. */
interface SettingsActions {
    fun setTheme(theme: ThemeSetting)
    fun setSwapSideButtons(swap: Boolean)
    /** Opens the system folder picker for a new notes folder. */
    fun chooseFolder()
    /** Asks again for the folder whose grant was lost (the picker starts there). */
    fun regrantFolder()
    /** Forgets the folder and releases its grant; the notes on disk stay. */
    fun forgetFolder()

    fun setFrontBufferWholeNote(on: Boolean) {}
    fun openDebugScreen(screen: DebugScreen) {}
}
