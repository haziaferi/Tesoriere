package com.hazia.tesoriere.engine

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/*
 * Ported from Sci-Fin native/shared/src/jvmCommon/kotlin/app/scifin/engine/BackupCrypto.jvm.kt at
 * 2892110, with pbkdf2Sha256 and randomBytes from its jvmCommon engine/Lock.jvm.kt (SPEC R133).
 * Unchanged but for the package; a cipher failure other than the tag check is left to the caller,
 * which reads it as a damaged file.
 */

private fun gcm(mode: Int, key: ByteArray, iv: ByteArray, aad: ByteArray): Cipher =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        updateAAD(aad)
    }

internal actual fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray =
    gcm(Cipher.ENCRYPT_MODE, key, iv, aad).doFinal(plain)

internal actual fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray? =
    try { gcm(Cipher.DECRYPT_MODE, key, iv, aad).doFinal(sealed) } catch (_: AEADBadTagException) { null }

internal actual fun pbkdf2Sha256(secret: String, salt: ByteArray, iterations: Int): ByteArray {
    val chars = secret.toCharArray()
    val spec = PBEKeySpec(chars, salt, iterations, 256)
    try {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
        spec.clearPassword(); chars.fill('\u0000')
    }
}

private val random = SecureRandom()

internal actual fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)
