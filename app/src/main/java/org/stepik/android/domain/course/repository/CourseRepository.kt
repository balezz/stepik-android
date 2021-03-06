package org.stepik.android.domain.course.repository

import io.reactivex.Maybe
import io.reactivex.Single
import org.stepik.android.domain.base.DataSourceType
import org.stepik.android.model.Course

interface CourseRepository {
    fun getCourse(courseId: Long, canUseCache: Boolean = true): Maybe<Course>

    fun getCourses(vararg courseIds: Long, primarySourceType: DataSourceType = DataSourceType.CACHE): Single<List<Course>>
}