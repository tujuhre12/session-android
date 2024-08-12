package org.thoughtcrime.securesms.video.exo;


import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSourceFactory;
import androidx.media3.datasource.TransferListener;


@UnstableApi
public class AttachmentDataSourceFactory implements DataSource.Factory {

  private final Context context;

  private final DefaultDataSourceFactory defaultDataSourceFactory;
  private final TransferListener listener;

  public AttachmentDataSourceFactory(@NonNull Context context,
                                     @NonNull DefaultDataSourceFactory defaultDataSourceFactory,
                                     @Nullable TransferListener listener)
  {
    this.context                  = context;
    this.defaultDataSourceFactory = defaultDataSourceFactory;
    this.listener                 = listener;
  }

  @Override
  public AttachmentDataSource createDataSource() {
    return new AttachmentDataSource(defaultDataSourceFactory.createDataSource(),
                                    new PartDataSource(context, listener));
  }
}
