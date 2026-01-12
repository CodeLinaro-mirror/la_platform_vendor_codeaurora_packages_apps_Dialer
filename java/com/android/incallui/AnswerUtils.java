/* Copyright (c) 2020, The Linux Foundation. All rights reserved.
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
 * Changes from Qualcomm Technologies, Inc. are provided under the following license:
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.incallui;

import android.telecom.Call.Details;
import android.telecom.PhoneAccount;
import com.android.dialer.common.LogUtil;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCallListener;
import com.android.incallui.call.state.DialerCallState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AnswerUtils {

  private AnswerUtils() {}

  public static void disconnectCallsAndAnswer(int videoState, boolean needLaunchUi) {

    CallList callList = InCallPresenter.getInstance().getCallList();
    if (callList == null) {
      LogUtil.i("AnswerUtils.disconnectCallsAndAnswer", "CallList is null.");
      return;
    }
    DialerCall incomingCall = callList.getIncomingCall();
    if (incomingCall == null) {
      LogUtil.i("AnswerUtils.disconnectCallsAndAnswer", "No valid call found.");
      return;
    }

    List<DialerCall> callsToDisconnect = new ArrayList<>();
    Set<DialerCall> callsToIgnore = new HashSet<>();
    PhoneAccount incomingPa = incomingCall.getPhoneAccount();
    boolean isDsda = incomingPa != null && incomingPa.hasSimultaneousCallingRestriction() &&
        incomingPa.getSimultaneousCallingRestriction().size() > 0;
    for (DialerCall currentCall : callList.getAllCalls()) {
      boolean isCurrentCallHoldable = currentCall.can(Details.CAPABILITY_SUPPORT_HOLD);
      if (DialerCallState.isConnectingOrConnected(currentCall.getState()) &&
          !(currentCall.getState() == DialerCallState.INCOMING)) {
        // We want to allow Telecom to decide whether to keep or disconnect
        // the held call if we're in DSDA.
        if (isCurrentCallHoldable && isDsda) {
          callsToIgnore.add(currentCall);
          continue;
        }

        // This check is added for carriers not supporting hold, however implicit hold
        // is supported, in such cases when this API is called it should be Telecom
        // which determines the correct sequence of operations. However if device is
        // in Pseudo DSDA then disconnect should be initiated from Dialer.
        boolean isPseudoDsda = !isDsda && !incomingPa.equals(currentCall.getPhoneAccount());
        if (currentCall.shouldIgnoreExtraForDroppingFgCall() && !isPseudoDsda) {
          callsToIgnore.add(currentCall);
          continue;
        }
        callsToDisconnect.add(currentCall);
      }
    }

    for (DialerCall currentCall: callsToDisconnect) {
      currentCall.setReleasedByAnsweringSecondCall(true);
      currentCall.addListener(
          new AnswerOnDisconnected(currentCall, null, videoState, needLaunchUi, true,
          callsToIgnore));
      if (currentCall.getParentId() == null) {
        //Send disconnect only for parent calls and not for child calls.
        currentCall.disconnect();
      }
    }

    /* On successfully disconnecting the current calls, MT call will be answered
     * {@link AnswerOnDisconnected.onDialerCallDisconnect.
     * If there are no calls to disconnect then answer MT call immediately.
     */
    if (callsToDisconnect.isEmpty()) {
      LogUtil.i("AnswerUtils.disconnectCallsAndAnswer", "There are no calls to release," +
          " answer incoming call");
      callList.getIncomingCall().answer(videoState);
      if (needLaunchUi) {
        InCallPresenter.getInstance().showInCall(
            false /* showDialpad */, false /* newOutgoingCall */);
      }
    }
  }

  public static void disconnectCallsAndAnswer(int videoState) {
    disconnectCallsAndAnswer(videoState, false);
  }

  public static void disconnectAndAnswer(DialerCall callToDisconnect, DialerCall callToAnswer) {
    callToDisconnect.addListener(
        new AnswerOnDisconnected(callToDisconnect, callToAnswer, 0, false, false, null));
    callToDisconnect.disconnect();
  }

  private static class AnswerOnDisconnected implements DialerCallListener {

    private final DialerCall disconnectingCall;
    private final DialerCall callToAnswer;
    private final int videoState;
    private final boolean needLaunchUi;
    private final boolean disconnectAll;
    private Set<DialerCall> callsToIgnore;

    AnswerOnDisconnected(DialerCall disconnectingCall, DialerCall callToAnswer,
        int videoState, boolean needLaunchUi, boolean disconnectAll,
        Set<DialerCall> callsToIgnore) {
      this.disconnectingCall = disconnectingCall;
      this.callToAnswer = callToAnswer;
      this.videoState = videoState;
      this.needLaunchUi = needLaunchUi;
      this.disconnectAll = disconnectAll;
      this.callsToIgnore = callsToIgnore;
    }

    @Override
    public void onDialerCallDisconnect() {
      if (disconnectAll) {
        checkAndAnswerPendingIncomingCall();
      } else {
        if (callToAnswer != null) callToAnswer.answer();
      }
      disconnectingCall.removeListener(this);
    }

    private void checkAndAnswerPendingIncomingCall() {
      CallList callList = InCallPresenter.getInstance().getCallList();
      if (callList == null) {
        LogUtil.i("AnswerUtils.checkAndAnswerPendingIncomingCall", "CallList is null.");
        return;
      }
      DialerCall callToAnswer = null;
      // Only answer when all the calls except Incoming call is disconnected.
      // Ignore the calls in ignore list.
      for (DialerCall call: callList.getAllCalls()) {
        if (call.getState() == DialerCallState.INCOMING) {
          callToAnswer = call;
          continue;
        }
        if (call.getState() != DialerCallState.DISCONNECTED &&
            (callsToIgnore == null || !callsToIgnore.contains(call))) {
          LogUtil.i(
              "AnswerUtils.checkAndAnswerPendingIncomingCall",
              "Wait for more calls to get disconnected");
          return;
        }
      }

      LogUtil.i(
          "AnswerUtils.checkAndAnswerPendingIncomingCall", "Answering call: " + callToAnswer);
      if (callToAnswer != null) {
        callToAnswer.answer(videoState);
        if (needLaunchUi) {
          InCallPresenter.getInstance().showInCall(
              false /* showDialpad */, false /* newOutgoingCall */);
        }
      }
    }

    @Override
    public void onDialerCallUpdate() {}

    @Override
    public void onDialerCallChildNumberChange() {}

    @Override
    public void onDialerCallLastForwardedNumberChange() {}

    @Override
    public void onDialerCallUpgradeToVideo() {}

    @Override
    public void onDialerCallSessionModificationStateChange() {}

    @Override
    public void onWiFiToLteHandover() {}

    @Override
    public void onHandoverToWifiFailure() {}

    @Override
    public void onInternationalCallOnWifi() {}

    @Override
    public void onEnrichedCallSessionUpdate() {}

    @Override
    public void onSuplServiceMessage(String suplNotificationMessage) {}
  }
}
