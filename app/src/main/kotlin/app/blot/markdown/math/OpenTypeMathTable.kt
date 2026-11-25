// Ported from AndroidMath's MTFreeTypeMathTable.kt (com.pvporbit.freetype), which reads the
// OpenType MATH table: https://github.com/gregcockroft/AndroidMath
// Copyright (c) 2018 AndroidMath. MIT License: see res/raw/licence_math_mit.txt.
// Changes: reads the table bytes directly instead of through FreeType, treats offsets, counts
// and glyph ids as unsigned, and keeps constants in an array instead of a map.
package app.blot.markdown.math

import java.nio.ByteBuffer

/** The OpenType `MATH` table in font units: constants, italics corrections, accent attachment and glyph variants. */
internal class OpenTypeMathTable(private val data: ByteBuffer) {
    class GlyphPartRecord(val glyph: Int, val startConnectorLength: Int, val endConnectorLength: Int, val fullAdvance: Int, val partFlags: Int)
    private class Construction(val assembly: Array<GlyphPartRecord>?, val variants: IntArray)

    private val constants = IntArray(CONSTANT_NAMES.size)
    private val italicsCorrection = HashMap<Int, Int>()
    private val topAccentAttachment = HashMap<Int, Int>()
    private val vertical = HashMap<Int, Construction>()
    private val horizontal = HashMap<Int, Construction>()
    var minConnectorOverlap: Int = 0
        private set

    init {
        require(data.getInt(0) == 0x00010000) { "unknown MATH table version" }
        readConstants(u16(4))
        val glyphInfo = u16(6)
        readMatched(glyphInfo + u16(glyphInfo), italicsCorrection)
        readMatched(glyphInfo + u16(glyphInfo + 2), topAccentAttachment)
        readVariants(u16(8))
    }

    private fun u16(at: Int) = data.getShort(at).toInt() and 0xFFFF
    private fun s16(at: Int) = data.getShort(at).toInt()

    /** The constant [name] as the MATH specification names it, e.g. `AxisHeight`. */
    fun constant(name: String): Int {
        val i = CONSTANT_NAMES.indexOf(name)
        require(i >= 0) { "unknown MATH constant $name" }
        return constants[i]
    }

    fun italicCorrection(gid: Int): Int = italicsCorrection[gid] ?: 0

    fun topAccentAttachment(gid: Int): Int? = topAccentAttachment[gid]

    /** The glyph's vertical variants, smallest first; just the glyph when it has none. */
    fun verticalVariants(gid: Int): List<Int> = variants(vertical, gid)

    fun horizontalVariants(gid: Int): List<Int> = variants(horizontal, gid)

    fun verticalAssembly(gid: Int): Array<GlyphPartRecord>? = vertical[gid]?.assembly

    private fun variants(table: Map<Int, Construction>, gid: Int): List<Int> {
        val v = table[gid]
        return if (v == null || v.variants.isEmpty()) listOf(gid) else v.variants.toList()
    }

    private fun readConstants(at: Int) {
        var p = at
        for (i in CONSTANT_NAMES.indices) {
            constants[i] = s16(p)
            // The first four are plain 16-bit values; the rest are MathValueRecords (value, device table offset).
            p += if (i < 4 || i == CONSTANT_NAMES.lastIndex) 2 else 4
        }
    }

    private fun readMatched(at: Int, out: HashMap<Int, Int>) {
        val coverage = coverage(at + u16(at))
        val count = u16(at + 2)
        for (i in 0 until count) out[coverage[i]] = s16(at + 4 + i * 4)
    }

    private fun coverage(at: Int): IntArray = when (u16(at)) {
        1 -> IntArray(u16(at + 2)) { u16(at + 4 + it * 2) }
        2 -> {
            val ranges = u16(at + 2)
            var total = 0
            for (r in 0 until ranges) total = maxOf(total, u16(at + 4 + r * 6 + 4) + u16(at + 4 + r * 6 + 2) - u16(at + 4 + r * 6) + 1)
            val out = IntArray(total)
            for (r in 0 until ranges) {
                val rec = at + 4 + r * 6
                val start = u16(rec)
                val end = u16(rec + 2)
                val index = u16(rec + 4)
                for (g in start..end) out[index + g - start] = g
            }
            out
        }
        else -> error("bad coverage format")
    }

    private fun readVariants(at: Int) {
        minConnectorOverlap = u16(at)
        val vertCoverage = coverage(at + u16(at + 2))
        val horizCoverage = coverage(at + u16(at + 4))
        val vertCount = u16(at + 6)
        val horizCount = u16(at + 8)
        for (g in 0 until vertCount) vertical[vertCoverage[g]] = construction(at + u16(at + 10 + g * 2))
        for (g in 0 until horizCount) horizontal[horizCoverage[g]] = construction(at + u16(at + 10 + (vertCount + g) * 2))
    }

    private fun construction(at: Int): Construction {
        val assemblyOffset = u16(at)
        val count = u16(at + 2)
        val variants = IntArray(count) { u16(at + 4 + it * 4) }
        return Construction(if (assemblyOffset == 0) null else assembly(at + assemblyOffset), variants)
    }

    private fun assembly(at: Int): Array<GlyphPartRecord> {
        val count = u16(at + 4)
        return Array(count) {
            val p = at + 6 + it * 10
            GlyphPartRecord(u16(p), u16(p + 2), u16(p + 4), u16(p + 6), u16(p + 8))
        }
    }

    private companion object {
        val CONSTANT_NAMES = arrayOf(
            "ScriptPercentScaleDown", "ScriptScriptPercentScaleDown", "DelimitedSubFormulaMinHeight",
            "DisplayOperatorMinHeight", "MathLeading", "AxisHeight", "AccentBaseHeight",
            "FlattenedAccentBaseHeight", "SubscriptShiftDown", "SubscriptTopMax", "SubscriptBaselineDropMin",
            "SuperscriptShiftUp", "SuperscriptShiftUpCramped", "SuperscriptBottomMin", "SuperscriptBaselineDropMax",
            "SubSuperscriptGapMin", "SuperscriptBottomMaxWithSubscript", "SpaceAfterScript", "UpperLimitGapMin",
            "UpperLimitBaselineRiseMin", "LowerLimitGapMin", "LowerLimitBaselineDropMin", "StackTopShiftUp",
            "StackTopDisplayStyleShiftUp", "StackBottomShiftDown", "StackBottomDisplayStyleShiftDown", "StackGapMin",
            "StackDisplayStyleGapMin", "StretchStackTopShiftUp", "StretchStackBottomShiftDown",
            "StretchStackGapAboveMin", "StretchStackGapBelowMin", "FractionNumeratorShiftUp",
            "FractionNumeratorDisplayStyleShiftUp", "FractionDenominatorShiftDown",
            "FractionDenominatorDisplayStyleShiftDown", "FractionNumeratorGapMin", "FractionNumDisplayStyleGapMin",
            "FractionRuleThickness", "FractionDenominatorGapMin", "FractionDenomDisplayStyleGapMin",
            "SkewedFractionHorizontalGap", "SkewedFractionVerticalGap", "OverbarVerticalGap", "OverbarRuleThickness",
            "OverbarExtraAscender", "UnderbarVerticalGap", "UnderbarRuleThickness", "UnderbarExtraDescender",
            "RadicalVerticalGap", "RadicalDisplayStyleVerticalGap", "RadicalRuleThickness", "RadicalExtraAscender",
            "RadicalKernBeforeDegree", "RadicalKernAfterDegree", "RadicalDegreeBottomRaisePercent",
        )
    }
}
