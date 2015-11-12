/*
 * Copyright (C) 2015 Preetam D'Souza
 *
 */

package com.android.settings.mdesktop;

import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.hardware.display.DisplayManager;
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

    private enum State {
        STARTING,
        STOPPING,
        STOPPED,
        RUNNING
    }
    private State mDesktopState;

    private DisplayManager mDisplayManager;
    private HdmiDisplayListener mHdmiDisplayListener;
    private boolean mDisplayListening = false;
    private boolean mHdmiDisplayConnected = false;

    private SwitchBar mSwitchBar;
    private boolean mSwitchBarListening = false;

    private TextView mCenterTextView;

    private ShutdownDialogFragment mShutdownDialogFragment;
    private static final String SHUTDOWN_DIALOG_TAG = ShutdownDialogFragment.class.getName();
    private boolean mShutdownConfirmed = false;

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

        mHdmiDisplayListener = new HdmiDisplayListener(context, mDisplayManager);
        mHdmiDisplayListener.setHdmiDisplayCallback(new HdmiDisplayListener.HdmiDisplayCallback() {
            @Override
            public void onHdmiDisplayAdded() {
                Log.d(TAG, "onHdmiDisplayAdded");
                mHdmiDisplayConnected = true;
                updateView();
            }

            @Override
            public void onHdmiDisplayRemoved() {
                Log.d(TAG, "onHdmiDisplayRemoved");
                mHdmiDisplayConnected = false;
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
            mDisplayManager.registerDisplayListener(mHdmiDisplayListener, null);
            mDisplayListening = true;
        }
        if (!mDesktopListening) {
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
            mDisplayManager.unregisterDisplayListener(mHdmiDisplayListener);
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
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mSwitchBar.hide();
    }

    @Override
    public boolean onBeforeCheckedChanged(ToggleSwitch toggleSwitch, boolean checked) {
        boolean attemptedShutdown = toggleSwitch.isChecked() && !checked;
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
            if (mDesktopState == State.STOPPED) {
                mPerspectiveManager.startDesktopPerspective();
                mDesktopState = State.STARTING;
                Log.d(TAG, "Desktop STARTING!");
                updateView();
            }
        } else {
            if (mDesktopState == State.RUNNING) {
                mPerspectiveManager.stopDesktopPerspective();
                mDesktopState = State.STOPPING;
                Log.d(TAG, "Desktop STOPPING!");
                updateView();
            }
        }
    }

    private void initializeState() {
        /*
         * Sync up any state that can change without accessing this fragment
         * since it's possible that we missed some events while in the background.
         */

        mDesktopState = mPerspectiveManager.isDesktopRunning() ?
                State.RUNNING : State.STOPPED;

        mHdmiDisplayListener.sync();
        mHdmiDisplayConnected = mHdmiDisplayListener.isHdmiDisplayConnected();

        updateView();
    }

    private void updateView() {
        switch (mDesktopState) {
            case STARTING:
                mSwitchBar.setChecked(true);
                mSwitchBar.setEnabled(false);
                mCenterTextView.setText(R.string.desktop_center_text_starting);
                break;
            case STOPPING:
                mSwitchBar.setChecked(false);
                mSwitchBar.setEnabled(false);
                mCenterTextView.setText(R.string.desktop_center_text_stopping);
                break;
            case STOPPED:
                mSwitchBar.setChecked(false);
                if (mHdmiDisplayConnected) {
                    mSwitchBar.setEnabled(true);
                    mCenterTextView.setText(R.string.desktop_center_text_stopped);
                } else {
                    mSwitchBar.setEnabled(false);
                    mCenterTextView.setText(R.string.desktop_center_text_off_hdmi_disconnected);
                }
                break;
            case RUNNING:
                mSwitchBar.setChecked(true);
                mSwitchBar.setEnabled(true);
                mCenterTextView.setText(R.string.desktop_center_text_running);
                break;
        }
    }

    private final class DesktopPerspectiveListener
            implements PerspectiveManager.PerspectiveListener {

        @Override
        public void onPerspectiveRunning() {
            mDesktopState = State.RUNNING;
            Log.d(TAG, "Desktop RUNNING!");
            updateView();
        }

        @Override
        public void onPerspectiveStopped() {
            mDesktopState = State.STOPPED;
            Log.d(TAG, "Desktop STOPPED!");
            updateView();
        }
    }

}
