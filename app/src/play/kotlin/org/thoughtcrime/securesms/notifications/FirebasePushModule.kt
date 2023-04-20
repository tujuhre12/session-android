package org.thoughtcrime.securesms.notifications

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.session.libsession.utilities.TextSecurePreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebasePushModule {
    @Provides
    @Singleton
    fun provideFirebasePushManager(
        @ApplicationContext context: Context,
        prefs: TextSecurePreferences,
    ): PushManager = FirebasePushManager(context, prefs)
}