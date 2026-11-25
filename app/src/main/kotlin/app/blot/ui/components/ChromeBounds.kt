package app.blot.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize

/**
 * Where the chrome sits on screen: every [chromeSurface] under a [LocalChromeBounds] reports its
 * rounded rectangle here while it is laid out. [onChange] gets them all, in pixels relative to
 * [origin], after any of them or [origin] changes. Main thread only.
 */
class ChromeBounds {
    private val rects = LinkedHashMap<Any, RoundRect>()

    /** Hears every change; set by whoever consumes the bounds. */
    var onChange: (List<RoundRect>) -> Unit = {}

    /** Window position, in pixels, that [current] is relative to: the top-left of the content the chrome covers. */
    var origin: Offset = Offset.Zero
        set(v) {
            if (v == field) return
            field = v
            publish()
        }

    /** The reported rectangles relative to [origin]. */
    val current: List<RoundRect>
        get() = rects.values.map { RoundRect(it.left - origin.x, it.top - origin.y, it.right - origin.x, it.bottom - origin.y, it.topLeftCornerRadius) }

    internal fun put(key: Any, windowRect: RoundRect) {
        if (rects[key] == windowRect) return
        rects[key] = windowRect
        publish()
    }

    internal fun remove(key: Any) {
        if (rects.remove(key) != null) publish()
    }

    private fun publish() = onChange(current)
}

/** The [ChromeBounds] that [chromeSurface]s in this composition report to; none by default. */
val LocalChromeBounds = staticCompositionLocalOf<ChromeBounds?> { null }

/**
 * Marks a piece of chrome drawn as [shape] over other content: it reports its bounds to
 * [LocalChromeBounds], and it takes every touch that lands on it, so a touch in its padding
 * never falls through to what lies underneath.
 */
fun Modifier.chromeSurface(shape: Shape): Modifier = this then ChromeSurfaceElement(shape)

private data class ChromeSurfaceElement(val shape: Shape) : ModifierNodeElement<ChromeSurfaceNode>() {
    override fun create() = ChromeSurfaceNode(shape)

    override fun update(node: ChromeSurfaceNode) {
        node.shape = shape
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "chromeSurface"
        properties["shape"] = shape
    }
}

private class ChromeSurfaceNode(var shape: Shape) :
    Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode, PointerInputModifierNode {
    private var reportedTo: ChromeBounds? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val sink = currentValueOf(LocalChromeBounds)
        if (sink !== reportedTo) {
            reportedTo?.remove(this)
            reportedTo = sink
        }
        if (sink == null || !coordinates.isAttached) return
        val bounds = coordinates.boundsInWindow()
        val radius = cornerRadius(coordinates.size)
        sink.put(this, RoundRect(bounds, CornerRadius(radius)))
    }

    private fun cornerRadius(size: IntSize): Float {
        val outline = shape.createOutline(size.toSize(), currentValueOf(LocalLayoutDirection), currentValueOf(LocalDensity))
        return (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
    }

    override fun onDetach() {
        reportedTo?.remove(this)
        reportedTo = null
    }

    // Being a pointer-input node is what makes the surface a hit target; it consumes nothing.
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) = Unit

    override fun onCancelPointerInput() = Unit
}
