package org.thoughtcrime.securesms.preferences

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.observeQuery
import com.squareup.phrase.Phrase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.COUNT_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsession.database.StorageProtocol
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.toAddress
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.recipients.displayName
import org.session.libsession.utilities.userConfigsChanged
import org.thoughtcrime.securesms.database.DatabaseContentProviders
import org.thoughtcrime.securesms.database.RecipientRepository
import org.thoughtcrime.securesms.util.adapter.SelectableItem
import javax.inject.Inject

@HiltViewModel
class BlockedContactsViewModel @Inject constructor(
    private val storage: StorageProtocol,
    private val configFactory: ConfigFactoryProtocol,
    private val recipientRepository: RecipientRepository,
): ViewModel() {
    private val selectedAddresses = MutableStateFlow<Set<Address>>(emptySet())

    val state: StateFlow<BlockedContactsViewState> = combine(
        selectedAddresses,
        configFactory.userConfigsChanged()
            .onStart { emit(Unit) }
            .map {
                configFactory.withUserConfigs {
                    it.contacts.all()
                }.asSequence()
                    .filter { it.blocked }
                    .map { recipientRepository.getRecipientSync(it.id.toAddress()) }
                    .toList()
            }
    ) { selected, blocked ->
        BlockedContactsViewState(
            blockedContacts = blocked,
            selectedItems = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BlockedContactsViewState())

    fun unblock() {
        storage.setBlocked(selectedAddresses.value, false)
        selectedAddresses.value = emptySet()
    }

    fun select(selectedItem: Recipient, isSelected: Boolean) {
        if (isSelected) {
            selectedAddresses.value += selectedItem.address
        } else {
            selectedAddresses.value -= selectedItem.address
        }
    }

    // Method to get the appropriate text to display when unblocking 1, 2, or several contacts
    fun getText(context: Context, contactsToUnblock: Set<Address>): CharSequence {
        val contacts = contactsToUnblock
            .mapNotNull { address -> state.value.blockedContacts.firstOrNull { it.address == address } }
        return when (contacts.size) {
            // Note: We do not have to handle 0 because if no contacts are chosen then the unblock button is deactivated
            1 -> Phrase.from(context, R.string.blockUnblockName)
                .put(NAME_KEY, contacts.first().displayName())
                .format()
            2 -> Phrase.from(context, R.string.blockUnblockNameTwo)
                .put(NAME_KEY, contacts.first().displayName())
                .format()
            else -> {
                val othersCount = contacts.size - 1
                Phrase.from(context, R.string.blockUnblockNameMultiple)
                    .put(NAME_KEY, contacts.first().displayName())
                    .put(COUNT_KEY, othersCount)
                    .format()
            }
        }
    }

    fun toggle(selectable: SelectableItem<Recipient>) {
        select(selectable.item, !selectable.isSelected)
    }

    data class BlockedContactsViewState(
        val blockedContacts: List<Recipient> = emptyList(),
        val selectedItems: Set<Address> = emptySet()
    ) {
        val items = blockedContacts.map { SelectableItem(it, it.address in selectedItems) }

        val unblockButtonEnabled get() = selectedItems.isNotEmpty()
        val emptyStateMessageTextViewVisible get() = blockedContacts.isEmpty()
        val nonEmptyStateGroupVisible get() = blockedContacts.isNotEmpty()
    }
}
