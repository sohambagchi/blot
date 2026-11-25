package app.screenscratch.core.model.format

import app.screenscratch.core.model.StrokePoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Real TB-J616F pen strokes, extracted from the pen test reports in `reports/`. */
object Fixtures {
    val strokeNames = listOf("tb-j616f-20261124-184928", "tb-j616f-20261124-190623")

    fun resource(path: String): String =
        checkNotNull(Fixtures::class.java.getResource("/$path")) { "missing resource $path" }.readText()

    /** The stroke's samples converted to canvas units (dp) and radians. */
    fun stroke(name: String): List<StrokePoint> {
        val root = Json.parseToJsonElement(resource("strokes/$name.json")).jsonObject
        val density = root.getValue("density").jsonPrimitive.double
        val t0 = root.getValue("samples").jsonArray.first().jsonArray[0].jsonPrimitive.long
        return root.getValue("samples").jsonArray.map { row ->
            val v = row.jsonArray.map { it.jsonPrimitive }
            StrokePoint(
                x = (v[1].double / density).toFloat(),
                y = (v[2].double / density).toFloat(),
                pressure = v[3].double.toFloat(),
                tilt = Math.toRadians(v[4].double).toFloat(),
                orientation = Math.toRadians(v[5].double).toFloat(),
                timeMs = v[0].long - t0,
            )
        }
    }
}
