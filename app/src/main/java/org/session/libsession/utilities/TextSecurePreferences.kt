package org.session.libsession.utilities

import android.content.Context
import android.hardware.Camera
import android.net.Uri
import android.provider.Settings
import androidx.annotation.ArrayRes
import androidx.annotation.StyleRes
import androidx.camera.core.CameraSelector
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.TextSecurePreferences.Companion.AUTOPLAY_AUDIO_MESSAGES
import org.session.libsession.utilities.TextSecurePreferences.Companion.CALL_NOTIFICATIONS_ENABLED
import org.session.libsession.utilities.TextSecurePreferences.Companion.CLASSIC_DARK
import org.session.libsession.utilities.TextSecurePreferences.Companion.CLASSIC_LIGHT
import org.session.libsession.utilities.TextSecurePreferences.Companion.ENVIRONMENT
import org.session.libsession.utilities.TextSecurePreferences.Companion.FOLLOW_SYSTEM_SETTINGS
import org.session.libsession.utilities.TextSecurePreferences.Companion.FORCED_SHORT_TTL
import org.session.libsession.utilities.TextSecurePreferences.Companion.HAS_HIDDEN_MESSAGE_REQUESTS
import org.session.libsession.utilities.TextSecurePreferences.Companion.HAVE_SHOWN_A_NOTIFICATION_ABOUT_TOKEN_PAGE
import org.session.libsession.utilities.TextSecurePreferences.Companion.HIDE_PASSWORD
import org.session.libsession.utilities.TextSecurePreferences.Companion.LAST_VACUUM_TIME
import org.session.libsession.utilities.TextSecurePreferences.Companion.LAST_VERSION_CHECK
import org.session.libsession.utilities.TextSecurePreferences.Companion.LEGACY_PREF_KEY_SELECTED_UI_MODE
import org.session.libsession.utilities.TextSecurePreferences.Companion.OCEAN_DARK
import org.session.libsession.utilities.TextSecurePreferences.Companion.OCEAN_LIGHT
import org.session.libsession.utilities.TextSecurePreferences.Companion.SELECTED_ACCENT_COLOR
import org.session.libsession.utilities.TextSecurePreferences.Companion.SELECTED_STYLE
import org.session.libsession.utilities.TextSecurePreferences.Companion.SET_FORCE_CURRENT_USER_PRO
import org.session.libsession.utilities.TextSecurePreferences.Companion.SET_FORCE_INCOMING_MESSAGE_PRO
import org.session.libsession.utilities.TextSecurePreferences.Companion.SET_FORCE_OTHER_USERS_PRO
import org.session.libsession.utilities.TextSecurePreferences.Companion.SET_FORCE_POST_PRO
import org.session.libsession.utilities.TextSecurePreferences.Companion.SHOWN_CALL_NOTIFICATION
import org.session.libsession.utilities.TextSecurePreferences.Companion.SHOWN_CALL_WARNING
import org.session.libsession.utilities.TextSecurePreferences.Companion._events
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.debugmenu.DebugMenuViewModel
import org.thoughtcrime.securesms.pro.ProStatusManager
import java.io.IOException
import java.time.ZonedDateTime
import java.util.Arrays
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

interface TextSecurePreferences {

    fun getConfigurationMessageSynced(): Boolean
    fun setConfigurationMessageSynced(value: Boolean)

    fun setPushEnabled(value: Boolean)
    val pushEnabled: StateFlow<Boolean>

    fun isScreenLockEnabled(): Boolean
    fun setScreenLockEnabled(value: Boolean)
    fun getScreenLockTimeout(): Long
    fun setScreenLockTimeout(value: Long)
    fun setBackupPassphrase(passphrase: String?)
    fun getBackupPassphrase(): String?
    fun setEncryptedBackupPassphrase(encryptedPassphrase: String?)
    fun getEncryptedBackupPassphrase(): String?
    fun setBackupEnabled(value: Boolean)
    fun isBackupEnabled(): Boolean
    fun setNextBackupTime(time: Long)
    fun getNextBackupTime(): Long
    fun setBackupSaveDir(dirUri: String?)
    fun getBackupSaveDir(): String?
    fun getNeedsSqlCipherMigration(): Boolean
    fun setAttachmentEncryptedSecret(secret: String)
    fun setAttachmentUnencryptedSecret(secret: String?)
    fun getAttachmentEncryptedSecret(): String?
    fun getAttachmentUnencryptedSecret(): String?
    fun setDatabaseEncryptedSecret(secret: String)
    fun setDatabaseUnencryptedSecret(secret: String?)
    fun getDatabaseUnencryptedSecret(): String?
    fun getDatabaseEncryptedSecret(): String?
    fun isIncognitoKeyboardEnabled(): Boolean
    fun isReadReceiptsEnabled(): Boolean
    fun setReadReceiptsEnabled(enabled: Boolean)
    fun isTypingIndicatorsEnabled(): Boolean
    fun setTypingIndicatorsEnabled(enabled: Boolean)
    fun isLinkPreviewsEnabled(): Boolean
    fun setLinkPreviewsEnabled(enabled: Boolean)
    fun hasSeenGIFMetaDataWarning(): Boolean
    fun setHasSeenGIFMetaDataWarning()
    fun isGifSearchInGridLayout(): Boolean
    fun setIsGifSearchInGridLayout(isGrid: Boolean)
    fun getNotificationPriority(): Int
    fun getMessageBodyTextSize(): Int
    fun setPreferredCameraDirection(value: CameraSelector)
    fun getPreferredCameraDirection(): CameraSelector
    fun getNotificationPrivacy(): NotificationPrivacyPreference
    fun getRepeatAlertsCount(): Int
    fun getLocalRegistrationId(): Int
    fun setLocalRegistrationId(registrationId: Int)
    fun isInThreadNotifications(): Boolean
    fun isUniversalUnidentifiedAccess(): Boolean
    fun getUpdateApkRefreshTime(): Long
    fun setUpdateApkRefreshTime(value: Long)
    fun setUpdateApkDownloadId(value: Long)
    fun getUpdateApkDownloadId(): Long
    fun setUpdateApkDigest(value: String?)
    fun getUpdateApkDigest(): String?
    fun getLocalNumber(): String?
    fun watchLocalNumber(): StateFlow<String?>
    fun getHasLegacyConfig(): Boolean
    fun setHasLegacyConfig(newValue: Boolean)
    fun setLocalNumber(localNumber: String)
    fun removeLocalNumber()
    fun isEnterSendsEnabled(): Boolean
    fun isPasswordDisabled(): Boolean
    fun setPasswordDisabled(disabled: Boolean)
    fun getLastVersionCode(): Int
    fun setLastVersionCode(versionCode: Int)
    fun isPassphraseTimeoutEnabled(): Boolean
    fun getPassphraseTimeoutInterval(): Int
    fun getLanguage(): String?
    fun isNotificationsEnabled(): Boolean
    fun getNotificationRingtone(): Uri
    fun removeNotificationRingtone()
    fun setNotificationRingtone(ringtone: String?)
    fun setNotificationVibrateEnabled(enabled: Boolean)
    fun isNotificationVibrateEnabled(): Boolean
    fun getNotificationLedColor(): Int
    fun isThreadLengthTrimmingEnabled(): Boolean
    fun getLogEncryptedSecret(): String?
    fun setLogEncryptedSecret(base64Secret: String?)
    fun getLogUnencryptedSecret(): String?
    fun setLogUnencryptedSecret(base64Secret: String?)
    fun getNotificationChannelVersion(): Int
    fun setNotificationChannelVersion(version: Int)
    fun getNotificationMessagesChannelVersion(): Int
    fun setNotificationMessagesChannelVersion(version: Int)
    fun getBooleanPreference(key: String?, defaultValue: Boolean): Boolean
    fun setBooleanPreference(key: String?, value: Boolean)
    fun getStringPreference(key: String, defaultValue: String?): String?
    fun setStringPreference(key: String?, value: String?)
    fun getIntegerPreference(key: String, defaultValue: Int): Int
    fun setIntegerPreference(key: String, value: Int)
    fun setIntegerPreferenceBlocking(key: String, value: Int): Boolean
    fun getLongPreference(key: String, defaultValue: Long): Long
    fun setLongPreference(key: String, value: Long)
    fun removePreference(key: String)
    fun getStringSetPreference(key: String, defaultValues: Set<String>): Set<String>?
    fun setStringSetPreference(key: String, value: Set<String>)
    fun getHasViewedSeed(): Boolean
    fun setHasViewedSeed(hasViewedSeed: Boolean)
    fun setRestorationTime(time: Long)
    fun getRestorationTime(): Long
    fun getLastSnodePoolRefreshDate(): Long
    fun setLastSnodePoolRefreshDate(date: Date)
    fun getLastOpenTimeDate(): Long
    fun setLastOpenDate()
    fun hasSeenLinkPreviewSuggestionDialog(): Boolean
    fun setHasSeenLinkPreviewSuggestionDialog()
    fun hasHiddenMessageRequests(): Boolean
    fun setHasHiddenMessageRequests(hidden: Boolean)
    fun forceCurrentUserAsPro(): Boolean
    fun setForceCurrentUserAsPro(isPro: Boolean)
    fun forceOtherUsersAsPro(): Boolean
    fun setForceOtherUsersAsPro(isPro: Boolean)
    fun forceIncomingMessagesAsPro(): Boolean
    fun setForceIncomingMessagesAsPro(isPro: Boolean)
    fun forcePostPro(): Boolean
    fun setForcePostPro(postPro: Boolean)
    fun watchPostProStatus(): StateFlow<Boolean>
    fun setShownCallWarning(): Boolean
    fun setShownCallNotification(): Boolean
    fun isCallNotificationsEnabled(): Boolean
    fun getLastVacuum(): Long
    fun setLastVacuumNow()
    fun getFingerprintKeyGenerated(): Boolean
    fun setFingerprintKeyGenerated()
    fun getSelectedAccentColor(): String?
    @StyleRes fun getAccentColorStyle(): Int?
    fun setAccentColorStyle(@StyleRes newColorStyle: Int?)
    fun getThemeStyle(): String
    fun getFollowSystemSettings(): Boolean
    fun setThemeStyle(themeStyle: String)
    fun setFollowSystemSettings(followSystemSettings: Boolean)
    fun autoplayAudioMessages(): Boolean
    fun hasForcedNewConfig(): Boolean
    fun hasPreference(key: String): Boolean
    fun clearAll()
    fun getHidePassword(): Boolean
    fun setHidePassword(value: Boolean)
    fun watchHidePassword(): StateFlow<Boolean>
    fun getLastVersionCheck(): Long
    fun setLastVersionCheck()
    fun getEnvironment(): Environment
    fun setEnvironment(value: Environment)
    fun hasSeenTokenPageNotification(): Boolean
    fun setHasSeenTokenPageNotification(value: Boolean)
    fun forcedShortTTL(): Boolean
    fun setForcedShortTTL(value: Boolean)

    fun  getDebugMessageFeatures(): Set<ProStatusManager.MessageProFeature>
    fun  setDebugMessageFeatures(features: Set<ProStatusManager.MessageProFeature>)

    fun getDebugSubscriptionType(): DebugMenuViewModel.DebugSubscriptionStatus?
    fun setDebugSubscriptionType(status: DebugMenuViewModel.DebugSubscriptionStatus?)

    fun setSubscriptionProvider(provider: String)
    fun getSubscriptionProvider(): String?

    var deprecationStateOverride: String?
    var deprecatedTimeOverride: ZonedDateTime?
    var deprecatingStartTimeOverride: ZonedDateTime?
    var migratedToGroupV2Config: Boolean
    var migratedToDisablingKDF: Boolean
    var migratedToMultiPartConfig: Boolean

    var migratedDisappearingMessagesToMessageContent: Boolean

    var selectedActivityAliasName: String?

    var inAppReviewState: String?


    companion object {
        val TAG = TextSecurePreferences::class.simpleName

        internal val _events = MutableSharedFlow<String>(0, 64, BufferOverflow.DROP_OLDEST)
        val events get() = _events.asSharedFlow()

        @JvmStatic
        var pushSuffix = ""


        // This is a stop-gap solution for static access to shared preference.
        val preferenceInstance: TextSecurePreferences
            get() = MessagingModuleConfiguration.shared.preferences

        const val DISABLE_PASSPHRASE_PREF = "pref_disable_passphrase"
        const val LANGUAGE_PREF = "pref_language"
        const val THREAD_TRIM_NOW = "pref_trim_now"
        const val LAST_VERSION_CODE_PREF = "last_version_code"
        const val RINGTONE_PREF = "pref_key_ringtone"
        const val VIBRATE_PREF = "pref_key_vibrate"
        const val NOTIFICATION_PREF = "pref_key_enable_notifications"
        const val LED_COLOR_PREF = "pref_led_color"
        const val LED_COLOR_PREF_PRIMARY = "pref_led_color_primary"
        const val LED_BLINK_PREF = "pref_led_blink"
        const val LED_BLINK_PREF_CUSTOM = "pref_led_blink_custom"
        const val PASSPHRASE_TIMEOUT_INTERVAL_PREF = "pref_timeout_interval"
        const val PASSPHRASE_TIMEOUT_PREF = "pref_timeout_passphrase"
        const val ENTER_SENDS_PREF = "pref_enter_sends"
        const val THREAD_TRIM_ENABLED = "pref_trim_threads"
        internal const val LOCAL_NUMBER_PREF = "pref_local_number"
        const val REGISTERED_GCM_PREF = "pref_gcm_registered"
        const val UPDATE_APK_REFRESH_TIME_PREF = "pref_update_apk_refresh_time"
        const val UPDATE_APK_DOWNLOAD_ID = "pref_update_apk_download_id"
        const val UPDATE_APK_DIGEST = "pref_update_apk_digest"
        const val IN_THREAD_NOTIFICATION_PREF = "pref_key_inthread_notifications"
        const val IN_APP_NOTIFICATION_SOUNDS = "pref_sound_when_app_open"
        const val MESSAGE_BODY_TEXT_SIZE_PREF = "pref_message_body_text_size"
        const val LOCAL_REGISTRATION_ID_PREF = "pref_local_registration_id"
        const val REPEAT_ALERTS_PREF = "pref_repeat_alerts"
        const val NOTIFICATION_PRIVACY_PREF = "pref_notification_privacy"
        const val NOTIFICATION_PRIORITY_PREF = "pref_notification_priority"
        const val MEDIA_DOWNLOAD_MOBILE_PREF = "pref_media_download_mobile"
        const val MEDIA_DOWNLOAD_WIFI_PREF = "pref_media_download_wifi"
        const val MEDIA_DOWNLOAD_ROAMING_PREF = "pref_media_download_roaming"
        const val DIRECT_CAPTURE_CAMERA_ID = "pref_direct_capture_camera_id"
        const val READ_RECEIPTS_PREF = "pref_read_receipts"
        const val INCOGNITO_KEYBORAD_PREF = "pref_incognito_keyboard"
        const val DATABASE_ENCRYPTED_SECRET = "pref_database_encrypted_secret"
        const val DATABASE_UNENCRYPTED_SECRET = "pref_database_unencrypted_secret"
        const val ATTACHMENT_ENCRYPTED_SECRET = "pref_attachment_encrypted_secret"
        const val ATTACHMENT_UNENCRYPTED_SECRET = "pref_attachment_unencrypted_secret"
        const val NEEDS_SQLCIPHER_MIGRATION = "pref_needs_sql_cipher_migration"
        const val BACKUP_ENABLED = "pref_backup_enabled_v3"
        const val BACKUP_PASSPHRASE = "pref_backup_passphrase"
        const val ENCRYPTED_BACKUP_PASSPHRASE = "pref_encrypted_backup_passphrase"
        const val BACKUP_TIME = "pref_backup_next_time"
        const val BACKUP_NOW = "pref_backup_create"
        const val BACKUP_SAVE_DIR = "pref_save_dir"
        const val SCREEN_LOCK = "pref_android_screen_lock"
        const val SCREEN_LOCK_TIMEOUT = "pref_android_screen_lock_timeout"
        const val LOG_ENCRYPTED_SECRET = "pref_log_encrypted_secret"
        const val LOG_UNENCRYPTED_SECRET = "pref_log_unencrypted_secret"
        const val NOTIFICATION_CHANNEL_VERSION = "pref_notification_channel_version"
        const val NOTIFICATION_MESSAGES_CHANNEL_VERSION = "pref_notification_messages_channel_version"
        const val UNIVERSAL_UNIDENTIFIED_ACCESS = "pref_universal_unidentified_access"
        const val TYPING_INDICATORS = "pref_typing_indicators"
        const val LINK_PREVIEWS = "pref_link_previews"
        const val GIF_METADATA_WARNING = "has_seen_gif_metadata_warning"
        const val GIF_GRID_LAYOUT = "pref_gif_grid_layout"
        val IS_PUSH_ENABLED get() = "pref_is_using_fcm$pushSuffix"
        const val CONFIGURATION_SYNCED = "pref_configuration_synced"
        const val PROFILE_PIC_EXPIRY = "profile_pic_expiry"
        const val LAST_OPEN_DATE = "pref_last_open_date"
        const val HAS_HIDDEN_MESSAGE_REQUESTS = "pref_message_requests_hidden"
        const val SET_FORCE_CURRENT_USER_PRO = "pref_force_current_user_pro"
        const val SET_FORCE_OTHER_USERS_PRO = "pref_force_other_users_pro"
        const val SET_FORCE_INCOMING_MESSAGE_PRO = "pref_force_incoming_message_pro"
        const val SET_FORCE_POST_PRO = "pref_force_post_pro"
        const val CALL_NOTIFICATIONS_ENABLED = "pref_call_notifications_enabled"
        const val SHOWN_CALL_WARNING = "pref_shown_call_warning" // call warning is user-facing warning of enabling calls
        const val SHOWN_CALL_NOTIFICATION = "pref_shown_call_notification" // call notification is a prompt to check privacy settings
        const val LAST_VACUUM_TIME = "pref_last_vacuum_time"
        const val AUTOPLAY_AUDIO_MESSAGES = "pref_autoplay_audio"
        const val FINGERPRINT_KEY_GENERATED = "fingerprint_key_generated"
        const val SELECTED_ACCENT_COLOR = "selected_accent_color"
        const val LAST_VERSION_CHECK = "pref_last_version_check"
        const val ENVIRONMENT = "debug_environment"
        const val MIGRATED_TO_GROUP_V2_CONFIG = "migrated_to_group_v2_config"
        const val MIGRATED_TO_DISABLING_KDF = "migrated_to_disabling_kdf"
        const val MIGRATED_TO_MULTIPART_CONFIG = "migrated_to_multi_part_config"
        const val FORCED_COMMUNITY_DESCRIPTION_POLL = "forced_community_description_poll"

        const val HAS_RECEIVED_LEGACY_CONFIG = "has_received_legacy_config"
        const val HAS_FORCED_NEW_CONFIG = "has_forced_new_config"

        const val GREEN_ACCENT = "accent_green"
        const val BLUE_ACCENT = "accent_blue"
        const val PURPLE_ACCENT = "accent_purple"
        const val PINK_ACCENT = "accent_pink"
        const val RED_ACCENT = "accent_red"
        const val ORANGE_ACCENT = "accent_orange"
        const val YELLOW_ACCENT = "accent_yellow"

        const val SELECTED_STYLE = "pref_selected_style" // classic_dark/light, ocean_dark/light
        const val FOLLOW_SYSTEM_SETTINGS = "pref_follow_system" // follow system day/night
        const val HIDE_PASSWORD = "pref_hide_password"

        const val LEGACY_PREF_KEY_SELECTED_UI_MODE = "SELECTED_UI_MODE" // this will be cleared upon launching app, for users migrating to theming build
        const val CLASSIC_DARK = "classic.dark"
        const val CLASSIC_LIGHT = "classic.light"
        const val OCEAN_DARK = "ocean.dark"
        const val OCEAN_LIGHT = "ocean.light"

        const val ALLOW_MESSAGE_REQUESTS = "libsession.ALLOW_MESSAGE_REQUESTS"

        const val DEPRECATED_STATE_OVERRIDE = "deprecation_state_override"
        const val DEPRECATED_TIME_OVERRIDE = "deprecated_time_override"
        const val DEPRECATING_START_TIME_OVERRIDE = "deprecating_start_time_override"

        // Key name for if we've warned the user that saving attachments will allow other apps to access them.
        // Note: We only ever display this once - and when the user has accepted the warning we never show it again
        // for the lifetime of the Session installation.
        const val HAVE_WARNED_USER_ABOUT_SAVING_ATTACHMENTS = "libsession.HAVE_WARNED_USER_ABOUT_SAVING_ATTACHMENTS"

        // As we will have an incoming push notification to inform the user about the new token page, but we
        // will also schedule instigating a local notification, we need to keep track of whether ANY notification
        // has been shown to the user, and if so we don't show another.
        const val HAVE_SHOWN_A_NOTIFICATION_ABOUT_TOKEN_PAGE = "pref_shown_a_notification_about_token_page"

        // Key name for the user's preferred date format string
        const val DATE_FORMAT_PREF = "libsession.DATE_FORMAT_PREF"

        // Key name for the user's preferred time format string
        const val TIME_FORMAT_PREF = "libsession.TIME_FORMAT_PREF"

        const val FORCED_SHORT_TTL = "forced_short_ttl"

        const val IN_APP_REVIEW_STATE = "in_app_review_state"

        const val DEBUG_MESSAGE_FEATURES = "debug_message_features"
        const val DEBUG_SUBSCRIPTION_STATUS = "debug_subscription_status"

        const val SUBSCRIPTION_PROVIDER = "session_subscription_provider"

        @JvmStatic
        fun getConfigurationMessageSynced(context: Context): Boolean {
            return getBooleanPreference(context, CONFIGURATION_SYNCED, false)
        }

        @JvmStatic
        fun setConfigurationMessageSynced(context: Context, value: Boolean) {
            setBooleanPreference(context, CONFIGURATION_SYNCED, value)
            _events.tryEmit(CONFIGURATION_SYNCED)
        }

        @JvmStatic
        fun isPushEnabled(context: Context): Boolean {
            return getBooleanPreference(context, IS_PUSH_ENABLED, false)
        }

        @JvmStatic
        fun setPushEnabled(context: Context, value: Boolean) {
            setBooleanPreference(context, IS_PUSH_ENABLED, value)
        }

        // endregion
        @JvmStatic
        fun isScreenLockEnabled(context: Context): Boolean {
            return getBooleanPreference(context, SCREEN_LOCK, false)
        }

        @JvmStatic
        fun setScreenLockEnabled(context: Context, value: Boolean) {
            setBooleanPreference(context, SCREEN_LOCK, value)
        }

        @JvmStatic
        fun getScreenLockTimeout(context: Context): Long {
            return getLongPreference(context, SCREEN_LOCK_TIMEOUT, 0)
        }

        @JvmStatic
        fun setScreenLockTimeout(context: Context, value: Long) {
            setLongPreference(context, SCREEN_LOCK_TIMEOUT, value)
        }

        @JvmStatic
        fun setBackupPassphrase(context: Context, passphrase: String?) {
            setStringPreference(context, BACKUP_PASSPHRASE, passphrase)
        }

        @JvmStatic
        fun getBackupPassphrase(context: Context): String? {
            return getStringPreference(context, BACKUP_PASSPHRASE, null)
        }

        @JvmStatic
        fun setEncryptedBackupPassphrase(context: Context, encryptedPassphrase: String?) {
            setStringPreference(context, ENCRYPTED_BACKUP_PASSPHRASE, encryptedPassphrase)
        }

        @JvmStatic
        fun getEncryptedBackupPassphrase(context: Context): String? {
            return getStringPreference(context, ENCRYPTED_BACKUP_PASSPHRASE, null)
        }

        fun setBackupEnabled(context: Context, value: Boolean) {
            setBooleanPreference(context, BACKUP_ENABLED, value)
        }

        @JvmStatic
        fun isBackupEnabled(context: Context): Boolean {
            return getBooleanPreference(context, BACKUP_ENABLED, false)
        }

        @JvmStatic
        fun setNextBackupTime(context: Context, time: Long) {
            setLongPreference(context, BACKUP_TIME, time)
        }

        @JvmStatic
        fun getNextBackupTime(context: Context): Long {
            return getLongPreference(context, BACKUP_TIME, -1)
        }

        fun setBackupSaveDir(context: Context, dirUri: String?) {
            setStringPreference(context, BACKUP_SAVE_DIR, dirUri)
        }

        fun getBackupSaveDir(context: Context): String? {
            return getStringPreference(context, BACKUP_SAVE_DIR, null)
        }

        @JvmStatic
        fun getNeedsSqlCipherMigration(context: Context): Boolean {
            return getBooleanPreference(context, NEEDS_SQLCIPHER_MIGRATION, false)
        }

        @JvmStatic
        fun setAttachmentEncryptedSecret(context: Context, secret: String) {
            setStringPreference(context, ATTACHMENT_ENCRYPTED_SECRET, secret)
        }

        @JvmStatic
        fun setAttachmentUnencryptedSecret(context: Context, secret: String?) {
            setStringPreference(context, ATTACHMENT_UNENCRYPTED_SECRET, secret)
        }

        @JvmStatic
        fun getAttachmentEncryptedSecret(context: Context): String? {
            return getStringPreference(context, ATTACHMENT_ENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun getAttachmentUnencryptedSecret(context: Context): String? {
            return getStringPreference(context, ATTACHMENT_UNENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun setDatabaseEncryptedSecret(context: Context, secret: String) {
            setStringPreference(context, DATABASE_ENCRYPTED_SECRET, secret)
        }

        @JvmStatic
        fun setDatabaseUnencryptedSecret(context: Context, secret: String?) {
            setStringPreference(context, DATABASE_UNENCRYPTED_SECRET, secret)
        }

        @JvmStatic
        fun getDatabaseUnencryptedSecret(context: Context): String? {
            return getStringPreference(context, DATABASE_UNENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun getDatabaseEncryptedSecret(context: Context): String? {
            return getStringPreference(context, DATABASE_ENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun isIncognitoKeyboardEnabled(context: Context): Boolean {
            return getBooleanPreference(context, INCOGNITO_KEYBORAD_PREF, true)
        }

        @JvmStatic
        fun isReadReceiptsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, READ_RECEIPTS_PREF, false)
        }

        fun setReadReceiptsEnabled(context: Context, enabled: Boolean) {
            setBooleanPreference(context, READ_RECEIPTS_PREF, enabled)
        }

        @JvmStatic
        fun isTypingIndicatorsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, TYPING_INDICATORS, false)
        }

        @JvmStatic
        fun setTypingIndicatorsEnabled(context: Context, enabled: Boolean) {
            setBooleanPreference(context, TYPING_INDICATORS, enabled)
        }

        @JvmStatic
        fun isLinkPreviewsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, LINK_PREVIEWS, false)
        }

        @JvmStatic
        fun setLinkPreviewsEnabled(context: Context, enabled: Boolean) {
            setBooleanPreference(context, LINK_PREVIEWS, enabled)
        }

        @JvmStatic
        fun hasSeenGIFMetaDataWarning(context: Context): Boolean {
            return getBooleanPreference(context, GIF_METADATA_WARNING, false)
        }

        @JvmStatic
        fun setHasSeenGIFMetaDataWarning(context: Context) {
            setBooleanPreference(context, GIF_METADATA_WARNING, true)
        }

        @JvmStatic
        fun isGifSearchInGridLayout(context: Context): Boolean {
            return getBooleanPreference(context, GIF_GRID_LAYOUT, false)
        }

        @JvmStatic
        fun setIsGifSearchInGridLayout(context: Context, isGrid: Boolean) {
            setBooleanPreference(context, GIF_GRID_LAYOUT, isGrid)
        }

        @JvmStatic
        fun getNotificationPrivacy(context: Context): NotificationPrivacyPreference {
            return NotificationPrivacyPreference(getStringPreference(context, NOTIFICATION_PRIVACY_PREF, "all"))
        }

        @JvmStatic
        fun getRepeatAlertsCount(context: Context): Int {
            return try {
                getStringPreference(context, REPEAT_ALERTS_PREF, "0")!!.toInt()
            } catch (e: NumberFormatException) {
                Log.w(TAG, e)
                0
            }
        }

        fun getLocalRegistrationId(context: Context): Int {
            return getIntegerPreference(context, LOCAL_REGISTRATION_ID_PREF, 0)
        }

        fun setLocalRegistrationId(context: Context, registrationId: Int) {
            setIntegerPreference(context, LOCAL_REGISTRATION_ID_PREF, registrationId)
        }

        @JvmStatic
        fun isInThreadNotifications(context: Context): Boolean {
            return getBooleanPreference(context, IN_THREAD_NOTIFICATION_PREF, true)
        }

        @JvmStatic
        fun isUniversalUnidentifiedAccess(context: Context): Boolean {
            return getBooleanPreference(context, UNIVERSAL_UNIDENTIFIED_ACCESS, false)
        }

        @JvmStatic
        fun getUpdateApkRefreshTime(context: Context): Long {
            return getLongPreference(context, UPDATE_APK_REFRESH_TIME_PREF, 0L)
        }

        @JvmStatic
        fun setUpdateApkRefreshTime(context: Context, value: Long) {
            setLongPreference(context, UPDATE_APK_REFRESH_TIME_PREF, value)
        }

        @JvmStatic
        fun setUpdateApkDownloadId(context: Context, value: Long) {
            setLongPreference(context, UPDATE_APK_DOWNLOAD_ID, value)
        }

        @JvmStatic
        fun getUpdateApkDownloadId(context: Context): Long {
            return getLongPreference(context, UPDATE_APK_DOWNLOAD_ID, -1)
        }

        @JvmStatic
        fun setUpdateApkDigest(context: Context, value: String?) {
            setStringPreference(context, UPDATE_APK_DIGEST, value)
        }

        @JvmStatic
        fun getUpdateApkDigest(context: Context): String? {
            return getStringPreference(context, UPDATE_APK_DIGEST, null)
        }

        @Deprecated(
            "Use the dependency-injected TextSecurePreference instance instead",
            ReplaceWith("TextSecurePreferences.getLocalNumber()")
        )
        @JvmStatic
        fun getLocalNumber(context: Context): String? {
            return preferenceInstance.getLocalNumber()
        }

        @JvmStatic
        fun getHasLegacyConfig(context: Context): Boolean {
            return getBooleanPreference(context, HAS_RECEIVED_LEGACY_CONFIG, false)
        }

        @JvmStatic
        fun setHasLegacyConfig(context: Context, newValue: Boolean) {
            setBooleanPreference(context, HAS_RECEIVED_LEGACY_CONFIG, newValue)
            _events.tryEmit(HAS_RECEIVED_LEGACY_CONFIG)
        }


        @JvmStatic
        fun isEnterSendsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, ENTER_SENDS_PREF, false)
        }

        @JvmStatic
        fun isPasswordDisabled(context: Context): Boolean {
            return getBooleanPreference(context, DISABLE_PASSPHRASE_PREF, true)
        }

        fun setPasswordDisabled(context: Context, disabled: Boolean) {
            setBooleanPreference(context, DISABLE_PASSPHRASE_PREF, disabled)
        }

        fun getLastVersionCode(context: Context): Int {
            return getIntegerPreference(context, LAST_VERSION_CODE_PREF, 0)
        }

        @Throws(IOException::class)
        fun setLastVersionCode(context: Context, versionCode: Int) {
            if (!setIntegerPreferenceBlocking(context, LAST_VERSION_CODE_PREF, versionCode)) {
                throw IOException("couldn't write version code to sharedpreferences")
            }
        }

        @JvmStatic
        fun isPassphraseTimeoutEnabled(context: Context): Boolean {
            return getBooleanPreference(context, PASSPHRASE_TIMEOUT_PREF, false)
        }

        @JvmStatic
        fun getPassphraseTimeoutInterval(context: Context): Int {
            return getIntegerPreference(context, PASSPHRASE_TIMEOUT_INTERVAL_PREF, 5 * 60)
        }

        @JvmStatic
        fun getLanguage(context: Context): String? {
            return getStringPreference(context, LANGUAGE_PREF, "zz")
        }

        @JvmStatic
        fun isNotificationsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, NOTIFICATION_PREF, true)
        }

        @JvmStatic
        fun getNotificationRingtone(context: Context): Uri {
            var result = getStringPreference(context, RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString())
            if (result != null && result.startsWith("file:")) {
                result = Settings.System.DEFAULT_NOTIFICATION_URI.toString()
            }
            return Uri.parse(result)
        }

        @JvmStatic
        fun removeNotificationRingtone(context: Context) {
            removePreference(context, RINGTONE_PREF)
        }

        @JvmStatic
        fun setNotificationRingtone(context: Context, ringtone: String?) {
            setStringPreference(context, RINGTONE_PREF, ringtone)
        }

        @JvmStatic
        fun setNotificationVibrateEnabled(context: Context, enabled: Boolean) {
            setBooleanPreference(context, VIBRATE_PREF, enabled)
        }

        @JvmStatic
        fun isNotificationVibrateEnabled(context: Context): Boolean {
            return getBooleanPreference(context, VIBRATE_PREF, true)
        }

        @JvmStatic
        fun getNotificationLedColor(context: Context): Int {
            return getIntegerPreference(context, LED_COLOR_PREF_PRIMARY, ThemeUtil.getThemedColor(context, R.attr.colorAccent))
        }

        @JvmStatic
        fun isThreadLengthTrimmingEnabled(context: Context): Boolean {
            return getBooleanPreference(context, THREAD_TRIM_ENABLED, true)
        }

        private fun getMediaDownloadAllowed(context: Context, key: String, @ArrayRes defaultValuesRes: Int): Set<String>? {
            return getStringSetPreference(context, key, HashSet(Arrays.asList(*context.resources.getStringArray(defaultValuesRes))))
        }

        @JvmStatic
        fun getLogEncryptedSecret(context: Context): String? {
            return getStringPreference(context, LOG_ENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun setLogEncryptedSecret(context: Context, base64Secret: String?) {
            setStringPreference(context, LOG_ENCRYPTED_SECRET, base64Secret)
        }

        @JvmStatic
        fun getLogUnencryptedSecret(context: Context): String? {
            return getStringPreference(context, LOG_UNENCRYPTED_SECRET, null)
        }

        @JvmStatic
        fun setLogUnencryptedSecret(context: Context, base64Secret: String?) {
            setStringPreference(context, LOG_UNENCRYPTED_SECRET, base64Secret)
        }

        @JvmStatic
        fun getNotificationChannelVersion(context: Context): Int {
            return getIntegerPreference(context, NOTIFICATION_CHANNEL_VERSION, 1)
        }

        @JvmStatic
        fun setNotificationChannelVersion(context: Context, version: Int) {
            setIntegerPreference(context, NOTIFICATION_CHANNEL_VERSION, version)
        }

        @JvmStatic
        fun getNotificationMessagesChannelVersion(context: Context): Int {
            return getIntegerPreference(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, 1)
        }

        @JvmStatic
        fun setNotificationMessagesChannelVersion(context: Context, version: Int) {
            setIntegerPreference(context, NOTIFICATION_MESSAGES_CHANNEL_VERSION, version)
        }

        @JvmStatic
        fun hasForcedNewConfig(context: Context): Boolean {
            return getBooleanPreference(context, HAS_FORCED_NEW_CONFIG, false)
        }
        
        fun forcedCommunityDescriptionPoll(context: Context, room: String): Boolean {
            return getBooleanPreference(context, FORCED_COMMUNITY_DESCRIPTION_POLL+room, false)
        }

        fun setForcedCommunityDescriptionPoll(context: Context, room: String, forced: Boolean) {
            setBooleanPreference(context, FORCED_COMMUNITY_DESCRIPTION_POLL+room, forced)
        }

        @JvmStatic
        fun getBooleanPreference(context: Context, key: String?, defaultValue: Boolean): Boolean {
            return getDefaultSharedPreferences(context).getBoolean(key, defaultValue)
        }

        @JvmStatic
        fun setBooleanPreference(context: Context, key: String?, value: Boolean) {
            getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply()
        }

        @JvmStatic
        fun getStringPreference(context: Context, key: String, defaultValue: String?): String? {
            return getDefaultSharedPreferences(context).getString(key, defaultValue)
        }

        @JvmStatic
        fun setStringPreference(context: Context, key: String?, value: String?) {
            getDefaultSharedPreferences(context).edit().putString(key, value).apply()
        }

        fun getIntegerPreference(context: Context, key: String, defaultValue: Int): Int {
            return getDefaultSharedPreferences(context).getInt(key, defaultValue)
        }

        private fun setIntegerPreference(context: Context, key: String, value: Int) {
            getDefaultSharedPreferences(context).edit().putInt(key, value).apply()
        }

        private fun setIntegerPreferenceBlocking(context: Context, key: String, value: Int): Boolean {
            return getDefaultSharedPreferences(context).edit().putInt(key, value).commit()
        }

        private fun getLongPreference(context: Context, key: String, defaultValue: Long): Long {
            return getDefaultSharedPreferences(context).getLong(key, defaultValue)
        }

        private fun setLongPreference(context: Context, key: String, value: Long) {
            getDefaultSharedPreferences(context).edit().putLong(key, value).apply()
        }

        private fun removePreference(context: Context, key: String) {
            getDefaultSharedPreferences(context).edit().remove(key).apply()
        }

        private fun getStringSetPreference(context: Context, key: String, defaultValues: Set<String>): Set<String>? {
            val prefs = getDefaultSharedPreferences(context)
            return if (prefs.contains(key)) {
                prefs.getStringSet(key, emptySet())
            } else {
                defaultValues
            }
        }

        fun getHasViewedSeed(context: Context): Boolean {
            return getBooleanPreference(context, "has_viewed_seed", false)
        }

        fun setHasViewedSeed(context: Context, hasViewedSeed: Boolean) {
            setBooleanPreference(context, "has_viewed_seed", hasViewedSeed)
        }

        fun setRestorationTime(context: Context, time: Long) {
            setLongPreference(context, "restoration_time", time)
        }

        fun getRestorationTime(context: Context): Long {
            return getLongPreference(context, "restoration_time", 0)
        }

        @Deprecated("We no longer keep the profile expiry in prefs, we write them in the file instead. Keeping it here for migration purposes")
        @JvmStatic
        fun getProfileExpiry(context: Context): Long{
            return getLongPreference(context, PROFILE_PIC_EXPIRY, 0)
        }

        fun getLastSnodePoolRefreshDate(context: Context?): Long {
            return getLongPreference(context!!, "last_snode_pool_refresh_date", 0)
        }

        fun setLastSnodePoolRefreshDate(context: Context?, date: Date) {
            setLongPreference(context!!, "last_snode_pool_refresh_date", date.time)
        }
        fun getLastOpenTimeDate(context: Context): Long {
            return getLongPreference(context, LAST_OPEN_DATE, 0)
        }

        fun setLastOpenDate(context: Context) {
            setLongPreference(context, LAST_OPEN_DATE, System.currentTimeMillis())
        }

        fun hasSeenLinkPreviewSuggestionDialog(context: Context): Boolean {
            return getBooleanPreference(context, "has_seen_link_preview_suggestion_dialog", false)
        }

        fun setHasSeenLinkPreviewSuggestionDialog(context: Context) {
            setBooleanPreference(context, "has_seen_link_preview_suggestion_dialog", true)
        }

        @JvmStatic
        fun hasHiddenMessageRequests(context: Context): Boolean {
            return getBooleanPreference(context, HAS_HIDDEN_MESSAGE_REQUESTS, false)
        }

        @JvmStatic
        fun removeHasHiddenMessageRequests(context: Context) {
            removePreference(context, HAS_HIDDEN_MESSAGE_REQUESTS)
            _events.tryEmit(HAS_HIDDEN_MESSAGE_REQUESTS)
        }

        @JvmStatic
        fun isCallNotificationsEnabled(context: Context): Boolean {
            return getBooleanPreference(context, CALL_NOTIFICATIONS_ENABLED, false)
        }

        @JvmStatic
        fun setShownCallWarning(context: Context): Boolean {
            val previousValue = getBooleanPreference(context, SHOWN_CALL_WARNING, false)
            if (previousValue) {
                return false
            }
            val setValue = true
            setBooleanPreference(context, SHOWN_CALL_WARNING, setValue)
            return previousValue != setValue
        }

        @JvmStatic
        fun getLastVacuumTime(context: Context): Long {
            return getLongPreference(context, LAST_VACUUM_TIME, 0)
        }

        @JvmStatic
        fun setLastVacuumNow(context: Context) {
            setLongPreference(context, LAST_VACUUM_TIME, System.currentTimeMillis())
        }

        @JvmStatic
        fun getFingerprintKeyGenerated(context: Context): Boolean {
            return getBooleanPreference(context, FINGERPRINT_KEY_GENERATED, false)
        }

        @JvmStatic
        fun setFingerprintKeyGenerated(context: Context) {
            setBooleanPreference(context, FINGERPRINT_KEY_GENERATED, true)
        }

        // ----- Get / set methods for if we have already warned the user that saving attachments will allow other apps to access them -----
        // Note: We only ever show the warning dialog about this ONCE - when the user accepts this fact we write true to the flag & never show again.
        @JvmStatic
        fun getHaveWarnedUserAboutSavingAttachments(context: Context): Boolean {
            return getBooleanPreference(context, HAVE_WARNED_USER_ABOUT_SAVING_ATTACHMENTS, false)
        }

        @JvmStatic
        fun setHaveWarnedUserAboutSavingAttachments(context: Context) {
            setBooleanPreference(context, HAVE_WARNED_USER_ABOUT_SAVING_ATTACHMENTS, true)
        }

        // ----- Get / set methods for the user's date format preference -----
        @JvmStatic
        fun getDateFormatPref(context: Context): Int {
            // Note: 0 means "follow system setting" (default) - go to the declaration of DATE_FORMAT_PREF for further details.
            return getIntegerPreference(context, DATE_FORMAT_PREF, -1)
        }

        @JvmStatic
        fun setDateFormatPref(context: Context, value: Int) { setIntegerPreference(context, DATE_FORMAT_PREF, value) }

        @JvmStatic
        fun forcedShortTTL(context: Context): Boolean {
            return getBooleanPreference(context, FORCED_SHORT_TTL, false)
        }
    }
}

@Singleton
class AppTextSecurePreferences @Inject constructor(
    @ApplicationContext private val context: Context
): TextSecurePreferences {
    private val localNumberState = MutableStateFlow(getStringPreference(TextSecurePreferences.LOCAL_NUMBER_PREF, null))
    private val postProLaunchState = MutableStateFlow(getBooleanPreference(SET_FORCE_POST_PRO, false))
    private val hiddenPasswordState = MutableStateFlow(getBooleanPreference(HIDE_PASSWORD, false))

    override var migratedToGroupV2Config: Boolean
        get() = getBooleanPreference(TextSecurePreferences.MIGRATED_TO_GROUP_V2_CONFIG, false)
        set(value) = setBooleanPreference(TextSecurePreferences.MIGRATED_TO_GROUP_V2_CONFIG, value)

    override var migratedToDisablingKDF: Boolean
        get() = getBooleanPreference(TextSecurePreferences.MIGRATED_TO_DISABLING_KDF, false)
        set(value) = getDefaultSharedPreferences(context).edit(commit = true) {
            putBoolean(TextSecurePreferences.MIGRATED_TO_DISABLING_KDF, value)
        }

    override var migratedToMultiPartConfig: Boolean
        get() = getBooleanPreference(TextSecurePreferences.MIGRATED_TO_MULTIPART_CONFIG, false)
        set(value) = setBooleanPreference(TextSecurePreferences.MIGRATED_TO_MULTIPART_CONFIG, value)

    override var migratedDisappearingMessagesToMessageContent: Boolean
        get() = getBooleanPreference("migrated_disappearing_messages_to_message_content", false)
        set(value) = setBooleanPreference("migrated_disappearing_messages_to_message_content", value)

    override fun getConfigurationMessageSynced(): Boolean {
        return getBooleanPreference(TextSecurePreferences.CONFIGURATION_SYNCED, false)
    }

    override var selectedActivityAliasName: String?
        get() = getStringPreference("selected_activity_alias_name", null)
        set(value) {
            setStringPreference("selected_activity_alias_name", value)
        }

    override fun setConfigurationMessageSynced(value: Boolean) {
        setBooleanPreference(TextSecurePreferences.CONFIGURATION_SYNCED, value)
        _events.tryEmit(TextSecurePreferences.CONFIGURATION_SYNCED)
    }

    override val pushEnabled: MutableStateFlow<Boolean> = MutableStateFlow(
        getBooleanPreference(TextSecurePreferences.IS_PUSH_ENABLED, false)
    )

    override fun setPushEnabled(value: Boolean) {
        setBooleanPreference(TextSecurePreferences.IS_PUSH_ENABLED, value)
        pushEnabled.value = value
    }

    override fun isScreenLockEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.SCREEN_LOCK, false)
    }

    override fun setScreenLockEnabled(value: Boolean) {
        setBooleanPreference(TextSecurePreferences.SCREEN_LOCK, value)
    }

    override fun getScreenLockTimeout(): Long {
        return getLongPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT, 0)
    }

    override fun setScreenLockTimeout(value: Long) {
        setLongPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT, value)
    }

    override fun setBackupPassphrase(passphrase: String?) {
        setStringPreference(TextSecurePreferences.BACKUP_PASSPHRASE, passphrase)
    }

    override fun getBackupPassphrase(): String? {
        return getStringPreference(TextSecurePreferences.BACKUP_PASSPHRASE, null)
    }

    override fun setEncryptedBackupPassphrase(encryptedPassphrase: String?) {
        setStringPreference(TextSecurePreferences.ENCRYPTED_BACKUP_PASSPHRASE, encryptedPassphrase)
    }

    override fun getEncryptedBackupPassphrase(): String? {
        return getStringPreference(TextSecurePreferences.ENCRYPTED_BACKUP_PASSPHRASE, null)
    }

    override fun setBackupEnabled(value: Boolean) {
        setBooleanPreference(TextSecurePreferences.BACKUP_ENABLED, value)
    }

    override fun isBackupEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.BACKUP_ENABLED, false)
    }

    override fun setNextBackupTime(time: Long) {
        setLongPreference(TextSecurePreferences.BACKUP_TIME, time)
    }

    override fun getNextBackupTime(): Long {
        return getLongPreference(TextSecurePreferences.BACKUP_TIME, -1)
    }

    override fun setBackupSaveDir(dirUri: String?) {
        setStringPreference(TextSecurePreferences.BACKUP_SAVE_DIR, dirUri)
    }

    override fun getBackupSaveDir(): String? {
        return getStringPreference(TextSecurePreferences.BACKUP_SAVE_DIR, null)
    }

    override fun getNeedsSqlCipherMigration(): Boolean {
        return getBooleanPreference(TextSecurePreferences.NEEDS_SQLCIPHER_MIGRATION, false)
    }

    override fun setAttachmentEncryptedSecret(secret: String) {
        setStringPreference(TextSecurePreferences.ATTACHMENT_ENCRYPTED_SECRET, secret)
    }

    override fun setAttachmentUnencryptedSecret(secret: String?) {
        setStringPreference(TextSecurePreferences.ATTACHMENT_UNENCRYPTED_SECRET, secret)
    }

    override fun getAttachmentEncryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.ATTACHMENT_ENCRYPTED_SECRET, null)
    }

    override fun getAttachmentUnencryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.ATTACHMENT_UNENCRYPTED_SECRET, null)
    }

    override fun setDatabaseEncryptedSecret(secret: String) {
        setStringPreference(TextSecurePreferences.DATABASE_ENCRYPTED_SECRET, secret)
    }

    override fun setDatabaseUnencryptedSecret(secret: String?) {
        setStringPreference(TextSecurePreferences.DATABASE_UNENCRYPTED_SECRET, secret)
    }

    override fun getDatabaseUnencryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.DATABASE_UNENCRYPTED_SECRET, null)
    }

    override fun getDatabaseEncryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.DATABASE_ENCRYPTED_SECRET, null)
    }

    override fun isIncognitoKeyboardEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.INCOGNITO_KEYBORAD_PREF, true)
    }

    override fun isReadReceiptsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.READ_RECEIPTS_PREF, false)
    }

    override fun setReadReceiptsEnabled(enabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.READ_RECEIPTS_PREF, enabled)
    }

    override fun isTypingIndicatorsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.TYPING_INDICATORS, false)
    }

    override fun setTypingIndicatorsEnabled(enabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.TYPING_INDICATORS, enabled)
    }

    override fun isLinkPreviewsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.LINK_PREVIEWS, false)
    }

    override fun setLinkPreviewsEnabled(enabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.LINK_PREVIEWS, enabled)
    }

    override fun hasSeenGIFMetaDataWarning(): Boolean {
        return getBooleanPreference(TextSecurePreferences.GIF_METADATA_WARNING, false)
    }

    override fun setHasSeenGIFMetaDataWarning() {
        setBooleanPreference(TextSecurePreferences.GIF_METADATA_WARNING, true)
    }

    override fun isGifSearchInGridLayout(): Boolean {
        return getBooleanPreference(TextSecurePreferences.GIF_GRID_LAYOUT, false)
    }

    override fun setIsGifSearchInGridLayout(isGrid: Boolean) {
        setBooleanPreference(TextSecurePreferences.GIF_GRID_LAYOUT, isGrid)
    }

    override fun getNotificationPriority(): Int {
        return getStringPreference(
            TextSecurePreferences.NOTIFICATION_PRIORITY_PREF, NotificationCompat.PRIORITY_HIGH.toString())!!.toInt()
    }

    override fun getMessageBodyTextSize(): Int {
        return getStringPreference(TextSecurePreferences.MESSAGE_BODY_TEXT_SIZE_PREF, "16")!!.toInt()
    }

    override fun setPreferredCameraDirection(value: CameraSelector) {
        setIntegerPreference(TextSecurePreferences.DIRECT_CAPTURE_CAMERA_ID,
            when(value){
                CameraSelector.DEFAULT_FRONT_CAMERA -> Camera.CameraInfo.CAMERA_FACING_FRONT
                else -> Camera.CameraInfo.CAMERA_FACING_BACK
            })
    }

    override fun getPreferredCameraDirection(): CameraSelector {
        return when(getIntegerPreference(TextSecurePreferences.DIRECT_CAPTURE_CAMERA_ID, Camera.CameraInfo.CAMERA_FACING_BACK)){
            Camera.CameraInfo.CAMERA_FACING_FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    override fun getNotificationPrivacy(): NotificationPrivacyPreference {
        return NotificationPrivacyPreference(getStringPreference(
            TextSecurePreferences.NOTIFICATION_PRIVACY_PREF, "all"))
    }

    override fun getRepeatAlertsCount(): Int {
        return try {
            getStringPreference(TextSecurePreferences.REPEAT_ALERTS_PREF, "0")!!.toInt()
        } catch (e: NumberFormatException) {
            Log.w(TextSecurePreferences.TAG, e)
            0
        }
    }

    override fun getLocalRegistrationId(): Int {
        return getIntegerPreference(TextSecurePreferences.LOCAL_REGISTRATION_ID_PREF, 0)
    }

    override fun setLocalRegistrationId(registrationId: Int) {
        setIntegerPreference(TextSecurePreferences.LOCAL_REGISTRATION_ID_PREF, registrationId)
    }

    override fun isInThreadNotifications(): Boolean {
        return getBooleanPreference(TextSecurePreferences.IN_THREAD_NOTIFICATION_PREF, true)
    }

    override fun isUniversalUnidentifiedAccess(): Boolean {
        return getBooleanPreference(TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS, false)
    }

    override fun getUpdateApkRefreshTime(): Long {
        return getLongPreference(TextSecurePreferences.UPDATE_APK_REFRESH_TIME_PREF, 0L)
    }

    override fun setUpdateApkRefreshTime(value: Long) {
        setLongPreference(TextSecurePreferences.UPDATE_APK_REFRESH_TIME_PREF, value)
    }

    override fun setUpdateApkDownloadId(value: Long) {
        setLongPreference(TextSecurePreferences.UPDATE_APK_DOWNLOAD_ID, value)
    }

    override fun getUpdateApkDownloadId(): Long {
        return getLongPreference(TextSecurePreferences.UPDATE_APK_DOWNLOAD_ID, -1)
    }

    override fun setUpdateApkDigest(value: String?) {
        setStringPreference(TextSecurePreferences.UPDATE_APK_DIGEST, value)
    }

    override fun getUpdateApkDigest(): String? {
        return getStringPreference(TextSecurePreferences.UPDATE_APK_DIGEST, null)
    }

    override fun getLocalNumber(): String? {
        return localNumberState.value
    }

    override fun watchLocalNumber(): StateFlow<String?> {
        return localNumberState
    }

    override fun getHasLegacyConfig(): Boolean {
        return getBooleanPreference(TextSecurePreferences.HAS_RECEIVED_LEGACY_CONFIG, false)
    }

    override fun setHasLegacyConfig(newValue: Boolean) {
        setBooleanPreference(TextSecurePreferences.HAS_RECEIVED_LEGACY_CONFIG, newValue)
        _events.tryEmit(TextSecurePreferences.HAS_RECEIVED_LEGACY_CONFIG)
    }

    override fun setLocalNumber(localNumber: String) {
        val normalised = localNumber.lowercase()
        setStringPreference(TextSecurePreferences.LOCAL_NUMBER_PREF, normalised)
        localNumberState.value = normalised
    }

    override fun removeLocalNumber() {
        localNumberState.value = null
        removePreference(TextSecurePreferences.LOCAL_NUMBER_PREF)
    }

    override fun isEnterSendsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.ENTER_SENDS_PREF, false)
    }

    override fun isPasswordDisabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.DISABLE_PASSPHRASE_PREF, true)
    }

    override fun setPasswordDisabled(disabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.DISABLE_PASSPHRASE_PREF, disabled)
    }

    override fun getLastVersionCode(): Int {
        return getIntegerPreference(TextSecurePreferences.LAST_VERSION_CODE_PREF, 0)
    }

    @Throws(IOException::class)
    override fun setLastVersionCode(versionCode: Int) {
        if (!setIntegerPreferenceBlocking(TextSecurePreferences.LAST_VERSION_CODE_PREF, versionCode)) {
            throw IOException("couldn't write version code to sharedpreferences")
        }
    }

    override fun isPassphraseTimeoutEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_PREF, false)
    }

    override fun getPassphraseTimeoutInterval(): Int {
        return getIntegerPreference(TextSecurePreferences.PASSPHRASE_TIMEOUT_INTERVAL_PREF, 5 * 60)
    }

    override fun getLanguage(): String? {
        return getStringPreference(TextSecurePreferences.LANGUAGE_PREF, "zz")
    }

    override fun isNotificationsEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.NOTIFICATION_PREF, true)
    }

    override fun getNotificationRingtone(): Uri {
        var result = getStringPreference(TextSecurePreferences.RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString())
        if (result != null && result.startsWith("file:")) {
            result = Settings.System.DEFAULT_NOTIFICATION_URI.toString()
        }
        return Uri.parse(result)
    }

    override fun removeNotificationRingtone() {
        removePreference(TextSecurePreferences.RINGTONE_PREF)
    }

    override fun setNotificationRingtone(ringtone: String?) {
        setStringPreference(TextSecurePreferences.RINGTONE_PREF, ringtone)
    }

    override fun setNotificationVibrateEnabled(enabled: Boolean) {
        setBooleanPreference(TextSecurePreferences.VIBRATE_PREF, enabled)
    }

    override fun isNotificationVibrateEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.VIBRATE_PREF, true)
    }

    override fun getNotificationLedColor(): Int {
        return getIntegerPreference(TextSecurePreferences.LED_COLOR_PREF_PRIMARY, context.getColor(R.color.accent_green))
    }

    override fun isThreadLengthTrimmingEnabled(): Boolean {
        return getBooleanPreference(TextSecurePreferences.THREAD_TRIM_ENABLED, true)
    }

    override fun getLogEncryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.LOG_ENCRYPTED_SECRET, null)
    }

    override fun setLogEncryptedSecret(base64Secret: String?) {
        setStringPreference(TextSecurePreferences.LOG_ENCRYPTED_SECRET, base64Secret)
    }

    override fun getLogUnencryptedSecret(): String? {
        return getStringPreference(TextSecurePreferences.LOG_UNENCRYPTED_SECRET, null)
    }

    override fun setLogUnencryptedSecret(base64Secret: String?) {
        setStringPreference(TextSecurePreferences.LOG_UNENCRYPTED_SECRET, base64Secret)
    }

    override fun getNotificationChannelVersion(): Int {
        return getIntegerPreference(TextSecurePreferences.NOTIFICATION_CHANNEL_VERSION, 1)
    }

    override fun setNotificationChannelVersion(version: Int) {
        setIntegerPreference(TextSecurePreferences.NOTIFICATION_CHANNEL_VERSION, version)
    }

    override fun getNotificationMessagesChannelVersion(): Int {
        return getIntegerPreference(TextSecurePreferences.NOTIFICATION_MESSAGES_CHANNEL_VERSION, 1)
    }

    override fun setNotificationMessagesChannelVersion(version: Int) {
        setIntegerPreference(TextSecurePreferences.NOTIFICATION_MESSAGES_CHANNEL_VERSION, version)
    }

    override fun hasForcedNewConfig(): Boolean =
        getBooleanPreference(TextSecurePreferences.HAS_FORCED_NEW_CONFIG, false)

    override fun getBooleanPreference(key: String?, defaultValue: Boolean): Boolean {
        return getDefaultSharedPreferences(context).getBoolean(key, defaultValue)
    }

    override fun setBooleanPreference(key: String?, value: Boolean) {
        getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply()
    }

    override fun getStringPreference(key: String, defaultValue: String?): String? {
        return getDefaultSharedPreferences(context).getString(key, defaultValue)
    }

    override fun setStringPreference(key: String?, value: String?) {
        getDefaultSharedPreferences(context).edit().putString(key, value).apply()
    }

    override fun getIntegerPreference(key: String, defaultValue: Int): Int {
        return getDefaultSharedPreferences(context).getInt(key, defaultValue)
    }

    override fun setIntegerPreference(key: String, value: Int) {
        getDefaultSharedPreferences(context).edit().putInt(key, value).apply()
    }

    override fun setIntegerPreferenceBlocking(key: String, value: Int): Boolean {
        return getDefaultSharedPreferences(context).edit().putInt(key, value).commit()
    }

    override fun getLongPreference(key: String, defaultValue: Long): Long {
        return getDefaultSharedPreferences(context).getLong(key, defaultValue)
    }

    override fun setLongPreference(key: String, value: Long) {
        getDefaultSharedPreferences(context).edit().putLong(key, value).apply()
    }

    override fun hasPreference(key: String): Boolean {
        return getDefaultSharedPreferences(context).contains(key)
    }

    override fun removePreference(key: String) {
        getDefaultSharedPreferences(context).edit().remove(key).apply()
    }

    override fun getStringSetPreference(key: String, defaultValues: Set<String>): Set<String>? {
        val prefs = getDefaultSharedPreferences(context)
        return if (prefs.contains(key)) {
            prefs.getStringSet(key, emptySet())
        } else {
            defaultValues
        }
    }

    override fun setStringSetPreference(key: String, value: Set<String>) {
        getDefaultSharedPreferences(context).edit { putStringSet(key, value) }
    }

    override fun getHasViewedSeed(): Boolean {
        return getBooleanPreference("has_viewed_seed", false)
    }

    override fun setHasViewedSeed(hasViewedSeed: Boolean) {
        setBooleanPreference("has_viewed_seed", hasViewedSeed)
    }

    override fun setRestorationTime(time: Long) {
        setLongPreference("restoration_time", time)
    }

    override fun getRestorationTime(): Long {
        return getLongPreference("restoration_time", 0)
    }

    override fun getLastSnodePoolRefreshDate(): Long {
        return getLongPreference("last_snode_pool_refresh_date", 0)
    }

    override fun setLastSnodePoolRefreshDate(date: Date) {
        setLongPreference("last_snode_pool_refresh_date", date.time)
    }

    override fun getLastOpenTimeDate(): Long {
        return getLongPreference(TextSecurePreferences.LAST_OPEN_DATE, 0)
    }

    override fun setLastOpenDate() {
        setLongPreference(TextSecurePreferences.LAST_OPEN_DATE, System.currentTimeMillis())
    }

    override fun hasSeenLinkPreviewSuggestionDialog(): Boolean {
        return getBooleanPreference("has_seen_link_preview_suggestion_dialog", false)
    }

    override fun setHasSeenLinkPreviewSuggestionDialog() {
        setBooleanPreference("has_seen_link_preview_suggestion_dialog", true)
    }

    override fun isCallNotificationsEnabled(): Boolean {
        return getBooleanPreference(CALL_NOTIFICATIONS_ENABLED, false)
    }

    override fun getLastVacuum(): Long {
        return getLongPreference(LAST_VACUUM_TIME, 0)
    }

    override fun setLastVacuumNow() {
        setLongPreference(LAST_VACUUM_TIME, System.currentTimeMillis())
    }

    override fun getLastVersionCheck(): Long {
        return getLongPreference(LAST_VERSION_CHECK, 0)
    }

    override fun setLastVersionCheck() {
        setLongPreference(LAST_VERSION_CHECK, System.currentTimeMillis())
    }

    override fun getEnvironment(): Environment {
        val environment = getStringPreference(ENVIRONMENT, null)
        return if (environment != null) {
            Environment.valueOf(environment)
        } else BuildConfig.DEFAULT_ENVIRONMENT
    }

    override fun setEnvironment(value: Environment) {
        setStringPreference(ENVIRONMENT, value.name)
    }

    override fun setShownCallNotification(): Boolean {
        val previousValue = getBooleanPreference(SHOWN_CALL_NOTIFICATION, false)
        if (previousValue) return false
        val setValue = true
        setBooleanPreference(SHOWN_CALL_NOTIFICATION, setValue)
        return previousValue != setValue
    }


    /**
     * Set the SHOWN_CALL_WARNING preference to `true`
     * Return `true` if the value did update (it was previously unset)
     */
    override fun setShownCallWarning() : Boolean {
        val previousValue = getBooleanPreference(SHOWN_CALL_WARNING, false)
        if (previousValue) {
            return false
        }
        val setValue = true
        setBooleanPreference(SHOWN_CALL_WARNING, setValue)
        return previousValue != setValue
    }

    override fun hasHiddenMessageRequests(): Boolean {
        return getBooleanPreference(HAS_HIDDEN_MESSAGE_REQUESTS, false)
    }

    override fun setHasHiddenMessageRequests(hidden: Boolean) {
        setBooleanPreference(HAS_HIDDEN_MESSAGE_REQUESTS, hidden)
        _events.tryEmit(HAS_HIDDEN_MESSAGE_REQUESTS)
    }
    override fun forceCurrentUserAsPro(): Boolean {
        return getBooleanPreference(SET_FORCE_CURRENT_USER_PRO, false)
    }

    override fun setForceCurrentUserAsPro(isPro: Boolean) {
        setBooleanPreference(SET_FORCE_CURRENT_USER_PRO, isPro)
        _events.tryEmit(SET_FORCE_CURRENT_USER_PRO)
    }

    override fun forceOtherUsersAsPro(): Boolean {
        return getBooleanPreference(SET_FORCE_OTHER_USERS_PRO, false)
    }

    override fun setForceOtherUsersAsPro(isPro: Boolean) {
        setBooleanPreference(SET_FORCE_OTHER_USERS_PRO, isPro)
        _events.tryEmit(SET_FORCE_OTHER_USERS_PRO)
    }

    override fun forceIncomingMessagesAsPro(): Boolean {
        return getBooleanPreference(SET_FORCE_INCOMING_MESSAGE_PRO, false)
    }

    override fun setForceIncomingMessagesAsPro(isPro: Boolean) {
        setBooleanPreference(SET_FORCE_INCOMING_MESSAGE_PRO, isPro)
    }

    override fun forcePostPro(): Boolean {
        return getBooleanPreference(SET_FORCE_POST_PRO, false)
    }

    override fun setForcePostPro(postPro: Boolean) {
        setBooleanPreference(SET_FORCE_POST_PRO, postPro)
        postProLaunchState.update { postPro }
        _events.tryEmit(SET_FORCE_POST_PRO)
    }

    override fun watchPostProStatus(): StateFlow<Boolean> {
        return postProLaunchState
    }

    override fun getFingerprintKeyGenerated(): Boolean {
        return getBooleanPreference(TextSecurePreferences.FINGERPRINT_KEY_GENERATED, false)
    }

    override fun setFingerprintKeyGenerated() {
        setBooleanPreference(TextSecurePreferences.FINGERPRINT_KEY_GENERATED, true)
    }

    override fun getSelectedAccentColor(): String? =
        getStringPreference(SELECTED_ACCENT_COLOR, null)

    @StyleRes
    override fun getAccentColorStyle(): Int? {
        return when (getSelectedAccentColor()) {
            TextSecurePreferences.GREEN_ACCENT -> R.style.PrimaryGreen
            TextSecurePreferences.BLUE_ACCENT -> R.style.PrimaryBlue
            TextSecurePreferences.PURPLE_ACCENT -> R.style.PrimaryPurple
            TextSecurePreferences.PINK_ACCENT -> R.style.PrimaryPink
            TextSecurePreferences.RED_ACCENT -> R.style.PrimaryRed
            TextSecurePreferences.ORANGE_ACCENT -> R.style.PrimaryOrange
            TextSecurePreferences.YELLOW_ACCENT -> R.style.PrimaryYellow
            else -> null
        }
    }

    override fun setAccentColorStyle(@StyleRes newColorStyle: Int?) {
        setStringPreference(
            SELECTED_ACCENT_COLOR, when (newColorStyle) {
                R.style.PrimaryGreen -> TextSecurePreferences.GREEN_ACCENT
                R.style.PrimaryBlue -> TextSecurePreferences.BLUE_ACCENT
                R.style.PrimaryPurple -> TextSecurePreferences.PURPLE_ACCENT
                R.style.PrimaryPink -> TextSecurePreferences.PINK_ACCENT
                R.style.PrimaryRed -> TextSecurePreferences.RED_ACCENT
                R.style.PrimaryOrange -> TextSecurePreferences.ORANGE_ACCENT
                R.style.PrimaryYellow -> TextSecurePreferences.YELLOW_ACCENT
                else -> null
            }
        )
    }

    override fun getThemeStyle(): String {
        val hasLegacy = getStringPreference(LEGACY_PREF_KEY_SELECTED_UI_MODE, null)
        if (!hasLegacy.isNullOrEmpty()) {
            migrateLegacyUiPref()
        }

        return getStringPreference(SELECTED_STYLE, CLASSIC_DARK)!!
    }

    override fun setThemeStyle(themeStyle: String) {
        val safeTheme = if (themeStyle !in listOf(CLASSIC_DARK, CLASSIC_LIGHT, OCEAN_DARK, OCEAN_LIGHT)) CLASSIC_DARK else themeStyle
        setStringPreference(SELECTED_STYLE, safeTheme)
    }

    override fun getFollowSystemSettings(): Boolean {
        val hasLegacy = getStringPreference(LEGACY_PREF_KEY_SELECTED_UI_MODE, null)
        if (!hasLegacy.isNullOrEmpty()) {
            migrateLegacyUiPref()
        }

        return getBooleanPreference(FOLLOW_SYSTEM_SETTINGS, false)
    }

    private fun migrateLegacyUiPref() {
        val legacy = getStringPreference(LEGACY_PREF_KEY_SELECTED_UI_MODE, null) ?: return
        val (mode, followSystem) = when (legacy) {
            "DAY" -> {
                CLASSIC_LIGHT to false
            }
            "NIGHT" -> {
                CLASSIC_DARK to false
            }
            "SYSTEM_DEFAULT" -> {
                CLASSIC_DARK to true
            }
            else -> {
                CLASSIC_DARK to false
            }
        }
        if (!hasPreference(FOLLOW_SYSTEM_SETTINGS) && !hasPreference(SELECTED_STYLE)) {
            setThemeStyle(mode)
            setFollowSystemSettings(followSystem)
        }
        removePreference(LEGACY_PREF_KEY_SELECTED_UI_MODE)
    }

    override fun setFollowSystemSettings(followSystemSettings: Boolean) {
        setBooleanPreference(FOLLOW_SYSTEM_SETTINGS, followSystemSettings)
    }

    override fun autoplayAudioMessages(): Boolean {
        return getBooleanPreference(AUTOPLAY_AUDIO_MESSAGES, false)
    }

    /**
     * Clear all prefs and reset or observables
     */
    override fun clearAll() {
        pushEnabled.update { false }
        localNumberState.update { null }
        postProLaunchState.update { false }
        hiddenPasswordState.update { false }

        getDefaultSharedPreferences(context).edit(commit = true) { clear() }
    }

    override fun getHidePassword() = getBooleanPreference(HIDE_PASSWORD, false)

    override fun setHidePassword(value: Boolean) {
        setBooleanPreference(HIDE_PASSWORD, value)
        hiddenPasswordState.update { value }
    }

    override fun watchHidePassword(): StateFlow<Boolean> {
        return hiddenPasswordState
    }

    override fun hasSeenTokenPageNotification(): Boolean {
        return getBooleanPreference(HAVE_SHOWN_A_NOTIFICATION_ABOUT_TOKEN_PAGE, false)
    }

    override fun setHasSeenTokenPageNotification(value: Boolean) {
        setBooleanPreference(HAVE_SHOWN_A_NOTIFICATION_ABOUT_TOKEN_PAGE, value)
    }

    override fun forcedShortTTL(): Boolean {
        return getBooleanPreference(FORCED_SHORT_TTL, false)
    }

    override fun setForcedShortTTL(value: Boolean) {
        setBooleanPreference(FORCED_SHORT_TTL, value)
    }

    override var inAppReviewState: String?
        get() = getStringPreference(TextSecurePreferences.IN_APP_REVIEW_STATE, null)
        set(value) = setStringPreference(TextSecurePreferences.IN_APP_REVIEW_STATE, value)

    override var deprecationStateOverride: String?
        get() = getStringPreference(TextSecurePreferences.DEPRECATED_STATE_OVERRIDE, null)
        set(value) {
            if (value == null) {
                removePreference(TextSecurePreferences.DEPRECATED_STATE_OVERRIDE)
            } else {
                setStringPreference(TextSecurePreferences.DEPRECATED_STATE_OVERRIDE, value)
            }
        }

    override var deprecatedTimeOverride: ZonedDateTime?
        get() = getStringPreference(TextSecurePreferences.DEPRECATED_TIME_OVERRIDE, null)?.let(ZonedDateTime::parse)
        set(value) {
            if (value == null) {
                removePreference(TextSecurePreferences.DEPRECATED_TIME_OVERRIDE)
            } else {
                setStringPreference(TextSecurePreferences.DEPRECATED_TIME_OVERRIDE, value.toString())
            }
        }

    override var deprecatingStartTimeOverride: ZonedDateTime?
        get() = getStringPreference(TextSecurePreferences.DEPRECATING_START_TIME_OVERRIDE, null)?.let(ZonedDateTime::parse)
        set(value) {
            if (value == null) {
                removePreference(TextSecurePreferences.DEPRECATING_START_TIME_OVERRIDE)
            } else {
                setStringPreference(TextSecurePreferences.DEPRECATING_START_TIME_OVERRIDE, value.toString())
            }
        }
    override fun getDebugMessageFeatures(): Set<ProStatusManager.MessageProFeature> {
        return getStringSetPreference( TextSecurePreferences.DEBUG_MESSAGE_FEATURES, emptySet())
            ?.map { ProStatusManager.MessageProFeature.valueOf(it) }?.toSet() ?: emptySet()
    }

    override fun setDebugMessageFeatures(features: Set<ProStatusManager.MessageProFeature>) {
        setStringSetPreference(TextSecurePreferences.DEBUG_MESSAGE_FEATURES, features.map { it.name }.toSet())
    }

    override fun getDebugSubscriptionType(): DebugMenuViewModel.DebugSubscriptionStatus? {
        return getStringPreference(TextSecurePreferences.DEBUG_SUBSCRIPTION_STATUS, null)?.let {
            DebugMenuViewModel.DebugSubscriptionStatus.valueOf(it)
        }
    }

    override fun setDebugSubscriptionType(status: DebugMenuViewModel.DebugSubscriptionStatus?) {
        setStringPreference(TextSecurePreferences.DEBUG_SUBSCRIPTION_STATUS, status?.name)
        _events.tryEmit(TextSecurePreferences.DEBUG_SUBSCRIPTION_STATUS)
    }

    override fun getSubscriptionProvider(): String? {
        return getStringPreference(TextSecurePreferences.SUBSCRIPTION_PROVIDER, null)
    }

    override fun setSubscriptionProvider(provider: String) {
        setStringPreference(TextSecurePreferences.SUBSCRIPTION_PROVIDER, provider)
    }
}
