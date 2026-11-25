package app.blot.export

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.blot.core.export.ExportAssets
import app.blot.core.export.ExportFormat
import app.blot.core.export.ExportOptions
import app.blot.core.model.Note
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The editor's "Share…" and "Save to folder" buttons: exports on [background], then opens the
 * share sheet or writes to a document the user creates with `ACTION_CREATE_DOCUMENT`.
 * [onResult] is called on the main thread.
 *
 * Registers activity-result launchers, so construct it before [activity] is started (in
 * `onCreate` or as a property initialiser). A save in progress when the process dies is lost.
 */
class ExportLauncher(
    private val activity: ComponentActivity,
    private val onResult: (Result) -> Unit = {},
    private val exporter: NoteExporter = NoteExporter(),
    private val background: Executor = Executors.newSingleThreadExecutor(),
) {
    sealed interface Result {
        data class Shared(val file: File) : Result
        data class Saved(val uri: Uri) : Result
        data object Cancelled : Result
        data class Failed(val error: Throwable) : Result
    }

    private class Pending(val note: Note, val assets: ExportAssets, val options: ExportOptions)

    private val main = ContextCompat.getMainExecutor(activity)
    private var pending: Pending? = null

    private val savers: Map<ExportFormat, ActivityResultLauncher<String>> = ExportFormat.entries.associateWith { format ->
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType)) { uri -> picked(uri) }
    }

    /** Exports [note] to the cache and opens the share sheet. */
    fun share(note: Note, assets: ExportAssets, options: ExportOptions) {
        val context = activity.applicationContext
        background.execute {
            val outcome = runCatching { ExportFiles.writeToCache(context, note, assets, options, exporter) }
            main.execute {
                outcome.fold(
                    { file ->
                        try {
                            activity.startActivity(ExportFiles.shareIntent(activity, file, options.format, ExportFiles.shareTitle(note)))
                            onResult(Result.Shared(file))
                        } catch (e: ActivityNotFoundException) {
                            onResult(Result.Failed(e))
                        }
                    },
                    { onResult(Result.Failed(it)) },
                )
            }
        }
    }

    /** Asks where to save, then exports [note] there. */
    fun saveToFolder(note: Note, assets: ExportAssets, options: ExportOptions) {
        pending = Pending(note, assets, options)
        savers.getValue(options.format).launch(ExportFiles.fileName(note, options.format))
    }

    private fun picked(uri: Uri?) {
        val p = pending
        pending = null
        if (uri == null || p == null) {
            onResult(Result.Cancelled)
            return
        }
        val context = activity.applicationContext
        background.execute {
            val outcome = runCatching { ExportFiles.writeToUri(context, uri, p.note, p.assets, p.options, exporter) }
            main.execute { onResult(outcome.fold({ Result.Saved(uri) }, { Result.Failed(it) })) }
        }
    }
}
