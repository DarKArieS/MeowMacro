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
import com.nekroz.meowmacro.MainWindow
import com.nekroz.meowmacro.R
import com.nekroz.meowmacro.macro.MacroController
import com.nekroz.meowmacro.macro.MacroRepo
import com.nekroz.meowmacro.macro.gestureCount
import com.nekroz.meowmacro.ui.theme.MeowMacroTheme

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
    private lateinit var macroController: MacroController

    override fun onCreate() {
        // Must restore saved state while the lifecycle is still INITIALIZED.
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        super.onCreate()

        startInForeground()

        windowManager = getSystemService(WindowManager::class.java)
        // Adds the recording overlay, so it must come before the floating window to stay below it.
        macroController = MacroController(this, windowManager, lifecycleScope, MacroRepo(this))
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
                        onClose = ::stopSelf
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
                            onTextInputActiveChange = ::setWindowFocusable,
                            onPlayClick = macroController::togglePlayback
                        )
                    }
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

    private fun moveWindowBy(dx: Int, dy: Int): Boolean {
        val view = overlayView ?: return false

        val metrics = windowManager.currentWindowMetrics
        val screen = metrics.bounds
        val topInset = 0 // 避免高於狀態欄，之後再也無法拖動視窗

        val x = (layoutParams.x + dx)
            .coerceIn(-view.width / 2, screen.width() - view.width / 2)
        val y = (layoutParams.y + dy)
            .coerceIn(topInset, maxOf(topInset, screen.height() - view.height / 2))
        if (x == layoutParams.x && y == layoutParams.y) return false
        layoutParams.x = x
        layoutParams.y = y
        windowManager.updateViewLayout(view, layoutParams)
        return true
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