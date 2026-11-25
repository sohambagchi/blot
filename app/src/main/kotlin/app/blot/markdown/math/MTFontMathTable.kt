// Ported from AndroidMath (render/MTFont.kt, render/MTFontMathTable.kt), a Kotlin port of iosMath:
// https://github.com/gregcockroft/AndroidMath, https://github.com/kostub/iosMath
// Copyright (c) 2018 AndroidMath. Copyright (c) 2013 MathChat.
// MIT License: see app/src/main/res/raw/licence_math_mit.txt.
// Changes: metrics come from MathFontFile instead of FreeType, fonts aren't loaded by asset
// name, and glyphs are compared by id instead of by name.
package app.blot.markdown.math

internal data class MTGlyphPart(
    var glyph: Int = 0,
    var fullAdvance: Float = 0f,
    var startConnectorLength: Float = 0f,
    var endConnectorLength: Float = 0f,
    var isExtender: Boolean = false,
)

internal class BoundingBox(
    var lowerLeftX: Float = 0f,
    var lowerLeftY: Float = 0f,
    var upperRightX: Float = 0f,
    var upperRightY: Float = 0f,
) {
    val width: Float get() = upperRightX - lowerLeftX
    val height: Float get() = upperRightY - lowerLeftY
}

/** The maths font at one size, px. */
internal class MTFont(val file: MathFontFile, val fontSize: Float) {
    val mathTable = MTFontMathTable(this)

    fun findGlyphForCharacterAtIndex(index: Int, str: String): CGGlyph = CGGlyph(file.glyph(Character.codePointAt(str, index)))

    fun getGidListForString(str: String): List<Int> {
        val out = ArrayList<Int>(str.length)
        var i = 0
        while (i < str.length) {
            val cp = Character.codePointAt(str, i)
            i += Character.charCount(cp)
            out += file.glyph(cp)
        }
        return out
    }

    fun copyFontWithSize(size: Float): MTFont = if (size == fontSize) this else MTFont(file, size)
}

internal class MTFontMathTable(val font: MTFont) {
    private val file = font.file
    private val math = file.math
    val fontSize: Float = font.fontSize
    private val unitsPerEm = file.unitsPerEm

    fun getAdvancesForGlyphs(glyphs: List<Int>, advances: Array<Float>, count: Int) {
        for (i in 0 until count) advances[i] = fontUnitsToPt(file.advance(glyphs[i]))
    }

    fun unionBounds(u: BoundingBox, b: BoundingBox) {
        u.lowerLeftX = minOf(u.lowerLeftX, b.lowerLeftX)
        u.lowerLeftY = minOf(u.lowerLeftY, b.lowerLeftY)
        u.upperRightX = maxOf(u.upperRightX, b.upperRightX)
        u.upperRightY = maxOf(u.upperRightY, b.upperRightY)
    }

    fun getBoundingRectsForGlyphs(glyphs: List<Int>, boundingRects: Array<BoundingBox?>?, count: Int): BoundingBox {
        val enclosing = BoundingBox()
        for (i in 0 until count) {
            val o = file.outline(glyphs[i])
            val nb = BoundingBox(fontUnitsToPt(o.xMin), fontUnitsToPt(o.yMin), fontUnitsToPt(o.xMax), fontUnitsToPt(o.yMax))
            unionBounds(enclosing, nb)
            if (boundingRects != null) boundingRects[i] = nb
        }
        return enclosing
    }

    private fun fontUnitsToPt(fontUnits: Int): Float = fontUnits * fontSize / unitsPerEm

    private fun fontUnitsToPt(fontUnits: Float): Float = fontUnits * fontSize / unitsPerEm

    /** px per font unit at this size. */
    val scale: Float get() = fontSize / unitsPerEm

    fun muUnit(): Float = fontSize / 18

    private fun constantFromTable(constName: String): Float = fontUnitsToPt(math.constant(constName))

    private fun percentFromTable(percentName: String): Float = math.constant(percentName) / 100.0f

    val fractionNumeratorDisplayStyleShiftUp: Float get() = constantFromTable("FractionNumeratorDisplayStyleShiftUp")
    val fractionNumeratorShiftUp: Float get() = constantFromTable("FractionNumeratorShiftUp")
    val fractionDenominatorDisplayStyleShiftDown: Float get() = constantFromTable("FractionDenominatorDisplayStyleShiftDown")
    val fractionDenominatorShiftDown: Float get() = constantFromTable("FractionDenominatorShiftDown")
    val fractionNumeratorDisplayStyleGapMin: Float get() = constantFromTable("FractionNumDisplayStyleGapMin")
    val fractionNumeratorGapMin: Float get() = constantFromTable("FractionNumeratorGapMin")
    val fractionDenominatorDisplayStyleGapMin: Float get() = constantFromTable("FractionDenomDisplayStyleGapMin")
    val fractionDenominatorGapMin: Float get() = constantFromTable("FractionDenominatorGapMin")
    val fractionRuleThickness: Float get() = constantFromTable("FractionRuleThickness")
    val skewedFractionHorizontalGap: Float get() = constantFromTable("SkewedFractionHorizontalGap")
    val skewedFractionVerticalGap: Float get() = constantFromTable("SkewedFractionVerticalGap")

    // FractionDelimiterSize and FractionDelimiterDisplayStyleSize are not constants of the
    // OpenType MATH specification; like LuaTeX and KaTeX they derive from the font size.
    val fractionDelimiterSize: Float get() = 1.01f * fontSize
    val fractionDelimiterDisplayStyleSize: Float get() = 2.39f * fontSize

    val superscriptShiftUp: Float get() = constantFromTable("SuperscriptShiftUp")
    val superscriptShiftUpCramped: Float get() = constantFromTable("SuperscriptShiftUpCramped")
    val subscriptShiftDown: Float get() = constantFromTable("SubscriptShiftDown")
    val superscriptBaselineDropMax: Float get() = constantFromTable("SuperscriptBaselineDropMax")
    val subscriptBaselineDropMin: Float get() = constantFromTable("SubscriptBaselineDropMin")
    val superscriptBottomMin: Float get() = constantFromTable("SuperscriptBottomMin")
    val subscriptTopMax: Float get() = constantFromTable("SubscriptTopMax")
    val subSuperscriptGapMin: Float get() = constantFromTable("SubSuperscriptGapMin")
    val superscriptBottomMaxWithSubscript: Float get() = constantFromTable("SuperscriptBottomMaxWithSubscript")
    val spaceAfterScript: Float get() = constantFromTable("SpaceAfterScript")

    val radicalRuleThickness: Float get() = constantFromTable("RadicalRuleThickness")
    val radicalExtraAscender: Float get() = constantFromTable("RadicalExtraAscender")
    val radicalVerticalGap: Float get() = constantFromTable("RadicalVerticalGap")
    val radicalDisplayStyleVerticalGap: Float get() = constantFromTable("RadicalDisplayStyleVerticalGap")
    val radicalKernBeforeDegree: Float get() = constantFromTable("RadicalKernBeforeDegree")
    val radicalKernAfterDegree: Float get() = constantFromTable("RadicalKernAfterDegree")
    val radicalDegreeBottomRaisePercent: Float get() = percentFromTable("RadicalDegreeBottomRaisePercent")

    val upperLimitGapMin: Float get() = constantFromTable("UpperLimitGapMin")
    val upperLimitBaselineRiseMin: Float get() = constantFromTable("UpperLimitBaselineRiseMin")
    val lowerLimitGapMin: Float get() = constantFromTable("LowerLimitGapMin")
    val lowerLimitBaselineDropMin: Float get() = constantFromTable("LowerLimitBaselineDropMin")
    val limitExtraAscenderDescender: Float get() = 0.0f

    val axisHeight: Float get() = constantFromTable("AxisHeight")
    val scriptScaleDown: Float get() = percentFromTable("ScriptPercentScaleDown")
    val scriptScriptScaleDown: Float get() = percentFromTable("ScriptScriptPercentScaleDown")
    val mathLeading: Float get() = constantFromTable("MathLeading")
    val delimitedSubFormulaMinHeight: Float get() = constantFromTable("DelimitedSubFormulaMinHeight")

    val accentBaseHeight: Float get() = constantFromTable("AccentBaseHeight")
    val flattenedAccentBaseHeight: Float get() = constantFromTable("FlattenedAccentBaseHeight")

    val displayOperatorMinHeight: Float get() = constantFromTable("DisplayOperatorMinHeight")

    val overbarExtraAscender: Float get() = constantFromTable("OverbarExtraAscender")
    val overbarRuleThickness: Float get() = constantFromTable("OverbarRuleThickness")
    val overbarVerticalGap: Float get() = constantFromTable("OverbarVerticalGap")
    val underbarExtraDescender: Float get() = constantFromTable("UnderbarExtraDescender")
    val underbarRuleThickness: Float get() = constantFromTable("UnderbarRuleThickness")
    val underbarVerticalGap: Float get() = constantFromTable("UnderbarVerticalGap")

    val stackBottomDisplayStyleShiftDown: Float get() = constantFromTable("StackBottomDisplayStyleShiftDown")
    val stackBottomShiftDown: Float get() = constantFromTable("StackBottomShiftDown")
    val stackDisplayStyleGapMin: Float get() = constantFromTable("StackDisplayStyleGapMin")
    val stackGapMin: Float get() = constantFromTable("StackGapMin")
    val stackTopDisplayStyleShiftUp: Float get() = constantFromTable("StackTopDisplayStyleShiftUp")
    val stackTopShiftUp: Float get() = constantFromTable("StackTopShiftUp")
    val stretchStackBottomShiftDown: Float get() = constantFromTable("StretchStackBottomShiftDown")
    val stretchStackGapAboveMin: Float get() = constantFromTable("StretchStackGapAboveMin")
    val stretchStackGapBelowMin: Float get() = constantFromTable("StretchStackGapBelowMin")
    val stretchStackTopShiftUp: Float get() = constantFromTable("StretchStackTopShiftUp")

    fun getVerticalVariantsForGlyph(glyph: CGGlyph): List<Int> = math.verticalVariants(glyph.gid)

    fun getHorizontalVariantsForGlyph(glyph: CGGlyph): List<Int> = math.horizontalVariants(glyph.gid)

    /** The first vertical variant that isn't [glyph] itself, or [glyph] when there is none. */
    fun getLargerGlyph(glyph: Int): Int = math.verticalVariants(glyph).firstOrNull { it != glyph } ?: glyph

    fun getItalicCorrection(gid: Int): Float = fontUnitsToPt(math.italicCorrection(gid))

    /** Where an accent attaches; the middle of the advance when the font doesn't say. */
    fun getTopAccentAdjustment(glyph: Int): Float =
        math.topAccentAttachment(glyph)?.let { fontUnitsToPt(it) } ?: (fontUnitsToPt(file.advance(glyph)) / 2)

    val minConnectorOverlap: Float get() = fontUnitsToPt(math.minConnectorOverlap)

    fun getVerticalGlyphAssemblyForGlyph(glyph: Int): List<MTGlyphPart>? =
        math.verticalAssembly(glyph)?.map { pi ->
            MTGlyphPart(
                glyph = pi.glyph,
                fullAdvance = fontUnitsToPt(pi.fullAdvance),
                startConnectorLength = fontUnitsToPt(pi.startConnectorLength),
                endConnectorLength = fontUnitsToPt(pi.endConnectorLength),
                isExtender = pi.partFlags and 1 == 1,
            )
        }
}
