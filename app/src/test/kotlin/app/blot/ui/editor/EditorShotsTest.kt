package app.blot.ui.editor

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.IntSize
import app.blot.canvas.InkColors
import app.blot.core.model.Argb
import app.blot.ui.LAND
import app.blot.ui.PHONE
import app.blot.ui.PHONE_LAND
import app.blot.ui.PORT
import app.blot.ui.preview.DotGrid
import app.blot.ui.shot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the editor chrome in the design's states to `app/build/ui-shots/editor-*.png`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorShotsTest {
    @get:Rule
    val compose = createComposeRule()

    private val base = EditorUiState(
        noteTitle = "Raft",
        folderName = "Distributed systems",
        canUndo = true,
        exportInfo = ExportInfo(
            areaSizes = mapOf(
                ExportArea.WholeCanvas to IntSize(1000, 300),
                ExportArea.VisibleArea to IntSize(1569, 941),
                ExportArea.Selection to IntSize(360, 230),
            ),
        ),
    )

    private fun editor(name: String, w: Int, h: Int, dark: Boolean, state: EditorUiState) {
        shotOf(name, w, h, dark, state)
    }

    private fun shotOf(name: String, w: Int, h: Int, dark: Boolean, state: EditorUiState): Bitmap {
        val e = InMemoryEditor(state)
        return compose.shot("editor-$name", w, h, dark) { Editor(e.state, e) { DotGrid() } }
    }

    /** Each palette chip's centre pixel, where it is in the window, is the colour that chip draws in the theme. */
    private fun assertChipsShowDrawnColours(shot: Bitmap, dark: Boolean) {
        for (hex in Palettes.flatMap { it.second }) {
            val b = compose.onNodeWithContentDescription(ColourMath.toHex(hex.toInt())).fetchSemanticsNode().boundsInRoot
            if (b.center.y >= shot.height) continue
            val got = shot.getPixel(b.center.x.toInt(), b.center.y.toInt())
            val want = if (dark) InkColors.darkMode(hex.toInt()) else hex.toInt()
            val close = listOf(16, 8, 0).all { kotlin.math.abs(((want shr it) and 0xFF) - ((got shr it) and 0xFF)) <= 2 }
            assertTrue("chip %06X: want %08X, got %08X".format(hex and 0xFFFFFF, want, got), close)
        }
    }

    /** The wheel at a few hues and saturations shows those wheel colours as they draw in the theme. */
    private fun assertWheelShowsDrawnColours(shot: Bitmap, dark: Boolean) {
        val b = compose.onNodeWithContentDescription("Colour wheel").fetchSemanticsNode().boundsInRoot
        val r = b.width / 2
        for (h in listOf(30f, 120f, 210f, 300f)) for (s in listOf(0.4f, 0.8f)) {
            val (dx, dy) = ColourMath.wheelOffset(h, s, r)
            val got = shot.getPixel((b.center.x + dx).toInt(), (b.center.y + dy).toInt())
            val stored = ColourMath.hsvToRgb(h, s, 1f)
            val want = if (dark) InkColors.darkMode(stored) else stored
            val close = listOf(16, 8, 0).all { kotlin.math.abs(((want shr it) and 0xFF) - ((got shr it) and 0xFF)) <= 12 }
            assertTrue("wheel h $h s $s: want %08X, got %08X".format(want, got), close)
        }
    }

    private val panelOnNavy = base.copy(colourPanelOpen = true, color = Argb(0xFF1B3F8F.toInt()))

    @Test @Config(qualifiers = LAND)
    fun landLight() = editor("land-light", 1569, 941, false, base)

    @Test @Config(qualifiers = LAND)
    fun landDark() = editor("land-dark", 1569, 941, true, base)

    @Test @Config(qualifiers = PORT)
    fun portLight() = editor("port-light", 941, 1569, false, base)

    @Test @Config(qualifiers = PORT)
    fun portDark() = editor("port-dark", 941, 1569, true, base)

    @Test @Config(qualifiers = LAND)
    fun landLightCollapsed() = editor("land-light-collapsed", 1569, 941, false, base.copy(toolbarCollapsed = true, cornerCollapsed = true))

    @Test @Config(qualifiers = LAND)
    fun landDarkCollapsed() = editor("land-dark-collapsed", 1569, 941, true, base.copy(toolbarCollapsed = true, cornerCollapsed = true))

    @Test @Config(qualifiers = PORT)
    fun portLightCollapsed() = editor("port-light-collapsed", 941, 1569, false, base.copy(toolbarCollapsed = true, cornerCollapsed = true))

    @Test @Config(qualifiers = PORT)
    fun portDarkCollapsed() = editor("port-dark-collapsed", 941, 1569, true, base.copy(toolbarCollapsed = true, cornerCollapsed = true))

    @Test @Config(qualifiers = LAND)
    fun landColourPanel() = editor("land-light-palette", 1569, 941, false, base.copy(colourPanelOpen = true))

    @Test @Config(qualifiers = LAND)
    fun landColourPanelOnNavy() {
        val shot = shotOf("land-light-palette-navy", 1569, 941, false, panelOnNavy)
        assertChipsShowDrawnColours(shot, dark = false)
        assertWheelShowsDrawnColours(shot, dark = false)
    }

    @Test @Config(qualifiers = LAND)
    fun landColourPanelDarkOnNavy() {
        val shot = shotOf("land-dark-palette-navy", 1569, 941, true, panelOnNavy)
        assertChipsShowDrawnColours(shot, dark = true)
        assertWheelShowsDrawnColours(shot, dark = true)
        compose.onNodeWithText("draws as ${ColourMath.toHex(InkColors.darkMode(0xFF1B3F8F.toInt()))}").assertExists()
        compose.onNodeWithContentDescription("Hex colour").assertTextEquals("#1B3F8F")
    }

    // A phone in portrait has no colour panel; turned to landscape it gets the tablet chrome.
    @Test @Config(qualifiers = PHONE_LAND)
    fun phoneLandColourPanel() = assertChipsShowDrawnColours(shotOf("phone-land-light-palette", 915, 412, false, panelOnNavy), dark = false)

    @Test @Config(qualifiers = PHONE_LAND)
    fun phoneLandColourPanelDark() = assertChipsShowDrawnColours(shotOf("phone-land-dark-palette", 915, 412, true, panelOnNavy), dark = true)

    @Test @Config(qualifiers = PORT)
    fun portColourPanelDark() = editor("port-dark-palette", 941, 1569, true, base.copy(colourPanelOpen = true))

    @Test @Config(qualifiers = LAND)
    fun landShapeMenu() = editor("land-light-menu-shape", 1569, 941, false, base.copy(tool = Tool.Shape, openMenu = ToolMenu.Shape))

    @Test @Config(qualifiers = LAND)
    fun landLineMenuDark() = editor("land-dark-menu-line", 1569, 941, true, base.copy(tool = Tool.Line, openMenu = ToolMenu.Line))

    @Test @Config(qualifiers = PORT)
    fun portPenMenu() = editor("port-light-menu-pen", 941, 1569, false, base.copy(openMenu = ToolMenu.Pen))

    @Test @Config(qualifiers = LAND)
    fun landExport() = editor("land-light-export", 1569, 941, false, base.copy(exportOpen = true))

    @Test @Config(qualifiers = LAND)
    fun landExportDark() = editor("land-dark-export", 1569, 941, true, base.copy(exportOpen = true))

    @Test @Config(qualifiers = LAND)
    fun landDockedBottom() = editor(
        "land-light-bottom-palette", 1569, 941, false,
        base.copy(toolbarPlacement = ToolbarPlacement.Docked(Edge.Bottom, 500f), colourPanelOpen = true),
    )

    @Test @Config(qualifiers = PHONE)
    fun phoneDark() = editor("phone-dark", 412, 915, true, base.copy(penMode = PenMode.Hand))

    @Test @Config(qualifiers = PHONE)
    fun phoneLight() = editor("phone-light", 412, 915, false, base.copy(penMode = PenMode.Hand))
}
