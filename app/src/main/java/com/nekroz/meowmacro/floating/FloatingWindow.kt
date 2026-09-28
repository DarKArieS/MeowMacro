package com.nekroz.meowmacro.floating

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nekroz.meowmacro.R
import com.nekroz.meowmacro.ui.theme.MeowMacroTheme

@Composable
fun FloatingWindow(
    onDrag: (dx: Int, dy: Int) -> Boolean,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    var minimized by rememberSaveable { mutableStateOf(false) }
    AnimatedContent(
        targetState = minimized,
        transitionSpec = {
            // The window is anchored at its top-left corner, so grow and shrink from there too.
            val enter = fadeIn(tween(TRANSITION_MILLIS, delayMillis = TRANSITION_MILLIS / 3)) +
                scaleIn(
                    tween(TRANSITION_MILLIS),
                    initialScale = 0.8f,
                    transformOrigin = TransformOrigin(0f, 0f)
                )
            val exit = fadeOut(tween(TRANSITION_MILLIS / 2)) +
                scaleOut(
                    tween(TRANSITION_MILLIS),
                    targetScale = 0.8f,
                    transformOrigin = TransformOrigin(0f, 0f)
                )
            // The overlay window wraps its content, and resizing a window every frame is very
            // janky. So hold the larger of both sizes for the whole transition, animating only
            // scale and alpha, and snap to the final size at the end: two resizes in total.
            enter togetherWith exit using SizeTransform { initial, target ->
                val larger = IntSize(
                    maxOf(initial.width, target.width),
                    maxOf(initial.height, target.height)
                )
                keyframes {
                    durationMillis = TRANSITION_MILLIS
                    larger at 0
                    larger at TRANSITION_MILLIS - 1
                }
            }
        },
        contentAlignment = Alignment.TopStart,
        label = "minimize"
    ) { isMinimized ->
        if (isMinimized) {
            MinimizedBlock(onDrag = onDrag, onExpand = { minimized = false })
        } else {
            ExpandedWindow(
                onDrag = onDrag,
                onMinimize = { minimized = true },
                onClose = onClose,
                content = content
            )
        }
    }
}

private const val TRANSITION_MILLIS = 250

@Composable
private fun ExpandedWindow(
    onDrag: (dx: Int, dy: Int) -> Boolean,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        // Notw: Use IntrinsicSize.Max let size to the widest child
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
                            .rememberWindowDragModifier(onDrag)
                            // Inside the filter so the padding is part of the drag area.
                            .padding(vertical = 6.dp)
                    )
                    val minimizeLabel = stringResource(R.string.overlay_minimize)
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = minimizeLabel }
                    ) {
                        Text(
                            text = "－",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
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
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                content()
            }
        }
    }
}

/** Solid block standing in for the minimized window: drag to move, tap to expand. */
@Composable
private fun MinimizedBlock(
    onDrag: (dx: Int, dy: Int) -> Boolean,
    onExpand: () -> Unit,
) {
    val expandLabel = stringResource(R.string.overlay_expand)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            // The drag filter consumes the raw touches, so expose the tap to accessibility here.
            .semantics {
                contentDescription = expandLabel
                onClick {
                    onExpand()
                    true
                }
            }
            .rememberWindowDragModifier(onDrag, onClick = onExpand)
    )
}

@Preview
@Composable
private fun MinimizedBlockPreview() {
    MeowMacroTheme {
        MinimizedBlock(onDrag = { _, _ -> true }, onExpand = {})
    }
}

@Preview
@Composable
private fun FloatingWindowPreview() {
    MeowMacroTheme {
        FloatingWindow(
            onDrag = { _, _ -> true },
            onClose = {}
        ) {
            Text("Content")
        }
    }
}
