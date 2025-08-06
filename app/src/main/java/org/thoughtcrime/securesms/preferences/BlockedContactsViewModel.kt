package org.thoughtcrime.securesms.preferences

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.observeQuery
import com.squareup.phrase.Phrase
import dagger.assisted.Assisted
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.COUNT_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.groups.GroupManagerV2
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.DatabaseContentProviders
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.groups.SelectContactsViewModel
import org.thoughtcrime.securesms.pro.ProStatusManager
import org.thoughtcrime.securesms.util.AvatarUtils
import org.thoughtcrime.securesms.util.adapter.SelectableItem
import javax.inject.Inject

@HiltViewModel
class BlockedContactsViewModel @Inject constructor(
    configFactory: ConfigFactory,
    @ApplicationContext private val appContext: Context,
    private val storage: StorageProtocol,
    avatarUtils: AvatarUtils,
    proStatusManager: ProStatusManager,
): SelectContactsViewModel(
    configFactory = configFactory,
    excludingAccountIDs = emptySet(),
    contactFiltering = { it.isBlocked },
    appContext = appContext,
    avatarUtils = avatarUtils,
    proStatusManager = proStatusManager
) {

    fun unblock() {
//        storage.setBlocked(state.selectedItems, false)
//        _state.value = state.copy(selectedItems = emptySet())
    }

    fun onUnblockClicked(){

    }


    // Method to get the appropriate text to display when unblocking 1, 2, or several contacts
    fun getText(context: Context, contactsToUnblock: Set<Recipient>): CharSequence {
        return when (contactsToUnblock.size) {
            // Note: We do not have to handle 0 because if no contacts are chosen then the unblock button is deactivated
            1 -> Phrase.from(context, R.string.blockUnblockName)
                .put(NAME_KEY, contactsToUnblock.elementAt(0).name)
                .format()
            2 -> Phrase.from(context, R.string.blockUnblockNameTwo)
                .put(NAME_KEY, contactsToUnblock.elementAt(0).name)
                .format()
            else -> {
                val othersCount = contactsToUnblock.size - 1
                Phrase.from(context, R.string.blockUnblockNameMultiple)
                    .put(NAME_KEY, contactsToUnblock.elementAt(0).name)
                    .put(COUNT_KEY, othersCount)
                    .format()
            }
        }
    }
}
