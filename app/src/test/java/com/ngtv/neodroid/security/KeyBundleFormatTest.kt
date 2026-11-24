package com.ngtv.neodroid.security

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Security

class KeyBundleFormatTest {

    @Before
    fun setUp() {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    @Test
    fun `bundle round trips`() {
        val first = generateEd25519()
        val second = generateEd25519()
        val entries = listOf(
            entry(first, "first"),
            entry(second, "second"),
        )

        val encoded = KeyBundleFormat.encode(entries, "a strong passphrase".toCharArray())
        val decoded = KeyBundleFormat.decode(encoded, "a strong passphrase".toCharArray())

        assertEquals(2, decoded.size)
        assertEquals("first", decoded[0].displayName)
        assertArrayEquals(first.private.encoded, decoded[0].privateKeyPkcs8)
        assertEquals("second", decoded[1].displayName)
        assertArrayEquals(second.private.encoded, decoded[1].privateKeyPkcs8)
    }

    @Test
    fun `bundle rejects the wrong passphrase`() {
        val encoded = KeyBundleFormat.encode(
            listOf(entry(generateEd25519(), "key")),
            "a strong passphrase".toCharArray(),
        )

        assertThrows(KeyBundleFormatException::class.java) {
            KeyBundleFormat.decode(encoded, "wrong passphrase".toCharArray())
        }
    }

    @Test
    fun `tampered header is rejected`() {
        val encoded = KeyBundleFormat.encode(
            listOf(entry(generateEd25519(), "key")),
            "a strong passphrase".toCharArray(),
        )
        encoded[KeyBundleFormatHeader.ITERATIONS_OFFSET] = 3

        assertThrows(KeyBundleFormatException::class.java) {
            KeyBundleFormat.decode(encoded, "a strong passphrase".toCharArray())
        }
    }

    private fun entry(keyPair: java.security.KeyPair, name: String): KeyBundleEntry =
        KeyBundleEntry(
            displayName = name,
            algorithm = SshKeyFormat.algorithmFor(keyPair.public)!!.storageValue,
            publicKey = SshKeyFormat.publicKeyLine(keyPair.public),
            privateKeyPkcs8 = keyPair.private.encoded,
        )

    private fun generateEd25519(): java.security.KeyPair = KeyPairGenerator
        .getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
        .generateKeyPair()

    private object KeyBundleFormatHeader {
        // magic (8) + version (1) + kdf id (1)
        const val ITERATIONS_OFFSET = 10
    }
}
