package app.blot.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The marks a note's background shows under its elements. */
@Serializable
enum class Pattern {
    @SerialName("dots") DOTS,
    @SerialName("grid") GRID,
    @SerialName("ruled") RULED,
}

/**
 * A note's background pattern: [pattern] repeated every [spacing] canvas units. A note without
 * one ([Note.background] null) has a plain background.
 */
@Serializable
data class Background(val pattern: Pattern, val spacing: Float = defaultSpacing(pattern)) {
    init {
        require(spacing.isFinite() && spacing > 0f) { "background spacing $spacing" }
    }

    companion object {
        /** The design's dot spacing, also used for the grid. */
        const val DOT_SPACING = 28f

        /** College ruled: 9/32 inch at 160 canvas units per inch. */
        const val RULED_SPACING = 45f

        /** The spacing range the editor offers; files may hold any positive spacing. */
        const val MIN_SPACING = 8f
        const val MAX_SPACING = 96f

        fun defaultSpacing(pattern: Pattern): Float = when (pattern) {
            Pattern.DOTS, Pattern.GRID -> DOT_SPACING
            Pattern.RULED -> RULED_SPACING
        }
    }
}
