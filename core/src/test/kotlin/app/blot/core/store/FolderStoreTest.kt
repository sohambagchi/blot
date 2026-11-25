package app.blot.core.store

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FolderStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val rootDir by lazy { tmp.newFolder("notes") }
    private val indexDir by lazy { tmp.newFolder("index") }
    private val tree by lazy { CountingTree(JavaFileTree(rootDir)) }
    private var summarized = 0
    private var now = System.currentTimeMillis() + 60_000

    private fun store(t: FileTree<File> = tree) =
        TreeNoteStore(t, indexDir, { summarized++; TestNotes.meta(it) }, clock = { now })

    private fun NoteStore.body(id: NoteId) = readNote(id) { TestNotes.parse(it) }.value.third

    private fun NoteStore.note(title: String, folder: String? = null, body: String = "") =
        create(TestNotes.bytes(title, title.length.toLong(), body), NoteMeta(title, title.length.toLong()), folder)

    /** Every directory named [id] anywhere under the root, as paths relative to it. */
    private fun copiesOf(id: NoteId): List<String> =
        rootDir.walkTopDown().filter { it.isDirectory && it.name == id.value }.map { it.relativeTo(rootDir).path }.toList()

    private fun hidden(): List<String> =
        rootDir.walkTopDown().filter { it.name.startsWith(".") }.map { it.relativeTo(rootDir).path }.toList()

    @Test
    fun notesAndFoldersSideBySide() {
        val s = store()
        assertEquals("Work", s.createFolder("  Work "))
        s.createFolder("Journal")
        val top = s.note("Top")
        val inWork = s.note("Plan", "Work", "w")
        val inJournal = s.note("Monday", "Journal")

        assertTrue(File(rootDir, "Work/${inWork.value}/note.json").isFile)
        assertTrue(File(rootDir, "Work/${inWork.value}/assets").isDirectory)
        assertEquals(listOf("Journal", "Work"), s.folders())
        assertEquals(mapOf(top to null, inWork to "Work", inJournal to "Journal"), s.list().associate { it.id to it.folder })
        assertEquals("Work", s.folderOf(inWork))
        assertNull(s.folderOf(top))
        assertEquals("w", s.body(inWork))

        // A new process finds them all from the folder and the index alone.
        summarized = 0
        val again = store()
        assertEquals(s.list(), again.list())
        assertEquals(listOf("Journal", "Work"), again.folders())
        assertEquals(0, summarized)

        // Writes, assets and deletes reach notes inside folders.
        again.writeNote(inWork, TestNotes.bytes("Plan B", 9, "w2"), NoteMeta("Plan B", 9))
        val asset = again.putAsset(inWork, byteArrayOf(1, 2), "png")
        assertArrayEquals(byteArrayOf(1, 2), again.readAsset(inWork, asset))
        assertEquals(NoteSummary(inWork, "Plan B", 9, folder = "Work"), again.list().single { it.id == inWork })
        again.delete(inJournal)
        assertFalse(File(rootDir, "Journal/${inJournal.value}").exists())
        assertEquals(setOf(top, inWork), store().list().map { it.id }.toSet())
    }

    @Test
    fun anEmptyTopLevelDirectoryIsAFolderAndFoldersDoNotNest() {
        File(rootDir, "Sketches").mkdir()
        File(rootDir, ".stfolder").mkdir()
        File(rootDir, "Sketches/Old").mkdir()
        File(rootDir, "Sketches/Old/inner").mkdir()
        File(rootDir, "Sketches/Old/inner/note.json").writeBytes(TestNotes.bytes("Deep", 1))
        val s = store()
        assertEquals(listOf("Sketches"), s.folders())
        assertEquals(emptyList<NoteSummary>(), s.list())
    }

    @Test
    fun aFolderHoldingANonNoteDirectoryKeepsItAndIsKeptOnDelete() {
        val s = store()
        s.createFolder("Work")
        val id = s.note("Plan", "Work")
        File(rootDir, "Work/Pictures").mkdir()
        File(rootDir, "Work/Pictures/cat.jpg").writeText("meow")
        assertEquals(listOf(id), s.list().map { it.id })

        // Remembered as not a note: later refreshes list the root and the folder, nothing else.
        tree.reset()
        s.list()
        assertEquals(mapOf("list" to 2), tree.calls)

        assertEquals(FolderDeleted(moved = 1, removed = false), s.deleteFolder("Work"))
        assertTrue(File(rootDir, "Work/Pictures/cat.jpg").isFile)
        assertEquals(listOf(id.value), copiesOf(id))
        assertEquals(NoteSummary(id, "Plan", 4), s.list().single())
    }

    @Test
    fun deletingAFolderMovesItsNotesToTheTopLevelAndNeverDeletesThem() {
        for (canMove in listOf(true, false)) {
            tmp.root.listFiles()!!.forEach { it.deleteRecursively() }
            rootDir.mkdirs(); indexDir.mkdirs()
            val s = store(JavaFileTree(rootDir, canMove))
            s.createFolder("Work")
            val a = s.note("A", "Work", "a")
            val b = s.note("B", "Work", "b")
            val asset = s.putAsset(b, byteArrayOf(7, 7, 7), "png")
            File(rootDir, "Work/.DS_Store").writeText("x")

            assertEquals(FolderDeleted(moved = 2, removed = true), s.deleteFolder("Work"))
            assertFalse(File(rootDir, "Work").exists())
            assertEquals(emptyList<String>(), s.folders())
            assertEquals(setOf(NoteSummary(a, "A", 1), NoteSummary(b, "B", 1)), s.list().toSet())
            assertEquals("b", s.body(b))
            assertArrayEquals(byteArrayOf(7, 7, 7), s.readAsset(b, asset))
            assertEquals(listOf(a.value), copiesOf(a))
            assertEquals(emptyList<String>(), hidden())
            assertThrows(NoSuchFolderException::class.java) { s.deleteFolder("Work") }
        }
    }

    @Test
    fun renamingAFolderKeepsItsNotesAndTheirIndexEntries() {
        val s = store()
        s.createFolder("work")
        val id = s.note("Plan", "work", "p")
        s.list()

        summarized = 0
        assertEquals("Projects", s.renameFolder("work", " Projects"))
        assertEquals(listOf("Projects"), s.folders())
        assertEquals(NoteSummary(id, "Plan", 4, folder = "Projects"), s.list().single())
        assertEquals("p", s.body(id))
        assertEquals(0, summarized)

        // Only the case changes: done through a temporary name, since FAT can't tell them apart.
        assertEquals("PROJECTS", s.renameFolder("Projects", "PROJECTS"))
        assertEquals(listOf("PROJECTS"), rootDir.list()!!.toList())
        assertEquals("PROJECTS", s.folderOf(id))
        assertEquals("PROJECTS", s.renameFolder("PROJECTS", "PROJECTS"))
        assertThrows(NoSuchFolderException::class.java) { s.renameFolder("work", "x") }
    }

    @Test
    fun folderNamesAreCheckedAndTheReasonGiven() {
        val s = store()
        s.createFolder("Work")
        val top = s.note("Top")
        fun refused(name: String) = assertThrows(FolderNameException::class.java) { s.createFolder(name) }.reason

        assertEquals("Type a name for the folder", refused("   "))
        assertEquals("Folder names can't contain /", refused("a/b"))
        assertEquals("Folder names can't contain :", refused("Notes: old"))
        assertEquals("Folder names can't contain ?", refused("why?"))
        assertEquals("Folder names can't contain control characters", refused("a\u0001b"))
        assertEquals("Folder names can't start with a dot", refused(".hidden"))
        assertEquals("Folder names can't end with a dot", refused("etc."))
        assertEquals("That name is too long", refused("x".repeat(256)))
        assertEquals("There's already a folder called “Work”", refused(" work"))
        assertEquals("A note's directory already has that name", refused(top.value.uppercase()))
        assertEquals(listOf("Work"), s.folders())

        s.createFolder("Ideas")
        assertEquals("There's already a folder called “Work”", assertThrows(FolderNameException::class.java) { s.renameFolder("Ideas", "WORK") }.reason)
        assertEquals("Ideas", s.renameFolder("Ideas", "Ideas "))
        assertNull(FolderNames.problem("Café — 2026 (draft)", listOf("Work")))
        assertEquals("Folder names can't contain \\", FolderNames.problem("a\\b", emptyList()))
    }

    @Test
    fun movingANoteKeepsItsIdContentAndAssets() {
        for (canMove in listOf(true, false)) {
            tmp.root.listFiles()!!.forEach { it.deleteRecursively() }
            rootDir.mkdirs(); indexDir.mkdirs()
            val counting = CountingTree(JavaFileTree(rootDir, canMove))
            val s = store(counting)
            s.createFolder("Work")
            val id = s.note("Plan", body = "p")
            val asset = s.putAsset(id, ByteArray(5000) { it.toByte() }, "png")
            s.list()

            counting.reset()
            summarized = 0
            s.move(id, "Work")
            assertEquals(1, counting["move"])
            assertEquals("copied only when the tree can't move", !canMove, counting["createFile"] > 0)
            assertEquals(listOf("Work/${id.value}"), copiesOf(id))
            assertEquals(emptyList<String>(), hidden())
            assertEquals("Work", s.folderOf(id))
            assertEquals("p", s.body(id))
            assertArrayEquals(ByteArray(5000) { it.toByte() }, s.readAsset(id, asset))
            assertEquals(NoteSummary(id, "Plan", 4, folder = "Work"), s.list().single())
            assertEquals("no note is read again for a move", 0, summarized)

            s.move(id, "Work")
            s.move(id, null)
            assertEquals(listOf(id.value), copiesOf(id))
            assertEquals(NoteSummary(id, "Plan", 4), store().list().single())
            assertThrows(NoSuchFolderException::class.java) { s.move(id, "Nope") }
        }
    }

    @Test
    fun aMoveOntoAnExistingNameIsRefused() {
        val s = store()
        s.createFolder("Work")
        val id = s.note("Plan")
        File(rootDir, "Work/${id.value}").mkdir()
        assertThrows(java.io.IOException::class.java) { s.move(id, "Work") }
        assertEquals(null, s.folderOf(id))
    }

    /** Whatever step of a copying move is interrupted, one complete copy of the note is listed. */
    @Test
    fun aCopyingMoveInterruptedAtEveryStepLeavesExactlyOneCompleteNote() {
        for (sticky in listOf(true, false)) {
            var crashAt = 0
            while (true) {
                tmp.root.listFiles()!!.forEach { it.deleteRecursively() }
                rootDir.mkdirs(); indexDir.mkdirs()
                val setup = store(JavaFileTree(rootDir))
                setup.createFolder("Work")
                setup.createFolder("Home")
                val id = setup.note("Plan", "Work", "the body")
                val one = setup.putAsset(id, ByteArray(300) { 1 }, "png")
                val two = setup.putAsset(id, ByteArray(700) { 2 }, "jpg")
                setup.list()

                val failing = CountingTree(JavaFileTree(rootDir, canMove = false), crashAt, sticky)
                try {
                    store(failing).move(id, "Home")
                } catch (_: SimulatedCrash) {
                }
                val failed = failing.mutations > crashAt

                val fresh = if (sticky) store(JavaFileTree(rootDir)) else store(failing)
                val label = "sticky=$sticky crash at $crashAt"
                assertEquals(label, listOf(id), fresh.list().map { it.id })
                assertEquals(label, 1, copiesOf(id).size)
                assertEquals(label, emptyList<String>(), hidden())
                assertEquals(label, "the body", fresh.body(id))
                assertArrayEquals(label, ByteArray(300) { 1 }, fresh.readAsset(id, one))
                assertArrayEquals(label, ByteArray(700) { 2 }, fresh.readAsset(id, two))
                assertEquals(label, setOf("note.json", "assets"), File(rootDir, copiesOf(id).single()).list()!!.toSet())
                if (!failed) {
                    assertEquals(label, "Home", fresh.folderOf(id))
                    break
                }
                crashAt++
                if (crashAt > 40) fail("the move never finished")
            }
            assertTrue("the copy path has many steps, finished at $crashAt", crashAt > 8)
        }
    }

    @Test
    fun leftoversOfAnInterruptedMoveAreSettledByTheNextListing() {
        val s = store()
        s.createFolder("Work")
        val id = s.note("Plan", body = "p")
        val dir = File(rootDir, id.value)

        // Crashed after hiding the original, before the checked copy took its name.
        dir.copyRecursively(File(rootDir, "Work/.${id.value}.moving"))
        dir.renameTo(File(rootDir, ".${id.value}.moved"))
        assertEquals(NoteSummary(id, "Plan", 4, folder = "Work"), store().list().single())
        assertEquals(emptyList<String>(), hidden())

        // Only the hidden original is left: it comes back where it was.
        File(rootDir, "Work/${id.value}").renameTo(File(rootDir, "Work/.${id.value}.moved"))
        assertEquals(NoteSummary(id, "Plan", 4, folder = "Work"), store().list().single())

        // A half-made copy without note.json next to nothing is left alone, hidden.
        File(rootDir, ".other.moving/assets").mkdirs()
        assertEquals(1, store().list().size)
        assertEquals(listOf(".other.moving"), hidden())
        assertTrue(File(rootDir, ".other.moving/assets").isDirectory)
    }

    @Test
    fun anIdFoundTwiceIsListedOnceAndTheFolderCopyIsKept() {
        val s = store()
        s.createFolder("Work")
        val id = s.note("Plan", body = "top")
        File(rootDir, id.value).copyRecursively(File(rootDir, "Work/${id.value}"))
        assertEquals(listOf(NoteSummary(id, "Plan", 4)), store().list())
        val deleted = store().deleteFolder("Work")
        assertEquals(FolderDeleted(moved = 0, removed = false), deleted)
        assertTrue(File(rootDir, "Work/${id.value}/note.json").isFile)
    }

    @Test
    fun aLookupBeforeAnyListingFindsNotesWithoutReadingThem() {
        val s = store()
        s.createFolder("Work")
        val top = s.note("Top", body = "t")
        val inWork = s.note("Plan", "Work", "w")

        summarized = 0
        val other = TreeNoteStore(JavaFileTree(rootDir), tmp.newFolder("other-index"), { summarized++; TestNotes.meta(it) }, clock = { now })
        assertEquals("w", other.body(inWork))
        assertEquals("t", other.body(top))
        assertEquals("Work", other.folderOf(inWork))
        assertEquals(0, summarized)
    }

    @Test
    fun refreshListsTheRootAndEachFolderOnceAndReadsOnlyChangedNotes() {
        val first = store()
        first.createFolder("Work")
        first.createFolder("Home")
        val top = first.note("Top")
        val work = (1..3).map { first.note("W$it", "Work") }
        first.note("H1", "Home")
        first.list()
        assertEquals(0, summarized)

        // A new process: the index answers, with one listing of the root and one per folder.
        tree.reset()
        val s = store()
        assertEquals(5, s.list().size)
        assertEquals(0, summarized)
        assertEquals(mapOf("list" to 3), tree.calls)

        // Another device changes a note inside a folder: only that note is read.
        val changed = File(rootDir, "Work/${work[1].value}")
        File(changed, "note.json").writeBytes(TestNotes.bytes("Synced", 99))
        changed.setLastModified(changed.lastModified() + 10_000)
        tree.reset()
        assertEquals(NoteSummary(work[1], "Synced", 99, folder = "Work"), s.list().first())
        assertEquals(1, summarized)
        assertEquals(mapOf("list" to 4, "read" to 1), tree.calls)

        // A top-level note changed the same way costs the same.
        val topDir = File(rootDir, top.value)
        File(topDir, "note.json").writeBytes(TestNotes.bytes("Top 2", 98))
        topDir.setLastModified(topDir.lastModified() + 10_000)
        tree.reset()
        summarized = 0
        s.list()
        assertEquals(1, summarized)
        assertEquals(mapOf("list" to 4, "read" to 1), tree.calls)

        // A folder made outside the app appears, and a note copied into it is read once.
        File(rootDir, "Synced/n1").mkdirs()
        File(rootDir, "Synced/n1/note.json").writeBytes(TestNotes.bytes("From afar", 5))
        summarized = 0
        assertEquals(NoteSummary(NoteId("n1"), "From afar", 5, folder = "Synced"), s.list().single { it.id.value == "n1" })
        assertEquals(listOf("Home", "Synced", "Work"), s.folders())
        assertEquals(1, summarized)
        summarized = 0
        tree.reset()
        s.list()
        assertEquals(0, summarized)
        assertEquals(mapOf("list" to 4), tree.calls)
    }
}
