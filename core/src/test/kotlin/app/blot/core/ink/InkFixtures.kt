package app.blot.core.ink

import kotlin.math.PI
import kotlin.math.sin

/** Test fixtures under resources/ink: input strokes and perfect-freehand's reference output (parity.txt). */
object InkFixtures {
    fun input(name: String): List<FreehandPoint> =
        lines("/ink/inputs/$name.txt").map { line ->
            val v = line.split(' ').map { it.toFloat() }
            FreehandPoint(v[0], v[1], v.getOrElse(2) { Float.NaN })
        }

    /** One block of parity.txt: its kind (outline, points or summary), input, option spec and rows. */
    class Block(val kind: String, val input: String, val spec: String, val count: Int, val rows: List<DoubleArray>) {
        val options: StrokeOptions get() = parseOptions(spec)
        override fun toString() = "$kind $input $spec"
    }

    val parity: List<Block> by lazy {
        val blocks = mutableListOf<Block>()
        val lines = lines("/ink/parity.txt")
        var i = 0
        while (i < lines.size) {
            val head = lines[i++].split(' ')
            val spec = if (head[2] == "-") "" else head[2]
            val count = head[3].toInt()
            if (head[0] == "summary") {
                blocks += Block("summary", head[1], spec, count, listOf(head.drop(4).map { it.toDouble() }.toDoubleArray()))
            } else {
                val rows = lines.subList(i, i + count).map { row -> row.split(' ').map { it.toDouble() }.toDoubleArray() }
                i += count
                blocks += Block(head[0], head[1], spec, count, rows)
            }
        }
        blocks
    }

    private val easings = mapOf(
        "linear" to Easing.Linear,
        "quad" to Easing { it * it },
        "outQuad" to Easing.EaseOutQuad,
        "outCubic" to Easing.EaseOutCubic,
        "outSine" to Easing { sin(it * PI / 2) },
    )

    /** Parses parity.mjs's "key=value,…" option spec; absent keys keep the defaults. */
    fun parseOptions(spec: String): StrokeOptions {
        var o = StrokeOptions()
        if (spec.isEmpty()) return o
        fun taper(v: String) = when (v) {
            "full" -> Taper.Full
            "none" -> Taper.None
            else -> Taper.Distance(v.toDouble())
        }
        for (kv in spec.split(',')) {
            val (k, v) = kv.split('=')
            o = when (k) {
                "size" -> o.copy(size = v.toDouble())
                "thinning" -> o.copy(thinning = v.toDouble())
                "smoothing" -> o.copy(smoothing = v.toDouble())
                "streamline" -> o.copy(streamline = v.toDouble())
                "simulatePressure" -> o.copy(simulatePressure = v.toBoolean())
                "last" -> o.copy(last = v.toBoolean())
                "easing" -> o.copy(easing = easings.getValue(v))
                "startCap" -> o.copy(start = o.start.copy(cap = v.toBoolean()))
                "startTaper" -> o.copy(start = o.start.copy(taper = taper(v)))
                "startEasing" -> o.copy(start = o.start.copy(easing = easings.getValue(v)))
                "endCap" -> o.copy(end = o.end.copy(cap = v.toBoolean()))
                "endTaper" -> o.copy(end = o.end.copy(taper = taper(v)))
                "endEasing" -> o.copy(end = o.end.copy(easing = easings.getValue(v)))
                else -> error("unknown option $k")
            }
        }
        return o
    }

    private fun lines(resource: String): List<String> =
        checkNotNull(InkFixtures::class.java.getResourceAsStream(resource)) { "missing $resource" }
            .bufferedReader()
            .useLines { seq -> seq.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList() }
}
