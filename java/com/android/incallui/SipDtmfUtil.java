/**
 Copyright (c) 2021 The Linux Foundation. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
     * Redistributions in binary form must reproduce the above
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

import com.android.incallui.incall.protocol.InCallButtonIds;

public class SipDtmfUtil {

  public static final int SIP_DTMF_TYPE_INVALID = 0;
  public static final int SIP_DTMF_TYPE_LIKE = 1 << 0;
  public static final int SIP_DTMF_TYPE_SHARE = 1 << 1;
  public static final int SIP_DTMF_TYPE_FAVORITE = 1 << 2;
  public static final int SIP_DTMF_TYPE_COPY = 1 << 3;
  public static final int SIP_DTMF_TYPE_COMMENT = 1 << 4;
  public static final int SIP_DTMF_TYPE_DETAIL = 1 << 5;
  public static final int SIP_DTMF_TYPE_RED_ENVELOPE = 1 << 6;
  public static final int SIP_DTMF_TYPE_LIKED = 1 << 7;

  private static final String IN_SIP_DTMF_LIKE_MSG = "2*001";
  private static final String IN_SIP_DTMF_SHARE_MSG = "2*002";
  private static final String IN_SIP_DTMF_FAVORITE_MSG = "2*003";
  private static final String IN_SIP_DTMF_COPY_MSG = "2*004";
  private static final String IN_SIP_DTMF_COMMENT_MSG = "2*005";
  private static final String IN_SIP_DTMF_DETAIL_MSG = "2*006";
  private static final String IN_SIP_DTMF_RED_ENVELOPE = "2*007";
  private static final String IN_SIP_DTMF_LIKED_MSG = "2*008";

  private static final String OUT_SIP_DTMF_LIKE_MSG = "1*001#";
  private static final String OUT_SIP_DTMF_SHARE_MSG = "1*002#";
  private static final String OUT_SIP_DTMF_FAVORITE_MSG = "1*003#";
  private static final String OUT_SIP_DTMF_COPY_MSG = "1*004#";
  private static final String OUT_SIP_DTMF_COMMENT_MSG = "1*005#";
  private static final String OUT_SIP_DTMF_DETAIL_MSG = "1*006#";
  private static final String OUT_SIP_DTMF_RED_ENVELOPE = "1*007#";

  /**
   * it takes DTMF text delimited by # and return bitmask which represents
   * buttons in following order Like|Share|...|Red Envelope, 1 in the bitmask
   * means that the button should be visible (presented to the user).
   */
  public static int  toButtonBitmask(String fromDtmf) {
    int sipDtmfBitMask = SIP_DTMF_TYPE_INVALID;
    if (fromDtmf == null) {
        return sipDtmfBitMask;
    }
    String[] splitSipDtmf = fromDtmf.split("#");
    for (String config : splitSipDtmf) {
        sipDtmfBitMask |= convertConfigToBitmask(config);
    }
    return sipDtmfBitMask;
  }

  /**
   * it converts button Id to DTMF text string and send text to modem/network
   */
  public static String toDtmfString(int fromButtonId) {
    switch (fromButtonId){
        case InCallButtonIds.BUTTON_LIKE:
            return OUT_SIP_DTMF_LIKE_MSG;
        case InCallButtonIds.BUTTON_SHARE:
            return OUT_SIP_DTMF_SHARE_MSG;
        case InCallButtonIds.BUTTON_FAVORITE:
            return OUT_SIP_DTMF_FAVORITE_MSG;
        case InCallButtonIds.BUTTON_COPY:
            return OUT_SIP_DTMF_COPY_MSG;
        case InCallButtonIds.BUTTON_COMMENT:
            return OUT_SIP_DTMF_COMMENT_MSG;
        case InCallButtonIds.BUTTON_DETAIL:
            return OUT_SIP_DTMF_DETAIL_MSG;
        case InCallButtonIds.BUTTON_RED_ENVELOPE:
            return OUT_SIP_DTMF_RED_ENVELOPE;
        default:
            return "";
    }
  }

  private static int convertConfigToBitmask(String config) {
    switch (config){
        case IN_SIP_DTMF_LIKE_MSG:
            return SIP_DTMF_TYPE_LIKE;
        case IN_SIP_DTMF_SHARE_MSG:
            return SIP_DTMF_TYPE_SHARE;
        case IN_SIP_DTMF_FAVORITE_MSG:
            return SIP_DTMF_TYPE_FAVORITE;
        case IN_SIP_DTMF_COPY_MSG:
            return SIP_DTMF_TYPE_COPY;
        case IN_SIP_DTMF_COMMENT_MSG:
            return SIP_DTMF_TYPE_COMMENT;
        case IN_SIP_DTMF_DETAIL_MSG:
            return SIP_DTMF_TYPE_DETAIL;
        case IN_SIP_DTMF_RED_ENVELOPE:
            return SIP_DTMF_TYPE_RED_ENVELOPE;
        case IN_SIP_DTMF_LIKED_MSG:
            return SIP_DTMF_TYPE_LIKED;
        default:
            return SIP_DTMF_TYPE_INVALID;
    }
  }
}
