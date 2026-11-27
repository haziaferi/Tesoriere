package com.hazia.tesoriere.engine

import com.hazia.tesoriere.data.Export
import com.hazia.tesoriere.model.CopySchedule
import kotlinx.datetime.LocalDate

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/app/CopyPlace.kt at 2892110
 * (CopyEvery), with the engine half of app/AppSession.kt's runCopy and app/AppStore.kt's copy
 * settings at the same commit. Sci-Fin's app/LocalCopies.kt is the forgotten-PIN erase, which is
 * ProfileRepository.eraseStore and data/Erase.kt (SPEC R147), so nothing of it is ported here.
 *
 * The automatic copy (SPEC R148): a backup file (SPEC R130) written daily or weekly into a folder
 * the user chose, off on a fresh install (SPEC R18), app-wide and device-only, never in a backup.
 * Turning it on ([AutoCopy.turnOn]) chooses a folder and weekly: that is R01's weekly backup.
 *
 * Kept as in Sci-Fin: choosing a folder ([AutoCopy.withFolder]) keeps the frequency, so a folder
 * chosen while the copy is off leaves it off; Sci-Fin's setCopyEvery, which asked for the folder
 * when turning it on, is [AutoCopy.turnOn].
 *
 * Changed from Sci-Fin:
 * - A copy's name holds the device as well as the day, `tesoriere-copy-<day>-<device>.json`, with
 *   the device made safe for a file name and lowercased ([AutoCopy.deviceName]), so two names never
 *   differ only in case; [AutoCopy.stale] names only this device's own copies beyond the newest 14,
 *   those dated after today counted as the oldest, never the day's own. Sci-Fin named a copy by its
 *   day alone and deleted every copy of its own beyond 14, another device's included, and kept
 *   copies dated after today as the newest.
 * - [AutoCopy.contents] takes the schedule: a sealed password the key store no longer gives back is
 *   PASSWORD_UNREADABLE, a password handed in that the schedule does not hold is PASSWORD_NOT_SAVED,
 *   and with the app lock on and no password nothing is written (Paused), so a plain copy is never
 *   written in place of an encrypted one (SPEC R132); a pause owes a notice as a failure does
 *   ([AutoCopy.afterPause]), so the weekly backup never stops unseen.
 * - [AutoCopy.outcome] reads the export: a file naming records the store cannot read is copied
 *   all the same, owing RECORDS_UNREADABLE's notice, and no old copy is deleted while it lasts
 *   (owner, 2026-11-27); an export with no file is NOT_MADE.
 * - A refused change gives a code: [AutoCopy.everyProblem] NO_FOLDER, and
 *   [AutoCopy.withoutPasswordProblem] R132's REQUIRED; Sci-Fin's AppStore returned in silence.
 * - An unknown frequency token reads as WEEKLY, since only `off` turns the copy off; Sci-Fin read it
 *   as OFF, which would stop the copies unseen.
 * - A last copy after today (the clock moved back) or one that is not a `YYYY-MM-DD` day makes a
 *   copy due; Sci-Fin held the copy back until the clock passed the last copy.
 * - A failure is a code ([CopyError]) stored as its token, not a sentence; a stored failure of no
 *   known token reads as NOT_WRITTEN.
 * - The copies' password is checked with BackupCrypto.exportProblem, R132's codes; Sci-Fin's
 *   AppStore checked it with passwordProblem and refused in place.
 * - CopyEvery has no label (SPEC R16). CopyPlace (the folder, writing and listing, the key store),
 *   CopyFolder, CopyOutcome and the run itself are platform work for phase 2 (SPEC R44).
 * - Section references are replaced by SPEC rule numbers.
 */

/** How often the automatic copy runs (SPEC R148), its stored token and its days between copies. */
enum class CopyEvery(val token: String, val days: Int) {
    OFF("off", 0), DAILY("daily", 1), WEEKLY("weekly", 7);

    companion object {
        /** The frequency a stored token names; any token but `off` that is not known reads as [WEEKLY]. */
        fun of(token: String): CopyEvery = entries.firstOrNull { it.token == token } ?: WEEKLY
    }
}

/** Why the last copy was not written (SPEC R148), and its stored token. */
enum class CopyError(val token: String) {
    /** The folder cannot be reached or read: moved, removed, or the app's access withdrawn. */
    FOLDER_UNREACHABLE("folder_unreachable"),

    /** The copy could not be written: the folder is read-only or full. */
    NOT_WRITTEN("not_written"),

    /** The copies' password cannot be read back from this device's key store. */
    PASSWORD_UNREADABLE("password_unreadable"),

    /** The backup itself could not be made: the export gave a code in place of a file, or found no Profile (SPEC R130). */
    NOT_MADE("not_made"),

    /**
     * A notice code, not a failure: the export named records the store cannot read (SPEC R130,
     * R141), and the copy was written of everything else ([CopyText.Lacking], AutoCopy.afterSuccess
     * with it); while it lasts no old copy is deleted, so those that still hold the records stay.
     */
    RECORDS_UNREADABLE("records_unreadable"),

    /**
     * A notice code, not a failure: the export left records out that would not read back (SPEC
     * R130), such as a ledger row whose key no longer reads, a 0.00 payment or a Trash item, and
     * none the store cannot read; the copy is written of the rest and kept as RECORDS_UNREADABLE is.
     */
    RECORDS_LEFT_OUT("records_left_out"),

    /** A password was handed in for copies whose schedule holds none sealed: the caller saves it with withPassword first. */
    PASSWORD_NOT_SAVED("password_not_saved"),

    /**
     * Not a failure of the copy: it is due, but held back while the app lock is on and the copies
     * have no password (SPEC R132). Owed a notice like a failure, so the weekly backup never stops unseen.
     */
    PAUSED_NEEDS_PASSWORD("paused_needs_password");

    companion object {
        /** The failure a stored token names; a token not known still records a failure, [NOT_WRITTEN]. */
        fun of(token: String): CopyError = entries.firstOrNull { it.token == token } ?: NOT_WRITTEN
    }
}

/** Why a change to the schedule is refused (SPEC R148). */
enum class CopyProblem {
    /** The copy cannot be on with no folder chosen; the app asks for one first. */
    NO_FOLDER,
}

/** What a copy writes, or why it writes nothing (SPEC R148). */
sealed interface CopyText {
    /** The file's text: the backup, or its encrypted file under the copies' password. */
    data class Ready(val text: String) : CopyText

    /**
     * The backup's text from an export that lacks records: ones the store cannot read (SPEC R141),
     * [notice] RECORDS_UNREADABLE, or ones it left out (SPEC R130), RECORDS_LEFT_OUT. It is copied
     * all the same (AutoCopy.contents), recorded with AutoCopy.afterSuccess, owing [notice]'s
     * notice, and AutoCopy.stale deletes no old copy after it.
     */
    data class Lacking(val text: String, val notice: CopyError) : CopyText

    /** A failure to record (AutoCopy.afterFailure). */
    data class Failed(val error: CopyError) : CopyText

    /**
     * The app lock is on and the copies have no password: nothing is written (SPEC R132), and the
     * pause is recorded (AutoCopy.afterPause), owing a notice at the first of a run of pauses.
     */
    data object Paused : CopyText
}

object AutoCopy {
    const val PREFIX = "tesoriere-copy-"
    const val SUFFIX = ".json"

    /** This device's copies kept in the folder; older ones of its own are deleted. */
    const val KEPT = 14

    /** The longest device name a copy's name holds. */
    const val DEVICE_MAX = 32

    /** A copy's name when the device gives no name. */
    const val NO_DEVICE = "device"

    /** The frequency of [s]; no row is [CopyEvery.OFF]. */
    fun every(s: CopySchedule?): CopyEvery = s?.let { CopyEvery.of(it.every) } ?: CopyEvery.OFF

    /** The day of the last copy written, or null when none is recorded or it is not a `YYYY-MM-DD` day. */
    fun lastCopy(s: CopySchedule?): LocalDate? = s?.lastCopy?.let(BackupJson::day)

    /** The last attempt's failure, or null after a success or before any attempt. */
    fun lastError(s: CopySchedule?): CopyError? = s?.lastError?.let(CopyError::of)

    /**
     * Whether a copy is due on [today] (SPEC R148): on, with a folder, and with no last copy that can
     * be trusted (none, unreadable, or after today) or at least its days since the last.
     */
    fun due(s: CopySchedule?, today: LocalDate): Boolean {
        if (s?.folder == null) return false
        val every = every(s)
        if (every == CopyEvery.OFF) return false
        val last = lastCopy(s) ?: return true
        return last > today || last.daysTo(today) >= every.days
    }

    /** Whether the copies are held back: on, with the app lock on and no password of their own (SPEC R132). */
    fun paused(s: CopySchedule?, lockOn: Boolean): Boolean =
        s != null && lockOn && s.sealedPassword == null && every(s) != CopyEvery.OFF

    /**
     * [s] with the folder chosen, at the frequency it had, off included, as in Sci-Fin; the last
     * failure cleared, since the new folder has not failed.
     */
    fun withFolder(s: CopySchedule?, folder: String, label: String): CopySchedule =
        (s ?: CopySchedule()).copy(folder = folder, folderLabel = label, lastError = null, failureNotified = false)

    /** Turning the copy on: the folder chosen and the copy weekly, which is R01's weekly backup. */
    fun turnOn(s: CopySchedule?, folder: String, label: String): CopySchedule =
        withFolder(s, folder, label).copy(every = CopyEvery.WEEKLY.token)

    /** Why [s] cannot be set to [every]: on with no folder chosen is NO_FOLDER; null when it can. */
    fun everyProblem(s: CopySchedule?, every: CopyEvery): CopyProblem? =
        if (every != CopyEvery.OFF && s?.folder == null) CopyProblem.NO_FOLDER else null

    /** [s] at [every]; unchanged when [everyProblem] gives a code. */
    fun withEvery(s: CopySchedule?, every: CopyEvery): CopySchedule {
        val base = s ?: CopySchedule()
        return if (everyProblem(s, every) != null) base else base.copy(every = every.token)
    }

    /** What stops [password] from protecting the copies: R132's check for an export, the same codes. */
    fun passwordProblem(lockOn: Boolean, password: String?, again: String, hint: String): PasswordProblem? =
        BackupCrypto.exportProblem(lockOn, password, again, hint)

    /**
     * [s] with the password the key store sealed and its hint, kept trimmed; a pause for want of a
     * password and its notice are cleared, since the next copy can be written. The caller checked [passwordProblem].
     */
    fun withPassword(s: CopySchedule?, sealed: String, hint: String): CopySchedule {
        val base = s ?: CopySchedule()
        val pausedBefore = base.lastError == CopyError.PAUSED_NEEDS_PASSWORD.token
        return base.copy(
            sealedPassword = sealed, hint = hint.trim(),
            lastError = if (pausedBefore) null else base.lastError,
            failureNotified = if (pausedBefore) false else base.failureNotified,
        )
    }

    /** Why the copies cannot go back to plain: R132's REQUIRED while the app lock is on; null when they can. */
    fun withoutPasswordProblem(lockOn: Boolean): PasswordProblem? = if (lockOn) PasswordProblem.REQUIRED else null

    /** [s] back to plain copies; unchanged when [withoutPasswordProblem] gives a code. */
    fun withoutPassword(s: CopySchedule, lockOn: Boolean): CopySchedule =
        if (withoutPasswordProblem(lockOn) != null) s else s.copy(sealedPassword = null, hint = null)

    /**
     * [device] as a copy's name holds it: its ASCII letters lowercased, its digits, `-` and `_`, every
     * other character `_`, at most [DEVICE_MAX]; [NO_DEVICE] when it is empty. No name made so holds a
     * character a file name on Android or Windows cannot, nor a dot, and no two differ only in case.
     */
    fun deviceName(device: String): String {
        if (device.isEmpty()) return NO_DEVICE
        return device.map { c ->
            when (c) {
                in 'A'..'Z' -> c + ('a' - 'A')
                in 'a'..'z', in '0'..'9', '-', '_' -> c
                else -> '_'
            }
        }.joinToString("").take(DEVICE_MAX)
    }

    /**
     * The copy's name for [today] from [device]: one per day and device name, so no copy replaces
     * one of another day; a second copy on the same day from the same device replaces the day's.
     */
    fun fileName(today: LocalDate, device: String): String = "$PREFIX$today-${deviceName(device)}$SUFFIX"

    /**
     * The names in [names] to delete after a copy on [today]: this device's own copies beyond the
     * newest [KEPT], oldest first, those dated after today (a clock that ran ahead) counted as the
     * oldest of all, and never the day's own. A name that is not exactly one of [fileName]'s for
     * this device, a `YYYY-MM-DD` day and this device's name, is never named. [written] is what was
     * just copied, [outcome]'s answer for the export: only after a whole copy ([CopyText.Ready]) is
     * any name given; after a copy lacking records ([CopyText.Lacking]), or nothing written, none,
     * so the older copies that still hold the records stay and the folder grows by one copy a run.
     * There is no default, so no caller deletes copies by leaving it out.
     */
    fun stale(names: List<String>, device: String, today: LocalDate, written: CopyText): List<String> {
        if (written !is CopyText.Ready) return emptyList()
        val tail = "-${deviceName(device)}$SUFFIX"
        val ours = names.distinct().mapNotNull { n ->
            if (!n.startsWith(PREFIX) || !n.endsWith(tail) || n.length < PREFIX.length + tail.length) return@mapNotNull null
            BackupJson.day(n.substring(PREFIX.length, n.length - tail.length))?.let { it to n }
        }.sortedWith(compareBy<Pair<LocalDate, String>> { it.first <= today }.thenBy { it.first })
        val own = fileName(today, device)
        return ours.dropLast(KEPT).map { it.second }.filter { it != own }
    }

    /**
     * The backup a copy writes, from the export (SPEC R130, R148): its plain text when the export
     * gave a whole file; the same text as [CopyText.Lacking] when the file lacks records, copied all
     * the same (owner, 2026-11-27): RECORDS_UNREADABLE when the export named records the store
     * cannot read, else RECORDS_LEFT_OUT when it left any out; a record repaired is carried, so
     * it lacks nothing. NOT_MADE when the export gave a code in place of a file or found no Profile.
     */
    fun outcome(export: Export): CopyText = when (export) {
        is Export.Ok -> when {
            export.unreadable.isNotEmpty() -> CopyText.Lacking(export.text, CopyError.RECORDS_UNREADABLE)
            export.leftOut > 0 -> CopyText.Lacking(export.text, CopyError.RECORDS_LEFT_OUT)
            else -> CopyText.Ready(export.text)
        }
        is Export.Refused, Export.Empty -> CopyText.Failed(CopyError.NOT_MADE)
    }

    /**
     * What a copy of [s] writes on [today] (SPEC R148), given the backup's [plain] text (SPEC R130),
     * the password the key store gave back for [s]'s sealed one ([unsealed], null when it gave none)
     * and whether the app lock is on: with a sealed password, the encrypted file of SPEC R133 under
     * it and [s]'s hint, or PASSWORD_UNREADABLE when none came back; with none, PASSWORD_NOT_SAVED
     * when a password is handed in all the same, else nothing while the lock is on (Paused, SPEC
     * R132), else the plain text. A copy is never plain while [s] holds a sealed password or a
     * password is handed in, so a password the caller meant to use never yields a plain file.
     */
    fun contents(s: CopySchedule, plain: String, unsealed: String?, lockOn: Boolean, today: LocalDate): CopyText = when {
        s.sealedPassword != null ->
            if (unsealed.isNullOrEmpty()) CopyText.Failed(CopyError.PASSWORD_UNREADABLE)
            else CopyText.Ready(BackupCrypto.encrypt(plain, unsealed, s.hint.orEmpty(), today))
        // the empty text is no password (SPEC R132), as it is for a sealed one
        !unsealed.isNullOrEmpty() -> CopyText.Failed(CopyError.PASSWORD_NOT_SAVED)
        lockOn -> CopyText.Paused
        else -> CopyText.Ready(plain)
    }

    /**
     * [s] after a copy written on [today]: its day recorded, the failure or pause and its notice
     * cleared; for a copy [lacking] records, its notice code recorded instead (RECORDS_UNREADABLE
     * or RECORDS_LEFT_OUT) and the run's notice marked given, as for a failure.
     */
    fun afterSuccess(s: CopySchedule, today: LocalDate, lacking: CopyText.Lacking? = null): CopySchedule =
        if (lacking != null) s.copy(lastCopy = today.toString(), lastError = lacking.notice.token, failureNotified = true)
        else s.copy(lastCopy = today.toString(), lastError = null, failureNotified = false)

    /** [s] after a failed copy: [error] recorded and the run's notice marked given; the last good copy kept. */
    fun afterFailure(s: CopySchedule, error: CopyError): CopySchedule =
        s.copy(lastError = error.token, failureNotified = true)

    /**
     * [s] after a due copy was paused ([CopyText.Paused]): PAUSED_NEEDS_PASSWORD recorded and the
     * run's notice marked given, as a failure is, so the weekly backup never stops unseen; the last
     * good copy kept, so the copy stays due.
     */
    fun afterPause(s: CopySchedule): CopySchedule = afterFailure(s, CopyError.PAUSED_NEEDS_PASSWORD)

    /**
     * Whether recording [next] now owes a notice (SPEC R148): the first of a run of failures, of
     * pauses, or of copies lacking records; one of these three after another of them starts a run
     * of its own. With [next] unknown, the first of a run only.
     */
    fun noticeOwed(s: CopySchedule, next: CopyError? = null): Boolean {
        if (!s.failureNotified) return true
        if (next == null) return false
        return run(lastError(s)) != run(next)
    }

    /** Which run of notices [e] belongs to: pauses, copies lacking records, or failures (SPEC R148). */
    private fun run(e: CopyError?): Int = when (e) {
        CopyError.PAUSED_NEEDS_PASSWORD -> 1
        CopyError.RECORDS_UNREADABLE, CopyError.RECORDS_LEFT_OUT -> 2
        else -> 0
    }
}
