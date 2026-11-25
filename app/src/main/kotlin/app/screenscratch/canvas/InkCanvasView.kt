package app.screenscratch.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.graphics.lowlatency.LowLatencyCanvasView
import androidx.graphics.surface.SurfaceControlCompat
import app.screenscratch.core.model.Element
import app.screenscratch.core.model.Note
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The inking canvas: a [SceneView] that draws the note at the controller's viewport, and, once
 * the window has drawn its first frame, a `LowLatencyCanvasView` underneath whose front buffer
 * shows in-progress ink. That buffer's `SurfaceView` is Z-ordered above the window, so while a
 * stroke is drawn it covers this view (and anything overlapping it); between strokes it is moved
 * off screen and the scene view shows. If the low-latency view can't be created, the scene view
 * draws in-progress ink itself.
 *
 * Host it with [NoteCanvas]. One view per [CanvasController].
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class InkCanvasView(
    context: Context,
    val controller: CanvasController,
    private val useFrontBuffer: Boolean = true,
) : FrameLayout(context), CanvasController.Listener {

    private val density = resources.displayMetrics.density
    private val uiRenderer = SceneRenderer(controller.outlineCache, onImageLoaded = { post { onAppearanceChanged() } })
    private val front = FrontInk()
    private val sceneView = SceneView(context)
    private val router = InputRouter(Target())
    private val livePainter = LiveInkPainter()

    /** The low-latency view, once attached; null before the first frame or if it failed. */
    var lowLatencyView: LowLatencyCanvasView? = null
        private set

    /** Draws text boxes; replace it with the markdown renderer. */
    var textBoxRenderer: TextBoxRenderer
        get() = uiRenderer.text
        set(v) {
            uiRenderer.text = v
            front.renderer.text = v
            onAppearanceChanged()
        }

    /** Supplies images. */
    var assetImages: AssetImages
        get() = uiRenderer.images
        set(v) {
            uiRenderer.images = v
            front.renderer.images = v
            onAppearanceChanged()
        }

    // UI-thread ink state.
    private var builder: StrokeBuilder? = null
    private var inkMode = InkMode.DRAW
    private var eraserX = Float.NaN
    private var eraserY = Float.NaN
    private var sceneGeneration = 0
    private var viewportChangedSinceStroke = true
    private var downNanos = 0L
    private var receivedNanos = 0L
    private var lastCommitMs = 0L
    private var bufferHoldsContent = false

    init {
        addView(sceneView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controller.listener = this
        controller.eraserRadiusPx = ERASER_RADIUS_DP * density
        if (useFrontBuffer && lowLatencyView == null) {
            // Add the SurfaceView only after the window's first frame: on Android 12 a window that
            // holds one from its first frame can wait for it forever (see the TB-J616F measurements).
            viewTreeObserver.registerFrameCommitCallback { post { attachLowLatency() } }
        }
    }

    override fun onDetachedFromWindow() {
        if (controller.listener === this) controller.listener = null
        router.cancel()
        super.onDetachedFromWindow()
    }

    private fun attachLowLatency() {
        if (!isAttachedToWindow || lowLatencyView != null) return
        try {
            val v = LowLatencyCanvasView(context)
            v.setRenderCallback(front)
            addView(v, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            lowLatencyView = v
            controller.stats.frontBuffered = true
        } catch (t: Throwable) {
            Log.w(TAG, "LowLatencyCanvasView unavailable; inking through the canvas view", t)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        controller.onViewSize(w, h, density)
        sceneChanged()
    }

    // Controller notifications.

    override fun onNoteChanged() = sceneChanged()

    override fun onViewportChanged() {
        viewportChangedSinceStroke = true
        sceneChanged()
        scheduleBufferClear()
    }

    override fun onAppearanceChanged() = sceneChanged()

    private fun sceneChanged() {
        sceneGeneration++
        sceneView.invalidate()
    }

    // The front buffer still holds the last stroke's frame; clearing it while it is hidden means a
    // stroke after a pan never flashes stale pixels. Not right after pen-up, while it may still show.
    private val clearBuffer = Runnable {
        if (builder == null && bufferHoldsContent) {
            lowLatencyView?.clear()
            bufferHoldsContent = false
        }
    }

    private fun scheduleBufferClear() {
        if (!bufferHoldsContent || builder != null) return
        removeCallbacks(clearBuffer)
        postDelayed(clearBuffer, (lastCommitMs + CLEAR_AFTER_COMMIT_MS - SystemClock.uptimeMillis()).coerceAtLeast(0))
    }

    // Input.

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val a = event.actionMasked
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            // Same clock as System.nanoTime (CLOCK_MONOTONIC); whole milliseconds before API 34.
            downNanos = if (Build.VERSION.SDK_INT >= 34) event.eventTimeNanos else event.eventTime * 1_000_000
            receivedNanos = System.nanoTime()
            val tool = event.getToolType(event.actionIndex)
            if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
                // Deliver each sample as it arrives instead of once per vsync.
                requestUnbufferedDispatch(event)
            }
        }
        event.toInputEvent()?.let(router::onEvent)
        return true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        event.toInputEvent()?.let(router::onEvent)
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val a = event.actionMasked
        if (a == MotionEvent.ACTION_BUTTON_PRESS || a == MotionEvent.ACTION_BUTTON_RELEASE) {
            event.toInputEvent()?.let(router::onEvent)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private inner class Target : InputTarget {
        override val tool: Tool get() = controller.tool
        override val penOnly: Boolean get() = controller.penOnly
        override val swapButtons: Boolean get() = controller.swapStylusButtons

        override fun cycleTool() = controller.cycleTool()

        override fun inkStart(mode: InkMode, pointer: PointerKind, sample: InputSample) {
            inkMode = mode
            val v = controller.viewport
            if (mode == InkMode.ERASE) {
                controller.beginErase()
                eraseTo(v.toCanvasX(sample.x), v.toCanvasY(sample.y), first = true)
                return
            }
            val b = StrokeBuilder(controller.color, controller.width, pressureReported = pointer == PointerKind.STYLUS)
            builder = b
            removeCallbacks(clearBuffer)
            val seg = add(b, v, sample)
            val llcv = lowLatencyView
            if (llcv == null) {
                sceneView.invalidate()
                return
            }
            front.beginStroke(
                SceneState(controller.note.value, v, controller.darkMode, controller.frontBufferContent, width, height, sceneGeneration),
                uiRenderer.color(b.color.value, controller.darkMode),
                downNanos,
                receivedNanos,
                viewportChangedSinceStroke,
            )
            viewportChangedSinceStroke = false
            bufferHoldsContent = true
            if (seg != null) front.queue.add(seg)
            llcv.renderFrontBufferedLayer()
        }

        override fun inkMove(samples: List<InputSample>) {
            val v = controller.viewport
            if (inkMode == InkMode.ERASE) {
                for (s in samples) eraseTo(v.toCanvasX(s.x), v.toCanvasY(s.y), first = false)
                return
            }
            val b = builder ?: return
            var added = false
            for (s in samples) {
                val seg = add(b, v, s) ?: continue
                added = true
                if (lowLatencyView != null) front.queue.add(seg)
            }
            if (!added) return
            val llcv = lowLatencyView
            if (llcv != null) llcv.renderFrontBufferedLayer() else sceneView.invalidate()
        }

        override fun inkEnd() {
            if (inkMode == InkMode.ERASE) {
                endErase()
                controller.endErase()
                return
            }
            val b = builder ?: return
            builder = null
            b.build(Element.newId())?.let {
                controller.commitStroke(it)
                controller.stats.strokes++
            }
            lastCommitMs = SystemClock.uptimeMillis()
            lowLatencyView?.commit()
            sceneView.invalidate()
        }

        override fun inkCancel() {
            if (inkMode == InkMode.ERASE) {
                endErase()
                controller.cancelErase()
                return
            }
            builder = null
            lowLatencyView?.let {
                it.cancel()
                front.queue.clear()
                // The buffer still holds the dropped ink.
                sceneGeneration++
            }
            sceneView.invalidate()
        }

        override fun transform(dx: Float, dy: Float, factor: Float, focusX: Float, focusY: Float) =
            controller.transform(dx, dy, factor, focusX, focusY)

        override fun transformEnd() = Unit

        private fun add(b: StrokeBuilder, v: Viewport, s: InputSample): LiveSegment? =
            b.add(v.toCanvasX(s.x), v.toCanvasY(s.y), s.pressure, s.tilt, s.orientation, s.timeMs)

        private fun eraseTo(x: Float, y: Float, first: Boolean) {
            val r = controller.eraserRadiusPx / controller.viewport.scale
            val fromX = if (first || eraserX.isNaN()) x else eraserX
            val fromY = if (first || eraserY.isNaN()) y else eraserY
            controller.eraseAlong(fromX, fromY, x, y, r)
            eraserX = x
            eraserY = y
            sceneView.invalidate()
        }

        private fun endErase() {
            eraserX = Float.NaN
            eraserY = Float.NaN
            sceneView.invalidate()
        }
    }

    private fun reportFirstInk(downNanos: Long, receivedNanos: Long, drawnNanos: Long, sceneNanos: Long, afterPan: Boolean) {
        val ms = (drawnNanos - downNanos) / 1e6f
        val dispatchMs = (receivedNanos - downNanos) / 1e6f
        val sceneMs = if (sceneNanos < 0) Float.NaN else sceneNanos / 1e6f
        Log.i(TAG, "pen-down to first front-buffer draw %.2f ms (dispatch %.2f ms, scene %s, after pan %b)".format(
            ms, dispatchMs, if (sceneNanos < 0) "not redrawn" else "%.2f ms".format(sceneMs), afterPan,
        ))
        post {
            controller.stats.penDownToInkMs = ms
            controller.stats.penDownDispatchMs = dispatchMs
            controller.stats.sceneRenderMs = sceneMs
            controller.stats.afterPan = afterPan
        }
    }

    private fun reportPresent(downNanos: Long, presentNanos: Long) {
        val ms = (presentNanos - downNanos) / 1e6f
        Log.i(TAG, "pen-down to first front-buffer frame handed to the display %.2f ms".format(ms))
        post { controller.stats.penDownToPresentMs = ms }
    }

    /** What the front buffer draws for a stroke; captured at pen-down, read on the render thread. */
    private class SceneState(
        val note: Note,
        val viewport: Viewport,
        val dark: Boolean,
        val content: FrontBufferContent,
        val width: Int,
        val height: Int,
        val generation: Int,
    )

    /** The `LowLatencyCanvasView` callbacks. Everything here but [beginStroke] runs on its render thread. */
    private inner class FrontInk : LowLatencyCanvasView.Callback {
        val renderer = SceneRenderer(controller.outlineCache, onImageLoaded = { post { onAppearanceChanged() } })
        private val painter = LiveInkPainter()
        val queue = ConcurrentLinkedQueue<LiveSegment>()

        @Volatile private var scene: SceneState? = null
        @Volatile private var inkColor = 0
        private val strokeSerial = AtomicInteger()
        private val firstDrawDown = AtomicLong(-1)
        @Volatile private var received = 0L
        private val firstPresentDown = AtomicLong(-1)
        private val clearInk = AtomicBoolean(false)
        @Volatile private var afterPan = false

        // Render thread only.
        private var renderedGeneration = -1
        private var renderedContent: FrontBufferContent? = null
        private var seenSerial = -1
        private val strokeSegments = ArrayList<LiveSegment>()

        /** UI thread: publishes the scene and ink colour for a new stroke. */
        fun beginStroke(s: SceneState, color: Int, downNanos: Long, receivedNanos: Long, afterPan: Boolean) {
            received = receivedNanos
            queue.clear()
            scene = s
            inkColor = color
            this.afterPan = afterPan
            clearInk.set(true)
            firstDrawDown.set(downNanos)
            firstPresentDown.set(downNanos)
            strokeSerial.incrementAndGet()
        }

        override fun onRedrawRequested(canvas: Canvas, width: Int, height: Int) {
            renderedGeneration = -1
            renderedContent = null
            draw(canvas, forced = true)
        }

        override fun onDrawFrontBufferedLayer(canvas: Canvas, width: Int, height: Int) = draw(canvas, forced = false)

        override fun onFrontBufferedLayerRenderComplete(
            frontBufferedLayerSurfaceControl: SurfaceControlCompat,
            transaction: SurfaceControlCompat.Transaction,
        ) {
            val down = firstPresentDown.getAndSet(-1)
            if (down >= 0) reportPresent(down, System.nanoTime())
        }

        private fun draw(canvas: Canvas, forced: Boolean) {
            val s = scene ?: run {
                canvas.drawColor(0, PorterDuff.Mode.CLEAR)
                return
            }
            val serial = strokeSerial.get()
            val newStroke = serial != seenSerial
            if (newStroke) {
                seenSerial = serial
                strokeSegments.clear()
            }
            var sceneNanos = -1L
            when (s.content) {
                FrontBufferContent.SCENE -> if (forced || renderedGeneration != s.generation || renderedContent != s.content) {
                    val t0 = System.nanoTime()
                    renderer.draw(canvas, s.note, s.viewport, s.width, s.height, s.dark)
                    sceneNanos = System.nanoTime() - t0
                    renderedGeneration = s.generation
                    renderedContent = s.content
                    // A redraw mid-stroke must bring back the ink drawn so far.
                    if (!newStroke) drawSegments(canvas, s, strokeSegments)
                }
                FrontBufferContent.INK_ONLY -> if (forced || clearInk.getAndSet(false) || renderedContent != s.content) {
                    canvas.drawColor(0, PorterDuff.Mode.CLEAR)
                    renderedContent = s.content
                    renderedGeneration = -1
                    if (!newStroke) drawSegments(canvas, s, strokeSegments)
                }
            }
            clearInk.set(false)
            canvas.save()
            canvas.translate(s.viewport.offsetX, s.viewport.offsetY)
            canvas.scale(s.viewport.scale, s.viewport.scale)
            val color = inkColor
            while (true) {
                val seg = queue.poll() ?: break
                painter.draw(canvas, seg, color)
                strokeSegments += seg
            }
            canvas.restore()
            val down = firstDrawDown.getAndSet(-1)
            if (down >= 0) reportFirstInk(down, received, System.nanoTime(), sceneNanos, afterPan)
        }

        private fun drawSegments(canvas: Canvas, s: SceneState, segments: List<LiveSegment>) {
            canvas.save()
            canvas.translate(s.viewport.offsetX, s.viewport.offsetY)
            canvas.scale(s.viewport.scale, s.viewport.scale)
            for (seg in segments) painter.draw(canvas, seg, inkColor)
            canvas.restore()
        }
    }

    /** Draws the note, plus in-progress ink when there is no front buffer and the eraser ring. */
    private inner class SceneView(context: Context) : View(context) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
        }

        override fun onDraw(canvas: Canvas) {
            val t0 = System.nanoTime()
            val v = controller.viewport
            val dark = controller.darkMode
            uiRenderer.draw(canvas, controller.note.value, v, width, height, dark)
            val b = builder
            if (b != null && lowLatencyView == null) {
                canvas.save()
                canvas.translate(v.offsetX, v.offsetY)
                canvas.scale(v.scale, v.scale)
                val c = uiRenderer.color(b.color.value, dark)
                for (seg in b.segments()) livePainter.draw(canvas, seg, c)
                canvas.restore()
            }
            if (!eraserX.isNaN()) {
                ring.color = if (dark) 0x99FFFFFF.toInt() else 0x99000000.toInt()
                canvas.drawCircle(v.toScreenX(eraserX), v.toScreenY(eraserY), controller.eraserRadiusPx, ring)
            }
            val ms = (System.nanoTime() - t0) / 1e6f
            controller.stats.sceneDrawMs = ms
            controller.stats.drawnElements = uiRenderer.lastDrawnCount
        }
    }

    companion object {
        const val TAG = "InkCanvas"
        const val ERASER_RADIUS_DP = 10f

        /** How long after pen-up the front buffer may still be on screen; it isn't cleared before then. */
        private const val CLEAR_AFTER_COMMIT_MS = 150L
    }
}
