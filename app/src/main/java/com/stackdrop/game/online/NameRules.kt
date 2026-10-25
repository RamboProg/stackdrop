package com.stackdrop.game.online

/**
 * The same shape rules as backend/functions/src/names.ts, checked before calling the server so
 * players get instant feedback. The server stays the authority and also filters offensive words.
 */
object NameRules {

    const val MIN = 3
    const val MAX = 16

    /** Characters the name prompt lets through. */
    val ALLOWED_CHAR = Regex("[A-Za-z0-9_ ]")

    private val NAME = Regex("[A-Za-z0-9_ ]+")

    /** Reserved by Firestore for document IDs; the server refuses these too. */
    private val RESERVED = Regex("__.*__")

    /** Returns a message for the player, or null when the name looks fine. */
    fun problem(raw: String): String? {
        val name = raw.trim()
        return when {
            name.length < MIN || name.length > MAX -> "Names are $MIN to $MAX characters"
            !NAME.matches(name) || RESERVED.matches(name) -> "Use letters, numbers, spaces or _"
            "  " in name -> "No double spaces, please"
            else -> null
        }
    }

    /** Server refusal reasons from claimName, as friendly text. */
    fun message(reason: String, suggestions: List<String>): String = when (reason) {
        "taken" -> if (suggestions.isEmpty()) "That name is taken" else "Taken. Try ${suggestions.joinToString(" or ")}"
        "banned" -> "Please pick a different name"
        "rate-limited" -> "You can change your name 3 times a day"
        "length" -> "Names are $MIN to $MAX characters"
        "characters" -> "Use letters, numbers, spaces or _"
        "spaces" -> "No double spaces, please"
        else -> "Couldn't save your name. Try again"
    }
}
