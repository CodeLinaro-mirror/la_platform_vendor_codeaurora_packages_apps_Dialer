/*
 * Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.dialer.satellite;

import android.app.PendingIntent;

/**
 * Container class of satellite information sent from framework
 * This class consists of:
 *      PendingIntent - used to launch either default messaging application
 *                      or satellite gateway application
 *      Handover type - indicates whether the emergency call will be handed
 *                      off to satellite SOS messaging or satellite T911
 *                      messaging
 *      boolean       - indicates whether a disconnect dialog should be shown
 *                      to the user when the call is disconnected.
 */

public class SatelliteInfo {

    private PendingIntent mPendingIntent;
    private int mHandoverType;
    // indicates whether disconnect dialog should be shown
    // should be shown if user doesn't launch satellite messaging application
    private boolean mShouldShowDisconnectDialog;

    public SatelliteInfo(PendingIntent pendingIntent, int handoverType) {
        mPendingIntent = pendingIntent;
        mHandoverType = handoverType;
        mShouldShowDisconnectDialog = true;
    }

    public PendingIntent getIntent() {
        return mPendingIntent;
    }

    public int getHandoverType() {
        return mHandoverType;
    }

    public boolean shouldShowDisconnectDialog() {
        return mShouldShowDisconnectDialog;
    }

    public void updateShowDisconnectDialog(boolean show) {
        mShouldShowDisconnectDialog = show;
    }

    public String toString() {
        return "SatelliteInfo {mPendingIntent = " + mPendingIntent +
                ", showDisconnectDialog: " + mShouldShowDisconnectDialog +
                ", mHandoverType = " + mHandoverType + "}";
    }
}
