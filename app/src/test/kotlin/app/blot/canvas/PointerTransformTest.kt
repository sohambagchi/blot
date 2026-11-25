package app.blot.canvas

import app.blot.core.model.Argb
import app.blot.core.model.Element
import app.blot.core.model.Note
import app.blot.core.model.Rect
import app.blot.core.model.Shape
import app.blot.core.model.ShapeKind
import app.blot.core.model.Stroke
import app.blot.core.model.StrokePoint
import app.blot.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Moving, scaling and rotating selections with the pointer, groups, readouts and the long press; canvas units at 100%. */
class PointerTransformTest {
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")
    private val deg = (PI / 180).toFloat()

    private fun controller(vararg elements: Element) =
        CanvasController(Note("n", created = t0, modified = t0, elements = elements.toList()))

    private val CanvasController.elements get() = note.value.elements

    private fun CanvasController.drag(vararg xy: Float) {
        toolStart(xy[0], xy[1])
        for (i in 1 until xy.size / 2) toolMove(xy[i * 2], xy[i * 2 + 1])
        toolEnd()
    }

    private fun hStroke(id: String, x0: Float, x1: Float, y: Float, group: String? = null) = Stroke(
        id, List(((x1 - x0) / 2).toInt() + 1) { StrokePoint(x0 + it * 2f, y, 0.5f, timeMs = it * 4L) }, Argb.BLACK, 2f, group = group,
    )

    // A 100 × 50 rectangle with a 2-unit outline: its selection frame is (99, 99)–(201, 151), centre (150, 125).
    private val box = Shape("r", ShapeKind.RECTANGLE, Rect(100f, 100f, 100f, 50f), Argb.BLACK, 2f)

    /** The rotate handle of a frame centred on (150, 125) and 52 high: above the top edge by pad + gap. */
    private val handleY = 125f - (26f + ToolGestures.SELECTION_PAD_DP + ToolGestures.ROTATE_HANDLE_GAP_DP)

    private fun pointAt(angleDeg: Float, r: Float = 60f) =
        floatArrayOf(150f + r * cos(angleDeg * deg), 125f + r * sin(angleDeg * deg))

    @Test
    fun theRotateHandleTurnsAboutTheCentreAsOneUndoStep() {
        val c = controller(box)
        c.snapAngles = false
        c.tool = Tool.SELECT
        c.select(setOf("r"))
        assertEquals(ToolGestures.Hit.ROTATE, c.toolGestures.hitSelection(150f, handleY))
        c.toolStart(150f, handleY)
        // The handle starts straight up (−90°); 30° clockwise is −60°.
        val p = pointAt(-60f)
        c.toolMove(p[0], p[1])
        assertEquals("+30°", c.toolGestures.readout?.text)
        // Only a preview until pen-up.
        assertEquals(0f, (c.elements[0] as Shape).rotation)
        assertEquals(30 * deg, c.scenePreview()!!.transform.rotation, 1e-4f)
        c.toolEnd()
        val turned = c.elements[0] as Shape
        assertEquals(30 * deg, turned.rotation, 1e-4f)
        assertEquals(150f, turned.frame.centerX, 1e-3f)
        assertEquals(125f, turned.frame.centerY, 1e-3f)
        assertNull(c.toolGestures.readout)
        c.undo()
        assertEquals(box, c.elements[0])
        assertFalse(c.canUndo)
    }

    @Test
    fun aTurnedShapesFrameTurnsWithItAndSoDoesItsHandle() {
        val c = controller(box.copy(rotation = (PI / 2).toFloat()))
        c.tool = Tool.SELECT
        c.select(setOf("r"))
        val f = c.selectionFrame()!!
        assertEquals((PI / 2).toFloat(), f.rotation, 1e-6f)
        // Turned a quarter clockwise, the frame's top points right: the handle is to the right.
        val h = f.rotateHandle(ToolGestures.SELECTION_PAD_DP, ToolGestures.ROTATE_HANDLE_GAP_DP)
        assertEquals(150f + 26f + 6f + 28f, h.x, 1e-3f)
        assertEquals(125f, h.y, 1e-3f)
        assertEquals(ToolGestures.Hit.ROTATE, c.toolGestures.hitSelection(h.x, h.y))
        // Inside the turned frame but outside the unturned one.
        assertEquals(ToolGestures.Hit.INSIDE, c.toolGestures.hitSelection(150f, 175f))
    }

    @Test
    fun rotationSnapsTo15DegreesWhenSnappingIsOn() {
        val c = controller(box)
        c.tool = Tool.SELECT
        c.select(setOf("r"))
        assertTrue(c.snapAngles)
        c.toolStart(150f, handleY)
        val p = pointAt(-90f + 20f)
        c.toolMove(p[0], p[1])
        assertEquals("+15°", c.toolGestures.readout?.text)
        val q = pointAt(-90f - 7f)
        c.toolMove(q[0], q[1])
        assertEquals("0°", c.toolGestures.readout?.text)
        val r = pointAt(-90f - 10f)
        c.toolMove(r[0], r[1])
        assertEquals("−15°", c.toolGestures.readout?.text)
        c.toolEnd()
        assertEquals(-15 * deg, (c.elements[0] as Shape).rotation, 1e-4f)

        c.snapAngles = false
        c.select(setOf("r"))
        val handle = c.selectionFrame()!!.rotateHandle(ToolGestures.SELECTION_PAD_DP, ToolGestures.ROTATE_HANDLE_GAP_DP)
        c.toolStart(handle.x, handle.y)
        val s = pointAt(-90f - 15f - 7f)
        c.toolMove(s[0], s[1])
        assertEquals("−7°", c.toolGestures.readout?.text)
        c.toolCancel()
    }

    @Test
    fun aMultipleSelectionTurnsItsPointsAboutTheSelectionCentre() {
        // Two strokes from x 0 to 100 at y 0 and y 40: bounds (−2, −2)–(102, 42), centre (50, 20).
        val c = controller(hStroke("a", 0f, 100f, 0f), hStroke("b", 0f, 100f, 40f))
        c.snapAngles = true
        c.tool = Tool.SELECT
        c.select(setOf("a", "b"))
        val f = c.selectionFrame()!!
        assertEquals(0f, f.rotation)
        val h = f.rotateHandle(ToolGestures.SELECTION_PAD_DP, ToolGestures.ROTATE_HANDLE_GAP_DP)
        c.toolStart(h.x, h.y)
        c.toolMove(50f + 80f, 20f)
        c.toolEnd()
        val a = c.elements[0] as Stroke
        // (0, 0) turned 90° clockwise about (50, 20) is (70, −30).
        assertEquals(70f, a.points.first().x, 1 / 16f)
        assertEquals(-30f, a.points.first().y, 1 / 16f)
        assertEquals(70f, a.points.last().x, 1 / 16f)
        assertEquals(70f, a.points.last().y, 1 / 16f)
        // The two strokes were one step.
        c.undo()
        assertEquals(0f, (c.elements[0] as Stroke).points.first().x)
        assertEquals(0f, (c.elements[1] as Stroke).points.first().x)
    }

    @Test
    fun scalingShowsTheFactorBesideTheCorner() {
        val c = controller(box)
        c.tool = Tool.SELECT
        c.select(setOf("r"))
        // Padded frame (93, 93)–(207, 157); drag the bottom-right corner along the diagonal.
        val ax = 93f
        val ay = 93f
        val cx = 207f
        val cy = 157f
        assertEquals(ToolGestures.Hit.CORNER, c.toolGestures.hitSelection(cx, cy))
        c.toolStart(cx, cy)
        c.toolMove(ax + (cx - ax) * 1.5f, ay + (cy - ay) * 1.5f)
        val r = c.toolGestures.readout!!
        assertEquals("1.5x", r.text)
        assertEquals(ax + (cx - ax) * 1.5f, r.x, 1e-3f)
        c.toolMove(ax + (cx - ax) * 0.95f, ay + (cy - ay) * 0.95f)
        assertEquals("0.95x", c.toolGestures.readout!!.text)
        c.toolMove(cx, cy)
        assertEquals("1x", c.toolGestures.readout!!.text)
        c.toolMove(ax + (cx - ax) * 0.5f, ay + (cy - ay) * 0.5f)
        c.toolEnd()
        val s = c.elements[0] as Shape
        assertEquals(1f, s.width, 1e-4f)
        assertEquals(50f, s.frame.width, 1e-3f)
        c.undo()
        assertEquals(box, c.elements[0])
    }

    @Test
    fun scalingATurnedFrameKeepsItsOppositeCornerAndTurn() {
        val turned = box.copy(rotation = (PI / 4).toFloat())
        val c = controller(turned)
        c.tool = Tool.SELECT
        c.select(setOf("r"))
        val xy = c.selectionFrame()!!.corners(ToolGestures.SELECTION_PAD_DP)
        // Bottom-right (index 2) away from the top-left (index 0), doubled.
        c.drag(xy[4], xy[5], xy[0] + (xy[4] - xy[0]) * 2, xy[1] + (xy[5] - xy[1]) * 2)
        val s = c.elements[0] as Shape
        assertEquals((PI / 4).toFloat(), s.rotation, 1e-5f)
        assertEquals(200f, s.frame.width, 0.01f)
        val after = c.selectionFrame()!!.corners(ToolGestures.SELECTION_PAD_DP * 2)
        // The grabbed handle's opposite corner stayed put (the pad scales with the frame).
        assertEquals(xy[0], after[0], 0.05f)
        assertEquals(xy[1], after[1], 0.05f)
    }

    @Test
    fun otherToolsNeverTransformTheSelectionAndLeavingThePointerClearsIt() {
        val c = controller(box)
        c.tool = Tool.SELECT
        c.select(setOf("r"))
        for (tool in listOf(Tool.PEN, Tool.PAN, Tool.ERASER, Tool.SHAPE, Tool.LINE, Tool.TEXT)) {
            c.tool = Tool.SELECT
            c.select(setOf("r"))
            c.tool = tool
            assertTrue("$tool", c.selection.isEmpty())
            assertEquals(ToolGestures.Hit.NONE, c.toolGestures.hitSelection(150f, 125f))
        }
        // A drag inside the old selection with the shape tool draws a shape; the box is untouched.
        c.tool = Tool.SHAPE
        c.drag(120f, 110f, 180f, 140f)
        assertEquals(box, c.elements[0])
        assertEquals(2, c.elements.size)
    }

    @Test
    fun aMemberSelectsItsWholeGroupWhichMovesAsOne() {
        val c = controller(hStroke("a", 0f, 40f, 0f, group = "g"), hStroke("b", 0f, 40f, 100f, group = "g"), hStroke("c", 200f, 240f, 0f))
        c.tool = Tool.SELECT
        // A tap on one member.
        c.drag(20f, 0f, 20f, 0f)
        assertEquals(setOf("a", "b"), c.selection)
        c.clearSelection()
        // A lasso touching only the second member.
        c.drag(10f, 90f, 30f, 90f, 30f, 110f, 10f, 110f, 10f, 90f)
        assertEquals(setOf("a", "b"), c.selection)
        c.drag(20f, 50f, 70f, 50f)
        assertEquals(50f, (c.elements[0] as Stroke).points[0].x)
        assertEquals(50f, (c.elements[1] as Stroke).points[0].x)
        assertEquals(200f, (c.elements[2] as Stroke).points[0].x)
    }

    @Test
    fun groupingThroughTheControllerKeepsTheGroupContiguousAndDuplicatesGetANewGroup() {
        val c = controller(hStroke("a", 0f, 40f, 0f), hStroke("x", 0f, 40f, 0f), hStroke("b", 0f, 40f, 10f))
        c.tool = Tool.SELECT
        c.select(setOf("a", "b"))
        assertTrue(c.canGroup)
        c.groupSelection()
        assertEquals(listOf("x", "a", "b"), c.elements.map { it.id })
        val g = c.elements[1].group!!
        assertTrue(c.canUngroup)
        assertFalse(c.canGroup)
        c.duplicateSelection()
        val copies = c.elements.drop(3)
        assertEquals(2, copies.size)
        assertEquals(1, copies.map { it.group }.toSet().size)
        assertTrue(copies[0].group != g && copies[0].group != null)
        assertEquals(copies.map { it.id }.toSet(), c.selection)
        // The group restacks as one.
        c.select(setOf("a"))
        c.sendBackward()
        assertEquals(listOf("a", "b", "x"), c.elements.take(3).map { it.id })
        // An undo that brings a group back around a selected member selects the whole group.
        c.select(setOf("a"))
        c.ungroupSelection()
        c.select(setOf("a"))
        assertEquals(setOf("a"), c.selection)
        c.undo()
        assertEquals(setOf("a", "b"), c.selection)
    }

    @Test
    fun aTurnedTextBoxKeepsItsTopLeftCornerWhenItsHeightChanges() {
        val c = controller(TextBox("t", Rect(0f, 0f, 100f, 20f), "hi", 18f, Argb.BLACK, rotation = (PI / 2).toFloat()))
        c.textMeasurer = { 60f }
        val before = (c.elements[0] as TextBox).frame.rotatedCorners((PI / 2).toFloat())
        assertTrue(c.editTextBox("t"))
        c.updateTextDraft("hi\n\nthere")
        c.finishTextEdit()
        val box = c.elements[0] as TextBox
        assertEquals(60f, box.frame.height)
        val after = box.frame.rotatedCorners(box.rotation)
        assertEquals(before[0], after[0], 1e-3f)
        assertEquals(before[1], after[1], 1e-3f)
    }

    @Test
    fun theSelectionMenuClosesWhenTheSelectionOrToolChanges() {
        val c = controller(box, box.copy(id = "s", frame = Rect(300f, 300f, 10f, 10f)))
        c.tool = Tool.SELECT
        c.openSelectionMenu()
        assertFalse("nothing selected", c.selectionMenuOpen)
        c.select(setOf("r"))
        c.openSelectionMenu()
        assertTrue(c.selectionMenuOpen)
        c.select(setOf("s"))
        assertFalse(c.selectionMenuOpen)
        c.openSelectionMenu()
        c.toolStart(305f, 305f)
        assertFalse(c.selectionMenuOpen)
        c.toolCancel()
        c.openSelectionMenu()
        c.tool = Tool.PEN
        assertFalse(c.selectionMenuOpen)
    }

    @Test
    fun readoutTexts() {
        assertEquals("+15°", Readouts.angle(15 * deg))
        assertEquals("−7°", Readouts.angle(-7 * deg))
        assertEquals("0°", Readouts.angle(0.2f * deg))
        assertEquals("−170°", Readouts.angle(190 * deg))
        assertEquals("+180°", Readouts.angle(180 * deg))
        assertEquals("1x", Readouts.scale(1f))
        assertEquals("1.1x", Readouts.scale(1.1f))
        assertEquals("0.95x", Readouts.scale(0.95f))
        assertEquals("0.5x", Readouts.scale(0.5f))
        assertEquals("1.23x", Readouts.scale(1.234f))
        assertEquals("1x", Readouts.scale(0.999f))
        assertEquals("2x", Readouts.scale(2f))
        assertEquals("12.5x", Readouts.scale(12.5f))
    }

    @Test
    fun longPressFiresAfterTheTimeoutOnlyIfHeldStill() {
        val lp = LongPress(slop = 8f)
        assertEquals(400L, lp.timeoutMs)
        lp.arm(100f, 100f, 1_000L)
        assertTrue(lp.move(105f, 103f))
        assertFalse(lp.fire(1_399L))
        assertTrue(lp.fire(1_400L))
        assertFalse("only once", lp.fire(1_500L))

        lp.arm(100f, 100f, 2_000L)
        assertFalse(lp.move(110f, 100f))
        assertFalse(lp.fire(2_400L))

        lp.arm(100f, 100f, 3_000L)
        lp.disarm()
        assertFalse(lp.fire(3_500L))
    }
}
