package org.nikoresu.transcare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nikoresu.transcare.data.LocalDataCipher

class LocalDataCipherTest {

    private val magic = "TCENC1".toByteArray(Charsets.US_ASCII)

    @Test
    fun recognisesTheCurrentFrame() {
        val frame = magic + byteArrayOf(1, 12) + ByteArray(12)

        assertTrue(LocalDataCipher.isEncrypted(frame))
        assertFalse(LocalDataCipher.isLegacyFrame(frame))
    }

    @Test
    fun recognisesTheLegacyFrameWithoutAVersionByte() {
        val frame = magic + byteArrayOf(12) + ByteArray(12)

        assertTrue(LocalDataCipher.isEncrypted(frame))
        assertTrue(LocalDataCipher.isLegacyFrame(frame))
    }

    @Test
    fun plainTextIsNotEncrypted() {
        assertFalse(LocalDataCipher.isEncrypted("[]".toByteArray()))
        assertFalse(LocalDataCipher.isLegacyFrame("[]".toByteArray()))
        assertFalse(LocalDataCipher.isEncrypted(magic))
    }
}
