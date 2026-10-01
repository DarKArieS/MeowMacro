package com.nekroz.meowmacro.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nekroz.meowmacro.ui.MainWindow
import com.nekroz.meowmacro.R
import com.nekroz.meowmacro.macro.MacroController
import com.nekroz.meowmacro.macro.MacroEvent
import com.nekroz.meowmacro.macro.MacroRepo
import com.nekroz.meowmacro.macro.MacroState
import com.nekroz.meowmacro.macro.gestureCount
import com.nekroz.meowmacro.ui.theme.MeowMacroTheme
import com.nekroz.meowmacro.ui.theme.PlayingGreen
import com.nekroz.meowmacro.ui.theme.RecordingRed

/**
 * Hosts the app UI in a draggable overlay window drawn on top of other apps.
 * Requires the SYSTEM_ALERT_WINDOW ("display over other apps") permission.
 */
class FloatingWindowService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var overlayView: ComposeView? = null
    private var isMinimized = false
    private lateinit var macroController: MacroController

    override fun onCreate() {
        // Must restore saved state while the lifecycle is still INITIALIZED.
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        super.onCreate()

        startInForeground()

        windowManager = getSystemService(WindowManager::class.java)
        // Adds the recording overlay, so it must come before the floating window to stay below it.
        macroController =
            MacroController(this, windowManager, lifecycleScope, MacroRepo(this), ::makeWayFor)
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable: touches outside the window and key input go to the app underneath.
            // No limits: let the window be dragged partly off screen (bounded in moveWindowBy).
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setContent {
                MeowMacroTheme {
                    FloatingWindow(
                        onDrag = ::moveWindowBy,
                        onClose = ::stopSelf,
                        onMinimizedChange = ::setMinimized,
                        // Get out of the way of the gestures being recorded or played back,
                        // and color the block so the running macro state stays visible.
                        minimizeRequested = macroController.state != MacroState.Idle,
                        minimizedColor = when (macroController.state) {
                            MacroState.Recording -> RecordingRed
                            MacroState.Playing -> PlayingGreen
                            MacroState.Idle -> MaterialTheme.colorScheme.primary
                        }
                    ) {
                        MainWindow(
                            macroState = macroController.state,
                            macros = macroController.macros,
                            selectedIndex = macroController.selectedIndex,
                            playingIndex = macroController.playingIndex,
                            recordingEventCount = macroController.recordingEvents.gestureCount(),
                            onRecordClick = macroController::toggleRecording,
                            onAddClick = macroController::addMacro,
                            onSelect = macroController::selectMacro,
                            onDeleteClick = macroController::deleteMacro,
                            onRename = macroController::renameMacro,
                            onMove = macroController::moveMacro,
                            onEnabledChange = macroController::setMacroEnabled,
                            onTextInputActiveChange = ::setWindowFocusable,
                            onPlayClick = macroController::togglePlayback
                        )
                    }
                }
            }
            // The window resizes when it is minimized or expanded; pull it back into bounds.
            addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    v.post { moveWindowBy(0, 0) }
                }
            }
        }
        windowManager.addView(view, layoutParams)
        overlayView = view
    }

    override fun onDestroy() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        macroController.release()
        viewModelStore.clear()
        super.onDestroy()
    }

    /**
     * The window is normally not focusable so key input goes to the app underneath; it has to be
     * while a text field is in use, or the keyboard can't open. Touches outside still pass through.
     */
    private fun setWindowFocusable(focusable: Boolean) {
        val view = overlayView ?: return
        layoutParams.flags = if (focusable) {
            layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(view, layoutParams)
    }

    /** No clamping here: the window keeps its old size until the transition ends and it resizes. */
    private fun setMinimized(minimized: Boolean) {
        isMinimized = minimized
    }

    private fun moveWindowBy(dx: Int, dy: Int): Boolean {
        val view = overlayView ?: return false

        val metrics = windowManager.currentWindowMetrics
        val screen = metrics.bounds
        val topInset = 0 // 避免高於狀態欄，之後再也無法拖動視窗

        // The expanded window may hang half off screen; the minimized block must stay fully on it.
        val offX = if (isMinimized) 0 else view.width / 2
        val offY = if (isMinimized) 0 else view.height / 2
        val x = (layoutParams.x + dx)
            .coerceIn(-offX, maxOf(-offX, screen.width() - view.width + offX))
        val y = (layoutParams.y + dy)
            .coerceIn(topInset, maxOf(topInset, screen.height() - view.height + offY))
        if (x == layoutParams.x && y == layoutParams.y) return false
        layoutParams.x = x
        layoutParams.y = y
        windowManager.updateViewLayout(view, layoutParams)
        return true
    }

    private suspend fun makeWayFor(events: List<MacroEvent>) {
        val view = overlayView ?: return
        windowManager.makeWayFor(events, view, layoutParams)
    }

    private fun startInForeground() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "floating_window"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, FloatingWindowService::class.java))
        }
    }
}