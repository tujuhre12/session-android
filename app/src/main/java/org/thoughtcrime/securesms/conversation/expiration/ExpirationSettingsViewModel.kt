package org.thoughtcrime.securesms.conversation.expiration

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.ui.GetString
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class Event {
    SUCCESS, FAIL
}

data class State(
    val isGroup: Boolean = false,
    val isSelfAdmin: Boolean = true,
    val address: Address? = null,
    val isNoteToSelf: Boolean = false,
    val expiryMode: ExpiryMode? = ExpiryMode.NONE,
    val isNewConfigEnabled: Boolean = true,
    val persistedMode: ExpiryMode? = null,
    val showDebugOptions: Boolean = false
) {
    val subtitle get() = when {
        isGroup || isNoteToSelf -> GetString(R.string.activity_expiration_settings_subtitle_sent)
        else -> GetString(R.string.activity_expiration_settings_subtitle)
    }

    val modeOptionsHidden get() = isNoteToSelf || (isGroup && isNewConfigEnabled)

    val duration get() = expiryMode?.duration
    val expiryType get() = expiryMode?.type
    val expiryTypeOrNone: ExpiryType = expiryType ?: ExpiryType.NONE

    val isTimeOptionsEnabled = isNoteToSelf || isSelfAdmin && (isNewConfigEnabled || expiryType == ExpiryType.LEGACY)
}

interface Callbacks {
    fun onSetClick(): Any?
    fun setMode(mode: ExpiryMode)
}

object NoOpCallbacks: Callbacks {
    override fun onSetClick() {}
    override fun setMode(mode: ExpiryMode) {}
}

class ExpirationSettingsViewModel(
    private val threadId: Long,
    private val application: Application,
    private val textSecurePreferences: TextSecurePreferences,
    private val messageExpirationManager: MessageExpirationManagerProtocol,
    private val threadDb: ThreadDatabase,
    private val groupDb: GroupDatabase,
    private val storage: Storage,
    isNewConfigEnabled: Boolean,
    showDebugOptions: Boolean
) : AndroidViewModel(application), Callbacks {

    private val _event = Channel<Event>()
    val event = _event.receiveAsFlow()

    private val _state = MutableStateFlow(
        State(
            isNewConfigEnabled = isNewConfigEnabled,
            showDebugOptions = showDebugOptions
        )
    )
    val state = _state.asStateFlow()

    val uiState = _state
        .map(::UiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    private var expirationConfig: ExpirationConfiguration? = null

    init {
        viewModelScope.launch {
            expirationConfig = storage.getExpirationConfiguration(threadId)
            val expiryMode = expirationConfig?.expiryMode ?: ExpiryMode.NONE
            val recipient = threadDb.getRecipientForThreadId(threadId)
            val groupRecord = recipient?.takeIf { it.isClosedGroupRecipient }
                ?.run { address.toGroupString().let(groupDb::getGroup).orNull() }

            _state.update { state ->
                state.copy(
                    address = recipient?.address,
                    isGroup = groupRecord != null,
                    isNoteToSelf = recipient?.address?.serialize() == textSecurePreferences.getLocalNumber(),
                    isSelfAdmin = groupRecord == null || groupRecord.admins.any{ it.serialize() == textSecurePreferences.getLocalNumber() },
                    expiryMode = expiryMode,
                    persistedMode = expiryMode
                )
            }
        }
    }

    override fun setMode(mode: ExpiryMode) {
        _state.update { it.copy(
            expiryMode = mode
        ) }
    }

    override fun onSetClick() = viewModelScope.launch {
        val state = _state.value
        val mode = state.expiryMode.let {
            when {
                it !is ExpiryMode.Legacy -> it
                state.isGroup -> ExpiryMode.AfterSend(it.expirySeconds)
                else -> ExpiryMode.AfterRead(it.expirySeconds)
            } ?: ExpiryMode.NONE
        }
        val address = state.address
        if (address == null) {
            _event.send(Event.FAIL)
            return@launch
        }

        val expiryChangeTimestampMs = SnodeAPI.nowWithOffset
        storage.setExpirationConfiguration(ExpirationConfiguration(threadId, mode, expiryChangeTimestampMs))

        val message = ExpirationTimerUpdate(mode.expirySeconds.toInt()).apply {
            sender = textSecurePreferences.getLocalNumber()
            recipient = address.serialize()
            sentTimestamp = expiryChangeTimestampMs
        }
        messageExpirationManager.setExpirationTimer(message, mode)
        MessageSender.send(message, address)

        ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(application)

        _event.send(Event.SUCCESS)
    }

    @dagger.assisted.AssistedFactory
    interface AssistedFactory {
        fun create(threadId: Long): Factory
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @AssistedInject constructor(
        @Assisted private val threadId: Long,
        private val application: Application,
        private val textSecurePreferences: TextSecurePreferences,
        private val messageExpirationManager: MessageExpirationManagerProtocol,
        private val threadDb: ThreadDatabase,
        private val groupDb: GroupDatabase,
        private val storage: Storage
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T = ExpirationSettingsViewModel(
            threadId,
            application,
            textSecurePreferences,
            messageExpirationManager,
            threadDb,
            groupDb,
            storage,
            ExpirationConfiguration.isNewConfigEnabled,
            BuildConfig.DEBUG
        ) as T
    }
}

private fun ExpiryType.defaultMode(persistedMode: ExpiryMode?) = when(this) {
    persistedMode?.type -> persistedMode
    ExpiryType.AFTER_READ -> mode(12.hours)
    else -> mode(1.days)
}

data class UiState(
    val cards: List<CardModel> = emptyList(),
    val showGroupFooter: Boolean = false
) {
    constructor(state: State): this(
        cards = listOfNotNull(
            typeOptions(state)?.let { CardModel(GetString(R.string.activity_expiration_settings_delete_type), it) },
            timeOptions(state)?.let { CardModel(GetString(R.string.activity_expiration_settings_timer), it) }
        ),
        showGroupFooter = state.isGroup && state.isNewConfigEnabled
    )
}

data class CardModel(
    val title: GetString,
    val options: List<OptionModel>
)

private fun typeOptions(state: State) =
    state.takeUnless { it.modeOptionsHidden }?.run {
        listOfNotNull(
            typeOption(
                ExpiryMode.NONE,
                state,
                R.string.expiration_off,
                contentDescription = R.string.AccessibilityId_disable_disappearing_messages,
                enabled = isSelfAdmin
            ),
            if (!isNewConfigEnabled) typeOption(
                ExpiryType.LEGACY.defaultMode(persistedMode),
                state,
                R.string.expiration_type_disappear_legacy,
                contentDescription = R.string.expiration_type_disappear_legacy_description,
                enabled = isSelfAdmin
            ) else null,
            if (!isGroup) typeOption(
                ExpiryType.AFTER_READ.defaultMode(persistedMode),
                state,
                R.string.expiration_type_disappear_after_read,
                R.string.expiration_type_disappear_after_read_description,
                contentDescription = R.string.expiration_type_disappear_after_read_description,
                enabled = isNewConfigEnabled && isSelfAdmin
            ) else null,
            typeOption(
                ExpiryType.AFTER_SEND.defaultMode(persistedMode),
                state,
                R.string.expiration_type_disappear_after_send,
                R.string.expiration_type_disappear_after_read_description,
                contentDescription = R.string.expiration_type_disappear_after_send_description,
                enabled = isNewConfigEnabled && isSelfAdmin
            )
        )
    }

private fun typeOption(
    mode: ExpiryMode,
    state: State,
    @StringRes title: Int,
    @StringRes subtitle: Int? = null,
    @StringRes contentDescription: Int = title,
    enabled: Boolean = true,
) = OptionModel(
    value = mode,
    title = GetString(title),
    subtitle = subtitle?.let(::GetString),
    contentDescription = GetString(contentDescription),
    selected = state.expiryType == mode.type,
    enabled = enabled
)

private fun debugTimes(isDebug: Boolean) = if (isDebug) listOf(10.seconds, 1.minutes) else emptyList()
private fun debugModes(isDebug: Boolean, type: ExpiryType) =
    debugTimes(isDebug).map { type.mode(it.inWholeSeconds) }
private fun debugOptions(state: State): List<OptionModel> =
    debugModes(state.showDebugOptions, state.expiryType.takeIf { it == ExpiryType.AFTER_READ } ?: ExpiryType.AFTER_SEND)
        .map { timeOption(it, state, subtitle = GetString("for testing purposes")) }

val defaultTimes = listOf(12.hours, 1.days, 7.days, 14.days)

val afterSendTimes = defaultTimes
val afterSendModes = afterSendTimes.map { it.inWholeSeconds }.map(ExpiryMode::AfterSend)
fun afterSendOptions(state: State) = afterSendModes.map { timeOption(it, state) }

val afterReadTimes = buildList {
    add(5.minutes)
    add(1.hours)
    addAll(defaultTimes)
}
val afterReadModes = afterReadTimes.map { it.inWholeSeconds }.map(ExpiryMode::AfterRead)
fun afterReadOptions(state: State) = afterReadModes.map { timeOption(it, state) }

private fun timeOptions(state: State): List<OptionModel>? =
    if (state.modeOptionsHidden) timeOptionsAfterSend(state)
    else when (state.expiryMode) {
        is ExpiryMode.Legacy, is ExpiryMode.AfterRead -> debugOptions(state) + afterReadOptions(state)
        is ExpiryMode.AfterSend -> debugOptions(state) + afterSendOptions(state)
        else -> null
    }

private fun timeOptionsAfterSend(state: State) = listOf(
    typeOption(ExpiryMode.NONE, state, R.string.expiration_off, enabled = state.isSelfAdmin),
) + debugOptions(state) + afterSendModes.map { timeOption(it, state) }

private fun timeOption(
    mode: ExpiryMode,
    state: State,
    title: GetString = GetString(mode.duration, ExpirationUtil::getExpirationDisplayValue),
    subtitle: GetString? = null,
) = OptionModel(
    value = mode,
    title = title,
    subtitle = subtitle,
    selected = state.expiryMode == mode,
    enabled = state.isTimeOptionsEnabled
)

data class OptionModel(
    val value: ExpiryMode,
    val title: GetString,
    val subtitle: GetString? = null,
    val contentDescription: GetString = title,
    val selected: Boolean = false,
    val enabled: Boolean = true,
)

enum class ExpiryType(private val createMode: (Long) -> ExpiryMode) {
    NONE({ ExpiryMode.NONE }),
    LEGACY(ExpiryMode::Legacy),
    AFTER_SEND(ExpiryMode::AfterSend),
    AFTER_READ(ExpiryMode::AfterRead);

    fun mode(seconds: Long) = if (seconds != 0L) createMode(seconds) else ExpiryMode.NONE
    fun mode(duration: Duration) = mode(duration.inWholeSeconds)
}

val ExpiryMode.type: ExpiryType get() = when(this) {
    is ExpiryMode.Legacy -> ExpiryType.LEGACY
    is ExpiryMode.AfterSend -> ExpiryType.AFTER_SEND
    is ExpiryMode.AfterRead -> ExpiryType.AFTER_READ
    else -> ExpiryType.NONE
}
