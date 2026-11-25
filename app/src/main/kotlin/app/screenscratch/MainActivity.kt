package app.screenscratch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.screenscratch.core.FORMAT_VERSION
import app.screenscratch.pentest.PenTestActivity
import app.screenscratch.ui.preview.UiPreviewActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val text = TextStyle(color = if (isSystemInDarkTheme()) Color(0xFFE8E8E8) else Color(0xFF111111))
            Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BasicText("Screen Scratch (format v$FORMAT_VERSION)", style = text)
                BasicText(
                    "UI preview",
                    Modifier.clickable { startActivity(Intent(this@MainActivity, UiPreviewActivity::class.java)) },
                    style = text,
                )
                BasicText(
                    "Pen test",
                    Modifier.clickable { startActivity(Intent(this@MainActivity, PenTestActivity::class.java)) },
                    style = text,
                )
            }
        }
    }
}
