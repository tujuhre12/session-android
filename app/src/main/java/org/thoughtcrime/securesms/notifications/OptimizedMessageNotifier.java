package org.thoughtcrime.securesms.notifications;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.session.libsession.messaging.sending_receiving.notifications.MessageNotifier;
import org.session.libsession.messaging.sending_receiving.pollers.Poller;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.utilities.Debouncer;
import org.session.libsignal.utilities.ThreadUtils;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.groups.OpenGroupManager;

import java.util.concurrent.TimeUnit;

public class OptimizedMessageNotifier implements MessageNotifier {
  private final MessageNotifier         wrapped;
  private final Debouncer               debouncer;

  @MainThread
  public OptimizedMessageNotifier(@NonNull MessageNotifier wrapped) {
    this.wrapped   = wrapped;
    this.debouncer = new Debouncer(TimeUnit.SECONDS.toMillis(2));
  }

  @Override
  public void setVisibleThread(long threadId) { wrapped.setVisibleThread(threadId); }

  @Override
  public void setHomeScreenVisible(boolean isVisible) {
    wrapped.setHomeScreenVisible(isVisible);
  }

  @Override public void setLastDesktopActivityTimestamp(long timestamp) { wrapped.setLastDesktopActivityTimestamp(timestamp);}

  @Override
  public void notifyMessageDeliveryFailed(Context context, Recipient recipient, long threadId) {
    wrapped.notifyMessageDeliveryFailed(context, recipient, threadId);
  }

  @Override
  public void cancelDelayedNotifications() { wrapped.cancelDelayedNotifications(); }

  @Override
  public void resetAllNotificationsSilently(@NonNull Context context) {
    Poller poller = ApplicationContext.getInstance(context).poller;
    boolean isCaughtUp = true;
    if (poller != null) {
      isCaughtUp = isCaughtUp && poller.isCaughtUp();
    }

    isCaughtUp = isCaughtUp && OpenGroupManager.INSTANCE.isAllCaughtUp();

    if (isCaughtUp) {
      performOnBackgroundThreadIfNeeded(() -> wrapped.resetAllNotificationsSilently(context));
    } else {
      debouncer.publish(() -> performOnBackgroundThreadIfNeeded(() -> wrapped.resetAllNotificationsSilently(context)));
    }
  }

  @Override
  public void updateNotificationForSpecificThread(@NonNull Context context, long threadId) {
    Poller lokiPoller = ApplicationContext.getInstance(context).poller;
    boolean isCaughtUp = true;
    if (lokiPoller != null) {
      isCaughtUp = isCaughtUp && lokiPoller.isCaughtUp();
    }

    isCaughtUp = isCaughtUp && OpenGroupManager.INSTANCE.isAllCaughtUp();
    
    if (isCaughtUp) {
      performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotificationForSpecificThread(context, threadId));
    } else {
      debouncer.publish(() -> performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotificationForSpecificThread(context, threadId)));
    }
  }

  @Override
  public void updateNotificationForSpecificThreadWithOptionalAudio(@NonNull Context context, long threadId, boolean signalTheUser) {
    Poller lokiPoller = ApplicationContext.getInstance(context).poller;
    boolean isCaughtUp = true;
    if (lokiPoller != null) {
      isCaughtUp = isCaughtUp && lokiPoller.isCaughtUp();
    }

    isCaughtUp = isCaughtUp && OpenGroupManager.INSTANCE.isAllCaughtUp();

    if (isCaughtUp) {
      performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotificationForSpecificThreadWithOptionalAudio(context, threadId, signalTheUser));
    } else {
      debouncer.publish(() -> performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotificationForSpecificThreadWithOptionalAudio(context, threadId, signalTheUser)));
    }
  }

  @Override
  public void updateNotificationWithReminderCountAndOptionalAudio(@androidx.annotation.NonNull Context context, int reminderCount, boolean playNotificationAudio) {
    Poller lokiPoller = ApplicationContext.getInstance(context).poller;
    boolean isCaughtUp = true;
    if (lokiPoller != null) {
      isCaughtUp = isCaughtUp && lokiPoller.isCaughtUp();
    }

    isCaughtUp = isCaughtUp && OpenGroupManager.INSTANCE.isAllCaughtUp();

    if (isCaughtUp) {
      performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotificationWithReminderCountAndOptionalAudio(context, reminderCount, playNotificationAudio));
    } else {
      debouncer.publish(() -> performOnBackgroundThreadIfNeeded(() -> wrapped.updateNotificationWithReminderCountAndOptionalAudio(context, reminderCount, playNotificationAudio)));
    }
  }

  @Override
  public void clearReminder(@NonNull Context context) { wrapped.clearReminder(context); }

  private void performOnBackgroundThreadIfNeeded(Runnable r) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      ThreadUtils.queue(r);
    } else {
      r.run();
    }
  }
}
