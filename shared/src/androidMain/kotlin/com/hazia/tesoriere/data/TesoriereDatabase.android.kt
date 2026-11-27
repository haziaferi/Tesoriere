package com.hazia.tesoriere.data

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.os.Build
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.hazia.tesoriere.engine.SystemClock
import com.hazia.tesoriere.model.SCHEMA_VERSION
import kotlinx.datetime.LocalDate

/*
 * Ported from Sci-Fin native/shared/src/androidMain/kotlin/app/scifin/data/SciFinDatabase.android.kt at 2892110.
 *
 * Changed from Sci-Fin: the file is `tesoriere.db`; the store opens only through the guard (SPEC
 * R144, R145), whose outcome is [StoreOpening], with a code for each store not opened, where Sci-Fin
 * built the database whatever the guard and the snapshot gave. Before Room, the store is reached only
 * through [FrameworkRawStore]: read-only, with an error handler that does nothing, for the quick
 * check, the version and the snapshot alike; Sci-Fin's version read and snapshot went through
 * `AndroidSQLiteDriver`, whose default handler deletes a damaged file and whose close can fold the
 * log in. Notices are answered one by one with [clearTesoriereDamageNotice]. Sci-Fin's local-copies
 * erase belongs with the app lock: [eraseTesoriere], the forgotten-PIN erase. No log of sentences. Section references are replaced by SPEC rule
 * numbers.
 */

private const val DB_NAME = "tesoriere.db"

/**
 * Android bootstrap (SPEC R141), in app-private storage: it survives an upgrade, not an uninstall
 * or Clear Data; what leaves the device is the backup file (SPEC R130). The guard (SPEC R144, R145)
 * runs before any open through `AndroidSQLiteDriver`, whose default handler deletes a damaged file.
 */
suspend fun openTesoriereDatabase(context: Context, today: LocalDate = SystemClock.today()): StoreOpening<TesoriereDatabase> {
    val app = context.applicationContext
    val file = app.getDatabasePath(DB_NAME)
    file.parentFile?.mkdirs()
    return Integrity.open(file, SCHEMA_VERSION, FrameworkRawStore, deviceName(), today) {
        Room.databaseBuilder<TesoriereDatabase>(context = app, name = DB_NAME).openFirstConnection(AndroidSQLiteDriver())
    }
}

/** The user answered the notice of the copy [keptAs]: it goes, and the copy stays (SPEC R144). */
fun clearTesoriereDamageNotice(context: Context, keptAs: String): Boolean =
    Integrity.clearNotice(context.applicationContext.getDatabasePath(DB_NAME), keptAs)

/** The user answered the notices whose file could no longer be read (SPEC R144); how many went. */
fun clearTesoriereUnreadableNotices(context: Context): Int =
    Integrity.clearUnreadableMarkers(context.applicationContext.getDatabasePath(DB_NAME))

/**
 * The forgotten-PIN erase (SPEC R147) of the store [repo] reads, `tesoriere.db` in app-private
 * storage, then its `snapshots` folder and the damage notices beside it; what was left, by code.
 */
suspend fun eraseTesoriere(context: Context, repo: ProfileRepository): Erased =
    Erase.everything(repo, context.applicationContext.getDatabasePath(DB_NAME))

/**
 * Android's way into the store before Room (SPEC R144, R145): the framework's SQLite, read-only, with
 * an error handler that does nothing, so a damage report throws instead of deleting the file, and
 * closing cannot fold the write-ahead log in nor delete it.
 */
private object FrameworkRawStore : RawStore {
    override fun probe(path: String): Probe = try {
        readOnly(path) { db ->
            val check = db.rawQuery("PRAGMA quick_check", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            val version = db.rawQuery("PRAGMA user_version", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            Probe.Read(check, version)
        }
    } catch (_: SQLiteDatabaseCorruptException) {
        // the framework reports SQLITE_CORRUPT and SQLITE_NOTADB alike as this
        Probe.Damaged
    } catch (_: SQLiteDatabaseLockedException) {
        Probe.Busy
    } catch (_: Exception) {
        Probe.Failed
    } catch (_: LinkageError) {
        Probe.Failed
    }

    override fun vacuumInto(path: String, out: String) {
        readOnly(path) { db -> db.execSQL("VACUUM INTO ?", arrayOf(out)) }
    }

    private fun <T> readOnly(path: String, block: (SQLiteDatabase) -> T): T {
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY, DatabaseErrorHandler { })
        try {
            return block(db)
        } finally {
            db.close()
        }
    }
}

/** Names the copies so two devices writing into one folder cannot collide (SPEC R145). */
private fun deviceName(): String = (Build.MODEL ?: Build.DEVICE ?: "android").take(24)
