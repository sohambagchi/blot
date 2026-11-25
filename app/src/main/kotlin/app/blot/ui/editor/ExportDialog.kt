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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
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

/** The export and share dialog (`Main-export`). */
@Composable
internal fun ExportDialog(state: EditorUiState, actions: EditorActions) {
    val c = colors
    val o = state.export
    ModalCard({ actions.setExportOpen(false) }, 680.dp, "Export and share") { width ->
        val stacked = width < 600.dp
        Column(Modifier.padding(start = 25.dp, end = 25.dp, top = 9.dp, bottom = 25.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText("Export and share", Modifier.semantics { heading() }, style = BlotType.title.copy(color = c.text))
                    BasicText(
                        listOf(state.noteTitle, state.pageLabel).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = BlotType.caption.copy(color = c.muted),
                    )
                }
                IconButton(
                    BlotIcons.Close, "Close", { actions.setExportOpen(false) },
                    Modifier.offset(x = 10.dp), iconSize = 20.dp, strokeWidth = 2f, tint = c.muted,
                )
            }
            val preview: @Composable () -> Unit = {
                Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportPreview(state, c)
                    BasicText(exportSizeLabel(o, state.exportInfo.areaSizes), style = BlotType.caption.copy(color = c.muted))
                }
            }
            val options: @Composable (Modifier) -> Unit = { m -> ExportOptionsColumn(state, actions, m) }
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    preview()
                    options(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                    preview()
                    options(Modifier.weight(1f))
                }
            }
            Column {
                Rule()
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(state.exportInfo.destination, Modifier.weight(1f), style = BlotType.monoCaption.copy(color = c.muted))
                    OutlineButton("Save to folder", actions::exportToFolder, Modifier.padding(top = 12.dp), glyph = BlotIcons.Download)
                    PrimaryButton("Share…", actions::share, Modifier.padding(top = 12.dp), glyph = BlotIcons.ShareAlt)
                }
            }
        }
    }
}

@Composable
private fun ExportOptionsColumn(state: EditorUiState, actions: EditorActions, modifier: Modifier) {
    val c = colors
    val o = state.export
    val set = actions::setExportOptions
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SegmentGroup(
            "Format",
            listOf(Segment(ExportFormat.Png, "PNG"), Segment(ExportFormat.Pdf, "PDF"), Segment(ExportFormat.Svg, "SVG")),
            o.format,
        ) { set(o.copy(format = it)) }
        SegmentGroup(
            "Area",
            listOf(
                Segment(ExportArea.WholeCanvas, "Whole canvas"),
                Segment(ExportArea.VisibleArea, "Visible area"),
                Segment(ExportArea.Selection, "Selection", enabled = state.hasSelection),
            ),
            o.area,
        ) { set(o.copy(area = it)) }
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
        SwitchRow("Dot grid", o.dotGrid, { set(o.copy(dotGrid = !o.dotGrid)) }, hint = "Include the canvas dots")
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

@Composable
private fun ExportPreview(state: EditorUiState, c: BlotColors) {
    val o = state.export
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
            if (o.dotGrid) {
                val step = 8.dp.toPx()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(dot, 1.dp.toPx(), Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
        }
        state.exportInfo.preview?.let {
            Image(it, null, Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit)
        }
    }
}
