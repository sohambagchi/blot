package app.screenscratch.core.model

import app.screenscratch.core.model.format.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * A note: its metadata, page setup and elements.
 *
 * @property title shown in the library; may be empty.
 * @property elements in stacking order: index 0 is drawn first (at the back), the last is in front.
 *   Ids are unique.
 */
@Serializable
data class Note(
    val id: String,
    val title: String = "",
    @Serializable(with = InstantSerializer::class) val created: Instant,
    @Serializable(with = InstantSerializer::class) val modified: Instant,
    val page: PageSetup = PageSetup.Infinite,
    val elements: List<Element> = emptyList(),
) {
    /** The element with [id], or null. Linear in the element count. */
    fun element(id: String): Element? = elements.firstOrNull { it.id == id }
}

/** Page portrait or landscape. */
enum class Orientation { PORTRAIT, LANDSCAPE }

/** How a note's canvas is laid out. */
@Serializable
sealed interface PageSetup {
    /** One unbounded canvas. */
    @Serializable
    @SerialName("infinite")
    data object Infinite : PageSetup

    /**
     * [count] pages of [width] × [height] canvas units, stacked top to bottom: page `i` has its
     * top-left corner at `(0, i × (height + PAGE_GAP))`. Elements may straddle pages or sit in
     * the gaps; exports clip to each page.
     *
     * [width] and [height] are authoritative; [paper] only names the size for the UI.
     */
    @Serializable
    @SerialName("pages")
    data class Pages(val paper: Paper, val width: Float, val height: Float, val count: Int = 1) : PageSetup {
        init {
            require(width > 0f && height > 0f) { "page size $width×$height" }
            require(count >= 1) { "page count $count" }
        }

        val orientation: Orientation get() = if (width > height) Orientation.LANDSCAPE else Orientation.PORTRAIT

        /** Page [index]'s rectangle in canvas units, for 0 ≤ index < [count]. */
        fun pageRect(index: Int): Rect {
            require(index in 0 until count) { "page $index of $count" }
            return Rect(0f, index * (height + PAGE_GAP), width, height)
        }

        companion object {
            /** Canvas units between consecutive pages. */
            const val PAGE_GAP = 24f

            fun a4(orientation: Orientation = Orientation.PORTRAIT, count: Int = 1) =
                of(Paper.A4, A4_SHORT, A4_LONG, orientation, count)

            fun letter(orientation: Orientation = Orientation.PORTRAIT, count: Int = 1) =
                of(Paper.LETTER, LETTER_SHORT, LETTER_LONG, orientation, count)

            /** Pages the size of a screen [width] × [height] canvas units (dp), as it was when the note was made. */
            fun screen(width: Float, height: Float, count: Int = 1) = Pages(Paper.SCREEN, width, height, count)

            private fun of(paper: Paper, short: Float, long: Float, orientation: Orientation, count: Int) =
                if (orientation == Orientation.PORTRAIT) Pages(paper, short, long, count)
                else Pages(paper, long, short, count)

            // 160 canvas units per inch.
            const val A4_SHORT = 210f / 25.4f * 160f
            const val A4_LONG = 297f / 25.4f * 160f
            const val LETTER_SHORT = 8.5f * 160f
            const val LETTER_LONG = 11f * 160f
        }
    }
}

/** The named page size of [PageSetup.Pages]. */
@Serializable
enum class Paper {
    @SerialName("screen") SCREEN,
    @SerialName("a4") A4,
    @SerialName("letter") LETTER,
}
