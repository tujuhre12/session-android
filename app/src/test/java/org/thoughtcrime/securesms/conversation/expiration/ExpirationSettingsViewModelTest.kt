package org.thoughtcrime.securesms.conversation.expiration

import android.app.Application
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.MainCoroutineRule
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.ui.GetString
import kotlin.time.Duration.Companion.hours
import network.loki.messenger.R
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class ExpirationSettingsViewModelTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private val application: Application = mock(Application::class.java)
    private val textSecurePreferences: TextSecurePreferences = mock(TextSecurePreferences::class.java)
    private val messageExpirationManager: SSKEnvironment.MessageExpirationManagerProtocol = mock(SSKEnvironment.MessageExpirationManagerProtocol::class.java)
    private val threadDb: ThreadDatabase = mock(ThreadDatabase::class.java)
    private val groupDb: GroupDatabase = mock(GroupDatabase::class.java)
    private val storage: Storage = mock(Storage::class.java)
    private val recipient = mock(Recipient::class.java)

    @Test
    fun `UI should show a list of times and an Off option`() = runTest {
        val threadId = 1L

        val expirationConfig = ExpirationConfiguration(
            threadId = threadId,
            expiryMode = ExpiryMode.AfterSend(12.hours.inWholeSeconds),
            updatedTimestampMs = 0
        )
        whenever(threadDb.getRecipientForThreadId(Mockito.anyLong())).thenReturn(recipient)
        whenever(storage.getExpirationConfiguration(Mockito.anyLong())).thenReturn(expirationConfig)
        whenever(textSecurePreferences.getLocalNumber()).thenReturn("05---LOCAL---ADDRESS")

        val userAddress = Address.fromSerialized(textSecurePreferences.getLocalNumber()!!)
        val someAddress = Address.fromSerialized("05---SOME---ADDRESS")

        whenever(recipient.isClosedGroupRecipient).thenReturn(false)
        whenever(recipient.address).thenReturn(someAddress)

        whenever(groupDb.getGroup(Mockito.anyString())).thenReturn(Optional.absent())

        val viewModel = createViewModel()

        advanceUntilIdle()

        MatcherAssert.assertThat(
            viewModel.state.value,
            CoreMatchers.equalTo(
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
        )

        val uiState = viewModel.uiState.value

        MatcherAssert.assertThat(
            uiState.cards.map { it.title },
            CoreMatchers.equalTo(
                listOf(
                    R.string.activity_expiration_settings_delete_type,
                    R.string.activity_expiration_settings_timer
                ).map(::GetString)
            )
        )

        MatcherAssert.assertThat(
            uiState.cards[0].options.map { it.title },
            CoreMatchers.equalTo(
                listOf(
                    R.string.expiration_off,
                    R.string.expiration_type_disappear_after_read,
                    R.string.expiration_type_disappear_after_send,
                ).map(::GetString)
            )
        )

        MatcherAssert.assertThat(
            uiState.cards[1].options.map { it.title },
            CoreMatchers.equalTo(
                listOf(
                    12.hours,
                    1.days,
                    7.days,
                    14.days,
                ).map(::GetString)
            )
        )

        MatcherAssert.assertThat(
            uiState.showGroupFooter,
            CoreMatchers.equalTo(false)
        )
    }

    private fun createViewModel(isNewConfigEnabled: Boolean = true) = ExpirationSettingsViewModel(
        1L,
        application,
        textSecurePreferences,
        messageExpirationManager,
        threadDb,
        groupDb,
        storage,
        isNewConfigEnabled,
        false
    )
}
