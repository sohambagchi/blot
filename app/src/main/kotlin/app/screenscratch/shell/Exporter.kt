package app.screenscratch.shell

import app.screenscratch.core.export.ExportAssets
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.Rect
import app.screenscratch.ui.editor.ExportOptions

/** Where an export goes. */
enum class ExportTarget {
    /** A document the user creates in a folder they pick. */
    Folder,

    /** The system share sheet. */
    Share,
}

/**
 * One export: [note] with its images from [assets], the dialog's [options], and what the editor
 * shows. [visibleArea] is null outside the editor; [dark] is whether the app shows its dark theme.
 */
class ExportJob(
    val note: Note,
    val assets: ExportAssets,
    val options: ExportOptions,
    val visibleArea: Rect? = null,
    val selection: Set<String> = emptySet(),
    val dark: Boolean = false,
)

/**
 * Turns a note into PNG, PDF or SVG and delivers it. Returns false when the user cancelled
 * (backed out of the folder picker); throws on failure, and the exception's message is shown.
 */
fun interface Exporter {
    suspend fun export(job: ExportJob, target: ExportTarget): Boolean
}

/** For tests and previews: every export fails with "not available". */
object UnavailableExporter : Exporter {
    override suspend fun export(job: ExportJob, target: ExportTarget): Boolean =
        throw UnsupportedOperationException("Export isn't available")
}
