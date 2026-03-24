/*
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.incallui.disconnectdialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.database.Cursor;
import android.content.Context;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.telecom.DisconnectCause;
import android.util.Pair;
import com.android.dialer.common.LogUtil;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.PermissionsUtil;
import com.android.incallui.call.DialerCall;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.function.Supplier;
import org.codeaurora.ims.QtiCallConstants;

/** Intelligent calling dialog shown to user after disconnect.
 *  Dialog will be shown if below conditions are met:
 *      1) Intelligent calling dialog is shown if the user setting is enabled
 *      2) If Intelligent calling related call end causes are received then
 *  If above conditions are met, it will attempt to find 3rd party VoIP
 *  applications for the contact
 *
 *  A dialog will be shown to inform user either:
 *      1) Call through 3rd party VoIP application could not be placed
 *      2) Call through 3rd party VoIP application will be placed
 *
 *  Example mimeTypes:
 *      Whatsapp:
 *          (voice) vnd.com.whatsapp.voip.call
 *          (video) vnd.com.whatsapp.video.call
 *      Telegram:
 *          (voice) vnd.org.telegram.messenger.android.call
 *          (video) vnd.org.telegram.messenger.android.call.video
 */
public class IntelligentCallingEnabledDialog implements DisconnectDialog {
  private final static String LOG_TAG = "IntelligentCallingEnabledDialog";
  private final static int INVALID_CONTACT_ID = -1;
  // use to find MimeTypes which support either video or voice calls
  private final static String VIDEO = "video";
  private final static String VOICE = "call";

  private ExecutorService executor;

  @Override
  public boolean shouldShow(DisconnectCause disconnectCause, DialerCall call) {
    // check if intelligent calling feature is enabled
    if (call == null) {
      LogUtil.w(LOG_TAG, "shouldShow DialerCall is null");
      return false;
    }
    if (!call.isIntelligentCallingFeatureEnabled()) {
      LogUtil.d(LOG_TAG, "isIntelligentCallingFeature disabled");
      return false;
    }
    return intelligentCallingErrorCodeReceived(call.getExtras());
  }

  // does not rely on DisconnectCause
  @Override
  public boolean shouldShow(DisconnectCause disconnectCause) {
    return false;
  }

  @Override
  public Pair<Dialog, CharSequence> createDialog(@NonNull Context context, DialerCall call) {
    // call shouldn't be null, but if null then pass null dialog and dialog won't be shown
    if (call == null) {
      return new Pair<>(null,
          context.getString(R.string.intelligent_calling_generic_error_message));
    }

    maybeCleanupExecutor();
    if (executor == null) {
      executor = Executors.newSingleThreadExecutor();
    }
    String phoneNumber = call.getNumber();
    String callType = call.isVideoCall() ? VIDEO : VOICE;
    String title = context.getString(R.string.intelligent_calling_title);
    String queryMessage = context.getString(R.string.intelligent_calling_querying_database);

    AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(queryMessage)
            .setPositiveButton(android.R.string.ok, null)
            .create();

    CompletableFuture.supplyAsync(() -> {
        return getContactInfoFromPhoneNumberAsync(context, call.getNumber(), callType);
    }, executor).thenAcceptAsync(resultString -> {
      maybeCleanupExecutor();
      if (resultString != null && !resultString.isEmpty()) {
        try {
          if (dialog.isShowing()) {
            LogUtil.d(LOG_TAG, "Dialog is still visible, updating message");
            dialog.setMessage(resultString);
          }
        } catch (IllegalStateException e) {
          LogUtil.w(LOG_TAG, "Aborting UI update due to exception");
        } finally {
          maybeCleanupExecutor();
        }
      }
    }, context.getMainExecutor()).exceptionally(throwable -> {
      LogUtil.e(LOG_TAG, "Exception throwing during async operation",
          throwable);
      maybeCleanupExecutor();
      return null;
    });;
    // show dialog until query has been completed asynchronously
    return new Pair<>(dialog, queryMessage);
  }

  // Helper function to query database on a separate thread
  // As this API is called from a separate thread (non-UI thread)
  // do not update/access UI elements in this function
  private static String getContactInfoFromPhoneNumberAsync(final Context context,
      final String phoneNumber, String callType) {
    String message = context.getString(R.string.intelligent_calling_generic_error_message);
    final long contactId = !PermissionsUtil.hasPermission(
        context, android.Manifest.permission.READ_CONTACTS) ?
        INVALID_CONTACT_ID : getContactIdFromPhoneNumber(context, phoneNumber);
    if (contactId == INVALID_CONTACT_ID) {
      LogUtil.w(LOG_TAG, "invalid contact id");
      return message;
    }
    String type = getFirstContactTypeFromNumber(context, contactId, callType);
    if (type == null || type.isEmpty()) {
      LogUtil.w(LOG_TAG, "type is null/empty");
      return context.getString(R.string.intelligent_calling_invalid_type);
    }
    Uri uri = getDataUriFromType(context, contactId, type);
    if (uri == null) {
      LogUtil.w(LOG_TAG, "Uri is null");
      return context.getString(R.string.intelligent_calling_invalid_uri);
    }
    message = context.getString(R.string.intelligent_calling_place_call);
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setDataAndType(uri, type);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    launchThirdPartyApplication(context, intent);

    return message;
  }

  // helper function to retrieve contact id based on phone number
  private static long getContactIdFromPhoneNumber(Context context, String phoneNumber) {
    long contactId = INVALID_CONTACT_ID;
    if (context == null || phoneNumber == null) {
      LogUtil.e(LOG_TAG, "context or phoneNumber is null");
      return contactId;
    }
    Uri lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(phoneNumber));
    LogUtil.d(LOG_TAG, "getContactIdFromPhoneNumber: " + phoneNumber);
    String[] lookupProjection = { ContactsContract.PhoneLookup._ID };
    try (Cursor cursor = context.getContentResolver().query(lookupUri, lookupProjection,
        null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int idIndex = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID);
        contactId = cursor.getLong(idIndex);
      }
    } catch (Exception e) {
      LogUtil.e(LOG_TAG, "getContactIdFromPhoneNumber- error querying contacts", e);
      return INVALID_CONTACT_ID;
    }
    return contactId;
  }

  // helper function to get MimeType from the contact's id
  // returns the first option which supports voice or video calls based on the call type
  // Checks against string values (ex: vnd.com.whatsapp.voip.call)
  private static String getFirstContactTypeFromNumber(Context context,
      long contactId, String callType) {
    if (contactId == INVALID_CONTACT_ID) {
      LogUtil.w(LOG_TAG,
          "getFirstContactTypeFromNumber - invalid contact id");
      return null;
    }
    String selection = ContactsContract.Data.CONTACT_ID + " = ?";
    String[] selectionArgs = { String.valueOf(contactId) };
    String[] dataProjection = { ContactsContract.Data.MIMETYPE };
    try (Cursor cursor = context.getContentResolver().query(
        ContactsContract.Data.CONTENT_URI,
        dataProjection,
        selection,
        selectionArgs,
        null)) {
      if (cursor != null) {
        int mimeTypeIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE);
        while (cursor.moveToNext()) {
          String mimeType = cursor.getString(mimeTypeIndex);
          if (mimeType.contains("call") && mimeType.contains(callType)) {
            LogUtil.d(LOG_TAG, "getFirstContactTypeFromNumber - adding mimeType: " + mimeType);
            return mimeType;
          }
          // logging this for future to support additional applications
          LogUtil.d(LOG_TAG,
              "getFirstContactTypeFromNumber - mimeType: " + mimeType);
        }
      }
    } catch (Exception e) {
      LogUtil.e(LOG_TAG, "getFirstContactTypeFromNumber - exception querying contacts", e);
      return null;
    }
    return null;
  }

  // Get the data URI for the contact based on the MimeType
  private static Uri getDataUriFromType(Context context, long contactId, String mimeType) {
    if (contactId == INVALID_CONTACT_ID || mimeType == null || mimeType.isEmpty()) {
      LogUtil.w(LOG_TAG,
          "getDataUriFromType - contactId/type is invalid");
      return null;
    }
    Uri dataUri = null;
    String selection = ContactsContract.Data.CONTACT_ID + " = ? AND " +
        ContactsContract.Data.MIMETYPE + " = ?";
    String[] selectionArgs = new String[] { String.valueOf(contactId), mimeType };
    String[] dataProj = { ContactsContract.Data._ID };

    try (Cursor dataCursor = context.getContentResolver().query(
        ContactsContract.Data.CONTENT_URI,
        dataProj,
        selection,
        selectionArgs,
        null)) {
      if (dataCursor != null && dataCursor.moveToFirst()) {
        long dataRowId = dataCursor.getLong(
            dataCursor.getColumnIndexOrThrow(ContactsContract.Data._ID));
        dataUri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataRowId);
        LogUtil.d(LOG_TAG, "getDataUriFromType - contact found: " + dataUri);
      }
    } catch (Exception e) {
      LogUtil.e(LOG_TAG, "getDataUriFromType - error querying contacts", e);
    }
    return dataUri;
  }

  // helper function to launch the 3rd party application
  private static void launchThirdPartyApplication(Context context, Intent intent) {
    DialerUtils.startActivityWithErrorToast(context, intent);
  }

  // helper function to check if intelligent calling codes are received
  private static boolean intelligentCallingErrorCodeReceived(Bundle extras) {
    if (extras == null) {
      return false;
    }
    int failCause = extras.getInt(QtiCallConstants.EXTRAS_KEY_CALL_FAIL_EXTRA_CODE,
        QtiCallConstants.DISCONNECT_CAUSE_UNSPECIFIED);
    LogUtil.d(LOG_TAG, "intelligentCallingErrorCodeReceived: " + failCause);
    return failCause == QtiCallConstants.CODE_RINGING_RINGBACK_TIMEOUT ||
        failCause == QtiCallConstants.CODE_NO_ANSWER_FROM_USER;
  }

  // helper function to shutdown executor and clean up as needed
  private void maybeCleanupExecutor() {
    if (executor != null && !executor.isShutdown()) {
      executor.shutdownNow();
      executor = null;
    }
  }
}
