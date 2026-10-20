package com.stackdrop.game.online

/** Client copy of backend/functions/src/codes.ts normalizeCode, for instant feedback on typos. */
object FriendCodes {

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val LENGTH = 8

    /** Characters the code prompt lets through; look-alikes (O, I, L) are fixed up below. */
    val ALLOWED_CHAR = Regex("[A-Za-z0-9 -]")

    /** The stored form ("K7Q2M9XD"), or null when the input cannot be a friend code. */
    fun normalize(input: String): String? {
        val s = input.uppercase()
            .filterNot { it == ' ' || it == '-' }
            .replace('O', '0')
            .replace('I', '1')
            .replace('L', '1')
        if (s.length != LENGTH || s.any { it !in ALPHABET }) return null
        return s
    }
}
