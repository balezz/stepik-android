package org.stepic.droid.persistence.model

data class DownloadProgress(
        val id: Long,
        val status: Status
) {
    sealed class Status {
        object NotCached: Status()
        data class Cached(val bytesTotal: Long): Status()
        object Pending: Status()
        data class InProgress(val progress: Float): Status()
    }
}