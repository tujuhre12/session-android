package org.thoughtcrime.securesms.notifications

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HuaweiPushModule {
    @Provides
    @Singleton
    fun provideFirebasePushManager(
        @ApplicationContext context: Context,
    ) = HuaweiPushManager(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HuaweiBindingModule {
    @Binds
    abstract fun bindPushManager(pushManager: HuaweiPushManager): PushManager
}
