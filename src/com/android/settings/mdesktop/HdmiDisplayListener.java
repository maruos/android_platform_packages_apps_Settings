package com.android.settings.mdesktop;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

/**
 * Created by pdsouza on 11/10/15.
 */
public class HdmiDisplayListener implements DisplayManager.DisplayListener {
    private static final String TAG = HdmiDisplayListener.class.getName();
    private final Context mContext;
    private final DisplayManager mDisplayManager;
    private int mHdmiDisplayId = -1;

    public interface HdmiDisplayCallback {
        void onHdmiDisplayAdded();
        void onHdmiDisplayRemoved();
    }
    private HdmiDisplayCallback mCallback;

    public HdmiDisplayListener(Context context, DisplayManager displayManager) {
        mContext = context;
        mDisplayManager = displayManager;
    }

    @Override
    public void onDisplayAdded(int displayId) {
        Display display = mDisplayManager.getDisplay(displayId);
        final boolean hdmiDisplayAdded = display.getType() == Display.TYPE_HDMI;

        if (hdmiDisplayAdded) {
            if (mHdmiDisplayId == -1) {
                mHdmiDisplayId = displayId;
                if (mCallback != null) {
                    mCallback.onHdmiDisplayAdded();
                }
            }
        }
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        if (displayId == mHdmiDisplayId) {
            if (mHdmiDisplayId != -1) {
                mHdmiDisplayId = -1;
                if (mCallback != null) {
                    mCallback.onHdmiDisplayRemoved();
                }
            }
        }
    }

    @Override
    public void onDisplayChanged(int displayId) { /* no-op */ }

    public void sync() {
        Display[] displays = mDisplayManager
                .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        for (Display display : displays) {
            if (display.getType() == Display.TYPE_HDMI) {
                mHdmiDisplayId = display.getDisplayId();
                break;
            }
        }
    }

    public boolean isHdmiDisplayConnected() {
        return mHdmiDisplayId != -1;
    }

    public void setHdmiDisplayCallback(HdmiDisplayCallback callback) {
        mCallback = callback;
    }

    public void removeHdmiDisplayCallback() {
        mCallback = null;
    }
}
