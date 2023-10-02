package org.thoughtcrime.securesms.groups

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.MainCoroutineRule
import org.thoughtcrime.securesms.database.Storage

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class ClosedGroupViewTests {

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var taskRule = InstantTaskExecutorRule()

    @Mock lateinit var textSecurePreferences: TextSecurePreferences
    @Mock lateinit var storage: Storage

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

    private fun createViewModel() = CreateGroupViewModel(textSecurePreferences, storage)

}