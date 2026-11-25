package app.blot.canvas

import kotlin.math.hypot

/** Whether an ink gesture draws a stroke or erases. */
enum class InkMode { DRAW, ERASE }

/** What [InputRouter] drives: the canvas's ink, eraser, viewport and tool. Coordinates are view pixels. */
interface InputTarget {
    val tool: Tool
    val penOnly: Boolean

    /** Swaps the side buttons: the secondary erases and the primary cycles tools. */
    val swapButtons: Boolean

    /** Moves [tool] to [Tool.next]. */
    fun cycleTool()

    fun inkStart(mode: InkMode, pointer: PointerKind, sample: InputSample)
    fun inkMove(samples: List<InputSample>)

    /** The gesture finished: a stroke is committed, an erase is recorded. */
    fun inkEnd()

    /** The gesture was cancelled: a stroke is dropped, an erase is reverted. */
    fun inkCancel()

    /** See [Viewport.transform]. */
    fun transform(dx: Float, dy: Float, factor: Float, focusX: Float, focusY: Float)

    /** A pan or pinch gesture ended. */
    fun transformEnd()

    /** A drag with [Tool.SELECT], [Tool.SHAPE], [Tool.LINE] or [Tool.TEXT] started at [sample]. */
    fun editStart(sample: InputSample) {}

    fun editMove(samples: List<InputSample>) {}

    /** The edit gesture finished and is applied. */
    fun editEnd() {}

    /** The edit gesture was cancelled and leaves no trace. */
    fun editCancel() {}
}

/**
 * Turns pointer events into ink, erase and viewport gestures, following the pen-input ADR:
 * - A stylus does what [InputTarget.tool] says; holding the erase button (primary, or secondary
 *   when swapped) erases instead, read on every event, and releasing it returns to the tool.
 * - Pressing the cycle button (the other one), hovering or touching, calls [InputTarget.cycleTool].
 * - With [InputTarget.penOnly], fingers only navigate: one drags to pan, two pinch. Without it, one
 *   finger uses the tool and a second finger turns the gesture into a pinch, dropping the stroke.
 * - While a stylus gesture runs, finger pointers are ignored (a resting palm). A stylus landing
 *   during a finger gesture ends that gesture and takes over.
 * - `ACTION_CANCEL` drops an in-progress stroke.
 *
 * Not thread-safe; call from the UI thread.
 */
class InputRouter(private val target: InputTarget) {
    private enum class Role { DRAW, ERASE, PAN, EDIT, NONE }

    private sealed interface Gesture
    private data object None : Gesture
    private class Single(
        val id: Int,
        val stylus: Boolean,
        val kind: PointerKind,
        val base: Role,
        val role: Role,
        var lastX: Float,
        var lastY: Float,
    ) : Gesture
    private class Pinch(val a: Int, val b: Int, var cx: Float, var cy: Float, var span: Float) : Gesture

    private var gesture: Gesture = None
    private var lastButtons = 0

    /** True while a stroke or erase is in progress. */
    val inking: Boolean get() = (gesture as? Single)?.role.let { it == Role.DRAW || it == Role.ERASE }

    fun onEvent(e: InputEvent) {
        if (e.action == InputAction.HOVER || e.action == InputAction.BUTTON || e.pointers.any { it.kind.isStylus }) {
            buttons(e.buttons)
        }
        when (e.action) {
            InputAction.HOVER, InputAction.BUTTON -> Unit
            InputAction.DOWN -> {
                cancel()
                e.pointer(e.actionPointerId)?.let { begin(it, e.buttons, it.samples) }
            }
            InputAction.POINTER_DOWN -> pointerDown(e)
            InputAction.MOVE -> move(e)
            InputAction.POINTER_UP -> pointerUp(e)
            InputAction.UP -> up(e)
            InputAction.CANCEL -> cancel()
        }
    }

    private fun buttons(buttons: Int) {
        val pressed = buttons and lastButtons.inv()
        lastButtons = buttons
        val cycle = if (target.swapButtons) StylusButtons.PRIMARY else StylusButtons.SECONDARY
        if (pressed and cycle != 0) target.cycleTool()
    }

    private val PointerKind.isStylus get() = this == PointerKind.STYLUS || this == PointerKind.ERASER

    /** What a pointer of [kind] does by the current tool, ignoring buttons. */
    private fun toolRole(kind: PointerKind): Role {
        if (!kind.isStylus && target.penOnly) return Role.PAN
        return when (target.tool) {
            Tool.PEN -> Role.DRAW
            Tool.ERASER -> Role.ERASE
            Tool.PAN -> Role.PAN
            Tool.SELECT, Tool.SHAPE, Tool.LINE, Tool.TEXT -> Role.EDIT
        }
    }

    private fun erasing(kind: PointerKind, buttons: Int): Boolean {
        if (kind == PointerKind.ERASER) return true
        val erase = if (target.swapButtons) StylusButtons.SECONDARY else StylusButtons.PRIMARY
        return kind.isStylus && buttons and erase != 0
    }

    // A gesture keeps the tool it started with ([base]); only the erase button changes it mid-gesture.
    private fun begin(p: InputPointer, buttons: Int, samples: List<InputSample>, base: Role = toolRole(p.kind)) {
        val role = if (erasing(p.kind, buttons)) Role.ERASE else base
        val first = samples.first()
        gesture = Single(p.id, p.kind.isStylus, p.kind, base, role, first.x, first.y)
        when (role) {
            Role.DRAW, Role.ERASE -> {
                target.inkStart(if (role == Role.DRAW) InkMode.DRAW else InkMode.ERASE, p.kind, first)
                if (samples.size > 1) target.inkMove(samples.subList(1, samples.size))
            }
            Role.EDIT -> {
                target.editStart(first)
                if (samples.size > 1) target.editMove(samples.subList(1, samples.size))
            }
            Role.PAN, Role.NONE -> Unit
        }
    }

    private fun end(g: Single, commit: Boolean) {
        when (g.role) {
            Role.DRAW, Role.ERASE -> if (commit) target.inkEnd() else target.inkCancel()
            Role.PAN -> target.transformEnd()
            Role.EDIT -> if (commit) target.editEnd() else target.editCancel()
            Role.NONE -> Unit
        }
        gesture = None
    }

    private fun pointerDown(e: InputEvent) {
        val p = e.pointer(e.actionPointerId) ?: return
        when (val g = gesture) {
            None -> begin(p, e.buttons, p.samples)
            is Single -> when {
                g.stylus -> Unit // A second contact while the pen is down is a palm.
                p.kind.isStylus -> {
                    end(g, commit = g.role == Role.PAN)
                    begin(p, e.buttons, p.samples)
                }
                else -> {
                    if (g.role == Role.DRAW || g.role == Role.ERASE) target.inkCancel()
                    if (g.role == Role.EDIT) target.editCancel()
                    val a = e.pointer(g.id) ?: return begin(p, e.buttons, p.samples)
                    gesture = pinchOf(a, p)
                }
            }
            is Pinch -> if (p.kind.isStylus) {
                target.transformEnd()
                begin(p, e.buttons, p.samples)
            }
        }
    }

    private fun pinchOf(a: InputPointer, b: InputPointer): Pinch {
        val pa = a.current
        val pb = b.current
        return Pinch(a.id, b.id, (pa.x + pb.x) / 2, (pa.y + pb.y) / 2, hypot(pb.x - pa.x, pb.y - pa.y))
    }

    private fun move(e: InputEvent) {
        when (val g = gesture) {
            None -> Unit
            is Single -> {
                val p = e.pointer(g.id) ?: return
                if (g.stylus) {
                    val role = if (erasing(p.kind, e.buttons)) Role.ERASE else g.base
                    if (role != g.role) {
                        end(g, commit = true)
                        begin(p, e.buttons, p.samples, g.base)
                        return
                    }
                }
                when (g.role) {
                    Role.DRAW, Role.ERASE -> target.inkMove(p.samples)
                    Role.PAN -> panTo(g, p.current)
                    Role.EDIT -> target.editMove(p.samples)
                    Role.NONE -> Unit
                }
            }
            is Pinch -> pinchTo(g, e)
        }
    }

    private fun pinchTo(g: Pinch, e: InputEvent) {
        val a = e.pointer(g.a)?.current ?: return
        val b = e.pointer(g.b)?.current ?: return
        val cx = (a.x + b.x) / 2
        val cy = (a.y + b.y) / 2
        val span = hypot(b.x - a.x, b.y - a.y)
        val factor = if (g.span >= 1f && span >= 1f) span / g.span else 1f
        if (cx != g.cx || cy != g.cy || factor != 1f) target.transform(cx - g.cx, cy - g.cy, factor, cx, cy)
        g.cx = cx
        g.cy = cy
        g.span = span
    }

    private fun panTo(g: Single, s: InputSample) {
        val dx = s.x - g.lastX
        val dy = s.y - g.lastY
        g.lastX = s.x
        g.lastY = s.y
        if (dx != 0f || dy != 0f) target.transform(dx, dy, 1f, s.x, s.y)
    }

    private fun pointerUp(e: InputEvent) {
        val id = e.actionPointerId
        when (val g = gesture) {
            None -> Unit
            is Single -> if (g.id == id) end(g, commit = true)
            is Pinch -> if (id == g.a || id == g.b) {
                val rest = e.pointer(if (id == g.a) g.b else g.a) ?: return cancel()
                val s = rest.current
                gesture = Single(rest.id, false, rest.kind, Role.PAN, Role.PAN, s.x, s.y)
            }
        }
    }

    private fun up(e: InputEvent) {
        when (val g = gesture) {
            None -> Unit
            is Single -> {
                // The lifting sample's pressure is often 0, so it isn't inked.
                if (g.role == Role.PAN) e.pointer(g.id)?.let { panTo(g, it.current) }
                if (g.role == Role.EDIT) e.pointer(g.id)?.let { target.editMove(listOf(it.current)) }
                end(g, commit = true)
            }
            is Pinch -> {
                target.transformEnd()
                gesture = None
            }
        }
    }

    /** Ends whatever gesture is in progress, dropping a stroke. */
    fun cancel() {
        when (val g = gesture) {
            None -> Unit
            is Single -> end(g, commit = false)
            is Pinch -> target.transformEnd()
        }
        gesture = None
    }
}
