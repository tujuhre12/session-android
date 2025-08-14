package org.thoughtcrime.securesms.home.startconversation.community

import android.content.Context
import android.webkit.URLUtil
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import org.session.libsession.database.StorageProtocol
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.OpenGroupUrlParser
import org.session.libsession.utilities.StringSubstitutionConstants.GROUP_NAME_KEY
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.groups.DefaultGroups
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.groups.GroupState
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.ui.getSubbedString
import org.thoughtcrime.securesms.util.State
import javax.inject.Inject

@HiltViewModel
class JoinCommunityViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val openGroupManager: OpenGroupManager,
    private val storage: StorageProtocol
): ViewModel() {

    val defaultRooms = OpenGroupApi.defaultRooms.map<DefaultGroups, GroupState> {
        State.Success(it)
    }.onStart {
        emit(State.Loading)
    }

    private val _state = MutableStateFlow(JoinCommunityState(defaultCommunities = State.Loading))
    val state: StateFlow<JoinCommunityState> = _state

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<UiEvent> get() = _uiEvents

    private var lastUrl: String? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            defaultRooms.collect { defaultCommunities ->
                _state.update { it.copy(defaultCommunities = defaultCommunities) }
            }
        }
    }

    //todo NEWCONVO delete JoinCommunityUriFragment, its layout, DefaultGroupsViewModel, JoinCOmmunityFragmentAdatpter, JoinCommunityFragment, its layout, default_group_chip

    private fun joinCommunityIfPossible(url: String) {
        //todo NEWCONVO test when there is an error, can we retry?
        if(lastUrl == url) return
        lastUrl = url

        viewModelScope.launch(Dispatchers.Default) {
            val openGroup = try {
                OpenGroupUrlParser.parseUrl(url)
            } catch (e: OpenGroupUrlParser.Error) {
                when (e) {
                    is OpenGroupUrlParser.Error.MalformedURL, OpenGroupUrlParser.Error.NoRoom -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                appContext,
                                appContext.getString(R.string.communityJoinError),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    is OpenGroupUrlParser.Error.InvalidPublicKey, OpenGroupUrlParser.Error.NoPublicKey -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                appContext,
                                appContext.getString(R.string.communityEnterUrlErrorInvalidDescription),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }
                }
            }

            _state.update { it.copy(loading = true) }

            try {
                val sanitizedServer = openGroup.server.removeSuffix("/")
                val openGroupID = "$sanitizedServer.${openGroup.room}"
                openGroupManager.add(
                    sanitizedServer,
                    openGroup.room,
                    openGroup.serverPublicKey,
                    appContext
                )

                storage.onOpenGroupAdded(sanitizedServer, openGroup.room)
                val threadID = GroupManager.getOpenGroupThreadID(openGroupID, appContext)

                withContext(Dispatchers.Main) {
                    _uiEvents.emit(UiEvent.NavigateToConversation(
                        threadId = threadID
                    ))
                }
            } catch (e: Exception) {
                Log.e("Loki", "Couldn't join community.", e)
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(loading = false) }

                    val txt = appContext.getSubbedString(R.string.groupErrorJoin,
                        GROUP_NAME_KEY to url)
                    Toast.makeText(appContext, txt, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    //todo NEWCONVO clear loading on error

    fun onCommand(command: Commands) {
        when (command) {
            is Commands.OnQRScanned -> {
                joinCommunityIfPossible(command.qr)
            }

            Commands.JoinCommunity -> {
                joinCommunityIfPossible(_state.value.communityUrl.trim())
            }

            is Commands.OnUrlChanged -> {
                _state.update {
                    it.copy(
                        communityUrl = command.url,
                        isJoinButtonEnabled = URLUtil.isValidUrl(command.url.trim())
                    )
                }
            }
        }
    }

    data class JoinCommunityState(
        val loading: Boolean = false,
        val isJoinButtonEnabled: Boolean = false,
        val communityUrl: String = "",
        val defaultCommunities: State<List<OpenGroupApi.DefaultGroup>>
    )

    sealed interface Commands {
        data class OnQRScanned(val qr: String) : Commands
        data object JoinCommunity: Commands
        data class OnUrlChanged(val url: String): Commands
    }

    sealed interface UiEvent {
        data class NavigateToConversation(val threadId: Long) : UiEvent
    }
}