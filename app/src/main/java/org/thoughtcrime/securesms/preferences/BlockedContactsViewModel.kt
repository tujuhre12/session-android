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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
import org.session.libsession.utilities.Address
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
    @ApplicationContext private val context: Context,
    private val storage: StorageProtocol,
    avatarUtils: AvatarUtils,
    proStatusManager: ProStatusManager,
): SelectContactsViewModel(
    configFactory = configFactory,
    excludingAccountIDs = emptySet(),
    contactFiltering = { it.isBlocked },
    appContext = context,
    avatarUtils = avatarUtils,
    proStatusManager = proStatusManager
) {
    private val _unblockDialog = MutableStateFlow(false)
    val unblockDialog: StateFlow<Boolean> = _unblockDialog

    fun unblock() {
        viewModelScope.launch {
            storage.setBlocked(
                recipients = currentSelected.map {
                    Recipient.from(context, Address.fromSerialized(it.hexString), false)
                },
                isBlocked = false
            )

            clearSelection()
        }
    }

    fun onUnblockClicked(){
        _unblockDialog.value = true
    }

    fun hideUnblockDialog(){
        _unblockDialog.value = false
    }

    // Method to get the appropriate text to display when unblocking 1, 2, or several contacts
    fun getDialogText(): CharSequence {
        val contactsList = contacts.value
        val firstSelected = contactsList.first { it.accountID == currentSelected.elementAt(0) }

        return when (currentSelected.size) {
            // Note: We do not have to handle 0 because if no contacts are chosen then the unblock button is deactivated
            1 -> Phrase.from(context, R.string.blockUnblockName)
                .put(NAME_KEY, firstSelected.name)
                .format()
            2 -> Phrase.from(context, R.string.blockUnblockNameTwo)
                .put(NAME_KEY, firstSelected.name)
                .format()
            else -> {
                val othersCount = currentSelected.size - 1
                Phrase.from(context, R.string.blockUnblockNameMultiple)
                    .put(NAME_KEY, firstSelected.name)
                    .put(COUNT_KEY, othersCount)
                    .format()
            }
        }
    }
}
