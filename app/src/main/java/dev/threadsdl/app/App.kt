package dev.threadsdl.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dev.threadsdl.app.core.AppSettings

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(DownloadService.CHANNEL_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(DownloadService.CHANNEL_RESULT, "Download results", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }
}

object Extras {
    const val LINK = "dev.threadsdl.app.LINK"
    const val AUTO_DOWNLOAD = "dev.threadsdl.app.AUTO_DOWNLOAD"
    const val TAB = "dev.threadsdl.app.TAB"
}
