package com.hazia.tesoriere.data

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hazia.tesoriere.engine.SystemClock
import com.hazia.tesoriere.model.SCHEMA_VERSION
import kotlinx.datetime.LocalDate

/*
 * Ported from Sci-Fin native/shared/src/androidMain/kotlin/app/scifin/data/SciFinDatabase.android.kt at 2892110.
 *
 * Changed from Sci-Fin: the file is `tesoriere.db`; the store opens only through the guard (SPEC
 * R144, R145), whose outcome is [StoreOpening], with a code for each store not opened, where Sci-Fin
 * built the database whatever the guard and the snapshot gave. Room and the guard both use the
 * bundled SQLite, as the desktop does: Sci-Fin's framework driver opens with Android's default
 * error handler, which deletes a database file it finds damaged, so damage first met while
 * the app runs would have deleted the store. With the bundled driver nothing deletes it: Room's query
 * throws, and the next start's guard moves the store aside (SPEC R144). Before Room, the store is
 * reached only through [BundledRawStore], read-only. Notices are answered one by one with
 * [clearTesoriereDamageNotice]. Sci-Fin's local-copies erase belongs with the app lock:
 * [eraseTesoriere], the forgotten-PIN erase. No log of sentences. Section references are replaced by
 * SPEC rule numbers.
 */

private const val DB_NAME = "tesoriere.db"

/**
 * Android bootstrap (SPEC R141), in app-private storage: it survives an upgrade, not an uninstall
 * or Clear Data; what leaves the device is the backup file (SPEC R130). The guard (SPEC R144, R145)
 * runs before Room's first open, and both use the bundled driver, which deletes nothing.
 */
suspend fun openTesoriereDatabase(context: Context, today: LocalDate = SystemClock.today()): StoreOpening<TesoriereDatabase> {
    val app = context.applicationContext
    val file = app.getDatabasePath(DB_NAME)
    file.parentFile?.mkdirs()
    return Integrity.open(file, SCHEMA_VERSION, BundledRawStore, deviceName(), today) {
        Room.databaseBuilder<TesoriereDatabase>(context = app, name = DB_NAME).openFirstConnection(BundledSQLiteDriver())
    }
}

/**
 * The user answered the notice of the copy [keptAs]: it goes, and the copy stays (SPEC R144).
 * A thin wrapper: it only finds the store's file; [Integrity.clearNotice] (jvmCommon) holds the
 * logic and is tested on the desktop.
 */
fun clearTesoriereDamageNotice(context: Context, keptAs: String): Boolean =
    Integrity.clearNotice(context.applicationContext.getDatabasePath(DB_NAME), keptAs)

/**
 * The user answered the notices whose file could no longer be read (SPEC R144); how many went.
 * A thin wrapper over [Integrity.clearUnreadableMarkers] (jvmCommon), tested on the desktop.
 */
fun clearTesoriereUnreadableNotices(context: Context): Int =
    Integrity.clearUnreadableMarkers(context.applicationContext.getDatabasePath(DB_NAME))

/**
 * The forgotten-PIN erase (SPEC R147) of the store [repo] reads, `tesoriere.db` in app-private
 * storage, then its `snapshots` folder and the damage notices beside it; what was left, by code.
 * A thin wrapper over [Erase.everything] (jvmCommon), tested on the desktop.
 */
suspend fun eraseTesoriere(context: Context, repo: ProfileRepository): Erased =
    Erase.everything(repo, context.applicationContext.getDatabasePath(DB_NAME))

/** Names the copies so two devices writing into one folder cannot collide (SPEC R145). */
private fun deviceName(): String = (Build.MODEL ?: Build.DEVICE ?: "android").take(24)
