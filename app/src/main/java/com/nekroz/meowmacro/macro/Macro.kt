package com.nekroz.meowmacro.macro

/** How many macros can be pinned at once; pin slots run from 1 to this. */
const val MAX_PINNED_MACROS = 5

/**
 * A named, saved recording. A macro that isn't [isEnabled] is kept but can't be played. Playing
 * one minimizes the floating window to get it out of the way, unless [minimizeOnPlay] is off.
 * [pinSlot] is the macro's number among the pinned macros, unique among them, or 0 if not pinned.
 */
data class Macro(
    val name: String,
    val macro: List<MacroEvent>,
    val isEnabled: Boolean = true,
    val minimizeOnPlay: Boolean = true,
    val pinSlot: Int = 0,
)
