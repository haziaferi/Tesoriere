package com.hazia.tesoriere.data

import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/*
 * Ported from Sci-Fin native/shared/src/jvmCommon/kotlin/app/scifin/data/Integrity.kt at 2892110.
 *
 * Changed from Sci-Fin:
 * - [open] is new: the whole guard in the order SPEC R144 and R145 give, which Sci-Fin's builders
 *   spelled out each for itself, and which opened the store whatever the snapshot gave. A store of a
 *   version above the app's is left alone with NEWER_SCHEMA, a snapshot that fails stops the
 *   migration, and a migration or open that fails gives a code; nothing is ever deleted to open.
 * - The store is reached only through a [RawStore], read-only and never deleting. Only SQLite's
 *   report of damage or of no database, or a quick check that is not ok, moves it; a lock gives BUSY
 *   and any other failure NOT_CHECKED, and the store stays where it is, not opened. Sci-Fin moved a
 *   store on any failure but a lock, and opened a locked one unchecked.
 * - A move is crash-safe: its notice is kept, marked unfinished, before the first file moves; the
 *   store's files move together or go back; and before anything else opens the store an unfinished
 *   move is finished, and log files with no store beside them are moved aside (LEFTOVER), or the
 *   store is not opened (NOT_MOVED). Sci-Fin wrote its marker after the main file had gone.
 * - The copy's name takes the day and, for a second copy that day, `-2`, `-3`, so no later copy
 *   overwrites one; Sci-Fin's took the time of day, and a second in the same second failed. A move
 *   never replaces a file already there.
 * - The notices are a list, one per copy, each answered on its own ([clearNotice]), written whole to
 *   a temporary file and then renamed over the last; a notice file that cannot be read stays owed,
 *   kept aside under its own name when a new notice is written. Sci-Fin's marker held one name.
 * - No log of sentences. Sci-Fin's `copies` (the forgotten-PIN erase) is Erase.kt's (SPEC R147).
 * - Section references are replaced by SPEC rule numbers.
 */

/**
 * SPEC R144: a store SQLite cannot read is moved aside, whole, before anything else opens it.
 *
 * It has to come first: Room's `AndroidSQLiteDriver` opens through Android's default error handler,
 * which deletes a database that reports damage on open; `BundledSQLiteDriver` on the desktop deletes
 * nothing, but it throws. Each platform's [RawStore] opens read-only and cannot delete.
 *
 * Not covered, and said so it is not assumed: damage that first shows while the app runs meets Room's
 * own connection, and on Android its default handler deletes the store.
 */
object Integrity {

    internal const val MARKER = "tesoriere.damaged"
    internal const val UNREADABLE_MARKER = "tesoriere.damaged.unreadable"
    private val SUFFIXES = listOf("", "-wal", "-shm")
    private val json = Json { ignoreUnknownKeys = true }
    private val entries = ListSerializer(Entry.serializer())

    /** A copy's notice as kept beside the store: [done] once every file of the store has moved. */
    @Serializable
    internal data class Entry(val notice: DamageNotice, val done: Boolean)

    /** The notice file as read: its entries (none when it is absent), or null when it cannot be read. */
    private fun read(db: File): List<Entry>? {
        val marker = File(db.parentFile, MARKER)
        if (!marker.exists()) return emptyList()
        return runCatching { json.decodeFromString(entries, marker.readText()) }.getOrNull()
    }

    /**
     * [list] written as the notice file, whole: to a temporary file, forced to disk, then renamed over
     * the last. A notice file that cannot be read is first kept aside under a name of its own, so it
     * stays owed. False, with the notice file as it was, when that cannot be done.
     */
    private fun write(db: File, list: List<Entry>): Boolean = runCatching {
        val marker = File(db.parentFile, MARKER)
        if (marker.exists() && read(db) == null) {
            val aside = freeName(db.parentFile, UNREADABLE_MARKER, "") { it.exists() }
            Files.move(marker.toPath(), aside.toPath())
        }
        val part = File(marker.path + ".part")
        FileOutputStream(part).use { out ->
            out.write(json.encodeToString(entries, list).encodeToByteArray())
            out.fd.sync()
        }
        Files.move(part.toPath(), marker.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }.isSuccess

    /** `<base><ext>`, then `<base>-2<ext>`, `-3`: the first that [taken] does not hold. */
    private fun freeName(dir: File?, base: String, ext: String, taken: (File) -> Boolean): File =
        generateSequence(1) { it + 1 }.map { n -> File(dir, if (n == 1) "$base$ext" else "$base-$n$ext") }.first { !taken(it) }

    /**
     * Opens the store [db] through the guard (SPEC R144, R145): an unfinished move finished, damage
     * moved aside, a store of a version above [current] left alone, a snapshot before a migration,
     * then [open], which builds the store and runs its migrations, closing what it opened and
     * throwing when they fail. Never throws on what the files hold. Internal: the app opens the store
     * through each platform's openTesoriereDatabase, which gives the guard its RawStore.
     */
    internal suspend fun <T> open(db: File, current: Int, raw: RawStore, device: String, today: LocalDate, open: suspend () -> T): StoreOpening<T> =
        open(db, current, raw, device, today, ::move, open)

    /** [open] with the file move given, so a test can make one fail. */
    internal suspend fun <T> open(
        db: File,
        current: Int,
        raw: RawStore,
        device: String,
        today: LocalDate,
        move: (File, File) -> Boolean,
        open: suspend () -> T,
    ): StoreOpening<T> {
        require(current >= 1) { "schema versions start at 1, got $current" }
        fun refused(problem: StoreProblem, stored: Int? = null) = StoreOpening.NotOpened(problem, stored, notices(db))

        if (!recover(db, device, today, move)) return refused(StoreProblem.NOT_MOVED)
        var stored: Int? = null
        if (db.isFile) {
            val reason = when (val p = raw.probe(db.absolutePath)) {
                is Probe.Read -> if (p.check == listOf("ok")) {
                    stored = p.version
                    null
                } else {
                    DamageReason.CHECK_FAILED
                }
                Probe.Damaged -> DamageReason.UNREADABLE
                Probe.Busy -> return refused(StoreProblem.BUSY)
                Probe.Failed -> return refused(StoreProblem.NOT_CHECKED)
            }
            if (reason != null && moveAside(db, device, today, reason, move) == null) return refused(StoreProblem.NOT_MOVED)
        }
        val version = stored
        if (version != null && version > current) return refused(StoreProblem.NEWER_SCHEMA, version)
        val snapshot = when (val s = Snapshot.before(db, version, current, raw, device)) {
            Snapshot.Outcome.NotNeeded -> null
            is Snapshot.Outcome.Kept -> s.file
            Snapshot.Outcome.Failed -> return refused(StoreProblem.SNAPSHOT_FAILED, version)
        }
        val store = try {
            open()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // a migration runs in one transaction, rolled back when it fails: the store is as it was
            return refused(if (snapshot != null) StoreProblem.MIGRATION_FAILED else StoreProblem.OPEN_FAILED, version)
        }
        return StoreOpening.Opened(store, notices(db), snapshot?.name)
    }

    /**
     * Before anything opens the store: each move left unfinished is finished, and a write-ahead log or
     * shared-memory file with no store beside it is moved aside, since a fresh store would otherwise
     * drop it or read it as its own. False when either cannot be done: the store must not open.
     */
    private fun recover(db: File, device: String, today: LocalDate, move: (File, File) -> Boolean): Boolean = runCatching {
        val list = read(db)
        if (list != null && list.any { !it.done }) {
            val dir = File(db.parentFile, Snapshot.FOLDER)
            for (e in list.filter { !it.done }) {
                val kept = File(dir, e.notice.keptAs)
                for (suffix in SUFFIXES) {
                    val from = File(db.path + suffix)
                    if (from.exists() && !move(from, File(kept.path + suffix))) return@runCatching false
                }
            }
            if (!write(db, list.map { it.copy(done = true) })) return@runCatching false
        }
        val leftover = !db.exists() && (File(db.path + "-wal").exists() || File(db.path + "-shm").exists())
        !leftover || moveAside(db, device, today, DamageReason.LEFTOVER, move) != null
    }.getOrDefault(false)

    /**
     * The store's files moved together into the `snapshots` folder under a name no copy holds, its
     * notice kept, marked unfinished, before the first moves; null when they cannot all move, with
     * those that moved put back and the notice withdrawn. A move whose files cannot go back keeps its
     * unfinished notice, and the next start finishes it.
     */
    private fun moveAside(db: File, device: String, today: LocalDate, reason: DamageReason, move: (File, File) -> Boolean): DamageNotice? = runCatching {
        val dir = File(db.parentFile, Snapshot.FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) return@runCatching null
        val before = read(db).orEmpty()
        val names = before.mapTo(HashSet()) { it.notice.keptAs }
        val kept = freeName(dir, "tesoriere-damaged-$today-${deviceTag(device)}", ".db") { k ->
            k.name in names || SUFFIXES.any { File(k.path + it).exists() }
        }
        val notice = DamageNotice(kept.name, if (db.isFile) db.length() else 0, today, reason)
        if (!write(db, before + Entry(notice, done = false))) return@runCatching null
        val moved = mutableListOf<Pair<File, File>>()
        for (suffix in SUFFIXES) {
            val from = File(db.path + suffix)
            if (!from.exists()) continue
            val to = File(kept.path + suffix)
            if (!move(from, to)) {
                val back = moved.asReversed().map { (f, t) -> move(t, f) }
                if (back.all { it }) write(db, before)
                return@runCatching null
            }
            moved += from to to
        }
        if (!write(db, before + Entry(notice, done = true))) return@runCatching null
        notice
    }.getOrNull()

    /**
     * [from] moved to [to], renamed where it can be, else copied and the source deleted, which the
     * platform does as one step; false when [to] is there already, which is never replaced or
     * deleted, or when the move fails.
     */
    internal fun move(from: File, to: File): Boolean {
        if (to.exists()) return false
        return try {
            Files.move(from.toPath(), to.toPath())
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Every notice still owed beside [db] (SPEC R144), an unreadable notice file counted as one owed. */
    fun notices(db: File): DamageNotices {
        val list = read(db)
        val aside = db.parentFile?.listFiles { f -> f.name.startsWith(UNREADABLE_MARKER) }.orEmpty().size
        return DamageNotices(list.orEmpty().filter { it.done }.map { it.notice }, aside + if (list == null) 1 else 0)
    }

    /** The user answered the notice of the copy [keptAs]: it goes, and the copy stays (SPEC R144). True when it was owed. */
    fun clearNotice(db: File, keptAs: String): Boolean {
        val list = read(db) ?: return false
        if (list.none { it.done && it.notice.keptAs == keptAs }) return false
        return write(db, list.filterNot { it.done && it.notice.keptAs == keptAs })
    }

    /** The user answered the notices whose file could not be read (SPEC R144): how many went. */
    fun clearUnreadableMarkers(db: File): Int {
        val dir = db.parentFile ?: return 0
        var n = dir.listFiles { f -> f.name.startsWith(UNREADABLE_MARKER) }.orEmpty().count { runCatching { it.delete() }.getOrDefault(false) }
        val marker = File(dir, MARKER)
        if (marker.exists() && read(db) == null && runCatching { marker.delete() }.getOrDefault(false)) n++
        return n
    }
}
