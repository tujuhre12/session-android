package org.thoughtcrime.securesms.preferences

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageView
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.UserPic
import org.session.libsession.avatars.AvatarHelper
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.ProfileKeyUtil
import org.session.libsession.utilities.ProfilePictureUtilities
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.UsernameUtils
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.ExternalStorageUtil.getImageDir
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.NoExternalStorageException
import org.session.libsignal.utilities.Util.SECURE_RANDOM
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.pro.ProStatusManager
import org.thoughtcrime.securesms.profiles.ProfileMediaConstraints
import org.thoughtcrime.securesms.util.AvatarUIData
import org.thoughtcrime.securesms.util.AvatarUtils
import org.thoughtcrime.securesms.util.BitmapDecodingException
import org.thoughtcrime.securesms.util.BitmapUtil
import org.thoughtcrime.securesms.util.NetworkConnectivity
import java.io.File
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: TextSecurePreferences,
    private val configFactory: ConfigFactory,
    private val connectivity: NetworkConnectivity,
    private val usernameUtils: UsernameUtils,
    private val avatarUtils: AvatarUtils,
    private val proStatusManager: ProStatusManager
) : ViewModel() {
    private val TAG = "SettingsViewModel"

    private var tempFile: File? = null

    val hexEncodedPublicKey: String = prefs.getLocalNumber() ?: ""

    private val userRecipient by lazy {
        Recipient.from(context, Address.fromSerialized(hexEncodedPublicKey), false)
    }

    private val _uiState = MutableStateFlow(UIState(
        recoveryHidden = prefs.getHidePassword(),
        isPro = proStatusManager.isCurrentUserPro(),
        isPostPro = proStatusManager.isPostPro()
    ))
    val uiState: StateFlow<UIState>
        get() = _uiState

    init {
        updateAvatar()

        // set default dialog ui
        viewModelScope.launch {
            _uiState.update { it.copy(avatarDialogState = getDefaultAvatarDialogState()) }
        }

        viewModelScope.launch {
            proStatusManager.proStatus.collect { isPro ->
                _uiState.update { it.copy(isPro = isPro) }
            }
        }

        viewModelScope.launch {
            proStatusManager.postProLaunchStatus.collect { postPro ->
                _uiState.update { it.copy(isPostPro = postPro) }
            }
        }
    }

    private fun updateAvatar(){
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(avatarData = avatarUtils.getUIDataFromRecipient(userRecipient)) }
        }
    }

    fun getDisplayName(): String = usernameUtils.getCurrentUsernameWithAccountIdFallback()

    fun hasAvatar() = prefs.getProfileAvatarId() != 0

    fun createTempFile(): File? {
        try {
            tempFile = File.createTempFile("avatar-capture", ".jpg", getImageDir(context))
        } catch (e: IOException) {
            Log.e("Cannot reserve a temporary avatar capture file.", e)
        } catch (e: NoExternalStorageException) {
            Log.e("Cannot reserve a temporary avatar capture file.", e)
        }

        return tempFile
    }

    fun getTempFile() = tempFile

    fun onAvatarPicked(result: CropImageView.CropResult) {
        when {
            result.isSuccessful -> {
                Log.i(TAG, result.getUriFilePath(context).toString())

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val profilePictureToBeUploaded =
                            BitmapUtil.createScaledBytes(
                                context,
                                result.getUriFilePath(context).toString(),
                                ProfileMediaConstraints()
                            ).bitmap

                        // update dialog with temporary avatar (has not been saved/uploaded yet)
                        _uiState.update { it.copy(avatarDialogState = AvatarDialogState.TempAvatar(profilePictureToBeUploaded, hasAvatar())) }
                    } catch (e: BitmapDecodingException) {
                        Log.e(TAG, e)
                    }
                }
            }

            result is CropImage.CancelledResult -> {
                Log.i(TAG, "Cropping image was cancelled by the user")
            }

            else -> {
                Log.e(TAG, "Cropping image failed")
            }
        }
    }

    fun onAvatarDialogDismissed() {
        viewModelScope.launch {
            _uiState.update { it.copy(
                avatarDialogState = getDefaultAvatarDialogState(),
                showAvatarDialog = false
            ) } }
    }

    private suspend fun getDefaultAvatarDialogState() = if (hasAvatar()) AvatarDialogState.UserAvatar(
        avatarUtils.getUIDataFromRecipient(userRecipient)
    )
    else AvatarDialogState.NoAvatar

    fun saveAvatar() {
        val tempAvatar = (uiState.value.avatarDialogState as? AvatarDialogState.TempAvatar)?.data
            ?: return Toast.makeText(context, R.string.profileErrorUpdate, Toast.LENGTH_LONG).show()

        if (!hasNetworkConnection()) {
            Log.w(TAG, "Cannot update profile picture - no network connection.")
            Toast.makeText(context, R.string.profileErrorUpdate, Toast.LENGTH_LONG).show()
            return
        }

        val onFail: () -> Unit = {
            Log.e(TAG, "Sync failed when uploading profile picture.")
            Toast.makeText(context, R.string.profileErrorUpdate, Toast.LENGTH_LONG).show()
        }

        syncProfilePicture(tempAvatar, onFail)
    }


    fun removeAvatar() {
        val haveNetworkConnection = connectivity.networkAvailable.value
        if (!haveNetworkConnection) {
            Log.w(TAG, "Cannot remove profile picture - no network connection.")
            Toast.makeText(context, R.string.profileDisplayPictureRemoveError, Toast.LENGTH_LONG).show()
            return
        }

        val onFail: () -> Unit = {
            Log.e(TAG, "Sync failed when removing profile picture.")
            Toast.makeText(context, R.string.profileDisplayPictureRemoveError, Toast.LENGTH_LONG).show()
        }

        val emptyProfilePicture = ByteArray(0)
        syncProfilePicture(emptyProfilePicture, onFail)
    }

    // Helper method used by updateProfilePicture and removeProfilePicture to sync it online
    private fun syncProfilePicture(profilePicture: ByteArray, onFail: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(showLoader = true) }

            try {
                // Grab the profile key and kick of the promise to update the profile picture
                val encodedProfileKey = ProfileKeyUtil.generateEncodedProfileKey(context)
                val url = ProfilePictureUtilities.upload(profilePicture, encodedProfileKey, context)

                // If the online portion of the update succeeded then update the local state
                AvatarHelper.setAvatar(
                    context,
                    Address.fromSerialized(TextSecurePreferences.getLocalNumber(context)!!),
                    profilePicture
                )

                // When removing the profile picture the supplied ByteArray is empty so we'll clear the local data
                if (profilePicture.isEmpty()) {
                    MessagingModuleConfiguration.shared.storage.clearUserPic()

                    // update dialog state
                    _uiState.update { it.copy(avatarDialogState = AvatarDialogState.NoAvatar) }
                } else {
                    prefs.setProfileAvatarId(SECURE_RANDOM.nextInt())
                    ProfileKeyUtil.setEncodedProfileKey(context, encodedProfileKey)

                    // Attempt to grab the details we require to update the profile picture
                    val profileKey = ProfileKeyUtil.getProfileKey(context)

                    // If we have a URL and a profile key then set the user's profile picture
                    if (url.isNotEmpty() && profileKey.isNotEmpty()) {
                        configFactory.withMutableUserConfigs {
                            it.userProfile.setPic(UserPic(url, profileKey))
                        }
                    }

                    // update dialog state
                    _uiState.update { it.copy(avatarDialogState = AvatarDialogState.UserAvatar(avatarUtils.getUIDataFromRecipient(userRecipient))) }
                }

            } catch (e: Exception){ // If the sync failed then inform the user
                Log.d(TAG, "Error syncing avatar: $e")
                withContext(Dispatchers.Main) {
                    onFail()
                }
            }

            // Finally update the main avatar
            updateAvatar()
            // And remove the loader animation after we've waited for the attempt to succeed or fail
            _uiState.update { it.copy(showLoader = false) }
        }
    }

    fun updateName(displayName: String) {
        usernameUtils.saveCurrentUserName(displayName)
    }

    fun permanentlyHidePassword() {
        //todo we can simplify this once we expose all our sharedPrefs as flows
        prefs.setHidePassword(true)
        _uiState.update { it.copy(recoveryHidden = true) }
    }

    fun hasNetworkConnection(): Boolean = connectivity.networkAvailable.value

    fun showUrlDialog(url: String) {
        _uiState.update { it.copy(showUrlDialog = url) }
    }
    fun hideUrlDialog() {
        _uiState.update { it.copy(showUrlDialog = null) }
    }

    fun showAvatarDialog() {
        _uiState.update { it.copy(showAvatarDialog = true) }
    }

    fun showAvatarPickerOptions(showCamera: Boolean) {
        _uiState.update { it.copy(
            showAvatarPickerOptions = true,
            showAvatarPickerOptionCamera = showCamera
        ) }
    }
    fun hideAvatarPickerOptions() {
        _uiState.update { it.copy(showAvatarPickerOptions = false) }

    }

    fun showAnimatedProCTA() {
        _uiState.update { it.copy(showAnimatedProCTA = true) }
    }
    fun hideAnimatedProCTA() {
        _uiState.update { it.copy(showAnimatedProCTA = false) }
    }

    fun goToProUpgradeScreen() {
        // hide dialog
        hideAnimatedProCTA()

        // to go Pro upgrade screen
        //todo PRO go to screen once it exists
    }

    sealed class AvatarDialogState() {
        object NoAvatar : AvatarDialogState()
        data class UserAvatar(val data: AvatarUIData) : AvatarDialogState()
        data class TempAvatar(
            val data: ByteArray,
            val hasAvatar: Boolean // true if the user has an avatar set already but is in this temp state because they are trying out a new avatar
        ) : AvatarDialogState()
    }

    data class UIState(
        val showLoader: Boolean = false,
        val avatarDialogState: AvatarDialogState = AvatarDialogState.NoAvatar,
        val avatarData: AvatarUIData? = null,
        val recoveryHidden: Boolean,
        val showUrlDialog: String? = null,
        val showAvatarDialog: Boolean = false,
        val showAvatarPickerOptionCamera: Boolean = false,
        val showAvatarPickerOptions: Boolean = false,
        val showAnimatedProCTA: Boolean = false,
        val isPro: Boolean,
        val isPostPro: Boolean
    )
}