package app.blot.core.store

import java.io.IOException

/** Note content used by the tests: `title|modified|body`. "CORRUPT" doesn't parse. */
object TestNotes {
    fun bytes(title: String, modified: Long, body: String = ""): ByteArray = "$title|$modified|$body".toByteArray()

    fun parse(bytes: ByteArray): Triple<String, Long, String> {
        val parts = String(bytes).split('|', limit = 3)
        require(parts.size == 3) { "corrupt note" }
        return Triple(parts[0], parts[1].toLong(), parts[2])
    }

    fun meta(bytes: ByteArray): NoteMeta = parse(bytes).let { NoteMeta(it.first, it.second) }
}

class SimulatedCrash : IOException("simulated crash")

/** Counts calls, and can throw [SimulatedCrash] in place of the mutation numbered [crashAt] (0-based). */
class CountingTree<R>(private val inner: FileTree<R>, var crashAt: Int = -1) : FileTree<R> {
    val calls = HashMap<String, Int>()
    var mutations = 0
        private set

    private fun count(op: String) {
        calls[op] = (calls[op] ?: 0) + 1
    }

    private fun mutate(op: String) {
        count(op)
        if (mutations++ == crashAt) throw SimulatedCrash()
    }

    fun reset() {
        calls.clear()
        mutations = 0
    }

    operator fun get(op: String): Int = calls[op] ?: 0

    override val root: R get() = inner.root
    override val rootKey: String get() = inner.rootKey
    override fun list(dir: R) = inner.list(dir).also { count("list") }
    override fun read(file: R) = inner.read(file).also { count("read") }
    override fun write(file: R, bytes: ByteArray) {
        try {
            mutate("write")
        } catch (e: SimulatedCrash) {
            inner.write(file, bytes.copyOf(bytes.size / 2)) // a torn write
            throw e
        }
        inner.write(file, bytes)
    }
    override fun createFile(dir: R, name: String): R { mutate("createFile"); return inner.createFile(dir, name) }
    override fun createDirectory(dir: R, name: String): R { mutate("createDirectory"); return inner.createDirectory(dir, name) }
    override fun rename(ref: R, newName: String): R { mutate("rename"); return inner.rename(ref, newName) }
    override fun delete(ref: R) { mutate("delete"); inner.delete(ref) }
}
