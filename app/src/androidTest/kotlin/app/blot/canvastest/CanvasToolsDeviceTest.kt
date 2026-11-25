package app.blot.canvastest

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.blot.canvas.EraserMode
import app.blot.canvas.LineArrows
import app.blot.canvas.Tool
import app.blot.core.model.Arrowhead
import app.blot.core.model.Line
import app.blot.core.model.LineRoute
import app.blot.core.model.Shape
import app.blot.core.model.format.NoteJson
import app.blot.core.model.ShapeKind
import app.blot.core.model.Stroke
import app.blot.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The editing tools with injected stylus events on the canvas test screen. Positions are view pixels. */
@RunWith(AndroidJUnit4::class)
class CanvasToolsDeviceTest {
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

    private fun setTool(s: ActivityScenario<CanvasTestActivity>, tool: Tool) = s.onActivity { it.controller.tool = tool }

    @Test
    fun lassoSelectThenMove() {
        launch().use { s ->
            val o = origin(s)
            stylus(o, FloatArray(20) { 300f + it * 10f }, FloatArray(20) { 300f })
            val drawn = read(s) { it.controller.note.value.elements.single() as Stroke }

            setTool(s, Tool.SELECT)
            // A loop around the stroke.
            val n = 36
            stylus(o, FloatArray(n + 1) { 395f + 160f * kotlin.math.cos(it * 2 * Math.PI / n).toFloat() },
                FloatArray(n + 1) { 300f + 60f * kotlin.math.sin(it * 2 * Math.PI / n).toFloat() })
            assertEquals(setOf(drawn.id), read(s) { it.controller.selection })

            // Drag from inside the selection.
            stylus(o, FloatArray(11) { 400f + it * 10f }, FloatArray(11) { 300f + it * 5f })
            val v = read(s) { it.controller.viewport }
            val moved = read(s) { it.controller.note.value.elements.single() as Stroke }
            assertEquals(drawn.id, moved.id)
            assertEquals(drawn.points[0].x + 100f / v.scale, moved.points[0].x, 0.1f)
            assertEquals(drawn.points[0].y + 50f / v.scale, moved.points[0].y, 0.1f)
            assertEquals(drawn.width, moved.width)
            s.onActivity { it.controller.undo() }
            assertEquals(drawn, read(s) { it.controller.note.value.elements.single() })
        }
    }

    @Test
    fun rectangleDraw() {
        launch().use { s ->
            val o = origin(s)
            setTool(s, Tool.SHAPE)
            s.onActivity { it.controller.shapeKind = ShapeKind.RECTANGLE }
            stylus(o, FloatArray(11) { 300f + it * 20f }, FloatArray(11) { 300f + it * 12f })
            val v = read(s) { it.controller.viewport }
            val shape = read(s) { it.controller.note.value.elements.single() as Shape }
            assertEquals(ShapeKind.RECTANGLE, shape.kind)
            assertEquals(v.toCanvasX(300f), shape.frame.left, 0.1f)
            assertEquals(v.toCanvasY(300f), shape.frame.top, 0.1f)
            assertEquals(200f / v.scale, shape.frame.width, 0.1f)
            assertEquals(120f / v.scale, shape.frame.height, 0.1f)
            assertEquals(read(s) { it.controller.width }, shape.width)
        }
    }

    @Test
    fun everyNewShapeKindDraws() {
        launch().use { s ->
            val o = origin(s)
            setTool(s, Tool.SHAPE)
            val kinds = ShapeKind.entries.filter { !it.isFormat1 }
            for ((i, kind) in kinds.withIndex()) {
                s.onActivity { it.controller.shapeKind = kind }
                val x0 = 120f + (i % 3) * 260f
                val y0 = 260f + (i / 3) * 220f
                stylus(o, FloatArray(11) { x0 + it * 18f }, FloatArray(11) { y0 + it * 15f })
            }
            val v = read(s) { it.controller.viewport }
            val shapes = read(s) { it.controller.note.value.elements.map { e -> e as Shape } }
            assertEquals(kinds, shapes.map { it.kind })
            for ((i, shape) in shapes.withIndex()) {
                assertEquals(v.toCanvasX(120f + (i % 3) * 260f), shape.frame.left, 0.1f)
                assertEquals(v.toCanvasY(260f + (i / 3) * 220f), shape.frame.top, 0.1f)
                assertEquals(180f / v.scale, shape.frame.width, 0.1f)
                assertEquals(150f / v.scale, shape.frame.height, 0.1f)
            }
            // Picked by its outline: a tap in the hexagon's frame's corner misses it, one on its top edge selects it.
            val hex = shapes.single { it.kind == ShapeKind.HEXAGON }
            setTool(s, Tool.SELECT)
            val cx = v.toScreenX(hex.frame.left) + 4f
            val cy = v.toScreenY(hex.frame.top) + 4f
            stylus(o, floatArrayOf(cx, cx), floatArrayOf(cy, cy))
            assertEquals(emptySet<String>(), read(s) { it.controller.selection })
            val hx = v.toScreenX(hex.frame.centerX)
            val hy = v.toScreenY(hex.frame.top)
            stylus(o, floatArrayOf(hx, hx), floatArrayOf(hy, hy))
            assertEquals(setOf(hex.id), read(s) { it.controller.selection })
        }
    }

    @Test
    fun elbowAndCurveDraw() {
        launch().use { s ->
            val o = origin(s)
            setTool(s, Tool.LINE)
            s.onActivity {
                it.controller.lineRoute = LineRoute.ELBOW
                it.controller.lineArrows = LineArrows.END
                it.controller.arrowhead = Arrowhead.ARROW
            }
            stylus(o, FloatArray(11) { 200f + it * 30f }, FloatArray(11) { 300f + it * 12f })
            // A curve: half a circle dragged from left to right, bulging up.
            s.onActivity { it.controller.lineRoute = LineRoute.CURVE }
            val n = 40
            stylus(
                o,
                FloatArray(n) { 200f + 300f * it / (n - 1) },
                FloatArray(n) { 700f - 150f * kotlin.math.sin(Math.PI * it / (n - 1)).toFloat() },
            )
            val v = read(s) { it.controller.viewport }
            val (elbow, curve) = read(s) { it.controller.note.value.elements.map { e -> e as Line } }
            assertEquals(LineRoute.ELBOW, elbow.route)
            assertEquals(4, elbow.points.size)
            assertEquals(elbow.points[0].y, elbow.points[1].y)
            assertEquals(elbow.points[1].x, elbow.points[2].x)
            assertEquals(v.toCanvasX(350f), elbow.points[1].x, 0.2f)
            assertEquals(v.toCanvasX(500f), elbow.points[3].x, 0.2f)
            assertEquals(Arrowhead.ARROW, elbow.endArrow)

            assertEquals(LineRoute.CURVE, curve.route)
            assertTrue("${curve.points.size}", curve.points.size in 3..8)
            assertEquals(v.toCanvasX(200f), curve.points.first().x, 0.2f)
            assertEquals(v.toCanvasX(500f), curve.points.last().x, 0.2f)
            // It passes over the top of the arc.
            assertTrue(curve.bounds.top < v.toCanvasY(560f))
            // Saved as format 2 and read back the same.
            val note = read(s) { it.controller.note.value }
            val text = NoteJson.encode(note)
            assertTrue(text.startsWith("{\n  \"format\": 2,"))
            assertEquals(note, NoteJson.decode(text))
        }
    }

    @Test
    fun partialErase() {
        launch().use { s ->
            val o = origin(s)
            stylus(o, FloatArray(40) { 300f + it * 10f }, FloatArray(40) { 300f })
            s.onActivity {
                it.controller.eraserMode = EraserMode.PARTIAL
                it.controller.tool = Tool.ERASER
            }
            stylus(o, FloatArray(10) { 500f }, FloatArray(10) { 250f + it * 10f })
            val pieces = read(s) { it.controller.note.value.elements.map { e -> e as Stroke } }
            assertEquals(2, pieces.size)
            val v = read(s) { it.controller.viewport }
            assertTrue(pieces[0].points.last().x < v.toCanvasX(500f))
            assertTrue(pieces[1].points.first().x > v.toCanvasX(500f))
            s.onActivity { it.controller.undo() }
            assertEquals(1, read(s) { it.controller.note.value.elements.size })
        }
    }

    @Test
    fun textBoxCreateAndEdit() {
        launch().use { s ->
            val o = origin(s)
            setTool(s, Tool.TEXT)
            stylus(o, floatArrayOf(300f, 300f), floatArrayOf(300f, 300f))
            assertNotNull(read(s) { it.canvasView!!.textEditor })
            // Typed through the field, not injected keys: keys sent while the keyboard is still
            // connecting arrive out of order on Android 12, and the edit flow is what's tested.
            type(s, "Hello")
            // A tap outside commits; above the keyboard, which covers the lower part of the screen.
            stylus(o, floatArrayOf(900f, 900f), floatArrayOf(100f, 100f))
            assertNull(read(s) { it.canvasView!!.textEditor })
            val box = read(s) { it.controller.note.value.elements.single() as TextBox }
            assertEquals("Hello", box.markdown)
            assertTrue(box.frame.height > 0f)

            // Tap the box to edit it again, type, and commit with Back.
            val v = read(s) { it.controller.viewport }
            val x = v.toScreenX(box.frame.x + 10f)
            val y = v.toScreenY(box.frame.centerY)
            stylus(o, floatArrayOf(x, x), floatArrayOf(y, y))
            assertEquals("Hello", read(s) { it.canvasView!!.textEditor!!.text.toString() })
            s.onActivity { it.canvasView!!.textEditor!!.setSelection(5) }
            type(s, " world")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            val edited = read(s) { it.controller.note.value.elements.single() as TextBox }
            assertEquals(box.id, edited.id)
            assertEquals("Hello world", edited.markdown)
            assertNull(read(s) { it.controller.editingText })
            s.onActivity { it.controller.undo() }
            assertEquals("Hello", read(s) { (it.controller.note.value.elements.single() as TextBox).markdown })
        }
    }

    private fun type(s: ActivityScenario<CanvasTestActivity>, text: String) {
        s.onActivity { a -> a.canvasView!!.textEditor!!.let { e -> e.text.insert(e.selectionStart, text) } }
        instrumentation.waitForIdleSync()
    }

    // Injection.

    private fun stylus(o: IntArray, xs: FloatArray, ys: FloatArray) {
        val down = SystemClock.uptimeMillis()
        val n = xs.size
        for (i in 0 until n) {
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                n - 1 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            instrumentation.sendPointerSync(event(down, down + i * 8L, action, o[0] + xs[i], o[1] + ys[i]))
        }
        instrumentation.waitForIdleSync()
    }

    private fun event(down: Long, time: Long, action: Int, x: Float, y: Float): MotionEvent {
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 0.6f; size = 0.1f })
        return MotionEvent.obtain(down, time, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN, 0)
    }
}
