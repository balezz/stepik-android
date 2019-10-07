package org.stepic.droid.persistence.downloads.progress

import org.stepic.droid.persistence.files.ExternalStorageManager
import org.stepic.droid.persistence.model.SystemDownloadRecord
import org.stepic.droid.persistence.model.PersistentItem
import org.stepic.droid.persistence.model.DownloadProgress
import org.stepic.droid.persistence.model.PersistentState
import java.io.File
import kotlin.math.max

/**
 * Download progress priority:
 * in progress - if any is in progress
 * pending - if all is pending
 * not_cached - if any not completed
 * cached - if all is completed
 */
internal fun countItemProgress(
    externalStorageManager: ExternalStorageManager,
    persistentItems: List<PersistentItem>,
    downloadRecords: List<SystemDownloadRecord>,
    itemState: PersistentState.State
): DownloadProgress.Status {
    if (persistentItems.isEmpty()) {
        return when(itemState) {
            PersistentState.State.NOT_CACHED  -> DownloadProgress.Status.NotCached
            PersistentState.State.IN_PROGRESS -> DownloadProgress.Status.Pending
            PersistentState.State.CACHED      -> DownloadProgress.Status.Cached(bytesTotal = 0)
        }
    }

    var hasItemsInProgress = false
    var hasItemsInTransfer = false
    var hasUndownloadedItems = false
    var hasCompletedItems = false

    var bytesTotal = 0L

    val progress = persistentItems.sumByDouble { item ->
        when(item.status) {
            PersistentItem.Status.IN_PROGRESS -> {
                hasItemsInProgress = true
                downloadRecords
                        .find { it.id == item.downloadId }
                        ?.takeIf { it.bytesTotal > 0 }
                        ?.let { it.bytesDownloaded.toDouble() / it.bytesTotal }
                        ?: 0.0
            }

            PersistentItem.Status.COMPLETED -> {
                val filePath = externalStorageManager.resolvePathForPersistentItem(item)
                if (filePath != null) {
                    bytesTotal += File(filePath).length()
                }

                hasCompletedItems = true
                1.0
            }

            PersistentItem.Status.FILE_TRANSFER -> {
                val record = downloadRecords.find { it.id == item.downloadId }
                if (record != null) {
                    bytesTotal += max(record.bytesDownloaded, record.bytesTotal)
                }

                hasItemsInTransfer = true
                1.0
            }

            else -> {
                hasUndownloadedItems = true
                0.0
            }
        }
    }

    return when {
        hasItemsInProgress -> if (progress == 0.0) {
            DownloadProgress.Status.Pending
        } else {
            DownloadProgress.Status.InProgress(progress.toFloat() / persistentItems.size)
        }

        hasItemsInTransfer || itemState == PersistentState.State.IN_PROGRESS ->
            DownloadProgress.Status.Pending

        hasUndownloadedItems || itemState == PersistentState.State.NOT_CACHED ->
            DownloadProgress.Status.NotCached

        else ->
            DownloadProgress.Status.Cached(bytesTotal)
    }
}