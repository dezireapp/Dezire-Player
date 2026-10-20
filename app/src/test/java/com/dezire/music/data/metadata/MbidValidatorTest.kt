package com.dezire.music.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MbidValidatorTest {

    private val validMbid = "a74b1b7f-71a5-4011-9441-d0b5e4122711"

    @Test
    fun `accepts and lower-cases valid uuids`() {
        assertTrue(MbidValidator.isValid(validMbid))
        assertEquals(validMbid, MbidValidator.normalize(validMbid))
        assertEquals(
            validMbid,
            MbidValidator.normalize("A74B1B7F-71A5-4011-9441-D0B5E4122711")
        )
    }

    @Test
    fun `treats missing mbid as null`() {
        assertNull(MbidValidator.normalize(null))
        assertNull(MbidValidator.normalize(""))
        assertNull(MbidValidator.normalize("   "))
    }

    @Test
    fun `rejects placeholder and malformed values`() {
        assertNull(MbidValidator.normalize("<unknown>"))
        assertNull(MbidValidator.normalize("not-a-mbid"))
        assertNull(MbidValidator.normalize("a74b1b7f71a540119441d0b5e4122711"))
        assertFalse(MbidValidator.isValid("123"))
    }
}
