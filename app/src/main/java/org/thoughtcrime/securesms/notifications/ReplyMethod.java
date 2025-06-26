package org.thoughtcrime.securesms.notifications;

import android.content.Context;

import androidx.annotation.NonNull;

import org.session.libsession.utilities.recipients.RecipientV2;

public enum ReplyMethod {

  GroupMessage,
  SecureMessage;

  public static @NonNull ReplyMethod forRecipient(Context context, RecipientV2 recipient) {
    if (recipient.isGroupOrCommunityRecipient()) {
      return ReplyMethod.GroupMessage;
    }
    return ReplyMethod.SecureMessage;
  }
}
