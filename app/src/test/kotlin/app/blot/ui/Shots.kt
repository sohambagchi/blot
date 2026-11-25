package app.blot.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.blot.ui.theme.BlotTheme
import java.io.File

/** Tablet landscape, tablet portrait, phone and phone landscape window sizes in dp. */
const val LAND = "w1569dp-h941dp-land-mdpi"
const val PORT = "w941dp-h1569dp-port-mdpi"
const val PHONE = "w412dp-h915dp-port-mdpi"
const val PHONE_LAND = "w915dp-h412dp-land-mdpi"

/** Renders [content] at [w] × [h] dp (1 px per dp) and saves it as `app/build/ui-shots/<name>.png`. */
fun ComposeContentTestRule.shot(name: String, w: Int, h: Int, dark: Boolean, content: @Composable () -> Unit): Bitmap {
    setContent {
        BlotTheme(dark) {
            Box(Modifier.size(w.dp, h.dp)) { content() }
        }
    }
    waitForIdle()
    val bitmap = onRoot().captureToImage().asAndroidBitmap()
    val dir = File(System.getProperty("user.dir")!!, "build/ui-shots").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return bitmap
}
