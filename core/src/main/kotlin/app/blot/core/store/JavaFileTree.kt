package app.blot.core.store

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * [FileTree] over a local directory. Used by the JVM tests and usable on device for app-private
 * storage. It refuses to overwrite names, like SAF, so logic tested here behaves the same there.
 *
 * @param canMove false makes [move] report that it can't, like a provider without
 *   `FLAG_SUPPORTS_MOVE`, so the copy fallback can be tested.
 */
class JavaFileTree(rootDir: File, private val canMove: Boolean = true) : FileTree<File> {
    override val root: File = rootDir.absoluteFile
    override val rootKey: String = "file:" + root.path

    override fun list(dir: File): List<TreeEntry<File>> {
        val children = dir.listFiles() ?: throw FileNotFoundException("not a directory: $dir")
        return children.map { f ->
            val isDir = f.isDirectory
            TreeEntry(f, f.name, isDir, if (isDir) 0L else f.length(), f.lastModified())
        }
    }

    override fun read(file: File): ByteArray = file.readBytes()

    override fun write(file: File, bytes: ByteArray) {
        if (!file.isFile) throw FileNotFoundException("no such file: $file")
        FileOutputStream(file).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
    }

    override fun createFile(dir: File, name: String): File {
        val f = child(dir, name)
        if (!f.createNewFile()) throw IOException("already exists: $f")
        return f
    }

    override fun createDirectory(dir: File, name: String): File {
        val f = child(dir, name)
        if (!f.mkdir()) throw IOException("cannot create directory: $f")
        return f
    }

    override fun rename(ref: File, newName: String): File {
        val target = child(ref.parentFile, newName)
        if (!ref.exists()) throw FileNotFoundException("no such file: $ref")
        if (target.exists()) throw IOException("already exists: $target")
        if (!ref.renameTo(target)) throw IOException("cannot rename $ref to $newName")
        return target
    }

    override fun delete(ref: File) {
        if (!ref.exists()) throw FileNotFoundException("no such file: $ref")
        if (!ref.deleteRecursively()) throw IOException("cannot delete: $ref")
    }

    override fun move(ref: File, from: File, to: File): File? {
        if (!canMove) return null
        if (ref.parentFile != from) throw FileNotFoundException("$ref is not in $from")
        val target = child(to, ref.name)
        if (!ref.exists()) throw FileNotFoundException("no such file: $ref")
        if (target.exists()) throw IOException("already exists: $target")
        if (!ref.renameTo(target)) throw IOException("cannot move $ref to $to")
        return target
    }

    private fun child(dir: File, name: String): File {
        require(name.isNotEmpty() && '/' !in name && name != "." && name != "..") { "bad name: $name" }
        return File(dir, name)
    }
}
