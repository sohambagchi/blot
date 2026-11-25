package app.blot.markdown.math

/**
 * Glyph outlines from a name-keyed `CFF ` table (Type 2 charstrings), the outline format of
 * Latin Modern Math. Hints are skipped; arithmetic operators, `seac` accents and CID-keyed fonts
 * aren't supported (the bundled font uses none of them). Thread-safe: every call decodes afresh.
 *
 * @param data the `CFF ` table's bytes.
 */
internal class CffOutlines(private val data: ByteArray) {
    private class Index(val count: Int, val offsets: IntArray, val dataStart: Int, val end: Int) {
        fun start(i: Int) = dataStart + offsets[i]
        fun end(i: Int) = dataStart + offsets[i + 1]
    }

    private val charStrings: Index
    private val localSubrs: Index?
    private val globalSubrs: Index

    init {
        val hdrSize = u8(2)
        val names = index(hdrSize)
        val topDicts = index(names.end)
        val strings = index(topDicts.end)
        globalSubrs = index(strings.end)
        val top = dict(topDicts.start(0), topDicts.end(0))
        require(top[CID_ROS] == null) { "CID-keyed CFF isn't supported" }
        charStrings = index(top.getValue(CHAR_STRINGS)[0].toInt())
        val priv = top[PRIVATE]
        localSubrs = if (priv == null) null else {
            val size = priv[0].toInt()
            val offset = priv[1].toInt()
            dict(offset, offset + size)[SUBRS]?.let { index(offset + it[0].toInt()) }
        }
    }

    val glyphCount: Int get() = charStrings.count

    private fun u8(i: Int) = data[i].toInt() and 0xFF
    private fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)

    private fun offset(at: Int, size: Int): Int {
        var v = 0
        for (k in 0 until size) v = (v shl 8) or u8(at + k)
        return v
    }

    private fun index(at: Int): Index {
        val count = u16(at)
        if (count == 0) return Index(0, IntArray(1), at + 2, at + 2)
        val offSize = u8(at + 2)
        val offsets = IntArray(count + 1) { offset(at + 3 + it * offSize, offSize) - 1 }
        val dataStart = at + 3 + (count + 1) * offSize
        return Index(count, offsets, dataStart, dataStart + offsets[count])
    }

    private fun dict(start: Int, end: Int): Map<Int, DoubleArray> {
        val out = HashMap<Int, DoubleArray>()
        val operands = ArrayList<Double>()
        var i = start
        while (i < end) {
            val b0 = u8(i)
            when {
                b0 <= 21 -> {
                    val op = if (b0 == 12) 1200 + u8(i + 1).also { i++ } else b0
                    i++
                    out[op] = operands.toDoubleArray()
                    operands.clear()
                }
                b0 == 28 -> { operands += data[i + 1].toInt().shl(8).or(u8(i + 2)).toDouble(); i += 3 }
                b0 == 29 -> { operands += offset(i + 1, 4).toDouble(); i += 5 }
                b0 == 30 -> { i = real(i + 1, operands) }
                b0 in 32..246 -> { operands += (b0 - 139).toDouble(); i++ }
                b0 in 247..250 -> { operands += ((b0 - 247) * 256 + u8(i + 1) + 108).toDouble(); i += 2 }
                b0 in 251..254 -> { operands += (-(b0 - 251) * 256 - u8(i + 1) - 108).toDouble(); i += 2 }
                else -> i++
            }
        }
        return out
    }

    /** Reads a nibble-coded real starting at [at]; returns the offset after it. */
    private fun real(at: Int, out: MutableList<Double>): Int {
        val sb = StringBuilder()
        var i = at
        while (true) {
            val b = u8(i++)
            for (n in intArrayOf(b shr 4, b and 0xF)) {
                when (n) {
                    in 0..9 -> sb.append(n)
                    0xA -> sb.append('.')
                    0xB -> sb.append('E')
                    0xC -> sb.append("E-")
                    0xE -> sb.append('-')
                    0xF -> {
                        out += sb.toString().toDoubleOrNull() ?: 0.0
                        return i
                    }
                }
            }
        }
    }

    /** The outline of glyph [gid] in font units, y up; an empty outline for a bad id. */
    fun outline(gid: Int): GlyphOutline {
        if (gid !in 0 until charStrings.count) return GlyphOutline(ByteArray(0), FloatArray(0), 0f, 0f, 0f, 0f)
        return Interpreter().run(charStrings.start(gid), charStrings.end(gid))
    }

    private inner class Interpreter {
        private val stack = FloatArray(48)
        private var sp = 0
        private var stems = 0
        private var widthSeen = false
        private var x = 0f
        private var y = 0f
        private var open = false
        private var depth = 0
        private var done = false
        private val verbs = ArrayList<Byte>()
        private val coords = ArrayList<Float>()

        fun run(start: Int, end: Int): GlyphOutline {
            exec(start, end)
            if (open) verbs += MathPath.CLOSE
            var x0 = Float.POSITIVE_INFINITY
            var y0 = Float.POSITIVE_INFINITY
            var x1 = Float.NEGATIVE_INFINITY
            var y1 = Float.NEGATIVE_INFINITY
            for (k in coords.indices step 2) {
                x0 = minOf(x0, coords[k]); x1 = maxOf(x1, coords[k])
                y0 = minOf(y0, coords[k + 1]); y1 = maxOf(y1, coords[k + 1])
            }
            if (coords.isEmpty()) { x0 = 0f; y0 = 0f; x1 = 0f; y1 = 0f }
            return GlyphOutline(verbs.toByteArray(), coords.toFloatArray(), x0, y0, x1, y1)
        }

        private fun bias(n: Int) = if (n < 1240) 107 else if (n < 33900) 1131 else 32768

        /** Drops a leading advance width, which the first stack-clearing operator may carry. */
        private fun width(expectEven: Boolean) {
            if (!widthSeen) {
                widthSeen = true
                if ((sp % 2 == 1) == expectEven) shift(1)
            }
        }

        private fun widthIf(extra: Boolean) {
            if (!widthSeen) {
                widthSeen = true
                if (extra) shift(1)
            }
        }

        private fun shift(n: Int) {
            System.arraycopy(stack, n, stack, 0, sp - n)
            sp -= n
        }

        private fun moveTo(dx: Float, dy: Float) {
            if (open) verbs += MathPath.CLOSE
            x += dx; y += dy
            verbs += MathPath.MOVE; coords += x; coords += y
            open = true
        }

        private fun lineTo(dx: Float, dy: Float) {
            x += dx; y += dy
            verbs += MathPath.LINE; coords += x; coords += y
        }

        private fun curveTo(dxa: Float, dya: Float, dxb: Float, dyb: Float, dxc: Float, dyc: Float) {
            val xa = x + dxa
            val ya = y + dya
            val xb = xa + dxb
            val yb = ya + dyb
            x = xb + dxc
            y = yb + dyc
            verbs += MathPath.CUBIC
            coords += xa; coords += ya; coords += xb; coords += yb; coords += x; coords += y
        }

        private fun exec(start: Int, end: Int) {
            depth++
            require(depth <= 10) { "subroutines nest too deep" }
            var i = start
            while (i < end && !done) {
                val b0 = u8(i)
                when {
                    b0 == 28 -> { push(data[i + 1].toInt().shl(8).or(u8(i + 2)).toFloat()); i += 3 }
                    b0 in 32..246 -> { push((b0 - 139).toFloat()); i++ }
                    b0 in 247..250 -> { push(((b0 - 247) * 256 + u8(i + 1) + 108).toFloat()); i += 2 }
                    b0 in 251..254 -> { push((-(b0 - 251) * 256 - u8(i + 1) - 108).toFloat()); i += 2 }
                    b0 == 255 -> { push(offset(i + 1, 4) / 65536f); i += 5 }
                    else -> {
                        i++
                        when (b0) {
                            1, 3, 18, 23 -> { width(expectEven = true); stems += sp / 2; sp = 0 }
                            19, 20 -> {
                                width(expectEven = true)
                                stems += sp / 2
                                sp = 0
                                i += (stems + 7) / 8
                            }
                            21 -> { widthIf(sp > 2); moveTo(stack[0], stack[1]); sp = 0 }
                            22 -> { widthIf(sp > 1); moveTo(stack[0], 0f); sp = 0 }
                            4 -> { widthIf(sp > 1); moveTo(0f, stack[0]); sp = 0 }
                            5 -> { for (k in 0 until sp - 1 step 2) lineTo(stack[k], stack[k + 1]); sp = 0 }
                            6, 7 -> {
                                var horizontal = b0 == 6
                                for (k in 0 until sp) {
                                    if (horizontal) lineTo(stack[k], 0f) else lineTo(0f, stack[k])
                                    horizontal = !horizontal
                                }
                                sp = 0
                            }
                            8 -> {
                                for (k in 0 until sp - 5 step 6) curveTo(stack[k], stack[k + 1], stack[k + 2], stack[k + 3], stack[k + 4], stack[k + 5])
                                sp = 0
                            }
                            24 -> {
                                var k = 0
                                while (sp - k >= 8) { curveTo(stack[k], stack[k + 1], stack[k + 2], stack[k + 3], stack[k + 4], stack[k + 5]); k += 6 }
                                lineTo(stack[k], stack[k + 1])
                                sp = 0
                            }
                            25 -> {
                                var k = 0
                                while (sp - k >= 8) { lineTo(stack[k], stack[k + 1]); k += 2 }
                                curveTo(stack[k], stack[k + 1], stack[k + 2], stack[k + 3], stack[k + 4], stack[k + 5])
                                sp = 0
                            }
                            26 -> {
                                var k = 0
                                var dx1 = 0f
                                if (sp % 2 == 1) { dx1 = stack[0]; k = 1 }
                                while (k + 3 < sp) {
                                    curveTo(dx1, stack[k], stack[k + 1], stack[k + 2], 0f, stack[k + 3])
                                    dx1 = 0f
                                    k += 4
                                }
                                sp = 0
                            }
                            27 -> {
                                var k = 0
                                var dy1 = 0f
                                if (sp % 2 == 1) { dy1 = stack[0]; k = 1 }
                                while (k + 3 < sp) {
                                    curveTo(stack[k], dy1, stack[k + 1], stack[k + 2], stack[k + 3], 0f)
                                    dy1 = 0f
                                    k += 4
                                }
                                sp = 0
                            }
                            30, 31 -> {
                                var horizontal = b0 == 31
                                var k = 0
                                while (k + 3 < sp) {
                                    val last = if (sp - k == 5) stack[k + 4] else 0f
                                    if (horizontal) curveTo(stack[k], 0f, stack[k + 1], stack[k + 2], last, stack[k + 3])
                                    else curveTo(0f, stack[k], stack[k + 1], stack[k + 2], stack[k + 3], last)
                                    horizontal = !horizontal
                                    k += 4
                                }
                                sp = 0
                            }
                            10 -> {
                                val subrs = localSubrs ?: error("no local subroutines")
                                val n = stack[--sp].toInt() + bias(subrs.count)
                                exec(subrs.start(n), subrs.end(n))
                            }
                            29 -> {
                                val n = stack[--sp].toInt() + bias(globalSubrs.count)
                                exec(globalSubrs.start(n), globalSubrs.end(n))
                            }
                            11 -> { depth--; return }
                            14 -> {
                                widthIf(sp == 1 || sp == 5)
                                require(sp < 4) { "seac accents aren't supported" }
                                done = true
                            }
                            12 -> { escape(u8(i)); i++ }
                            else -> sp = 0
                        }
                    }
                }
            }
            depth--
        }

        private fun escape(op: Int) {
            val s = stack
            when (op) {
                35 -> {
                    curveTo(s[0], s[1], s[2], s[3], s[4], s[5])
                    curveTo(s[6], s[7], s[8], s[9], s[10], s[11])
                }
                34 -> {
                    curveTo(s[0], 0f, s[1], s[2], s[3], 0f)
                    curveTo(s[4], 0f, s[5], -s[2], s[6], 0f)
                }
                36 -> {
                    curveTo(s[0], s[1], s[2], s[3], s[4], 0f)
                    curveTo(s[5], 0f, s[6], s[7], s[8], -(s[1] + s[3] + s[7]))
                }
                37 -> {
                    val dx = s[0] + s[2] + s[4] + s[6] + s[8]
                    val dy = s[1] + s[3] + s[5] + s[7] + s[9]
                    curveTo(s[0], s[1], s[2], s[3], s[4], s[5])
                    if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) curveTo(s[6], s[7], s[8], s[9], s[10], -dy)
                    else curveTo(s[6], s[7], s[8], s[9], -dx, s[10])
                }
                else -> error("charstring operator 12 $op isn't supported")
            }
            sp = 0
        }

        private fun push(v: Float) {
            require(sp < stack.size) { "charstring stack overflow" }
            stack[sp++] = v
        }
    }

    private companion object {
        const val CHAR_STRINGS = 17
        const val PRIVATE = 18
        const val SUBRS = 19
        const val CID_ROS = 1230
    }
}
