package app.screenscratch.ui.settings

import androidx.compose.foundation.clickable
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
import app.screenscratch.R
import app.screenscratch.ui.components.IconButton
import app.screenscratch.ui.components.ModalCard
import app.screenscratch.ui.components.OutlineButton
import app.screenscratch.ui.components.Rule
import app.screenscratch.ui.components.Segment
import app.screenscratch.ui.components.Segmented
import app.screenscratch.ui.components.SwitchRow
import app.screenscratch.ui.icons.GlyphIcon
import app.screenscratch.ui.icons.ScratchIcons
import app.screenscratch.ui.library.StorageState
import app.screenscratch.ui.theme.ScratchType
import app.screenscratch.ui.theme.ThemeSetting
import app.screenscratch.ui.theme.colors

/** A third-party component shipped in the app and its licence. */
data class Licence(val component: String, val licence: String, val note: String = "")

/** What the APK contains besides our own MIT code. */
val Licences = listOf(
    Licence("Inter typeface 4.1", "SIL Open Font License 1.1", "Subset to Latin; full licence below"),
    Licence("AndroidX: Compose UI and Foundation, Activity, Core, Graphics, Input", "Apache License 2.0"),
    Licence("Kotlin standard library and kotlinx.serialization", "Apache License 2.0"),
    Licence("commonmark-java and its strikethrough and task-list extensions", "BSD 2-Clause"),
)

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
            IconButton(ScratchIcons.Back, "Back to settings", onBack, Modifier.offset(x = (-10).dp), iconSize = 20.dp, tint = c.muted)
        }
        BasicText(title, Modifier.weight(1f).semantics { heading() }, style = ScratchType.title.copy(color = c.text))
        IconButton(
            ScratchIcons.Close, if (onBack == null) "Close settings" else "Close", onClose,
            Modifier.offset(x = 10.dp), iconSize = 20.dp, strokeWidth = 2f, tint = c.muted,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(text, Modifier.padding(top = 4.dp), style = ScratchType.captionStrong.copy(color = colors.muted))
}

@Composable
private fun SettingsBody(settings: SettingsUiState, storage: StorageState, actions: SettingsActions, onLicences: () -> Unit) {
    val c = colors
    SectionLabel("Storage")
    Row(
        Modifier.padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("Storage folder", style = ScratchType.body.copy(color = c.text))
            val (path, colour) = when (storage) {
                StorageState.NotChosen -> "Not chosen" to c.muted
                is StorageState.Ready -> storage.path to c.muted
                is StorageState.PermissionLost -> "${storage.path} (access lost)" to c.danger
            }
            BasicText(path, style = ScratchType.monoCaption.copy(color = colour))
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
        style = ScratchType.caption.copy(color = c.muted),
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
        hint = if (settings.swapSideButtons) "Primary cycles pen, pan and select; secondary erases while held"
        else "Primary erases while held; secondary cycles pen, pan and select",
        minHeight = 56.dp,
    )
    Rule(Modifier.padding(vertical = 4.dp))
    SectionLabel("About")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onLicences),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                listOf("Screen Scratch", settings.version).filter { it.isNotEmpty() }.joinToString(" "),
                style = ScratchType.body.copy(color = c.text),
            )
            BasicText("MIT licence · Open-source licences", style = ScratchType.caption.copy(color = c.muted))
        }
        GlyphIcon(ScratchIcons.ChevronRight, c.muted, size = 18.dp, strokeWidth = 2f)
    }
}

@Composable
private fun LicenceList() {
    val c = colors
    val context = LocalContext.current
    val ofl = remember {
        runCatching { context.resources.openRawResource(R.raw.licence_inter_ofl).bufferedReader().use { it.readText() } }
            .getOrDefault("")
    }
    BasicText(
        "Screen Scratch is MIT licensed. It includes:",
        Modifier.padding(bottom = 8.dp),
        style = ScratchType.body.copy(color = c.text),
    )
    for (l in Licences) {
        Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(l.component, style = ScratchType.body.copy(color = c.text))
            BasicText(
                listOf(l.licence, l.note).filter { it.isNotEmpty() }.joinToString(" · "),
                style = ScratchType.caption.copy(color = c.muted),
            )
        }
    }
    Rule(Modifier.padding(vertical = 8.dp))
    BasicText(ofl, style = ScratchType.monoCaption.copy(color = c.muted))
}
