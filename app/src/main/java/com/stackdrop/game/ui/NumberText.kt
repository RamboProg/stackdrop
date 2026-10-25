package com.stackdrop.game.ui

/** The text of a number drawn every frame, rebuilt only when the number changes. */
class NumberText {
    private var value = Int.MIN_VALUE
    private var text = ""

    fun of(v: Int): String {
        if (v != value) {
            value = v
            text = v.toString()
        }
        return text
    }
}
