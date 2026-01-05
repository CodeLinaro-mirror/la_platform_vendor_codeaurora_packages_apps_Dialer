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
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.incallui;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;

import com.android.incallui.call.DialerCall;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallDisconnectedListener;

import java.util.HashMap;

import org.codeaurora.ims.QtiCallConstants;

/**
 * This class listens to details change from the {@class InCallDetailsListener}.
 * When call details change, this class is notified and we parse the callExtras from the details
 * and show the appropriate toast message to user.
 *
 */
public class CallProgressNotification implements InCallDetailsListener, InCallDisconnectedListener {

    private static CallProgressNotification sCallProgressNotification;
    private Context mContext;
    private Resources mResources;
    private final HashMap<String, String> mCallProgressInfoMap = new HashMap<>();

    // These values are based on Q850 defined by ITU. Ref: https://www.itu.int/rec/T-REC-Q.850
    private static final int CALL_REJECT_UNALLOCATED_NUMBER = 1;
    private static final int CALL_REJECT_USER_BUSY = 17;
    private static final int CALL_REJECT_NO_USER_RESPONSDING = 18;
    private static final int CALL_REJECT_NO_ANSWER_FROM_USER = 19;
    private static final int CALL_REJECT_SUBSCRIBER_ABSENT = 20;
    private static final int CALL_REJECT_NON_UNIQUE_REASON_CODE = 21;
    private static final int CALL_REJECT_INVALID_NUMBER_FORMAT = 28;

    /**
     * Sip 603 response : Decline
     */
    private static final int CODE_SIP_USER_REJECTED = 603;

    // Non unique call reject reason text received from network in english.
    private String mCallRejectedReasonFromNw;
    private String mUserCallRejectedReasonFromNw;
    private String mNonUserCallRejectedReasonFromNw;

    private final int[] types = {
            QtiCallConstants.CALL_PROGRESS_INFO_TYPE_CALL_WAITING,
            QtiCallConstants.CALL_PROGRESS_INFO_TYPE_CALL_REJ_Q850,
            QtiCallConstants.CALL_PROGRESS_INFO_TYPE_CALL_REJ_SIP,
            QtiCallConstants.CALL_PROGRESS_INFO_TYPE_CALL_WARNING
    };

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
        InCallPresenter.getInstance().addInCallDisconnectedListener(this);

        mCallRejectedReasonFromNw = mContext.getString(R.string.
                call_progress_info_call_rejected_reason_from_nw);
        mUserCallRejectedReasonFromNw = mContext.getString(R.string.
                call_progress_info_user_call_rejected_reason_from_nw);
        mNonUserCallRejectedReasonFromNw = mContext.getString(R.string.
                call_progress_info_nonuser_call_rejected_reason_from_nw);
    }

    public void tearDown() {
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallPresenter.getInstance().removeInCallDisconnectedListener(this);
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

        if (call == null || details == null) {
            Log.d(this, "onDetailsChanged - Call/details is null. Return");
            return;
        }

        if (mContext == null || mResources == null) {
            Log.d(this, "onDetailsChanged - Not initialized. Return");
            return;
        }

        final int callState = call.getState();
        if (!(DialerCallState.isDialing(callState) || callState == DialerCallState.DISCONNECTING
            || callState == DialerCallState.DISCONNECTED)) {
            Log.d(this, "onDetailsChanged - Call is not Dialing/End. Return");
            return;
        }

        final Bundle callExtras = details.getExtras();

        if (callExtras == null) {
            Log.d(this, "onDetailsChanged - CallExtras are null. Return");
            return;
        }

        final String callId = call.getId();

        if (callId == null) {
            Log.d(this, "onDetailsChanged - Call ID is null. Return");
            return;
        }

        final String oldCallInfoReasonText = mCallProgressInfoMap.containsKey(callId) ?
                mCallProgressInfoMap.get(callId) : "";

        StringBuilder callInfoReasonTextBuilder = new StringBuilder();

        for (int i = 0; i < types.length; i++) {
            String text = getCallProgressText(types[i], callExtras);
            Log.d(this, "getCallProgressText - text : " + text);
            if (text != null && !text.isEmpty()) {
                // Toast is limited to two lines, so use double spaces to separate
                // multiple notifications.
                callInfoReasonTextBuilder.append(text).append("  ");
            }
        }

        final String callInfoReasonText = callInfoReasonTextBuilder.toString();

        if (oldCallInfoReasonText.equals(callInfoReasonText)) {
            Log.d(this, "onDetailsChanged - Call info reason text is not changed.");
            return;
        }

        mCallProgressInfoMap.put(callId, callInfoReasonText);

        if (callInfoReasonText.isEmpty()) {
            Log.d(this, "onDetailsChanged - Received empty call info reason text.");
            return;
        }

        // There is a known risk, in disconnected state, the toast will show multi times
        // because onDetailsChanged will be called multiple times, and onCallDisconnected
        // will be called once. And we could not ensure the order of these two callbacks.
        // TODO: Need to improve this logic to avoid showing multiple same toast.
        QtiCallUtils.displayToast(mContext, callInfoReasonText);

    }

    private String getCallInfoReasonText(int callProgressInfoType) {
        switch (callProgressInfoType) {
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

    private String getCallProgressText(int type, Bundle callExtras) {
        if (callExtras == null) {
            return null;
        }
        switch (type) {
            // Use CALL_PROGRESS_INFO_TYPE_CALL_WAITING instead of all three types:
            // CALL_PROGRESS_INFO_TYPE_CALL_WAITING, CALL_PROGRESS_INFO_TYPE_CALL_FORWARDING,
            // and CALL_PROGRESS_INFO_TYPE_REMOTE_AVAILABLE because we only want to get the value
            // in EXTRAS_CALL_PROGRESS_WFA_TYPE
            case QtiCallConstants.CALL_PROGRESS_INFO_TYPE_CALL_WAITING:
                int wfaType = callExtras.getInt(QtiCallConstants.EXTRAS_CALL_PROGRESS_WFA_TYPE,
                        QtiCallConstants.CALL_PROGRESS_INFO_TYPE_INVALID);
                Log.d(this, "getCallProgressText - wfaType : " + wfaType);
                if (wfaType == QtiCallConstants.CALL_PROGRESS_INFO_TYPE_INVALID) {
                    return null;
                }
                return getCallInfoReasonText(wfaType);
            case QtiCallConstants.CALL_PROGRESS_INFO_TYPE_CALL_REJ_Q850:
                int q850Code = callExtras.getInt(
                        QtiCallConstants.EXTRAS_CALL_PROGRESS_REJECT_Q850_CODE,
                        QtiCallConstants.CALL_REJECTION_CODE_INVALID);
                Log.d(this, "getCallProgressText - q850Code : " + q850Code);
                if (q850Code == QtiCallConstants.CALL_REJECTION_CODE_INVALID) {
                    return null;
                }
                return getCallInfoCallRejectQ850ReasonText(q850Code, callExtras);
            case QtiCallConstants.CALL_PROGRESS_INFO_TYPE_CALL_REJ_SIP:
                int sipCode = callExtras.getInt(
                        QtiCallConstants.EXTRAS_CALL_PROGRESS_REJECT_SIP_CODE,
                        QtiCallConstants.CALL_REJECTION_CODE_INVALID);
                Log.d(this, "getCallProgressText - sipCode : " + sipCode);
                if (sipCode == QtiCallConstants.CALL_REJECTION_CODE_INVALID) {
                    return null;
                }

                return getCallInfoCallRejectSipReasonText(sipCode, callExtras);
            case QtiCallConstants.CALL_PROGRESS_INFO_TYPE_CALL_WARNING:
                return callExtras.getString(QtiCallConstants.EXTRAS_CALL_PROGRESS_WARNING_TEXT,
                        null);
            default:
                return null;
        }
    }


    private String getCallInfoCallRejectQ850ReasonText(int reasonCode, Bundle callExtras) {
        final boolean isCalledPartyRinging = callExtras.getBoolean(
                QtiCallConstants.EXTRA_IS_CALLED_PARTY_RINGING);

        String rejectReasonText = getQ850ReasonForUniqueReasonText(reasonCode,
                isCalledPartyRinging);

        return rejectReasonText == null ? getQ850ReasonForNonUniqueReasonText(
                reasonCode, callExtras) : rejectReasonText;
    }

    private String getQ850ReasonForUniqueReasonText(int reasonCode, boolean isCalledPartyRinging) {
        switch (reasonCode) {
            case CALL_REJECT_UNALLOCATED_NUMBER:
                return mResources.getString(R.string.call_progress_info_unallocated_number);
            case CALL_REJECT_SUBSCRIBER_ABSENT:
                return mResources.getString(R.string.call_progress_info_subscriber_absent);
            case CALL_REJECT_NO_USER_RESPONSDING:
                return mResources.getString(R.string.call_progress_info_no_user_responding);
            case CALL_REJECT_USER_BUSY:
                if (isCalledPartyRinging) {
                    return mResources.getString(R.string.call_progress_info_user_busy_ringing);
                } else {
                    return mResources.getString(R.string.call_progress_info_user_busy_preringing);
                }
            case CALL_REJECT_NO_ANSWER_FROM_USER:
                return mResources.getString(R.string.call_progress_info_no_answer_from_user);
            case CALL_REJECT_INVALID_NUMBER_FORMAT:
                return mResources.getString(R.string.call_progress_info_invalid_number_format);
            default:
                return null;
        }
    }

    private String getQ850ReasonForNonUniqueReasonText(int reasonCode, Bundle callExtras) {
        if (reasonCode != CALL_REJECT_NON_UNIQUE_REASON_CODE) {
            Log.d(this, "getQ850ReasonForNonUniqueReasonText - invalid reason code from network");
            return null;
        }

        String reasonText = callExtras.getString(
                QtiCallConstants.EXTRAS_CALL_PROGRESS_REJECT_Q850_TEXT, null);

        if (reasonText == null ) {
            Log.d(this, "getQ850ReasonForNonUniqueReasonText - valid reason code but text is null"
                    + "set reason text as 'call rejected' ");
            return mResources.getString(R.string.call_progress_info_call_rejected);
        }

        //Remove trailing/leading white spaces in string
        reasonText = reasonText.trim();
        Log.d(this, "getQ850ReasonForNonUniqueReasonText - reason text : " + reasonText);

        if (reasonText.equals(mCallRejectedReasonFromNw)) {
            return mResources.getString(R.string.call_progress_info_call_rejected);
        } else if (reasonText.equals(mUserCallRejectedReasonFromNw)) {
            return mResources.getString(R.string.call_progress_info_user_call_rejected);
        } else if (reasonText.equals(mNonUserCallRejectedReasonFromNw)) {
            return mResources.getString(R.string.call_progress_info_nonuser_call_rejected);
        }

        return null;
    }

    private String getCallInfoCallRejectSipReasonText(int reasonCode, Bundle callExtras) {
        final boolean isCalledPartyRinging = callExtras.getBoolean(
                QtiCallConstants.EXTRA_IS_CALLED_PARTY_RINGING);

        String rejectReasonText = getRejectSipReasonForUniqueReasonText(reasonCode,
                isCalledPartyRinging);

        return rejectReasonText != null ? rejectReasonText :
            callExtras.getString(QtiCallConstants.EXTRAS_CALL_PROGRESS_REJECT_SIP_TEXT, null);
    }

    private String getRejectSipReasonForUniqueReasonText(int reasonCode,
            boolean isCalledPartyRinging) {
        switch (reasonCode) {
            case CODE_SIP_USER_REJECTED:
                return isCalledPartyRinging ?
                    mResources.getString(R.string.call_progress_info_user_busy_ringing) : null;
            default:
                return null;
        }
    }

    /**
     * This method overrides onDisconnect method of {@interface InCallDisconnectedListener}
     */
    @Override
    public void onCallDisconnected(final DialerCall call) {
        Log.d(this, "onDisconnect: call: " + call);
        mCallProgressInfoMap.remove(call.getId());
    }
}
