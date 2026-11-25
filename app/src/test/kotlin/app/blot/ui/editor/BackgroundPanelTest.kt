package app.blot.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import app.blot.canvas.OutlineCache
import app.blot.canvas.SceneRenderer
import app.blot.canvas.Viewport
import app.blot.core.model.Note
import java.time.Instant
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.blot.core.model.Background
import app.blot.core.model.Pattern
import app.blot.ui.LAND
import app.blot.ui.PHONE
import app.blot.ui.PORT
import app.blot.ui.shot
import app.blot.ui.theme.BlotTheme
import app.blot.ui.theme.colors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Background button and panel, with shots in `app/build/ui-shots/editor-*-background*.png`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackgroundPanelTest {
    @get:Rule
    val compose = createComposeRule()

    private val base = EditorUiState(noteTitle = "Raft", folderName = "Distributed systems", canUndo = true)

    private fun show(editor: InMemoryEditor) {
        compose.setContent {
            BlotTheme(false) {
                Box(Modifier.size(1569.dp, 941.dp)) { EditorChrome(editor.state, editor) { Paper(editor.state.background) } }
            }
        }
    }

    @Test @Config(qualifiers = LAND)
    fun theButtonOpensThePanelAndChoicesApplyAtOnce() {
        val e = InMemoryEditor(base)
        show(e)
        compose.onNodeWithContentDescription("Background").performClick()
        assertTrue(e.state.backgroundPanelOpen)
        compose.onNodeWithContentDescription("No background pattern").assertIsSelected()
        compose.onNodeWithText("Small").assertDoesNotExist()

        compose.onNodeWithContentDescription("Dots background").performClick()
        assertEquals(Background(Pattern.DOTS, 28f), e.state.background)
        compose.onNodeWithContentDescription("Dots background").assertIsSelected()

        compose.onNodeWithText("Large").performClick()
        assertEquals(42f, e.state.background!!.spacing)
        // The preset carries over to another pattern.
        compose.onNodeWithContentDescription("Lines background").performClick()
        assertEquals(Background(Pattern.RULED, 68f), e.state.background)
        compose.onNodeWithText("68 dp").assertExists()

        compose.onNodeWithContentDescription("Spacing").performSemanticsAction(SemanticsActions.SetProgress) { it(30.4f) }
        assertEquals(Background(Pattern.RULED, 30f), e.state.background)
        compose.onNodeWithContentDescription("Spacing").performTouchInput { click(Offset(0f, centerY)) }
        assertEquals(Background.MIN_SPACING, e.state.background!!.spacing)
        compose.onNodeWithContentDescription("Spacing").performTouchInput { click(Offset(width - 1f, centerY)) }
        assertEquals(Background.MAX_SPACING, e.state.background!!.spacing)

        compose.onNodeWithContentDescription("No background pattern").performClick()
        assertNull(e.state.background)
        compose.onNodeWithText("Small").assertDoesNotExist()

        compose.onRoot().performTouchInput { click(Offset(700f, 600f)) }
        assertFalse(e.state.backgroundPanelOpen)
    }

    @Test @Config(qualifiers = LAND)
    fun otherMenusCloseThePanel() {
        val e = InMemoryEditor(base.copy(backgroundPanelOpen = true))
        e.setColourPanelOpen(true)
        assertFalse(e.state.backgroundPanelOpen)
        e.setBackgroundPanelOpen(true)
        assertFalse(e.state.colourPanelOpen)
        e.setExportOpen(true)
        assertFalse(e.state.backgroundPanelOpen)
    }

    @Test
    fun choicesKeepTheSizePreset() {
        assertEquals(listOf(21f, 28f, 42f), BackgroundChoices.presets(Pattern.DOTS))
        assertEquals(listOf(34f, 45f, 68f), BackgroundChoices.presets(Pattern.RULED))
        assertEquals(Background(Pattern.GRID, 21f), BackgroundChoices.choose(Background(Pattern.DOTS, 21f), Pattern.GRID))
        assertEquals(Background(Pattern.RULED, 45f), BackgroundChoices.choose(Background(Pattern.DOTS, 50f), Pattern.RULED))
        assertEquals(Background(Pattern.DOTS, 28f), BackgroundChoices.choose(null, Pattern.DOTS))
        assertNull(BackgroundChoices.choose(Background(Pattern.DOTS), null))
        assertEquals(8f, BackgroundChoices.clampSpacing(2f))
        assertEquals(96f, BackgroundChoices.clampSpacing(400f))
    }

    @Test @Config(qualifiers = LAND)
    fun exportSwitchFollowsTheNotesPattern() {
        val e = InMemoryEditor(base.copy(exportOpen = true))
        show(e)
        compose.onNodeWithText("Background pattern").assertDoesNotExist()
        e.state = e.state.copy(exportInfo = ExportInfo(pattern = Pattern.GRID))
        compose.onNodeWithText("Background pattern").performClick()
        assertTrue(e.state.export.backgroundPattern)
        compose.onNodeWithText("Include the note's dots, grid or lines").assertExists()
    }

    private fun shot(name: String, w: Int, h: Int, dark: Boolean, state: EditorUiState) {
        val e = InMemoryEditor(state)
        compose.shot("editor-$name", w, h, dark) { Editor(e.state, e) { Paper(e.state.background) } }
    }

    private val open = base.copy(backgroundPanelOpen = true)

    @Test @Config(qualifiers = LAND)
    fun landLight() = shot("land-light-background", 1569, 941, false, open.copy(background = Background(Pattern.GRID)))

    @Test @Config(qualifiers = LAND)
    fun landDark() = shot("land-dark-background", 1569, 941, true, open.copy(background = Background(Pattern.DOTS, 42f)))

    @Test @Config(qualifiers = LAND)
    fun landLightNone() = shot("land-light-background-none", 1569, 941, false, open)

    @Test @Config(qualifiers = PORT)
    fun portLight() = shot("port-light-background", 941, 1569, false, open.copy(background = Background(Pattern.RULED, 40f)))

    @Test @Config(qualifiers = PHONE)
    fun phoneLight() = shot("phone-light-background", 412, 915, false, open.copy(background = Background(Pattern.RULED)))

    @Test @Config(qualifiers = PHONE)
    fun phoneDark() = shot("phone-dark-background", 412, 915, true, open.copy(background = Background(Pattern.GRID, 21f)))

    @Test @Config(qualifiers = LAND)
    fun exportDialogWithPattern() = shot(
        "land-light-export-background", 1569, 941, false,
        base.copy(exportOpen = true, export = ExportOptions(backgroundPattern = true), exportInfo = ExportInfo(pattern = Pattern.RULED)),
    )
}

/** Stands in for the canvas: an empty note with [background], drawn by the canvas's renderer at 100%. */
@Composable
private fun Paper(background: Background?) {
    val dark = colors.dark
    val renderer = remember { SceneRenderer(OutlineCache()) }
    Canvas(Modifier.fillMaxSize()) {
        val note = Note("n", created = Instant.EPOCH, modified = Instant.EPOCH, background = background)
        drawIntoCanvas { renderer.draw(it.nativeCanvas, note, Viewport(density = density), size.width.toInt(), size.height.toInt(), dark) }
    }
}

