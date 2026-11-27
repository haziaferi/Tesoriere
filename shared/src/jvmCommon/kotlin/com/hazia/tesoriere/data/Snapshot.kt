package com.hazia.tesoriere.data

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/*
 * Ported from Sci-Fin native/shared/src/jvmCommon/kotlin/app/scifin/data/Snapshot.kt at 2892110.
 *
 * Changed from Sci-Fin:
 * - [before] gives an [Outcome] instead of a file or null, so the guard can tell a snapshot that
 *   failed from one not needed and keep the migration from running without it (SPEC R145). Sci-Fin
 *   opened the store either way.
 * - The version comes from the guard's read-only probe ([RawStore.probe]) and the copy is written
 *   through [RawStore.vacuumInto]; Sci-Fin opened the store for writing through Room's driver, whose
 *   close folds the write-ahead log in and deletes it, and read the file header as a fallback.
 * - A snapshot is written to a `.part` file, forced to disk and renamed, so a file under the final
 *   name is always whole; Sci-Fin wrote the final name directly, so a crash left a part that was
 *   then kept as the snapshot.
 * - A store of a version above the app's is not snapshotted here: the guard leaves it alone (SPEC
 *   R145). Sci-Fin snapshotted it, since its destructive fallback would then have emptied it.
 * - No log of sentences; the outcome is the record. Names start `tesoriere-`, and a device name with
 *   no letter or digit left is `device`.
 * - Section references are replaced by SPEC rule numbers.
 */

/**
 * SPEC R145: the store is snapshotted before Room opens it at a newer version.
 *
 * The live store is three files, `tesoriere.db` with `-wal` and `-shm`, and the main file can hold
 * almost nothing beside a large write-ahead log, so a copy would catch no rows. `VACUUM INTO` folds
 * the log in and writes one whole, consistent database, through raw SQLite, so it works on a schema
 * Room would refuse, which is the case a snapshot before a migration is for.
 *
 * Snapshots are one file each, written once and never changed, in a `snapshots` folder beside the
 * store, named by version and device so two machines writing into one folder cannot collide. They
 * are an undo on this device; the backup file is what travels (SPEC R130).
 */
object Snapshot {

    /** The folder beside the store that holds the snapshots and the damaged copies (SPEC R144, R145). */
    const val FOLDER = "snapshots"

    /** What [before] did. */
    sealed interface Outcome {
        /** No store, or one already at the app's version or above it: nothing to snapshot. */
        data object NotNeeded : Outcome

        /** The snapshot for the store's version, written now or kept as it was from before. */
        data class Kept(val file: File) : Outcome

        /** The snapshot could not be written: the migration must not run (SPEC R145). */
        data object Failed : Outcome
    }

    /**
     * `tesoriere-v1-DESKTOP.db`: the version and the device, and on purpose no time. One snapshot per
     * version per device, written once: that bounds them, and a good snapshot is never replaced by a
     * later, possibly worse one (SPEC R145).
     */
    internal fun nameFor(version: Int?, device: String): String = "tesoriere-v${version ?: "unknown"}-${deviceTag(device)}.db"

    /**
     * [db], whose stored version is [stored] (null: unknown), snapshotted when that is below
     * [current] or unknown: written to `<name>.part`, a stale one deleted first, forced to disk and
     * only then renamed to its name, so a file of that name is always whole and is kept as it is.
     * Never throws: a snapshot that cannot be written is [Outcome.Failed].
     */
    fun before(db: File, stored: Int?, current: Int, raw: RawStore, device: String): Outcome {
        if (!db.isFile) return Outcome.NotNeeded // a first run has nothing to protect
        if (stored != null && stored >= current) return Outcome.NotNeeded // no migration coming
        val dir = File(db.parentFile, FOLDER)
        val out = File(dir, nameFor(stored, device))
        val part = File(dir, out.name + ".part")
        return runCatching {
            if (out.isFile) return@runCatching Outcome.Kept(out) // written once, never rewritten
            if (!dir.isDirectory && !dir.mkdirs()) return@runCatching Outcome.Failed
            if (part.exists() && !part.delete()) return@runCatching Outcome.Failed
            raw.vacuumInto(db.absolutePath, part.absolutePath)
            FileChannel.open(part.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(part.toPath(), out.toPath(), StandardCopyOption.ATOMIC_MOVE)
            Outcome.Kept(out)
        }.getOrElse {
            // a half-written snapshot is not one: the next opening writes it whole
            runCatching { part.delete() }
            Outcome.Failed
        }
    }
}

/** A device name as a file name keeps it: letters, digits and hyphens, at most 24 (SPEC R144, R145). */
internal fun deviceTag(device: String): String =
    device.filter { it.isLetterOrDigit() || it == '-' }.take(24).ifEmpty { "device" }
