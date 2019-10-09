package org.stepik.android.domain.course_content.interactor

import com.google.firebase.perf.FirebasePerformance
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles.zip
import io.reactivex.rxkotlin.toObservable
import org.stepic.droid.analytic.AmplitudeAnalytic
import org.stepic.droid.analytic.Analytic
import org.stepic.droid.util.concat
import org.stepic.droid.util.mapToLongArray
import org.stepic.droid.util.plus
import org.stepic.droid.util.reduce
import org.stepik.android.domain.base.DataSourceType
import org.stepik.android.domain.lesson.repository.LessonRepository
import org.stepik.android.domain.progress.mapper.getProgresses
import org.stepik.android.domain.progress.repository.ProgressRepository
import org.stepik.android.domain.section.repository.SectionRepository
import org.stepik.android.domain.unit.repository.UnitRepository
import org.stepik.android.model.Course
import org.stepik.android.model.Progress
import org.stepik.android.model.Section
import org.stepik.android.model.Unit
import org.stepik.android.presentation.course_content.mapper.CourseContentItemMapper
import org.stepik.android.remote.base.chunkedSingleMap
import org.stepik.android.view.course_content.model.CourseContentItem
import javax.inject.Inject

class CourseContentInteractor
@Inject
constructor(
    private val courseObservableSource: Observable<Course>,
    private val sectionRepository: SectionRepository,
    private val unitRepository: UnitRepository,
    private val lessonRepository: LessonRepository,
    private val progressRepository: ProgressRepository,

    private val courseContentItemMapper: CourseContentItemMapper
) {
    fun getCourseContent(shouldSkipStoredValue: Boolean = false): Observable<Pair<Course, List<CourseContentItem>>> =
        courseObservableSource
            .skip(if (shouldSkipStoredValue) 1 else 0)
            .switchMap { course ->
                getEmptySections(course) concat getContent(course)
            }

    private fun getEmptySections(course: Course): Observable<Pair<Course, List<CourseContentItem>>> =
        Observable.just(course to emptyList())

    private fun getContent(course: Course): Observable<Pair<Course, List<CourseContentItem>>> {
        val courseContentLoadingTrace = FirebasePerformance.getInstance().newTrace(Analytic.Traces.COURSE_CONTENT_LOADING)
        courseContentLoadingTrace.putAttribute(AmplitudeAnalytic.Course.Params.COURSE, course.id.toString())
        courseContentLoadingTrace.start()

        return zip(
            progressRepository
                .getProgresses(*listOfNotNull(course.progress).toTypedArray()),
            getSectionsOfCourse(course)
        )
            .flatMap{ (progresses, it) ->
                populateSections(course, progresses.firstOrNull(), it)
            }
            .flatMapObservable { items ->
                Observable.just(course to items) + loadUnits(course, items)
            }
            .doOnComplete {
                courseContentLoadingTrace.stop()
            }
    }

    private fun getSectionsOfCourse(course: Course): Single<List<Section>> =
        sectionRepository
            .getSections(*course.sections ?: longArrayOf(), primarySourceType = DataSourceType.REMOTE)

    private fun populateSections(course: Course, courseProgress: Progress?, sections: List<Section>): Single<List<CourseContentItem>> =
        sections
            .getProgresses()
            .let { progressIds ->
                progressRepository
                    .getProgresses(*progressIds, dataSourceType = DataSourceType.CACHE)
                    .flatMap { sectionProgresses ->
                        val lastViewed = courseProgress?.lastViewed

                        val isProgressesActual =
                            sectionProgresses.isEmpty() ||
                            sectionProgresses.any { progress -> progress.lastViewed == lastViewed }

                        if (isProgressesActual) {
                            Single.just(sectionProgresses)
                        } else {
                            progressRepository
                                .getProgresses(*progressIds, dataSourceType = DataSourceType.REMOTE)
                        }
                    }
                    .map { sectionProgresses ->
                        courseContentItemMapper
                            .mapSectionsWithEmptyUnits(course, sections, sectionProgresses)
                    }
            }

    private fun loadUnits(course: Course, items: List<CourseContentItem>): Observable<Pair<Course, List<CourseContentItem>>> =
        Observable
            .just(courseContentItemMapper.getUnitPlaceholdersIds(items))
            .flatMap { unitIds ->
                val sources = unitIds
                    .asIterable()
                    .chunked(10)
                    .map { getUnits(it.toLongArray()) }

                reduce(sources, items) { newItems, units ->
                    val sectionItems = newItems
                        .filterIsInstance<CourseContentItem.SectionItem>()

                    populateUnits(sectionItems, units)
                        .map { unitItems ->
                            courseContentItemMapper.replaceUnitPlaceholders(newItems, unitItems)
                        }
                }
            }
            .map { course to it }
//            .flatMap(::getUnits)
//            .flatMap { units ->
//                val sectionItems = items
//                    .filterIsInstance<CourseContentItem.SectionItem>()
//
//                populateUnits(sectionItems, units)
//            }
//            .map { unitItems ->
//                course to courseContentItemMapper.replaceUnitPlaceholders(items, unitItems)
//            }

    private fun getUnits(unitIds: LongArray): Single<List<Unit>> =
        unitRepository
            .getUnits(*unitIds, primarySourceType = DataSourceType.CACHE)

    private fun populateUnits(sectionItems: List<CourseContentItem.SectionItem>, units: List<Unit>): Single<List<CourseContentItem.UnitItem>> =
        zip(
            getUnitsProgresses(sectionItems, units),
            lessonRepository
                .getLessons(*units.mapToLongArray(Unit::lesson), primarySourceType = DataSourceType.REMOTE)
        )
            .map { (progresses, lessons) ->
                courseContentItemMapper.mapUnits(sectionItems, units, lessons, progresses)
            }

    private fun getUnitsProgresses(sectionItems: List<CourseContentItem.SectionItem>, units: List<Unit>): Single<List<Progress>> =
        units
            .getProgresses()
            .let { progressIds ->
                if (progressIds.isEmpty()) {
                    Single.just(emptyList())
                } else {
                    progressRepository
                        .getProgresses(*progressIds, dataSourceType = DataSourceType.CACHE)
                        .flatMap { unitProgresses ->
                            val progressesToFetch = mutableListOf<String>()
                            val unitsMap = units.groupBy(Unit::section)

                            sectionItems.forEach { sectionItem ->
                                val sectionUnits = unitsMap[sectionItem.section.id] ?: emptyList()
                                val isProgressesActual =
                                    sectionUnits.isEmpty() ||
                                    sectionUnits.any { unit ->
                                        unitProgresses.any { it.id == unit.progress && sectionItem.progress?.lastViewed == it.lastViewed }
                                    }

                                if (!isProgressesActual) {
                                    progressesToFetch += sectionUnits.getProgresses()
                                }
                            }

                            if (progressesToFetch.isEmpty()) {
                                Single.just(unitProgresses)
                            } else {
                                progressRepository
                                    .getProgresses(*progressesToFetch.toTypedArray(), dataSourceType = DataSourceType.REMOTE)
                                    .map { remoteProgresses ->
                                        unitProgresses.filterNot { it.id in progressesToFetch } + remoteProgresses
                                    }
                            }
                        }
                }
            }
}