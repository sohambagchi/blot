package app.screenscratch.core.store

/**
 * Just enough JSON for the library index: objects, arrays, strings, integers, booleans and null.
 * Parsed objects are `Map<String, Any?>`, arrays `List<Any?>`, numbers [Long].
 */
internal object MiniJson {
    fun write(value: Any?, out: StringBuilder = StringBuilder()): StringBuilder {
        when (value) {
            null -> out.append("null")
            is String -> quote(value, out)
            is Boolean, is Int, is Long -> out.append(value.toString())
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) out.append(',')
                    quote(k as String, out)
                    out.append(':')
                    write(v, out)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) out.append(',')
                    write(v, out)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("unsupported: ${value::class}")
        }
        return out
    }

    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.skipSpace()
        if (p.i != text.length) p.fail("trailing data")
        return v
    }

    private fun quote(s: String, out: StringBuilder) {
        out.append('"')
        for (c in s) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    private class Parser(val s: String) {
        var i = 0

        fun fail(msg: String): Nothing = throw IllegalArgumentException("$msg at $i")

        fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            skipSpace()
            if (i >= s.length) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else fail("unexpected '$c'")
            }
        }

        fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("expected $word")
            i += word.length
            return v
        }

        fun num(): Long {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            return s.substring(start, i).toLongOrNull() ?: fail("bad number")
        }

        fun str(): String {
            i++
            val b = StringBuilder()
            while (true) {
                if (i >= s.length) fail("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        if (i >= s.length) fail("bad escape")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> b.append(e)
                            'n' -> b.append('\n')
                            'r' -> b.append('\r')
                            't' -> b.append('\t')
                            'b' -> b.append('\b')
                            'f' -> b.append('\u000c')
                            'u' -> {
                                if (i + 4 > s.length) fail("bad escape")
                                b.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> fail("bad escape")
                        }
                    }
                    else -> b.append(c)
                }
            }
        }

        fun obj(): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            skipSpace()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                skipSpace()
                if (i >= s.length || s[i] != '"') fail("expected key")
                val k = str()
                skipSpace()
                if (i >= s.length || s[i] != ':') fail("expected ':'")
                i++
                m[k] = value()
                skipSpace()
                if (i >= s.length) fail("unterminated object")
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return m
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        fun arr(): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            skipSpace()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                l.add(value())
                skipSpace()
                if (i >= s.length) fail("unterminated array")
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return l
                    else -> fail("expected ',' or ']'")
                }
            }
        }
    }
}
