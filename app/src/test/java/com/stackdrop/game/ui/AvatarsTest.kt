package com.stackdrop.game.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarsTest {

    @Test
    fun defaultMatchesTheBackendHash() {
        // Same case as codes.test.ts: 97 + 98 + 99 = 294, 294 % 12 = 6.
        assertEquals(6, Avatars.defaultFor("abc"))
    }

    @Test
    fun defaultIsAlwaysAValidIndex() {
        assertEquals(0, Avatars.defaultFor(null))
        for (uid in listOf("x", "kQ9zP2mL7wR4tY8uI1oE", "AAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
            assertTrue(Avatars.defaultFor(uid) in 0 until Avatars.COUNT)
        }
    }
}
