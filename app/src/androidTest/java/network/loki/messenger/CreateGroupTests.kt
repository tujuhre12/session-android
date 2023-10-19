package network.loki.messenger

import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.groups.CreateGroup
import org.thoughtcrime.securesms.groups.CreateGroupFragment
import org.thoughtcrime.securesms.groups.CreateGroupState
import org.thoughtcrime.securesms.ui.AppTheme

@RunWith(AndroidJUnit4::class)
@SmallTest
class CreateGroupTests {

    @get:Rule
    val composeTest = createComposeRule()

    @Test
    fun testNavigateToCreateGroup() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ApplicationContext
        // Accessibility IDs
        val nameDesc = application.getString(R.string.AccessibilityId_closed_group_edit_group_name)
        val buttonDesc = application.getString(R.string.AccessibilityId_create_closed_group_create_button)

        lateinit var postedGroup: CreateGroupState
        var backPressed = false
        var closePressed = false

        composeTest.setContent {
            AppTheme {
                CreateGroup(
                    viewState = CreateGroupFragment.ViewState.DEFAULT,
                    createGroupState = CreateGroupState("", "", emptySet()),
                    onCreate = { submitted ->
                        postedGroup = submitted
                    },
                    onBack = { backPressed = true },
                    onClose = { closePressed = true })
            }
        }

        with(composeTest) {
            onNode(hasContentDescriptionExactly(nameDesc)).performTextInput("Name")
            onNode(hasContentDescriptionExactly(buttonDesc)).performClick()
        }

        assertThat(postedGroup.groupName, equalTo("Name"))
        assertThat(backPressed, equalTo(false))
        assertThat(closePressed, equalTo(false))

    }

    @Test
    fun testFailToCreate() {

    }

    @Test
    fun testBackButton() {

    }

    @Test
    fun testCloseButton() {

    }


}