package org.thoughtcrime.securesms

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.squareup.phrase.Phrase
import network.loki.messenger.R
import org.session.libsession.utilities.StringSubstitutionConstants.APP_NAME_KEY

class MissingMicrophonePermissionDialog {
    companion object {
        @JvmStatic
        fun show(context: Context) = context.showSessionDialog {
            title(R.string.permissionsMicrophone)
            text(
                Phrase.from(context, R.string.permissionsMicrophoneAccessRequired)
                    .put(APP_NAME_KEY, context.getString(R.string.app_name))
                    .format().toString())
            button(R.string.sessionSettings, R.string.AccessibilityId_sessionSettings) {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val uri = Uri.fromParts("package", context.packageName, null)
                intent.setData(uri)
                context.startActivity(intent)
            }
            cancelButton()
        }
    }
}
