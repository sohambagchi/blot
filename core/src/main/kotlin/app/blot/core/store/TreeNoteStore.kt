package app.blot.core.store

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
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
 * Layout under the root: `<id>/note.json`, `<id>/assets/<sha-256>.<ext>`, and the same inside
 * folders: `<folder>/<id>/...`. A top-level directory holding `note.json` or `note.json.tmp` is
 * a note; any other top-level directory is a folder. Inside a folder, directories without either
 * file are ignored, and folders don't nest. Names starting with "." (e.g. Syncthing's
 * `.stfolder`) are ignored everywhere. An id found twice is listed once, the top-level copy first.
 *
 * Saving: write `note.json.tmp`, delete `note.json`, rename the tmp to `note.json`. None of this
 * is atomic over SAF, but at every point a complete copy exists in one of the two files, and
 * [readNote] tries `note.json` first, then the tmp. A crash between writing the tmp and deleting
 * `note.json` keeps the previous version, because a tmp next to a parseable `note.json` is
 * treated as stale (it may have been synced from another device).
 *
 * Moving when [FileTree.move] can't: copy into a hidden `.<id>.moving` beside the destination,
 * check the copy against the original, rename the original to a hidden `.<id>.moved`, rename the
 * copy to `<id>`, then delete the original. [list] settles a move interrupted at any step: with
 * the note visible, the hidden leftovers go; without it, the checked copy (or, failing that, the
 * original) takes the note's name back.
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

    private class Place<R>(val folder: String?, val ref: R)

    /** Where each note was in the latest scan, by id. Null until first needed, and after refs went stale. */
    private var places: HashMap<String, Place<R>>? = null

    /** Folder directories by name, from the same scan as [places]. */
    private var folderDirs = HashMap<String, R>()

    override fun create(content: ByteArray, meta: NoteMeta, folder: String?): NoteId = synchronized(lock) {
        val known = places()
        val parent = parent(folder)
        var id: NoteId
        do id = newId() while (id.value in known || id.value in folderDirs)
        val dir = tree.createDirectory(parent, id.value)
        known[id.value] = Place(folder, dir)
        // note.json first: an empty top-level directory reads as a folder.
        save(dir, NoteFiles(null, null, null), content)
        tree.createDirectory(dir, ASSETS)
        index.entries[LibraryIndex.key(folder, id.value)] =
            LibraryIndex.Entry(null, NoteSummary(id, meta.title, meta.modified, folder = folder))
        index.save()
        id
    }

    override fun list(): List<NoteSummary> = synchronized(lock) { refresh() }

    override fun rebuildIndex(): List<NoteSummary> = synchronized(lock) {
        index.entries.clear()
        refresh()
    }

    override fun folders(): List<String> = synchronized(lock) {
        places()
        folderDirs.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    override fun folderOf(id: NoteId): String? = synchronized(lock) { place(id).folder }

    override fun <T> readNote(id: NoteId, parse: (ByteArray) -> T): NoteRead<T> = synchronized(lock) {
        read(id, noteFiles(place(id).ref), parse)
    }

    override fun writeNote(id: NoteId, content: ByteArray, meta: NoteMeta): Unit = synchronized(lock) {
        val p = place(id)
        save(p.ref, noteFiles(p.ref), content)
        index.entries[LibraryIndex.key(p.folder, id.value)] =
            LibraryIndex.Entry(null, NoteSummary(id, meta.title, meta.modified, folder = p.folder))
        index.save()
    }

    override fun delete(id: NoteId): Unit = synchronized(lock) {
        val p = place(id)
        tree.delete(p.ref)
        places?.remove(id.value)
        index.entries.remove(LibraryIndex.key(p.folder, id.value))
        index.save()
    }

    override fun createFolder(name: String): String = synchronized(lock) {
        refresh()
        val clean = FolderNames.clean(name, folderDirs.keys, others = topLevelNotes())
        folderDirs[clean] = tree.createDirectory(tree.root, clean)
        index.entries[clean] = LibraryIndex.Entry(null, null)
        index.save()
        clean
    }

    override fun renameFolder(name: String, newName: String): String = synchronized(lock) {
        refresh()
        val dir = folderDirs[name] ?: throw NoSuchFolderException(name)
        val clean = FolderNames.clean(newName, folderDirs.keys, renaming = name, others = topLevelNotes())
        if (clean == name) return name
        if (clean.equals(name, ignoreCase = true)) {
            // A case-insensitive provider would see the new name as taken and pick "Name (1)".
            tree.rename(tree.rename(dir, "$clean~%08x".format(random.nextInt())), clean)
        } else {
            tree.rename(dir, clean)
        }
        val prefix = "$name/"
        for (key in index.entries.keys.filter { it == name || it.startsWith(prefix) }) {
            val e = index.entries.remove(key)!!
            index.entries[if (key == name) clean else "$clean/${key.removePrefix(prefix)}"] =
                e.copy(summary = e.summary?.copy(folder = clean))
        }
        index.save()
        // Refs below a renamed directory may contain its name (SAF document ids are often paths).
        places = null
        clean
    }

    override fun deleteFolder(name: String): FolderDeleted = synchronized(lock) {
        refresh()
        val dir = folderDirs[name] ?: throw NoSuchFolderException(name)
        val ids = places!!.filterValues { it.folder == name }.keys.sorted()
        for (id in ids) moveNote(NoteId(id), null)
        // Anything but hidden files may be the user's, or a second copy of a note's id.
        val removed = tree.list(dir).all { it.name.startsWith(".") && !it.isDirectory }
        if (removed) {
            tree.delete(dir)
            folderDirs.remove(name)
            index.entries.keys.removeAll { it == name || it.startsWith("$name/") }
            index.save()
        }
        FolderDeleted(ids.size, removed)
    }

    override fun move(id: NoteId, folder: String?): Unit = synchronized(lock) { moveNote(id, folder) }

    override fun putAsset(id: NoteId, bytes: ByteArray, ext: String): String = synchronized(lock) {
        require(EXT.matches(ext)) { "bad extension: $ext" }
        val name = sha256Hex(bytes) + "." + ext
        val dir = place(id).ref
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
        val assets = noteFiles(place(id).ref).assets ?: throw FileNotFoundException("no assets in $id")
        val entry = tree.list(assets.ref).firstOrNull { it.name == name && !it.isDirectory }
            ?: throw FileNotFoundException("no asset $name in $id")
        tree.read(entry.ref)
    }

    override fun deleteUnreferencedAssets(id: NoteId, referenced: Set<String>): List<String> = synchronized(lock) {
        val assets = noteFiles(place(id).ref).assets ?: return emptyList()
        tree.list(assets.ref).filter { it.name !in referenced }.map {
            tree.delete(it.ref)
            it.name
        }
    }

    private class NoteFiles<R>(val json: TreeEntry<R>?, val tmp: TreeEntry<R>?, val assets: TreeEntry<R>?) {
        val isNote get() = json != null || tmp != null

        companion object {
            fun <R> of(children: List<TreeEntry<R>>) = NoteFiles(
                children.firstOrNull { it.name == NOTE && !it.isDirectory },
                children.firstOrNull { it.name == NOTE_TMP && !it.isDirectory },
                children.firstOrNull { it.name == ASSETS && it.isDirectory },
            )
        }
    }

    private fun noteFiles(dir: R): NoteFiles<R> = NoteFiles.of(tree.list(dir))

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

    private fun places(): HashMap<String, Place<R>> = places ?: locate()

    private fun place(id: NoteId): Place<R> {
        places?.get(id.value)?.let { return it }
        locate()[id.value]?.let { return it }
        // Perhaps hidden by an interrupted move, which only a full listing settles.
        refresh()
        return places!![id.value] ?: throw NoSuchNoteException(id)
    }

    /**
     * Finds every note and folder as [refresh] would, without reading any note: the root's
     * directories the index knows as notes aren't listed, and every directory in a folder is
     * taken for a note. For lookups, which then fail on a directory that isn't one.
     */
    private fun locate(): HashMap<String, Place<R>> {
        val found = HashMap<String, Place<R>>()
        val folders = HashMap<String, R>()
        val inFolders = ArrayList<Pair<String, List<TreeEntry<R>>>>()
        fun dirs(children: List<TreeEntry<R>>) = children.filter { it.isDirectory && !it.name.startsWith(".") }
        for (c in dirs(tree.list(tree.root))) {
            if (index.entries[c.name]?.summary != null) {
                found[c.name] = Place(null, c.ref)
                continue
            }
            val children = tree.list(c.ref)
            if (NoteFiles.of(children).isNote) {
                found[c.name] = Place(null, c.ref)
            } else {
                folders[c.name] = c.ref
                inFolders += c.name to dirs(children)
            }
        }
        for ((f, children) in inFolders.sortedBy { it.first }) {
            for (c in children) found.putIfAbsent(c.name, Place(f, c.ref))
        }
        places = found
        folderDirs = folders
        return found
    }

    private fun parent(folder: String?): R {
        places()
        return if (folder == null) tree.root else folderDirs[folder] ?: throw NoSuchFolderException(folder)
    }

    private fun topLevelNotes(): Set<String> = places().filterValues { it.folder == null }.keys

    private fun moveNote(id: NoteId, folder: String?) {
        val p = place(id)
        if (p.folder == folder) return
        val from = parent(p.folder)
        val to = parent(folder)
        val there = tree.list(to)
        if (there.any { it.name == id.value }) throw IOException("${folder ?: "The top level"} already holds a directory named $id")
        there.firstOrNull { it.name == staging(id.value) }?.let { tree.delete(it.ref) }
        val moved = try {
            tree.move(p.ref, from, to)
        } catch (e: IOException) {
            places = null
            tree.list(to).firstOrNull { it.name == id.value && it.isDirectory }?.ref
                ?: if (tree.list(from).any { it.name == id.value }) null else throw e
        }
        val ref = moved ?: copyAcross(id.value, p.ref, to)
        places?.set(id.value, Place(folder, ref))
        index.entries.remove(LibraryIndex.key(p.folder, id.value))?.let { e ->
            // A moved directory keeps its time; a copy is a new directory with its own.
            val time = if (moved != null) e.dirModified else null
            index.entries[LibraryIndex.key(folder, id.value)] = LibraryIndex.Entry(time, e.summary?.copy(folder = folder))
        }
        index.save()
    }

    /** Copies note directory [src] into [to] as described on the class; returns the copy's ref. */
    private fun copyAcross(id: String, src: R, to: R): R {
        val copy = tree.createDirectory(to, staging(id))
        val old = try {
            copyDir(src, copy)
            if (!sameTree(src, copy)) throw IOException("the copy of note $id doesn't match the original")
            tree.rename(src, moved(id))
        } catch (e: Exception) {
            discard(copy)
            throw e
        }
        val placed = try {
            tree.rename(copy, id)
        } catch (e: Exception) {
            places = null
            throw e
        }
        discard(old)
        return placed
    }

    /** Subdirectories first and `note.json` last, so a copy holding `note.json` is complete. */
    private fun copyDir(src: R, dst: R) {
        val children = tree.list(src)
        for (c in children.filter { it.isDirectory }) copyDir(c.ref, tree.createDirectory(dst, c.name))
        val files = children.filter { !it.isDirectory }.sortedBy { if (it.name == NOTE) 2 else if (it.name == NOTE_TMP) 1 else 0 }
        for (c in files) tree.write(tree.createFile(dst, c.name), tree.read(c.ref))
    }

    private fun sameTree(a: R, b: R): Boolean {
        val x = tree.list(a).associateBy { it.name }
        val y = tree.list(b).associateBy { it.name }
        if (x.keys != y.keys) return false
        return x.values.all { e ->
            val f = y.getValue(e.name)
            e.isDirectory == f.isDirectory && if (e.isDirectory) sameTree(e.ref, f.ref) else e.size == f.size
        }
    }

    private fun discard(ref: R) {
        try {
            tree.delete(ref)
        } catch (_: Exception) {
            // Hidden; a later list() settles it.
        }
    }

    private class Leftover<R>(val id: String, val isCopy: Boolean, val ref: R)

    /**
     * Lists the root, then each folder, once, and re-summarizes only directories whose modified
     * time differs from the index. A folder's own time says nothing about the notes inside it, so
     * every folder is listed. A note this store just saved has no recorded time; it adopts the
     * listed one without a re-read, so an outside change landing between that save and this
     * refresh goes unnoticed until the next.
     *
     * Times are coarse (Android 12's `File.lastModified()`, which the external-storage provider
     * reports, has whole seconds; FAT has two), so a second change inside the same tick keeps the
     * same time. As in git's "racy clean" rule, a time within [RACY_MS] of now is not trusted:
     * it is recorded as unknown, and the note is read again on the next refresh.
     */
    private fun refresh(settle: Boolean = true): List<NoteSummary> {
        val now = clock()
        val found = HashMap<String, Place<R>>()
        val folders = HashMap<String, R>()
        val listings = ArrayList<Pair<TreeEntry<R>, List<TreeEntry<R>>>>()
        val summaries = ArrayList<NoteSummary>()
        val seen = HashSet<String>()
        val leftovers = ArrayList<Leftover<R>>()
        var changed = false

        fun keep(key: String, folder: String?, c: TreeEntry<R>, e: LibraryIndex.Entry) {
            seen += key
            if (index.entries.put(key, e) != e) changed = true
            val summary = e.summary ?: return
            if (found.putIfAbsent(c.name, Place(folder, c.ref)) == null) summaries += summary.copy(folder = folder)
        }

        fun visible(children: List<TreeEntry<R>>): List<TreeEntry<R>> = children.filter { c ->
            if (!c.isDirectory) return@filter false
            if (!c.name.startsWith(".")) return@filter true
            LEFTOVER.matchEntire(c.name)?.let { leftovers += Leftover(it.groupValues[1], it.groupValues[2] == "moving", c.ref) }
            false
        }

        for (c in visible(tree.list(tree.root))) {
            val known = index.entries[c.name]
            val current = if (known?.summary != null) current(known, c, now) else null
            if (current != null) {
                keep(c.name, null, c, current)
                continue
            }
            val children = tree.list(c.ref)
            val files = NoteFiles.of(children)
            if (files.isNote) {
                keep(c.name, null, c, LibraryIndex.Entry(trusted(c.lastModified, now), summarizeDir(NoteId(c.name), files, c.lastModified)))
            } else {
                folders[c.name] = c.ref
                listings += c to children
                keep(c.name, null, c, LibraryIndex.Entry(null, null))
            }
        }
        for ((f, children) in listings.sortedBy { it.first.name }) {
            for (c in visible(children)) {
                val key = LibraryIndex.key(f.name, c.name)
                val e = current(index.entries[key], c, now) ?: LibraryIndex.Entry(
                    trusted(c.lastModified, now),
                    noteFiles(c.ref).takeIf { it.isNote }?.let { summarizeDir(NoteId(c.name), it, c.lastModified) },
                )
                keep(key, f.name, c, e)
            }
        }
        if (index.entries.keys.retainAll(seen)) changed = true
        places = found
        folderDirs = folders
        if (changed || index.dirty) index.save()
        if (settle && leftovers.isNotEmpty() && settleMoves(leftovers)) return refresh(settle = false)
        return summaries.sortedWith(compareByDescending<NoteSummary> { it.modified }.thenBy { it.id.value })
    }

    /** The index entry for [c] if it still holds, or null when the directory must be read again. */
    private fun current(known: LibraryIndex.Entry?, c: TreeEntry<R>, now: Long): LibraryIndex.Entry? {
        if (known == null || c.lastModified == TreeEntry.UNKNOWN_TIME) return null
        if (known.dirModified == c.lastModified) return known
        if (known.dirModified == null) return known.copy(dirModified = trusted(c.lastModified, now))
        return null
    }

    private fun trusted(t: Long, now: Long) =
        if (t == TreeEntry.UNKNOWN_TIME || now - t < RACY_MS) TreeEntry.UNKNOWN_TIME else t

    /** Finishes or undoes interrupted moves (see the class comment); true if anything changed. */
    private fun settleMoves(leftovers: List<Leftover<R>>): Boolean {
        var acted = false
        for ((id, group) in leftovers.groupBy { it.id }) {
            if (id in places!!) {
                group.forEach { discard(it.ref) }
                acted = true
                continue
            }
            val copies = group.filter { it.isCopy }
            val originals = group.filter { !it.isCopy }
            // A copy exists beside a hidden original only once it was checked.
            val winner = (if (originals.isNotEmpty()) copies.firstOrNull() else null)
                ?: originals.firstOrNull()
                ?: copies.firstOrNull { noteFiles(it.ref).json != null }
                ?: continue
            try {
                tree.rename(winner.ref, id)
            } catch (_: IOException) {
                continue
            }
            group.filter { it !== winner }.forEach { discard(it.ref) }
            acted = true
        }
        return acted
    }

    private fun summarizeDir(id: NoteId, files: NoteFiles<R>, dirTime: Long): NoteSummary = try {
        val meta = read(id, files, summarize).value
        NoteSummary(id, meta.title, meta.modified)
    } catch (e: NoteUnreadableException) {
        NoteSummary(id, id.value, dirTime, damaged = true)
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
        private val LEFTOVER = Regex("""\.(.+)\.(moving|moved)""")

        private fun staging(id: String) = ".$id.moving"
        private fun moved(id: String) = ".$id.moved"

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
