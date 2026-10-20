package com.stackdrop.game.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FriendCodesTest {

    @Test
    fun matchesTheServerNormalization() {
        // Same cases as backend/functions/test/codes.test.ts.
        assertEquals("K7Q2M9XD", FriendCodes.normalize("k7q2-m9xd"))
        assertEquals("K7Q2M9XD", FriendCodes.normalize(" K7Q2 M9XD "))
        assertEquals("00111234", FriendCodes.normalize("O0IL-1234"))
        assertNull(FriendCodes.normalize("K7Q2-M9X"))
        assertNull(FriendCodes.normalize("K7Q2-M9XU"))
    }
}
