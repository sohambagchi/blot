package app.screenscratch.core.store

import java.io.IOException

/** A note's stable identity: the name of its directory. It never changes, even when the title does. */
@JvmInline
value class NoteId(val value: String) {
    override fun toString(): String = value
}

/** What the caller knows about a note's content, supplied on save and by the summarizer. */
data class NoteMeta(val title: String, val modified: Long)

/** One row of the library. [damaged] means neither `note.json` nor its backup could be parsed. */
data class NoteSummary(
    val id: NoteId,
    val title: String,
    val modified: Long,
    val damaged: Boolean = false,
)

/** A parsed note. [fromBackup] is true when it came from `note.json.tmp`; saving again repairs the note. */
data class NoteRead<T>(val value: T, val fromBackup: Boolean)

class NoSuchNoteException(id: NoteId) : IOException("no such note: $id")

/** Neither `note.json` nor `note.json.tmp` exists or parses. [suppressed] holds the parse errors. */
class NoteUnreadableException(id: NoteId) : IOException("note $id is unreadable")

/**
 * Notes in a user-chosen folder: one directory per note, holding `note.json` (opaque bytes to
 * this layer) and `assets/` (files named by content hash).
 *
 * See docs/adr/2026-11-24-1742-storage-saf-directory-per-note.md and
 * docs/adr/2026-11-24-1934-note-directory-named-by-id.md and
 * docs/adr/2026-11-24-1934-note-json-wins-over-tmp.md.
 */
interface NoteStore {
    /** Creates a note with [content] and returns its new id. */
    fun create(content: ByteArray, meta: NoteMeta): NoteId

    /**
     * Summaries of all notes, newest first. Uses the library index and re-reads only note
     * directories whose modified time changed since the last call.
     */
    fun list(): List<NoteSummary>

    /** Discards the library index and summarizes every note again. */
    fun rebuildIndex(): List<NoteSummary>

    /**
     * Reads `note.json` and hands it to [parse]. If `note.json` is missing, or [parse] throws
     * (any [Exception]), the same is tried with `note.json.tmp`. Read errors from the folder itself,
     * such as a lost permission, propagate unchanged.
     *
     * @throws NoteUnreadableException when neither copy parses.
     */
    fun <T> readNote(id: NoteId, parse: (ByteArray) -> T): NoteRead<T>

    /** Saves [content] as the note's `note.json` and records [meta] in the index. */
    fun writeNote(id: NoteId, content: ByteArray, meta: NoteMeta)

    /** Deletes the note's directory and everything in it. */
    fun delete(id: NoteId)

    /**
     * Stores [bytes] in the note's `assets/` as `<sha-256 hex>.<ext>` and returns that name.
     * Storing the same bytes again writes nothing.
     */
    fun putAsset(id: NoteId, bytes: ByteArray, ext: String): String

    fun readAsset(id: NoteId, name: String): ByteArray

    /** Deletes every file in the note's `assets/` not named in [referenced]; returns the names deleted. */
    fun deleteUnreferencedAssets(id: NoteId, referenced: Set<String>): List<String>
}
