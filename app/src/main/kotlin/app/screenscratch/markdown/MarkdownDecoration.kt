package app.screenscratch.markdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint

/**
 * A non-text shape of the rendered markdown: block backgrounds, quote bars, list markers, rules
 * and images. Coordinates are px relative to the layout's top-left. [MarkdownLayout.draw] paints
 * these before the text, and an SVG writer can serialise the same list.
 */
sealed interface MarkdownDecoration {
    /** Paints this decoration; [paint] is scratch state that will be overwritten. */
    fun draw(canvas: Canvas, paint: TextPaint)

    /** A filled rectangle with corner radius [radius]: code backgrounds, quote bars, task boxes. */
    data class Box(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val color: Int, val radius: Float = 0f,
    ) : MarkdownDecoration {
        override fun draw(canvas: Canvas, paint: TextPaint) {
            paint.reset(color, Paint.Style.FILL, 0f)
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        }
    }

    /** A stroked rectangle whose stroke is centred on the given edges. */
    data class Outline(
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        val color: Int, val strokeWidth: Float, val radius: Float = 0f,
    ) : MarkdownDecoration {
        override fun draw(canvas: Canvas, paint: TextPaint) {
            paint.reset(color, Paint.Style.STROKE, strokeWidth)
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        }
    }

    /** A circle, filled when [strokeWidth] is 0, otherwise stroked. */
    data class Circle(
        val cx: Float, val cy: Float, val radius: Float, val color: Int, val strokeWidth: Float = 0f,
    ) : MarkdownDecoration {
        override fun draw(canvas: Canvas, paint: TextPaint) {
            paint.reset(color, if (strokeWidth == 0f) Paint.Style.FILL else Paint.Style.STROKE, strokeWidth)
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    /** An open polyline with round caps and joins; [points] is x0, y0, x1, y1, ... */
    class Polyline(val points: List<Float>, val color: Int, val strokeWidth: Float) : MarkdownDecoration {
        override fun draw(canvas: Canvas, paint: TextPaint) {
            paint.reset(color, Paint.Style.STROKE, strokeWidth)
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            val path = Path()
            path.moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
            canvas.drawPath(path, paint)
        }
    }

    /** Text in the regular face, left edge at [x]: ordered-list numbers and image alt text. */
    data class Label(
        val text: String, val x: Float, val baseline: Float, val textSize: Float, val color: Int,
    ) : MarkdownDecoration {
        override fun draw(canvas: Canvas, paint: TextPaint) {
            paint.reset(color, Paint.Style.FILL, 0f)
            paint.textSize = textSize
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(text, x, baseline, paint)
        }
    }

    /** A resolved image, scaled into the given rectangle. [path] is the source destination. */
    class Image(
        val path: String, val bitmap: Bitmap,
        val left: Float, val top: Float, val right: Float, val bottom: Float,
    ) : MarkdownDecoration {
        override fun draw(canvas: Canvas, paint: TextPaint) {
            paint.reset(0xFF000000.toInt(), Paint.Style.FILL, 0f)
            paint.isFilterBitmap = true
            canvas.drawBitmap(bitmap, null, RectF(left, top, right, bottom), paint)
        }
    }
}

private fun TextPaint.reset(color: Int, style: Paint.Style, strokeWidth: Float) {
    reset()
    isAntiAlias = true
    hinting = Paint.HINTING_OFF
    isSubpixelText = true
    this.color = color
    this.style = style
    this.strokeWidth = strokeWidth
}
