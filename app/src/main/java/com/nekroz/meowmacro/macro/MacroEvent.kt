package com.nekroz.meowmacro.macro

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import androidx.compose.ui.geometry.Offset

/** A step of a recorded macro. Positions are raw screen coordinates. */
sealed interface MacroEvent {

    /** Idle time between two gestures. */
    data class Wait(val durationMillis: Long) : MacroEvent

    /** [durationMillis] is how long the finger stayed down. */
    data class Tap(val position: Offset, val durationMillis: Long) : MacroEvent

    data class Swipe(val points: List<Offset>, val durationMillis: Long) : MacroEvent
}

/** The gesture that performs this event, or null for a [MacroEvent.Wait]. */
fun MacroEvent.toGestureDescription(): GestureDescription? {
    val path = Path()
    val strokeMillis = when (this) {
        is MacroEvent.Wait -> return null
        is MacroEvent.Tap -> {
            path.moveTo(position)
            durationMillis
        }
        is MacroEvent.Swipe -> {
            path.moveTo(points.first())
            points.drop(1).forEach { path.lineTo(it.x.coerceAtLeast(0f), it.y.coerceAtLeast(0f)) }
            durationMillis
        }
    }
    val duration = strokeMillis.coerceIn(1, GestureDescription.getMaxGestureDuration())
    return GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        .build()
}

// Gesture paths may not contain negative coordinates.
private fun Path.moveTo(point: Offset) = moveTo(point.x.coerceAtLeast(0f), point.y.coerceAtLeast(0f))
