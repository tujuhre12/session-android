package org.thoughtcrime.securesms.jobs

import android.content.Context
import android.text.TextUtils
import org.session.libsession.avatars.AvatarHelper
import org.session.libsession.messaging.jobs.Job
import org.session.libsession.messaging.jobs.JobDelegate
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.utilities.DownloadUtilities.downloadFile
import org.session.libsession.utilities.TextSecurePreferences.Companion.setProfileAvatarId
import org.session.libsession.utilities.Util.copy
import org.session.libsession.utilities.Util.equals
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.streams.ProfileCipherInputStream
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.dependencies.DatabaseComponent.Companion.get
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.SecureRandom

class RetrieveProfileAvatarJob(val profileAvatar: String, val recipientAddress: Address): Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 0

    lateinit var context: Context

    companion object {
        val TAG = RetrieveProfileAvatarJob::class.simpleName
        val KEY: String = "RetrieveProfileAvatarJob"

        // Keys used for database storage
        private val PROFILE_AVATAR_KEY = "profileAvatar"
        private val RECEIPIENT_ADDRESS_KEY = "recipient"
    }

    override fun execute(dispatcherName: String) {
        val recipient = Recipient.from(context, recipientAddress, true)
        val database = get(context).recipientDatabase()
        val profileKey = recipient.resolve().profileKey

        if (profileKey == null || (profileKey.size != 32 && profileKey.size != 16)) {
            Log.w(TAG, "Recipient profile key is gone!")
            return
        }

        if (AvatarHelper.avatarFileExists(context, recipient.resolve().address) && equals(profileAvatar, recipient.resolve().profileAvatar)) {
            Log.w(TAG, "Already retrieved profile avatar: $profileAvatar")
            return
        }

        if (TextUtils.isEmpty(profileAvatar)) {
            Log.w(TAG, "Removing profile avatar for: " + recipient.address.serialize())
            AvatarHelper.delete(context, recipient.address)
            database.setProfileAvatar(recipient, profileAvatar)
            return
        }

        val downloadDestination = File.createTempFile("avatar", ".jpg", context.cacheDir)

        try {
            downloadFile(downloadDestination, profileAvatar)
            val avatarStream: InputStream = ProfileCipherInputStream(FileInputStream(downloadDestination), profileKey)
            val decryptDestination = File.createTempFile("avatar", ".jpg", context.cacheDir)
            copy(avatarStream, FileOutputStream(decryptDestination))
            decryptDestination.renameTo(AvatarHelper.getAvatarFile(context, recipient.address))
        } finally {
            downloadDestination.delete()
        }

        if (recipient.isLocalNumber) {
            setProfileAvatarId(context, SecureRandom().nextInt())
        }
        database.setProfileAvatar(recipient, profileAvatar)
    }

    override fun serialize(): Data {
        return Data.Builder()
                .putString(PROFILE_AVATAR_KEY, profileAvatar)
                .putString(RECEIPIENT_ADDRESS_KEY, recipientAddress.serialize())
                .build()
    }

    override fun getFactoryKey(): String {
        return KEY
    }

    class Factory: Job.Factory<RetrieveProfileAvatarJob> {
        override fun create(data: Data): RetrieveProfileAvatarJob {
            val profileAvatar = data.getString(PROFILE_AVATAR_KEY)
            val recipientAddress = Address.fromSerialized(data.getString(RECEIPIENT_ADDRESS_KEY))
            return RetrieveProfileAvatarJob(profileAvatar, recipientAddress)
        }
    }
}