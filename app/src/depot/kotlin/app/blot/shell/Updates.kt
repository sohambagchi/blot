package app.blot.shell

import android.content.Context
import android.content.Intent
import com.sohambagchi.depot.Depot

/** depot's "App updates" screen. This copy is built when depot's client is in the build. */
object Updates {
    fun intent(context: Context): Intent? = Depot.updatesIntent(context)
}
