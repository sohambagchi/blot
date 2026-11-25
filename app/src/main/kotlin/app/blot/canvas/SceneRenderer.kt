package app.blot.canvas

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.blot.core.edit.Similarity
import app.blot.core.model.Arrowhead
import app.blot.core.model.Element
import app.blot.core.model.Image
import app.blot.core.model.Line
import app.blot.core.model.Note
import app.blot.core.model.PageSetup
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import app.blot.core.model.Stroke
import app.blot.core.model.TextBox
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Draws a [Note] with plain `android.graphics`: background, pages, pattern, then elements in stacking
 * order, skipping elements whose bounds miss the view. One instance per drawing thread: it keeps
 * `Path`s and paints that must not be shared; [outlines] may be shared.
 */
class SceneRenderer(
    private val outlines: OutlineCache,
    @Volatile var text: TextBoxRenderer = PlainTextBoxRenderer(),
    @Volatile var images: AssetImages = AssetImages.NONE,
    /** Called (on any thread) when an image that was missing has loaded. */
    private val onImageLoaded: () -> Unit = {},
) {
    private class CachedPath(val xy: FloatArray, val path: Path)

    private val paths = HashMap<String, CachedPath>()
    private val darkColors = HashMap<Int, Int>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scratchPath = Path()
    private val rectF = RectF()
    private val pattern = PatternPainter()

    /** Elements drawn by the latest [draw]; for tests and stats. */
    var lastDrawnCount = 0
        private set

    /** The colour [argb] is drawn in, after the dark-mode transform when [dark]. */
    fun color(argb: Int, dark: Boolean): Int =
        if (!dark) argb else darkColors.getOrPut(argb) { InkColors.darkMode(argb) }

    /**
     * Draws [note] as seen through [viewport] into a [width] × [height] pixel area at the canvas
     * origin. [preview] changes how some elements are shown while a tool gesture runs.
     */
    fun draw(canvas: Canvas, note: Note, viewport: Viewport, width: Int, height: Int, dark: Boolean, preview: ScenePreview? = null) {
        val palette = CanvasPalette.of(dark)
        val page = note.page
        fill.color = if (page is PageSetup.Pages) palette.desk else palette.paper
        // A rect, not drawColor: Compose doesn't clip interop views, and drawColor fills the whole window.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)

        val visible = viewport.visibleRect(width, height)
        canvas.save()
        canvas.translate(viewport.offsetX, viewport.offsetY)
        canvas.scale(viewport.scale, viewport.scale)
        if (page is PageSetup.Pages) drawPages(canvas, page, visible, palette)
        drawBackground(canvas, note, visible, viewport.scale, palette, viewport.offsetX, viewport.offsetY)
        var drawn = 0
        for (e in note.elements) {
            if (preview != null && e.id in preview.hidden) continue
            if (preview != null && e.id in preview.transformed) {
                val t = preview.transform
                if (!t.bounds(e.bounds).intersects(visible)) continue
                canvas.save()
                canvas.translate(t.originX + t.dx, t.originY + t.dy)
                if (t.rotation != 0f) canvas.rotate(Math.toDegrees(t.rotation.toDouble()).toFloat())
                canvas.scale(t.scale, t.scale)
                canvas.translate(-t.originX, -t.originY)
                drawElement(canvas, e, dark, palette)
                canvas.restore()
                drawn++
                continue
            }
            if (!e.bounds.intersects(visible)) continue
            drawElement(canvas, e, dark, palette)
            drawn++
        }
        canvas.restore()
        lastDrawnCount = drawn
    }

    private fun drawPages(canvas: Canvas, pages: PageSetup.Pages, visible: app.blot.core.model.Rect, palette: CanvasPalette) {
        val range = visiblePages(pages, visible.inflate(SHADOW_RADIUS * 2))
        if (range.isEmpty()) return
        shadow.color = palette.paper
        shadow.setShadowLayer(SHADOW_RADIUS, 0f, SHADOW_DY, palette.pageShadow)
        fill.color = palette.paper
        for (i in range) {
            val r = pages.pageRect(i)
            canvas.drawRect(r.left, r.top, r.right, r.bottom, shadow)
            canvas.drawRect(r.left, r.top, r.right, r.bottom, fill)
        }
    }

    /**
     * Draws [note]'s background pattern where it meets [visible], into [canvas] in canvas units at
     * [pxPerUnit] pixels per unit, with the canvas origin at pixel ([originX], [originY]) so lines
     * land on whole pixels. Never goes through the ink colour transform.
     */
    fun drawBackground(
        canvas: Canvas,
        note: Note,
        visible: app.blot.core.model.Rect,
        pxPerUnit: Float,
        palette: CanvasPalette,
        originX: Float = Float.NaN,
        originY: Float = Float.NaN,
    ) {
        val bg = note.background ?: return
        for (m in pattern.marks(bg, note.page, visible, pxPerUnit)) {
            pattern.draw(canvas, m, pxPerUnit, palette.patternDot, palette.patternLine, originX, originY)
        }
    }

    /** Draws one element in canvas units. */
    fun drawElement(canvas: Canvas, e: Element, dark: Boolean, palette: CanvasPalette = CanvasPalette.of(dark)) {
        when (e) {
            is Stroke -> drawStroke(canvas, e, dark)
            is Shape -> drawShape(canvas, e, dark)
            is Line -> drawLine(canvas, e, dark)
            is TextBox -> rotated(canvas, e.frame, e.rotation) { text.draw(canvas, e, color(e.color.value, dark)) }
            is Image -> rotated(canvas, e.frame, e.rotation) { drawImage(canvas, e, palette) }
        }
    }

    /** Runs [draw] with [canvas] turned by [rotation] radians about [frame]'s centre. */
    private inline fun rotated(canvas: Canvas, frame: app.blot.core.model.Rect, rotation: Float, draw: () -> Unit) {
        if (rotation == 0f) return draw()
        canvas.save()
        canvas.rotate(Math.toDegrees(rotation.toDouble()).toFloat(), frame.centerX, frame.centerY)
        draw()
        canvas.restore()
    }

    private fun drawStroke(canvas: Canvas, s: Stroke, dark: Boolean) {
        val xy = outlines.outline(s)
        val cached = paths[s.id]
        val path = if (cached != null && cached.xy === xy) cached.path else outlinePath(xy).also { paths[s.id] = CachedPath(xy, it) }
        fill.color = color(s.color.value, dark)
        canvas.drawPath(path, fill)
    }

    /** Drops cached paths for strokes not in [ids]. */
    fun retain(ids: Set<String>) {
        paths.keys.retainAll(ids)
    }

    private fun drawShape(canvas: Canvas, s: Shape, dark: Boolean) {
        val f = s.frame
        canvas.save()
        if (s.rotation != 0f) canvas.rotate(Math.toDegrees(s.rotation.toDouble()).toFloat(), f.centerX, f.centerY)
        rectF.set(f.left, f.top, f.right, f.bottom)
        s.fill?.let {
            fill.color = color(it.value, dark)
            if (s.kind == ShapeKind.RECTANGLE) canvas.drawRect(rectF, fill) else canvas.drawOval(rectF, fill)
        }
        if (s.width > 0f) {
            line.color = color(s.color.value, dark)
            line.strokeWidth = s.width
            line.strokeJoin = Paint.Join.MITER
            line.strokeCap = Paint.Cap.BUTT
            if (s.kind == ShapeKind.RECTANGLE) canvas.drawRect(rectF, line) else canvas.drawOval(rectF, line)
        }
        canvas.restore()
    }

    private fun drawLine(canvas: Canvas, l: Line, dark: Boolean) {
        val c = color(l.color.value, dark)
        line.color = c
        line.strokeWidth = l.width
        line.strokeJoin = Paint.Join.ROUND
        line.strokeCap = Paint.Cap.ROUND
        fill.color = c
        val pts = l.points
        val n = pts.size
        // A triangle's line stops at the triangle's base so its round cap stays behind the head.
        var sx = pts[0].x
        var sy = pts[0].y
        var ex = pts[n - 1].x
        var ey = pts[n - 1].y
        val len = l.arrowheadLength
        if (l.startArrow == Arrowhead.TRIANGLE) towards(sx, sy, pts[1].x, pts[1].y, len * cos(ARROW_HALF_ANGLE)).let { sx = it.first; sy = it.second }
        if (l.endArrow == Arrowhead.TRIANGLE) towards(ex, ey, pts[n - 2].x, pts[n - 2].y, len * cos(ARROW_HALF_ANGLE)).let { ex = it.first; ey = it.second }
        scratchPath.rewind()
        scratchPath.moveTo(sx, sy)
        for (i in 1 until n - 1) scratchPath.lineTo(pts[i].x, pts[i].y)
        scratchPath.lineTo(ex, ey)
        canvas.drawPath(scratchPath, line)
        drawArrowhead(canvas, l.startArrow, pts[0].x, pts[0].y, pts[1].x, pts[1].y, l)
        drawArrowhead(canvas, l.endArrow, pts[n - 1].x, pts[n - 1].y, pts[n - 2].x, pts[n - 2].y, l)
    }

    /** The point [d] from ([x], [y]) towards ([tx], [ty]), or ([x], [y]) if they coincide. */
    private fun towards(x: Float, y: Float, tx: Float, ty: Float, d: Float): Pair<Float, Float> {
        val len = hypot(tx - x, ty - y)
        if (len == 0f) return x to y
        val k = minOf(d, len) / len
        return (x + (tx - x) * k) to (y + (ty - y) * k)
    }

    // The head stays within Line.arrowheadLength of the tip: arms of length (L − width/2) with
    // round caps, or a triangle whose base corners are L from the tip.
    private fun drawArrowhead(canvas: Canvas, head: Arrowhead, tipX: Float, tipY: Float, fromX: Float, fromY: Float, l: Line) {
        if (head == Arrowhead.NONE || (tipX == fromX && tipY == fromY)) return
        val back = atan2(fromY - tipY, fromX - tipX)
        val len = l.arrowheadLength
        when (head) {
            Arrowhead.ARROW -> {
                val arm = (len - l.width / 2).coerceAtLeast(0f)
                scratchPath.rewind()
                scratchPath.moveTo(tipX + arm * cos(back + ARROW_HALF_ANGLE), tipY + arm * sin(back + ARROW_HALF_ANGLE))
                scratchPath.lineTo(tipX, tipY)
                scratchPath.lineTo(tipX + arm * cos(back - ARROW_HALF_ANGLE), tipY + arm * sin(back - ARROW_HALF_ANGLE))
                canvas.drawPath(scratchPath, line)
            }
            Arrowhead.TRIANGLE -> {
                scratchPath.rewind()
                scratchPath.moveTo(tipX, tipY)
                scratchPath.lineTo(tipX + len * cos(back + ARROW_HALF_ANGLE), tipY + len * sin(back + ARROW_HALF_ANGLE))
                scratchPath.lineTo(tipX + len * cos(back - ARROW_HALF_ANGLE), tipY + len * sin(back - ARROW_HALF_ANGLE))
                scratchPath.close()
                canvas.drawPath(scratchPath, fill)
            }
            Arrowhead.NONE -> Unit
        }
    }

    private fun drawImage(canvas: Canvas, img: Image, palette: CanvasPalette) {
        val f = img.frame
        rectF.set(f.left, f.top, f.right, f.bottom)
        val bmp = images.bitmap(img.asset, onImageLoaded)
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, rectF, bitmapPaint)
            return
        }
        fill.color = palette.placeholder
        canvas.drawRect(rectF, fill)
        line.color = palette.placeholderEdge
        line.strokeWidth = 1f
        line.strokeCap = Paint.Cap.BUTT
        canvas.drawRect(rectF, line)
        canvas.drawLine(f.left, f.top, f.right, f.bottom, line)
        canvas.drawLine(f.right, f.top, f.left, f.bottom, line)
    }

    companion object {
        /** Canvas units. */
        const val SHADOW_RADIUS = 6f
        const val SHADOW_DY = 1.5f
        private const val ARROW_HALF_ANGLE = (Math.PI / 7).toFloat()

        /**
         * A closed path through an outline polygon, using quadratic curves between edge midpoints
         * as perfect-freehand's `getSvgPathFromStroke` does, so the outline has no visible facets.
         */
        fun outlinePath(xy: FloatArray, out: Path = Path()): Path {
            out.rewind()
            val n = xy.size / 2
            if (n == 0) return out
            if (n < 3) {
                out.moveTo(xy[0], xy[1])
                for (i in 1 until n) out.lineTo(xy[i * 2], xy[i * 2 + 1])
                out.close()
                return out
            }
            out.moveTo((xy[0] + xy[2]) / 2, (xy[1] + xy[3]) / 2)
            for (i in 1..n) {
                val j = i % n
                val k = (i + 1) % n
                out.quadTo(xy[j * 2], xy[j * 2 + 1], (xy[j * 2] + xy[k * 2]) / 2, (xy[j * 2 + 1] + xy[k * 2 + 1]) / 2)
            }
            out.close()
            return out
        }
    }
}

/**
 * How [SceneRenderer.draw] shows a note while a tool gesture runs, without changing the note:
 * the elements in [transformed] are drawn through [transform] (a selection being moved, rotated or
 * scaled), and those in [hidden] are not drawn (a text box being edited).
 */
class ScenePreview(
    val transformed: Set<String> = emptySet(),
    val transform: Similarity = Similarity.IDENTITY,
    val hidden: Set<String> = emptySet(),
)
