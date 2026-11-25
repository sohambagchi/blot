package app.blot.core.store

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

class TreeNoteStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val rootDir by lazy { tmp.newFolder("notes") }
    private val indexDir by lazy { tmp.newFolder("index") }
    private val tree by lazy { CountingTree(JavaFileTree(rootDir)) }
    private var summarized = 0

    /** A minute ahead, so what the test just wrote is old enough for its time to be trusted. */
    private var now = System.currentTimeMillis() + 60_000

    private fun store(t: FileTree<File> = tree) =
        TreeNoteStore(t, indexDir, { summarized++; TestNotes.meta(it) }, clock = { now })

    private fun NoteStore.text(id: NoteId) = readNote(id) { TestNotes.parse(it) }

    private fun noteDir(id: NoteId) = File(rootDir, id.value)

    @Test
    fun createListAndRead() {
        val s = store()
        val a = s.create(TestNotes.bytes("Alpha", 100, "a"), NoteMeta("Alpha", 100))
        val b = s.create(TestNotes.bytes("Beta", 200, "b"), NoteMeta("Beta", 200))

        assertEquals(listOf(NoteSummary(b, "Beta", 200), NoteSummary(a, "Alpha", 100)), s.list())
        assertEquals(NoteRead(Triple("Alpha", 100L, "a"), fromBackup = false), s.text(a))
        assertEquals(setOf("note.json", "assets"), noteDir(a).list()!!.toSet())
        assertEquals(0, summarized)
    }

    @Test
    fun directoryIsNamedByStableIdNotTitle() {
        val s = store()
        val id = s.create(TestNotes.bytes("Old title", 1), NoteMeta("Old title", 1))
        assertTrue(id.value, Regex("\\d{8}-\\d{6}-[0-9a-f]{8}").matches(id.value))

        s.writeNote(id, TestNotes.bytes("New title", 2), NoteMeta("New title", 2))
        assertEquals(listOf(id.value), rootDir.list()!!.toList())
        assertEquals(listOf(NoteSummary(id, "New title", 2)), s.list())
    }

    @Test
    fun writeNoteReplacesContentAndLeavesNoTmp() {
        val s = store()
        val id = s.create(TestNotes.bytes("A", 1, "one"), NoteMeta("A", 1))
        s.writeNote(id, TestNotes.bytes("A", 2, "two"), NoteMeta("A", 2))
        s.writeNote(id, TestNotes.bytes("A", 3, "three"), NoteMeta("A", 3))
        assertEquals("three", s.text(id).value.third)
        assertEquals(setOf("note.json", "assets"), noteDir(id).list()!!.toSet())
    }

    @Test
    fun deleteRemovesTheDirectory() {
        val s = store()
        val keep = s.create(TestNotes.bytes("Keep", 1), NoteMeta("Keep", 1))
        val gone = s.create(TestNotes.bytes("Gone", 2), NoteMeta("Gone", 2))
        s.putAsset(gone, byteArrayOf(1, 2, 3), "png")
        s.delete(gone)

        assertFalse(noteDir(gone).exists())
        assertEquals(listOf(keep), s.list().map { it.id })
        assertThrows(NoSuchNoteException::class.java) { s.text(gone) }
        assertEquals(listOf(keep), store().list().map { it.id })
    }

    @Test
    fun readFallsBackToTmpWhenNoteJsonIsMissing() {
        val s = store()
        val id = s.create(TestNotes.bytes("A", 1, "old"), NoteMeta("A", 1))
        File(noteDir(id), "note.json").renameTo(File(noteDir(id), "note.json.tmp"))

        assertEquals(NoteRead(Triple("A", 1L, "old"), fromBackup = true), s.text(id))

        s.writeNote(id, TestNotes.bytes("A", 2, "new"), NoteMeta("A", 2))
        assertEquals(NoteRead(Triple("A", 2L, "new"), fromBackup = false), s.text(id))
        assertEquals(setOf("note.json", "assets"), noteDir(id).list()!!.toSet())
    }

    @Test
    fun readFallsBackToTmpWhenNoteJsonDoesNotParse() {
        val s = store()
        val id = s.create(TestNotes.bytes("A", 1), NoteMeta("A", 1))
        File(noteDir(id), "note.json.tmp").writeBytes(TestNotes.bytes("A", 1, "backup"))
        File(noteDir(id), "note.json").writeText("CORRUPT")

        assertEquals(NoteRead(Triple("A", 1L, "backup"), fromBackup = true), s.text(id))
    }

    @Test
    fun staleTmpNextToGoodNoteJsonIsIgnored() {
        val s = store()
        val id = s.create(TestNotes.bytes("A", 1, "good"), NoteMeta("A", 1))
        File(noteDir(id), "note.json.tmp").writeText("CORRUPT")

        assertEquals(NoteRead(Triple("A", 1L, "good"), fromBackup = false), s.text(id))

        s.writeNote(id, TestNotes.bytes("A", 2, "next"), NoteMeta("A", 2))
        assertEquals("next", s.text(id).value.third)
        assertEquals(setOf("note.json", "assets"), noteDir(id).list()!!.toSet())
    }

    @Test
    fun unreadableWhenNeitherCopyParses() {
        val s = store()
        val id = s.create(TestNotes.bytes("A", 1), NoteMeta("A", 1))
        File(noteDir(id), "note.json").writeText("CORRUPT")
        val e = assertThrows(NoteUnreadableException::class.java) { s.text(id) }
        assertEquals(1, e.suppressed.size)

        File(noteDir(id), "note.json.tmp").writeText("ALSO CORRUPT")
        assertEquals(2, assertThrows(NoteUnreadableException::class.java) { s.text(id) }.suppressed.size)
    }

    /** Whatever step of a save is interrupted, the note reads back as the old or the new version. */
    @Test
    fun crashAtEveryStepOfASaveLeavesAReadableNote() {
        val stepsPerSave = 4 // the tmp may exist already: write, delete, rename (+ createFile when not)
        for (crashAt in 0 until stepsPerSave) {
            tmp.root.listFiles()!!.forEach { it.deleteRecursively() }
            rootDir.mkdirs(); indexDir.mkdirs()
            val id = store().create(TestNotes.bytes("A", 1, "old"), NoteMeta("A", 1))

            val crashing = CountingTree(JavaFileTree(rootDir), crashAt)
            try {
                store(crashing).writeNote(id, TestNotes.bytes("A", 2, "new"), NoteMeta("A", 2))
            } catch (_: SimulatedCrash) {
            }

            val fresh = store(JavaFileTree(rootDir))
            val body = fresh.text(id).value.third
            assertTrue("crash at $crashAt read '$body'", body == "old" || body == "new")
            assertEquals(1, fresh.list().size)

            fresh.writeNote(id, TestNotes.bytes("A", 3, "after"), NoteMeta("A", 3))
            assertEquals(NoteRead(Triple("A", 3L, "after"), false), fresh.text(id))
            assertEquals(setOf("note.json", "assets"), noteDir(id).list()!!.toSet())
        }
    }

    @Test
    fun assetsAreNamedByHashAndDeduplicated() {
        val s = store()
        val id = s.create(TestNotes.bytes("A", 1), NoteMeta("A", 1))
        val png = byteArrayOf(9, 8, 7, 6)
        val sha = MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) }

        tree.reset()
        val name = s.putAsset(id, png, "png")
        assertEquals("$sha.png", name)
        assertEquals(1, tree["write"])

        tree.reset()
        assertEquals(name, s.putAsset(id, png.copyOf(), "png"))
        assertEquals(0, tree.mutations)

        val other = s.putAsset(id, byteArrayOf(1), "png")
        assertArrayEquals(png, s.readAsset(id, name))
        assertArrayEquals(byteArrayOf(1), s.readAsset(id, other))
        assertEquals(setOf(name, other), File(noteDir(id), "assets").list()!!.toSet())
        assertThrows(FileNotFoundException::class.java) { s.readAsset(id, "nope.png") }
        assertThrows(IllegalArgumentException::class.java) { s.putAsset(id, png, "../x") }
    }

    @Test
    fun putAssetRecreatesMissingAssetsDirectoryAndReplacesTornCopies() {
        val s = store()
        val id = s.create(TestNotes.bytes("A", 1), NoteMeta("A", 1))
        File(noteDir(id), "assets").deleteRecursively()
        val name = s.putAsset(id, byteArrayOf(1, 2, 3), "bin")

        File(noteDir(id), "assets/$name").writeBytes(byteArrayOf(1))
        File(noteDir(id), "assets/$name.tmp").writeBytes(byteArrayOf(5))
        assertEquals(name, s.putAsset(id, byteArrayOf(1, 2, 3), "bin"))
        assertArrayEquals(byteArrayOf(1, 2, 3), s.readAsset(id, name))
    }

    @Test
    fun garbageCollectionDeletesOnlyUnreferencedAssets() {
        val s = store()
        val id = s.create(TestNotes.bytes("A", 1), NoteMeta("A", 1))
        val keep = s.putAsset(id, byteArrayOf(1), "png")
        val drop = s.putAsset(id, byteArrayOf(2), "png")
        File(noteDir(id), "assets/leftover.png.tmp").writeBytes(byteArrayOf(3))

        assertEquals(setOf(drop, "leftover.png.tmp"), s.deleteUnreferencedAssets(id, setOf(keep)).toSet())
        assertEquals(listOf(keep), File(noteDir(id), "assets").list()!!.toList())
        assertEquals(emptyList<String>(), s.deleteUnreferencedAssets(id, setOf(keep)))
    }

    @Test
    fun indexRefreshReadsOnlyChangedNotes() {
        val first = store()
        val ids = (1..5).map { first.create(TestNotes.bytes("N$it", it.toLong()), NoteMeta("N$it", it.toLong())) }
        first.list() // adopts the directory times of the notes it just wrote
        assertEquals(0, summarized)

        // A new process: the index file alone answers, with one listing of the root.
        tree.reset()
        val s = store()
        assertEquals(5, s.list().size)
        assertEquals(0, summarized)
        assertEquals(mapOf("list" to 1), tree.calls)

        // Another device changes one note (sync tools write a temp file and rename it in).
        val changed = noteDir(ids[2])
        File(changed, "incoming").writeBytes(TestNotes.bytes("Synced", 99))
        File(changed, "note.json").delete()
        File(changed, "incoming").renameTo(File(changed, "note.json"))
        changed.setLastModified(changed.lastModified() + 10_000)
        tree.reset()
        val after = s.list()
        assertEquals(1, summarized)
        assertEquals(mapOf("list" to 2, "read" to 1), tree.calls)
        assertEquals(NoteSummary(ids[2], "Synced", 99), after.first())

        // An added note is summarized, a removed one disappears.
        val added = File(rootDir, "copied-in").apply { mkdir() }
        File(added, "note.json").writeBytes(TestNotes.bytes("Copied", 50))
        noteDir(ids[0]).deleteRecursively()
        summarized = 0
        val again = s.list()
        assertEquals(1, summarized)
        assertEquals(5, again.size)
        assertTrue(again.any { it.id == NoteId("copied-in") && it.title == "Copied" })
        assertFalse(again.any { it.id == ids[0] })

        summarized = 0
        assertEquals(again, s.rebuildIndex())
        assertEquals(5, summarized)
    }

    @Test
    fun changesWithinOneTimestampTickAreNotMissed() {
        now = System.currentTimeMillis()
        val s = store()
        val id = s.create(TestNotes.bytes("A", 1), NoteMeta("A", 1))
        s.list()
        val dir = noteDir(id)
        val tick = dir.lastModified()

        File(dir, "note.json").writeBytes(TestNotes.bytes("B", 2))
        dir.setLastModified(tick)
        summarized = 0
        assertEquals("B", s.list().single().title)
        assertEquals(1, summarized)

        now += 10_000
        s.list()
        s.list()
        assertEquals("once old enough, the time is trusted again", 2, summarized)
    }

    @Test
    fun directoriesThatAreNotNotesAreFoldersOrSkipped() {
        val s = store()
        s.create(TestNotes.bytes("A", 1), NoteMeta("A", 1))
        File(rootDir, ".stfolder").mkdir()
        File(rootDir, "Pictures").mkdir()
        File(rootDir, "stray.txt").writeText("x")

        assertEquals(1, s.list().size)
        assertEquals(listOf("Pictures"), s.folders())
        tree.reset()
        assertEquals(1, s.list().size)
        assertEquals("the root, then the folder", mapOf("list" to 2), tree.calls)
    }

    @Test
    fun damagedNotesAreListed() {
        val dir = File(rootDir, "broken").apply { mkdir() }
        File(dir, "note.json").writeText("CORRUPT")
        val listed = store().list().single()
        assertEquals(NoteId("broken"), listed.id)
        assertTrue(listed.damaged)
    }

    @Test
    fun indexIsRebuiltWhenCorruptOrFromAnotherRoot() {
        store().create(TestNotes.bytes("A", 1), NoteMeta("A", 1))
        store().list()
        val indexFile = File(indexDir, TreeNoteStore.INDEX_FILE)

        indexFile.writeText("{not json")
        summarized = 0
        assertEquals(listOf("A"), store().list().map { it.title })
        assertEquals(1, summarized)

        val elsewhere = tmp.newFolder("other")
        assertEquals(emptyList<NoteSummary>(), store(JavaFileTree(elsewhere)).list())
        summarized = 0
        assertEquals(listOf("A"), store().list().map { it.title })
        assertEquals(1, summarized)
    }
}
