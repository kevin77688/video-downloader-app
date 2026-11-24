package io.github.kevin77688.uvd.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import android.widget.Toast
import io.github.kevin77688.uvd.Extras
import io.github.kevin77688.uvd.MainActivity
import io.github.kevin77688.uvd.R
import io.github.kevin77688.uvd.core.UserException
import io.github.kevin77688.uvd.core.userMessage
import io.github.kevin77688.uvd.extractor.Extractors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Foreground service that runs downloads one after another, so a share from another app keeps
 * downloading after the (invisible) ShareActivity has finished.
 */
class DownloadService : Service() {

    companion object {
        private const val CHANNEL_PROGRESS = "progress"
        private const val CHANNEL_RESULT = "result"
        private const val NOTIF_PROGRESS = 1
        private var resultId = 100

        /** [link] must contain a supported URL (see [Extractors.find]). */
        fun start(context: Context, link: String) {
            context.startForegroundService(Intent(context, DownloadService::class.java).putExtra(Extras.LINK, link))
        }

        fun createChannels(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannels(
                listOf(
                    NotificationChannel(
                        CHANNEL_PROGRESS,
                        context.getString(R.string.channel_progress),
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                    NotificationChannel(
                        CHANNEL_RESULT,
                        context.getString(R.string.channel_result),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                ),
            )
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
        startForeground(
            NOTIF_PROGRESS,
            progressNotification(getString(R.string.notif_preparing), getString(R.string.status_preparing), null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
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
        try {
            val match = Extractors.find(link) ?: throw UserException(R.string.err_unsupported_link)
            val title = getString(R.string.notif_downloading_from, match.platform.displayName)
            var lastNotify = 0L
            val count = DownloadJob.run(this, match) { message, progress ->
                DownloadBus.status.value = DownloadStatus.Working(match.platform, message, progress)
                val now = SystemClock.elapsedRealtime()
                if (now - lastNotify > 500) {
                    lastNotify = now
                    nm.notify(NOTIF_PROGRESS, progressNotification(title, message, progress))
                }
            }
            DownloadBus.status.value = DownloadStatus.Success(match.platform, count)
            DownloadBus.completed.value++
            notifyResult(
                getString(R.string.notif_complete),
                resources.getQuantityString(R.plurals.saved_files, count, count),
                success = true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.userMessage(this)
            DownloadBus.status.value = DownloadStatus.Failure(message)
            notifyResult(getString(R.string.notif_failed), message, success = false)
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

    private fun progressNotification(title: String, message: String, progress: Float?): Notification =
        Notification.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
            .setContentIntent(openAppIntent(Extras.TAB_DOWNLOAD))
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
            .setContentIntent(openAppIntent(if (success) Extras.TAB_GALLERY else Extras.TAB_DOWNLOAD))
            .build()
        nm.notify(resultId++, n)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
