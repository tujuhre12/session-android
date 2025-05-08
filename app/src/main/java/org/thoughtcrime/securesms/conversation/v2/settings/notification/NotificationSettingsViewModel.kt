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
import org.thoughtcrime.securesms.database.RecipientDatabase.NOTIFY_TYPE_MENTIONS
import org.thoughtcrime.securesms.database.RecipientDatabase.NOTIFY_TYPE_NONE
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
) : ViewModel(), Callbacks<Any> {
    private var thread: Recipient? = null

    private val durationForever: Long = Long.MAX_VALUE

    // the options the user is currently using
    private var currentOption: NotificationType = NotificationType.All //todo UCS this should be read from last selected choice in prefs
    private var currentMutedUntil: Long? = null //todo UCS this should be read from last selected choice in prefs

    // the option selected on this screen
    private var selectedOption: NotificationType = NotificationType.All
    private var selectedMuteDuration: Long? = durationForever

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        // update data when we have a recipient and update when there are changes from the thread or recipient
        viewModelScope.launch(Dispatchers.Default) {
            repository.recipientUpdateFlow(threadId).collect {
                thread = it

                // update the user's current choice of notification
                currentMutedUntil = it?.mutedUntil

                currentOption = when{
                    currentMutedUntil != null -> NotificationType.Mute
                    it?.notifyType == NOTIFY_TYPE_MENTIONS -> NotificationType.MentionsOnly
                    else -> NotificationType.All
                }

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
                    value = NotificationType.Mute,
                    title = GetString(R.string.notificationsMute),
                    iconRes = R.drawable.ic_volume_off,
                    selected = selectedOption is NotificationType.Mute
                ),
            )
        )

        var muteOptions: OptionsCardData<Long>? = null

        // add the mute options if necessary
        if(selectedOption is NotificationType.Mute) {
            muteOptions = OptionsCardData(
                    title = GetString(R.string.disappearingMessagesTimer),
                    options = muteDurations.map {
                        RadioOption(
                            value = it,
                            title =
                                if(it == durationForever) GetString(R.string.forever)
                                else GetString(
                                    LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(
                                        context,
                                        it.milliseconds
                                    )
                            ),
                            selected = false //todo UCS calculate this properly
                        )
                    }
                )
        }

        _uiState.update {
            UiState(
                notificationTypes = defaultOptions,
                muteTypes = muteOptions,
                enableButton = shouldEnableSetButton()
            )
        }
    }

    private fun shouldEnableSetButton(): Boolean {
        return true //todo UCS implement this properly
        /*return when{
            selectedOption is NotificationType.Mute -> selectedMuteDuration != currentMuteDuration
            else -> selectedOption != currentOption
        }*/
    }

    override fun onSetClick() = viewModelScope.launch {
        //todo UCS implement
    }

    override fun setValue(value: Any) {
        when(value){
            is Long -> selectedMuteDuration = value

            is NotificationType -> selectedOption = value
        }

        updateState()
    }

    private fun unmute(context: Context) {
        val conversation = thread ?: return
       // recipientDatabase.setMuted(conversation, 0)
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
        val notificationTypes: OptionsCardData<NotificationType>? = null,
        val muteTypes: OptionsCardData<Long>? = null,
        val enableButton: Boolean = false,
    )

    sealed interface NotificationType {
        data object All: NotificationType
        data object MentionsOnly: NotificationType
        data object Mute: NotificationType
    }

    private val muteDurations = listOf(
        durationForever,
        TimeUnit.HOURS.toMillis(1),
        TimeUnit.HOURS.toMillis(2),
        TimeUnit.DAYS.toMillis(1),
        TimeUnit.DAYS.toMillis(7),
    )

/*    sealed class MuteDuration(val duration: Long) {
        data object Forever: MuteDuration(Long.MAX_VALUE)
        data object OneHour: MuteDuration(TimeUnit.HOURS.toMillis(1))
        data object TwoHours: MuteDuration(TimeUnit.HOURS.toMillis(2))
        data object OneDay: MuteDuration(TimeUnit.DAYS.toMillis(1))
        data object OneWeek: MuteDuration(TimeUnit.DAYS.toMillis(7))
    }*/

    @AssistedFactory
    interface Factory {
        fun create(threadId: Long): NotificationSettingsViewModel
    }
}