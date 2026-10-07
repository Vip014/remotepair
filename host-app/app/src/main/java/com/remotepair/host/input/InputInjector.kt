package com.remotepair.host.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.remotepair.host.service.RemotePairAccessibilityService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Translates input events received over the WebRTC "input" DataChannel into
 * real gestures via the Accessibility service.
 *
 * Coordinates arrive normalized (0.0-1.0); we scale to the real screen size.
 */
object InputInjector {
    private val json = Json { ignoreUnknownKeys = true }

    fun handle(messageJson: String, screenW: Int, screenH: Int) {
        val svc = RemotePairAccessibilityService.instance ?: return
        val obj = runCatching { json.parseToJsonElement(messageJson).jsonObject }.getOrNull() ?: return
        when (obj["t"]?.jsonPrimitive?.content) {
            "tap" -> {
                val x = (obj["x"]?.jsonPrimitive?.content?.toFloatOrNull() ?: return) * screenW
                val y = (obj["y"]?.jsonPrimitive?.content?.toFloatOrNull() ?: return) * screenH
                tap(svc, x, y)
            }
            "swipe" -> {
                val x1 = (obj["x1"]?.jsonPrimitive?.content?.toFloatOrNull() ?: return) * screenW
                val y1 = (obj["y1"]?.jsonPrimitive?.content?.toFloatOrNull() ?: return) * screenH
                val x2 = (obj["x2"]?.jsonPrimitive?.content?.toFloatOrNull() ?: return) * screenW
                val y2 = (obj["y2"]?.jsonPrimitive?.content?.toFloatOrNull() ?: return) * screenH
                val ms = obj["ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 200L
                swipe(svc, x1, y1, x2, y2, ms)
            }
            "back" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "recents" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }
    }

    private fun tap(svc: AccessibilityService, x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        svc.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun swipe(svc: AccessibilityService, x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, ms.coerceIn(50, 2000))
        svc.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
