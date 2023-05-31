package org.thoughtcrime.securesms.preferences

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.setBooleanPreference
import org.thoughtcrime.securesms.permissions.Permissions

internal class CallToggleListener(
    private val context: Fragment,
    private val setCallback: Function1<Boolean?, Void>
) : Preference.OnPreferenceChangeListener {

    private fun requestMicrophonePermission() {
        Permissions.with(context)
            .request(Manifest.permission.RECORD_AUDIO)
            .onAllGranted {
                setBooleanPreference(
                    context.requireContext(),
                    TextSecurePreferences.CALL_NOTIFICATIONS_ENABLED,
                    true
                )
                setCallback(true)
            }
            .onAnyDenied { setCallback(false) }
            .execute()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (!(newValue as Boolean)) return true

        // check if we've shown the info dialog and check for microphone permissions
        val dialog = AlertDialog.Builder(
            ContextThemeWrapper(
                context.requireContext(), R.style.ThemeOverlay_Session_AlertDialog
            )
        )
            .setTitle(R.string.dialog_voice_video_title)
            .setMessage(R.string.dialog_voice_video_message)
            .setPositiveButton(R.string.dialog_link_preview_enable_button_title) { d: DialogInterface?, w: Int -> requestMicrophonePermission() }
            .setNegativeButton(R.string.cancel) { d: DialogInterface?, w: Int -> }
            .show()
        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        positiveButton.contentDescription = "Enable"
        return false
    }
}