package com.audiops.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BalancedRowsTest {
    private val five = listOf(1, 2, 3, 4, 5)

    @Test fun fewerItemsThanColumnsFillOneRow() {
        assertEquals(listOf(listOf(1, 2)), balancedRows(listOf(1, 2), columns = 6))
    }

    @Test fun rowsAreAsEvenAsPossible() {
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5)), balancedRows(five, columns = 3))
        assertEquals(listOf(listOf(1, 2), listOf(3)), balancedRows(listOf(1, 2, 3), columns = 2))
        assertEquals(five.map { listOf(it) }, balancedRows(five, columns = 1))
    }

    @Test fun noItemsNoRows() {
        assertEquals(emptyList<List<Int>>(), balancedRows(emptyList<Int>(), columns = 3))
    }
}
