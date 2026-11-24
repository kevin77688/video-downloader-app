package io.github.kevin77688.uvd

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
import io.github.kevin77688.uvd.settings.AppSettings
import io.github.kevin77688.uvd.settings.ThemeMode
import io.github.kevin77688.uvd.ui.IncomingLink
import io.github.kevin77688.uvd.ui.MainScreen
import io.github.kevin77688.uvd.ui.AppTheme
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
            AppTheme(dark) {
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
        if (intent.hasExtra(Extras.TAB)) requestedTab.value = intent.getIntExtra(Extras.TAB, Extras.TAB_DOWNLOAD)
    }
}
