package org.thoughtcrime.securesms.conversation.expiration

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.SSKEnvironment.MessageExpirationManagerProtocol
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.conversation.expiration.ExpiryType
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.ui.GetString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

data class Event(
    val saveSuccess: Boolean
)

data class State(
    val isGroup: Boolean = false,
    val isSelfAdmin: Boolean = true,
    val address: Address? = null,
    val isNoteToSelf: Boolean = false,
    val expiryMode: ExpiryMode? = ExpiryMode.NONE,
    val isNewConfigEnabled: Boolean = true,
    val callbacks: Callbacks = NoOpCallbacks
) {
    val subtitle get() = when {
        isGroup || isNoteToSelf -> GetString(R.string.activity_expiration_settings_subtitle_sent)
        else -> GetString(R.string.activity_expiration_settings_subtitle)
    }
    val duration get() = expiryMode?.duration
    val expiryType get() = expiryMode?.type

    val isTimeOptionsEnabled = isNoteToSelf || isSelfAdmin && (isNewConfigEnabled || expiryType == ExpiryType.LEGACY)
}

interface Callbacks {
    fun onSetClick(): Any = Unit
    fun setType(type: ExpiryType) {}
    fun setTime(seconds: Long) {}
    fun setMode(mode: ExpiryMode) {}
}

object NoOpCallbacks: Callbacks

class ExpirationSettingsViewModel(
    private val threadId: Long,
    private val textSecurePreferences: TextSecurePreferences,
    private val messageExpirationManager: MessageExpirationManagerProtocol,
    private val threadDb: ThreadDatabase,
    private val groupDb: GroupDatabase,
    private val storage: Storage,
    isNewConfigEnabled: Boolean
) : ViewModel(), Callbacks {

    private val _event = Channel<Event>()
    val event = _event.receiveAsFlow()

    private val _state = MutableStateFlow(State(
        isNewConfigEnabled = isNewConfigEnabled,
        callbacks = this@ExpirationSettingsViewModel
    ))
    val state = _state.asStateFlow()

    val uiState = _state.map { UiState(it) }

    private var expirationConfig: ExpirationConfiguration? = null

    init {
        viewModelScope.launch {
            expirationConfig = storage.getExpirationConfiguration(threadId)
            val expiryMode = expirationConfig?.expiryMode ?: ExpiryMode.NONE
            val recipient = threadDb.getRecipientForThreadId(threadId)
            val groupInfo = recipient?.takeIf { it.isClosedGroupRecipient }
                ?.run { address.toGroupString().let(groupDb::getGroup).orNull() }

            _state.update { state ->
                state.copy(
                    isGroup = groupInfo != null,
                    isSelfAdmin = groupInfo == null || groupInfo.admins.any{ it.serialize() == textSecurePreferences.getLocalNumber() },
                    expiryMode = expiryMode
                )
            }
        }
    }

    override fun setType(type: ExpiryType) {
        _state.update { it.copy(expiryMode = type.mode(0)) }
    }

    override fun setTime(seconds: Long) {
        _state.update { it.copy(
            expiryMode = it.expiryType?.mode(seconds)
        ) }
    }

    override fun setMode(mode: ExpiryMode) {
        _state.update { it.copy(
            expiryMode = mode
        ) }
    }

    override fun onSetClick() = viewModelScope.launch {
        val state = _state.value
        val expiryMode = state.expiryMode ?: ExpiryMode.NONE
        val typeValue = expiryMode.let {
            if (it is ExpiryMode.Legacy) ExpiryMode.AfterRead(it.expirySeconds)
            else it
        }
        val address = state.address
        if (address == null || expirationConfig?.expiryMode == typeValue) {
            _event.send(Event(false))
            return@launch
        }

        val expiryChangeTimestampMs = SnodeAPI.nowWithOffset
        storage.setExpirationConfiguration(ExpirationConfiguration(threadId, typeValue, expiryChangeTimestampMs))

        val message = ExpirationTimerUpdate(typeValue.expirySeconds.toInt())
        message.sender = textSecurePreferences.getLocalNumber()
        message.recipient = address.serialize()
        message.sentTimestamp = expiryChangeTimestampMs
        messageExpirationManager.setExpirationTimer(message, typeValue)

        MessageSender.send(message, address)
        _event.send(Event(true))
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
            storage,
            ExpirationConfiguration.isNewConfigEnabled
        ) as T
    }
}

data class UiState(
    val cards: List<CardModel> = emptyList(),
    val showGroupFooter: Boolean = false,
    val callbacks: Callbacks = NoOpCallbacks
) {
    constructor(state: State): this(
        cards = listOf(
            CardModel(GetString(R.string.activity_expiration_settings_delete_type), typeOptions(state)),
            CardModel(GetString(R.string.activity_expiration_settings_timer), timeOptions(state))
        ),
        showGroupFooter = state.isGroup && state.isNewConfigEnabled,
        callbacks = state.callbacks
    )
}

data class CardModel(
    val title: GetString,
    val options: List<OptionModel>
)

private fun typeOptions(state: State) =
    if (state.isNoteToSelf || (state.isGroup && state.isNewConfigEnabled)) emptyList()
    else listOfNotNull(
        typeOption(
            ExpiryType.NONE,
            state,
            R.string.expiration_off,
            contentDescription = R.string.AccessibilityId_disable_disappearing_messages,
            enabled = state.isSelfAdmin
        ),
        if (!state.isNewConfigEnabled) typeOption(
            ExpiryType.LEGACY,
            state,
            R.string.expiration_type_disappear_legacy,
            contentDescription = R.string.expiration_type_disappear_legacy_description,
            enabled = state.isSelfAdmin
        ) else null,
        if (!state.isGroup) typeOption(
            ExpiryType.AFTER_READ,
            state,
            R.string.expiration_type_disappear_after_read,
            R.string.expiration_type_disappear_after_read_description,
            contentDescription = R.string.expiration_type_disappear_after_read_description,
            enabled = state.isNewConfigEnabled && state.isSelfAdmin
        ) else null,
        typeOption(
            ExpiryType.AFTER_SEND,
            state,
            R.string.expiration_type_disappear_after_send,
            R.string.expiration_type_disappear_after_read_description,
            contentDescription = R.string.expiration_type_disappear_after_send_description,
            enabled = state.isNewConfigEnabled && state.isSelfAdmin
        ),
    )

private fun typeOption(
    type: ExpiryType,
    state: State,
    @StringRes title: Int,
    @StringRes subtitle: Int? = null,
    @StringRes contentDescription: Int = title,
    enabled: Boolean = true,
    onClick: () -> Unit = { state.callbacks.setType(type) }
) = OptionModel(
    GetString(title),
    subtitle?.let(::GetString),
    selected = state.expiryType == type,
    enabled = enabled,
    onClick = onClick
)

private fun timeOptions(state: State): List<OptionModel> =
    if (state.isNoteToSelf || (state.isGroup && state.isNewConfigEnabled)) timeOptionsOnly(state)
    else when (state.expiryMode) {
        is ExpiryMode.Legacy -> afterReadTimes
        is ExpiryMode.AfterRead -> afterReadTimes
        is ExpiryMode.AfterSend -> afterSendTimes
        else -> emptyList()
    }.map { timeOption(it, state) }

private val afterSendTimes = listOf(12.hours, 1.days, 7.days, 14.days)
private val afterReadTimes = listOf(5.minutes, 1.hours) + afterSendTimes

private fun timeOptionsOnly(state: State) = listOfNotNull(
    typeOption(ExpiryType.NONE, state, R.string.arrays__off, enabled = state.isSelfAdmin),
    timeOption(1.minutes, state, subtitle = GetString("for testing purposes")).takeIf { BuildConfig.DEBUG },
) + afterSendTimes.map { timeOption(it, state) }

private fun timeOption(
    duration: Duration,
    state: State,
    title: GetString = GetString { ExpirationUtil.getExpirationDisplayValue(it, duration.inWholeSeconds.toInt()) },
    subtitle: GetString? = null
) = OptionModel(
    title = title,
    subtitle = subtitle,
    selected = state.expiryMode?.duration == duration,
    enabled = state.isTimeOptionsEnabled
) { state.callbacks.setTime(duration.inWholeSeconds) }

data class OptionModel(
    val title: GetString,
    val subtitle: GetString? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {}
)

enum class ExpiryType(private val createMode: (Long) -> ExpiryMode) {
    NONE({ ExpiryMode.NONE }),
    LEGACY(ExpiryMode::Legacy),
    AFTER_SEND(ExpiryMode::AfterSend),
    AFTER_READ(ExpiryMode::AfterRead);

    fun mode(seconds: Long) = createMode(seconds)
}

private val ExpiryMode.type: ExpiryType get() = when(this) {
    is ExpiryMode.Legacy -> ExpiryType.LEGACY
    is ExpiryMode.AfterSend -> ExpiryType.AFTER_SEND
    is ExpiryMode.AfterRead -> ExpiryType.AFTER_READ
    else -> ExpiryType.NONE
}
