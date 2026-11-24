package io.github.kevin77688.uvd

import android.app.Application
import io.github.kevin77688.uvd.download.DownloadService
import io.github.kevin77688.uvd.settings.AppSettings

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        DownloadService.createChannels(this)
    }
}

/** Intent extras shared by activities, the service and notifications. */
object Extras {
    const val LINK = "io.github.kevin77688.uvd.LINK"
    const val AUTO_DOWNLOAD = "io.github.kevin77688.uvd.AUTO_DOWNLOAD"
    const val TAB = "io.github.kevin77688.uvd.TAB"

    const val TAB_DOWNLOAD = 0
    const val TAB_GALLERY = 1
    const val TAB_SETTINGS = 2
}
