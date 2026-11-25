package app.screenscratch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.screenscratch.canvas.InkCanvasView
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.Stroke
import app.screenscratch.core.model.format.NoteJson
import app.screenscratch.core.store.NoteMeta
import app.screenscratch.core.store.TreeNoteStore
import app.screenscratch.shell.AppSettings
import app.screenscratch.shell.Screen
import app.screenscratch.storage.NoteFolder
import app.screenscratch.storage.TestFolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The whole app over a test documents provider: create, draw, list, reopen, relaunch, lose access. */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var tree: Uri

    @Before
    fun setUp() {
        val folder = NoteFolder(context)
        folder.forget()
        context.getSharedPreferences(AppSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        File(context.cacheDir, "thumbnails").deleteRecursively()
        File(context.filesDir, "library-index").deleteRecursively()
        tree = TestFolder.fresh(context)
        folder.adopt(tree)
    }

    @After
    fun tearDown() = NoteFolder(context).forget()

    @Test
    fun drawnNoteIsListedReopenedAndSurvivesARelaunch() {
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithContentDescription("New note").performClick()
        compose.onNodeWithContentDescription("New infinite canvas").performClick()
        val view = waitFor(scenario) { canvasView(it) }
        val xs = FloatArray(12) { view.width * 0.35f + it * 25f }
        val ys = FloatArray(12) { view.height * 0.55f + it * 6f }
        stylusStroke(view, xs, ys)
        waitUntil(scenario) { strokes(it) == 1 }

        compose.onNodeWithContentDescription("Back to library").performClick()
        waitUntil(scenario) { it.app.library.notes.singleOrNull()?.thumbnail != null }
        compose.onNodeWithContentDescription("Open Untitled").assertExists()

        compose.onNodeWithContentDescription("Open Untitled").performClick()
        waitFor(scenario) { canvasView(it) }
        assertEquals(1, read(scenario) { strokes(it) })
        compose.onNodeWithContentDescription("Back to library").performClick()
        waitUntil(scenario) { it.app.screen == Screen.Library }

        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitUntil(scenario) { it.app.library.notes.singleOrNull()?.thumbnail != null }
        compose.onNodeWithContentDescription("Open Untitled").performClick()
        waitFor(scenario) { canvasView(it) }
        val stroke = read(scenario) { (it.app.screen as Screen.Editor).session.controller.note.value.elements.single() as Stroke }
        assertEquals(11, stroke.points.size)
        scenario.close()

        // The note is in the folder as plain note.json.
        val store = TreeNoteStore(NoteFolder(context).tree()!!, File(context.cacheDir, "flow-test-index"), { NoteMeta("", 0) })
        val id = store.list().single().id
        val note: Note = store.readNote(id) { NoteJson.decode(it.decodeToString()) }.value
        assertEquals(listOf(stroke.id), note.elements.map { it.id })
    }

    @Test
    fun lostAccessShowsTheRegrantPrompt() {
        context.contentResolver.releasePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Grant access again").assertExists()
            compose.onNodeWithText("Choose another folder").assertExists()
        }
    }

    private fun strokes(a: MainActivity): Int =
        (a.app.screen as? Screen.Editor)?.session?.controller?.note?.value?.elements?.count { it is Stroke } ?: -1

    private fun canvasView(a: MainActivity): InkCanvasView? = find(a.window.decorView)

    private fun find(v: View): InkCanvasView? = when (v) {
        is InkCanvasView -> v.takeIf { it.isAttachedToWindow && it.width > 0 }
        is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
        else -> null
    }

    private fun <T> read(s: ActivityScenario<MainActivity>, f: (MainActivity) -> T): T {
        instrumentation.waitForIdleSync()
        var v: T? = null
        s.onActivity { v = f(it) }
        @Suppress("UNCHECKED_CAST")
        return v as T
    }

    // Compose's test clock only runs frames while the rule syncs, so poll through it.
    private fun <T : Any> waitFor(s: ActivityScenario<MainActivity>, timeoutMs: Long = 10_000, f: (MainActivity) -> T?): T {
        var found: T? = null
        compose.waitUntil(timeoutMs) {
            found = read(s, f)
            found != null
        }
        return found!!
    }

    private fun waitUntil(s: ActivityScenario<MainActivity>, timeoutMs: Long = 10_000, f: (MainActivity) -> Boolean) {
        compose.waitUntil(timeoutMs) { read(s, f) }
    }

    private fun stylusStroke(view: View, xs: FloatArray, ys: FloatArray) {
        val o = IntArray(2).also { view.getLocationOnScreen(it) }
        val down = SystemClock.uptimeMillis()
        val n = xs.size
        for (i in 0 until n) {
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                n - 1 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
            val coords = arrayOf(MotionEvent.PointerCoords().apply {
                x = o[0] + xs[i]
                y = o[1] + ys[i]
                pressure = 0.3f + 0.6f * i / n
                size = 0.1f
            })
            val e = MotionEvent.obtain(down, down + i * 4L, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN, 0)
            instrumentation.sendPointerSync(e)
        }
        instrumentation.waitForIdleSync()
    }
}
