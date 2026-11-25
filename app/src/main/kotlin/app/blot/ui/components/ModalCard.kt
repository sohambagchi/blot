package app.blot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.blot.ui.theme.Elevation
import app.blot.ui.theme.Radii
import app.blot.ui.theme.colors

/** A centred modal card over a scrim; tapping the scrim calls [onDismiss]. */
@Composable
fun ModalCard(
    onDismiss: () -> Unit,
    width: Dp,
    title: String,
    content: @Composable (width: Dp) -> Unit,
) {
    val c = colors
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .chromeSurface(RectangleShape)
            .background(c.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        val w = min(width, maxWidth - 48.dp)
        Box(
            Modifier
                .width(w)
                .floatingSurface(RoundedCornerShape(Radii.dialog), Elevation.Dialog)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .semantics { paneTitle = title },
        ) {
            content(w)
        }
    }
}
