package org.thoughtcrime.securesms.jobmanager;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.jobmanager.impl.DefaultExecutorFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;

/**
 * Allows the scheduling of durable jobs that will be run as early as possible.
 */
public class JobManager implements ConstraintObserver.Notifier {

  private static final String TAG = JobManager.class.getSimpleName();

  private final ExecutorService executor;

  private final Set<EmptyQueueListener> emptyQueueListeners = new CopyOnWriteArraySet<>();

  public JobManager(@NonNull Application application, @NonNull Configuration configuration) {
    this.executor      = configuration.getExecutorFactory().newSingleThreadExecutor("JobManager");

    executor.execute(() -> {
      for (ConstraintObserver constraintObserver : configuration.getConstraintObservers()) {
        constraintObserver.register(this);
      }

      if (Build.VERSION.SDK_INT < 26) {
        application.startService(new Intent(application, KeepAliveService.class));
      }

      wakeUp();
    });
  }

  /**
   * Adds a listener to that will be notified when the job queue has been drained.
   */
  void addOnEmptyQueueListener(@NonNull EmptyQueueListener listener) {
    executor.execute(() -> {
      emptyQueueListeners.add(listener);
    });
  }

  /**
   * Removes a listener that was added via {@link #addOnEmptyQueueListener(EmptyQueueListener)}.
   */
  void removeOnEmptyQueueListener(@NonNull EmptyQueueListener listener) {
    executor.execute(() -> {
      emptyQueueListeners.remove(listener);
    });
  }

  @Override
  public void onConstraintMet(@NonNull String reason) {
    Log.i(TAG, "onConstraintMet(" + reason + ")");
    wakeUp();
  }

  /**
   * Pokes the system to take another pass at the job queue.
   */
  void wakeUp() {}

  public interface EmptyQueueListener {
    void onQueueEmpty();
  }

  public static class Configuration {

    private final ExecutorFactory          executorFactory;
    private final List<ConstraintObserver> constraintObservers;

    private Configuration(@NonNull ExecutorFactory executorFactory,
                          @NonNull List<ConstraintObserver> constraintObservers)
    {
      this.executorFactory        = executorFactory;
      this.constraintObservers    = constraintObservers;
    }

    @NonNull ExecutorFactory getExecutorFactory() {
      return executorFactory;
    }

    @NonNull List<ConstraintObserver> getConstraintObservers() {
      return constraintObservers;
    }

    public static class Builder {

      private ExecutorFactory                 executorFactory     = new DefaultExecutorFactory();
      private List<ConstraintObserver>        constraintObservers = new ArrayList<>();

      public @NonNull Builder setConstraintObservers(@NonNull List<ConstraintObserver> constraintObservers) {
        this.constraintObservers = constraintObservers;
        return this;
      }

      public @NonNull Configuration build() {
        return new Configuration(executorFactory,
                                 new ArrayList<>(constraintObservers));
      }
    }
  }
}
