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
import kotlinx.coroutines.SupervisorJob
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
   /* fun onContactSelected(contacts: Set<AccountId>) {
        performGroupOperation(
            showLoading = false,
            errorMessage = { err ->
                if (err is GroupInviteException) {
                    err.format(context, usernameUtils).toString()
                } else {
                    null
                }
            }
        ) {
            groupManager.inviteMembers(
                groupId,
                contacts.toList(),
                shareHistory = false,
                isReinvite = false,
            )
        }
    }*/

    /**
     * Perform a group operation, such as inviting a member, removing a member.
     *
     * This is a helper function that encapsulates the common error handling and progress tracking.
     */
   /* private fun performGroupOperation(
        showLoading: Boolean = true,
        errorMessage: ((Throwable) -> String?)? = null,
        operation: suspend () -> Unit) {
        viewModelScope.launch {
            if (showLoading) {
                mutableInProgress.value = true
            }

            // We need to use GlobalScope here because we don't want
            // any group operation to be cancelled when the view model is cleared.
            @Suppress("OPT_IN_USAGE")
            val task = GlobalScope.async {
                operation()
            }

            try {
                task.await()
            } catch (e: Exception) {
                mutableError.value = errorMessage?.invoke(e)
                    ?: context.getString(R.string.errorUnknown)
            } finally {
                if (showLoading) {
                    mutableInProgress.value = false
                }
            }
        }
    }*/


    @AssistedFactory
    interface Factory {
        fun create(
            groupId: AccountId,
            excludingAccountIDs: Set<AccountId> = emptySet(),
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        ): InviteContactsViewModel
    }
}
