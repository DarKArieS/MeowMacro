package com.nekroz.meowmacro.floating

import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
const val DEFAULT_FLING_FRICTION = 3f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.rememberWindowDragModifier(
    onDrag: (dx: Int, dy: Int) -> Boolean,
    flingFriction: Float = DEFAULT_FLING_FRICTION,
    /** Called for a touch that stays within the touch slop; such a touch doesn't fling. */
    onClick: (() -> Unit)? = null,
    /** Called for the second of two such touches in quick succession. */
    onDoubleClick: (() -> Unit)? = null,
): Modifier {
    val scope = rememberCoroutineScope()
    val velocityTracker = remember { VelocityTracker.obtain() }
    DisposableEffect(velocityTracker) { onDispose { velocityTracker.recycle() } }
    // Track raw screen coordinates: local pointer positions shift as the window
    // itself moves, which would make the drag jitter and skew the velocity.
    val lastRaw = remember { FloatArray(2) }
    val downRaw = remember { FloatArray(2) }
    var dragging by remember { mutableStateOf(false) }
    val viewConfiguration = LocalViewConfiguration.current
    val touchSlop = viewConfiguration.touchSlop
    var flingJob by remember { mutableStateOf<Job?>(null) }
    // Release time of the last tap, if the next touch can still complete a double tap.
    var lastTapUpTime by remember { mutableStateOf<Long?>(null) }
    var secondTap by remember { mutableStateOf(false) }

    fun trackVelocity(event: MotionEvent) {
        val raw = MotionEvent.obtain(event).apply { setLocation(event.rawX, event.rawY) }
        velocityTracker.addMovement(raw)
        raw.recycle()
    }

    return this.pointerInteropFilter { event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                flingJob?.cancel()
                velocityTracker.clear()
                trackVelocity(event)
                lastRaw[0] = event.rawX
                lastRaw[1] = event.rawY
                downRaw[0] = event.rawX
                downRaw[1] = event.rawY
                dragging = false
                secondTap = lastTapUpTime?.let {
                    event.eventTime - it <= viewConfiguration.doubleTapTimeoutMillis
                } ?: false
            }

            MotionEvent.ACTION_MOVE -> {
                trackVelocity(event)
                if (!dragging) {
                    val distance = Offset(event.rawX - downRaw[0], event.rawY - downRaw[1])
                    dragging = distance.getDistance() > touchSlop
                }
                val dx = (event.rawX - lastRaw[0]).toInt()
                val dy = (event.rawY - lastRaw[1]).toInt()
                if (dx != 0 || dy != 0) {
                    lastRaw[0] += dx
                    lastRaw[1] += dy
                    onDrag(dx, dy)
                }
            }

            MotionEvent.ACTION_UP -> if (!dragging && (onClick != null || onDoubleClick != null)) {
                if (secondTap && onDoubleClick != null) {
                    lastTapUpTime = null
                    onDoubleClick()
                } else {
                    lastTapUpTime = event.eventTime
                    onClick?.invoke()
                }
            } else {
                lastTapUpTime = null
                trackVelocity(event)
                velocityTracker.computeCurrentVelocity(1000)
                val velocity = AnimationVector(velocityTracker.xVelocity, velocityTracker.yVelocity)
                flingJob = scope.launch {
                    var moved = Offset.Zero
                    AnimationState(Offset.VectorConverter, Offset.Zero, velocity)
                        .animateDecay(exponentialDecay(frictionMultiplier = flingFriction)) {
                            val dx = (value.x - moved.x).toInt()
                            val dy = (value.y - moved.y).toInt()
                            if (dx != 0 || dy != 0) {
                                moved += Offset(dx.toFloat(), dy.toFloat())
                                if (!onDrag(dx, dy)) cancelAnimation()
                            }
                        }
                }
            }
        }
        true
    }
}