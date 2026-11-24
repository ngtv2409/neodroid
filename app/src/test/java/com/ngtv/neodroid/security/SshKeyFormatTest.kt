package com.ngtv.neodroid.security

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.ECGenParameterSpec

class SshKeyFormatTest {

    @Before
    fun setUp() {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    @Test
    fun `unencrypted OpenSSH key round trips`() {
        val keyPair = generateEd25519()

        val pem = SshKeyFormat.encodeOpenSsh(keyPair, passphrase = null)
        val decoded = SshKeyFormat.decode(pem, passphrase = null)

        assertArrayEquals(keyPair.public.encoded, decoded.public.encoded)
        assertArrayEquals(keyPair.private.encoded, decoded.private.encoded)
    }

    @Test
    fun `encrypted OpenSSH key round trips`() {
        val keyPair = generateEd25519()
        val passphrase = "correct horse".toCharArray()

        val pem = SshKeyFormat.encodeOpenSsh(keyPair, passphrase)
        val decoded = SshKeyFormat.decode(pem, "correct horse".toCharArray())

        assertArrayEquals(keyPair.public.encoded, decoded.public.encoded)
        assertArrayEquals(keyPair.private.encoded, decoded.private.encoded)
    }

    @Test
    fun `encrypted OpenSSH key rejects the wrong passphrase`() {
        val pem = SshKeyFormat.encodeOpenSsh(
            keyPair = generateEd25519(),
            passphrase = "correct horse".toCharArray(),
        )

        assertThrows(SshKeyFormatException::class.java) {
            SshKeyFormat.decode(pem, "wrong passphrase".toCharArray())
        }
    }

    @Test
    fun `RSA and ECDSA keys round trip`() {
        val rsa = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME).apply {
            initialize(2048)
        }.generateKeyPair()
        val ec = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME).apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

        listOf(rsa, ec).forEach { keyPair ->
            val decoded = SshKeyFormat.decode(
                SshKeyFormat.encodeOpenSsh(keyPair, passphrase = null),
                passphrase = null,
            )
            assertArrayEquals(keyPair.public.encoded, decoded.public.encoded)
        }
    }

    @Test
    fun `pkcs8 and public key are matched`() {
        val keyPair = generateEd25519()
        val publicLine = SshKeyFormat.publicKeyLine(keyPair.public)

        val decoded = SshKeyFormat.keyPairFromPkcs8(
            privateKeyPkcs8 = keyPair.private.encoded,
            publicKey = publicLine,
        )

        assertArrayEquals(keyPair.public.encoded, decoded.public.encoded)
        assertNotNull(decoded.private)
    }

    private fun generateEd25519(): KeyPair = KeyPairGenerator
        .getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME)
        .generateKeyPair()
}
