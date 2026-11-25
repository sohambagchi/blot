package app.blot.ui.library

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import app.blot.ui.editor.ExportInfo
import app.blot.ui.editor.ExportOptions
import app.blot.ui.settings.SettingsActions
import app.blot.ui.settings.SettingsUiState

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
    /** The note has no title; [title] then reads "Untitled". */
    val untitled: Boolean = false,
    /** The [FolderItem.id] of the folder holding the note, or null at the top level. */
    val folder: String? = null,
)

/** A folder in the left column; [id] null is "All notes". */
@Immutable
data class FolderItem(val id: String?, val name: String, val count: Int)

/** The folder dialogs, opened from "New folder" and a folder's menu. */
@Immutable
sealed interface FolderDialog {
    /** Naming a new folder; [error] says why the last [name] typed was refused. */
    data class New(val error: String? = null, val name: String = "") : FolderDialog

    /** Renaming folder [id], now called [name]. */
    data class Rename(val id: String, val name: String, val error: String? = null) : FolderDialog

    /** Confirming that folder [id] goes and its [count] notes move to the top level. */
    data class Delete(val id: String, val name: String, val count: Int) : FolderDialog
}

/**
 * The notes folder, mirroring `NoteFolder.State` with a display [path]
 * (e.g. "Documents/Blot") instead of a tree URI.
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
    /** The open card menu lists the folders to move its note to instead of its actions. */
    val moveMenu: Boolean = false,
    /** The folder whose menu is open. */
    val folderMenu: String? = null,
    val folderDialog: FolderDialog? = null,
    /** The note whose rename dialog is open. */
    val renaming: String? = null,
    val newNoteOpen: Boolean = false,
    val settingsOpen: Boolean = false,
    /** The export dialog opened from a card menu, or null. */
    val exporting: LibraryExport? = null,
    val settings: SettingsUiState = SettingsUiState(),
    /** A short notice at the bottom (a failed delete, an unreadable note); [LibraryActions.dismissMessage] clears it. */
    val message: String? = null,
) {
    /** The heading: the selected folder's name, or "All notes". */
    val heading: String get() = folders.firstOrNull { it.id == selectedFolder }?.name ?: "All notes"
}

/**
 * The export dialog for note [id] as opened from its card: [subtitle] names the note and its
 * pages; [info] carries the preview and the whole note's size.
 */
data class LibraryExport(
    val id: String,
    val subtitle: String,
    val options: ExportOptions = ExportOptions(),
    val info: ExportInfo = ExportInfo(),
)

/** What the library screen asks for; settings intents come from [SettingsActions]. */
interface LibraryActions : SettingsActions {
    fun openNote(id: String)
    fun selectFolder(id: String?)
    /** Opens the dialog that names a new folder. */
    fun newFolder()
    /** Opens the menu of folder [id], or closes it when null. */
    fun setFolderMenu(id: String?)
    /** Opens [dialog], or closes the open folder dialog when null. */
    fun setFolderDialog(dialog: FolderDialog?)
    /** Creates a folder named [name] as typed; a refused name keeps the dialog open with the reason. */
    fun createFolder(name: String)
    /** Renames folder [id] to [name] as typed; a refused name keeps the dialog open with the reason. */
    fun renameFolder(id: String, name: String)
    /** Moves the notes of folder [id] to the top level, then removes the folder. */
    fun deleteFolder(id: String)
    fun setQuery(query: String)
    fun setNewNoteOpen(open: Boolean)
    fun newNote(kind: PageKind)
    /** Opens the card menu of note [id], or closes it when null. */
    fun setCardMenu(id: String?)
    /** Shows the folders to move to in the open card menu, or its actions again. */
    fun setMoveMenu(open: Boolean)
    /** Moves note [id] into folder [folder], or to the top level when null. */
    fun moveNote(id: String, folder: String?)
    /** Opens the rename dialog for note [id], or closes it when null. */
    fun setRenaming(id: String?)
    /** Renames note [id] to [title] as typed; blank leaves it untitled. Closes the dialog. */
    fun renameNote(id: String, title: String)
    /** Opens the export dialog for note [id]. */
    fun openExport(id: String)
    fun setExportOptions(options: ExportOptions)
    fun closeExport()
    /** Exports with the dialog's options to a document the user creates; closes the dialog when saved. */
    fun exportToFolder()
    /** Exports with the dialog's options and opens the share sheet. */
    fun shareExport()
    fun deleteNote(id: String)
    fun setSettingsOpen(open: Boolean)
    /** The theme button in the header: flips between light and dark. */
    fun toggleDark()
    fun dismissMessage() {}
}
