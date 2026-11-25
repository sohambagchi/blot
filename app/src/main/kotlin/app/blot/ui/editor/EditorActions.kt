package app.blot.ui.editor

import app.blot.core.model.Argb
import app.blot.core.model.Background
import app.blot.ui.library.PageKind

/**
 * What the editor chrome asks for. Each call is an intent; the implementation updates
 * [EditorUiState] (and the canvas) in response.
 */
interface EditorActions {
    /** A toolbar tool was tapped while not active. Tapping the active one calls [openMenu] instead. */
    fun selectTool(tool: Tool)
    fun setPenMode(mode: PenMode)
    fun setEraserMode(mode: EraserMode)
    fun setSelectMode(mode: SelectMode)
    fun setShape(kind: ShapeKind)
    fun setLine(kind: LineKind)
    fun setSnapToShapes(on: Boolean)
    fun setSnapAngles(on: Boolean)

    fun setColor(color: Argb)
    fun setStrokeWidth(width: Float)
    fun setPressure(on: Boolean)
    fun setPenOnly(on: Boolean)

    fun undo()
    fun redo()

    /** Opens [menu], or closes any menu when null. */
    fun openMenu(menu: ToolMenu?)
    fun setColourPanelOpen(open: Boolean)

    /** The toolbar was dropped; [placement] is already snapped. */
    fun moveToolbar(placement: ToolbarPlacement)
    /** The drag handle was tapped, or writing ran long enough to collapse the toolbar. */
    fun setToolbarCollapsed(collapsed: Boolean)

    fun setExportOpen(open: Boolean)
    fun setExportOptions(options: ExportOptions)
    fun exportToFolder()
    fun share()

    fun back()
    /** The page-layout chip was tapped: opens or closes the page menu. */
    fun openPageSettings()
    /** The zoom chip was tapped. */
    fun resetZoom()

    fun setPageMenuOpen(open: Boolean) {}
    fun setPageKind(kind: PageKind) {}
    fun setPageLandscape(landscape: Boolean) {}
    fun addPage() {}
    fun removePage() {}

    /** The Background button was tapped (true), or the panel dismissed (false). */
    fun setBackgroundPanelOpen(open: Boolean) {}

    /** The note's background pattern changed; null for none. Applied live, as the panel's controls move. */
    fun setBackground(background: Background?) {}

    /** The notice in [EditorUiState.message] was shown long enough, or tapped. */
    fun dismissMessage() {}

    /** The title was tapped (true), or its edit was cancelled (false) and the title stays as it was. */
    fun setTitleEditing(editing: Boolean) {}

    /** The title edit was committed with [title] as typed; it ends the edit. */
    fun setTitle(title: String) {}

    /** An item of the selection menu was tapped: run it on the selection and close the menu. */
    fun selectionCommand(command: SelectionCommand) {}

    /** The selection menu was dismissed without a choice. */
    fun closeSelectionMenu() {}
}
