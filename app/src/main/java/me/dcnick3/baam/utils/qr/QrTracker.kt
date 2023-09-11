package me.dcnick3.baam.utils.qr

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import android.util.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import me.dcnick3.baam.ui.camera.GraphicOverlay
import me.dcnick3.pqrs.model.QrCode
import me.dcnick3.pqrs.model.ScanResult
import me.dcnick3.pqrs.model.Vector
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "QrTracker"

enum class QrStatus {
    Checking,
    Valid,
    Invalid,
}

data class QrTrackerUpdate(
    val scanResult: ScanResult?,
    val transform: Matrix?,
    val analysisSize: Size,
)

private class ScanResultGraphic(overlay: GraphicOverlay, val dotSize: Float) :
    GraphicOverlay.Graphic(overlay) {
    var qrs: List<Pair<QrCode, QrStatus>> = emptyList()
    private var transform: Matrix? = null
    private var analysisSize: Size? = null

    private val paints: Map<QrStatus, Paint> = mapOf(
        (QrStatus.Valid to Paint()).apply {
            this.second.color = Color(0x80408040).toArgb()
        },
        (QrStatus.Invalid to Paint()).apply {
            this.second.color = Color(0x80FF0000).toArgb()
        },
        (QrStatus.Checking to Paint()).apply {
            this.second.color = Color(0x800000c0).toArgb()
        },
    )

    fun update(update: QrTrackerUpdate) {
        this.transform = update.transform
        this.analysisSize = update.analysisSize
    }

    override fun draw(canvas: Canvas) {
        val transform = transform ?: return

        // first, transform the points to the normalized viewport space (-1, -1) - (1, 1)
        // work around android bug: transform is not calculated correctly when using FILL_CENTER

        // first of all find out which side is cropped in the preview view by comparing aspect ratios of size and canvas
//        val canvasAspect = canvas.width.toFloat() / canvas.height.toFloat()
//        val sizeAspect = size.width.toFloat() / size.height.toFloat()
//        val isWidthCropped = canvasAspect > sizeAspect

//        val fakeSize = if (isWidthCropped) {
//            SizeF((canvas.height * sizeAspect), canvas.height.toFloat())
//        } else {
//            SizeF(canvas.width.toFloat(), (canvas.width / sizeAspect))
//        }

        // then, transform from the normalized viewport space to the UI space
//        combinedTransform.postScale(1f / canvas.width, 1f / canvas.height)
//        combinedTransform.postScale(fakeSize.width / 2f, fakeSize.height / 2f)

//        transform.postScale()

        fun drawPoint(p: Vector, status: QrStatus) {
            val point = floatArrayOf(p.x, p.y)
            transform.mapPoints(point)

            canvas.drawCircle(point[0], point[1], dotSize, paints[status]!!)
        }

        for ((qr, status) in qrs) {
            drawPoint(qr.bottom_left, status)
            drawPoint(qr.bottom_right, status)
            drawPoint(qr.top_left, status)
            drawPoint(qr.top_right, status)
        }
    }
}

interface QrTrackerClient {
    fun onNewQr(tracker: QrTracker, qr: QrCode)
    fun onUpdatedQr(tracker: QrTracker, qr: QrCode, newState: QrStatus)
    fun onLostQr(tracker: QrTracker, qr: QrCode)
}

private data class TrackedQr(
    var qr: QrCode,
    var status: QrStatus,
    var lastSeen: Instant,
)

class QrTracker(
    private val client: QrTrackerClient,
    private val overlay: GraphicOverlay,
    dotSize: Float
) {
    private val graphic = ScanResultGraphic(overlay, dotSize)

    private var qrs: MutableMap<String, TrackedQr> = mutableMapOf()
    private var lastFrameTime: Instant = Clock.System.now()

    companion object {
        private val forgetFrames = (1500).milliseconds
        private val hideFrames = (800).milliseconds
    }

    init {
        overlay.add(graphic)
    }

    private fun transformedQrs(update: QrTrackerUpdate): List<QrCode> {
        val qrs = update.scanResult?.qrs ?: return emptyList()
        val size = update.analysisSize
        val transform = Matrix()
        transform.postScale(2.0f / size.width, 2.0f / size.height)
        transform.postTranslate(-1.0f, -1.0f)

        fun transformVec(vec: Vector): Vector {
            val point = floatArrayOf(vec.x, vec.y)
            transform.mapPoints(point)
            return Vector(point[0], point[1])
        }

        return qrs.map { qr ->
            qr.copy(
                bottom_left = transformVec(qr.bottom_left),
                bottom_right = transformVec(qr.bottom_right),
                top_left = transformVec(qr.top_left),
                top_right = transformVec(qr.top_right),
            )
        }
    }

    fun update(update: QrTrackerUpdate) {
        graphic.update(update)

        synchronized(qrs) {
            for (qr in transformedQrs(update)) {
                var tracked = qrs[qr.content]
                if (tracked == null) {
                    tracked = TrackedQr(qr, QrStatus.Checking, lastFrameTime)
                    qrs[qr.content] = tracked
                    Log.d(TAG, "New QR: ${qr.content}")
                    client.onNewQr(this, qr)
                }

                tracked.qr = qr
                tracked.lastSeen = lastFrameTime
            }

            val iterator = qrs.values.iterator()
            while(iterator.hasNext()) {
                val tracked = iterator.next()
                if (tracked.lastSeen + forgetFrames < lastFrameTime) {
                    // forget
                    iterator.remove()
                    Log.d(TAG, "Lost QR: ${tracked.qr.content}")
                    client.onLostQr(this, tracked.qr)
                }
            }

            val newGraphicQrs = qrs.values
                .filter { it.lastSeen + hideFrames >= lastFrameTime }
                .map { Pair(it.qr, it.status) }
            if (newGraphicQrs != graphic.qrs) {
                graphic.qrs = newGraphicQrs
                overlay.postInvalidate()
            }
        }

        lastFrameTime = Clock.System.now()
    }

    fun qrValid(qr: QrCode) {
        synchronized(qrs) {
            val trackedQr = qrs[qr.content]
            if (trackedQr != null) {
                trackedQr.status = QrStatus.Valid
                Log.d(TAG, "QR valid: ${qr.content}")
                client.onUpdatedQr(this, qr, QrStatus.Valid)
            }
        }
    }

    fun qrInvalid(qr: QrCode) {
        synchronized(qrs) {
            val trackedQr = qrs[qr.content]
            if (trackedQr != null) {
                trackedQr.status = QrStatus.Invalid
                Log.d(TAG, "QR invalid: ${qr.content}")
                client.onUpdatedQr(this, qr, QrStatus.Invalid)
            }
        }
    }
}