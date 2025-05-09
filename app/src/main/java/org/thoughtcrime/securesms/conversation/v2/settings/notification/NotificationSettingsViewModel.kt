package org.thoughtcrime.securesms.conversation.v2.settings.notification

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.phrase.Phrase
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
import org.session.libsession.utilities.StringSubstitutionConstants.GROUP_NAME_KEY
import org.session.libsession.utilities.StringSubstitutionConstants.TIME_LARGE_KEY
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.settings.ConversationSettingsNavigator
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase.NOTIFY_TYPE_ALL
import org.thoughtcrime.securesms.database.RecipientDatabase.NOTIFY_TYPE_MENTIONS
import org.thoughtcrime.securesms.database.RecipientDatabase.NOTIFY_TYPE_NONE
import org.thoughtcrime.securesms.home.HomeActivity
import org.thoughtcrime.securesms.repository.ConversationRepository
import org.thoughtcrime.securesms.ui.Callbacks
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.ui.OptionsCardData
import org.thoughtcrime.securesms.ui.RadioOption
import org.thoughtcrime.securesms.ui.getSubbedString
import org.thoughtcrime.securesms.util.DateUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel(assistedFactory = NotificationSettingsViewModel.Factory::class)
class NotificationSettingsViewModel @AssistedInject constructor(
    @Assisted private val threadId: Long,
    @ApplicationContext private val context: Context,
    private val recipientDatabase: RecipientDatabase,
    private val repository: ConversationRepository,
    private val navigator: ConversationSettingsNavigator,
) : ViewModel(), Callbacks<Any> {
    private var thread: Recipient? = null

    private val durationForever: Long = Long.MAX_VALUE

    // the options the user is currently using
    private var currentOption: NotificationType = NotificationType.All
    private var currentMutedUntil: Long? = null

    // the option selected on this screen
    private var selectedOption: NotificationType = NotificationType.All
    private var selectedMuteDuration: Long? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        // update data when we have a recipient and update when there are changes from the thread or recipient
        viewModelScope.launch(Dispatchers.Default) {
            repository.recipientUpdateFlow(threadId).collect {
                thread = it

                // update the user's current choice of notification
                currentMutedUntil = it?.mutedUntil
                val hasMutedUntil = currentMutedUntil != null && currentMutedUntil!! > 0L

                currentOption = when{
                    hasMutedUntil -> NotificationType.Mute
                    it?.notifyType == NOTIFY_TYPE_MENTIONS -> NotificationType.MentionsOnly
                    else -> NotificationType.All
                }

                // set our default selection to those
                selectedOption = currentOption
                // default selection for mute is either our custom "Muted Until" or "Forever" if nothing is pre picked
                selectedMuteDuration = if(hasMutedUntil) currentMutedUntil else durationForever

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
            val muteRadioOptions = mutableListOf<RadioOption<Long>>()

            // if the user is currently "muting until", and that muting is not forever,
            // then add a new option that specifies how much longer the mute  is on for
            if(currentMutedUntil != null && currentMutedUntil!! > 0L &&
                currentMutedUntil!! < System.currentTimeMillis() + TimeUnit.DAYS.toMillis(14)){ // more than two weeks from now means forever
                muteRadioOptions.add(
                    RadioOption(
                        value = currentMutedUntil!!,
                        title = GetString("Muted Until: ${formatTime(currentMutedUntil!!)}"), //todo UCS need the crowdin string
                        selected = selectedMuteDuration == currentMutedUntil
                    )
                )
            }

            // add the regular options
            muteRadioOptions.addAll(
                muteDurations.map {
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
                        selected = selectedMuteDuration == it
                    )
                }
            )

            muteOptions = OptionsCardData(
                    title = GetString(R.string.disappearingMessagesTimer),
                    options = muteRadioOptions
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

    //todo UCS update with date utils functions once we have the code from the network page
    private fun formatTime(timestamp: Long): String{
        val formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yy")

        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }

    private fun shouldEnableSetButton(): Boolean {
        return when{
            selectedOption is NotificationType.Mute -> selectedMuteDuration != currentMutedUntil
            else -> selectedOption != currentOption
        }
    }

    override fun onSetClick() = viewModelScope.launch {
        when(selectedOption){
            is NotificationType.All, is NotificationType.MentionsOnly -> {
                unmute()
                setNotifyType(selectedOption.notifyType)

             }
            else -> {
                val muteDuration = selectedMuteDuration ?: return@launch

                mute(if(muteDuration == durationForever) muteDuration else System.currentTimeMillis() + muteDuration)

                // also show a toast in this case
                val toastString = if(muteDuration == durationForever) {
                    context.getString(R.string.notificationsMuted)
                } else {
                    context.getSubbedString(
                        R.string.notificationsMutedFor,
                        TIME_LARGE_KEY to LocalisedTimeUtil.getDurationWithSingleLargestTimeUnit(
                            context,
                            muteDuration.milliseconds
                        )
                    )
                }

                Toast.makeText(context, toastString, Toast.LENGTH_LONG).show()
            }
        }

        // navigate back to the conversation settings
        navigator.navigateUp()
    }

    override fun setValue(value: Any) {
        when(value){
            is Long -> selectedMuteDuration = value

            is NotificationType -> selectedOption = value
        }

        updateState()
    }

    private fun unmute() {
        val conversation = thread ?: return
        recipientDatabase.setMuted(conversation, 0)
    }

    private fun mute(until: Long) {
        val conversation = thread ?: return
        recipientDatabase.setMuted(conversation, until)
    }

    private fun setNotifyType(notifyType: Int) {
        val conversation = thread ?: return
        recipientDatabase.setNotifyType(conversation, notifyType)
    }

    data class UiState(
        val notificationTypes: OptionsCardData<NotificationType>? = null,
        val muteTypes: OptionsCardData<Long>? = null,
        val enableButton: Boolean = false,
    )

    sealed class NotificationType(val notifyType: Int) {
        data object All: NotificationType(NOTIFY_TYPE_ALL)
        data object MentionsOnly: NotificationType(NOTIFY_TYPE_MENTIONS)
        data object Mute: NotificationType(NOTIFY_TYPE_NONE)
    }

    private val muteDurations = listOf(
        durationForever,
        TimeUnit.HOURS.toMillis(1),
        TimeUnit.HOURS.toMillis(2),
        TimeUnit.DAYS.toMillis(1),
        TimeUnit.DAYS.toMillis(7),
    )

    @AssistedFactory
    interface Factory {
        fun create(threadId: Long): NotificationSettingsViewModel
    }
}