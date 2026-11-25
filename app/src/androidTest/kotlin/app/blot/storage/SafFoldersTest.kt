package app.blot.storage

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.blot.core.store.FileTree
import app.blot.core.store.FolderDeleted
import app.blot.core.store.FolderNameException
import app.blot.core.store.NoteId
import app.blot.core.store.NoteMeta
import app.blot.core.store.NoteSummary
import app.blot.core.store.TreeEntry
import app.blot.core.store.TreeNoteStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** Folders and moves over SAF against [TestDocumentsProvider], with its moves on and off. */
@RunWith(AndroidJUnit4::class)
class SafFoldersTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = NoteFolder(context, context.getSharedPreferences("note_folder_test", Context.MODE_PRIVATE))
    private val indexDir = File(context.cacheDir, "saf-folders-index")

    @Before
    fun setUp() {
        folder.forget()
        indexDir.deleteRecursively()
        folder.adopt(TestFolder.fresh(context))
    }

    @After
    fun tearDown() {
        TestFolder.moves(context, true)
        folder.forget()
    }

    private fun tree() = checkNotNull(folder.tree())

    private fun store(t: FileTree<Uri> = tree()) = TreeNoteStore(t, indexDir, TestNotes::meta)

    private fun TreeNoteStore<Uri>.note(title: String, folder: String? = null, body: String = "") =
        create(TestNotes.bytes(title, 1, body), NoteMeta(title, 1), folder)

    /** Paths of every entry under the root, e.g. "Work/<id>/assets/<hash>.png". */
    private fun paths(dir: Uri = tree().root, prefix: String = ""): List<String> = tree().list(dir).flatMap { e ->
        listOf(prefix + e.name) + if (e.isDirectory) paths(e.ref, "$prefix${e.name}/") else emptyList()
    }

    @Test
    fun foldersHoldNotesAndRenameKeepsThem() {
        val s = store()
        assertEquals("Work", s.createFolder(" Work"))
        val top = s.note("Top")
        val inWork = s.note("Plan", "Work", "p")
        assertEquals(setOf("Work", top.value), tree().list(tree().root).map { it.name }.toSet())
        assertEquals(mapOf(top to null, inWork to "Work"), s.list().associate { it.id to it.folder })
        assertEquals("There's already a folder called “Work”", assertThrows(FolderNameException::class.java) { s.createFolder("WORK") }.reason)

        assertEquals("Projects", s.renameFolder("Work", "Projects"))
        assertEquals("p", s.readNote(inWork, TestNotes::body).value)
        s.writeNote(inWork, TestNotes.bytes("Plan", 2, "p2"), NoteMeta("Plan", 2))
        assertEquals(NoteSummary(inWork, "Plan", 2, folder = "Projects"), store().list().single { it.id == inWork })
        assertTrue("Projects/${inWork.value}/note.json" in paths())
    }

    @Test
    fun movesUseTheProviderWhenItCanAndCopyWhenItCannot() {
        for (moves in listOf(true, false)) {
            setUp()
            TestFolder.moves(context, moves)
            val watched = WatchedTree(tree())
            val s = store(watched)
            s.createFolder("Work")
            val id = s.note("Plan", body = "p")
            val image = ByteArray(20_000) { it.toByte() }
            val asset = s.putAsset(id, image, "png")

            s.move(id, "Work")
            assertEquals("moves=$moves", if (moves) 1 else 0, watched.providerMoves)
            assertEquals(listOf("Work", "Work/${id.value}", "Work/${id.value}/assets", "Work/${id.value}/assets/$asset", "Work/${id.value}/note.json"), paths().sorted())
            assertEquals("Work", s.folderOf(id))
            assertArrayEquals(image, s.readAsset(id, asset))
            assertEquals(NoteSummary(id, "Plan", 1, folder = "Work"), store().list().single())

            s.move(id, null)
            assertEquals(setOf("Work", id.value), tree().list(tree().root).map { it.name }.toSet())
            assertEquals("p", store().readNote(id, TestNotes::body).value)
        }
    }

    @Test
    fun deletingAFolderMovesItsNotesOutFirst() {
        TestFolder.moves(context, false)
        val s = store()
        s.createFolder("Work")
        val a = s.note("A", "Work", "a")
        val b = s.note("B", "Work", "b")
        assertEquals(FolderDeleted(moved = 2, removed = true), s.deleteFolder("Work"))
        assertEquals(setOf(a.value, b.value), tree().list(tree().root).map { it.name }.toSet())
        assertEquals(listOf(null, null), store().list().map { it.folder })
        assertEquals("b", store().readNote(b, TestNotes::body).value)
    }

    /** A copying move cut off at each provider call leaves the note whole, in one place. */
    @Test
    fun anInterruptedCopyingMoveLeavesExactlyOneCompleteNote() {
        var failAt = 0
        while (true) {
            setUp()
            TestFolder.moves(context, false)
            val setup = store()
            setup.createFolder("Work")
            val id = setup.note("Plan", body = "whole")
            val asset = setup.putAsset(id, ByteArray(4000) { 3 }, "png")
            setup.list()

            val failing = WatchedTree(tree(), failAt)
            try {
                store(failing).move(id, "Work")
            } catch (_: Interrupted) {
            }

            val fresh = store()
            val label = "failure at $failAt"
            assertEquals(label, listOf(id), fresh.list().map { it.id })
            val copies = paths().filter { it.substringAfterLast('/') == id.value }
            assertEquals(label, 1, copies.size)
            assertTrue(label, paths().none { it.substringAfterLast('/').startsWith(".") })
            assertEquals(label, "whole", fresh.readNote(id, TestNotes::body).value)
            assertArrayEquals(label, ByteArray(4000) { 3 }, fresh.readAsset(id, asset))
            if (failing.mutations <= failAt) {
                assertEquals("Work", fresh.folderOf(id))
                break
            }
            failAt++
        }
        assertTrue("got through $failAt steps", failAt > 6)
    }

    private class Interrupted : IOException("interrupted")

    /** Passes calls to [inner], counts provider moves, and dies at mutation [failAt] as a killed process would. */
    private class WatchedTree(private val inner: FileTree<Uri>, private val failAt: Int = -1) : FileTree<Uri> {
        var providerMoves = 0
        var mutations = 0
        private var dead = false

        private fun check() {
            if (dead) throw Interrupted()
        }

        private fun mutate() {
            check()
            if (mutations++ == failAt) {
                dead = true
                throw Interrupted()
            }
        }

        override val root: Uri get() = inner.root
        override val rootKey: String get() = inner.rootKey
        override fun list(dir: Uri): List<TreeEntry<Uri>> = check().let { inner.list(dir) }
        override fun read(file: Uri): ByteArray = check().let { inner.read(file) }
        override fun write(file: Uri, bytes: ByteArray) = mutate().let { inner.write(file, bytes) }
        override fun createFile(dir: Uri, name: String): Uri = mutate().let { inner.createFile(dir, name) }
        override fun createDirectory(dir: Uri, name: String): Uri = mutate().let { inner.createDirectory(dir, name) }
        override fun rename(ref: Uri, newName: String): Uri = mutate().let { inner.rename(ref, newName) }
        override fun delete(ref: Uri) = mutate().let { inner.delete(ref) }
        override fun move(ref: Uri, from: Uri, to: Uri): Uri? {
            mutate()
            return inner.move(ref, from, to)?.also { providerMoves++ }
        }
    }
}
