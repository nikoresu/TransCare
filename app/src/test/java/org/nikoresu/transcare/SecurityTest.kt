package org.nikoresu.transcare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nikoresu.transcare.security.PinHasher

class SecurityTest {

    private val iterations = 10_000

    @Test
    fun pinValidationRequiresFourToEightDigits() {
        assertFalse(PinHasher.isValidPin(""))
        assertFalse(PinHasher.isValidPin("123"))
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.isValidPin("12 4"))
    }

    @Test
    fun hashVerifiesOnlyTheOriginalPin() {
        val hashed = PinHasher.hash("4821", iterations = iterations)

        assertTrue(
            PinHasher.verify("4821", hashed.salt, hashed.hash, hashed.iterations),
        )
        assertFalse(
            PinHasher.verify("4822", hashed.salt, hashed.hash, hashed.iterations),
        )
        assertFalse(
            PinHasher.verify("4821", hashed.salt, hashed.hash, hashed.iterations + 1),
        )
    }

    @Test
    fun everyHashUsesAFreshSalt() {
        val first = PinHasher.hash("4821", iterations = iterations)
        val second = PinHasher.hash("4821", iterations = iterations)

        assertNotEquals(first.salt.toList(), second.salt.toList())
        assertNotEquals(first.hash.toList(), second.hash.toList())
        assertTrue(
            PinHasher.verify("4821", first.salt, first.hash, first.iterations),
        )
        assertTrue(
            PinHasher.verify("4821", second.salt, second.hash, second.iterations),
        )
    }

    @Test
    fun invalidPinCannotBeHashed() {
        assertThrows(IllegalArgumentException::class.java) {
            PinHasher.hash("12", iterations = iterations)
        }
    }

    @Test
    fun verifyRejectsMissingHashData() {
        assertFalse(PinHasher.verify("4821", ByteArray(0), ByteArray(32), iterations))
        assertFalse(PinHasher.verify("4821", ByteArray(16), ByteArray(0), iterations))
        assertFalse(PinHasher.verify("4821", ByteArray(16), ByteArray(32), 0))
    }
}
