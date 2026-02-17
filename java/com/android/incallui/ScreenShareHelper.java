/* Copyright (c) 2019, The Linux Foundation. All rights reserved.
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

 * Changes from Qualcomm Technologies, Inc. are provided under the following license:
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.incallui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.view.Surface;
import com.android.dialer.common.LogUtil;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.codeaurora.ims.ImsScreenShareListenerBase;
import org.codeaurora.ims.ImsScreenShareManager;
import org.codeaurora.ims.QtiImsException;
import org.codeaurora.ims.QtiImsExtConnector;
import org.codeaurora.ims.QtiImsExtManager;
public class ScreenShareHelper {

  public static final int REQUEST_MEDIA_PROJECTION = 1;
  public static final int VIDEO_SCREEN_SHARE = 4;

  //Outgoing Video Source
  public static final int NONE = 0;
  public static final int CAMERA = 1;
  public static final int SCREEN = 2;

  private static final Set<ScreenShareListener> screenShareListeners =
    Collections.newSetFromMap(new ConcurrentHashMap<ScreenShareListener, Boolean>(8, 0.9f, 1));
  private static Intent mPermission = null;

  private static MediaProjection mMediaProjection = null;
  private static QtiImsExtConnector mQtiImsExtConnector = null;
  private static MediaProjectionManager mProjectionManager = null;
  private static ImsScreenShareManager mImsScreenShareManager = null;
  private static QtiImsExtManager mQtiImsExtManager = null;
  private static boolean mIsSessionActive = false;
  private static VirtualDisplay mVirtualDisplay = null;
  private static MediaProjection.Callback mMediaProjectionCallback = null;
  private static ImsScreenShareListenerBase mImsScreenShareListener = null;

  private static int mCalculatedScreenShareWidth = -1;
  private static int mCalculatedScreenShareHeight = -1;

  private static InCallActivity getActivity() {
    final InCallActivity inCallActivity = InCallPresenter.getInstance().getActivity();
    if (inCallActivity == null) {
      LogUtil.w("ScreenShareHelper.getActivity", "inCallActivity is null");
      return null;
    }
    return inCallActivity;
  }

  public static MediaProjectionManager getProjectionManager() {
    if (mProjectionManager == null && getActivity() != null) {
      mProjectionManager = (MediaProjectionManager)
        getActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }
    return mProjectionManager;
  }

  public static void requestScreenSharePermission() {
    LogUtil.w("ScreenShareHelper.requestScreenSharePermission",
        "requesting screen share");
    if (getProjectionManager() != null && getActivity() != null) {
      getActivity().startActivityForResult(
        getProjectionManager().createScreenCaptureIntent(),
        REQUEST_MEDIA_PROJECTION);
    } else {
      LogUtil.w("ScreenShareHelper.requestScreenSharePermission()",
          "screen share not available");
    }
  }

  public static void onPermissionChanged(Intent data) {
    mPermission = data;
    for (ScreenShareListener listener: screenShareListeners) {
      listener.onScreenSharePermissionChanged();
    }
  }

  public static Intent getPermission() {
    return mPermission;
  }

  public static boolean screenShareRequested() {
    return mPermission != null;
  }

  public static void addScreenShareListener(ScreenShareListener listener) {
    if (listener != null) {
      screenShareListeners.add(listener);
    }
  }

  public static void removeScreenShareListener(ScreenShareListener listener) {
    if (listener != null) {
      screenShareListeners.remove(listener);
    }
  }

  public interface ScreenShareListener {
    void onScreenSharePermissionChanged();
  }

  public static void setVirtualDisplay(VirtualDisplay virtualDisplay) {
    mVirtualDisplay = virtualDisplay;
  }

  public static VirtualDisplay getVirtualDisplay(
      int width, int height, int densityDpi, Surface surface) {
    if (mVirtualDisplay == null) {
        // Create new VirtualDisplay
        if (getMediaProjection() == null) return null;
        mVirtualDisplay = getMediaProjection().createVirtualDisplay(
                              "ScreenCapture", width, height, densityDpi,
                              DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                              surface, null, null);
        if (mVirtualDisplay != null) {
            setVirtualDisplay(mVirtualDisplay);
            LogUtil.i("VideoCallPresenter.setupVirtualDisplay",
                "Created new VirtualDisplay");
        }
    }
    return mVirtualDisplay;
  }

  public static void setMediaProjectionCallback(MediaProjection.Callback callback) {
    mMediaProjectionCallback = callback;
  }

  public static MediaProjection.Callback getMediaProjectionCallback() {
    return mMediaProjectionCallback;
  }

  public static void setImsScreenShareListener(ImsScreenShareListenerBase listener) {
    mImsScreenShareListener = listener;
  }

  public static ImsScreenShareListenerBase getImsScreenShareListener() {
    return mImsScreenShareListener;
  }

  public static void setCalculatedScreenShareParams(int sharedWidth, int sharedHeight) {
    mCalculatedScreenShareWidth = sharedWidth;
    mCalculatedScreenShareHeight = sharedHeight;
  }

  public static int getCalculatedScreenShareWidth() {
    return mCalculatedScreenShareWidth;
  }

  public static int getCalculatedScreenShareHeight() {
    return mCalculatedScreenShareHeight;
  }

  public static void setImsScreenShareManager(ImsScreenShareManager imsScreenShareManager) {
    mImsScreenShareManager = imsScreenShareManager;
  }

  public static ImsScreenShareManager getImsScreenShareManager() {
    if (mImsScreenShareManager == null) {
      LogUtil.i("ScreenShareHelper.getImsScreenShareManager", "mImsScreenShareManager is null");
    }
    return mImsScreenShareManager;
  }

  public static void setQtiImsExtManager(QtiImsExtManager qtiImsExtManager) {
    mQtiImsExtManager = qtiImsExtManager;
  }

  public static QtiImsExtManager getQtiImsExtManager() {
    if (mQtiImsExtManager == null) {
      LogUtil.i("ScreenShareHelper.getQtiImsExtManager", "mQtiImsExtManager is null");
    }
    return mQtiImsExtManager;
  }

  public static boolean isSessionActive() {
    return mIsSessionActive;
  }

  public static void setIsSessionActive(boolean isSessionActive) {
    mIsSessionActive = isSessionActive;
  }

  public static void setQtiImsExtConnector(QtiImsExtConnector connector) {
    mQtiImsExtConnector = connector;
  }

  public static QtiImsExtConnector getQtiImsExtConnector() {
    return mQtiImsExtConnector;
  }

  public static MediaProjection getMediaProjection() {
    return mMediaProjection;
  }

  public static void setupMediaProjection(MediaProjection.Callback mediaProjectionCallback,
                                          Handler handler) {
    if (getPermission() == null || getMediaProjection() != null) {
      LogUtil.e("ScreenShareHelper.setupVirtualDisplay",
                "Permission is null or media projection exists");
      return;
    }
    mMediaProjection = getProjectionManager().getMediaProjection(Activity.RESULT_OK,
                                                                 getPermission());

    mMediaProjection.registerCallback(mediaProjectionCallback, handler);
    setMediaProjectionCallback(mediaProjectionCallback);
    LogUtil.i("ScreenShareHelper.setupVirtualDisplay", "Created new MediaProjection");
  }

  public static void cleanupMediaProjection() {
    if (getMediaProjection() != null) {
      if (getMediaProjectionCallback() != null) {
        getMediaProjection().unregisterCallback(getMediaProjectionCallback());
        setMediaProjectionCallback(null);
      }
      getMediaProjection().stop();
      mMediaProjection = null;
    }
  }

  public static void exitScreenShare() {
    try {
      if (getImsScreenShareManager() == null) {
        LogUtil.i("ScreenShareHelper.exitScreenShare",
            "getImsScreenShareManager returned null");
        return;
      }
      LogUtil.i("ScreenShareHelper.exitScreenShare", "exitScreenShare");
      getImsScreenShareManager().stopScreenShare();
    } catch (QtiImsException e) {
      LogUtil.e("ScreenShareHelper.exitScreenShare", "exception " + e);
      clearScreenShareStates();
    }
  }

  public static void startScreenShare(int widthPixels, int heightPixels) {
    try {
      if (getImsScreenShareManager() == null) {
        LogUtil.i("ScreenShareHelper.startScreenShare",
                  "getImsScreenShareManager returned null");
        return;
      }
      LogUtil.i("ScreenShareHelper.startScreenShare", "startScreenShare");
      getImsScreenShareManager().startScreenShare(widthPixels, heightPixels);
    } catch (QtiImsException e) {
      LogUtil.e("ScreenShareHelper.startScreenShare", "exception " + e);
      clearScreenShareStates();
    }
  }

  public static void clearScreenShareStates() {
    setIsSessionActive(false);
    onPermissionChanged(null);
    cleanupMediaProjection();
    // Clean up QtiImsExtConnector
    if (getQtiImsExtConnector() != null) {
      getQtiImsExtConnector().disconnect();
      setQtiImsExtConnector(null);
      setQtiImsExtManager(null);
    }

    setImsScreenShareManager(null);
    setImsScreenShareListener(null);
    setCalculatedScreenShareParams(-1, -1);
    if (mVirtualDisplay != null) {
      mVirtualDisplay.release();
      setVirtualDisplay(null);
    }
  }
}
