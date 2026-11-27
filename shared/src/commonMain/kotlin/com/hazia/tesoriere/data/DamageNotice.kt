package com.hazia.tesoriere.data

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/app/DamageNotice.kt at 2892110.
 *
 * Changed from Sci-Fin:
 * - A record of codes and figures, not an interface over a marker: the kept copy's name, its size,
 *   the day it was moved and why (SPEC R144). Sci-Fin's marker held the name alone, and its screen
 *   held the words, which are the design pass's (SPEC R13, R16).
 * - Notices are kept as a list, one per copy, each answered on its own with `Integrity.clearNotice`;
 *   a notice file that cannot be read stays owed ([DamageNotices.unreadableMarkers]). Sci-Fin kept
 *   one name, which a second copy replaced, and its `None` stand-in is [DamageNotices.NONE].
 * - The outcome of opening the store, [StoreOpening], and its codes, [StoreProblem], are new: Sci-Fin
 *   opened whatever the guard and the snapshot gave, and kept the destructive fallback beneath (SPEC
 *   R145).
 */

/** Why a store was moved aside (SPEC R144). */
@Serializable
enum class DamageReason {
    /** SQLite could not open the file at all. */
    UNREADABLE,

    /** SQLite opened it, and its quick check reported a problem. */
    CHECK_FAILED,

    /** A write-ahead log or shared-memory file with no store beside it, as an unfinished move leaves them. */
    LEFTOVER,
}

/**
 * A store SQLite could not read was moved aside before the app opened it, and the user has not yet
 * answered (SPEC R144). [keptAs] is the copy's file name in the `snapshots` folder beside the store,
 * with its write-ahead log and shared-memory files, when there were any, under the same name plus
 * `-wal` and `-shm`; [bytes] is the main file's size, 0 when only the log files were left.
 */
@Serializable
data class DamageNotice(val keptAs: String, val bytes: Long, val movedOn: LocalDate, val reason: DamageReason)

/**
 * Every notice still owed (SPEC R144): one per copy moved aside and not yet answered, oldest first,
 * and [unreadableMarkers], the count of kept notice files that can no longer be read, each owed too.
 */
data class DamageNotices(val notices: List<DamageNotice>, val unreadableMarkers: Int) {
    val owed: Boolean get() = notices.isNotEmpty() || unreadableMarkers > 0

    companion object {
        val NONE = DamageNotices(emptyList(), 0)
    }
}

/** Why the store was not opened (SPEC R144, R145). Nothing was deleted in any of them. */
enum class StoreProblem {
    /** Damaged, or left by an unfinished move, and it could not be moved aside whole: not opened. */
    NOT_MOVED,

    /** Held locked by another connection: not checked, not opened. */
    BUSY,

    /** SQLite could not read it for a reason other than damage: left in place, not opened. */
    NOT_CHECKED,

    /** Of a schema version above the app's: left as it was, not moved. */
    NEWER_SCHEMA,

    /** Of a version below the app's, and its snapshot could not be written: not migrated. */
    SNAPSHOT_FAILED,

    /** Of a version below the app's, and its migration failed: the store and its snapshot as they were. */
    MIGRATION_FAILED,

    /** Of the app's version, or fresh, and it would not open: left as it was. */
    OPEN_FAILED,
}

/** The outcome of opening the store through the guard (SPEC R144, R145). */
sealed interface StoreOpening<out T> {
    /**
     * The store, open. [damage] holds the notices still owed, from this opening or earlier ones;
     * [snapshot] is the name of the snapshot in the `snapshots` folder taken before its migration,
     * or null when it needed none.
     */
    data class Opened<T>(val store: T, val damage: DamageNotices, val snapshot: String?) : StoreOpening<T>

    /** Not opened, with the code, the store's schema version when it was read, and the notices owed. */
    data class NotOpened(val problem: StoreProblem, val storedVersion: Int?, val damage: DamageNotices) : StoreOpening<Nothing>
}
