package org.stepik.android.view.course_content.ui.adapter.delegates.unit

import android.graphics.BitmapFactory
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory
import android.support.v4.util.LongSparseArray
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.view_course_content_unit.view.*
import org.stepic.droid.R
import org.stepic.droid.persistence.model.DownloadProgress
import org.stepic.droid.ui.custom.adapter_delegates.AdapterDelegate
import org.stepic.droid.ui.custom.adapter_delegates.DelegateViewHolder
import org.stepic.droid.ui.util.RoundedBitmapImageViewTarget
import org.stepic.droid.ui.util.changeVisibility
import org.stepik.android.view.course_content.model.CourseContentItem

class CourseContentUnitDelegate(
    private val unitClickListener: CourseContentUnitClickListener,
    private val unitDownloadStatuses: LongSparseArray<DownloadProgress.Status>
) : AdapterDelegate<CourseContentItem, DelegateViewHolder<CourseContentItem>>() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(createView(parent, R.layout.view_course_content_unit))

    override fun isForViewType(position: Int, data: CourseContentItem): Boolean =
        data is CourseContentItem.UnitItem

    inner class ViewHolder(root: View) : DelegateViewHolder<CourseContentItem>(root) {
        private val unitIcon = root.unitIcon
        private val unitTitle = root.unitTitle
        private val unitTextProgress = root.unitTextProgress
        private val unitProgress = root.unitProgress

        private val unitViewCount = root.unitViewCount
        private val unitViewCountIcon = root.unitViewCountIcon
        private val unitRating = root.unitRating
        private val unitRatingIcon = root.unitRatingIcon

        private val unitTimeToComplete = root.unitTimeToComplete

        private val unitDownloadStatus = root.unitDownloadStatus

        private val unitIconTarget = RoundedBitmapImageViewTarget(
                context.resources.getDimension(R.dimen.course_image_radius), unitIcon)

        private val unitIconPlaceholder = with(context.resources) {
            val coursePlaceholderBitmap = BitmapFactory.decodeResource(this, R.drawable.general_placeholder)
            val circularBitmapDrawable = RoundedBitmapDrawableFactory.create(this, coursePlaceholderBitmap)
            circularBitmapDrawable.cornerRadius = getDimension(R.dimen.course_image_radius)
            circularBitmapDrawable
        }

        init {
            root.setOnClickListener {
                (itemData as? CourseContentItem.UnitItem)?.let(unitClickListener::onItemClicked)
            }

            unitDownloadStatus.setOnClickListener {
                val item = (itemData as? CourseContentItem.UnitItem) ?: return@setOnClickListener
                when (unitDownloadStatus.status) {
                    DownloadProgress.Status.NotCached ->
                        unitClickListener.onItemDownloadClicked(item)

                    is DownloadProgress.Status.InProgress ->
                        unitClickListener.onItemCancelClicked(item)

                    is DownloadProgress.Status.Cached ->
                        unitClickListener.onItemRemoveClicked(item)
                }
            }
        }

        override fun onBind(data: CourseContentItem) {
            with(data as CourseContentItem.UnitItem) {
                unitTitle.text = context.resources.getString(R.string.course_content_unit_title,
                        section.position, unit.position, lesson.title)
                if (progress != null && progress.cost > 0) {
                    val score = progress
                        .score
                        ?.toFloatOrNull()
                        ?.toLong()
                        ?: 0L

                    unitTextProgress.text = context.resources.getString(R.string.course_content_text_progress_points,
                        score, progress.cost)

                    unitProgress.progress = score / progress.cost.toFloat()
                    unitTextProgress.visibility = View.VISIBLE
                } else {
                    unitProgress.progress = 0f
                    unitTextProgress.visibility = View.GONE
                }

                val timeToComplete = lesson.timeToComplete.takeIf { it > 0 } ?: 0

                if (timeToComplete > 0) {
                    unitTimeToComplete.visibility = View.VISIBLE

                    val (timeValue, @StringRes timeUnit) =
                        if (timeToComplete in 0 until 3600) {
                            timeToComplete / 60 to R.string.course_content_time_to_complete_minutes_unit
                        } else {
                            timeToComplete / 3600 to R.string.course_content_time_to_complete_hours_unit
                        }

                    unitTimeToComplete.text = context.getString(R.string.course_content_time_to_complete, context.getString(timeUnit, timeValue))
                } else {
                    unitTimeToComplete.visibility = View.GONE
                }

                unitDownloadStatus.status = unitDownloadStatuses[data.unit.id] ?: DownloadProgress.Status.Pending

                Glide.with(unitIcon.context)
                        .asBitmap()
                        .load(lesson.coverUrl)
                        .placeholder(unitIconPlaceholder)
                        .centerCrop()
                        .into(unitIconTarget)

                unitViewCount.text = lesson.passedBy.toString()

                @DrawableRes
                val unitRatingDrawableRes =
                    if (lesson.voteDelta < 0) {
                        R.drawable.ic_course_content_dislike
                    } else {
                        R.drawable.ic_course_content_like
                    }

                unitRatingIcon.setImageResource(unitRatingDrawableRes)
                unitRating.text = Math.abs(lesson.voteDelta).toString()

                unitDownloadStatus.changeVisibility(isEnabled)
                itemView.isEnabled = isEnabled

                val alpha = if (isEnabled) 1f else 0.4f
                unitTitle.alpha = alpha
                unitRatingIcon.alpha = alpha
                unitRating.alpha = alpha
                unitViewCount.alpha = alpha
                unitViewCountIcon.alpha = alpha
                unitTimeToComplete.alpha = alpha
            }
        }
    }
}