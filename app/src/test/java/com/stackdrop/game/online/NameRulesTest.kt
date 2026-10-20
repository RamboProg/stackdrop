package com.stackdrop.game.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameRulesTest {

    @Test
    fun acceptsTheSameNamesAsTheServer() {
        assertNull(NameRules.problem("Mo_92"))
        assertNull(NameRules.problem("  Ali B  "))
        assertNull(NameRules.problem("A".repeat(16)))
    }

    @Test
    fun rejectsBadShapes() {
        assertNotNull(NameRules.problem("Mo"))
        assertNotNull(NameRules.problem("A".repeat(17)))
        assertNotNull(NameRules.problem("Mo-92"))
        assertNotNull(NameRules.problem("Mo  92"))
    }

    @Test
    fun promptFilterAllowsOnlyNameCharacters() {
        assertTrue(NameRules.ALLOWED_CHAR.matches("a"))
        assertTrue(NameRules.ALLOWED_CHAR.matches("_"))
        assertTrue(NameRules.ALLOWED_CHAR.matches(" "))
        assertTrue(!NameRules.ALLOWED_CHAR.matches("-"))
    }

    @Test
    fun takenNamesOfferSuggestions() {
        assertEquals("Taken. Try Mo_921 or Mo_92x", NameRules.message("taken", listOf("Mo_921", "Mo_92x")))
        assertEquals("That name is taken", NameRules.message("taken", emptyList()))
    }
}
