package io.github.kevin77688.uvd

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import io.github.kevin77688.uvd.download.DownloadService
import io.github.kevin77688.uvd.extractor.Extractors
import io.github.kevin77688.uvd.settings.AppSettings

/** Invisible target of the system share sheet: queues a background download and closes. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString
        val match = Extractors.find(text)
        when {
            match == null ->
                Toast.makeText(this, R.string.toast_no_supported_link, Toast.LENGTH_SHORT).show()
            !AppSettings.hasValidFolder(this) ->
                // First run: the main screen asks for a folder, then downloads.
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(Extras.LINK, match.url)
                        .putExtra(Extras.AUTO_DOWNLOAD, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            else -> {
                DownloadService.start(this, match.url)
                Toast.makeText(this, getString(R.string.toast_downloading, match.platform.displayName), Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
