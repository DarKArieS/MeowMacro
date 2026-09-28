package com.nekroz.meowmacro.floating

import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nekroz.meowmacro.MainWindow
import com.nekroz.meowmacro.R
import com.nekroz.meowmacro.ui.theme.MeowMacroTheme

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FloatingWindow(
    onDrag: (dx: Int, dy: Int) -> Unit,
    onClose: () -> Unit
) {
    // Track raw screen coordinates: local pointer positions shift as the window
    // itself moves, which would make the drag jitter.
    val lastRaw = remember { FloatArray(2) }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        // Size to the widest child; fillMaxWidth/weight below then stretch within that width
        // instead of the whole screen.
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Only the title area is the drag handle, so the close button receives its taps.
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .pointerInteropFilter { event ->
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        lastRaw[0] = event.rawX
                                        lastRaw[1] = event.rawY
                                    }

                                    MotionEvent.ACTION_MOVE -> {
                                        val dx = (event.rawX - lastRaw[0]).toInt()
                                        val dy = (event.rawY - lastRaw[1]).toInt()
                                        if (dx != 0 || dy != 0) {
                                            lastRaw[0] += dx
                                            lastRaw[1] += dy
                                            onDrag(dx, dy)
                                        }
                                    }
                                }
                                true
                            }
                            // Inside the filter so the padding is part of the drag area.
                            .padding(vertical = 6.dp)
                    )
                    val closeLabel = stringResource(R.string.overlay_close)
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = closeLabel }
                    ) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Box(modifier = Modifier.padding(16.dp)) {
                MainWindow(name = "Android")
            }
        }
    }
}

@Preview
@Composable
private fun FloatingWindowPreview() {
    MeowMacroTheme {
        FloatingWindow(onDrag = { _, _ -> }, onClose = {})
    }
}
