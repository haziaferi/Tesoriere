package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.AppLock
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Lock.kt at 2892110.
 *
 * The app lock (SPEC R146): an optional PIN of the app's own, off on a fresh install (SPEC R18),
 * on while its one AppLock row is kept, never in a backup (SPEC R130). The PIN guards the screen,
 * not the data: the store is not encrypted with it.
 *
 * Changed from Sci-Fin:
 * - A PIN is the digits 0 to 9 alone; Sci-Fin's Char.isDigit took any script's digits, which a
 *   phone's number pad cannot type. Pin.clean keeps the same digits.
 * - Pin.lockFor gives null, not an exception, for a PIN that is not one, for a hint that is the
 *   PIN (Sci-Fin's AppStore checked that) and for a cost outside ACCEPTED_ITERATIONS.
 * - A stored row that cannot be read (a salt or hash not Base64, an empty salt, a hash not of 32
 *   bytes, a cost outside ACCEPTED_ITERATIONS, a digit count outside MIN..MAX) is DAMAGED
 *   (Pin.readable, Pin.state) and matches no PIN; Sci-Fin let such a row throw.
 * - Pin.nextFailedCount is new: the count after a wrong PIN, never wrapping; the gate that saves it
 *   is the app's, in phase 2 (SPEC R44, R146).
 * - hintProblem gives R132's code PasswordProblem.HINT_IS_PASSWORD, not a sentence, and takes no
 *   noun: the words for a PIN and a password are the design pass's (SPEC R16). BackupCrypto's
 *   password check uses it for the same comparison.
 * - LockAfter has no label (SPEC R16).
 * - pbkdf2Sha256 and randomBytes are BackupCrypto.kt's, declared there with their JVM actuals in
 *   BackupCrypto.jvm.kt; Sci-Fin's Lock.jvm.kt holds nothing else, so it is not ported as a file.
 * - The forgotten-PIN erase (Sci-Fin's AppStore.eraseEverything) is the store's and the files':
 *   ProfileRepository.eraseStore and data/Erase.kt (SPEC R147).
 * - Section references are replaced by SPEC rule numbers.
 */

/** How long the app may be out of sight before it locks (SPEC R146), and its stored token. */
enum class LockAfter(val token: String, val wait: Duration) {
    IMMEDIATELY("immediately", Duration.ZERO),
    ONE_MINUTE("one_minute", 1.minutes),
    FIVE_MINUTES("five_minutes", 5.minutes);

    companion object {
        /** The choice a stored token names; a token not known reads as the default, [ONE_MINUTE]. */
        fun of(token: String): LockAfter = entries.firstOrNull { it.token == token } ?: ONE_MINUTE
    }
}

/**
 * The lock as the gate finds it (SPEC R146): no row, a row that can be read, or a row that cannot,
 * which the app reports before asking for a new PIN.
 */
enum class LockState { OFF, ON, DAMAGED }

object Pin {
    const val MIN = 4
    const val MAX = 6

    /**
     * PBKDF2-HMAC-SHA256's cost for a new PIN. A PIN has at most a million values, so no cost makes
     * a copied hash safe on its own; the cost is a brake and the waits hold back guessing (SPEC R146).
     */
    const val ITERATIONS = 120_000

    /** The costs a stored row may hold: at least one, and none that would hang the phone. */
    val ACCEPTED_ITERATIONS = 1..5_000_000

    const val SALT_BYTES = 16
    private const val HASH_BYTES = 32

    /** The wrong PINs in a row that bring the first wait. */
    const val FREE_TRIES = 5
    private val FIRST_WAIT = 30.seconds
    private val LONGEST_WAIT = 1.hours

    private fun isPinDigit(c: Char): Boolean = c in '0'..'9'

    /** Whether [pin] is a PIN: [MIN] to [MAX] of the digits 0 to 9. */
    fun valid(pin: String): Boolean = pin.length in MIN..MAX && pin.all(::isPinDigit)

    /** What a field keeps of what is typed: the digits 0 to 9, at most [MAX]. */
    fun clean(typed: String): String = typed.filter(::isPinDigit).take(MAX)

    /**
     * The wait owed after [failed] wrong PINs in a row: none for the first [FREE_TRIES] - 1, then
     * 30 seconds, doubling with each further miss, never more than an hour (SPEC R146).
     */
    fun waitAfter(failed: Int): Duration {
        if (failed < FREE_TRIES) return Duration.ZERO
        val doublings = (failed - FREE_TRIES).coerceAtMost(10)
        return (FIRST_WAIT * (1 shl doublings)).coerceAtMost(LONGEST_WAIT)
    }

    /**
     * A new lock row for [pin], salted afresh, with [hint] kept trimmed or as none when blank; null
     * when [pin] is not a PIN, the hint is the PIN (SPEC R146, [hintProblem]) or [iterations] is
     * outside [ACCEPTED_ITERATIONS].
     */
    fun lockFor(pin: String, hint: String?, after: LockAfter, iterations: Int = ITERATIONS): AppLock? {
        if (!valid(pin) || iterations !in ACCEPTED_ITERATIONS) return null
        if (hint != null && hintProblem(pin, hint) != null) return null
        val salt = randomBytes(SALT_BYTES)
        return AppLock(
            salt = Base64.encode(salt), hash = Base64.encode(pbkdf2Sha256(pin, salt, iterations)),
            iterations = iterations, pinLength = pin.length, hint = hint?.trim()?.ifEmpty { null }, lockAfter = after.token,
        )
    }

    /**
     * The wrong-PIN count after one more wrong PIN, from the count [stored] in the row (SPEC R146):
     * a stored count below 0 reads as 0, and [Int.MAX_VALUE] stays as it is, never wrapping.
     */
    fun nextFailedCount(stored: Int): Int = when {
        stored < 0 -> 1
        stored == Int.MAX_VALUE -> Int.MAX_VALUE
        else -> stored + 1
    }

    /** [lock]'s hash and salt, or null when the row cannot be read (SPEC R146). Never throws. */
    private fun parts(lock: AppLock): Pair<ByteArray, ByteArray>? {
        if (lock.iterations !in ACCEPTED_ITERATIONS || lock.pinLength !in MIN..MAX) return null
        val (hash, salt) = try {
            Base64.decode(lock.hash) to Base64.decode(lock.salt)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return if (hash.size != HASH_BYTES || salt.isEmpty()) null else hash to salt
    }

    /**
     * Whether [lock] can be read (SPEC R146): salt and hash Base64, a salt not empty, a hash of 32
     * bytes, a cost in [ACCEPTED_ITERATIONS] and a digit count of [MIN] to [MAX].
     */
    fun readable(lock: AppLock): Boolean = parts(lock) != null

    /** The lock's state for the gate (SPEC R146): OFF with no row, DAMAGED for a row that cannot be read. */
    fun state(lock: AppLock?): LockState = when {
        lock == null -> LockState.OFF
        readable(lock) -> LockState.ON
        else -> LockState.DAMAGED
    }

    /**
     * Whether [pin] is the one [lock] holds, compared in constant time. A row that cannot be read
     * matches no PIN (SPEC R146); nothing here throws on what the row holds.
     */
    fun matches(lock: AppLock, pin: String): Boolean {
        if (!valid(pin)) return false
        val (want, salt) = parts(lock) ?: return false
        val got = try {
            pbkdf2Sha256(pin, salt, lock.iterations)
        } catch (_: Exception) {
            return false
        }
        if (want.size != got.size) return false
        var diff = 0
        for (i in want.indices) diff = diff or (want[i].toInt() xor got[i].toInt())
        return diff == 0
    }
}

/**
 * Why [hint] cannot be kept beside [secret], a PIN or a password, or null when it can (SPEC R132,
 * R146): only when it is the secret itself, compared trimmed and ignoring case, so `1234 ` or
 * `Secret` cannot pass for something other than `1234` or `secret`. An empty hint is no problem
 * here; whether one is required is the caller's (SPEC R132's NO_HINT).
 */
fun hintProblem(secret: String, hint: String): PasswordProblem? {
    val h = hint.trim()
    if (h.isEmpty() || secret.isEmpty()) return null
    return if (h.equals(secret.trim(), ignoreCase = true)) PasswordProblem.HINT_IS_PASSWORD else null
}
