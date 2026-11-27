package com.hazia.tesoriere.engine

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.hazia.tesoriere.data.TesoriereDatabase
import com.hazia.tesoriere.data.UnreadableRecord
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AppState
import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.ImportState
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.Profile
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.RecurringExpense
import com.hazia.tesoriere.model.SCHEMA_VERSION
import com.hazia.tesoriere.model.Savings
import com.hazia.tesoriere.model.Settings
import com.hazia.tesoriere.model.Shift
import com.hazia.tesoriere.model.Transaction
import com.hazia.tesoriere.model.TrashItem
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.Payment
import com.hazia.tesoriere.payments.Payments
import com.hazia.tesoriere.receipts.ReceiptSettings
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Backup.kt at 2892110.
 *
 * The backup file (SPEC R130) and reading it (SPEC R131). One JSON file, every Profile whole, the
 * Trash as at the export; a restore replaces, never merges. The file is read and checked whole
 * before anything changes, and a file that cannot be taken is refused whole with a code.
 *
 * Changed from Sci-Fin:
 * - A Profile carries its payments, with their parts, difference marks, receipt id and lines, bank
 *   keys and the share Savings paid, and its receipt settings (SPEC R21, R112); the file carries
 *   the Trash items, each with its delete day, so their 30 days run on (owner, 2026-11-26).
 *   Sci-Fin left the Trash out and emptied it on restore.
 * - A refusal is a code ([BackupProblem]), not a sentence (SPEC R13, R16); a file never throws,
 *   whatever it holds: nesting deeper than [Backup.MAX_DEPTH] is refused before parsing, and any
 *   failure while reading is DAMAGED.
 * - The file names its app and its schema version: one naming no app is another app's, since a
 *   Sci-Fin file of schema 1 names neither and is read as SPEC R19 says, and one with no version is
 *   damaged. Sci-Fin read both as its own of version 1.
 * - An encrypted file is refused as ENCRYPTED, to be opened with BackupCrypto first (SPEC R133).
 * - The file names the receipt Profile (SPEC R112), which alone may hold a folder, and the folder
 *   travels with it; whether Profiles are switched on is not carried: a restore switches them on
 *   for a file of more than one ([ValidatedBackup.profilesNeeded]).
 * - Damage also covers a payment whose total and parts break SPEC R23 (Payments.partsAddUp), settings
 *   or import memory that are null or not an object, an export day that is not one, a Trash item
 *   whose day or payload cannot be read, and a deleted Profile whose id is a Profile in the file.
 *   What a restore from the Trash puts back, a deleted Profile whole and the payments and rows a
 *   delete removed, is checked as the file's own records, its ids among theirs (SPEC R122, R126).
 *   A Trash item of a Profile neither in the file nor in its Trash is left out, not refused.
 * - An `encrypted` mark of false is a plain file.
 * - Sci-Fin's three appearance repairs are not ported: Tesoriere's file has no appearance (SPEC R16).
 *   A Profile's name is kept as written; Sci-Fin gave a blank one a label of its own.
 * - `apply`, the restore into the database (SPEC R143), replaces the device's Trash with the file's,
 *   less the items already past their 30 days, where Sci-Fin emptied it; takes the open Profile and
 *   the receipt Profile from the file; switches Profiles on for a file of more than one and leaves
 *   the switch as it was otherwise; and keeps the rest of the app's state, the day of the last export
 *   included, where Sci-Fin wrote the app's state afresh. While the device's store holds records it
 *   cannot read (SPEC R141) it writes nothing and names them, UNREADABLE_ON_DEVICE, until the app
 *   restores again naming the very records the user agreed to lose (SPEC R143); Sci-Fin deleted
 *   every Profile regardless. Its test hook is an internal overload, so the public signature has
 *   none. Whether the platform granted notifications stays the device's: the value of the device's
 *   Profile of the same id, else none, not yet answered, for the file's Profiles and the deleted
 *   Profiles in its Trash alike; Sci-Fin restored the file's.
 * - Section references are replaced by SPEC rule numbers; the log line of a refused Profile is gone.
 */

/** One Profile as the file holds it (SPEC R130). Every list absent from a file reads as empty (SPEC R131). */
@Serializable
data class BackupProfile(
    val id: String,
    val name: String,
    val savings: Money? = null,
    val settings: Settings,
    val importState: ImportState,
    val goals: List<Goal> = emptyList(),
    val categories: List<Category> = emptyList(),
    val recurringExpenses: List<RecurringExpense> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val shifts: List<Shift> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val receiptSettings: ReceiptSettings? = null,
)

/** The file (SPEC R130): these seven keys and nothing else of the device. */
@Serializable
data class BackupFile(
    val app: String = Backup.APP,
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: String,
    val activeProfileId: String,
    val profiles: List<BackupProfile>,
    val trash: List<TrashItem> = emptyList(),
    val receiptProfileId: String? = null,
)

/** The file an export writes (SPEC R130), with how many records and Trash items it left out and how many it repaired. */
internal data class Readable(val file: BackupFile, val leftOut: Int, val repaired: Int)

/**
 * A file read whole (SPEC R131): what a restore puts in place of the device's Profiles and Trash
 * (SPEC R130). Made only by [Backup.validate], so no restore starts from a file it has not read.
 */
@ConsistentCopyVisibility
data class ValidatedBackup internal constructor(
    val profiles: List<ProfileData>,
    val trash: List<TrashItem>,
    val activeProfileId: String,
    /** null for a file that names no export day. */
    val exportedAt: LocalDate?,
    val version: Int,
    /** The Profile that holds the receipt folder, or none (SPEC R112, R130). */
    val receiptProfileId: String?,
    /** The file's Trash items left out: of a Profile neither in the file nor in its Trash (SPEC R131). */
    val trashLeftOut: Int,
) {
    val transactionCount: Int get() = profiles.sumOf { it.transactions.size }
    val paymentCount: Int get() = profiles.sumOf { it.payments.size }

    /** Whether the restore switches Profiles on: the file holds more than one (SPEC R18, R130); else the device's switch stays. */
    val profilesNeeded: Boolean get() = profiles.size > 1
}

/** Why a file is refused whole (SPEC R131); how it reads is the design pass's (SPEC R16). */
enum class BackupProblem {
    /** Not JSON, or not a JSON object. */
    NOT_A_BACKUP,

    /** Of another app, or naming no app: a Sci-Fin file is read as SPEC R19 says. */
    OTHER_APP,

    /** Encrypted: open it with BackupCrypto.decrypt first (SPEC R133). */
    ENCRYPTED,

    /** Of a schema above [SCHEMA_VERSION]. */
    NEWER_SCHEMA,

    /** Anything in it that cannot be read as SPEC R131 says. */
    DAMAGED,
}

sealed interface BackupResult {
    data class Ok(val backup: ValidatedBackup) : BackupResult
    data class Refused(val problem: BackupProblem) : BackupResult
}

/** Why a restore wrote nothing (SPEC R143); how it reads is the design pass's (SPEC R16). */
enum class ApplyProblem {
    /**
     * The device's store holds records it cannot read (SPEC R141), which the restore would delete:
     * the user is told first, and the app restores again once they agree.
     */
    UNREADABLE_ON_DEVICE,
}

/** What a restore did (SPEC R143). */
sealed interface Applied {
    /** The file's data in place of the device's. */
    data object Restored : Applied

    /** Nothing written: why, and the records of the device it would have deleted. */
    data class Refused(val problem: ApplyProblem, val unreadable: List<UnreadableRecord>) : Applied
}

object Backup {
    /** The app a Tesoriere file names (SPEC R130), outside and inside its encryption (SPEC R133). */
    const val APP = "tesoriere"

    /** The deepest nesting of arrays and objects read; the file's own is 7 (SPEC R131). */
    const val MAX_DEPTH = 64

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    /** A JSON number written as a whole number: no sign but minus, no fraction, no exponent. */
    private val WHOLE = Regex("-?(0|[1-9][0-9]*)")

    /** Something in the file that cannot be read (SPEC R131). */
    private class Damaged : Exception()

    internal fun toProfile(d: ProfileData) = BackupProfile(
        d.profile.id, d.profile.name, d.savings.amount, d.settings, d.importState,
        d.goals, d.categories, d.recurringExpenses, d.accounts, d.transactions, d.shifts, d.payments, d.receiptSettings,
    )

    /** The file of every Profile in [profiles] and every item of [trash] as it is (SPEC R130). */
    fun build(profiles: List<ProfileData>, trash: List<TrashItem>, activeProfileId: String, receiptProfileId: String?, exportedAt: LocalDate): BackupFile =
        BackupFile(exportedAt = exportedAt.toString(), activeProfileId = activeProfileId, profiles = profiles.map(::toProfile), trash = trash, receiptProfileId = receiptProfileId)

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /**
     * The file [build] gives, less each record and Trash item that would not read back as SPEC R131
     * says, with two repairs, and how many of each (SPEC R130). Left out: a ledger row whose key does
     * not read, a payment of total 0.00 whose parts do not add up, and a Trash item whose day or
     * payload cannot be read, whose records would break those checks, whose ids a record kept before
     * it holds, whose deleted Profile is among the Profiles, or whose Profile is neither among the
     * Profiles nor in the Trash. Repaired: any other payment whose parts do not add up, written with
     * one part of its whole total in no category (SPEC R99), so its money and its receipt id and
     * bank keys stay; and a receipt folder held by a Profile other than the receipt Profile, cleared,
     * as is a receipt Profile not among the Profiles (SPEC R112). Damage the store tolerates (SPEC
     * R141) never stops an export; a fault of no one record is left for [validate] to name.
     */
    internal fun readable(
        profiles: List<ProfileData>, trash: List<TrashItem>, activeProfileId: String, receiptProfileId: String?, exportedAt: LocalDate,
    ): Readable {
        var leftOut = 0
        var repaired = 0
        val live = profiles.mapTo(HashSet()) { it.profileId }
        val receipt = receiptProfileId?.takeIf { it in live }
        if (receipt != receiptProfileId) repaired++
        val kept = profiles.map { d ->
            val rows = d.transactions.filter { TransactionKey.parse(it.key) != null }
            val pays = d.payments.mapNotNull { pay ->
                when {
                    Payments.partsAddUp(pay.total, pay.parts) -> pay
                    pay.total == 0L -> { leftOut++; null }
                    else -> { repaired++; pay.copy(parts = listOf(Part(null, pay.total))) }
                }
            }
            leftOut += d.transactions.size - rows.size
            val s = d.receiptSettings
            val settings = if (s?.folder != null && d.profileId != receipt) { repaired++; s.copy(folder = null) } else s
            d.copy(transactions = rows, payments = pays, receiptSettings = settings)
        }
        val seen = Seen()
        try {
            for (d in kept) records(seen, d.profileId, d.goals, d.categories, d.recurringExpenses, d.accounts, d.transactions, d.shifts, d.payments)
        } catch (_: Damaged) {
            // ids the store keeps once cannot repeat; if they do, it is no one record's fault
            return Readable(build(kept, trash, activeProfileId, receipt, exportedAt), leftOut, repaired)
        }
        val read = trash.map { item ->
            item to runCatching { LocalDate.parse(item.deletedOn); Trash.decode(item.payload) }.getOrNull()
        }
        val deleted = HashSet<String>()
        val keep = HashSet<String>()
        // a deleted Profile's own item first, so the items of its records know it is in the Trash
        for ((item, p) in read.sortedBy { it.second?.profile == null }) {
            if (p == null) { leftOut++; continue }
            // of a Profile gone for good: it could never be restored (SPEC R126, R131)
            if (p.profile == null && item.profileId !in live && item.profileId !in deleted) { leftOut++; continue }
            // the item's ids are tried in a set of its own, kept only when the whole item reads
            val trial = seen.child()
            val ok = try {
                p.profile?.let { tp -> if (tp.profile.id != item.profileId || item.profileId in live || item.profileId in deleted) throw Damaged() }
                trial.add("trash", listOf(item.id))
                p.profile?.let { t -> records(trial, t.profile.id, t.goals, t.categories, t.expenses, t.accounts, t.transactions, t.shifts, t.payments) }
                payments(p.payments); trial.add("payment", p.payments.map { it.id })
                rows(p.transactions); trial.add("transaction", p.transactions.map { it.id })
                rows(p.written)
                true
            } catch (_: Damaged) {
                false
            }
            if (!ok) { leftOut++; continue }
            trial.mergeInto(seen)
            keep += item.id
            p.profile?.let { deleted += item.profileId }
        }
        return Readable(build(kept, trash.filter { it.id in keep }, activeProfileId, receipt, exportedAt), leftOut, repaired)
    }

    /**
     * Restores [b] into [db] in one transaction (SPEC R143): the file's Profiles in place of every
     * Profile on the device, the device's Trash replaced by the file's less each item past its 30
     * days on [today] (SPEC R121), the open and the receipt Profile the file's, Profiles switched on
     * for a file of more than one (SPEC R130), and [today] kept as the last restore. The app lock,
     * the automatic copy and whether notifications were granted stay the device's: a Profile of the
     * file keeps the value of the device's Profile of its id, and one new to the device, or held in
     * the file's Trash, reads as not yet answered.
     * While the device's store holds records it cannot read (SPEC R141), which the restore would
     * delete, nothing is written and they are named, UNREADABLE_ON_DEVICE; once the user has been
     * told and agrees, the app restores again with [agreedToDelete], the very list the refusal
     * named. A list that is not the store's at the restore, read in the same transaction as the
     * delete, is refused again with the store's list. A failure anywhere leaves the device as it was.
     */
    suspend fun apply(db: TesoriereDatabase, b: ValidatedBackup, today: LocalDate, agreedToDelete: List<UnreadableRecord> = emptyList()): Applied =
        apply(db, b, today, agreedToDelete) {}

    /** [apply], with [afterProfiles] run once the file's Profiles are written, so a test can fail the restore there. */
    internal suspend fun apply(
        db: TesoriereDatabase, b: ValidatedBackup, today: LocalDate, agreedToDelete: List<UnreadableRecord> = emptyList(),
        afterProfiles: suspend () -> Unit,
    ): Applied =
        db.useWriterConnection { conn ->
            conn.immediateTransaction {
                val dao = db.dao()
                val device = dao.profiles()
                // what the store could not read no file carries: it goes only once the user knows (SPEC R141, R143)
                val unreadable = device.flatMap { dao.unreadableRecords(it.id) }
                // only the records the user agreed to lose go: any other list, read here in the delete's
                // own transaction, is refused again with the store's
                if (unreadable.isNotEmpty() && unreadable.toSet() != agreedToDelete.toSet()) {
                    return@immediateTransaction Applied.Refused(ApplyProblem.UNREADABLE_ON_DEVICE, unreadable)
                }
                // whether the platform granted notifications is this device's, not the file's (SPEC R143)
                val granted = device.associate { it.id to dao.load(it.id)?.settings?.notificationPermissionGranted }
                // every device Profile goes first, so no id the file shares with one can clash (SPEC R141)
                for (p in device) dao.deleteProfileWhole(p.id)
                // a file read whole holds each id once (SPEC R131): a clash left is a mistake, and the
                // failed check undoes the transaction
                for (p in b.profiles) {
                    check(dao.save(p.copy(settings = p.settings.copy(notificationPermissionGranted = granted[p.profileId]))) == null) { "an id of the file is held twice" }
                }
                afterProfiles()
                // the device's Trash belonged to the data replaced; the file's runs on its own days, and a
                // deleted Profile in it takes the device's value too, so a restore from the Trash never brings the file's
                val gone = Trash.tidy(b.trash, today).gone.toHashSet()
                dao.emptyTrash()
                dao.insertTrash(b.trash.filter { it.id !in gone }.map { item -> withDeviceGrant(item, granted) })
                val prev = dao.appState()
                dao.upsertAppState(
                    (prev ?: AppState(activeProfileId = b.activeProfileId)).copy(
                        schemaVersion = SCHEMA_VERSION, activeProfileId = b.activeProfileId, receiptProfileId = b.receiptProfileId,
                        profilesEnabled = prev?.profilesEnabled == true || b.profilesNeeded, lastBackupImportDate = today,
                    ),
                )
                Applied.Restored
            }
        }

    /**
     * [item] with a deleted Profile's notifications granted set to the device's value for that
     * Profile in [granted], or none, not yet answered, for one new to the device (SPEC R143); any
     * other item, and one already holding that value, as it is.
     */
    private fun withDeviceGrant(item: TrashItem, granted: Map<String, Boolean?>): TrashItem {
        val p = Trash.decode(item.payload) ?: return item
        val tp = p.profile ?: return item
        val own = granted[tp.profile.id]
        if (tp.settings.notificationPermissionGranted == own) return item
        return item.copy(payload = Trash.encode(p.copy(profile = tp.copy(settings = tp.settings.copy(notificationPermissionGranted = own)))))
    }

    /**
     * The file [text] read and checked whole, or the code of why it is refused (SPEC R131); nothing
     * is read from a refused file. It never throws, whatever the text holds.
     */
    fun validate(text: String): BackupResult {
        val root = BackupJson.parseObject(text) ?: return refused(
            if (text.trimStart().startsWith("{") && BackupJson.tooDeep(text)) BackupProblem.DAMAGED else BackupProblem.NOT_A_BACKUP,
        )
        return try {
            read(root)
        } catch (_: Throwable) {
            // a decoding failure, a day that is not one, a number past a Long: any of it is damage
            refused(BackupProblem.DAMAGED)
        }
    }

    private fun refused(p: BackupProblem) = BackupResult.Refused(p)

    private fun read(root: JsonObject): BackupResult {
        if (root["app"].str() != APP) return refused(BackupProblem.OTHER_APP)
        if (BackupJson.markedEncrypted(root)) return refused(BackupProblem.ENCRYPTED)
        val v = root["schemaVersion"] as? JsonPrimitive ?: throw Damaged()
        if (v.isString || !WHOLE.matches(v.content)) throw Damaged()
        if (v.content.startsWith("-")) throw Damaged()
        // a whole number too large for an Int is still above the app's schema
        val version = v.content.toIntOrNull() ?: return refused(BackupProblem.NEWER_SCHEMA)
        if (version > SCHEMA_VERSION) return refused(BackupProblem.NEWER_SCHEMA)
        if (version < 1) throw Damaged()

        val list = root["profiles"] as? JsonArray ?: throw Damaged()
        if (list.isEmpty()) throw Damaged()
        val seen = Seen()
        val profiles = list.map { profile(it, seen) }
        val live = profiles.mapTo(HashSet()) { it.profileId }

        val (trash, leftOut) = trash(root["trash"], live, seen)

        val receipt = text(root, "receiptProfileId")
        if (receipt != null && receipt !in live) throw Damaged()
        // the receipt Profile alone holds a folder (SPEC R112)
        val holders = profiles.filter { it.receiptSettings?.folder != null }.map { it.profileId }
        if (holders.size > 1 || (holders.size == 1 && holders[0] != receipt)) throw Damaged()

        val active = text(root, "activeProfileId")?.takeIf { it in live } ?: profiles.first().profileId
        val exportedAt = text(root, "exportedAt")?.let { LocalDate.parse(it) }
        return BackupResult.Ok(ValidatedBackup(profiles, trash, active, exportedAt, version, receipt, leftOut))
    }

    /** Key [key] of [root] as text: null when absent; a null or any other kind is damage (SPEC R131). */
    private fun text(root: JsonObject, key: String): String? = when (val el = root[key]) {
        null -> null
        // receiptProfileId alone may hold nothing
        is JsonNull -> if (key == "receiptProfileId") null else throw Damaged()
        else -> el.str() ?: throw Damaged()
    }

    /** Ids already met, per kind of record, across the file and its Trash: the stored keys are global, so a repeat would overwrite. */
    private class Seen(private val parent: Seen? = null) {
        private val ids = HashMap<String, MutableSet<String>>()

        private fun has(kind: String, id: String): Boolean = ids[kind]?.contains(id) == true || parent?.has(kind, id) == true

        fun add(kind: String, list: List<String>) = list.forEach { if (has(kind, it) || !ids.getOrPut(kind) { HashSet() }.add(it)) throw Damaged() }

        /** A set of its own to try a Trash item's ids on, against this one's (SPEC R130). */
        fun child() = Seen(this)

        /** This set's ids added to [target]'s, once its item reads whole. */
        fun mergeInto(target: Seen) {
            for ((kind, set) in ids) target.ids.getOrPut(kind) { HashSet() }.addAll(set)
        }
    }

    private fun profile(e: JsonElement, seen: Seen): ProfileData {
        val po = e as? JsonObject ?: throw Damaged()
        val id = po["id"].str()?.takeIf { it.isNotBlank() } ?: throw Damaged()
        if (po["name"].str() == null) throw Damaged()
        val bp = json.decodeFromJsonElement(BackupProfile.serializer(), withProfileId(po, id))
        records(seen, id, bp.goals, bp.categories, bp.recurringExpenses, bp.accounts, bp.transactions, bp.shifts, bp.payments)
        return ProfileData(
            profile = Profile(id, bp.name),
            savings = Savings(id, bp.savings),
            settings = bp.settings.copy(profileId = id),
            importState = bp.importState.copy(profileId = id),
            goals = bp.goals.map { it.copy(profileId = id) },
            categories = bp.categories.map { it.copy(profileId = id) },
            recurringExpenses = bp.recurringExpenses.map { it.copy(profileId = id) },
            accounts = bp.accounts.map { it.copy(profileId = id) },
            transactions = bp.transactions.map { it.copy(profileId = id) },
            shifts = bp.shifts.map { it.copy(profileId = id) },
            payments = bp.payments.map { it.copy(profileId = id) },
            receiptSettings = bp.receiptSettings?.copy(profileId = id),
        )
    }

    /** One Profile's records, in the file or in its Trash: each id new to its kind, each row's key read, each payment's parts as SPEC R23 says. */
    private fun records(
        seen: Seen, id: String, goals: List<Goal>, categories: List<Category>, expenses: List<RecurringExpense>,
        accounts: List<Account>, transactions: List<Transaction>, shifts: List<Shift>, payments: List<Payment>,
    ) {
        rows(transactions)
        payments(payments)
        seen.add("profile", listOf(id)); seen.add("goal", goals.map { it.id }); seen.add("category", categories.map { it.id })
        seen.add("recurring expense", expenses.map { it.id }); seen.add("account", accounts.map { it.id })
        seen.add("transaction", transactions.map { it.id }); seen.add("shift", shifts.map { it.id })
        seen.add("payment", payments.map { it.id })
    }

    /** Every row's key is one the ledger reads (SPEC R51): every Savings figure reads them. */
    private fun rows(transactions: List<Transaction>) = transactions.forEach { if (TransactionKey.parse(it.key) == null) throw Damaged() }

    private fun payments(payments: List<Payment>) = payments.forEach { if (!Payments.partsAddUp(it.total, it.parts)) throw Damaged() }

    /**
     * The file's Trash (SPEC R130, R131), and how many of its items are left out: an item whose
     * Profile is neither in the file nor itself in the Trash can never be restored. A deleted
     * Profile's item carries its own id, which no Profile in the file has (SPEC R126); what a
     * restore would put back, a deleted Profile whole and the payments and rows a delete removed,
     * is checked as the file's own records. The rows a delete wrote stay in the ledger and share
     * its ids (SPEC R122): only their keys are read.
     */
    private fun trash(el: JsonElement?, live: Set<String>, seen: Seen): Pair<List<TrashItem>, Int> {
        val items = when (el) {
            null -> return emptyList<TrashItem>() to 0
            is JsonArray -> el.map { json.decodeFromJsonElement(TrashItem.serializer(), it) }
            else -> throw Damaged()
        }
        val read = items.map { item ->
            LocalDate.parse(item.deletedOn)
            item to (Trash.decode(item.payload) ?: throw Damaged())
        }
        val deleted = HashSet<String>()
        for ((item, p) in read) {
            val tp = p.profile ?: continue
            if (tp.profile.id != item.profileId || item.profileId in live || !deleted.add(item.profileId)) throw Damaged()
        }
        val kept = read.filter { (item, _) -> item.profileId in live || item.profileId in deleted }
        seen.add("trash", kept.map { it.first.id })
        for ((_, p) in kept) {
            p.profile?.let { t -> records(seen, t.profile.id, t.goals, t.categories, t.expenses, t.accounts, t.transactions, t.shifts, t.payments) }
            payments(p.payments); seen.add("payment", p.payments.map { it.id })
            rows(p.transactions); seen.add("transaction", p.transactions.map { it.id })
            rows(p.written)
        }
        return kept.map { it.first } to read.size - kept.size
    }

    /**
     * [po] with Profile [id] in its settings, import memory and receipt settings, whose stored
     * shape needs one: absent settings and import memory read as their defaults; null receipt
     * settings are none; any other null, or anything but an object, is damage (SPEC R131).
     */
    private fun withProfileId(po: JsonObject, id: String): JsonObject {
        val out = po.toMutableMap()
        for (key in listOf("settings", "importState", "receiptSettings")) {
            val o = when (val el = po[key]) {
                null -> if (key == "receiptSettings") continue else JsonObject(emptyMap())
                is JsonNull -> if (key == "receiptSettings") continue else throw Damaged()
                is JsonObject -> el
                else -> throw Damaged()
            }
            out[key] = JsonObject(o.toMutableMap().also { it["profileId"] = JsonPrimitive(id) })
        }
        return JsonObject(out)
    }

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/** JSON reading shared by the backup file and its encrypted envelope (SPEC R131, R133). It never throws. */
internal object BackupJson {
    private val json = Json

    /** A day written `YYYY-MM-DD`. */
    private val DAY = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

    /** Whether [o] carries an `encrypted` mark: a value other than null or false (SPEC R131). */
    fun markedEncrypted(o: JsonObject): Boolean {
        val m = o["encrypted"] as? JsonPrimitive ?: return false
        return m !is JsonNull && !(!m.isString && m.content == "false")
    }

    /** [s] as a day when it is one written `YYYY-MM-DD`, else null (SPEC R133). */
    fun day(s: String): LocalDate? = if (DAY.matches(s)) runCatching { LocalDate.parse(s) }.getOrNull() else null

    /** [text] as a JSON object, or null when it is not one or nests deeper than [Backup.MAX_DEPTH]. */
    fun parseObject(text: String): JsonObject? {
        if (tooDeep(text)) return null
        return try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: Throwable) {
            null
        }
    }

    /** Whether [text] nests arrays and objects deeper than [Backup.MAX_DEPTH], counted outside strings. */
    fun tooDeep(text: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '[', '{' -> if (++depth > Backup.MAX_DEPTH) return true
                ']', '}' -> depth--
            }
        }
        return false
    }
}
