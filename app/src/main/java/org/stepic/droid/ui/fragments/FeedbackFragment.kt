package org.stepic.droid.ui.fragments

import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_feedback.*
import org.stepic.droid.R
import org.stepic.droid.base.App
import org.stepic.droid.base.FragmentBase
import org.stepic.droid.util.DeviceInfoUtil
import org.stepik.android.presentation.feedback.FeedbackPresenter
import org.stepik.android.presentation.feedback.FeedbackView
import timber.log.Timber
import javax.inject.Inject

class FeedbackFragment : FragmentBase(), FeedbackView {
    companion object {
        fun newInstance(): FeedbackFragment {
            val args = Bundle()

            val fragment = FeedbackFragment()
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var feedbackPresenter: FeedbackPresenter

    @Inject
    internal lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injectComponentNewArch()
        feedbackPresenter = ViewModelProviders
            .of(this, viewModelFactory)
            .get(FeedbackPresenter::class.java)
    }

    override fun onStart() {
        super.onStart()
        feedbackPresenter.attachView(this)
    }

    override fun onStop() {
        feedbackPresenter.detachView(this)
        super.onStop()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            inflater.inflate(R.layout.fragment_feedback, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initButtons()
    }

    override fun sendTextFeedback(mailTo: String, subject: String, body: String) {
        val emailIntent = Intent(Intent.ACTION_SENDTO)
        Timber.d("Body: $body")
        val mailData = getString(R.string.email_intent_template).format(
            Uri.encode(mailTo),
            Uri.encode(subject),
            Uri.encode(body)
        )
        emailIntent.data = Uri.parse(mailData)
        startActivity(emailIntent)
    }

    override fun getMailToString(): String = getString(R.string.support_email)

    override fun getEmailSubjectString(): String = getString(R.string.feedback_subject)

    override fun getAboutSystemInfo(): String = DeviceInfoUtil.getInfosAboutDevice(context, "<br>")

    private fun initButtons() {
        feedbackGoodButton.setOnClickListener {
            if (config.isAppInStore) {
                screenManager.showStoreWithApp(activity)
            } else {
                feedbackPresenter.sendTextFeedback(getMailToString(), getEmailSubjectString(), getAboutSystemInfo())
            }
        }
        feedbackBadButton.setOnClickListener { feedbackPresenter.sendTextFeedback(getMailToString(), getEmailSubjectString(), getAboutSystemInfo()) }
    }

    private fun injectComponentNewArch() {
        App.component()
            .feedbackComponentBuilder()
            .build()
            .inject(this)
    }

    private fun destroyButtons() {
        feedbackGoodButton.setOnClickListener(null)
        feedbackBadButton.setOnClickListener(null)
    }

    override fun onDestroyView() {
        destroyButtons()
        super.onDestroyView()
    }
}