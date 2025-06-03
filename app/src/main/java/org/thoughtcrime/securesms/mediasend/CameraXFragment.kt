package org.thoughtcrime.securesms.mediasend

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import network.loki.messenger.databinding.CameraxFragmentBinding
import org.session.libsession.utilities.MediaTypes
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.setSafeOnClickListener
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraXFragment : Fragment() {

    interface Controller {
        fun onImageCaptured(imageUri: Uri, width: Int, height: Int)
        fun onCameraError()
    }

    private var _binding: CameraxFragmentBinding? = null
    private val binding get() = _binding!!

    private var controller: Controller? = null

    private var imageCapture: ImageCapture? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA //todo CAM use text prefs and save it back to there when flipping
    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val TAG = "CameraXFragment"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = CameraxFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        //todo CAM handle orientation change
        binding.cameraCaptureButton.setSafeOnClickListener { takePhoto() } //todo CAM optimise layout
        binding.cameraFlipButton.setSafeOnClickListener { flipCamera() } //todo CAM hideif only one camera
        binding.cameraCloseButton.setSafeOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Controller) {
            controller = context
        } else {
            throw RuntimeException("$context must implement CameraXFragment.Controller")
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis())
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TempCameraX")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    controller?.onCameraError()
                }

                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    val tempUri = result.savedUri ?: run {
                        controller?.onCameraError(); return
                    }

                    cameraExecutor.execute { wrapInBlobAndReturn(tempUri) }
                }
            }
        )
    }

    private fun wrapInBlobAndReturn(tempUri: Uri) {
        try {
            val resolver = requireContext().contentResolver
            val size = resolver.openAssetFileDescriptor(tempUri, "r")?.use { it.length } ?: -1L

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(tempUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            val width  = bounds.outWidth
            val height = bounds.outHeight

            val input  = resolver.openInputStream(tempUri) ?: throw IOException("open failed")
            val blobUri = BlobProvider.getInstance()
                .forData(input, size)
                .withMimeType(MediaTypes.IMAGE_JPEG)
                .createForSingleSessionOnDisk(requireContext()) { e: IOException? ->
                    org.session.libsignal.utilities.Log.w(TAG, "Failed to write to disk.", e)
                }
                .get()

            resolver.delete(tempUri, null, null)

            controller?.onImageCaptured(
                blobUri,
                width,
                height
            )
        } catch (t: Throwable) {
            Log.e(TAG, "wrapInBlob failed", t)
            controller?.onCameraError()
        }
    }

    private fun flipCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
            CameraSelector.DEFAULT_FRONT_CAMERA
        else
            CameraSelector.DEFAULT_BACK_CAMERA

        startCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }
}
