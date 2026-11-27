package com.hazia.tesoriere

import com.hazia.tesoriere.data.ProfileRepository
import com.hazia.tesoriere.data.buildInMemoryTesoriereDatabase
import com.hazia.tesoriere.engine.LockAfter
import com.hazia.tesoriere.engine.LockState
import com.hazia.tesoriere.engine.PasswordProblem
import com.hazia.tesoriere.engine.Pin
import com.hazia.tesoriere.engine.hintProblem
import com.hazia.tesoriere.model.AppLock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/LockTest.kt at 2892110: its
 * four engine cases (the PIN, the waits, the hash, the hint). Changed: the hint's problem is R132's
 * code, not a sentence; a PIN is the digits 0 to 9 alone; a stored row that cannot be read matches
 * no PIN, is DAMAGED and throws nothing; the next wrong-PIN count never wraps. Not ported: the
 * cases through Sci-Fin's AppStore (the gate, leaving and returning, turning off and changing the
 * PIN, the backup), which are the app's in phase 2; the erase is EraseTest's. Every PIN and hint is
 * made up.
 */
class LockTest {
    private val fullWidth1234 = "１２３４"
    private val b64 = Base64.getEncoder()

    // SPEC:R146
    @Test fun `R146 a PIN is 4 to 6 of the digits 0 to 9, and a field keeps only those, at most 6`() {
        assertTrue(Pin.valid("1234")); assertTrue(Pin.valid("123456"))
        assertFalse(Pin.valid("123")); assertFalse(Pin.valid("1234567")); assertFalse(Pin.valid("12a4"))
        assertFalse(Pin.valid(fullWidth1234), "another script's digits are not a PIN")
        assertFalse(Pin.valid("")); assertFalse(Pin.valid("12 34"))
        assertEquals("123456", Pin.clean("1 2-3a4567"))
        assertEquals("", Pin.clean(fullWidth1234))
    }

    // SPEC:R146
    @Test fun `R146 wrong PINs 1 to 4 owe nothing, then 30 seconds doubling to at most 1 hour`() {
        assertEquals(listOf(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO), (1..4).map(Pin::waitAfter))
        assertEquals(listOf(30.seconds, 1.minutes, 2.minutes, 4.minutes), (5..8).map(Pin::waitAfter))
        assertEquals(1.hours, Pin.waitAfter(20))
        assertEquals(1.hours, Pin.waitAfter(Int.MAX_VALUE))
        assertEquals(Duration.ZERO, Pin.waitAfter(0)); assertEquals(Duration.ZERO, Pin.waitAfter(-1))
    }

    // SPEC:R146
    @Test fun `R146 a PIN of 482100 is kept only as a salted hash of 120000 iterations, and checked against it`() {
        val a = assertNotNull(Pin.lockFor("482100", "the first flat", LockAfter.ONE_MINUTE))
        val b = assertNotNull(Pin.lockFor("482100", null, LockAfter.ONE_MINUTE))
        assertFalse("482100" in a.hash + a.salt)
        assertTrue(a.salt != b.salt && a.hash != b.hash, "the same PIN must not hash the same twice")
        assertEquals(16, Base64.getDecoder().decode(a.salt).size); assertEquals(32, Base64.getDecoder().decode(a.hash).size)
        assertTrue(Pin.matches(a, "482100")); assertFalse(Pin.matches(a, "482101")); assertFalse(Pin.matches(a, "4821"))
        assertEquals(120_000, a.iterations); assertEquals(6, a.pinLength); assertEquals("the first flat", a.hint)
        assertEquals("one_minute", a.lockAfter); assertEquals(0, a.failedAttempts)
        assertNull(assertNotNull(Pin.lockFor("4821", "   ", LockAfter.FIVE_MINUTES)).hint, "a hint of spaces is kept as none")
    }

    // SPEC:R146 SPEC:R132
    @Test fun `R146 a hint is optional and may never be the PIN, giving R132's HINT_IS_PASSWORD`() {
        assertNull(hintProblem("4821", "")); assertNull(hintProblem("4821", "   "))
        assertNull(hintProblem("4821", "grandad's door"))
        assertEquals(PasswordProblem.HINT_IS_PASSWORD, hintProblem("4821", " 4821 "))
        assertEquals(PasswordProblem.HINT_IS_PASSWORD, hintProblem("Lisbon2026", "lisbon2026"))
    }

    // SPEC:R146
    @Test fun `R146 no lock is made for 123, for a full-width 1234, or for 4821 with the hint 4821`() {
        assertNull(Pin.lockFor("123", null, LockAfter.ONE_MINUTE))
        assertNull(Pin.lockFor(fullWidth1234, null, LockAfter.ONE_MINUTE))
        assertNull(Pin.lockFor("4821", " 4821", LockAfter.ONE_MINUTE))
        assertNull(Pin.lockFor("4821", null, LockAfter.ONE_MINUTE, iterations = 0))
    }

    // SPEC:R146
    @Test fun `R146 a row that cannot be read is DAMAGED, matches no PIN and throws nothing`() {
        val good = assertNotNull(Pin.lockFor("4821", null, LockAfter.ONE_MINUTE, iterations = 1_000))
        assertTrue(Pin.matches(good, "4821")); assertTrue(Pin.readable(good)); assertEquals(LockState.ON, Pin.state(good))
        val rows = mapOf(
            "salt not base64" to good.copy(salt = "not base64!"),
            "hash not base64" to good.copy(hash = "not base64!"),
            "empty salt" to good.copy(salt = ""),
            "hash of 31 bytes" to good.copy(hash = b64.encodeToString(ByteArray(31))),
            "hash of 33 bytes" to good.copy(hash = b64.encodeToString(ByteArray(33))),
            "empty hash" to good.copy(hash = ""),
            "cost 0" to good.copy(iterations = 0),
            "cost -5" to good.copy(iterations = -5),
            "cost 5000001" to good.copy(iterations = 5_000_001),
            "3 digits" to good.copy(pinLength = 3),
            "7 digits" to good.copy(pinLength = 7),
        )
        for ((case, row) in rows) {
            assertFalse(Pin.readable(row), "read as whole: $case")
            assertEquals(LockState.DAMAGED, Pin.state(row), case)
            assertFalse(Pin.matches(row, "4821"), "matched: $case")
        }
        assertFalse(Pin.matches(good, "12a4")); assertFalse(Pin.matches(good, fullWidth1234))
        val made = AppLock(salt = "c2FsdA==", hash = "aGFzaA==", iterations = 600_000, pinLength = 6)
        assertEquals(LockState.DAMAGED, Pin.state(made), "a 4-byte hash is not a PBKDF2 hash")
    }

    // SPEC:R146
    @Test fun `R146 costs of 1 and 5000000 and digit counts of 4 and 6 read, a lock at 1 iteration matches, none at 5000001`() {
        val one = assertNotNull(Pin.lockFor("4821", null, LockAfter.ONE_MINUTE, iterations = 1))
        assertEquals(1, one.iterations); assertTrue(Pin.matches(one, "4821")); assertFalse(Pin.matches(one, "4822"))
        assertNull(Pin.lockFor("4821", null, LockAfter.ONE_MINUTE, iterations = 5_000_001))
        assertTrue(Pin.readable(one.copy(iterations = 5_000_000)))
        assertTrue(Pin.readable(one.copy(pinLength = 4))); assertTrue(Pin.readable(one.copy(pinLength = 6)))
        assertEquals(LockState.ON, Pin.state(one))
        assertEquals(LockState.OFF, Pin.state(null))
    }

    // SPEC:R146
    @Test fun `R146 a lock for 482100 whose digit count reads 4 still matches 482100, the count shaping only the field`() {
        val lock = assertNotNull(Pin.lockFor("482100", null, LockAfter.ONE_MINUTE, iterations = 1_000)).copy(pinLength = 4)
        assertEquals(LockState.ON, Pin.state(lock))
        assertTrue(Pin.matches(lock, "482100")); assertFalse(Pin.matches(lock, "4821"))
        val five = lock.copy(pinLength = 5)
        assertTrue(Pin.matches(five, "482100"))
    }

    // SPEC:R146
    @Test fun `R146 the count after 4 is 5, after -3 is 1, and 2147483647 never wraps`() {
        assertEquals(1, Pin.nextFailedCount(0))
        assertEquals(5, Pin.nextFailedCount(4))
        assertEquals(1, Pin.nextFailedCount(-3))
        assertEquals(1, Pin.nextFailedCount(Int.MIN_VALUE))
        assertEquals(Int.MAX_VALUE, Pin.nextFailedCount(Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, Pin.nextFailedCount(Int.MAX_VALUE - 1))
        assertEquals(1.hours, Pin.waitAfter(Pin.nextFailedCount(Int.MAX_VALUE)))
        assertEquals(30.seconds, Pin.waitAfter(Pin.nextFailedCount(4)))
    }

    // SPEC:R146 SPEC:R18
    @Test fun `R146 a fresh install holds no lock row, and a token not known reads as one_minute`() {
        val db = buildInMemoryTesoriereDatabase()
        try {
            runBlocking {
                ProfileRepository(db, TestClock(LocalDate(2026, 11, 27)), Ids("l")::next).createProfile("A", "p-a")
                assertNull(db.dao().appLock())
                assertEquals(LockState.OFF, Pin.state(db.dao().appLock()))
            }
        } finally {
            db.close()
        }
        assertEquals(LockAfter.ONE_MINUTE, LockAfter.of("fortnightly"))
        assertEquals(LockAfter.ONE_MINUTE, LockAfter.of(AppLock(salt = "", hash = "", iterations = 1, pinLength = 4).lockAfter))
        assertEquals(listOf(Duration.ZERO, 1.minutes, 5.minutes), LockAfter.entries.map { it.wait })
        assertEquals(listOf("immediately", "one_minute", "five_minutes"), LockAfter.entries.map { it.token })
        LockAfter.entries.forEach { assertEquals(it, LockAfter.of(it.token)) }
    }
}
