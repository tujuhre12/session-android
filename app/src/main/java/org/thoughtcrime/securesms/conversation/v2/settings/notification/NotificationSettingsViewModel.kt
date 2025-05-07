package org.thoughtcrime.securesms.conversation.v2.settings.notification

import android.content.Context
import android.widget.Toast
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
import network.loki.messenger.libsession_util.util.ExpiryMode
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

    private var selectedOption: NotificationType = NotificationType.All //todo UCS this should be read from last selected choice in prefs

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
                NotificationType.All.toRadioOption(selectedOption),
                NotificationType.MentionsOnly.toRadioOption(selectedOption),
                NotificationType.Mute.toRadioOption(selectedOption)
            )
        )

        val options = mutableListOf(defaultOptions)

        // add the mute options if necessary
        if(selectedOption is NotificationType.Mute || selectedOption is NotificationType.MuteType) {
            options.add(
                //todo UCS add actual mute options
                //todo UCS figure out selection for this sub category when visible
                OptionsCardData(
                    title = null,
                    options = listOf(
                        NotificationType.All.toRadioOption(selectedOption),
                        NotificationType.MentionsOnly.toRadioOption(selectedOption),
                        NotificationType.Mute.toRadioOption(selectedOption)
                    )
                )
            )
        }
//todo UCS need to add icons ro radio options
        _uiState.update {
            UiState(
                cards = options,
                enableButton = true //todo UCS calculate this properly
            )
        }
    }

    override fun setValue(value: NotificationType){
        selectedOption = value
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

    data class UiState(
        val cards: List<OptionsCardData<NotificationType>> = emptyList(),
        val enableButton: Boolean = false,
    )

    sealed interface NotificationType {
        data object All: NotificationType
        data object MentionsOnly: NotificationType
        data object Mute: NotificationType
        sealed class MuteType(
            val duration: Long
        ): NotificationType{
            data object Mute1H: MuteType(TimeUnit.HOURS.toMillis(1))
            data object Mute2H: MuteType(TimeUnit.HOURS.toMillis(2))
            data object Mute1Day: MuteType(TimeUnit.DAYS.toMillis(1))
            data object Mute1Week: MuteType(TimeUnit.DAYS.toMillis(7))
            data object MuteForever: MuteType(Long.MAX_VALUE)

            fun getTitleFromDuration(context: Context)  = LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(
                context,
                this.duration.milliseconds
            )
        }

        fun getTitle(context: Context): String {
            return when(this){
                is All -> context.getString(R.string.notificationsAllMessages)
                is MentionsOnly -> context.getString(R.string.notificationsMentionsOnly)
                is Mute -> context.getString(R.string.notificationsMute)
                is MuteType -> getTitleFromDuration(context)
            }
        }

        fun toRadioOption(currentlySelectedOption: NotificationType) = RadioOption(
            value = this,
            title = GetString(this::getTitle),
            selected = this == currentlySelectedOption
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(threadId: Long): NotificationSettingsViewModel
    }
}