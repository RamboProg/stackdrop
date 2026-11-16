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
        assertNotNull(NameRules.problem("__ab__"))
    }

    @Test
    fun promptFilterAllowsOnlyNameCharacters() {
        assertTrue(NameRules.ALLOWED_CHAR.matches("a"))
        assertTrue(NameRules.ALLOWED_CHAR.matches("_"))
        assertTrue(NameRules.ALLOWED_CHAR.matches(" "))
        assertTrue(!NameRules.ALLOWED_CHAR.matches("-"))
    }

    @Test
    fun googleNamesAreFittedToTheRules() {
        assertEquals("Mohamed Ismail", NameRules.fromDisplayName("Mohamed Ismail"))
        assertEquals("Jose Muller", NameRules.fromDisplayName("José Müller"))
        assertEquals("OBrien", NameRules.fromDisplayName("O'Brien"))
        assertEquals("Anne Marie", NameRules.fromDisplayName("  Anne - Marie "))
        // Cut at the last word break that fits; one long word is cut hard.
        assertEquals("Alexandra", NameRules.fromDisplayName("Alexandra Konstantinova"))
        assertEquals("A".repeat(16), NameRules.fromDisplayName("A".repeat(20)))
        assertEquals("Bo Konstantinova", NameRules.fromDisplayName("Bo Konstantinova Smith"))
    }

    @Test
    fun unusableGoogleNamesGiveNothing() {
        assertNull(NameRules.fromDisplayName(null))
        assertNull(NameRules.fromDisplayName("Li"))
        assertNull(NameRules.fromDisplayName("محمد"))
        assertNull(NameRules.fromDisplayName("__ab__"))
    }

    @Test
    fun takenNamesOfferSuggestions() {
        assertEquals("Taken. Try Mo_921 or Mo_92x", NameRules.message("taken", listOf("Mo_921", "Mo_92x")))
        assertEquals("That name is taken", NameRules.message("taken", emptyList()))
    }
}
