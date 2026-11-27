package com.hazia.tesoriere

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.hazia.tesoriere.data.BundledRawStore
import com.hazia.tesoriere.data.DamageNotices
import com.hazia.tesoriere.data.Integrity
import com.hazia.tesoriere.data.Probe
import com.hazia.tesoriere.data.Snapshot
import com.hazia.tesoriere.data.StoreOpening
import com.hazia.tesoriere.data.StoreProblem
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/SnapshotTest.kt at 2892110.
 *
 * The case that matters is the one the file layout makes: with the write-ahead log on, the main
 * file can hold a header and nothing else, so a copy of it would hold no rows. These tests leave
 * the rows in the log and require the snapshot to hold them (SPEC R145).
 *
 * Changed from Sci-Fin: the names are Tesoriere's; the version is read by the guard's read-only
 * probe, not from the file header; `Snapshot.before` gives an outcome instead of a file or null, so
 * a snapshot that fails is told from one not needed. Added, since SCHEMA_VERSION is 1 and no
 * migration exists yet (SPEC R142): the guard's whole path with a version 2 built in the test, its
 * migration run in one exclusive transaction rolled back on failure, as Room runs one -- the
 * snapshot taken before the migration, a failed migration, and a snapshot that cannot be written.
 * Added after review: a half-written snapshot left from before, and a snapshot that leaves the
 * store's files as they were.
 */
class SnapshotTest {
    private val driver = BundledSQLiteDriver()
    private val day = LocalDate(2026, 11, 26)

    /** A store of [version] holding [rows] settings rows, closed, so its log is folded in. */
    private fun seed(version: Int, rows: Int = 3): File {
        val db = TestDb.file()
        val c = driver.open(db.absolutePath)
        c.execSQL("PRAGMA journal_mode=WAL")
        c.execSQL("CREATE TABLE settings (profileId TEXT PRIMARY KEY, currency TEXT NOT NULL)")
        for (i in 0 until rows) c.execSQL("INSERT INTO settings VALUES ('p$i', 'EUR')")
        c.execSQL("PRAGMA user_version = $version")
        c.close()
        return db
    }

    /** A store of version 1 with 5 settings rows in its write-ahead log alone, its writer left open. */
    private fun withRowsInLog(db: File): SQLiteConnection {
        val a = driver.open(db.absolutePath)
        a.execSQL("PRAGMA journal_mode=WAL")
        a.execSQL("PRAGMA wal_autocheckpoint=0")
        a.execSQL("CREATE TABLE settings (profileId TEXT PRIMARY KEY, currency TEXT NOT NULL)")
        for (i in 0 until 5) a.execSQL("INSERT INTO settings VALUES ('p$i', 'EUR')")
        a.execSQL("PRAGMA user_version = 1")
        // Sci-Fin measured that this driver folds the log in when any connection that can write
        // closes, so the writer stays open: the rows are in the log alone, as the owner's Sci-Fin
        // store was found
        assertTrue(File(db.path + "-wal").length() > 0, "expected rows in the write-ahead log")
        assertTrue(db.length() <= 4096, "expected the main file to hold no rows yet, was ${db.length()}")
        return a
    }

    private fun <T> read(db: File, sql: String, get: (SQLiteStatement) -> T): T {
        val c = driver.open(db.absolutePath)
        try {
            return c.prepare(sql).use { s -> s.step(); get(s) }
        } finally {
            c.close()
        }
    }

    private fun rowCount(db: File): Int = read(db, "SELECT count(*) FROM settings") { it.getInt(0) }
    private fun version(db: File): Int = read(db, "PRAGMA user_version") { it.getInt(0) }
    private fun columns(db: File): List<String> {
        val c = driver.open(db.absolutePath)
        try {
            return c.prepare("PRAGMA table_info(settings)").use { s -> buildList { while (s.step()) add(s.getText(1)) } }
        } finally {
            c.close()
        }
    }

    private fun snapshots(db: File) = File(db.parentFile, "snapshots")

    /**
     * Version 2 built in the test: one column added, run as Room runs a migration, in one exclusive
     * transaction that is rolled back when a step fails. [fail] makes its second step fail.
     */
    private fun migrateTo2(db: File, fail: Boolean, ran: MutableList<File>? = null): suspend () -> SQLiteConnection = {
        // what the snapshot folder held at the moment the migration began
        ran?.addAll(snapshots(db).listFiles().orEmpty())
        val c = driver.open(db.absolutePath)
        try {
            c.execSQL("BEGIN EXCLUSIVE TRANSACTION")
            try {
                c.execSQL("ALTER TABLE settings ADD COLUMN trendMonths INTEGER NOT NULL DEFAULT 6")
                if (fail) c.execSQL("INSERT INTO no_such_table VALUES (1)")
                c.execSQL("PRAGMA user_version = 2")
                c.execSQL("END TRANSACTION")
            } catch (e: Exception) {
                c.execSQL("ROLLBACK TRANSACTION")
                throw e
            }
            c
        } catch (e: Exception) {
            c.close()
            throw e
        }
    }

    private fun guard(db: File, open: suspend () -> SQLiteConnection): StoreOpening<SQLiteConnection> =
        runBlocking { Integrity.open(db, current = 2, raw = BundledRawStore, device = "TEST", today = day, open = open) }

    // SPEC:R145
    @Test fun `R145 a store's version 2 is read by the read-only probe, with a quick check of ok`() {
        assertEquals(Probe.Read(listOf("ok"), 2), BundledRawStore.probe(seed(2).absolutePath))
    }

    // SPEC:R145 SPEC:R144
    @Test fun `R145 a file of 10 bytes is no database, and a missing one cannot be read`() {
        val dir = TestDb.file().parentFile
        File(dir, "short.db").writeBytes(ByteArray(10) { 7 })
        assertEquals(Probe.Damaged, BundledRawStore.probe(File(dir, "short.db").absolutePath))
        assertEquals(Probe.Failed, BundledRawStore.probe(File(dir, "absent.db").absolutePath))
        assertFalse(File(dir, "absent.db").exists())
    }

    // SPEC:R145
    @Test fun `R145 a store of version 1 with 5 rows only in its log is snapshotted whole as tesoriere-v1-TEST for version 2`() {
        val db = TestDb.file()
        val a = withRowsInLog(db)
        val out = assertIs<Snapshot.Outcome.Kept>(Snapshot.before(db, 1, current = 2, raw = BundledRawStore, device = "TEST")).file
        assertEquals("snapshots", out.parentFile.name)
        assertEquals("tesoriere-v1-TEST.db", out.name)
        // one file, with no log beside it, and no half-written file left
        assertEquals(listOf(out), snapshots(db).listFiles()!!.toList())
        assertEquals(5, rowCount(out))
        assertEquals(1, version(out))
        a.close()
    }

    // SPEC:R145
    @Test fun `R145 a snapshot of a store left with 5 rows in its log holds them and leaves the store and its log byte for byte as they were`() {
        val db = TestDb.leftWithRowsInLog(version = 1, rows = 5)
        val main = db.readBytes()
        val wal = File(db.path + "-wal").readBytes()
        val out = assertIs<Snapshot.Outcome.Kept>(Snapshot.before(db, 1, current = 2, raw = BundledRawStore, device = "TEST")).file
        assertContentEquals(main, db.readBytes())
        assertContentEquals(wal, File(db.path + "-wal").readBytes())
        assertEquals(5, rowCount(out))
    }

    // SPEC:R145
    @Test fun `R145 a half-written snapshot left from before is replaced by a whole one of 3 rows`() {
        val db = seed(1)
        snapshots(db).mkdirs()
        val part = File(snapshots(db), "tesoriere-v1-TEST.db.part").apply { writeBytes(ByteArray(100) { 1 }) }
        val out = assertIs<Snapshot.Outcome.Kept>(Snapshot.before(db, 1, current = 2, raw = BundledRawStore, device = "TEST")).file
        assertEquals("tesoriere-v1-TEST.db", out.name)
        assertFalse(part.exists())
        assertEquals(listOf(out), snapshots(db).listFiles()!!.toList())
        assertEquals(3, rowCount(out))
    }

    // SPEC:R145
    @Test fun `R145 a store already of version 2 is left alone by an app of version 2`() {
        val db = seed(2)
        assertEquals(Snapshot.Outcome.NotNeeded, Snapshot.before(db, 2, current = 2, raw = BundledRawStore, device = "TEST"))
        assertFalse(snapshots(db).exists())
    }

    // SPEC:R145
    @Test fun `R145 a first run has nothing to protect and writes nothing`() {
        val db = TestDb.file()
        assertEquals(Snapshot.Outcome.NotNeeded, Snapshot.before(db, null, current = 2, raw = BundledRawStore, device = "TEST"))
        assertFalse(snapshots(db).exists())
    }

    // SPEC:R145
    @Test fun `R145 names carry the version and the device, and no time, so each is written once`() {
        assertEquals("tesoriere-v2-DESKTOP.db", Snapshot.nameFor(2, "DESKTOP"))
        assertEquals("tesoriere-v2-TestPhone2.db", Snapshot.nameFor(2, "Test Phone 2"))
        assertEquals("tesoriere-vunknown-A.db", Snapshot.nameFor(null, "A"))
        assertEquals("tesoriere-v1-device.db", Snapshot.nameFor(1, " ./ "))
        assertEquals("tesoriere-v1-abcdefghijklmnopqrstuvwx.db", Snapshot.nameFor(1, "abcdefghijklmnopqrstuvwxyz"))
    }

    // SPEC:R145
    @Test fun `R145 the version 7 is read through the log while the file header still reads 0`() {
        val db = TestDb.file()
        val a = driver.open(db.absolutePath)
        a.execSQL("PRAGMA journal_mode=WAL")
        a.execSQL("PRAGMA wal_autocheckpoint=0")
        a.execSQL("CREATE TABLE t (x TEXT)")
        a.execSQL("PRAGMA user_version = 7")
        val header = db.readBytes().copyOfRange(60, 64)
        assertContentEquals(ByteArray(4), header)
        assertEquals(Probe.Read(listOf("ok"), 7), BundledRawStore.probe(db.absolutePath))
        a.close()
    }

    // SPEC:R145
    @Test fun `R145 a snapshot of version 1 that exists is never rewritten`() {
        val db = seed(1)
        val first = assertIs<Snapshot.Outcome.Kept>(Snapshot.before(db, 1, 2, BundledRawStore, "TEST")).file
        val stamp = first.lastModified()
        val again = assertIs<Snapshot.Outcome.Kept>(Snapshot.before(db, 1, 2, BundledRawStore, "TEST")).file
        assertEquals(first, again)
        assertEquals(stamp, again.lastModified(), "a second pass must not rewrite the snapshot")
        assertEquals(1, snapshots(db).listFiles()!!.size)
    }

    // SPEC:R145
    @Test fun `R145 a store of version 1 with 3 rows is snapshotted before the migration to version 2 runs`() {
        val db = seed(1)
        val seenByMigration = mutableListOf<File>()
        val o = assertIs<StoreOpening.Opened<SQLiteConnection>>(guard(db, migrateTo2(db, fail = false, ran = seenByMigration)))
        o.store.close()
        assertEquals("tesoriere-v1-TEST.db", o.snapshot)
        assertEquals(DamageNotices.NONE, o.damage)
        val snap = File(snapshots(db), "tesoriere-v1-TEST.db")
        assertEquals(listOf(snap), seenByMigration)
        assertEquals(1, version(snap))
        assertEquals(3, rowCount(snap))
        assertFalse("trendMonths" in columns(snap))
        assertEquals(2, version(db))
        assertTrue("trendMonths" in columns(db))
        assertEquals(3, rowCount(db))
    }

    // SPEC:R145
    @Test fun `R145 a migration from 1 to 2 that fails after adding a column gives MIGRATION_FAILED and leaves the store and the snapshot as they were`() {
        val db = seed(1)
        assertEquals(StoreOpening.NotOpened(StoreProblem.MIGRATION_FAILED, 1, DamageNotices.NONE), guard(db, migrateTo2(db, fail = true)))
        assertEquals(1, version(db))
        assertEquals(3, rowCount(db))
        assertFalse("trendMonths" in columns(db))
        val snap = File(snapshots(db), "tesoriere-v1-TEST.db")
        val stamp = snap.lastModified()
        val bytes = snap.readBytes()

        // tried again: the same code, and the snapshot neither rewritten nor joined by another
        assertEquals(StoreOpening.NotOpened(StoreProblem.MIGRATION_FAILED, 1, DamageNotices.NONE), guard(db, migrateTo2(db, fail = true)))
        assertEquals(stamp, snap.lastModified())
        assertContentEquals(bytes, snap.readBytes())
        assertEquals(listOf(snap), snapshots(db).listFiles()!!.toList())
        assertEquals(1, version(snap))
        assertEquals(3, rowCount(snap))
        assertEquals(1, version(db))
    }

    // SPEC:R145
    @Test fun `R145 a store of version 1 whose snapshots folder cannot be made gives SNAPSHOT_FAILED and its migration does not run`() {
        val db = seed(1)
        snapshots(db).writeText("a file where the folder would go")
        var ran = false
        val r = guard(db) { ran = true; migrateTo2(db, fail = false)() }
        assertEquals(StoreOpening.NotOpened(StoreProblem.SNAPSHOT_FAILED, 1, DamageNotices.NONE), r)
        assertFalse(ran)
        assertEquals(1, version(db))
        assertFalse("trendMonths" in columns(db))
    }
}
