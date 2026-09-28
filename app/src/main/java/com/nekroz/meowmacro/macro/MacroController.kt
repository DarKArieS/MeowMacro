package com.nekroz.meowmacro.macro

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.nekroz.meowmacro.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MacroState { Idle, Recording, Playing }

/** Time for the window manager to hide the recording overlay before a gesture is injected. */
const val PASS_THROUGH_DELAY_MILLIS = 50L

/** Attempts to pass a gesture through while the overlay still intercepts it; each waits longer. */
private const val PASS_THROUGH_MAX_ATTEMPTS = 3

/**
 * Records taps and swipes anywhere on screen into the selected [Macro] and replays macros with the
 * recorded timing. Macros are loaded from and saved to [repo].
 *
 * Recording uses a transparent full-screen overlay that must sit below the floating window, so
 * create this before adding the floating window. Each captured gesture is immediately re-injected
 * through [MacroAccessibilityService] so the app underneath still receives it.
 */
class MacroController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val scope: CoroutineScope,
    private val repo: MacroRepo,
) {
    var state by mutableStateOf(MacroState.Idle)
        private set
    var macros by mutableStateOf<List<Macro>>(emptyList())
        private set

    /** Index in [macros] that the next recording is saved to, or -1 when none is selected. */
    var selectedIndex by mutableIntStateOf(-1)
        private set

    /** Index in [macros] being played, or -1. */
    var playingIndex by mutableIntStateOf(-1)
        private set

    /** Events captured so far by the recording in progress. */
    var recordingEvents by mutableStateOf<List<MacroEvent>>(emptyList())
        private set

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var job: Job? = null

    // In-progress recording state.
    private var passThroughJob: Job? = null
    private var overlayHitDuringPassThrough = false
    private var lastEventEnd = 0L
    private var downTime = 0L
    private var points: MutableList<Offset>? = null

    @SuppressLint("ClickableViewAccessibility")
    private val captureView = View(context).apply {
        visibility = View.GONE
        setOnTouchListener { _, event ->
            onCaptureTouch(event)
            true
        }
    }

    private val captureParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        fitInsetsTypes = 0
    }

    init {
        windowManager.addView(captureView, captureParams)
        scope.launch {
            // Keep any macro added while loading.
            macros = repo.load() + macros
            if (selectedIndex !in macros.indices && macros.isNotEmpty()) selectedIndex = 0
        }
    }

    /** Appends an empty macro and selects it as the recording target. */
    fun addMacro() {
        if (state == MacroState.Recording) return
        val name = context.getString(R.string.macro_default_name, macros.size + 1)
        macros = macros + Macro(name, emptyList())
        selectedIndex = macros.lastIndex
        save()
    }

    fun selectMacro(index: Int) {
        // Switching the target mid-recording would split the recording.
        if (state == MacroState.Recording || index !in macros.indices) return
        selectedIndex = index
    }

    /** Renames the macro at [index]; blank names are ignored. */
    fun renameMacro(index: Int, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || index !in macros.indices) return
        macros = macros.toMutableList().also { it[index] = it[index].copy(name = trimmed) }
        save()
    }

    /** Removes the macro at [index]. Only allowed while idle, so indices can't shift under use. */
    fun deleteMacro(index: Int) {
        if (state != MacroState.Idle || index !in macros.indices) return
        macros = macros.toMutableList().also { it.removeAt(index) }
        selectedIndex = when {
            index < selectedIndex -> selectedIndex - 1
            // The selected macro itself was deleted: select its successor, or the new last one.
            index == selectedIndex -> selectedIndex.coerceAtMost(macros.lastIndex)
            else -> selectedIndex
        }
        save()
    }

    fun toggleRecording() {
        when (state) {
            MacroState.Recording -> stop()
            MacroState.Idle -> if (requireAccessibility()) startRecording()
            MacroState.Playing -> Unit
        }
    }

    fun togglePlayback(index: Int) {
        when (state) {
            MacroState.Playing -> stop()
            MacroState.Idle -> {
                val events = macros.getOrNull(index)?.macro
                if (!events.isNullOrEmpty() && requireAccessibility()) startPlayback(index, events)
            }
            MacroState.Recording -> Unit
        }
    }

    fun release() {
        stop()
        windowManager.removeView(captureView)
    }

    private fun startRecording() {
        if (selectedIndex !in macros.indices) addMacro()
        recordingEvents = emptyList()
        points = null
        state = MacroState.Recording
        captureView.visibility = View.VISIBLE
    }

    private fun startPlayback(index: Int, events: List<MacroEvent>) {
        state = MacroState.Playing
        playingIndex = index
        job = scope.launch {
            try {
                for (event in events) {
                    if (event is MacroEvent.Wait) {
                        delay(event.durationMillis)
                        continue
                    }
                    val gesture = event.toGestureDescription() ?: continue
                    // A cancelled gesture (e.g. the user touched the screen) doesn't abort the rest.
                    val service = MacroAccessibilityService.instance ?: break
                    service.perform(gesture)
                }
            } finally {
                // stop() may already have moved on to a new recording.
                if (state == MacroState.Playing) {
                    state = MacroState.Idle
                    playingIndex = -1
                }
            }
        }
    }

    private fun stop() {
        if (state == MacroState.Recording) saveRecording()
        job?.cancel()
        job = null
        passThroughJob?.cancel()
        passThroughJob = null
        points = null
        captureView.visibility = View.GONE
        state = MacroState.Idle
        playingIndex = -1
    }

    /** Replaces the selected macro's events with the finished recording. */
    private fun saveRecording() {
        val index = selectedIndex
        // An empty recording is most likely a mis-tap; don't wipe the macro with it.
        if (recordingEvents.isEmpty() || index !in macros.indices) return
        macros = macros.toMutableList().also { it[index] = it[index].copy(macro = recordingEvents) }
        save()
    }

    private fun save() {
        val snapshot = macros
        // Undispatched so the save starts even if the scope is cancelled right after, e.g. when
        // the service is destroyed mid-recording; the repo then finishes it regardless.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { repo.save(snapshot) }
    }

    private fun onCaptureTouch(event: MotionEvent) {
        // The overlay should be hidden while passing a gesture through, so anything arriving
        // now is most likely that injected gesture: never record it, and have it re-sent.
        if (passThroughJob?.isActive == true) {
            overlayHitDuringPassThrough = true
            return
        }
        // Only the first pointer is recorded; rawX/rawY are the screen coordinates gestures use.
        val point = Offset(event.rawX, event.rawY)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = event.eventTime
                points = mutableListOf(point)
            }

            MotionEvent.ACTION_MOVE -> points?.add(point)

            MotionEvent.ACTION_UP -> {
                val path = points ?: return
                path += point
                points = null
                onGestureCaptured(path, event.eventTime)
            }

            MotionEvent.ACTION_CANCEL -> points = null
        }
    }

    private fun onGestureCaptured(path: List<Offset>, upTime: Long) {
        val duration = upTime - downTime
        val start = path.first()
        val isTap = path.all { (it - start).getDistance() <= touchSlop }
        val gesture = if (isTap) {
            MacroEvent.Tap(start, duration)
        } else {
            MacroEvent.Swipe(path, duration)
        }
        // The wait before the first gesture isn't part of the macro.
        val wait = if (recordingEvents.isEmpty()) null else MacroEvent.Wait(downTime - lastEventEnd)
        recordingEvents = recordingEvents + listOfNotNull(wait, gesture)
        lastEventEnd = upTime
        passThrough(gesture)
    }

    /**
     * Lets the captured gesture reach the app underneath by replaying it with the overlay hidden.
     * Merely making it untouchable isn't enough: the gesture would still be flagged
     * FLAG_WINDOW_IS_OBSCURED, which apps filtering obscured touches (e.g. many games) drop.
     */
    private fun passThrough(event: MacroEvent) {
        val gesture = event.toGestureDescription() ?: return
        passThroughJob = scope.launch {
            captureView.visibility = View.INVISIBLE
            try {
                for (attempt in 1..PASS_THROUGH_MAX_ATTEMPTS) {
                    delay(PASS_THROUGH_DELAY_MILLIS * attempt)
                    overlayHitDuringPassThrough = false
                    MacroAccessibilityService.instance?.perform(gesture) ?: break
                    if (!overlayHitDuringPassThrough) break
                }
            } finally {
                if (state == MacroState.Recording) captureView.visibility = View.VISIBLE
            }
        }
    }

    /** Returns whether the accessibility service is on; otherwise sends the user to enable it. */
    private fun requireAccessibility(): Boolean {
        if (MacroAccessibilityService.instance != null) return true
        Toast.makeText(context, R.string.accessibility_required, Toast.LENGTH_LONG).show()
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return false
    }
}
