package app.screenscratch.canvas

import app.screenscratch.canvas.InputAction.CANCEL
import app.screenscratch.canvas.InputAction.DOWN
import app.screenscratch.canvas.InputAction.HOVER
import app.screenscratch.canvas.InputAction.MOVE
import app.screenscratch.canvas.InputAction.POINTER_DOWN
import app.screenscratch.canvas.InputAction.POINTER_UP
import app.screenscratch.canvas.InputAction.UP
import app.screenscratch.canvas.PointerKind.FINGER
import app.screenscratch.canvas.PointerKind.STYLUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputRouterTest {
    private class Recorder : InputTarget {
        override var tool = Tool.PEN
        override var penOnly = true
        override var swapButtons = false
        val calls = ArrayList<String>()
        var dx = 0f
        var dy = 0f
        var factor = 1f

        override fun cycleTool() {
            tool = tool.next()
            calls += "cycle:$tool"
        }

        override fun inkStart(mode: InkMode, pointer: PointerKind, sample: InputSample) {
            calls += "start:$mode:$pointer:${sample.x.toInt()}"
        }

        override fun inkMove(samples: List<InputSample>) {
            calls += "move:" + samples.joinToString(",") { it.x.toInt().toString() }
        }

        override fun inkEnd() { calls += "end" }
        override fun inkCancel() { calls += "cancel" }

        override fun transform(dx: Float, dy: Float, factor: Float, focusX: Float, focusY: Float) {
            this.dx += dx
            this.dy += dy
            this.factor *= factor
            if (calls.lastOrNull() != "transform") calls += "transform"
        }

        override fun transformEnd() { calls += "transformEnd" }

        override fun editStart(sample: InputSample) { calls += "editStart:${sample.x.toInt()}" }
        override fun editMove(samples: List<InputSample>) { calls += "editMove:" + samples.joinToString(",") { it.x.toInt().toString() } }
        override fun editEnd() { calls += "editEnd" }
        override fun editCancel() { calls += "editCancel" }
    }

    private val t = Recorder()
    private val router = InputRouter(t)

    private fun p(id: Int, kind: PointerKind, vararg xs: Float, y: Float = 0f) =
        InputPointer(id, kind, xs.map { InputSample(it, y) })

    private fun ev(action: InputAction, vararg pointers: InputPointer, actionId: Int = pointers.first().id, buttons: Int = 0) =
        router.onEvent(InputEvent(action, pointers.toList(), actionId, buttons))

    private fun stroke(kind: PointerKind, buttons: Int = 0) {
        ev(DOWN, p(0, kind, 10f), buttons = buttons)
        ev(MOVE, p(0, kind, 11f, 12f, 13f), buttons = buttons)
        ev(UP, p(0, kind, 13f), buttons = buttons)
    }

    @Test
    fun stylusDrawsWithHistoricalSamples() {
        stroke(STYLUS)
        assertEquals(listOf("start:DRAW:STYLUS:10", "move:11,12,13", "end"), t.calls)
        assertFalse(router.inking)
    }

    @Test
    fun inkingIsReportedWhileTheStrokeRuns() {
        ev(DOWN, p(0, STYLUS, 10f))
        assertTrue(router.inking)
        ev(UP, p(0, STYLUS, 10f))
        assertFalse(router.inking)
    }

    @Test
    fun penOnlyFingerPans() {
        ev(DOWN, p(0, FINGER, 100f, y = 50f))
        ev(MOVE, p(0, FINGER, 90f, 120f, y = 80f))
        ev(UP, p(0, FINGER, 130f, y = 90f))
        assertEquals(listOf("transform", "transformEnd"), t.calls)
        assertEquals(30f, t.dx)
        assertEquals(40f, t.dy)
    }

    @Test
    fun fingerDrawsWithoutPenOnly() {
        t.penOnly = false
        stroke(FINGER)
        assertEquals(listOf("start:DRAW:FINGER:10", "move:11,12,13", "end"), t.calls)
    }

    @Test
    fun twoFingersPinch() {
        ev(DOWN, p(0, FINGER, 100f, y = 100f))
        ev(POINTER_DOWN, p(0, FINGER, 100f, y = 100f), p(1, FINGER, 200f, y = 100f), actionId = 1)
        ev(MOVE, p(0, FINGER, 50f, y = 100f), p(1, FINGER, 250f, y = 100f))
        ev(POINTER_UP, p(0, FINGER, 50f, y = 100f), p(1, FINGER, 250f, y = 100f), actionId = 0)
        ev(MOVE, p(1, FINGER, 260f, y = 110f))
        ev(UP, p(1, FINGER, 260f, y = 110f))
        assertEquals(2f, t.factor, 1e-5f)
        assertEquals(10f, t.dx, 1e-5f) // the centroid stayed put; then the remaining finger moved 10
        assertEquals(10f, t.dy, 1e-5f)
        assertEquals(listOf("transform", "transformEnd"), t.calls)
    }

    @Test
    fun secondFingerDropsAFingerStrokeAndPinches() {
        t.penOnly = false
        ev(DOWN, p(0, FINGER, 10f))
        ev(MOVE, p(0, FINGER, 20f))
        ev(POINTER_DOWN, p(0, FINGER, 20f), p(1, FINGER, 120f), actionId = 1)
        ev(MOVE, p(0, FINGER, 10f), p(1, FINGER, 130f))
        assertEquals(listOf("start:DRAW:FINGER:10", "move:20", "cancel", "transform"), t.calls)
        assertEquals(1.2f, t.factor, 1e-5f)
    }

    @Test
    fun palmDuringAStylusStrokeIsIgnored() {
        ev(DOWN, p(0, STYLUS, 10f))
        ev(POINTER_DOWN, p(0, STYLUS, 10f), p(1, FINGER, 500f), actionId = 1)
        ev(MOVE, p(0, STYLUS, 11f), p(1, FINGER, 600f))
        ev(POINTER_UP, p(0, STYLUS, 11f), p(1, FINGER, 600f), actionId = 1)
        ev(UP, p(0, STYLUS, 11f))
        assertEquals(listOf("start:DRAW:STYLUS:10", "move:11", "end"), t.calls)
    }

    @Test
    fun stylusLandingDuringAFingerPanTakesOver() {
        ev(DOWN, p(0, FINGER, 100f))
        ev(MOVE, p(0, FINGER, 110f))
        ev(POINTER_DOWN, p(0, FINGER, 110f), p(1, STYLUS, 30f), actionId = 1)
        ev(MOVE, p(0, FINGER, 120f), p(1, STYLUS, 31f))
        assertEquals(listOf("transform", "transformEnd", "start:DRAW:STYLUS:30", "move:31"), t.calls)
    }

    @Test
    fun cancelDropsTheStroke() {
        ev(DOWN, p(0, STYLUS, 10f))
        ev(MOVE, p(0, STYLUS, 11f))
        ev(CANCEL, p(0, STYLUS, 11f))
        assertEquals(listOf("start:DRAW:STYLUS:10", "move:11", "cancel"), t.calls)
        assertFalse(router.inking)
    }

    @Test
    fun primaryButtonErasesWhileHeld() {
        ev(DOWN, p(0, STYLUS, 10f), buttons = StylusButtons.PRIMARY)
        ev(MOVE, p(0, STYLUS, 11f), buttons = StylusButtons.PRIMARY)
        ev(MOVE, p(0, STYLUS, 12f, 13f)) // released mid-stroke: back to the pen
        ev(UP, p(0, STYLUS, 13f))
        assertEquals(
            listOf("start:ERASE:STYLUS:10", "move:11", "end", "start:DRAW:STYLUS:12", "move:13", "end"),
            t.calls,
        )
        assertEquals(Tool.PEN, t.tool)
    }

    @Test
    fun primaryButtonErasesEvenWithThePanTool() {
        t.tool = Tool.PAN
        stroke(STYLUS, buttons = StylusButtons.PRIMARY)
        assertEquals("start:ERASE:STYLUS:10", t.calls.first())
    }

    @Test
    fun secondaryPressCyclesToolsWhileHovering() {
        val hover = p(0, STYLUS, 5f)
        ev(HOVER, hover)
        ev(HOVER, hover, buttons = StylusButtons.SECONDARY)
        ev(HOVER, hover, buttons = StylusButtons.SECONDARY) // held: no repeat
        ev(HOVER, hover)
        ev(HOVER, hover, buttons = StylusButtons.SECONDARY)
        ev(HOVER, hover)
        ev(InputAction.BUTTON, hover, buttons = StylusButtons.SECONDARY)
        ev(InputAction.BUTTON, hover)
        assertEquals(listOf("cycle:PAN", "cycle:SELECT", "cycle:PEN"), t.calls)
    }

    @Test
    fun secondaryPressWhileTouchingCyclesForTheNextStroke() {
        ev(DOWN, p(0, STYLUS, 10f))
        ev(MOVE, p(0, STYLUS, 11f), buttons = StylusButtons.SECONDARY)
        ev(UP, p(0, STYLUS, 11f))
        assertEquals(listOf("start:DRAW:STYLUS:10", "cycle:PAN", "move:11", "end"), t.calls)
        assertEquals(Tool.PAN, t.tool)
    }

    @Test
    fun swapFlagSwapsTheButtons() {
        t.swapButtons = true
        ev(HOVER, p(0, STYLUS, 5f), buttons = StylusButtons.PRIMARY)
        assertEquals(Tool.PAN, t.tool)
        ev(HOVER, p(0, STYLUS, 5f))
        t.calls.clear()
        stroke(STYLUS, buttons = StylusButtons.SECONDARY)
        assertEquals("start:ERASE:STYLUS:10", t.calls.first())
    }

    @Test
    fun fingerButtonsAreNotStylusButtons() {
        // A finger event reports no stylus buttons; it must not look like a release and re-press.
        ev(HOVER, p(0, STYLUS, 5f), buttons = StylusButtons.SECONDARY)
        ev(DOWN, p(1, FINGER, 5f))
        ev(UP, p(1, FINGER, 5f))
        ev(HOVER, p(0, STYLUS, 5f), buttons = StylusButtons.SECONDARY)
        assertEquals(listOf("cycle:PAN"), t.calls.filter { it.startsWith("cycle") })
    }

    @Test
    fun panToolMakesThePenPan() {
        t.tool = Tool.PAN
        ev(DOWN, p(0, STYLUS, 10f))
        ev(MOVE, p(0, STYLUS, 25f))
        ev(UP, p(0, STYLUS, 25f))
        assertEquals(listOf("transform", "transformEnd"), t.calls)
        assertEquals(15f, t.dx)
    }

    @Test
    fun selectIsAnEditGesture() {
        for (tool in listOf(Tool.SELECT)) {
            t.tool = tool
            t.calls.clear()
            stroke(STYLUS)
            // The lifting sample is passed on too: a line ends where the pen lifts.
            assertEquals(listOf("editStart:10", "editMove:11,12,13", "editMove:13", "editEnd"), t.calls)
            assertFalse(router.inking)
        }
    }

    @Test
    fun theEraseButtonEndsAnEditGestureAndErases() {
        t.tool = Tool.SELECT
        ev(DOWN, p(0, STYLUS, 10f))
        ev(MOVE, p(0, STYLUS, 11f), buttons = StylusButtons.PRIMARY)
        assertEquals(listOf("editStart:10", "editEnd", "start:ERASE:STYLUS:11"), t.calls)
    }

    @Test
    fun aSecondFingerCancelsAFingerEditForAPinch() {
        t.tool = Tool.SELECT
        t.penOnly = false
        ev(DOWN, p(0, FINGER, 10f))
        ev(POINTER_DOWN, p(0, FINGER, 10f), p(1, FINGER, 50f), actionId = 1)
        assertEquals(listOf("editStart:10", "editCancel"), t.calls)
    }

    @Test
    fun eraserToolAndEraserEndErase() {
        t.tool = Tool.ERASER
        stroke(STYLUS)
        assertEquals("start:ERASE:STYLUS:10", t.calls.first())
        t.tool = Tool.PEN
        t.calls.clear()
        stroke(PointerKind.ERASER)
        assertEquals("start:ERASE:ERASER:10", t.calls.first())
    }
}
