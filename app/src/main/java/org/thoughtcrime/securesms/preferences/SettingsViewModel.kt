package org.thoughtcrime.securesms.preferences

import android.content.Context
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageView
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import network.loki.messenger.R
import network.loki.messenger.libsession_util.util.UserPic
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.avatars.AvatarHelper
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.ProfileKeyUtil
import org.session.libsession.utilities.ProfilePictureUtilities
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.truncateIdForDisplay
import org.session.libsignal.utilities.ExternalStorageUtil.getImageDir
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.NoExternalStorageException
import org.session.libsignal.utilities.Util.SECURE_RANDOM
import org.thoughtcrime.securesms.profiles.ProfileMediaConstraints
import org.thoughtcrime.securesms.util.BitmapDecodingException
import org.thoughtcrime.securesms.util.BitmapUtil
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.NetworkUtils
import java.io.File
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val prefs: TextSecurePreferences
) : ViewModel() {
    private val TAG = "SettingsViewModel"

    private var tempFile: File? = null

    val hexEncodedPublicKey: String get() = prefs.getLocalNumber() ?: ""

    private val _avatarDialogState: MutableStateFlow<AvatarDialogState> = MutableStateFlow(
        getDefaultAvatarDialogState()
    )
    val avatarDialogState: StateFlow<AvatarDialogState>
        get() = _avatarDialogState

    fun getDisplayName(): String =
        prefs.getProfileName() ?: truncateIdForDisplay(hexEncodedPublicKey)

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
                        _avatarDialogState.value =
                            AvatarDialogState.TempAvatar(profilePictureToBeUploaded)
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
        _avatarDialogState.value =getDefaultAvatarDialogState()
    }

    fun getDefaultAvatarDialogState() = if (hasAvatar()) AvatarDialogState.UserAvatar(Address.fromSerialized(hexEncodedPublicKey))
    else AvatarDialogState.NoAvatar

    //todo properly close dialog when done and make sure the state is the right one post change
    //todo make ripple effect round in dialog avatar picker
    //todo link other states, like making sure we show the actual avatar if there's already one
    //todo move upload and remove to VM
    //todo make buttons in dialog disabled
    //todo clean up the classes I made which aren't used now...

    sealed class AvatarDialogState() {
        object NoAvatar : AvatarDialogState()
        data class UserAvatar(val address: Address) : AvatarDialogState()
        data class TempAvatar(val data: ByteArray) : AvatarDialogState()
    }

    // Helper method used by updateProfilePicture and removeProfilePicture to sync it online
    /*private fun syncProfilePicture(profilePicture: ByteArray, onFail: () -> Unit) {
        binding.loader.isVisible = true

        // Grab the profile key and kick of the promise to update the profile picture
        val encodedProfileKey = ProfileKeyUtil.generateEncodedProfileKey(this)
        val updateProfilePicturePromise = ProfilePictureUtilities.upload(profilePicture, encodedProfileKey, this)

        // If the online portion of the update succeeded then update the local state
        updateProfilePicturePromise.successUi {

            // When removing the profile picture the supplied ByteArray is empty so we'll clear the local data
            if (profilePicture.isEmpty()) {
                MessagingModuleConfiguration.shared.storage.clearUserPic()
            }

            val userConfig = configFactory.user
            AvatarHelper.setAvatar(this, Address.fromSerialized(TextSecurePreferences.getLocalNumber(this)!!), profilePicture)
            prefs.setProfileAvatarId(SECURE_RANDOM.nextInt() )
            ProfileKeyUtil.setEncodedProfileKey(this, encodedProfileKey)

            // Attempt to grab the details we require to update the profile picture
            val url = prefs.getProfilePictureURL()
            val profileKey = ProfileKeyUtil.getProfileKey(this)

            // If we have a URL and a profile key then set the user's profile picture
            if (!url.isNullOrEmpty() && profileKey.isNotEmpty()) {
                userConfig?.setPic(UserPic(url, profileKey))
            }

            if (userConfig != null && userConfig.needsDump()) {
                configFactory.persist(userConfig, SnodeAPI.nowWithOffset)
            }

            ConfigurationMessageUtilities.forceSyncConfigurationNowIfNeeded(this@SettingsActivity)

            // Update our visuals
            binding.profilePictureView.recycle()
            binding.profilePictureView.update()
        }

        // If the sync failed then inform the user
        updateProfilePicturePromise.failUi { onFail() }

        // Finally, remove the loader animation after we've waited for the attempt to succeed or fail
        updateProfilePicturePromise.alwaysUi { binding.loader.isVisible = false }
    }

    private fun updateProfilePicture(profilePicture: ByteArray) {

        val haveNetworkConnection = NetworkUtils.haveValidNetworkConnection(this@SettingsActivity);
        if (!haveNetworkConnection) {
            Log.w(TAG, "Cannot update profile picture - no network connection.")
            Toast.makeText(this@SettingsActivity, R.string.profileErrorUpdate, Toast.LENGTH_LONG).show()
            return
        }

        val onFail: () -> Unit = {
            Log.e(TAG, "Sync failed when uploading profile picture.")
            Toast.makeText(this@SettingsActivity, R.string.profileErrorUpdate, Toast.LENGTH_LONG).show()
        }

        syncProfilePicture(profilePicture, onFail)
    }

    private fun removeProfilePicture() {

        val haveNetworkConnection = NetworkUtils.haveValidNetworkConnection(this@SettingsActivity);
        if (!haveNetworkConnection) {
            Log.w(TAG, "Cannot remove profile picture - no network connection.")
            Toast.makeText(this@SettingsActivity, R.string.profileDisplayPictureRemoveError, Toast.LENGTH_LONG).show()
            return
        }

        val onFail: () -> Unit = {
            Log.e(TAG, "Sync failed when removing profile picture.")
            Toast.makeText(this@SettingsActivity, R.string.profileDisplayPictureRemoveError, Toast.LENGTH_LONG).show()
        }

        val emptyProfilePicture = ByteArray(0)
        syncProfilePicture(emptyProfilePicture, onFail)
    }*/
}