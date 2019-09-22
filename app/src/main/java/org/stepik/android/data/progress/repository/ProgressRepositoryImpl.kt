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
    override fun getProgress(progressId: String): Single<Progress> =
        progressRemoteDataSource
            .getProgress(progressId)
            .doCompletableOnSuccess(progressCacheDataSource::saveProgress)
            .onErrorResumeNext(progressCacheDataSource.getProgress(progressId).toSingle())

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
                cacheSource

            else ->
                throw IllegalArgumentException("Unsupported source type = $dataSourceType")
        }
    }


    override fun saveProgresses(progresses: List<Progress>): Completable =
        progressCacheDataSource
            .saveProgresses(progresses)
}