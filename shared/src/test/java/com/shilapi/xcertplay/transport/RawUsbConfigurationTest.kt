package com.shilapi.xcertplay.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawUsbConfigurationTest {
    @Test
    fun keepsInterfacesInTheirRealConfigurations() {
        val raw = byteArrayOf(
            18, 1, 0, 2, 0, 0, 0, 64, 0x05, 0x0a, 0x12, 0xa8.toByte(), 0, 1, 1, 2, 3, 2,
            9, 2, 18, 0, 1, 1, 0, 0x80.toByte(), 50,
            9, 4, 0, 0, 0, 6, 1, 1, 0,
            9, 2, 36, 0, 2, 6, 0, 0x80.toByte(), 50,
            9, 4, 1, 0, 2, 0xff.toByte(), 0xfe.toByte(), 2, 0,
            9, 4, 2, 0, 1, 2, 0x0d, 0, 0,
            9, 4, 3, 1, 2, 0x0a, 0, 0, 0,
        )

        val configurations = parseRawUsbConfigurations(raw)

        assertEquals(listOf(1, 6), configurations.map { it.id })
        assertEquals(listOf(0), configurations[0].interfaces.map { it.id })
        assertEquals(listOf(1, 2, 3), configurations[1].interfaces.map { it.id })
        assertEquals(1, configurations[1].interfaces.last().alternateSetting)
    }

    @Test
    fun rejectsTruncatedDescriptor() {
        assertTrue(parseRawUsbConfigurations(byteArrayOf(9, 2, 1)).isEmpty())
    }
}
