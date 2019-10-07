package org.stepik.android.view.course_content.ui.adapter.delegates.control_bar

import org.stepic.droid.web.storage.model.StorageRecord
import org.stepik.android.domain.personal_deadlines.model.DeadlinesWrapper
import org.stepik.android.model.Course

interface CourseContentControlBarClickListener {
    fun onCreateScheduleClicked()
    fun onChangeScheduleClicked(record: StorageRecord<DeadlinesWrapper>)
    fun onRemoveScheduleClicked(record: StorageRecord<DeadlinesWrapper>)
    fun onExportScheduleClicked()

    fun onDownloadAllClicked(course: Course)
    fun onRemoveAllClicked(course: Course)
}