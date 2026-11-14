package com.nekroz.meowmacro.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/** UI state kept across app restarts, such as which MainWindow sections are expanded. */
class UiPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val recordBarExpandedState =
        mutableStateOf(prefs.getBoolean(KEY_RECORD_BAR_EXPANDED, true))
    private val listExpandedState = mutableStateOf(prefs.getBoolean(KEY_LIST_EXPANDED, true))

    var recordBarExpanded: Boolean
        get() = recordBarExpandedState.value
        set(value) {
            recordBarExpandedState.value = value
            prefs.edit { putBoolean(KEY_RECORD_BAR_EXPANDED, value) }
        }

    var listExpanded: Boolean
        get() = listExpandedState.value
        set(value) {
            listExpandedState.value = value
            prefs.edit { putBoolean(KEY_LIST_EXPANDED, value) }
        }

    private companion object {
        const val PREFS_NAME = "ui"
        const val KEY_RECORD_BAR_EXPANDED = "record_bar_expanded"
        const val KEY_LIST_EXPANDED = "list_expanded"
    }
}
