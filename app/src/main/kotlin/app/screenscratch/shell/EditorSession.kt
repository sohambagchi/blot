package app.screenscratch.shell

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import app.screenscratch.canvas.AssetImages
import app.screenscratch.canvas.CanvasController
import app.screenscratch.canvas.FrontBufferContent
import app.screenscratch.canvas.OutlineCache
import app.screenscratch.canvas.SceneRenderer
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.format.NoteJson
import app.screenscratch.core.store.NoteId
import app.screenscratch.core.store.NoteMeta
import app.screenscratch.core.store.NoteStore
import app.screenscratch.ui.editor.EditorUiState
import app.screenscratch.ui.editor.ExportOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How the editor starts: settings and theme, and what the chrome should say first. */
data class SessionSetup(
    val dark: Boolean,
    val penOnly: Boolean,
    val swapSideButtons: Boolean,
    val frontBuffer: FrontBufferContent,
    val folderName: String,
    /** Shown once when the editor opens, e.g. that the note came from its backup. */
    val message: String? = null,
    /** Edits are not saved, e.g. because `note.json` is from a newer app version. */
    val readOnly: Boolean = false,
)

/**
 * One open note: the canvas controller and the chrome's state holder, with autosave through
 * [store] on the [io] scope (one thread, shared with the library, so a save always finishes
 * before the library lists the folder again), thumbnails, text-box and image rendering.
 * Create and use on the main thread; [close] when leaving the editor.
 */
class EditorSession(
    val noteId: NoteId,
    note: Note,
    setup: SessionSetup,
    private val store: NoteStore,
    private val io: CoroutineScope,
    private val main: CoroutineScope,
    private val thumbnails: ThumbnailCache,
    private val exporter: Exporter,
    onBack: () -> Unit,
    onPenOnlyChanged: (Boolean) -> Unit,
    private val onSaveFailed: (Exception) -> Unit,
    screenPageSize: () -> Pair<Float, Float>,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val readOnly = setup.readOnly

    val controller = CanvasController(note).apply {
        darkMode = setup.dark
        penOnly = setup.penOnly
        swapStylusButtons = setup.swapSideButtons
        frontBufferContent = setup.frontBuffer
    }

    val images = NoteAssetImages(read = { store.readAsset(noteId, it) }, launch = { io.launch { it() } })

    val textRenderer = MarkdownTextBoxRenderer(images, setup.dark).apply {
        onImageLoaded = { mainHandler.post { controller.redraw() } }
    }

    val editor = CanvasEditor(
        controller,
        EditorUiState(
            noteTitle = note.title.ifBlank { UNTITLED },
            folderName = setup.folderName,
            penOnly = setup.penOnly,
            message = setup.message,
        ),
        onBack = onBack,
        onExport = ::export,
        onPenOnlyChanged = onPenOnlyChanged,
        onExportOpened = ::renderExportPreview,
        screenPageSize = screenPageSize,
    )

    val autosave = Autosave(::save)

    // The io thread's own renderer for thumbnails; SceneRenderer is single-threaded.
    private val thumbText = MarkdownTextBoxRenderer(images)
    private val thumbRenderer = SceneRenderer(OutlineCache(), thumbText, AssetImages { ref, _ -> images.loadNow(ref) })

    @Volatile private var dark = setup.dark
    private val watch: Job = main.launch {
        controller.note.drop(1).collect { n ->
            editor.noteChanged(n)
            if (!readOnly) autosave.onEdit(n)
        }
    }

    /** Applies the app theme to the canvas and the text boxes. */
    fun setDark(dark: Boolean) {
        this.dark = dark
        textRenderer.dark = dark
        controller.darkMode = dark
    }

    /** Saves any pending edit now; for `onStop`. */
    fun flush() = autosave.flush()

    /** Saves any pending edit and stops watching the note. */
    fun close() {
        autosave.flush()
        watch.cancel()
    }

    private fun save(note: Note) {
        val darkNow = dark
        io.launch {
            try {
                store.writeNote(noteId, NoteJson.encode(note).encodeToByteArray(), NoteMeta(note.title, note.modified.toEpochMilli()))
            } catch (e: Exception) {
                Log.w(TAG, "save of $noteId failed", e)
                withContext(Dispatchers.Main.immediate) {
                    autosave.retry(note)
                    editor.showMessage("Couldn't save: ${describe(e)}")
                    onSaveFailed(e)
                }
                return@launch
            }
            try {
                thumbText.dark = darkNow
                thumbnails.put(noteId.value, note.modified.toEpochMilli(), darkNow, ThumbnailRenderer.render(note, darkNow, thumbRenderer), CardInfo.of(note))
            } catch (e: Exception) {
                Log.w(TAG, "thumbnail of $noteId failed", e)
            }
        }
    }

    private fun export(options: ExportOptions, target: ExportTarget) {
        val note = controller.note.value
        io.launch {
            val error = try {
                exporter.export(note, options, target)
                null
            } catch (e: Exception) {
                e
            }
            withContext(Dispatchers.Main.immediate) {
                if (error != null) editor.showMessage(error.message ?: "Export failed")
                else if (target == ExportTarget.Folder) editor.setExportOpen(false)
            }
        }
    }

    private fun renderExportPreview() {
        val note = controller.note.value
        val darkNow = !editor.state.export.lightColours && dark
        io.launch {
            thumbText.dark = darkNow
            val bmp = try {
                ThumbnailRenderer.render(note, darkNow, thumbRenderer, PREVIEW_WIDTH, PREVIEW_HEIGHT)
            } catch (e: Exception) {
                null
            }
            withContext(Dispatchers.Main.immediate) { editor.setExportPreview(bmp?.asImageBitmap()) }
        }
    }

    companion object {
        private const val TAG = "EditorSession"
        const val UNTITLED = "Untitled"
        private const val PREVIEW_WIDTH = 960
        private const val PREVIEW_HEIGHT = 540

        /** A failure as the user should read it. */
        fun describe(e: Exception): String = when (e) {
            is SecurityException -> "access to the notes folder was lost"
            else -> e.message ?: e.javaClass.simpleName
        }
    }
}
