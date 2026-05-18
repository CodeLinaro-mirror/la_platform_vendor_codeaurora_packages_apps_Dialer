/* Copyright (c) 2017-2021, The Linux Foundation. All rights reserved.
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

import android.support.v4.app.FragmentManager;
import android.support.v4.os.UserManagerCompat;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.Call.Details;
import android.telecom.VideoProfile;
import android.view.View;

import com.android.dialer.common.LogUtil;
import com.android.incallui.call.CallList;
import com.android.dialer.util.CallUtil;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.videotech.utils.VideoUtils;
import com.android.incallui.videotech.utils.SessionModificationState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.List;
import java.util.Objects;

import org.codeaurora.ims.QtiCallConstants;
import org.codeaurora.ims.QtiImsExtListenerBaseImpl;
import org.codeaurora.ims.QtiImsExtConnector;
import org.codeaurora.ims.QtiImsExtManager;
import org.codeaurora.ims.QtiImsException;
import org.codeaurora.ims.utils.QtiImsExtUtils;

public class BottomSheetHelper implements PrimaryCallTracker.PrimaryCallChangeListener,
        InCallPresenter.InCallEventListener, InCallPresenter.InCallStateListener {

   private ConcurrentHashMap<String,Boolean> moreOptionsMap;
   private ExtBottomSheetFragment moreOptionsSheet;
   private boolean mIsHideMe = false;
   private Context mContext;
   private Executor mExecutor;
   private DialerCall mCall;
   private String[][] mMoreOptions;
   private PrimaryCallTracker mPrimaryCallTracker;
   private Resources mResources;
   private static BottomSheetHelper mHelper;
   private boolean mHasSentCancelUpgradeRequest = false;
   private AlertDialog callTransferDialog;
   private AlertDialog mCancelModifyCallDialog;
   private QtiImsExtConnector mQtiImsExtConnector;
   private QtiImsExtManager mQtiImsExtManager;
   private static final int BLIND_TRANSFER = 0;
   private static final int ASSURED_TRANSFER = 1;
   private static final int CONSULTATIVE_TRANSFER = 2;
   private QtiImsExtListenerBaseImpl mImsInterfaceListener;
   private AlertDialog modifyCallDialog;
   private static final int INVALID_INDEX = -1;
   // BottomSheet internal call types, start from higher values to avoid conflict.
   private static final int CALL_TYPE_INACTIVE= 1000;
   // Call transition to indicate VoLTE -> VT + RTT
   private static final int CALL_TYPE_VT_RTT = 1001;
   // Call transition to indicate VT -> RTT VoLTE
   private static final int CALL_TYPE_VT_TO_RTT = 1002;
   // Call transition to indicate RTT VoLTE -> VT call with RTT disabled
   private static final int CALL_TYPE_RTT_TO_VT = 1003;
   // Call transition to indicate VT + RTT -> VoLTE
   private static final int CALL_TYPE_DROP_BOTH = 1004;
   private static final int RTT_VT_SUPPORT_UNKNOWN = -1;
   private static final int RTT_VT_SUPPORT_DISABLED = 0;
   private static final int RTT_VT_SUPPORT_ENABLED = 1;
   private int mPendingMoDualTransitionType = CALL_TYPE_INACTIVE;
   private int INVALID_RTT_REQUEST_ID = -1;
   private boolean mPendingMtVtRttUpgrade = false;
   private int mPendingRttRequestId = INVALID_RTT_REQUEST_ID;
   private int isRttVtFeatureSupported = RTT_VT_SUPPORT_UNKNOWN;
   // Tracks the phoneId for which the MT RTT+VT upgrade listener is currently registered.
   // INVALID_PHONE_ID means not yet registered (or registration was reset).
   private int mMtRttVTListenerRegisteredPhoneId = QtiCallConstants.INVALID_PHONE_ID;
   private BottomSheetHelper() {
     LogUtil.d("BottomSheetHelper"," ");
   }

   public static BottomSheetHelper getInstance() {
     if (mHelper == null) {
       mHelper = new BottomSheetHelper();
     }
     return mHelper;
   }

   private void createQtiImsExtConnector(Context context) {
     try {
       mQtiImsExtConnector = new QtiImsExtConnector(context,
           new QtiImsExtConnector.IListener() {
             @Override
             public void onConnectionAvailable(QtiImsExtManager qtiImsExtManager) {
               mQtiImsExtManager = qtiImsExtManager;
               registerMtRttVtModifyListenerSafely();
               updateIsRttVtFeatureSupported();
             }
             @Override
             public void onConnectionUnavailable() {
               mMtRttVTListenerRegisteredPhoneId = QtiCallConstants.INVALID_PHONE_ID;
               isRttVtFeatureSupported = RTT_VT_SUPPORT_UNKNOWN;
               mQtiImsExtManager = null;
             }
           });
     } catch (QtiImsException e) {
       LogUtil.e("BottomSheetHelper.createQtiImsExtConnector",
           "Unable to create QtiImsExtConnector");
     }
   }

   public void setUp(Context context) {
     LogUtil.d("BottomSheetHelper","setUp");
     mContext = context;
     mExecutor = context.getMainExecutor();
     mImsInterfaceListener = new QtiImsExtListenerBaseImpl(mExecutor) {
       /* Handles cancel call modify response */
       @Override
       public void receiveCancelModifyCallResponse(int phoneId, int result) {
            LogUtil.d("BottomSheetHelper.receiveCancelModifyCallResponse", "result: " + result);
            mHasSentCancelUpgradeRequest = false;
            maybeUpdateCancelModifyCallInMap();
       }

       //IMS indicates dual-upgrade, mark pending and send ack to forward the upgrade.
       public void onIncomingRttVtUpgrade() {
            mPendingMtVtRttUpgrade  = true;
       }
     };

     createQtiImsExtConnector(context);
     mQtiImsExtConnector.connect();
     mResources = context.getResources();
     mMoreOptions = getMoreOptionsFromRes(R.array.bottom_sheet_more_options);
     moreOptionsMap = prepareSheetOptions(mMoreOptions);
     mPrimaryCallTracker = new PrimaryCallTracker();
     InCallPresenter.getInstance().addListener(mPrimaryCallTracker);
     InCallPresenter.getInstance().addIncomingCallListener(mPrimaryCallTracker);
     InCallPresenter.getInstance().addInCallEventListener(this);
     InCallPresenter.getInstance().addListener(this);
     mPrimaryCallTracker.addListener(this);
   }

   public void tearDown() {
     LogUtil.d("BottomSheetHelper","tearDown");
     InCallPresenter.getInstance().removeListener(mPrimaryCallTracker);
     InCallPresenter.getInstance().removeIncomingCallListener(mPrimaryCallTracker);
     InCallPresenter.getInstance().removeInCallEventListener(this);
     InCallPresenter.getInstance().removeListener(this);
     mMtRttVTListenerRegisteredPhoneId = QtiCallConstants.INVALID_PHONE_ID;
     isRttVtFeatureSupported = RTT_VT_SUPPORT_UNKNOWN;
     if (mPrimaryCallTracker != null) {
       mPrimaryCallTracker.removeListener(this);
       mPrimaryCallTracker = null;
     }
     mIsHideMe = false;
     if (mQtiImsExtConnector != null) {
       mQtiImsExtConnector.disconnect();
       mQtiImsExtConnector = null;
     }
     mQtiImsExtManager = null;
     mContext = null;
     mResources = null;
     moreOptionsMap = null;
     mMoreOptions = null;
     mHasSentCancelUpgradeRequest = false;
   }

   public void updateMap() {
     if (mPrimaryCallTracker == null) {
       LogUtil.w("BottomSheetHelper.updateMap : ", "PrimaryCallTracker is null");
       return;
     }
     mCall = mPrimaryCallTracker.getPrimaryCall();
     LogUtil.i("BottomSheetHelper.updateMap","mCall = " + mCall);
     moreOptionsMap = prepareSheetOptions(mMoreOptions);
     if (mCall != null && moreOptionsMap != null && mResources != null) {
       maybeUpdateManageConferenceInMap();
       maybeUpdateAddParticipantInMap();
       maybeUpdateOneWayVideoOptionsInMap();
       maybeUpdateModifyCallInMap();
       maybeUpdateHideMeInMap();
       maybeUpdateDeflectInMap();
       maybeUpdateTransferInMap();
       maybeUpdateDialpadOptionInMap();
       maybeUpdateCancelModifyCallInMap();
       maybeUpdatePipModeInMap();
       maybeUpdateTirAcceptOptionsInMap();
       maybeUpdateSwitchToRttInMap();
     }
   }

   // Utility function which converts options from string array to HashMap<String,Boolean>
   private static ConcurrentHashMap<String,Boolean> prepareSheetOptions(String[][] answerOptArray) {
     ConcurrentHashMap<String,Boolean> map = new ConcurrentHashMap<String,Boolean>();
     for (int iter = 0; iter < answerOptArray.length; iter ++) {
       map.put(answerOptArray[iter][0],Boolean.valueOf(answerOptArray[iter][1]));
     }
     return map;
   }

   private boolean isOneWayVideoOptionsVisible() {
     final int primaryCallState = mCall.getState();
     final int requestedVideoState = mCall.getVideoTech().getRequestedVideoState();
     // Disable One dir options for RTT VT upgrades, below check handles these scenarios
     // VoLTE -> RTT + VT and RTT -> RTT + VT
     final boolean areUpgradesRelatedtoRttVt = ((!mCall.isActiveRttCall() &&
         getPendingRttRequestId() != INVALID_RTT_REQUEST_ID) ||
         (mCall.isActiveRttCall() && mCall.hasReceivedVideoUpgradeRequest() &&
         VideoProfile.isBidirectional(requestedVideoState)));
     return (QtiCallUtils.useExt(mContext) && mCall.hasReceivedVideoUpgradeRequest()
       && VideoProfile.isAudioOnly(mCall.getVideoState())
       && VideoProfile.isBidirectional(requestedVideoState)
       && !areUpgradesRelatedtoRttVt)
       || ((DialerCallState.INCOMING == primaryCallState
       || DialerCallState.CALL_WAITING == primaryCallState)
       && (QtiCallUtils.isVideoBidirectional(mCall)
       && QtiImsExtUtils.canAcceptAsOneWayVideo(getPhoneId(), mContext))
       && !mCall.isActiveRttCall());
   }

   private boolean isModifyCallOptionsVisible() {
     final int primaryCallState = mCall.getState();
     return QtiCallUtils.useExt(mContext) && (DialerCallState.ACTIVE == primaryCallState
        || DialerCallState.ONHOLD == primaryCallState)
        && (QtiCallUtils.hasTransmitVideoCapabilities(mCall)
        || QtiCallUtils.hasReceiveVideoCapabilities(mCall))
        && !mCall.hasReceivedVideoUpgradeRequest()
        && !isCancelModifyCallOptionsVisible();
   }

   private boolean isCancelModifyCallOptionsVisible() {
     if (QtiImsExtUtils.isCancelModifyCallSupported(getPhoneId(), mContext)) {
       DialerCall call = mPrimaryCallTracker.getPrimaryCall();
       boolean isDualVtUpgradeRequest = call.getVideoTech().getUpgradeToVideoState() ==
           QtiCallConstants.STATE_DUAL_BIDIRECTIONAL;
       return !mHasSentCancelUpgradeRequest && (call.getVideoTech().getSessionModificationState()
         == SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE) &&
         !isDualVtUpgradeRequest;
     }
     return false;
   }

   private void registerMtRttVtModifyListenerSafely() {
     if (mQtiImsExtManager == null) {
       LogUtil.w("BottomSheetHelper.registerMtRttVtModifyListenerSafely",
           "QtiImsExtManager is null");
       return;
     }
     final int phoneId = getPhoneId();
     if (phoneId == QtiCallConstants.INVALID_PHONE_ID) {
       LogUtil.w("BottomSheetHelper.registerMtRttVtModifyListenerSafely",
           "Invalid phoneId; will retry later when call is present.");
       return;
     }
     try {
       mQtiImsExtManager.setIncomingRttVtUpgradeListener(phoneId, mImsInterfaceListener);
       mMtRttVTListenerRegisteredPhoneId = phoneId;
       LogUtil.i("BottomSheetHelper.registerMtRttVtModifyListenerSafely",
           "Registered MT dual-upgrade listener for phoneId=" + phoneId);
     } catch (QtiImsException e) {
       LogUtil.e("BottomSheetHelper.registerMtRttVtModifyListenerSafely",
           "Failed to register MT dual-upgrade listener: " + e);
     }
   }

   private void updateIsRttVtFeatureSupported() {
     if (mQtiImsExtManager == null) {
       LogUtil.w("BottomSheetHelper.updateIsRttVtFeatureSupported",
           "QtiImsExtManager is null");
       return;
     }
     int phoneId = getPhoneId();
     if (phoneId == QtiCallConstants.INVALID_PHONE_ID) {
       LogUtil.w("BottomSheetHelper.updateIsRttVtFeatureSupported",
           "Invalid phoneId; will retry later when call is present.");
       return;
     }
     try {
       isRttVtFeatureSupported = mQtiImsExtManager.isRttVtFeatureSupported(phoneId)
           ? RTT_VT_SUPPORT_ENABLED : RTT_VT_SUPPORT_DISABLED;
       LogUtil.i("BottomSheetHelper.updateIsRttVtFeatureSupported",
           "isRttVtFeatureSupported=" + isRttVtFeatureSupported + " phoneId=" + phoneId);
     } catch (QtiImsException e) {
       isRttVtFeatureSupported = RTT_VT_SUPPORT_UNKNOWN;
       LogUtil.e("BottomSheetHelper.updateIsRttVtFeatureSupported",
           "Failed to query RTT/VT feature support: " + e +
           " isRttVtFeatureSupported=" + isRttVtFeatureSupported);
     }
   }

   // Accessors to read/clear pending MT VT+RTT state
   public boolean isPendingMtVtRttUpgrade() {
     return mPendingMtVtRttUpgrade;
   }

   public int getPendingRttRequestId() {
     return mPendingRttRequestId;
   }

   public void clearPendingMtVtRttUpgrade() {
     mPendingMtVtRttUpgrade = false;
     mPendingRttRequestId = INVALID_RTT_REQUEST_ID;
   }

   public boolean isRttVtFeatureSupported() {
     return isRttVtFeatureSupported == RTT_VT_SUPPORT_ENABLED;
   }

   private void maybeUpdateManageConferenceInMap() {
     /* show manage conference option only for active video conference calls if the call
        has manage conference capability */
     boolean visible = mCall.isVideoCall() && mCall.getState() == DialerCallState.ACTIVE &&
         mCall.can(android.telecom.Call.Details.CAPABILITY_MANAGE_CONFERENCE);
     moreOptionsMap.put(mResources.getString(R.string.manageConferenceLabel), visible);
   }

   private void maybeUpdatePipModeInMap() {
     /* show Pip mode option only for active video calls if the settings db property
        "disable_pip_mode" is set */
     if (!canDisablePipMode()) {
        return;
     }
     final boolean visible = mCall.isVideoCall() && mCall.getState() == DialerCallState.ACTIVE
         && !mCall.hasReceivedVideoUpgradeRequest();
     moreOptionsMap.put(mResources.getString(R.string.pipModeLabel), visible);
   }

   public boolean isManageConferenceVisible() {
     if (moreOptionsMap == null || mResources == null || mCall == null) {
         LogUtil.w("isManageConferenceVisible","moreOptionsMap or mResources or mCall is null");
         return false;
     }

     return moreOptionsMap.get(mResources.getString(R.string.manageConferenceLabel)).booleanValue()
        && !mCall.hasReceivedVideoUpgradeRequest();
   }

   public void showBottomSheet(FragmentManager manager) {
     LogUtil.d("BottomSheetHelper.showBottomSheet","moreOptionsMap: " + moreOptionsMap);
     moreOptionsSheet = ExtBottomSheetFragment.newInstance(moreOptionsMap);
     moreOptionsSheet.show(manager, null);
   }

   public void dismissBottomSheet() {
     final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
     if (inCallActivity == null || !inCallActivity.isVisible()) {
       LogUtil.w("BottomSheetHelper.dismissBottomSheet",
               "In call activity is either null or not visible");
       return;
     }
     if (moreOptionsSheet != null && moreOptionsSheet.isVisible()) {
       moreOptionsSheet.dismiss();
       moreOptionsSheet = null;
     }
     if (callTransferDialog != null && callTransferDialog.isShowing()) {
       callTransferDialog.dismiss();
       callTransferDialog = null;
     }
     if (modifyCallDialog != null && modifyCallDialog.isShowing()) {
       modifyCallDialog.dismiss();
     }

     if (mCancelModifyCallDialog != null && mCancelModifyCallDialog.isShowing()) {
       mCancelModifyCallDialog.dismiss();
       mCancelModifyCallDialog = null;
     }
   }

   public void optionSelected(@Nullable String text) {
     //callback for bottomsheet clicks
     LogUtil.d("BottomSheetHelper.optionSelected","text : " + text);
     if (text.equals(mResources.getString(R.string.add_participant_option_msg))) {
       if (QtiImsExtUtils.isCarrierConfigEnabled(getPhoneId(), mContext,
           "add_multi_participants_enabled")) {
         startAddMultiParticipantActivity();
       } else {
         startAddParticipantActivity();
       }
     } else if (text.equals(mResources.getString(R.string.manageConferenceLabel))) {
       manageConferenceCall();
     } else if (text.equals(mResources.getString(R.string.qti_ims_hideMeText_unselected)) ||
         text.equals(mResources.getString(R.string.qti_ims_hideMeText_selected))) {
       hideMeClicked(text.equals(mResources.getString(R.string.qti_ims_hideMeText_unselected)));
     } else if (text.equals(mResources.getString(R.string.qti_description_target_deflect))) {
       deflectCall();
     } else if (text.equals(mResources.getString(R.string.qti_description_transfer))) {
       transferCall();
     } else if (text.equals(mResources.getString(R.string.dialpad_label))) {
       showDialpad();
     } else if (text.equals(mResources.getString(R.string.video_tx_label))) {
       acceptIncomingCallOrUpgradeRequest(VideoProfile.STATE_TX_ENABLED);
     } else if (text.equals(mResources.getString(R.string.video_rx_label))) {
       acceptIncomingCallOrUpgradeRequest(VideoProfile.STATE_RX_ENABLED);
     } else if (text.equals(mResources.getString(R.string.modify_call_label))) {
       displayModifyCallOptions();
     } else if (text.equals(mResources.getString(R.string.cancel_modify_call_label))) {
       displayCancelModifyCallOptions();
     } else if (text.equals(mResources.getString(R.string.pipModeLabel))) {
       VideoCallPresenter.showPipModeMenu();
     } else if (text.equals(mResources.getString(R.string.accept_call_with_tir_restricted_label))) {
        acceptIncomingCallWithTir(QtiImsExtUtils.QTI_IMS_TIR_PRESENTATION_RESTRICTED);
     } else if (text.equals(mResources.getString(
            R.string.accept_call_with_tir_unrestricted_label))) {
        acceptIncomingCallWithTir(QtiImsExtUtils.QTI_IMS_TIR_PRESENTATION_UNRESTRICTED);
     } else if (text.equals(mResources.getString(R.string.switch_to_rtt_label))) {
        InCallActivity activity = InCallPresenter.getInstance().getActivity();
        if (activity != null) {
            activity.toggleVideoRtt(true /* showRtt */);
        } else {
            LogUtil.w("BottomSheetHelper.optionSelected", "InCallActivity is null");
        }
     }
     moreOptionsSheet = null;
   }

   public void sheetDismissed() {
     LogUtil.d("BottomSheetHelper.sheetDismissed"," ");
     moreOptionsSheet = null;
   }

   private String[][] getMoreOptionsFromRes(final int resId) {
     TypedArray typedArray = mResources.obtainTypedArray(resId);
     String[][] array = new String[typedArray.length()][];
     for  (int iter = 0;iter < typedArray.length(); iter++) {
       int id = typedArray.getResourceId(iter, 0);
       if (id > 0) {
         array[iter] = mResources.getStringArray(id);
       }
     }
     typedArray.recycle();
     return array;
   }

   public boolean shallShowMoreButton(Activity activity) {
     if (mPrimaryCallTracker != null) {
       DialerCall call = mPrimaryCallTracker.getPrimaryCall();
       if (call != null && activity != null) {
         int primaryCallState = call.getState();
         return !(activity.isInMultiWindowMode()
           || call.isEmergencyCall()
           || ((DialerCallState.isDialing(primaryCallState) ||
           DialerCallState.CONNECTING == primaryCallState ||
           DialerCallState.INCOMING == primaryCallState ||
           DialerCallState.CALL_WAITING == primaryCallState) &&
           !call.isVideoCall())
           || DialerCallState.DISCONNECTING == primaryCallState
           || call.hasSentVideoUpgradeRequest()
           || !(getPhoneIdExtra(call) != QtiCallConstants.INVALID_PHONE_ID))
           || isCancelModifyCallOptionsVisible()
           || canDisplayAcceptWithTirOptionsButtons()
           || canDisplayDeflectOptionsButtons()
           || QtiCallUtils.hasVideoCrbtVoLteCall(mContext, call)
           || QtiCallUtils.isVideoCrs(call);
       }
     }
     LogUtil.w("BottomSheetHelper shallShowMoreButton","returns false");
     return false;
   }

   public void updateMoreButtonVisibility(boolean isVisible, View moreOptionsMenuButton) {
     if (moreOptionsMenuButton == null) {
       return;
     }
     if (isVisible) {
       moreOptionsMenuButton.setVisibility(View.VISIBLE);
     } else {
       dismissBottomSheet();
       moreOptionsMenuButton.setVisibility(View.GONE);
     }
   }

   private boolean isHideMeOptionVisible() {
     if (!QtiImsExtUtils.shallShowStaticImageUi(getPhoneId(), mContext) ||
         !VideoUtils.hasCameraPermissionAndShownPrivacyToast(mContext)) {
       return false;
     }
     return mCall != null && mCall.isVideoCall() && mCall.getState() == DialerCallState.ACTIVE
         && !mCall.hasReceivedVideoUpgradeRequest();
   }

   private void maybeUpdateHideMeInMap() {
     LogUtil.v("BottomSheetHelper.maybeUpdateHideMeInMap", " mIsHideMe = " + mIsHideMe);
     String hideMeText = mIsHideMe ? mResources.getString(R.string.qti_ims_hideMeText_selected) :
         mResources.getString(R.string.qti_ims_hideMeText_unselected);
     moreOptionsMap.put(hideMeText, isHideMeOptionVisible());
   }

   /**
    * Handles click on hide me button
    * @param isHideMe True if user selected hide me option else false
    */
   private void hideMeClicked(boolean isHideMe) {
     LogUtil.d("BottomSheetHelper.hideMeClicked", " isHideMe = " + isHideMe);
     mIsHideMe = isHideMe;
     if (isHideMe) {
       // Replace "Hide Me" string with "Show Me"
       moreOptionsMap.remove(mResources.getString(R.string.qti_ims_hideMeText_unselected));
       moreOptionsMap.put(mResources.getString(R.string.qti_ims_hideMeText_selected), isHideMe);
     } else {
       // Replace "Show Me" string with "Hide Me"
       moreOptionsMap.remove(mResources.getString(R.string.qti_ims_hideMeText_selected));
       moreOptionsMap.put(mResources.getString(R.string.qti_ims_hideMeText_unselected), !isHideMe);
     }

     /* Click on hideme shall change the static image state i.e. decision
        is made in VideoCallPresenter whether to replace preview video with
        static image or whether to resume preview video streaming */
     InCallPresenter.getInstance().notifyHideMeUiModeChanged();
   }

   // Returns TRUE if UE is in hide me mode else returns FALSE
   public boolean isInHideMeMode(DialerCall call) {
     LogUtil.v("BottomSheetHelper.isInHideMeMode", "mIsHideMe: " + mIsHideMe);
     return mIsHideMe &&
         QtiImsExtUtils.shallShowStaticImageUi(getPhoneIdExtra(call), mContext);
   }

   private int getPhoneIdExtra(DialerCall call) {
     if (call == null) {
       return QtiCallConstants.INVALID_PHONE_ID;
     }
     final Bundle extras = call.getExtras();
     return ((extras == null) ? QtiCallConstants.INVALID_PHONE_ID :
         extras.getInt(QtiImsExtUtils.QTI_IMS_PHONE_ID_EXTRA_KEY,
         QtiCallConstants.INVALID_PHONE_ID));
   }

    /**
    * This API should be called only when there is a call.
    * Caller should handle if INVALID_PHONE_ID is returned.
    */
   public int getPhoneId() {
     if (mPrimaryCallTracker == null) {
       LogUtil.w("BottomSheetHelper.getPhoneId", "mPrimaryCallTracker is null.");
       return QtiCallConstants.INVALID_PHONE_ID;
     }

     final DialerCall call = mPrimaryCallTracker.getPrimaryCall();
     if (call == null) {
       LogUtil.w("BottomSheetHelper.getPhoneId", "primaryCall is null.");
       return QtiCallConstants.INVALID_PHONE_ID;
     }

     final int phoneId = getPhoneIdExtra(call);
     LogUtil.d("BottomSheetHelper.getPhoneId", "phoneId : " + phoneId);
     return phoneId;
   }

    @Override
    public void onHideMeUiModeChanged() {
      //No-op
    }

    @Override
    public void onOutgoingVideoSourceChanged(int videoSource) {
      if (mCall == null) {
          return;
      }
      if (videoSource == ScreenShareHelper.SCREEN && !mCall.isVideoCall()) {
        changeToVideoClicked(mCall, VideoProfile.STATE_TX_ENABLED);
      }
    }

    @Override
    public void onSessionModificationStateChange(DialerCall call) {
      //No-op
    }

    @Override
    public void onSipDtmfChanged(int bitMask) {
      //No-op
    }

    @Override
    public void onPrimaryCallChanged(DialerCall call) {
      LogUtil.d("BottomSheetHelper.onPrimaryCallChanged", "");
      mMtRttVTListenerRegisteredPhoneId = QtiCallConstants.INVALID_PHONE_ID;
      registerMtRttVtModifyListenerSafely();
      dismissBottomSheet();
      updateMap();
    }

    @Override
    public void onStateChange(InCallPresenter.InCallState oldState,
        InCallPresenter.InCallState newState, CallList callList) {
      if (newState == InCallPresenter.InCallState.INCALL) {
        if (mMtRttVTListenerRegisteredPhoneId == QtiCallConstants.INVALID_PHONE_ID) {
          LogUtil.d("BottomSheetHelper.onStateChange",
              "State is INCALL and listener not yet registered, retrying.");
          registerMtRttVtModifyListenerSafely();
        }
        if (isRttVtFeatureSupported == RTT_VT_SUPPORT_UNKNOWN) {
          LogUtil.d("BottomSheetHelper.onStateChange",
              "State is INCALL and RTT/VT feature support is not yet resolved, retrying.");
          updateIsRttVtFeatureSupported();
        }
      }
    }

   private void manageConferenceCall() {
     final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
     if (inCallActivity == null) {
       LogUtil.w("BottomSheetHelper.manageConferenceCall", "inCallActivity is null");
       return;
     }

     inCallActivity.showConferenceFragment(true);
   }

   private boolean canDisplayDeflectOptionsButtons() {
     final boolean showDeflectCall =
         mCall.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_DEFLECT) &&
         !mCall.isVideoCall() && !mCall.hasReceivedVideoUpgradeRequest();
     return showDeflectCall;
   }

   private void maybeUpdateDeflectInMap() {
     moreOptionsMap.put(mResources.getString(R.string.qti_description_target_deflect),
         canDisplayDeflectOptionsButtons());
   }

   /**
    * Deflect the incoming call.
    */
   private void deflectCall() {
     LogUtil.enterBlock("BottomSheetHelper.deflectCall");
     if(mCall == null ) {
       LogUtil.w("BottomSheetHelper.deflectCall", "mCall is null");
       return;
     }
     String deflectCallNumber = QtiImsExtUtils.getCallDeflectNumber(
          mContext.getContentResolver());
     /* If not set properly, inform via Log */
     if (deflectCallNumber == null) {
       LogUtil.w("BottomSheetHelper.deflectCall",
            "Number not set. Provide the number via IMS settings and retry.");
       return;
     }
     Uri deflectCallNumberUri = CallUtil.getCallUri(deflectCallNumber);
     if (deflectCallNumberUri == null) {
       LogUtil.w("BottomSheetHelper.deflectCall", "Deflect number Uri is null.");
       return;
     }

     LogUtil.d("BottomSheetHelper.deflectCall", "mCall:" + mCall +
          "deflectCallNumberUri: " + Log.pii(deflectCallNumberUri));
     mCall.deflectCall(deflectCallNumberUri);
   }

   private void maybeUpdateTransferInMap() {
     final boolean showTransferOptions =
         (mCall.can(android.telecom.Call.Details.CAPABILITY_TRANSFER) ||
         mCall.can(android.telecom.Call.Details.CAPABILITY_TRANSFER_CONSULTATIVE)) &&
         !mCall.hasReceivedVideoUpgradeRequest();
     LogUtil.i("BottomSheetHelper.maybeUpdateTransferInMap",
         "value of showTransferOptions in BottomSheetHelper = " + showTransferOptions);
     moreOptionsMap.put(mResources.getString(R.string.qti_description_transfer),
         showTransferOptions);
   }

   private void transferCall() {
     LogUtil.enterBlock("BottomSheetHelper.transferCall");
     if(mCall == null ) {
       LogUtil.w("BottomSheetHelper.transferCall", "mCall is null");
       return;
     }
     displayCallTransferOptions();
   }

   /**
    * The function is called when Call Transfer button gets pressed. The function creates and
    * displays call transfer options.
    */
   public void displayCallTransferOptions() {
     final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
     if (inCallActivity == null) {
       LogUtil.e("BottomSheetHelper.displayCallTransferOptions", "inCallActivity is NULL");
       return;
     }
     final ArrayList<CharSequence> items = getCallTransferOptions();
     AlertDialog.Builder builder = new AlertDialog.Builder(inCallActivity)
          .setTitle(R.string.qti_description_transfer);

     DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int item) {
              LogUtil.d("BottomSheetHelper.onCallTransferItemClicked", "" + items.get(item));
              onCallTransferItemClicked(item);
              dialog.dismiss();
         }
     };
     builder.setSingleChoiceItems(items.toArray(new CharSequence[0]), INVALID_INDEX, listener);
     callTransferDialog = builder.create();
     callTransferDialog.show();
   }

   private ArrayList<CharSequence> getCallTransferOptions() {
     final ArrayList<CharSequence> items = new ArrayList<CharSequence>();
     if (mCall.can(android.telecom.Call.Details.CAPABILITY_TRANSFER_CONSULTATIVE)
         && isConsultativeTransferOnSameSub()) {
       items.add(mResources.getText(R.string.qti_ims_onscreenBlindTransfer));
       items.add(mResources.getText(R.string.qti_ims_onscreenAssuredTransfer));
       items.add(mResources.getText(R.string.qti_ims_onscreenConsultativeTransfer));
     } else if (mCall.can(android.telecom.Call.Details.CAPABILITY_TRANSFER)) {
       items.add(mResources.getText(R.string.qti_ims_onscreenBlindTransfer));
       items.add(mResources.getText(R.string.qti_ims_onscreenAssuredTransfer));
     }
     return items;
   }

   /**
    * Returns true if active and secondary banner call are on the same sub, false otherwise.
    */
   private static boolean isConsultativeTransferOnSameSub() {
     DialerCall secondaryCall = CallList.getInstance().getBackgroundCall();
     DialerCall foregroundCall = CallList.getInstance().getActiveCall();
     return secondaryCall != null && foregroundCall != null &&
         Objects.equals(secondaryCall.getAccountHandle(), foregroundCall.getAccountHandle());
   }

   private void onCallTransferItemClicked(int item) {
     switch(item) {
       case BLIND_TRANSFER:
         transferCall(false);
         break;
       case ASSURED_TRANSFER:
         transferCall(true);
         break;
       case CONSULTATIVE_TRANSFER:
         transferCallConsultative();
         break;
       default:
         break;
     }
   }

   //Called for blind and assured transfer
   private void transferCall(boolean isConfirmationRequired) {
     LogUtil.enterBlock("BottomSheetHelper.transferCall");
     if(mCall == null ) {
       LogUtil.w("BottomSheetHelper.transferCall", "mCall is null");
       return;
     }
     String transferCallNumber = QtiImsExtUtils.getCallDeflectNumber(
         mContext.getContentResolver());
     /* If not set properly, inform via Log */
     if (transferCallNumber == null) {
       LogUtil.w("BottomSheetHelper.transferCall","transfer number error, number is null");
       return;
     }
     Uri transferCallNumberUri = CallUtil.getCallUri(transferCallNumber);
     if (transferCallNumberUri == null) {
       LogUtil.w("BottomSheetHelper.transferCall", "transfer number Uri is null.");
       return;
     }
     LogUtil.d("BottomSheetHelper.transferCall", "mCall:" + mCall +
         "transferCallNumberUri: " + Log.pii(transferCallNumberUri));
     mCall.transferCall(transferCallNumberUri, isConfirmationRequired);
   }

   //Called for consultative transfer
   private void transferCallConsultative() {
     LogUtil.enterBlock("BottomSheetHelper.transferCallConsultative");
     if (mCall == null) {
       LogUtil.w("BottomSheetHelper.transferCallConsultative", "mCall is null");
       return;
     }
     //For Consultative transfer number is not needed
     DialerCall backgroundCall = CallList.getInstance().getBackgroundCall();
     if (backgroundCall == null ||
         !Objects.equals(backgroundCall.getAccountHandle(), mCall.getAccountHandle())) {
       LogUtil.w("BottomSheetHelper.transferCallConsultative", "backgroundCall is null" +
           " or background call is on a different sub.");
       return;
     }
     mCall.transferCall(backgroundCall);
   }

   private void showDialpad() {
     final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
     if (inCallActivity == null) {
       LogUtil.w("BottomSheetHelper.showDialpad", "inCallActivity is null");
       return;
     }

     inCallActivity.showDialpadFragment(true, true);
   }

   private void maybeUpdateDialpadOptionInMap() {
     // Enable dialpad option in bottomsheet for video calls, video CRS, video CRBT or UVS.
     // When video call is held, UI displays onscreen dialpad button
     // similar to volte calls.
     final int primaryCallState = mCall.getNonConferenceState();
     final boolean enable = (mCall.isVideoCall()
             && primaryCallState != DialerCallState.INCOMING
             && primaryCallState != DialerCallState.CALL_WAITING
             && primaryCallState != DialerCallState.ONHOLD)
         || QtiCallUtils.isVideoCrs(mCall)
         || QtiCallUtils.isVisualizedVoiceCall(mCall)
         || QtiCallUtils.hasVideoCrbtVoLteCall(mContext, mCall);
     moreOptionsMap.put(mResources.getString(R.string.dialpad_label), enable);
   }

   private boolean isAddParticipantSupported() {
     boolean showAddParticipant = mCall != null
         && mCall.can(android.telecom.Call.Details.CAPABILITY_ADD_PARTICIPANT)
         && UserManagerCompat.isUserUnlocked(mContext)
         && !mCall.hasReceivedVideoUpgradeRequest();
     if (QtiImsExtUtils.isCarrierConfigEnabled(getPhoneId(), mContext,
         "add_participant_only_in_conference")) {
       showAddParticipant = showAddParticipant && (mCall != null) && (mCall.isConferenceCall());
     }
     return showAddParticipant;
   }

   private void startAddMultiParticipantActivity() {
     Intent intent =
         QtiCallUtils.getAddParticipantsIntent(mContext, null, getPhoneId());
     List<String> childCallIdList = (mCall != null) ? mCall.getChildCallIds() : null;
     if (childCallIdList != null) {
       StringBuffer sb = new StringBuffer();
       for (String tmp: childCallIdList) {
         String number = CallList.getInstance().getCallById(tmp).getNumber();
         if (number.contains(";")) {
           String[] temp = number.split(";");
           number = temp[0];
         }
         sb.append(number).append(";");
       }
       intent.putExtra("current_participant_list", sb.toString());
     } else {
       LogUtil.e("BottomSheetHelper.startAddMultiParticipantActivity",
           "sendAddMultiParticipantsIntent, childCallIdList null.");
     }
     final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
     if (inCallActivity == null || !inCallActivity.isVisible()) {
         LogUtil.w("BottomSheetHelper.startAddMultiParticipantActivity",
             "In call activity is either null or not visible");
         return;
     }
     try {
       inCallActivity.startActivityForResult(intent,
               QtiCallUtils.REQUEST_ADD_PARTICIPANT);
     } catch (ActivityNotFoundException e) {
       LogUtil.e("BottomSheetHelper.startAddMultiParticipantActivity",
           "Activity not found. Exception = " + e);
     }
   }

   private void maybeUpdateAddParticipantInMap() {
     moreOptionsMap.put(mContext.getResources().getString(R.string.add_participant_option_msg),
         isAddParticipantSupported());
   }

   private void startAddParticipantActivity() {
     final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
     if (inCallActivity == null || !inCallActivity.isVisible()) {
         LogUtil.w("BottomSheetHelper.startAddParticipantActivity",
               "In call activity is either null or not visible");
         return;
     }
     try {
       inCallActivity.startActivityForResult(QtiCallUtils.getAddParticipantsIntent(
           mContext, null, getPhoneId()),
           QtiCallUtils.REQUEST_ADD_PARTICIPANT);
     } catch (ActivityNotFoundException e) {
       LogUtil.e("BottomSheetHelper.startAddParticipantActivity",
           "Activity not found. Exception = " + e);
     }
   }

   private void maybeUpdateOneWayVideoOptionsInMap() {
     final boolean showOneWayVideo = isOneWayVideoOptionsVisible();
     moreOptionsMap.put(mResources.getString(R.string.video_rx_label), showOneWayVideo);
     moreOptionsMap.put(mResources.getString(R.string.video_tx_label), showOneWayVideo);
   }

   private void maybeUpdateModifyCallInMap() {
     moreOptionsMap.put(mContext.getResources().getString(R.string.modify_call_label),
        isModifyCallOptionsVisible());
   }

   private void maybeUpdateCancelModifyCallInMap() {
     moreOptionsMap.put(mContext.getResources().getString(R.string.cancel_modify_call_label),
        isCancelModifyCallOptionsVisible());
   }

   private void acceptIncomingCallOrUpgradeRequest(int videoState) {
     if (mCall == null) {
       LogUtil.e("BottomSheetHelper.acceptIncomingCallOrUpgradeRequest", "Call is null. Return");
       return;
     }

     if (mCall.answeringDisconnectsOtherCall()) {
       AnswerUtils.disconnectCallsAndAnswer(videoState);
       return;
     }

     if (mCall.hasReceivedVideoUpgradeRequest()) {
       mCall.getVideoTech().acceptVideoRequest(videoState);
     } else {
       mCall.answer(videoState);
     }
   }

    /**
     * The function is called when Modify Call button gets pressed. The function creates and
     * displays modify call options.
     */
    public void displayModifyCallOptions() {
      final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
      if (inCallActivity == null) {
        LogUtil.e("BottomSheetHelper.displayModifyCallOptions", "inCallActivity is NULL");
        return;
      }

      if (mCall == null) {
        LogUtil.e("BottomSheetHelper.displayModifyCallOptions",
            "Can't display modify call options. Call is null");
        return;
      }

      boolean isVideoEnabled = CallUtil.isVideoEnabled(mContext);
      final ArrayList<CharSequence> items = new ArrayList<CharSequence>();
      final ArrayList<Integer> itemToCallType = new ArrayList<Integer>();
      boolean isCallRttVt = QtiCallUtils.isVideoBidirectional(mCall) && mCall.isActiveRttCall();
      // Prepare the string array and mapping.
      if (QtiCallUtils.hasVoiceCapabilities(mCall) && mCall.isVideoCall()
          && !QtiCallUtils.isDualVideo(mCall) && !isCallRttVt
          || QtiCallUtils.isVisualizedVoiceCall(mCall)) {
        items.add(mResources.getText(R.string.modify_call_option_voice));
        itemToCallType.add(VideoProfile.STATE_AUDIO_ONLY);
      }

      if (!QtiCallUtils.isDualVideo(mCall) && isVideoEnabled
          && QtiCallUtils.hasReceiveVideoCapabilities(mCall)
          && !QtiCallUtils.isVideoRxOnly(mCall)
          && !QtiCallUtils.isVisualizedVoiceCall(mCall) && !isCallRttVt) {
        items.add(mResources.getText(R.string.modify_call_option_vt_rx));
        itemToCallType.add(VideoProfile.STATE_RX_ENABLED);
      }

      if (!QtiCallUtils.isDualVideo(mCall) && isVideoEnabled
          && QtiCallUtils.hasTransmitVideoCapabilities(mCall)
          && (!QtiCallUtils.isVideoTxOnly(mCall)
          || ScreenShareHelper.screenShareRequested())
          && !QtiCallUtils.isVisualizedVoiceCall(mCall) && !isCallRttVt) {
        items.add(mResources.getText(R.string.modify_call_option_vt_tx));
        itemToCallType.add(VideoProfile.STATE_TX_ENABLED);
      }

      if (isVideoEnabled && QtiCallUtils.hasReceiveVideoCapabilities(mCall)
          && QtiCallUtils.hasTransmitVideoCapabilities(mCall)
          && (!QtiCallUtils.isVideoBidirectional(mCall)
          || ScreenShareHelper.screenShareRequested()
          || QtiCallUtils.isDualVideo(mCall))) {
        items.add(mResources.getText(R.string.modify_call_option_vt));
        itemToCallType.add(VideoProfile.STATE_BIDIRECTIONAL);
      }

      if (!QtiCallUtils.isDualVideo(mCall) && isVideoEnabled &&
          canDisplayScreenShareButton() &&
          mCall.getState() == DialerCallState.ACTIVE &&
          QtiCallUtils.hasTransmitVideoCapabilities(mCall)
          && !ScreenShareHelper.screenShareRequested()
          && !QtiCallUtils.isVideoRxOnly(mCall) && !isCallRttVt) {
        items.add(mResources.getText(R.string.modify_call_option_screen_share));
        itemToCallType.add(ScreenShareHelper.VIDEO_SCREEN_SHARE);
      }

      if (isVideoEnabled && QtiCallUtils.isVideoBidirectional(mCall) &&
          !QtiCallUtils.isDualVideo(mCall) && QtiCallUtils.isDualVideoSupported(mCall) &&
          (!mCall.isRemotelyHeld() && mCall.getNonConferenceState() != DialerCallState.ONHOLD) &&
          !isCallRttVt) {
        if (mCall.getDualVtCapability() == QtiCallConstants.DUAL_VIDEO_TX_RX_ENABLED) {
            items.add(mResources.getText(R.string.modify_call_option_dual_vt));
            itemToCallType.add(QtiCallConstants.STATE_DUAL_BIDIRECTIONAL);
        }
      }

      if (!mCall.isVideoCall()
          && isRttVtFeatureSupported()
          && mCall.canUpgradeToRttCall()
          && canSupportBidirectionalVt(mCall)) {
        LogUtil.i("BottomSheetHelper.displayModifyCallOptions", "enable VT+RTT (VoLTE->VT+RTT)");
        items.add(mResources.getText(R.string.modify_call_option_vt_rtt));
        itemToCallType.add(CALL_TYPE_VT_RTT);
      }

      if (isRttVtFeatureSupported()
          && QtiCallUtils.isVideoBidirectional(mCall)
          && !QtiCallUtils.isDualVideo(mCall)
          && mCall.canUpgradeToRttCall()) {
        LogUtil.i("BottomSheetHelper.displayModifyCallOptions", "enable RTT-only (VT->RTT)");
        items.add(mResources.getText(R.string.modify_call_option_rtt_only));
        itemToCallType.add(CALL_TYPE_VT_TO_RTT);
      }

      AlertDialog.Builder builder = new AlertDialog.Builder(inCallActivity);
      builder.setTitle(R.string.modify_call_option_title);

      DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int item) {
            final int setCallType = itemToCallType.get(item);
            if (setCallType == ScreenShareHelper.VIDEO_SCREEN_SHARE) {
              ScreenShareHelper.requestScreenSharePermission();
              dialog.dismiss();
              return;
            }

            if (ScreenShareHelper.screenShareRequested()) {
              if (mCall.getVideoState() == setCallType) {
                InCallPresenter.getInstance().notifyOutgoingVideoSourceChanged(
                    ScreenShareHelper.CAMERA);
                dialog.dismiss();
                return;
              }
              if (setCallType != VideoProfile.STATE_AUDIO_ONLY
                  && setCallType != VideoProfile.STATE_RX_ENABLED) {
                InCallPresenter.getInstance().notifyOutgoingVideoSourceChanged(
                    ScreenShareHelper.CAMERA);
              } else if (setCallType == VideoProfile.STATE_RX_ENABLED ||
                         setCallType == VideoProfile.STATE_AUDIO_ONLY) {
                InCallPresenter.getInstance().notifyOutgoingVideoSourceChanged(
                    ScreenShareHelper.NONE);
              }
            }
            Log.i(this, "Videocall: ModifyCall: upgrade/downgrade to " + setCallType);
            if (setCallType == CALL_TYPE_VT_RTT || setCallType == CALL_TYPE_VT_TO_RTT) {
              requestMoDualTransitionInternal(setCallType);
              dialog.dismiss();
              return;
            }

            Log.v(this, "Videocall: ModifyCall: upgrade/downgrade to "
                + QtiCallUtils.callTypeToString(setCallType));
            changeToVideoClicked(mCall, setCallType);
            dialog.dismiss();
          }
      };
      builder.setSingleChoiceItems(items.toArray(new CharSequence[0]), INVALID_INDEX, listener);
      modifyCallDialog = builder.create();
      modifyCallDialog.show();
    }

    public void displayCancelModifyCallOptions() {
      final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
      if (inCallActivity == null) {
        LogUtil.e("BottomSheetHelper.displayCancelModifyCallOptions", "inCallActivity is NULL");
        return;
      }
      AlertDialog.Builder alertDialog = new AlertDialog.Builder(inCallActivity);
      alertDialog.setTitle(R.string.cancel_modify_call_title);
      alertDialog.setPositiveButton(R.string.cancel_upgrade, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            cancelUpgradeClicked(mCall);
          }
      } );
      alertDialog.setNegativeButton(R.string.not_cancel, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Log.d(this, "not cancel voice call upgrade to video");
          }
      } );
      mCancelModifyCallDialog = alertDialog.create();
      mCancelModifyCallDialog.show();
    }

    /**
     * Sends a session modify request to the telephony framework
     */
    private void changeToVideoClicked(DialerCall call, int videoState) {
      call.getVideoTech().upgradeToVideo(videoState);
    }

    public PrimaryCallTracker getPrimaryCallTracker() {
      return mPrimaryCallTracker;
    }

    /**
      * Cancel the upgrade request.
      */
    private void cancelUpgradeClicked(DialerCall call) {
      LogUtil.enterBlock("BottomSheetHelper.cancelUpgradeClicked");
      if(call == null ) {
        LogUtil.w("BottomSheetHelper.cancelUpgradeClicked", "call is null");
        return;
      }

      if (mQtiImsExtManager == null) {
        QtiCallUtils.displayToast(mContext, "An error occurred, Please try again later.");
        return;
      }

      try {
        LogUtil.d("BottomSheetHelper.cancelUpgradeClicked",
            "Sending cancel upgrade request with Phone id " + getPhoneId());
        mQtiImsExtManager.sendCancelModifyCall(getPhoneId(), mImsInterfaceListener);
        mHasSentCancelUpgradeRequest = true;
        maybeUpdateCancelModifyCallInMap();
      } catch (QtiImsException e) {
        LogUtil.e("BottomSheetHelper.cancelUpgradeClicked", "sendCancelModifyCall exception " + e);
      }
    }

    /**
     * Handles a change to the fullscreen mode of the app.
     *
     * @param isFullscreenMode {@code true} if the app is now fullscreen, {@code false} otherwise.
     */
    @Override
    public void onFullscreenModeChanged(boolean isFullscreenMode) {
      if (isFullscreenMode) {
        dismissBottomSheet();
      }
    }

    public boolean canDisablePipMode() {
      return (Settings.Global.getInt(
         mContext.getContentResolver(), "disable_pip_mode", 0) != 0);
    }

    private boolean canDisplayScreenShareButton() {
      return Settings.Global.getInt(mContext.getContentResolver(),
          "enable_screen_share", 0) == 1;
    }

    private boolean canDisplayAcceptWithTirOptionsButtons() {
      boolean showAcceptWithTirOptions = false;
      if (mCall == null) {
        LogUtil.d("BottomSheetHelper.canDisplayAcceptWithTirOptionsButtons",
                "mCall is null");
        return false;
      }
      final int primaryCallState = mCall.getState();
      if (primaryCallState == DialerCallState.INCOMING ||
          primaryCallState == DialerCallState.CALL_WAITING) {
            Bundle extras = mCall.getExtras();
            showAcceptWithTirOptions = (extras == null) ? false :
                extras.getBoolean(QtiImsExtUtils.EXTRA_TIR_OVERWRITE_ALLOWED, false);
      }
      return showAcceptWithTirOptions;
    }

    private void maybeUpdateTirAcceptOptionsInMap() {
        boolean visible = canDisplayAcceptWithTirOptionsButtons();
        moreOptionsMap.put(mResources.getString(
                R.string.accept_call_with_tir_restricted_label), visible);
        moreOptionsMap.put(mResources.getString(
                R.string.accept_call_with_tir_unrestricted_label), visible);
    }

    private void acceptIncomingCallWithTir(int presentation) {
        LogUtil.enterBlock("BottomSheetHelper.acceptIncomingCallWithTir");
        if (mCall == null) {
            LogUtil.d("BottomSheetHelper.acceptIncomingCallWithTir", "mCall is null");
            return;
        }
        try {
            Bundle extra = new Bundle();
            extra.putInt(QtiImsExtUtils.EXTRA_ANSWER_OPTION_TIR_CONFIG,
                presentation);
            mQtiImsExtManager.setAnswerExtras(getPhoneId(), extra);
            acceptIncomingCallOrUpgradeRequest(mCall.getVideoState());
        } catch (QtiImsException e) {
            LogUtil.e("BottomSheetHelper.acceptIncomingCallWithTir",
                "setAnswerExtras exception " + e);
        }
    }

    public QtiImsExtManager getQtiImsExtManager() {
        return mQtiImsExtManager;
    }

    public void setRttRequestId(int rttReqId) {
        LogUtil.i("BottomSheetHelper.setRttRequestId :","rttreqid" + rttReqId);
        mPendingRttRequestId = rttReqId;
    }

    public void requestMoDualTransitionRttToVt() {
        requestMoDualTransitionInternal(CALL_TYPE_RTT_TO_VT);
    }

    public void requestMoDualTransitionDropBoth() {
        requestMoDualTransitionInternal(CALL_TYPE_DROP_BOTH);
    }

    private void requestMoDualTransitionInternal(int action) {
        if (mQtiImsExtManager == null) {
            LogUtil.w("BottomSheetHelper.requestMoDualTransitionInternal",
                    "QtiImsExtManager is null");
        }
        final int phoneId = getPhoneId();
        if (phoneId == org.codeaurora.ims.QtiCallConstants.INVALID_PHONE_ID) {
             LogUtil.w("BottomSheetHelper.requestMoDualTransitionInternal",
                     "Invalid phoneId");
        }
        mPendingMoDualTransitionType = action;
        try {
            LogUtil.d("BottomSheetHelper.requestMoDualTransitionInternal",
                    "requestMoDualTransitionInternal, phoneId="
                    + phoneId + " action=" + action);
            boolean ret = mQtiImsExtManager.setPendingOutgoingRttVtModifyFlag(phoneId);
            if(ret) {
                onPendingMoRttVtModifyCompleted();
            } else {
                mPendingMoDualTransitionType = CALL_TYPE_INACTIVE;
                LogUtil.d("BottomSheetHelper.requestMoDualTransitionInternal",
                        "setPendingOutgoingRttVtModifyFlag failure, phoneId="
                        + phoneId + " action=" + action);
            }
        } catch (QtiImsException e) {
            LogUtil.e("BottomSheetHelper.requestMoDualTransitionInternal",
                    "requestMoDualTransitionInternal exception " + e);
            mPendingMoDualTransitionType = CALL_TYPE_INACTIVE;
        }
   }

    public void onPendingMoRttVtModifyCompleted() {
        LogUtil.i("BottomSheetHelper.onPendingMoRttVtModifyCompleted",
                "onPendingMoRttVtModifyCompleted=" + mPendingMoDualTransitionType);
        if (mCall == null) {
            LogUtil.e("BottomSheetHelper.onPendingMoRttVtModifyCompleted",
                    "No primary call");
            mPendingMoDualTransitionType = CALL_TYPE_INACTIVE;
            return;
        }
        try {
            if (mPendingMoDualTransitionType == CALL_TYPE_VT_RTT) {
                changeToVideoClicked(mCall, VideoProfile.STATE_BIDIRECTIONAL);
                mCall.sendRttUpgradeRequest();
            } else if (mPendingMoDualTransitionType == CALL_TYPE_VT_TO_RTT) {
                changeToVideoClicked(mCall, VideoProfile.STATE_AUDIO_ONLY);
                mCall.sendRttUpgradeRequest();
            } else if (mPendingMoDualTransitionType == CALL_TYPE_RTT_TO_VT) {
                // RTT-only -> VT-only
                changeToVideoClicked(mCall, VideoProfile.STATE_BIDIRECTIONAL);
                mCall.sendRttDowngradeRequest();
            } else if (mPendingMoDualTransitionType == CALL_TYPE_DROP_BOTH) {
                // RTT+VT -> VoLTE
                changeToVideoClicked(mCall, VideoProfile.STATE_AUDIO_ONLY);
                mCall.sendRttDowngradeRequest();
            } else {
                LogUtil.w("BottomSheetHelper.onPendingMoRttVtModifyCompleted",
                        "Unknown pending MO dual action: " +
                        mPendingMoDualTransitionType);
            }
        } catch (Exception e) {
            LogUtil.e("BottomSheetHelper.onPendingMoRttVtModifyCompleted",
                    "Dual transition execution failed: " + e);
        } finally {
            mPendingMoDualTransitionType = CALL_TYPE_INACTIVE;
        }
    }

    private void maybeUpdateSwitchToRttInMap() {
        boolean visible = false;
        if (mCall != null) {
            final int primaryCallState = mCall.getState();
            try {
                visible = mCall.isVideoCall()
                        && mCall.isActiveRttCall()
                        && primaryCallState == DialerCallState.ACTIVE
                        && !mCall.hasReceivedVideoUpgradeRequest();
            } catch (Exception e) {
                LogUtil.w("BottomSheetHelper.maybeUpdateSwitchToRttInMap",
                        "error: " + e);
            }
        }
        moreOptionsMap.put(mResources.getString(R.string.switch_to_rtt_label), visible);
    }

   private boolean canSupportBidirectionalVt(DialerCall call) {
      if (call == null || mContext == null) {
          return false;
      }
      boolean isVideoEnabled = com.android.dialer.util.CallUtil.isVideoEnabled(mContext);
      return isVideoEnabled
             && QtiCallUtils.hasReceiveVideoCapabilities(call)
             && QtiCallUtils.hasTransmitVideoCapabilities(call)
             && !QtiCallUtils.isVisualizedVoiceCall(call);
   }
}
