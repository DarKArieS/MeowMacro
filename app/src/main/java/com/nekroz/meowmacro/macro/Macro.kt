package com.nekroz.meowmacro.macro

/** A named, saved recording. A macro that isn't [isEnabled] is kept but can't be played. */
data class Macro(
    val name: String,
    val macro: List<MacroEvent>,
    val isEnabled: Boolean = true,
)
