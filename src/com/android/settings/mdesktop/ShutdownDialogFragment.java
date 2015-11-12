/*
 * Copyright (C) 2015 Preetam D'Souza
 *
 */

package com.android.settings.mdesktop;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

import com.android.settings.R;

/**
 * A shutdown confirmation dialog to explicitly ensure that the
 * user wants to shutdown the desktop perspective.
 */
public class ShutdownDialogFragment extends DialogFragment {
    private static final String TAG = ShutdownDialogFragment.class.getName();

    public interface ShutdownDialogListener {
        void onShutdownCancel(DialogFragment dialog);
        void onShutdown(DialogFragment dialog);
    }

    ShutdownDialogListener listener;

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.desktop_shutdown_dialog_title)
                .setMessage(R.string.desktop_shutdown_dialog_details)
                .setNegativeButton(R.string.desktop_shutdown_dialog_negative_action,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Log.d(TAG, "Cancel.");
                                listener.onShutdownCancel(ShutdownDialogFragment.this);
                            }
                        }
                )
                .setPositiveButton(R.string.desktop_shutdown_dialog_positive_action,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Log.d(TAG, "Shut down!");
                                listener.onShutdown(ShutdownDialogFragment.this);
                            }
                        }
                );
        return builder.create();
    }
}
