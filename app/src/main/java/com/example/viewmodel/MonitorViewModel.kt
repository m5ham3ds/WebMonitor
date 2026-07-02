package com.example.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.example.service.MonitorManager
import com.example.service.ScreenshotData
import com.example.service.WebMonitorService
import kotlinx.coroutines.flow.StateFlow

class MonitorViewModel(application: Application) : AndroidViewModel(application) {

    val isRunning: StateFlow<Boolean> = MonitorManager.isRunning
    val isPaused: StateFlow<Boolean> = MonitorManager.isPaused
    val screenshots: StateFlow<Map<Int, ScreenshotData>> = MonitorManager.screenshots

    fun startMonitoring(url: String, instanceCount: Int) {
        MonitorManager.urlToLoad = url
        MonitorManager.instanceCount = instanceCount
        
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, WebMonitorService::class.java).apply {
            action = WebMonitorService.ACTION_START
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun pauseMonitoring() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, WebMonitorService::class.java).apply {
            action = WebMonitorService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun stopMonitoring() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, WebMonitorService::class.java).apply {
            action = WebMonitorService.ACTION_STOP
        }
        context.startService(intent)
    }
}
