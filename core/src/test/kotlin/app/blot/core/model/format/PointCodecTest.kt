package app.blot.core.model.format

import app.blot.core.model.StrokePoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class PointCodecTest {
    private val xyTolerance = 0.5f / PointCodec.XY_STEPS_PER_UNIT + 1e-4f
    private val pressureTolerance = 0.5f / PointCodec.PRESSURE_STEPS + 1e-6f
    private val angleTolerance = (PI / PointCodec.ANGLE_STEPS_PER_TURN).toFloat() + 1e-6f

    @Test
    fun realStrokesRoundTripWithinHalfAStep() {
        for (name in Fixtures.strokeNames) {
            val points = Fixtures.stroke(name)
            val decoded = PointCodec.decodeBase64(PointCodec.encodeBase64(points))
            assertEquals(points.size, decoded.size)
            points.zip(decoded).forEach { (p, d) ->
                assertEquals(p.timeMs, d.timeMs)
                assertEquals(p.x, d.x, xyTolerance)
                assertEquals(p.y, d.y, xyTolerance)
                assertEquals(p.pressure, d.pressure, pressureTolerance)
                assertEquals(p.tilt, d.tilt, angleTolerance)
                assertEquals(p.orientation, d.orientation, angleTolerance)
            }
        }
    }

    @Test
    fun pressureIsLosslessForTwelveBitPen() {
        val points = Fixtures.stroke(Fixtures.strokeNames[1])
        val decoded = PointCodec.decode(PointCodec.encode(points))
        points.zip(decoded).forEach { (p, d) ->
            assertEquals(Math.round(p.pressure * 4095.0), Math.round(d.pressure * 4095.0))
        }
    }

    @Test
    fun reencodingIsIdempotent() {
        val bytes = PointCodec.encode(Fixtures.stroke(Fixtures.strokeNames[0]))
        val once = PointCodec.decode(bytes)
        assertArrayEquals(bytes, PointCodec.encode(once))
        assertEquals(once, PointCodec.decode(PointCodec.encode(once)))
    }

    @Test
    fun goldenBytes() {
        // Mask 0b001011 (time, x, pressure), count 2, then per point the deltas of t, x, p.
        // x 1.5 → 24 → zig-zag 48; x −0.25 → −4, delta −28 → zig-zag 55.
        // p 1.0 → 4095 → zig-zag 8190 = varint FE 3F; p 0 → delta −4095 → zig-zag 8189 = FD 3F.
        val points = listOf(
            StrokePoint(1.5f, 0f, 1f, timeMs = 0),
            StrokePoint(-0.25f, 0f, 0f, timeMs = 5),
        )
        val expected = byteArrayOf(0b001011, 2, 0, 48, 0xFE.toByte(), 0x3F, 10, 55, 0xFD.toByte(), 0x3F)
        assertArrayEquals(expected, PointCodec.encode(points))
        assertEquals(points, PointCodec.decode(expected))
    }

    @Test
    fun emptyAndExtremeValuesRoundTrip() {
        assertEquals(emptyList<StrokePoint>(), PointCodec.decode(PointCodec.encode(emptyList())))
        val points = listOf(
            StrokePoint(-500_000f, 500_000f, 0f, (PI / 2).toFloat(), (-PI).toFloat(), timeMs = 0),
            StrokePoint(0f, 0f, 1f, 0f, PI.toFloat(), timeMs = 86_400_000),
        )
        val decoded = PointCodec.decode(PointCodec.encode(points))
        points.zip(decoded).forEach { (p, d) ->
            assertEquals(p.x, d.x, xyTolerance)
            assertEquals(p.y, d.y, xyTolerance)
            assertEquals(p.orientation, d.orientation, angleTolerance)
            assertEquals(p.timeMs, d.timeMs)
        }
    }

    @Test
    fun outOfRangePressureIsClamped() {
        val d = PointCodec.decode(PointCodec.encode(listOf(StrokePoint(0f, 0f, 1.7f, timeMs = 0))))
        assertEquals(1f, d.single().pressure)
    }

    @Test
    fun malformedInputIsRejected() {
        val good = PointCodec.encode(Fixtures.stroke(Fixtures.strokeNames[0]))
        val bad = listOf(
            good.copyOf(good.size - 1), // truncated
            good + 0, // trailing byte
            byteArrayOf(0x40, 0), // unknown channel bit
            byteArrayOf(0x01, 0x7F, 0), // more points than bytes
            byteArrayOf(0x01, 1) + ByteArray(10) { 0xFF.toByte() }, // varint past 64 bits
        )
        for (b in bad) assertThrows(IllegalArgumentException::class.java) { PointCodec.decode(b) }
        assertThrows(IllegalArgumentException::class.java) { PointCodec.decodeBase64("not base64!") }
        assertThrows(IllegalArgumentException::class.java) {
            PointCodec.encode(listOf(StrokePoint(Float.NaN, 0f, 0f, timeMs = 0)))
        }
    }

    @Serializable
    private data class NaivePoint(
        val x: Float, val y: Float, val pressure: Float, val tilt: Float, val orientation: Float, val timeMs: Long,
    )

    @Test
    fun encodedIsMuchSmallerThanNaiveJson() {
        for (name in Fixtures.strokeNames) {
            val points = Fixtures.stroke(name)
            val naive = Json.encodeToString(
                ListSerializer(NaivePoint.serializer()),
                points.map { NaivePoint(it.x, it.y, it.pressure, it.tilt, it.orientation, it.timeMs) },
            ).length
            val encoded = PointCodec.encodeBase64(points).length
            val ratio = naive.toDouble() / encoded
            println(
                "$name: ${points.size} points, naive JSON $naive B, base64 blob $encoded B " +
                    "(%.2f B/point), ratio %.1f×".format(encoded.toDouble() / points.size, ratio),
            )
            assertTrue("ratio $ratio", ratio >= 5.0)
        }
    }

    @Test
    fun allZeroChannelsAreLeftOut() {
        val points = listOf(StrokePoint(3f, 4f, 0.5f, timeMs = 0), StrokePoint(3f, 5f, 0.5f, timeMs = 4))
        val bytes = PointCodec.encode(points)
        assertEquals(0b001111, bytes[0].toInt())
        assertTrue(abs(PointCodec.decode(bytes)[1].tilt) == 0f)
    }
}
