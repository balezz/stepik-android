package org.stepik.android.remote.achievement

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.Observables
import org.stepik.android.domain.achievement.model.AchievementItem
import org.stepic.droid.util.onErrorSafe
import org.stepik.android.data.achievement.source.AchievementRemoteDataSource
import org.stepik.android.model.achievements.Achievement
import org.stepik.android.model.achievements.AchievementProgress
import org.stepik.android.remote.achievement.service.AchievementsService
import javax.inject.Inject
import kotlin.math.min

class AchievementRemoteDataSourceImpl
@Inject
constructor(
    private val achievementsService: AchievementsService
) : AchievementRemoteDataSource {
    override fun getAchievements(userId: Long, count: Int): Single<List<AchievementItem>> =
        getDistinctAchievementKindsOrderedByObtainDate(userId, count).flatMap { kind ->
            getAchievementWithProgressByKind(userId, kind)
        }.toList()

    override fun getAchievement(userId: Long, kind: String): Single<AchievementItem> =
        getAchievementWithProgressByKind(userId, kind).firstOrError()

    private fun getDistinctAchievementKindsOrderedByObtainDate(userId: Long, count: Int = -1): Observable<String> =
        Observable.create { emitter ->
            val kinds = LinkedHashSet<String>()
            var hasNextPage = true
            var page = 1

            paginationLoop@ while (hasNextPage) {
                val response =
                    try {
                        achievementsService.getAchievementProgresses(user = userId, page = page, order = "-obtain_date").blockingGet()
                    } catch (e: Exception) {
                        return@create emitter.onErrorSafe(e)
                    }

                for (item in response.achievementsProgresses) {
                    kinds.add(item.kind)
                    if (kinds.size == count) {
                        break@paginationLoop
                    }
                }

                hasNextPage = response.meta.hasNext
                page = response.meta.page + 1
            }

            hasNextPage = true
            page = 1

            paginationLoop@ while (hasNextPage && (count == -1 || kinds.size < count)) {
                val response =
                    try {
                        achievementsService.getAchievements(page = page).blockingGet()
                    } catch (e: Exception) {
                        return@create emitter.onErrorSafe(e)
                    }

                for (item in response.achievements) {
                    kinds.add(item.kind)
                    if (kinds.size == count) {
                        break@paginationLoop
                    }
                }

                hasNextPage = response.meta.hasNext
                page = response.meta.page + 1
            }

            kinds.forEach(emitter::onNext) // in order to handle errors in correct way
            emitter.onComplete()
        }

    private fun getAllAchievementsByKind(kind: String): Single<List<Achievement>> =
        Observable.range(1, Int.MAX_VALUE)
            .concatMapSingle { achievementsService.getAchievements(kind = kind, page = it) }
            .takeUntil { !it.meta.hasNext }
            .map { it.achievements }
            .reduce(emptyList()) { a, b -> a + b }

    private fun getAchievementWithProgressByKind(userId: Long, kind: String): Observable<AchievementItem> =
        getAllAchievementsByKind(kind)
            .toObservable()
            .flatMap { achievementsList -> Observable.fromIterable(achievementsList) }
            .flatMap {
                Observables.zip(
                    Observable.just(it),
                    achievementsService.getAchievementProgresses(user = userId, achievement = it.id)
                        .map {
                            it.achievementsProgresses.firstOrNull() ?: AchievementProgress.EmptyStub
                        }.toObservable()
                )
            }.toList().map {
                val sorted = it.sortedBy { (achievement, _) -> achievement.targetScore }
                val firstCompleted = sorted.indexOfLast { (_, progress) -> progress.obtainDate != null }
                val level = firstCompleted + 1

                AchievementItem(
                    sorted.getOrNull(firstCompleted)?.first,
                    sorted[min(level, sorted.size - 1)].first,
                    sorted[min(level, sorted.size - 1)].second,
                    currentLevel = level,
                    maxLevel = sorted.size
                )
            }.toObservable()
}