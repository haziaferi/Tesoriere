package com.hazia.tesoriere.data

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import com.hazia.tesoriere.engine.SystemClock
import com.hazia.tesoriere.model.SCHEMA_VERSION
import kotlinx.datetime.LocalDate
import java.io.File

/*
 * Ported from Sci-Fin native/shared/src/desktopMain/kotlin/app/scifin/data/SciFinDatabase.desktop.kt at 2892110.
 *
 * Changed from Sci-Fin: the store opens only through the guard (SPEC R144, R145), whose outcome is
 * [StoreOpening]: a damaged file moved aside, a newer store left alone, a snapshot before a migration,
 * and a code for each store not opened; Sci-Fin built the database whatever the guard and the snapshot
 * gave. Before Room, the store is reached only through [BundledRawStore], read-only, for the quick
 * check, the version and the snapshot alike, where Sci-Fin opened it to write: closing a connection
 * that can write folded a write-ahead log in and deleted it. Only SQLite's codes for damage move a
 * store ([probeFailure]). The unguarded builders are internal, for tests. No log of sentences. Section references are
 * replaced by SPEC rule numbers.
 */

/**
 * Desktop bootstrap (SPEC R141): the database file [dbFile] with the bundled SQLite driver, opened
 * through the guard (SPEC R144, R145). [today] dates a damaged copy; [device] names the copies.
 */
suspend fun openTesoriereDatabase(
    dbFile: File,
    today: LocalDate = SystemClock.today(),
    device: String = deviceName(),
): StoreOpening<TesoriereDatabase> {
    dbFile.parentFile?.mkdirs()
    return Integrity.open(dbFile, SCHEMA_VERSION, BundledRawStore, device, today) {
        Room.databaseBuilder<TesoriereDatabase>(name = dbFile.absolutePath).openFirstConnection(BundledSQLiteDriver())
    }
}

/**
 * The forgotten-PIN erase (SPEC R147) of the store [repo] reads, the file [dbFile] it was opened
 * from, then its `snapshots` folder and the damage notices beside it; Android's counterpart is
 * eraseTesoriere(context, repo).
 */
suspend fun eraseTesoriere(dbFile: File, repo: ProfileRepository): Erased = Erase.everything(repo, dbFile)

/** The database file [dbFile] with no guard: tests only; the app opens through [openTesoriereDatabase]. */
internal fun buildTesoriereDatabase(dbFile: File): TesoriereDatabase {
    dbFile.parentFile?.mkdirs()
    return Room.databaseBuilder<TesoriereDatabase>(name = dbFile.absolutePath)
        .finishBuilding(BundledSQLiteDriver())
}

/** An in-memory database for tests: nothing touches the disk. */
internal fun buildInMemoryTesoriereDatabase(): TesoriereDatabase =
    Room.inMemoryDatabaseBuilder<TesoriereDatabase>().finishBuilding(BundledSQLiteDriver())

/**
 * The desktop's way into the store before Room (SPEC R144, R145): the bundled driver, which deletes
 * nothing, opened read-only, so closing it cannot fold the write-ahead log in nor delete it.
 */
internal object BundledRawStore : RawStore {
    override fun probe(path: String): Probe = try {
        readOnly(path) { c ->
            val check = c.prepare("PRAGMA quick_check").use { s -> buildList { while (s.step()) add(s.getText(0)) } }
            val version = c.prepare("PRAGMA user_version").use { s -> if (s.step()) s.getInt(0) else 0 }
            Probe.Read(check, version)
        }
    } catch (e: Exception) {
        probeFailure(e)
    } catch (e: LinkageError) {
        probeFailure(e)
    }

    override fun vacuumInto(path: String, out: String) {
        readOnly(path) { c -> c.execSQL("VACUUM INTO '" + out.replace("'", "''") + "'") }
    }

    private fun <T> readOnly(path: String, block: (SQLiteConnection) -> T): T {
        val c = BundledSQLiteDriver().open(path, SQLITE_OPEN_READONLY)
        try {
            return block(c)
        } finally {
            c.close()
        }
    }
}

private val ERROR_CODE = Regex("""Error code: (-?\d+)""")

/**
 * What a failure of the bundled driver means to the guard (SPEC R144): only SQLITE_CORRUPT (11) and
 * SQLITE_NOTADB (26), extended codes included, mean damage; SQLITE_BUSY (5) and SQLITE_LOCKED (6)
 * mean another connection holds it; anything else, a native library that did not load included, is
 * a failure that moves nothing.
 */
internal fun probeFailure(e: Throwable): Probe {
    val code = (e as? SQLiteException)?.message?.let { m -> ERROR_CODE.find(m)?.groupValues?.get(1)?.toIntOrNull() }
        ?: return Probe.Failed
    return when (code and 0xFF) {
        11, 26 -> Probe.Damaged
        5, 6 -> Probe.Busy
        else -> Probe.Failed
    }
}

/** Names the copies so two machines writing into one folder cannot collide (SPEC R145). */
private fun deviceName(): String =
    (System.getenv("COMPUTERNAME") ?: System.getProperty("user.name") ?: "desktop").take(24)
