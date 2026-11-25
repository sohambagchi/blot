package app.screenscratch.ui.icons

import app.screenscratch.ui.icons.Glyph.Companion.circle
import app.screenscratch.ui.icons.Glyph.Companion.ellipse
import app.screenscratch.ui.icons.Glyph.Companion.rect

/** Every icon in the design, with path data copied from the `.dc.html` artboards. */
object ScratchIcons {
    // Editor tools (Main.dc.html, ICON).
    val Pen = Glyph("M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z M14 6l3 3")
    val Hand = Glyph(
        "M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2 M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2 " +
            "M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8 " +
            "M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15",
    )
    val Pointer = Glyph(
        "M4.037 4.688a.495.495 0 0 1 .651-.651l16 6.5a.5.5 0 0 1-.063.947l-6.124 1.58a2 2 0 0 0-1.438 1.435" +
            "l-1.579 6.126a.5.5 0 0 1-.947.063z",
    )
    val Eraser = Glyph("M8 20 3.7 15.7a1 1 0 0 1 0-1.4l9.6-9.6a1 1 0 0 1 1.4 0l5.6 5.6a1 1 0 0 1 0 1.4L12 20 M21 20H8 M6.5 11.5l6 6")
    val EraseCut = Glyph(listOf("M3 15c1.5-4 3.5-5 6-3 M15 12c2.5 2 4.5 1 6-3"), dashed = listOf("M12 4v16"))
    val Lasso = Glyph(listOf("M7.5 14.5c-1.2 2-.4 4.6 1.8 5.5"), dashed = listOf("M4 10a8 5.5 0 1 0 16 0a8 5.5 0 1 0-16 0"))
    val Marquee = Glyph(emptyList(), dashed = listOf("M4 5h16v14H4Z"))
    val Rect = Glyph("M4 6h16v12H4Z")
    val RoundedRect = Glyph("M8 5h8a4 4 0 0 1 4 4v6a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V9a4 4 0 0 1 4-4Z")
    val Ellipse = Glyph("M3 12a9 7 0 1 0 18 0a9 7 0 1 0-18 0")
    val Triangle = Glyph("M12 4 21 20H3Z")
    val Diamond = Glyph("M12 3l9 9-9 9-9-9Z")
    val Parallelogram = Glyph("M7 6h14l-4 12H3Z")
    val Hexagon = Glyph("M7.5 4h9l4.5 8-4.5 8h-9L3 12Z")
    val Cylinder = Glyph("M5 6a7 2.5 0 1 0 14 0a7 2.5 0 1 0-14 0 M5 6v12a7 2.5 0 0 0 14 0V6")
    val Line = Glyph("M5 19 19 5")
    val Arrow = Glyph("M5 19 19 5 M10 5h9v9")
    val DoubleArrow = Glyph("M5 19 19 5 M10 5h9v9 M14 19H5v-9")
    val Elbow = Glyph("M4 19h8V7h7 M16 4l3 3-3 3")
    val Curve = Glyph("M4 19C6 9 14 15 19 5 M13.5 5.5 19 5l.5 5.5")
    val SnapShape = Glyph("M3 6h9v12H3Z M15 12h6", circle(12f, 12f, 1.6f))
    val SnapAngle = Glyph("M4 20h16 M4 20 16 8 M10 20a6 6 0 0 0-1.76-4.24")
    val Text = Glyph("M4 7V5h16v2", "M12 5v14", "M9 19h6")
    val Image = Glyph(rect(3f, 3f, 18f, 18f, 2.5f), circle(9f, 9f, 2f), "m21 15-5-5L5 21")

    // Chrome.
    val Back = Glyph("m15 18-6-6 6-6")
    val Undo = Glyph("M9 14 4 9l5-5", "M4 9h11a5 5 0 0 1 0 10h-3")
    val Redo = Glyph("m15 14 5-5-5-5", "M20 9H9a5 5 0 0 0 0 10h3")
    val Share = Glyph("M12 3v12", "m7 8 5-5 5 5", "M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5")
    val Download = Glyph("M12 3v12 M7 10l5 5 5-5 M5 21h14")
    val Close = Glyph("M6 6l12 12 M18 6 6 18")
    val ChevronDown = Glyph("m6 9 6 6 6-6")
    val ChevronUp = Glyph("m6 15 6-6 6 6")
    val ChevronRight = Glyph("m9 18 6-6-6-6")
    val ChevronLeft = Glyph("m15 18-6-6 6-6")
    val Plus = Glyph("M12 5v14 M5 12h14")
    /** Drag handle of the horizontal toolbar: two columns of three dots. */
    val GripVertical = Glyph(listOf("M3 6h0 M9 6h0 M3 12h0 M9 12h0 M3 18h0 M9 18h0"), viewportWidth = 12f, viewportHeight = 24f)
    /** Drag handle of the vertical rail: two rows of three dots. */
    val GripHorizontal = Glyph(listOf("M6 3h0 M12 3h0 M18 3h0 M6 9h0 M12 9h0 M18 9h0"), viewportWidth = 24f, viewportHeight = 12f)
    val Move = Glyph("M12 2v20", "M2 12h20", "m9 5 3-3 3 3", "m9 19 3 3 3-3", "m5 9-3 3 3 3", "m19 9 3 3-3 3")
    val LassoPhone = Glyph(listOf("M7.5 14.5c-1.2 2-.4 4.6 1.8 5.5"), dashed = listOf(ellipse(12f, 10f, 8f, 5.5f)))

    // Library (Library.dc.html).
    val Folder = Glyph("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z")
    val FolderPlus = Glyph("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z", "M12 10v6", "M9 13h6")
    val Settings = Glyph(
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38" +
            "a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38" +
            "a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18" +
            "a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08" +
            "a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08" +
            "a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        circle(12f, 12f, 3f),
    )
    val Search = Glyph(circle(11f, 11f, 7f), "m20 20-3.5-3.5")
    val Moon = Glyph("M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5Z")
    val Sun = Glyph(
        "M12 4V2 M12 22v-2 M4 12H2 M22 12h-2 M5.6 5.6 4.2 4.2 M19.8 19.8l-1.4-1.4 M5.6 18.4l-1.4 1.4 " +
            "M19.8 4.2l-1.4 1.4 M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0",
    )
    val Kebab = Glyph("M12 5h0 M12 12h0 M12 19h0")
    val Infinite = Glyph("M12 12c-2-2.67-4-4-6-4a4 4 0 1 0 0 8c2 0 4-1.33 6-4Zm0 0c2 2.67 4 4 6 4a4 4 0 0 0 0-8c-2 0-4 1.33-6 4Z")
    val ScreenPage = Glyph(
        "M5 8h6a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2Z " +
            "M7 4a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4 M8 18h0",
    )
    val ExportPng = Glyph("M3 3h18v18H3Z M8.5 10a1.5 1.5 0 1 0 0-3a1.5 1.5 0 0 0 0 3 M21 15l-5-5L5 21")
    val ExportPdf = Glyph("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z M14 2v6h6 M9 13h6 M9 17h4")
    val ExportSvg = Glyph("M7 8l-4 4 4 4 M17 8l4 4-4 4 M14 4l-4 16")
    val ShareAlt = Glyph("M12 3v12 M7 8l5-5 5 5 M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5")
    val Delete = Glyph("M3 6h18 M8 6V4h8v2 M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6 M10 11v6 M14 11v6")
    val Rename = Glyph("M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z")
    val Warning = Glyph("M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z", "M12 9v4", "M12 17h0")
    val Notes = Glyph("M6 3h9l5 5v13H6Z", "M14 3v6h6", "M9 13h7", "M9 17h5")
}
