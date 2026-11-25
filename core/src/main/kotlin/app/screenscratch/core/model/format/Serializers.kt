package app.screenscratch.core.model.format

import app.screenscratch.core.model.StrokePoint
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.format.DateTimeParseException

/** Stroke samples as one base64 string; see [PointCodec]. */
internal object StrokePointsSerializer : KSerializer<List<StrokePoint>> {
    override val descriptor = PrimitiveSerialDescriptor("app.screenscratch.StrokePoints", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: List<StrokePoint>) =
        encoder.encodeString(PointCodec.encodeBase64(value))

    override fun deserialize(decoder: Decoder): List<StrokePoint> = try {
        PointCodec.decodeBase64(decoder.decodeString())
    } catch (e: IllegalArgumentException) {
        throw SerializationException("bad stroke points: ${e.message}", e)
    }
}

/** An instant as an ISO-8601 UTC string, such as `2026-11-24T19:19:00.123Z`. */
internal object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("app.screenscratch.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = try {
        Instant.parse(decoder.decodeString())
    } catch (e: DateTimeParseException) {
        throw SerializationException("bad instant: ${e.message}", e)
    }
}
