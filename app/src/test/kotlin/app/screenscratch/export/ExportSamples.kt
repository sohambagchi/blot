package app.screenscratch.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import app.screenscratch.core.export.ExportAssets
import app.screenscratch.core.model.Argb
import app.screenscratch.core.model.Arrowhead
import app.screenscratch.core.model.AssetRef
import app.screenscratch.core.model.Element
import app.screenscratch.core.model.Image
import app.screenscratch.core.model.Line
import app.screenscratch.core.model.Note
import app.screenscratch.core.model.PageSetup
import app.screenscratch.core.model.PenKind
import app.screenscratch.core.model.Point
import app.screenscratch.core.model.Rect
import app.screenscratch.core.model.Shape
import app.screenscratch.core.model.ShapeKind
import app.screenscratch.core.model.Stroke
import app.screenscratch.core.model.StrokePoint
import app.screenscratch.core.model.TextBox
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A note with every element type, and its assets. Needs Robolectric's native graphics. */
object ExportSamples {
    private val t0 = Instant.parse("2026-11-24T00:00:00Z")
    val photo = AssetRef("a".repeat(64), "png")
    val missing = AssetRef("b".repeat(64), "png")

    /** A 64 × 48 gradient. */
    val photoBitmap: Bitmap by lazy {
        Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).also {
            val paint = Paint().apply {
                shader = LinearGradient(0f, 0f, 64f, 48f, Color.rgb(240, 140, 60), Color.rgb(60, 120, 220), Shader.TileMode.CLAMP)
            }
            Canvas(it).drawRect(0f, 0f, 64f, 48f, paint)
        }
    }

    val photoBytes: ByteArray by lazy {
        ByteArrayOutputStream().also { photoBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    val assets = ExportAssets { path -> if (path == photo.path) photoBytes else null }

    const val MARKDOWN = """# Export test

Text with **bold**, *italic*, `code`, ~~gone~~ & a [link](https://example.com/?a=1&b=2) <ok>.

- [x] done
- bullet
  1. nested

> a quote

```
code block
```

![photo](assets/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png)
"""

    private fun stroke(id: String, color: Int, width: Float, pen: PenKind = PenKind.PEN, reported: Boolean = true, f: (Float) -> Triple<Float, Float, Float>): Stroke {
        val n = 60
        return Stroke(id, List(n) { i -> f(i / (n - 1f)).let { (x, y, p) -> StrokePoint(x, y, p, timeMs = i * 4L) } }, Argb(color), width, pen, reported)
    }

    /** The elements, laid out in about 0..820 × 0..700 from ([dx], [dy]). */
    fun elements(dx: Float = 0f, dy: Float = 0f): List<Element> {
        fun p(x: Float, y: Float) = Point(x + dx, y + dy)
        fun r(x: Float, y: Float, w: Float, h: Float) = Rect(x + dx, y + dy, w, h)
        return listOf(
            Shape("rect", ShapeKind.RECTANGLE, r(40f, 260f, 220f, 110f), Argb(0xFF333333.toInt()), 2f, fill = Argb(0xFFFFF59D.toInt())),
            Shape("oval", ShapeKind.ELLIPSE, r(300f, 260f, 200f, 100f), Argb(0xFF1E5BD8.toInt()), 3f, rotation = (PI / 8).toFloat()),
            stroke("wave", 0xFF000000.toInt(), 3f) { t -> Triple(dx + 40f + t * 480f, dy + 80f + 40f * sin(t * 4 * PI).toFloat(), 0.3f + 0.6f * sin(t * PI).toFloat()) },
            stroke("loop", 0xFFE53935.toInt(), 4f) { t ->
                val a = t * 2 * PI
                Triple(dx + 620f + 60f * cos(a).toFloat(), dy + 100f + 60f * sin(a).toFloat(), 0.2f + 0.8f * t)
            },
            stroke("simulated", 0xFF2E7D32.toInt(), 3f, reported = false) { t -> Triple(dx + 40f + t * 400f, dy + 190f + 20f * sin(t * 2 * PI).toFloat(), 0f) },
            stroke("highlight", 0x66FFD600, 16f, PenKind.HIGHLIGHTER) { t -> Triple(dx + 50f + t * 380f, dy + 180f, 0.5f) },
            Line("arrow", listOf(p(40f, 420f), p(300f, 420f), p(360f, 470f)), Argb(0xFF6A1B9A.toInt()), 2f, Arrowhead.TRIANGLE, Arrowhead.ARROW),
            Line("plain", listOf(p(40f, 500f), p(300f, 500f)), Argb(0xFF000000.toInt()), 1.5f),
            TextBox("text", r(400f, 400f, 380f, 420f), MARKDOWN, 16f, Argb(0xFF222222.toInt())),
            Image("img", photo, r(560f, 220f, 128f, 96f)),
            Image("missing", missing, r(710f, 220f, 64f, 64f)),
        )
    }

    fun infinite(): Note = Note("inf", "Export sample", t0, t0, PageSetup.Infinite, elements())

    /** Two A4 pages: the sample on page 1, and a stroke straddling the gap into page 2. */
    fun pages(): Note {
        val page = PageSetup.Pages.a4(count = 2)
        val top2 = page.pageRect(1).top
        val straddle = stroke("straddle", 0xFF000000.toInt(), 6f) { t -> Triple(200f + t * 400f, top2 - 60f + t * 120f, 0.6f) }
        val second = stroke("second", 0xFFE53935.toInt(), 4f) { t -> Triple(100f + t * 600f, top2 + 300f, 0.5f) }
        return Note("pages", "Pages / sample: 2", t0, t0, page, elements(60f, 60f) + straddle + second)
    }
}
