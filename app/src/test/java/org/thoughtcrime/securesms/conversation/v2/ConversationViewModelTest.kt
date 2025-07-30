package org.thoughtcrime.securesms.conversation.v2

import android.app.Application
import android.content.ContentResolver
import app.cash.copper.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import network.loki.messenger.libsession_util.util.KeyPair
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.session.libsession.messaging.groups.LegacyGroupDeprecationManager
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.BaseViewModelTest
import org.thoughtcrime.securesms.MainCoroutineRule
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.repository.ConversationRepository
import org.thoughtcrime.securesms.util.AvatarUIData
import org.thoughtcrime.securesms.util.AvatarUtils
import org.thoughtcrime.securesms.util.RecipientChangeSource
import java.time.ZonedDateTime

class ConversationViewModelTest: BaseViewModelTest() {

    @get:Rule
    val rule = MainCoroutineRule()

    private val repository = mock<ConversationRepository>()
    private val storage = mock<Storage>()


    private val testContentResolver = mock<ContentResolver>()

    private val application = mock<Application> {
        on { getString(any()) } doReturn ""
        on { contentResolver } doReturn testContentResolver
        on { getString(any()) } doReturn ""
    }

    private val avatarUtils = mock<AvatarUtils> {
        onBlocking { getUIDataFromRecipient(anyOrNull()) }
            .doReturn(AvatarUIData(elements = emptyList()))
    }

    private fun createViewModel(recipient: Recipient): ConversationViewModel {
        ConversationViewModel(
            repository = repository,
            storage = storage,
            messageDataProvider = mock(),
            groupDb = mock(),
            threadDb = mock(),
            textSecurePreferences = mock(),
            lokiMessageDb = mock(),
            application = application,
            reactionDb = mock(),
            configFactory = mock(),
            groupManagerV2 = mock(),
            callManager = mock(),
            legacyGroupDeprecationManager = mock {
                on { deprecationState } doReturn MutableStateFlow(LegacyGroupDeprecationManager.DeprecationState.DEPRECATED)
                on { deprecatedTime } doReturn MutableStateFlow(ZonedDateTime.now())
            },
            expiredGroupManager = mock(),
            avatarUtils = avatarUtils,
            lokiAPIDb = mock(),
            dateUtils = mock(),
            proStatusManager = mock(),
            upmFactory = mock(),
            lokiThreadDatabase = mock(),
            blindMappingRepository = mock(),
            address = recipient.address,
            recipientRepository = mock {
                on { getRecipientSyncOrEmpty(recipient.address) } doReturn recipient
            },
            createThreadIfNotExists = true,
            openGroupManager = mock(),
        )
    }

    @Before
    fun setUp() {
    }

    @Test
    fun `should save draft message`() = runBlockingTest {
        val draft = "Hi there"

        val viewModel = createViewModel(recipient = Recipient(
            address =
        ))

        viewModel.saveDraft(draft)

        // The above is an async process to wait 100ms to give it a chance to complete
        verify(repository, Mockito.timeout(100).times(1)).saveDraft(threadId, draft)
    }

    @Test
    fun `should retrieve draft message`() = runBlockingTest {
        val draft = "Hi there"
        whenever(repository.getDraft(anyLong())).thenReturn(draft)

        val result = viewModel.getDraft()

        verify(repository).getDraft(threadId)
        assertThat(result, equalTo(draft))
    }

    @Test
    fun `should unblock contact recipient`() = runBlockingTest {
        whenever(recipient.isContactRecipient).thenReturn(true)

        viewModel.unblock()

        verify(repository).setBlocked(recipient, false)
    }

    @Test
    fun `should emit error message on ban user failure`() = runBlockingTest {
        val error = Throwable()
        whenever(repository.banUser(anyLong(), any())).thenReturn(Result.failure(error))
        whenever(application.getString(any())).thenReturn("Ban failed")

        viewModel.banUser(recipient)

        assertThat(viewModel.uiState.first().uiMessages.first().message, equalTo("Ban failed"))
    }

    @Test
    fun `should emit a message on ban user success`() = runBlockingTest {
        whenever(repository.banUser(anyLong(), any())).thenReturn(Result.success(Unit))
        whenever(application.getString(any())).thenReturn("User banned")

        viewModel.banUser(recipient)

        assertThat(
            viewModel.uiState.first().uiMessages.first().message,
            equalTo("User banned")
        )
    }

    @Test
    fun `should emit error message on ban user and delete all failure`() = runBlockingTest {
        val error = Throwable()
        whenever(repository.banAndDeleteAll(anyLong(), any())).thenReturn(Result.failure(error))
        whenever(application.getString(any())).thenReturn("Ban failed")

        viewModel.banAndDeleteAll(messageRecord)

        assertThat(viewModel.uiState.first().uiMessages.first().message, equalTo("Ban failed"))
    }

    @Test
    fun `should emit a message on ban user and delete all success`() = runBlockingTest {
        whenever(repository.banAndDeleteAll(anyLong(), any())).thenReturn(Result.success(Unit))
        whenever(application.getString(any())).thenReturn("User banned")

        viewModel.banAndDeleteAll(messageRecord)

        assertThat(
            viewModel.uiState.first().uiMessages.first().message,
            equalTo("User banned")
        )
    }

    @Test
    fun `should remove shown message`() = runBlockingTest {
        // Given that a message is generated
        whenever(repository.banUser(anyLong(), any())).thenReturn(Result.success(Unit))
        whenever(application.getString(any())).thenReturn("User banned")

        viewModel.banUser(recipient)
        assertThat(viewModel.uiState.value.uiMessages.size, equalTo(1))
        // When the message is shown
        viewModel.messageShown(viewModel.uiState.first().uiMessages.first().id)
        // Then it should be removed
        assertThat(viewModel.uiState.value.uiMessages.size, equalTo(0))
    }

    @Test
    fun `open group recipient should have no blinded recipient`() = runBlockingTest {
        whenever(recipient.isCommunityRecipient).thenReturn(true)
        whenever(recipient.isCommunityOutboxRecipient).thenReturn(false)
        whenever(recipient.isCommunityInboxRecipient).thenReturn(false)
        assertThat(viewModel.blindedRecipient, nullValue())
    }
}