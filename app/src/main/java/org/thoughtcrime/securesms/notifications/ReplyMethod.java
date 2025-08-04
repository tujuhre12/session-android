package org.thoughtcrime.securesms.notifications;

import android.content.Context;

import androidx.annotation.NonNull;

import org.session.libsession.utilities.recipients.CommonRecipient;


public enum ReplyMethod {

  GroupMessage,
  SecureMessage;

  public static @NonNull ReplyMethod forRecipient(Context context, CommonRecipient recipient) {
    if (recipient.isGroupOrCommunityRecipient()) {
      return ReplyMethod.GroupMessage;
    }
    return ReplyMethod.SecureMessage;
  }
}
