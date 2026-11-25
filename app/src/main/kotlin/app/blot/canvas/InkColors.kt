package app.blot.canvas

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The dark-mode colour transform: notes store true colours, and dark mode maps them at draw
 * time only. Near-greys have their lightness reversed as `1 − L²` (OKLab L), which keeps
 * near-black ink near-white and near-white near-black while mid-greys move less than a plain
 * reversal would; chromatic colours keep their hue and have their lightness mapped, in order, into
 * the band [DARK_BAND_LOW]..[DARK_BAND_HIGH] ([darkChromaticLightness]), so they read on a dark
 * page and darker inks still draw darker. Greyish colours blend between the two rules by their
 * chroma, so the mapping has no seams. Chroma is reduced only to fit sRGB. Alpha is kept. Images
 * are never passed through it.
 *
 * Works in OKLab, whose lightness tracks perceived lightness across hues (saturated blue and
 * yellow have the same HSL lightness but very different contrast on a dark page). Conversion
 * matrices from Björn Ottosson, "A perceptual color space for image processing" (2020),
 * https://bottosson.github.io/posts/oklab/ (Copyright (c) 2020 Björn Ottosson; the post's
 * reference code is offered as public domain or under the MIT licence).
 */
object InkColors {
    // The band passes through (0.6, 0.75), so the quick red, blue and green (stored L 0.55–0.61)
    // draw about where the earlier floor of 0.75 put them. Its low end is chosen so that
    // the darkest fully chromatic sRGB colour (#000023, L ≈ 0.12) still has 3.2:1 WCAG contrast on
    // the dark page (CanvasPalette.DARK.paper); the darkest palette chip, #43207A, gets 4.6:1.

    /** OKLab lightness that the darkest chromatic ink (stored L = 0) draws at in dark mode. */
    const val DARK_BAND_LOW = 0.48

    /** OKLab lightness that the lightest chromatic ink (stored L = 1) draws at in dark mode. */
    const val DARK_BAND_HIGH = 0.93

    // OKLab chroma below which a colour counts as grey, and above which as fully chromatic.
    internal const val GREY_CHROMA = 0.03
    internal const val CHROMATIC_CHROMA = 0.08

    /** [argb] (`0xAARRGGBB`, sRGB) as dark mode draws it. */
    fun darkMode(argb: Int): Int {
        val alpha = argb ushr 24
        val lab = toOklab(argb)
        val l = lab[0]
        val a = lab[1]
        val b = lab[2]
        val chroma = sqrt(a * a + b * b)
        val w = smoothstep(GREY_CHROMA, CHROMATIC_CHROMA, chroma)
        val target = (1 - w) * (1 - l * l) + w * darkChromaticLightness(l)
        return (alpha shl 24) or (fromOklabInGamut(target, a, b) and 0xFFFFFF)
    }

    /**
     * The OKLab lightness a fully chromatic ink of stored lightness [l] draws at in dark mode: a
     * straight, increasing map of 0..1 onto the band, so lightness order and (at 45 % scale)
     * lightness steps are kept.
     */
    fun darkChromaticLightness(l: Double): Double = DARK_BAND_LOW + (DARK_BAND_HIGH - DARK_BAND_LOW) * l

    /** OKLab (L, a, b) of the colour's RGB channels; alpha is ignored. */
    fun toOklab(argb: Int): DoubleArray {
        val r = toLinear(((argb shr 16) and 0xFF) / 255.0)
        val g = toLinear(((argb shr 8) and 0xFF) / 255.0)
        val b = toLinear((argb and 0xFF) / 255.0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    /**
     * The opaque sRGB colour for OKLab ([l], [a], [b]), with chroma reduced (hue and lightness
     * kept) until it fits in sRGB.
     */
    internal fun fromOklabInGamut(l: Double, a: Double, b: Double): Int {
        val rgb = DoubleArray(3)
        oklabToLinear(l, a, b, rgb)
        if (inGamut(rgb)) return pack(rgb)
        var lo = 0.0
        var hi = 1.0
        repeat(20) {
            val mid = (lo + hi) / 2
            oklabToLinear(l, a * mid, b * mid, rgb)
            if (inGamut(rgb)) lo = mid else hi = mid
        }
        oklabToLinear(l, a * lo, b * lo, rgb)
        return pack(rgb)
    }

    private fun oklabToLinear(lightness: Double, a: Double, b: Double, out: DoubleArray) {
        val l = cube(lightness + 0.3963377774 * a + 0.2158037573 * b)
        val m = cube(lightness - 0.1055613458 * a - 0.0638541728 * b)
        val s = cube(lightness - 0.0894841775 * a - 1.2914855480 * b)
        out[0] = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        out[1] = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        out[2] = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    }

    private fun cube(x: Double) = x * x * x

    private fun inGamut(rgb: DoubleArray): Boolean = rgb.all { it >= -1e-4 && it <= 1 + 1e-4 }

    private fun pack(rgb: DoubleArray): Int {
        fun ch(v: Double) = (toSrgb(v.coerceIn(0.0, 1.0)) * 255).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(rgb[0]) shl 16) or (ch(rgb[1]) shl 8) or ch(rgb[2])
    }

    private fun toLinear(c: Double): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun toSrgb(c: Double): Double = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055

    private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }
}

/** The canvas's own colours for one theme, as `0xAARRGGBB`. */
class CanvasPalette private constructor(
    /** The page, and the whole background of an infinite canvas. */
    val paper: Int,
    /** Around pages. */
    val desk: Int,
    val pageShadow: Int,
    val placeholder: Int,
    val placeholderEdge: Int,
    /** A background pattern's dots: the design's `dot` token, lifted in dark mode to read on the dark page. */
    val patternDot: Int,
    /** A background pattern's grid and ruled lines: the design's `border` token, a step lighter than the page in dark mode. */
    val patternLine: Int,
) {
    companion object {
        val LIGHT = CanvasPalette(
            0xFFFFFFFF.toInt(), 0xFFE4E4E7.toInt(), 0x40000000, 0xFFEDEDED.toInt(), 0xFFBDBDBD.toInt(),
            0xFFD6D3CB.toInt(), 0xFFE3E0D8.toInt(),
        )
        val DARK = CanvasPalette(
            0xFF1E1E20.toInt(), 0xFF0F0F10.toInt(), 0x80000000.toInt(), 0xFF2C2C2E.toInt(), 0xFF5A5A5E.toInt(),
            0xFF3A3A40.toInt(), 0xFF323237.toInt(),
        )

        fun of(dark: Boolean) = if (dark) DARK else LIGHT
    }
}
