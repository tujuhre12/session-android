package org.thoughtcrime.securesms.components.emoji;

import static androidx.emoji2.text.EmojiCompat.LOAD_STATE_SUCCEEDED;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.widget.TextViewCompat;
import androidx.emoji2.text.EmojiCompat;
import androidx.emoji2.text.EmojiSpan;

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

  @Override
  public void setText(@Nullable CharSequence text, BufferType type) {
    if (text == null || text.length() == 0) {
      previousText = text;
      previousOverflowText = overflowText;
      previousBufferType = type;
      super.setText(text, type);
      return;
    }

    if (unchanged(text, overflowText, type)) {
      return;
    }

    if(scaleEmojis && EmojiCompat.get().getLoadState() == LOAD_STATE_SUCCEEDED) {
      // Using EmojiCompat to process the text in order to know how many emojis we have
      CharSequence processedText = EmojiCompat.get().process(text, 0, text.length(), Integer.MAX_VALUE, EmojiCompat.REPLACE_STRATEGY_ALL);
      boolean allEmojis = processedText instanceof Spannable && isTextOnlyEmojiUsingSpans((Spannable) processedText);
      if (allEmojis) {
        int emojiCount = 0;
        Spannable spannable = (Spannable) processedText;
        emojiCount = spannable.getSpans(0, spannable.length(), EmojiSpan.class).length;

        float scale = 1.0f;
        if (emojiCount <= 8) scale += 0.3f;
        if (emojiCount <= 6) scale += 0.3f;
        if (emojiCount <= 4) scale += 0.3f;
        if (emojiCount <= 2) scale += 0.3f;
        if (emojiCount <= 1) scale += 0.3f;
        super.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalFontSize * scale);
      } else {
        super.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalFontSize);
      }
    } else {
      super.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalFontSize);
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

  /**
   * Checks if the given text only contains emojis by going through the spans
   * @param text
   * @return true if the text only contains emojis, false otherwise
   */
  public static boolean isTextOnlyEmojiUsingSpans(Spannable text) {
    if (text == null || text.length() == 0) {
      // Depending on how you define "only emoji," empty text might be false or true.
      return false;
    }

    int length = text.length();
    EmojiSpan[] spans = text.getSpans(0, length, EmojiSpan.class);

    // If there are no spans at all, but the text isn't empty, it can't be all emoji
    if (spans == null || spans.length == 0) {
      return false;
    }

    // Track coverage for each character
    boolean[] coverage = new boolean[length];
    for (EmojiSpan span : spans) {
      int start = Math.max(0, text.getSpanStart(span));
      int end   = Math.min(length, text.getSpanEnd(span));

      for (int i = start; i < end; i++) {
        coverage[i] = true;
      }
    }

    // Check if every character is covered
    for (boolean covered : coverage) {
      if (!covered) {
        return false;
      }
    }
    return true;
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
