package com.example.service

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ScreenshotData(
    val instanceId: Int,
    val bitmap: Bitmap,
    val timestamp: Long
)

object MonitorManager {
    private val _screenshots = MutableStateFlow<Map<Int, ScreenshotData>>(emptyMap())
    val screenshots: StateFlow<Map<Int, ScreenshotData>> = _screenshots.asStateFlow()

    val isRunning = MutableStateFlow(false)
    val isPaused = MutableStateFlow(false)
    var urlToLoad = ""
    var instanceCount = 1
    var targetOpenCount = 1
    val completedCount = MutableStateFlow(0)

    fun updateScreenshot(data: ScreenshotData) {
        _screenshots.update { currentMap ->
            currentMap.toMutableMap().apply {
                put(data.instanceId, data)
            }
        }
    }

    fun clearScreenshots() {
        _screenshots.value = emptyMap()
    }
}
