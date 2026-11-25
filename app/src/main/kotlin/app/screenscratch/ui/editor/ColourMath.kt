package app.screenscratch.ui.editor

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Hue in degrees [0, 360), saturation and value in [0, 1]. */
data class Hsv(val h: Float, val s: Float, val v: Float)

/**
 * Colour maths for the colour panel. The wheel is an HSV disc at full value: hue runs clockwise
 * from the right (3 o'clock), as in the design's `conic-gradient(from 90deg, …)`, and saturation
 * grows linearly from the centre. The tone strip below it runs black → wheel colour → white:
 * tone 0–0.5 scales value from 0 to 1, tone 0.5–1 mixes towards white, which equals lowering
 * saturation at full value.
 */
object ColourMath {
    fun rgbToHsv(rgb: Int): Hsv {
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val d = hi - lo
        val h = when {
            d == 0f -> 0f
            hi == r -> 60f * (((g - b) / d).mod(6f))
            hi == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return Hsv(h.mod(360f), if (hi == 0f) 0f else d / hi, hi)
    }

    /** An opaque `0xFFRRGGBB` colour. */
    fun hsvToRgb(h: Float, s: Float, v: Float): Int {
        val hh = h.mod(360f) / 60f
        val c = v * s
        val x = c * (1f - kotlin.math.abs(hh.mod(2f) - 1f))
        val (r, g, b) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        fun ch(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** Hue and saturation at offset ([dx], [dy]) from the wheel's centre; outside the rim clamps to 1. */
    fun wheelHueSat(dx: Float, dy: Float, radius: Float): Pair<Float, Float> {
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return angle.mod(360f) to min(1f, hypot(dx, dy) / radius)
    }

    /** Offset from the wheel's centre of the marker for hue [h] and saturation [s]. */
    fun wheelOffset(h: Float, s: Float, radius: Float): Pair<Float, Float> {
        val a = Math.toRadians(h.toDouble())
        return (cos(a) * s * radius).toFloat() to (sin(a) * s * radius).toFloat()
    }

    /** The colour for wheel hue [h] and saturation [s] at [tone] on the strip (0.5 = the wheel colour). */
    fun withTone(h: Float, s: Float, tone: Float): Int {
        val t = tone.coerceIn(0f, 1f)
        return if (t <= 0.5f) hsvToRgb(h, s, t * 2f) else hsvToRgb(h, s * (1f - (t - 0.5f) * 2f), 1f)
    }

    /** The strip position that, with the colour's own hue and saturation on the wheel, gives back [hsv]. */
    fun toneOf(hsv: Hsv): Float = hsv.v / 2f

    /**
     * Parses `#RGB` or `#RRGGBB` (the `#` optional, any case, surrounding spaces ignored) to an
     * opaque `0xFFRRGGBB`; null for anything else.
     */
    fun parseHex(text: String): Int? {
        val digits = text.trim().removePrefix("#")
        if (digits.length != 3 && digits.length != 6) return null
        if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val full = if (digits.length == 3) digits.map { "$it$it" }.joinToString("") else digits
        return (0xFF000000L or full.toLong(16)).toInt()
    }

    /** `#RRGGBB`, upper case. */
    fun toHex(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)
}
