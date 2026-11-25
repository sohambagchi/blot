package app.blot.core.store

/** One child of a directory, as returned by [FileTree.list]. */
data class TreeEntry<R>(
    val ref: R,
    val name: String,
    val isDirectory: Boolean,
    /** Bytes; 0 for directories. */
    val size: Long,
    /** Epoch milliseconds, or [UNKNOWN_TIME] when the backend doesn't report it. */
    val lastModified: Long,
) {
    companion object {
        const val UNKNOWN_TIME: Long = -1L
    }
}

/**
 * The few file operations the note store needs, shaped after what the Storage Access Framework
 * offers so the note rules can be written once and tested on the JVM.
 *
 * Refs ([R]) are opaque handles obtained from [root], [list] and the create/rename calls.
 *
 * Contract, which every implementation must keep:
 * - Nothing replaces an existing name. [createFile], [createDirectory] and [rename] onto a name
 *   that exists is a caller bug (SAF providers silently pick "name (1)" instead).
 * - [rename] may return a different ref; the old one is then invalid.
 * - [write] truncates and replaces the whole file, and has returned only once the bytes are on
 *   disk as far as the backend allows. It is not atomic.
 * - [delete] of a directory removes everything in it.
 * - Operations throw [java.io.IOException] (often [java.io.FileNotFoundException]) on failure.
 */
interface FileTree<R> {
    /** The directory the user chose. */
    val root: R

    /** Identifies [root] across app runs (a path or tree URI), so caches can tell roots apart. */
    val rootKey: String

    /** All children of [dir], with their metadata, in one backend call. */
    fun list(dir: R): List<TreeEntry<R>>

    fun read(file: R): ByteArray

    /** Replaces the contents of an existing file. */
    fun write(file: R, bytes: ByteArray)

    /** Creates an empty file named [name] in [dir]. */
    fun createFile(dir: R, name: String): R

    fun createDirectory(dir: R, name: String): R

    /** Renames [ref] within its directory and returns the ref to use from now on. */
    fun rename(ref: R, newName: String): R

    fun delete(ref: R)
}
