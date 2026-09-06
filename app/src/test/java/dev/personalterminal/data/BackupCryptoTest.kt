package dev.personalterminal.data

import dev.personalterminal.data.backup.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    @Test fun `round trip`() {
        val plain = "hello archive".toByteArray()
        val enc = BackupCrypto.encrypt(plain, "correct horse")
        assertTrue(BackupCrypto.isEncrypted(enc))
        assertArrayEquals(plain, BackupCrypto.decrypt(enc, "correct horse"))
    }

    @Test fun `wrong passphrase fails`() {
        val enc = BackupCrypto.encrypt("x".toByteArray(), "a")
        try { BackupCrypto.decrypt(enc, "b"); fail("should not decrypt") } catch (_: Exception) {}
    }

    @Test fun `plain zip is not mistaken for an envelope`() {
        assertTrue(!BackupCrypto.isEncrypted("PK\u0003\u0004abcdefgh".toByteArray()))
    }
}
