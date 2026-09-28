package com.nekroz.meowmacro.macro

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import androidx.compose.ui.geometry.Offset

/** A single recorded touch gesture, in raw screen coordinates. */
sealed interface MacroGesture {
    /** How long the finger stayed down, in milliseconds. */
    val durationMillis: Long

    data class Tap(val position: Offset, override val durationMillis: Long) : MacroGesture

    data class Swipe(val points: List<Offset>, override val durationMillis: Long) : MacroGesture
}

/**
 * A recorded gesture plus the idle time before it: measured from the end of the previous
 * gesture to this gesture's touch down. Always 0 for the first gesture.
 */
data class MacroEvent(val delayMillis: Long, val gesture: MacroGesture)

fun MacroGesture.toGestureDescription(): GestureDescription {
    val path = Path()
    when (this) {
        is MacroGesture.Tap -> path.moveTo(position)
        is MacroGesture.Swipe -> {
            path.moveTo(points.first())
            points.drop(1).forEach { path.lineTo(it.x.coerceAtLeast(0f), it.y.coerceAtLeast(0f)) }
        }
    }
    val duration = durationMillis.coerceIn(1, GestureDescription.getMaxGestureDuration())
    return GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        .build()
}

// Gesture paths may not contain negative coordinates.
private fun Path.moveTo(point: Offset) = moveTo(point.x.coerceAtLeast(0f), point.y.coerceAtLeast(0f))
