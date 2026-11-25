package app.screenscratch.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A true, non-premultiplied sRGB colour packed as `0xAARRGGBB`, the same layout as Android's
 * `@ColorInt`. Notes always store true colours; dark mode transforms them at draw time.
 * Serialized as the string `"#AARRGGBB"`.
 */
@Serializable(with = ArgbSerializer::class)
@JvmInline
value class Argb(val value: Int) {
    val alpha: Int get() = value ushr 24
    val red: Int get() = (value shr 16) and 0xFF
    val green: Int get() = (value shr 8) and 0xFF
    val blue: Int get() = value and 0xFF

    /** `"#AARRGGBB"`, upper-case hex. */
    fun toHex(): String = "#%08X".format(value)

    override fun toString(): String = toHex()

    companion object {
        val BLACK = Argb(0xFF000000.toInt())
        val WHITE = Argb(0xFFFFFFFF.toInt())
        val TRANSPARENT = Argb(0)

        /** Builds a colour from 0–255 channels. */
        fun of(alpha: Int, red: Int, green: Int, blue: Int): Argb {
            require(alpha in 0..255 && red in 0..255 && green in 0..255 && blue in 0..255)
            return Argb((alpha shl 24) or (red shl 16) or (green shl 8) or blue)
        }

        /** Parses `"#AARRGGBB"`, or `"#RRGGBB"` as opaque; either case. */
        fun parse(hex: String): Argb {
            val digits = hex.removePrefix("#")
            val v = digits.toLongOrNull(16)
            require(
                v != null && (digits.length == 8 || digits.length == 6) && digits.all { Character.digit(it, 16) >= 0 },
            ) { "not a colour: $hex" }
            return Argb(if (digits.length == 6) (0xFF000000L or v).toInt() else v.toInt())
        }
    }
}

internal object ArgbSerializer : KSerializer<Argb> {
    override val descriptor = PrimitiveSerialDescriptor("app.screenscratch.Argb", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Argb) = encoder.encodeString(value.toHex())
    override fun deserialize(decoder: Decoder): Argb = try {
        Argb.parse(decoder.decodeString())
    } catch (e: IllegalArgumentException) {
        throw SerializationException(e.message, e)
    }
}
