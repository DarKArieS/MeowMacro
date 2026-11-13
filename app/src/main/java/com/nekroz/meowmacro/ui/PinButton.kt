package com.nekroz.meowmacro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nekroz.meowmacro.R
import com.nekroz.meowmacro.ui.theme.MeowMacroTheme

/**
 * Button cycling a pin slot: not pinned (0) → 1 → 2 → … → [maxSlots] → not pinned. Slots in
 * [takenSlots], i.e. those used by other items, are skipped. Long-clicking unpins right away.
 * Shows a pin while not pinned and the slot number otherwise.
 */
@Composable
fun PinButton(
    slot: Int,
    takenSlots: Set<Int>,
    maxSlots: Int,
    onSlotChange: (slot: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val next = nextPinSlot(slot, takenSlots, maxSlots)
    val label = if (slot == 0) {
        stringResource(R.string.macro_pin)
    } else {
        stringResource(R.string.macro_pinned, slot)
    }
    // Not pinned with every slot taken: nothing to change to.
    val clickable = enabled && next != slot
    val contentColor = LocalContentColor.current.copy(alpha = if (clickable) 1f else 0.38f)
    // IconButton has no long click, so this stands in for one.
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(
                enabled = clickable,
                role = Role.Button,
                onLongClickLabel = if (slot != 0) stringResource(R.string.macro_unpin) else null,
                onLongClick = if (slot != 0) {
                    { onSlotChange(0) }
                } else {
                    null
                },
                onClick = { onSlotChange(next) }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (slot == 0) {
            Icon(
                painter = painterResource(R.drawable.ic_pin),
                contentDescription = label,
                tint = contentColor
            )
        } else {
            PinNumberIcon(
                number = slot,
                containerColor = if (enabled) MaterialTheme.colorScheme.primary else contentColor,
                modifier = Modifier.clearAndSetSemantics { contentDescription = label }
            )
        }
    }
}

/** The slot after [slot] that isn't in [takenSlots], or 0 (not pinned) once past [maxSlots]. */
fun nextPinSlot(slot: Int, takenSlots: Set<Int>, maxSlots: Int): Int =
    ((slot + 1)..maxSlots).firstOrNull { it !in takenSlots } ?: 0

/** A pin slot [number] on a rounded square, sized to sit where a 24dp icon would. */
@Composable
fun PinNumberIcon(
    number: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.surface,
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PinButtonPreview() {
    MeowMacroTheme {
        Row {
            PinButton(slot = 0, takenSlots = emptySet(), maxSlots = 5, onSlotChange = {})
            PinButton(slot = 1, takenSlots = emptySet(), maxSlots = 5, onSlotChange = {})
            PinButton(slot = 5, takenSlots = setOf(1), maxSlots = 5, onSlotChange = {})
            PinButton(
                slot = 2,
                takenSlots = emptySet(),
                maxSlots = 5,
                onSlotChange = {},
                enabled = false
            )
        }
    }
}
