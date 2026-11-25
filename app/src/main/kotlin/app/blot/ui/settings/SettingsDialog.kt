package app.blot.ui.settings

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blot.R
import app.blot.ui.components.IconButton
import app.blot.ui.components.ModalCard
import app.blot.ui.components.OutlineButton
import app.blot.ui.components.Rule
import app.blot.ui.components.Segment
import app.blot.ui.components.Segmented
import app.blot.ui.components.SwitchRow
import app.blot.ui.icons.GlyphIcon
import app.blot.ui.icons.BlotIcons
import app.blot.ui.library.StorageState
import app.blot.ui.theme.BlotType
import app.blot.ui.theme.ThemeSetting
import app.blot.ui.theme.colors

/** A third-party component shipped in the app and its licence. */
data class Licence(val component: String, val licence: String, val note: String = "")

/** What the APK contains besides our own MIT code. */
val Licences = listOf(
    Licence("Inter typeface 4.1", "SIL Open Font License 1.1", "Subset to Latin; full licence below"),
    Licence("AndroidX: Compose UI and Foundation, Activity, Core, Graphics, Input", "Apache License 2.0"),
    Licence("Kotlin standard library and kotlinx.serialization", "Apache License 2.0"),
    Licence("commonmark-java and its strikethrough and task-list extensions", "BSD 2-Clause"),
    Licence("Latin Modern Math 1.959, as Blot Math", "GUST Font License (LPPL 1.3c)", "Subset for maths; full licence below"),
    Licence("AndroidMath and iosMath typesetter", "MIT", "Ported for maths; full licence below"),
)

/** Full licence texts shown under the list, in [Licences] order. */
private val LicenceTexts = listOf(R.raw.licence_inter_ofl, R.raw.licence_latin_modern_math, R.raw.licence_math_mit)

/**
 * The settings dialog (`Library.dc.html`, settings): storage folder, theme, the pen's side
 * buttons, and About with the licences. "Pen only" lives in the editor's colour panel.
 */
@Composable
fun SettingsDialog(
    settings: SettingsUiState,
    storage: StorageState,
    actions: SettingsActions,
    onClose: () -> Unit,
) {
    var licences by remember { mutableStateOf(false) }
    ModalCard(onClose, 480.dp, "Settings") {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 21.dp, end = 21.dp, top = 9.dp, bottom = 21.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Header(if (licences) "Licences" else "Settings", if (licences) ({ licences = false }) else null, onClose)
            if (licences) LicenceList() else SettingsBody(settings, storage, actions) { licences = true }
        }
    }
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)?, onClose: () -> Unit) {
    val c = colors
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(BlotIcons.Back, "Back to settings", onBack, Modifier.offset(x = (-10).dp), iconSize = 20.dp, tint = c.muted)
        }
        BasicText(title, Modifier.weight(1f).semantics { heading() }, style = BlotType.title.copy(color = c.text))
        IconButton(
            BlotIcons.Close, if (onBack == null) "Close settings" else "Close", onClose,
            Modifier.offset(x = 10.dp), iconSize = 20.dp, strokeWidth = 2f, tint = c.muted,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(text, Modifier.padding(top = 4.dp), style = BlotType.captionStrong.copy(color = colors.muted))
}

@Composable
private fun SettingsBody(settings: SettingsUiState, storage: StorageState, actions: SettingsActions, onLicences: () -> Unit) {
    val c = colors
    var developer by remember { mutableStateOf(false) }
    SectionLabel("Storage")
    Row(
        Modifier.padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("Storage folder", style = BlotType.body.copy(color = c.text))
            val (path, colour) = when (storage) {
                StorageState.NotChosen -> "Not chosen" to c.muted
                is StorageState.Ready -> storage.path to c.muted
                is StorageState.PermissionLost -> "${storage.path} (access lost)" to c.danger
            }
            BasicText(path, style = BlotType.monoCaption.copy(color = colour))
        }
        if (storage !is StorageState.NotChosen) {
            OutlineButton("Forget", actions::forgetFolder, height = 40.dp, radius = 10.dp, horizontalPadding = 14.dp)
        }
        OutlineButton(
            if (storage is StorageState.NotChosen) "Choose…" else "Change…", actions::chooseFolder,
            height = 40.dp, radius = 10.dp, horizontalPadding = 14.dp,
        )
    }
    BasicText(
        "Synced by your own tool (e.g. Syncthing). Each note is a plain folder.",
        Modifier.padding(bottom = 8.dp),
        style = BlotType.caption.copy(color = c.muted),
    )
    Rule(Modifier.padding(vertical = 4.dp))
    SectionLabel("Appearance")
    Segmented(
        listOf(Segment(ThemeSetting.Light, "Light"), Segment(ThemeSetting.Dark, "Dark"), Segment(ThemeSetting.System, "System")),
        settings.theme,
        actions::setTheme,
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
        raised = false,
    )
    Rule(Modifier.padding(vertical = 4.dp))
    SectionLabel("Pen")
    SwitchRow(
        "Swap side buttons",
        settings.swapSideButtons,
        { actions.setSwapSideButtons(!settings.swapSideButtons) },
        Modifier.padding(vertical = 6.dp),
        hint = if (settings.swapSideButtons) "Primary switches to the pointer; secondary erases while held"
        else "Primary erases while held; secondary switches to the pointer",
        minHeight = 56.dp,
    )
    Rule(Modifier.padding(vertical = 4.dp))
    SectionLabel("About")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).combinedClickable(
            role = Role.Button,
            onLongClickLabel = "Show developer options",
            onLongClick = { developer = true },
            onClick = onLicences,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                listOf("Blot", settings.version).filter { it.isNotEmpty() }.joinToString(" "),
                style = BlotType.body.copy(color = c.text),
            )
            BasicText("MIT licence · Open-source licences", style = BlotType.caption.copy(color = c.muted))
        }
        GlyphIcon(BlotIcons.ChevronRight, c.muted, size = 18.dp, strokeWidth = 2f)
    }
    if (developer) DeveloperSection(settings, actions)
}

/** Hidden until the About row is long-pressed: the debug screens and the front-buffer mode. */
@Composable
private fun DeveloperSection(settings: SettingsUiState, actions: SettingsActions) {
    val c = colors
    Rule(Modifier.padding(vertical = 4.dp))
    SectionLabel("Developer")
    SwitchRow(
        "Front buffer holds the whole note",
        settings.frontBufferWholeNote,
        { actions.setFrontBufferWholeNote(!settings.frontBufferWholeNote) },
        Modifier.padding(vertical = 6.dp),
        hint = if (settings.frontBufferWholeNote) "The note is re-rendered into the ink buffer after each pan; chrome over the canvas hides while writing"
        else "Only ink goes into the buffer, over the canvas; chrome stays visible while writing",
        minHeight = 56.dp,
    )
    Row(Modifier.padding(top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlineButton("Pen test", { actions.openDebugScreen(DebugScreen.PenTest) }, height = 40.dp, radius = 10.dp, horizontalPadding = 14.dp)
        OutlineButton("Canvas test", { actions.openDebugScreen(DebugScreen.CanvasTest) }, height = 40.dp, radius = 10.dp, horizontalPadding = 14.dp)
        OutlineButton("UI preview", { actions.openDebugScreen(DebugScreen.UiPreview) }, height = 40.dp, radius = 10.dp, horizontalPadding = 14.dp)
    }
    BasicText("Needs confirming on the tablet: which buffer mode feels faster there.", style = BlotType.caption.copy(color = c.muted))
}

@Composable
private fun LicenceList() {
    val c = colors
    val context = LocalContext.current
    val texts = remember {
        LicenceTexts.map { id ->
            runCatching { context.resources.openRawResource(id).bufferedReader().use { it.readText() } }.getOrDefault("")
        }
    }
    BasicText(
        "Blot is MIT licensed. It includes:",
        Modifier.padding(bottom = 8.dp),
        style = BlotType.body.copy(color = c.text),
    )
    for (l in Licences) {
        Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(l.component, style = BlotType.body.copy(color = c.text))
            BasicText(
                listOf(l.licence, l.note).filter { it.isNotEmpty() }.joinToString(" · "),
                style = BlotType.caption.copy(color = c.muted),
            )
        }
    }
    for (text in texts) {
        Rule(Modifier.padding(vertical = 8.dp))
        BasicText(text, style = BlotType.monoCaption.copy(color = c.muted))
    }
}
