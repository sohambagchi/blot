package app.blot.shell

import kotlin.math.roundToInt
import app.blot.core.model.Rect
import app.blot.core.model.PageSetup
import app.blot.canvas.pagesBounds
import androidx.compose.ui.unit.IntSize
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
import app.blot.canvas.AssetImages
import app.blot.canvas.CanvasController
import app.blot.canvas.FrontBufferContent
import app.blot.canvas.OutlineCache
import app.blot.canvas.SceneRenderer
import app.blot.core.FORMAT_VERSION
import app.blot.core.export.ExportAssets
import app.blot.core.model.Note
import app.blot.core.model.NoteTitle
import app.blot.core.model.format.NoteFormatException
import app.blot.core.model.format.NoteJson
import app.blot.core.store.FolderNameException
import app.blot.core.store.FolderNames
import app.blot.core.store.NoteId
import app.blot.core.store.NoteMeta
import app.blot.core.store.NoteStore
import app.blot.core.store.NoteSummary
import app.blot.core.store.NoteUnreadableException
import app.blot.core.store.TreeNoteStore
import app.blot.storage.NoteFolder
import app.blot.storage.SafFileTree
import app.blot.ui.editor.ExportArea
import app.blot.ui.editor.ExportOptions
import app.blot.ui.library.FolderDialog
import app.blot.ui.library.FolderItem
import app.blot.ui.library.LibraryActions
import app.blot.ui.library.LibraryExport
import app.blot.ui.library.LibraryUiState
import app.blot.ui.library.NoteCard
import app.blot.ui.library.PageKind
import app.blot.ui.library.StorageState
import app.blot.ui.settings.DebugScreen
import app.blot.ui.settings.SettingsUiState
import app.blot.ui.theme.ThemeSetting
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
import java.time.temporal.ChronoUnit
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
    private var folderNames: List<String> = emptyList()
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
            message("Blot couldn't keep access to that folder")
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

    /** Opens the editor on note [id], which lives in [inFolder] (null: the top level). */
    private fun openSession(store: NoteStore, id: NoteId, inFolder: String?, note: Note, message: String?, readOnly: Boolean, editTitle: Boolean = false) {
        val path = (library.storage as? StorageState.Ready)?.path.orEmpty()
        val s = EditorSession(
            id, note,
            SessionSetup(dark, settings.penOnly, settings.swapSideButtons, settings.frontBuffer, inFolder ?: path.substringAfterLast('/'), message, readOnly, editTitle),
            store, ioScope, main, thumbnails, exporter,
            onBack = ::closeEditor,
            onPenOnlyChanged = { settings.penOnly = it },
            onSaveFailed = { if (it is SecurityException) updateFolder(folder.state()) },
            screenPageSize = { screenSizeDp() },
            onBackgroundChanged = { settings.background = it },
        )
        library = library.copy(
            cardMenu = null, moveMenu = false, folderMenu = null, folderDialog = null, renaming = null, newNoteOpen = false, settingsOpen = false,
        )
        screen = Screen.Editor(s)
    }

    // The library.

    /** Lists the folder and rebuilds the cards, then draws missing thumbnails one by one. */
    fun refresh() {
        if (treeUri == null) return
        refreshJob?.cancel()
        val darkNow = dark
        refreshJob = main.launch {
            val (list, folders) = try {
                withStore { it.list() to it.folders() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                storageFailed(e, "Couldn't list your notes")
                return@launch
            }
            cards = withContext(io) { list.map { card(it, darkNow) } }
            folderNames = folders
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

    /** Shows the cards of the selected folder, or all of them, that match the search. */
    private fun showCards() {
        val q = library.query.trim()
        val selected = library.selectedFolder?.takeIf { it in folderNames }
        val counts = cards.groupingBy { it.folder }.eachCount()
        library = library.copy(
            folders = listOf(FolderItem(null, "All notes", cards.size)) + folderNames.map { FolderItem(it, it, counts[it] ?: 0) },
            selectedFolder = selected,
            notes = cards.filter { (selected == null || it.folder == selected) && (q.isEmpty() || it.title.contains(q, ignoreCase = true)) },
        )
    }

    /** io thread. */
    private fun card(s: NoteSummary, dark: Boolean): NoteCard {
        val info = thumbnails.info(s.id.value, s.modified)
        val file = thumbnails.file(s.id.value, s.modified, dark)
        val bitmap = file?.let { f ->
            decoded.get(f.name) ?: ThumbnailCache.decode(f)?.asImageBitmap()?.also { decoded.put(f.name, it) }
        }
        val title = NoteTitle.display(s.title)
        return NoteCard(
            id = s.id.value,
            title = if (s.damaged) "$title (unreadable)" else title,
            date = formatDate(s.modified),
            kind = info?.kind ?: PageKind.Infinite,
            pages = info?.pages ?: 1,
            thumbnail = bitmap,
            untitled = s.title.isBlank(),
            folder = s.folder,
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
                val (store, opened, folder) = withStore { Triple(it, readForEditing(it, NoteId(id)), it.folderOf(NoteId(id))) }
                openSession(store, NoteId(id), folder, opened.note, opened.message, opened.readOnly)
            } catch (e: NoteUnreadableException) {
                message(
                    if (e.suppressed.any { it is NewerFormat }) "This note needs a newer version of Blot"
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
        val note = CanvasController.newNote(CanvasEditor.pageSetup(kind, screen = screenSizeDp())).copy(background = settings.background)
        val folder = library.selectedFolder
        main.launch {
            try {
                val (store, id) = withStore {
                    it to it.create(NoteJson.encode(note).encodeToByteArray(), NoteMeta(note.title, note.modified.toEpochMilli()), folder)
                }
                openSession(store, id, folder, note, null, false, editTitle = true)
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

    override fun setRenaming(id: String?) {
        library = library.copy(renaming = id, cardMenu = null, newNoteOpen = false)
    }

    override fun renameNote(id: String, title: String) {
        library = library.copy(renaming = null)
        main.launch {
            try {
                withStore { renameInStore(it, NoteId(id), title) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                storageFailed(e, "Couldn't rename the note")
            }
            refresh()
        }
    }

    // The note behind library.exporting, read once when the dialog opens.
    private var exportSource: Note? = null

    override fun openExport(id: String) {
        val card = library.notes.firstOrNull { it.id == id } ?: return
        exportSource = null
        library = library.copy(cardMenu = null, exporting = LibraryExport(id, card.title))
        main.launch {
            try {
                val note = withStore { it.readNote(NoteId(id)) { b -> NoteJson.decode(b.decodeToString()) }.value }
                val e = library.exporting?.takeIf { it.id == id } ?: return@launch
                exportSource = note
                val whole = when (val p = note.page) {
                    is PageSetup.Pages -> pagesBounds(p)
                    PageSetup.Infinite -> note.elements.map { it.bounds }.reduceOrNull(Rect::union)
                }
                val sizes = whole?.let { mapOf(ExportArea.WholeCanvas to IntSize(it.width.roundToInt(), it.height.roundToInt())) }.orEmpty()
                library = library.copy(
                    exporting = e.copy(
                        subtitle = listOf(card.title, CanvasEditor.pageLabel(note.page)).joinToString(" · "),
                        info = e.info.copy(areaSizes = sizes, pattern = note.background?.pattern),
                    ),
                )
                renderExportPreview(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                library = library.copy(exporting = null)
                storageFailed(e, "Couldn't open the note for export")
            }
        }
    }

    private fun renderExportPreview(id: String) {
        val note = exportSource ?: return
        val darkNow = dark && library.exporting?.options?.lightColours == false
        main.launch {
            val preview = try {
                withStore { store ->
                    val images = NoteAssetImages(read = { store.readAsset(NoteId(id), it) }, launch = {})
                    val renderer = SceneRenderer(OutlineCache(), MarkdownTextBoxRenderer(images, darkNow), AssetImages { ref, _ -> images.loadNow(ref) })
                    ThumbnailRenderer.render(note, darkNow, renderer, PREVIEW_WIDTH, PREVIEW_HEIGHT, pattern = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "export preview of $id failed", e)
                null
            }
            val e = library.exporting?.takeIf { it.id == id } ?: return@launch
            library = library.copy(exporting = e.copy(info = e.info.copy(preview = preview?.asImageBitmap())))
        }
    }

    override fun setExportOptions(options: ExportOptions) {
        val e = library.exporting ?: return
        library = library.copy(exporting = e.copy(options = options))
        if (options.lightColours != e.options.lightColours) renderExportPreview(e.id)
    }

    override fun closeExport() {
        library = library.copy(exporting = null)
        exportSource = null
    }

    override fun exportToFolder() = exportFromLibrary(ExportTarget.Folder)

    override fun shareExport() = exportFromLibrary(ExportTarget.Share)

    private fun exportFromLibrary(target: ExportTarget) {
        val e = library.exporting ?: return
        main.launch {
            try {
                val darkNow = dark
                val job = withStore { store ->
                    val nid = NoteId(e.id)
                    val note = store.readNote(nid) { b -> NoteJson.decode(b.decodeToString()) }.value
                    ExportJob(note, ExportAssets.of(store, nid), e.options, dark = darkNow)
                }
                if (exporter.export(job, target) && target == ExportTarget.Folder) closeExport()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                storageFailed(e, "Couldn't export the note")
            }
        }
    }

    override fun selectFolder(id: String?) {
        library = library.copy(selectedFolder = id, folderMenu = null, cardMenu = null, moveMenu = false)
        showCards()
    }

    override fun newFolder() {
        library = library.copy(folderDialog = FolderDialog.New(), folderMenu = null, cardMenu = null, newNoteOpen = false)
    }

    override fun setFolderMenu(id: String?) {
        library = library.copy(folderMenu = id, cardMenu = null, moveMenu = false, newNoteOpen = false)
    }

    override fun setFolderDialog(dialog: FolderDialog?) {
        library = library.copy(folderDialog = dialog, folderMenu = null)
    }

    override fun createFolder(name: String) {
        FolderNames.problem(name, folderNames)?.let {
            library = library.copy(folderDialog = FolderDialog.New(it, name))
            return
        }
        main.launch {
            try {
                val created = withStore { it.createFolder(name) }
                folderNames = (folderNames + created).sortedWith(String.CASE_INSENSITIVE_ORDER)
                library = library.copy(folderDialog = null, selectedFolder = created)
                showCards()
            } catch (e: FolderNameException) {
                library = library.copy(folderDialog = FolderDialog.New(e.reason, name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                library = library.copy(folderDialog = null)
                storageFailed(e, "Couldn't create the folder")
            }
            refresh()
        }
    }

    override fun renameFolder(id: String, name: String) {
        FolderNames.problem(name, folderNames, renaming = id)?.let {
            library = library.copy(folderDialog = FolderDialog.Rename(id, id, it))
            return
        }
        main.launch {
            try {
                val renamed = withStore { it.renameFolder(id, name) }
                folderNames = folderNames.map { if (it == id) renamed else it }.sortedWith(String.CASE_INSENSITIVE_ORDER)
                cards = cards.map { if (it.folder == id) it.copy(folder = renamed) else it }
                library = library.copy(folderDialog = null, selectedFolder = library.selectedFolder.let { if (it == id) renamed else it })
                showCards()
            } catch (e: FolderNameException) {
                library = library.copy(folderDialog = FolderDialog.Rename(id, id, e.reason))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                library = library.copy(folderDialog = null)
                storageFailed(e, "Couldn't rename the folder")
            }
            refresh()
        }
    }

    override fun deleteFolder(id: String) {
        library = library.copy(folderDialog = null, folderMenu = null)
        main.launch {
            try {
                val deleted = withStore { it.deleteFolder(id) }
                if (!deleted.removed) {
                    message("Moved the notes out of “$id”. It still holds other files, so the folder was kept.")
                } else if (library.selectedFolder == id) {
                    library = library.copy(selectedFolder = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                storageFailed(e, "Couldn't delete the folder")
            }
            refresh()
        }
    }

    override fun setMoveMenu(open: Boolean) {
        library = library.copy(moveMenu = open)
    }

    override fun moveNote(id: String, folder: String?) {
        library = library.copy(cardMenu = null, moveMenu = false)
        main.launch {
            try {
                withStore { it.move(NoteId(id), folder) }
                cards = cards.map { if (it.id == id) it.copy(folder = folder) else it }
                showCards()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                storageFailed(e, "Couldn't move the note")
            }
            refresh()
        }
    }

    override fun setQuery(query: String) {
        if (query == library.query) return
        library = library.copy(query = query)
        showCards()
    }

    override fun setNewNoteOpen(open: Boolean) {
        library = library.copy(newNoteOpen = open, cardMenu = null)
    }

    override fun setCardMenu(id: String?) {
        library = library.copy(cardMenu = id, moveMenu = false, folderMenu = null, newNoteOpen = false)
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
            folderNames = emptyList()
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
            newer -> Opened(read.value, "This note was saved by a newer version of Blot. Showing its older backup; changes won't be saved.", true)
            else -> Opened(read.value, "Opened from the backup copy: note.json was missing or unreadable. Your next edit saves over it.", false)
        }
    }

    companion object {
        private const val TAG = "AppModel"
        private const val PREVIEW_WIDTH = 960
        private const val PREVIEW_HEIGHT = 540
        private const val THUMBNAIL_CACHE = 200
        private val FORMAT = Regex("\"format\"\\s*:\\s*(\\d+)")

        private fun summarize(bytes: ByteArray): NoteMeta {
            val n = NoteJson.decode(bytes.decodeToString())
            return NoteMeta(n.title, n.modified.toEpochMilli())
        }

        /**
         * Renames note [id] in [store] to [title], cleaned as the editor does, as a save: the
         * modified time moves to [now] and the library index takes the new title. Blocking.
         */
        fun renameInStore(store: NoteStore, id: NoteId, title: String, now: Instant = Instant.now()) {
            val note = store.readNote(id) { NoteJson.decode(it.decodeToString()) }.value
            val clean = NoteTitle.clean(title)
            if (clean == note.title) return
            val renamed = note.copy(title = clean, modified = now.truncatedTo(ChronoUnit.MILLIS))
            store.writeNote(id, NoteJson.encode(renamed).encodeToByteArray(), NoteMeta(renamed.title, renamed.modified.toEpochMilli()))
        }

        /** Whether `note.json` [text] declares a format newer than this app reads. */
        fun isNewerFormat(text: String): Boolean =
            FORMAT.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { it > FORMAT_VERSION } ?: false

        /** "Documents/Blot" for the external-storage tree `primary:Documents/Blot`. */
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
