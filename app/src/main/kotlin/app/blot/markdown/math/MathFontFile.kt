package app.blot.markdown.math

import java.nio.ByteBuffer

/**
 * An OpenType maths font read from its bytes, in font units: character map, advances, glyph
 * outlines and the `MATH` table. Replaces the FreeType binding AndroidMath reads fonts through.
 * Thread-safe; outlines are decoded on first use and kept.
 */
internal class MathFontFile(bytes: ByteArray) {
    private val tables = HashMap<String, ByteBuffer>()
    val unitsPerEm: Int
    private val advances: IntArray
    private val cmap = HashMap<Int, Int>()
    private val cff: CffOutlines
    val math: OpenTypeMathTable
    private val outlines: Array<GlyphOutline?>

    init {
        val buf = ByteBuffer.wrap(bytes)
        val numTables = buf.getShort(4).toInt() and 0xFFFF
        for (t in 0 until numTables) {
            val rec = 12 + t * 16
            val tag = String(bytes, rec, 4, Charsets.ISO_8859_1)
            val offset = buf.getInt(rec + 8)
            val length = buf.getInt(rec + 12)
            tables[tag] = ByteBuffer.wrap(bytes, offset, length).slice()
        }
        unitsPerEm = table("head").getShort(18).toInt() and 0xFFFF
        val numGlyphs = table("maxp").getShort(4).toInt() and 0xFFFF
        val numberOfHMetrics = table("hhea").getShort(34).toInt() and 0xFFFF
        val hmtx = table("hmtx")
        advances = IntArray(numGlyphs) { g -> hmtx.getShort(minOf(g, numberOfHMetrics - 1) * 4).toInt() and 0xFFFF }
        readCmap(table("cmap"))
        val cffBuf = table("CFF ")
        cff = CffOutlines(ByteArray(cffBuf.remaining()).also { cffBuf.duplicate().get(it) })
        math = OpenTypeMathTable(table("MATH"))
        outlines = arrayOfNulls(numGlyphs)
    }

    private fun table(tag: String): ByteBuffer = tables[tag]?.duplicate() ?: error("font has no $tag table")

    val glyphCount: Int get() = advances.size

    /** The glyph for [codepoint], or 0 (`.notdef`) when the font has none. */
    fun glyph(codepoint: Int): Int = cmap[codepoint] ?: 0

    fun advance(gid: Int): Int = if (gid in advances.indices) advances[gid] else 0

    fun outline(gid: Int): GlyphOutline {
        val g = if (gid in outlines.indices) gid else 0
        outlines[g]?.let { return it }
        val o = cff.outline(g)
        synchronized(outlines) { outlines[g] = o }
        return o
    }

    private fun readCmap(cmapTable: ByteBuffer) {
        val n = cmapTable.getShort(2).toInt() and 0xFFFF
        var format4 = -1
        var format12 = -1
        for (i in 0 until n) {
            val platform = cmapTable.getShort(4 + i * 8).toInt()
            val encoding = cmapTable.getShort(6 + i * 8).toInt()
            val offset = cmapTable.getInt(8 + i * 8)
            val format = cmapTable.getShort(offset).toInt() and 0xFFFF
            val unicode = platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10))
            if (!unicode) continue
            if (format == 12) format12 = offset else if (format == 4) format4 = offset
        }
        if (format12 >= 0) {
            val groups = cmapTable.getInt(format12 + 12)
            for (g in 0 until groups) {
                val at = format12 + 16 + g * 12
                val start = cmapTable.getInt(at)
                val end = cmapTable.getInt(at + 4)
                val glyph = cmapTable.getInt(at + 8)
                for (c in start..end) cmap[c] = glyph + (c - start)
            }
        } else if (format4 >= 0) {
            val segX2 = cmapTable.getShort(format4 + 6).toInt() and 0xFFFF
            val ends = format4 + 14
            val starts = ends + segX2 + 2
            val deltas = starts + segX2
            val rangeOffsets = deltas + segX2
            for (s in 0 until segX2 / 2) {
                val end = cmapTable.getShort(ends + s * 2).toInt() and 0xFFFF
                val start = cmapTable.getShort(starts + s * 2).toInt() and 0xFFFF
                val delta = cmapTable.getShort(deltas + s * 2).toInt()
                val ro = cmapTable.getShort(rangeOffsets + s * 2).toInt() and 0xFFFF
                for (c in start..end) {
                    if (c == 0xFFFF) continue
                    val g = if (ro == 0) (c + delta) and 0xFFFF else {
                        val at = rangeOffsets + s * 2 + ro + (c - start) * 2
                        val raw = cmapTable.getShort(at).toInt() and 0xFFFF
                        if (raw == 0) 0 else (raw + delta) and 0xFFFF
                    }
                    if (g != 0) cmap[c] = g
                }
            }
        }
    }

    companion object {
        private const val RESOURCE = "blot-math.otf"

        /** The bundled font: a subset of Latin Modern Math, see `scripts/subset-math-font.sh`. */
        val bundled: MathFontFile by lazy {
            val bytes = MathFontFile::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes() }
                ?: error("missing font resource $RESOURCE")
            MathFontFile(bytes)
        }
    }
}
