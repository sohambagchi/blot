package app.blot.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import app.blot.canvas.CanvasPalette
import app.blot.canvas.OutlineCache
import app.blot.canvas.PatternPainter
import app.blot.canvas.SceneRenderer
import app.blot.core.export.ExportAssets
import app.blot.core.export.ExportBackground
import app.blot.core.export.ExportFormat
import app.blot.core.export.ExportOptions
import app.blot.core.export.ExportPalette
import app.blot.core.export.ExportPlan
import app.blot.core.export.ExportSizes
import app.blot.core.export.PixelSize
import app.blot.core.export.Sheet
import app.blot.core.export.SvgNoteWriter
import app.blot.core.export.SvgXml
import app.blot.core.model.Note
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Exports a note as PNG, PDF or SVG. PNG and PDF draw each element with the canvas's
 * [SceneRenderer] and text boxes with [app.blot.markdown.MarkdownLayout], so they look
 * as on screen; PDF stays vector (strokes are filled paths, text is text). SVG serialises the
 * same geometry and layout with [SvgNoteWriter]. Page notes give one PDF page per note page
 * and one tall PNG or SVG with the pages stacked.
 *
 * Runs synchronously; call it off the main thread. Safe to share between threads as long as
 * [outlines] is (each call has its own renderer). The caller closes the stream.
 */
class NoteExporter(private val outlines: OutlineCache = OutlineCache()) {
    /** Exports in [ExportOptions.format]. */
    fun export(note: Note, assets: ExportAssets, options: ExportOptions, out: OutputStream) {
        when (options.format) {
            ExportFormat.PNG -> exportPng(note, assets, options, out)
            ExportFormat.PDF -> exportPdf(note, assets, options, out)
            ExportFormat.SVG -> exportSvg(note, assets, options, out)
        }
    }

    /** Writes one PNG of the whole plan and returns its size and the scale used. */
    fun exportPng(note: Note, assets: ExportAssets, options: ExportOptions, out: OutputStream): PixelSize {
        val plan = ExportPlan.of(note, options)
        val size = ExportSizes.png(plan.bounds, options.scale, options.maxPixels)
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val session = Session(assets, options)
        try {
            val canvas = Canvas(bitmap)
            canvas.scale(size.scale, size.scale)
            canvas.translate(-plan.bounds.x, -plan.bounds.y)
            if (session.paper && plan.sheets.size > 1) session.fill(canvas, plan.bounds, session.palette.desk)
            for (sheet in plan.sheets) session.drawSheet(canvas, plan, sheet, size.scale, -plan.bounds.x * size.scale, -plan.bounds.y * size.scale)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } finally {
            session.close()
            bitmap.recycle()
        }
        return size
    }

    /** Writes a PDF with one page per sheet and returns the page count. */
    fun exportPdf(note: Note, assets: ExportAssets, options: ExportOptions, out: OutputStream): Int {
        val plan = ExportPlan.of(note, options)
        val document = PdfDocument()
        val session = Session(assets, options)
        try {
            for ((i, sheet) in plan.sheets.withIndex()) {
                val size = ExportSizes.pdfPage(sheet.rect)
                val page = document.startPage(PdfDocument.PageInfo.Builder(size.width, size.height, i + 1).create())
                val canvas = page.canvas
                canvas.scale(size.scale, size.scale)
                canvas.translate(-sheet.rect.x, -sheet.rect.y)
                session.drawSheet(canvas, plan, sheet)
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            session.close()
            document.close()
        }
        return plan.sheets.size
    }

    /** Writes one UTF-8 SVG document. */
    fun exportSvg(note: Note, assets: ExportAssets, options: ExportOptions, out: OutputStream) {
        val plan = ExportPlan.of(note, options)
        val session = Session(assets, options)
        val hrefs = HashMap<String, String?>()
        try {
            val writer = SvgNoteWriter(
                session.palette,
                outline = outlines::outline,
                color = { session.color(it) },
                textLayout = { box -> session.text.svgText(box, session.color(box.color.value)) },
                imageHref = { path ->
                    hrefs.getOrPut(path) { assets.read(path)?.let { SvgXml.dataUri(it, path.substringAfterLast('.', "")) } }
                },
            )
            val w = BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8))
            writer.write(plan, options, w)
            w.flush()
        } finally {
            session.close()
        }
    }

    /** One export's renderer, decoded images and laid-out text. */
    private inner class Session(assets: ExportAssets, options: ExportOptions) {
        val dark = options.dark
        val paper = options.background == ExportBackground.PAPER
        private val canvasPalette = CanvasPalette.of(dark)
        val palette = palette(dark)
        private val bitmaps = ExportBitmaps(assets)
        val text = MarkdownTextBoxes(dark, palette.paper, bitmaps)
        private val scene = SceneRenderer(outlines, text, bitmaps)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val pattern = PatternPainter()

        fun color(argb: Int): Int = scene.color(argb, dark)

        fun fill(canvas: Canvas, r: app.blot.core.model.Rect, color: Int) {
            paint.color = color
            canvas.drawRect(r.left, r.top, r.right, r.bottom, paint)
        }

        /**
         * Draws [sheet] at [pxPerUnit] output pixels per canvas unit, with the canvas origin at pixel
         * ([originX], [originY]); infinity and NaN for vector output.
         */
        fun drawSheet(
            canvas: Canvas,
            plan: ExportPlan,
            sheet: Sheet,
            pxPerUnit: Float = Float.POSITIVE_INFINITY,
            originX: Float = Float.NaN,
            originY: Float = Float.NaN,
        ) {
            val r = sheet.rect
            canvas.save()
            canvas.clipRect(r.left, r.top, r.right, r.bottom)
            if (paper) fill(canvas, r, palette.paper)
            for (m in plan.patternOn(sheet, pxPerUnit)) pattern.draw(canvas, m, pxPerUnit, palette.dot, palette.line, originX, originY)
            for (e in plan.elementsOn(sheet)) scene.drawElement(canvas, e, dark, canvasPalette)
            canvas.restore()
        }

        fun close() = bitmaps.recycle()
    }

    companion object {
        /** The canvas's colours for a light or [dark] export. */
        fun palette(dark: Boolean): ExportPalette {
            val c = CanvasPalette.of(dark)
            return ExportPalette(c.paper, c.desk, c.placeholder, c.placeholderEdge, c.patternDot, c.patternLine)
        }
    }
}
