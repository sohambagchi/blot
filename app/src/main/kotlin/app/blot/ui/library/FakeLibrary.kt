package app.blot.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.PathParser
import app.blot.core.model.NoteTitle
import app.blot.core.store.FolderNameException
import app.blot.core.store.FolderNames
import app.blot.ui.editor.ExportOptions
import app.blot.ui.theme.InkColors
import app.blot.ui.theme.ThemeSetting

/**
 * An in-memory [LibraryActions] with the design's sample notes, for previews and tests. Search
 * and folders filter the samples; new notes, folders, moves, deletes and settings change only
 * memory. Folder names are checked by the app's rules.
 * [onOpen] is called when a note is opened.
 */
class FakeLibrary(
    storage: StorageState = StorageState.Ready("Documents/Blot"),
    dark: Boolean = false,
    withNotes: Boolean = true,
    private val onOpen: (NoteCard) -> Unit = {},
) : LibraryActions {
    private data class Sample(val id: String, val folder: String?, val title: String, val date: String, val kind: PageKind, val pages: Int, val ink: Int, val scribble: String)

    /** [extra] stands for the design's notes that aren't samples, so the counts match it. */
    private data class Folder(val id: String, val name: String, val extra: Int)

    private var samples: List<Sample> = if (withNotes) designSamples() else emptyList()
    private var folders: List<Folder> = if (withNotes) designFolders() else emptyList()
    private var nextId = 100

    /** Draw thumbnails for this theme. */
    var dark: Boolean = dark
        set(value) {
            if (field != value) {
                field = value
                refresh()
            }
        }

    var state by mutableStateOf(
        LibraryUiState(storage = storage, selectedFolder = if (withNotes) "ds" else null, settings = app.blot.ui.settings.SettingsUiState(version = "0.7")),
    )
        private set

    init {
        refresh()
    }

    private fun refresh() {
        val s = state
        val q = s.query.trim()
        val inFolder = samples.filter { s.selectedFolder == null || it.folder == s.selectedFolder }
        val shown = inFolder.filter { q.isEmpty() || NoteTitle.display(it.title).contains(q, ignoreCase = true) }
        val counts = samples.groupingBy { it.folder }.eachCount()
        val items = listOf(FolderItem(null, "All notes", samples.size + folders.sumOf { it.extra })) +
            folders.sortedBy { it.name.lowercase() }.map { FolderItem(it.id, it.name, (counts[it.id] ?: 0) + it.extra) }
        val ink = if (dark) InkColors.Dark else InkColors.Light
        state = s.copy(
            folders = items,
            notes = shown.map {
                val colour = when (it.ink) {
                    1 -> ink.red
                    2 -> ink.blue
                    3 -> ink.green
                    else -> ink.black
                }
                NoteCard(it.id, NoteTitle.display(it.title), it.date, it.kind, it.pages, thumbnail(it.scribble, colour), untitled = it.title.isBlank(), folder = it.folder)
            },
        )
    }

    private inline fun update(f: LibraryUiState.() -> LibraryUiState) {
        state = state.f()
    }

    override fun openNote(id: String) {
        state.notes.firstOrNull { it.id == id }?.let(onOpen)
    }

    override fun selectFolder(id: String?) {
        update { copy(selectedFolder = id) }
        refresh()
    }

    override fun newFolder() = update { copy(folderDialog = FolderDialog.New(), folderMenu = null, cardMenu = null, newNoteOpen = false) }

    override fun setFolderMenu(id: String?) = update { copy(folderMenu = id, cardMenu = null, newNoteOpen = false) }

    override fun setFolderDialog(dialog: FolderDialog?) = update { copy(folderDialog = dialog, folderMenu = null) }

    override fun createFolder(name: String) {
        val clean = try {
            FolderNames.clean(name, folders.map { it.name })
        } catch (e: FolderNameException) {
            update { copy(folderDialog = FolderDialog.New(e.reason, name)) }
            return
        }
        val id = "f${nextId++}"
        folders = folders + Folder(id, clean, 0)
        update { copy(folderDialog = null, selectedFolder = id) }
        refresh()
    }

    override fun renameFolder(id: String, name: String) {
        val old = folders.firstOrNull { it.id == id } ?: return
        val clean = try {
            FolderNames.clean(name, folders.map { it.name }, renaming = old.name)
        } catch (e: FolderNameException) {
            update { copy(folderDialog = FolderDialog.Rename(id, old.name, e.reason)) }
            return
        }
        folders = folders.map { if (it.id == id) it.copy(name = clean) else it }
        update { copy(folderDialog = null) }
        refresh()
    }

    override fun deleteFolder(id: String) {
        samples = samples.map { if (it.folder == id) it.copy(folder = null) else it }
        folders = folders.filter { it.id != id }
        update { copy(folderDialog = null, selectedFolder = selectedFolder.takeIf { it != id }) }
        refresh()
    }

    override fun setMoveMenu(open: Boolean) = update { copy(moveMenu = open) }

    override fun moveNote(id: String, folder: String?) {
        samples = samples.map { if (it.id == id) it.copy(folder = folder) else it }
        update { copy(cardMenu = null, moveMenu = false) }
        refresh()
    }

    override fun setQuery(query: String) {
        if (query == state.query) return
        update { copy(query = query) }
        refresh()
    }

    override fun setNewNoteOpen(open: Boolean) = update { copy(newNoteOpen = open, cardMenu = null) }

    override fun newNote(kind: PageKind) {
        val folder = state.selectedFolder
        samples = listOf(Sample("n${nextId++}", folder, "", "Today", kind, 1, 0, "")) + samples
        update { copy(newNoteOpen = false) }
        refresh()
    }

    override fun setCardMenu(id: String?) = update { copy(cardMenu = id, moveMenu = false, folderMenu = null, newNoteOpen = false) }
    override fun setRenaming(id: String?) = update { copy(renaming = id, cardMenu = null, newNoteOpen = false) }

    override fun renameNote(id: String, title: String) {
        samples = samples.map { if (it.id == id) it.copy(title = NoteTitle.clean(title)) else it }
        update { copy(renaming = null) }
        refresh()
    }

    override fun openExport(id: String) {
        val note = state.notes.firstOrNull { it.id == id } ?: return
        state = state.copy(cardMenu = null, exporting = LibraryExport(id, note.title))
    }
    override fun setExportOptions(options: ExportOptions) {
        state = state.copy(exporting = state.exporting?.copy(options = options))
    }
    override fun closeExport() {
        state = state.copy(exporting = null)
    }
    override fun exportToFolder() = closeExport()
    override fun shareExport() {}

    override fun deleteNote(id: String) {
        samples = samples.filter { it.id != id }
        update { copy(cardMenu = null) }
        refresh()
    }

    override fun setSettingsOpen(open: Boolean) = update { copy(settingsOpen = open, newNoteOpen = false, cardMenu = null) }

    override fun toggleDark() = setTheme(if (dark) ThemeSetting.Light else ThemeSetting.Dark)

    override fun setTheme(theme: ThemeSetting) = update { copy(settings = settings.copy(theme = theme)) }
    override fun setSwapSideButtons(swap: Boolean) = update { copy(settings = settings.copy(swapSideButtons = swap)) }
    override fun chooseFolder() = update { copy(storage = StorageState.Ready("Documents/Blot")) }
    override fun regrantFolder() = update { copy(storage = StorageState.Ready((storage as? StorageState.PermissionLost)?.path ?: "Documents/Blot")) }
    override fun forgetFolder() = update { copy(storage = StorageState.NotChosen, settingsOpen = false) }

    private companion object {
        // The design's card scribbles, in a 240 × 95 box starting at y = 55.
        const val S1 = "M20 70 h120 M20 92 h150 M20 114 h90 M150 120 c 20 -30 50 -30 60 0"
        const val S2 = "M30 70 h40 v30 h-40 z M100 60 h40 v30 h-40 z M100 110 h40 v30 h-40 z M70 85 L100 75 M70 85 L100 125"
        const val S3 = "M20 72 c 30 -10 60 10 90 0 s 60 -10 90 0 M20 100 h170 M20 124 h120"
        const val S4 = "M40 120 c 20 -60 60 -60 80 0 s 60 60 80 0"

        fun designFolders() = listOf(
            Folder("ds", "Distributed systems", 0),
            Folder("cc", "Compilers", 6),
            Folder("jn", "Journal", 7),
            Folder("sk", "Sketches", 2),
        )

        fun designSamples() = listOf(
            Sample("raft", "ds", "Raft", "Today", PageKind.Infinite, 0, 2, S2),
            Sample("paxos", "ds", "Paxos vs Raft", "Nov 21", PageKind.A4, 4, 0, S1),
            Sample("clocks", "ds", "Vector clocks", "Nov 18", PageKind.A4, 2, 1, S3),
            Sample("cap", "ds", "CAP sketch", "Nov 12", PageKind.Infinite, 0, 3, S4),
            Sample("gossip", "ds", "Gossip protocols", "Nov 9", PageKind.Letter, 3, 0, S3),
            Sample("crdt", "ds", "CRDTs", "Nov 4", PageKind.Infinite, 0, 2, S2),
            Sample("spanner", "ds", "Spanner notes", "Oct 30", PageKind.Screen, 1, 0, S1),
            Sample("exam", "ds", "Exam review", "Oct 22", PageKind.A4, 6, 1, S4),
        )

        /** Renders [scribble] at 2× into a transparent 480 × 190 bitmap, or null for a blank note. */
        fun thumbnail(scribble: String, ink: Color): ImageBitmap? {
            if (scribble.isEmpty()) return null
            val bitmap = ImageBitmap(480, 190)
            val canvas = Canvas(bitmap)
            canvas.scale(2f, 2f)
            canvas.translate(0f, -55f)
            val paint = Paint().apply {
                color = ink
                style = PaintingStyle.Stroke
                strokeWidth = 2f
                strokeCap = StrokeCap.Round
                strokeJoin = StrokeJoin.Round
                isAntiAlias = true
            }
            canvas.drawPath(PathParser().parsePathString(scribble).toPath(), paint)
            return bitmap
        }
    }
}
