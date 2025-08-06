package org.thoughtcrime.securesms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY
import org.session.libsignal.utilities.AccountId
import org.thoughtcrime.securesms.groups.ContactItem
import org.thoughtcrime.securesms.groups.compose.MemberItem
import org.thoughtcrime.securesms.groups.compose.multiSelectMemberList
import org.thoughtcrime.securesms.ui.SearchBar
import org.thoughtcrime.securesms.ui.components.BackAppBar
import org.thoughtcrime.securesms.ui.qaTag
import org.thoughtcrime.securesms.ui.theme.LocalColors
import org.thoughtcrime.securesms.ui.theme.LocalDimensions
import org.thoughtcrime.securesms.ui.theme.LocalType
import org.thoughtcrime.securesms.ui.theme.PreviewTheme
import org.thoughtcrime.securesms.ui.theme.primaryBlue
import org.thoughtcrime.securesms.util.AvatarUIData
import org.thoughtcrime.securesms.util.AvatarUIElement


@Composable
fun ShareScreen(
    viewModel: ShareViewModel,
    onBack: () -> Unit,
) {
    ShareList(
        contacts = viewModel.contacts.collectAsState().value,
        onContactItemClicked = viewModel::onContactItemClicked,
        searchQuery = viewModel.searchQuery.collectAsState().value,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSearchQueryClear = {viewModel.onSearchQueryChanged("") },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareList(
    contacts: List<ContactItem>,
    onContactItemClicked: (accountId: AccountId) -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSearchQueryClear: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            BackAppBar(
                title = Phrase.from(LocalContext.current, R.string.shareToSession)
                    .put(APP_NAME_KEY, stringResource(R.string.app_name))
                    .format().toString(),
                onBack = onBack,
            )
        },
    ) { paddings ->
        Column(
            modifier = Modifier
                .padding(top = paddings.calculateTopPadding())
                .consumeWindowInsets(paddings),
        ) {
            Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))

            SearchBar(
                query = searchQuery,
                onValueChanged = onSearchQueryChanged,
                onClear = onSearchQueryClear,
                placeholder = stringResource(R.string.searchContacts),
                modifier = Modifier
                    .padding(horizontal = LocalDimensions.current.smallSpacing)
                    .qaTag(R.string.AccessibilityId_groupNameSearch),
                backgroundColor = LocalColors.current.backgroundSecondary,
            )

            val scrollState = rememberLazyListState()

            Spacer(modifier = Modifier.height(LocalDimensions.current.smallSpacing))

            if(contacts.isEmpty() && searchQuery.isEmpty()){
                Text(
                    text = stringResource(id = R.string.conversationsNone),
                    modifier = Modifier.padding(top = LocalDimensions.current.spacing)
                        .align(Alignment.CenterHorizontally),
                    style = LocalType.current.base.copy(color = LocalColors.current.textSecondary)
                )
            } else {
                LazyColumn(
                    state = scrollState,
                    contentPadding = PaddingValues(bottom = paddings.calculateBottomPadding()),
                ) {

                    items(contacts) { contacts ->
                        // Each member's view
                        MemberItem(
                            accountId = contacts.accountID,
                            onClick = onContactItemClicked,
                            title = contacts.name,
                            showProBadge = contacts.showProBadge,
                            showAsAdmin = false,
                            avatarUIData = contacts.avatarUIData
                        )
                    }
                }
            }
        }

    }
}

@Preview
@Composable
private fun PreviewSelectContacts() {
    val random = "05abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234"
    val contacts = List(20) {
        ContactItem(
            accountID = AccountId(random),
            name = "User $it",
            selected = it % 3 == 0,
            showProBadge = true,
            avatarUIData = AvatarUIData(
                listOf(
                    AvatarUIElement(
                        name = "TOTO",
                        color = primaryBlue
                    )
                )
            ),
        )
    }

    PreviewTheme {
        ShareList(
            contacts = contacts,
            onContactItemClicked = {},
            searchQuery = "",
            onSearchQueryChanged = {},
            onSearchQueryClear = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PreviewSelectEmptyContacts() {
    val contacts = emptyList<ContactItem>()

    PreviewTheme {
        ShareList(
            contacts = contacts,
            onContactItemClicked = {},
            searchQuery = "",
            onSearchQueryChanged = {},
            onSearchQueryClear = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PreviewSelectEmptyContactsWithSearch() {
    val contacts = emptyList<ContactItem>()

    PreviewTheme {
        ShareList(
            contacts = contacts,
            onContactItemClicked = {},
            searchQuery = "Test",
            onSearchQueryChanged = {},
            onSearchQueryClear = {},
            onBack = {},
        )
    }
}

