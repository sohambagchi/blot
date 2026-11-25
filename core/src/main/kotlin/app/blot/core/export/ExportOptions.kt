package app.blot.core.export

import app.blot.core.model.Rect

/** An export file type, with the MIME type and extension it is shared and saved under. */
enum class ExportFormat(val mimeType: String, val extension: String) {
    PNG("image/png", "png"),
    PDF("application/pdf", "pdf"),
    SVG("image/svg+xml", "svg"),
}

/** What part of a note is exported. */
sealed interface ExportRange {
    /** Every page of a page note; the content bounds plus [ExportOptions.margin] of an infinite one. */
    data object WholeNote : ExportRange

    /** Exactly [rect] in canvas units, such as the area visible on screen. */
    data class Area(val rect: Rect) : ExportRange

    /** Only the elements with these ids, framed by their bounds plus [ExportOptions.margin]. */
    data class Selection(val ids: Set<String>) : ExportRange

    /**
     * Pages [first] to [last] (0-based, inclusive, clamped to the note's pages) of a page note.
     * An infinite note exports as [WholeNote].
     */
    data class Pages(val first: Int, val last: Int) : ExportRange {
        init {
            require(first in 0..last) { "pages $first..$last" }
        }
    }
}

/** Whether exports fill the page with the paper colour or leave it transparent. */
enum class ExportBackground { PAPER, TRANSPARENT }

/**
 * How a note is exported.
 *
 * @property dark draws on the dark page with the canvas's dark-mode ink transform; otherwise
 *   stored colours on the light page.
 * @property backgroundPattern draws the note's background pattern (dots, grid or ruled lines) on
 *   the paper; a note without one draws none.
 * @property scale PNG pixels per canvas unit (dp).
 * @property margin canvas units around the content of an infinite note or a selection.
 * @property maxPixels PNG pixel budget; a larger image is exported at a smaller scale.
 */
data class ExportOptions(
    val format: ExportFormat = ExportFormat.PNG,
    val range: ExportRange = ExportRange.WholeNote,
    val background: ExportBackground = ExportBackground.PAPER,
    val dark: Boolean = false,
    val backgroundPattern: Boolean = false,
    val scale: Float = 2f,
    val margin: Float = 24f,
    val maxPixels: Long = 1L shl 25,
) {
    init {
        require(scale > 0f) { "scale $scale" }
        require(margin >= 0f) { "margin $margin" }
        require(maxPixels > 0) { "maxPixels $maxPixels" }
    }
}

/** An export's colours as `0xAARRGGBB`; the same roles as the canvas palette. */
data class ExportPalette(
    val paper: Int,
    /** Between stacked pages. */
    val desk: Int,
    /** A missing image's fill and edge. */
    val placeholder: Int,
    val placeholderEdge: Int,
    val dot: Int,
    /** Grid and ruled lines. */
    val line: Int = dot,
)
