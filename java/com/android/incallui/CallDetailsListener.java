/* Copyright (c) 2021 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *
 *  * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.incallui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import com.android.dialer.common.LogUtil;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.InCallPresenter.InCallDetailsListener;

import org.codeaurora.ims.QtiCallConstants;
import org.codeaurora.ims.QtiImsExtManager;
import org.codeaurora.ims.QtiImsException;
import org.codeaurora.ims.utils.QtiImsExtUtils;

/**
 * This class listens to details change from the {@class InCallDetailsListener}.
 * When call details change, this class is notified and dump the call details data.
 */
public class CallDetailsListener implements InCallDetailsListener {

    private static final String SEND_DATA_CHANNEL_INFO_TEST = "send_data_channel_info_test";
     /**
      * Intent action broadcasted when data channel elements(modemCallId and phoneId) are
      * available for MO.
      * This broadcast for testing purposes only.
      */
    public static final String ACTION_DATA_CHANNEL_INFO =
       "org.codeaurora.intent.action.DATA_CHANNEL_INFO";
    private static final int DEFAULT_MODEM_CALL_ID = -1;
    private boolean isDcInfoSent = false;

    /**
     * This method overrides onDetailsChanged method of {@class InCallDetailsListener}.
     * We are notified when call details changed.
     */
    @Override
    public void onDetailsChanged(DialerCall call, android.telecom.Call.Details details) {
        if (!LogUtil.isVerboseEnabled()) {
            return;
        }

        Log.v(this, "onDetailsChanged - call: " + call + "details: " + details);

        if (call == null || details == null ) {
            Log.v(this, "onDetailsChanged - Call/details is null. Return");
            return;
        }

        final Bundle callExtras = details.getExtras();

        if (callExtras == null) {
            Log.v(this, "onDetailsChanged - CallExtras are null. Return");
            return;
        }

        Log.v(this, "onDetailsChanged - call extras : " + callExtras);

        if (isDcInfoSent && call.getState() != DialerCallState.DIALING) {
            isDcInfoSent = false;
            return;
        }

        if (!isDcInfoSent && call.getState() == DialerCallState.DIALING) {
            maybeBroadcastDcInfoIntent(call);
        }
    }

    private boolean shouldSendDcInfo(Context context) {
        return context != null ? (Settings.Global.getInt(context.getContentResolver(),
                    SEND_DATA_CHANNEL_INFO_TEST, 0) == 1) : false;
    }

    private void maybeBroadcastDcInfoIntent(DialerCall call) {
        Context cxt = call.getContext();
        if (cxt == null || !shouldSendDcInfo(cxt)) {
            Log.v(this, "maybeBroadcastDcInfoIntent - context null or not send DC.");
            return;
        }
        int modemCallId = QtiCallUtils.getDcModemCallId(call);
        int phoneId = QtiCallUtils.getPhoneId(call);
        if (modemCallId == DEFAULT_MODEM_CALL_ID ||
                phoneId == QtiCallConstants.INVALID_PHONE_ID) {
            Log.v(this, "maybeBroadcastDcInfoIntent - phoneId/modemCallid is invalid.");
            return;
        }
        boolean isDcEnabled = false;
        try {
            QtiImsExtManager extMgr = BottomSheetHelper.getInstance().getQtiImsExtManager();
            isDcEnabled = extMgr != null ? extMgr.isDataChannelEnabled(phoneId) : false;
        } catch (QtiImsException e) {
            LogUtil.e("CallDetailsListener.maybeBroadcastDcInfoIntent", "isDataChannelEnabled" + e);
        }
        if (!isDcEnabled) {
            Log.v(this, "maybeBroadcastDcInfoIntent - DC is disabled.");
            return;
        }
        Log.v(this, "maybeBroadcastDcInfoIntent", "modemCallId : " + modemCallId
                + ", phoneId : " + phoneId + ", isDcEnabled : " + isDcEnabled
                + ", shouldSendDcInfo : " + shouldSendDcInfo(cxt));

        Intent intent = new Intent(ACTION_DATA_CHANNEL_INFO);
        intent.putExtra(QtiCallConstants.EXTRA_DATA_CHANNEL_MODEM_CALL_ID,
                modemCallId);
        intent.putExtra(QtiImsExtUtils.QTI_IMS_PHONE_ID_EXTRA_KEY, phoneId);
        cxt.sendBroadcast(intent, "com.qti.permission.RECEIVE_DC_INFO");
        isDcInfoSent = true;
    }
}
