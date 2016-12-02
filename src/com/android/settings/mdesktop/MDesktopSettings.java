/*
 * Copyright 2015-2016 Preetam J. D'Souza
 * Copyright 2016 The Maru OS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.mdesktop;

import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.mperspective.Perspective;
import android.mperspective.PerspectiveManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.widget.SwitchBar;
import com.android.settings.widget.ToggleSwitch;

/**
 * Main settings fragment for Maru Desktop.
 */
public class MDesktopSettings extends Fragment
        implements SwitchBar.OnSwitchChangeListener,
        ToggleSwitch.OnBeforeCheckedChangeListener,
        ShutdownDialogFragment.ShutdownDialogListener {
    private static final String TAG = "MDesktopSettings";

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

    private TextView mCenterTextView;
    private TextView mCenterTextHintView;

    private ShutdownDialogFragment mShutdownDialogFragment;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.mdesktop_settings, container, false);
        mCenterTextView = (TextView) view.findViewById(R.id.desktop_settings_center_text);
        mCenterTextHintView = (TextView) view.findViewById(R.id.desktop_settings_center_hint_text);
        return view;
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
                    mShutdownDialogFragment = new ShutdownDialogFragment();
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
                mCenterTextView.setText(R.string.desktop_center_text_starting);
                break;
            case Perspective.STATE_STOPPING:
                mSwitchBar.setChecked(false);
                mSwitchBar.setEnabled(false);
                mCenterTextView.setText(R.string.desktop_center_text_stopping);
                break;
            case Perspective.STATE_STOPPED:
                mSwitchBar.setChecked(false);
                mSwitchBar.setEnabled(true);
                if (prevDesktopState == Perspective.STATE_STOPPING || prevDesktopState == mDesktopState) {
                    mCenterTextView.setText(R.string.desktop_center_text_stopped);
                    if (!mMaruDisplayConnected) {
                        mCenterTextHintView.setText(R.string.desktop_center_text_hint_autostart);
                        hintVisibility = View.VISIBLE;
                    }
                } else if (prevDesktopState == Perspective.STATE_STARTING) {
                    mCenterTextView.setText(R.string.desktop_center_text_start_failure);
                } else if (prevDesktopState == Perspective.STATE_RUNNING) {
                    mCenterTextView.setText(R.string.desktop_center_text_crash);
                }
                break;
            case Perspective.STATE_RUNNING:
                mSwitchBar.setChecked(true);
                mSwitchBar.setEnabled(true);
                if (prevDesktopState == Perspective.STATE_STARTING || prevDesktopState == mDesktopState) {
                    if (mMaruDisplayConnected) {
                        mCenterTextView.setText(R.string.desktop_center_text_running);
                    } else {
                        mCenterTextView.setText(R.string.desktop_center_text_running_bg);
                        mCenterTextHintView.setText(R.string.desktop_center_text_hint_interact);
                        hintVisibility = View.VISIBLE;
                    }
                } else if (prevDesktopState == Perspective.STATE_STOPPING) {
                    mCenterTextView.setText(R.string.desktop_center_text_stop_failure);
                }
                break;
        }

        // opt: defer state changes for defaults to avoid unnecessary updates
        mCenterTextHintView.setVisibility(hintVisibility);
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

}
