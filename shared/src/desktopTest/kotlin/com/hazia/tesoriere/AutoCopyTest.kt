package com.hazia.tesoriere

import com.hazia.tesoriere.data.buildInMemoryTesoriereDatabase
import com.hazia.tesoriere.engine.AutoCopy
import com.hazia.tesoriere.engine.BackupCrypto
import com.hazia.tesoriere.engine.CopyError
import com.hazia.tesoriere.engine.CopyEvery
import com.hazia.tesoriere.engine.CopyProblem
import com.hazia.tesoriere.engine.CopyText
import com.hazia.tesoriere.engine.PasswordProblem
import com.hazia.tesoriere.engine.plusDays
import com.hazia.tesoriere.model.CopySchedule
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/AutoCopyTest.kt at 2892110: the
 * engine half of its cases (weekly once a folder is chosen, catching up a missed week, the newest 14
 * kept and nothing else touched, a password with its hint, paused while the PIN is on with no
 * password, one notice per run of failures). Changed: a copy's name holds the device; only this
 * device's copies are deleted; an unknown frequency reads as weekly, a last copy after today makes
 * one due, and a failure is a code. Not ported: the cases through Sci-Fin's AppStore and AppSession
 * (the folder picker, the key store, writing the files, the notice, the export with the copies'
 * password) and Windows' DPAPI sealing, which are the app's in phase 2. Every password, hint, folder
 * and device name is made up.
 */
class AutoCopyTest {
    private val nov27 = LocalDate(2026, 11, 27)
    private val weekly = CopySchedule(every = "weekly", folder = "folder-handle", folderLabel = "Sync")
    private val daily = weekly.copy(every = "daily")
    private val plain = """{"app":"tesoriere","schemaVersion":1,"exportedAt":"2026-11-27","activeProfileId":"p-1","profiles":[]}"""

    /** A whole copy just written: the only kind after which old copies are deleted (SPEC R148). */
    private val whole = CopyText.Ready(plain)

    // SPEC:R148 SPEC:R18
    @Test fun `R148 a fresh install holds no copy row, and no copy is due`() {
        val db = buildInMemoryTesoriereDatabase()
        try {
            val row = runBlocking { db.dao().copySchedule() }
            assertNull(row)
            assertEquals(CopyEvery.OFF, AutoCopy.every(row))
            assertFalse(AutoCopy.due(row, nov27))
            assertFalse(AutoCopy.due(CopySchedule(), nov27), "the default row is off")
            assertFalse(AutoCopy.due(weekly.copy(every = "off", lastCopy = "2026-01-01"), nov27), "off with a folder is never due")
        } finally {
            db.close()
        }
    }

    // SPEC:R148 SPEC:R01
    @Test fun `R148 choosing the folder Sync keeps the frequency, off staying off and daily daily`() {
        val chosen = AutoCopy.withFolder(null, "folder-handle", "Sync")
        assertEquals(CopyEvery.OFF, AutoCopy.every(chosen), "a folder chosen while off stays off, as in Sci-Fin")
        assertEquals("folder-handle", chosen.folder); assertEquals("Sync", chosen.folderLabel)
        assertFalse(AutoCopy.due(chosen, nov27))

        val failedDaily = daily.copy(lastError = "not_written", failureNotified = true)
        val moved = AutoCopy.withFolder(failedDaily, "other-handle", "Drive")
        assertEquals(CopyEvery.DAILY, AutoCopy.every(moved))
        assertNull(moved.lastError); assertFalse(moved.failureNotified)

        assertEquals(CopyEvery.OFF, AutoCopy.every(AutoCopy.withFolder(CopySchedule(lastCopy = "2026-11-20"), "h", "Sync")))
    }

    // SPEC:R148 SPEC:R01
    @Test fun `R148 turning the copy on with the folder Sync makes it weekly, R01's weekly backup`() {
        val on = AutoCopy.turnOn(null, "folder-handle", "Sync")
        assertEquals(CopyEvery.WEEKLY, AutoCopy.every(on))
        assertEquals("folder-handle", on.folder); assertEquals("Sync", on.folderLabel)
        assertTrue(AutoCopy.due(on, nov27), "the first copy is due at once")
        val failedOff = CopySchedule(lastCopy = "2026-11-20", lastError = "not_written", failureNotified = true)
        val again = AutoCopy.turnOn(failedOff, "h", "Drive")
        assertEquals(CopyEvery.WEEKLY, AutoCopy.every(again)); assertNull(again.lastError); assertFalse(again.failureNotified)
        assertEquals("2026-11-20", again.lastCopy)
    }

    // SPEC:R148
    @Test fun `R148 a copy cannot be turned on with no folder, NO_FOLDER, and on with no folder is never due`() {
        assertEquals(CopyProblem.NO_FOLDER, AutoCopy.everyProblem(null, CopyEvery.DAILY))
        assertEquals(CopyProblem.NO_FOLDER, AutoCopy.everyProblem(CopySchedule(), CopyEvery.WEEKLY))
        assertNull(AutoCopy.everyProblem(null, CopyEvery.OFF))
        assertNull(AutoCopy.everyProblem(weekly, CopyEvery.DAILY))
        assertEquals(CopySchedule(), AutoCopy.withEvery(null, CopyEvery.DAILY))
        assertEquals(CopySchedule(), AutoCopy.withEvery(CopySchedule(), CopyEvery.WEEKLY))
        assertEquals(CopyEvery.DAILY, AutoCopy.every(AutoCopy.withEvery(weekly, CopyEvery.DAILY)))
        val off = AutoCopy.withEvery(weekly, CopyEvery.OFF)
        assertEquals(CopyEvery.OFF, AutoCopy.every(off)); assertEquals("folder-handle", off.folder, "turning it off keeps the folder")
        assertFalse(AutoCopy.due(CopySchedule(every = "weekly"), nov27))
    }

    // SPEC:R148
    @Test fun `R148 a weekly copy of 27 November 2026 is not due on 27 November or 3 December, due on 4 December`() {
        val s = weekly.copy(lastCopy = "2026-11-27")
        assertFalse(AutoCopy.due(s, nov27), "the same day")
        assertFalse(AutoCopy.due(s, LocalDate(2026, 12, 3)))
        assertTrue(AutoCopy.due(s, LocalDate(2026, 12, 4)), "across a month's end")
        assertTrue(AutoCopy.due(s, LocalDate(2026, 12, 6)), "a week missed is caught up on the next run")
    }

    // SPEC:R148
    @Test fun `R148 a weekly copy of 28 December 2026 is not due on 3 January 2027, due on 4 January`() {
        val s = weekly.copy(lastCopy = "2026-12-28")
        assertFalse(AutoCopy.due(s, LocalDate(2026, 12, 31)))
        assertFalse(AutoCopy.due(s, LocalDate(2027, 1, 3)))
        assertTrue(AutoCopy.due(s, LocalDate(2027, 1, 4)), "across a year's end")
    }

    // SPEC:R148
    @Test fun `R148 a daily copy of 27 November is not due the same day, due on 28 November`() {
        val s = daily.copy(lastCopy = "2026-11-27")
        assertFalse(AutoCopy.due(s, nov27))
        assertTrue(AutoCopy.due(s, LocalDate(2026, 11, 28)))
        assertTrue(AutoCopy.due(daily.copy(lastCopy = "2026-02-28"), LocalDate(2026, 3, 1)))
    }

    // SPEC:R148
    @Test fun `R148 no last copy, 2026-13-01, yesterday, or 28 November when today is 27 November, is due`() {
        assertTrue(AutoCopy.due(weekly, nov27), "the first copy")
        for (bad in listOf("2026-13-01", "yesterday", "", "2026-11-2", "2026-11-27T10:00:00Z", " 2026-11-27")) {
            assertNull(AutoCopy.lastCopy(weekly.copy(lastCopy = bad)), bad)
            assertTrue(AutoCopy.due(weekly.copy(lastCopy = bad), nov27), bad)
        }
        assertTrue(AutoCopy.due(weekly.copy(lastCopy = "2026-11-28"), nov27), "the clock moved back a day")
        assertTrue(AutoCopy.due(daily.copy(lastCopy = "2027-11-27"), nov27), "the clock moved back a year")
        assertEquals(LocalDate(2026, 11, 20), AutoCopy.lastCopy(weekly.copy(lastCopy = "2026-11-20")))
    }

    // SPEC:R148
    @Test fun `R148 fortnightly and the empty text read as weekly, off alone as off`() {
        assertEquals(CopyEvery.WEEKLY, CopyEvery.of("fortnightly"))
        assertEquals(CopyEvery.WEEKLY, CopyEvery.of(""))
        assertEquals(CopyEvery.WEEKLY, CopyEvery.of("Weekly"))
        assertEquals(CopyEvery.WEEKLY, CopyEvery.of("OFF"), "only the token off turns it off")
        assertEquals(CopyEvery.OFF, CopyEvery.of("off"))
        assertEquals(CopyEvery.DAILY, CopyEvery.of("daily"))
        assertEquals(listOf("off" to 0, "daily" to 1, "weekly" to 7), CopyEvery.entries.map { it.token to it.days })
        val damaged = weekly.copy(every = "fortnightly", lastCopy = "2026-11-20")
        assertFalse(AutoCopy.due(damaged, LocalDate(2026, 11, 26)))
        assertTrue(AutoCopy.due(damaged, nov27))
    }

    // SPEC:R148
    @Test fun `R148 a stored failure disk_full, or a sentence, reads as NOT_WRITTEN, and none as no failure`() {
        assertNull(AutoCopy.lastError(weekly))
        assertNull(AutoCopy.lastError(null))
        assertEquals(CopyError.NOT_WRITTEN, AutoCopy.lastError(weekly.copy(lastError = "disk_full")))
        assertEquals(CopyError.NOT_WRITTEN, AutoCopy.lastError(weekly.copy(lastError = "The copy could not be written to its folder.")))
        assertEquals(CopyError.NOT_WRITTEN, AutoCopy.lastError(weekly.copy(lastError = "")))
        for (e in CopyError.entries) assertEquals(e, AutoCopy.lastError(AutoCopy.afterFailure(weekly, e)), e.name)
        assertEquals(
            listOf("folder_unreachable", "not_written", "password_unreadable", "not_made", "records_unreadable", "records_left_out", "password_not_saved", "paused_needs_password"),
            CopyError.entries.map { it.token },
        )
    }

    // SPEC:R148 SPEC:R130
    @Test fun `R148 a copy whose backup could not be made is stored as not_made, reads back as NOT_MADE and owes a notice`() {
        assertTrue(AutoCopy.noticeOwed(weekly))
        val failed = AutoCopy.afterFailure(weekly, CopyError.NOT_MADE)
        assertEquals("not_made", failed.lastError)
        assertEquals(CopyError.NOT_MADE, AutoCopy.lastError(failed))
        assertEquals(CopyError.NOT_MADE, CopyError.of("not_made"))
        assertFalse(AutoCopy.noticeOwed(failed), "the notice of this run of failures is the one owed now")
        assertEquals(weekly.lastCopy, failed.lastCopy, "the last good copy is kept")
    }

    // SPEC:R148
    @Test fun `R148 the copy of 27 November 2026 from Pixel 9 is tesoriere-copy-2026-11-27-pixel_9 json`() {
        assertEquals("tesoriere-copy-2026-11-27-pixel_9.json", AutoCopy.fileName(nov27, "Pixel 9"))
        assertEquals(AutoCopy.fileName(nov27, "phone"), AutoCopy.fileName(nov27, "Phone"), "no two names differ only in case")
        assertEquals("tesoriere-copy-2026-11-27-pc.json", AutoCopy.fileName(nov27, "pc"))
        assertEquals("tesoriere-copy-2026-01-05-pc.json", AutoCopy.fileName(LocalDate(2026, 1, 5), "pc"), "a day is always 10 characters")
    }

    // SPEC:R148
    @Test fun `R148 a device named with characters a file name cannot hold gives only letters, digits, - and _`() {
        assertEquals("a_b_c_d_e_f_g_h_i_j", AutoCopy.deviceName("a/b\\c:d*e?f\"g<h>i|j"))
        assertEquals("tesoriere-copy-2026-11-27-a_b_c_d_e_f_g_h_i_j.json", AutoCopy.fileName(nov27, "a/b\\c:d*e?f\"g<h>i|j"))
        assertEquals("x".repeat(32), AutoCopy.deviceName("x".repeat(40)))
        assertEquals("device", AutoCopy.deviceName(""))
        assertEquals("__", AutoCopy.deviceName(".."), "no name can climb out of the folder")
        assertEquals("zo__s_phone", AutoCopy.deviceName("Zoë's phone"))
        assertEquals("tab_nl_nul_", AutoCopy.deviceName("tab\tnl\nnul\u0000"))
        assertEquals("my-pc_2", AutoCopy.deviceName("my-PC_2"))
        assertEquals("pixel", AutoCopy.deviceName("PIXEL"))
        val unsafe = Regex("[^a-z0-9._-]")
        for (d in listOf("a/b\\c:d*e?f\"g<h>i|j", "..", "Zoë's phone", "\u0000", "CON", " ", "日本")) {
            val name = AutoCopy.fileName(nov27, d)
            assertFalse(unsafe.containsMatchIn(name), name)
            assertTrue(name.length <= "tesoriere-copy-2026-11-27-.json".length + 32, name)
        }
    }

    // SPEC:R148
    @Test fun `R148 the copies of 26 and 27 November differ, and so do those of two devices on one day`() {
        val names = (0 until 400).map { AutoCopy.fileName(LocalDate(2026, 1, 1).plusDays(it), "phone") }
        assertEquals(names.size, names.toSet().size, "no day's copy replaces another day's")
        assertNotEquals(AutoCopy.fileName(LocalDate(2026, 11, 26), "phone"), AutoCopy.fileName(nov27, "phone"))
        assertNotEquals(AutoCopy.fileName(nov27, "phone"), AutoCopy.fileName(nov27, "pc"))
        assertNotEquals(AutoCopy.fileName(nov27, "phone"), AutoCopy.fileName(nov27, "phone-2"))
        assertEquals(AutoCopy.fileName(nov27, "phone"), AutoCopy.fileName(nov27, "phone"), "a second copy the same day replaces the day's")
    }

    // SPEC:R148
    @Test fun `R148 beside 17 daily copies of phone, 3 of pc and notes txt, the 3 oldest of phone go`() {
        val start = LocalDate(2026, 11, 11)
        val phone = (0 until 17).map { AutoCopy.fileName(start.plusDays(it), "phone") }
        val pc = (0 until 3).map { AutoCopy.fileName(start.plusDays(it), "pc") }
        val others = listOf(
            "notes.txt", "tesoriere-copy-2026-01-01-phone.json.bak", "tesoriere-copy-2026-13-01-phone.json",
            "tesoriere-copy-2026-01-01-phone-2.json", "Tesoriere-copy-2026-01-01-phone.json", "my-backup.json",
            "tesoriere-copy-2026-02-30-phone.json", "tesoriere-copy-2026-01-01-my-phone.json",
        )
        val folder = (others + pc + phone).shuffled(kotlin.random.Random(7))
        assertEquals(phone.take(3), AutoCopy.stale(folder, "phone", nov27, whole))
        assertEquals(emptyList(), AutoCopy.stale(folder, "pc", nov27, whole), "pc has 3, under 14")
        assertEquals(emptyList(), AutoCopy.stale(phone.take(14), "phone", nov27, whole))
        assertEquals(emptyList(), AutoCopy.stale(emptyList(), "phone", nov27, whole))
        assertEquals(14, AutoCopy.KEPT)
    }

    // SPEC:R148
    @Test fun `R148 after the clock ran ahead, copies dated after today go first and the day's own copy never goes`() {
        val later = (0 until 14).map { AutoCopy.fileName(LocalDate(2027, 1, 1).plusDays(it), "phone") }
        val today = AutoCopy.fileName(nov27, "phone")
        val yesterday = AutoCopy.fileName(LocalDate(2026, 11, 26), "phone")
        assertEquals(later.take(2), AutoCopy.stale(later + today + yesterday, "phone", nov27, whole), "yesterday's copy stays")
        val real = (0 until 13).map { AutoCopy.fileName(nov27.plusDays(-it), "phone") }
        assertEquals(later.take(13), AutoCopy.stale(later + real, "phone", nov27, whole))
        assertEquals(later.take(14), AutoCopy.stale(later + real + AutoCopy.fileName(LocalDate(2026, 11, 14), "phone"), "phone", nov27, whole))
        val ahead15 = (0 until 15).map { AutoCopy.fileName(nov27.plusDays(1 + it), "phone") }
        assertEquals(ahead15.take(2), AutoCopy.stale(ahead15 + today, "phone", nov27, whole), "the day's own copy is kept over any dated later")
    }

    // SPEC:R148
    @Test fun `R148 a failure owes one notice for each run of failures, and a success on 27 November clears it`() {
        val before = daily.copy(lastCopy = "2026-11-25")
        assertTrue(AutoCopy.noticeOwed(before))
        val first = AutoCopy.afterFailure(before, CopyError.FOLDER_UNREACHABLE)
        assertEquals("folder_unreachable", first.lastError); assertTrue(first.failureNotified)
        assertEquals("2026-11-25", first.lastCopy, "a failure keeps the last good copy")
        assertFalse(AutoCopy.noticeOwed(first), "the second failure of a run owes none")
        val second = AutoCopy.afterFailure(first, CopyError.NOT_WRITTEN)
        assertEquals(CopyError.NOT_WRITTEN, AutoCopy.lastError(second)); assertFalse(AutoCopy.noticeOwed(second))

        val ok = AutoCopy.afterSuccess(second, nov27)
        assertEquals("2026-11-27", ok.lastCopy); assertNull(ok.lastError); assertFalse(ok.failureNotified)
        assertTrue(AutoCopy.noticeOwed(ok), "a new run of failures is said again")
        assertFalse(AutoCopy.due(ok, nov27))
        assertEquals(daily.copy(lastCopy = "2026-11-27"), ok, "nothing else of the schedule changes")
    }

    // SPEC:R148 SPEC:R132 SPEC:R146
    @Test fun `R148 with the app lock on a copy with no password is paused, one with a password is not`() {
        assertTrue(AutoCopy.paused(weekly, lockOn = true))
        assertFalse(AutoCopy.paused(weekly, lockOn = false))
        val sealed = AutoCopy.withPassword(weekly, "sealed-by-key-store", "  the stable ")
        assertEquals("sealed-by-key-store", sealed.sealedPassword); assertEquals("the stable", sealed.hint)
        assertFalse(AutoCopy.paused(sealed, lockOn = true))
        assertFalse(AutoCopy.paused(weekly.copy(every = "off"), lockOn = true), "a copy that is off is not paused")
        assertFalse(AutoCopy.paused(null, lockOn = true))

        assertEquals(PasswordProblem.REQUIRED, AutoCopy.withoutPasswordProblem(lockOn = true))
        assertNull(AutoCopy.withoutPasswordProblem(lockOn = false))
        assertEquals(sealed, AutoCopy.withoutPassword(sealed, lockOn = true), "refused while the lock is on")
        val plain = AutoCopy.withoutPassword(sealed, lockOn = false)
        assertNull(plain.sealedPassword); assertNull(plain.hint)
    }

    // SPEC:R148 SPEC:R132
    @Test fun `R148 the copies' password follows R132, short gives TOO_SHORT and none REQUIRED while the lock is on`() {
        assertEquals(PasswordProblem.TOO_SHORT, AutoCopy.passwordProblem(lockOn = true, "short", "short", "a hint"))
        assertEquals(PasswordProblem.TOO_SHORT, AutoCopy.passwordProblem(lockOn = false, "short", "short", "a hint"))
        assertEquals(PasswordProblem.REQUIRED, AutoCopy.passwordProblem(lockOn = true, null, "", ""))
        assertEquals(PasswordProblem.REQUIRED, AutoCopy.passwordProblem(lockOn = true, "", "", ""))
        assertNull(AutoCopy.passwordProblem(lockOn = false, null, "", ""))
        assertEquals(PasswordProblem.NO_HINT, AutoCopy.passwordProblem(lockOn = true, "correct horse", "correct horse", "  "))
        assertEquals(PasswordProblem.HINT_IS_PASSWORD, AutoCopy.passwordProblem(lockOn = true, "correct horse", "correct horse", " Correct Horse "))
        assertNull(AutoCopy.passwordProblem(lockOn = true, "correct horse", "correct horse", "the stable"))
    }

    // SPEC:R148 SPEC:R133
    @Test fun `R148 a copy with correct horse and the stable shows its hint and opens to the backup's text`() {
        val sealed = AutoCopy.withPassword(weekly, "sealed-by-key-store", "the stable")
        val written = AutoCopy.contents(sealed, plain, "correct horse", lockOn = true, nov27)
        val text = assertIs<CopyText.Ready>(written).text
        assertFalse(plain in text)
        val locked = BackupCrypto.locked(text)
        assertEquals("the stable", locked?.hint); assertEquals(nov27, locked?.exportedAt); assertEquals("tesoriere", locked?.app)
        val opened = assertIs<BackupCrypto.Opened.Plain>(BackupCrypto.decrypt(text, "correct horse"))
        assertEquals(plain, opened.text)
        assertIs<CopyText.Ready>(AutoCopy.contents(sealed, plain, "correct horse", lockOn = false, nov27))
    }

    // SPEC:R148
    @Test fun `R148 a copy with no password and the app lock off is the backup's text`() {
        assertEquals(CopyText.Ready(plain), AutoCopy.contents(weekly, plain, null, lockOn = false, nov27))
    }

    // SPEC:R148 SPEC:R132
    @Test fun `R148 a password handed in for copies whose schedule holds none sealed is PASSWORD_NOT_SAVED, never a plain copy`() {
        for (lockOn in listOf(true, false)) {
            assertEquals(CopyText.Failed(CopyError.PASSWORD_NOT_SAVED), AutoCopy.contents(weekly, plain, "correct horse", lockOn, nov27), "lock on: $lockOn")
        }
        assertEquals(CopyText.Ready(plain), AutoCopy.contents(weekly, plain, "", lockOn = false, nov27), "the empty text is no password")
        assertEquals("password_not_saved", AutoCopy.afterFailure(weekly, CopyError.PASSWORD_NOT_SAVED).lastError)
    }

    // SPEC:R148 SPEC:R01 SPEC:R132
    @Test fun `R148 a due copy paused by the app lock owes one notice for each run of pauses, cleared by a password or a success`() {
        val due = weekly.copy(lastCopy = "2026-11-13")
        assertTrue(AutoCopy.due(due, nov27))
        assertEquals(CopyText.Paused, AutoCopy.contents(due, plain, null, lockOn = true, nov27))
        assertTrue(AutoCopy.noticeOwed(due, CopyError.PAUSED_NEEDS_PASSWORD))
        val paused = AutoCopy.afterPause(due)
        assertEquals("paused_needs_password", paused.lastError)
        assertEquals(CopyError.PAUSED_NEEDS_PASSWORD, AutoCopy.lastError(paused))
        assertTrue(AutoCopy.due(paused, nov27.plusDays(1)), "a pause keeps the last good copy, so the copy stays due")
        assertFalse(AutoCopy.noticeOwed(paused, CopyError.PAUSED_NEEDS_PASSWORD), "the second pause of a run owes none")
        // a pause after a run of failures, and a failure after a run of pauses, each owe their own
        val failing = AutoCopy.afterFailure(due, CopyError.FOLDER_UNREACHABLE)
        assertTrue(AutoCopy.noticeOwed(failing, CopyError.PAUSED_NEEDS_PASSWORD))
        assertTrue(AutoCopy.noticeOwed(paused, CopyError.NOT_WRITTEN))
        assertFalse(AutoCopy.noticeOwed(failing, CopyError.NOT_WRITTEN))
        // a password set clears the pause and its notice; another failure is kept
        val sealed = AutoCopy.withPassword(paused, "sealed-by-key-store", "the stable")
        assertNull(sealed.lastError); assertFalse(sealed.failureNotified)
        assertEquals("folder_unreachable", AutoCopy.withPassword(failing, "sealed-by-key-store", "the stable").lastError)
        // the next success clears it too
        val ok = AutoCopy.afterSuccess(paused, nov27)
        assertNull(ok.lastError); assertTrue(AutoCopy.noticeOwed(ok, CopyError.PAUSED_NEEDS_PASSWORD))
    }

    // SPEC:R148 SPEC:R130 SPEC:R141
    @Test fun `R148 an export naming a record it could not read is still copied and owes RECORDS_UNREADABLE once, and an export with no file is NOT_MADE`() {
        val file = com.hazia.tesoriere.engine.BackupFile(exportedAt = "2026-11-27", activeProfileId = "p-1", profiles = emptyList())
        val export = com.hazia.tesoriere.data.Export.Ok(file, plain, 0, 0, emptyList())
        assertEquals(CopyText.Ready(plain), AutoCopy.outcome(export))
        val lacking = export.copy(unreadable = listOf(com.hazia.tesoriere.data.UnreadableRecord("p-1", com.hazia.tesoriere.data.StoredKind.IMPORT_MEMORY, null)))
        assertEquals(CopyText.Lacking(plain, CopyError.RECORDS_UNREADABLE), AutoCopy.outcome(lacking))
        assertEquals(CopyText.Lacking(plain, CopyError.RECORDS_UNREADABLE), AutoCopy.outcome(lacking.copy(leftOut = 1)), "unreadable records are named first")
        assertEquals(CopyText.Failed(CopyError.NOT_MADE), AutoCopy.outcome(com.hazia.tesoriere.data.Export.Empty))
        assertEquals(CopyText.Failed(CopyError.NOT_MADE), AutoCopy.outcome(com.hazia.tesoriere.data.Export.Refused(com.hazia.tesoriere.engine.BackupProblem.DAMAGED)))
        // the copy is written, of everything readable, and a notice is owed
        val due = weekly.copy(lastCopy = "2026-11-13")
        assertEquals(CopyText.Ready(plain), AutoCopy.contents(due, (AutoCopy.outcome(lacking) as CopyText.Lacking).text, null, lockOn = false, nov27))
        assertTrue(AutoCopy.noticeOwed(due, CopyError.RECORDS_UNREADABLE))
        val written = AutoCopy.afterSuccess(due, nov27, AutoCopy.outcome(lacking) as CopyText.Lacking)
        assertEquals("2026-11-27", written.lastCopy)
        assertEquals(CopyError.RECORDS_UNREADABLE, AutoCopy.lastError(written))
        assertFalse(AutoCopy.due(written, nov27), "a copy lacking records is a copy written")
        assertFalse(AutoCopy.noticeOwed(written, CopyError.RECORDS_UNREADABLE), "the next lacking copy owes none")
        // a run of folder failures and a run of lacking copies each owe their own notice
        val failing = AutoCopy.afterFailure(due, CopyError.FOLDER_UNREACHABLE)
        assertTrue(AutoCopy.noticeOwed(failing, CopyError.RECORDS_UNREADABLE))
        assertTrue(AutoCopy.noticeOwed(written, CopyError.FOLDER_UNREACHABLE))
        assertTrue(AutoCopy.noticeOwed(written, CopyError.PAUSED_NEEDS_PASSWORD))
        // a whole copy clears it
        val clean = AutoCopy.afterSuccess(written, nov27)
        assertNull(clean.lastError); assertTrue(AutoCopy.noticeOwed(clean, CopyError.RECORDS_UNREADABLE))
    }

    // SPEC:R148 SPEC:R141
    @Test fun `R148 beside 20 copies of phone none goes while records are unreadable, and the 6 oldest go after the first whole copy`() {
        val phone = (0 until 20).map { AutoCopy.fileName(nov27.plusDays(-it), "phone") }.reversed()
        assertEquals(emptyList(), AutoCopy.stale(phone, "phone", nov27, CopyText.Lacking(plain, CopyError.RECORDS_UNREADABLE)))
        assertEquals(phone.take(6), AutoCopy.stale(phone, "phone", nov27, whole))
        // nothing written, nothing deleted
        assertEquals(emptyList(), AutoCopy.stale(phone, "phone", nov27, CopyText.Paused))
        assertEquals(emptyList(), AutoCopy.stale(phone, "phone", nov27, CopyText.Failed(CopyError.NOT_WRITTEN)))
    }

    // SPEC:R148 SPEC:R130
    @Test fun `R148 an export that left 1 record out, with none unreadable, is a copy lacking records, RECORDS_LEFT_OUT, and of 20 copies none goes`() {
        val file = com.hazia.tesoriere.engine.BackupFile(exportedAt = "2026-11-27", activeProfileId = "p-1", profiles = emptyList())
        val leftOut = com.hazia.tesoriere.data.Export.Ok(file, plain, 1, 0, emptyList())
        val out = AutoCopy.outcome(leftOut)
        assertEquals(CopyText.Lacking(plain, CopyError.RECORDS_LEFT_OUT), out)
        val phone = (0 until 20).map { AutoCopy.fileName(nov27.plusDays(-it), "phone") }.reversed()
        assertEquals(emptyList(), AutoCopy.stale(phone, "phone", nov27, out))
        val due = weekly.copy(lastCopy = "2026-11-13")
        val written = AutoCopy.afterSuccess(due, nov27, out as CopyText.Lacking)
        assertEquals("records_left_out", written.lastError)
        assertEquals(CopyError.RECORDS_LEFT_OUT, AutoCopy.lastError(written))
        // one run with the copies lacking unreadable records, apart from failures
        assertFalse(AutoCopy.noticeOwed(written, CopyError.RECORDS_UNREADABLE))
        assertTrue(AutoCopy.noticeOwed(written, CopyError.FOLDER_UNREACHABLE))
        // a repaired record is carried: the copy is whole
        assertEquals(CopyText.Ready(plain), AutoCopy.outcome(leftOut.copy(leftOut = 0, repaired = 2)))
    }

    // SPEC:R148 SPEC:R132
    @Test fun `R148 a sealed password the key store no longer gives back is PASSWORD_UNREADABLE, never a plain copy`() {
        val sealed = AutoCopy.withPassword(weekly, "sealed-by-a-reset-key-store", "the stable")
        assertFalse(AutoCopy.paused(sealed, lockOn = true), "a schedule with a sealed password is not paused")
        for (lockOn in listOf(true, false)) {
            assertEquals(CopyText.Failed(CopyError.PASSWORD_UNREADABLE), AutoCopy.contents(sealed, plain, null, lockOn, nov27))
            assertEquals(CopyText.Failed(CopyError.PASSWORD_UNREADABLE), AutoCopy.contents(sealed, plain, "", lockOn, nov27))
        }
    }

    // SPEC:R148 SPEC:R132 SPEC:R146
    @Test fun `R148 with the app lock on and no password for the copies, nothing is written, a password handed in included`() {
        assertEquals(CopyText.Paused, AutoCopy.contents(weekly, plain, null, lockOn = true, nov27))
        assertEquals(CopyText.Failed(CopyError.PASSWORD_NOT_SAVED), AutoCopy.contents(weekly, plain, "correct horse", lockOn = true, nov27))
    }
}
