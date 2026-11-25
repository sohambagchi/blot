package app.screenscratch.core.store

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileNotFoundException
import java.io.IOException

class JavaFileTreeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun operationsKeepTheSafContract() {
        val tree = JavaFileTree(tmp.root)
        val dir = tree.createDirectory(tree.root, "d")
        val f = tree.createFile(dir, "a")
        tree.write(f, byteArrayOf(1, 2, 3))
        tree.write(f, byteArrayOf(4))
        assertArrayEquals(byteArrayOf(4), tree.read(f))

        val entry = tree.list(dir).single()
        assertEquals("a", entry.name)
        assertEquals(1L, entry.size)
        assertEquals(false, entry.isDirectory)
        assertEquals(true, tree.list(tree.root).single().isDirectory)

        tree.createFile(dir, "b")
        assertThrows(IOException::class.java) { tree.createFile(dir, "b") }
        assertThrows(IOException::class.java) { tree.createDirectory(tree.root, "d") }
        assertThrows(IOException::class.java) { tree.rename(f, "b") }
        val renamed = tree.rename(f, "c")
        assertThrows(FileNotFoundException::class.java) { tree.write(f, byteArrayOf()) }
        assertArrayEquals(byteArrayOf(4), tree.read(renamed))

        tree.delete(dir)
        assertEquals(emptyList<TreeEntry<*>>(), tree.list(tree.root))
        assertThrows(FileNotFoundException::class.java) { tree.delete(dir) }
    }

    @Test
    fun miniJsonRoundTrips() {
        val value = mapOf(
            "s" to "quote \" slash \\ newline \n tab \t ctrl \u0001 é 🖊",
            "n" to -42L,
            "big" to 1_764_000_000_000L,
            "b" to true,
            "z" to null,
            "l" to listOf(1L, "x", emptyMap<String, Any?>(), emptyList<Any?>()),
        )
        assertEquals(value, MiniJson.parse(MiniJson.write(value).toString()))
        assertEquals(mapOf("a" to listOf(1L)), MiniJson.parse(" { \"a\" : [ 1 ] } "))
        assertThrows(IllegalArgumentException::class.java) { MiniJson.parse("{\"a\":1") }
        assertThrows(IllegalArgumentException::class.java) { MiniJson.parse("[1] x") }
    }
}
