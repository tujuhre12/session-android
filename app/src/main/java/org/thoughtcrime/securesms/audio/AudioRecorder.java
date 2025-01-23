package org.thoughtcrime.securesms.audio;

import android.content.Context;
import android.net.Uri;
import android.util.Pair;
import androidx.annotation.NonNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.session.libsession.utilities.MediaTypes;
import org.session.libsession.utilities.Util;
import org.session.libsignal.utilities.ListenableFuture;
import org.session.libsignal.utilities.Log;
import org.session.libsignal.utilities.SettableFuture;
import org.session.libsignal.utilities.ThreadUtils;
import org.thoughtcrime.securesms.providers.BlobProvider;

public class AudioRecorder {

  private static final String TAG = AudioRecorder.class.getSimpleName();

  // Single-threaded executor for sequential I/O operations
  private static final ExecutorService executor = ThreadUtils.newDynamicSingleThreadedExecutor();

  private final Context context;
  private AudioCodec    audioCodec;
  private File          tempAudioFile;

  // Callback so the calling code can know when "startRecording()" is done
  public interface VoiceMessageRecordingStartedCallback {
    void onVoiceMessageRecordingStarted();
  }

  public AudioRecorder(@NonNull Context context) { this.context = context; }

  // Method to instigate recording a voice message
  public void startRecording(@NonNull VoiceMessageRecordingStartedCallback callback) {
    Log.i(TAG, "startRecording()");

    executor.execute(() -> {
      Log.i(TAG, "Running startRecording() on thread " + Thread.currentThread().getId());

      if (audioCodec != null) {
        Log.w(TAG, "Already recording, ignoring start request...");
        return;
      }

      try {
        // Create a temporary audio file in cache & open a FileOutputStream for it
        tempAudioFile = File.createTempFile("temp_voice_message", ".aac", context.getCacheDir());
        Log.i(TAG, "Created temp audio file: " + tempAudioFile.getAbsolutePath());
        FileOutputStream fos = new FileOutputStream(tempAudioFile);

        // Start recording using our AudioCodec & notify the callback that recording started successfully
        audioCodec = new AudioCodec();
        audioCodec.start(fos);
        callback.onVoiceMessageRecordingStarted();
      } catch (IOException e) {
        Log.w(TAG, "Failed to start recording", e);
      }
    });
  }

  // Method to write the recorded voice message from our temp file into the database and delete the file afterwards
  public @NonNull ListenableFuture<Pair<Uri, Long>> stopRecording() {
    Log.i(TAG, "stopRecording()");

    final SettableFuture<Pair<Uri, Long>> future = new SettableFuture<>();

    executor.execute(() -> {
      if (audioCodec == null) {
        sendToFuture(future, new IOException("AudioCodec was never initialized successfully!"));
        return;
      }

      // Stop the AudioCodec so the file is finalized
      audioCodec.stop();
      audioCodec = null;

      // Make sure we have a valid temp file
      if (tempAudioFile == null || !tempAudioFile.exists()) {
        sendToFuture(future, new IOException("Temp audio file not found!"));
        return;
      }

      try {
        // Prepare the FileInputStream and note its size
        final FileInputStream fis = new FileInputStream(tempAudioFile);
        final long fileSize = tempAudioFile.length();

        // Construct the BlobSpec from the temp file
        Long now = System.currentTimeMillis();
        String nowString = now.toString();
        String filename = tempAudioFile.getName();
        BlobProvider.BlobSpec blobSpec = new BlobProvider.BlobSpec(fis, nowString, BlobProvider.StorageType.SINGLE_SESSION_DISK, MediaTypes.AUDIO_AAC, filename, fileSize);

        // Write to disk and receive a CompletableFuture<Uri> to delete the temp file once written
        CompletableFuture<Uri> completableFuture =
                BlobProvider.getInstance()
                        .writeBlobSpecToDiskWithFutureToDeleteFileLater(
                                context,
                                blobSpec,
                                e -> Log.w(TAG, "Error during recording insert", e)
                        );

        // Bridge the CompletableFuture<Uri> into the ListenableFuture<Pair<Uri, Long>>
        completableFuture.whenComplete((uri, throwable) -> {
          if (throwable != null) {
            // Async copy failed
            sendToFuture(future, (Exception)throwable);
          } else {
            // Success -> Return (uri, fileSize)
            sendToFuture(future, new Pair<>(uri, fileSize));
          }

          // Clean up the temporary file now that the copy is done
          if (tempAudioFile != null && tempAudioFile.exists()) {
            // noinspection ResultOfMethodCallIgnored
            tempAudioFile.delete();
          }
          tempAudioFile = null;
        });

      } catch (IOException e) {
        sendToFuture(future, e);
      }
    });

    return future;
  }

  // Helper for setting an exception on the future
  private <T> void sendToFuture(final SettableFuture<T> future, final Exception exception) {
    Util.runOnMain(() -> future.setException(exception));
  }

  // Helper for setting a success result on the future
  private <T> void sendToFuture(final SettableFuture<T> future, final T result) {
    Util.runOnMain(() -> future.set(result));
  }
}