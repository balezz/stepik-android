package org.stepik.android.domain.attempt.repository

import io.reactivex.Single
import org.stepik.android.domain.base.DataSourceType
import org.stepik.android.model.attempts.Attempt

interface AttemptRepository {
    fun createAttemptForStep(stepId: Long): Single<Attempt>
    fun getAttemptsForStep(stepId: Long, userId: Long): Single<List<Attempt>>

    fun getAttempts(vararg attemptIds: Long, dataSourceType: DataSourceType = DataSourceType.REMOTE): Single<List<Attempt>>
}