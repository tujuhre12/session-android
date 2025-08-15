package org.thoughtcrime.securesms.pro.subscription

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaySubscriptionModule {

    @Provides
    @IntoSet
    fun providePlayStoreManager(manager: PlayStoreSubscriptionManager): SubscriptionManager {
        return manager
    }
}