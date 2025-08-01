package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.collection.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.utilities.Address
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.database.model.NotifyType
import org.thoughtcrime.securesms.database.model.RecipientSettings
import org.thoughtcrime.securesms.util.DateUtils.Companion.asEpochSeconds
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RecipientSettingsDatabase @Inject constructor(
    @ApplicationContext context: Context,
    databaseHelper: Provider<SQLCipherOpenHelper>
) : Database(context, databaseHelper) {
    private val mutableChangeNotification = MutableSharedFlow<Address>(extraBufferCapacity = 256)
    private val cache = LruCache<Address, RecipientSettings>(256)

    val changeNotification: SharedFlow<Address>
        get() = mutableChangeNotification

    fun save(address: Address, updater: (RecipientSettings) -> RecipientSettings) {
        val oldSettings = getSettings(address)
        val newSettings = updater.invoke(oldSettings)

        // If nothing is updated, return early
        if (oldSettings == newSettings) {
            Log.d(TAG, "No changes to settings for ${address.debugString}, old: $oldSettings, new: $newSettings")
            return
        }

        // Otherwise update the database and cache
        Log.d(TAG, "Saving settings to db for ${address.debugString}")
        cache.put(address, newSettings)
        writableDatabase.insertWithOnConflict(
            TABLE_NAME,
            null,
            newSettings.toContentValues().apply {
                put(COL_ADDRESS, address.toString())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )

        mutableChangeNotification.tryEmit(address)
    }

    fun delete(address: Address) {
        cache.remove(address)
        writableDatabase.delete(
            TABLE_NAME,
            "$COL_ADDRESS = ?",
            arrayOf(address.toString())
        )
        mutableChangeNotification.tryEmit(address)
    }

    fun getSettings(address: Address): RecipientSettings {
        val existing = cache[address]
        if (existing != null) {
            return existing
        }

        return readableDatabase.rawQuery("SELECT * FROM $TABLE_NAME WHERE $COL_ADDRESS = ?", address.address)
            .use { cursor ->
                // If no settings are saved in the database, return the empty settings, and cache
                // that as well so that we don't have to query the database again.
                val settings = if (cursor.moveToNext()) {
                    cursor.toRecipientSettings()
                } else {
                    RecipientSettings()
                }

                cache.put(address, settings)
                settings
            }
    }

    companion object {
        private const val TAG = "RecipientSettingsDatabase"

        const val TABLE_NAME = "recipient_settings"

        const val COL_ADDRESS = "address"
        private const val COL_MUTE_UNTIL = "mute_until"
        private const val COL_NOTIFY_TYPE = "notify_type"
        private const val COL_AUTO_DOWNLOAD_ATTACHMENTS = "auto_download_attachments"
        private const val COL_PROFILE_PIC_KEY = "profile_pic_key_b64"
        private const val COL_PROFILE_PIC_URL = "profile_pic_url"
        private const val COL_NAME = "name"
        private const val COL_BLOCKS_COMMUNITY_MESSAGES_REQUESTS = "blocks_community_messages_requests"
        private const val COL_IS_PRO = "is_pro"

        // The time when the profile pic/name/is_pro was last updated, in epoch seconds.
        private const val COL_PROFILE_UPDATE_TIME = "profile_update_time"

        const val MIGRATION_CREATE_TABLE = """
            CREATE TABLE recipient_settings (
                $COL_ADDRESS TEXT NOT NULL PRIMARY KEY COLLATE NOCASE,
                $COL_MUTE_UNTIL INTEGER NOT NULL DEFAULT 0,
                $COL_NOTIFY_TYPE INTEGER NOT NULL DEFAULT 1,
                $COL_AUTO_DOWNLOAD_ATTACHMENTS BOOLEAN NOT NULL DEFAULT FALSE,
                $COL_PROFILE_PIC_KEY TEXT,
                $COL_PROFILE_PIC_URL TEXT,
                $COL_NAME TEXT,
                $COL_BLOCKS_COMMUNITY_MESSAGES_REQUESTS BOOLEAN NOT NULL DEFAULT TRUE,
                $COL_IS_PRO BOOLEAN NOT NULL DEFAULT FALSE,
                $COL_PROFILE_UPDATE_TIME INTEGER NOT NULL DEFAULT 0
            ) WITHOUT ROWID
        """

        const val MIGRATE_MOVE_DATA_FROM_OLD_TABLE = """
           INSERT OR REPLACE INTO recipient_settings (
                $COL_ADDRESS,
                $COL_NAME,
                $COL_MUTE_UNTIL,
                $COL_NOTIFY_TYPE,
                $COL_AUTO_DOWNLOAD_ATTACHMENTS,
                $COL_PROFILE_PIC_KEY,
                $COL_PROFILE_PIC_URL,
                $COL_BLOCKS_COMMUNITY_MESSAGES_REQUESTS
            )
            SELECT
                r.recipient_ids,
                ifnull(nullif(r.system_display_name, ''), r.signal_profile_name) AS name,
                r.mute_until,
                CASE(r.notify_type)
                    WHEN 2 THEN "NONE"
                    WHEN 1 THEN "MENTIONS"
                    ELSE "ALL"
                END AS notify_type,
                (IFNULL(r.auto_download, 0) == 1) AS auto_download_attachments,
                r.profile_key,
                r.signal_profile_avatar,
                r.blocks_community_message_requests
            FROM recipient_preferences r
        """


        const val MIGRATE_DROP_OLD_TABLE = """
            DROP TABLE recipient_preferences
        """

        private fun readUserProfile(keyB64: String?, url: String?): UserPic? {
            return if (keyB64.isNullOrBlank() || url.isNullOrEmpty()) {
                null
            } else {
                UserPic(url, Base64.decode(keyB64))
            }
        }

        private fun readNotifyType(t: String): NotifyType {
            return runCatching { NotifyType.valueOf(t) }
                .onFailure { Log.e(TAG, "Error reading notify type of $t", it) }
                .getOrDefault(NotifyType.ALL)
        }

        private fun Cursor.toRecipientSettings(): RecipientSettings {
            return RecipientSettings(
                muteUntil = getLong(getColumnIndexOrThrow(COL_MUTE_UNTIL)).asEpochSeconds(),
                notifyType = readNotifyType(getString(getColumnIndexOrThrow(COL_NOTIFY_TYPE))),
                autoDownloadAttachments = getInt(getColumnIndexOrThrow(COL_AUTO_DOWNLOAD_ATTACHMENTS)) == 1,
                profilePic = readUserProfile(
                    keyB64 = getString(getColumnIndexOrThrow(COL_PROFILE_PIC_KEY)),
                    url = getString(getColumnIndexOrThrow(COL_PROFILE_PIC_URL))
                ),
                blocksCommunityMessagesRequests = getInt(getColumnIndexOrThrow(COL_BLOCKS_COMMUNITY_MESSAGES_REQUESTS)) == 1,
                name = getString(getColumnIndexOrThrow(COL_NAME)),
                isPro = getInt(getColumnIndexOrThrow(COL_IS_PRO)) == 1,
                profileUpdated = getLong(getColumnIndexOrThrow(COL_PROFILE_UPDATE_TIME)).asEpochSeconds(),
            )
        }

        private fun RecipientSettings.toContentValues(): ContentValues {
            return ContentValues().apply {
                put(COL_NAME, name)
                put(COL_MUTE_UNTIL, muteUntil?.toEpochSecond() ?: 0L)
                put(COL_NOTIFY_TYPE, notifyType.name)
                put(COL_AUTO_DOWNLOAD_ATTACHMENTS, autoDownloadAttachments)
                put(COL_PROFILE_PIC_KEY, profilePic?.key?.data?.let(Base64::encodeBytes))
                put(COL_PROFILE_PIC_URL, profilePic?.url)
                put(COL_BLOCKS_COMMUNITY_MESSAGES_REQUESTS, blocksCommunityMessagesRequests)
                put(COL_IS_PRO, isPro)
            }
        }
    }
}