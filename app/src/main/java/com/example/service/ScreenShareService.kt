package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import java.io.ByteArrayOutputStream

class ScreenShareService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    @Volatile
    private var jpegQuality: Int = 68

    @Volatile
    private var lastCaptureMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSharing()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_QUALITY -> {
                jpegQuality = intent.getIntExtra(EXTRA_QUALITY, 68).coerceIn(30, 95)
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA_INTENT)
                }
                jpegQuality = intent.getIntExtra(EXTRA_QUALITY, 68).coerceIn(30, 95)

                createNotificationChannel()
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIF_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIF_ID, notification)
                }

                if (resultCode == Activity.RESULT_OK && data != null) {
                    startProjection(resultCode, data)
                } else {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpm.getMediaProjection(resultCode, data) ?: run {
            stopSelf()
            return
        }
        mediaProjection = projection

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val scale = 0.5f
        val width = (metrics.widthPixels * scale).toInt().coerceIn(360, 720)
        val height = (metrics.heightPixels * scale).toInt().coerceIn(640, 1440)
        val density = metrics.densityDpi

        handlerThread = HandlerThread("DroidVmScreenCapture").apply { start() }
        backgroundHandler = Handler(handlerThread!!.looper)

        RealtimeShareServer.start(applicationContext, modeLabel = "Device Screen (MediaProjection)")

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSharing()
                stopSelf()
            }
        }, backgroundHandler)

        reader.setOnImageAvailableListener({ imgReader ->
            val now = System.currentTimeMillis()
            if (now - lastCaptureMs < 65L) {
                imgReader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            val image = imgReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            lastCaptureMs = now
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bmp = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bmp.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
                if (cropped !== bmp) bmp.recycle()
                val baos = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                cropped.recycle()
                RealtimeShareServer.updateFrame(baos.toByteArray())
            } catch (_: Exception) {
            } finally {
                image.close()
            }
        }, backgroundHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "DroidVmRealtimeShare",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            backgroundHandler
        )
    }

    private fun stopSharing() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
        handlerThread?.quitSafely()
        handlerThread = null
        RealtimeShareServer.stop()
    }

    override fun onDestroy() {
        stopSharing()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screen_share_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.screen_share_channel_desc)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenShareService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.screen_share_notif_title))
            .setContentText(getString(R.string.screen_share_notif_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop_share), stopIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.action.START_SCREEN_SHARE"
        const val ACTION_STOP = "com.example.action.STOP_SCREEN_SHARE"
        const val ACTION_UPDATE_QUALITY = "com.example.action.UPDATE_QUALITY"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA_INTENT = "data_intent"
        const val EXTRA_QUALITY = "quality"
        private const val CHANNEL_ID = "droidvm_screen_share"
        private const val NOTIF_ID = 4102
    }
}
