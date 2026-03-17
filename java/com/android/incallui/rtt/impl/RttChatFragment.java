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
 * limitations under the License
 *
 * Changes from Qualcomm Technologies, Inc. are provided under the following license:
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.incallui.rtt.impl;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.telecom.CallAudioState;
import android.telecom.Call.Details;
import android.telephony.satellite.SatelliteManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import androidx.core.view.ViewCompat;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.UiUtil;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.rtt.RttTranscript;
import com.android.dialer.rtt.RttTranscriptMessage;
import com.android.dialer.satellite.SatelliteInfo;
import com.android.dialer.util.CallUtil;
import com.android.dialer.util.DrawableConverter;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment.AudioRouteSelectorPresenter;
import com.android.incallui.BottomSheetHelper;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.hold.OnHoldFragment;
import com.android.incallui.incall.protocol.ContactPhotoType;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.incall.protocol.InCallButtonUi;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.incall.protocol.InCallButtonUiDelegateFactory;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.InCallScreenDelegateFactory;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.incall.protocol.SecondaryInfo;
import com.android.incallui.QtiCallUtils;
import com.android.incallui.rtt.impl.RttChatAdapter.MessageListener;
import com.android.incallui.rtt.protocol.Constants;
import com.android.incallui.rtt.protocol.RttCallScreen;
import com.android.incallui.rtt.protocol.RttCallScreenDelegate;
import com.android.incallui.rtt.protocol.RttCallScreenDelegateFactory;
import org.codeaurora.ims.utils.QtiImsExtUtils;
import java.util.List;

/** RTT chat fragment to show chat bubbles. */
public class RttChatFragment extends Fragment
    implements OnEditorActionListener,
        TextWatcher,
        MessageListener,
        RttCallScreen,
        InCallScreen,
        InCallButtonUi,
        AudioRouteSelectorPresenter {

  private static final String ARG_CALL_ID = "call_id";

  private RecyclerView recyclerView;
  private RttChatAdapter adapter;
  private EditText editText;
  private ImageButton submitButton;
  private boolean isClearingInput;

  private InCallScreenDelegate inCallScreenDelegate;
  private RttCallScreenDelegate rttCallScreenDelegate;
  private InCallButtonUiDelegate inCallButtonUiDelegate;
  private View endCallButton;
  private TextView nameTextView;
  private Chronometer chronometer;
  private boolean isTimerStarted;
  private RttOverflowMenu overflowMenu;
  private SecondaryInfo savedSecondaryInfo;
  private TextView statusBanner;
  private TextView vtIndicatorBanner;
  private PrimaryInfo primaryInfo = PrimaryInfo.empty();
  private PrimaryCallState primaryCallState = PrimaryCallState.empty();
  private boolean isUserScrolling;
  private boolean shouldAutoScrolling;
  private AudioSelectMenu audioSelectMenu;
  private Button launchSatelliteAppButton;
  private TextView satellitePromptText;

  /**
   * Create a new instance of RttChatFragment.
   *
   * @param callId call id of the RTT call.
   * @return new RttChatFragment
   */
  public static RttChatFragment newInstance(String callId) {
    Bundle bundle = new Bundle();
    bundle.putString(ARG_CALL_ID, callId);
    RttChatFragment instance = new RttChatFragment();
    instance.setArguments(bundle);
    return instance;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    LogUtil.i("RttChatFragment.onCreate", null);
    inCallButtonUiDelegate =
        FragmentUtils.getParent(this, InCallButtonUiDelegateFactory.class)
            .newInCallButtonUiDelegate();
    if (savedInstanceState != null) {
      inCallButtonUiDelegate.onRestoreInstanceState(savedInstanceState);
    }
    inCallScreenDelegate =
        FragmentUtils.getParentUnsafe(this, InCallScreenDelegateFactory.class)
            .newInCallScreenDelegate();
    // Prevent updating local message until UI is ready.
    isClearingInput = true;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle bundle) {
    super.onViewCreated(view, bundle);
    LogUtil.i("RttChatFragment.onViewCreated", null);
    ViewCompat.requestApplyInsets(view);

    rttCallScreenDelegate =
        FragmentUtils.getParentUnsafe(this, RttCallScreenDelegateFactory.class)
            .newRttCallScreenDelegate(this);

    rttCallScreenDelegate.initRttCallScreenDelegate(this);

    inCallScreenDelegate.onInCallScreenDelegateInit(this);
    inCallScreenDelegate.onInCallScreenReady();
    inCallButtonUiDelegate.onInCallButtonUiReady(this);
  }

  @Override
  public List<RttTranscriptMessage> getRttTranscriptMessageList() {
    return adapter.getRttTranscriptMessageList();
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.frag_rtt_chat, container, false);
    view.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    editText = view.findViewById(R.id.rtt_chat_input);
    editText.setOnEditorActionListener(this);
    editText.addTextChangedListener(this);

    editText.setOnKeyListener(
        (v, keyCode, event) -> {
          // This is only triggered when input method doesn't handle delete key, which usually means
          // the current input box is empty.
          // On non-English keyboard delete key could be passed here so we still need to check if
          // the input box is empty.
          if (keyCode == KeyEvent.KEYCODE_DEL
              && event.getAction() == KeyEvent.ACTION_DOWN
              && TextUtils.isEmpty(editText.getText())) {
            String lastMessage = adapter.retrieveLastLocalMessage();
            if (lastMessage != null) {
              resumeInput(lastMessage);
              rttCallScreenDelegate.onLocalMessage("\b");
              return true;
            }
            return false;
          }
          return false;
        });
    recyclerView = view.findViewById(R.id.rtt_recycler_view);
    LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
    layoutManager.setStackFromEnd(true);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setHasFixedSize(false);
    adapter = new RttChatAdapter(getContext(), this);
    recyclerView.setAdapter(adapter);
    recyclerView.addOnScrollListener(
        new OnScrollListener() {
          @Override
          public void onScrollStateChanged(RecyclerView recyclerView, int i) {
            if (i == RecyclerView.SCROLL_STATE_DRAGGING) {
              isUserScrolling = true;
            } else if (i == RecyclerView.SCROLL_STATE_IDLE) {
              isUserScrolling = false;
              // Auto scrolling for new messages should be resumed if it's scrolled to bottom.
              shouldAutoScrolling = !recyclerView.canScrollVertically(1);
            }
          }

          @Override
          public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (dy < 0 && isUserScrolling) {
              UiUtil.hideKeyboardFrom(getContext(), editText);
            }
          }
        });

    submitButton = view.findViewById(R.id.rtt_chat_submit_button);
    submitButton.setOnClickListener(
        v -> {
          Logger.get(getContext()).logImpression(DialerImpression.Type.RTT_SEND_BUTTON_CLICKED);
          adapter.submitLocalMessage();
          resumeInput("");
          rttCallScreenDelegate.onLocalMessage(Constants.BUBBLE_BREAKER);
          // Auto scrolling for new messages should be resumed since user has submit current
          // message.
          shouldAutoScrolling = true;
        });
    submitButton.setEnabled(false);
    endCallButton = view.findViewById(R.id.rtt_end_call_button);
    endCallButton.setOnClickListener(
        v -> {
          LogUtil.i("RttChatFragment.onClick", "end call button clicked");
          inCallButtonUiDelegate.onEndCallClicked();
        });

    overflowMenu = new RttOverflowMenu(getContext(), inCallButtonUiDelegate, inCallScreenDelegate);
    view.findViewById(R.id.rtt_overflow_button)
        .setOnClickListener(
            v -> {
              // Hide keyboard when opening overflow menu. This is alternative solution since hiding
              // keyboard after the menu is open or dialpad is shown doesn't work.
              UiUtil.hideKeyboardFrom(getContext(), editText);
              updateTransferButtonVisibility();
              updateVideoToggleMenuLabel();
              overflowMenu.showAtLocation(v, Gravity.TOP | Gravity.RIGHT, 0, 0);
            });

    nameTextView = view.findViewById(R.id.rtt_name_or_number);
    chronometer = view.findViewById(R.id.rtt_timer);
    statusBanner = view.findViewById(R.id.rtt_status_banner);

    launchSatelliteAppButton = (Button) view.findViewById(R.id.launch_satellite_app);
    launchSatelliteAppButton.setOnClickListener(
            v -> {
              LogUtil.i("RttChatFragment.onClick", "Launch Satellite button clicked");
              inCallButtonUiDelegate.onLaunchSatelliteAppButtonClicked();
            });
    satellitePromptText = (TextView) view.findViewById(R.id.satellite_text);
    return view;
  }

  @Override
  public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
    if (actionId == EditorInfo.IME_ACTION_SEND) {
      if (!TextUtils.isEmpty(editText.getText())) {
        Logger.get(getContext())
            .logImpression(DialerImpression.Type.RTT_KEYBOARD_SEND_BUTTON_CLICKED);
        submitButton.performClick();
      }
      return true;
    }
    return false;
  }

  @Override
  public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

  @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {
    if (isClearingInput) {
      return;
    }
    String messageToAppend = adapter.computeChangeOfLocalMessage(s.toString());
    if (!TextUtils.isEmpty(messageToAppend)) {
      adapter.addLocalMessage(messageToAppend);
      rttCallScreenDelegate.onLocalMessage(messageToAppend);
    }
  }

  @Override
  public void onRemoteMessage(String message) {
    adapter.addRemoteMessage(message);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    LogUtil.enterBlock("RttChatFragment.onDestroyView");
    inCallButtonUiDelegate.onInCallButtonUiUnready();
    inCallScreenDelegate.onInCallScreenUnready();
  }

  @Override
  public void afterTextChanged(Editable s) {
    if (TextUtils.isEmpty(s)) {
      submitButton.setEnabled(false);
    } else {
      submitButton.setEnabled(true);
    }
  }

  @Override
  public void onUpdateLocalMessage(int position) {
    if (position < 0) {
      return;
    }
    recyclerView.smoothScrollToPosition(position);
  }

  @Override
  public void onUpdateRemoteMessage(int position) {
    if (position < 0) {
      return;
    }
    if (shouldAutoScrolling) {
      recyclerView.smoothScrollToPosition(position);
    }
  }

  @Override
  public void onRestoreRttChat(RttTranscript rttTranscript) {
    String unfinishedLocalMessage = adapter.onRestoreRttChat(rttTranscript);
    if (unfinishedLocalMessage != null) {
      resumeInput(unfinishedLocalMessage);
    }
  }

  private void resumeInput(String input) {
    isClearingInput = true;
    editText.setText(input);
    editText.setSelection(input.length());
    isClearingInput = false;
  }

  @Override
  public void onStart() {
    LogUtil.enterBlock("RttChatFragment.onStart");
    super.onStart();
    isClearingInput = false;
    onRttScreenStart();
  }

  @Override
  public void onResume() {
    super.onResume();
    LogUtil.i("RttChatFragment.onResume", "updatevtbanner");
    updateVtIndicatorBanner();
    updateTransferButtonVisibility();
    updateVideoToggleMenuLabel();
  }

  private void updateVtIndicatorBanner() {
    if (getView() == null || primaryCallState.state() == DialerCallState.DIALING) {
      return;
    }

    DialerCall call = CallList.getInstance().getCallById(getCallId());
    boolean isRttVt = call != null && call.isVideoCall() && call.isActiveRttCall();

    if (isRttVt) {
      statusBanner.setText(getString(R.string.rtt_vt_banner_text));
      statusBanner.setVisibility(View.VISIBLE);
    } else {
      statusBanner.setVisibility(View.GONE);
    }
  }

  @Override
  public void onStop() {
    LogUtil.enterBlock("RttChatFragment.onStop");
    super.onStop();
    isClearingInput = true;
    if (overflowMenu.isShowing()) {
      overflowMenu.dismiss();
    }
    onRttScreenStop();
  }

  @Override
  public void onRttScreenStart() {
    rttCallScreenDelegate.onRttCallScreenUiReady();
    Activity activity = getActivity();
    Window window = getActivity().getWindow();
    window.setStatusBarColor(activity.getColor(R.color.rtt_status_bar_color));
    window.setNavigationBarColor(activity.getColor(R.color.rtt_navigation_bar_color));
  }

  @Override
  public void onRttScreenStop() {
    Activity activity = getActivity();
    Window window = getActivity().getWindow();
    window.setStatusBarColor(activity.getColor(android.R.color.transparent));
    window.setNavigationBarColor(activity.getColor(android.R.color.transparent));
    rttCallScreenDelegate.onRttCallScreenUiUnready();
  }

  @Override
  public Fragment getRttCallScreenFragment() {
    return this;
  }

  @Override
  public String getCallId() {
    return Assert.isNotNull(getArguments().getString(ARG_CALL_ID));
  }

  @Override
  public void setPrimary(@NonNull PrimaryInfo primaryInfo) {
    LogUtil.i("RttChatFragment.setPrimary", primaryInfo.toString());
    nameTextView.setText(primaryInfo.name());
    updateAvatar(primaryInfo);
    this.primaryInfo = primaryInfo;
  }

  private void updateAvatar(PrimaryInfo primaryInfo) {
    boolean hasPhoto =
        primaryInfo.photo() != null && primaryInfo.photoType() == ContactPhotoType.CONTACT;
    // Contact has a photo, don't render a letter tile.
    if (hasPhoto) {
      int avatarSize = getResources().getDimensionPixelSize(R.dimen.rtt_avatar_size);
      adapter.setAvatarDrawable(
          DrawableConverter.getRoundedDrawable(
              getContext(), primaryInfo.photo(), avatarSize, avatarSize));
    } else {
      LetterTileDrawable letterTile = new LetterTileDrawable(getResources());
      letterTile.setCanonicalDialerLetterTileDetails(
          primaryInfo.name(),
          primaryInfo.contactInfoLookupKey(),
          LetterTileDrawable.SHAPE_CIRCLE,
          LetterTileDrawable.getContactTypeFromPrimitives(
              primaryCallState.isVoiceMailNumber(),
              primaryInfo.isSpam(),
              primaryCallState.isBusinessNumber(),
              primaryInfo.numberPresentation(),
              primaryCallState.isConference()));
      adapter.setAvatarDrawable(letterTile);
    }
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (savedSecondaryInfo != null) {
      setSecondary(savedSecondaryInfo);
    }
  }

  @Override
  public void setSecondary(@NonNull SecondaryInfo secondaryInfo) {
    LogUtil.i("RttChatFragment.setSecondary", secondaryInfo.toString());
    if (!isAdded()) {
      savedSecondaryInfo = secondaryInfo;
      return;
    }
    savedSecondaryInfo = null;
    FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
    Fragment oldBanner = getChildFragmentManager().findFragmentById(R.id.rtt_on_hold_banner);
    if (secondaryInfo.shouldShow()) {
      OnHoldFragment onHoldFragment = OnHoldFragment.newInstance(secondaryInfo);
      onHoldFragment.setPadTopInset(false);
      transaction.replace(R.id.rtt_on_hold_banner, onHoldFragment);
    } else {
      if (oldBanner != null) {
        transaction.remove(oldBanner);
      }
    }
    transaction.setCustomAnimations(R.anim.abc_slide_in_top, R.anim.abc_slide_out_top);
    transaction.commitNowAllowingStateLoss();
    overflowMenu.enableSwitchToSecondaryButton(secondaryInfo.shouldShow());
  }

  @Override
  public void setCallState(@NonNull PrimaryCallState primaryCallState) {
    LogUtil.i("RttChatFragment.setCallState", primaryCallState.toString());
    this.primaryCallState = primaryCallState;
    if (!isTimerStarted && primaryCallState.state() == DialerCallState.ACTIVE) {
      LogUtil.i(
          "RttChatFragment.setCallState", "starting timer with base: %d", chronometer.getBase());
      chronometer.setBase(
          primaryCallState.connectTimeMillis()
              - System.currentTimeMillis()
              + SystemClock.elapsedRealtime());
      chronometer.start();
      isTimerStarted = true;
      editText.setVisibility(View.VISIBLE);
      submitButton.setVisibility(View.VISIBLE);
      editText.setFocusableInTouchMode(true);
      if (editText.requestFocus()) {
        UiUtil.showKeyboardFrom(getContext(), editText);
      }
      adapter.showAdvisory();
    }
    if (primaryCallState.state() == DialerCallState.DIALING) {
      showWaitingForJoinBanner();
    } else {
      hideWaitingForJoinBanner();
    }
    if (primaryCallState.state() == DialerCallState.DISCONNECTED) {
      rttCallScreenDelegate.onSaveRttTranscript();
    }
     updateVtIndicatorBanner();
     updateTransferButtonVisibility();
  }

  // Compute whether Transfer options should be shown in RTT menu and toggle visibility
  private void updateTransferButtonVisibility() {
    if (overflowMenu == null) {
      return;
    }
    DialerCall currentCall = CallList.getInstance().getCallById(getCallId());
    boolean showTransferOptions =
        currentCall != null
            && !currentCall.isEmergencyCall()
            && (currentCall.can(Details.CAPABILITY_TRANSFER)
            || currentCall.can(Details.CAPABILITY_TRANSFER_CONSULTATIVE))
            && !currentCall.hasReceivedVideoUpgradeRequest();
    overflowMenu.enableTransferButton(showTransferOptions);
  }

  private void showWaitingForJoinBanner() {
    statusBanner.setText(getString(R.string.rtt_status_banner_text, primaryInfo.name()));
    statusBanner.setVisibility(View.VISIBLE);
  }

  private void hideWaitingForJoinBanner() {
    statusBanner.setVisibility(View.GONE);
  }

  @Override
  public void setEndCallButtonEnabled(boolean enabled, boolean animate) {}

  @Override
  public void showManageConferenceCallButton(boolean visible) {}

  @Override
  public boolean isManageConferenceVisible() {
    return false;
  }

  @Override
  public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {}

  @Override
  public void showNoteSentToast() {}

  @Override
  public void updateInCallScreenColors() {}

  @Override
  public void onInCallScreenDialpadVisibilityChange(boolean isShowing) {
    overflowMenu.setDialpadButtonChecked(isShowing);
  }

  @Override
  public void onInCallShowDialpad(boolean isShown) {}

  @Override
  public int getAnswerAndDialpadContainerResourceId() {
    return R.id.incall_dialpad_container;
  }

  @Override
  public void showLocationUi(Fragment locationUi) {}

  @Override
  public boolean isShowingLocationUi() {
    return false;
  }

  @Override
  public Fragment getInCallScreenFragment() {
    return this;
  }

  @Override
  public void showButton(int buttonId, boolean show) {
    switch (buttonId) {
      case InCallButtonIds.BUTTON_SWAP:
        overflowMenu.enableSwapCallButton(show);
        return;
      case InCallButtonIds.BUTTON_MERGE:
        overflowMenu.enableMergeCallButton(show);
        return;
      case InCallButtonIds.BUTTON_DOWNGRADE_TO_VOICE:
        overflowMenu.enableDowngradeRttButton(show);
        return;
      case InCallButtonIds.BUTTON_HOLD:
        overflowMenu.enableHoldButton(show);
        return;
      case InCallButtonIds.BUTTON_SHOW_SATELLITE_PROMPT:
        overflowMenu.enableSatelliteButton(show);
        return;
      case InCallButtonIds.BUTTON_ADD_CALL:
        overflowMenu.enableAddCallButton(show);
        return;
    }
  }

  @Override
  public void enableButton(int buttonId, boolean enable) {
    //Switch to secondary button has separate show/enable calls
    //from CallButtonPresenter as compared to other buttons.
    if (buttonId == InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY) {
      overflowMenu.enableSwitchToSecondaryButton(enable);
    }
  }

  @Override
  public void setEnabled(boolean on) {}

  @Override
  public void setHold(boolean on) {
    overflowMenu.setHoldButtonChecked(on);
  }

  @Override
  public void setCameraSwitched(boolean isBackFacingCamera) {}

  @Override
  public void setVideoPaused(boolean isPaused) {}

  @Override
  public void setAudioState(CallAudioState audioState) {
    LogUtil.i("RttChatFragment.setAudioState", "audioState: " + audioState);
    overflowMenu.setMuteButtonChecked(audioState.isMuted());
    overflowMenu.setAudioState(audioState);
    if (audioSelectMenu != null) {
      audioSelectMenu.setAudioState(audioState);
    }
  }

  @Override
  public void updateButtonStates() {}

  @Override
  public void updateInCallButtonUiColors(int color) {}

  @Override
  public Fragment getInCallButtonUiFragment() {
    return this;
  }

  @Override
  public void showAudioRouteSelector() {
    audioSelectMenu =
        new AudioSelectMenu(
            getContext(),
            inCallButtonUiDelegate,
            () -> overflowMenu.showAtLocation(getView(), Gravity.TOP | Gravity.RIGHT, 0, 0));
    audioSelectMenu.showAtLocation(getView(), Gravity.TOP | Gravity.RIGHT, 0, 0);
  }

  @Override
  public void onAudioRouteSelected(int audioRoute, BluetoothDevice device) {
    inCallButtonUiDelegate.setAudioRoute(audioRoute, device);
  }

  @Override
  public void showDowngradeOptions() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
    builder.setTitle(R.string.downgrade_call_menu_title);

    DialerCall currentCall = CallList.getInstance().getCallById(getCallId());
    if (currentCall == null) {
      LogUtil.w("RttChatFragment.showDowngradeOptions", "currentCall is null");
      return;
    }

    boolean featureSupported = BottomSheetHelper.getInstance().isRttVtFeatureSupported();
    boolean isRttActive = currentCall.isActiveRttCall();
    boolean isVideo = currentCall.isVideoCall();
    boolean isRttOnly = isRttActive && !isVideo;
    boolean isRttVt  = isRttActive && isVideo;

    boolean phoneAccountRttDowngradeCapable = currentCall.isPhoneAccountRttDowngradeCapable();
    boolean vtBiDirSupported = isVtBidirectionalSupported(currentCall);

    String voice = getString(R.string.downgrade_to_voice_call);
    String rtt   = getString(R.string.downgrade_to_rtt_call);
    String video = getString(R.string.downgrade_to_video_call);

    java.util.ArrayList<String> itemsList = new java.util.ArrayList<>();

    // Voice only
    boolean showVoice =
        (isRttOnly && phoneAccountRttDowngradeCapable)
        || (isRttVt && phoneAccountRttDowngradeCapable);
    if (showVoice) {
      itemsList.add(voice);
    }

    // RTT only (only for RTT+VT)
    boolean showRttOnly = isRttVt && featureSupported;
    if (showRttOnly) {
      itemsList.add(rtt);
    }

    // VT only
    boolean showVtOnly =
        (isRttOnly && phoneAccountRttDowngradeCapable && featureSupported && vtBiDirSupported)
        || (isRttVt && phoneAccountRttDowngradeCapable);
    if (showVtOnly) {
      itemsList.add(video);
    }

    if (itemsList.isEmpty()) {
      LogUtil.i("RttChatFragment.showDowngradeOptions", "No downgrade options available");
      return;
    }

    String[] items = itemsList.toArray(new String[0]);
    builder.setItems(
        items,
        (dialog, which) -> {
          String selected = items[which];
          if (voice.equals(selected)) {
            inCallButtonUiDelegate.downgradeToVoice();
          } else if (rtt.equals(selected)) {
            inCallButtonUiDelegate.downgradeToRtt();
          } else if (video.equals(selected)) {
            inCallButtonUiDelegate.downgradeToVideo();
          }
        });
    AlertDialog dialog = builder.create();
    dialog.show();
  }

  @Override
  public void onAudioRouteSelectorDismiss() {}

  @Override
  public void enableSatellitePrompt(boolean checked, SatelliteInfo info) {
    LogUtil.d("RTTChatFragment.enableSatellitePrompt", "isChecked : " + checked);
    if (!checked) {
      launchSatelliteAppButton.setEnabled(false);
      launchSatelliteAppButton.setVisibility(View.GONE);
      satellitePromptText.setVisibility(View.GONE);
      return;
    }
    if (info == null) {
      LogUtil.e("enableSatellitePrompt", "unexpected call or satellite info is null");
      return;
    }
    String text = getContext().getString(R.string.satellite_sos_title);
    String buttonText = getContext().getString(R.string.launch_satellite_application);
    if (info.getHandoverType() ==
        SatelliteManager.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911) {
      text = getContext().getString(R.string.satellite_messaging_title);
      buttonText = getContext().getString(R.string.launch_messaging_application);
    }
    text += "\n \n" + getContext().getString(R.string.satellite_generic_message);
    launchSatelliteAppButton.setVisibility(View.VISIBLE);
    launchSatelliteAppButton.setEnabled(true);
    launchSatelliteAppButton.setText(buttonText);
    satellitePromptText.setVisibility(View.VISIBLE);
    satellitePromptText.setText(text);
  }

  private boolean isVtBidirectionalSupported(DialerCall call) {
    if (call == null || getContext() == null) {
      return false;
    }
    boolean isVideoEnabled = CallUtil.isVideoEnabled(getContext());
    return isVideoEnabled
        && QtiCallUtils.hasReceiveVideoCapabilities(call)
        && QtiCallUtils.hasTransmitVideoCapabilities(call)
        && !QtiCallUtils.isVisualizedVoiceCall(call);
  }

  private void updateVideoToggleMenuLabel() {
    if (overflowMenu == null) {
      return;
    }
    DialerCall call = CallList.getInstance().getCallById(getCallId());
    boolean isRttVt = call != null && call.isVideoCall() && call.isActiveRttCall();
    overflowMenu.setSwitchToVideoLabel(isRttVt);

    boolean featureSupported = BottomSheetHelper.getInstance().isRttVtFeatureSupported();
    boolean vtBiDirSupported = isVtBidirectionalSupported(call);
    // showVideoToggle is a single button which will be used to upgrade to RTT VT
    // as well as switch to video view once call is already RTT VT from RTT fragment
    // hence we should only on featureSupported and if we can upgrade to VT Bidir
    boolean showVideoToggle = featureSupported && vtBiDirSupported;
    overflowMenu.enableVideoToggleButton(showVideoToggle);
  }

}
