package app.blot.shell

import android.content.Context
import android.content.Intent

/** No updater: this copy is built when depot's client is not in the build, as in public builds. */
object Updates {
    fun intent(context: Context): Intent? = null
}
