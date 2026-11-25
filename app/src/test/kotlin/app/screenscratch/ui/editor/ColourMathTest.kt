package app.screenscratch.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColourMathTest {
    private fun assertHueSat(h: Float, s: Float, actual: Pair<Float, Float>) {
        assertEquals(h, actual.first, 0.01f)
        assertEquals(s, actual.second, 0.0001f)
    }

    @Test
    fun hueRunsClockwiseFromThreeOClock() {
        assertHueSat(0f, 1f, ColourMath.wheelHueSat(64f, 0f, 64f))
        assertHueSat(90f, 1f, ColourMath.wheelHueSat(0f, 64f, 64f))
        assertHueSat(180f, 0.5f, ColourMath.wheelHueSat(-32f, 0f, 64f))
        assertHueSat(270f, 0.25f, ColourMath.wheelHueSat(0f, -16f, 64f))
        assertHueSat(45f, 1f, ColourMath.wheelHueSat(10f, 10f, 10f * Math.sqrt(2.0).toFloat()))
    }

    @Test
    fun saturationGrowsLinearlyAndClampsAtTheRim() {
        assertHueSat(0f, 0f, ColourMath.wheelHueSat(0f, 0f, 64f))
        assertHueSat(0f, 0.75f, ColourMath.wheelHueSat(48f, 0f, 64f))
        assertHueSat(0f, 1f, ColourMath.wheelHueSat(200f, 0f, 64f))
    }

    @Test
    fun markerOffsetInvertsHueSat() {
        for (h in listOf(0f, 37f, 120f, 200f, 333f)) {
            for (s in listOf(0.2f, 0.6f, 1f)) {
                val (dx, dy) = ColourMath.wheelOffset(h, s, 64f)
                assertHueSat(h, s, ColourMath.wheelHueSat(dx, dy, 64f))
            }
        }
    }

    @Test
    fun hsvToRgbMatchesTheWheelStops() {
        assertEquals(0xFFFF0000.toInt(), ColourMath.hsvToRgb(0f, 1f, 1f))
        assertEquals(0xFFFFFF00.toInt(), ColourMath.hsvToRgb(60f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), ColourMath.hsvToRgb(120f, 1f, 1f))
        assertEquals(0xFF00FFFF.toInt(), ColourMath.hsvToRgb(180f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), ColourMath.hsvToRgb(240f, 1f, 1f))
        assertEquals(0xFFFF00FF.toInt(), ColourMath.hsvToRgb(300f, 1f, 1f))
        assertEquals(0xFFFFFFFF.toInt(), ColourMath.hsvToRgb(77f, 0f, 1f))
        assertEquals(0xFF000000.toInt(), ColourMath.hsvToRgb(77f, 1f, 0f))
    }

    @Test
    fun rgbHsvRoundTrips() {
        for (rgb in listOf(0xFF7A5CFF, 0xFF1C1B19, 0xFFD93F3F, 0xFF2F6BDE, 0xFF2E9A5B, 0xFF808080, 0xFFFFFFFF, 0xFF000000)) {
            val hsv = ColourMath.rgbToHsv(rgb.toInt())
            assertEquals(ColourMath.toHex(rgb.toInt()), ColourMath.toHex(ColourMath.hsvToRgb(hsv.h, hsv.s, hsv.v)))
        }
        val purple = ColourMath.rgbToHsv(0xFF7A5CFF.toInt())
        assertEquals(251.0f, purple.h, 0.1f)
        assertEquals(0.639f, purple.s, 0.001f)
        assertEquals(1f, purple.v, 0f)
    }

    @Test
    fun toneStripRunsBlackThroughColourToWhite() {
        assertEquals(0xFF000000.toInt(), ColourMath.withTone(0f, 1f, 0f))
        assertEquals(0xFFFF0000.toInt(), ColourMath.withTone(0f, 1f, 0.5f))
        assertEquals(0xFF800000.toInt(), ColourMath.withTone(0f, 1f, 0.25f))
        assertEquals(0xFFFF8080.toInt(), ColourMath.withTone(0f, 1f, 0.75f))
        assertEquals(0xFFFFFFFF.toInt(), ColourMath.withTone(0f, 1f, 1f))
        val ink = ColourMath.rgbToHsv(0xFF1C1B19.toInt())
        assertEquals("#1C1B19", ColourMath.toHex(ColourMath.withTone(ink.h, ink.s, ColourMath.toneOf(ink))))
    }

    @Test
    fun parsesHex() {
        assertEquals(0xFF7A5CFF.toInt(), ColourMath.parseHex("#7A5CFF"))
        assertEquals(0xFF7A5CFF.toInt(), ColourMath.parseHex("7a5cff"))
        assertEquals(0xFF7A5CFF.toInt(), ColourMath.parseHex("  #7a5CfF "))
        assertEquals(0xFFFFAA00.toInt(), ColourMath.parseHex("#fa0"))
        assertEquals(0xFF000000.toInt(), ColourMath.parseHex("#000000"))
    }

    @Test
    fun rejectsBadHex() {
        for (bad in listOf("", "#", "#12", "#1234", "#12345", "#1234567", "#FF7A5CFF", "#GGGGGG", "7A5C F", "##7A5CFF", "+7A5CF")) {
            assertNull(bad, ColourMath.parseHex(bad))
        }
    }

    @Test
    fun formatsHex() {
        assertEquals("#7A5CFF", ColourMath.toHex(0xFF7A5CFF.toInt()))
        assertEquals("#00000A", ColourMath.toHex(0x0000000A))
    }
}
