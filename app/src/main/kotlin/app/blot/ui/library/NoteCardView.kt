package app.blot.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blot.ui.components.EllipsisText
import app.blot.ui.components.IconButton
import app.blot.ui.components.Rule
import app.blot.ui.components.boxShadow
import app.blot.ui.components.floatingSurface
import app.blot.ui.icons.Glyph
import app.blot.ui.icons.GlyphIcon
import app.blot.ui.icons.BlotIcons
import app.blot.ui.theme.Elevation
import app.blot.ui.theme.Radii
import app.blot.ui.theme.BlotType
import app.blot.ui.theme.colors

internal fun PageKind.label() = when (this) {
    PageKind.Infinite -> "Infinite canvas"
    PageKind.Screen -> "Screen"
    PageKind.A4 -> "A4"
    PageKind.Letter -> "Letter"
}

/** A note card: thumbnail, page chip, menu button, title and date. [onBounds] reports where it sits, for its menu. */
@Composable
internal fun NoteCardView(
    note: NoteCard,
    menuOpen: Boolean,
    actions: LibraryActions,
    onBounds: (Rect) -> Unit,
) {
    val c = colors
    val shape = RoundedCornerShape(Radii.card)
    Box(
        Modifier
            .fillMaxWidth()
            .height(196.dp)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .clickable(role = Role.Button, onClickLabel = "Open") { actions.openNote(note.id) }
            .semantics { contentDescription = "Open ${note.title}" },
    ) {
        Box(Modifier.offset(y = 46.dp).fillMaxWidth().height(100.dp)) {
            val thumb = note.thumbnail
            if (thumb != null) {
                Image(thumb, null, Modifier.fillMaxWidth().height(100.dp), contentScale = ContentScale.Fit)
            } else {
                ThumbnailPlaceholder()
            }
        }
        PageChip(note, Modifier.padding(10.dp))
        IconButton(
            BlotIcons.Kebab, "More actions for ${note.title}", { actions.setCardMenu(if (menuOpen) null else note.id) },
            Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp),
            iconSize = 20.dp, strokeWidth = 2.6f, tint = c.muted,
            background = if (menuOpen) c.selected else Color.Transparent,
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EllipsisText(note.title, BlotType.bodyStrong.copy(color = c.text), Modifier.weight(1f).alignByBaseline())
            BasicText(note.date, Modifier.alignByBaseline(), style = BlotType.caption.copy(color = c.muted), maxLines = 1)
        }
    }
}

/** Three faint ruled lines where a thumbnail would be. */
@Composable
private fun ThumbnailPlaceholder() {
    val c = colors
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val w = size.width
        val stroke = 2.dp.toPx()
        val left = w * 0.18f
        for ((i, frac) in listOf(0.64f, 0.5f, 0.36f).withIndex()) {
            val y = size.height * (0.3f + i * 0.2f)
            drawLine(c.border, Offset(left, y), Offset(left + w * frac, y), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun PageChip(note: NoteCard, modifier: Modifier) {
    val c = colors
    Row(
        modifier
            .height(26.dp)
            .clip(RoundedCornerShape(Radii.chip))
            .background(c.chip)
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = note.kind.label() +
                    if (note.kind == PageKind.Infinite) "" else ", ${note.pages} page${if (note.pages == 1) "" else "s"}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PageGlyph(note.kind, c.chipText, small = true)
        if (note.kind != PageKind.Infinite) {
            Spacer(Modifier.width(1.dp).height(12.dp).background(c.chipText.copy(alpha = 0.4f)))
            BasicText(note.pages.toString(), style = BlotType.captionStrong.copy(color = c.chipText))
        }
    }
}

/** The page-kind mark: a glyph for infinite and screen pages, "A4" or "LTR" for paper. */
@Composable
internal fun PageGlyph(kind: PageKind, tint: Color, small: Boolean) {
    val glyph: Glyph? = when (kind) {
        PageKind.Infinite -> BlotIcons.Infinite
        PageKind.Screen -> BlotIcons.ScreenPage
        else -> null
    }
    if (glyph != null) {
        GlyphIcon(glyph, tint, size = if (small) 16.dp else 22.dp, strokeWidth = if (small) 2f else 1.8f)
    } else {
        val text = if (kind == PageKind.A4) "A4" else "LTR"
        BasicText(text, style = (if (small) BlotType.captionStrong else BlotType.smallStrong).copy(color = tint))
    }
}

/** Where "Move to…" can send [note]: the top level (id null) unless it is there, and every other folder. */
internal fun moveTargets(note: NoteCard, folders: List<FolderItem>): List<FolderItem> =
    (if (note.folder != null) listOf(FolderItem(null, "Top level", 0)) else emptyList()) +
        folders.filter { it.id != null && it.id != note.folder }

/**
 * The card menu: rename, move to another folder, export, delete. With [moving] it lists the
 * folders to move to instead, under a row that leads back.
 */
@Composable
internal fun CardMenu(note: NoteCard, moving: Boolean, folders: List<FolderItem>, actions: LibraryActions, modifier: Modifier = Modifier) {
    val c = colors
    val targets = moveTargets(note, folders)
    fun done(f: () -> Unit) = {
        actions.setCardMenu(null)
        f()
    }
    MenuSurface(modifier) {
        if (moving) {
            MenuRow(BlotIcons.ChevronLeft, "Move to", c.muted) { actions.setMoveMenu(false) }
            Rule(Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (t in targets) MenuRow(BlotIcons.Folder, t.name, c.text, done { actions.moveNote(note.id, t.id) })
            }
        } else {
            MenuRow(BlotIcons.Rename, "Rename", c.text) { actions.setRenaming(note.id) }
            if (targets.isNotEmpty()) {
                Rule(Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                MenuRow(FolderMove, "Move to…", c.text) { actions.setMoveMenu(true) }
            }
            Rule(Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
            MenuRow(BlotIcons.ShareAlt, "Export…", c.text, done { actions.openExport(note.id) })
            Rule(Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
            MenuRow(BlotIcons.Delete, "Delete", c.danger, done { actions.deleteNote(note.id) })
        }
    }
}

/** A folder's menu: rename, and delete (after a confirmation that its notes move out). */
@Composable
internal fun FolderMenu(folder: FolderItem, actions: LibraryActions, modifier: Modifier = Modifier) {
    val c = colors
    val id = folder.id ?: return
    MenuSurface(modifier) {
        MenuRow(BlotIcons.Rename, "Rename", c.text) { actions.setFolderDialog(FolderDialog.Rename(id, folder.name)) }
        Rule(Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
        MenuRow(BlotIcons.Delete, "Delete", c.danger) { actions.setFolderDialog(FolderDialog.Delete(id, folder.name, folder.count)) }
    }
}

private val FolderMove = Glyph(
    "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z",
    "M8 13h7",
    "m12.5 10 3 3-3 3",
)

@Composable
private fun MenuSurface(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .width(184.dp)
            .floatingSurface(RoundedCornerShape(12.dp), Elevation.LibraryMenu)
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
private fun MenuRow(glyph: Glyph, label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphIcon(glyph, color, size = 18.dp)
        EllipsisText(label, BlotType.body.copy(color = color))
    }
}

/** The new-note button and, when open, the four page kinds above it. */
@Composable
internal fun NewNoteButton(open: Boolean, actions: LibraryActions, modifier: Modifier = Modifier) {
    val c = colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (open) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val labels = mapOf(
                    PageKind.Infinite to "New infinite canvas",
                    PageKind.Screen to "New screen-sized page",
                    PageKind.A4 to "New A4 note",
                    PageKind.Letter to "New Letter note",
                )
                for ((kind, label) in labels) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .floatingSurface(RoundedCornerShape(13.dp), Elevation.FabItem)
                            .clickable(role = Role.Button) {
                                actions.setNewNoteOpen(false)
                                actions.newNote(kind)
                            }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        PageGlyph(kind, c.text, small = false)
                    }
                }
            }
        }
        IconButton(
            if (open) BlotIcons.Close else BlotIcons.Plus,
            if (open) "Close new note menu" else "New note",
            { actions.setNewNoteOpen(!open) },
            Modifier.boxShadow(RoundedCornerShape(16.dp), Elevation.Fab, c.shadowStrong),
            size = 56.dp, iconSize = 24.dp, strokeWidth = 2f, radius = 16.dp, background = c.active, tint = c.activeText,
        )
    }
}
