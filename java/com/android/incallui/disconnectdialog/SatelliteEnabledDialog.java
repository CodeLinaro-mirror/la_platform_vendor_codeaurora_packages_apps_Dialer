/*
 * Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.incallui.disconnectdialog;

import com.android.dialer.common.LogUtil;
import com.android.dialer.satellite.SatelliteInfo;
import com.android.dialer.util.IntentUtil;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.telecom.DisconnectCause;
import android.telephony.satellite.SatelliteManager;
import android.util.Pair;
import com.android.incallui.call.DialerCall;

/** Satellite dialog shown to user after disconnect. */
public class SatelliteEnabledDialog implements DisconnectDialog {

  @Override
  public boolean shouldShow(DisconnectCause disconnectCause, DialerCall call) {
    if (call != null) {
      final SatelliteInfo info = call.getSatelliteInfo();
      LogUtil.i("SatelliteEnabledDialog", "info: " + call.getSatelliteInfo());
      if (info != null) {
        return info.shouldShowDisconnectDialog();
      }
    }
    return shouldShow(disconnectCause);
  }

  @Override
  public boolean shouldShow(DisconnectCause disconnectCause) {
    return disconnectCause.getTelephonyDisconnectCause() ==
            android.telephony.DisconnectCause.SATELLITE_ENABLED;
  }

  @Override
  public Pair<Dialog, CharSequence> createDialog(@NonNull Context context, DialerCall call) {
    DisconnectCause disconnectCause = call.getDisconnectCause();
    CharSequence title = context.getString(R.string.satellite_messaging_title);
    CharSequence message = context.getString(R.string.satellite_generic_message);
    Dialog dialog = null;
    // If a call is disconnected in framework due to DisconnectCause#SATELLITE_ENABLED
    // create an intent which will launch the default messaging application
    // If the call is disconnected normally either by the user or remotely, and the user did
    // not launch the pending intent, then show a disconnect dialog to allow the user another
    // opportunity (depending on the handover type, the message/option will differ)
    if (disconnectCause.getTelephonyDisconnectCause() ==
            android.telephony.DisconnectCause.SATELLITE_ENABLED) {
        message = disconnectCause.getDescription();
        Intent intent = createDefaultSmsIntent(call.getNumber());
        dialog = createLaunchMessagingAppDialog(context, title, message, intent);
    } else {
        SatelliteInfo info = call.getSatelliteInfo();
        // pass null dialog, in which case a dialog won't be shown
        if (info == null) {
            return new Pair<>(null, message);
        }
        CharSequence application = context.getString(R.string.launch_messaging_application);
        if (info.getHandoverType() ==
                SatelliteManager.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS) {
            application = context.getString(R.string.launch_satellite_application);
            title = context.getString(R.string.satellite_sos_title);
        }
        dialog = createLaunchSatelliteAppDialog(
            context, title, message, application, info);
    }

    return new Pair<>(dialog, message);
  }

  private Intent createDefaultSmsIntent(String number) {
    return IntentUtil.getSendSmsIntent(number);
  }

  // Helper function to create dialog to launch default SMS application
  private Dialog createLaunchMessagingAppDialog(Context context, CharSequence title,
      CharSequence message, Intent intent) {
    return new AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(R.string.launch_messaging_application,
                (dialogInterface, id) -> {
                    context.startActivity(intent);
                })
        .setNegativeButton(android.R.string.cancel, null)
        .create();
  }

  // Helper function to create dialog to launch pending intent
  private Dialog createLaunchSatelliteAppDialog(Context context, CharSequence title,
      CharSequence message,
      CharSequence application, SatelliteInfo info) {
    return new AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(application,
                (dialogInterface, id) -> {
                    IntentUtil.maybeLaunchSatellitePendingIntent(info.getIntent());
                })
        .setNegativeButton(android.R.string.cancel, null)
        .create();
  }
}
