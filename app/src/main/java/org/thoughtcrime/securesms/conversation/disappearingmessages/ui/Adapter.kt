package org.thoughtcrime.securesms.conversation.disappearingmessages.ui

import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.thoughtcrime.securesms.conversation.disappearingmessages.ExpiryType
import org.thoughtcrime.securesms.conversation.disappearingmessages.State
import org.thoughtcrime.securesms.ui.GetString
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun State.toUiState() = UiState(
    cards = listOfNotNull(
        typeOptions()?.let { ExpiryOptionsCard(GetString(R.string.activity_disappearing_messages_delete_type), it) },
        timeOptions()?.let { ExpiryOptionsCard(GetString(R.string.activity_disappearing_messages_timer), it) }
    ),
    showGroupFooter = isGroup && isNewConfigEnabled,
    showSetButton = isSelfAdmin
)

private fun State.typeOptions(): List<ExpiryRadioOption>? = if (typeOptionsHidden) null else {
    buildList {
        add(offTypeOption())
        if (!isNewConfigEnabled) add(legacyTypeOption())
        if (!isGroup) add(afterReadTypeOption())
        add(afterSendTypeOption())
    }
}

private fun State.timeOptions(): List<ExpiryRadioOption>? {
    val type = takeUnless {
        it.typeOptionsHidden
    }?.expiryType ?: if (isNewConfigEnabled) ExpiryType.AFTER_SEND else ExpiryType.LEGACY

    return when (type) {
        ExpiryType.AFTER_READ -> afterReadModes
        ExpiryType.AFTER_SEND -> afterSendModes
        ExpiryType.LEGACY -> legacyModes
        else -> null
    }?.map { timeOption(it) }?.let {
        buildList {
            if (typeOptionsHidden) add(offTypeOption())
            addAll(debugOptions())
            addAll(it)
        }
    }
}

private fun State.offTypeOption() = typeOption(ExpiryType.NONE)
private fun State.legacyTypeOption() = typeOption(ExpiryType.LEGACY)
private fun State.afterReadTypeOption() = newTypeOption(ExpiryType.AFTER_READ)
private fun State.afterSendTypeOption() = newTypeOption(ExpiryType.AFTER_SEND)
private fun State.newTypeOption(type: ExpiryType) = typeOption(type, isNewConfigEnabled && isSelfAdmin)

private fun State.typeOption(
    type: ExpiryType,
    enabled: Boolean = isSelfAdmin,
) = ExpiryRadioOption(
    value = type.defaultMode(persistedMode),
    title = GetString(type.title),
    subtitle = type.subtitle?.let(::GetString),
    contentDescription = GetString(type.contentDescription),
    selected = expiryType == type,
    enabled = enabled
)

private fun debugTimes(isDebug: Boolean) = if (isDebug) listOf(10.seconds, 1.minutes) else emptyList()
private fun debugModes(isDebug: Boolean, type: ExpiryType) =
    debugTimes(isDebug).map { type.mode(it.inWholeSeconds) }
private fun State.debugOptions(): List<ExpiryRadioOption> =
    debugModes(showDebugOptions, expiryType.takeIf { it == ExpiryType.AFTER_READ } ?: ExpiryType.AFTER_SEND)
        .map { timeOption(it, subtitle = GetString("for testing purposes")) }

private val afterSendTimes = listOf(12.hours, 1.days, 7.days, 14.days)

private val afterSendModes = afterSendTimes.map { it.inWholeSeconds }.map(ExpiryMode::AfterSend)
private val legacyModes = afterSendTimes.map { it.inWholeSeconds }.map(ExpiryMode::Legacy)

private val afterReadTimes = buildList {
    add(5.minutes)
    add(1.hours)
    addAll(afterSendTimes)
}
private val afterReadModes = afterReadTimes.map { it.inWholeSeconds }.map(ExpiryMode::AfterRead)

private fun State.timeOption(
    mode: ExpiryMode,
    title: GetString = GetString(mode.duration),
    subtitle: GetString? = null,
) = ExpiryRadioOption(
    value = mode,
    title = title,
    subtitle = subtitle,
    contentDescription = title,
    selected = expiryMode == mode,
    enabled = isTimeOptionsEnabled
)
