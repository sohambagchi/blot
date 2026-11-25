package app.blot.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

class InkColorsTest {
    private fun channels(c: Int) = intArrayOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
    private fun lightness(c: Int) = InkColors.toOklab(c)[0]
    private fun chroma(c: Int) = InkColors.toOklab(c).let { hypot(it[1], it[2]) }
    private fun hue(c: Int) = InkColors.toOklab(c).let { atan2(it[2], it[1]) }

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
    fun chromaticInkIsLightenedKeepingItsHue() {
        for (c in listOf(0xFFE53935.toInt(), 0xFF1E5BD8.toInt(), 0xFF2E7D32.toInt(), 0xFF0000FF.toInt(), 0xFF6A1B9A.toInt())) {
            val d = InkColors.darkMode(c)
            assertTrue("%08X → %08X".format(c, d), lightness(d) >= InkColors.DARK_MIN_LIGHTNESS - 0.01)
            assertTrue("%08X → %08X".format(c, d), lightness(d) > lightness(c))
            var dh = abs(hue(d) - hue(c))
            if (dh > PI) dh = 2 * PI - dh
            assertTrue("hue of %08X moved by %.3f".format(c, dh), dh < 0.12)
            assertTrue("%08X lost its colour".format(c), chroma(d) > 0.05)
        }
    }

    @Test
    fun lightChromaticInkIsKept() {
        val yellow = 0xFFFFEB3B.toInt()
        val d = InkColors.darkMode(yellow)
        channels(yellow).zip(channels(d)).forEach { (a, b) -> assertTrue(abs(a - b) <= 2) }
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
    fun outputsStayInGamut() {
        // Every channel combination on a coarse grid gives a valid opaque colour.
        for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) {
            val c = InkColors.darkMode((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            assertEquals(0xFF, c ushr 24)
        }
    }
}
