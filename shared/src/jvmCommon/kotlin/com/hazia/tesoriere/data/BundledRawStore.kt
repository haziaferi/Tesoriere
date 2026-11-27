package com.hazia.tesoriere.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.execSQL

/*
 * New in Tesoriere: Sci-Fin's guard opened the store through each platform's Room driver, for
 * writing. Moved from the desktop's database builder so Android's guard and Room use the same
 * bundled SQLite as the desktop's (SPEC R144): Android's framework driver opens with a default error
 * handler that deletes a database it finds damaged.
 */

/**
 * Both apps' way into the store before Room (SPEC R144, R145): the bundled driver, which deletes
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
