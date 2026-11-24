package dev.threadsdl.app.core

import kotlinx.coroutines.flow.MutableStateFlow

sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Working(val message: String, val progress: Float?) : DownloadStatus
    data class Success(val count: Int) : DownloadStatus
    data class Failure(val message: String) : DownloadStatus
}

/** Process-wide download state shared between the service and the UI. */
object DownloadBus {
    val status = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)

    /** Incremented after every successful job so the gallery can refresh. */
    val completed = MutableStateFlow(0)
}
