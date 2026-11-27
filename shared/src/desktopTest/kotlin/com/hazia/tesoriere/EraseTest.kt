package com.hazia.tesoriere

import com.hazia.tesoriere.data.DamageNotices
import com.hazia.tesoriere.data.Erase
import com.hazia.tesoriere.data.EraseProblem
import com.hazia.tesoriere.data.Export
import com.hazia.tesoriere.engine.Applied
import com.hazia.tesoriere.engine.Backup
import com.hazia.tesoriere.engine.BackupResult
import com.hazia.tesoriere.data.ProfileRepository
import com.hazia.tesoriere.data.StoreOpening
import com.hazia.tesoriere.data.TesoriereDatabase
import com.hazia.tesoriere.data.openTesoriereDatabase
import com.hazia.tesoriere.engine.LockAfter
import com.hazia.tesoriere.engine.LockState
import com.hazia.tesoriere.engine.Pin
import com.hazia.tesoriere.engine.Trash
import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.CopySchedule
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * The forgotten-PIN erase (SPEC R147), after Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/LockTest.kt's
 * erase case at 2892110, through the real store on disk and the guard. Changed from Sci-Fin: the
 * automatic copy's folder and any file outside `snapshots` stay; the notices kept aside go too; a
 * file that cannot be deleted and a store that cannot be erased give codes. Every name, PIN and
 * figure is made up.
 */
class EraseTest {
    private val day = LocalDate(2026, 11, 27)
    private val damaged = "tesoriere-damaged-2026-11-27-TEST.db"

    private fun opened(file: File): StoreOpening.Opened<TesoriereDatabase> =
        assertIs(runBlocking { openTesoriereDatabase(file, day, "TEST") })

    private fun repo(db: TesoriereDatabase) = ProfileRepository(db, TestClock(day), Ids("e")::next)

    private class Device(val file: File, val db: TesoriereDatabase, val myBackup: File, val chosen: File, val bytes: ByteArray)

    /**
     * A store that was damaged once (its copy and notice kept), then holding Profiles A and B, a
     * lock, a weekly copy and a Trash item, with a snapshot, a notice kept aside as unreadable, a
     * file beside the store and a backup in a folder of the user's.
     */
    private fun device(): Device {
        val file = storeIn(TestDb.file().parentFile)
        file.writeBytes(Random(27).nextBytes(8192))
        val o = opened(file)
        assertTrue(o.damage.owed)
        val r = repo(o.store)
        runBlocking {
            r.createProfile("A", "p-a")
            r.createProfile("B", "p-b")
            o.store.dao().upsertAppLock(assertNotNull(Pin.lockFor("4821", "the old bus", LockAfter.ONE_MINUTE, iterations = 1_000)))
            o.store.dao().upsertCopySchedule(CopySchedule(every = "weekly", folder = "chosen-folder", lastCopy = "2026-11-20"))
            r.update("p-a") { it.copy(categories = listOf(Category("fun-a", "p-a", "Fun"))) }
            assertNotNull(r.deleteToTrash("p-a", "category", "Fun") { Trash.deleteCategory(it, "fun-a") })
        }
        val dir = file.parentFile
        File(dir, "snapshots/tesoriere-v1-TEST.db").writeBytes(ByteArray(4096) { 7 })
        File(dir, "tesoriere.damaged.unreadable").writeText("{oops")
        val bytes = """{"app":"tesoriere","made":"up"}""".encodeToByteArray()
        val myBackup = File(dir, "my-backup.json").also { it.writeBytes(bytes) }
        val chosen = File(dir.parentFile, "chosen/tesoriere-2026-11-20-TEST.json").also { it.parentFile.mkdirs(); it.writeBytes(bytes) }
        return Device(file, o.store, myBackup, chosen, bytes)
    }

    /** `<base>/store/tesoriere.db`, so folders outside the store's can be made beside it, all under the test's temp folder. */
    private fun storeIn(base: File): File = File(base, "store/tesoriere.db").also { it.parentFile.mkdirs() }

    private val precious = """{"made":"up","keep":true}""".encodeToByteArray()

    /** `<base>/<name>/precious.json`, a folder outside the store's that the erase must not reach. */
    private fun outside(base: File, name: String): File =
        File(base, "$name/precious.json").also { it.parentFile.mkdirs(); it.writeBytes(precious) }

    private fun linkGone(f: File) = !Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS)

    /** A Windows junction [link] to [target], made by `mklink /J`; false when it could not be made. */
    private fun junction(link: File, target: File): Boolean {
        val p = ProcessBuilder("cmd", "/c", "mklink", "/J", link.absolutePath, target.absolutePath).redirectErrorStream(true).start()
        p.inputStream.readBytes()
        return p.waitFor() == 0 && Files.exists(link.toPath(), LinkOption.NOFOLLOW_LINKS)
    }

    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    // SPEC:R145
    // SPEC:R147
    @Test fun `R147 on Windows a junction in snapshots and one standing as snapshots are removed alone, and precious json stays`() {
        // junctions exist only on Windows; elsewhere the symbolic-link test below covers links
        assumeTrue("junctions are a Windows feature", windows)
        val base = TestDb.file().parentFile
        val store = storeIn(base)
        val inner = outside(base, "outside-inner")
        val snapshots = File(store.parentFile, "snapshots").also { it.mkdirs() }
        File(snapshots, "tesoriere-v1-TEST.db").writeBytes(ByteArray(64) { 1 })
        val j = File(snapshots, "j")
        assertTrue(junction(j, inner.parentFile), "mklink /J failed")

        assertEquals(emptyList(), Erase.files(store))
        assertContentEquals(precious, inner.readBytes()); assertTrue(linkGone(j)); assertTrue(linkGone(snapshots))

        val whole = outside(base, "outside-whole")
        assertTrue(junction(snapshots, whole.parentFile), "mklink /J failed")
        assertEquals(emptyList(), Erase.files(store))
        assertContentEquals(precious, whole.readBytes()); assertTrue(linkGone(snapshots))
    }

    // SPEC:R145
    // SPEC:R147
    @Test fun `R147 a symbolic link in snapshots and one standing as snapshots are removed alone, and precious json stays`() {
        val base = TestDb.file().parentFile
        val store = storeIn(base)
        val inner = outside(base, "outside-inner")
        val snapshots = File(store.parentFile, "snapshots").also { it.mkdirs() }
        val link = File(snapshots, "l")
        val made = try {
            Files.createSymbolicLink(link.toPath(), inner.parentFile.toPath())
            true
        } catch (_: Exception) {
            false
        }
        // Windows without the right to make links refuses: nothing to test there
        assumeTrue("the system refused to make a symbolic link", made)
        File(snapshots, "tesoriere-v1-TEST.db").writeBytes(ByteArray(64) { 1 })

        assertEquals(emptyList(), Erase.files(store))
        assertContentEquals(precious, inner.readBytes()); assertTrue(linkGone(link)); assertTrue(linkGone(snapshots))

        val whole = outside(base, "outside-whole")
        Files.createSymbolicLink(snapshots.toPath(), whole.parentFile.toPath())
        assertEquals(emptyList(), Erase.files(store))
        assertContentEquals(precious, whole.readBytes()); assertTrue(linkGone(snapshots))
    }

    // SPEC:R147
    @Test fun `R147 a read held open by another connection leaves the rows gone and gives STORE_NOT_COMPACTED`() {
        val d = device()
        val reader = BundledSQLiteDriver().open(d.file.absolutePath)
        try {
            reader.execSQL("BEGIN")
            reader.prepare("SELECT COUNT(*) FROM profiles").use { it.step() }
            assertEquals(EraseProblem.STORE_NOT_COMPACTED, runBlocking { repo(d.db).eraseStore() })
            runBlocking { assertTrue(d.db.dao().profiles().isEmpty()); assertNull(d.db.dao().appLock()) }
        } finally {
            reader.close()
            d.db.close()
        }
    }

    // SPEC:R121
    // SPEC:R144
    // SPEC:R145
    // SPEC:R147
    @Test fun `R147 begun by a caller already cancelled, the erase still erases the store and the files`() {
        val d = device()
        runBlocking {
            val job = launch {
                cancel()
                Erase.everything(repo(d.db), d.file)
            }
            job.join()
            assertTrue(d.db.dao().profiles().isEmpty()); assertNull(d.db.dao().appLock()); assertTrue(d.db.dao().trash().isEmpty())
        }
        assertTrue(linkGone(File(d.file.parentFile, "snapshots"))); assertFalse(File(d.file.parentFile, "tesoriere.damaged").exists())
        assertContentEquals(d.bytes, d.myBackup.readBytes())
        d.db.close()
    }

    // SPEC:R121
    // SPEC:R144
    // SPEC:R145
    // SPEC:R147 SPEC:R146 SPEC:R18
    @Test fun `R147 after the erase a fresh start opens an empty store with the lock OFF, and files outside snapshots stay`() {
        val d = device()
        val out = runBlocking { Erase.everything(repo(d.db), d.file) }
        assertEquals(emptySet(), out.problems); assertEquals(emptyList(), out.filesLeft)
        d.db.close()

        val again = opened(d.file)
        try {
            assertEquals(DamageNotices.NONE, again.damage, "no notice is owed after the erase")
            runBlocking {
                val dao = again.store.dao()
                assertTrue(dao.profiles().isEmpty()); assertNull(dao.appState())
                assertNull(dao.appLock()); assertEquals(LockState.OFF, Pin.state(dao.appLock()))
                assertNull(dao.copySchedule()); assertTrue(dao.trash().isEmpty())
                assertNull(repo(again.store).open())
            }
        } finally {
            again.store.close()
        }
        val dir = d.file.parentFile
        assertFalse(File(dir, "snapshots").exists())
        assertFalse(File(dir, "tesoriere.damaged").exists()); assertFalse(File(dir, "tesoriere.damaged.unreadable").exists())
        assertContentEquals(d.bytes, d.myBackup.readBytes()); assertContentEquals(d.bytes, d.chosen.readBytes())
    }

    // SPEC:R143 SPEC:R147 SPEC:R146 SPEC:R148
    @Test fun `R143 a backup of A and B restored after the erase gives them back, B open, with the lock OFF, no copy schedule and no notice owed`() {
        val d = device()
        val text = runBlocking { assertIs<Export.Ok>(repo(d.db).backupFile(day)).text }
        assertEquals(emptySet(), runBlocking { Erase.everything(repo(d.db), d.file) }.problems)
        d.db.close()

        val again = opened(d.file)
        try {
            assertEquals(DamageNotices.NONE, again.damage)
            val r = repo(again.store)
            runBlocking {
                assertEquals(Applied.Restored, Backup.apply(again.store, assertIs<BackupResult.Ok>(Backup.validate(text)).backup, day))
                assertEquals(listOf("A", "B"), r.profiles().map { it.name })
                assertEquals("p-b", r.open()!!.profileId, "the Profile open at the export")
                assertEquals(listOf("category"), r.trash().map { it.kind })
                assertEquals(LockState.OFF, r.lock().state)
                assertNull(r.copySchedule())
                assertEquals(day, r.appState()!!.lastBackupImportDate)
            }
        } finally {
            again.store.close()
        }
        assertEquals(DamageNotices.NONE, opened(d.file).also { it.store.close() }.damage, "the restore owes no notice")
    }

    // SPEC:R121
    // SPEC:R144
    // SPEC:R145
    // SPEC:R147
    @Test fun `R147 a damaged copy that cannot be deleted gives FILE_NOT_DELETED naming it, the store erased and the rest gone`() {
        val d = device()
        val stuck = File(d.file.parentFile, "snapshots/$damaged")
        assertTrue(stuck.isFile)
        val out = runBlocking { Erase.everything(repo(d.db), d.file) { f -> f.canonicalFile != stuck.canonicalFile && f.delete() } }
        assertEquals(setOf(EraseProblem.FILE_NOT_DELETED), out.problems)
        assertEquals(listOf("snapshots/$damaged"), out.filesLeft)
        runBlocking {
            assertTrue(d.db.dao().profiles().isEmpty()); assertNull(d.db.dao().appLock()); assertTrue(d.db.dao().trash().isEmpty())
        }
        val dir = d.file.parentFile
        assertFalse(File(dir, "snapshots/tesoriere-v1-TEST.db").exists())
        assertFalse(File(dir, "tesoriere.damaged").exists()); assertFalse(File(dir, "tesoriere.damaged.unreadable").exists())
        assertTrue(stuck.isFile)

        // run again once the copy can go: no problem, nothing left
        val second = runBlocking { Erase.everything(repo(d.db), d.file) }
        assertEquals(emptySet(), second.problems); assertEquals(emptyList(), second.filesLeft)
        assertFalse(File(dir, "snapshots").exists())
        assertContentEquals(d.bytes, d.myBackup.readBytes())
        d.db.close()
    }

    // SPEC:R147
    @Test fun `R147 a store already closed gives STORE_NOT_ERASED and touches no file`() {
        val d = device()
        d.db.close()
        val out = runBlocking { Erase.everything(repo(d.db), d.file) }
        assertEquals(setOf(EraseProblem.STORE_NOT_ERASED), out.problems)
        val dir = d.file.parentFile
        assertTrue(File(dir, "snapshots/tesoriere-v1-TEST.db").isFile); assertTrue(File(dir, "snapshots/$damaged").isFile)
        assertTrue(File(dir, "tesoriere.damaged").isFile); assertTrue(File(dir, "tesoriere.damaged.unreadable").isFile)

        val again = opened(d.file)
        try {
            assertEquals(2, runBlocking { again.store.dao().profiles().size }, "nothing was erased")
            assertNotNull(runBlocking { again.store.dao().appLock() })
        } finally {
            again.store.close()
        }
    }
}
