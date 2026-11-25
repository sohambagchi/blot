package app.blot.markdown

/** The look of one run of text, as SVG export needs it. Sizes are px. */
data class RunStyle(
    val textSize: Float,
    /** CSS-style weight: 400 regular, 700 bold. */
    val weight: Int,
    val italic: Boolean,
    val monospace: Boolean,
    val color: Int,
    val strikethrough: Boolean,
    val underline: Boolean,
) {
    val bold: Boolean get() = weight >= 600
}

/**
 * Text drawn with one style on one line. [x] is the left edge in px from the layout's left;
 * [start] is the character offset into [MarkdownLayout.text]. [link] is the destination when the
 * run is inside a link.
 */
data class TextRun(
    val text: String,
    val start: Int,
    val x: Float,
    val width: Float,
    val style: RunStyle,
    val link: String? = null,
)

/**
 * One laid-out line. [left] is where text starts after list, quote and code indents; [top],
 * [baseline] and [bottom] are px from the layout's top. Lines that hold only an image, a rule
 * or a block gap have no runs.
 */
data class MarkdownLine(
    val index: Int,
    val top: Float,
    val baseline: Float,
    val bottom: Float,
    val left: Float,
    val runs: List<TextRun>,
)
