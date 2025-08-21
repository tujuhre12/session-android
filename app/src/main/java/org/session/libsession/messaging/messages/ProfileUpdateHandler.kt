package org.session.libsession.messaging.messages

import network.loki.messenger.libsession_util.util.BaseCommunityInfo
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.messaging.messages.visible.Profile
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.toAddress
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.database.BlindMappingRepository
import org.thoughtcrime.securesms.database.RecipientRepository
import org.thoughtcrime.securesms.database.RecipientSettingsDatabase
import org.thoughtcrime.securesms.util.DateUtils.Companion.secondsToInstant
import org.thoughtcrime.securesms.util.DateUtils.Companion.toEpochSeconds
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This class handles the profile updates coming from user's messages. The messages can be
 * from a 1to1, groups or community conversations.
 *
 * Although [handleProfileUpdate] takes an [Address] or [AccountId], this class can only handle
 * the profile updates for **users**, not groups or communities' profile, as they have very different
 * mechanisms and storage for their updates.
 */
@Singleton
class ProfileUpdateHandler @Inject constructor(
    private val configFactory: ConfigFactoryProtocol,
    private val recipientDatabase: RecipientSettingsDatabase,
    private val blindIdMappingRepository: BlindMappingRepository,
    private val recipientRepository: RecipientRepository,
) {

    fun handleProfileUpdate(senderId: AccountId, updates: Updates, fromCommunity: BaseCommunityInfo?) {
        val unblinded = if (senderId.prefix?.isBlinded() == true && fromCommunity != null) {
            blindIdMappingRepository.getMapping(fromCommunity.baseUrl, Address.Blinded(senderId))
        } else {
            null
        }

        val senderAddress = senderId.toAddress()
        val sender = recipientRepository.getRecipientSync(senderAddress)

        if (sender.isSelf) {
            Log.d(TAG, "Ignoring profile update for ourselves")
            return
        }

        Log.d(TAG, "Handling profile update for $senderId")

        // If the sender has standard address (either as unblinded, or as is), we will check if
        // they are a contact and update their contact information accordingly.
        val standardSender = unblinded ?: (senderAddress as? Address.Standard)
        if (standardSender != null && (updates.name != null || updates.pic != null || updates.profileUpdateTime != null)) {
            configFactory.withMutableUserConfigs { configs ->
                configs.contacts.get(standardSender.accountId.hexString)?.let { existingContact ->
                    if (shouldUpdateProfile(
                        lastUpdated = existingContact.profileUpdatedEpochSeconds.secondsToInstant(),
                        newUpdateTime = updates.profileUpdateTime
                    )) {
                        configs.contacts.set(
                            existingContact.copy(
                                name = updates.name ?: existingContact.name,
                                profilePicture = updates.pic ?: existingContact.profilePicture,
                                profileUpdatedEpochSeconds = updates.profileUpdateTime?.toEpochSeconds() ?: 0L,
                            )
                        )
                        Log.d(TAG, "Updated contact profile for ${standardSender.debugString}")
                    } else {
                        Log.d(TAG, "Ignoring contact profile update for ${standardSender.debugString}, no changes detected")
                    }
                } ?: Log.w(TAG, "Got a unblinded address for a contact but it does not exist: ${standardSender.debugString}")
            }
        }

        // If we have a blinded address, we need to look at if we have a blinded contact to update
        if (senderAddress is Address.Blinded && (updates.pic != null || !updates.name.isNullOrBlank())) {
            configFactory.withMutableUserConfigs { configs ->
                configs.contacts.getBlinded(senderAddress.blindedId.hexString)?.let { c ->
                    if (shouldUpdateProfile(
                        lastUpdated = c.profileUpdatedEpochSeconds.secondsToInstant(),
                        newUpdateTime = updates.profileUpdateTime
                    )) {
                        if (updates.pic != null) {
                            c.profilePic = updates.pic
                        }

                        if (!updates.name.isNullOrBlank()) {
                            c.name = updates.name
                        }

                        configs.contacts.setBlinded(c)
                    }
                }
            }
        }


        // We'll always update both blinded/unblinded addresses in the recipient settings db,
        // as the user could delete the config and leave us no way to find the profile pic of
        // the sender.
        sequenceOf(senderAddress, unblinded)
            .filterNotNull()
            .forEach { address ->
                recipientDatabase.save(address) { r ->
                    if (shouldUpdateProfile(
                            lastUpdated = r.profileUpdated,
                            newUpdateTime = updates.profileUpdateTime
                        )) {
                        r.copy(
                            name = updates.name?.takeIf { it.isNotBlank() } ?: r.name,
                            profilePic = updates.pic ?: r.profilePic,
                            blocksCommunityMessagesRequests = updates.blocksCommunityMessageRequests ?: r.blocksCommunityMessagesRequests
                        )
                    } else {
                        r
                    }
                }
            }
    }

    /**
     * Determines if the profile should be updated based on the last updated time and the new update time.
     *
     * This function takes optional times because we need to deal with older versions of the app
     * where the updated time is not set.
     */
    private fun shouldUpdateProfile(
        lastUpdated: Instant?,
        newUpdateTime: Instant?
    ): Boolean {
        return (lastUpdated == null || newUpdateTime == null) || (newUpdateTime > lastUpdated)
    }

    class Updates private constructor(
        val name: String? = null,
        val pic: UserPic? = null,
        val blocksCommunityMessageRequests: Boolean? = null,
        val profileUpdateTime: Instant?,
    ) {
        companion object {
            fun create(
                name: String? = null,
                picUrl: String?,
                picKey: ByteArray?,
                blocksCommunityMessageRequests: Boolean? = null,
                proStatus: Boolean? = null,
                profileUpdateTime: Instant?
            ): Updates? {
                val hasNameUpdate = !name.isNullOrBlank()
                val pic = when {
                    picUrl == null -> null
                    picUrl.isBlank() || picKey == null || picKey.size !in VALID_PROFILE_KEY_LENGTH -> UserPic.DEFAULT
                    else -> UserPic(picUrl, picKey)
                }

                if (!hasNameUpdate && pic == null && blocksCommunityMessageRequests == null && proStatus == null) {
                    return null
                }

                return Updates(name, pic, blocksCommunityMessageRequests, profileUpdateTime)
            }

            fun Profile.toUpdates(
                blocksCommunityMessageRequests: Boolean? = null,
            ): Updates? {
                return create(
                    name = this.displayName,
                    picUrl = this.profilePictureURL,
                    picKey = this.profileKey,
                    blocksCommunityMessageRequests = blocksCommunityMessageRequests,
                    profileUpdateTime = this.profileUpdated
                )
            }
        }
    }

    companion object {
        const val TAG = "ProfileUpdateHandler"

        const val MAX_PROFILE_NAME_LENGTH = 100

        private val VALID_PROFILE_KEY_LENGTH = listOf(16, 32)
    }
}