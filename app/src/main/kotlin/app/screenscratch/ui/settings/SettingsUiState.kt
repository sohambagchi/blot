package app.screenscratch.ui.settings

import androidx.compose.runtime.Immutable
import app.screenscratch.ui.theme.ThemeSetting

/** The settings the app keeps: theme and the pen's side-button roles; [version] is shown under About. */
@Immutable
data class SettingsUiState(
    val theme: ThemeSetting = ThemeSetting.System,
    /** False: primary erases while held, secondary cycles pen, pan and select. True swaps them. */
    val swapSideButtons: Boolean = false,
    val version: String = "",
)

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
}
