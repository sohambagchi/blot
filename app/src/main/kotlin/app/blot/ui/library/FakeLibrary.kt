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
import app.blot.ui.editor.ExportFormat
import app.blot.ui.theme.InkColors
import app.blot.ui.theme.ThemeSetting

/**
 * An in-memory [LibraryActions] with the design's sample notes, for previews and tests. Search
 * and folders filter the samples; new notes, deletes and settings change only memory.
 * [onOpen] is called when a note is opened.
 */
class FakeLibrary(
    storage: StorageState = StorageState.Ready("Documents/Blot"),
    dark: Boolean = false,
    withNotes: Boolean = true,
    private val onOpen: (NoteCard) -> Unit = {},
) : LibraryActions {
    private data class Sample(val id: String, val folder: String, val title: String, val date: String, val kind: PageKind, val pages: Int, val ink: Int, val scribble: String)

    private var samples: List<Sample> = if (withNotes) designSamples() else emptyList()
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
        LibraryUiState(storage = storage, selectedFolder = "ds", settings = app.blot.ui.settings.SettingsUiState(version = "0.7")),
    )
        private set

    init {
        refresh()
    }

    private fun refresh() {
        val s = state
        val q = s.query.trim()
        val inFolder = samples.filter { s.selectedFolder == null || it.folder == s.selectedFolder }
        val shown = inFolder.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
        val counts = samples.groupingBy { it.folder }.eachCount()
        val extra = mapOf("cc" to 6, "jn" to 7, "sk" to 2)
        val folders = listOf(
            FolderItem(null, "All notes", samples.size + extra.values.sum() - extra.keys.sumOf { counts[it] ?: 0 }),
            FolderItem("ds", "Distributed systems", counts["ds"] ?: 0),
            FolderItem("cc", "Compilers", counts["cc"] ?: extra.getValue("cc")),
            FolderItem("jn", "Journal", counts["jn"] ?: extra.getValue("jn")),
            FolderItem("sk", "Sketches", counts["sk"] ?: extra.getValue("sk")),
        )
        val ink = if (dark) InkColors.Dark else InkColors.Light
        state = s.copy(
            folders = folders,
            notes = shown.map {
                val colour = when (it.ink) {
                    1 -> ink.red
                    2 -> ink.blue
                    3 -> ink.green
                    else -> ink.black
                }
                NoteCard(it.id, it.title, it.date, it.kind, it.pages, thumbnail(it.scribble, colour))
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

    override fun newFolder() {}

    override fun setQuery(query: String) {
        if (query == state.query) return
        update { copy(query = query) }
        refresh()
    }

    override fun setNewNoteOpen(open: Boolean) = update { copy(newNoteOpen = open, cardMenu = null) }

    override fun newNote(kind: PageKind) {
        val folder = state.selectedFolder ?: "ds"
        samples = listOf(Sample("n${nextId++}", folder, "Untitled", "Today", kind, 1, 0, "")) + samples
        update { copy(newNoteOpen = false) }
        refresh()
    }

    override fun setCardMenu(id: String?) = update { copy(cardMenu = id, newNoteOpen = false) }
    override fun exportNote(id: String, format: ExportFormat) {}
    override fun shareNote(id: String) {}

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
