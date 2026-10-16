package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionModeTest {
    @Test
    fun `missing mode remains backward compatible`() {
        assertEquals(ProtectionMode.BALANCED, ProtectionMode.fromStored(null))
    }

    @Test
    fun `unknown mode fails safely to balanced`() {
        assertEquals(ProtectionMode.BALANCED, ProtectionMode.fromStored("future_mode"))
    }

    @Test
    fun `strict mode round trips from storage`() {
        assertEquals(ProtectionMode.STRICT, ProtectionMode.fromStored("STRICT"))
    }
}
