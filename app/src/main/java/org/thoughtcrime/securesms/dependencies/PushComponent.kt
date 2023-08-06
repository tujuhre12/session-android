package org.thoughtcrime.securesms.dependencies

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.thoughtcrime.securesms.notifications.PushManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PushComponent {

}