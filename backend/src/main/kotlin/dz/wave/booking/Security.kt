package dz.wave.booking

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Booking references: 6 characters without ambiguous glyphs (no 0/O, 1/I/L), ~887 million values. */
object BookingReferences {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun next(): String = buildString { repeat(6) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    fun isWellFormed(ref: String): Boolean = ref.length == 6 && ref.all { it in ALPHABET }
}

/** AES-256-GCM for passenger PII at rest (names, document numbers, contact details). */
class PiiCipher(key: ByteArray) {
    private val secretKey = SecretKeySpec(key.also { require(it.size == 32) { "PII key must be 32 bytes" } }, "AES")
    private val random = SecureRandom()

    fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES))
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }

    companion object {
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}

/**
 * Signs the e-ticket QR payload so port staff can verify it offline-first without trusting
 * whatever the passenger shows: `WAVE1.<ref>.<issuedAtEpoch>.<signature>`.
 */
class TicketSigner(key: ByteArray) {
    private val keySpec = SecretKeySpec(key, "HmacSHA256")

    fun sign(reference: String, issuedAt: Instant): String {
        val body = "WAVE1.$reference.${issuedAt.epochSecond}"
        return "$body.${mac(body)}"
    }

    /** Returns the booking reference when the payload is authentic. */
    fun verify(payload: String): String? {
        val parts = payload.split('.')
        if (parts.size != 4 || parts[0] != "WAVE1") return null
        val body = parts.take(3).joinToString(".")
        val expected = mac(body)
        val ok = MessageDigest.isEqual(expected.toByteArray(), parts[3].toByteArray())
        return if (ok) parts[1] else null
    }

    private fun mac(body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keySpec)
        val digest = mac.doFinal(body.toByteArray()).copyOf(16)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
