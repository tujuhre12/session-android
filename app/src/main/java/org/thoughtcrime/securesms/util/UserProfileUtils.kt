package org.thoughtcrime.securesms.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.widget.Toast
import com.squareup.phrase.Phrase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.database.StorageProtocol
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.toAddress
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsession.utilities.StringSubstitutionConstants.NAME_KEY
import org.session.libsession.utilities.recipients.BasicRecipient
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.recipients.displayName
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.database.BlindMappingRepository
import org.thoughtcrime.securesms.database.RecipientRepository
import org.thoughtcrime.securesms.pro.ProStatusManager

/**
 * Helper class to get the information required for the user profile modal
 */
class UserProfileUtils @AssistedInject constructor(
    @param:ApplicationContext private val context: Context,
    @Assisted private val userAddress: Address,
    @Assisted private val threadId: Long,
    @Assisted private val scope: CoroutineScope,
    private val avatarUtils: AvatarUtils,
    private val proStatusManager: ProStatusManager,
    private val storage: StorageProtocol,
    private val blindedIdMappingRepository: BlindMappingRepository,
    private val recipientRepository: RecipientRepository,
) {
    private val _userProfileModalData: MutableStateFlow<UserProfileModalData?> = MutableStateFlow(null)
    val userProfileModalData: StateFlow<UserProfileModalData?>
        get() = _userProfileModalData

    init {
        Log.d("UserProfileUtils", "init")

        scope.launch(Dispatchers.Default) {
            _userProfileModalData.update { getDefaultProfileData() }
        }
    }

    private suspend fun getDefaultProfileData(): UserProfileModalData {
        // if we have a blinded address, check if it can be resolved
        val recipient = (if (userAddress.isBlinded) storage.getOpenGroup(threadId) else null)
            ?.let { openGroup ->
                blindedIdMappingRepository.getMapping(
                    serverUrl = openGroup.server,
                    blindedAddress = AccountId(userAddress.address)
                )
            }
            ?.let { recipientRepository.getRecipient(it.toAddress()) }
            ?: recipientRepository.getRecipientOrEmpty(userAddress)


        // we apply the display rules from figma (the numbers being the number of characters):
        // - if the address is blinded (with a tooltip), display as 10...10
        // - if the address is a resolved blinded id (with a tooltip) 23 / 23 / 20
        // - for the rest: non blinded address which aren't from a community, break in 33 / 33
        val (displayAddress, tooltipText) = when {
            recipient.address.isBlinded -> {
                "${userAddress.address.take(10)}...${userAddress.address.takeLast(10)}" to
                        context.getString(R.string.tooltipBlindedIdCommunities)
            }

            userAddress.isBlinded -> {
                "${recipient.address.address.substring(0, 23)}\n${recipient.address.address.substring(23, 46)}\n${recipient.address.address.substring(46)}" to
                        Phrase.from(context, R.string.tooltipAccountIdVisible)
                            .put(NAME_KEY, recipient.displayName())
                            .format()
            }

            else -> {
                "${userAddress.address.take(33)}\n${userAddress.address.takeLast(33)}" to null
            }
        }

        return UserProfileModalData(
            name = recipient.displayName(),
            subtitle = (recipient.basic as? BasicRecipient.Contact)?.nickname?.takeIf { it.isNotBlank() }?.let { "($it)" },
            avatarUIData = avatarUtils.getUIDataFromAccountId(accountId = recipient.address.address),
            isPro = proStatusManager.isUserPro(recipient.address),
            currentUserPro = proStatusManager.isCurrentUserPro(),
            rawAddress = recipient.address.address,
            displayAddress = displayAddress,
            threadId = threadId,
            isBlinded = recipient.address.isBlinded,
            tooltipText = tooltipText,
            enableMessage = !recipient.address.isBlinded || recipient.acceptsCommunityMessageRequests,
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
                val accountID = userAddress.address
                val clip = ClipData.newPlainText("Account ID", accountID)
                val manager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(clip)
                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    @AssistedFactory
    interface UserProfileUtilsFactory {
        fun create(userAddress: Address, threadId: Long, scope: CoroutineScope): UserProfileUtils
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