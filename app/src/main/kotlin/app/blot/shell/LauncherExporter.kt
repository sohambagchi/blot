package app.blot.shell

import androidx.activity.ComponentActivity
import app.blot.core.model.Rect
import app.blot.export.ExportLauncher
import app.blot.export.exportOptionsFor
import app.blot.ui.editor.ExportArea
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * [Exporter] through [ExportLauncher]: the share sheet, or a document the user creates.
 * Construct it in the activity's `onCreate`, since the launcher registers activity results.
 * One export at a time: a new one cancels any still waiting for the folder picker.
 */
class LauncherExporter(activity: ComponentActivity) : Exporter {
    // Main thread only.
    private var waiting: CancellableContinuation<ExportLauncher.Result>? = null

    private val launcher = ExportLauncher(activity, onResult = { result ->
        val c = waiting
        waiting = null
        c?.resume(result)
    })

    override suspend fun export(job: ExportJob, target: ExportTarget): Boolean = withContext(Dispatchers.Main.immediate) {
        waiting?.cancel()
        waiting = null
        val dialog = if (job.visibleArea == null && job.options.area == ExportArea.VisibleArea) {
            job.options.copy(area = ExportArea.WholeCanvas)
        } else {
            job.options
        }
        val options = exportOptionsFor(dialog, job.visibleArea ?: NO_AREA, job.selection, job.dark)
        val result = suspendCancellableCoroutine { c ->
            waiting = c
            when (target) {
                ExportTarget.Share -> launcher.share(job.note, job.assets, options)
                ExportTarget.Folder -> launcher.saveToFolder(job.note, job.assets, options)
            }
        }
        when (result) {
            is ExportLauncher.Result.Failed -> throw result.error
            ExportLauncher.Result.Cancelled -> false
            is ExportLauncher.Result.Saved, is ExportLauncher.Result.Shared -> true
        }
    }

    private companion object {
        val NO_AREA = Rect(0f, 0f, 0f, 0f)
    }
}
