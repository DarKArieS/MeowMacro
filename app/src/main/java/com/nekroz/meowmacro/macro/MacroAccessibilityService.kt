package com.nekroz.meowmacro.macro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Injects recorded gestures into whatever app is on screen. Only used for [dispatchGesture];
 * must be enabled by the user in the system accessibility settings.
 */
class MacroAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** Performs [gesture] and suspends until it ends. Returns false if it was cancelled or rejected. */
    suspend fun perform(gesture: GestureDescription): Boolean = suspendCancellableCoroutine { cont ->
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                cont.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                cont.resume(false)
            }
        }
        if (!dispatchGesture(gesture, callback, null)) {
            cont.resume(false)
        }
    }

    companion object {
        /** The connected service, or null while it is disabled. */
        var instance: MacroAccessibilityService? = null
            private set
    }
}
