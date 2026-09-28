package com.nekroz.meowmacro.macro

/** A named, saved recording. */
data class Macro(
    val name: String,
    val macro: List<MacroEvent>,
)
