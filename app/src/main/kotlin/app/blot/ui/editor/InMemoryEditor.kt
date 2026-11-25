package app.blot.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.blot.core.model.Argb
import app.blot.core.model.Background
import app.blot.core.model.NoteTitle
import app.blot.ui.library.PageKind

/**
 * [EditorActions] that only update an in-memory [EditorUiState]: the UI preview and tests use it
 * as is, and the real editor can wrap it, forwarding tool and colour changes to the canvas.
 * [onBack], [onExport] and [onShare] are the only effects outside the state.
 */
open class InMemoryEditor(
    initial: EditorUiState = EditorUiState(),
    private val autoCollapse: AutoCollapse = AutoCollapse(),
    private val onBack: () -> Unit = {},
    private val onExport: (ExportOptions) -> Unit = {},
    private val onShare: (ExportOptions) -> Unit = {},
) : EditorActions {
    var state by mutableStateOf(initial)

    /**
     * The canvas calls this on pen down and on every pen move, with a monotonic time such as
     * `MotionEvent.eventTime`. Collapses the toolbar after continuous writing; never expands it.
     */
    fun onWritingActivity(nowMs: Long) {
        if (autoCollapse.onWritingActivity(nowMs) && !(state.toolbarCollapsed && state.cornerCollapsed)) {
            setToolbarCollapsed(true)
        }
    }

    private inline fun update(f: EditorUiState.() -> EditorUiState) {
        state = state.f()
    }

    override fun selectTool(tool: Tool) = update { copy(tool = tool, openMenu = null) }
    override fun setPenMode(mode: PenMode) = update { copy(penMode = mode, tool = Tool.Pen, openMenu = null) }
    override fun setEraserMode(mode: EraserMode) = update { copy(eraserMode = mode, tool = Tool.Eraser, openMenu = null) }
    override fun setSelectMode(mode: SelectMode) = update { copy(selectMode = mode, tool = Tool.Select, openMenu = null) }
    override fun setShape(kind: ShapeKind) = update { copy(shape = kind, tool = Tool.Shape, openMenu = null) }
    override fun setLine(kind: LineKind) = update { copy(line = kind, tool = Tool.Line, openMenu = null) }
    override fun setSnapToShapes(on: Boolean) = update { copy(snapToShapes = on) }
    override fun setSnapAngles(on: Boolean) = update { copy(snapAngles = on) }
    override fun setColor(color: Argb) = update { copy(color = color) }
    override fun setStrokeWidth(width: Float) = update { copy(strokeWidth = width) }
    override fun setPressure(on: Boolean) = update { copy(pressure = on) }
    override fun setPenOnly(on: Boolean) = update { copy(penOnly = on) }
    override fun undo() {}
    override fun redo() {}
    override fun openMenu(menu: ToolMenu?) = update {
        copy(openMenu = menu, colourPanelOpen = if (menu != null) false else colourPanelOpen, backgroundPanelOpen = if (menu != null) false else backgroundPanelOpen)
    }
    override fun setColourPanelOpen(open: Boolean) = update { copy(colourPanelOpen = open, openMenu = null, backgroundPanelOpen = if (open) false else backgroundPanelOpen) }
    override fun moveToolbar(placement: ToolbarPlacement) = update { copy(toolbarPlacement = placement) }

    override fun setToolbarCollapsed(collapsed: Boolean) {
        if (!collapsed) autoCollapse.reset()
        update { copy(toolbarCollapsed = collapsed, cornerCollapsed = collapsed || cornerCollapsed, openMenu = null, colourPanelOpen = false, backgroundPanelOpen = false) }
    }

    override fun setCornerCollapsed(collapsed: Boolean) = update { copy(cornerCollapsed = collapsed, backgroundPanelOpen = false) }

    override fun setExportOpen(open: Boolean) = update { copy(exportOpen = open, openMenu = null, colourPanelOpen = false, backgroundPanelOpen = false) }
    override fun setExportOptions(options: ExportOptions) = update { copy(export = options) }
    override fun exportToFolder() = onExport(state.export)
    override fun share() = onShare(state.export)
    override fun back() = onBack()
    override fun openPageSettings() = setPageMenuOpen(!state.pageMenuOpen)
    override fun resetZoom() = update { copy(zoomPercent = 100) }

    override fun setPageMenuOpen(open: Boolean) =
        update {
            copy(
                pageMenuOpen = open, openMenu = if (open) null else openMenu, colourPanelOpen = if (open) false else colourPanelOpen,
                backgroundPanelOpen = if (open) false else backgroundPanelOpen,
            )
        }
    override fun setPageKind(kind: PageKind) = update { copy(pageKind = kind) }
    override fun setPageLandscape(landscape: Boolean) = update { copy(pageLandscape = landscape) }
    override fun addPage() = update { copy(pageCount = pageCount + 1) }
    override fun removePage() = update { copy(pageCount = (pageCount - 1).coerceAtLeast(1)) }
    override fun setBackgroundPanelOpen(open: Boolean) = update {
        if (open) copy(backgroundPanelOpen = true, openMenu = null, colourPanelOpen = false, pageMenuOpen = false) else copy(backgroundPanelOpen = false)
    }
    override fun setBackground(background: Background?) = update { copy(background = background) }
    override fun dismissMessage() = update { copy(message = null) }
    override fun setTitleEditing(editing: Boolean) = update { copy(titleEditing = editing) }
    override fun setTitle(title: String) = update { copy(noteTitle = NoteTitle.clean(title), titleEditing = false) }
    override fun selectionCommand(command: SelectionCommand) = closeSelectionMenu()
    override fun closeSelectionMenu() = update { copy(selectionMenu = null) }
}
