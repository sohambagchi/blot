package app.blot.core.export

/**
 * A text box laid out for SVG: its lines of styled runs and the shapes drawn under them, in
 * canvas units from the box's top-left. Colours are final (any dark-mode transform applied).
 * The app fills it from the markdown renderer's lines and decorations.
 */
class SvgText(val lines: List<TextLine>, val decorations: List<Decoration>) {
    /** Text in one style. [x] is its left edge; [width] its measured advance. */
    data class Run(
        val text: String,
        val x: Float,
        val width: Float,
        val fontSize: Float,
        val weight: Int = 400,
        val italic: Boolean = false,
        val monospace: Boolean = false,
        val color: Int,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        val link: String? = null,
    )

    data class TextLine(val baseline: Float, val runs: List<Run>)

    sealed interface Decoration {
        data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float, val color: Int, val radius: Float = 0f) : Decoration

        /** A stroked rectangle, stroke centred on the edges. */
        data class Outline(
            val left: Float, val top: Float, val right: Float, val bottom: Float,
            val color: Int, val strokeWidth: Float, val radius: Float = 0f,
        ) : Decoration

        /** Filled when [strokeWidth] is 0, otherwise stroked. */
        data class Circle(val cx: Float, val cy: Float, val radius: Float, val color: Int, val strokeWidth: Float = 0f) : Decoration

        /** Round-capped open polyline; [points] is x0, y0, x1, y1, … */
        data class Polyline(val points: List<Float>, val color: Int, val strokeWidth: Float) : Decoration

        /** Regular-face text with its left edge at [x]. */
        data class Label(val text: String, val x: Float, val baseline: Float, val fontSize: Float, val color: Int) : Decoration

        /** An image file, by its path in the note (`assets/<hash>.<ext>`). */
        data class Image(val path: String, val left: Float, val top: Float, val right: Float, val bottom: Float) : Decoration
    }
}
