package com.hazia.tesoriere

import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.hazia.tesoriere.data.BundledRawStore
import com.hazia.tesoriere.data.DamageNotice
import com.hazia.tesoriere.data.DamageNotices
import com.hazia.tesoriere.data.DamageReason
import com.hazia.tesoriere.data.Integrity
import com.hazia.tesoriere.data.Probe
import com.hazia.tesoriere.data.ProfileRepository
import com.hazia.tesoriere.data.RawStore
import com.hazia.tesoriere.data.StoreOpening
import com.hazia.tesoriere.data.StoreProblem
import com.hazia.tesoriere.data.TesoriereDatabase
import com.hazia.tesoriere.data.openTesoriereDatabase
import com.hazia.tesoriere.data.probeFailure
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/IntegrityTest.kt at 2892110.
 *
 * Changed from Sci-Fin: the guard runs through the desktop builder Tesoriere's app must use
 * (`openTesoriereDatabase`), and its notices are records of codes and figures (SPEC R144). Dropped:
 * the cases of Sci-Fin's screen and app store (the damage sheet, Start fresh, a restore answering
 * it), which are the design pass's (SPEC R13, R16). Added: the notice's figures, a second copy the
 * same day, a store that cannot be moved, the write-ahead log moved with the store, and the newer
 * and unopenable stores of SPEC R145. Added after review: a move cut short and finished at the next
 * start, log files with no store, several notices and an unreadable notice file, failures that are
 * not damage, a locked store, a move that would overwrite, and the apps' sources scanned for a way
 * around the guard.
 */
class IntegrityTest {
    private val day = LocalDate(2026, 11, 26)
    private val damaged = "tesoriere-damaged-2026-11-26-TEST.db"

    private fun open(file: File): StoreOpening<TesoriereDatabase> = runBlocking { openTesoriereDatabase(file, day, "TEST") }

    private fun opened(file: File): StoreOpening.Opened<TesoriereDatabase> = assertIs(open(file))

    /** The guard with [move] in place of the real one, opening nothing but a token. */
    private fun guardWith(file: File, move: (File, File) -> Boolean): StoreOpening<String> =
        runBlocking { Integrity.open(file, 1, BundledRawStore, "TEST", day, move) { "opened" } }

    private fun profiles(o: StoreOpening.Opened<TesoriereDatabase>): Int =
        runBlocking { ProfileRepository(o.store, TestClock(day), Ids("g")::next).profiles().size }.also { o.store.close() }

    /** A store holding Profile Ana, written and closed through the guarded builder. */
    private fun healthy(file: File) {
        val o = opened(file)
        runBlocking { ProfileRepository(o.store, TestClock(day), Ids("h")::next).createProfile("Ana", "p-ana") }
        o.store.close()
    }

    private fun noise(file: File, size: Int = 8192, seed: Int = 26): ByteArray =
        Random(seed).nextBytes(size).also { file.writeBytes(it) }

    private fun snapshots(file: File) = File(file.parentFile, "snapshots")
    private fun marker(file: File) = File(file.parentFile, "tesoriere.damaged")
    private fun notice(keptAs: String = damaged, bytes: Long = 8192, reason: DamageReason = DamageReason.UNREADABLE) =
        DamageNotice(keptAs, bytes, day, reason)
    private fun owed(vararg n: DamageNotice, unreadable: Int = 0) = DamageNotices(n.toList(), unreadable)

    // SPEC:R144
    @Test fun `R144 a healthy store of one Profile stays where it is, with no notice and no snapshot`() {
        val file = TestDb.file()
        healthy(file)
        val o = opened(file)
        assertEquals(DamageNotices.NONE, o.damage)
        assertEquals(null, o.snapshot)
        assertEquals(1, profiles(o))
        assertTrue(file.isFile)
        assertFalse(snapshots(file).exists())
    }

    // SPEC:R144 SPEC:R145
    @Test fun `R144 a first run makes a fresh store and writes no snapshot and no notice`() {
        val file = TestDb.file()
        val o = opened(file)
        assertEquals(DamageNotices.NONE, o.damage)
        assertEquals(null, o.snapshot)
        assertEquals(0, profiles(o))
        assertFalse(snapshots(file).exists())
    }

    // SPEC:R144
    @Test fun `R144 an 8192-byte file of noise moves to tesoriere-damaged-2026-11-26-TEST whole and an empty store opens`() {
        val file = TestDb.file()
        val bytes = noise(file)
        val o = opened(file)
        assertEquals(owed(notice()), o.damage)
        assertEquals(0, profiles(o))
        val copy = File(snapshots(file), damaged)
        assertContentEquals(bytes, copy.readBytes())

        // a restart before the notice is answered gives it again
        assertEquals(owed(notice()), opened(file).also { it.store.close() }.damage)
        assertEquals(owed(notice()), Integrity.notices(file))

        // answered: no notice, and the copy stays
        assertTrue(Integrity.clearNotice(file, damaged))
        assertEquals(DamageNotices.NONE, Integrity.notices(file))
        assertEquals(DamageNotices.NONE, opened(file).also { it.store.close() }.damage)
        assertContentEquals(bytes, copy.readBytes())
    }

    // SPEC:R144
    @Test fun `R144 the write-ahead log and shared-memory files move with the store, under the same name`() {
        val file = TestDb.file()
        noise(file)
        val wal = Random(3).nextBytes(4096).also { File(file.path + "-wal").writeBytes(it) }
        File(file.path + "-shm").writeBytes(ByteArray(32768))
        opened(file).store.close()
        val kept = File(snapshots(file), damaged)
        assertContentEquals(wal, File(kept.path + "-wal").readBytes())
        assertEquals(32768L, File(kept.path + "-shm").length())
    }

    // SPEC:R144
    @Test fun `R144 a store of one Profile with 2048 bytes overwritten past its first page moves with CHECK_FAILED`() {
        val file = TestDb.file()
        healthy(file)
        assertTrue(file.length() > 4096 * 3, "needs more than the header page, was ${file.length()}")
        RandomAccessFile(file, "rw").use { f -> f.seek(4096L + 16); f.write(ByteArray(2048) { 0x5A }) }
        val length = file.length()
        val o = opened(file)
        assertEquals(owed(notice(bytes = length, reason = DamageReason.CHECK_FAILED)), o.damage)
        assertEquals(0, profiles(o))
    }

    // SPEC:R144
    @Test fun `R144 a second damaged store that day goes to tesoriere-damaged-2026-11-26-TEST-2, and both notices are owed`() {
        val file = TestDb.file()
        val first = noise(file)
        opened(file).store.close()
        val firstCopy = File(snapshots(file), damaged)
        val stamp = firstCopy.lastModified()

        val second = noise(file, 12288, seed = 99)
        val o = opened(file)
        o.store.close()
        val secondNotice = notice("tesoriere-damaged-2026-11-26-TEST-2.db", 12288)
        assertEquals(owed(notice(), secondNotice), o.damage)
        assertContentEquals(first, firstCopy.readBytes())
        assertEquals(stamp, firstCopy.lastModified())
        assertContentEquals(second, File(snapshots(file), "tesoriere-damaged-2026-11-26-TEST-2.db").readBytes())

        // each is answered on its own
        assertTrue(Integrity.clearNotice(file, damaged))
        assertEquals(owed(secondNotice), Integrity.notices(file))
        assertFalse(Integrity.clearNotice(file, damaged))
        assertEquals(owed(secondNotice), Integrity.notices(file))
    }

    // SPEC:R144
    @Test fun `R144 a notice file that cannot be read stays owed, beside a notice written after it, until answered`() {
        val file = TestDb.file()
        healthy(file)
        marker(file).writeText("{oops")
        assertEquals(owed(unreadable = 1), opened(file).also { it.store.close() }.damage)

        val other = TestDb.file()
        assertEquals(DamageNotices.NONE, Integrity.notices(other))
        // a store damaged later: its notice is kept and the unreadable one is still owed
        file.delete()
        noise(file)
        assertEquals(owed(notice(), unreadable = 1), opened(file).also { it.store.close() }.damage)
        assertEquals(1, Integrity.clearUnreadableMarkers(file))
        assertEquals(owed(notice()), Integrity.notices(file))
    }

    // SPEC:R144
    @Test fun `R144 a notice file half written beside a whole one leaves the whole one owed`() {
        val file = TestDb.file()
        noise(file)
        opened(file).store.close()
        File(marker(file).path + ".part").writeText("[{\"notice\":")
        assertEquals(owed(notice()), Integrity.notices(file))
        // the next notice is written whole, and replaces the half-written file
        file.delete()
        noise(file, seed = 5)
        opened(file).store.close()
        assertEquals(2, Integrity.notices(file).notices.size)
        assertFalse(File(marker(file).path + ".part").exists())
    }

    // SPEC:R144
    @Test fun `R144 the notice of a move is kept before the first file moves`() {
        val file = TestDb.file()
        noise(file)
        val seen = mutableListOf<String>()
        val r = guardWith(file) { from, to ->
            seen += if (marker(file).isFile) marker(file).readText() else ""
            Integrity.move(from, to)
        }
        assertIs<StoreOpening.Opened<String>>(r)
        assertTrue(damaged in seen.first(), "the first move began with no notice kept: ${seen.first()}")
    }

    // SPEC:R144
    @Test fun `R144 a move whose log fails to move puts the store back, byte for byte, with no notice`() {
        val file = TestDb.file()
        val bytes = noise(file)
        val wal = Random(3).nextBytes(4096).also { File(file.path + "-wal").writeBytes(it) }
        val r = guardWith(file) { from, to -> !to.name.endsWith("-wal") && Integrity.move(from, to) }
        assertEquals(StoreOpening.NotOpened(StoreProblem.NOT_MOVED, null, DamageNotices.NONE), r)
        assertContentEquals(bytes, file.readBytes())
        assertContentEquals(wal, File(file.path + "-wal").readBytes())
        assertEquals(emptyList(), snapshots(file).listFiles().orEmpty().toList())
        // the next start moves it whole, under the first name of the day
        assertEquals(owed(notice()), opened(file).also { it.store.close() }.damage)
    }

    // SPEC:R144
    @Test fun `R144 a move cut short with its store gone and its log left is finished at the next start, its 4096-byte log kept`() {
        val file = TestDb.file()
        val bytes = noise(file)
        val wal = Random(3).nextBytes(4096).also { File(file.path + "-wal").writeBytes(it) }
        // the log will not move and the store will not move back: what a crash between the two leaves
        val r = guardWith(file) { from, to -> !to.name.endsWith("-wal") && to != file && Integrity.move(from, to) }
        assertEquals(StoreOpening.NotOpened(StoreProblem.NOT_MOVED, null, DamageNotices.NONE), r)
        assertFalse(file.exists())

        val o = opened(file)
        assertEquals(owed(notice()), o.damage)
        assertEquals(0, profiles(o))
        val kept = File(snapshots(file), damaged)
        assertContentEquals(bytes, kept.readBytes())
        assertContentEquals(wal, File(kept.path + "-wal").readBytes())
    }

    // SPEC:R144
    @Test fun `R144 a 4096-byte log with no store beside it is moved aside as LEFTOVER before a fresh store opens`() {
        val file = TestDb.file()
        val wal = Random(8).nextBytes(4096).also { File(file.path + "-wal").writeBytes(it) }
        val o = opened(file)
        assertEquals(owed(notice(bytes = 0, reason = DamageReason.LEFTOVER)), o.damage)
        assertEquals(0, profiles(o))
        assertContentEquals(wal, File(File(snapshots(file), damaged).path + "-wal").readBytes())
        assertFalse(File(snapshots(file), damaged).exists())
    }

    // SPEC:R144
    @Test fun `R144 a damaged store whose snapshots folder cannot be made gives NOT_MOVED and stays where it was`() {
        val file = TestDb.file()
        val bytes = noise(file)
        snapshots(file).writeText("a file where the folder would go")
        assertEquals(StoreOpening.NotOpened(StoreProblem.NOT_MOVED, null, DamageNotices.NONE), open(file))
        assertContentEquals(bytes, file.readBytes())
        assertEquals(DamageNotices.NONE, Integrity.notices(file))
    }

    // SPEC:R144
    @Test fun `R144 a store SQLite fails to read for a reason other than damage gives NOT_CHECKED, moved nowhere and not opened`() {
        val file = TestDb.file()
        val bytes = noise(file)
        val failing = object : RawStore {
            override fun probe(path: String) = Probe.Failed
            override fun vacuumInto(path: String, out: String) = error("not reached")
        }
        var ran = false
        val r = runBlocking { Integrity.open(file, 1, failing, "TEST", day) { ran = true } }
        assertEquals(StoreOpening.NotOpened(StoreProblem.NOT_CHECKED, null, DamageNotices.NONE), r)
        assertFalse(ran)
        assertContentEquals(bytes, file.readBytes())
        assertFalse(snapshots(file).exists())
    }

    // SPEC:R144
    @Test fun `R144 only SQLite's codes 11 and 26 are damage, 5 and 6 a lock, and 14, 10, a disk error or a missing library move nothing`() {
        fun e(code: Int) = SQLiteException("Error code: $code, message: test")
        assertEquals(Probe.Damaged, probeFailure(e(11)))
        assertEquals(Probe.Damaged, probeFailure(e(26)))
        assertEquals(Probe.Damaged, probeFailure(e(779))) // SQLITE_CORRUPT_INDEX, an extended 11
        assertEquals(Probe.Busy, probeFailure(e(5)))
        assertEquals(Probe.Busy, probeFailure(e(6)))
        assertEquals(Probe.Failed, probeFailure(e(14)))
        assertEquals(Probe.Failed, probeFailure(e(10)))
        assertEquals(Probe.Failed, probeFailure(e(3338))) // SQLITE_IOERR_FSYNC
        assertEquals(Probe.Failed, probeFailure(SQLiteException("no code")))
        assertEquals(Probe.Failed, probeFailure(IOException("disk")))
        assertEquals(Probe.Failed, probeFailure(UnsatisfiedLinkError("sqliteJni")))
    }

    // SPEC:R144
    @Test fun `R144 a store held locked by another connection gives BUSY and stays as it was`() {
        val file = TestDb.file()
        val c = BundledSQLiteDriver().open(file.absolutePath)
        c.execSQL("CREATE TABLE notes (id TEXT PRIMARY KEY)")
        c.execSQL("INSERT INTO notes VALUES ('kept')")
        c.execSQL("BEGIN EXCLUSIVE TRANSACTION")
        try {
            assertEquals(StoreOpening.NotOpened(StoreProblem.BUSY, null, DamageNotices.NONE), open(file))
            assertFalse(snapshots(file).exists())
        } finally {
            c.execSQL("ROLLBACK TRANSACTION")
            c.close()
        }
        assertTrue(file.isFile)
    }

    // SPEC:R144
    @Test fun `R144 a move onto a file that is there already fails and leaves both files as they were`() {
        val dir = TestDb.file().parentFile
        val from = File(dir, "from.db").apply { writeText("from") }
        val to = File(dir, "to.db").apply { writeText("kept") }
        assertFalse(Integrity.move(from, to))
        assertEquals("kept", to.readText())
        assertEquals("from", from.readText())
    }

    // SPEC:R145
    @Test fun `R145 a store of version 2 reached by an app of version 1 gives NEWER_SCHEMA with 2, byte for byte as it was`() {
        val file = TestDb.file()
        val c = BundledSQLiteDriver().open(file.absolutePath)
        c.execSQL("CREATE TABLE settings (profileId TEXT PRIMARY KEY, currency TEXT NOT NULL)")
        c.execSQL("INSERT INTO settings VALUES ('p-1', 'EUR')")
        c.execSQL("PRAGMA user_version = 2")
        c.close()
        val bytes = file.readBytes()
        assertEquals(StoreOpening.NotOpened(StoreProblem.NEWER_SCHEMA, 2, DamageNotices.NONE), open(file))
        assertContentEquals(bytes, file.readBytes())
        assertFalse(snapshots(file).exists())
        assertFalse(File(file.path + "-wal").exists())
    }

    // SPEC:R145
    @Test fun `R145 a store of version 2 left with 5 rows in its log is left byte for byte as it was, its log included`() {
        val file = TestDb.leftWithRowsInLog(version = 2, rows = 5)
        val main = file.readBytes()
        val wal = File(file.path + "-wal").readBytes()
        assertTrue(wal.isNotEmpty())
        assertEquals(StoreOpening.NotOpened(StoreProblem.NEWER_SCHEMA, 2, DamageNotices.NONE), open(file))
        assertContentEquals(main, file.readBytes())
        assertContentEquals(wal, File(file.path + "-wal").readBytes())
        assertFalse(snapshots(file).exists())
    }

    // SPEC:R145
    @Test fun `R145 a store of the app's version that Room cannot open gives OPEN_FAILED and keeps its rows`() {
        val file = TestDb.file()
        val c = BundledSQLiteDriver().open(file.absolutePath)
        c.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        c.execSQL("INSERT INTO room_master_table VALUES (42, 'not this schema')")
        c.execSQL("CREATE TABLE notes (id TEXT PRIMARY KEY)")
        c.execSQL("INSERT INTO notes VALUES ('kept')")
        c.execSQL("PRAGMA user_version = 1")
        c.close()
        assertEquals(StoreOpening.NotOpened(StoreProblem.OPEN_FAILED, 1, DamageNotices.NONE), open(file))
        val r = BundledSQLiteDriver().open(file.absolutePath)
        try {
            r.prepare("SELECT id FROM notes").use { s -> assertTrue(s.step()); assertEquals("kept", s.getText(0)) }
            r.prepare("SELECT identity_hash FROM room_master_table").use { s -> assertTrue(s.step()); assertEquals("not this schema", s.getText(0)) }
        } finally {
            r.close()
        }
        assertFalse(snapshots(file).exists())
    }

    // SPEC:R144 SPEC:R145
    @Test fun `R144 neither app's sources build the database except through openTesoriereDatabase`() {
        val around = Regex("""\bdatabaseBuilder\b|\binMemoryDatabaseBuilder\b|\bRoom\.|\bbuildTesoriereDatabase\b|\bbuildInMemoryTesoriereDatabase\b|\bfinishBuilding\b|\bopenFirstConnection\b|\bTesoriereDatabaseConstructor\b""")
        // the matcher itself, so an empty scan cannot pass for a clean one
        assertTrue(around.containsMatchIn("val db = Room.databaseBuilder<TesoriereDatabase>(name = path).build()"))
        assertFalse(around.containsMatchIn("val opening = openTesoriereDatabase(context)"))
        val root = listOf(File(".."), File(".")).first { File(it, "androidApp/src").isDirectory }
        val sources = listOf("androidApp", "desktopApp").map { File(root, it) }
            .flatMap { app -> app.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") && "build" !in it.relativeTo(app).invariantSeparatorsPath.split('/') }.toList() }
        val found = sources.flatMap { f -> f.readLines().withIndex().filter { around.containsMatchIn(it.value) }.map { "${f.path}:${it.index + 1}: ${it.value.trim()}" } }
        assertEquals(emptyList(), found)
    }
}
