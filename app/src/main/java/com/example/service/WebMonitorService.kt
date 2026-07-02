package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class WebMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val webViews = mutableListOf<WebView>()
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"
        private const val NOTIFICATION_CHANNEL_ID = "web_monitor_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                MonitorManager.isRunning.value = true
                MonitorManager.isPaused.value = false
                startForegroundServiceWithNotification()
                startMonitoring()
            }
            ACTION_PAUSE -> {
                val wasPaused = MonitorManager.isPaused.value
                MonitorManager.isPaused.value = !wasPaused
            }
            ACTION_STOP -> {
                stopMonitoring()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Web Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        // We can create a PendingIntent to open the app when the notification is clicked
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("المراقبة قيد التشغيل")
            .setContentText("يتم فحص الرابط بشكل دوري في الخلفية")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startMonitoring() {
        cleanupWebViews()
        MonitorManager.clearScreenshots()
        
        val url = MonitorManager.urlToLoad
        val count = MonitorManager.instanceCount

        for (i in 0 until count) {
            val webView = WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                
                // Set fixed size so it can draw without being attached to a window
                val width = 1080
                val height = 1920
                measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, width, height)
            }
            webViews.add(webView)
            runMonitorLoop(webView, url, i)
        }
    }

    private fun runMonitorLoop(webView: WebView, url: String, instanceId: Int) {
        serviceScope.launch {
            while (isActive && MonitorManager.isRunning.value) {
                if (MonitorManager.isPaused.value) {
                    delay(1000)
                    continue
                }

                try {
                    // Load the URL and wait for it to finish
                    val success = loadUrlAndWait(webView, url)
                    if (success) {
                        // Small delay to allow any post-load rendering/animations to settle
                        delay(2000)
                        
                        val bitmap = captureScreenshot(webView)
                        if (bitmap != null) {
                            MonitorManager.updateScreenshot(
                                ScreenshotData(
                                    instanceId = instanceId,
                                    bitmap = bitmap,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Wait before next request to avoid spamming and OOM
                delay(10000)
            }
        }
    }

    private suspend fun loadUrlAndWait(webView: WebView, url: String): Boolean = suspendCancellableCoroutine { cont ->
        var isFinished = false
        var progress100 = false
        var hasResumed = false

        fun checkDone() {
            if (!hasResumed && isFinished && progress100) {
                hasResumed = true
                if (cont.isActive) cont.resume(true) { }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isFinished = true
                checkDone()
            }
            
            override fun onReceivedError(
                view: WebView?, 
                errorCode: Int, 
                description: String?, 
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (!hasResumed) {
                    hasResumed = true
                    if (cont.isActive) cont.resume(false) { }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    progress100 = true
                    checkDone()
                }
            }
        }

        webView.loadUrl(url)
        
        cont.invokeOnCancellation {
            handler.post { webView.stopLoading() }
        }
    }

    private suspend fun captureScreenshot(view: WebView): Bitmap? = withContext(Dispatchers.Main) {
        if (view.width <= 0 || view.height <= 0) return@withContext null
        try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            return@withContext bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun stopMonitoring() {
        MonitorManager.isRunning.value = false
        cleanupWebViews()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupWebViews() {
        webViews.forEach { webView ->
            webView.stopLoading()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
        webViews.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        cleanupWebViews()
        MonitorManager.isRunning.value = false
    }
}
