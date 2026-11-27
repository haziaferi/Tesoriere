package com.hazia.tesoriere.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.hazia.tesoriere.engine.Backup
import com.hazia.tesoriere.engine.BackupFile
import com.hazia.tesoriere.engine.BackupProblem
import com.hazia.tesoriere.engine.BackupResult
import com.hazia.tesoriere.engine.Clock
import com.hazia.tesoriere.engine.LockState
import com.hazia.tesoriere.engine.Pin
import com.hazia.tesoriere.engine.RestoreProblem
import com.hazia.tesoriere.engine.SciFinImport
import com.hazia.tesoriere.engine.SciFinRead
import com.hazia.tesoriere.engine.Trash
import com.hazia.tesoriere.engine.TrashPayload
import com.hazia.tesoriere.model.AppLock
import com.hazia.tesoriere.model.AppState
import com.hazia.tesoriere.model.CopySchedule
import com.hazia.tesoriere.model.Profile
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.TrashItem
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/*
 * Ported from the persistence part of Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/app/AppStore.kt
 * (with app/AppSession.kt's activeOrFirst, setActive and createProfile) at 2892110.
 *
 * Changed from Sci-Fin:
 * - Only the storage is ported: no screen state, routes, sheets, notifications or month cycle (SPEC
 *   R04); opening a Profile runs the month cycle (SPEC R70), which is the app's open, in phase 2.
 * - A refused step gives a code ([ProfileProblem], SPEC R140, R141), not silence: a write of a
 *   Profile deleted since it was read, or of an id another Profile holds, writes nothing. [update]
 *   and [deleteToTrash] run an engine step on the Profile as stored, in the transaction of its write,
 *   so no copy read earlier can bring back what a delete took; the writes of a copy are internal, for tests.
 * - Profiles are switched on once the device holds more than one, by a Profile created, restored
 *   from the Trash or added from Sci-Fin (SPEC R136, R140); Sci-Fin had no switch.
 * - A delete into the Trash and a restore out of it write the Profile and the Trash in one
 *   transaction (SPEC R141); Sci-Fin wrote them one after the other.
 * - The Trash keeps payments and receipt settings; deleting the receipt Profile frees the folder,
 *   and so does a restore of the Profile take it back only while no other Profile holds one (SPEC R126).
 * - An export checks its own file reads back whole before it is given (SPEC R130, R131), and names
 *   each record the store could not read, which the file cannot carry (SPEC R141).
 * - The app lock and the copy schedule, device-only rows never in a backup, are read and written
 *   here ([lock], [setLock], [clearLock], [copySchedule], [setCopySchedule]), as is the Trash
 *   notice's mark ([markWarned]); a lock row that cannot be read is DAMAGED (SPEC R146).
 * - The forgotten-PIN erase's store part, AppStore.eraseEverything's, gives a code ([EraseProblem],
 *   SPEC R147) where Sci-Fin passed over a failed VACUUM.
 * - Section references are replaced by SPEC rule numbers.
 */

/** Why a Profile step or write is refused (SPEC R140, R141); how it reads is the design pass's (SPEC R16). */
enum class ProfileProblem {
    /** No Profile has that id: never created, or deleted since it was read. */
    NO_SUCH_PROFILE,

    /** A name of spaces only. */
    BLANK_NAME,

    /** The only Profile on the device cannot be deleted. */
    LAST_PROFILE,

    /** A record written holds an id another Profile's record holds (SPEC R20). */
    ID_IN_USE,

    /** The Profile written holds one id twice in one kind of record (SPEC R141). */
    ID_TWICE,

    /** A shift's hours, a row's hours worked, or an Account's APR or interest rate is infinity or not a number (SPEC R141). */
    NOT_FINITE,

    /**
     * The Profile to delete holds records the store cannot read (SPEC R141), which the delete would
     * take with it: the user is told first, and the app deletes again once they agree (SPEC R140).
     */
    UNREADABLE_RECORDS,
}

/** What went wrong in the forgotten-PIN erase (SPEC R147); how it reads is the design pass's (SPEC R16). */
enum class EraseProblem {
    /** The store's transaction failed: nothing was erased, and no file was touched. */
    STORE_NOT_ERASED,

    /** The rows are gone, but the store file could not be rewritten, so their old pages may remain in it. */
    STORE_NOT_COMPACTED,

    /** A file in the `snapshots` folder or a notice file could not be deleted; Erase names each. */
    FILE_NOT_DELETED,
}

/** What [ProfileRepository.update] gives. */
sealed interface Update {
    /** The step's Profile, written. */
    data class Saved(val data: ProfileData) : Update

    /** Nothing written. */
    data class Refused(val problem: ProfileProblem) : Update
}

/** What deleting a Profile gives (SPEC R126, R140). */
sealed interface ProfileDelete {
    /** The Profile is in the Trash. */
    data object Deleted : ProfileDelete

    /** Nothing written: why, and for UNREADABLE_RECORDS the records the delete would take for good (SPEC R141). */
    data class Refused(val problem: ProfileProblem, val unreadable: List<UnreadableRecord> = emptyList()) : ProfileDelete
}

/** What a restore from the Trash gives (SPEC R122, R126). */
sealed interface TrashRestore {
    /** Put back: the Profile as the restore left it, saved with the item gone from the Trash. */
    data class Restored(val data: ProfileData) : TrashRestore

    /** Refused by the engine, nothing written (SPEC R122). */
    data class Refused(val problem: RestoreProblem) : TrashRestore

    /**
     * No such item, a payload that cannot be read, its Profile not on the device, or a Profile whose
     * id is on it again: nothing written.
     */
    data object NotRestorable : TrashRestore

    /**
     * The write of what the restore puts back refused (SPEC R141): ID_IN_USE for a record whose id
     * another Profile holds, ID_TWICE for one id twice in one kind of record; nothing written, the
     * item kept in the Trash.
     */
    data class WriteRefused(val problem: ProfileProblem) : TrashRestore
}

/**
 * The app lock as the store holds it (SPEC R146): its [state], and its [row] while one is kept, for
 * Pin.matches and the count of wrong PINs. A DAMAGED row matches no PIN, so the app opens and asks
 * for a new one.
 */
data class StoredLock(val state: LockState, val row: AppLock?)

/**
 * The kinds of record the store keeps for a Profile that can fail to read (SPEC R141). A Goal's and a
 * category's only fields that can fail, a Goal's mode and day, read as nothing, so neither is a kind.
 */
enum class StoredKind { RECURRING_EXPENSE, ACCOUNT, TRANSACTION, SHIFT, PAYMENT, SETTINGS, IMPORT_MEMORY, RECEIPT_SETTINGS }

/**
 * A record of Profile [profileId] that the store holds but cannot read (SPEC R141), which no backup
 * file can carry (SPEC R130): [id] names a record of a list, and is null for the Profile's settings,
 * import memory or receipt settings, one per Profile.
 */
data class UnreadableRecord(val profileId: String, val kind: StoredKind, val id: String?)

/** What an export gives (SPEC R130). */
sealed interface Export {
    /**
     * The file and its text, which reads back whole (SPEC R131), how many records and Trash items
     * were left out of it because they would not, and how many were repaired so they would (SPEC
     * R130); and, named apart, each record the store holds but could not read, which the file lacks
     * (SPEC R141): an export with any of them is not a whole copy of the device, and says so.
     */
    data class Ok(val file: BackupFile, val text: String, val leftOut: Int, val repaired: Int, val unreadable: List<UnreadableRecord>) : Export

    /** The file would not read back: why, and no file. */
    data class Refused(val problem: BackupProblem) : Export

    /** No Profile to export. */
    data object Empty : Export
}

/**
 * The store's one door for the engine (SPEC R141): a Profile read whole and written whole, each
 * write in one transaction, and the Profile steps of SPEC R140. [clock] dates the Trash's items
 * (SPEC R121) and a new Profile's first month (SPEC R70); [ids] gives new Trash items and Sci-Fin's
 * records their ids.
 */
class ProfileRepository(private val db: TesoriereDatabase, private val clock: Clock, private val ids: () -> String) {

    private suspend fun <T> write(block: suspend (TesoriereDao) -> T): T =
        db.useWriterConnection { c -> c.immediateTransaction { block(db.dao()) } }

    /** Profile [profileId] whole, or null when there is none; nothing stored makes it throw (SPEC R141). */
    suspend fun load(profileId: String): ProfileData? = db.dao().load(profileId)

    /**
     * [d] written whole in one transaction; refused, writing nothing, while its Profile is not on the
     * device. Internal, for tests: a copy read earlier may hold what a delete took or lack what a
     * restore put back, so every write from outside starts from the Profile as stored, through
     * [update], [updateWithApp], [deleteToTrash] or [restore] (SPEC R141).
     */
    internal suspend fun save(d: ProfileData): ProfileProblem? = write { dao -> saveExisting(dao, d) }

    /** [app] and [d] in one transaction. Internal, for tests, as [save]; [updateWithApp] is the way from outside. */
    internal suspend fun save(app: AppState, d: ProfileData): ProfileProblem? = write { dao ->
        saveExisting(dao, d) ?: run { dao.upsertAppState(app); null }
    }

    /**
     * Runs [step] on Profile [profileId] as stored and writes its outcome, in one transaction (SPEC
     * R141): the way to write an engine step, since a copy read earlier may hold what a delete took.
     */
    suspend fun update(profileId: String, step: (ProfileData) -> ProfileData): Update = write { dao ->
        val d = dao.load(profileId) ?: return@write Update.Refused(ProfileProblem.NO_SUCH_PROFILE)
        val after = step(d)
        saveExisting(dao, after)?.let { Update.Refused(it) } ?: Update.Saved(after)
    }

    /**
     * [update] for a step that changes the app's state too, such as choosing the receipt folder (SPEC
     * R112): both read as stored and written in one transaction. With no app state yet, the step gets
     * one naming [profileId] open.
     */
    suspend fun updateWithApp(profileId: String, step: (AppState, ProfileData) -> Pair<AppState, ProfileData>): Update = write { dao ->
        val d = dao.load(profileId) ?: return@write Update.Refused(ProfileProblem.NO_SUCH_PROFILE)
        val (app, after) = step(dao.appState() ?: AppState(activeProfileId = profileId), d)
        saveExisting(dao, after)?.let { Update.Refused(it) } ?: run { dao.upsertAppState(app); Update.Saved(after) }
    }

    private suspend fun saveExisting(dao: TesoriereDao, d: ProfileData): ProfileProblem? =
        if (dao.profile(d.profileId) == null) ProfileProblem.NO_SUCH_PROFILE else dao.save(d)

    /** Every Profile, by name. */
    suspend fun profiles(): List<Profile> = db.dao().profiles()

    suspend fun appState(): AppState? = db.dao().appState()

    /**
     * The open Profile; when the Profile the app keeps open is gone, the first by name, made the
     * open one, so the app's state never names a Profile that is not there. Null with no Profile.
     */
    suspend fun open(): ProfileData? = write { dao ->
        val app = dao.appState()
        app?.let { dao.load(it.activeProfileId) } ?: dao.profiles().firstOrNull()?.let { first ->
            dao.load(first.id)?.also { dao.upsertAppState((app ?: AppState(activeProfileId = first.id)).copy(activeProfileId = first.id)) }
        }
    }

    /**
     * A fresh Profile named [name], trimmed, with id [id], created today and opened (SPEC R140);
     * Profiles are switched on when the device then holds more than one. An id already on the
     * device gives that Profile back unchanged.
     */
    suspend fun createProfile(name: String, id: String): ProfileData = write { dao ->
        dao.load(id)?.let { return@write it }
        val d = ProfileData.fresh(id, name.trim(), createdOn = clock.today())
        // a fresh Profile holds no record, so no id of it can be another Profile's
        dao.save(d)
        val app = dao.appState()
        val many = dao.profiles().size > 1
        dao.upsertAppState((app ?: AppState(activeProfileId = id)).copy(activeProfileId = id, profilesEnabled = app?.profilesEnabled == true || many))
        d
    }

    /** Opens Profile [profileId] (SPEC R140). */
    suspend fun switchTo(profileId: String): ProfileProblem? = write { dao ->
        if (dao.profile(profileId) == null) return@write ProfileProblem.NO_SUCH_PROFILE
        dao.upsertAppState((dao.appState() ?: AppState(activeProfileId = profileId)).copy(activeProfileId = profileId))
        null
    }

    /** Renames Profile [profileId] to [name], trimmed (SPEC R140). */
    suspend fun renameProfile(profileId: String, name: String): ProfileProblem? = write { dao ->
        val p = dao.profile(profileId) ?: return@write ProfileProblem.NO_SUCH_PROFILE
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@write ProfileProblem.BLANK_NAME
        dao.upsertProfile(p.copy(name = trimmed))
        null
    }

    /**
     * Deletes Profile [profileId] whole to the Trash (SPEC R126, R140), in one transaction: its item
     * dated today, its records gone, the receipt folder freed when it held it (SPEC R112), and the
     * first remaining Profile by name opened when it was the open one. Never the last Profile.
     * While the Profile holds records the store cannot read (SPEC R141), which no Trash item can
     * hold and the delete would take for good, nothing is written and they are named,
     * UNREADABLE_RECORDS; once the user has been told and agrees, the app deletes again with
     * [agreedToDelete], the very list the refusal named. A list that is not the store's at the
     * delete, read in the same transaction, is refused again with the store's (as Backup.apply, SPEC R143).
     */
    suspend fun deleteProfile(profileId: String, agreedToDelete: List<UnreadableRecord> = emptyList()): ProfileDelete = write { dao ->
        val d = dao.load(profileId) ?: return@write ProfileDelete.Refused(ProfileProblem.NO_SUCH_PROFILE)
        val remaining = dao.profiles().filter { it.id != profileId }
        if (remaining.isEmpty()) return@write ProfileDelete.Refused(ProfileProblem.LAST_PROFILE)
        val unreadable = dao.unreadableRecords(profileId)
        if (unreadable.isNotEmpty() && unreadable.toSet() != agreedToDelete.toSet()) {
            return@write ProfileDelete.Refused(ProfileProblem.UNREADABLE_RECORDS, unreadable)
        }
        val app = dao.appState() ?: AppState(activeProfileId = remaining.first().id)
        val (freed, payload) = Trash.deleteProfile(app, d)
        dao.insertTrashItem(TrashItem(ids(), profileId, PROFILE_KIND, d.profile.name, clock.today().toString(), Trash.encode(payload)))
        dao.deleteProfileWhole(profileId)
        dao.upsertAppState(freed.copy(activeProfileId = if (freed.activeProfileId == profileId) remaining.first().id else freed.activeProfileId))
        ProfileDelete.Deleted
    }

    // ---- the Trash ------------------------------------------------------------------------------

    /** Every Trash item, newest first. */
    suspend fun trash(): List<TrashItem> = db.dao().trash()

    /**
     * An engine delete's outcome (Trash.deleteGoal and the rest), written in one transaction (SPEC
     * R141): [after], the Profile the delete left, and a Trash item of [kind] and [name] holding
     * [payload], dated today (SPEC R121). The item is returned; null, writing nothing, when [save]
     * would refuse [after]. Internal, for tests, as [save]: [deleteToTrash] runs the delete on the
     * Profile as stored, the way from outside.
     */
    internal suspend fun saveDeleted(after: ProfileData, kind: String, name: String, payload: TrashPayload): TrashItem? = write { dao ->
        saved(dao, after, kind, name, payload)
    }

    /**
     * Runs the delete [step] (Trash.deleteGoal and the rest) on Profile [profileId] as stored and
     * writes its Profile and its Trash item in one transaction (SPEC R121, R141). Null, writing
     * nothing, when the Profile is gone, [step] deletes nothing, or the write is refused.
     */
    suspend fun deleteToTrash(
        profileId: String, kind: String, name: String, step: (ProfileData) -> Pair<ProfileData, TrashPayload>?,
    ): TrashItem? = write { dao ->
        val d = dao.load(profileId) ?: return@write null
        val (after, payload) = step(d) ?: return@write null
        saved(dao, after, kind, name, payload)
    }

    private suspend fun saved(dao: TesoriereDao, after: ProfileData, kind: String, name: String, payload: TrashPayload): TrashItem? {
        if (saveExisting(dao, after) != null) return null
        val item = TrashItem(ids(), after.profileId, kind, name, clock.today().toString(), Trash.encode(payload))
        dao.insertTrashItem(item)
        return item
    }

    /**
     * Restores Trash item [itemId] (SPEC R122, R126) and writes the outcome in one transaction
     * (SPEC R141): the restored Profile and the item's removal together, so the item never stays in
     * the Trash beside what it put back. A Profile comes back as one more Profile, not opened, the
     * receipt folder with it while no other Profile holds one, and switches Profiles on. Any other
     * item goes back into its own Profile through Trash.restore, with the choices given; a refusal
     * writes nothing, and one of the write gives its code (WriteRefused, SPEC R141).
     */
    suspend fun restore(
        itemId: String, replacementCategoryId: String? = null, pairWith: String? = null, differenceCategoryId: String? = null,
    ): TrashRestore = write { dao ->
        val item = dao.trashItem(itemId) ?: return@write TrashRestore.NotRestorable
        val p = Trash.decode(item.payload) ?: return@write TrashRestore.NotRestorable
        p.profile?.let { tp ->
            if (dao.profile(tp.profile.id) != null) return@write TrashRestore.NotRestorable
            val app = dao.appState() ?: AppState(activeProfileId = tp.profile.id)
            val (after, d) = Trash.restoreProfile(app, tp)
            dao.save(d)?.let { return@write TrashRestore.WriteRefused(it) }
            dao.upsertAppState(after.copy(profilesEnabled = after.profilesEnabled || dao.profiles().size > 1))
            dao.deleteTrash(itemId)
            return@write TrashRestore.Restored(d)
        }
        val d = dao.load(item.profileId) ?: return@write TrashRestore.NotRestorable
        Trash.restoreProblem(d, p, replacementCategoryId, pairWith, differenceCategoryId)?.let { return@write TrashRestore.Refused(it) }
        val after = Trash.restore(d, p, clock, ids, replacementCategoryId, pairWith, differenceCategoryId)
        dao.save(after)?.let { return@write TrashRestore.WriteRefused(it) }
        dao.deleteTrash(itemId)
        TrashRestore.Restored(after)
    }

    /** Delete forever (SPEC R121): the item leaves the Trash, moving no money. */
    suspend fun deleteForever(itemId: String) = db.dao().deleteTrash(itemId)

    /**
     * Marks the Trash items [itemIds] warned (SPEC R121): their one notice was given, so Trash.tidy
     * names them due no notice again. An id not in the Trash is passed over.
     */
    suspend fun markWarned(itemIds: Collection<String>) = write { dao ->
        itemIds.distinct().chunked(500).forEach { dao.markWarned(it) }
    }

    // ---- the device's own rows: the app lock and the automatic copy --------------------------------

    /** The app lock as stored (SPEC R146): OFF with no row, DAMAGED for a row Pin cannot read, which matches no PIN. */
    suspend fun lock(): StoredLock = db.dao().appLock().let { StoredLock(Pin.state(it), it) }

    /** [lock] kept as the device's one lock row (SPEC R146): a new PIN, or the count of wrong PINs saved before a wait shows. */
    suspend fun setLock(lock: AppLock) = db.dao().upsertAppLock(lock.copy(id = 1))

    /** Removes the lock row: the app lock is off (SPEC R146). */
    suspend fun clearLock() = db.dao().deleteAppLock()

    /**
     * The automatic copy's schedule, or null before it was ever set up (SPEC R148); a stored
     * frequency or day that cannot be read is AutoCopy's to read, as weekly and as due.
     */
    suspend fun copySchedule(): CopySchedule? = db.dao().copySchedule()

    /** [s] kept as the device's one copy schedule (SPEC R148), made by AutoCopy's steps. */
    suspend fun setCopySchedule(s: CopySchedule) = db.dao().upsertCopySchedule(s.copy(id = 1))

    // ---- Sci-Fin and backups ----------------------------------------------------------------------

    /**
     * Adds the Profiles of a Sci-Fin file read whole (SPEC R19, R136), in one transaction, each with
     * new ids, switching Profiles on when the device then holds more than one. Nothing is added
     * while SciFinImport.canConfirm refuses. The Profiles added are returned; none open.
     */
    suspend fun addSciFin(read: SciFinRead.Ok): List<ProfileData> = write { dao ->
        val existing = dao.profiles().mapNotNull { dao.load(it.id) }
        val all = SciFinImport.addTo(existing, read, ids)
        val added = all.drop(existing.size)
        if (added.isEmpty()) return@write added
        // every record takes a new id from [ids] (SPEC R136): a clash is a mistake in the id source,
        // and the failed check undoes the transaction
        added.forEach { check(dao.save(it) == null) { "a new Sci-Fin id is already in use" } }
        val app = dao.appState() ?: AppState(activeProfileId = added.first().profileId)
        dao.upsertAppState(app.copy(profilesEnabled = app.profilesEnabled || SciFinImport.profilesNeeded(all)))
        added
    }

    /**
     * The backup file of every Profile and the Trash as they are (SPEC R130), less each record and
     * Trash item that would not read back and with the repairs of Backup.readable, counted; then
     * checked to read back whole (SPEC R131), so only a fault of no one record gives a code in place
     * of the file. The records the store could not read, which load left out or gave their defaults
     * (SPEC R141), are named in the result, since the file cannot carry them.
     */
    suspend fun backupFile(exportedAt: LocalDate): Export = write { dao ->
        val all = dao.profiles().mapNotNull { dao.load(it.id) }
        if (all.isEmpty()) return@write Export.Empty
        // an app state absent or unreadable (SPEC R141) names the first Profile by name open and no receipt Profile
        val app = dao.appState() ?: AppState(activeProfileId = all.first().profileId)
        val unreadable = all.flatMap { dao.unreadableRecords(it.profileId) }
        val (file, leftOut, repaired) = Backup.readable(all, dao.trash(), app.activeProfileId, app.receiptProfileId, exportedAt)
        // a figure no file can hold, such as an infinity, reads as missing in load (SPEC R141); should
        // one still reach the file, it is a fault of no one record, and the export never throws
        val text = try {
            Backup.encode(file)
        } catch (_: IllegalArgumentException) {
            return@write Export.Refused(BackupProblem.DAMAGED)
        }
        when (val r = Backup.validate(text)) {
            is BackupResult.Ok -> Export.Ok(file, text, leftOut, repaired, unreadable)
            is BackupResult.Refused -> Export.Refused(r.problem)
        }
    }

    // ---- the forgotten-PIN erase ------------------------------------------------------------------

    /**
     * The store part of the forgotten-PIN erase (SPEC R147): every Profile with its records, the
     * app's state, the lock, the copy schedule and the Trash deleted in one transaction, then the
     * file rewritten (VACUUM, the log folded in and emptied) so their old pages go too. Null when
     * both are done; STORE_NOT_ERASED, with nothing erased, when the transaction fails;
     * STORE_NOT_COMPACTED when the rows are gone but the rewrite failed, or the checkpoint reports
     * that another connection kept the log from being emptied. Once begun it runs to its end, even
     * when the caller is cancelled, which it learns on return; it never throws otherwise, and a
     * store already closed is STORE_NOT_ERASED. The files beside the store are Erase's, in jvmCommon.
     */
    suspend fun eraseStore(): EraseProblem? = withContext(NonCancellable) {
        try {
            write { dao -> dao.eraseAll() }
        } catch (_: Exception) {
            return@withContext EraseProblem.STORE_NOT_ERASED
        }
        try {
            // VACUUM fails with an error when it cannot write; the checkpoint instead reports a
            // reader in the way in its first column, 1 for blocked, and returns normally
            val blocked = db.useWriterConnection { c ->
                c.usePrepared("VACUUM") { it.step() }
                c.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { s -> s.step() && s.getLong(0) != 0L }
            }
            if (blocked) EraseProblem.STORE_NOT_COMPACTED else null
        } catch (_: Exception) {
            EraseProblem.STORE_NOT_COMPACTED
        }
    }

    companion object {
        /** The Trash item kind of a deleted Profile (SPEC R126). */
        const val PROFILE_KIND = "profile"
    }
}
