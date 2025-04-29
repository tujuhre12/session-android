package org.thoughtcrime.securesms.groups

import android.content.Context
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.session.libsession.messaging.groups.GroupManagerV2
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.util.AvatarUtils

@OptIn(FlowPreview::class)
@HiltViewModel(assistedFactory = InviteContactsViewModel.Factory::class)
class InviteContactsViewModel @AssistedInject constructor(
    private val configFactory: ConfigFactory,
    private val avatarUtils: AvatarUtils,
    private val groupManager: GroupManagerV2,
    @ApplicationContext private val appContext: Context,
    @Assisted private val groupId: AccountId,
    @Assisted private val excludingAccountIDs: Set<AccountId>,
    @Assisted private val scope: CoroutineScope,
) : SelectContactsViewModel(
    configFactory,
    avatarUtils,
    appContext,
    excludingAccountIDs,
    scope
) {
    fun onContactSelected(contacts: Set<AccountId>) {
        // perform this on a global scope as a fire and forget
        // other screens can deal with displaying something for the invite failure
        GlobalScope.launch {
            try {
                groupManager.inviteMembers(
                    groupId,
                    contacts.toList(),
                    shareHistory = false,
                    isReinvite = false,
                )
            } catch (e: Exception) {
                // we can safely ignore exceptions
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            groupId: AccountId,
            excludingAccountIDs: Set<AccountId> = emptySet(),
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        ): InviteContactsViewModel
    }
}
