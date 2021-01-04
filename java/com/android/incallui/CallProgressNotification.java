/* Copyright (c) 2021, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
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
 */

package com.android.incallui;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;

import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.InCallPresenter.InCallDetailsListener;

import java.util.HashMap;

import org.codeaurora.ims.QtiCallConstants;

/**
 * This class listens to details change from the {@class InCallDetailsListener}.
 * When call details change, this class is notified and we parse the callExtras from the details
 * and show the appropriate toast message to user.
 *
 */
public class CallProgressNotification implements InCallDetailsListener, CallList.Listener {

    private static CallProgressNotification sCallProgressNotification;
    private Context mContext;
    private Resources mResources;
    private final HashMap<String, Integer> mCallProgressInfoMap = new HashMap<>();

    // These values are based on Q850 defined by ITU. Ref: https://www.itu.int/rec/T-REC-Q.850
    private static final int CALL_REJECT_UNALLOCATED_NUMBER = 1;
    private static final int CALL_REJECT_USER_BUSY = 17;
    private static final int CALL_REJECT_NO_USER_RESPONSDING = 18;
    private static final int CALL_REJECT_NO_ANSWER_FROM_USER = 19;
    private static final int CALL_REJECT_SUBSCRIBER_ABSENT = 20;
    private static final int CALL_REJECT_NON_UNIQUE_REASON_CODE = 21;

    // Non unique call reject reason text received from network in english.
    private String mCallRejectedReasonFromNw;
    private String mUserCallRejectedReasonFromNw;
    private String mNonUserCallRejectedReasonFromNw;

    /**
     * This method returns a singleton instance of {@class CallProgressNotification}
     */
    public static synchronized CallProgressNotification getInstance() {
        if (sCallProgressNotification == null) {
            sCallProgressNotification = new CallProgressNotification();
        }
        return sCallProgressNotification;
    }

    public void setUp(Context context) {
        mContext = context;
        mResources = mContext.getResources();
        InCallPresenter.getInstance().addDetailsListener(this);

        mCallRejectedReasonFromNw = mContext.getString(R.string.
                call_progress_info_call_rejected_reason_from_nw);
        mUserCallRejectedReasonFromNw = mContext.getString(R.string.
                call_progress_info_user_call_rejected_reason_from_nw);
        mNonUserCallRejectedReasonFromNw = mContext.getString(R.string.
                call_progress_info_nonuser_call_rejected_reason_from_nw);
    }

    public void tearDown() {
        InCallPresenter.getInstance().removeDetailsListener(this);
        mResources = null;
        mContext = null;
    }

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private CallProgressNotification() {
    }

    /**
     * This method overrides onDetailsChanged method of {@class InCallDetailsListener}.
     * We are notified when call details changed.
     */
    @Override
    public void onDetailsChanged(DialerCall call, android.telecom.Call.Details details) {
        Log.d(this, "onDetailsChanged - call: " + call + "details: " + details);

        if (call == null || details == null || !DialerCallState.isDialing(call.getState())) {
            Log.d(this, "onDetailsChanged - Call/details is null/Call is not Dialing. Return");
            return;
        }

        final Bundle callExtras = details.getExtras();

        if (callExtras == null) {
            Log.d(this, "onDetailsChanged - CallExtras are null. Return");
            return;
        }

        final String callId = call.getId();
        final int oldCallProgressInfoType = mCallProgressInfoMap.containsKey(callId) ?
                mCallProgressInfoMap.get(callId) : QtiCallConstants.CALL_PROGRESS_INFO_TYPE_INVALID;

        final int newCallProgressInfoType = callExtras.getInt(
                QtiCallConstants.EXTRAS_CALL_PROGRESS_INFO_TYPE,
                QtiCallConstants.CALL_PROGRESS_INFO_TYPE_INVALID);

        if (oldCallProgressInfoType == newCallProgressInfoType) {
            Log.d(this, "onDetailsChanged - oldCallProgressInfoType : " + oldCallProgressInfoType +
                    " newCallProgressInfoType : " + newCallProgressInfoType);
            return;
        }

        mCallProgressInfoMap.put(callId, newCallProgressInfoType);

        if (newCallProgressInfoType == QtiCallConstants.CALL_PROGRESS_INFO_TYPE_INVALID) {
            Log.d(this, "onDetailsChanged - Received invalid call info type from network");
            return;
        }

        final String callInfoReasonText = getCallInfoReasonText(newCallProgressInfoType,
                callExtras);

        if (callInfoReasonText == null) {
            Log.d(this, "onDetailsChanged - Received empty call info reason text.");
            return;
        }

        QtiCallUtils.displayToast(mContext, callInfoReasonText);

    }

    private String getCallInfoReasonText(int callProgressInfoType, Bundle callExtras) {

        if (mResources == null) {
            Log.d(this, "getCallInfoReasonText - resources are empty");
            return null;
        }

        switch (callProgressInfoType) {
            case QtiCallConstants.CALL_PROGRESS_INFO_TYPE_CALL_REJ_Q850:
                return getCallInfoCallRejectReason(callExtras);
            case QtiCallConstants.CALL_PROGRESS_INFO_TYPE_CALL_WAITING:
                return mResources.getString(R.string.call_progress_info_call_waiting);
            case QtiCallConstants.CALL_PROGRESS_INFO_TYPE_CALL_FORWARDING:
                return mResources.getString(R.string.call_progress_info_call_forwarding);
            case QtiCallConstants.CALL_PROGRESS_INFO_TYPE_REMOTE_AVAILABLE:
                return mResources.getString(R.string.call_progress_info_remote_available);
            default:
                return null;
        }
    }

    private String getCallInfoCallRejectReason(Bundle callExtras) {
        final int reasonCode = callExtras.getInt(QtiCallConstants.EXTRAS_CALL_PROGRESS_REASON_CODE,
                QtiCallConstants.CALL_REJECTION_CODE_INVALID);

        if (reasonCode == QtiCallConstants.CALL_REJECTION_CODE_INVALID) {
            Log.d(this, "getCallInfoCallRejectReason - Received invalid call info reason code" +
                    " from network");
            return null;
        }

        String rejectReason = getReasonForUniqueReasonCode(reasonCode);

        return rejectReason == null ? getReasonForNonUniqueReasonCode(reasonCode, callExtras) :
                rejectReason;
    }

    private String getReasonForUniqueReasonCode(int reasonCode) {
        switch (reasonCode) {
            case CALL_REJECT_UNALLOCATED_NUMBER:
                return mResources.getString(R.string.call_progress_info_unallocated_number);
            case CALL_REJECT_SUBSCRIBER_ABSENT:
                return mResources.getString(R.string.call_progress_info_subscriber_absent);
            case CALL_REJECT_NO_USER_RESPONSDING:
                return mResources.getString(R.string.call_progress_info_no_user_responding);
            case CALL_REJECT_USER_BUSY:
                return mResources.getString(R.string.call_progress_info_user_busy);
            case CALL_REJECT_NO_ANSWER_FROM_USER:
                return mResources.getString(R.string.call_progress_info_no_answer_from_user);
            default:
                return null;
        }
    }

    private String getReasonForNonUniqueReasonCode(int reasonCode, Bundle callExtras) {
        String reasonText = callExtras.getString(
                QtiCallConstants.EXTRAS_CALL_PROGRESS_REASON_TEXT, null);

        if (reasonText == null || reasonCode != CALL_REJECT_NON_UNIQUE_REASON_CODE) {
            Log.d(this, "getReasonForNonUniqueReasonCode - Received invalid call info reason text" +
                    " or invalid reason code from network");
            return null;
        }

        //Remove trailing/leading white spaces in string
        reasonText = reasonText.trim();
        Log.d(this, "getReasonForNonUniqueReasonCode - reason text : " + reasonText);

        if (reasonText.equals(mCallRejectedReasonFromNw)) {
            return mResources.getString(R.string.call_progress_info_call_rejected);
        } else if (reasonText.equals(mUserCallRejectedReasonFromNw)) {
            return mResources.getString(R.string.call_progress_info_user_call_rejected);
        } else if (reasonText.equals(mNonUserCallRejectedReasonFromNw)) {
            return mResources.getString(R.string.call_progress_info_nonuser_call_rejected);
        }

        return null;
    }

    /**
     * This method overrides onDisconnect method of {@interface CallList.Listener}
     */
    @Override
    public void onDisconnect(final DialerCall call) {
        Log.d(this, "onDisconnect: call: " + call);
        mCallProgressInfoMap.remove(call.getId());
    }

    @Override
    public void onUpgradeToVideo(DialerCall call) {
        //NO-OP
    }

    @Override
    public void onIncomingCall(DialerCall call) {
        //NO-OP
    }

    @Override
    public void onCallListChange(CallList callList) {
        //NO-OP
    }

    @Override
    public void onSessionModificationStateChange(DialerCall call) {
        //NO-OP
    }

    @Override
    public void onWiFiToLteHandover(DialerCall call) {
        //NO-OP
    }

    @Override
    public void onHandoverToWifiFailed(DialerCall call) {
        //NO-OP
    }

    @Override
    public void onInternationalCallOnWifi(DialerCall call) {
        //NO-OP
    }

    @Override
    public void onSuplServiceMessage(String suplNotificationMessage) {
        //NO-OP
    }
}
