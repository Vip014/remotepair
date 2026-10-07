package com.remotepair.host.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service used to inject taps/swipes/global-actions that arrive
 * from the controller. Holds a static instance so InputInjector can reach it.
 */
class RemotePairAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
    companion object {
        @Volatile var instance: RemotePairAccessibilityService? = null
    }
}
