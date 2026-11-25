package app.screenscratch.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.screenscratch.R

/** Inter 4.1, subset to Latin (scripts/subset-inter.sh); other scripts fall back to the system font. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** The design's type scale; sizes are CSS px = sp. Colours are applied where the text is drawn. */
@Immutable
object ScratchType {
    private val base = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal)
    private val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal)

    /** Library heading (folder name). */
    val heading = base.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    /** Dialog titles. */
    val title = base.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    /** App name in the folder column. */
    val brand = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = base.copy(fontSize = 14.sp)
    val bodyMedium = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val bodyStrong = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val small = base.copy(fontSize = 13.sp)
    val smallStrong = base.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val caption = base.copy(fontSize = 12.sp)
    val captionMedium = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val captionStrong = base.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val monoCaption = mono.copy(fontSize = 12.sp)
    val monoSmall = mono.copy(fontSize = 13.sp)
}
