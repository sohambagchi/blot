package app.blot.core.export

import app.blot.core.model.Element
import app.blot.core.model.Note
import app.blot.core.model.PageSetup
import app.blot.core.model.Point
import app.blot.core.model.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One paper area of an export, in canvas units: a note page, or the whole exported rectangle
 * of an infinite note, an area or a selection. Elements are clipped to it.
 *
 * @property dotOrigin where the dot grid's cells start: the page's corner, or the canvas origin.
 */
data class Sheet(val rect: Rect, val dotOrigin: Point)

/**
 * What an export draws: [bounds] (canvas units) is the whole picture; [sheets] are the paper
 * areas inside it, each drawn with its own background, dots and clip. Space in [bounds] outside
 * every sheet is the gap between stacked pages. [elements] keeps the note's stacking order.
 */
class ExportPlan(val bounds: Rect, val sheets: List<Sheet>, val elements: List<Element>) {
    /** [elements] that reach into [sheet]. */
    fun elementsOn(sheet: Sheet): List<Element> = elements.filter { it.bounds.intersects(sheet.rect) }

    companion object {
        /** Side of the box exported for an empty note or selection, before the margin. */
        const val EMPTY_SIZE = 1f

        fun of(note: Note, options: ExportOptions): ExportPlan {
            val page = note.page
            val range = options.range
            return when {
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
                    val sheets = (first..last).map { page.pageRect(it).let { r -> Sheet(r, Point(r.x, r.y)) } }
                    val bounds = sheets.first().rect.union(sheets.last().rect)
                    ExportPlan(bounds, sheets, note.elements.filter { it.bounds.intersects(bounds) })
                }
                else -> single(framed(note.elements, options.margin), note.elements)
            }
        }

        private fun single(rect: Rect, elements: List<Element>) = ExportPlan(rect, listOf(Sheet(rect, Point(0f, 0f))), elements)

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

/** The canvas dots that exports can draw: [RADIUS] dots centred in [SPACING] cells, as in the design. */
object DotGrid {
    /** Canvas units. */
    const val SPACING = 28f
    const val RADIUS = 1.2f

    /** Centres of the dots in [area], for cells starting at [origin]. */
    fun dots(area: Rect, origin: Point): Sequence<Point> {
        val half = SPACING / 2
        val i0 = ceil((area.left - origin.x - half) / SPACING).toInt()
        val i1 = floor((area.right - origin.x - half) / SPACING).toInt()
        val j0 = ceil((area.top - origin.y - half) / SPACING).toInt()
        val j1 = floor((area.bottom - origin.y - half) / SPACING).toInt()
        return (j0..j1).asSequence().flatMap { j ->
            (i0..i1).asSequence().map { i -> Point(origin.x + half + i * SPACING, origin.y + half + j * SPACING) }
        }
    }
}
