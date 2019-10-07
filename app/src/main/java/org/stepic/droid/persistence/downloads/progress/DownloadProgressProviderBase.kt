package org.stepic.droid.persistence.downloads.progress

import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.rxkotlin.toFlowable
import org.stepic.droid.persistence.files.ExternalStorageManager
import org.stepic.droid.persistence.model.*
import org.stepic.droid.persistence.storage.PersistentStateManager
import org.stepic.droid.persistence.storage.dao.PersistentItemDao
import org.stepic.droid.persistence.storage.dao.SystemDownloadsDao
import org.stepic.droid.util.zip

abstract class DownloadProgressProviderBase<T>(
        private val updatesObservable: Observable<Structure>,
        private val intervalUpdatesObservable: Observable<kotlin.Unit>,

        private val systemDownloadsDao: SystemDownloadsDao,
        private val persistentItemDao: PersistentItemDao,
        private val persistentStateManager: PersistentStateManager,

        private val externalStorageManager: ExternalStorageManager
): DownloadProgressProvider<T> {
    private companion object {
        private fun List<PersistentItem>.getDownloadIdsOfCorrectItems() =
                this.filter { it.status.isCorrect }.map { it.downloadId }.toLongArray()
    }

    override fun getProgress(vararg items: T): Flowable<DownloadProgress> =
            getProgress(*items.map { it.getId() }.toLongArray())

    override fun getProgress(vararg ids: Long): Flowable<DownloadProgress> =
            ids.toFlowable().flatMap(::getItemProgress)

    private fun getItemProgress(itemId: Long) = getItemUpdateObservable(itemId)
            .switchMap {
                intervalUpdatesObservable.startWith(kotlin.Unit)
                        .concatMap { getItemProgressFromDB(itemId) }
                        .takeUntil { it.status is DownloadProgress.Status.Cached || it.status == DownloadProgress.Status.NotCached }
            }.toFlowable(BackpressureStrategy.LATEST)

    private fun getItemProgressFromDB(itemId: Long) =
            Observable.fromCallable {
                persistentStateManager.getState(itemId, persistentStateType)
            }.concatMap { state ->
                when (state) {
                    PersistentState.State.IN_PROGRESS ->
                        Observable.just(DownloadProgress(itemId, DownloadProgress.Status.Pending))
                    else ->
                        getPersistentObservable(itemId)
                                .concatMap(::fetchSystemDownloads)
                                .map { (persistentItems, downloadItems) ->   // count progresses
                                    DownloadProgress(itemId, countItemProgress(externalStorageManager, persistentItems, downloadItems, state))
                                }
                }
            }

    private fun getPersistentObservable(itemId: Long) =
            persistentItemDao.getItems(mapOf(persistentItemKeyFieldColumn to itemId.toString()))

    private fun fetchSystemDownloads(items: List<PersistentItem>) =
            Observable.just(items) zip systemDownloadsDao.get(*items.getDownloadIdsOfCorrectItems())

    private fun getItemUpdateObservable(itemId: Long) =
            updatesObservable.filter { it.keyFieldValue == itemId }.map { kotlin.Unit }.startWith(kotlin.Unit)

    protected abstract fun T.getId(): Long
    protected abstract val Structure.keyFieldValue: Long
    protected abstract val persistentItemKeyFieldColumn: String
    protected abstract val persistentStateType: PersistentState.Type
}