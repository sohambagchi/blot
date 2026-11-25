package app.blot.canvas

import app.blot.ui.editor.Palettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class InkColorsTest {
    private fun channels(c: Int) = intArrayOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
    private fun lightness(c: Int) = InkColors.toOklab(c)[0]
    private fun chroma(c: Int) = InkColors.toOklab(c).let { hypot(it[1], it[2]) }
    private fun hue(c: Int) = InkColors.toOklab(c).let { atan2(it[2], it[1]) }
    private fun deltaE(x: Int, y: Int): Double {
        val p = InkColors.toOklab(x)
        val q = InkColors.toOklab(y)
        return sqrt((p[0] - q[0]).pow(2) + (p[1] - q[1]).pow(2) + (p[2] - q[2]).pow(2))
    }

    /** WCAG 2 contrast ratio of [c] against the dark page. */
    private fun contrastOnDarkPage(c: Int): Double {
        fun luminance(x: Int): Double {
            fun lin(v: Int) = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
            val (r, g, b) = channels(x).map(::lin)
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }
        return (luminance(c) + 0.05) / (luminance(CanvasPalette.DARK.paper) + 0.05)
    }

    @Test
    fun blackBecomesNearWhite() {
        val c = InkColors.darkMode(0xFF000000.toInt())
        assertTrue(channels(c).all { it >= 0xF0 })
        assertEquals(0xFF, c ushr 24)
    }

    @Test
    fun nearBlackBecomesNearWhite() {
        val c = InkColors.darkMode(0xFF111111.toInt())
        assertTrue(channels(c).joinToString(), channels(c).all { it >= 0xD8 })
    }

    @Test
    fun whiteBecomesNearBlack() {
        val c = InkColors.darkMode(0xFFFFFFFF.toInt())
        assertTrue(channels(c).all { it <= 0x10 })
    }

    @Test
    fun greysReverseTheirLightnessAlongTheCurve() {
        for (v in listOf(0x20, 0x60, 0x90, 0xD0)) {
            val grey = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            val l = lightness(grey)
            assertEquals(1 - l * l, lightness(InkColors.darkMode(grey)), 0.01)
        }
    }

    @Test
    fun chromaticInkIsLightenedIntoTheBandKeepingItsHue() {
        for (c in listOf(0xFFE53935.toInt(), 0xFF1E5BD8.toInt(), 0xFF2E7D32.toInt(), 0xFF0000FF.toInt(), 0xFF6A1B9A.toInt())) {
            val d = InkColors.darkMode(c)
            assertEquals("%08X → %08X".format(c, d), InkColors.darkChromaticLightness(lightness(c)), lightness(d), 0.01)
            assertTrue("%08X → %08X".format(c, d), lightness(d) > lightness(c))
            var dh = abs(hue(d) - hue(c))
            if (dh > PI) dh = 2 * PI - dh
            assertTrue("hue of %08X moved by %.3f".format(c, dh), dh < 0.12)
            assertTrue("%08X lost its colour".format(c), chroma(d) > 0.05)
        }
    }

    @Test
    fun lightChromaticInkMovesLittle() {
        val yellow = 0xFFFFEB3B.toInt()
        val d = InkColors.darkMode(yellow)
        assertTrue("%08X → %08X".format(yellow, d), deltaE(yellow, d) < 0.04)
    }

    @Test
    fun quickColoursDrawAsBefore() {
        // Tuned against the earlier lightness floor; these are its outputs, within a small ΔE.
        val before = mapOf(
            0xFF1C1B19.toInt() to 0xFFF0EEEB.toInt(),
            0xFFD93F3F.toInt() to 0xFFFF847C.toInt(),
            0xFF2F6BDE.toInt() to 0xFF80ADFF.toInt(),
            0xFF2E9A5B.toInt() to 0xFF60C785.toInt(),
        )
        for ((ink, old) in before) {
            val d = InkColors.darkMode(ink)
            assertTrue("%08X → %08X, was %08X".format(ink, d, old), deltaE(d, old) < 0.03)
        }
    }

    @Test
    fun darkerChromaticInkDrawsDarkerAtEveryHue() {
        for (deg in 0 until 360 step 15) {
            val h = Math.toRadians(deg.toDouble())
            val samples = (0..32).map { 0.15 + it * 0.025 }
                .map { l -> InkColors.fromOklabInGamut(l, 0.12 * cos(h), 0.12 * sin(h)) }
                .filter { chroma(it) >= InkColors.CHROMATIC_CHROMA }
            assertTrue("hue $deg has ${samples.size} chromatic samples", samples.size >= 4)
            for ((x, y) in samples.zipWithNext()) {
                val dx = InkColors.darkMode(x)
                val dy = InkColors.darkMode(y)
                assertTrue("hue $deg: %08X → %08X not darker than %08X → %08X".format(x, dx, y, dy), lightness(dy) - lightness(dx) > 0.005)
            }
        }
    }

    @Test
    fun everyPaletteChipDrawsDistinctly() {
        val chips = Palettes.flatMap { it.second }.map { it.toInt() }
        val drawn = chips.map(InkColors::darkMode)
        for (i in chips.indices) for (j in i + 1 until chips.size) {
            val d = deltaE(drawn[i], drawn[j])
            assertTrue("%08X and %08X both draw near %08X (ΔE %.4f)".format(chips[i], chips[j], drawn[i], d), d > 0.02)
        }
    }

    @Test
    fun darkestInksReadOnTheDarkPage() {
        // Every fully chromatic colour on a grid of the sRGB cube, the deepest blue there is, and near-black greys.
        val chromatic = buildList {
            for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
                val c = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                if (chroma(c) >= InkColors.CHROMATIC_CHROMA) add(c)
            }
            add(0xFF000023.toInt())
        }
        for (c in chromatic + listOf(0xFF000000.toInt(), 0xFF1C1B19.toInt(), 0xFF1F1F1F.toInt())) {
            val d = InkColors.darkMode(c)
            assertTrue("%08X → %08X: %.2f:1".format(c, d, contrastOnDarkPage(d)), contrastOnDarkPage(d) >= 3.0)
        }
        val darkestChip = Palettes.flatMap { it.second }.map { it.toInt() }
            .filter { chroma(it) >= InkColors.CHROMATIC_CHROMA }.minBy(::lightness)
        assertTrue(contrastOnDarkPage(InkColors.darkMode(darkestChip)) >= 4.5)
    }

    @Test
    fun alphaIsKept() {
        assertEquals(0x80, InkColors.darkMode(0x80E53935.toInt()) ushr 24)
        assertEquals(0x00, InkColors.darkMode(0x00000000) ushr 24)
    }

    @Test
    fun noSeamBetweenGreysAndColours() {
        // A dark ink tinted ever more blue: neighbouring inputs must give neighbouring outputs.
        var prev = InkColors.darkMode(0xFF202020.toInt())
        for (b in 0x21..0x80) {
            val c = InkColors.darkMode((0xFF shl 24) or (0x20 shl 16) or (0x20 shl 8) or b)
            val jump = channels(prev).zip(channels(c)).maxOf { (x, y) -> abs(x - y) }
            assertTrue("jump of $jump at blue $b", jump <= 12)
            prev = c
        }
    }

    @Test
    fun noSeamAsChromaGrows() {
        // Grey to fully chromatic in OKLab chroma steps of 0.002, at several lightnesses and hues. The
        // blend is steep where the two rules disagree most (up to ~0.5 in L), but a seam would jump
        // by that whole gap in one step.
        for (l in listOf(0.2, 0.4, 0.6, 0.8)) for (deg in listOf(30, 150, 265)) {
            val h = Math.toRadians(deg.toDouble())
            var prev = InkColors.darkMode(InkColors.fromOklabInGamut(l, 0.0, 0.0))
            for (k in 1..60) {
                val c = InkColors.darkMode(InkColors.fromOklabInGamut(l, k * 0.002 * cos(h), k * 0.002 * sin(h)))
                assertTrue("L $l hue $deg step $k: %08X → %08X".format(prev, c), deltaE(prev, c) < 0.06)
                prev = c
            }
        }
    }

    @Test
    fun outputsStayInGamut() {
        // Every channel combination on a coarse grid gives a valid opaque colour.
        for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) {
            val c = InkColors.darkMode((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            assertEquals(0xFF, c ushr 24)
        }
    }
}
