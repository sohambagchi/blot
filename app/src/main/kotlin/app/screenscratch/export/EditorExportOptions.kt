package app.screenscratch.export

import app.screenscratch.core.export.ExportBackground
import app.screenscratch.core.export.ExportFormat
import app.screenscratch.core.export.ExportOptions
import app.screenscratch.core.export.ExportRange
import app.screenscratch.core.model.Rect
import app.screenscratch.ui.editor.ExportArea
import app.screenscratch.ui.editor.ExportOptions as DialogOptions
import app.screenscratch.ui.editor.ExportBackground as DialogBackground
import app.screenscratch.ui.editor.ExportFormat as DialogFormat

/**
 * The export for the editor dialog's choices [dialog]. [visibleArea] is the canvas rectangle on
 * screen, [selection] the selected element ids, and [appDark] whether the app shows the dark
 * theme: as the dialog's preview does, colours are dark only when the app is dark and "Light
 * colours" is off. PDF always has paper, since the dialog hides the background choice for it.
 */
fun exportOptionsFor(dialog: DialogOptions, visibleArea: Rect, selection: Set<String>, appDark: Boolean): ExportOptions {
    val format = when (dialog.format) {
        DialogFormat.Png -> ExportFormat.PNG
        DialogFormat.Pdf -> ExportFormat.PDF
        DialogFormat.Svg -> ExportFormat.SVG
    }
    val range = when (dialog.area) {
        ExportArea.WholeCanvas -> ExportRange.WholeNote
        ExportArea.VisibleArea -> ExportRange.Area(visibleArea)
        ExportArea.Selection -> if (selection.isEmpty()) ExportRange.WholeNote else ExportRange.Selection(selection)
    }
    val background = if (format == ExportFormat.PDF || dialog.background == DialogBackground.Paper) {
        ExportBackground.PAPER
    } else {
        ExportBackground.TRANSPARENT
    }
    return ExportOptions(
        format = format,
        range = range,
        background = background,
        dark = appDark && !dialog.lightColours,
        dotGrid = dialog.dotGrid,
        scale = dialog.scale.toFloat(),
    )
}
