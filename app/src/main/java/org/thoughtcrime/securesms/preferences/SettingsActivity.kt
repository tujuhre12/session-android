package org.thoughtcrime.securesms.preferences

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivitySettingsBinding
import network.loki.messenger.libsession_util.util.UserPic
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.all
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.avatars.AvatarHelper
import org.session.libsession.avatars.ProfileContactPhoto
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.ProfileKeyUtil
import org.session.libsession.utilities.ProfilePictureUtilities
import org.session.libsession.utilities.SSKEnvironment.ProfileManagerProtocol
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.truncateIdForDisplay
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.avatar.AvatarSelection
import org.thoughtcrime.securesms.components.ProfilePictureView
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.home.PathActivity
import org.thoughtcrime.securesms.messagerequests.MessageRequestsActivity
import org.thoughtcrime.securesms.onboarding.recoverypassword.RecoveryPasswordActivity
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.preferences.appearance.AppearanceSettingsActivity
import org.thoughtcrime.securesms.profiles.ProfileMediaConstraints
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.Cell
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.ItemButton
import org.thoughtcrime.securesms.ui.ItemButtonWithDrawable
import org.thoughtcrime.securesms.ui.components.OutlineButton
import org.thoughtcrime.securesms.ui.components.TemporaryStateButton
import org.thoughtcrime.securesms.ui.destructiveButtonColors
import org.thoughtcrime.securesms.util.BitmapDecodingException
import org.thoughtcrime.securesms.util.BitmapUtil
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.push
import org.thoughtcrime.securesms.util.show
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject

private const val TAG = "SettingsActivity"

@AndroidEntryPoint
class SettingsActivity : PassphraseRequiredActionBarActivity() {

    @Inject
    lateinit var configFactory: ConfigFactory
    @Inject
    lateinit var prefs: TextSecurePreferences

    private lateinit var binding: ActivitySettingsBinding
    private var displayNameEditActionMode: ActionMode? = null
        set(value) { field = value; handleDisplayNameEditActionModeChanged() }
    private var tempFile: File? = null

    private val hexEncodedPublicKey: String get() = TextSecurePreferences.getLocalNumber(this)!!

    companion object {
        private const val SCROLL_STATE = "SCROLL_STATE"
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()

        binding.run {
            setupProfilePictureView(profilePictureView)
            profilePictureView.setOnClickListener { showEditProfilePictureUI() }
            ctnGroupNameSection.setOnClickListener { startActionMode(DisplayNameEditActionModeCallback()) }
            btnGroupNameDisplay.text = getDisplayName()
            publicKeyTextView.text = hexEncodedPublicKey
            versionTextView.text = String.format(getString(R.string.version_s), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }

        binding.composeView.setContent {
            AppTheme {
                Buttons()
            }
        }
    }

    private fun getDisplayName(): String =
        TextSecurePreferences.getProfileName(this) ?: truncateIdForDisplay(hexEncodedPublicKey)

    private fun setupProfilePictureView(view: ProfilePictureView) {
        view.apply {
            publicKey = hexEncodedPublicKey
            displayName = getDisplayName()
            isLarge = true
            update()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val scrollBundle = SparseArray<Parcelable>()
        binding.scrollView.saveHierarchyState(scrollBundle)
        outState.putSparseParcelableArray(SCROLL_STATE, scrollBundle)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState.getSparseParcelableArray<Parcelable>(SCROLL_STATE)?.let { scrollBundle ->
            binding.scrollView.restoreHierarchyState(scrollBundle)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings_general, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_qr_code -> {
                push<QRCodeActivity>()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            AvatarSelection.REQUEST_CODE_AVATAR -> {
                val outputFile = Uri.fromFile(File(cacheDir, "cropped"))
                val inputFile: Uri? = data?.data ?: tempFile?.let(Uri::fromFile)
                AvatarSelection.circularCropImage(this, inputFile, outputFile, R.string.CropImageActivity_profile_avatar)
            }
            AvatarSelection.REQUEST_CODE_CROP_IMAGE -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val profilePictureToBeUploaded = BitmapUtil.createScaledBytes(this@SettingsActivity, AvatarSelection.getResultUri(data), ProfileMediaConstraints()).bitmap
                        launch(Dispatchers.Main) {
                            updateProfile(true, profilePictureToBeUploaded)
                        }
                    } catch (e: BitmapDecodingException) {
                        Log.e(TAG, e)
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }
    // endregion

    // region Updating
    private fun handleDisplayNameEditActionModeChanged() {
        val isEditingDisplayName = this.displayNameEditActionMode != null

        binding.btnGroupNameDisplay.isInvisible = isEditingDisplayName
        binding.displayNameEditText.isInvisible = !isEditingDisplayName

        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (isEditingDisplayName) {
            binding.displayNameEditText.setText(binding.btnGroupNameDisplay.text)
            binding.displayNameEditText.selectAll()
            binding.displayNameEditText.requestFocus()
            inputMethodManager.showSoftInput(binding.displayNameEditText, 0)

            // Save the updated display name when the user presses enter on the soft keyboard
            binding.displayNameEditText.setOnEditorActionListener { v, actionId, event ->
                when (actionId) {
                    // Note: IME_ACTION_DONE is how we've configured the soft keyboard to respond,
                    // while IME_ACTION_UNSPECIFIED is what triggers when we hit enter on a
                    // physical keyboard.
                    EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_UNSPECIFIED -> {
                        saveDisplayName()
                        displayNameEditActionMode?.finish()
                        true
                    }
                    else -> false
                }
            }
        } else {
            inputMethodManager.hideSoftInputFromWindow(binding.displayNameEditText.windowToken, 0)
        }
    }

    private fun updateProfile(
        isUpdatingProfilePicture: Boolean,
        profilePicture: ByteArray? = null,
        displayName: String? = null
    ) {
        binding.loader.isVisible = true
        val promises = mutableListOf<Promise<*, Exception>>()
        if (displayName != null) {
            TextSecurePreferences.setProfileName(this, displayName)
            configFactory.user?.setName(displayName)
        }
        val encodedProfileKey = ProfileKeyUtil.generateEncodedProfileKey(this)
        if (isUpdatingProfilePicture) {
            if (profilePicture != null) {
                promises.add(ProfilePictureUtilities.upload(profilePicture, encodedProfileKey, this))
            } else {
                MessagingModuleConfiguration.shared.storage.clearUserPic()
            }
        }
        all(promises) successUi { // Do this on the UI thread so that it happens before the alwaysUi clause below
            val userConfig = configFactory.user
            if (isUpdatingProfilePicture) {
                AvatarHelper.setAvatar(this, Address.fromSerialized(TextSecurePreferences.getLocalNumber(this)!!), profilePicture)
                prefs.setProfileAvatarId(profilePicture?.let { SecureRandom().nextInt() } ?: 0 )
                ProfileKeyUtil.setEncodedProfileKey(this, encodedProfileKey)
                // new config
                val url = TextSecurePreferences.getProfilePictureURL(this)
                val profileKey = ProfileKeyUtil.getProfileKey(this)
                if (profilePicture == null) {
                    userConfig?.setPic(UserPic.DEFAULT)
                } else if (!url.isNullOrEmpty() && profileKey.isNotEmpty()) {
                    userConfig?.setPic(UserPic(url, profileKey))
                }
            }
            if (userConfig != null && userConfig.needsDump()) {
                configFactory.persist(userConfig, SnodeAPI.nowWithOffset)
            }
            ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(this@SettingsActivity)
        } alwaysUi {
            if (displayName != null) {
                binding.btnGroupNameDisplay.text = displayName
            }
            if (isUpdatingProfilePicture) {
                binding.profilePictureView.recycle() // Clear the cached image before updating
                binding.profilePictureView.update()
            }
            binding.loader.isVisible = false
        }
    }
    // endregion

    // region Interaction

    /**
     * @return true if the update was successful.
     */
    private fun saveDisplayName(): Boolean {
        val displayName = binding.displayNameEditText.text.toString().trim()
        if (displayName.isEmpty()) {
            Toast.makeText(this, R.string.activity_settings_display_name_missing_error, Toast.LENGTH_SHORT).show()
            return false
        }
        if (displayName.toByteArray().size > ProfileManagerProtocol.Companion.NAME_PADDED_LENGTH) {
            Toast.makeText(this, R.string.activity_settings_display_name_too_long_error, Toast.LENGTH_SHORT).show()
            return false
        }
        updateProfile(false, displayName = displayName)
        return true
    }

    private fun showEditProfilePictureUI() {
        showSessionDialog {
            title(R.string.activity_settings_set_display_picture)
            view(R.layout.dialog_change_avatar)
            button(R.string.activity_settings_upload) { startAvatarSelection() }
            if (prefs.getProfileAvatarId() != 0) {
                button(R.string.activity_settings_remove) { removeAvatar() }
            }
            cancelButton()
        }.apply {
            val profilePic = findViewById<ProfilePictureView>(R.id.profile_picture_view)
                ?.also(::setupProfilePictureView)

            val pictureIcon = findViewById<View>(R.id.ic_pictures)

            val recipient = Recipient.from(context, Address.fromSerialized(hexEncodedPublicKey), false)

            val photoSet = (recipient.contactPhoto as ProfileContactPhoto).avatarObject !in setOf("0", "")

            profilePic?.isVisible = photoSet
            pictureIcon?.isVisible = !photoSet
        }
    }

    private fun removeAvatar() {
        updateProfile(true)
    }

    private fun startAvatarSelection() {
        // Ask for an optional camera permission.
        Permissions.with(this)
            .request(Manifest.permission.CAMERA)
            .onAnyResult {
                tempFile = AvatarSelection.startAvatarSelection(this, false, true)
            }
            .execute()
    }
    // endregion

    private inner class DisplayNameEditActionModeCallback: ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.title = getString(R.string.activity_settings_display_name_edit_text_hint)
            mode.menuInflater.inflate(R.menu.menu_apply, menu)
            this@SettingsActivity.displayNameEditActionMode = mode
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            this@SettingsActivity.displayNameEditActionMode = null
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.applyButton -> {
                    if (this@SettingsActivity.saveDisplayName()) {
                        mode.finish()
                    }
                    return true
                }
            }
            return false
        }
    }

    @Composable
    fun Buttons() {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlineButton(
                    modifier = Modifier.weight(1f),
                    onClick = { sendInvitation() }
                ) {
                    Text(stringResource(R.string.share))
                }

                TemporaryStateButton { source, temporary ->
                    OutlineButton(
                        modifier = Modifier.weight(1f),
                        interactionSource = source,
                        onClick = { copyPublicKey() },
                    ) {
                        AnimatedVisibility(temporary) { Text(stringResource(R.string.copied)) }
                        AnimatedVisibility(!temporary) { Text(stringResource(R.string.copy)) }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            var hasPaths by remember {
                mutableStateOf(false)
            }

            CheckPaths { hasPaths = it }

            Cell {
                Column {
                    ItemButtonWithDrawable(R.string.activity_path_title, icon = if (hasPaths) R.drawable.ic_status else R.drawable.ic_path_yellow) { show<PathActivity>() }
                    Divider()
                    ItemButton(R.string.activity_settings_privacy_button_title, icon = R.drawable.ic_privacy_icon) { show<PrivacySettingsActivity>() }
                    Divider()
                    ItemButton(R.string.activity_settings_notifications_button_title, icon = R.drawable.ic_speaker, contentDescription = R.string.AccessibilityId_notifications) { show<NotificationSettingsActivity>() }
                    Divider()
                    ItemButton(R.string.activity_settings_conversations_button_title, icon = R.drawable.ic_conversations, contentDescription = R.string.AccessibilityId_conversations) { show<ChatSettingsActivity>() }
                    Divider()
                    ItemButton(R.string.activity_settings_message_requests_button_title, icon = R.drawable.ic_message_requests, contentDescription = R.string.AccessibilityId_message_requests) { show<MessageRequestsActivity>() }
                    Divider()
                    ItemButton(R.string.activity_settings_message_appearance_button_title, icon = R.drawable.ic_appearance, contentDescription = R.string.AccessibilityId_appearance) { show<AppearanceSettingsActivity>() }
                    Divider()
                    ItemButton(R.string.activity_settings_invite_button_title, icon = R.drawable.ic_invite_friend, contentDescription = R.string.AccessibilityId_invite_friend) { sendInvitation() }
                    Divider()
                    if (!prefs.getHidePassword()) {
                        ItemButton(R.string.sessionRecoveryPassword, icon = R.drawable.ic_recovery_phrase, contentDescription = R.string.AccessibilityId_recovery_password_menu_item) { show<RecoveryPasswordActivity>() }
                        Divider()
                    }
                    ItemButton(R.string.activity_settings_help_button, icon = R.drawable.ic_help, contentDescription = R.string.AccessibilityId_help) { show<HelpSettingsActivity>() }
                    Divider()
                    ItemButton(R.string.activity_settings_clear_all_data_button_title, colors = destructiveButtonColors(), icon = R.drawable.ic_clear_data, contentDescription = R.string.AccessibilityId_clear_data) { ClearAllDataDialog().show(supportFragmentManager, "Clear All Data Dialog") }
                }
            }
        }
    }

    @Composable
    fun CheckPaths(setHasPaths: (Boolean) -> Unit) {
        val context = LocalContext.current
        val manager = LocalBroadcastManager.getInstance(context)

        fun update() {
            lifecycleScope.launch {
                val paths = withContext(Dispatchers.IO) { OnionRequestAPI.paths }
                setHasPaths(paths.isNotEmpty())
            }
        }

        fun addReceiver(action: String): BroadcastReceiver = createReceiver { update() }.also { manager.registerReceiver(it, IntentFilter(action)) }

        val receivers = listOf("buildingPaths", "pathsBuilt").map(::addReceiver)

        DisposableEffect(Unit) {
            onDispose {
                receivers.forEach(manager::unregisterReceiver)
            }
        }
    }
}

fun createReceiver(update: () -> Unit) = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { update() }
}
