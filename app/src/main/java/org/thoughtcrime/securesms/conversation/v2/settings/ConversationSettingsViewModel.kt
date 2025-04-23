package org.thoughtcrime.securesms.conversation.v2.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.session.libsession.database.StorageProtocol
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.UsernameUtils
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.util.AvatarUtils


@HiltViewModel(assistedFactory = ConversationSettingsViewModel.Factory::class)
class ConversationSettingsViewModel @AssistedInject constructor(
    @Assisted private val threadId: Long,
    @ApplicationContext context: Context,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(threadId: Long): ConversationSettingsViewModel
    }
}
