package org.thoughtcrime.securesms.conversation.expiration

import android.app.Application
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.MainCoroutineRule
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.ui.GetString
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private const val THREAD_ID = 1L


@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class ExpirationSettingsViewModelTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock lateinit var application: Application
    @Mock lateinit var textSecurePreferences: TextSecurePreferences
    @Mock lateinit var messageExpirationManager: SSKEnvironment.MessageExpirationManagerProtocol
    @Mock lateinit var threadDb: ThreadDatabase
    @Mock lateinit var groupDb: GroupDatabase
    @Mock lateinit var storage: Storage
    @Mock lateinit var recipient: Recipient

    @Test
    fun `1-1 conversation, 12 hours after send, new config`() = runTest {
        val time = 12.hours
        val someAddress = Address.fromSerialized("05---SOME---ADDRESS")
        val config = newExpirationConfiguration(time)

        whenever(threadDb.getRecipientForThreadId(Mockito.anyLong())).thenReturn(recipient)
        whenever(storage.getExpirationConfiguration(Mockito.anyLong())).thenReturn(config)
        whenever(textSecurePreferences.getLocalNumber()).thenReturn("05---LOCAL---ADDRESS")
        whenever(recipient.isClosedGroupRecipient).thenReturn(false)
        whenever(recipient.address).thenReturn(someAddress)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertThat(
            viewModel.state.value
        ).isEqualTo(
            State(
                isGroup = false,
                isSelfAdmin = true,
                address = someAddress,
                isNoteToSelf = false,
                expiryMode = ExpiryMode.AfterSend(12.hours.inWholeSeconds),
                isNewConfigEnabled = true,
                persistedMode = ExpiryMode.AfterSend(12.hours.inWholeSeconds),
                showDebugOptions = false
            )
        )

        val newTypeOption = TypeOptionCreator(time)
        val newTimeOption = TimeOptionCreator(ExpiryType.AFTER_SEND)

        assertThat(
            viewModel.uiState.value
        ).isEqualTo(
            UiState(
                showGroupFooter = false,
                CardModel(
                    R.string.activity_expiration_settings_delete_type,
                    newTypeOption(ExpiryType.NONE),
                    newTypeOption(ExpiryType.AFTER_READ),
                    newTypeOption(ExpiryType.AFTER_SEND, selected = true)
                ),
                CardModel(
                    GetString(R.string.activity_expiration_settings_timer),
                    newTimeOption(duration = 12.hours, selected = true),
                    newTimeOption(duration = 1.days),
                    newTimeOption(duration = 7.days),
                    newTimeOption(duration = 14.days)
                )
            )
        )
    }

    @Test
    fun `1-1 conversation, 1 day after send, new config`() = runTest {
        val time = 1.days
        val someAddress = Address.fromSerialized("05---SOME---ADDRESS")
        val config = newExpirationConfiguration(time)

        whenever(threadDb.getRecipientForThreadId(Mockito.anyLong())).thenReturn(recipient)
        whenever(storage.getExpirationConfiguration(Mockito.anyLong())).thenReturn(config)
        whenever(textSecurePreferences.getLocalNumber()).thenReturn("05---LOCAL---ADDRESS")
        whenever(recipient.isClosedGroupRecipient).thenReturn(false)
        whenever(recipient.address).thenReturn(someAddress)

        val viewModel = createViewModel()

        advanceUntilIdle()

        assertThat(
            viewModel.state.value
        ).isEqualTo(
            State(
                isGroup = false,
                isSelfAdmin = true,
                address = someAddress,
                isNoteToSelf = false,
                expiryMode = ExpiryMode.AfterSend(1.days.inWholeSeconds),
                isNewConfigEnabled = true,
                persistedMode = ExpiryMode.AfterSend(1.days.inWholeSeconds),
                showDebugOptions = false
            )
        )

        val newTypeOption = TypeOptionCreator(time)
        val newTimeOption = TimeOptionCreator(ExpiryType.AFTER_SEND)

        assertThat(
            viewModel.uiState.value
        ).isEqualTo(
            UiState(
                showGroupFooter = false,
                CardModel(
                    R.string.activity_expiration_settings_delete_type,
                    newTypeOption(ExpiryType.NONE),
                    typeOption(12.hours, ExpiryType.AFTER_READ),
                    newTypeOption(ExpiryType.AFTER_SEND, selected = true)
                ),
                CardModel(
                    GetString(R.string.activity_expiration_settings_timer),
                    newTimeOption(duration = 12.hours),
                    newTimeOption(duration = 1.days, selected = true),
                    newTimeOption(duration = 7.days),
                    newTimeOption(duration = 14.days)
                )
            )
        )
    }

    private fun newExpirationConfiguration(time: Duration) = ExpirationConfiguration(
        threadId = THREAD_ID,
        expiryMode = ExpiryMode.AfterSend(time.inWholeSeconds),
        updatedTimestampMs = 0
    )

    private class TypeOptionCreator(private val time: Duration) {
        operator fun invoke(type: ExpiryType, selected: Boolean = false, enabled: Boolean = true) =
            typeOption(time, type, selected, enabled)
    }

    private class TimeOptionCreator(private val type: ExpiryType) {
        operator fun invoke(duration: Duration, selected: Boolean = false, enabled: Boolean = true) = OptionModel(
            value = type.mode(duration),
            title = GetString(duration),
            enabled = enabled,
            selected = selected
        )
    }

    private fun createViewModel(isNewConfigEnabled: Boolean = true) = ExpirationSettingsViewModel(
        THREAD_ID,
        application,
        textSecurePreferences,
        messageExpirationManager,
        threadDb,
        groupDb,
        storage,
        isNewConfigEnabled,
        showDebugOptions = false
    )
}

fun typeOption(time: Duration, type: ExpiryType, selected: Boolean = false, enabled: Boolean = true) =
    OptionModel(
        type.mode(time),
        GetString(type.title),
        type.subtitle?.let(::GetString),
        GetString(type.contentDescription),
        selected = selected,
        enabled = enabled
    )
