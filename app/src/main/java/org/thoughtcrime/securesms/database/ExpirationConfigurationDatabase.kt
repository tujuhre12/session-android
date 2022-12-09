package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.utilities.GroupUtil.CLOSED_GROUP_PREFIX
import org.session.libsession.utilities.GroupUtil.OPEN_GROUP_INBOX_PREFIX
import org.session.libsession.utilities.GroupUtil.OPEN_GROUP_PREFIX
import org.session.libsignal.protos.SignalServiceProtos.Content.ExpirationType
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper

class ExpirationConfigurationDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {

    companion object {
        const val TABLE_NAME = "expiration_configuration"
        const val THREAD_ID = "thread_id"
        const val DURATION_SECONDS = "duration_seconds"
        const val EXPIRATION_TYPE = "expiration_type"
        const val UPDATED_TIMESTAMP_MS = "updated_timestamp_ms"

        @JvmField
        val CREATE_EXPIRATION_CONFIGURATION_TABLE_COMMAND = """
          CREATE TABLE $TABLE_NAME (
            $THREAD_ID INTEGER NOT NULL PRIMARY KEY ON CONFLICT REPLACE,
            $DURATION_SECONDS INTEGER NOT NULL,
            $EXPIRATION_TYPE INTEGER DEFAULT NULL,
            $UPDATED_TIMESTAMP_MS INTEGER DEFAULT NULL
          )
        """.trimIndent()

        @JvmField
        val MIGRATE_GROUP_CONVERSATION_EXPIRY_TYPE_COMMAND = """
            INSERT INTO $TABLE_NAME ($THREAD_ID, $DURATION_SECONDS, $EXPIRATION_TYPE) SELECT ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.ID}, ${RecipientDatabase.EXPIRE_MESSAGES}, 1
            FROM ${ThreadDatabase.TABLE_NAME}, ${RecipientDatabase.TABLE_NAME}
            WHERE ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.ADDRESS} LIKE '$CLOSED_GROUP_PREFIX%'
            AND EXISTS (SELECT ${RecipientDatabase.EXPIRE_MESSAGES} FROM ${RecipientDatabase.TABLE_NAME} WHERE ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.ADDRESS} = ${RecipientDatabase.TABLE_NAME}.${RecipientDatabase.ADDRESS} AND ${RecipientDatabase.EXPIRE_MESSAGES} > 0)
        """.trimIndent()

        @JvmField
        val MIGRATE_ONE_TO_ONE_CONVERSATION_EXPIRY_TYPE_COMMAND = """
            INSERT INTO $TABLE_NAME ($THREAD_ID, $DURATION_SECONDS, $EXPIRATION_TYPE) SELECT ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.ID}, ${RecipientDatabase.EXPIRE_MESSAGES}, 2
            FROM ${ThreadDatabase.TABLE_NAME}, ${RecipientDatabase.TABLE_NAME}
            WHERE ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.ADDRESS} NOT LIKE '$CLOSED_GROUP_PREFIX%'
            AND ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.ADDRESS} NOT LIKE '$OPEN_GROUP_PREFIX%'
            AND ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.ADDRESS} NOT LIKE '$OPEN_GROUP_INBOX_PREFIX%'
            AND EXISTS (SELECT ${RecipientDatabase.EXPIRE_MESSAGES} FROM ${RecipientDatabase.TABLE_NAME} WHERE ${ThreadDatabase.TABLE_NAME}.${ThreadDatabase.ADDRESS} = ${RecipientDatabase.TABLE_NAME}.${RecipientDatabase.ADDRESS} AND ${RecipientDatabase.EXPIRE_MESSAGES} > 0)
        """.trimIndent()

        private fun readExpirationConfiguration(cursor: Cursor): ExpirationConfiguration {
            return ExpirationConfiguration(
                threadId = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID)),
                durationSeconds = cursor.getInt(cursor.getColumnIndexOrThrow(DURATION_SECONDS)),
                expirationType = ExpirationType.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(EXPIRATION_TYPE))),
                updatedTimestampMs = cursor.getLong(cursor.getColumnIndexOrThrow(UPDATED_TIMESTAMP_MS))
            )
        }
    }

    fun getExpirationConfiguration(threadId: Long): ExpirationConfiguration? {
        val query = "$THREAD_ID = ?"
        val args = arrayOf("$threadId")

        val mappings: MutableList<ExpirationConfiguration> = mutableListOf()

        readableDatabase.query(TABLE_NAME, null, query, args, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                mappings += readExpirationConfiguration(cursor)
            }
        }

        return mappings.firstOrNull()
    }

    fun setExpirationConfiguration(configuration: ExpirationConfiguration) {
        writableDatabase.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(THREAD_ID, configuration.threadId)
                put(DURATION_SECONDS, configuration.durationSeconds)
                put(EXPIRATION_TYPE, configuration.expirationType?.number)
                put(UPDATED_TIMESTAMP_MS, configuration.updatedTimestampMs)
            }

            writableDatabase.insert(TABLE_NAME, null, values)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

}