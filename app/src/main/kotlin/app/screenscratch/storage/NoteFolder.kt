package app.screenscratch.storage

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * The user-chosen notes folder: asks for it with `ACTION_OPEN_DOCUMENT_TREE`, keeps the
 * persistable read/write grant, remembers the tree URI, and notices when the grant is gone
 * (revoked in settings, app data cleared, or the provider forgot it).
 */
class NoteFolder(
    private val context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
) {
    sealed interface State {
        data object NotChosen : State
        data class Ready(val treeUri: Uri) : State
        /** A folder was chosen but its grant is gone; ask again with [launcher] (pass [treeUri] as the start). */
        data class PermissionLost(val treeUri: Uri) : State
    }

    fun state(): State {
        val uri = prefs.getString(KEY_TREE, null)?.let(Uri::parse) ?: return State.NotChosen
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        return if (granted) State.Ready(uri) else State.PermissionLost(uri)
    }

    /** The folder as a [SafFileTree], or null unless [state] is [State.Ready]. */
    fun tree(): SafFileTree? =
        (state() as? State.Ready)?.let { SafFileTree(context.contentResolver, it.treeUri) }

    /**
     * Takes the persistable grant for a picked [treeUri] and remembers it, releasing the grant of
     * any previously chosen folder (the system caps how many an app may hold).
     */
    fun adopt(treeUri: Uri): State {
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(treeUri, RW)
        val previous = prefs.getString(KEY_TREE, null)?.let(Uri::parse)
        if (previous != null && previous != treeUri) {
            try {
                resolver.releasePersistableUriPermission(previous, RW)
            } catch (_: SecurityException) {
                // Already gone.
            }
        }
        prefs.edit().putString(KEY_TREE, treeUri.toString()).apply()
        return state()
    }

    /** Forgets the folder and releases its grant. Notes on disk are untouched. */
    fun forget() {
        prefs.getString(KEY_TREE, null)?.let(Uri::parse)?.let {
            try {
                context.contentResolver.releasePersistableUriPermission(it, RW)
            } catch (_: SecurityException) {
            }
        }
        prefs.edit().remove(KEY_TREE).apply()
    }

    /**
     * Registers the folder picker; call before the caller is started (e.g. in `onCreate`).
     * [onResult] gets the new state, or the unchanged one if the user backed out.
     */
    fun launcher(caller: ActivityResultCaller, onResult: (State) -> Unit): ActivityResultLauncher<Uri?> =
        caller.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            onResult(if (uri == null) state() else adopt(uri))
        }

    private companion object {
        const val PREFS = "note_folder"
        const val KEY_TREE = "tree_uri"
        const val RW = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
