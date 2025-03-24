package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.widget.TextViewCompat;
import network.loki.messenger.R;
import org.session.libsession.utilities.Util;
import org.session.libsignal.utilities.guava.Optional;

public class EmojiTextView extends AppCompatTextView {
  private final boolean scaleEmojis;
  private static final char ELLIPSIS = '…';

  private CharSequence previousText;
  private BufferType previousBufferType = BufferType.NORMAL;
  private float originalFontSize;
  private boolean sizeChangeInProgress;
  private int maxLength;
  private CharSequence overflowText;
  private CharSequence previousOverflowText;

  public EmojiTextView(Context context) {
    this(context, null);
  }

  public EmojiTextView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public EmojiTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    scaleEmojis = true;
    maxLength = 1000;
    originalFontSize = getResources().getDimension(R.dimen.medium_font_size);
  }

  /**
   * Checks if a Unicode codepoint is considered an emoji.
   */
  private boolean isEmoji(int codePoint) {
    return (codePoint >= 0x1F600 && codePoint <= 0x1F64F)   // Emoticons
            || (codePoint >= 0x1F300 && codePoint <= 0x1F5FF)   // Misc Symbols and Pictographs
            || (codePoint >= 0x1F680 && codePoint <= 0x1F6FF)   // Transport & Map Symbols
            || (codePoint >= 0x2600 && codePoint <= 0x26FF)     // Misc symbols
            || (codePoint >= 0x2700 && codePoint <= 0x27BF)     // Dingbats
            || (codePoint >= 0x1F900 && codePoint <= 0x1F9FF)   // Supplemental Symbols and Pictographs
            || (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF);  // Regional indicator symbols (flags)
  }

  /**
   * Returns true if the text (ignoring whitespace) consists solely of emojis.
   */
  private boolean isAllEmojis(CharSequence text) {
    if (text == null || text.length() == 0) return false;
    int len = text.length();
    int emojiCount = 0;
    for (int offset = 0; offset < len; ) {
      int codePoint = Character.codePointAt(text, offset);
      if (!Character.isWhitespace(codePoint)) {
        if (!isEmoji(codePoint)) {
          return false;
        }
        emojiCount++;
      }
      offset += Character.charCount(codePoint);
    }
    return emojiCount > 0;
  }

  /**
   * Counts the number of emoji codepoints in the text (ignoring whitespace).
   */
  private int countEmojis(CharSequence text) {
    if (text == null || text.length() == 0) return 0;
    int len = text.length();
    int emojiCount = 0;
    for (int offset = 0; offset < len; ) {
      int codePoint = Character.codePointAt(text, offset);
      if (!Character.isWhitespace(codePoint) && isEmoji(codePoint)) {
        emojiCount++;
      }
      offset += Character.charCount(codePoint);
    }
    return emojiCount;
  }

  @Override
  public void setText(@Nullable CharSequence text, BufferType type) {
    if (text == null || text.length() == 0) {
      previousText = text;
      previousOverflowText = overflowText;
      previousBufferType = type;
      super.setText(text, type);
      return;
    }

    boolean allEmojis = isAllEmojis(text);
    if (scaleEmojis && allEmojis) {
      int emojiCount = countEmojis(text);
      float scale = 1.0f;
      if (emojiCount <= 8) scale += 0.3f;
      if (emojiCount <= 6) scale += 0.3f;
      if (emojiCount <= 4) scale += 0.3f;
      if (emojiCount <= 2) scale += 0.3f;
      if (emojiCount <= 1) scale += 0.3f;
      super.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalFontSize * scale);
    } else if (scaleEmojis) {
      super.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalFontSize);
    }

    if (unchanged(text, overflowText, type)) {
      return;
    }

    previousText = text;
    previousOverflowText = overflowText;
    previousBufferType = type;

    // Let EmojiCompat (already initialized elsewhere) do its work.
    SpannableStringBuilder builder = new SpannableStringBuilder();
    builder.append(text).append(Optional.fromNullable(overflowText).or(""));
    super.setText(builder, BufferType.SPANNABLE);

    // Android fails to ellipsize spannable strings. (https://issuetracker.google.com/issues/36991688)
    // We ellipsize them ourselves by manually truncating the appropriate section.
    if (getEllipsize() == TextUtils.TruncateAt.END) {
      if (maxLength > 0) {
        ellipsizeAnyTextForMaxLength();
      } else {
        ellipsizeEmojiTextForMaxLines();
      }
    }
  }

  public void setOverflowText(@Nullable CharSequence overflowText) {
    this.overflowText = overflowText;
    setText(previousText, BufferType.SPANNABLE);
  }

  private void ellipsizeAnyTextForMaxLength() {
    if (maxLength > 0 && getText().length() > maxLength + 1) {
      SpannableStringBuilder newContent = new SpannableStringBuilder();
      newContent.append(getText().subSequence(0, maxLength))
              .append(ELLIPSIS)
              .append(Optional.fromNullable(overflowText).or(""));
      super.setText(newContent, BufferType.SPANNABLE);
    }
  }

  private void ellipsizeEmojiTextForMaxLines() {
    post(() -> {
      if (getLayout() == null) {
        ellipsizeEmojiTextForMaxLines();
        return;
      }
      int maxLines = TextViewCompat.getMaxLines(EmojiTextView.this);
      if (maxLines <= 0 && maxLength < 0) {
        return;
      }
      int lineCount = getLineCount();
      if (lineCount > maxLines) {
        int overflowStart = getLayout().getLineStart(maxLines - 1);
        CharSequence overflow = getText().subSequence(overflowStart, getText().length());
        CharSequence ellipsized = TextUtils.ellipsize(overflow, getPaint(), getWidth(), TextUtils.TruncateAt.END);
        SpannableStringBuilder newContent = new SpannableStringBuilder();
        newContent.append(getText().subSequence(0, overflowStart))
                .append(ellipsized.subSequence(0, ellipsized.length()))
                .append(Optional.fromNullable(overflowText).or(""));
        super.setText(newContent, BufferType.SPANNABLE);
      }
    });
  }

  private boolean unchanged(CharSequence text, CharSequence overflowText, BufferType bufferType) {
    CharSequence finalPrevText = (previousText == null || previousText.length() == 0 ? "" : previousText);
    CharSequence finalText = (text == null || text.length() == 0 ? "" : text);
    CharSequence finalPrevOverflowText = (previousOverflowText == null || previousOverflowText.length() == 0 ? "" : previousOverflowText);
    CharSequence finalOverflowText = (overflowText == null || overflowText.length() == 0 ? "" : overflowText);
    return Util.equals(finalPrevText, finalText)
            && Util.equals(finalPrevOverflowText, finalOverflowText)
            && Util.equals(previousBufferType, bufferType)
            && !sizeChangeInProgress;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (!sizeChangeInProgress) {
      sizeChangeInProgress = true;
      setText(previousText, previousBufferType);
      sizeChangeInProgress = false;
    }
  }

  @Override
  public void invalidateDrawable(@NonNull Drawable drawable) {
    super.invalidateDrawable(drawable);
  }

  @Override
  public void setTextSize(float size) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
  }

  @Override
  public void setTextSize(int unit, float size) {
    this.originalFontSize = TypedValue.applyDimension(unit, size, getResources().getDisplayMetrics());
    super.setTextSize(unit, size);
  }
}
