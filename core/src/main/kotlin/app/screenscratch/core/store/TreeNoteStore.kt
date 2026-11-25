package app.screenscratch.core.store

import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Random

/**
 * The one implementation of the note rules, over any [FileTree] (SAF on device, `java.io.File`
 * in tests).
 *
 * Layout under the root: `<id>/note.json`, `<id>/assets/<sha-256>.<ext>`. Directories whose name
 * starts with "." (e.g. Syncthing's `.stfolder`) are ignored, as are directories without a
 * `note.json` or `note.json.tmp`.
 *
 * Saving: write `note.json.tmp`, delete `note.json`, rename the tmp to `note.json`. None of this
 * is atomic over SAF, but at every point a complete copy exists in one of the two files, and
 * [readNote] tries `note.json` first, then the tmp. A crash between writing the tmp and deleting
 * `note.json` keeps the previous version, because a tmp next to a parseable `note.json` is
 * treated as stale (it may have been synced from another device).
 *
 * All methods are serialised on one lock; call them off the main thread.
 *
 * @param indexDir app-private directory for the library index file.
 * @param summarize extracts the title and modified time from `note.json` bytes; throws if they
 *   don't parse. Used only when the index needs refreshing.
 */
class TreeNoteStore<R : Any>(
    private val tree: FileTree<R>,
    indexDir: File,
    private val summarize: (ByteArray) -> NoteMeta,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = SecureRandom(),
) : NoteStore {
    private val lock = Any()
    private val index = LibraryIndex(File(indexDir, INDEX_FILE), tree.rootKey).also { it.load() }

    /** Note directories by name, from the latest listing of the root. Null until first needed. */
    private var dirs: MutableMap<String, R>? = null

    override fun create(content: ByteArray, meta: NoteMeta): NoteId = synchronized(lock) {
        val known = dirs ?: listRoot().let { dirs!! }
        var id: NoteId
        do id = newId() while (id.value in known)
        val dir = tree.createDirectory(tree.root, id.value)
        known[id.value] = dir
        tree.createDirectory(dir, ASSETS)
        save(dir, NoteFiles(null, null, null), content)
        index.entries[id.value] = LibraryIndex.Entry(null, NoteSummary(id, meta.title, meta.modified))
        index.save()
        id
    }

    override fun list(): List<NoteSummary> = synchronized(lock) { refresh() }

    override fun rebuildIndex(): List<NoteSummary> = synchronized(lock) {
        index.entries.clear()
        refresh()
    }

    override fun <T> readNote(id: NoteId, parse: (ByteArray) -> T): NoteRead<T> = synchronized(lock) {
        read(id, noteFiles(dirRef(id)), parse)
    }

    override fun writeNote(id: NoteId, content: ByteArray, meta: NoteMeta): Unit = synchronized(lock) {
        val dir = dirRef(id)
        save(dir, noteFiles(dir), content)
        index.entries[id.value] = LibraryIndex.Entry(null, NoteSummary(id, meta.title, meta.modified))
        index.save()
    }

    override fun delete(id: NoteId): Unit = synchronized(lock) {
        tree.delete(dirRef(id))
        dirs?.remove(id.value)
        index.entries.remove(id.value)
        index.save()
    }

    override fun putAsset(id: NoteId, bytes: ByteArray, ext: String): String = synchronized(lock) {
        require(EXT.matches(ext)) { "bad extension: $ext" }
        val name = sha256Hex(bytes) + "." + ext
        val dir = dirRef(id)
        val assets = noteFiles(dir).assets?.ref ?: tree.createDirectory(dir, ASSETS)
        val existing = tree.list(assets).associateBy { it.name }
        existing[name]?.let {
            // Assets only appear under their final name once complete, so a size match is enough.
            if (it.size == bytes.size.toLong()) return name
            tree.delete(it.ref)
        }
        val tmpName = "$name.tmp"
        val tmp = existing[tmpName]?.ref ?: tree.createFile(assets, tmpName)
        tree.write(tmp, bytes)
        tree.rename(tmp, name)
        name
    }

    override fun readAsset(id: NoteId, name: String): ByteArray = synchronized(lock) {
        val assets = noteFiles(dirRef(id)).assets ?: throw FileNotFoundException("no assets in $id")
        val entry = tree.list(assets.ref).firstOrNull { it.name == name && !it.isDirectory }
            ?: throw FileNotFoundException("no asset $name in $id")
        tree.read(entry.ref)
    }

    override fun deleteUnreferencedAssets(id: NoteId, referenced: Set<String>): List<String> = synchronized(lock) {
        val assets = noteFiles(dirRef(id)).assets ?: return emptyList()
        tree.list(assets.ref).filter { it.name !in referenced }.map {
            tree.delete(it.ref)
            it.name
        }
    }

    private class NoteFiles<R>(val json: TreeEntry<R>?, val tmp: TreeEntry<R>?, val assets: TreeEntry<R>?)

    private fun noteFiles(dir: R): NoteFiles<R> {
        val children = tree.list(dir)
        return NoteFiles(
            children.firstOrNull { it.name == NOTE && !it.isDirectory },
            children.firstOrNull { it.name == NOTE_TMP && !it.isDirectory },
            children.firstOrNull { it.name == ASSETS && it.isDirectory },
        )
    }

    private fun save(dir: R, files: NoteFiles<R>, content: ByteArray) {
        val tmp = files.tmp?.ref ?: tree.createFile(dir, NOTE_TMP)
        tree.write(tmp, content)
        files.json?.let { tree.delete(it.ref) }
        tree.rename(tmp, NOTE)
    }

    private fun <T> read(id: NoteId, files: NoteFiles<R>, parse: (ByteArray) -> T): NoteRead<T> {
        val failure = NoteUnreadableException(id)
        for ((entry, fromBackup) in listOf(files.json to false, files.tmp to true)) {
            if (entry == null) continue
            val bytes = tree.read(entry.ref)
            try {
                return NoteRead(parse(bytes), fromBackup)
            } catch (e: Exception) {
                failure.addSuppressed(e)
            }
        }
        throw failure
    }

    private fun listRoot(): List<TreeEntry<R>> {
        val children = tree.list(tree.root).filter { it.isDirectory && !it.name.startsWith(".") }
        dirs = children.associateTo(HashMap()) { it.name to it.ref }
        return children
    }

    private fun dirRef(id: NoteId): R {
        dirs?.get(id.value)?.let { return it }
        listRoot()
        return dirs!![id.value] ?: throw NoSuchNoteException(id)
    }

    /**
     * Re-summarizes only directories whose modified time differs from the index. A note this
     * store just saved has no recorded time; it adopts the listed one without a re-read, so an
     * outside change landing between that save and this refresh goes unnoticed until the next.
     *
     * Times are coarse (Android 12's `File.lastModified()`, which the external-storage provider
     * reports, has whole seconds; FAT has two), so a second change inside the same tick keeps the
     * same time. As in git's "racy clean" rule, a time within [RACY_MS] of now is not trusted:
     * it is recorded as unknown, and the note is read again on the next refresh.
     */
    private fun refresh(): List<NoteSummary> {
        val children = listRoot()
        val now = clock()
        fun trusted(t: Long) = if (t == TreeEntry.UNKNOWN_TIME || now - t < RACY_MS) TreeEntry.UNKNOWN_TIME else t
        var changed = false
        for (c in children) {
            val known = index.entries[c.name]
            if (known != null && c.lastModified != TreeEntry.UNKNOWN_TIME) {
                if (known.dirModified == c.lastModified) continue
                if (known.dirModified == null) {
                    index.entries[c.name] = known.copy(dirModified = trusted(c.lastModified))
                    changed = true
                    continue
                }
            }
            index.entries[c.name] = LibraryIndex.Entry(trusted(c.lastModified), summarizeDir(NoteId(c.name), c))
            changed = true
        }
        val names = children.mapTo(HashSet()) { it.name }
        if (index.entries.keys.retainAll(names)) changed = true
        if (changed || index.dirty) index.save()
        return index.entries.values.mapNotNull { it.summary }
            .sortedWith(compareByDescending<NoteSummary> { it.modified }.thenBy { it.id.value })
    }

    private fun summarizeDir(id: NoteId, dir: TreeEntry<R>): NoteSummary? {
        val files = noteFiles(dir.ref)
        if (files.json == null && files.tmp == null) return null
        return try {
            val meta = read(id, files, summarize).value
            NoteSummary(id, meta.title, meta.modified)
        } catch (e: NoteUnreadableException) {
            NoteSummary(id, id.value, dir.lastModified, damaged = true)
        }
    }

    private fun newId(): NoteId {
        val time = ID_TIME.format(Instant.ofEpochMilli(clock()))
        return NoteId("%s-%08x".format(time, random.nextInt()))
    }

    companion object {
        const val NOTE = "note.json"
        const val NOTE_TMP = "note.json.tmp"
        const val ASSETS = "assets"
        const val INDEX_FILE = "library-index.json"
        private const val RACY_MS = 3_000L

        private val EXT = Regex("[a-z0-9]{1,10}")
        private val ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

        private fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val hex = "0123456789abcdef"
            val out = CharArray(digest.size * 2)
            digest.forEachIndexed { i, b ->
                out[2 * i] = hex[(b.toInt() shr 4) and 0xf]
                out[2 * i + 1] = hex[b.toInt() and 0xf]
            }
            return String(out)
        }
    }
}
