package com.hazia.tesoriere.data

/*
 * New in Tesoriere: Sci-Fin's guard took a probe function and a Room driver, and its version read
 * and snapshot opened the store through that driver, for writing.
 */

/**
 * Each platform's way into the store's file before Room opens it (SPEC R144, R145): read-only, and
 * through an open that never deletes, so nothing the guard does can change or remove the files it
 * checks, moves or copies. Room's own drivers are neither: closing a connection that can write folds
 * the write-ahead log in and deletes it, and Android's default open deletes a file it finds damaged.
 */
interface RawStore {
    /** The store's quick check and schema version, or what kept SQLite from reading them. Never throws. */
    fun probe(path: String): Probe

    /** The store at [path] written whole into the new file [out] by `VACUUM INTO`; throws when it cannot be. */
    fun vacuumInto(path: String, out: String)
}

/** What [RawStore.probe] found. */
sealed interface Probe {
    /** SQLite read the store: its quick check's rows and its `user_version`, write-ahead log included. */
    data class Read(val check: List<String>, val version: Int) : Probe

    /** SQLite reported the file as damaged or as not a database: the one failure that moves it (SPEC R144). */
    data object Damaged : Probe

    /** Another connection holds the store locked. */
    data object Busy : Probe

    /** Any other failure: the file could not be opened or read, the disk failed, SQLite did not load. */
    data object Failed : Probe
}
