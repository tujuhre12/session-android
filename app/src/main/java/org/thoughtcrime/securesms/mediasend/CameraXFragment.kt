package org.thoughtcrime.securesms.mediasend

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.databinding.CameraxFragmentBinding
import org.session.libsession.utilities.MediaTypes
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.applySafeInsetsMargins
import org.thoughtcrime.securesms.util.setSafeOnClickListener
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class CameraXFragment : Fragment() {

    interface Controller {
        fun onImageCaptured(imageUri: Uri, size: Long, width: Int, height: Int)
        fun onCameraError()
    }

    private lateinit var binding: CameraxFragmentBinding

    private var callbacks: Controller? = null

    private lateinit var cameraController: LifecycleCameraController
    private lateinit var cameraExecutor: ExecutorService


    private lateinit var orientationListener: OrientationEventListener
    private var lastRotation: Int = Surface.ROTATION_0

    @Inject
    lateinit var prefs: TextSecurePreferences

    companion object {
        private const val TAG = "CameraXFragment"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = CameraxFragmentBinding.inflate(inflater, container, false)
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

        binding.cameraControlsSafeArea.applySafeInsetsMargins()

        binding.cameraCaptureButton.setSafeOnClickListener { takePhoto() }
        binding.cameraFlipButton.setSafeOnClickListener { flipCamera() }
        binding.cameraCloseButton.setSafeOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // keep track of orientation changes
        orientationListener = object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return

                val newRotation = when {
                    degrees in  45..134  -> Surface.ROTATION_270
                    degrees in 135..224  -> Surface.ROTATION_180
                    degrees in 225..314  -> Surface.ROTATION_90
                    else                 -> Surface.ROTATION_0
                }

                if (newRotation != lastRotation) {
                    lastRotation = newRotation
                    updateUiForRotation(newRotation)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        orientationListener.enable()
    }

    override fun onPause() {
        orientationListener.disable()
        super.onPause()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Controller) {
            callbacks = context
        } else {
            throw RuntimeException("$context must implement CameraXFragment.Controller")
        }
    }

    private fun updateUiForRotation(rotation: Int = lastRotation) {
        val angle = when (rotation) {
            Surface.ROTATION_0   -> 0f
            Surface.ROTATION_90  -> 90f
            Surface.ROTATION_180 -> 180f
            else                 -> 270f
        }

        binding.cameraFlipButton.animate()
            .rotation(angle)
            .setDuration(150)
            .start()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        // set up camera
        cameraController = LifecycleCameraController(requireContext()).apply {
            cameraSelector = prefs.getPreferredCameraDirection()
            setImageCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            setTapToFocusEnabled(true)
            setPinchToZoomEnabled(true)
        }

        // attach it to the view
        binding.previewView.controller = cameraController
        cameraController.bindToLifecycle(viewLifecycleOwner)

        // wait for initialisation to complete
        cameraController.initializationFuture.addListener({
            val hasFront = cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            val hasBack  = cameraController.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)

            binding.cameraFlipButton.visibility =
                if (hasFront && hasBack) View.VISIBLE else View.GONE
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        cameraController.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(img: ImageProxy) {
                    try {
                        val buffer   = img.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                        val w = img.width; val h = img.height
                        img.close()

                        val uri = BlobProvider.getInstance()
                            .forData(bytes)
                            .withMimeType(MediaTypes.IMAGE_JPEG)
                            .createForSingleSessionInMemory()

                        callbacks?.onImageCaptured(uri, bytes.size.toLong(), w, h)
                    } catch (t: Throwable) {
                        Log.e(TAG, "capture failed", t)
                        callbacks?.onCameraError()
                    }
                }
                override fun onError(e: ImageCaptureException) {
                    Log.e(TAG, "takePicture error", e)
                    callbacks?.onCameraError()
                }
            }
        )
    }

    private fun flipCamera() {
        val newSelector =
            if (cameraController.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                CameraSelector.DEFAULT_FRONT_CAMERA
            else
                CameraSelector.DEFAULT_BACK_CAMERA

        cameraController.cameraSelector = newSelector
        prefs.setPreferredCameraDirection(newSelector)

        // animate icon
        binding.cameraFlipButton.animate()
            .rotationBy(-180f)
            .setDuration(200)
            .start()
    }

    override fun onDestroyView() {
        cameraExecutor.shutdown()
        super.onDestroyView()
    }
}
