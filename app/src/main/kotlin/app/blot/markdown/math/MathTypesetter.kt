package app.blot.markdown.math

import kotlin.math.max

/**
 * Typeset maths: [path] holds its outlines in px, y down, with the origin on the baseline at
 * the left edge. [ascent] and [descent] are the box above and below the baseline.
 */
internal class MathBox(val width: Float, val ascent: Float, val descent: Float, val path: MathPath)

/** LaTeX that couldn't be typeset; [message] says why. */
internal class MathException(message: String) : Exception(message)

/**
 * Typesets LaTeX maths with the iosMath typesetter ported from AndroidMath and the bundled
 * Latin Modern Math subset. Thread-safe.
 */
internal object MathTypesetter {
    /**
     * [latex] in text style at [fontSize] px, cut where a line may break (after a top-level
     * binary operator or relation); the boxes sit side by side, each starting where the last ends.
     */
    fun inline(latex: String, fontSize: Float): List<MathBox> {
        val (display, breaks) = typeset(latex, fontSize, MTLineStyle.KMTLineStyleText)
        val subs = display.subDisplays.orEmpty()
        if (subs.isEmpty()) return listOf(MathBox(display.width, display.ascent, display.descent, MathPath.EMPTY))
        val cuts = (listOf(0) + breaks.filter { it in 1 until subs.size } + subs.size).distinct()
        return (0 until cuts.size - 1).map { k ->
            val x0 = if (k == 0) 0f else subs[cuts[k]].position.x
            val x1 = if (k == cuts.size - 2) display.width else subs[cuts[k + 1]].position.x
            val out = MathPathBuilder()
            var ascent = 0f
            var descent = 0f
            for (i in cuts[k] until cuts[k + 1]) {
                val d = subs[i]
                d.draw(out, -x0, 0f)
                ascent = max(ascent, d.position.y + d.ascent)
                descent = max(descent, d.descent - d.position.y)
            }
            MathBox(max(0f, x1 - x0), ascent, descent, out.build())
        }
    }

    /** [latex] in display style at [fontSize] px, as one box. */
    fun display(latex: String, fontSize: Float): MathBox {
        val (display, _) = typeset(latex, fontSize, MTLineStyle.KMTLineStyleDisplay)
        val out = MathPathBuilder()
        display.draw(out, 0f, 0f)
        return MathBox(display.width, display.ascent, display.descent, out.build())
    }

    private fun typeset(latex: String, fontSize: Float, style: MTLineStyle): Pair<MTMathListDisplay, List<Int>> {
        // The parser and typesetter signal some bad input by throwing, not through the error.
        return try {
            val error = MTParseError()
            val list = MTMathListBuilder.buildFromString(latex, error)
                ?: throw MathException(error.errordesc.ifEmpty { "can't parse" })
            MTTypesetter.createLineWithBreaks(list, MTFont(MathFontFile.bundled, fontSize), style)
        } catch (e: MathException) {
            throw e
        } catch (e: MathDisplayException) {
            throw MathException(e.message)
        } catch (e: RuntimeException) {
            throw MathException(e.message ?: e.javaClass.simpleName)
        }
    }
}
