@file:OptIn(ExperimentalPermissionsApi::class)

package me.dcnick3.baam.ui.camera

import android.graphics.Matrix
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import me.dcnick3.baam.api.parseChallenge
import me.dcnick3.baam.ui.ErrorBar
import me.dcnick3.baam.ui.ErrorBarState
import me.dcnick3.baam.utils.qr.QrStatus
import me.dcnick3.baam.utils.qr.QrTracker
import me.dcnick3.baam.utils.qr.QrTrackerClient
import me.dcnick3.baam.utils.qr.QrTrackerUpdate
import me.dcnick3.baam.viewmodel.ApiViewModel
import me.dcnick3.baam.viewmodel.ChallengeResult
import me.dcnick3.pqrs.PqrsScanner
import me.dcnick3.pqrs.model.QrCode
import java.util.concurrent.Executors
import kotlin.time.Duration

//private const val TAG = "ScannerScreen"
private const val DEBUG_OVERLAY = false

private class ImageAnalyzer(
    trackerClient: QrTrackerClient,
    overlay: GraphicOverlay,
    dotSize: Float,
) : ImageAnalysis.Analyzer {
    private val pqrsScanner = PqrsScanner()
    private val tracker = QrTracker(trackerClient, overlay, dotSize)
    private var transform: Matrix? = null

    private val _analysisTime = MutableStateFlow(Duration.ZERO)
    private val _analysisResolution = MutableStateFlow(Size(0, 0))
    val analysisTime = _analysisTime.asStateFlow()
    val analysisResolution = _analysisResolution.asStateFlow()

    override fun analyze(image: ImageProxy) {
        val timeStart = Clock.System.now()

        val width = image.width
        val height = image.height

        val plane = image.planes[0]
        val result = pqrsScanner.scanGrayscale(
            plane.buffer,
            width,
            height,
            plane.pixelStride,
            plane.rowStride,
        )

        // after done, release the ImageProxy object
        image.close()

        tracker.update(
            QrTrackerUpdate(
                scanResult = result,
                transform = transform,
                analysisSize = Size(width, height),
            )
        )

        if (DEBUG_OVERLAY) {
            _analysisTime.value = Clock.System.now() - timeStart
            _analysisResolution.value = Size(width, height)
        }
    }

    override fun getDefaultTargetResolution(): Size {
        return Size(640, 480)
    }

    override fun getTargetCoordinateSystem(): Int {
        return COORDINATE_SYSTEM_VIEW_REFERENCED
    }

    override fun updateTransform(matrix: Matrix?) {
        transform = matrix
    }
}

@Composable
fun ScannerScreen(navController: NavController, vm: ApiViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val cameraController: LifecycleCameraController =
        remember { LifecycleCameraController(context) }
    val cameraExecutor = Executors.newSingleThreadExecutor()

    val scope = rememberCoroutineScope()
    val errorState = remember { ErrorBarState(scope) }

    val overlay = GraphicOverlay(context, null)
    val trackerClient = remember {
        object : QrTrackerClient {
            val qrStates = mutableStateMapOf<String, QrStatus>()

            override fun onNewQr(tracker: QrTracker, qr: QrCode) {
                Log.i("TAG", "New QR: ${qr.content}")
                qrStates[qr.content] = QrStatus.Checking

                when (val challenge = parseChallenge(qr.content)) {
                    null -> {
                        tracker.qrInvalid(qr)
                    }

                    else -> {
                        scope.launch {
                            when (val result = vm.submitChallenge(challenge)) {
                                is ChallengeResult.Success -> {
                                    tracker.qrValid(qr)
                                    // hack to avoid double-open of the success page
                                    if (navController.currentBackStackEntry?.destination?.route?.startsWith(
                                            "scan"
                                        ) == true
                                    )
                                        navController.navigate("success/${result.code}")
                                }

                                ChallengeResult.Unacceptable -> {
                                    tracker.qrInvalid(qr)
                                }

                                is ChallengeResult.Error -> {
                                    errorState.setError(result.error)
                                    tracker.qrInvalid(qr)
                                }
                            }
                        }
                    }
                }
            }

            override fun onUpdatedQr(tracker: QrTracker, qr: QrCode, newState: QrStatus) {
                Log.i("TAG", "Updated QR: ${qr.content} -> $newState")
                qrStates[qr.content] = newState
            }

            override fun onLostQr(tracker: QrTracker, qr: QrCode) {
                Log.i("TAG", "Lost QR: ${qr.content}")
                qrStates.remove(qr.content)
            }
        }
    }
    val pointSize = with(LocalDensity.current) { 8.dp.toPx() }
    val imageAnalyzer = remember {
        ImageAnalyzer(
            trackerClient,
            overlay,
            pointSize,
        )
    }

    val resolution by imageAnalyzer.analysisResolution.collectAsState()
    val time by imageAnalyzer.analysisTime.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) { ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize(),
            factory = { context ->
                FrameLayout(context).apply {
                    addView(PreviewView(context).apply {
                        setBackgroundColor(Color.White.toArgb())
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }.also { previewView ->
                        previewView.controller = cameraController
//                    cameraController.imageAnalysisTargetSize
                        cameraController.setImageAnalysisAnalyzer(cameraExecutor, imageAnalyzer)
                        cameraController.bindToLifecycle(lifecycleOwner)
                    })
                }
            },
            onRelease = {
                cameraController.unbind()
            }
        )

        AndroidView(factory = { overlay })

        ErrorBar(state = errorState)

        if (DEBUG_OVERLAY) {
            Column(Modifier.background(Color(0x80ffffff))) {
                Text("Resolution: ${resolution.width}x${resolution.height}")
                Text("Analysis time: $time")
                Text("Tracked QRs:")

                for (entry in trackerClient.qrStates) {
                    Text(
                        "${entry.key.split('#')[1]}: '${entry.value}'",
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}