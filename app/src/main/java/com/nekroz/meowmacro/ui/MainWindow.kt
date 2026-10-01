package com.nekroz.meowmacro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nekroz.meowmacro.R
import com.nekroz.meowmacro.floating.FloatingWindow
import com.nekroz.meowmacro.macro.Macro
import com.nekroz.meowmacro.macro.MacroEvent
import com.nekroz.meowmacro.macro.MacroState
import com.nekroz.meowmacro.macro.gestureCount
import com.nekroz.meowmacro.ui.theme.MeowMacroTheme
import kotlinx.coroutines.flow.first

@Composable
fun MainWindow(
    macroState: MacroState,
    macros: List<Macro>,
    selectedIndex: Int,
    playingIndex: Int,
    recordingEventCount: Int,
    onRecordClick: () -> Unit,
    onAddClick: () -> Unit,
    onSelect: (index: Int) -> Unit,
    onPlayClick: (index: Int) -> Unit,
    onDeleteClick: (index: Int) -> Unit,
    onRename: (index: Int, name: String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onEnabledChange: (index: Int, enabled: Boolean) -> Unit,
    /** Called with true while a text field needs keyboard input, and false once it's done. */
    onTextInputActiveChange: (active: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var recordBarExpanded by rememberSaveable { mutableStateOf(true) }
    var listExpanded by rememberSaveable { mutableStateOf(true) }
    // Index of the macro awaiting delete confirmation, or -1.
    var pendingDelete by rememberSaveable { mutableIntStateOf(-1) }
    // Index of the macro being renamed, or -1.
    var pendingRename by rememberSaveable { mutableIntStateOf(-1) }
    // While true, rows show move, enable and delete buttons instead of play.
    var editMode by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier) {
        val selected = macros.getOrNull(selectedIndex)
        SectionHeader(
            title = selected?.name ?: stringResource(R.string.macro_none_selected),
            expanded = recordBarExpanded,
            collapseLabel = stringResource(R.string.macro_record_bar_collapse),
            expandLabel = stringResource(R.string.macro_record_bar_expand),
            onToggleExpanded = { recordBarExpanded = !recordBarExpanded }
        )
        if (recordBarExpanded) {
            RecordBar(
                macroState = macroState,
                selected = selected,
                recordingEventCount = recordingEventCount,
                onRecordClick = onRecordClick,
                onAddClick = onAddClick
            )
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(
                title = stringResource(R.string.macro_list_title, macros.size),
                expanded = listExpanded,
                collapseLabel = stringResource(R.string.macro_list_collapse),
                expandLabel = stringResource(R.string.macro_list_expand),
                onToggleExpanded = { listExpanded = !listExpanded },
                modifier = Modifier.weight(1f)
            )
            if (listExpanded && macros.isNotEmpty()) {
                IconButton(
                    onClick = {
                        pendingDelete = -1
                        pendingRename = -1
                        editMode = !editMode
                    },
                    modifier = Modifier.size(32.dp),
                    // Leaving is always allowed; entering only while idle.
                    enabled = editMode || macroState == MacroState.Idle
                ) {
                    Icon(
                        painter = painterResource(
                            if (editMode) R.drawable.ic_check else R.drawable.ic_edit
                        ),
                        contentDescription = stringResource(
                            if (editMode) R.string.macro_edit_done else R.string.macro_edit
                        )
                    )
                }
            }
        }
        val deleting = macros.getOrNull(pendingDelete)
        val renaming = macros.getOrNull(pendingRename)
        if (listExpanded && renaming != null) {
            RenamePanel(
                initialName = renaming.name,
                onConfirm = { name ->
                    onRename(pendingRename, name)
                    pendingRename = -1
                },
                onDismiss = { pendingRename = -1 },
                onTextInputActiveChange = onTextInputActiveChange
            )
        } else if (listExpanded && deleting != null) {
            DeleteConfirmation(
                macroName = deleting.name,
                // Recording or playing may have started since; the controller then ignores it.
                confirmEnabled = macroState == MacroState.Idle,
                onConfirm = {
                    onDeleteClick(pendingDelete)
                    pendingDelete = -1
                    // Nothing left to edit once the last macro is gone.
                    if (macros.size == 1) editMode = false
                },
                onDismiss = { pendingDelete = -1 }
            )
        } else if (listExpanded && macros.isNotEmpty()) {
            // Scrolling Column rather than LazyColumn: the floating window sizes itself with
            // intrinsic measurements, which lazy layouts don't support.
            Column(
                modifier = Modifier
                    .heightIn(max = 120.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 4.dp)
            ) {
                macros.forEachIndexed { index, macro ->
                    if (editMode) {
                        EditRow(
                            macro = macro,
                            selected = index == selectedIndex,
                            // The controller also ignores edits unless idle.
                            enabled = macroState == MacroState.Idle,
                            canMoveUp = index > 0,
                            canMoveDown = index < macros.lastIndex,
                            onMoveUp = { onMove(index, index - 1) },
                            onMoveDown = { onMove(index, index + 1) },
                            onEnabledChange = { onEnabledChange(index, it) },
                            onDeleteClick = { pendingDelete = index }
                        )
                    } else {
                        MacroRow(
                            macro = macro,
                            selected = index == selectedIndex,
                            playing = index == playingIndex,
                            macroState = macroState,
                            onClick = { onSelect(index) },
                            onLongClick = {
                                pendingDelete = -1
                                pendingRename = index
                            },
                            onPlayClick = { onPlayClick(index) }
                        )
                    }
                }
            }
        }
    }
}

/** A full-width title that collapses or expands the section below it. */
@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    collapseLabel: String,
    expandLabel: String,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title + if (expanded) " ▾" else " ▸",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                onClickLabel = if (expanded) collapseLabel else expandLabel,
                onClick = onToggleExpanded
            )
            .padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun RenamePanel(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onTextInputActiveChange: (active: Boolean) -> Unit,
) {
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length)))
    }
    val valid = name.text.isNotBlank()
    val focusRequester = remember { FocusRequester() }
    val windowInfo = LocalWindowInfo.current
    val focusManager = LocalFocusManager.current
    DisposableEffect(Unit) {
        onTextInputActiveChange(true)
        onDispose {
            // Otherwise focus moves on to the next clickable, leaving it highlighted.
            focusManager.clearFocus()
            onTextInputActiveChange(false)
        }
    }
    LaunchedEffect(Unit) {
        // The window only becomes focusable above; the keyboard won't open before it has focus.
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        focusRequester.requestFocus()
    }
    DialogPanel(
        confirmText = stringResource(R.string.confirm),
        confirmEnabled = valid,
        onConfirm = { onConfirm(name.text) },
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.macro_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(name.text) }),
            modifier = Modifier.focusRequester(focusRequester)
        )
    }
}

@Composable
private fun DeleteConfirmation(
    macroName: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogPanel(
        confirmText = stringResource(R.string.macro_delete_confirm_button),
        confirmEnabled = confirmEnabled,
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        Text(
            text = stringResource(R.string.macro_delete_confirm, macroName),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Dialog-styled panel with cancel and confirm buttons, shown inline in place of the list rather
 * than as an AlertDialog: dialogs need an activity window, which the service-hosted floating
 * window doesn't have.
 */
@Composable
private fun DialogPanel(
    confirmText: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp)) {
            Box(modifier = Modifier.padding(end = 8.dp)) {
                content()
            }
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                    Text(
                        text = confirmText,
                        color = if (destructive && confirmEnabled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color.Unspecified
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordBar(
    macroState: MacroState,
    selected: Macro?,
    recordingEventCount: Int,
    onRecordClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val recording = macroState == MacroState.Recording
        IconButton(onClick = onRecordClick, enabled = macroState != MacroState.Playing) {
            Icon(
                painter = painterResource(if (recording) R.drawable.ic_stop else R.drawable.ic_record),
                contentDescription = stringResource(
                    if (recording) R.string.macro_stop_recording else R.string.macro_record
                ),
                tint = if (recording) LocalContentColor.current else MaterialTheme.colorScheme.error
            )
        }
        Text(
            text = when (macroState) {
                MacroState.Recording -> stringResource(R.string.macro_recording, recordingEventCount)
                MacroState.Playing -> stringResource(R.string.macro_playing)
                MacroState.Idle -> stringResource(
                    R.string.macro_event_count,
                    selected?.macro?.gestureCount() ?: 0
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onAddClick, enabled = !recording) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.macro_add)
            )
        }
    }
}

@Composable
private fun MacroRow(
    macro: Macro,
    selected: Boolean,
    playing: Boolean,
    macroState: MacroState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: () -> Unit,
) {
    // While playing, only the playing macro can be clicked to stop; other rows just select.
    val canPlay = when (macroState) {
        MacroState.Idle -> macro.isEnabled && macro.macro.isNotEmpty()
        MacroState.Playing -> playing
        MacroState.Recording -> false
    }
    val playLabel = stringResource(if (playing) R.string.macro_stop_playing else R.string.macro_play)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .combinedClickable(
                enabled = macroState != MacroState.Recording,
                onClickLabel = if (canPlay) playLabel else null,
                onLongClickLabel = stringResource(R.string.macro_rename),
                onLongClick = onLongClick,
                onClick = {
                    onClick()
                    if (canPlay) onPlayClick()
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicator only; the whole row is the play/stop target. Sized like an IconButton.
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play),
                contentDescription = null,
                tint = if (canPlay) {
                    LocalContentColor.current
                } else {
                    LocalContentColor.current.copy(alpha = 0.38f)
                }
            )
        }
        MacroName(macro = macro, selected = selected, modifier = Modifier.weight(1f))
    }
}

/** The macro's name, dimmed while the macro is disabled. */
@Composable
private fun MacroName(macro: Macro, selected: Boolean, modifier: Modifier = Modifier) {
    val color = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = macro.name,
        style = MaterialTheme.typography.bodyMedium,
        color = if (macro.isEnabled) color else color.copy(alpha = 0.38f),
        modifier = modifier
    )
}

/**
 * A macro row in edit mode: move buttons, the name, an enable toggle and a delete button, with
 * nothing else clickable.
 */
@Composable
private fun EditRow(
    macro: Macro,
    selected: Boolean,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEnabledChange: (enabled: Boolean) -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            ),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMoveUp,
            modifier = Modifier.size(36.dp),
            enabled = enabled && canMoveUp
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_move_up),
                contentDescription = stringResource(R.string.macro_move_up)
            )
        }
        IconButton(
            onClick = onMoveDown,
            modifier = Modifier.size(36.dp),
            enabled = enabled && canMoveDown
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_move_down),
                contentDescription = stringResource(R.string.macro_move_down)
            )
        }
        MacroName(macro = macro, selected = selected, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onEnabledChange(!macro.isEnabled) },
            modifier = Modifier.size(36.dp),
            enabled = enabled
        ) {
            Icon(
                painter = painterResource(
                    if (macro.isEnabled) R.drawable.ic_toggle_on else R.drawable.ic_toggle_off
                ),
                // Names the action, like the other buttons in the row.
                contentDescription = stringResource(
                    if (macro.isEnabled) R.string.macro_disable else R.string.macro_enable
                ),
                tint = if (enabled && macro.isEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    LocalContentColor.current
                }
            )
        }
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.size(36.dp),
            enabled = enabled
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.macro_delete),
                tint = if (enabled) MaterialTheme.colorScheme.error else LocalContentColor.current
            )
        }
    }
}

@Preview
@Composable
private fun MainWindowPreview() {
    val tap = MacroEvent.Tap(Offset(100f, 200f), 50)
    MeowMacroTheme {
        // Inside the floating window, which sizes MainWindow to its content as on device.
        FloatingWindow(onDrag = { _, _ -> true }, onClose = {}) {
            MainWindow(
                macroState = MacroState.Idle,
                macros = listOf(
                    Macro("巨集 1", listOf(tap)),
                    Macro("巨集 2", listOf(tap, MacroEvent.Wait(300), tap)),
                    Macro("巨集 3", emptyList()),
                    Macro("巨集 4", listOf(tap), isEnabled = false),
                ),
                selectedIndex = 1,
                playingIndex = -1,
                recordingEventCount = 0,
                onRecordClick = {},
                onAddClick = {},
                onSelect = {},
                onPlayClick = {},
                onDeleteClick = {},
                onRename = { _, _ -> },
                onMove = { _, _ -> },
                onEnabledChange = { _, _ -> },
                onTextInputActiveChange = {}
            )
        }
    }
}
