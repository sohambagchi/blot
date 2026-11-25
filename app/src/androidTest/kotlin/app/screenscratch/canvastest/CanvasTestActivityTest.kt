package app.screenscratch.canvastest

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.screenscratch.canvas.FrontBufferContent
import app.screenscratch.canvas.Tool
import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasTestActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun launch(): ActivityScenario<CanvasTestActivity> {
        val intent = Intent(instrumentation.targetContext, CanvasTestActivity::class.java)
            .putExtra(CanvasTestActivity.EXTRA_FRESH, true)
        val scenario = ActivityScenario.launch<CanvasTestActivity>(intent)
        scenario.onActivity {
            it.controller.penOnly = true
            it.controller.tool = Tool.PEN
        }
        return scenario
    }

    /** The canvas's top-left corner on screen. */
    private fun origin(scenario: ActivityScenario<CanvasTestActivity>): IntArray {
        instrumentation.waitForIdleSync()
        lateinit var o: IntArray
        scenario.onActivity { o = IntArray(2).also { a -> it.canvasView!!.getLocationOnScreen(a) } }
        return o
    }

    private fun waitForFrontBuffer(scenario: ActivityScenario<CanvasTestActivity>): Boolean {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = it.canvasView?.lowLatencyView != null }
            if (ready) return true
            Thread.sleep(50)
        }
        return false
    }

    private fun <T> read(scenario: ActivityScenario<CanvasTestActivity>, f: (CanvasTestActivity) -> T): T {
        instrumentation.waitForIdleSync()
        var v: T? = null
        scenario.onActivity { v = f(it) }
        @Suppress("UNCHECKED_CAST")
        return v as T
    }

    @Test
    fun strokeLandsInCanvasCoordinatesAfterPanAndZoom() {
        launch().use { s ->
            val o = origin(s)
            val start = read(s) { it.controller.viewport }

            fingerDrag(o, 400f, 400f, 520f, 460f)
            val panned = read(s) { it.controller.viewport }
            assertEquals(start.offsetX + 120f, panned.offsetX, 0.01f)
            assertEquals(start.offsetY + 60f, panned.offsetY, 0.01f)

            pinch(o, 600f, 400f, fromSpan = 200f, toSpan = 300f)
            val zoomed = read(s) { it.controller.viewport }
            // Finger moves are resampled to the frame time, which can overshoot a little.
            assertEquals(panned.zoom * 1.5f, zoomed.zoom, 0.1f)
            // The point under the fingers stayed put.
            assertEquals(panned.toCanvasX(600f), zoomed.toCanvasX(600f), 0.05f)
            assertEquals(panned.toCanvasY(400f), zoomed.toCanvasY(400f), 0.05f)

            val xs = FloatArray(STROKE_POINTS) { 300f + it * 10f }
            val ys = FloatArray(STROKE_POINTS) { 250f + it * 5f }
            stylusStroke(o, xs, ys)
            val note = read(s) { it.controller.note.value }
            val stroke = note.elements.single() as Stroke
            // DOWN and MOVEs are inked; the lifting sample is not.
            assertEquals(STROKE_POINTS - 1, stroke.points.size)
            assertEquals(Argb.BLACK, stroke.color)
            assertTrue(stroke.pressureReported)
            for ((i, p) in stroke.points.withIndex()) {
                assertEquals("x of $i", zoomed.toCanvasX(xs[i]), p.x, 1 / 32f + 1e-3f)
                assertEquals("y of $i", zoomed.toCanvasY(ys[i]), p.y, 1 / 32f + 1e-3f)
            }
            assertEquals(1, read(s) { it.controller.stats.strokes })
        }
    }

    @Test
    fun secondaryButtonCyclesToolsHoveringAndTouching() {
        launch().use { s ->
            val o = origin(s)
            val seen = ArrayList<Tool>()
            repeat(3) {
                // A hovering pen always jitters; identical positions may be dropped on the way.
                hover(o, 500f + it * 4, 300f, MotionEvent.BUTTON_STYLUS_SECONDARY)
                hover(o, 502f + it * 4, 301f, 0)
                seen += read(s) { it.controller.tool }
            }
            assertEquals(listOf(Tool.PAN, Tool.SELECT, Tool.PEN), seen)

            // Pressed while touching: the stroke goes on, the next one pans.
            stylusStroke(o, floatArrayOf(300f, 310f, 320f, 330f), floatArrayOf(300f, 300f, 300f, 300f),
                buttonsAt = { if (it == 2) MotionEvent.BUTTON_STYLUS_SECONDARY else 0 })
            assertEquals(Tool.PAN, read(s) { it.controller.tool })
            assertEquals(1, read(s) { it.controller.note.value.elements.size })
            val before = read(s) { it.controller.viewport }
            stylusStroke(o, floatArrayOf(300f, 350f, 400f), floatArrayOf(300f, 300f, 300f))
            assertEquals(1, read(s) { it.controller.note.value.elements.size })
            assertEquals(before.offsetX + 100f, read(s) { it.controller.viewport.offsetX }, 0.01f)
        }
    }

    @Test
    fun primaryButtonErasesAndUndoBringsItBack() {
        launch().use { s ->
            val o = origin(s)
            stylusStroke(o, FloatArray(10) { 300f + it * 20f }, FloatArray(10) { 300f })
            assertEquals(1, read(s) { it.controller.note.value.elements.size })
            stylusStroke(o, FloatArray(10) { 390f }, FloatArray(10) { 250f + it * 10f },
                buttonsAt = { MotionEvent.BUTTON_STYLUS_PRIMARY })
            assertEquals(0, read(s) { it.controller.note.value.elements.size })
            assertEquals(Tool.PEN, read(s) { it.controller.tool })
            s.onActivity { it.controller.undo() }
            assertEquals(1, read(s) { it.controller.note.value.elements.size })
        }
    }

    @Test
    fun cancelDropsTheStroke() {
        launch().use { s ->
            val o = origin(s)
            stylusStroke(o, floatArrayOf(300f, 320f, 340f), floatArrayOf(300f, 310f, 320f), cancel = true)
            assertEquals(0, read(s) { it.controller.note.value.elements.size })
            assertTrue(!read(s) { it.controller.canUndo })
        }
    }

    /**
     * Pans with a finger, then draws at once, many times, and logs the pen-down to first
     * front-buffer draw time for each front-buffer mode. Read the numbers from logcat (tag InkCanvas
     * and CanvasLatency); on an emulator `LowLatencyCanvasView` takes a different path from a device.
     */
    @Test
    fun measurePenDownAfterPan() {
        launch().use { s ->
            val o = origin(s)
            val front = waitForFrontBuffer(s)
            Log.i(LOG, "front buffer attached: $front")
            // Something to render: a page of strokes.
            s.onActivity { a -> a.controller.load(sampleNote(a.controller.note.value)) }
            for (mode in FrontBufferContent.entries) {
                s.onActivity { it.controller.frontBufferContent = mode }
                val ink = ArrayList<Float>()
                val dispatch = ArrayList<Float>()
                val present = ArrayList<Float>()
                val scene = ArrayList<Float>()
                repeat(LATENCY_RUNS) { i ->
                    fingerDrag(o, 400f, 400f, 400f + (if (i % 2 == 0) 40f else -40f), 400f)
                    stylusStroke(o, FloatArray(6) { 700f + it * 8f }, FloatArray(6) { 300f + i * 6f })
                    Thread.sleep(150)
                    instrumentation.waitForIdleSync()
                    ink += read(s) { it.controller.stats.penDownToInkMs }
                    dispatch += read(s) { it.controller.stats.penDownDispatchMs }
                    present += read(s) { it.controller.stats.penDownToPresentMs }
                    scene += read(s) { it.controller.stats.sceneRenderMs }
                }
                Log.i(LOG, "$mode pen-down→ink ms: ${ink.joinToString { "%.1f".format(it) }}")
                Log.i(LOG, "$mode dispatch ms: ${dispatch.joinToString { "%.1f".format(it) }}")
                Log.i(LOG, "$mode pen-down→display ms: ${present.joinToString { "%.1f".format(it) }}")
                Log.i(LOG, "$mode scene re-render ms: ${scene.joinToString { "%.1f".format(it) }}")
                if (front) assertTrue(ink.none { it.isNaN() })
            }
            val stats = read(s) { it.controller.stats }
            Log.i(LOG, "view draw %.2f ms for %d elements".format(stats.sceneDrawMs, stats.drawnElements))
        }
    }

    private fun sampleNote(base: Note): Note {
        val strokes = (0 until 300).map { k ->
            val row = k / 20
            val col = k % 20
            Stroke(
                "s$k",
                List(40) { i ->
                    app.screenscratch.core.model.StrokePoint(
                        20f + col * 50f + i,
                        20f + row * 40f + 10f * kotlin.math.sin(i / 4f),
                        0.4f + 0.4f * kotlin.math.sin(i / 10f),
                        timeMs = i * 4L,
                    )
                },
                Argb.BLACK,
                2.5f,
            )
        }
        return base.copy(elements = strokes)
    }

    // Injection.

    private fun stylusStroke(
        o: IntArray,
        xs: FloatArray,
        ys: FloatArray,
        buttonsAt: (Int) -> Int = { 0 },
        cancel: Boolean = false,
    ) {
        val down = SystemClock.uptimeMillis()
        val n = xs.size
        for (i in 0 until n) {
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                n - 1 -> if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val e = event(down, down + i * 4L, action, MotionEvent.TOOL_TYPE_STYLUS,
                listOf(o[0] + xs[i] to o[1] + ys[i]), pressure = 0.3f + 0.6f * i / n, buttons = buttonsAt(i))
            instrumentation.sendPointerSync(e)
        }
        instrumentation.waitForIdleSync()
    }

    private fun hover(o: IntArray, x: Float, y: Float, buttons: Int) {
        val t = SystemClock.uptimeMillis()
        val e = event(t, t, MotionEvent.ACTION_HOVER_MOVE, MotionEvent.TOOL_TYPE_STYLUS, listOf(o[0] + x to o[1] + y),
            pressure = 0f, buttons = buttons)
        instrumentation.uiAutomation.injectInputEvent(e, true)
        instrumentation.waitForIdleSync()
    }

    private fun fingerDrag(o: IntArray, x0: Float, y0: Float, x1: Float, y1: Float, steps: Int = 6) {
        val down = SystemClock.uptimeMillis()
        for (i in 0..steps) {
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                steps -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val t = i / steps.toFloat()
            instrumentation.sendPointerSync(
                event(down, down + i * 8L, action, MotionEvent.TOOL_TYPE_FINGER,
                    listOf(o[0] + x0 + (x1 - x0) * t to o[1] + y0 + (y1 - y0) * t)),
            )
        }
        instrumentation.waitForIdleSync()
    }

    private fun pinch(o: IntArray, cx: Float, cy: Float, fromSpan: Float, toSpan: Float, steps: Int = 5) {
        val down = SystemClock.uptimeMillis()
        fun at(span: Float) = listOf(o[0] + cx - span / 2 to o[1] + cy, o[0] + cx + span / 2 to o[1] + cy)
        val finger = MotionEvent.TOOL_TYPE_FINGER
        var t = down
        instrumentation.sendPointerSync(event(down, t, MotionEvent.ACTION_DOWN, finger, at(fromSpan).take(1)))
        instrumentation.sendPointerSync(event(down, t, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), finger, at(fromSpan)))
        for (i in 1..steps) {
            t += 8
            instrumentation.sendPointerSync(event(down, t, MotionEvent.ACTION_MOVE, finger, at(fromSpan + (toSpan - fromSpan) * i / steps)))
        }
        instrumentation.sendPointerSync(event(down, t, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), finger, at(toSpan)))
        instrumentation.sendPointerSync(event(down, t, MotionEvent.ACTION_UP, finger, at(toSpan).take(1)))
        instrumentation.waitForIdleSync()
    }

    private fun event(
        down: Long,
        time: Long,
        action: Int,
        toolType: Int,
        points: List<Pair<Float, Float>>,
        pressure: Float = 1f,
        buttons: Int = 0,
    ): MotionEvent {
        val props = Array(points.size) { i -> MotionEvent.PointerProperties().apply { id = i; this.toolType = toolType } }
        val coords = Array(points.size) { i ->
            MotionEvent.PointerCoords().apply {
                x = points[i].first
                y = points[i].second
                this.pressure = pressure
                size = 0.1f
            }
        }
        val source = if (toolType == MotionEvent.TOOL_TYPE_STYLUS) InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN
        else InputDevice.SOURCE_TOUCHSCREEN
        return MotionEvent.obtain(down, time, action, points.size, props, coords, 0, buttons, 1f, 1f, 0, 0, source, 0)
    }

    private companion object {
        const val STROKE_POINTS = 21
        const val LATENCY_RUNS = 10
        const val LOG = "CanvasLatency"
    }
}
