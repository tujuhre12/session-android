package org.thoughtcrime.securesms.preferences

import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.view.ContextThemeWrapper
import androidx.preference.Preference
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.isPasswordDisabled
import org.session.libsession.utilities.TextSecurePreferences.Companion.setScreenLockEnabled
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.CallNotificationBuilder.Companion.areNotificationsEnabled
import org.thoughtcrime.securesms.util.IntentUtils

class PrivacySettingsPreferenceFragment : ListSummaryPreferenceFragment() {
    override fun onCreate(paramBundle: Bundle?) {
        super.onCreate(paramBundle)
        findPreference<Preference>(TextSecurePreferences.SCREEN_LOCK)!!.onPreferenceChangeListener =
            ScreenLockListener()
        findPreference<Preference>(TextSecurePreferences.READ_RECEIPTS_PREF)!!.onPreferenceChangeListener =
            ReadReceiptToggleListener()
        findPreference<Preference>(TextSecurePreferences.TYPING_INDICATORS)!!.onPreferenceChangeListener =
            TypingIndicatorsToggleListener()
        findPreference<Preference>(TextSecurePreferences.LINK_PREVIEWS)!!.onPreferenceChangeListener =
            LinkPreviewToggleListener()
        findPreference<Preference>(TextSecurePreferences.CALL_NOTIFICATIONS_ENABLED)!!.onPreferenceChangeListener =
            CallToggleListener(this) { setCall(it) }
        initializeVisibility()
    }

    private fun setCall(isEnabled: Boolean) {
        (findPreference<Preference>(TextSecurePreferences.CALL_NOTIFICATIONS_ENABLED) as SwitchPreferenceCompat?)!!.isChecked =
            isEnabled
        if (isEnabled && !areNotificationsEnabled(requireActivity())) {
            // show a dialog saying that calls won't work properly if you don't have notifications on at a system level
            AlertDialog.Builder(
                ContextThemeWrapper(
                    requireActivity(),
                    R.style.ThemeOverlay_Session_AlertDialog
                )
            )
                .setTitle(R.string.CallNotificationBuilder_system_notification_title)
                .setMessage(R.string.CallNotificationBuilder_system_notification_message)
                .setPositiveButton(R.string.activity_notification_settings_title) { d: DialogInterface, w: Int ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                        if (IntentUtils.isResolvable(requireContext(), settingsIntent)) {
                            startActivity(settingsIntent)
                        }
                    } else {
                        val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID))
                        if (IntentUtils.isResolvable(requireContext(), settingsIntent)) {
                            startActivity(settingsIntent)
                        }
                    }
                    d.dismiss()
                }
                .setNeutralButton(R.string.dismiss) { d: DialogInterface, w: Int ->
                    // do nothing, user might have broken notifications
                    d.dismiss()
                }
                .show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_app_protection)
    }

    override fun onResume() {
        super.onResume()
    }

    private fun initializeVisibility() {
        if (isPasswordDisabled(context!!)) {
            val keyguardManager =
                context!!.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!keyguardManager.isKeyguardSecure) {
                (findPreference<Preference>(TextSecurePreferences.SCREEN_LOCK) as SwitchPreferenceCompat?)!!.isChecked =
                    false
                findPreference<Preference>(TextSecurePreferences.SCREEN_LOCK)!!.isEnabled =
                    false
            }
        } else {
            findPreference<Preference>(TextSecurePreferences.SCREEN_LOCK)!!.isVisible = false
            findPreference<Preference>(TextSecurePreferences.SCREEN_LOCK_TIMEOUT)!!.isVisible =
                false
        }
    }

    private inner class ScreenLockListener : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            val enabled = newValue as Boolean
            setScreenLockEnabled(context!!, enabled)
            val intent = Intent(context, KeyCachingService::class.java)
            intent.action = KeyCachingService.LOCK_TOGGLED_EVENT
            context!!.startService(intent)
            return true
        }
    }

    private inner class ReadReceiptToggleListener : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            return true
        }
    }

    private inner class TypingIndicatorsToggleListener : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            val enabled = newValue as Boolean
            if (!enabled) {
                ApplicationContext.getInstance(requireContext()).typingStatusRepository.clear()
            }
            return true
        }
    }

    private inner class LinkPreviewToggleListener : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            return true
        }
    }
}