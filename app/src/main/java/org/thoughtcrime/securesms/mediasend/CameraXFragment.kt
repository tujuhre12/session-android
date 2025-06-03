package org.thoughtcrime.securesms.mediasend

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
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

    private var _binding: CameraxFragmentBinding? = null
    private val binding get() = _binding!!

    private var controller: Controller? = null

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraSelector: CameraSelector
    private lateinit var cameraExecutor: ExecutorService

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
        _binding = CameraxFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cameraSelector = prefs.getPreferredCameraDirection()
        cameraExecutor = Executors.newSingleThreadExecutor()

        prefs.getPreferredCameraDirection()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        binding.cameraControlsSafeArea.applySafeInsetsMargins()

        //todo CAM handle orientation change and rotate flip camera button on landscape
        binding.cameraCaptureButton.setSafeOnClickListener { takePhoto() }
        binding.cameraFlipButton.setSafeOnClickListener { flipCamera() } //todo CAM hide if only one camera
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

            // hide flip button if we  only have one camera
            val hasBack  = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            binding.cameraFlipButton.visibility =
                if (hasBack && hasFront) View.VISIBLE else View.GONE

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
        val capture = imageCapture ?: return
        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes  = ByteArray(buffer.remaining()).also { buffer.get(it) }
                        val w = image.width
                        val h = image.height
                        image.close()

                        val blobUri = BlobProvider.getInstance()
                            .forData(bytes)
                            .withMimeType(MediaTypes.IMAGE_JPEG)
                            .createForSingleSessionOnDisk(requireContext()) {
                                Log.w(TAG, "Blob write failed", it)
                            }.get()

                        controller?.onImageCaptured(blobUri, bytes.size.toLong(), w, h)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Capture pipeline failed", t)
                        controller?.onCameraError()
                    }
                }

                override fun onError(e: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", e)
                    controller?.onCameraError()
                }
            }
        )
    }

    private fun flipCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
            CameraSelector.DEFAULT_FRONT_CAMERA
        else
            CameraSelector.DEFAULT_BACK_CAMERA

        prefs.setPreferredCameraDirection(cameraSelector)

        startCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }
}
