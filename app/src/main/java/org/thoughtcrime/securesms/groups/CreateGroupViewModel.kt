package org.thoughtcrime.securesms.groups

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.Storage
import javax.inject.Inject

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val textSecurePreferences: TextSecurePreferences,
    private val storage: Storage,
) : ViewModel() {

    private val _recipients = MutableLiveData<List<Recipient>>()
    val recipients: LiveData<List<Recipient>> = _recipients

    private val _viewState = MutableLiveData(CreateGroupFragment.ViewState(false, null, null))
    val viewState: LiveData<CreateGroupFragment.ViewState>  = _viewState

    init {
        viewModelScope.launch {
//            threadDb.approvedConversationList.use { openCursor ->
//                val reader = threadDb.readerFor(openCursor)
//                val recipients = mutableListOf<Recipient>()
//                while (true) {
//                    recipients += reader.next?.recipient ?: break
//                }
//                withContext(Dispatchers.Main) {
//                    _recipients.value = recipients
//                        .filter { !it.isGroupRecipient && it.hasApprovedMe() && it.address.serialize() != textSecurePreferences.getLocalNumber() }
//                }
//            }
        }
    }

    fun tryCreateGroup(createGroupState: CreateGroupState) {
        _viewState.postValue(CreateGroupFragment.ViewState(true, null, null))

        // do some validations
        if (createGroupState.groupName.isEmpty()) {
            return _viewState.postValue(
                CreateGroupFragment.ViewState(false, R.string.error, null)
            )
        }
        // TODO: add future validation for empty group ? we'll add ourselves anyway ig

        // make a group
        storage.createGroup()
    }

    fun filter(query: String): List<Recipient> {
        return _recipients.value?.filter {
            it.address.serialize().contains(query, ignoreCase = true) || it.name?.contains(query, ignoreCase = true) == true
        } ?: emptyList()
    }
}