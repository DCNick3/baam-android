@file:OptIn(ExperimentalPermissionsApi::class)

package me.dcnick3.baam.ui.camera

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.Size
import android.util.SizeF
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import me.dcnick3.pqrs.PqrsScanner
import me.dcnick3.pqrs.model.ScanResult
import me.dcnick3.pqrs.model.Vector
import java.util.concurrent.Executors


private class ScanResultGraphic(overlay: GraphicOverlay, val dotSize: Float) : GraphicOverlay.Graphic(overlay) {
    private var scanResult: ScanResult? = null
    private var transform: Matrix? = null
    private var size: Size? = null


    private val paint = Paint()

    init {
        paint.color = Color(0x80408040).toArgb()
    }


    fun update(scanResult: ScanResult?, transform: Matrix?, size: Size) {
        this.scanResult = scanResult
        this.transform = transform
        this.size = size
        overlay.postInvalidate()
    }

    override fun draw(canvas: Canvas) {
        val scanResult = scanResult ?: return
        val transform = transform ?: return
        val size = size ?: return


        val combinedTransform = Matrix()
        // first, transform the points to the normalized viewport space (-1, -1) - (1, 1)
        combinedTransform.postScale(2.0f / size.width, 2.0f / size.height);
        combinedTransform.postTranslate(-1.0f, -1.0f);

        // work around android bug: transform is not calculated correctly when using FILL_CENTER

        // first of all find out which side is cropped in the preview view by comparing aspect ratios of size and canvas
        val canvasAspect = canvas.width.toFloat() / canvas.height.toFloat()
        val sizeAspect = size.width.toFloat() / size.height.toFloat()
        val isWidthCropped = canvasAspect > sizeAspect

//        val fakeSize = if (isWidthCropped) {
//            SizeF((canvas.height * sizeAspect), canvas.height.toFloat())
//        } else {
//            SizeF(canvas.width.toFloat(), (canvas.width / sizeAspect))
//        }

        // then, transform from the normalized viewport space to the UI space
        combinedTransform.postConcat(transform)
//        combinedTransform.postScale(1f / canvas.width, 1f / canvas.height)
//        combinedTransform.postScale(fakeSize.width / 2f, fakeSize.height / 2f)

//        transform.postScale()

        fun drawPoint(p: Vector) {
            val point = floatArrayOf(p.x, p.y);
            combinedTransform.mapPoints(point)

            canvas.drawCircle(point[0], point[1], dotSize, paint)
        }

        for (p in scanResult.qrs) {
            drawPoint(p.bottom_left)
            drawPoint(p.bottom_right)
            drawPoint(p.top_left)
            drawPoint(p.top_right)
        }
    }
}

private class ImageAnalyzer(overlay: GraphicOverlay, dotSize: Float): ImageAnalysis.Analyzer {
    private val pqrsScanner = PqrsScanner()
    private val scanResultGraphic = ScanResultGraphic(overlay, dotSize)
    private var transform: Matrix? = null;

    init {
        overlay.add(scanResultGraphic)
    }

    override fun analyze(image: ImageProxy) {
        val width = image.width
        val height = image.height

        val plane = image.planes[0];
        val result = pqrsScanner.scanGrayscale(
            plane.buffer,
            width,
            height,
            plane.pixelStride,
            plane.rowStride,
        )

        // after done, release the ImageProxy object
        image.close()

        scanResultGraphic.update(result, transform, Size(width, height))
    }

    override fun getDefaultTargetResolution(): Size {
        return Size(640, 480)
    }

    override fun getTargetCoordinateSystem(): Int {
        return COORDINATE_SYSTEM_VIEW_REFERENCED;
    }

    override fun updateTransform(matrix: Matrix?) {
        transform = matrix;
    }
}

@Composable
fun CameraPreview() {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val cameraController: LifecycleCameraController = remember { LifecycleCameraController(context) }
    val cameraExecutor = Executors.newSingleThreadExecutor()

    val overlay = GraphicOverlay(context, null)
    val imageAnalyzer = ImageAnalyzer(
        overlay,
        with(LocalDensity.current) { 8.dp.toPx() }
    )

    Box(modifier = Modifier.fillMaxSize()) {->
        AndroidView(
            modifier = Modifier
                .fillMaxSize(),
            factory = { context ->
                FrameLayout(context).apply {
                    addView(PreviewView(context).apply {
                        setBackgroundColor(Color.White.toArgb())
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }.also { previewView ->
                        previewView.controller = cameraController
//                    cameraController.imageAnalysisTargetSize
                        cameraController.setImageAnalysisAnalyzer(cameraExecutor, imageAnalyzer)
                        cameraController.bindToLifecycle(lifecycleOwner)
                    })

                    addView(overlay)
                }
            },
            onRelease = {
                cameraController.unbind()
            }
        )
    }
}