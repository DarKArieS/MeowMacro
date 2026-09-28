package com.nekroz.meowmacro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nekroz.meowmacro.macro.MacroState
import com.nekroz.meowmacro.ui.theme.MeowMacroTheme

@Composable
fun MainWindow(
    macroState: MacroState,
    eventCount: Int,
    onRecordClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
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
        val playing = macroState == MacroState.Playing
        IconButton(
            onClick = onPlayClick,
            enabled = macroState != MacroState.Recording && eventCount > 0
        ) {
            Icon(
                painter = painterResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play),
                contentDescription = stringResource(
                    if (playing) R.string.macro_stop_playing else R.string.macro_play
                )
            )
        }
        Text(
            text = when (macroState) {
                MacroState.Idle -> stringResource(R.string.macro_event_count, eventCount)
                MacroState.Recording -> stringResource(R.string.macro_recording, eventCount)
                MacroState.Playing -> stringResource(R.string.macro_playing)
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainWindowPreview() {
    MeowMacroTheme {
        MainWindow(macroState = MacroState.Idle, eventCount = 3, onRecordClick = {}, onPlayClick = {})
    }
}
