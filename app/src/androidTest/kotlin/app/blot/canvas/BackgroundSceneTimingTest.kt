package app.blot.canvas

import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.blot.core.model.Background
import app.blot.core.model.Note
import app.blot.core.model.PageSetup
import app.blot.core.model.Pattern
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Times the scene render with a full-screen background pattern at 100% and 20% zoom, recorded
 * and drawn on the GPU as the canvas view and the front buffer draw it. Logs one line per case
 * under the tag `BackgroundTiming`: the median of [RUNS] frames, recording plus drawing.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundSceneTimingTest {
    @Test
    fun fullScreenPatterns() {
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        val w = maxOf(metrics.widthPixels, metrics.heightPixels)
        val h = minOf(metrics.widthPixels, metrics.heightPixels)
        val reader = ImageReader.newInstance(
            w, h, PixelFormat.RGBA_8888, 3,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val node = RenderNode("scene").apply { setPosition(0, 0, w, h) }
        val renderer = HardwareRenderer().apply {
            setSurface(reader.surface)
            setContentRoot(node)
        }
        val scene = SceneRenderer(OutlineCache())
        val t0 = Instant.EPOCH
        try {
            for (pattern in listOf(null) + Pattern.entries) for (zoom in listOf(1f, 0.2f)) {
                val note = Note("n", created = t0, modified = t0, page = PageSetup.Infinite, background = pattern?.let { Background(it) })
                val v = Viewport(zoom, 0f, 0f, metrics.density)
                val times = DoubleArray(RUNS + WARMUP) {
                    val start = System.nanoTime()
                    val c = node.beginRecording()
                    scene.draw(c, note, v, w, h, false)
                    node.endRecording()
                    renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
                    val ms = (System.nanoTime() - start) / 1e6
                    reader.acquireLatestImage()?.close()
                    ms
                }.drop(WARMUP).sorted()
                val median = times[times.size / 2]
                Log.i(TAG, "%dx%d px, %s at %d%%: median %.2f ms, max %.2f ms".format(w, h, pattern?.name?.lowercase() ?: "none", (zoom * 100).toInt(), median, times.last()))
                assertTrue("$pattern at $zoom: $median ms", median < BUDGET_MS)
            }
        } finally {
            renderer.destroy()
            reader.close()
        }
    }

    private companion object {
        const val TAG = "BackgroundTiming"
        const val RUNS = 30
        const val WARMUP = 5

        /** Generous: this test reports numbers, it isn't a frame budget. */
        const val BUDGET_MS = 100.0
    }
}
