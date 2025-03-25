package org.thoughtcrime.securesms.reactions.any;

import android.content.Context;

import androidx.annotation.NonNull;

import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import network.loki.messenger.R;

final class ReactWithAnyEmojiRepository {

  private static final String TAG = Log.tag(ReactWithAnyEmojiRepository.class);

  private final RecentEmojiPageModel        recentEmojiPageModel;

  ReactWithAnyEmojiRepository(@NonNull Context context) {
    this.recentEmojiPageModel = new RecentEmojiPageModel(context);
  }

  void addEmojiToMessage(@NonNull String emoji) {
    recentEmojiPageModel.onCodePointSelected(emoji);
  }
}
