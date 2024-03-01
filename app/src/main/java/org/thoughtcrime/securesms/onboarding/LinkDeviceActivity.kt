package org.thoughtcrime.securesms.onboarding

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.ui.AppTheme
import org.thoughtcrime.securesms.ui.OutlineButton
import org.thoughtcrime.securesms.ui.baseBold
import org.thoughtcrime.securesms.ui.colorDestructive
import java.util.concurrent.Executors
import javax.inject.Inject

private const val TAG = "LinkDeviceActivity"

@AndroidEntryPoint
@androidx.annotation.OptIn(ExperimentalGetImage::class)
class LinkDeviceActivity : BaseActionBarActivity() {

    @Inject
    lateinit var prefs: TextSecurePreferences

    val viewModel: LinkDeviceViewModel by viewModels()

    val preview = Preview.Builder().build()
    val selector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "Load Account"
        prefs.setHasViewedSeed(true)
        prefs.setConfigurationMessageSynced(false)
        prefs.setRestorationTime(System.currentTimeMillis())
        prefs.setLastProfileUpdateTime(0)

        lifecycleScope.launch {
            viewModel.eventFlow.collect {
                startLoadingActivity(it.mnemonic)
            }
        }

        ComposeView(this).apply {
            setContent {
                val state by viewModel.stateFlow.collectAsState()
                AppTheme {
                    LoadAccountScreen(state, viewModel::onChange, viewModel::tryPhrase)
                }
            }
        }.let(::setContentView)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun LoadAccountScreen(state: LinkDeviceState, onChange: (String) -> Unit = {}, onContinue: () -> Unit = {}) {
        val titles = listOf(R.string.activity_recovery_password, R.string.activity_link_device_scan_qr_code)
        val pagerState = rememberPagerState { titles.size }

        Column {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.height(48.dp)
            ) {
                val animationScope = rememberCoroutineScope()
                titles.forEachIndexed { i, it ->
                    Tab(i == pagerState.currentPage, onClick = { animationScope.launch { pagerState.animateScrollToPage(i) } }) {
                        Text(stringResource(id = it))
                    }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val title = titles[page]
                val localContext = LocalContext.current
                val cameraProvider = remember { ProcessCameraProvider.getInstance(localContext) }

                val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                val scanner = BarcodeScanning.getClient(options)

                runCatching {
                    when (title) {
                        R.string.activity_link_device_scan_qr_code -> {
                            LocalSoftwareKeyboardController.current?.hide()
                            cameraProvider.get().bindToLifecycle(LocalLifecycleOwner.current, selector, preview, buildAnalysisUseCase(scanner, viewModel::tryPhrase))
                        }
                        else -> cameraProvider.get().unbind(preview)
                    }
                }.onFailure { Log.e(TAG, "error binding camera", it) }
                when (title) {
                    R.string.activity_recovery_password -> RecoveryPassword(state, onChange, onContinue)
                    R.string.activity_link_device_scan_qr_code -> MaybeScanQrCode()
                }
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun MaybeScanQrCode() {
        Box(modifier = Modifier.fillMaxSize()) {
            val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

            if (cameraPermissionState.status.isGranted) {
                ScanQrCode(preview)
            } else if (cameraPermissionState.status.shouldShowRationale) {
                Column(
                    modifier = Modifier.align(Alignment.Center)
                        .padding(horizontal = 60.dp)
                ) {
                    Text(
                        "Camera Permission permanently denied. Configure in settings.",
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.size(20.dp))
                    OutlineButton(
                        text = "Settings",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Intent(ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }.let(::startActivity)
                    }
                }
            } else {
                OutlineButton(
                    text = "Grant Camera Permission",
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    cameraPermissionState.run { launchPermissionRequest() }
                }
            }
        }
    }
}

@Composable
fun ScanQrCode(preview: Preview) {
    Box {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { PreviewView(it).apply { preview.setSurfaceProvider(surfaceProvider) } }
        )

        Box(
            Modifier
                .aspectRatio(1f)
                .padding(20.dp)
                .clip(shape = RoundedCornerShape(20.dp))
                .background(Color(0x33ffffff))
                .align(Alignment.Center)
        )
    }
}

@Composable
fun RecoveryPassword(state: LinkDeviceState, onChange: (String) -> Unit = {}, onContinue: () -> Unit = {}) {
    Column(
        modifier = Modifier.padding(horizontal = 60.dp)
    ) {
        Spacer(Modifier.weight(1f))
        Row {
            Text("Recovery Password", style = MaterialTheme.typography.h4)
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_recovery_phrase),
                contentDescription = "",
            )
        }
        Spacer(Modifier.size(28.dp))
        Text("Enter your recovery password to load your account. If you haven't saved it, you can find it in your app settings.")
        Spacer(Modifier.size(24.dp))
        OutlinedTextField(
            value = state.recoveryPhrase,
            onValueChange = { onChange(it) },
            placeholder = { Text("Enter your recovery password") },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = state.error?.let { colorDestructive } ?: LocalContentColor.current.copy(LocalContentAlpha.current),
                focusedBorderColor = Color(0xff414141),
                unfocusedBorderColor = Color(0xff414141),
                cursorColor = LocalContentColor.current,
                placeholderColor = state.error?.let { colorDestructive } ?: MaterialTheme.colors.onSurface.copy(ContentAlpha.medium)
            ),
            singleLine = true,
            keyboardActions = KeyboardActions(
                onDone = { onContinue() },
                onGo = { onContinue() },
                onSearch = { onContinue() },
                onSend = { onContinue() },
            ),
            isError = state.error != null,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.size(12.dp))
        state.error?.let {
            Text(it, style = MaterialTheme.typography.baseBold, color = MaterialTheme.colors.error)
        }
        Spacer(Modifier.weight(2f))
        OutlineButton(
            text = stringResource(id = R.string.continue_2),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 64.dp, vertical = 20.dp)
                .width(200.dp)
        ) { onContinue() }
    }
}

fun Context.startLinkDeviceActivity() {
    Intent(this, LinkDeviceActivity::class.java).let(::startActivity)
}

@SuppressLint("UnsafeOptInUsageError")
private fun buildAnalysisUseCase(
    scanner: BarcodeScanner,
    onBarcodeScanned: (String) -> Unit
): ImageAnalysis = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build().apply {
        setAnalyzer(Executors.newSingleThreadExecutor(), Analyzer(scanner, onBarcodeScanned))
    }

class Analyzer(
    private val scanner: BarcodeScanner,
    private val onBarcodeScanned: (String) -> Unit
): Analyzer {
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        InputImage.fromMediaImage(
            image.image!!,
            image.imageInfo.rotationDegrees
        ).let(scanner::process).apply {
            addOnSuccessListener { barcodes ->
                barcodes.forEach {
                    it.takeIf { it.valueType == Barcode.TYPE_TEXT }?.rawValue?.let(onBarcodeScanned)
                }
            }
            addOnCompleteListener {
                image.close()
            }
        }
    }
}
