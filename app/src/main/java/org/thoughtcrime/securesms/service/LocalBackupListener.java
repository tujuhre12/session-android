package org.thoughtcrime.securesms.service;

import android.content.Context;
import android.content.Intent;

import org.session.libsession.messaging.jobs.JobQueue;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.jobs.LocalBackupJob;
import org.session.libsession.utilities.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

public class LocalBackupListener extends PersistentAlarmManagerListener {

  private static final long INTERVAL = TimeUnit.DAYS.toMillis(1);

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return TextSecurePreferences.getNextBackupTime(context);
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    if (TextSecurePreferences.isBackupEnabled(context)) {
      LocalBackupJob job = new LocalBackupJob();
      job.context = context;
      JobQueue.getShared().add(job);
    }

    long nextTime = System.currentTimeMillis() + INTERVAL;
    TextSecurePreferences.setNextBackupTime(context, nextTime);

    return nextTime;
  }

  public static void schedule(Context context) {
    if (TextSecurePreferences.isBackupEnabled(context)) {
      new LocalBackupListener().onReceive(context, new Intent());
    }
  }
}
