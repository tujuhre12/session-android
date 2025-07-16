package org.thoughtcrime.securesms.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.widget.Toast
import com.squareup.phrase.Phrase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2.Companion.THREAD_ID
import org.thoughtcrime.securesms.pro.ProStatusManager
import javax.inject.Inject

/**
 * Helper class to get the information required for the user profile modal
 */
class UserProfileUtils @AssistedInject constructor(
    @ApplicationContext private val context: Context,
    @Assisted private val recipient: Recipient,
    @Assisted private val threadId: Long,
    @Assisted private val scope: CoroutineScope,
    private val avatarUtils: AvatarUtils,
    private val configFactory: ConfigFactoryProtocol,
    private val proStatusManager: ProStatusManager
) {
    private val _userProfileModalData: MutableStateFlow<UserProfileModalData?> = MutableStateFlow(null)
    val userProfileModalData: StateFlow<UserProfileModalData?>
        get() = _userProfileModalData

    init {
        Log.d("UserProfileUtils", "init")

        scope.launch {
            _userProfileModalData.update { getDefaultProfileData() }
        }
    }

    private suspend fun getDefaultProfileData(): UserProfileModalData {
        val address = recipient.address.toString()
        val configContact = configFactory.withUserConfigs { configs ->
            configs.contacts.get(address)
        }

        val isBlinded = IdPrefix.fromValue(address)?.isBlinded() == true

        val isResolvedBlinded = false //todo UPM implement

        // we apply the display rules from figma (the numbers being the number of characters):
        // - if the address is blinded (with a tooltip), display as 10...10
        // - if the address is a resolved blinded id (with a tooltip) 23 / 23 / 20
        // - for the rest: non blinded address which aren't from a community, break in 33 / 33
        val (displayAddress, tooltipText) = when {
            isBlinded -> {
                "${address.take(10)}...${address.takeLast(10)}" to
                        context.getString(R.string.tooltipBlindedIdCommunities)
            }

            isResolvedBlinded -> {
                "${address.substring(0, 23)}\n${address.substring(23, 46)}\n${address.substring(46)}" to
                        Phrase.from(context, R.string.tooltipAccountIdVisible)
                            .put(NAME_KEY, recipient.name)
                            .format()
            }

            else -> {
                "${address.take(33)}\n${address.takeLast(33)}" to null
            }
        }

        return UserProfileModalData(
            name = recipient.name,
            subtitle = if(configContact?.nickname?.isNotEmpty() == true) "(${configContact.name})" else null,
            avatarUIData = avatarUtils.getUIDataFromAccountId(accountId = address),
            isPro = proStatusManager.isUserPro(recipient.address),
            currentUserPro = proStatusManager.isCurrentUserPro(),
            rawAddress = address,
            displayAddress = displayAddress,
            threadId = threadId,
            isBlinded = isBlinded,
            tooltipText = tooltipText,
            enableMessage = !isBlinded || !recipient.blocksCommunityMessageRequests,
            expandedAvatar = false,
            showQR = false,
            showProCTA = false
        )

    }

    fun onCommand(command: UserProfileModalCommands){
        when(command){
            UserProfileModalCommands.ShowProCTA -> {
                _userProfileModalData.update { _userProfileModalData.value?.copy(showProCTA = true) }
            }

            UserProfileModalCommands.HideSessionProCTA -> {
                _userProfileModalData.update { _userProfileModalData.value?.copy(showProCTA = false) }
            }

            UserProfileModalCommands.ToggleQR -> {
                    _userProfileModalData.update {
                        _userProfileModalData.value?.let{
                            it.copy(showQR = !it.showQR)
                        }
                    }
            }

            UserProfileModalCommands.ToggleAvatarExpand -> {
                _userProfileModalData.update {
                    _userProfileModalData.value?.let{
                        it.copy(expandedAvatar = !it.expandedAvatar)
                    }
                }
            }

            UserProfileModalCommands.CopyAccountId -> {
                //todo we do this in a few places, should reuse the logic
                val accountID = recipient.address.toString()
                val clip = ClipData.newPlainText("Account ID", accountID)
                val manager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(clip)
                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    @AssistedFactory
    interface UserProfileUtilsFactory {
        fun create(recipient: Recipient, threadId: Long, scope: CoroutineScope): UserProfileUtils
    }

}

data class UserProfileModalData(
    val name: String,
    val subtitle: String?,
    val isPro: Boolean,
    val currentUserPro: Boolean,
    val rawAddress: String,
    val displayAddress: String,
    val threadId: Long,
    val isBlinded: Boolean,
    val tooltipText: CharSequence?,
    val enableMessage: Boolean,
    val expandedAvatar: Boolean,
    val showQR: Boolean,
    val avatarUIData: AvatarUIData,
    val showProCTA: Boolean
)

sealed interface UserProfileModalCommands {
    object ShowProCTA: UserProfileModalCommands
    object HideSessionProCTA: UserProfileModalCommands
    object CopyAccountId: UserProfileModalCommands
    object ToggleAvatarExpand: UserProfileModalCommands
    object ToggleQR: UserProfileModalCommands
}