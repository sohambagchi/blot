package app.blot.core.export

import app.blot.core.model.Element
import app.blot.core.model.Image
import app.blot.core.model.Line
import app.blot.core.model.Note
import app.blot.core.model.Point
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import app.blot.core.model.Stroke
import app.blot.core.model.TextBox

/**
 * Serialises a note as one SVG document whose user units are canvas units (dp), so the
 * `width`/`height` in CSS px match the note's size on an Android screen at 100%. Strokes are
 * filled outline paths, shapes and lines are SVG primitives, images are `data:` URIs, and text
 * boxes are one `<text>` per laid-out line with a `<tspan>` per styled run; no `foreignObject`.
 * Pages are stacked as in the note, each clipped to itself.
 *
 * @param outline a stroke's outline polygon, x0, y0, x1, y1, … in canvas units.
 * @param color the colour a stored colour is drawn in (the dark-mode transform for a dark export).
 * @param textLayout a text box's layout, or null to write its markdown source as plain lines.
 * @param imageHref the `href` for an image path (`assets/<hash>.<ext>`), or null to draw a placeholder.
 */
class SvgNoteWriter(
    private val palette: ExportPalette,
    private val outline: (Stroke) -> FloatArray,
    private val color: (Int) -> Int = { it },
    private val textLayout: (TextBox) -> SvgText? = { null },
    private val imageHref: (String) -> String? = { null },
) {
    private var nextId = 0

    fun write(note: Note, options: ExportOptions, out: Appendable) = write(ExportPlan.of(note, options), options, out)

    fun write(plan: ExportPlan, options: ExportOptions, out: Appendable) {
        nextId = 0
        val x = SvgXml(out)
        val b = plan.bounds
        x.declaration()
        x.open(
            "svg",
            "xmlns" to SVG_NS, "xmlns:xlink" to XLINK_NS, "version" to "1.1",
            "width" to b.width, "height" to b.height,
            "viewBox" to "${SvgXml.num(b.x)} ${SvgXml.num(b.y)} ${SvgXml.num(b.width)} ${SvgXml.num(b.height)}",
        )
        val paper = options.background == ExportBackground.PAPER
        val stacked = plan.sheets.size > 1 || plan.sheets.single().rect != b
        if (paper && stacked) rect(x, b, "fill" to SvgXml.rgb(palette.desk), "fill-opacity" to SvgXml.opacity(palette.desk))
        for (sheet in plan.sheets) {
            val r = sheet.rect
            if (paper) rect(x, r, "fill" to SvgXml.rgb(palette.paper), "fill-opacity" to SvgXml.opacity(palette.paper))
            if (options.dotGrid) dots(x, sheet)
            val clip = id("page")
            x.open("clipPath", "id" to clip)
            rect(x, r)
            x.close()
            x.open("g", "clip-path" to "url(#$clip)")
            for (e in plan.elementsOn(sheet)) element(x, e)
            x.close()
        }
        x.finish()
    }

    private fun id(prefix: String) = "$prefix${nextId++}"

    private fun rect(x: SvgXml, r: Rect, vararg attrs: Pair<String, Any?>) =
        x.leaf("rect", "x" to r.x, "y" to r.y, "width" to r.width, "height" to r.height, *attrs)

    private fun dots(x: SvgXml, sheet: Sheet) {
        val id = id("dots")
        val s = DotGrid.SPACING
        x.open("defs")
        x.open(
            "pattern", "id" to id, "patternUnits" to "userSpaceOnUse",
            "x" to sheet.dotOrigin.x, "y" to sheet.dotOrigin.y, "width" to s, "height" to s,
        )
        x.leaf("circle", "cx" to s / 2, "cy" to s / 2, "r" to DotGrid.RADIUS, "fill" to SvgXml.rgb(palette.dot), "fill-opacity" to SvgXml.opacity(palette.dot))
        x.close()
        x.close()
        rect(x, sheet.rect, "fill" to "url(#$id)")
    }

    private fun element(x: SvgXml, e: Element) {
        when (e) {
            is Stroke -> stroke(x, e)
            is Shape -> shape(x, e)
            is Line -> line(x, e)
            is TextBox -> rotated(x, e.frame, e.rotation) { textBox(x, e) }
            is Image -> rotated(x, e.frame, e.rotation) { image(x, e) }
        }
    }

    /** Draws [body] inside a group turned by [rotation] about [frame]'s centre, or bare when it is 0. */
    private inline fun rotated(x: SvgXml, frame: Rect, rotation: Float, body: () -> Unit) {
        if (rotation == 0f) return body()
        x.open("g", "transform" to rotate(rotation, frame))
        body()
        x.close()
    }

    private fun rotate(rotation: Float, frame: Rect) =
        "rotate(${SvgXml.num(Math.toDegrees(rotation.toDouble()).toFloat())} ${SvgXml.num(frame.centerX)} ${SvgXml.num(frame.centerY)})"

    private fun fill(argb: Int): Array<Pair<String, Any?>> {
        val c = color(argb)
        return arrayOf("fill" to SvgXml.rgb(c), "fill-opacity" to SvgXml.opacity(c))
    }

    private fun stroke(argb: Int, width: Float): Array<Pair<String, Any?>> {
        val c = color(argb)
        return arrayOf("stroke" to SvgXml.rgb(c), "stroke-opacity" to SvgXml.opacity(c), "stroke-width" to width)
    }

    private fun stroke(x: SvgXml, s: Stroke) {
        val xy = outline(s)
        if (xy.size < 2) return
        x.leaf("path", "d" to outlinePathData(xy), *fill(s.color.value))
    }

    private fun shape(x: SvgXml, s: Shape) {
        val f = s.frame
        val transform = if (s.rotation == 0f) null else rotate(s.rotation, f)
        val paint = arrayOf(
            *(s.fill?.let { fill(it.value) } ?: arrayOf("fill" to "none")),
            *(if (s.width > 0f) stroke(s.color.value, s.width) else emptyArray()),
            "transform" to transform,
        )
        if (s.fill == null && s.width == 0f) return
        when (s.kind) {
            ShapeKind.RECTANGLE -> rect(x, f, *paint)
            ShapeKind.ELLIPSE -> x.leaf("ellipse", "cx" to f.centerX, "cy" to f.centerY, "rx" to f.width / 2, "ry" to f.height / 2, *paint)
        }
    }

    private fun line(x: SvgXml, l: Line) {
        val stroked = arrayOf(
            "fill" to "none", *stroke(l.color.value, l.width),
            "stroke-linecap" to "round", "stroke-linejoin" to "round",
        )
        x.leaf("path", "d" to polyline(LineGeometry.shaft(l), close = false), *stroked)
        for (atStart in listOf(true, false)) {
            val head = LineGeometry.head(l, atStart) ?: continue
            if (head.filled) {
                x.leaf("path", "d" to polyline(head.points, close = true), *fill(l.color.value))
            } else {
                x.leaf("path", "d" to polyline(head.points, close = false), *stroked)
            }
        }
    }

    private fun image(x: SvgXml, img: Image) {
        val f = img.frame
        val href = imageHref(img.asset.path)
        if (href != null) {
            x.leaf(
                "image", "x" to f.x, "y" to f.y, "width" to f.width, "height" to f.height,
                "preserveAspectRatio" to "none", "xlink:href" to href,
            )
            return
        }
        x.open("g")
        rect(x, f, "fill" to SvgXml.rgb(palette.placeholder), "stroke" to SvgXml.rgb(palette.placeholderEdge), "stroke-width" to 1f)
        x.leaf(
            "path",
            "d" to polyline(listOf(Point(f.left, f.top), Point(f.right, f.bottom)), false) + " " +
                polyline(listOf(Point(f.right, f.top), Point(f.left, f.bottom)), false),
            "fill" to "none", "stroke" to SvgXml.rgb(palette.placeholderEdge), "stroke-width" to 1f,
        )
        x.close()
    }

    private fun textBox(x: SvgXml, box: TextBox) {
        val f = box.frame
        val layout = textLayout(box) ?: plainText(box)
        val clip = id("text")
        x.open("clipPath", "id" to clip)
        rect(x, Rect(0f, 0f, f.width, f.height))
        x.close()
        x.open(
            "g", "transform" to "translate(${SvgXml.num(f.x)} ${SvgXml.num(f.y)})",
            "clip-path" to "url(#$clip)", "font-family" to SANS,
        )
        for (d in layout.decorations) decoration(x, d)
        for (line in layout.lines) {
            if (line.runs.isEmpty()) continue
            x.openInline("text", "xml:space" to "preserve", "y" to line.baseline)
            for (run in line.runs) {
                val link = run.link
                if (link != null) x.inlineOpen("a", "xlink:href" to link)
                val decorations = listOfNotNull("underline".takeIf { run.underline }, "line-through".takeIf { run.strikethrough })
                x.inlineLeaf(
                    "tspan", run.text,
                    "x" to run.x, "y" to line.baseline,
                    "font-size" to run.fontSize,
                    "font-weight" to run.weight.takeIf { it != 400 },
                    "font-style" to "italic".takeIf { run.italic },
                    "font-family" to MONO.takeIf { run.monospace },
                    "text-decoration" to decorations.joinToString(" ").ifEmpty { null },
                    "fill" to SvgXml.rgb(run.color), "fill-opacity" to SvgXml.opacity(run.color),
                    // Pins each run to the width the app measured, whatever font the viewer substitutes.
                    "textLength" to run.width.takeIf { it > 0f && run.text.isNotBlank() },
                    "lengthAdjust" to "spacing".takeIf { run.width > 0f && run.text.isNotBlank() },
                )
                if (link != null) x.inlineClose("a")
            }
            x.closeInline()
        }
        x.close()
    }

    private fun plainText(box: TextBox): SvgText {
        val c = color(box.color.value)
        val lines = box.markdown.split('\n').mapIndexed { i, s ->
            SvgText.TextLine(box.fontSize * (1 + i * PLAIN_LINE_HEIGHT), listOf(SvgText.Run(s, 0f, 0f, box.fontSize, color = c)))
        }
        return SvgText(lines, emptyList())
    }

    private fun decoration(x: SvgXml, d: SvgText.Decoration) {
        fun paint(c: Int) = arrayOf("fill" to SvgXml.rgb(c), "fill-opacity" to SvgXml.opacity(c))
        fun line(c: Int, w: Float) = arrayOf("fill" to "none", "stroke" to SvgXml.rgb(c), "stroke-opacity" to SvgXml.opacity(c), "stroke-width" to w)
        when (d) {
            is SvgText.Decoration.Box -> roundRect(x, d.left, d.top, d.right, d.bottom, d.radius, *paint(d.color))
            is SvgText.Decoration.Outline -> roundRect(x, d.left, d.top, d.right, d.bottom, d.radius, *line(d.color, d.strokeWidth))
            is SvgText.Decoration.Circle -> x.leaf(
                "circle", "cx" to d.cx, "cy" to d.cy, "r" to d.radius,
                *(if (d.strokeWidth == 0f) paint(d.color) else line(d.color, d.strokeWidth)),
            )
            is SvgText.Decoration.Polyline -> x.leaf(
                "polyline", "points" to d.points.joinToString(" ") { SvgXml.num(it) },
                *line(d.color, d.strokeWidth), "stroke-linecap" to "round", "stroke-linejoin" to "round",
            )
            is SvgText.Decoration.Label -> x.textLeaf(
                "text", d.text, "xml:space" to "preserve", "x" to d.x, "y" to d.baseline, "font-size" to d.fontSize, *paint(d.color),
            )
            is SvgText.Decoration.Image -> imageHref(d.path)?.let {
                x.leaf(
                    "image", "x" to d.left, "y" to d.top, "width" to d.right - d.left, "height" to d.bottom - d.top,
                    "preserveAspectRatio" to "none", "xlink:href" to it,
                )
            }
        }
    }

    private fun roundRect(x: SvgXml, l: Float, t: Float, r: Float, b: Float, radius: Float, vararg attrs: Pair<String, Any?>) {
        val rr = radius.takeIf { it > 0f }
        x.leaf("rect", "x" to l, "y" to t, "width" to r - l, "height" to b - t, "rx" to rr, "ry" to rr, *attrs)
    }

    companion object {
        const val SVG_NS = "http://www.w3.org/2000/svg"
        const val XLINK_NS = "http://www.w3.org/1999/xlink"

        /** The renderer draws with Android's default faces; these name them and close fallbacks. */
        const val SANS = "Roboto, 'Noto Sans', 'Helvetica Neue', Arial, sans-serif"
        const val MONO = "'Roboto Mono', 'Droid Sans Mono', 'Noto Sans Mono', 'DejaVu Sans Mono', monospace"

        private const val PLAIN_LINE_HEIGHT = 1.25f

        /**
         * Path data for a closed outline polygon: quadratic curves between edge midpoints, as
         * perfect-freehand's `getSvgPathFromStroke` (MIT, Copyright (c) 2021 Stephen Ruiz Ltd,
         * https://github.com/steveruizok/perfect-freehand) and the canvas's `outlinePath` do.
         */
        fun outlinePathData(xy: FloatArray): String {
            val n = xy.size / 2
            val sb = StringBuilder(n * 16)
            fun pt(px: Float, py: Float) {
                SvgXml.num(px, sb)
                sb.append(' ')
                SvgXml.num(py, sb)
            }
            if (n == 0) return ""
            if (n < 3) {
                sb.append('M')
                pt(xy[0], xy[1])
                for (i in 1 until n) {
                    sb.append('L')
                    pt(xy[i * 2], xy[i * 2 + 1])
                }
                return sb.append('Z').toString()
            }
            sb.append('M')
            pt((xy[0] + xy[2]) / 2, (xy[1] + xy[3]) / 2)
            for (i in 1..n) {
                val j = i % n
                val k = (i + 1) % n
                sb.append('Q')
                pt(xy[j * 2], xy[j * 2 + 1])
                sb.append(' ')
                pt((xy[j * 2] + xy[k * 2]) / 2, (xy[j * 2 + 1] + xy[k * 2 + 1]) / 2)
            }
            return sb.append('Z').toString()
        }

        /** Path data through [points]; closed with `Z` when [close]. */
        fun polyline(points: List<Point>, close: Boolean): String {
            val sb = StringBuilder()
            for ((i, p) in points.withIndex()) {
                sb.append(if (i == 0) 'M' else 'L')
                SvgXml.num(p.x, sb)
                sb.append(' ')
                SvgXml.num(p.y, sb)
            }
            if (close) sb.append('Z')
            return sb.toString()
        }
    }
}
