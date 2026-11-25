package app.blot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.blot.canvas.FrontBufferContent
import app.blot.canvas.InkCanvasView
import app.blot.canvas.InkExclusion
import app.blot.canvas.CanvasController
import app.blot.core.model.format.NoteJson
import app.blot.core.store.NoteMeta
import app.blot.core.store.TreeNoteStore
import app.blot.shell.AppSettings
import app.blot.shell.Screen
import app.blot.storage.NoteFolder
import app.blot.storage.TestFolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A stylus stroke across the open colour panel, read back from the screen while the pen is still
 * down: the front buffer's ink must stop at the panel's edge, in both front-buffer modes, and the
 * committed stroke must stay behind the panel after pen-up.
 */
@RunWith(AndroidJUnit4::class)
class InkOverChromeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val shots = File(context.getExternalFilesDir(null), "ink-over-chrome").apply { mkdirs() }

    @Before
    fun setUp() {
        val folder = NoteFolder(context)
        folder.forget()
        context.getSharedPreferences(AppSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "library-index").deleteRecursively()
        folder.adopt(TestFolder.fresh(context))
        val store = TreeNoteStore(folder.tree()!!, File(context.cacheDir, "chrome-test-index"), { NoteMeta("", 0) })
        val note = CanvasController.newNote().copy(title = "Clip test")
        store.create(NoteJson.encode(note).encodeToByteArray(), NoteMeta(note.title, note.modified.toEpochMilli()))
    }

    @After
    fun tearDown() = NoteFolder(context).forget()

    @Test
    fun inkOnlyBufferStopsAtThePanel() = strokeAcrossThePanel(FrontBufferContent.INK_ONLY, "ink-only")

    @Test
    fun wholeNoteBufferShowsThePanel() = strokeAcrossThePanel(FrontBufferContent.SCENE, "whole-note")

    /** Without exclusions the ink does cover the panel: the readback can see the bug this guards against. */
    @Test
    fun withoutExclusionsInkCoversThePanel() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            val view = openEditorWithPanel(s, FrontBufferContent.INK_ONLY)
            val panel = panelRect(s)
            // Chrome reports again only when it moves, so this holds for the stroke.
            s.onActivity { controller(it).inkExclusions = emptyList() }
            val path = strokePath(view, panel)
            val before = baseline
            penDownAndMove(path)
            Thread.sleep(SETTLE_MS)
            val during = screenshot()
            save(during, "no-exclusions-during")
            penUp(path)
            val inside = changed(before, during, path, panel, inside = true)
            assertTrue("ink over the panel at $inside of the stroke's samples there", inside > 0)
        }
    }

    private fun strokeAcrossThePanel(mode: FrontBufferContent, name: String) {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            val view = openEditorWithPanel(s, mode)
            val panel = panelRect(s)
            val path = strokePath(view, panel)
            val before = baseline
            save(before, "$name-before")
            penDownAndMove(path)
            Thread.sleep(SETTLE_MS)
            val during = screenshot()
            save(during, "$name-during")
            val outside = changed(before, during, path, panel, inside = false)
            val inside = changed(before, during, path, panel, inside = true)
            assertTrue("no ink drawn beside the panel ($outside)", outside > 0)
            assertEquals("ink drawn over the panel mid-stroke", 0, inside)

            penUp(path)
            // Right after pen-up, while the buffer is being swapped for the committed stroke.
            for (i in 0 until 4) {
                val after = screenshot()
                if (i == 0) save(after, "$name-after-up")
                assertEquals("ink over the panel after pen-up (shot $i)", 0, changed(before, after, path, panel, inside = true))
            }
            assertEquals(1, read(s) { controller(it).note.value.elements.size })
        }
    }

    private fun controller(a: MainActivity) = (a.app.screen as Screen.Editor).session.controller

    private fun openEditorWithPanel(s: ActivityScenario<MainActivity>, mode: FrontBufferContent): InkCanvasView {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Open Clip test").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Open Clip test").performClick()
        val view = waitFor(s) { a -> find(a.window.decorView)?.takeIf { it.lowLatencyView != null } }
        s.onActivity { controller(it).frontBufferContent = mode }
        compose.waitForIdle()
        val closed = screenshot()
        compose.onNodeWithContentDescription("More colours and stroke width").performClick()
        compose.waitForIdle()
        val panel = waitFor(s) { a -> panelOrNull(a) }
        baseline = settledWithPanel(closed, panel, view)
        return view
    }

    private lateinit var baseline: Bitmap

    /** A screenshot once the open panel is on screen and has stopped changing. */
    private fun settledWithPanel(closed: Bitmap, panel: InkExclusion, view: View): Bitmap {
        val o = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(o) }
        val points = (1..9).flatMap { i -> (1..9).map { j -> (o[0] + panel.left + (panel.right - panel.left) * i / 10f).roundToInt() to (o[1] + panel.top + (panel.bottom - panel.top) * j / 10f).roundToInt() } }
        fun same(a: Bitmap, b: Bitmap) = points.none { (x, y) -> differs(a.getPixel(x, y), b.getPixel(x, y)) }
        val deadline = SystemClock.uptimeMillis() + 5_000
        var last = screenshot()
        while (SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(100)
            val now = screenshot()
            if (!same(closed, now) && same(last, now)) return now
            last = now
        }
        save(last, "panel-never-showed")
        throw AssertionError("the colour panel never reached the screen")
    }

    /** The colour panel's exclusion, in canvas pixels: the only one that wide and tall. */
    private fun panelOrNull(a: MainActivity): InkExclusion? {
        val d = a.resources.displayMetrics.density
        return controller(a).inkExclusions.singleOrNull { it.right - it.left >= 359 * d && it.bottom - it.top >= 200 * d }
    }

    private fun panelRect(s: ActivityScenario<MainActivity>): InkExclusion = read(s) { panelOrNull(it)!! }

    /** Screen points of a horizontal stroke through the panel's middle, from well left of it to well right. */
    private val canvasOrigin = IntArray(2)

    private fun strokePath(view: View, panel: InkExclusion): List<Pair<Float, Float>> {
        instrumentation.runOnMainSync { view.getLocationOnScreen(canvasOrigin) }
        val o = canvasOrigin
        val y = o[1] + (panel.top + panel.bottom) / 2f
        val x0 = o[0] + panel.left - 160f
        val x1 = o[0] + panel.right + 160f
        val n = 48
        return List(n) { i -> (x0 + (x1 - x0) * i / (n - 1)) to y }
    }

    private var downTime = 0L

    private fun penDownAndMove(path: List<Pair<Float, Float>>) {
        downTime = SystemClock.uptimeMillis()
        for ((i, p) in path.withIndex()) {
            inject(if (i == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE, p, downTime + i * 4L)
        }
        instrumentation.waitForIdleSync()
    }

    private fun penUp(path: List<Pair<Float, Float>>) {
        inject(MotionEvent.ACTION_UP, path.last(), downTime + path.size * 4L)
        instrumentation.waitForIdleSync()
    }

    private fun inject(action: Int, p: Pair<Float, Float>, time: Long) {
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            x = p.first
            y = p.second
            pressure = 0.8f
            size = 0.1f
        })
        val e = MotionEvent.obtain(downTime, time, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN, 0)
        instrumentation.uiAutomation.injectInputEvent(e, true)
        e.recycle()
    }

    /**
     * How many of the stroke's samples [inside] (or outside) the panel differ between the two
     * screenshots, looking at the sample and two pixels above and below. Inside, a margin keeps
     * clear of the panel's anti-aliased border.
     */
    private fun changed(a: Bitmap, b: Bitmap, path: List<Pair<Float, Float>>, panel: InkExclusion, inside: Boolean): Int {
        val margin = 6f
        var n = 0
        for ((x, y) in path) {
            val cx = x - canvasOrigin[0]
            val inPanel = cx > panel.left + margin && cx < panel.right - margin
            val clearOf = cx < panel.left - margin || cx > panel.right + margin
            if (if (inside) !inPanel else !clearOf) continue
            val xi = x.roundToInt()
            val yi = y.roundToInt()
            if ((-2..2).any { dy -> differs(a.getPixel(xi, yi + dy), b.getPixel(xi, yi + dy)) }) n++
        }
        return n
    }

    private fun differs(p: Int, q: Int): Boolean =
        abs(Color.red(p) - Color.red(q)) + abs(Color.green(p) - Color.green(q)) + abs(Color.blue(p) - Color.blue(q)) > 24

    private fun screenshot(): Bitmap = instrumentation.uiAutomation.takeScreenshot()!!.copy(Bitmap.Config.ARGB_8888, false)

    private fun save(b: Bitmap, name: String) {
        FileOutputStream(File(shots, "$name.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

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

    private fun <T : Any> waitFor(s: ActivityScenario<MainActivity>, timeoutMs: Long = 10_000, f: (MainActivity) -> T?): T {
        var found: T? = null
        compose.waitUntil(timeoutMs) {
            found = read(s, f)
            found != null
        }
        return found!!
    }

    private companion object {
        /** Long enough for the front buffer to draw the queued ink and reach the display. */
        const val SETTLE_MS = 300L
    }
}
