package app.blot.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.blot.ui.LAND
import app.blot.ui.components.ChromeBounds
import app.blot.ui.components.LocalChromeBounds
import app.blot.ui.theme.BlotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The chrome reports where it covers the canvas, and keeps the touches that land on it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = LAND)
class ChromeBoundsTest {
    @get:Rule
    val compose = createComposeRule()

    private val bounds = ChromeBounds()
    private var published: List<RoundRect> = emptyList()
    private var canvasDowns = 0

    private fun show(editor: InMemoryEditor) {
        bounds.onChange = { published = it }
        compose.setContent {
            CompositionLocalProvider(LocalChromeBounds provides bounds) {
                BlotTheme(false) {
                    // mdpi: one pixel per dp, so node bounds in dp compare with reported pixels.
                    Box(Modifier.size(1569.dp, 941.dp)) {
                        EditorChrome(editor.state, editor) {
                            Box(
                                Modifier.fillMaxSize().testTag("canvas").pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            if (awaitPointerEvent().changes.any { it.pressed && !it.previousPressed }) canvasDowns++
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun containing(r: Rect): RoundRect? = published.firstOrNull {
        it.left <= r.left + 0.5f && it.top <= r.top + 0.5f && it.right >= r.right - 0.5f && it.bottom >= r.bottom - 0.5f
    }

    private fun boundsOf(node: SemanticsNodeInteraction): Rect = node.getBoundsInRoot().let { Rect(it.left.value, it.top.value, it.right.value, it.bottom.value) }

    @Test
    fun everyFloatingPieceIsReportedAndTheColourPanelComesAndGoes() {
        val e = InMemoryEditor(EditorUiState(noteTitle = "Raft"))
        show(e)
        val closed = published.size
        // Back pill, undo pill, two chips and the toolbar.
        assertEquals(5, closed)
        for (label in listOf("Back to library", "Undo", "Pen tools: Pen", "More colours and stroke width")) {
            val r = boundsOf(compose.onNodeWithContentDescription(label))
            assertTrue("$label at $r in $published", containing(r) != null)
        }
        val pill = containing(boundsOf(compose.onNodeWithContentDescription("Back to library")))!!
        assertEquals(14f, pill.topLeftCornerRadius.x)

        compose.onNodeWithContentDescription("More colours and stroke width").performClick()
        compose.waitForIdle()
        assertEquals(closed + 1, published.size)
        val toggle = boundsOf(compose.onNodeWithText("Pressure"))
        val panel = published.filter { it.width >= 360f && it.height > 200f }.single()
        assertTrue(containing(toggle) == panel)

        e.setColourPanelOpen(false)
        compose.waitForIdle()
        assertEquals(closed, published.size)
    }

    @Test
    fun aTouchOnThePanelsPaddingNeverReachesTheCanvas() {
        val e = InMemoryEditor(EditorUiState(colourPanelOpen = true))
        show(e)
        val panel = published.filter { it.width >= 360f && it.height > 200f }.single()
        compose.onNodeWithTag("canvas").performTouchInput { click(Offset(panel.left + 6f, panel.top + 6f)) }
        compose.onNodeWithTag("canvas").performTouchInput { click(Offset(panel.right - 4f, panel.bottom - 4f)) }
        compose.waitForIdle()
        assertEquals(0, canvasDowns)
        compose.onNodeWithTag("canvas").performTouchInput { click(Offset(panel.left - 40f, panel.bottom + 40f)) }
        compose.waitForIdle()
        assertEquals(1, canvasDowns)
    }

    @Test
    fun reportsAreRelativeToTheOrigin() {
        val b = ChromeBounds()
        var got: List<RoundRect> = emptyList()
        b.onChange = { got = it }
        b.origin = Offset(0f, 100f)
        assertEquals(emptyList<RoundRect>(), got)
        val e = InMemoryEditor(EditorUiState())
        compose.setContent {
            CompositionLocalProvider(LocalChromeBounds provides b) {
                BlotTheme(false) { Box(Modifier.size(1569.dp, 941.dp)) { EditorChrome(e.state, e) { Box(Modifier.fillMaxSize()) } } }
            }
        }
        compose.waitForIdle()
        val back = compose.onNodeWithContentDescription("Back to library").getBoundsInRoot()
        assertTrue(got.any { it.top < back.top.value - 99f && it.bottom > back.bottom.value - 101f && it.left <= back.left.value })
        val before = got
        b.origin = Offset.Zero
        assertEquals(before.map { RoundRect(it.left, it.top + 100f, it.right, it.bottom + 100f, it.topLeftCornerRadius) }, got)
        assertEquals(CornerRadius(14f), got.first().topLeftCornerRadius)
    }
}
