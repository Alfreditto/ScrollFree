package es.tortugaselectronicas.scrollfree

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import es.tortugaselectronicas.scrollfree.data.YtDlpManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the app-wide cleanup of downloaded videos.
 *
 * Videos are considered disposable: they live in the cache directory and are wiped
 * both when the app is (re)started and when it is sent to the background for good.
 */
class ScrollFreeApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Wipe anything left over from a previous session as soon as we start.
        appScope.launch { YtDlpManager.clearVideoCache(this@ScrollFreeApplication) }

        // Wipe again once the app is fully backgrounded (not on a config change).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                appScope.launch { YtDlpManager.clearVideoCache(this@ScrollFreeApplication) }
            }
        })
    }
}
