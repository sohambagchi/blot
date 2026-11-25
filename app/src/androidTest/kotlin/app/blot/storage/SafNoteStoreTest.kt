package app.blot.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.blot.core.store.NoteId
import app.blot.core.store.NoteMeta
import app.blot.core.store.NoteRead
import app.blot.core.store.NoteSummary
import app.blot.core.store.TreeNoteStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The core note rules running over SAF against [TestDocumentsProvider] in another process. */
@RunWith(AndroidJUnit4::class)
class SafNoteStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = NoteFolder(context, context.getSharedPreferences("note_folder_test", Context.MODE_PRIVATE))
    private val indexDir = File(context.cacheDir, "saf-test-index")
    private lateinit var treeUri: Uri
    private var summarized = 0

    @Before
    fun setUp() {
        folder.forget()
        indexDir.deleteRecursively()
        assertEquals(NoteFolder.State.NotChosen, folder.state())
        treeUri = TestFolder.fresh(context)
        assertEquals(NoteFolder.State.Ready(treeUri), folder.adopt(treeUri))
    }

    @After
    fun tearDown() = folder.forget()

    private fun tree() = checkNotNull(folder.tree())

    private fun store(index: File = indexDir) =
        TreeNoteStore(tree(), index, { summarized++; TestNotes.meta(it) })

    private fun names(dir: Uri) = tree().list(dir).map { it.name }.toSet()

    private fun noteDir(id: NoteId) = tree().list(tree().root).single { it.name == id.value }.ref

    @Test
    fun noteLifecycle() {
        val s = store()
        val a = s.create(TestNotes.bytes("Alpha", 100, "a"), NoteMeta("Alpha", 100))
        val b = s.create(TestNotes.bytes("Beta", 200, "b"), NoteMeta("Beta", 200))
        assertEquals(listOf(NoteSummary(b, "Beta", 200), NoteSummary(a, "Alpha", 100)), s.list())
        assertEquals(setOf("note.json", "assets"), names(noteDir(a)))

        s.writeNote(a, TestNotes.bytes("Alpha 2", 300, "longer body than before"), NoteMeta("Alpha 2", 300))
        s.writeNote(a, TestNotes.bytes("Alpha 3", 400, "short"), NoteMeta("Alpha 3", 400))
        assertEquals(NoteRead("short", false), s.readNote(a, TestNotes::body))
        assertEquals(setOf("note.json", "assets"), names(noteDir(a)))
        assertEquals(listOf("Alpha 3", "Beta"), s.list().map { it.title })

        val image = ByteArray(10_000) { it.toByte() }
        val name = s.putAsset(a, image, "png")
        assertEquals(name, s.putAsset(a, image.copyOf(), "png"))
        val other = s.putAsset(a, byteArrayOf(1), "png")
        assertArrayEquals(image, s.readAsset(a, name))
        assertEquals(listOf(other), s.deleteUnreferencedAssets(a, setOf(name)))

        s.delete(b)
        assertEquals(setOf(a.value), names(tree().root))
        assertEquals(listOf(a), store().list().map { it.id })
    }

    @Test
    fun recoversFromInterruptedSaves() {
        val s = store()
        val id = s.create(TestNotes.bytes("A", 1, "saved"), NoteMeta("A", 1))
        val t = tree()

        // Crash after deleting note.json, before the rename.
        t.rename(t.list(noteDir(id)).single { it.name == "note.json" }.ref, "note.json.tmp")
        assertEquals(NoteRead("saved", true), s.readNote(id, TestNotes::body))
        s.writeNote(id, TestNotes.bytes("A", 2, "repaired"), NoteMeta("A", 2))
        assertEquals(NoteRead("repaired", false), s.readNote(id, TestNotes::body))
        assertEquals(setOf("note.json", "assets"), names(noteDir(id)))

        // A corrupt note.json with a good backup next to it.
        val backup = t.createFile(noteDir(id), "note.json.tmp")
        t.write(backup, TestNotes.bytes("A", 2, "backup"))
        t.write(t.list(noteDir(id)).single { it.name == "note.json" }.ref, "CORRUPT".toByteArray())
        assertEquals(NoteRead("backup", true), s.readNote(id, TestNotes::body))

        // A stale tmp next to a good note.json is ignored, then overwritten by the next save.
        s.writeNote(id, TestNotes.bytes("A", 3, "good"), NoteMeta("A", 3))
        t.write(t.createFile(noteDir(id), "note.json.tmp"), "half a wr".toByteArray())
        assertEquals(NoteRead("good", false), s.readNote(id, TestNotes::body))
        s.writeNote(id, TestNotes.bytes("A", 4, "next"), NoteMeta("A", 4))
        assertEquals(NoteRead("next", false), s.readNote(id, TestNotes::body))
        assertEquals(setOf("note.json", "assets"), names(noteDir(id)))
    }

    @Test
    fun indexRefreshReadsOnlyChangedNotes() {
        val ids = store().run {
            (1..3).map { create(TestNotes.bytes("N$it", it.toLong()), NoteMeta("N$it", it.toLong())) }
        }
        Thread.sleep(3_100) // until the directories' times are old enough to be trusted
        summarized = 0
        val s = store()
        assertEquals(3, s.list().size)
        assertEquals(3, s.list().size)
        assertEquals(0, summarized)

        // Another writer (a second store with its own index, like a sync client) changes one note.
        store(File(context.cacheDir, "saf-test-index-other"))
            .writeNote(ids[1], TestNotes.bytes("Changed", 9), NoteMeta("Changed", 9))
        assertEquals(NoteSummary(ids[1], "Changed", 9), s.list().first())
        assertEquals(1, summarized)

        summarized = 0
        assertEquals(3, s.rebuildIndex().size)
        assertEquals(3, summarized)
    }

    @Test
    fun detectsLostPermission() {
        assertTrue(folder.state() is NoteFolder.State.Ready)
        context.contentResolver.releasePersistableUriPermission(
            treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        assertEquals(NoteFolder.State.PermissionLost(treeUri), folder.state())
        assertNull(folder.tree())

        assertEquals(NoteFolder.State.Ready(treeUri), folder.adopt(TestFolder.fresh(context)))
    }
}
