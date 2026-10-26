package com.vasilispngs.adblock.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SelfUpdaterTest {
    @Test
    fun `names a file by its SHA-256 digest as GitHub does`() {
        assertEquals(
            "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256("abc".byteInputStream())
        )
    }

    @Test
    fun `hands every block it reads on`() {
        val copied = StringBuilder()

        sha256("abc".byteInputStream()) { buffer, read -> copied.append(String(buffer, 0, read)) }

        assertEquals("abc", copied.toString())
    }
}
