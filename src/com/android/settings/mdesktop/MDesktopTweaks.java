/*
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

import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.SwitchPreference;

import com.android.internal.logging.MetricsLogger;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

/**
 * Experimental settings and tweaks for Maru Desktop.
 */
public class MDesktopTweaks extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "MDesktopTweaks";

    private static final String KEY_PICK_NATIVE_MODE = "tweak_pick_native_mode";
    private static final String PROPERTY_PICK_NATIVE_MODE = "persist.m.hdmi.try_native_mode";

    private SwitchPreference mPickNativeModePreference;

    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.MDESKTOP_TWEAKS;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.mdesktop_tweaks);

        if (isPickNativeModeAvailable()) {
            mPickNativeModePreference = (SwitchPreference) findPreference(KEY_PICK_NATIVE_MODE);
            mPickNativeModePreference.setOnPreferenceChangeListener(this);
        } else {
            removePreference(KEY_PICK_NATIVE_MODE);
        }
    }

    private static boolean isPickNativeModeAvailable() {
        // if the key exists, pick native mode is available on this device
        return !"".equals(SystemProperties.get(PROPERTY_PICK_NATIVE_MODE));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mPickNativeModePreference) {
            boolean value = (Boolean) newValue;
            SystemProperties.set(PROPERTY_PICK_NATIVE_MODE, value ? "1" : "0");
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        syncState();
    }

    private void syncState() {
        if (mPickNativeModePreference != null) {
            final String value = SystemProperties.get(PROPERTY_PICK_NATIVE_MODE);
            mPickNativeModePreference.setChecked("1".equals(value));
        }
    }
}
