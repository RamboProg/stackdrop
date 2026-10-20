package com.stackdrop.game.online

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSnapshotTest {

    @Test
    fun friendCodeDisplaysWithADash() {
        val p = ProfileSnapshot("uid", "Mo", 3, "K7Q2M9XD", emptyMap())
        assertEquals("K7Q2-M9XD", p.displayCode)
    }
}
