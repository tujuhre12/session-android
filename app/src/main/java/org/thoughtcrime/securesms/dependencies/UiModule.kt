package org.thoughtcrime.securesms.dependencies

import androidx.recyclerview.widget.RecyclerView
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class UiModule {
    @Singleton
    @Provides
    @ConversationViewPool
    fun provideConversationRecycledViewPool(): RecyclerView.RecycledViewPool {
        return RecyclerView.RecycledViewPool()
    }
}