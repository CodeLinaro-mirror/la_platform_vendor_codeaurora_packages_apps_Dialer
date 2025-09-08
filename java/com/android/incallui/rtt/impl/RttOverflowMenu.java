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

import android.content.Context;
import android.telecom.CallAudioState;
import android.view.View;
import android.widget.PopupWindow;
import com.android.dialer.common.LogUtil;
import com.android.incallui.BottomSheetHelper;
import com.android.incallui.call.DialerCall;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.rtt.impl.RttCheckableButton.OnCheckedChangeListener;
import com.android.incallui.speakerbuttonlogic.SpeakerButtonInfo;

/** Overflow menu for RTT call. */
public class RttOverflowMenu extends PopupWindow implements OnCheckedChangeListener {

  private final RttCheckableButton muteButton;
  private final RttCheckableButton speakerButton;
  private final RttCheckableButton dialpadButton;
  private final RttCheckableButton addCallButton;
  private final RttCheckableButton swapCallButton;
  private final RttCheckableButton downgradeCallButton;
  private final RttCheckableButton transferButton;
  private final RttCheckableButton mergeCallButton;
  private final RttCheckableButton holdButton;
  private final RttCheckableButton satelliteButton;
  private final RttCheckableButton videoToggleButton;
  private final InCallButtonUiDelegate inCallButtonUiDelegate;
  private final InCallScreenDelegate inCallScreenDelegate;
  private boolean isSwitchToSecondaryButtonEnabled;
  private boolean isSwapCallButtonEnabled;
  private boolean isMergeCallButtonEnabled;
  private boolean isDowngradeRttButtonEnabled;

  RttOverflowMenu(
      Context context,
      InCallButtonUiDelegate inCallButtonUiDelegate,
      InCallScreenDelegate inCallScreenDelegate) {
    super(context, null, 0, R.style.OverflowMenu);
    this.inCallButtonUiDelegate = inCallButtonUiDelegate;
    this.inCallScreenDelegate = inCallScreenDelegate;
    View view = View.inflate(context, R.layout.overflow_menu, null);
    setContentView(view);
    setOnDismissListener(this::dismiss);
    setFocusable(true);
    setWidth(context.getResources().getDimensionPixelSize(R.dimen.rtt_overflow_menu_width));
    muteButton = view.findViewById(R.id.menu_mute);
    muteButton.setOnCheckedChangeListener(this);
    speakerButton = view.findViewById(R.id.menu_speaker);
    speakerButton.setOnCheckedChangeListener(this);
    dialpadButton = view.findViewById(R.id.menu_keypad);
    dialpadButton.setOnCheckedChangeListener(this);
    addCallButton = view.findViewById(R.id.menu_add_call);
    addCallButton.setOnClickListener(v -> this.inCallButtonUiDelegate.addCallClicked());
    holdButton = view.findViewById(R.id.menu_hold_call);
    holdButton.setOnCheckedChangeListener(this);
    swapCallButton = view.findViewById(R.id.menu_swap_call);
    swapCallButton.setOnClickListener(
        v -> {
          if (isSwapCallButtonEnabled) {
            this.inCallButtonUiDelegate.swapClicked();
          }
          if (isSwitchToSecondaryButtonEnabled) {
            this.inCallScreenDelegate.onSecondaryInfoClicked();
          }
        });
    downgradeCallButton = view.findViewById(R.id.menu_downgrade_call);
    downgradeCallButton.setOnClickListener(
        v -> {
          inCallButtonUiDelegate.downgradeCall();
          dismiss();
        });
    mergeCallButton = view.findViewById(R.id.menu_merge_call);
    mergeCallButton.setOnClickListener(
        v -> {
          if (isMergeCallButtonEnabled) {
            this.inCallButtonUiDelegate.mergeClicked();
          }
        });
    satelliteButton = view.findViewById(R.id.menu_satellite);
    satelliteButton.setOnCheckedChangeListener(this);
    videoToggleButton = view.findViewById(R.id.menu_vt);
    videoToggleButton.setOnClickListener(
        v -> {
          inCallButtonUiDelegate.onRttToVideoClicked();
          dismiss();
        });
    transferButton = view.findViewById(R.id.menu_transfer);
      transferButton.setOnClickListener(
          v -> {
            try {
              BottomSheetHelper.getInstance().displayCallTransferOptions();
            } catch (Exception e) {
              LogUtil.e("RttOverflowMenu", "Failed to display transfer options: " + e);
            }
            dismiss();
          });
    }
  @Override
  public void onCheckedChanged(RttCheckableButton button, boolean isChecked) {
    if (button == muteButton) {
      inCallButtonUiDelegate.muteClicked(isChecked, true);
    } else if (button == speakerButton) {
      inCallButtonUiDelegate.toggleSpeakerphone();
    } else if (button == dialpadButton) {
      inCallButtonUiDelegate.showDialpadClicked(isChecked);
    } else if (button == holdButton) {
      inCallButtonUiDelegate.holdClicked(isChecked);
    } else if (button == satelliteButton) {
      setSatelliteButtonChecked(isChecked);
      inCallButtonUiDelegate.satelliteAvailabilityButtonClicked(isChecked);
      dismiss();
    }
  }

  void setSatelliteButtonChecked(boolean isChecked) {
    satelliteButton.setChecked(isChecked);
  }

  void setMuteButtonChecked(boolean isChecked) {
    muteButton.setChecked(isChecked);
  }

  void setHoldButtonChecked(boolean isChecked) {
    holdButton.setChecked(isChecked);
  }

  void setAudioState(CallAudioState audioState) {
    SpeakerButtonInfo info = new SpeakerButtonInfo(audioState);
    if (info.nonBluetoothMode) {
      speakerButton.setChecked(info.isChecked);
      speakerButton.setOnClickListener(null);
      speakerButton.setOnCheckedChangeListener(this);
    } else {
      speakerButton.setText(info.label);
      speakerButton.setCompoundDrawablesWithIntrinsicBounds(info.icon, 0, 0, 0);
      speakerButton.setOnClickListener(
          v -> {
            inCallButtonUiDelegate.showAudioRouteSelector();
            dismiss();
          });
      speakerButton.setOnCheckedChangeListener(null);
    }
  }

  void setSwitchToVideoLabel(boolean isRttVt) {
    if (videoToggleButton == null) {
      return;
    }
    videoToggleButton.setText(
        isRttVt
            ? videoToggleButton.getContext().getString(R.string.switch_to_video)
            : videoToggleButton.getContext().getString(R.string.incall_label_videocall));
  }

  void setDialpadButtonChecked(boolean isChecked) {
    dialpadButton.setChecked(isChecked);
  }

  void enableSwapCallButton(boolean enabled) {
    isSwapCallButtonEnabled = enabled;
    swapCallButton.setVisibility(
        isSwapCallButtonEnabled || isSwitchToSecondaryButtonEnabled ? View.VISIBLE : View.GONE);
  }

  void enableSwitchToSecondaryButton(boolean enabled) {
    isSwitchToSecondaryButtonEnabled = enabled;
    swapCallButton.setVisibility(
        isSwapCallButtonEnabled || isSwitchToSecondaryButtonEnabled ? View.VISIBLE : View.GONE);
  }

  void enableMergeCallButton(boolean enabled) {
    isMergeCallButtonEnabled = enabled;
    mergeCallButton.setVisibility(isMergeCallButtonEnabled ? View.VISIBLE : View.GONE);
  }

  void enableDowngradeRttButton(boolean enabled) {
    isDowngradeRttButtonEnabled = enabled;
    downgradeCallButton.setVisibility(
        isDowngradeRttButtonEnabled ? View.VISIBLE : View.GONE);
  }

  void enableHoldButton(boolean enabled) {
    holdButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
  }

  void enableSatelliteButton(boolean enabled) {
    satelliteButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
  }

  void enableAddCallButton(boolean enabled) {
    addCallButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
  }

  void enableTransferButton(boolean enabled) {
    transferButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
  }

  void enableVideoToggleButton(boolean enabled) {
    if (videoToggleButton != null) {
      videoToggleButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }
  }
}
