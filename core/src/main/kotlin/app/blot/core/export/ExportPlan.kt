package app.blot.core.export

import app.blot.core.model.Background
import app.blot.core.model.Element
import app.blot.core.model.Note
import app.blot.core.model.PageSetup
import app.blot.core.model.PatternMarks
import app.blot.core.model.Rect
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One paper area of an export, in canvas units: a note page, or the whole exported rectangle
 * of an infinite note, an area or a selection. Elements are clipped to it.
 */
data class Sheet(val rect: Rect)

/**
 * What an export draws: [bounds] (canvas units) is the whole picture; [sheets] are the paper
 * areas inside it, each drawn with its own background, pattern and clip. Space in [bounds]
 * outside every sheet is the gap between stacked pages. [elements] keeps the note's stacking
 * order. [background] is the pattern to draw, null for none, laid out on [page]'s pages.
 */
class ExportPlan(
    val bounds: Rect,
    val sheets: List<Sheet>,
    val elements: List<Element>,
    val background: Background? = null,
    val page: PageSetup = PageSetup.Infinite,
) {
    /** [elements] that reach into [sheet]. */
    fun elementsOn(sheet: Sheet): List<Element> = elements.filter { it.bounds.intersects(sheet.rect) }

    /**
     * The [background] marks inside [sheet]: one per note page that reaches into it, or one from
     * the canvas origin for an infinite note. Thinned as on screen for [pxPerUnit] output pixels
     * per canvas unit; vector output passes infinity and keeps every mark.
     */
    fun patternOn(sheet: Sheet, pxPerUnit: Float = Float.POSITIVE_INFINITY): List<PatternMarks> {
        val bg = background ?: return emptyList()
        val stride = PatternMarks.stride(bg.spacing, pxPerUnit)
        return when (val p = page) {
            PageSetup.Infinite -> listOfNotNull(PatternMarks.of(bg, null, sheet.rect, stride))
            is PageSetup.Pages -> (0 until p.count).mapNotNull { PatternMarks.of(bg, p.pageRect(it), sheet.rect, stride) }
        }
    }

    companion object {
        /** Side of the box exported for an empty note or selection, before the margin. */
        const val EMPTY_SIZE = 1f

        fun of(note: Note, options: ExportOptions): ExportPlan {
            val page = note.page
            val range = options.range
            val plan = when {
                range is ExportRange.Area -> single(range.rect, note.elements.filter { it.bounds.intersects(range.rect) })
                range is ExportRange.Selection -> {
                    val chosen = note.elements.filter { it.id in range.ids }
                    single(framed(chosen, options.margin), chosen)
                }
                page is PageSetup.Pages -> {
                    val (first, last) = if (range is ExportRange.Pages) {
                        min(range.first, page.count - 1) to min(range.last, page.count - 1)
                    } else {
                        0 to page.count - 1
                    }
                    val sheets = (first..last).map { Sheet(page.pageRect(it)) }
                    val bounds = sheets.first().rect.union(sheets.last().rect)
                    ExportPlan(bounds, sheets, note.elements.filter { it.bounds.intersects(bounds) })
                }
                else -> single(framed(note.elements, options.margin), note.elements)
            }
            val background = note.background.takeIf { options.backgroundPattern } ?: return plan
            return ExportPlan(plan.bounds, plan.sheets, plan.elements, background, page)
        }

        private fun single(rect: Rect, elements: List<Element>) = ExportPlan(rect, listOf(Sheet(rect)), elements)

        private fun framed(elements: List<Element>, margin: Float): Rect =
            (contentBounds(elements) ?: Rect(0f, 0f, EMPTY_SIZE, EMPTY_SIZE)).inflate(margin)

        /** The union of the elements' bounds, or null when there are none. */
        fun contentBounds(elements: List<Element>): Rect? =
            elements.asSequence().map { it.bounds }.reduceOrNull { a, b -> a.union(b) }
    }
}

/** A bitmap size for [bounds] and the scale (px per canvas unit) that fits it in the budget. */
data class PixelSize(val width: Int, val height: Int, val scale: Float)

/** Output sizes for the raster and PDF exporters. */
object ExportSizes {
    /** Points per canvas unit: a canvas unit is 1/160 inch and a point 1/72. */
    const val POINTS_PER_UNIT = 72f / 160f

    /** The largest PDF page side, 200 inches; PDF 1.x viewers reject larger pages. */
    const val MAX_PDF_POINTS = 14_400f

    /**
     * The PNG size for [bounds] at [scale] px per canvas unit, scaled down to at most
     * [maxPixels] pixels. Each side is at least 1 px.
     */
    fun png(bounds: Rect, scale: Float, maxPixels: Long): PixelSize {
        var s = scale
        val area = bounds.width.toDouble() * bounds.height * s * s
        if (area > maxPixels) s = (s * sqrt(maxPixels / area)).toFloat()
        fun side(v: Float) = max(1, ceil(v * s - 1e-3f).toInt())
        var w = side(bounds.width)
        var h = side(bounds.height)
        // Rounding up may cross the budget by a row or column.
        while (w.toLong() * h > maxPixels && s > 0f) {
            s *= 0.999f
            w = side(bounds.width)
            h = side(bounds.height)
        }
        return PixelSize(w, h, s)
    }

    /** A PDF page size in whole points for [rect], with the scale in points per canvas unit. */
    fun pdfPage(rect: Rect): PixelSize {
        val longest = max(rect.width, rect.height) * POINTS_PER_UNIT
        val s = if (longest > MAX_PDF_POINTS) MAX_PDF_POINTS / max(rect.width, rect.height) else POINTS_PER_UNIT
        return PixelSize(max(1, (rect.width * s).roundToInt()), max(1, (rect.height * s).roundToInt()), s)
    }
}
