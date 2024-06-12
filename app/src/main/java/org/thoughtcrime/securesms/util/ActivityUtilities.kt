package org.thoughtcrime.securesms.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.ui.primaryBlue
import org.thoughtcrime.securesms.ui.primaryGreen
import org.thoughtcrime.securesms.ui.primaryOrange
import org.thoughtcrime.securesms.ui.primaryPink
import org.thoughtcrime.securesms.ui.primaryPurple
import org.thoughtcrime.securesms.ui.primaryRed
import org.thoughtcrime.securesms.ui.primaryYellow

fun BaseActionBarActivity.setUpActionBarSessionLogo(hideBackButton: Boolean = false) {
    val actionbar = supportActionBar!!

    actionbar.setDisplayShowHomeEnabled(false)
    actionbar.setDisplayShowTitleEnabled(false)
    actionbar.setDisplayHomeAsUpEnabled(false)
    actionbar.setHomeButtonEnabled(false)

    actionbar.setCustomView(R.layout.session_logo_action_bar_content)
    actionbar.setDisplayShowCustomEnabled(true)

    val rootView: Toolbar = actionbar.customView!!.parent as Toolbar
    rootView.setPadding(0,0,0,0)
    rootView.setContentInsetsAbsolute(0,0);

    val backButton = actionbar.customView!!.findViewById<View>(R.id.back_button)
    if (hideBackButton) {
        backButton.visibility = View.GONE
    } else {
        backButton.visibility = View.VISIBLE
        backButton.setOnClickListener {
            onSupportNavigateUp()
        }
    }
}

val AppCompatActivity.defaultSessionRequestCode: Int
    get() = 42

fun AppCompatActivity.push(intent: Intent, isForResult: Boolean = false) {
    if (isForResult) {
        startActivityForResult(intent, defaultSessionRequestCode)
    } else {
        startActivity(intent)
    }
    overridePendingTransition(R.anim.slide_from_right, R.anim.fade_scale_out)
}

fun AppCompatActivity.show(intent: Intent, isForResult: Boolean = false) {
    if (isForResult) {
        startActivityForResult(intent, defaultSessionRequestCode)
    } else {
        startActivity(intent)
    }
    overridePendingTransition(R.anim.slide_from_bottom, R.anim.fade_scale_out)
}

interface ActivityDispatcher {
    companion object {
        const val SERVICE = "ActivityDispatcher_SERVICE"
        @SuppressLint("WrongConstant")
        fun get(context: Context) = context.getSystemService(SERVICE) as? ActivityDispatcher
    }
    fun dispatchIntent(body: (Context)->Intent?)
    fun showDialog(dialogFragment: DialogFragment, tag: String? = null)
}

fun TextSecurePreferences.themeState(): ThemeState {
    val themeStyle = getThemeStyle().getThemeStyle()
    val accentStyle = getAccentColorStyle() ?: themeStyle.getDefaultAccentColor()
    val followSystem = getFollowSystemSettings()
    return ThemeState(
        themeStyle,
        accentStyle,
        followSystem
    )
}

@StyleRes
fun String.getThemeStyle(): Int = when (this) {
    TextSecurePreferences.CLASSIC_DARK -> R.style.Classic_Dark
    TextSecurePreferences.CLASSIC_LIGHT -> R.style.Classic_Light
    TextSecurePreferences.OCEAN_DARK -> R.style.Ocean_Dark
    TextSecurePreferences.OCEAN_LIGHT -> R.style.Ocean_Light
    else -> throw NullPointerException("The style [$this] is not supported")
}

@StyleRes
fun Int.getDefaultAccentColor(): Int = when (this) {
    R.style.Ocean_Dark, R.style.Ocean_Light -> R.style.PrimaryBlue
    else -> R.style.PrimaryGreen
}

data class ThemeState (
    @StyleRes val theme: Int,
    @StyleRes val accentStyle: Int,
    val followSystem: Boolean
) {
    val isLight = theme in setOf(R.style.Classic_Light, R.style.Ocean_Light)
    val isClassic = theme in setOf(R.style.Classic_Light, R.style.Classic_Dark)

    val accent = when(accentStyle) {
        R.style.PrimaryGreen -> primaryGreen
        R.style.PrimaryBlue -> primaryBlue
        R.style.PrimaryPurple -> primaryPurple
        R.style.PrimaryPink -> primaryPink
        R.style.PrimaryRed -> primaryRed
        R.style.PrimaryOrange -> primaryOrange
        R.style.PrimaryYellow -> primaryYellow
        else -> primaryGreen
    }
}

inline fun <reified T: Activity> Activity.show() = Intent(this, T::class.java).also(::startActivity).also { overridePendingTransition(R.anim.slide_from_bottom, R.anim.fade_scale_out) }
inline fun <reified T: Activity> Activity.push() = Intent(this, T::class.java).also(::startActivity).also { overridePendingTransition(R.anim.slide_from_right, R.anim.fade_scale_out) }
inline fun <reified T: Activity> Context.start() = Intent(this, T::class.java).also(::startActivity)
inline fun <reified T: Activity> Context.start(modify: Intent.() -> Unit) = Intent(this, T::class.java).also(modify).also(::startActivity)
