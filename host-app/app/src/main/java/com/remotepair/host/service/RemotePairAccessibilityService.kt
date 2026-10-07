package com.remotepair.host.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service entry point.
 *
 * In the full version this will receive gesture dispatch requests from the
 * Controller via DataChannel and call dispatchGesture() + performGlobalAction()
 * to inject taps/swipes/back/home into whichever app is on screen.
 *
 * For MVP, the service just needs to exist so the user can enable it in
 * Settings -> Accessibility, which proves the plumbing works.
 */
class RemotePairAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used - we don't observe UI events, we only dispatch gestures.
    }
    override fun onInterrupt() {}
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
    companion object {
        @Volatile var instance: RemotePairAccessibilityService? = null
    }
}
