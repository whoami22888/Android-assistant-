package com.whoami22888.remoteagent.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.whoami22888.remoteagent.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

/** MediaProjection is started only from Android's system consent dialog and captures frames only on explicit request. */
class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = intent.parcelableIntent(EXTRA_RESULT_DATA) ?: return START_NOT_STICKY
        startForegroundCompat()
        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, resultData)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                releaseCapture()
                stopSelf()
            }
        }, null)
        createDisplay()
        instance = this
        _active.value = true
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseCapture()
        if (instance === this) instance = null
        _active.value = false
        super.onDestroy()
    }

    private fun createDisplay() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "RemoteAgentCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null,
        )
    }

    private fun snapshot(callback: (String?, String) -> Unit) {
        val image = imageReader?.acquireLatestImage()
        if (image == null) {
            callback(null, "No current frame is available yet. Wait a moment and try again.")
            return
        }
        image.use {
            runCatching {
                val plane = it.planes[0]
                val padding = plane.rowStride - plane.pixelStride * it.width
                val expanded = Bitmap.createBitmap(it.width + padding / plane.pixelStride, it.height, Bitmap.Config.ARGB_8888)
                expanded.copyPixelsFromBuffer(plane.buffer)
                val cropped = Bitmap.createBitmap(expanded, 0, 0, it.width, it.height)
                val bytes = ByteArrayOutputStream().use { stream ->
                    cropped.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                    stream.toByteArray()
                }
                require(bytes.size <= MAX_FRAME_BYTES) { "Frame exceeds the 1 MB privacy limit." }
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }.onSuccess { callback(it, "Frame captured after explicit user request.") }
                .onFailure { callback(null, "Frame capture failed: ${it.javaClass.simpleName}") }
        }
    }

    private fun releaseCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
    }

    private fun startForegroundCompat() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.capture_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableIntent(key: String): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, Intent::class.java)
    } else {
        getParcelableExtra(key)
    }

    companion object {
        private const val ACTION_START = "com.whoami22888.remoteagent.START_CAPTURE"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 301
        private const val MAX_FRAME_BYTES = 1_000_000
        private var instance: ScreenCaptureService? = null
        private val _active = MutableStateFlow(false)
        val active = _active.asStateFlow()

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun captureOneShot(callback: (String?, String) -> Unit) {
            val service = instance
            if (service == null) callback(null, "Start screen capture from Android's consent dialog first.")
            else service.snapshot(callback)
        }
    }
}
