package org.thoughtcrime.securesms.conversation.v2.settings.notification

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.LocalisedTimeUtil
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.repository.ConversationRepository
import org.thoughtcrime.securesms.ui.Callbacks
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.OptionsCardData
import org.thoughtcrime.securesms.ui.RadioOption
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel(assistedFactory = NotificationSettingsViewModel.Factory::class)
class NotificationSettingsViewModel @AssistedInject constructor(
    @Assisted private val threadId: Long,
    @ApplicationContext private val context: Context,
    private val recipientDatabase: RecipientDatabase,
    private val repository: ConversationRepository,
) : ViewModel(), Callbacks<NotificationSettingsViewModel.NotificationType> {
    private var thread: Recipient? = null

    // the options the user is currently using
    private var currentOption: NotificationType = NotificationType.All //todo UCS this should be read from last selected choice in prefs
    private var currentMuteDuration: Long = Long.MAX_VALUE //todo UCS this should be read from last selected choice in prefs

    // the option selected on this screen
    private var selectedOption: NotificationType = currentOption
    private var selectedMuteDuration: Long = currentMuteDuration

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        // update data when we have a recipient and update when there are changes from the thread or recipient
        viewModelScope.launch(Dispatchers.Default) {
            repository.recipientUpdateFlow(threadId).collect {
                thread = it

                updateState()
            }
        }
    }

    private fun updateState(){
        // start with the default options
        val defaultOptions = OptionsCardData(
            title = null,
            options = listOf(
                // All
                RadioOption(
                    value = NotificationType.All,
                    title = GetString(R.string.notificationsAllMessages),
                    iconRes = R.drawable.ic_volume_2,
                    selected = selectedOption is NotificationType.All
                ),
                // Mentions Only
                RadioOption(
                    value = NotificationType.MentionsOnly,
                    title = GetString(R.string.notificationsMentionsOnly),
                    iconRes = R.drawable.ic_at_sign,
                    selected = selectedOption is NotificationType.MentionsOnly
                ),
                // Mute
                RadioOption(
                    value = NotificationType.Mute(selectedMuteDuration),
                    title = GetString(R.string.notificationsMute),
                    iconRes = R.drawable.ic_volume_off,
                    selected = selectedOption is NotificationType.Mute
                ),
            )
        )

        val options = mutableListOf(defaultOptions)

        // add the mute options if necessary
        if(selectedOption is NotificationType.Mute) {
            options.add(
                OptionsCardData(
                    title = GetString(R.string.disappearingMessagesTimer),
                    options = muteDurations.map {
                        RadioOption(
                            value = NotificationType.Mute(it),
                            title =
                                if(it == Long.MAX_VALUE) GetString(R.string.forever)
                                else GetString(
                                    LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(
                                        context,
                                        it.milliseconds
                                    )
                            ),
                            selected = (selectedOption as? NotificationType.Mute)?.duration == it
                        )
                    }
                )
            )
        }

        _uiState.update {
            UiState(
                cards = options,
                enableButton = true //todo UCS calculate this properly
            )
        }
    }

    override fun setValue(value: NotificationType){
        selectedOption = value
        if(value is NotificationType.Mute){
            selectedMuteDuration = value.duration
        }

        updateState()
    }

    override fun onSetClick() = viewModelScope.launch {
        //todo UCS implement
    }

    private fun unmute(context: Context) {
        val conversation = thread ?: return
        recipientDatabase.setMuted(conversation, 0)
    }

    private fun mute(context: Context) {
        val conversation = thread ?: return
        //conversation.setMuted(thread, until)
    }

    private fun setNotifyType(context: Context) {
        val conversation = thread ?: return
        //conversation.setNotifyType(thread, notifyType)
    }

    private val muteDurations = listOf(
        Long.MAX_VALUE,
        TimeUnit.HOURS.toMillis(1),
        TimeUnit.HOURS.toMillis(2),
        TimeUnit.DAYS.toMillis(1),
        TimeUnit.DAYS.toMillis(7),
    )

    data class UiState(
        val cards: List<OptionsCardData<NotificationType>> = emptyList(),
        val enableButton: Boolean = false,
    )

    sealed interface NotificationType {
        data object All: NotificationType
        data object MentionsOnly: NotificationType
        data class Mute(val duration: Long): NotificationType
    }

    @AssistedFactory
    interface Factory {
        fun create(threadId: Long): NotificationSettingsViewModel
    }
}