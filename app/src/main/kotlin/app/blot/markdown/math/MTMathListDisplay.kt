// Ported from AndroidMath (render/MTMathListDisplay.kt), a Kotlin port of iosMath:
// https://github.com/gregcockroft/AndroidMath, https://github.com/kostub/iosMath
// Copyright (c) 2018 AndroidMath. Copyright (c) 2013 MathChat.
// MIT License: see app/src/main/res/raw/licence_math_mit.txt.
package app.blot.markdown.math

import app.blot.markdown.math.MTLinePosition.KMTLinePositionRegular

internal data class CGPoint(var x: Float = 0.0f, var y: Float = 0.0f)
internal data class CGRect(var x: Float = 0.0f, var y: Float = 0.0f, var width: Float = 0.0f, var height: Float = 0.0f)

/**
 * A typeset box, y up. [draw] adds its outlines to a path in a y-down space, with ([ox], [oy])
 * the translation in effect (y up), as iosMath's draw does with a flipped context.
 */
internal open class MTDisplay(open var ascent: Float = 0.0f, open var descent: Float = 0.0f, open var width: Float = 0.0f,
                     var range: NSRange = NSRange(), var hasScript: Boolean = false) {

    var shiftDown: Float = 0.0f

    /** Position relative to the parent display; y up. */
    var position: CGPoint = CGPoint()
        set(value) {
            field = value.copy()
            positionChanged()
        }

    init {
        this.range = range.copy()
    }

    open fun positionChanged() {
    }

    open fun draw(out: MathPathBuilder, ox: Float, oy: Float) {
    }

    fun displayBounds(): CGRect {
        return CGRect(position.x, position.y - descent, width, ascent + descent)
    }

}


// List of normal atoms to display that would be an attributed string on iOS
// Since we do not allow kerning attribute changes this is a string displayed using the advances for the font
// Normally this is a single character. In some cases the string will be fused atoms
internal class MTCTLineDisplay(val str: String, range: NSRange, val font: MTFont, val atoms: List<MTMathAtom>) :
        MTDisplay(range = range) {

    init {
        computeDimensions()
    }

    // Our own implementation of the ios6 function to get glyph path bounds.
    fun computeDimensions() {
        val glyphs = font.getGidListForString(str)
        val num = glyphs.count()
        val bboxes: Array<BoundingBox?> = arrayOfNulls(num)
        val advances: Array<Float> = Array(num, { 0.0f })
        // Get the bounds for these glyphs
        font.mathTable.getBoundingRectsForGlyphs(glyphs.toList(), bboxes, num)
        font.mathTable.getAdvancesForGlyphs(glyphs.toList(), advances, num)

        this.width = 0.0f
        for (i in 0 until num) {
            val b = bboxes[i]
            if (b != null) {
                val ascent = maxOf(0.0f, b.upperRightY - 0)
                // Descent is how much the line goes below the origin. However if the line is all above the origin, then descent can't be negative.
                val descent = maxOf(0.0f, 0.0f - b.lowerLeftY)
                if (ascent > this.ascent) {
                    this.ascent = ascent
                }
                if (descent > this.descent) {
                    this.descent = descent
                }
                this.width += advances[i]
            }
        }

    }


    override fun draw(out: MathPathBuilder, ox: Float, oy: Float) {
        val glyphs = font.getGidListForString(str)
        var x = ox + position.x
        val y = -(oy + position.y)
        val scale = font.mathTable.scale
        for (g in glyphs) {
            out.glyph(font.file.outline(g), x, y, scale)
            x += font.file.advance(g) * scale
        }
    }


}


/**
@typedef MTLinePosition
@brief The type of position for a line, i.e. subscript/superscript or regular.
 */

internal enum class MTLinePosition {
    /// Regular
    KMTLinePositionRegular,
    /// Positioned at a subscript
    KMTLinePositionSubscript,
    /// Positioned at a superscript
    KMTLinePositionSuperscript
}

internal class MTMathListDisplay(displays: List<MTDisplay>, range: NSRange) : MTDisplay(range = range) {
    /// Where the line is positioned
    var type: MTLinePosition = KMTLinePositionRegular
    /// An array of MTDisplays which are positioned relative to the position of the
/// the current display.
    var subDisplays: List<MTDisplay>? = null
    /// If a subscript or superscript this denotes the location in the parent MTList. For a
    /// regular list this is NSNotFound
    var index = NSNotFound

    init {
        this.subDisplays = displays
        this.recomputeDimensions()
    }

    override fun draw(out: MathPathBuilder, ox: Float, oy: Float) {
        val sd = this.subDisplays ?: return
        for (displayAtom in sd) displayAtom.draw(out, ox + position.x, oy + position.y)
    }


    fun recomputeDimensions() {
        var max_ascent = 0.0f
        var max_descent = 0.0f
        var max_width = 0.0f
        val sd = this.subDisplays
        if (sd != null) {
            for (atom in sd.toList()) {
                val ascent = maxOf(0.0f, atom.position.y + atom.ascent)
                if (ascent > max_ascent) {
                    max_ascent = ascent
                }

                val descent = maxOf(0.0f, 0 - (atom.position.y - atom.descent))
                if (descent > max_descent) {
                    max_descent = descent
                }
                val width = atom.width + atom.position.x
                if (width > max_width) {
                    max_width = width
                }
            }
        }
        this.ascent = max_ascent
        this.descent = max_descent
        this.width = max_width
    }

}

// MTFractionDisplay

internal class MTFractionDisplay(var numerator: MTMathListDisplay, var denominator: MTMathListDisplay, range: NSRange) :
        MTDisplay(range = range) {
    var linePosition = 0.0f
    var lineThickness = 0.0f


    // NSAssert(self.range.length == 1, @"Fraction range length not 1 - range (%lu, %lu)", (unsigned long)range.location, (unsigned long)range.length)
    var numeratorUp: Float = 0.0f
        set(value) {
            field = value
            this.updateNumeratorPosition()
        }
    var denominatorDown: Float = 0.0f
        set(value) {
            field = value
            this.updateDenominatorPosition()
        }

    override var ascent: Float
        get() = this.numerator.ascent + this.numeratorUp
        set(value) {
        }

    override var descent: Float
        get() = this.denominator.descent + this.denominatorDown
        set(value) {
        }


    override var width: Float
        get() = maxOf(this.numerator.width, this.denominator.width)
        set(value) {
        }


    fun updateDenominatorPosition() {
        this.denominator.position = CGPoint(this.position.x + (this.width - this.denominator.width) / 2, this.position.y - this.denominatorDown)
    }

    fun updateNumeratorPosition() {

        this.numerator.position = CGPoint(this.position.x + (this.width - this.numerator.width) / 2, this.position.y + this.numeratorUp)
    }

    override fun positionChanged() {
        this.updateDenominatorPosition()
        this.updateNumeratorPosition()
    }

    override fun draw(out: MathPathBuilder, ox: Float, oy: Float) {
        this.numerator.draw(out, ox, oy)
        this.denominator.draw(out, ox, oy)
        if (lineThickness != 0f) {
            rule(out, ox + position.x, ox + position.x + width, oy + position.y + linePosition, lineThickness)
        }
    }

}

// MTRadicalDisplay


internal class MTRadicalDisplay(val radicand: MTMathListDisplay, val radicalGlyph: MTDisplay, range: NSRange) :
        MTDisplay(range = range) {

    init {
        updateRadicandPosition()
    }

    var radicalShift: Float = 0.0f
    var degree: MTMathListDisplay? = null
    var topKern: Float = 0.0f
    var lineThickness: Float = 0.0f


    fun setDegree(degree: MTMathListDisplay, fontMetrics: MTFontMathTable) {
        // sets up the degree of the radical
        var kernBefore = fontMetrics.radicalKernBeforeDegree
        val kernAfter = fontMetrics.radicalKernAfterDegree
        val raise = fontMetrics.radicalDegreeBottomRaisePercent * (this.ascent - this.descent)

        // The layout is:
        // kernBefore, raise, degree, kernAfter, radical
        this.degree = degree

        // the radical is now shifted by kernBefore + degree.width + kernAfter
        this.radicalShift = kernBefore + degree.width + kernAfter
        if (radicalShift < 0) {
            // we can't have the radical shift backwards, so instead we increase the kernBefore such
            // that _radicalShift will be 0.
            kernBefore -= radicalShift
            radicalShift = 0.0f
        }

        // Note: position of degree is relative to parent.
        val deg = this.degree
        if (deg != null) {
            deg.position = CGPoint(this.position.x + kernBefore, this.position.y + raise)
            // Update the width by the _radicalShift
            this.width = radicalShift + radicalGlyph.width + this.radicand.width
        }
        // update the position of the radicand
        this.updateRadicandPosition()
    }

    override fun positionChanged() {
        updateRadicandPosition()
    }

    fun updateRadicandPosition() {
        // The position of the radicand includes the position of the MTRadicalDisplay
        // This is to make the positioning of the radical consistent with fractions and
        // have the cursor position finding algorithm work correctly.
        // move the radicand by the width of the radical sign
        this.radicand.position = CGPoint(this.position.x + radicalShift + radicalGlyph.width, this.position.y)
    }

    override fun draw(out: MathPathBuilder, ox: Float, oy: Float) {
        this.radicand.draw(out, ox, oy)
        degree?.draw(out, ox, oy)
        // The glyph and the rule are relative to the radical sign's origin.
        val gx = ox + position.x + radicalShift
        val gy = oy + position.y
        radicalGlyph.draw(out, gx, gy)
        val y = ascent - topKern - lineThickness / 2
        // The rule starts half its thickness inside the sign so the two join without a seam.
        rule(out, gx + radicalGlyph.width - lineThickness / 2, gx + radicalGlyph.width + radicand.width, gy + y, lineThickness)
    }
}


// MTGlyphDisplay

internal class MTGlyphDisplay(val glyph: CGGlyph, range: NSRange, val myfont: MTFont) :
        MTDisplay(range = range) {

    override fun draw(out: MathPathBuilder, ox: Float, oy: Float) {
        val scale = myfont.mathTable.scale
        out.glyph(myfont.file.outline(glyph.gid), ox + position.x, -(oy + position.y - shiftDown), scale)
    }

    override var ascent: Float
        get() = super.ascent - this.shiftDown
        set(value) {
            super.ascent = value
        }

    override var descent: Float
        get() = super.descent + this.shiftDown
        set(value) {
            super.descent = value
        }

}


// MTGlyphConstructionDisplay

internal class MTGlyphConstructionDisplay(val glyphs: MutableList<Int>, val offsets: MutableList<Float>, val myfont: MTFont) :
        MTDisplay() {
    init {
        assert(glyphs.size == offsets.size)
    }


    override fun draw(out: MathPathBuilder, ox: Float, oy: Float) {
        val scale = myfont.mathTable.scale
        val x = ox + position.x
        val y = oy + position.y - shiftDown
        for (i in 0 until glyphs.count()) out.glyph(myfont.file.outline(glyphs[i]), x, -(y + offsets[i]), scale)
    }

    override var ascent: Float
        get() = super.ascent - this.shiftDown
        set(value) {
            super.ascent = value
        }

    override var descent: Float
        get() = super.descent + this.shiftDown
        set(value) {
            super.descent = value
        }

}

// MTLargeOpLimitsDisplay

internal class MTLargeOpLimitsDisplay(val nucleus: MTDisplay, var upperLimit: MTMathListDisplay?, var lowerLimit: MTMathListDisplay?, var limitShift: Float, var extraPadding: Float) :
        MTDisplay() {


    init {
        var maxWidth: Float = nucleus.width
        if (upperLimit != null) {
            maxWidth = maxOf(maxWidth, upperLimit!!.width)
        }
        if (lowerLimit != null) {
            maxWidth = maxOf(maxWidth, lowerLimit!!.width)
        }
        this.width = maxWidth
    }

    override var ascent
        get() = if (this.upperLimit != null) {
            nucleus.ascent + extraPadding + upperLimit!!.ascent + upperLimitGap + upperLimit!!.descent
        } else {
            nucleus.ascent
        }
        set(value) {
        }

    override var descent: Float
        get() = if (this.lowerLimit != null) {
            nucleus.descent + extraPadding + lowerLimitGap + lowerLimit!!.descent + lowerLimit!!.ascent
        } else {
            nucleus.descent
        }
        set(value) {
        }


    var lowerLimitGap: Float = 0.0f
        set(value) {
            field = value
            this.updateLowerLimitPosition()
        }


    var upperLimitGap: Float = 0.0f
        set(value) {
            field = value
            this.updateUpperLimitPosition()

        }

    override fun positionChanged() {
        this.updateLowerLimitPosition()
        this.updateUpperLimitPosition()
        this.updateNucleusPosition()
    }

    fun updateLowerLimitPosition() {
        val ll = this.lowerLimit
        if (ll != null) {
            // The position of the lower limit includes the position of the MTLargeOpLimitsDisplay
            // This is to make the positioning of the radical consistent with fractions and radicals
            // Move the starting point to below the nucleus leaving a gap of _lowerLimitGap and subtract
            // the ascent to to get the baseline. Also center and shift it to the left by _limitShift.
            ll.position = CGPoint(position.x - limitShift + (this.width - ll.width) / 2,
                    position.y - nucleus.descent - lowerLimitGap - ll.ascent)
        }
    }

    fun updateUpperLimitPosition() {
        val ul = this.upperLimit
        if (ul != null) {
            // The position of the upper limit includes the position of the MTLargeOpLimitsDisplay
            // This is to make the positioning of the radical consistent with fractions and radicals
            // Move the starting point to above the nucleus leaving a gap of _upperLimitGap and add
            // the descent to to get the baseline. Also center and shift it to the right by _limitShift.
            ul.position = CGPoint(position.x + limitShift + (this.width - ul.width) / 2,
                    position.y + nucleus.ascent + upperLimitGap + ul.descent)
        }
    }

    fun updateNucleusPosition() {
        // Center the nucleus
        nucleus.position = CGPoint(position.x + (this.width - nucleus.width) / 2, position.y)
    }

    override fun draw(out: MathPathBuilder, ox: Float, oy: Float) {
        upperLimit?.draw(out, ox, oy)
        lowerLimit?.draw(out, ox, oy)
        nucleus.draw(out, ox, oy)
    }
}

// MTLineDisplay  overline or underline
internal class MTLineDisplay(val inner: MTMathListDisplay, range: NSRange) :
        MTDisplay(range = range) {
    // How much the line should be moved up.
    var lineShiftUp: Float = 0.0f
    var lineThickness: Float = 0.0f

    override fun draw(out: MathPathBuilder, ox: Float, oy: Float) {
        this.inner.draw(out, ox, oy)
        if (lineThickness != 0f) {
            rule(out, ox + position.x, ox + position.x + width, oy + position.y + lineShiftUp, lineThickness)
        }
    }

    override fun positionChanged() {
        this.updateInnerPosition()
    }

    fun updateInnerPosition() {
        this.inner.position = CGPoint(this.position.x, this.position.y)
    }

}

// MTAccentDisplay

internal class MTAccentDisplay(val accent: MTGlyphDisplay, val accentee: MTMathListDisplay, range: NSRange) :
        MTDisplay(range = range) {
    init {
        accentee.position = CGPoint()
        super.range = range.copy()
    }

    override fun positionChanged() {
        this.updateAccenteePosition()
    }

    fun updateAccenteePosition() {
        this.accentee.position = CGPoint(this.position.x, this.position.y)
    }

    override fun draw(out: MathPathBuilder, ox: Float, oy: Float) {
        this.accentee.draw(out, ox, oy)
        this.accent.draw(out, ox + position.x, oy + position.y)
    }
}

/** A horizontal rule [thickness] thick centred on [y] (y up), as a filled rectangle. */
private fun rule(out: MathPathBuilder, x0: Float, x1: Float, y: Float, thickness: Float) {
    out.rect(x0, -(y + thickness / 2), x1, -(y - thickness / 2))
}
