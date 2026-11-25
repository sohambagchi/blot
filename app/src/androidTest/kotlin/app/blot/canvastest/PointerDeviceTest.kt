package app.blot.canvastest

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.blot.canvas.Tool
import app.blot.canvas.ToolGestures
import app.blot.core.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The pen's side buttons, the pointer's rotate handle and the long-press menu, with injected
 * stylus events that carry `buttonState`, on the canvas test screen. Positions are view pixels.
 */
@RunWith(AndroidJUnit4::class)
class PointerDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun launch(): ActivityScenario<CanvasTestActivity> {
        val intent = Intent(instrumentation.targetContext, CanvasTestActivity::class.java)
            .putExtra(CanvasTestActivity.EXTRA_FRESH, true)
        val scenario = ActivityScenario.launch<CanvasTestActivity>(intent)
        scenario.onActivity {
            it.controller.penOnly = true
            it.controller.swapStylusButtons = false
            it.controller.tool = Tool.PEN
        }
        return scenario
    }

    private fun origin(s: ActivityScenario<CanvasTestActivity>): IntArray {
        instrumentation.waitForIdleSync()
        lateinit var o: IntArray
        s.onActivity { o = IntArray(2).also { a -> it.canvasView!!.getLocationOnScreen(a) } }
        return o
    }

    private fun <T> read(s: ActivityScenario<CanvasTestActivity>, f: (CanvasTestActivity) -> T): T {
        instrumentation.waitForIdleSync()
        var v: T? = null
        s.onActivity { v = f(it) }
        @Suppress("UNCHECKED_CAST")
        return v as T
    }

    @Test
    fun secondaryPressWhileHoveringSwitchesToThePointerForGood() {
        launch().use { s ->
            val o = origin(s)
            hover(o, 500f, 500f, MotionEvent.BUTTON_STYLUS_SECONDARY)
            hover(o, 505f, 500f, 0)
            assertEquals(Tool.SELECT, read(s) { it.controller.tool })
            // Still the pointer after release: a drag lassoes instead of drawing.
            stylus(o, FloatArray(10) { 300f + it * 10f }, FloatArray(10) { 300f + it * 4f })
            assertEquals(0, read(s) { it.controller.note.value.elements.size })
            assertEquals(Tool.SELECT, read(s) { it.controller.tool })
        }
    }

    @Test
    fun secondaryPressWhileTouchingSwitchesAfterTheStroke() {
        launch().use { s ->
            val o = origin(s)
            val buttons = IntArray(20) { if (it in 5..9) MotionEvent.BUTTON_STYLUS_SECONDARY else 0 }
            stylus(o, FloatArray(20) { 300f + it * 10f }, FloatArray(20) { 300f }, buttons)
            assertEquals(1, read(s) { it.controller.note.value.elements.size })
            assertEquals(Tool.SELECT, read(s) { it.controller.tool })
        }
    }

    @Test
    fun primaryHeldErasesAndThePenComesBack() {
        launch().use { s ->
            val o = origin(s)
            stylus(o, FloatArray(20) { 300f + it * 10f }, FloatArray(20) { 300f })
            assertEquals(1, read(s) { it.controller.note.value.elements.size })
            stylus(o, FloatArray(10) { 400f }, FloatArray(10) { 250f + it * 10f }, IntArray(10) { MotionEvent.BUTTON_STYLUS_PRIMARY })
            assertEquals(0, read(s) { it.controller.note.value.elements.size })
            assertEquals(Tool.PEN, read(s) { it.controller.tool })
            stylus(o, FloatArray(10) { 300f + it * 10f }, FloatArray(10) { 500f })
            assertEquals(1, read(s) { it.controller.note.value.elements.size })
        }
    }

    @Test
    fun lassoThenRotateWithTheHandle() {
        launch().use { s ->
            val o = origin(s)
            stylus(o, FloatArray(21) { 300f + it * 10f }, FloatArray(21) { 400f })
            val drawn = read(s) { it.controller.note.value.elements.single() as Stroke }
            hover(o, 700f, 700f, MotionEvent.BUTTON_STYLUS_SECONDARY)
            hover(o, 700f, 700f, 0)
            val n = 36
            stylus(o, FloatArray(n + 1) { 400f + 160f * cos(it * 2 * PI / n).toFloat() },
                FloatArray(n + 1) { 400f + 60f * sin(it * 2 * PI / n).toFloat() })
            assertEquals(setOf(drawn.id), read(s) { it.controller.selection })

            // Drag the rotate handle a quarter turn clockwise about the selection's centre.
            val v = read(s) { it.controller.viewport }
            val frame = read(s) { it.controller.selectionFrame()!! }
            val h = frame.rotateHandle(ToolGestures.SELECTION_PAD_DP / v.zoom, ToolGestures.ROTATE_HANDLE_GAP_DP / v.zoom)
            val cx = v.toScreenX(frame.rect.centerX)
            val cy = v.toScreenY(frame.rect.centerY)
            val hx = v.toScreenX(h.x)
            val hy = v.toScreenY(h.y)
            val r = kotlin.math.hypot(hx - cx, hy - cy)
            val a0 = atan2(hy - cy, hx - cx)
            val steps = 20
            stylus(o, FloatArray(steps + 1) { cx + r * cos(a0 + (PI / 2 * it / steps).toFloat()) },
                FloatArray(steps + 1) { cy + r * sin(a0 + (PI / 2 * it / steps).toFloat()) })
            val turned = read(s) { it.controller.note.value.elements.single() as Stroke }
            // A horizontal stroke now stands upright through the same centre.
            val xs = turned.points.map { it.x }
            val ys = turned.points.map { it.y }
            assertTrue("${xs.min()}..${xs.max()}", xs.max() - xs.min() < 1f)
            assertEquals(drawn.points.maxOf { it.x } - drawn.points.minOf { it.x }, ys.max() - ys.min(), 1f)
            s.onActivity { it.controller.undo() }
            assertEquals(drawn, read(s) { it.controller.note.value.elements.single() })
        }
    }

    @Test
    fun holdingStillOnTheSelectionOpensTheMenu() {
        launch().use { s ->
            val o = origin(s)
            stylus(o, FloatArray(21) { 300f + it * 10f }, FloatArray(21) { 400f })
            val id = read(s) { it.controller.note.value.elements.single().id }
            s.onActivity {
                it.controller.tool = Tool.SELECT
                it.controller.select(setOf(id))
            }
            val before = read(s) { it.controller.note.value }
            val down = SystemClock.uptimeMillis()
            send(o, down, down, MotionEvent.ACTION_DOWN, 400f, 400f, 0)
            SystemClock.sleep(700)
            instrumentation.waitForIdleSync()
            assertTrue(read(s) { it.controller.selectionMenuOpen })
            send(o, down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, 430f, 400f, 0)
            send(o, down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 430f, 400f, 0)
            instrumentation.waitForIdleSync()
            // The hold didn't move anything and kept the menu open.
            assertEquals(before, read(s) { it.controller.note.value })
            assertTrue(read(s) { it.controller.selectionMenuOpen })
            // A tap elsewhere closes it and does nothing else.
            stylus(o, floatArrayOf(900f, 900f), floatArrayOf(200f, 200f))
            assertFalse(read(s) { it.controller.selectionMenuOpen })
            assertEquals(setOf(id), read(s) { it.controller.selection })
        }
    }

    // Injection.

    private fun stylus(o: IntArray, xs: FloatArray, ys: FloatArray, buttons: IntArray = IntArray(xs.size)) {
        val down = SystemClock.uptimeMillis()
        val n = xs.size
        for (i in 0 until n) {
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                n - 1 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            send(o, down, down + i * 8L, action, xs[i], ys[i], buttons[i])
        }
        instrumentation.waitForIdleSync()
    }

    private fun hover(o: IntArray, x: Float, y: Float, buttons: Int) {
        val t = SystemClock.uptimeMillis()
        val e = event(t, t, MotionEvent.ACTION_HOVER_MOVE, o[0] + x, o[1] + y, buttons)
        instrumentation.uiAutomation.injectInputEvent(e, true)
        instrumentation.waitForIdleSync()
    }

    private fun send(o: IntArray, down: Long, time: Long, action: Int, x: Float, y: Float, buttons: Int) =
        instrumentation.sendPointerSync(event(down, time, action, o[0] + x, o[1] + y, buttons))

    private fun event(down: Long, time: Long, action: Int, x: Float, y: Float, buttons: Int): MotionEvent {
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = if (action == MotionEvent.ACTION_HOVER_MOVE) 0f else 0.6f
            size = 0.1f
        })
        return MotionEvent.obtain(down, time, action, 1, props, coords, 0, buttons, 1f, 1f, 0, 0,
            InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN, 0)
    }
}
