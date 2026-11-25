package app.screenscratch.shell

import app.screenscratch.core.model.Note
import app.screenscratch.ui.editor.ExportOptions

/** Where an export goes. */
enum class ExportTarget {
    /** A file in the exports folder. */
    Folder,

    /** The system share sheet. */
    Share,
}

/**
 * Turns a note into PNG, PDF or SVG. Called off the main thread; throws on failure, and the
 * exception's message is shown to the user.
 */
fun interface Exporter {
    suspend fun export(note: Note, options: ExportOptions, target: ExportTarget)
}

/** Stands in until export is built: every export fails with "not available yet". */
object UnavailableExporter : Exporter {
    override suspend fun export(note: Note, options: ExportOptions, target: ExportTarget) {
        throw UnsupportedOperationException("Export isn't available yet")
    }
}
