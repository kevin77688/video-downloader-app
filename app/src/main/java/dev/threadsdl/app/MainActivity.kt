package dev.threadsdl.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.threadsdl.app.core.AppSettings
import dev.threadsdl.app.core.ThemeMode
import dev.threadsdl.app.ui.IncomingLink
import dev.threadsdl.app.ui.MainScreen
import dev.threadsdl.app.ui.ThreadsTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val incomingLink = MutableStateFlow<IncomingLink?>(null)
    private val requestedTab = MutableStateFlow<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val mode by AppSettings.theme.collectAsState()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ThreadsTheme(dark) {
                MainScreen(incomingLink, requestedTab)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(Extras.LINK)?.let {
            incomingLink.value = IncomingLink(it, intent.getBooleanExtra(Extras.AUTO_DOWNLOAD, false))
        }
        if (intent.hasExtra(Extras.TAB)) requestedTab.value = intent.getIntExtra(Extras.TAB, 0)
    }
}
