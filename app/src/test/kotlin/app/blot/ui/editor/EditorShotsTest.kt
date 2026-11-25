package app.blot.ui.editor

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.IntSize
import app.blot.ui.LAND
import app.blot.ui.PHONE
import app.blot.ui.PORT
import app.blot.ui.preview.DotGrid
import app.blot.ui.shot
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
        val e = InMemoryEditor(state)
        compose.shot("editor-$name", w, h, dark) { Editor(e.state, e) { DotGrid() } }
    }

    @Test @Config(qualifiers = LAND)
    fun landLight() = editor("land-light", 1569, 941, false, base)

    @Test @Config(qualifiers = LAND)
    fun landDark() = editor("land-dark", 1569, 941, true, base)

    @Test @Config(qualifiers = PORT)
    fun portLight() = editor("port-light", 941, 1569, false, base)

    @Test @Config(qualifiers = PORT)
    fun portDark() = editor("port-dark", 941, 1569, true, base)

    @Test @Config(qualifiers = LAND)
    fun landLightCollapsed() = editor("land-light-collapsed", 1569, 941, false, base.copy(toolbarCollapsed = true))

    @Test @Config(qualifiers = LAND)
    fun landDarkCollapsed() = editor("land-dark-collapsed", 1569, 941, true, base.copy(toolbarCollapsed = true))

    @Test @Config(qualifiers = PORT)
    fun portLightCollapsed() = editor("port-light-collapsed", 941, 1569, false, base.copy(toolbarCollapsed = true))

    @Test @Config(qualifiers = PORT)
    fun portDarkCollapsed() = editor("port-dark-collapsed", 941, 1569, true, base.copy(toolbarCollapsed = true))

    @Test @Config(qualifiers = LAND)
    fun landColourPanel() = editor("land-light-palette", 1569, 941, false, base.copy(colourPanelOpen = true))

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
