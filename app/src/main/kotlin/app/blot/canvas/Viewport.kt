package app.blot.canvas

import app.blot.core.model.PageSetup
import app.blot.core.model.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Maps canvas units to view pixels: `screen = canvas × scale + offset`, with `scale = zoom × density`.
 *
 * @property zoom 1 is 100%: one canvas unit is one dp. Kept within [MIN_ZOOM]–[MAX_ZOOM].
 * @property offsetX view pixels.
 * @property offsetY view pixels.
 * @property density view pixels per dp.
 */
data class Viewport(
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val density: Float = 1f,
) {
    init {
        require(density > 0f) { "density $density" }
        require(zoom in MIN_ZOOM..MAX_ZOOM) { "zoom $zoom" }
    }

    /** View pixels per canvas unit. */
    val scale: Float get() = zoom * density

    fun toScreenX(x: Float): Float = x * scale + offsetX
    fun toScreenY(y: Float): Float = y * scale + offsetY
    fun toCanvasX(sx: Float): Float = (sx - offsetX) / scale
    fun toCanvasY(sy: Float): Float = (sy - offsetY) / scale

    /** The canvas rectangle a [width] × [height] pixel view shows. */
    fun visibleRect(width: Int, height: Int): Rect =
        Rect(toCanvasX(0f), toCanvasY(0f), width / scale, height / scale)

    /** Moves the content by ([dx], [dy]) view pixels. */
    fun panBy(dx: Float, dy: Float): Viewport = copy(offsetX = offsetX + dx, offsetY = offsetY + dy)

    /**
     * Multiplies the zoom by [factor], clamped to the limits, keeping the canvas point under
     * ([focusX], [focusY]) (view pixels) where it is.
     */
    fun zoomBy(factor: Float, focusX: Float, focusY: Float): Viewport {
        val z = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val f = z / zoom
        return copy(zoom = z, offsetX = focusX - (focusX - offsetX) * f, offsetY = focusY - (focusY - offsetY) * f)
    }

    /** [zoomBy] to an absolute [zoom]. */
    fun zoomTo(zoom: Float, focusX: Float, focusY: Float): Viewport = zoomBy(zoom / this.zoom, focusX, focusY)

    /**
     * One step of a two-finger gesture: the content moves by ([dx], [dy]) and then scales by
     * [factor] about the new focus ([focusX], [focusY]), so the canvas point that was under the
     * old focus ends up under the new one.
     */
    fun transform(dx: Float, dy: Float, factor: Float, focusX: Float, focusY: Float): Viewport =
        panBy(dx, dy).zoomBy(factor, focusX, focusY)

    companion object {
        const val MIN_ZOOM = 0.2f
        const val MAX_ZOOM = 8f

        /**
         * The viewport that shows all of [rect] centred in a [width] × [height] pixel view, with
         * [margin] pixels clear on every side, at no more than [maxZoom].
         */
        fun fit(rect: Rect, width: Int, height: Int, density: Float, margin: Float = 0f, maxZoom: Float = MAX_ZOOM): Viewport {
            val w = max(1f, width - 2 * margin)
            val h = max(1f, height - 2 * margin)
            val rw = max(rect.width, 1e-3f)
            val rh = max(rect.height, 1e-3f)
            val zoom = (min(w / rw, h / rh) / density).coerceIn(MIN_ZOOM, min(maxZoom, MAX_ZOOM))
            val s = zoom * density
            return Viewport(zoom, width / 2f - rect.centerX * s, height / 2f - rect.centerY * s, density)
        }

        /**
         * The viewport that shows [rect]'s full width with [margin] pixels each side, its top edge
         * [margin] pixels below the view's top, at no more than [maxZoom].
         */
        fun fitWidth(rect: Rect, width: Int, density: Float, margin: Float = 0f, maxZoom: Float = MAX_ZOOM): Viewport {
            val w = max(1f, width - 2 * margin)
            val zoom = (w / max(rect.width, 1e-3f) / density).coerceIn(MIN_ZOOM, min(maxZoom, MAX_ZOOM))
            val s = zoom * density
            return Viewport(zoom, width / 2f - rect.centerX * s, margin - rect.top * s, density)
        }
    }
}

/** Indices of the pages of [pages] that intersect [visible], in order; empty if none do. */
fun visiblePages(pages: PageSetup.Pages, visible: Rect): IntRange {
    if (visible.right < 0f || visible.left > pages.width) return IntRange.EMPTY
    val pitch = pages.height + PageSetup.Pages.PAGE_GAP
    // Page i spans [i × pitch, i × pitch + height].
    val first = max(0, ceil((visible.top - pages.height) / pitch).toInt())
    val last = min(pages.count - 1, floor(visible.bottom / pitch).toInt())
    return if (first > last) IntRange.EMPTY else first..last
}

/** The rectangle around all pages of [pages], in canvas units. */
fun pagesBounds(pages: PageSetup.Pages): Rect =
    Rect(0f, 0f, pages.width, pages.count * pages.height + (pages.count - 1) * PageSetup.Pages.PAGE_GAP)
