package org.thoughtcrime.securesms.conversation.expiration

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.SSKEnvironment.MessageExpirationManagerProtocol
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.ui.GetString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes


@OptIn(ExperimentalCoroutinesApi::class)
class ExpirationSettingsViewModel(
    private val threadId: Long,
    private val textSecurePreferences: TextSecurePreferences,
    private val messageExpirationManager: MessageExpirationManagerProtocol,
    private val threadDb: ThreadDatabase,
    private val groupDb: GroupDatabase,
    private val storage: Storage
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    val uiState = _state.map {
        UiState(
            cards = listOf(
                CardModel(GetString(R.string.activity_expiration_settings_delete_type), typeOptions()),
                CardModel(GetString(R.string.activity_expiration_settings_timer), timeOptions(it))
            )
        )
    }

    private var expirationConfig: ExpirationConfiguration? = null

    init {
        // SETUP
        viewModelScope.launch {
            expirationConfig = storage.getExpirationConfiguration(threadId)
            val expiryMode = expirationConfig?.expiryMode ?: ExpiryMode.NONE
            val recipient = threadDb.getRecipientForThreadId(threadId)
            val groupInfo = recipient?.takeIf { it.isClosedGroupRecipient }
                ?.run { address.toGroupString().let(groupDb::getGroup).orNull() }
            _state.update { state ->
                state.copy(
                    isSelfAdmin = groupInfo == null || groupInfo.admins.any{ it.serialize() == textSecurePreferences.getLocalNumber() },
                    recipient = recipient,
                    expiryType = expiryMode.type,
                    expiryMode = expiryMode
                )
            }


        }
//        selectedExpirationType.mapLatest {
//            when (it) {
//                is ExpiryMode.Legacy, is ExpiryMode.AfterSend -> afterSendOptions
//                is ExpiryMode.AfterRead -> afterReadOptions
//                else -> emptyList()
//            }
//        }.onEach { options ->
//            val enabled = _uiState.value.isSelfAdmin || recipient.value?.isClosedGroupRecipient == true
//            _expirationTimerOptions.value = options.let {
//                if (ExpirationConfiguration.isNewConfigEnabled && recipient.value?.run { isLocalNumber || isClosedGroupRecipient } == true) it.drop(1) else it
//            }.map { it.copy(enabled = enabled) }
//        }.launchIn(viewModelScope)
    }

    fun typeOption(
        type: ExpiryType,
        @StringRes title: Int,
        @StringRes subtitle: Int,
//        @StringRes contentDescription: Int
    ) = OptionModel(GetString(title), GetString(subtitle)) { setType(type) }

    private fun typeOptions() = listOf(
        typeOption(ExpiryType.NONE, R.string.expiration_off, R.string.AccessibilityId_disable_disappearing_messages),
        typeOption(ExpiryType.LEGACY, R.string.expiration_type_disappear_legacy, R.string.expiration_type_disappear_legacy_description),
        typeOption(ExpiryType.AFTER_READ, R.string.expiration_type_disappear_after_read, R.string.expiration_type_disappear_after_read_description),
        typeOption(ExpiryType.AFTER_SEND, R.string.expiration_type_disappear_after_send, R.string.expiration_type_disappear_after_send_description),
    )

    private fun setType(type: ExpiryType) {
        _state.update { it.copy(expiryType = type, expiryMode = type.mode(0)) }
    }

    private fun setTime(seconds: Long) {
        _state.update { it.copy(
            expiryMode = it.expiryType.mode(seconds)
        ) }
    }

    private fun setMode(mode: ExpiryMode) {
        _state.update { it.copy(
            expiryMode = mode
        ) }
    }

    fun timeOption(seconds: Long, @StringRes id: Int) = OptionModel(GetString(id), selected = false, onClick = { setTime(seconds) })
    fun timeOption(seconds: Long, title: String, subtitle: String) = OptionModel(GetString(title), GetString(subtitle), selected = false, onClick = { setTime(seconds) })

//    private fun timeOptions(state: State) = timeOptions(state.types.isEmpty(), state.expiryType == ExpiryType.AFTER_SEND)
    private fun timeOptions(state: State) = noteToSelfOptions()

    val afterReadTimes = listOf(12.hours, 1.days, 7.days, 14.days)
    val afterSendTimes = listOf(5.minutes, 1.hours) + afterReadTimes

    private fun noteToSelfOptions() = listOfNotNull(
        typeOption(ExpiryType.NONE, R.string.arrays__off, R.string.arrays__off),
        noteToSelfOption(1.minutes, subtitle = "for testing purposes").takeIf { BuildConfig.DEBUG },
    ) + afterSendTimes.map(::noteToSelfOption)

    private fun noteToSelfOption(
        duration: Duration,
        title: GetString = GetString { ExpirationUtil.getExpirationDisplayValue(it, duration.inWholeSeconds.toInt()) },
        subtitle: String? = null
    ) = OptionModel(
        title = title,
        subtitle = subtitle?.let(::GetString),
        selected = false,
        onClick = { setMode(ExpiryMode.AfterSend(duration.inWholeSeconds)) }
    )

    fun onSetClick() = viewModelScope.launch {
        val state = _state.value
//        val expiryMode = _selectedExpirationTimer.value?.value ?: ExpiryMode.NONE
//        val typeValue = expiryMode.let {
//            if (it is ExpiryMode.Legacy) ExpiryMode.AfterRead(it.expirySeconds)
//            else it
//        }
        val address = state.recipient?.address
//        if (address == null || expirationConfig?.expiryMode == typeValue) {
//            _state.update {
//                it.copy(settingsSaved = false)
//            }
//            return@launch
//        }

//        val expiryChangeTimestampMs = SnodeAPI.nowWithOffset
//        storage.setExpirationConfiguration(ExpirationConfiguration(threadId, typeValue, expiryChangeTimestampMs))
//
//        val message = ExpirationTimerUpdate(typeValue.expirySeconds.toInt())
//        message.sender = textSecurePreferences.getLocalNumber()
//        message.recipient = address.serialize()
//        message.sentTimestamp = expiryChangeTimestampMs
//        messageExpirationManager.setExpirationTimer(message, typeValue)
//
//        MessageSender.send(message, address)
//        state.update {
//            it.copy(settingsSaved = true)
//        }
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(threadId: Long): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        private val textSecurePreferences: TextSecurePreferences,
        private val messageExpirationManager: MessageExpirationManagerProtocol,
        private val threadDb: ThreadDatabase,
        private val groupDb: GroupDatabase,
        private val storage: Storage
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T = ExpirationSettingsViewModel(
            threadId,
            textSecurePreferences,
            messageExpirationManager,
            threadDb,
            groupDb,
            storage
        ) as T
    }
}

data class State(
    val isSelfAdmin: Boolean = false,
    val settingsSaved: Boolean? = null,
    val recipient: Recipient? = null,
    val expiryType: ExpiryType = ExpiryType.NONE,
    val expiryMode: ExpiryMode? = null,
    val types: List<ExpiryType> = emptyList()
) {
    val isSelf = recipient?.isLocalNumber == true
}

data class UiState(
    val cards: List<CardModel> = emptyList()
)

data class CardModel(
    val title: GetString,
    val options: List<OptionModel>
)

data class OptionModel(
    val title: GetString,
    val subtitle: GetString? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit = {}
)

enum class ExpiryType(val mode: (Long) -> ExpiryMode) {
    NONE({ ExpiryMode.NONE }),
    LEGACY(ExpiryMode::Legacy),
    AFTER_SEND(ExpiryMode::AfterSend),
    AFTER_READ(ExpiryMode::AfterRead)
}

private val ExpiryMode.type: ExpiryType get() = when(this) {
        is ExpiryMode.Legacy -> ExpiryType.LEGACY
        is ExpiryMode.AfterSend -> ExpiryType.AFTER_SEND
        is ExpiryMode.AfterRead -> ExpiryType.AFTER_READ
        else -> ExpiryType.NONE
    }
