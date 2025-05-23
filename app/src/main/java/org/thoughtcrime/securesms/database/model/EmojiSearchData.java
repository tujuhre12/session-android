package org.thoughtcrime.securesms.database.model;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Ties together an emoji with it's associated search tags.
 */
public final class EmojiSearchData {
  @JsonProperty
  private String emoji;

  @JsonProperty
  private List<String> tags;

  @Keep
  public EmojiSearchData() {}

  public @NonNull String getEmoji() {
    return emoji;
  }

  public @NonNull List<String> getTags() {
    return tags;
  }
}
