package app.blot.ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import app.blot.core.model.Argb
import app.blot.ui.library.PageKind
import app.blot.ui.theme.QuickInk

/**
 * The toolbar's tools. [Shape] and [Line] draw the kind chosen in their menus. [Select] is the
 * pointer, selecting by its [SelectMode]; the pen's pointer button switches to it.
 */
enum class Tool { Pen, Eraser, Select, Shape, Line, Text, Image }

/** What the pen does while [Tool.Pen] is active. */
enum class PenMode { Pen, Hand }

enum class EraserMode { WholeStroke, PartOfStroke }

enum class SelectMode { Lasso, Rectangle }

enum class ShapeKind { Rectangle, RoundedRectangle, Ellipse, Triangle, Diamond, Hexagon, Parallelogram, Cylinder }

enum class LineKind { Line, Arrow, DoubleArrow, Elbow, Curve }

/** The tool groups that open a menu when tapped while already active. */
enum class ToolMenu { Pen, Eraser, Select, Shape, Line }

/** A screen edge the toolbar can dock to. */
enum class Edge { Top, Bottom, Left, Right }

/**
 * Where the toolbar sits, in dp within the editor. A docked toolbar runs along [Docked.edge]
 * (a pill on top and bottom, a rail on left and right) with its centre at [Docked.along] dp
 * from the edge's start; a floating one has its top-left corner at ([Floating.x], [Floating.y]).
 * Positions are clamped into the window when laid out, so they survive rotation.
 */
@Immutable
sealed interface ToolbarPlacement {
    val vertical: Boolean

    data class Docked(val edge: Edge, val along: Float) : ToolbarPlacement {
        override val vertical: Boolean get() = edge == Edge.Left || edge == Edge.Right
    }

    data class Floating(val x: Float, val y: Float, override val vertical: Boolean) : ToolbarPlacement
}

/** What the selection menu's items do; each is one undo step. */
enum class SelectionCommand { BringForward, SendBackward, Group, Ungroup, Duplicate, Delete }

/**
 * The selection menu, open after a long press on the selection. [anchor] is the selection's box
 * in dp within the editor, which the menu sits above (or below, without room above).
 * [canGroup] shows Group, [canUngroup] shows Ungroup in its place.
 */
@Immutable
data class SelectionMenu(
    val anchor: androidx.compose.ui.geometry.Rect,
    val canGroup: Boolean = false,
    val canUngroup: Boolean = false,
)

enum class ExportFormat { Png, Pdf, Svg }

enum class ExportArea { WholeCanvas, VisibleArea, Selection }

enum class ExportBackground { Paper, Transparent }

/** The export dialog's choices. [scale] applies to PNG only; [background] is ignored for PDF. */
@Immutable
data class ExportOptions(
    val format: ExportFormat = ExportFormat.Png,
    val area: ExportArea = ExportArea.WholeCanvas,
    val scale: Int = 2,
    val background: ExportBackground = ExportBackground.Paper,
    val lightColours: Boolean = true,
    val dotGrid: Boolean = false,
)

/**
 * What the export dialog shows besides the options: a [preview] of the chosen area (null shows
 * an empty page), the size in canvas units (dp) of each area, and where saved files go.
 */
@Immutable
data class ExportInfo(
    val preview: ImageBitmap? = null,
    val areaSizes: Map<ExportArea, IntSize> = emptyMap(),
    val destination: String = "Blot/Exports",
)

/**
 * Everything the editor chrome draws. A plain value: the screen renders it and reports intent
 * through [EditorActions]; whoever implements those (the canvas wiring, or [InMemoryEditor])
 * produces the next state.
 */
@Immutable
data class EditorUiState(
    /** The note's title; empty for an untitled note, which shows as "Untitled". */
    val noteTitle: String = "",
    /** The title is being edited in place, in the back pill or the phone's top bar. */
    val titleEditing: Boolean = false,
    val folderName: String = "",
    /** Page layout as shown on the bottom-right chip, e.g. "Infinite canvas" or "A4". */
    val pageLabel: String = "Infinite canvas",
    val zoomPercent: Int = 100,
    /** The page menu above the page chip. */
    val pageMenuOpen: Boolean = false,
    val pageKind: PageKind = PageKind.Infinite,
    /** Page count; ignored for [PageKind.Infinite]. */
    val pageCount: Int = 1,
    val pageLandscape: Boolean = false,

    val tool: Tool = Tool.Pen,
    val penMode: PenMode = PenMode.Pen,
    val eraserMode: EraserMode = EraserMode.WholeStroke,
    val selectMode: SelectMode = SelectMode.Lasso,
    val shape: ShapeKind = ShapeKind.Rectangle,
    val line: LineKind = LineKind.Arrow,
    val snapToShapes: Boolean = true,
    val snapAngles: Boolean = true,

    /** Ink as stored in the note (true colour); dark mode lightens it for display. */
    val color: Argb = QuickInk.Black,
    /** Stroke width in canvas units (dp). */
    val strokeWidth: Float = 2f,
    val pressure: Boolean = true,
    /** Only the stylus draws; fingers pan and zoom. */
    val penOnly: Boolean = true,

    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasSelection: Boolean = false,
    /** The long-press menu over the selection, or null when closed. */
    val selectionMenu: SelectionMenu? = null,

    /** Null until the user moves the toolbar: then it sits where the design puts it for the orientation. */
    val toolbarPlacement: ToolbarPlacement? = null,
    val toolbarCollapsed: Boolean = false,
    /** The corner pills show only Back and Undo; they collapse with the toolbar and expand with their own arrow. */
    val cornerCollapsed: Boolean = false,
    val openMenu: ToolMenu? = null,
    val colourPanelOpen: Boolean = false,

    val exportOpen: Boolean = false,
    val export: ExportOptions = ExportOptions(),
    val exportInfo: ExportInfo = ExportInfo(),

    /** A short notice over the canvas (a failed save, a recovered note); [EditorActions.dismissMessage] clears it. */
    val message: String? = null,
) {
    companion object {
        /** The colour panel's stroke width presets. */
        val WidthPresets = listOf(1f, 2f, 4f, 7f)
    }
}
