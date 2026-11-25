package app.screenscratch.core.model.format

import app.screenscratch.core.model.StrokePoint
import java.util.Base64
import kotlin.math.PI

/**
 * Packs stroke samples into the compact blob stored in `note.json`.
 *
 * Layout (all integers unsigned LEB128 varints):
 * 1. channel mask: bit 0 time, 1 x, 2 y, 3 pressure, 4 tilt, 5 orientation;
 * 2. point count;
 * 3. per point, for each channel in the mask in bit order, the zig-zag-encoded difference
 *    between this point's quantised value and the previous point's (the first point's
 *    difference is from 0).
 *
 * A channel is left out of the mask exactly when all its quantised values are 0, and decodes
 * as 0. Quantisation steps are [XY_STEPS_PER_UNIT], [PRESSURE_STEPS] and [ANGLE_STEPS_PER_TURN];
 * time is whole milliseconds. Decoding then re-encoding a blob gives the same bytes.
 */
object PointCodec {
    /** Coordinate steps per canvas unit: x and y are stored to 1/16 unit. */
    const val XY_STEPS_PER_UNIT = 16

    /** Pressure 0–1 is stored as an integer 0–4095 (12 bits, the TB-J616F pen's resolution). */
    const val PRESSURE_STEPS = 4095

    /** Tilt and orientation are stored in steps of one 4096th of a turn (about 0.088°). */
    const val ANGLE_STEPS_PER_TURN = 4096

    private const val CHANNELS = 6
    private const val MAX_POINTS = 1 shl 20
    private const val RADIANS_PER_STEP = 2.0 * PI / ANGLE_STEPS_PER_TURN

    /** Encodes [points] as base64 (RFC 4648, with padding) of the varint blob. */
    fun encodeBase64(points: List<StrokePoint>): String = Base64.getEncoder().encodeToString(encode(points))

    /** Decodes [encodeBase64]'s output. Throws [IllegalArgumentException] on malformed input. */
    fun decodeBase64(text: String): List<StrokePoint> = decode(Base64.getDecoder().decode(text))

    /** Encodes [points] as the varint blob. Every value must be finite. */
    fun encode(points: List<StrokePoint>): ByteArray {
        val q = Array(CHANNELS) { LongArray(points.size) }
        points.forEachIndexed { i, p ->
            q[0][i] = p.timeMs
            q[1][i] = quantise(p.x.toDouble() * XY_STEPS_PER_UNIT)
            q[2][i] = quantise(p.y.toDouble() * XY_STEPS_PER_UNIT)
            q[3][i] = quantise(p.pressure.coerceIn(0f, 1f).toDouble() * PRESSURE_STEPS)
            q[4][i] = quantise(p.tilt / RADIANS_PER_STEP)
            q[5][i] = quantise(p.orientation / RADIANS_PER_STEP)
        }
        var mask = 0
        for (c in 0 until CHANNELS) if (q[c].any { it != 0L }) mask = mask or (1 shl c)

        val out = VarintWriter(8 + points.size * 8)
        out.write(mask.toLong())
        out.write(points.size.toLong())
        val prev = LongArray(CHANNELS)
        for (i in points.indices) {
            for (c in 0 until CHANNELS) {
                if (mask and (1 shl c) == 0) continue
                out.write(zigZag(q[c][i] - prev[c]))
                prev[c] = q[c][i]
            }
        }
        return out.toByteArray()
    }

    /** Decodes [encode]'s output. Throws [IllegalArgumentException] on malformed input. */
    fun decode(bytes: ByteArray): List<StrokePoint> {
        val r = VarintReader(bytes)
        val mask = r.read()
        require(mask in 0L until (1L shl CHANNELS)) { "unknown channel mask $mask" }
        val channels = (0 until CHANNELS).filter { mask.toInt() and (1 shl it) != 0 }
        val count = r.read()
        require(count in 0L..MAX_POINTS.toLong()) { "point count $count" }
        require(channels.isEmpty() || count <= r.remaining / channels.size) { "point count $count exceeds data" }

        val prev = LongArray(CHANNELS)
        val points = ArrayList<StrokePoint>(count.toInt())
        repeat(count.toInt()) {
            for (c in channels) prev[c] += unZigZag(r.read())
            points += StrokePoint(
                x = (prev[1].toDouble() / XY_STEPS_PER_UNIT).toFloat(),
                y = (prev[2].toDouble() / XY_STEPS_PER_UNIT).toFloat(),
                pressure = (prev[3].toDouble() / PRESSURE_STEPS).toFloat(),
                tilt = (prev[4] * RADIANS_PER_STEP).toFloat(),
                orientation = (prev[5] * RADIANS_PER_STEP).toFloat(),
                timeMs = prev[0],
            )
        }
        require(r.remaining == 0) { "${r.remaining} trailing bytes" }
        return points
    }

    private fun quantise(v: Double): Long {
        require(v.isFinite()) { "non-finite sample value" }
        return Math.round(v)
    }

    private fun zigZag(n: Long): Long = (n shl 1) xor (n shr 63)
    private fun unZigZag(n: Long): Long = (n ushr 1) xor -(n and 1)
}

private class VarintWriter(capacity: Int) {
    private var buf = ByteArray(capacity)
    private var size = 0

    /** Writes [v] as an unsigned LEB128 varint (7 bits per byte, low bits first). */
    fun write(v: Long) {
        var n = v
        while (true) {
            if (size == buf.size) buf = buf.copyOf(buf.size * 2)
            if (n and 0x7FL.inv() == 0L) {
                buf[size++] = n.toByte()
                return
            }
            buf[size++] = ((n and 0x7F) or 0x80).toByte()
            n = n ushr 7
        }
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}

private class VarintReader(private val bytes: ByteArray) {
    private var pos = 0
    val remaining: Int get() = bytes.size - pos

    fun read(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(pos < bytes.size) { "truncated varint" }
            val b = bytes[pos++].toInt()
            // The tenth byte holds only bit 63 and must end the varint.
            require(shift < 63 || (b and 0xFE) == 0) { "varint overflows 64 bits" }
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }
}
