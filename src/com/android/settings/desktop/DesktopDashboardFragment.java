/*
 * Copyright (C) 2015-2016 Preetam J. D'Souza
 * Copyright (C) 2016 The Maru OS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.android.settings.desktop;

import android.app.DialogFragment;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.mperspective.Perspective;
import android.mperspective.PerspectiveManager;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.ToggleSwitch;
import com.android.settingslib.core.AbstractPreferenceController;

import java.util.List;

public class DesktopDashboardFragment extends DashboardFragment
        implements SwitchBar.OnSwitchChangeListener,
        ToggleSwitch.OnBeforeCheckedChangeListener,
        ShutdownDialogFragment.ShutdownDialogListener {

    private static final String TAG = DesktopDashboardFragment.class.getName();

    private static final String KEY_DESKTOP_STATUS = "desktop_status";

    private PerspectiveManager mPerspectiveManager;
    private DesktopPerspectiveListener mDesktopListener;
    private boolean mDesktopListening = false;

    private int mDesktopState;

    private DisplayManager mDisplayManager;
    private MaruDisplayListener mMaruDisplayListener;
    private boolean mDisplayListening = false;
    private boolean mMaruDisplayConnected = false;

    private SwitchBar mSwitchBar;
    private boolean mSwitchBarListening = false;

    private Preference mDesktopStatusSummary;

    private static final String SHUTDOWN_DIALOG_TAG = ShutdownDialogFragment.class.getName();
    private boolean mShutdownConfirmed = false;
    private boolean mOverrideShutdownDialog = false;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final SettingsActivity settingsActivity = (SettingsActivity) getActivity();
        final Context context = settingsActivity.getApplicationContext();
        mPerspectiveManager = (PerspectiveManager) context
                .getSystemService(Context.PERSPECTIVE_SERVICE);
        mDisplayManager = (DisplayManager) context
                .getSystemService(Context.DISPLAY_SERVICE);

        mDesktopListener = new DesktopPerspectiveListener();

        mMaruDisplayListener = new MaruDisplayListener(context, mDisplayManager);
        mMaruDisplayListener.setDisplayCallback(new MaruDisplayListener.MaruDisplayCallback() {
            @Override
            public void onDisplayAdded() {
                Log.d(TAG, "onDisplayAdded");
                mMaruDisplayConnected = mMaruDisplayListener.isMaruDisplayConnected();
                updateView();
            }

            @Override
            public void onDisplayRemoved() {
                Log.d(TAG, "onDisplayRemoved");
                mMaruDisplayConnected = mMaruDisplayListener.isMaruDisplayConnected();
                updateView();
            }
        });

        mSwitchBar = settingsActivity.getSwitchBar();
        mSwitchBar.show();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        mDesktopStatusSummary = getPreferenceScreen().findPreference(KEY_DESKTOP_STATUS);
        mDesktopStatusSummary.setIcon(com.android.settingslib.R.drawable.ic_info_outline_24dp);
        mDesktopStatusSummary.setSelectable(false);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mSwitchBarListening) {
            mSwitchBar.addOnSwitchChangeListener(this);
            mSwitchBar.getSwitch().setOnBeforeCheckedChangeListener(this);
            mSwitchBarListening = true;
        }
        if (!mDisplayListening) {
            // registered on calling thread's looper
            mDisplayManager.registerDisplayListener(mMaruDisplayListener, null);
            mDisplayListening = true;
        }
        if (!mDesktopListening) {
            // registered on calling thread's looper
            mPerspectiveManager.registerPerspectiveListener(mDesktopListener, null);
            mDesktopListening = true;
        }

        initializeState();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mSwitchBarListening) {
            mSwitchBar.removeOnSwitchChangeListener(this);
            mSwitchBar.getSwitch().setOnBeforeCheckedChangeListener(null);
            mSwitchBarListening = false;
        }
        if (mDisplayListening) {
            mDisplayManager.unregisterDisplayListener(mMaruDisplayListener);
            mDisplayListening = false;
        }
        if (mDesktopListening) {
            mPerspectiveManager.unregisterPerspectiveListener();
            mDesktopListening = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mSwitchBar.hide();
    }

    @Override
    public boolean onBeforeCheckedChanged(ToggleSwitch toggleSwitch, boolean checked) {
        if (!mOverrideShutdownDialog) {
            boolean attemptedShutdown = toggleSwitch.isChecked() && !checked
                    && mDesktopState == Perspective.STATE_RUNNING;
            if (attemptedShutdown) {
                if (!mShutdownConfirmed) {
                    ShutdownDialogFragment mShutdownDialogFragment = new ShutdownDialogFragment();
                    mShutdownDialogFragment.listener = this;
                    mShutdownDialogFragment.show(getFragmentManager(), SHUTDOWN_DIALOG_TAG);
                    /*
                     * Ignore the change until the user confirms the dialog.
                     * The dialog action callbacks will set the state.
                     */
                    return true;
                } else {
                    // reset the confirmation for next time
                    mShutdownConfirmed = false;
                }
            }
        }

        // OK the change
        return false;
    }

    @Override
    public void onShutdownCancel(DialogFragment dialog) { /* no-op */ }

    @Override
    public void onShutdown(DialogFragment dialog) {
        // the user has confirmed shutdown so update the SwitchBar
        mShutdownConfirmed = true;
        mSwitchBar.setChecked(false);
    }

    @Override
    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        if (isChecked) {
            if (mDesktopState == Perspective.STATE_STOPPED) {
                // prematurely update our state so the user has immediate feedback
                updateDesktopStateIfNeeded(Perspective.STATE_STARTING);

                mPerspectiveManager.startDesktopPerspective();
            }
        } else {
            if (mDesktopState == Perspective.STATE_RUNNING) {
                // prematurely update our state so the user has immediate feedback
                updateDesktopStateIfNeeded(Perspective.STATE_STOPPING);

                mPerspectiveManager.stopDesktopPerspective();
            }
        }
    }

    private void initializeState() {
        /*
         * Sync up any state that can change without accessing this fragment
         * since it's possible that we missed some events while in the background.
         */

        mDesktopState = mPerspectiveManager.isDesktopRunning() ?
                Perspective.STATE_RUNNING : Perspective.STATE_STOPPED;

        mMaruDisplayListener.sync();
        mMaruDisplayConnected = mMaruDisplayListener.isMaruDisplayConnected();

        updateView();
    }

    private void updateDesktopStateIfNeeded(int state) {
        if (mDesktopState != state) {
            int prevState = mDesktopState;
            mDesktopState = state;
            updateView(prevState);
        }
    }

    private void updateView() {
        updateView(mDesktopState);
    }

    private void updateView(final int prevDesktopState) {
        // common defaults to simplify state configuration
        int hintVisibility = View.INVISIBLE;

        switch (mDesktopState) {
            case Perspective.STATE_STARTING:
                mSwitchBar.setChecked(true);
                mSwitchBar.setEnabled(false);
                mDesktopStatusSummary.setTitle(R.string.desktop_status_starting);
                break;
            case Perspective.STATE_STOPPING:
                mSwitchBar.setChecked(false);
                mSwitchBar.setEnabled(false);
                mDesktopStatusSummary.setTitle(R.string.desktop_status_stopping);
                break;
            case Perspective.STATE_STOPPED:
                mSwitchBar.setChecked(false);
                mSwitchBar.setEnabled(true);
                if (prevDesktopState == Perspective.STATE_STOPPING || prevDesktopState == mDesktopState) {
                    mDesktopStatusSummary.setTitle(R.string.desktop_status_stopped);
                    if (!mMaruDisplayConnected) {
                        mDesktopStatusSummary.setSummary(R.string.desktop_status_hint_autostart);
                        hintVisibility = View.VISIBLE;
                    }
                } else if (prevDesktopState == Perspective.STATE_STARTING) {
                    mDesktopStatusSummary.setTitle(R.string.desktop_status_start_failure);
                } else if (prevDesktopState == Perspective.STATE_RUNNING) {
                    mDesktopStatusSummary.setTitle(R.string.desktop_status_crash);
                }
                break;
            case Perspective.STATE_RUNNING:
                mSwitchBar.setChecked(true);
                mSwitchBar.setEnabled(true);
                if (prevDesktopState == Perspective.STATE_STARTING || prevDesktopState == mDesktopState) {
                    if (mMaruDisplayConnected) {
                        mDesktopStatusSummary.setTitle(R.string.desktop_status_running);
                    } else {
                        mDesktopStatusSummary.setTitle(R.string.desktop_status_running_bg);
                        mDesktopStatusSummary.setSummary(R.string.desktop_status_hint_interact);
                        hintVisibility = View.VISIBLE;
                    }
                } else if (prevDesktopState == Perspective.STATE_STOPPING) {
                    mDesktopStatusSummary.setTitle(R.string.desktop_status_stop_failure);
                }
                break;
        }

        if (hintVisibility != View.VISIBLE) {
            mDesktopStatusSummary.setSummary(null);
        }
    }

    private final class DesktopPerspectiveListener
            implements PerspectiveManager.PerspectiveListener {
        @Override
        public void onPerspectiveStateChanged(int state) {
            Log.d(TAG, "onPerspectiveStateChanged: " + Perspective.stateToString(state));
            /*
             * Kind of ugly but due to the way the dialog is triggered
             * we need to override it in the unlikely case that the state
             * changes from STARTING to STOPPED (error) or RUNNING to STOPPED (crash).
             */
            mOverrideShutdownDialog = true;
            updateDesktopStateIfNeeded(state);
            mOverrideShutdownDialog = false;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.SETTINGS_DESKTOP_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_url_desktop_dashboard;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.desktop_dashboard;
    }

    @Override
    protected List<AbstractPreferenceController> getPreferenceControllers(Context context) {
        // No actual preferences right now.
        return null;
    }
}
