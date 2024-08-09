package org.thoughtcrime.securesms.avatar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import network.loki.messenger.R
import org.session.libsignal.utilities.ExternalStorageUtil.getImageDir
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.NoExternalStorageException
import org.thoughtcrime.securesms.util.FileProviderUtil
import org.thoughtcrime.securesms.util.IntentUtils
import java.io.File
import java.io.IOException
import java.util.LinkedList

object AvatarSelection {
    private val TAG: String = AvatarSelection::class.java.simpleName

    const val REQUEST_CODE_IMAGE_PICK: Int = 888

    /**
     * Returns result on [.REQUEST_CODE_CROP_IMAGE]
     */
    fun circularCropImage(
        activity: Activity,
        launcher: ActivityResultLauncher<CropImageContractOptions>,
        inputFile: Uri?,
        outputFile: Uri?,
        @StringRes title: Int,
        bgColor: Int
    ) {
        launcher.launch(
            options(inputFile) {
                setGuidelines(CropImageView.Guidelines.ON)
                setAspectRatio(1, 1)
                setCropShape(CropImageView.CropShape.OVAL)
                setOutputUri(outputFile)
                setAllowRotation(true)
                setAllowFlipping(true)
                setBackgroundColor(ContextCompat.getColor(activity, R.color.avatar_background))
                setToolbarColor(bgColor)
                setActivityBackgroundColor(bgColor)
                setActivityTitle(activity.getString(title))
            }
        )
    }

    /**
     * Returns result on [.REQUEST_CODE_AVATAR]
     *
     * @return Temporary capture file if created.
     */
    fun startAvatarSelection(
        activity: Activity,
        includeClear: Boolean,
        attemptToIncludeCamera: Boolean
    ): File? {
        var captureFile: File? = null
        val hasCameraPermission = ContextCompat
            .checkSelfPermission(
                activity,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        if (attemptToIncludeCamera && hasCameraPermission) {
            try {
                captureFile = File.createTempFile("avatar-capture", ".jpg", getImageDir(activity))
            } catch (e: IOException) {
                Log.e("Cannot reserve a temporary avatar capture file.", e)
            } catch (e: NoExternalStorageException) {
                Log.e("Cannot reserve a temporary avatar capture file.", e)
            }
        }

        val chooserIntent = createAvatarSelectionIntent(activity, captureFile, includeClear)
        activity.startActivityForResult(chooserIntent, REQUEST_CODE_IMAGE_PICK)
        return captureFile
    }

    private fun createAvatarSelectionIntent(
        context: Context,
        tempCaptureFile: File?,
        includeClear: Boolean
    ): Intent {
        val extraIntents: MutableList<Intent> = LinkedList()
        var galleryIntent = Intent(Intent.ACTION_PICK)
        galleryIntent.setDataAndType(MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*")

        if (!IntentUtils.isResolvable(context, galleryIntent)) {
            galleryIntent = Intent(Intent.ACTION_GET_CONTENT)
            galleryIntent.setType("image/*")
        }

        if (tempCaptureFile != null) {
            val uri = FileProviderUtil.getUriFor(context, tempCaptureFile)
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            cameraIntent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            extraIntents.add(cameraIntent)
        }

        if (includeClear) {
            extraIntents.add(Intent("network.loki.securesms.action.CLEAR_PROFILE_PHOTO"))
        }

        val chooserIntent = Intent.createChooser(
            galleryIntent,
            context.getString(R.string.CreateProfileActivity_profile_photo)
        )

        if (!extraIntents.isEmpty()) {
            chooserIntent.putExtra(
                Intent.EXTRA_INITIAL_INTENTS,
                extraIntents.toTypedArray<Intent>()
            )
        }

        return chooserIntent
    }
}