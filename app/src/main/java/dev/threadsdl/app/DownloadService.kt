package dev.threadsdl.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import android.widget.Toast
import dev.threadsdl.app.core.DownloadBus
import dev.threadsdl.app.core.DownloadStatus
import dev.threadsdl.app.core.PostDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Foreground service that runs downloads one after another, so a share from Threads keeps
 * downloading after the (invisible) ShareActivity has finished.
 */
class DownloadService : Service() {

    companion object {
        const val CHANNEL_PROGRESS = "progress"
        const val CHANNEL_RESULT = "result"
        private const val NOTIF_PROGRESS = 1
        private var resultId = 100

        fun start(context: Context, link: String) {
            context.startForegroundService(Intent(context, DownloadService::class.java).putExtra(Extras.LINK, link))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = Channel<String>(Channel.UNLIMITED)
    private var pending = 0
    private var lastStartId = 0
    private lateinit var nm: NotificationManager

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        scope.launch {
            for (link in queue) {
                process(link)
                if (--pending == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(lastStartId)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        startForeground(NOTIF_PROGRESS, progressNotification("Preparing download…", null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val link = intent?.getStringExtra(Extras.LINK)
        if (link == null) {
            if (pending == 0) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        } else {
            pending++
            queue.trySend(link)
        }
        return START_NOT_STICKY
    }

    private suspend fun process(link: String) {
        var lastNotify = 0L
        val update: (String, Float?) -> Unit = { message, progress ->
            DownloadBus.status.value = DownloadStatus.Working(message, progress)
            val now = SystemClock.elapsedRealtime()
            if (now - lastNotify > 500) {
                lastNotify = now
                nm.notify(NOTIF_PROGRESS, progressNotification(message, progress))
            }
        }
        try {
            val count = PostDownloader.run(this, link, update)
            DownloadBus.status.value = DownloadStatus.Success(count)
            DownloadBus.completed.value++
            notifyResult("Download complete", if (count == 1) "Saved 1 file" else "Saved $count files", success = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            DownloadBus.status.value = DownloadStatus.Failure(message)
            notifyResult("Download failed", message, success = false)
        }
    }

    private fun openAppIntent(tab: Int): PendingIntent = PendingIntent.getActivity(
        this,
        tab,
        Intent(this, MainActivity::class.java)
            .putExtra(Extras.TAB, tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(message: String, progress: Float?): Notification =
        Notification.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Downloading from Threads")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
            .setContentIntent(openAppIntent(0))
            .build()

    private fun notifyResult(title: String, text: String, success: Boolean) {
        if (!nm.areNotificationsEnabled()) {
            Toast.makeText(this, "$title: $text", Toast.LENGTH_LONG).show()
            return
        }
        val n = Notification.Builder(this, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(if (success) 1 else 0))
            .build()
        nm.notify(resultId++, n)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
