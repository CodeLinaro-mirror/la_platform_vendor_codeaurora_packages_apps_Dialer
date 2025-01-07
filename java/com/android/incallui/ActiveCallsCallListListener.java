/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.incallui;

import android.content.Context;
import android.support.annotation.NonNull;
import com.android.dialer.activecalls.ActiveCallInfo;
import com.android.dialer.activecalls.ActiveCallsComponent;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.InCallPresenter.InCallState;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/** Updates {@link com.android.dialer.activecalls.ActiveCalls} */
@SuppressWarnings("Guava")
public class ActiveCallsCallListListener implements InCallPresenter.InCallStateListener {

  private final Context appContext;

  ActiveCallsCallListListener(Context appContext) {
    this.appContext = appContext;
  }

  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    ImmutableList.Builder<ActiveCallInfo> activeCalls = ImmutableList.builder();
    for (DialerCall call : callList.getAllCalls()) {
      if (call.getState() != DialerCallState.DISCONNECTED && call.getAccountHandle() != null) {
        activeCalls.add(
            ActiveCallInfo.builder()
                .setPhoneAccountHandle(Optional.of(call.getAccountHandle()))
                .build());
      }
    }
    ActiveCallsComponent.get(appContext).activeCalls().setActiveCalls(activeCalls.build());
  }

}
