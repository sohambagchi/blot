package app.screenscratch.ui.editor

import app.screenscratch.core.model.Argb

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
    /** The page-layout chip was tapped. */
    fun openPageSettings()
    /** The zoom chip was tapped. */
    fun resetZoom()
}
