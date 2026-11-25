package app.screenscratch.storage

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract.Document
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.screenscratch.core.store.NoteMeta
import app.screenscratch.core.store.TreeNoteStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Times listing ~200 note directories over SAF. Results go to logcat under [TAG]; the test only
 * fails if the listing is wrong. Compares the one-query listing with the per-getter pattern
 * `DocumentFile` uses (one query per property per file).
 */
@RunWith(AndroidJUnit4::class)
class SafListingSpeedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder = NoteFolder(context, context.getSharedPreferences("note_folder_test", Context.MODE_PRIVATE))

    @After
    fun tearDown() = folder.forget()

    @Test
    fun listTwoHundredNotes() {
        folder.adopt(TestFolder.fresh(context))
        val tree = checkNotNull(folder.tree())
        val indexDir = File(context.cacheDir, "saf-speed-index").apply { deleteRecursively() }
        val store = TreeNoteStore(tree, indexDir, TestNotes::meta)

        val createMs = timed {
            repeat(NOTES) { store.create(TestNotes.bytes("Note $it", it.toLong(), "x".repeat(2_000)), NoteMeta("Note $it", it.toLong())) }
        }
        Thread.sleep(3_100) // past the store's window for untrusted (too recent) directory times

        val listMs = (1..RUNS).map { timed { assertEquals(NOTES, tree.list(tree.root).size) } }
        val rebuildMs = timed { assertEquals(NOTES, store.rebuildIndex().size) }
        val warmMs = (1..RUNS).map { timed { assertEquals(NOTES, store.list().size) } }
        val justSaved = store.list().first().id
        store.writeNote(justSaved, TestNotes.bytes("Saved", 1_000), NoteMeta("Saved", 1_000))
        val afterSaveMs = (1..2).map { timed { store.list() } }
        val coldStoreMs = timed { assertEquals(NOTES, TreeNoteStore(tree, indexDir, TestNotes::meta).list().size) }
        val perGetterMs = timed { assertEquals(NOTES, perGetterListing(tree)) }

        val subSecond = tree.list(tree.root).count { it.lastModified % 1000 != 0L }
        Log.i(TAG, "directories whose modified time has sub-second digits: $subSecond of $NOTES")
        Log.i(TAG, "notes=$NOTES create total=${createMs}ms (${createMs / NOTES}ms/note)")
        Log.i(TAG, "tree.list(root) one query: median=${listMs.sorted()[RUNS / 2]}ms min=${listMs.min()}ms")
        Log.i(TAG, "store.rebuildIndex() (list + read every note): ${rebuildMs}ms")
        Log.i(TAG, "store.list() unchanged, warm: median=${warmMs.sorted()[RUNS / 2]}ms")
        Log.i(TAG, "store.list() twice right after a save: ${afterSaveMs}ms")
        Log.i(TAG, "store.list() new store from index file: ${coldStoreMs}ms")
        Log.i(TAG, "per-getter listing (DocumentFile pattern, 4 queries/file): ${perGetterMs}ms")
    }

    private fun perGetterListing(tree: SafFileTree): Int {
        val resolver = context.contentResolver
        val children = tree.list(tree.root).map { it.ref }
        for (uri in children) {
            for (column in listOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)) {
                queryOne(uri, column)
            }
        }
        return children.size
    }

    private fun queryOne(uri: Uri, column: String) {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)!!.use { it.moveToFirst() }
    }

    private inline fun timed(block: () -> Unit): Long {
        val start = SystemClock.elapsedRealtimeNanos()
        block()
        return (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
    }

    private companion object {
        const val TAG = "SafListingSpeed"
        const val NOTES = 200
        const val RUNS = 9
    }
}
