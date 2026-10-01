package com.nekroz.meowmacro.floating

import android.view.View
import android.view.WindowManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nekroz.meowmacro.macro.MacroEvent
import com.nekroz.meowmacro.macro.PASS_THROUGH_DELAY_MILLIS
import com.nekroz.meowmacro.macro.touches
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val MINIMIZE_TIMEOUT_MILLIS = 1000L
private const val MINIMIZE_POLL_MILLIS = 16L

/** Gap kept between the minimized block and the gestures being played back. */
private val BLOCK_CLEARANCE = 8.dp

/**
 * Gets the floating window, shown in [view] at [params], out of the way of [events] about to be
 * played, and waits until it is.
 */
suspend fun WindowManager.makeWayFor(
    events: List<MacroEvent>,
    view: View,
    params: WindowManager.LayoutParams,
) {
    moveClearOf(events, view, params)
    val size = with(Density(view.context)) { MinimizedBlockSize.roundToPx() }
    // Playback minimizes the window, but it keeps its expanded size until the transition
    // ends. Don't wait forever, though: the user may expand it again meanwhile.
    withTimeoutOrNull(MINIMIZE_TIMEOUT_MILLIS) {
        while (view.width > size || view.height > size) delay(MINIMIZE_POLL_MILLIS)
    }
    // Time for the window manager to apply the new size.
    delay(PASS_THROUGH_DELAY_MILLIS)
}

/**
 * Moves the window so the block it minimizes to for playback isn't hit by any gesture in
 * [events]: to the clear spot nearest to where it is now. It stays put if it already is
 * clear, or if no spot is.
 */
private fun WindowManager.moveClearOf(
    events: List<MacroEvent>,
    view: View,
    params: WindowManager.LayoutParams,
) {
    val density = Density(view.context)
    val size = with(density) { MinimizedBlockSize.roundToPx() }
    val clearance = with(density) { BLOCK_CLEARANCE.toPx() }

    // Gestures are in screen coordinates, but the window is positioned inside the system bars.
    val onScreen = IntArray(2)
    view.getLocationOnScreen(onScreen)
    val originX = onScreen[0] - params.x
    val originY = onScreen[1] - params.y
    val screen = currentWindowMetrics.bounds
    val maxX = screen.width() - size - originX
    val maxY = screen.height() - size - originY
    if (maxX < 0 || maxY < 0) return

    val current = IntOffset(params.x, params.y)
    val step = (size / 4).coerceAtLeast(1)
    val xs = gridAround(current.x.coerceIn(0, maxX), maxX, step)
    val ys = gridAround(current.y.coerceIn(0, maxY), maxY, step)
    val spot = xs.flatMap { x -> ys.map { y -> IntOffset(x, y) } }
        .sortedBy { (it - current).run { x * x + y * y } }
        .firstOrNull {
            val left = (it.x + originX).toFloat()
            val top = (it.y + originY).toFloat()
            !events.touches(
                Rect(left - clearance, top - clearance, left + size + clearance, top + size + clearance)
            )
        }
    if (spot == null || spot == current) return
    params.x = spot.x
    params.y = spot.y
    updateViewLayout(view, params)
}

/** Positions from 0 to [max] that are whole [step]s away from [center], plus both ends. */
private fun gridAround(center: Int, max: Int, step: Int): List<Int> =
    ((center downTo 0 step step) + (center..max step step) + listOf(0, max)).distinct()
