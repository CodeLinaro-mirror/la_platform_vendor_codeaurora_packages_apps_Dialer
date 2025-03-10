/**
 Copyright (c) 2015-2019, 2021 The Linux Foundation. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
     * Redistributions in binary form must reproduce the above
*       copyright notice, this list of conditions and the following
*       disclaimer in the documentation and/or other materials provided
*       with the distribution.
*     * Neither the name of The Linux Foundation nor the names of its
*       contributors may be used to endorse or promote products derived
*       from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
* ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
* BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
* WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
* OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Changes from Qualcomm Innovation Center, Inc. are provided under the following license:
 * Copyright (c) 2024-2025 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
*/

package com.android.incallui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.Connection.VideoProvider;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsMmTelManager;
import android.widget.Toast;
import android.telecom.VideoProfile;
import com.android.dialer.util.PermissionsUtil;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.state.DialerCallState;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.*;
import org.codeaurora.ims.QtiCallConstants;
import org.codeaurora.ims.utils.QtiImsExtUtils;

/**
 * This class contains Qti specific utiltity functions.
 */
public class QtiCallUtils {

    private static String LOG_TAG = "QtiCallUtils";
    //Maximum number of IMS phones in device.
    private static final int MAX_IMS_PHONE_COUNT = 2;
    public static final int REQUEST_ADD_PARTICIPANT = 1000;
    // ensure this extra is same the one defined in ConferenceURIDialer.java
    public static final String EXTRA_ADD_PARTICIPANT_NUMBER =
            "org.codeaurora.extra.ADD_PARTICIPANT_NUMBER";
    private static final int UNKNOWN_CALL_TYPE = -1;
    private static final int DEFAULT_CALL_INDEX = -1;
    private static final int INVALID_MODEM_CALL_ID = -1;

    /**
     * Returns true if it is emergency number else false
     */
    public static boolean isEmergencyNumber(Context context, String number) {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.isEmergencyNumber(number);
    }

   /**
     * Displays the string corresponding to the resourceId as a Toast on the UI
     */
    public static void displayToast(Context context, int resourceId) {
      if (context == null) {
          Log.w(LOG_TAG, "displayToast context is null");
          return;
      }
      displayToast(context, context.getResources().getString(resourceId));
    }

    /**
     * Displays the message as a Toast on the UI
     */
    public static void displayToast(Context context, String msg) {
      if (context == null) {
          Log.w(LOG_TAG, "displayToast context is null");
          return;
      }
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

   /**
     * Checks the boolean flag in config file to figure out if we are going to use Qti extension or
     * not
     */
    public static boolean useExt(Context context) {
        if (context == null) {
            Log.w(context, "Context is null...");
        }
        return context != null && context.getResources().getBoolean(R.bool.video_call_use_ext);
    }

    public static List<Uri> getConferenceCallList(String num) {
        List<Uri> numList = new ArrayList<>();
        String[] splitNum = num.split(";");
        for (String number : splitNum) {
            numList.add(Uri.parse(number));
        }
        return numList;
    }

    /**
     * Converts the call type to string
     */
    public static String callTypeToString(int callType) {
        switch (callType) {
            case VideoProfile.STATE_BIDIRECTIONAL:
                return "VT";
            case VideoProfile.STATE_TX_ENABLED:
                return "VT_TX";
            case VideoProfile.STATE_RX_ENABLED:
                return "VT_RX";
        }
        return "";
    }

    public static boolean isVideoBidirectional(DialerCall call) {
        return call != null && VideoProfile.isBidirectional(call.getVideoState());
    }

    public static boolean isDualVideoSupported(DialerCall call) {
        return call != null && call.isDualVideoSupported();
    }

    public static boolean isDualVideo(DialerCall call) {
        return call != null ? isDualVideo(call.getVideoState()) : false;
    }

    public static boolean isDualVideo(int videoState) {
        Log.v(LOG_TAG, "isDualVideo: " + videoState);
        return videoState == QtiCallConstants.STATE_DUAL_BIDIRECTIONAL;
    }

    public static boolean isVideoTxOnly(DialerCall call) {
        if (call == null) {
            return false;
        }
        return isVideoTxOnly(call.getVideoState());
    }

    public static boolean isVideoTxOnly(int videoState) {
        return VideoProfile.isTransmissionEnabled(videoState) &&
                !VideoProfile.isReceptionEnabled(videoState);
    }

    public static boolean isVideoRxOnly(DialerCall call) {
        if (call == null) {
            return false;
        }
        int videoState = call.getVideoState();
        return !VideoProfile.isTransmissionEnabled(videoState) &&
                VideoProfile.isReceptionEnabled(videoState);
    }

    public static boolean isVideoRxOnly(int videoState) {
        return !VideoProfile.isTransmissionEnabled(videoState) &&
            VideoProfile.isReceptionEnabled(videoState);
    }

    /**
     * Returns true if the CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO is set to false.
     * Note that - CAPABILITY_SUPPORTS_DOWNGRADE_TO_VOICE_LOCAL and
     * CAPABILITY_SUPPORTS_DOWNGRADE_TO_VOICE_REMOTE maps to
     * CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO
     */
    public static boolean hasVoiceCapabilities(DialerCall call) {
        return call != null &&
                !call.can(android.telecom.Call.Details.CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO);
    }

    /**
     * Returns true if local has the VT Transmit and if remote capability has VT Receive set i.e.
     * Local can transmit and remote can receive
     */
    public static boolean hasTransmitVideoCapabilities(DialerCall call) {
        return call != null &&
                call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_TX)
                && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_RX);
    }

    /**
     * Returns true if local has the VT Receive and if remote capability has VT Transmit set i.e.
     * Local can transmit and remote can receive
     */
    public static boolean hasReceiveVideoCapabilities(DialerCall call) {
        return call != null &&
                call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_RX)
                && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE_TX);
    }

     /**
      * Returns true if both voice and video capabilities (see above) are set
      */
     public static boolean hasVoiceOrVideoCapabilities(DialerCall call) {
         return hasVoiceCapabilities(call) || hasTransmitVideoCapabilities(call)
                 || hasReceiveVideoCapabilities(call);
     }

    public static CharSequence getLabelForIncomingWifiVideoCall(Context context) {
        final DialerCall call = getIncomingOrActiveCall();

        if (call == null) {
            return context.getString(R.string.contact_grid_incoming_wifi_video_call);
        }

        final int requestedVideoState = call.getVideoTech().getRequestedVideoState();

        if (QtiCallUtils.isVideoRxOnly(call)
            || requestedVideoState == VideoProfile.STATE_RX_ENABLED) {
            return context.getString(R.string.incoming_wifi_video_rx_call);
        } else if (QtiCallUtils.isVideoTxOnly(call)
            || requestedVideoState == VideoProfile.STATE_TX_ENABLED) {
            return context.getString(R.string.incoming_wifi_video_tx_call);
        } else {
            return context.getString(R.string.contact_grid_incoming_wifi_video_call);
        }
    }

    public static CharSequence getLabelForIncomingVideoCall(Context context) {
        final DialerCall call = getIncomingOrActiveCall();
        if (call == null) {
            return context.getString(R.string.contact_grid_incoming_video_call);
        }

        final int requestedVideoState = call.getVideoTech().getRequestedVideoState();

        if (isVideoCrs(call)) {
            return context.getString(R.string.incoming_video_crs_call);
        }

        if (QtiCallUtils.isVideoRxOnly(call)
            || requestedVideoState == VideoProfile.STATE_RX_ENABLED) {
            return context.getString(R.string.incoming_video_rx_call);
        } else if (QtiCallUtils.isVideoTxOnly(call)
            || requestedVideoState == VideoProfile.STATE_TX_ENABLED) {
            return context.getString(R.string.incoming_video_tx_call);
        } else {
            return context.getString(R.string.contact_grid_incoming_video_call);
        }
    }

    public static DialerCall getIncomingOrActiveCall() {
        CallList callList = InCallPresenter.getInstance().getCallList();
        if (callList == null) {
           return null;
        } else {
           return callList.getIncomingOrActive();
        }
    }

    public static DialerCall getIncomingCall() {
        CallList callList = InCallPresenter.getInstance().getCallList();
        return callList == null ? null : callList.getIncomingCall();
    }

    /** This method converts the QtiCallConstants' Orientation modes to the ActivityInfo
     * screen orientation.
     */
    public static int toScreenOrientation(int orientationMode) {
        switch(orientationMode) {
            case QtiCallConstants.ORIENTATION_MODE_LANDSCAPE:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case QtiCallConstants.ORIENTATION_MODE_PORTRAIT:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case QtiCallConstants.ORIENTATION_MODE_DYNAMIC:
                return ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
            default:
                return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
    }

    private static boolean enforceReadPhoneState(Context context, String message) {
        try {
            context.enforceCallingOrSelfPermission(
                    android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE, message);
            return true;
        } catch (SecurityException e) {
            context.enforceCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_STATE,
                    message);
            return false;
        }
    }

    public static int getSubId(Context context, int phoneId) {
        SubscriptionManager subscriptionManager =
                (SubscriptionManager) context.getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (subscriptionManager == null) {
            Log.e(LOG_TAG, "getSubId SubscriptionManager is null");
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        SubscriptionInfo subInfo =
                subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(phoneId);
        if (subInfo == null) {
            Log.e(LOG_TAG, "getSubId subInfo is null");
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        return subInfo.getSubscriptionId();
    }

   /**
    * Whether ims is registered
    * @param context of the activity
    * @param int phoneId which need to check
    * @return boolean whether ims is registered
    */
    private static boolean isImsRegistered(Context context, int phoneId,
            TelephonyManager telephonyManager) {
        int subId = getSubId(context, phoneId);
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.e(LOG_TAG, "isImsRegistered subId is invalid");
            return false;
        }
        return telephonyManager.isImsRegistered(subId);
    }

   /**
    * Show adhoc conference call menu option if any phone account has adhoc conf capability.
    * @param context of the activity.
    * @return boolean whether should show adhoc conference dialer menu option.
    */
    public static boolean shouldShowAdhocConferenceCallOption(Context context) {
        if (!PermissionsUtil.hasPhonePermissions(context) ||
                !enforceReadPhoneState(context, "shouldShowAdhocConferenceCallOption")) {
            Log.i(LOG_TAG, "shouldShowAdhocConferenceCallOption no phone permissions");
            return false;
        }

        TelecomManager telecomManager = context.getSystemService(TelecomManager.class);

        for (PhoneAccountHandle accountHandle : telecomManager.getCallCapablePhoneAccounts()) {
            PhoneAccount account = telecomManager.getPhoneAccount(accountHandle);
            if (account != null &&
                    account.hasCapabilities(PhoneAccount.CAPABILITY_ADHOC_CONFERENCE_CALLING)) {
                Log.d(LOG_TAG, "shouldShowAdhocConferenceCallOption found" +
                        " adhoc conf call phoneacc");
                return true;
            }
        }
        return false;
    }

   /**
    * Open phone account selection menu if both accounts
    * support adhoc conference, else pick the adhoc supported one when
    * default outgoing account is not set.
    * Pick the default outgoing one if it supports adhoc conference
    * else show error toast to change default outoging account.
    * @param context of the activity.
    * @param string number we want to pass to conference dialer.
    * @return void.
    */
    public static void choosePhoneAccountforAdhocConference(Context context, String number) {
        if (!PermissionsUtil.hasPhonePermissions(context)) {
            return;
        }
        boolean isOperatorSpecific = false;
        boolean isVtConferenceSupported = false;
        TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
        TelephonyManager telephonyManager =
            (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        PhoneAccountHandle defaultPhoneAccount = telecomManager.getDefaultOutgoingPhoneAccount(
                PhoneAccount.SCHEME_TEL);
        PhoneAccount defaultAccount = telecomManager.getPhoneAccount(defaultPhoneAccount);
         if (defaultAccount != null) {
            if(defaultAccount.hasCapabilities(PhoneAccount.CAPABILITY_ADHOC_CONFERENCE_CALLING)){
                int phoneId =
                        SubscriptionManager.getPhoneId
                        (telephonyManager.getSubIdForPhoneAccount(defaultAccount));
                context.startActivity(getConferenceDialerIntent(context, number, phoneId));
            } else {
                Toast.makeText(context, context.getString(
                        R.string.adhoc_conference_call_not_supported),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            int supportedAdhocConferenceAccounts = 0;
            int phoneId = -1;
            for (PhoneAccountHandle accountHandle : telecomManager.getCallCapablePhoneAccounts()) {
                PhoneAccount account = telecomManager.getPhoneAccount(accountHandle);
                if (account != null &&
                        account.hasCapabilities(PhoneAccount.CAPABILITY_ADHOC_CONFERENCE_CALLING)) {
                    Log.d(LOG_TAG, "show4gConferenceDialerMenuOption found" +
                            " ahoc conf call phoneacc");
                    supportedAdhocConferenceAccounts++;
                    phoneId =
                            SubscriptionManager.getPhoneId(
                            telephonyManager.getSubIdForPhoneAccount(account));
                }
            }
            if(supportedAdhocConferenceAccounts > 1){
                showPhoneAccountMenuForConferenceDialer(context, telephonyManager, number);
            } else {
                context.startActivity(getConferenceDialerIntent(context, number, phoneId));
            }
        }
    }

   /**
    * Open user selected 4G dialer.
    * @param context of the activity.
    * @param telephony manager object for sdk function.
    * @param string number we want to pass to conference dialer.
    * @return void.
    */
    public static void showPhoneAccountMenuForConferenceDialer(Context context,
            TelephonyManager telephonyManager, String number) {

        SubscriptionManager subscriptionManager =
                (SubscriptionManager) context.getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        int activeModemCount = telephonyManager.getActiveModemCount();
        ArrayList<String> subInfoList = new ArrayList<>();
        ArrayList<Integer> subIdList = new ArrayList<>();

        for (int slotId = 0; slotId < activeModemCount; slotId++) {
            SubscriptionInfo info = subscriptionManager.
                    getActiveSubscriptionInfoForSimSlotIndex(slotId);
            if (info != null) {
                String phoneNumber = "";
                int subId = info.getSubscriptionId();
                try {
                    phoneNumber = subscriptionManager.getPhoneNumber(subId);
                } catch (IllegalStateException
                        | SecurityException
                        | UnsupportedOperationException e) {
                    Log.w(LOG_TAG, "get number error." + e);
                }
                // If we could get the phone number, shows operator name + phone number.
                // Otherwise, shows operator name + slotId
                if (phoneNumber != null && !phoneNumber.isEmpty()) {
                    subInfoList.add(info.getDisplayName().toString() + "\n" + phoneNumber);
                } else {
                    subInfoList.add(info.getDisplayName().toString() + " " + slotId);
                }
                subIdList.add(subId);
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.select_your_option);
        builder.setItems(subInfoList.toArray(new String[0]),
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //The user clicked on options[which]
                Log.d(LOG_TAG, "onClick : which option = " + which);
                context.startActivity(getConferenceDialerIntent(context,
                        number, SubscriptionManager.getPhoneId(subIdList.get(which))));

            }
        });
        builder.setNegativeButton(R.string.select_your_4g_dialer_cancel_option,
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //The user clicked on Cancel
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

   /**
    * get phoneaccount for a specific phoneid
    * @param context of the activity.
    * @param int phoneid.
    * @return phoneaccount for that particular phoneid.
    */
    private static PhoneAccountHandle getPhoneAccountHandle(Context context, int phoneId){
        TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
        TelephonyManager telephonyManager =
            (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        PhoneAccountHandle phoneAccountHandle = null;
        for (PhoneAccountHandle accountHandle : telecomManager.getCallCapablePhoneAccounts()) {
                PhoneAccount account = telecomManager.getPhoneAccount(accountHandle);
                if(phoneId ==
                        SubscriptionManager.getPhoneId(
                        telephonyManager.getSubIdForPhoneAccount(account))) {
                    phoneAccountHandle = accountHandle;
                    break;
                }
        }
        return phoneAccountHandle;
    }

   /**
    * get intent to start conference dialer
    * with this intent, we can originate an conference call
    */
    public static Intent getConferenceDialerIntent(Context context,
                                                   String number, int phoneId) {
        Intent intent = new Intent("org.codeaurora.confdialer.ACTION_LAUNCH_CONF_DIALER");
        intent.putExtra("confernece_number_key", number);
        intent.putExtra("phone_id", phoneId);
        intent.putExtra("phone_account_handle", getPhoneAccountHandle(context, phoneId));
        return intent;
    }

   /**
    * used to get intent to start conference dialer
    * with this intent, we can add participants to an existing conference call
    */
    public static Intent getAddParticipantsIntent(Context context,
                                                  String number, int phoneId) {
        Intent intent = new Intent("org.codeaurora.confdialer.ACTION_LAUNCH_CONF_DIALER");
        intent.putExtra("add_participant", true);
        intent.putExtra("current_participant_list", number);
        intent.putExtra("phone_id", phoneId);
        intent.putExtra("phone_account_handle", getPhoneAccountHandle(context, phoneId));
        return intent;
    }

    //Checks if it is VoLTE call with Video CRBT
    public static boolean hasVideoCrbtVoLteCall(Context context, DialerCall call) {
        if (context == null) {
            Log.w(LOG_TAG, "context is null");
            return false;
        }
        if (call == null) {
            Log.w(LOG_TAG, "call is null");
            return false;
        }
        final Bundle extras = call.getExtras();
        if (extras == null) {
            return false;
        }
        return extras.getBoolean(QtiCallConstants.EXTRA_IS_CRBT_CALL, false);
    }

    public static boolean hasVideoCrbtVoLteCall(Context context) {
        return hasVideoCrbtVoLteCall(context, CallList.getInstance().getFirstCall());
    }

    //Checks if it is VT call with Video CRBT, it is limitation that CRBT flag will not exist in
    //VT call with video CRBT, so we could only rely on the carrier + dialing state + VT-BI call
    //type + early media, early media condition is implemented in VideoCallPresenter.java
    public static boolean hasVideoCrbtVtCall(Context context) {
        if (context == null) {
            Log.w(LOG_TAG, "context is null");
            return false;
        }
        DialerCall call = CallList.getInstance().getFirstCall();
        if (call == null) {
            Log.w(LOG_TAG, "call is null");
            return false;
        }
        if (call.getState() != DialerCallState.DIALING) {
            Log.w(LOG_TAG, "call is not dialing state.");
            return false;
        }
        return QtiImsExtUtils.isVideoCrbtSupported(BottomSheetHelper.getInstance().getPhoneId(),
                context) && isVideoBidirectional(call);
    }

    //Checks if incoming call has video CRS
    public static boolean isVideoCrs(DialerCall call) {
        if (call == null) {
            Log.w(LOG_TAG, "call is null");
            return false;
        }
        if (call.getState() != DialerCallState.INCOMING) {
            Log.w(LOG_TAG, "call is not incoming state.");
            return false;
        }
        Bundle extras = call.getExtras();
        if (extras == null) {
            return false;
        }
        int crsType = extras.getInt(QtiCallConstants.EXTRA_CRS_TYPE,
                QtiCallConstants.CRS_TYPE_INVALID);
        return  (crsType & QtiCallConstants.CRS_TYPE_VIDEO) == QtiCallConstants.CRS_TYPE_VIDEO;
    }

    //Checks what's original call type of video CRS
    private static int getOriginalCallType(DialerCall call) {
        if (!isVideoCrs(call)) {
            return call.getVideoState();
        }
        //Ideally if call has video CRS, then original call type should be there.
        Bundle extras = call.getExtras();
        if (extras == null) {
            return UNKNOWN_CALL_TYPE;
        }
        return extras.getInt(QtiCallConstants.EXTRA_ORIGINAL_CALL_TYPE,
                UNKNOWN_CALL_TYPE);
    }

    //Checks if original call type is VT with or without video CRS
    public static boolean isVideoCallOriginally(DialerCall call) {
        if (!isVideoCrs(call)) {
            return call == null ? false : call.isVideoCall();
        }
        int originalCallType = getOriginalCallType(call);
        if (originalCallType == UNKNOWN_CALL_TYPE) {
            Log.w(LOG_TAG, "Video CRS call has no original call type, it's not expected.");
        }
        return VideoProfile.STATE_BIDIRECTIONAL == originalCallType;
    }

    public static boolean isPreparatory(DialerCall call) {
        if (!isVideoCrs(call)) {
            return false;
        }
        Bundle extras = call.getExtras();
        if (extras == null) {
            return false;
        }
        return extras.getBoolean(QtiCallConstants.EXTRA_IS_PREPARATORY, false);
    }

    public static int getImsCallId(DialerCall call) {
        if (call == null) {
            return DEFAULT_CALL_INDEX;
        }
        Bundle extras = call.getExtras();
        if (extras == null) {
            return DEFAULT_CALL_INDEX;
        }
        return extras.getInt(QtiCallConstants.EXTRA_IMS_CALL_ID, DEFAULT_CALL_INDEX);
    }

    public static int getPhoneId(DialerCall call) {
        if (call == null) {
            return QtiCallConstants.INVALID_PHONE_ID;
        }
        final Bundle extras = call.getExtras();
        return ((extras == null) ? QtiCallConstants.INVALID_PHONE_ID :
            extras.getInt(QtiImsExtUtils.QTI_IMS_PHONE_ID_EXTRA_KEY,
            QtiCallConstants.INVALID_PHONE_ID));
    }

    public static boolean isCustomerServiceNumber(Context context, String number) {
        if (context == null || number == null) {
            return false;
        }
        String[] customerNumbers = QtiImsExtUtils.getCustomerServiceNumbers(
                BottomSheetHelper.getInstance().getPhoneId(), context);
        if (customerNumbers == null) {
            return false;
        }
        for (String item : customerNumbers) {
            if (number.equals(item)) {
                return true;
            }
        }
        return false;
    }

    public static int getDcModemCallId(DialerCall call) {
        if (call == null) {
            return INVALID_MODEM_CALL_ID;
        }
        final Bundle extras = call.getExtras();
        return ((extras == null) ? INVALID_MODEM_CALL_ID :
            extras.getInt(QtiCallConstants.EXTRA_DATA_CHANNEL_MODEM_CALL_ID,
            INVALID_MODEM_CALL_ID));
    }

    /**
     * Check if call has visualized voice attribute, it could be used in the case,
     * if return true, the options VT-TX/RX will not be shown in the Modify call sheet
     * even if this visualized voice call is downgraded to voice call or upgraded to VT call.
     */
    public static boolean hasVisualizedVoiceAttribute(DialerCall call) {
        if (call == null) {
            return false;
        }
        final Bundle extras = call.getExtras();
        return ((extras == null) ? false :
            extras.getBoolean(QtiCallConstants.EXTRA_IS_VISUALIZED_VOICE_CALL, false));
    }


    /**
     * Check if it is VT-RX call with visualized voice attribute, it could be used
     * in the cases, if return true:
     *   1. Do not show local preview window
     *   2. Support to turn off screen while user ear is close to the screen
     *   3. Disallow the rotation
     *   4. Show voice call icon in status bar
     * If this visualized voice call is downgraded or upgraded, these Dialer UI
     * behaviors follow its current call type completely.
     */
    public static boolean isVisualizedVoiceCall(DialerCall call) {
        return hasVisualizedVoiceAttribute(call) && isVideoRxOnly(call);
    }

    public static boolean isVisualizedVoiceCall() {
        return isVisualizedVoiceCall(CallList.getInstance().getFirstCall());
    }
}
