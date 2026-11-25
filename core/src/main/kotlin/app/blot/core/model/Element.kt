package app.blot.core.model

import app.blot.core.geometry.LineRoutes
import app.blot.core.geometry.PathData
import app.blot.core.geometry.ShapeOutlines
import app.blot.core.model.format.StrokePointsSerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Something drawn on a note. A note keeps its elements in stacking order; see [Note.elements].
 *
 * All lengths and positions are in canvas units (see [Point]).
 */
@Serializable
sealed interface Element {
    /** Stable identity, unique within a note, kept across edits and saves. */
    val id: String

    /** An axis-aligned rectangle that contains everything the element draws. */
    val bounds: Rect

    /**
     * The group this element belongs to, or null. Elements that share a group id are selected,
     * transformed and restacked together and lie next to each other in the stacking order.
     * Groups don't nest.
     */
    val group: String?

    companion object {
        /** A fresh random element or note id. */
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * One pen sample.
 *
 * @property x canvas units.
 * @property y canvas units.
 * @property pressure 0–1.
 * @property tilt radians from perpendicular to the screen, 0–π/2.
 * @property orientation radians, −π–π, the direction the pen leans in canvas space as Android's
 *   `AXIS_ORIENTATION` defines it: 0 points up, π/2 right, ±π down, −π/2 left.
 * @property timeMs milliseconds since the stroke's first sample.
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tilt: Float = 0f,
    val orientation: Float = 0f,
    val timeMs: Long,
)

/** The pen a stroke was drawn with; the stroke engine shapes the outline from it. */
@Serializable
enum class PenKind {
    @SerialName("pen") PEN,
    @SerialName("pencil") PENCIL,
    @SerialName("highlighter") HIGHLIGHTER,
}

/**
 * A freehand pen stroke, stored as its raw samples.
 *
 * @property width nominal diameter in canvas units. The stroke engine's outline radius must never
 *   exceed it, so that [bounds] (the samples' box grown by [width]) contains the outline.
 * @property pressureReported false when the input device reported no pressure; the stroke engine
 *   then simulates it, and the stored pressures carry no meaning.
 */
@Serializable
@SerialName("stroke")
data class Stroke(
    override val id: String,
    @Serializable(with = StrokePointsSerializer::class) val points: List<StrokePoint>,
    val color: Argb,
    val width: Float,
    val pen: PenKind = PenKind.PEN,
    val pressureReported: Boolean = true,
    override val group: String? = null,
) : Element {
    init {
        require(points.isNotEmpty()) { "stroke $id has no points" }
        require(width > 0f) { "stroke $id has width $width" }
    }

    @Transient
    override val bounds: Rect = run {
        var l = Float.POSITIVE_INFINITY
        var t = Float.POSITIVE_INFINITY
        var r = Float.NEGATIVE_INFINITY
        var b = Float.NEGATIVE_INFINITY
        for (p in points) {
            if (p.x < l) l = p.x
            if (p.x > r) r = p.x
            if (p.y < t) t = p.y
            if (p.y > b) b = p.y
        }
        Rect.fromEdges(l, t, r, b).inflate(width)
    }
}

/**
 * The geometry of a [Shape]; [app.blot.core.geometry.ShapeOutlines] gives each one's outline.
 * Values after [ELLIPSE] need format 2 ([isFormat1]).
 */
@Serializable
enum class ShapeKind {
    @SerialName("rectangle") RECTANGLE,
    @SerialName("ellipse") ELLIPSE,
    @SerialName("roundedRectangle") ROUNDED_RECTANGLE,
    @SerialName("triangle") TRIANGLE,
    @SerialName("diamond") DIAMOND,
    @SerialName("hexagon") HEXAGON,
    @SerialName("parallelogram") PARALLELOGRAM,
    @SerialName("cylinder") CYLINDER,
    ;

    /** True for the kinds format 1 has. */
    val isFormat1: Boolean get() = this == RECTANGLE || this == ELLIPSE
}

/**
 * A closed shape inscribed in [frame], rotated about the frame's centre. Its outline is
 * [outline]; the outline is stroked with mitred joins.
 *
 * @property rotation radians, clockwise on screen (y grows down).
 * @property width outline width in canvas units, centred on the geometry; 0 draws no outline.
 * @property fill fill colour, or null for none.
 */
@Serializable
@SerialName("shape")
data class Shape(
    override val id: String,
    val kind: ShapeKind,
    val frame: Rect,
    val color: Argb,
    val width: Float,
    val fill: Argb? = null,
    val rotation: Float = 0f,
    override val group: String? = null,
) : Element {
    init {
        require(width >= 0f) { "shape $id has width $width" }
    }

    /** The outline in [frame], unrotated: drawn turned by [rotation] about the frame's centre. */
    val outline: PathData by lazy(LazyThreadSafetyMode.PUBLICATION) { ShapeOutlines.of(kind, frame) }

    override val bounds: Rect by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val c = abs(cos(rotation))
            val s = abs(sin(rotation))
            val half = width / 2f
            when (kind) {
                // Corners of the outline's outer edge (mitred), rotated.
                ShapeKind.RECTANGLE -> {
                    val hw = frame.width / 2f + half
                    val hh = frame.height / 2f + half
                    Rect.around(frame.centerX, frame.centerY, hw * c + hh * s, hw * s + hh * c)
                }
                // Exact extents of a rotated ellipse, then the outline's half width.
                ShapeKind.ELLIPSE -> {
                    val a = frame.width / 2f
                    val b = frame.height / 2f
                    Rect.around(
                        frame.centerX, frame.centerY,
                        sqrt(a * a * c * c + b * b * s * s) + half,
                        sqrt(a * a * s * s + b * b * c * c) + half,
                    )
                }
                else -> outline.strokeBounds(width, rotation, frame.centerX, frame.centerY)
            }
        }
}

/** The end decoration of a [Line]. */
@Serializable
enum class Arrowhead {
    @SerialName("none") NONE,
    /** Two strokes forming an open V. */
    @SerialName("arrow") ARROW,
    /** A filled triangle. */
    @SerialName("triangle") TRIANGLE,
}

/** How a [Line] runs through its points. Anything but [STRAIGHT] needs format 2. */
@Serializable
enum class LineRoute {
    /** Straight segments from point to point. */
    @SerialName("straight") STRAIGHT,

    /** Straight segments (right-angled when drawn) whose corners are rounded; see [Line.cornerRadius]. */
    @SerialName("elbow") ELBOW,

    /** A smooth curve through every point. */
    @SerialName("curve") CURVE,
}

/**
 * A line through [points] (at least two), optionally with arrowheads at its ends. [route] says
 * how it runs between them; [centreline] is the path it draws.
 *
 * @property width line width in canvas units.
 * @property route written only when not [LineRoute.STRAIGHT].
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("line")
data class Line(
    override val id: String,
    val points: List<Point>,
    val color: Argb,
    val width: Float,
    val startArrow: Arrowhead = Arrowhead.NONE,
    val endArrow: Arrowhead = Arrowhead.NONE,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val route: LineRoute = LineRoute.STRAIGHT,
    override val group: String? = null,
) : Element {
    init {
        require(points.size >= 2) { "line $id has ${points.size} points" }
        require(width > 0f) { "line $id has width $width" }
    }

    /**
     * How far an arrowhead may reach from its end point, in canvas units, in any direction.
     * Renderers must draw arrowheads within it.
     */
    val arrowheadLength: Float get() = max(ARROWHEAD_MIN_LENGTH, ARROWHEAD_LENGTH_PER_WIDTH * width)

    /** An elbow's corner radius in canvas units; a corner takes at most half of either segment. */
    val cornerRadius: Float get() = max(CORNER_MIN_RADIUS, CORNER_RADIUS_PER_WIDTH * width)

    /** The path the line's centre follows, without arrowheads. */
    val centreline: PathData by lazy(LazyThreadSafetyMode.PUBLICATION) { LineRoutes.path(points, route, cornerRadius) }

    override val bounds: Rect by lazy(LazyThreadSafetyMode.PUBLICATION) {
        // A curve stays within its Bézier control points; other routes within their points.
        val box = if (route == LineRoute.CURVE) centreline.controlBounds() else Rect.fromEdges(
            points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y },
        )
        val arrows = startArrow != Arrowhead.NONE || endArrow != Arrowhead.NONE
        box.inflate(width / 2f + if (arrows) arrowheadLength else 0f)
    }

    companion object {
        const val ARROWHEAD_LENGTH_PER_WIDTH = 4f
        const val ARROWHEAD_MIN_LENGTH = 8f
        const val CORNER_RADIUS_PER_WIDTH = 4f
        const val CORNER_MIN_RADIUS = 12f
    }
}

/**
 * A markdown text box. Text wraps to the frame's width; the editor keeps the frame's height
 * at least the laid-out text's height so that [bounds] contains it.
 *
 * @property markdown CommonMark source.
 * @property fontSize body text size in canvas units.
 * @property rotation radians about the frame's centre, clockwise on screen. Written only when non-zero.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("text")
data class TextBox(
    override val id: String,
    val frame: Rect,
    val markdown: String,
    val fontSize: Float,
    val color: Argb,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val rotation: Float = 0f,
    override val group: String? = null,
) : Element {
    init {
        require(fontSize > 0f) { "text $id has font size $fontSize" }
    }

    override val bounds: Rect get() = frame.rotatedBounds(rotation)
}

/**
 * A file in the note directory's `assets/`, named by its content hash.
 *
 * @property sha256 lower-case hex SHA-256 of the file's bytes, 64 characters.
 * @property extension lower-case file extension without the dot, such as `png` or `jpg`.
 */
@Serializable
data class AssetRef(val sha256: String, val extension: String) {
    init {
        require(sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' }) { "bad sha256 $sha256" }
        require(extension.isNotEmpty() && extension.all { it in 'a'..'z' || it in '0'..'9' }) {
            "bad extension $extension"
        }
    }

    /** Path relative to the note directory, such as `assets/<sha256>.png`. */
    val path: String get() = "assets/$sha256.$extension"
}

/**
 * A bitmap image drawn into [frame], stretched to fit, and turned by [rotation] radians about the
 * frame's centre (clockwise on screen; written only when non-zero). Images are never recoloured
 * in dark mode.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("image")
data class Image(
    override val id: String,
    val asset: AssetRef,
    val frame: Rect,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val rotation: Float = 0f,
    override val group: String? = null,
) : Element {
    override val bounds: Rect get() = frame.rotatedBounds(rotation)
}
