package app.screenscratch.core.export

import java.util.Base64
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Writes indented SVG/XML elements to [out]. Attribute values and text are escaped; attributes
 * whose value is null are left out.
 */
class SvgXml(private val out: Appendable) {
    private val stack = ArrayList<String>()

    /** The XML declaration; call first. */
    fun declaration() {
        out.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
    }

    fun open(name: String, vararg attrs: Pair<String, Any?>) {
        start(name, attrs)
        out.append(">\n")
        stack += name
    }

    fun leaf(name: String, vararg attrs: Pair<String, Any?>) {
        start(name, attrs)
        out.append("/>\n")
    }

    /** An element holding only [text], on one line. */
    fun textLeaf(name: String, text: String, vararg attrs: Pair<String, Any?>) {
        start(name, attrs)
        out.append('>')
        escape(text, out)
        out.append("</").append(name).append(">\n")
    }

    /** Starts an element whose children are written inline with [inlineLeaf]; ends with [closeInline]. */
    fun openInline(name: String, vararg attrs: Pair<String, Any?>) {
        start(name, attrs)
        out.append('>')
        stack += name
    }

    fun inlineLeaf(name: String, text: String, vararg attrs: Pair<String, Any?>) {
        out.append('<').append(name)
        attributes(attrs)
        out.append('>')
        escape(text, out)
        out.append("</").append(name).append('>')
    }

    fun inlineOpen(name: String, vararg attrs: Pair<String, Any?>) {
        out.append('<').append(name)
        attributes(attrs)
        out.append('>')
    }

    fun inlineClose(name: String) {
        out.append("</").append(name).append('>')
    }

    fun closeInline() {
        out.append("</").append(stack.removeAt(stack.lastIndex)).append(">\n")
    }

    fun close() {
        val name = stack.removeAt(stack.lastIndex)
        indent()
        out.append("</").append(name).append(">\n")
    }

    /** Closes every open element. */
    fun finish() {
        while (stack.isNotEmpty()) close()
    }

    private fun start(name: String, attrs: Array<out Pair<String, Any?>>) {
        indent()
        out.append('<').append(name)
        attributes(attrs)
    }

    private fun attributes(attrs: Array<out Pair<String, Any?>>) {
        for ((k, v) in attrs) {
            if (v == null) continue
            out.append(' ').append(k).append("=\"")
            escape(if (v is Float) num(v) else v.toString(), out)
            out.append('"')
        }
    }

    private fun indent() {
        repeat(stack.size) { out.append("  ") }
    }

    companion object {
        /**
         * Appends [s] with `&<>"'` escaped and characters XML 1.0 cannot hold (most C0 controls,
         * lone surrogates, U+FFFE/F) dropped.
         */
        fun escape(s: String, out: Appendable) {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '&' -> out.append("&amp;")
                    c == '<' -> out.append("&lt;")
                    c == '>' -> out.append("&gt;")
                    c == '"' -> out.append("&quot;")
                    c == '\'' -> out.append("&apos;")
                    c == '\t' || c == '\n' || c == '\r' -> out.append(c)
                    c < ' ' || c == '￾' || c == '￿' -> Unit
                    c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> {
                        out.append(c).append(s[i + 1])
                        i++
                    }
                    c.isSurrogate() -> Unit
                    else -> out.append(c)
                }
                i++
            }
        }

        fun escape(s: String): String = StringBuilder(s.length).also { escape(s, it) }.toString()

        /** [v] with at most two decimals and no trailing zeros, independent of locale. */
        fun num(v: Float): String = StringBuilder().also { num(v, it) }.toString()

        fun num(v: Float, out: StringBuilder) {
            val hundredths = (v.toDouble() * 100).roundToLong()
            if (hundredths < 0) out.append('-')
            val a = abs(hundredths)
            out.append(a / 100)
            val frac = (a % 100).toInt()
            if (frac != 0) {
                out.append('.').append(frac / 10)
                if (frac % 10 != 0) out.append(frac % 10)
            }
        }

        /** `#RRGGBB` for the colour's RGB. */
        fun rgb(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

        /** The colour's alpha as an opacity, or null when opaque. */
        fun opacity(argb: Int): String? {
            val a = argb ushr 24
            return if (a == 255) null else num(a / 255f)
        }

        /** A `data:` URI for an image file's [bytes]; [extension] picks the MIME type. */
        fun dataUri(bytes: ByteArray, extension: String): String =
            "data:${imageMime(extension)};base64," + Base64.getEncoder().encodeToString(bytes)

        fun imageMime(extension: String): String = when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            else -> "image/png"
        }
    }
}
