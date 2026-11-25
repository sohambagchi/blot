package app.blot.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTitleTest {
    @Test
    fun blankIsUntitled() {
        assertEquals("", NoteTitle.clean(""))
        assertEquals("", NoteTitle.clean("  \t\n "))
        assertEquals("Untitled", NoteTitle.display(""))
        assertEquals("Untitled", NoteTitle.display("   "))
        assertEquals("Raft", NoteTitle.display("Raft"))
    }

    @Test
    fun keepsOneTrimmedLine() {
        assertEquals("Raft notes", NoteTitle.clean("  Raft notes  "))
        assertEquals("Raft notes day 2", NoteTitle.clean("Raft\nnotes\r\n\tday 2"))
        assertEquals("a b", NoteTitle.clean("a\u0000\u0007b"))
        assertEquals("a b", NoteTitle.clean("a b"))
        assertEquals("Paxos: why? / how", NoteTitle.clean("Paxos: why? / how"))
    }

    @Test
    fun cutsAtMaxLengthWithoutSplittingCodePoints() {
        assertEquals("x".repeat(NoteTitle.MAX_LENGTH), NoteTitle.clean("x".repeat(NoteTitle.MAX_LENGTH + 30)))
        val emoji = "📝"
        val long = emoji.repeat(NoteTitle.MAX_LENGTH + 5)
        val cut = NoteTitle.clean(long)
        assertEquals(NoteTitle.MAX_LENGTH, cut.codePointCount(0, cut.length))
        assertEquals(emoji.repeat(NoteTitle.MAX_LENGTH), cut)
        assertEquals("word tail", NoteTitle.clean("word" + " ".repeat(NoteTitle.MAX_LENGTH) + "tail"))
        assertEquals("a".repeat(NoteTitle.MAX_LENGTH - 1), NoteTitle.clean("a".repeat(NoteTitle.MAX_LENGTH - 1) + " b"))
    }
}
