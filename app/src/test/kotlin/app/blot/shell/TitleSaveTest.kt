package app.blot.shell

import app.blot.canvas.CanvasController
import app.blot.canvas.FrontBufferContent
import app.blot.core.model.Note
import app.blot.core.model.format.NoteJson
import app.blot.core.store.JavaFileTree
import app.blot.core.store.NoteId
import app.blot.core.store.NoteMeta
import app.blot.core.store.TreeNoteStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** A title change is saved like any edit, and the library index and listing follow it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleSaveTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val store by lazy {
        TreeNoteStore(JavaFileTree(tmp.newFolder("notes")), tmp.newFolder("index"), { b ->
            NoteJson.decode(b.decodeToString()).let { NoteMeta(it.title, it.modified.toEpochMilli()) }
        })
    }

    private fun create(note: Note): NoteId = store.create(NoteJson.encode(note).encodeToByteArray(), NoteMeta(note.title, note.modified.toEpochMilli()))

    private fun read(id: NoteId): Note = store.readNote(id) { NoteJson.decode(it.decodeToString()) }.value

    @Test
    fun renamingInTheEditorSavesThroughAutosave() {
        val note = CanvasController.newNote()
        val id = create(note)
        val now = CoroutineScope(Dispatchers.Unconfined)
        val session = EditorSession(
            id, note,
            SessionSetup(dark = false, penOnly = true, swapSideButtons = false, frontBuffer = FrontBufferContent.INK_ONLY, folderName = "Notes", editTitle = true),
            store, now, now, ThumbnailCache(tmp.newFolder("thumbs")), UnavailableExporter,
            onBack = {}, onPenOnlyChanged = {}, onSaveFailed = { throw it }, screenPageSize = { 1280f to 800f },
        )
        assertTrue(session.editor.uiState.titleEditing)
        assertEquals("", session.editor.uiState.noteTitle)

        session.editor.setTitle("Raft notes")
        assertTrue(session.autosave.hasPending)
        session.flush()

        assertEquals("Raft notes", read(id).title)
        assertEquals(note.elements, read(id).elements)
        assertEquals(listOf("Raft notes"), store.list().map { it.title })
        session.close()
    }

    @Test
    fun renamingFromTheLibraryRewritesTheNoteAndItsSummary() {
        val created = Instant.parse("2026-11-24T10:00:00Z")
        val id = create(CanvasController.newNote().copy(title = "Raft", created = created, modified = created))
        val later = Instant.parse("2026-11-24T12:00:00Z")

        AppModel.renameInStore(store, id, "  Paxos\tvs Raft ", later)
        val renamed = read(id)
        assertEquals("Paxos vs Raft", renamed.title)
        assertEquals(later, renamed.modified)
        assertEquals(created, renamed.created)
        assertEquals(listOf("Paxos vs Raft"), store.list().map { it.title })

        // Blank makes it untitled; the same title again changes nothing.
        AppModel.renameInStore(store, id, " ", later.plusSeconds(60))
        assertEquals("", read(id).title)
        AppModel.renameInStore(store, id, "", later.plusSeconds(120))
        assertEquals(later.plusSeconds(60), read(id).modified)
    }
}
