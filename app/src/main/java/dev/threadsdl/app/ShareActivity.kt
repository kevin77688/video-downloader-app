package dev.threadsdl.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dev.threadsdl.app.core.AppSettings
import dev.threadsdl.app.core.LinkParser

/** Invisible target of the system share sheet: queues a background download and closes. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString
        val link = LinkParser.find(text)
        when {
            link == null ->
                Toast.makeText(this, "No Threads link found", Toast.LENGTH_SHORT).show()
            !AppSettings.hasValidFolder(this) ->
                // First run: the main screen asks for a folder, then downloads.
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(Extras.LINK, link)
                        .putExtra(Extras.AUTO_DOWNLOAD, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            else -> {
                DownloadService.start(this, link)
                Toast.makeText(this, "Downloading…", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
