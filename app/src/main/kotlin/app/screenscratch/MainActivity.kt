package app.screenscratch

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.screenscratch.canvastest.CanvasTestActivity
import app.screenscratch.pentest.PenTestActivity
import app.screenscratch.shell.AppModel
import app.screenscratch.shell.ScreenScratchApp
import app.screenscratch.storage.NoteFolder
import app.screenscratch.ui.preview.UiPreviewActivity
import app.screenscratch.ui.settings.DebugScreen

/** The app's single activity: library and editor, switched by [AppModel.screen]. */
class MainActivity : ComponentActivity() {
    /** The app's state; tests read it. */
    lateinit var app: AppModel
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val folder = NoteFolder(applicationContext)
        app = AppModel(applicationContext, folder, version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty())
        val picker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree(), app::onTreePicked)
        app.pickFolder = { picker.launch(it) }
        app.openDebug = { screen ->
            val cls = when (screen) {
                DebugScreen.PenTest -> PenTestActivity::class.java
                DebugScreen.CanvasTest -> CanvasTestActivity::class.java
                DebugScreen.UiPreview -> UiPreviewActivity::class.java
            }
            startActivity(Intent(this, cls))
        }
        app.screenSizeDp = { resources.configuration.let { it.screenWidthDp.toFloat() to it.screenHeightDp.toFloat() } }
        enableEdgeToEdge()
        setContent { ScreenScratchApp(app, ::applyBarStyle) }
    }

    override fun onResume() {
        super.onResume()
        app.onResume()
    }

    override fun onStop() {
        app.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        app.dispose()
        super.onDestroy()
    }

    private fun applyBarStyle(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(style, style)
    }
}
