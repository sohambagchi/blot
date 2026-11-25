package app.screenscratch.ui.library

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import app.screenscratch.ui.editor.ExportFormat
import app.screenscratch.ui.settings.SettingsActions
import app.screenscratch.ui.settings.SettingsUiState

/** A note's page layout, shown as the chip on its card and offered by the new-note button. */
enum class PageKind { Infinite, Screen, A4, Letter }

/** One note in the grid. [thumbnail] is drawn in the current theme; null shows a placeholder. */
@Immutable
data class NoteCard(
    val id: String,
    val title: String,
    /** Already formatted for display, e.g. "Today" or "Nov 21". */
    val date: String,
    val kind: PageKind,
    /** Page count; ignored for [PageKind.Infinite]. */
    val pages: Int = 1,
    val thumbnail: ImageBitmap? = null,
)

/** A folder in the left column; [id] null is "All notes". */
@Immutable
data class FolderItem(val id: String?, val name: String, val count: Int)

/**
 * The notes folder, mirroring `NoteFolder.State` with a display [path]
 * (e.g. "Documents/ScreenScratch") instead of a tree URI.
 */
@Immutable
sealed interface StorageState {
    data object NotChosen : StorageState
    data class Ready(val path: String) : StorageState
    data class PermissionLost(val path: String) : StorageState
}

/** Everything the library screen draws. [notes] are already filtered by folder and [query]. */
@Immutable
data class LibraryUiState(
    val storage: StorageState = StorageState.NotChosen,
    val folders: List<FolderItem> = emptyList(),
    val selectedFolder: String? = null,
    val notes: List<NoteCard> = emptyList(),
    val query: String = "",
    /** The note whose card menu is open. */
    val cardMenu: String? = null,
    val newNoteOpen: Boolean = false,
    val settingsOpen: Boolean = false,
    val settings: SettingsUiState = SettingsUiState(),
    /** A short notice at the bottom (a failed delete, an unreadable note); [LibraryActions.dismissMessage] clears it. */
    val message: String? = null,
) {
    /** The heading: the selected folder's name, or "All notes". */
    val heading: String get() = folders.firstOrNull { it.id == selectedFolder }?.name ?: "All notes"
}

/** What the library screen asks for; settings intents come from [SettingsActions]. */
interface LibraryActions : SettingsActions {
    fun openNote(id: String)
    fun selectFolder(id: String?)
    fun newFolder()
    fun setQuery(query: String)
    fun setNewNoteOpen(open: Boolean)
    fun newNote(kind: PageKind)
    /** Opens the card menu of note [id], or closes it when null. */
    fun setCardMenu(id: String?)
    fun exportNote(id: String, format: ExportFormat)
    fun shareNote(id: String)
    fun deleteNote(id: String)
    fun setSettingsOpen(open: Boolean)
    /** The theme button in the header: flips between light and dark. */
    fun toggleDark()
    fun dismissMessage() {}
}
