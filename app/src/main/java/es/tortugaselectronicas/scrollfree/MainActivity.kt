package es.tortugaselectronicas.scrollfree

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import es.tortugaselectronicas.scrollfree.ui.ScrollFreeApp
import es.tortugaselectronicas.scrollfree.ui.theme.ScrollFreeTheme
import es.tortugaselectronicas.scrollfree.util.UrlUtils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class MainActivity : ComponentActivity() {

    /**
     * Links received via ACTION_SEND / ACTION_VIEW. Emitted into a SharedFlow so the
     * Compose app can react both on cold start and while it is already running
     * (launchMode=singleTask -> onNewIntent).
     */
    private val incomingUrls = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        extractUrl(intent)?.let { incomingUrls.tryEmit(it) }

        setContent {
            ScrollFreeTheme {
                ScrollFreeApp(incomingUrl = incomingUrls as SharedFlow<String>)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractUrl(intent)?.let { incomingUrls.tryEmit(it) }
    }

    /** Extracts a URL from a share/open intent. */
    private fun extractUrl(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND ->
                UrlUtils.extractFirstUrl(intent.getStringExtra(Intent.EXTRA_TEXT))

            Intent.ACTION_VIEW -> intent.dataString

            else -> null
        }
    }
}
