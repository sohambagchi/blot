package app.blot.core.store

import java.io.IOException

/** A folder name was refused; [reason] says why, in words for the user. */
class FolderNameException(val reason: String) : IOException(reason)

/** No folder of that name is in the notes folder. */
class NoSuchFolderException(name: String) : IOException("no such folder: $name")

/**
 * The rules for folder names. A folder is a directory named exactly as the user typed it (trimmed),
 * so the name must be one that SAF providers and FAT-formatted cards store unchanged.
 */
object FolderNames {
    /** UTF-8 bytes; ext4 and most providers cap a name at 255. */
    const val MAX_BYTES = 255

    private const val FORBIDDEN = "/\\:*?\"<>|"

    /**
     * Returns [name] trimmed if it can name a new folder, or throws [FolderNameException].
     * [folders] are the existing folders, compared ignoring case because FAT and the emulated
     * storage of Android ignore it; [renaming] is the folder being renamed, which may keep its
     * name in another case. [others] are the other directory names at the top level, i.e. notes.
     */
    fun clean(name: String, folders: Collection<String>, renaming: String? = null, others: Collection<String> = emptyList()): String {
        val n = name.trim()
        fun refuse(reason: String): Nothing = throw FolderNameException(reason)
        if (n.isEmpty()) refuse("Type a name for the folder")
        n.firstOrNull { it in FORBIDDEN }?.let { refuse("Folder names can't contain $it") }
        if (n.any { it.code < 0x20 || it.code == 0x7f }) refuse("Folder names can't contain control characters")
        if (n.startsWith('.')) refuse("Folder names can't start with a dot")
        if (n.endsWith('.')) refuse("Folder names can't end with a dot")
        if (n.toByteArray().size > MAX_BYTES) refuse("That name is too long")
        folders.firstOrNull { it.equals(n, ignoreCase = true) && it != renaming }?.let { refuse("There's already a folder called “$it”") }
        if (others.any { it.equals(n, ignoreCase = true) }) refuse("A note's directory already has that name")
        return n
    }

    /** [clean]'s reason for refusing [name], or null if it is acceptable. */
    fun problem(name: String, folders: Collection<String>, renaming: String? = null): String? = try {
        clean(name, folders, renaming)
        null
    } catch (e: FolderNameException) {
        e.reason
    }
}
