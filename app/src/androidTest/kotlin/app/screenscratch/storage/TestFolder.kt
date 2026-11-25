package app.screenscratch.storage

import android.content.Context
import android.net.Uri
import app.screenscratch.core.store.NoteMeta

/** Gets a fresh, empty tree from [TestDocumentsProvider], granted as the folder picker would. */
object TestFolder {
    private val grant = Uri.parse("content://app.screenscratch.test.grant")

    fun fresh(context: Context): Uri {
        val resolver = context.contentResolver
        resolver.call(grant, "reset", null, null)
        return Uri.parse(checkNotNull(resolver.call(grant, "grant", null, null)).getString("uri"))
    }
}

/** Note content for these tests: `title|modified|body`. */
object TestNotes {
    fun bytes(title: String, modified: Long, body: String = ""): ByteArray = "$title|$modified|$body".toByteArray()

    fun body(bytes: ByteArray): String {
        val parts = String(bytes).split('|', limit = 3)
        require(parts.size == 3) { "corrupt note" }
        return parts[2]
    }

    fun meta(bytes: ByteArray): NoteMeta {
        val parts = String(bytes).split('|', limit = 3)
        require(parts.size == 3) { "corrupt note" }
        return NoteMeta(parts[0], parts[1].toLong())
    }
}
