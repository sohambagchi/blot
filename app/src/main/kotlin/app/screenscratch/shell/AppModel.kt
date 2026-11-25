package app.screenscratch.shell

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.screenscratch.canvas.AssetImages
import app.screenscratch.canvas.CanvasController
import app.screenscratch.canvas.FrontBufferContent
import app.screenscratch.canvas.OutlineCache
import app.screenscratch.canvas.SceneRenderer
import app.screenscratch.core.FORMAT_VERSION
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.format.NoteFormatException
import app.screenscratch.core.model.format.NoteJson
import app.screenscratch.core.store.NoteId
import app.screenscratch.core.store.NoteMeta
import app.screenscratch.core.store.NoteStore
import app.screenscratch.core.store.NoteSummary
import app.screenscratch.core.store.NoteUnreadableException
import app.screenscratch.core.store.TreeNoteStore
import app.screenscratch.storage.NoteFolder
import app.screenscratch.storage.SafFileTree
import app.screenscratch.ui.editor.ExportFormat
import app.screenscratch.ui.editor.ExportOptions
import app.screenscratch.ui.library.FolderItem
import app.screenscratch.ui.library.LibraryActions
import app.screenscratch.ui.library.LibraryUiState
import app.screenscratch.ui.library.NoteCard
import app.screenscratch.ui.library.PageKind
import app.screenscratch.ui.library.StorageState
import app.screenscratch.ui.settings.DebugScreen
import app.screenscratch.ui.settings.SettingsUiState
import app.screenscratch.ui.theme.ThemeSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

/** The one thread all note-folder I/O runs on, so saves, listings and reads happen in order. */
object NotesIo {
    val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "notes-io") }.asCoroutineDispatcher()

    /** Never cancelled: a save started while the app is leaving still finishes. */
    val scope = CoroutineScope(SupervisorJob() + dispatcher)
}

/** Which screen the single activity shows. */
sealed interface Screen {
    data object Library : Screen
    class Editor(val session: EditorSession) : Screen
}

/**
 * The app without its activity: the notes folder, the library over [TreeNoteStore] on SAF, the
 * open editor session, and the settings. Navigation is the [screen] state. Implements the
 * library's actions; activity-bound effects (the folder picker, debug screens) go through the
 * callbacks the activity sets. Main thread only.
 */
class AppModel(
    private val context: Context,
    private val folder: NoteFolder = NoteFolder(context),
    private val settings: AppSettings = AppSettings.of(context),
    private val exporter: Exporter = UnavailableExporter,
    private val thumbnails: ThumbnailCache = ThumbnailCache(File(context.cacheDir, "thumbnails")),
    private val indexDir: File = File(context.filesDir, "library-index"),
    private val io: CoroutineDispatcher = NotesIo.dispatcher,
    private val ioScope: CoroutineScope = NotesIo.scope,
    version: String = "",
) : LibraryActions {
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Opens the system folder picker, starting at the given tree when re-granting. */
    var pickFolder: (Uri?) -> Unit = {}
    var openDebug: (DebugScreen) -> Unit = {}

    /** The window size in dp, for screen-sized pages. */
    var screenSizeDp: () -> Pair<Float, Float> = { 1280f to 800f }

    var screen by mutableStateOf<Screen>(Screen.Library)
        private set

    var themeSetting by mutableStateOf(settings.theme)
        private set

    var library by mutableStateOf(
        LibraryUiState(
            settings = SettingsUiState(
                theme = settings.theme,
                swapSideButtons = settings.swapSideButtons,
                version = version,
                frontBufferWholeNote = settings.frontBuffer == FrontBufferContent.SCENE,
            ),
        ),
    )
        private set

    /** The theme the UI is drawn in; thumbnails follow it. */
    var dark: Boolean = when (settings.theme) {
        ThemeSetting.Light -> false
        ThemeSetting.Dark -> true
        ThemeSetting.System -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
        private set

    private val session: EditorSession? get() = (screen as? Screen.Editor)?.session
    private var treeUri: Uri? = null
    private var lostUri: Uri? = null
    private var cards: List<NoteCard> = emptyList()
    private var refreshJob: Job? = null
    private var opening = false
    private val decoded = LruCache<String, ImageBitmap>(THUMBNAIL_CACHE)

    // Owned by the io thread.
    private var store: NoteStore? = null
    private var storeUri: Uri? = null

    init {
        updateFolder(folder.state())
    }

    // Lifecycle, from the activity.

    fun onResume() {
        updateFolder(folder.state())
        if (screen == Screen.Library) refresh()
    }

    fun onStop() {
        session?.flush()
    }

    fun dispose() {
        session?.close()
        main.cancel()
    }

    /** The folder picker returned [uri], or null if the user backed out. */
    fun onTreePicked(uri: Uri?) {
        val state = try {
            if (uri == null) folder.state() else folder.adopt(uri)
        } catch (e: SecurityException) {
            Log.w(TAG, "couldn't keep access to $uri", e)
            message("Screen Scratch couldn't keep access to that folder")
            folder.state()
        }
        updateFolder(state)
        refresh()
    }

    /** The resolved theme changed: the canvas and thumbnails follow. */
    fun applyDark(dark: Boolean) {
        if (dark == this.dark) return
        this.dark = dark
        session?.setDark(dark)
        if (screen == Screen.Library) refresh()
    }

    // The editor.

    /** Leaves the editor: saves any pending edit, then lists the folder again. */
    fun closeEditor() {
        val s = session ?: return
        s.close()
        screen = Screen.Library
        refresh()
    }

    private fun openSession(store: NoteStore, id: NoteId, note: Note, message: String?, readOnly: Boolean) {
        val path = (library.storage as? StorageState.Ready)?.path.orEmpty()
        val s = EditorSession(
            id, note,
            SessionSetup(dark, settings.penOnly, settings.swapSideButtons, settings.frontBuffer, path.substringAfterLast('/'), message, readOnly),
            store, ioScope, main, thumbnails, exporter,
            onBack = ::closeEditor,
            onPenOnlyChanged = { settings.penOnly = it },
            onSaveFailed = { if (it is SecurityException) updateFolder(folder.state()) },
            screenPageSize = { screenSizeDp() },
        )
        library = library.copy(cardMenu = null, newNoteOpen = false, settingsOpen = false)
        screen = Screen.Editor(s)
    }

    // The library.

    /** Lists the folder and rebuilds the cards, then draws missing thumbnails one by one. */
    fun refresh() {
        if (treeUri == null) return
        refreshJob?.cancel()
        val darkNow = dark
        refreshJob = main.launch {
            val list = try {
                withStore { it.list() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                storageFailed(e, "Couldn't list your notes")
                return@launch
            }
            cards = withContext(io) { list.map { card(it, darkNow) } }
            showCards()
            for (summary in list) {
                if (summary.damaged || thumbnails.file(summary.id.value, summary.modified, darkNow) != null) continue
                val card = try {
                    withStore { s -> renderThumbnail(s, summary, darkNow); card(summary, darkNow) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "thumbnail of ${summary.id} failed", e)
                    continue
                }
                cards = cards.map { if (it.id == card.id) card else it }
                showCards()
            }
        }
    }

    private fun showCards() {
        val q = library.query.trim()
        library = library.copy(
            folders = listOf(FolderItem(null, "All notes", cards.size)),
            selectedFolder = null,
            notes = cards.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) },
        )
    }

    /** io thread. */
    private fun card(s: NoteSummary, dark: Boolean): NoteCard {
        val info = thumbnails.info(s.id.value, s.modified)
        val file = thumbnails.file(s.id.value, s.modified, dark)
        val bitmap = file?.let { f ->
            decoded.get(f.name) ?: ThumbnailCache.decode(f)?.asImageBitmap()?.also { decoded.put(f.name, it) }
        }
        val title = s.title.ifBlank { EditorSession.UNTITLED }
        return NoteCard(
            id = s.id.value,
            title = if (s.damaged) "$title (unreadable)" else title,
            date = formatDate(s.modified),
            kind = info?.kind ?: PageKind.Infinite,
            pages = info?.pages ?: 1,
            thumbnail = bitmap,
        )
    }

    /** io thread: reads a note and caches its thumbnail and card info. */
    private fun renderThumbnail(store: NoteStore, s: NoteSummary, dark: Boolean) {
        val note = store.readNote(s.id) { NoteJson.decode(it.decodeToString()) }.value
        val images = NoteAssetImages(read = { store.readAsset(s.id, it) }, launch = {})
        val renderer = SceneRenderer(OutlineCache(), MarkdownTextBoxRenderer(images, dark), AssetImages { ref, _ -> images.loadNow(ref) })
        thumbnails.put(s.id.value, s.modified, dark, ThumbnailRenderer.render(note, dark, renderer), CardInfo.of(note))
    }

    override fun openNote(id: String) {
        if (opening || screen != Screen.Library) return
        opening = true
        library = library.copy(cardMenu = null, newNoteOpen = false)
        main.launch {
            try {
                val (store, opened) = withStore { it to readForEditing(it, NoteId(id)) }
                openSession(store, NoteId(id), opened.note, opened.message, opened.readOnly)
            } catch (e: NoteUnreadableException) {
                message(
                    if (e.suppressed.any { it is NewerFormat }) "This note needs a newer version of Screen Scratch"
                    else "Couldn't open the note: its note.json and backup are both unreadable",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                storageFailed(e, "Couldn't open the note")
            } finally {
                opening = false
            }
        }
    }

    override fun newNote(kind: PageKind) {
        if (opening || screen != Screen.Library) return
        opening = true
        library = library.copy(newNoteOpen = false)
        val note = CanvasController.newNote(CanvasEditor.pageSetup(kind, screen = screenSizeDp()))
        main.launch {
            try {
                val (store, id) = withStore {
                    it to it.create(NoteJson.encode(note).encodeToByteArray(), NoteMeta(note.title, note.modified.toEpochMilli()))
                }
                openSession(store, id, note, null, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                storageFailed(e, "Couldn't create the note")
            } finally {
                opening = false
            }
        }
    }

    override fun deleteNote(id: String) {
        library = library.copy(cardMenu = null)
        main.launch {
            try {
                withStore { it.delete(NoteId(id)) }
                withContext(io) { thumbnails.remove(id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                storageFailed(e, "Couldn't delete the note")
            }
            refresh()
        }
    }

    override fun exportNote(id: String, format: ExportFormat) = exportFromLibrary(id, ExportOptions(format = format), ExportTarget.Folder)

    override fun shareNote(id: String) = exportFromLibrary(id, ExportOptions(), ExportTarget.Share)

    private fun exportFromLibrary(id: String, options: ExportOptions, target: ExportTarget) {
        library = library.copy(cardMenu = null)
        main.launch {
            try {
                val note = withStore { it.readNote(NoteId(id)) { b -> NoteJson.decode(b.decodeToString()) }.value }
                withContext(io) { exporter.export(note, options, target) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                storageFailed(e, "Couldn't export the note")
            }
        }
    }

    override fun selectFolder(id: String?) {
        library = library.copy(selectedFolder = null)
    }

    override fun newFolder() = message("Folders aren't available yet")

    override fun setQuery(query: String) {
        if (query == library.query) return
        library = library.copy(query = query)
        showCards()
    }

    override fun setNewNoteOpen(open: Boolean) {
        library = library.copy(newNoteOpen = open, cardMenu = null)
    }

    override fun setCardMenu(id: String?) {
        library = library.copy(cardMenu = id, newNoteOpen = false)
    }

    override fun setSettingsOpen(open: Boolean) {
        library = library.copy(settingsOpen = open, newNoteOpen = false, cardMenu = null)
    }

    override fun toggleDark() = setTheme(if (dark) ThemeSetting.Light else ThemeSetting.Dark)

    override fun dismissMessage() {
        library = library.copy(message = null)
    }

    // Settings.

    override fun setTheme(theme: ThemeSetting) {
        settings.theme = theme
        themeSetting = theme
        library = library.copy(settings = library.settings.copy(theme = theme))
    }

    override fun setSwapSideButtons(swap: Boolean) {
        settings.swapSideButtons = swap
        session?.controller?.swapStylusButtons = swap
        library = library.copy(settings = library.settings.copy(swapSideButtons = swap))
    }

    override fun setFrontBufferWholeNote(on: Boolean) {
        val mode = if (on) FrontBufferContent.SCENE else FrontBufferContent.INK_ONLY
        settings.frontBuffer = mode
        session?.controller?.frontBufferContent = mode
        library = library.copy(settings = library.settings.copy(frontBufferWholeNote = on))
    }

    override fun openDebugScreen(screen: DebugScreen) = openDebug(screen)

    override fun chooseFolder() = pickFolder(null)

    override fun regrantFolder() = pickFolder(lostUri)

    override fun forgetFolder() {
        folder.forget()
        updateFolder(folder.state())
        library = library.copy(settingsOpen = false)
    }

    // Storage.

    private fun updateFolder(state: NoteFolder.State) {
        treeUri = (state as? NoteFolder.State.Ready)?.treeUri
        lostUri = (state as? NoteFolder.State.PermissionLost)?.treeUri
        val storage = when (state) {
            NoteFolder.State.NotChosen -> StorageState.NotChosen
            is NoteFolder.State.Ready -> StorageState.Ready(displayPath(state.treeUri))
            is NoteFolder.State.PermissionLost -> StorageState.PermissionLost(displayPath(state.treeUri))
        }
        if (storage == library.storage) return
        if (storage !is StorageState.Ready) {
            refreshJob?.cancel()
            cards = emptyList()
        }
        library = library.copy(storage = storage)
        showCards()
    }

    private suspend fun <T> withStore(block: (NoteStore) -> T): T {
        val uri = treeUri ?: throw IllegalStateException("No notes folder is chosen")
        return withContext(io) {
            val s = store?.takeIf { storeUri == uri }
                ?: TreeNoteStore(SafFileTree(context.contentResolver, uri), indexDir, ::summarize).also {
                    store = it
                    storeUri = uri
                }
            block(s)
        }
    }

    private fun storageFailed(e: Exception, what: String) {
        Log.w(TAG, what, e)
        if (e is SecurityException) {
            updateFolder(folder.state())
            if (library.storage !is StorageState.PermissionLost) message("$what: ${EditorSession.describe(e)}")
        } else {
            message("$what: ${EditorSession.describe(e)}")
        }
    }

    private fun message(text: String) {
        library = library.copy(message = text)
    }

    private class Opened(val note: Note, val message: String?, val readOnly: Boolean)

    /** Marks a parse failure of a note.json written by a newer app. */
    private class NewerFormat(cause: Exception) : Exception(cause)

    private fun readForEditing(store: NoteStore, id: NoteId): Opened {
        var newer = false
        val read = store.readNote(id) { bytes ->
            val text = bytes.decodeToString()
            try {
                NoteJson.decode(text)
            } catch (e: NoteFormatException) {
                if (isNewerFormat(text)) {
                    newer = true
                    throw NewerFormat(e)
                }
                throw e
            }
        }
        return when {
            !read.fromBackup -> Opened(read.value, null, false)
            newer -> Opened(read.value, "This note was saved by a newer version of Screen Scratch. Showing its older backup; changes won't be saved.", true)
            else -> Opened(read.value, "Opened from the backup copy: note.json was missing or unreadable. Your next edit saves over it.", false)
        }
    }

    companion object {
        private const val TAG = "AppModel"
        private const val THUMBNAIL_CACHE = 200
        private val FORMAT = Regex("\"format\"\\s*:\\s*(\\d+)")

        private fun summarize(bytes: ByteArray): NoteMeta {
            val n = NoteJson.decode(bytes.decodeToString())
            return NoteMeta(n.title, n.modified.toEpochMilli())
        }

        /** Whether `note.json` [text] declares a format newer than this app reads. */
        fun isNewerFormat(text: String): Boolean =
            FORMAT.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { it > FORMAT_VERSION } ?: false

        /** "Documents/ScreenScratch" for the external-storage tree `primary:Documents/ScreenScratch`. */
        fun displayPath(treeUri: Uri): String {
            val id = try {
                DocumentsContract.getTreeDocumentId(treeUri)
            } catch (e: IllegalArgumentException) {
                return treeUri.toString()
            }
            val path = id.substringAfter(':', id)
            return path.ifEmpty { id.substringBefore(':') }
        }

        /** "Today", "Yesterday", "Nov 21", or "Nov 21, 2025" in another year. */
        fun formatDate(epochMs: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
            val d = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
            return when {
                d == today -> "Today"
                d == today.minusDays(1) -> "Yesterday"
                d.year == today.year -> DateTimeFormatter.ofPattern("MMM d", locale).format(d)
                else -> DateTimeFormatter.ofPattern("MMM d, yyyy", locale).format(d)
            }
        }
    }
}
