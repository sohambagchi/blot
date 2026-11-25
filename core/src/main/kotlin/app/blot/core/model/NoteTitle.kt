package app.blot.core.model

/** The rules for [Note.title]: one line, trimmed, at most [MAX_LENGTH] characters; empty means untitled. */
object NoteTitle {
    /** What the library and the editor show for an untitled note. */
    const val UNTITLED = "Untitled"

    /** Longest title kept, in code points. */
    const val MAX_LENGTH = 120

    private val SPACES = Regex("[\\s\\p{Cntrl}\\u2028\\u2029]+")

    /**
     * [raw] as a title: line breaks, tabs and control characters become spaces, runs of spaces
     * collapse, the ends are trimmed and the rest is cut to [MAX_LENGTH] code points. Blank
     * input gives "" (untitled).
     */
    fun clean(raw: String): String {
        val one = SPACES.replace(raw, " ").trim()
        if (one.codePointCount(0, one.length) <= MAX_LENGTH) return one
        return one.substring(0, one.offsetByCodePoints(0, MAX_LENGTH)).trimEnd()
    }

    /** [title] for display: [UNTITLED] when it is blank. */
    fun display(title: String): String = title.ifBlank { UNTITLED }
}
