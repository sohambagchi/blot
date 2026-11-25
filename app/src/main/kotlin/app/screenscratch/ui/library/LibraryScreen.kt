package app.screenscratch.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.screenscratch.ui.components.EllipsisText
import app.screenscratch.ui.components.IconButton
import app.screenscratch.ui.components.OutlineButton
import app.screenscratch.ui.components.PrimaryButton
import app.screenscratch.ui.components.floatingSurface
import app.screenscratch.ui.editor.MessageMillis
import app.screenscratch.ui.editor.Notice
import app.screenscratch.ui.editor.PhoneMaxWidth
import kotlinx.coroutines.delay
import app.screenscratch.ui.icons.Glyph
import app.screenscratch.ui.icons.GlyphIcon
import app.screenscratch.ui.icons.ScratchIcons
import app.screenscratch.ui.settings.SettingsDialog
import app.screenscratch.ui.theme.Elevation
import app.screenscratch.ui.theme.Radii
import app.screenscratch.ui.theme.ScratchType
import app.screenscratch.ui.theme.colors
import kotlin.math.roundToInt

/**
 * The notes library (`Library.dc.html`): folders, search, the notes grid, the new-note button,
 * card menus and settings. Shows a folder prompt instead while no notes folder is usable, and a
 * single-column layout on phones.
 */
@Composable
fun Library(state: LibraryUiState, actions: LibraryActions, modifier: Modifier = Modifier) {
    val c = colors
    val cards = remember { mutableStateMapOf<String, Rect>() }
    val origin = remember { floatArrayOf(0f, 0f) }
    BoxWithConstraints(
        modifier.fillMaxSize().background(c.bg).onGloballyPositioned {
            val p = it.positionInRoot()
            origin[0] = p.x
            origin[1] = p.y
        },
    ) {
        val onBounds = { id: String, r: Rect ->
            val local = r.translate(Offset(-origin[0], -origin[1]))
            if (cards[id] != local) cards[id] = local
        }
        when (val storage = state.storage) {
            is StorageState.Ready -> {
                if (maxWidth < PhoneMaxWidth) CompactLibrary(state, actions, storage.path, onBounds)
                else TabletLibrary(state, actions, storage.path, portrait = maxHeight > maxWidth, onBounds)
            }
            StorageState.NotChosen -> FolderPrompt(
                ScratchIcons.Folder,
                "Choose where your notes live",
                "Pick a folder on this device. Each note is saved there as a plain folder, so a sync tool " +
                    "such as Syncthing can carry your notes to other devices.",
            ) {
                PrimaryButton("Choose folder…", actions::chooseFolder)
            }
            is StorageState.PermissionLost -> FolderPrompt(
                ScratchIcons.Warning,
                "Screen Scratch can no longer open ${storage.path}",
                "Access to the folder was removed, for example by clearing the app's data. Your notes are " +
                    "still there; grant access again to keep using them.",
            ) {
                OutlineButton("Choose another folder", actions::chooseFolder)
                PrimaryButton("Grant access again", actions::regrantFolder)
            }
        }
        val menuFor = state.cardMenu
        val anchor = menuFor?.let { cards[it] }
        if (menuFor != null) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { actions.setCardMenu(null) } })
        }
        if (menuFor != null && anchor != null) {
            AnchoredMenu(anchor) { CardMenu(menuFor, actions) }
        }
        state.message?.let { message ->
            val dismiss by rememberUpdatedState(actions::dismissMessage)
            LaunchedEffect(message) {
                delay(MessageMillis)
                dismiss()
            }
            Notice(message, dismiss, Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp))
        }
        if (state.settingsOpen) {
            SettingsDialog(state.settings, state.storage, actions) { actions.setSettingsOpen(false) }
        }
    }
}

/** Places [content] with its right edge 6 dp inside [card]'s and its top 46 dp below [card]'s top. */
@Composable
private fun AnchoredMenu(card: Rect, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val p = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (card.right - 6.dp.toPx()).roundToInt() - p.width
            val y = (card.top + 46.dp.toPx()).roundToInt()
            p.place(x.coerceAtLeast(0), y.coerceAtMost(constraints.maxHeight - p.height).coerceAtLeast(0))
        }
    }
}

@Composable
private fun TabletLibrary(
    state: LibraryUiState,
    actions: LibraryActions,
    path: String,
    portrait: Boolean,
    onBounds: (String, Rect) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        FolderColumn(state, actions, path, if (portrait) 220.dp else 260.dp)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EllipsisText(
                        state.heading, ScratchType.heading.copy(color = colors.text),
                        Modifier.weight(1f).semantics { heading() },
                    )
                    SearchField(state.query, actions::setQuery, Modifier.width(if (portrait) 180.dp else 260.dp))
                    ThemeButton(actions)
                }
                NotesGrid(state, actions, if (portrait) 2 else 4, onBounds)
            }
            NewNoteLayer(state, actions)
        }
    }
}

@Composable
private fun FolderColumn(state: LibraryUiState, actions: LibraryActions, path: String, width: Dp) {
    val c = colors
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .drawBehind { drawRect(c.border, Offset(size.width - 1.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height)) }
            .padding(start = 12.dp, end = 13.dp, top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("Screen Scratch", style = ScratchType.brand.copy(color = c.text))
            EllipsisText(path, ScratchType.monoCaption.copy(color = c.muted))
        }
        for (f in state.folders) {
            val on = f.id == state.selectedFolder
            NavRow(
                ScratchIcons.Folder, f.name, onClick = { actions.selectFolder(f.id) },
                selected = on, count = f.count.toString(),
            )
        }
        NavRow(ScratchIcons.FolderPlus, "New folder", onClick = actions::newFolder, muted = true)
        Spacer(Modifier.weight(1f))
        Column(Modifier.drawBehind { drawRect(c.border, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) }.padding(top = 9.dp)) {
            NavRow(ScratchIcons.Settings, "Settings", onClick = { actions.setSettingsOpen(true) }, selected = state.settingsOpen)
        }
    }
}

@Composable
private fun NavRow(
    glyph: Glyph,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    count: String? = null,
    muted: Boolean = false,
) {
    val c = colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(Radii.menuItem))
            .background(if (selected) c.selected else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphIcon(glyph, c.muted, size = 18.dp)
        val style = if (selected) ScratchType.bodyStrong else ScratchType.body
        BasicText(
            label, Modifier.weight(1f).padding(vertical = 4.dp), style = style.copy(color = if (muted) c.muted else c.text),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (count != null) BasicText(count, style = ScratchType.caption.copy(color = c.muted))
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = colors
    val field = rememberTextFieldState(query)
    val emit by rememberUpdatedState(onQuery)
    LaunchedEffect(query) {
        if (field.text.toString() != query) field.setTextAndPlaceCursorAtEnd(query)
    }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { emit(it) }
    }
    val shape = RoundedCornerShape(Radii.field)
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlyphIcon(ScratchIcons.Search, c.muted, size = 18.dp)
        BasicTextField(
            state = field,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = ScratchType.body.copy(color = c.text),
            cursorBrush = SolidColor(c.text),
            modifier = Modifier.weight(1f).semantics { contentDescription = "Search notes" },
            decorator = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (field.text.isEmpty()) BasicText("Search notes", style = ScratchType.body.copy(color = c.muted), maxLines = 1)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun ThemeButton(actions: LibraryActions) {
    val c = colors
    val shape = RoundedCornerShape(Radii.field)
    IconButton(
        if (c.dark) ScratchIcons.Sun else ScratchIcons.Moon,
        "Toggle dark mode",
        actions::toggleDark,
        Modifier.clip(shape).background(c.surface).border(1.dp, c.border, shape),
        iconSize = 18.dp,
        radius = Radii.field,
    )
}

@Composable
private fun NotesGrid(state: LibraryUiState, actions: LibraryActions, columns: Int, onBounds: (String, Rect) -> Unit) {
    if (state.notes.isEmpty()) {
        EmptyNotes(state.query)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 104.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.notes, key = { it.id }) { note ->
            NoteCardView(note, state.cardMenu == note.id, actions) { onBounds(note.id, it) }
        }
    }
}

@Composable
private fun EmptyNotes(query: String) {
    val c = colors
    Column(
        Modifier.fillMaxSize().padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        GlyphIcon(if (query.isBlank()) ScratchIcons.Notes else ScratchIcons.Search, c.muted, size = 32.dp, strokeWidth = 1.5f)
        BasicText(
            if (query.isBlank()) "No notes here yet" else "No notes match “${query.trim()}”",
            Modifier.padding(top = 4.dp),
            style = ScratchType.brand.copy(color = c.text, textAlign = TextAlign.Center),
        )
        BasicText(
            if (query.isBlank()) "Tap + to start a note." else "Try other words, or look in All notes.",
            style = ScratchType.body.copy(color = c.muted, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun NewNoteLayer(state: LibraryUiState, actions: LibraryActions) {
    Box(Modifier.fillMaxSize()) {
        if (state.newNoteOpen) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { actions.setNewNoteOpen(false) } })
        }
        NewNoteButton(state.newNoteOpen, actions, Modifier.align(Alignment.BottomEnd).padding(24.dp))
    }
}

/** Phone width: no folder column; folders become chips above the search field. */
@Composable
private fun CompactLibrary(state: LibraryUiState, actions: LibraryActions, path: String, onBounds: (String, Rect) -> Unit) {
    val c = colors
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EllipsisText(state.heading, ScratchType.heading.copy(color = c.text), Modifier.semantics { heading() })
                    EllipsisText(path, ScratchType.monoCaption.copy(color = c.muted))
                }
                ThemeButton(actions)
                Spacer(Modifier.width(8.dp))
                IconButton(ScratchIcons.Settings, "Settings", { actions.setSettingsOpen(true) }, iconSize = 20.dp, tint = c.text)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.folders, key = { it.id ?: "" }) { f ->
                    val on = f.id == state.selectedFolder
                    val shape = RoundedCornerShape(Radii.menuItem)
                    Box(
                        Modifier
                            .height(36.dp)
                            .clip(shape)
                            .background(if (on) c.selected else c.surface)
                            .border(1.dp, if (on) c.selected else c.border, shape)
                            .clickable(role = Role.Button) { actions.selectFolder(f.id) }
                            .semantics { selected = on }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            "${f.name}  ${f.count}",
                            style = (if (on) ScratchType.bodyStrong else ScratchType.body).copy(color = c.text),
                        )
                    }
                }
            }
            SearchField(state.query, actions::setQuery, Modifier.fillMaxWidth())
            NotesGrid(state, actions, 2, onBounds)
        }
        NewNoteLayer(state, actions)
    }
}

/** Shown instead of the library while no notes folder can be used. */
@Composable
private fun FolderPrompt(glyph: Glyph, title: String, body: String, buttons: @Composable () -> Unit) {
    val c = colors
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(460.dp)
                .floatingSurface(RoundedCornerShape(Radii.dialog), Elevation.Bar)
                .padding(29.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(Radii.field)).background(c.chip),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(glyph, c.text, size = 24.dp) }
            BasicText(title, Modifier.padding(top = 4.dp).semantics { heading() }, style = ScratchType.title.copy(color = c.text))
            BasicText(body, style = ScratchType.body.copy(color = c.muted, lineHeight = ScratchType.body.fontSize * 1.5f))
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) { buttons() }
        }
    }
}
