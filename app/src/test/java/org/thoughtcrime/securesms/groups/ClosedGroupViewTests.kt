package org.thoughtcrime.securesms.groups

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import network.loki.messenger.libsession_util.util.Sodium
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.SessionId
import org.thoughtcrime.securesms.MainCoroutineRule
import org.thoughtcrime.securesms.database.ConfigDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.dependencies.ConfigFactory

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class ClosedGroupViewTests {

    companion object {
        private const val OTHER_ID = "051000000000000000000000000000000000000000000000000000000000000000"
    }

    private val seed =
        Hex.fromStringCondensed("0123456789abcdef0123456789abcdef00000000000000000000000000000000")
    private val keyPair = Sodium.ed25519KeyPair(seed)
    private val userSessionId = SessionId(IdPrefix.STANDARD, Sodium.ed25519PkToCurve25519(keyPair.pubKey))

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var taskRule = InstantTaskExecutorRule()

    @Mock lateinit var textSecurePreferences: TextSecurePreferences
    lateinit var storage: Storage

    @Before
    fun setup() {
        whenever(textSecurePreferences.getLocalNumber()).thenReturn(userSessionId.hexString())
        val context = mock<Context>()
        val emptyDb = mock<ConfigDatabase> { db ->
            whenever(db.retrieveConfigAndHashes(any(), any())).thenReturn(byteArrayOf())
        }
        val overridenStorage = Storage(mock(), mock(), ConfigFactory(context, emptyDb) {
            keyPair.secretKey to userSessionId.hexString()
        }, mock())
        storage = spy(overridenStorage) { storage ->
            whenever(storage.createNewGroup(any(), any(), any())).thenCallRealMethod()
        }
    }

    @Test
    fun `Should error on empty name`() {
        val viewModel = createViewModel()
        val state = CreateGroupState(
            groupName = "",
            groupDescription = "",
            members = emptySet()
        )
        viewModel.tryCreateGroup(state)
        assertNotNull(viewModel.viewState.value?.error)
    }

    @Test
    fun `Should error on empty members`() {
        val viewModel = createViewModel()
        val state = CreateGroupState(
            groupName = "group",
            groupDescription = "anything",
            members = emptySet()
        )
        viewModel.tryCreateGroup(state)
        assertNotNull(viewModel.viewState.value?.error)
    }

    @Test
    fun `Should work with valid name and members`() {
        val viewModel = createViewModel()
        val state = CreateGroupState(
            groupName = "group",
            groupDescription = "",
            members = emptySet()
        )
        assertNotNull(viewModel.tryCreateGroup(state))
    }

    private fun createViewModel() = CreateGroupViewModel(textSecurePreferences, storage)

}