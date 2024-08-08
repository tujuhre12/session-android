package org.thoughtcrime.securesms.avatar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
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

    const val REQUEST_CODE_CROP_IMAGE: Int = CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE
    const val REQUEST_CODE_AVATAR: Int = REQUEST_CODE_CROP_IMAGE + 1

    /**
     * Returns result on [.REQUEST_CODE_CROP_IMAGE]
     */
    fun circularCropImage(
        activity: Activity,
        inputFile: Uri?,
        outputFile: Uri?,
        @StringRes title: Int
    ) {
        CropImage.activity(inputFile)
            .setGuidelines(CropImageView.Guidelines.ON)
            .setAspectRatio(1, 1)
            .setCropShape(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) CropImageView.CropShape.RECTANGLE else CropImageView.CropShape.OVAL)
            .setOutputUri(outputFile)
            .setAllowRotation(true)
            .setAllowFlipping(true)
            .setBackgroundColor(ContextCompat.getColor(activity, R.color.avatar_background))
            .setActivityTitle(activity.getString(title))
            .start(activity)
    }

    fun getResultUri(data: Intent?): Uri {
        return CropImage.getActivityResult(data).uri
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
        activity.startActivityForResult(chooserIntent, REQUEST_CODE_AVATAR)
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