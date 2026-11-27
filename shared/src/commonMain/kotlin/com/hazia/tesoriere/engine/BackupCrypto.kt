package com.hazia.tesoriere.engine

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/BackupCrypto.kt at 2892110,
 * with pbkdf2Sha256 and randomBytes from its engine/Lock.kt.
 *
 * A backup with a password (SPEC R132, R133): the plain file wrapped whole in AES-256-GCM, keyed by
 * PBKDF2-HMAC-SHA256. Outside the encryption, for anyone holding the file: the app's name, the
 * export day and the hint, bound to the sealed data with the iteration count as associated data,
 * so none can be changed without the file refusing to open.
 *
 * Changed from Sci-Fin:
 * - The app's name is a parameter (`appTag`), in the envelope and in the associated data, so the
 *   same code opens Tesoriere's files (`tesoriere`) and Sci-Fin's (`sci-fin`, SPEC R19); the
 *   associated data keeps Sci-Fin's layout `<app>|1|<iterations>|<exportedAt>|<hint>`.
 * - Password problems and refusals are codes ([PasswordProblem], [OpenProblem]), not sentences
 *   (SPEC R16). The hint check is Sci-Fin's Lock.kt hintProblem, shared with the app lock (Lock.kt, SPEC R146).
 * - Opening never throws on a file's contents: every envelope field is required, a salt not of 16
 *   bytes, a nonce not of 12 or sealed data shorter than its tag is DAMAGED, and any failure of the
 *   cipher other than its tag check is DAMAGED; Sci-Fin let a cut nonce throw. A key that cannot be
 *   drawn from the password given is WRONG_PASSWORD.
 * - The export day is a `YYYY-MM-DD` day (encrypt takes a LocalDate; an envelope with any other
 *   day is DAMAGED): the associated data joins the day and the hint with `|` unescaped, as Sci-Fin
 *   wrote it, so a day holding more could move the boundary between them. Sci-Fin wrote its day so.
 * - [LockedBackup] names the file's app, so the caller knows which reader the opened text goes to,
 *   and shows no app, hint or day for an envelope that cannot be read.
 * - [BackupCrypto.exportProblem] decides that a file is not written without a password while the
 *   app lock is on (SPEC R132); Sci-Fin's AppStore decided it.
 * - pbkdf2Sha256 and randomBytes, Sci-Fin's Lock.kt's, are declared here; the app lock (Lock.kt,
 *   SPEC R146) uses them too.
 * - Section references are replaced by SPEC rule numbers.
 */

/** The envelope of a file with a password (SPEC R133). Every field is required, so none can be missing unnoticed. */
@Serializable
data class EncryptedBackupFile(
    val app: String,
    val encrypted: Int,
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val iv: String,
    val exportedAt: String,
    val hint: String,
    val data: String,
)

/**
 * A file that needs its password before it can be read: what the prompt shows, and the text to open.
 * [app], [hint] and [exportedAt] are null when the envelope cannot be read; opening it is then DAMAGED.
 */
data class LockedBackup(val text: String, val app: String?, val hint: String?, val exportedAt: LocalDate?)

/** Why a file cannot be written with this password yet (SPEC R132), the first that applies. */
enum class PasswordProblem { REQUIRED, TOO_SHORT, NOT_REPEATED, NO_MATCH, NO_HINT, HINT_IS_PASSWORD }

/** Why an encrypted file cannot be opened, other than a wrong password (SPEC R133). */
enum class OpenProblem {
    /** Not an encrypted backup, or one whose fields cannot be read or whose cost is out of range. */
    DAMAGED,

    /** Of another app than the one asked for. */
    OTHER_APP,

    /** Of an envelope version or key function this app does not know. */
    NEWER_FORMAT,
}

object BackupCrypto {
    /** A file leaves the device, so its cost is a file's (OWASP's figure for PBKDF2-HMAC-SHA256). */
    const val ITERATIONS = 600_000

    /** What a file may ask for: below, it was not written by this app; above, it would hang the phone. */
    val ACCEPTED_ITERATIONS = 100_000..5_000_000
    const val MIN_PASSWORD = 8
    const val SALT_BYTES = 16
    const val IV_BYTES = 12

    /** The GCM tag appended to the sealed data, in bytes. */
    private const val TAG_BYTES = 16
    private const val VERSION = 1
    private const val KDF = "PBKDF2-HMAC-SHA256"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    /**
     * Why [password] cannot protect a file yet, or null when it can (SPEC R132): long enough, typed
     * twice the same as [again], and with a [hint] that is not the password itself, compared
     * trimmed and ignoring case.
     */
    fun passwordProblem(password: String, again: String, hint: String): PasswordProblem? = when {
        password.length < MIN_PASSWORD -> PasswordProblem.TOO_SHORT
        again.isEmpty() -> PasswordProblem.NOT_REPEATED
        again != password -> PasswordProblem.NO_MATCH
        hint.isBlank() -> PasswordProblem.NO_HINT
        hintProblem(password, hint) != null -> PasswordProblem.HINT_IS_PASSWORD
        else -> null
    }

    /**
     * What stops the export from writing its file (SPEC R132): no [password] (null or empty: a plain
     * file) while the app lock is on, else, with a password, [passwordProblem]'s first problem.
     */
    fun exportProblem(lockOn: Boolean, password: String?, again: String, hint: String): PasswordProblem? = when {
        password.isNullOrEmpty() -> if (lockOn) PasswordProblem.REQUIRED else null
        else -> passwordProblem(password, again, hint)
    }

    private fun aad(appTag: String, exportedAt: String, hint: String, iterations: Int) =
        "$appTag|$VERSION|$iterations|$exportedAt|$hint".encodeToByteArray()

    /** [plain] sealed under [password], with [hint] and [exportedAt] in the clear (SPEC R133). The hint is kept trimmed. */
    fun encrypt(plain: String, password: String, hint: String, exportedAt: LocalDate, appTag: String = Backup.APP, iterations: Int = ITERATIONS): String {
        val salt = randomBytes(SALT_BYTES); val iv = randomBytes(IV_BYTES)
        val key = pbkdf2Bytes(password, salt, iterations)
        val h = hint.trim()
        val day = exportedAt.toString()
        val sealed = aesGcmEncrypt(key, iv, plain.encodeToByteArray(), aad(appTag, day, h, iterations))
        return json.encodeToString(
            EncryptedBackupFile.serializer(),
            EncryptedBackupFile(appTag, VERSION, KDF, iterations, Base64.encode(salt), Base64.encode(iv), day, h, Base64.encode(sealed)),
        )
    }

    /**
     * The prompt's view of [text] when it is an encrypted backup of any app; null for anything else (a
     * plain file goes to Backup.validate). An envelope that cannot be read shows no app, hint or day;
     * one of a known format that [decrypt] would refuse as DAMAGED, its iteration count out of bounds
     * or its salt, nonce or sealed data unreadable or cut short, shows its app but no hint or day (SPEC R133).
     */
    fun locked(text: String): LockedBackup? {
        val o = BackupJson.parseObject(text) ?: return null
        if (!BackupJson.markedEncrypted(o)) return null
        val f = envelope(o) ?: return LockedBackup(text, null, null, null)
        if (f.encrypted == VERSION && f.kdf == KDF && sealedParts(f) == null) return LockedBackup(text, f.app, null, null)
        return LockedBackup(text, f.app, f.hint, BackupJson.day(f.exportedAt))
    }

    /**
     * [f]'s salt, nonce and sealed data, or null when its iteration count is out of bounds or any of
     * them cannot be read or is not of its size: the envelope is DAMAGED (SPEC R133).
     */
    private fun sealedParts(f: EncryptedBackupFile): Triple<ByteArray, ByteArray, ByteArray>? {
        if (f.iterations !in ACCEPTED_ITERATIONS) return null
        val (salt, iv, sealed) = try {
            Triple(Base64.decode(f.salt), Base64.decode(f.iv), Base64.decode(f.data))
        } catch (_: Throwable) {
            return null
        }
        if (salt.size != SALT_BYTES || iv.size != IV_BYTES || sealed.size < TAG_BYTES) return null
        return Triple(salt, iv, sealed)
    }

    /**
     * [o] as an envelope whose export day is a `YYYY-MM-DD` day, else null (SPEC R133): the day and
     * the hint are joined in the associated data, so a day holding more could move the boundary.
     */
    private fun envelope(o: JsonObject): EncryptedBackupFile? {
        val f = try {
            json.decodeFromJsonElement(EncryptedBackupFile.serializer(), o)
        } catch (_: Throwable) {
            return null
        }
        return f.takeIf { BackupJson.day(it.exportedAt) != null }
    }

    sealed interface Opened {
        data class Plain(val text: String) : Opened
        data object WrongPassword : Opened
        data class Refused(val problem: OpenProblem) : Opened
    }

    /**
     * The backup text sealed in [text] under [password], for app [appTag] (SPEC R133), or why not.
     * Nothing is changed either way, and it never throws on a file's contents.
     */
    fun decrypt(text: String, password: String, appTag: String = Backup.APP): Opened {
        val o = BackupJson.parseObject(text) ?: return Opened.Refused(OpenProblem.DAMAGED)
        val f = envelope(o) ?: return Opened.Refused(OpenProblem.DAMAGED)
        if (f.app != appTag) return Opened.Refused(OpenProblem.OTHER_APP)
        if (f.encrypted != VERSION || f.kdf != KDF) return Opened.Refused(OpenProblem.NEWER_FORMAT)
        val (salt, iv, sealed) = sealedParts(f) ?: return Opened.Refused(OpenProblem.DAMAGED)
        val key = try {
            pbkdf2Bytes(password, salt, f.iterations)
        } catch (_: Throwable) {
            // a password the key function refuses cannot be the one the file was sealed with
            return Opened.WrongPassword
        }
        val plain = try {
            aesGcmDecrypt(key, iv, sealed, aad(appTag, f.exportedAt, f.hint, f.iterations))
        } catch (_: Throwable) {
            return Opened.Refused(OpenProblem.DAMAGED)
        }
        // GCM cannot tell a wrong password from a changed file: both fail the tag
        if (plain == null) return Opened.WrongPassword
        return Opened.Plain(plain.decodeToString())
    }
}

/** PBKDF2-HMAC-SHA256 over any password, 32 bytes. */
internal fun pbkdf2Bytes(password: String, salt: ByteArray, iterations: Int): ByteArray = pbkdf2Sha256(password, salt, iterations)

/** PBKDF2-HMAC-SHA256, 32 bytes. The platform's own implementation (javax.crypto on both hosts). */
internal expect fun pbkdf2Sha256(secret: String, salt: ByteArray, iterations: Int): ByteArray

/** Cryptographically strong random bytes. */
internal expect fun randomBytes(n: Int): ByteArray

/** AES-256-GCM, 128-bit tag appended. */
internal expect fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray

/** The plain bytes, or null when the tag does not verify (wrong key, or the file was altered). */
internal expect fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray?
