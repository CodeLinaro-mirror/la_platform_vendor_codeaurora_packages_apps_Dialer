/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.incallui.multisim;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.preferredsim.suggestion.SimSuggestionComponent;
import com.android.dialer.util.PermissionsUtil;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.InCallPresenter;
import com.android.incallui.InCallPresenter.InCallDisconnectedListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.incalluilock.InCallUiLock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Hangs up the current call and redial the call using the {@code otherAccount} instead. the in call
 * ui will be prevented from closing until the process has finished.
 */
public class SwapSimWorker implements Worker<Void, Void>,
    InCallStateListener, InCallDisconnectedListener {

  // Timeout waiting for the call to hangup or redial.
  private static final int DEFAULT_TIMEOUT_MILLIS = 5_000;

  private final Context context;
  private final DialerCall call;
  private final CallList callList;
  private final InCallUiLock inCallUiLock;

  private final CountDownLatch disconnectLatch = new CountDownLatch(1);
  private final CountDownLatch dialingLatch = new CountDownLatch(1);

  private final PhoneAccountHandle otherAccount;
  private final String number;

  private final int timeoutMillis;

  private CountDownLatch latchForTest;

  @MainThread
  public SwapSimWorker(
      Context context,
      DialerCall call,
      CallList callList,
      PhoneAccountHandle otherAccount,
      InCallUiLock lock) {
    this(context, call, callList, otherAccount, lock, DEFAULT_TIMEOUT_MILLIS);
  }

  @VisibleForTesting
  SwapSimWorker(
      Context context,
      DialerCall call,
      CallList callList,
      PhoneAccountHandle otherAccount,
      InCallUiLock lock,
      int timeoutMillis) {
    Assert.isMainThread();
    this.context = context;
    this.call = call;
    this.callList = callList;
    this.otherAccount = otherAccount;
    inCallUiLock = lock;
    this.timeoutMillis = timeoutMillis;
    number = call.getNumber();
    InCallPresenter.getInstance().addListener(this);
    InCallPresenter.getInstance().addInCallDisconnectedListener(this);
    call.disconnect();
  }

  @WorkerThread
  @Nullable
  @Override
  @SuppressWarnings("MissingPermission")
  public Void doInBackground(Void unused) {
    try {
      SimSuggestionComponent.get(context)
          .getSuggestionProvider()
          .reportIncorrectSuggestion(context, number, otherAccount);

      if (!PermissionsUtil.hasPhonePermissions(context)) {
        LogUtil.e("SwapSimWorker.doInBackground", "missing phone permission");
        return null;
      }
      if (!disconnectLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
        LogUtil.e("SwapSimWorker.doInBackground", "timeout waiting for call to disconnect");
        return null;
      }
      LogUtil.i("SwapSimWorker.doInBackground", "call disconnected, redialing");
      TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
      Bundle extras = new Bundle();
      extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, otherAccount);
      telecomManager.placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null), extras);
      if (latchForTest != null) {
        latchForTest.countDown();
      }
      if (!dialingLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
        LogUtil.e("SwapSimWorker.doInBackground", "timeout waiting for call to dial");
      }
      return null;
    } catch (InterruptedException e) {
      LogUtil.e("SwapSimWorker.doInBackground", "interrupted", e);
      Thread.currentThread().interrupt();
      return null;
    } finally {
      ThreadUtil.postOnUiThread(
          () -> {
            InCallPresenter.getInstance().removeListener(this);
            InCallPresenter.getInstance().removeInCallDisconnectedListener(this);
            inCallUiLock.release();
          });
    }
  }

  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    if (callList.getOutgoingCall() != null
        && !DialerCall.areSame(call, callList.getOutgoingCall())) {
      dialingLatch.countDown();
    }
  }

  @VisibleForTesting
  void setLatchForTest(CountDownLatch latch) {
    latchForTest = latch;
  }

  @MainThread
  @Override
  public void onCallDisconnected(DialerCall call) {
    if (DialerCall.areSame(this.call, call)) {
      disconnectLatch.countDown();
    }
  }
}
