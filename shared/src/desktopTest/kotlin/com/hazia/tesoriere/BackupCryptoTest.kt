package com.hazia.tesoriere

import com.hazia.tesoriere.engine.BackupCrypto
import com.hazia.tesoriere.engine.OpenProblem
import com.hazia.tesoriere.engine.PasswordProblem
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/BackupCryptoTest.kt at
 * 2892110. Changed: refusals and password problems are codes, not sentences (SPEC R132, R133);
 * the app's name is asked for, so a Sci-Fin file opens through the same code (SPEC R19). Not
 * ported: the export and restore through Sci-Fin's AppStore and the PIN's hold on export, which
 * are the app's and the app lock's. Every password and hint is made up.
 */
class BackupCryptoTest {
    private val fast = 100_000
    private val plain = """{"app":"tesoriere","schemaVersion":1,"profiles":[]}"""

    private fun seal(text: String = plain, app: String = "tesoriere") =
        BackupCrypto.encrypt(text, "correct horse", "the stable", LocalDate(2026, 11, 26), appTag = app, iterations = fast)

    private fun with(text: String, key: String, value: JsonPrimitive): String =
        JsonObject(Json.parseToJsonElement(text).jsonObject.toMutableMap().also { it[key] = value }).toString()

    private fun refused(text: String, password: String = "correct horse", app: String = "tesoriere"): OpenProblem =
        assertIs<BackupCrypto.Opened.Refused>(BackupCrypto.decrypt(text, password, app)).problem

    // SPEC:R133
    @Test fun `R133 a file opens with its password and with nothing else, and keeps its hint and day in the clear`() {
        val text = seal()
        assertFalse("profiles" in text, "the content is not readable")
        val l = assertNotNull(BackupCrypto.locked(text))
        assertEquals("the stable", l.hint); assertEquals(LocalDate(2026, 11, 26), l.exportedAt); assertEquals("tesoriere", l.app)
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.decrypt(text, "correct hors"))
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.decrypt(text, ""))
        assertEquals(plain, assertIs<BackupCrypto.Opened.Plain>(BackupCrypto.decrypt(text, "correct horse")).text)
        assertNull(BackupCrypto.locked(plain), "a plain file is not asked for a password")
        assertNull(BackupCrypto.locked("{not json"))
    }

    // SPEC:R133
    @Test fun `R133 each file has its own 16-byte salt and 12-byte nonce, at 600000 iterations by default`() {
        val a = Json.parseToJsonElement(seal()).jsonObject
        val b = Json.parseToJsonElement(seal()).jsonObject
        assertNotEquals(a["salt"], b["salt"]); assertNotEquals(a["iv"], b["iv"])
        assertEquals(16, Base64.getDecoder().decode(a["salt"]!!.jsonPrimitive.content).size)
        assertEquals(12, Base64.getDecoder().decode(a["iv"]!!.jsonPrimitive.content).size)
        assertEquals(600_000, BackupCrypto.ITERATIONS)
    }

    // SPEC:R133
    @Test fun `R133 a changed hint, day or iteration count gives WRONG_PASSWORD, and 50 iterations DAMAGED`() {
        val text = seal()
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.decrypt(with(text, "hint", JsonPrimitive("it is: correct horse")), "correct horse"))
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.decrypt(with(text, "exportedAt", JsonPrimitive("2026-11-27")), "correct horse"))
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.decrypt(with(text, "iterations", JsonPrimitive(fast + 1)), "correct horse"))
        assertEquals(OpenProblem.DAMAGED, refused(with(text, "iterations", JsonPrimitive(50))))
        assertEquals(OpenProblem.DAMAGED, refused(with(text, "iterations", JsonPrimitive(5_000_001))))
    }

    // SPEC:R133
    @Test fun `R133 a damaged envelope is DAMAGED, every case, and none of them throws`() {
        val text = seal()
        val short = Base64.getEncoder().encodeToString(ByteArray(4))
        val cases = mapOf(
            "not JSON" to "{not json",
            "a plain backup" to plain,
            "a list" to "[1]",
            "a nonce cut short" to with(text, "iv", JsonPrimitive(short)),
            "no nonce" to with(text, "iv", JsonPrimitive("")),
            "a salt cut short" to with(text, "salt", JsonPrimitive(short)),
            "a salt that is not Base64" to with(text, "salt", JsonPrimitive("%%%")),
            "sealed data shorter than its tag" to with(text, "data", JsonPrimitive(short)),
            "iterations in words" to with(text, "iterations", JsonPrimitive("many")),
            "iterations past an Int" to with(text, "iterations", JsonPrimitive(99999999999L)),
            "a hint that is a number" to with(text, "hint", JsonPrimitive(5)),
            "100000 nested arrays" to "{\"app\":" + "[".repeat(100_000) + "]".repeat(100_000) + "}",
        )
        for ((case, t) in cases) assertEquals(OpenProblem.DAMAGED, refused(t), case)
    }

    // SPEC:R133
    @Test fun `R133 a day of 2026-11-26 bar the with the hint stable is DAMAGED, though it binds as the file's own`() {
        val text = seal()
        // `tesoriere|1|<n>|2026-11-26|the stable` either way: the day must be a day, so the boundary cannot move
        val moved = with(with(text, "exportedAt", JsonPrimitive("2026-11-26|the")), "hint", JsonPrimitive("stable"))
        assertEquals(OpenProblem.DAMAGED, refused(moved))
        val l = assertNotNull(BackupCrypto.locked(moved))
        assertNull(l.hint); assertNull(l.exportedAt); assertNull(l.app)
        for (day in listOf("26/11/2026", "2026-11-26T10:00", "", "2026-02-30")) {
            assertEquals(OpenProblem.DAMAGED, refused(with(text, "exportedAt", JsonPrimitive(day))), day)
        }
    }

    // SPEC:R133
    @Test fun `R133 an envelope of version 2 or of another key function is NEWER_FORMAT`() {
        val text = seal()
        assertEquals(OpenProblem.NEWER_FORMAT, refused(with(text, "encrypted", JsonPrimitive(2))))
        assertEquals(OpenProblem.NEWER_FORMAT, refused(with(text, "kdf", JsonPrimitive("Argon2id"))))
    }

    // SPEC:R133, R19
    @Test fun `R133 a file asked for as sci-fin is OTHER_APP, and a file sealed as Sci-Fin seals it opens as sci-fin`() {
        assertEquals(OpenProblem.OTHER_APP, refused(seal(), app = "sci-fin"))
        assertEquals(OpenProblem.OTHER_APP, refused(seal(app = "sci-fin")))
        // Sci-Fin's envelope at 2892110, built here with javax.crypto alone: the associated data is
        // `sci-fin|1|<iterations>|<exportedAt>|<hint>`, the key PBKDF2-HMAC-SHA256 of 32 bytes
        val salt = ByteArray(16) { it.toByte() }; val iv = ByteArray(12) { (it + 7).toByte() }
        val spec = PBEKeySpec("correct horse".toCharArray(), salt, fast, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            updateAAD("sci-fin|1|$fast|2026-10-20|the stable".encodeToByteArray())
        }
        val sealed = cipher.doFinal("""{"app":"sci-fin"}""".encodeToByteArray())
        val e = Base64.getEncoder()
        val sciFin = """{"app":"sci-fin","encrypted":1,"kdf":"PBKDF2-HMAC-SHA256","iterations":$fast,""" +
            """"salt":"${e.encodeToString(salt)}","iv":"${e.encodeToString(iv)}","exportedAt":"2026-10-20","hint":"the stable","data":"${e.encodeToString(sealed)}"}"""
        assertEquals("sci-fin", BackupCrypto.locked(sciFin)?.app)
        assertEquals("""{"app":"sci-fin"}""", assertIs<BackupCrypto.Opened.Plain>(BackupCrypto.decrypt(sciFin, "correct horse", "sci-fin")).text)
        assertEquals(OpenProblem.OTHER_APP, refused(sciFin))
    }

    // SPEC:R132
    @Test fun `R132 a file password is at least eight characters, typed twice, with a hint that is not the password`() {
        assertEquals(PasswordProblem.TOO_SHORT, BackupCrypto.passwordProblem("short", "short", "h"))
        assertEquals(PasswordProblem.NOT_REPEATED, BackupCrypto.passwordProblem("longenough", "", "h"))
        assertEquals(PasswordProblem.NO_MATCH, BackupCrypto.passwordProblem("longenough", "longenougH", "h"))
        assertEquals(PasswordProblem.NO_HINT, BackupCrypto.passwordProblem("longenough", "longenough", "  "))
        assertEquals(PasswordProblem.HINT_IS_PASSWORD, BackupCrypto.passwordProblem("longenough", "longenough", "LongEnough"))
        assertEquals(PasswordProblem.HINT_IS_PASSWORD, BackupCrypto.passwordProblem("longenough", "longenough", " LongEnough "))
        assertNull(BackupCrypto.passwordProblem("longenough", "longenough", "the usual, twice"))
    }

    // SPEC:R132, R18
    @Test fun `R132 while the app lock is on a file is never written without a password, nor with a weak one`() {
        assertEquals(PasswordProblem.REQUIRED, BackupCrypto.exportProblem(lockOn = true, password = null, again = "", hint = ""))
        assertEquals(PasswordProblem.REQUIRED, BackupCrypto.exportProblem(lockOn = true, password = "", again = "", hint = ""))
        assertNull(BackupCrypto.exportProblem(lockOn = false, password = null, again = "", hint = ""), "a plain file while the lock is off")
        assertEquals(PasswordProblem.TOO_SHORT, BackupCrypto.exportProblem(lockOn = true, password = "short", again = "short", hint = "h"))
        assertEquals(PasswordProblem.NO_HINT, BackupCrypto.exportProblem(lockOn = false, password = "longenough", again = "longenough", hint = ""))
        assertNull(BackupCrypto.exportProblem(lockOn = true, password = "longenough", again = "longenough", hint = "the usual"))
    }
}
