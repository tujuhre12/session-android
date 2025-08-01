package org.thoughtcrime.securesms.conversation.v2

import android.app.Application
import android.content.ContentResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.session.libsession.messaging.groups.LegacyGroupDeprecationManager
import org.session.libsession.utilities.Address.Companion.toAddress
import org.session.libsession.utilities.recipients.BasicRecipient
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.BaseViewModelTest
import org.thoughtcrime.securesms.MainCoroutineRule
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.repository.ConversationRepository
import org.thoughtcrime.securesms.util.AvatarUIData
import org.thoughtcrime.securesms.util.AvatarUtils
import java.time.ZonedDateTime

private val STANDARD_ADDRESS = "0538e63512fd78c04d45b83ec7f0f3d593f60276ce535d1160eb589a00cca7db59".toAddress()


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

    private lateinit var messageRecord: MessageRecord

    private val standardRecipient = Recipient(
        address = STANDARD_ADDRESS,
        basic = BasicRecipient.Contact(
            name = "Test User",
            nickname = "Test User",
            avatar = null,
            approved = true,
            approvedMe = true,
            blocked = false,
            expiryMode = ExpiryMode.NONE,
            1,
        )
    )

    private val threadId = 12345L

    private fun createViewModel(recipient: Recipient): ConversationViewModel {
        return ConversationViewModel(
            repository = repository,
            storage = storage,
            groupDb = mock(),
            threadDb = mock {
                on { getOrCreateThreadIdFor(recipient.address) } doReturn threadId
                on { getThreadIdIfExistsFor(recipient.address) } doReturn threadId
            },
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
                on { getRecipientSync(recipient.address) } doReturn recipient
                on { observeRecipient(recipient.address) } doAnswer {
                    flowOf(recipient)
                }
            },
            createThreadIfNotExists = true,
            openGroupManager = mock(),
            attachmentDownloadHandlerFactory = mock(),
        )
    }

    @Before
    fun setUp() {
        messageRecord = mock { record ->
            whenever(record.individualRecipient).thenReturn(standardRecipient)
        }
    }

    @Test
    fun `should save draft message`() = runBlockingTest {
        val draft = "Hi there"

        val viewModel = createViewModel(recipient = standardRecipient)

        viewModel.saveDraft(draft)

        // The above is an async process to wait 100ms to give it a chance to complete
        verify(repository, Mockito.timeout(100).times(1)).saveDraft(threadId, draft)
    }

    @Test
    fun `should retrieve draft message`() = runBlockingTest {
        val draft = "Hi there"
        whenever(repository.getDraft(anyLong())).thenReturn(draft)

        val viewModel = createViewModel(recipient = standardRecipient)

        val result = viewModel.getDraft()

        verify(repository).getDraft(threadId)
        assertThat(result, equalTo(draft))
    }

    @Test
    fun `should unblock contact recipient`() = runBlockingTest {
        val viewModel = createViewModel(recipient = standardRecipient)
        viewModel.unblock()

        verify(repository).setBlocked(standardRecipient.address, false)
    }

    @Test
    fun `should emit error message on ban user failure`() = runBlockingTest {
        val error = Throwable()
        val viewModel = createViewModel(recipient = standardRecipient)
        whenever(repository.banUser(anyLong(), any())).thenReturn(Result.failure(error))
        whenever(application.getString(any())).thenReturn("Ban failed")

        viewModel.banUser(standardRecipient.address)

        assertThat(viewModel.uiMessages.value.first().message, equalTo("Ban failed"))
    }

    @Test
    fun `should emit a message on ban user success`() = runBlockingTest {
        val viewModel = createViewModel(recipient = standardRecipient)
        whenever(repository.banUser(anyLong(), any())).thenReturn(Result.success(Unit))
        whenever(application.getString(any())).thenReturn("User banned")

        viewModel.banUser(standardRecipient.address)

        assertThat(
            viewModel.uiMessages.value.first().message,
            equalTo("User banned")
        )
    }

    @Test
    fun `should emit error message on ban user and delete all failure`() = runBlockingTest {
        val viewModel = createViewModel(recipient = standardRecipient)
        val error = Throwable()
        whenever(repository.banAndDeleteAll(anyLong(), any())).thenReturn(Result.failure(error))
        whenever(application.getString(any())).thenReturn("Ban failed")

        viewModel.banAndDeleteAll(messageRecord)

        assertThat(viewModel.uiMessages.value.first().message, equalTo("Ban failed"))
    }

    @Test
    fun `should emit a message on ban user and delete all success`() = runBlockingTest {
        val viewModel = createViewModel(recipient = standardRecipient)
        whenever(repository.banAndDeleteAll(anyLong(), any())).thenReturn(Result.success(Unit))
        whenever(application.getString(any())).thenReturn("User banned")

        viewModel.banAndDeleteAll(messageRecord)

        assertThat(
            viewModel.uiMessages.value.first().message,
            equalTo("User banned")
        )
    }

    @Test
    fun `should remove shown message`() = runBlockingTest {
        val viewModel = createViewModel(recipient = standardRecipient)
        // Given that a message is generated
        whenever(repository.banUser(anyLong(), any())).thenReturn(Result.success(Unit))
        whenever(application.getString(any())).thenReturn("User banned")

        viewModel.banUser(standardRecipient.address)
        assertThat(viewModel.uiMessages.value.size, equalTo(1))
        // When the message is shown
        viewModel.messageShown(viewModel.uiMessages.value.first().id)
        // Then it should be removed
        assertThat(viewModel.uiMessages.value.size, equalTo(0))
    }
}