package dev.personalterminal.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based envelope for backup archives.
 *
 * Layout: `MAGIC(8) | version(1) | salt(16) | iv(12) | AES-256-GCM ciphertext+tag`.
 * Key = PBKDF2-HMAC-SHA256(passphrase, salt, 200k iterations, 256 bit). Everything needed to
 * decrypt except the passphrase travels with the file, so an archive can be restored on a new
 * phone by typing the passphrase – there is no recovery if it is lost.
 */
object BackupCrypto {
    val MAGIC: ByteArray = "PTBAKX01".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val ITERATIONS = 200_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    fun encrypt(plain: ByteArray, passphrase: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        val ct = cipher.doFinal(plain)
        return MAGIC + byteArrayOf(VERSION) + salt + iv + ct
    }

    fun decrypt(envelope: ByteArray, passphrase: String): ByteArray {
        require(envelope.size > MAGIC.size + 1 + SALT_LEN + IV_LEN) { "not an encrypted archive" }
        require(envelope.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "not an encrypted archive" }
        var o = MAGIC.size
        val version = envelope[o]; o += 1
        require(version == VERSION) { "unsupported archive version $version" }
        val salt = envelope.copyOfRange(o, o + SALT_LEN); o += SALT_LEN
        val iv = envelope.copyOfRange(o, o + IV_LEN); o += IV_LEN
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        return cipher.doFinal(envelope, o, envelope.size - o)
    }

    fun isEncrypted(head: ByteArray): Boolean = head.size >= MAGIC.size && head.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun key(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
