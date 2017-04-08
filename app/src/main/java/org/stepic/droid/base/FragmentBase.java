package org.stepic.droid.base;

import android.app.DownloadManager;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.squareup.otto.Bus;

import org.stepic.droid.analytic.Analytic;
import org.stepic.droid.concurrency.MainHandler;
import org.stepic.droid.configuration.Config;
import org.stepic.droid.core.AudioFocusHelper;
import org.stepic.droid.core.Shell;
import org.stepic.droid.core.LocalProgressManager;
import org.stepic.droid.core.ShareHelper;
import org.stepic.droid.fonts.FontsProvider;
import org.stepic.droid.notifications.LocalReminder;
import org.stepic.droid.preferences.SharedPreferenceHelper;
import org.stepic.droid.preferences.UserPreferences;
import org.stepic.droid.storage.CancelSniffer;
import org.stepic.droid.storage.IDownloadManager;
import org.stepic.droid.storage.operations.DatabaseFacade;
import org.stepic.droid.util.resolvers.CoursePropertyResolver;
import org.stepic.droid.util.resolvers.text.TextResolver;

import java.util.concurrent.ThreadPoolExecutor;

import javax.inject.Inject;

import butterknife.ButterKnife;
import butterknife.Unbinder;

public class FragmentBase extends Fragment {

    private Unbinder unbinder;

    @Inject
    protected LocalReminder localReminder;

    @Inject
    protected TextResolver textResolver;

    @Inject
    protected ShareHelper shareHelper;

    @Inject
    protected Analytic analytic;

    @Inject
    public ThreadPoolExecutor threadPoolExecutor;

    @Inject
    public DatabaseFacade databaseFacade;

    @Inject
    protected FontsProvider fontsProvider;

    /**
     * Do not use bus, try to use presenters, after deleting most part of bus related code project will use RxJava
     */
    @Deprecated
    @Inject
    public Bus bus;

    @Inject
    public Config config;

    @Inject
    public Shell shell;

    @Inject
    public LocalProgressManager localProgressManager;

    @Inject
    public IDownloadManager downloadManager;

    @Inject
    public SharedPreferenceHelper sharedPreferenceHelper;

    @Inject
    public UserPreferences userPreferences;

    @Inject
    public CoursePropertyResolver coursePropertyResolver;

    @Inject
    public MainHandler mainHandler;

    @Inject
    public AudioFocusHelper audioFocusHelper;

    @Inject
    public DownloadManager systemDownloadManager;

    @Inject
    public CancelSniffer cancelSniffer;

    public FragmentBase() {
        injectComponent();
    }

    protected void injectComponent() {
        App.component().inject(this);
    }

    protected void hideSoftKeypad() {
        View view = this.getActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) (getActivity().getSystemService(Context.INPUT_METHOD_SERVICE));
            if (imm.isAcceptingText()) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        unbinder = ButterKnife.bind(this, view);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (unbinder != null) {
            //in Kotlin, for example, butter knife is not used
            unbinder.unbind();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
//        RefWatcher refWatcher = App.getRefWatcher(getActivity());
//        refWatcher.watch(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

}