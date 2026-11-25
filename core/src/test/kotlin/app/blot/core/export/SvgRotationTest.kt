package app.blot.core.export

import app.blot.core.model.Argb
import app.blot.core.model.AssetRef
import app.blot.core.model.Element
import app.blot.core.model.Image
import app.blot.core.model.Note
import app.blot.core.model.Rect
import app.blot.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.PI
import org.w3c.dom.Element as XmlElement

class SvgRotationTest {
    private val palette = ExportPalette(0xFFFFFFFF.toInt(), 0xFFE4E4E7.toInt(), 0xFFEDEDED.toInt(), 0xFFBDBDBD.toInt(), 0xFFD6D3CB.toInt())
    private val photo = AssetRef("a".repeat(64), "jpg")

    private fun svg(vararg els: Element): Document {
        val t0 = Instant.EPOCH
        val note = Note("n", created = t0, modified = t0, elements = els.toList())
        val writer = SvgNoteWriter(palette, { FloatArray(0) }, imageHref = { SvgXml.dataUri(byteArrayOf(1), "jpg") })
        val out = StringBuilder().also { writer.write(note, ExportOptions(format = ExportFormat.SVG), it) }.toString()
        return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(ByteArrayInputStream(out.toByteArray(Charsets.UTF_8)))
    }

    private fun Document.all(name: String): List<XmlElement> {
        val list = getElementsByTagNameNS(SvgNoteWriter.SVG_NS, name)
        return List(list.length) { list.item(it) as XmlElement }
    }

    private val XmlElement.parentElement get() = parentNode as XmlElement

    @Test
    fun rotatedImageIsWrappedInARotateAboutItsCentre() {
        val doc = svg(Image("img", photo, Rect(100f, 50f, 80f, 40f), rotation = (PI / 6).toFloat()))
        val group = doc.all("image").single().parentElement
        assertEquals("g", group.localName)
        assertEquals("rotate(30 140 70)", group.getAttribute("transform"))
    }

    @Test
    fun rotatedTextBoxIsWrappedInARotateAboutItsCentre() {
        val doc = svg(TextBox("t", Rect(0f, 0f, 200f, 60f), "hello", 16f, Argb.BLACK, rotation = (-PI / 4).toFloat()))
        val inner = doc.all("text").single().parentElement
        assertEquals("translate(0 0)", inner.getAttribute("transform"))
        assertEquals("rotate(-45 100 30)", inner.parentElement.getAttribute("transform"))
    }

    @Test
    fun unrotatedElementsHaveNoRotateGroup() {
        val doc = svg(Image("img", photo, Rect(0f, 0f, 10f, 10f)), TextBox("t", Rect(0f, 20f, 50f, 20f), "x", 12f, Argb.BLACK))
        for (g in doc.all("g")) assertFalse(g.getAttribute("transform"), g.getAttribute("transform").startsWith("rotate"))
    }

    @Test
    fun rotatedImageWidensTheExportBounds() {
        // A 100 × 20 image turned 90° spans 20 × 100.
        val img = Image("img", photo, Rect(0f, 0f, 100f, 20f), rotation = (PI / 2).toFloat())
        val vb = svg(img).documentElement.getAttribute("viewBox").split(' ').map { it.toFloat() }
        assertEquals(20f + 48f, vb[2], 0.01f)
        assertEquals(100f + 48f, vb[3], 0.01f)
    }
}
