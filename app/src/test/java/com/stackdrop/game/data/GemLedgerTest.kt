package com.stackdrop.game.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GemLedgerTest {

    @Test
    fun balanceIsEarnedMinusSpentAcrossDevices() {
        val ledger = mapOf("a" to GemCount(100, 30), "b" to GemCount(20, 0))
        assertEquals(90, GemLedger.total(ledger))
    }

    @Test
    fun balanceNeverGoesNegative() {
        assertEquals(0, GemLedger.total(mapOf("a" to GemCount(10, 0), "b" to GemCount(0, 40))))
    }

    @Test
    fun addRecordsEarningAndSpendingSeparately() {
        val earned = GemLedger.add(emptyMap(), "a", 25)
        val spent = GemLedger.add(earned, "a", -10)
        assertEquals(GemCount(25, 10), spent["a"])
        assertEquals(15, GemLedger.total(spent))
    }

    @Test
    fun mergeKeepsTheLargerCountPerDevice() {
        val a = mapOf("x" to GemCount(50, 10), "y" to GemCount(5, 0))
        val b = mapOf("x" to GemCount(40, 20), "z" to GemCount(7, 1))
        val merged = GemLedger.merge(a, b)
        assertEquals(mapOf("x" to GemCount(50, 20), "y" to GemCount(5, 0), "z" to GemCount(7, 1)), merged)
        assertEquals(merged, GemLedger.merge(merged, a))
    }

    @Test
    fun encodingRoundTripsAndSkipsJunk() {
        val ledger = mapOf("legacy" to GemCount(300, 0), "abc123" to GemCount(12, 4))
        assertEquals(ledger, GemLedger.decode(GemLedger.encode(ledger)))
        assertEquals(mapOf("ok" to GemCount(1, 2)), GemLedger.decode("ok:1:2;bad;x:y:z;:3:4"))
        assertEquals(emptyMap<String, GemCount>(), GemLedger.decode(""))
    }
}
