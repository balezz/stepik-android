package org.stepik.android.view.lesson.ui.activity

import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.view.ViewPager
import android.support.v7.content.res.AppCompatResources
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog
import kotlinx.android.synthetic.main.activity_lesson.*
import kotlinx.android.synthetic.main.empty_login.*
import kotlinx.android.synthetic.main.error_lesson_not_found.*
import kotlinx.android.synthetic.main.error_no_connection_with_button.*
import kotlinx.android.synthetic.main.view_centered_toolbar.*
import org.stepic.droid.R
import org.stepic.droid.analytic.Analytic
import org.stepic.droid.base.App
import org.stepic.droid.base.FragmentActivityBase
import org.stepic.droid.ui.adapters.StepFragmentAdapter
import org.stepic.droid.ui.dialogs.TimeIntervalPickerDialogFragment
import org.stepik.android.view.lesson.ui.interfaces.NextMoveable
import org.stepic.droid.ui.util.initCenteredToolbar
import org.stepic.droid.ui.util.snackbar
import org.stepic.droid.util.DeviceInfoUtil
import org.stepic.droid.util.RatingUtil
import org.stepic.droid.util.reportRateEvent
import org.stepic.droid.util.resolvers.StepTypeResolver
import org.stepik.android.domain.feedback.model.SupportEmailData
import org.stepik.android.domain.last_step.model.LastStep
import org.stepik.android.model.Lesson
import org.stepik.android.model.Section
import org.stepik.android.model.Step
import org.stepik.android.model.Unit
import org.stepik.android.presentation.lesson.LessonPresenter
import org.stepik.android.presentation.lesson.LessonView
import org.stepik.android.view.app_rating.ui.dialog.RateAppDialog
import org.stepik.android.view.base.ui.span.TypefaceSpanCompat
import org.stepik.android.view.fragment_pager.FragmentDelegateScrollStateChangeListener
import org.stepik.android.view.lesson.routing.getLessonDeepLinkData
import org.stepik.android.view.lesson.ui.delegate.LessonInfoTooltipDelegate
import org.stepik.android.view.lesson.ui.interfaces.Playable
import org.stepik.android.view.ui.delegate.ViewStateDelegate
import javax.inject.Inject

class LessonActivity : FragmentActivityBase(), LessonView,
    NextMoveable, RateAppDialog.Companion.Callback, TimeIntervalPickerDialogFragment.Companion.Callback {
    companion object {
        private const val EXTRA_SECTION = "section"
        private const val EXTRA_UNIT = "unit"
        private const val EXTRA_LESSON = "lesson"
        private const val EXTRA_BACK_ANIMATION = "back_animation"
        private const val EXTRA_AUTOPLAY = "autoplay"

        private const val EXTRA_LAST_STEP = "last_step"

        fun createIntent(context: Context, section: Section, unit: Unit, lesson: Lesson, isNeedBackAnimation: Boolean = false, isAutoplayEnabled: Boolean = false): Intent =
            Intent(context, LessonActivity::class.java)
                .putExtra(EXTRA_SECTION, section)
                .putExtra(EXTRA_UNIT, unit)
                .putExtra(EXTRA_LESSON, lesson)
                .putExtra(EXTRA_BACK_ANIMATION, isNeedBackAnimation)
                .putExtra(EXTRA_AUTOPLAY, isAutoplayEnabled)

        fun createIntent(context: Context, lastStep: LastStep): Intent =
            Intent(context, LessonActivity::class.java)
                .putExtra(EXTRA_LAST_STEP, lastStep)
    }

    @Inject
    internal lateinit var stepTypeResolver: StepTypeResolver

    @Inject
    internal lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var lessonPresenter: LessonPresenter

    private lateinit var viewStateDelegate: ViewStateDelegate<LessonView.State>
    private lateinit var viewStepStateDelegate: ViewStateDelegate<LessonView.StepsState>

    private lateinit var stepsAdapter: StepFragmentAdapter

    private var infoMenuItem: MenuItem? = null
    private var isInfoMenuItemVisible: Boolean = false
        set(value) {
            field = value
            infoMenuItem?.isVisible = value
        }

    private lateinit var lessonInfoTooltipDelegate: LessonInfoTooltipDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lesson)

        if (savedInstanceState == null) {
            if (intent.getBooleanExtra(EXTRA_BACK_ANIMATION, false)) {
                overridePendingTransition(R.anim.slide_in_from_start, R.anim.slide_out_to_end)
            } else {
                overridePendingTransition(R.anim.slide_in_from_end, R.anim.slide_out_to_start)
            }
        }

        injectComponent()
        lessonPresenter = ViewModelProviders
            .of(this, viewModelFactory)
            .get(LessonPresenter::class.java)

        initCenteredToolbar(R.string.lesson_title, showHomeButton = true)

        viewStateDelegate = ViewStateDelegate()
        viewStateDelegate.addState<LessonView.State.Idle>(lessonPlaceholder)
        viewStateDelegate.addState<LessonView.State.Loading>(lessonPlaceholder)
        viewStateDelegate.addState<LessonView.State.LessonNotFound>(lessonNotFound)
        viewStateDelegate.addState<LessonView.State.EmptyLogin>(emptyLogin)
        viewStateDelegate.addState<LessonView.State.NetworkError>(errorNoConnection)
        viewStateDelegate.addState<LessonView.State.LessonLoaded>(lessonPager)

        viewStepStateDelegate = ViewStateDelegate()
        viewStepStateDelegate.addState<LessonView.StepsState.Idle>(lessonPlaceholder)
        viewStepStateDelegate.addState<LessonView.StepsState.Loading>(lessonPlaceholder)
        viewStepStateDelegate.addState<LessonView.StepsState.NetworkError>(errorNoConnection)
        viewStepStateDelegate.addState<LessonView.StepsState.EmptySteps>(emptyLesson)
        viewStepStateDelegate.addState<LessonView.StepsState.AccessDenied>(lessonNotFound)
        viewStepStateDelegate.addState<LessonView.StepsState.Loaded>(lessonPager, lessonTab)

        lessonInfoTooltipDelegate = LessonInfoTooltipDelegate(centeredToolbar)

        tryAgain.setOnClickListener { setDataToPresenter(forceUpdate = true) }
        goToCatalog.setOnClickListener { screenManager.showCatalog(this); finish() }
        authAction.setOnClickListener { screenManager.showLaunchScreen(this) }

        stepsAdapter = StepFragmentAdapter(supportFragmentManager, stepTypeResolver)
        lessonPager.adapter = stepsAdapter
        lessonPager.addOnPageChangeListener(FragmentDelegateScrollStateChangeListener(lessonPager, stepsAdapter))
        lessonPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                hideSoftKeypad()
                lessonPresenter.onStepOpened(position)
            }
        })
        lessonTab.setupWithViewPager(lessonPager, true)

        setDataToPresenter()
    }

    private fun injectComponent() {
        App.component()
            .lessonComponentBuilder()
            .build()
            .inject(this)
    }

    private fun setDataToPresenter(forceUpdate: Boolean = false) {
        val lastStep = intent.getParcelableExtra<LastStep>(EXTRA_LAST_STEP)

        val lesson = intent.getParcelableExtra<Lesson>(EXTRA_LESSON)
        val unit = intent.getParcelableExtra<Unit>(EXTRA_UNIT)
        val section = intent.getParcelableExtra<Section>(EXTRA_SECTION)

        val isFromNextLesson = intent.getBooleanExtra(EXTRA_BACK_ANIMATION, false)

        val deepLinkData = intent.getLessonDeepLinkData()

        when {
            lastStep != null ->
                lessonPresenter.onLastStep(lastStep, forceUpdate)

            deepLinkData != null ->
                lessonPresenter.onDeepLink(deepLinkData, forceUpdate)

            lesson != null && unit != null && section != null ->
                lessonPresenter.onLesson(lesson, unit, section, isFromNextLesson, forceUpdate)

            else ->
                lessonPresenter.onEmptyData()
        }
    }

    override fun onStart() {
        super.onStart()
        lessonPresenter.attachView(this)
    }

    override fun onStop() {
        lessonPresenter.detachView(this)
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.lesson_menu, menu)
        infoMenuItem = menu.findItem(R.id.lesson_menu_item_info)
        infoMenuItem?.isVisible = isInfoMenuItemVisible
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.lesson_menu_item_info -> {
                lessonPresenter.onShowLessonInfoClicked(lessonPager.currentItem)
                true
            }
            else ->
                super.onOptionsItemSelected(item)
        }

    override fun setState(state: LessonView.State) {
        viewStateDelegate.switchState(state)
        when (state) {
            is LessonView.State.LessonLoaded -> {
                viewStepStateDelegate.switchState(state.stepsState)
                centeredToolbarTitle.text = state.lessonData.lesson.title

                stepsAdapter.lessonData = state.lessonData
                if (state.stepsState is LessonView.StepsState.Loaded) {
                    stepsAdapter.items = state.stepsState.stepItems

                    if (intent.getBooleanExtra(EXTRA_AUTOPLAY, false)) {
                        lessonPager.post { playCurrentStep() }
                        intent.removeExtra(EXTRA_AUTOPLAY)
                    }
                } else {
                    stepsAdapter.items = emptyList()
                }

                invalidateTabLayout()
            }
        }

        isInfoMenuItemVisible =
            state is LessonView.State.LessonLoaded &&
            state.stepsState is LessonView.StepsState.Loaded
    }

    private fun invalidateTabLayout() {
        for (i in 0 until lessonTab.tabCount) {
            val tabIcon = AppCompatResources
                .getDrawable(this, stepsAdapter.getTabDrawable(i))
                ?.mutate()

            val tintColor = ContextCompat
                .getColor(this, stepsAdapter.getTabTint(i))

            lessonTab.getTabAt(i)?.icon = tabIcon
            tabIcon?.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        }
    }

    override fun showStepAtPosition(position: Int) {
        lessonPager.currentItem = position
        lessonPresenter.onStepOpened(position)
    }

    override fun showLessonInfoTooltip(stepScore: Long, stepCost: Long, lessonTimeToComplete: Long, certificateThreshold: Long) {
        lessonInfoTooltipDelegate
            .showLessonInfoTooltip(stepScore, stepCost, lessonTimeToComplete, certificateThreshold)
    }

    override fun moveNext(isAutoplayEnabled: Boolean): Boolean {
        val itemCount = lessonPager
            .adapter
            ?.count
            ?: return false

        val isNotLastItem = lessonPager.currentItem < itemCount - 1

        if (isNotLastItem) {
            lessonPager.currentItem++
            if (isAutoplayEnabled) {
                playCurrentStep()
            }
        }

        return isNotLastItem
    }

    private fun playCurrentStep() {
        (stepsAdapter.activeFragments[lessonPager.currentItem] as? Playable)
            ?.play()
    }

    override fun showComments(step: Step, discussionId: Long) {
        screenManager.openComments(this, step.discussionProxy, step.id, discussionId, false)
    }

    override fun showRateDialog() {
        val supportFragmentManager = supportFragmentManager
            ?.takeIf { it.findFragmentByTag(RateAppDialog.TAG) == null }
            ?: return

        val dialog = RateAppDialog.newInstance()
        analytic.reportEvent(Analytic.Rating.SHOWN)
        dialog.show(supportFragmentManager, RateAppDialog.TAG)
    }

    override fun showStreakDialog(streakDays: Int) {
        val streakTitle = SpannableString(getString(R.string.streak_dialog_title))
        streakTitle.setSpan(
            ForegroundColorSpan(Color.BLACK),
            0,
            streakTitle.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val typefaceSpan = TypefaceSpanCompat(ResourcesCompat.getFont(this, R.font.roboto_bold))
        streakTitle.setSpan(typefaceSpan, 0, streakTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val description = if (streakDays > 0) {
            analytic.reportEvent(Analytic.Streak.SHOW_DIALOG_UNDEFINED_STREAKS, streakDays.toString())
            resources.getQuantityString(R.plurals.streak_description, streakDays, streakDays)
        } else {
            analytic.reportEvent(Analytic.Streak.SHOW_DIALOG_POSITIVE_STREAKS, streakDays.toString())
            getString(R.string.streak_description_not_positive)
        }

        analytic.reportEvent(Analytic.Streak.SHOWN_MATERIAL_DIALOG)
        val dialog = MaterialStyledDialog.Builder(this)
                .setTitle(streakTitle)
                .setDescription(description)
                .setHeaderDrawable(R.drawable.dialog_background)
                .setPositiveText(R.string.ok)
                .setNegativeText(R.string.later_tatle)
                .setScrollable(true, 10) // number of lines lines
                .onPositive { _, _ ->
                    val supportFragmentManager = supportFragmentManager
                        ?.takeIf { it.findFragmentByTag(RateAppDialog.TAG) == null }
                        ?: return@onPositive

                    analytic.reportEvent(Analytic.Streak.POSITIVE_MATERIAL_DIALOG)
                    val dialogFragment = TimeIntervalPickerDialogFragment.newInstance()
                    dialogFragment.show(supportFragmentManager, TimeIntervalPickerDialogFragment.TAG)
                }
                .onNegative { _, _ -> onStreakDialogCancelled() }
                .build()
        dialog.show()
    }

    override fun onClickLater(starNumber: Int) {
        if (RatingUtil.isExcellent(starNumber)) {
            analytic.reportRateEvent(starNumber, Analytic.Rating.POSITIVE_LATER)
        } else {
            analytic.reportRateEvent(starNumber, Analytic.Rating.NEGATIVE_LATER)
        }
    }

    override fun onClickGooglePlay(starNumber: Int) {
        lessonPresenter.onAppRateShow()
        analytic.reportRateEvent(starNumber, Analytic.Rating.POSITIVE_APPSTORE)

        if (config.isAppInStore) {
            screenManager.showStoreWithApp(this)
        } else {
            setupTextFeedback()
        }
    }

    override fun onClickSupport(starNumber: Int) {
        lessonPresenter.onAppRateShow()
        analytic.reportRateEvent(starNumber, Analytic.Rating.NEGATIVE_EMAIL)
        setupTextFeedback()
    }

    override fun sendTextFeedback(supportEmailData: SupportEmailData) {
        screenManager.openTextFeedBack(this, supportEmailData)
    }

    override fun onTimeIntervalPicked(chosenInterval: Int) {
        lessonPresenter.setStreakTime(chosenInterval)
        analytic.reportEvent(Analytic.Streak.CHOOSE_INTERVAL, chosenInterval.toString())
        lessonPager.snackbar(messageRes = R.string.streak_notification_enabled_successfully, length = Snackbar.LENGTH_LONG)
    }

    private fun setupTextFeedback() {
        lessonPresenter.sendTextFeedback(
            getString(R.string.feedback_subject),
            DeviceInfoUtil.getInfosAboutDevice(this, "\n")
        )
    }

    private fun onStreakDialogCancelled() {
        analytic.reportEvent(Analytic.Streak.NEGATIVE_MATERIAL_DIALOG)
        lessonPager.snackbar(messageRes = R.string.streak_notification_canceled, length = Snackbar.LENGTH_LONG)
    }
}