package com.dailytalk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNumberTest {
    @Test fun generatesValidTenDigitNumbers() {
        repeat(500) { assertTrue(Regex("^[1-9][0-9]{9}$").matches(ChatNumber.generate())) }
    }
    @Test fun skipsBiasedBytesAndLeadingZero() {
        val seq = listOf(255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10).iterator()
        assertEquals("1234567890", ChatNumber.generate { seq.next() })
    }
    @Test fun normalisesTypedNumbers() {
        assertEquals("1234567890", ChatNumber.normalize("12345 67890"))
        assertEquals("1234567890", ChatNumber.normalize("12345-67890"))
    }
    @Test fun rejectsInvalidNumbers() {
        listOf("", "0123456789", "12345", "12345678901", "abcdefghij", "+911234567890").forEach { assertNull(it, ChatNumber.normalize(it)) }
    }
    @Test fun formatsForDisplay() {
        assertEquals("12345 67890", ChatNumber.format("1234567890"))
        assertEquals("nonsense", ChatNumber.format("nonsense"))
    }
}
