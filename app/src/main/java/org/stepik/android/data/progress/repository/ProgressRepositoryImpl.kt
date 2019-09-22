package org.stepik.android.data.progress.repository

import io.reactivex.Completable
import io.reactivex.Single
import org.stepic.droid.util.doCompletableOnSuccess
import org.stepik.android.data.progress.source.ProgressCacheDataSource
import org.stepik.android.data.progress.source.ProgressRemoteDataSource
import org.stepik.android.domain.base.DataSourceType
import org.stepik.android.domain.progress.repository.ProgressRepository
import org.stepik.android.model.Progress
import javax.inject.Inject

class ProgressRepositoryImpl
@Inject
constructor(
    private val progressRemoteDataSource: ProgressRemoteDataSource,
    private val progressCacheDataSource: ProgressCacheDataSource
) : ProgressRepository {
    override fun getProgresses(vararg progressIds: String, dataSourceType: DataSourceType): Single<List<Progress>> {
        val cacheSource = progressCacheDataSource
            .getProgresses(*progressIds)

        return when (dataSourceType) {
            DataSourceType.REMOTE ->
                progressRemoteDataSource
                    .getProgresses(*progressIds)
                    .doCompletableOnSuccess(progressCacheDataSource::saveProgresses)
                    .onErrorResumeNext(cacheSource)

            DataSourceType.CACHE ->
                cacheSource.flatMap { cachedProgresses ->
                    val ids = (progressIds.toList() - cachedProgresses.mapNotNull(Progress::id)).toTypedArray()
                    progressRemoteDataSource
                        .getProgresses(*ids)
                        .doCompletableOnSuccess(progressCacheDataSource::saveProgresses)
                        .map { remoteProgresses -> cachedProgresses + remoteProgresses }
                }

            else ->
                throw IllegalArgumentException("Unsupported source type = $dataSourceType")
        }
    }


    override fun saveProgresses(progresses: List<Progress>): Completable =
        progressCacheDataSource
            .saveProgresses(progresses)
}