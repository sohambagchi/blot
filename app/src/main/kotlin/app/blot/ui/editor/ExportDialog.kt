package app.blot.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.blot.core.model.NoteTitle
import app.blot.core.model.Pattern
import app.blot.ui.components.IconButton
import app.blot.ui.components.ModalCard
import app.blot.ui.components.OutlineButton
import app.blot.ui.components.PrimaryButton
import app.blot.ui.components.Rule
import app.blot.ui.components.Segment
import app.blot.ui.components.Segmented
import app.blot.ui.components.SwitchRow
import app.blot.ui.components.floatingSurface
import app.blot.ui.icons.BlotIcons
import app.blot.ui.theme.Elevation
import app.blot.ui.theme.Radii
import app.blot.ui.theme.BlotColors
import app.blot.ui.theme.BlotType
import app.blot.ui.theme.colors

/** The size line under the export preview, e.g. "2000 × 600 px". Empty when the area's size is unknown. */
fun exportSizeLabel(options: ExportOptions, sizes: Map<ExportArea, androidx.compose.ui.unit.IntSize>): String {
    val size = sizes[options.area] ?: return ""
    return when (options.format) {
        ExportFormat.Png -> "${size.width * options.scale} × ${size.height * options.scale} px"
        ExportFormat.Pdf -> "1 page, sized to fit"
        ExportFormat.Svg -> "Vector, ${size.width} × ${size.height}"
    }
}

/** The editor's export and share dialog (`Main-export`). */
@Composable
internal fun ExportDialog(state: EditorUiState, actions: EditorActions) = ExportSheet(
    subtitle = listOf(NoteTitle.display(state.noteTitle), state.pageLabel).filter { it.isNotEmpty() }.joinToString(" · "),
    options = state.export,
    info = state.exportInfo,
    areas = ExportArea.entries,
    selectionAvailable = state.hasSelection,
    onOptions = actions::setExportOptions,
    onClose = { actions.setExportOpen(false) },
    onSave = actions::exportToFolder,
    onShare = actions::share,
)

/**
 * The export and share dialog, shared by the editor and the library's card menu. [areas] lists
 * the area choices offered (the group is hidden when there is only one); [selectionAvailable]
 * enables "Selection" when it is offered.
 */
@Composable
fun ExportSheet(
    subtitle: String,
    options: ExportOptions,
    info: ExportInfo,
    areas: List<ExportArea>,
    selectionAvailable: Boolean,
    onOptions: (ExportOptions) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val c = colors
    val o = options
    ModalCard(onClose, 680.dp, "Export and share") { width ->
        val stacked = width < 600.dp
        Column(Modifier.padding(start = 25.dp, end = 25.dp, top = 9.dp, bottom = 25.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText("Export and share", Modifier.semantics { heading() }, style = BlotType.title.copy(color = c.text))
                    BasicText(subtitle, style = BlotType.caption.copy(color = c.muted))
                }
                IconButton(
                    BlotIcons.Close, "Close", onClose,
                    Modifier.offset(x = 10.dp), iconSize = 20.dp, strokeWidth = 2f, tint = c.muted,
                )
            }
            val preview: @Composable () -> Unit = {
                Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportPreview(o, info, c)
                    BasicText(exportSizeLabel(o, info.areaSizes), style = BlotType.caption.copy(color = c.muted))
                }
            }
            val optionsColumn: @Composable (Modifier) -> Unit = { m -> ExportOptionsColumn(o, areas, selectionAvailable, info.pattern != null, onOptions, m) }
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    preview()
                    optionsColumn(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                    preview()
                    optionsColumn(Modifier.weight(1f))
                }
            }
            Column {
                Rule()
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(info.destination, Modifier.weight(1f), style = BlotType.monoCaption.copy(color = c.muted))
                    OutlineButton("Save to folder", onSave, Modifier.padding(top = 12.dp), glyph = BlotIcons.Download)
                    PrimaryButton("Share…", onShare, Modifier.padding(top = 12.dp), glyph = BlotIcons.ShareAlt)
                }
            }
        }
    }
}

@Composable
private fun ExportOptionsColumn(
    o: ExportOptions,
    areas: List<ExportArea>,
    selectionAvailable: Boolean,
    hasPattern: Boolean,
    set: (ExportOptions) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SegmentGroup(
            "Format",
            listOf(Segment(ExportFormat.Png, "PNG"), Segment(ExportFormat.Pdf, "PDF"), Segment(ExportFormat.Svg, "SVG")),
            o.format,
        ) { set(o.copy(format = it)) }
        if (areas.size > 1) {
            val labels = mapOf(
                ExportArea.WholeCanvas to "Whole canvas",
                ExportArea.VisibleArea to "Visible area",
                ExportArea.Selection to "Selection",
            )
            SegmentGroup(
                "Area",
                areas.map { Segment(it, labels.getValue(it), enabled = it != ExportArea.Selection || selectionAvailable) },
                o.area,
            ) { set(o.copy(area = it)) }
        }
        if (o.format == ExportFormat.Png) {
            SegmentGroup("Scale", listOf(Segment(1, "1×"), Segment(2, "2×"), Segment(3, "3×")), o.scale) { set(o.copy(scale = it)) }
        }
        if (o.format != ExportFormat.Pdf) {
            SegmentGroup(
                "Background",
                listOf(Segment(ExportBackground.Paper, "Paper"), Segment(ExportBackground.Transparent, "Transparent")),
                o.background,
            ) { set(o.copy(background = it)) }
        }
        SwitchRow("Light colours", o.lightColours, { set(o.copy(lightColours = !o.lightColours)) }, hint = "Export as it looks in light mode")
        if (hasPattern) {
            SwitchRow(
                "Background pattern", o.backgroundPattern, { set(o.copy(backgroundPattern = !o.backgroundPattern)) },
                hint = "Include the note's dots, grid or lines",
            )
        }
    }
}

@Composable
private fun <T> SegmentGroup(label: String, options: List<Segment<T>>, selected: T, onSelect: (T) -> Unit) {
    val c = colors
    Column(Modifier.semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(label, style = BlotType.captionStrong.copy(color = c.muted))
        Segmented(options, selected, onSelect, Modifier.fillMaxWidth())
    }
}

/** A hint of [pattern] across the preview, at a fixed small spacing. */
private fun DrawScope.exportPreviewPattern(pattern: Pattern, color: Color) {
    val step = 8.dp.toPx()
    when (pattern) {
        Pattern.DOTS -> {
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    drawCircle(color, 1.dp.toPx(), Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        Pattern.GRID, Pattern.RULED -> {
            val rule = if (pattern == Pattern.RULED) step * 1.6f else step
            if (pattern == Pattern.GRID) {
                var x = step
                while (x < size.width) {
                    drawLine(color, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    x += step
                }
            }
            var y = rule
            while (y < size.height) {
                drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                y += rule
            }
        }
    }
}

@Composable
private fun ExportPreview(o: ExportOptions, info: ExportInfo, c: BlotColors) {
    val light = o.lightColours || !c.dark
    val paper = if (light) BlotColors.Light.bg else BlotColors.Dark.bg
    val dot = if (light) BlotColors.Light.dot else BlotColors.Dark.dot
    val transparent = o.background == ExportBackground.Transparent && o.format != ExportFormat.Pdf
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.fillMaxWidth().height(170.dp).clip(shape).border(1.dp, c.border, shape)
            .semantics { contentDescription = "Export preview" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (transparent) {
                drawRect(Color.White)
                val cell = 8.dp.toPx()
                var y = 0
                while (y * cell < size.height) {
                    var x = 0
                    while (x * cell < size.width) {
                        if ((x + y) % 2 == 0) drawRect(Color(0xFFE4E4E4), Offset(x * cell, y * cell), Size(cell, cell))
                        x++
                    }
                    y++
                }
            } else {
                drawRect(paper)
            }
            val pattern = info.pattern
            if (o.backgroundPattern && pattern != null) exportPreviewPattern(pattern, dot)
        }
        info.preview?.let {
            Image(it, null, Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit)
        }
    }
}
