package app.screenscratch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.BasicText
import app.screenscratch.core.FORMAT_VERSION

// Placeholder shell so the build and test harness have something to run.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BasicText("Screen Scratch (format v$FORMAT_VERSION)") }
    }
}
