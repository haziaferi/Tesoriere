@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere.engine

import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.DateMode
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.ImportState
import com.hazia.tesoriere.model.Money
import com.hazia.tesoriere.model.Profile
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.RecurringExpense
import com.hazia.tesoriere.model.Savings
import com.hazia.tesoriere.model.Settings
import com.hazia.tesoriere.model.Shift
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.model.Transaction
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.Payment
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.statements.StatementImport
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Backup.kt at 2892110 (its
 * reading, Backup.validate), with the record shapes of its model/Entities.kt and the history of
 * data/Migrations.kt, the envelope of its engine/BackupCrypto.kt (opened by Tesoriere's BackupCrypto
 * with the app `sci-fin`) and the key forms of its engine/Import.kt.
 *
 * A Sci-Fin backup read whole into new Tesoriere Profiles (SPEC R19, R134-R138), after a review of
 * each Profile's counts; nothing is merged into a Profile on the device.
 *
 * Changed from Sci-Fin:
 * - A refusal is a code ([SciFinRefusal]), never a sentence, and a file's contents never throw
 *   (SPEC R134). A schema version that is not a whole number is DAMAGED; Sci-Fin called it another
 *   app's file. A JSON null app is read as no app, as Sci-Fin read it.
 * - The three appearance repairs are not ported: Tesoriere reads no appearance field (SPEC R16), and
 *   a Profile's name is kept as written, where Sci-Fin gave a blank one a label of its own.
 * - Sci-Fin's records become Tesoriere's (SPEC R135-R139): `cat:` rows other than corrections and
 *   starting balances, goal spends and card charges become payments; Category.spent is dropped (SPEC
 *   R25) and its lastResetMonth becomes the Profile's month mark; a recurring expense waits for a
 *   category before the Profiles can be added (owner answer 2 of 2026-11-24); a card's payoffs,
 *   its fixed payment's rows and the `savings` rows beside its payments by hand, are transfers from
 *   its first logged charge on; those before it, or all with no charge, wait for a payoff category
 *   they are filed under, and a card with no charge has its charges not logged (owner, 2026-11-26);
 *   the kept keys of the statement
 *   import take Tesoriere's key forms, once per Sci-Fin row each stands for, and the payee rules
 *   their rule names; a catch-up summary still unread is not carried; Accounts are switched on for
 *   a Profile that holds one; whether notifications were granted reads as not yet answered, null,
 *   since it is the device's own (SPEC R136). Every record takes a new id when added ([SciFinImport.addTo]), where
 *   Sci-Fin's restore kept the file's ids and replaced the device's Profiles.
 * - A Profile with no settings, or with its records nested under `data` as in Sci-Fin's web
 *   prototype, a blank id, a key naming no id and a goal spend of zero or above are DAMAGED.
 * - Sci-Fin's `apply`, which replaced the database, is not ported: the Profiles are added beside the
 *   device's, and storage comes with phase 1's Task 21. The log line of a refused Profile is gone.
 * - Section references are replaced by SPEC rule numbers.
 */

/** Why a Sci-Fin file is refused whole (SPEC R134); how it reads is the design pass's (SPEC R16). */
enum class SciFinRefusal {
    /** Not JSON, or not a JSON object. */
    NOT_A_BACKUP,

    /** Naming an app other than `sci-fin`, outside its encryption or inside it. */
    OTHER_APP,

    /** Encrypted, and opened with no password. */
    PASSWORD_NEEDED,

    /** Encrypted, and the password is wrong or the envelope was changed (SPEC R133). */
    WRONG_PASSWORD,

    /** Encrypted with an envelope version or key function the app does not know (SPEC R133). */
    NEWER_FORMAT,

    /** Of a Sci-Fin schema above [SciFinImport.HIGHEST_SCHEMA]. */
    NEWER_SCHEMA,

    /** Anything in it that cannot be read as SPEC R134 says. */
    DAMAGED,
}

/** What the review shows of one Sci-Fin Profile (SPEC R19): its name, Savings and counts. [ledgerRows] counts Sci-Fin's rows, payments or rows now. */
data class SciFinProfileCounts(
    val name: String,
    val goals: Int,
    val categories: Int,
    val recurringExpenses: Int,
    val accounts: Int,
    val ledgerRows: Int,
    val savings: Money?,
)

data class SciFinReview(val profiles: List<SciFinProfileCounts>)

sealed interface SciFinRead {
    /**
     * A file read whole (SPEC R134): its Profiles as Tesoriere's, still under the file's ids until
     * [SciFinImport.addTo] gives them new ones (SPEC R136), and the Sci-Fin schema [version] it was.
     */
    data class Ok(val review: SciFinReview, val profiles: List<ProfileData>, val version: Int) : SciFinRead

    data class Refused(val reason: SciFinRefusal) : SciFinRead
}

/** A category as a Sci-Fin file holds it: its stored spent is read past, its last reset month kept for the month mark (SPEC R25, R136). */
@Serializable
private data class SciFinCategory(
    val id: String,
    val profileId: String,
    val name: String,
    val icon: String? = null,
    val planned: Money = 0,
    val spent: Money = 0,
    val lastResetMonth: String? = null,
)

/** A recurring expense as a Sci-Fin file holds it: no category (SPEC R137). Schemas 1 to 3's `lastReminderFiredFor` is read past. */
@Serializable
private data class SciFinExpense(
    val id: String,
    val profileId: String,
    val name: String,
    val icon: String? = null,
    val amount: Money,
    val cadence: Cadence,
    val firstDueDate: LocalDate,
    val dueDate: LocalDate,
    val dueDate2: LocalDate? = null,
    val dueDateAnchorDay: Int? = null,
    val dateMode: DateMode? = null,
    val intervalDays: Int? = null,
    val apportionedShare: Money? = null,
    val reminderEnabled: Boolean = false,
    val reminderLeadDays: Int = 3,
    val lastSweptMonth: String? = null,
    val endDate: LocalDate? = null,
    val finishedAt: LocalDate? = null,
    val linkedAccountId: String? = null,
)

/**
 * One Profile as a Sci-Fin file holds it, from schema 1 to 10: every list absent from a file reads as
 * empty, shifts before schema 9 included. Goals, Accounts, rows, shifts, settings and import memory
 * share Tesoriere's shapes, whose fields Sci-Fin lacks take their defaults.
 */
@Serializable
private data class SciFinProfile(
    val id: String,
    val name: String,
    val savings: Money? = null,
    val settings: Settings,
    val importState: ImportState,
    val goals: List<Goal> = emptyList(),
    val categories: List<SciFinCategory> = emptyList(),
    val recurringExpenses: List<SciFinExpense> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val shifts: List<Shift> = emptyList(),
)

object SciFinImport {
    /** The app a Sci-Fin file names, outside and inside its encryption (SPEC R133, R134). */
    const val APP = "sci-fin"

    /** The highest Sci-Fin schema read (SPEC R19). */
    const val HIGHEST_SCHEMA = 10

    /** A recurring expense's category before the review gives it one (SPEC R137): no category's id. */
    const val NO_CATEGORY = ""

    /** The notes Sci-Fin wrote on a rise of an Account's balance that is not a charge (SPEC R135). */
    private val BALANCE_NOTES = setOf(StoredLabels.OPENING_BALANCE, StoredLabels.CORRECTION, StoredLabels.RESTORED, StoredLabels.EDIT_RECONCILIATION)

    /** The notes of Sci-Fin's direct edits of a category's spent, kept as adjustments (SPEC R135). */
    private val ADJUSTMENT_NOTES = setOf(StoredLabels.CORRECTION, StoredLabels.STARTING_BALANCE)

    private val json = Json { ignoreUnknownKeys = true }

    /** A JSON number written as a whole number: no sign but minus, no fraction, no exponent. */
    private val WHOLE = Regex("-?(0|[1-9][0-9]*)")

    /** Something in the file that cannot be read (SPEC R134). */
    private class Damaged : Exception()

    private fun refused(r: SciFinRefusal) = SciFinRead.Refused(r)

    /**
     * The Sci-Fin file [text] read and checked whole, or the code of why it is refused (SPEC R134);
     * an encrypted file is opened with [password] first (SPEC R133). It never throws, whatever the
     * text holds, and nothing is read from a refused file.
     */
    fun read(text: String, password: String?): SciFinRead {
        val root = BackupJson.parseObject(text) ?: return refused(
            if (text.trimStart().startsWith("{") && BackupJson.tooDeep(text)) SciFinRefusal.DAMAGED else SciFinRefusal.NOT_A_BACKUP,
        )
        if (!BackupJson.markedEncrypted(root)) return plain(root)
        // the envelope's app is read before any password is asked for
        val app = BackupCrypto.locked(text)?.app ?: return refused(SciFinRefusal.DAMAGED)
        if (app != APP) return refused(SciFinRefusal.OTHER_APP)
        if (password.isNullOrEmpty()) return refused(SciFinRefusal.PASSWORD_NEEDED)
        return when (val o = BackupCrypto.decrypt(text, password, APP)) {
            is BackupCrypto.Opened.Plain -> {
                val inner = BackupJson.parseObject(o.text)
                // what an envelope seals is a plain file
                if (inner == null || BackupJson.markedEncrypted(inner)) refused(SciFinRefusal.DAMAGED) else plain(inner)
            }
            BackupCrypto.Opened.WrongPassword -> refused(SciFinRefusal.WRONG_PASSWORD)
            is BackupCrypto.Opened.Refused -> refused(
                when (o.problem) {
                    OpenProblem.DAMAGED -> SciFinRefusal.DAMAGED
                    OpenProblem.OTHER_APP -> SciFinRefusal.OTHER_APP
                    OpenProblem.NEWER_FORMAT -> SciFinRefusal.NEWER_FORMAT
                },
            )
        }
    }

    private fun plain(root: JsonObject): SciFinRead {
        // no app, or a null one, is Sci-Fin's: its files before schema 5 name none (SPEC R134)
        val app = root["app"]
        if (app != null && app !is JsonNull && app.str() != APP) return refused(SciFinRefusal.OTHER_APP)
        val v = root["schemaVersion"]
        val version = if (v == null || v is JsonNull) 1 else {
            if (v !is JsonPrimitive || v.isString || !WHOLE.matches(v.content) || v.content.startsWith("-")) return refused(SciFinRefusal.DAMAGED)
            // a whole number too large for an Int is still above the highest schema
            val n = v.content.toIntOrNull() ?: return refused(SciFinRefusal.NEWER_SCHEMA)
            if (n > HIGHEST_SCHEMA) return refused(SciFinRefusal.NEWER_SCHEMA)
            if (n < 1) return refused(SciFinRefusal.DAMAGED)
            n
        }
        val profiles = try {
            val list = root["profiles"] as? JsonArray ?: throw Damaged()
            if (list.isEmpty()) throw Damaged()
            val seen = HashMap<String, MutableSet<String>>()
            list.map { profile(it, seen) }
        } catch (_: Throwable) {
            // a decoding failure, a day that is not one, a number past a Long: any of it is damage
            return refused(SciFinRefusal.DAMAGED)
        }
        return SciFinRead.Ok(review(profiles), profiles, version)
    }

    private fun review(profiles: List<ProfileData>) = SciFinReview(
        profiles.map { p ->
            SciFinProfileCounts(
                p.profile.name, p.goals.size, p.categories.size, p.recurringExpenses.size, p.accounts.size,
                p.transactions.size + p.payments.size, p.savings.amount,
            )
        },
    )

    private fun profile(e: JsonElement, seen: HashMap<String, MutableSet<String>>): ProfileData {
        val po = e as? JsonObject ?: throw Damaged()
        val id = po["id"].str()?.takeIf { it.isNotBlank() } ?: throw Damaged()
        if (po["name"].str() == null) throw Damaged()
        // native Sci-Fin always wrote settings; its web prototype nested the records under `data`, which is not read
        if (po["settings"] == null || po["data"] is JsonObject) throw Damaged()
        val sf = json.decodeFromJsonElement(SciFinProfile.serializer(), withProfileId(po, id))
        // ids are global across Profiles, per kind of record, and never blank (SPEC R134)
        fun unique(kind: String, ids: List<String>) = ids.forEach { if (it.isBlank() || !seen.getOrPut(kind) { HashSet() }.add(it)) throw Damaged() }
        unique("profile", listOf(id)); unique("goal", sf.goals.map { it.id }); unique("category", sf.categories.map { it.id })
        unique("recurring expense", sf.recurringExpenses.map { it.id }); unique("account", sf.accounts.map { it.id })
        unique("transaction", sf.transactions.map { it.id }); unique("shift", sf.shifts.map { it.id })
        // every Savings figure reads the keys (SPEC R51); a key names an id, and a goal spend is below zero, as Sci-Fin wrote it
        for (t in sf.transactions) {
            when (val k = TransactionKey.parse(t.key)) {
                null -> throw Damaged()
                TransactionKey.SavingsKey -> {}
                else -> if (t.key.substringAfter(':').isBlank() || (k is TransactionKey.GoalSpendKey && t.amount >= 0)) throw Damaged()
            }
        }
        return convert(id, sf)
    }

    /** A Sci-Fin Profile as Tesoriere's, under the file's ids (SPEC R135-R139). */
    private fun convert(id: String, sf: SciFinProfile): ProfileData {
        val categories = sf.categories.map { Category(it.id, id, it.name, it.icon, it.planned) }
        val credit = sf.accounts.filter { it.type == AccountType.CREDIT }.mapTo(HashSet()) { it.id }
        val payments = ArrayList<Payment>()
        val written = ArrayList<Transaction>()
        for (t in sf.transactions.map { it.copy(profileId = id) }) {
            val p = payment(t, credit)
            if (p != null) payments += p else written += t
        }
        // a card's payoffs (SPEC R139): transfers from its first logged charge on; before it, or all
        // of them with none, they wait for the payoff category the review gives, and a card with no
        // charge logged that has any arrives with its charges not logged
        val cardOf = sf.recurringExpenses.mapNotNull { e -> e.linkedAccountId?.takeIf { it in credit }?.let { e.id to it } }.toMap()
        val pending = credit.filterTo(HashSet()) { card ->
            firstCharge(payments, card) == null && payoffRows(card, written, cardOf.filterValues { it == card }.keys).isNotEmpty()
        }
        val rows = written.map { t ->
            val card = (TransactionKey.parse(t.key) as? TransactionKey.RecurringKey)?.let { cardOf[it.id] }
            val first = card?.let { firstCharge(payments, it) }
            if (first != null && t.date >= first) t.copy(accountPayoff = true) else t
        }
        // the month cycle starts from the oldest category reset, as Sci-Fin's did (SPEC R70, R136)
        val mark = sf.categories.mapNotNull { monthMark(it.lastResetMonth) }.minOrNull()
        val settings = sf.settings.copy(
            profileId = id,
            accountsEnabled = sf.accounts.isNotEmpty(),
            lastMonthProcessed = mark,
            // Sci-Fin's notices are its own sentences, not R74's codes
            catchUpSummaryPending = false, catchUpDepositCount = 0, catchUpDepositTotal = 0,
            catchUpMonths = 0, catchUpSwept = 0, catchUpEnded = "",
            // a permission Sci-Fin was granted is not Tesoriere's: not yet answered, as on a Profile new to the device (SPEC R136)
            notificationPermissionGranted = null,
        )
        val held = categories.mapTo(HashSet()) { it.id }
        return ProfileData(
            profile = Profile(id, sf.name),
            savings = Savings(id, sf.savings),
            settings = settings,
            importState = sf.importState.copy(
                profileId = id,
                // a key once for each Sci-Fin row it stands for, so twins are counted (SPEC R26, R138)
                importedKeys = sf.importState.importedKeys.flatMap { k -> keptKey(k)?.let { kept -> List(rowsFor(k, sf.transactions)) { kept } }.orEmpty() },
                payeeRules = rules(sf.importState.payeeRules, held),
            ),
            goals = sf.goals.map { it.copy(profileId = id) },
            categories = categories,
            recurringExpenses = sf.recurringExpenses.map { expense(it, id) },
            // Sci-Fin had no such switch or categories: a card R139 names waits for its payoff category
            accounts = sf.accounts.map {
                it.copy(profileId = id, chargesNotLogged = it.id in pending, payoffCategoryId = null, feesCategoryId = null, loanCategoryId = null)
            },
            transactions = rows,
            shifts = sf.shifts.map { it.copy(profileId = id) },
            payments = payments,
        )
    }

    /** A synthetic key's amount, signed with two decimals and a point (SPEC R26). */
    private val KEY_AMOUNT = Regex("(-?)(0|[1-9][0-9]{0,14})\\.([0-9]{2})")

    /**
     * How many times Sci-Fin's kept key [k] is kept (SPEC R138): once for each row of [rows] that
     * Sci-Fin's Confirm wrote for a statement row of that key, dated its day, noted its description
     * (first 40 characters, lower case) and of its amount, a `savings` row for money in and a `cat:`,
     * `spend:` or `acct:` row for money out; once when there is none or the key is no synthetic key.
     */
    private fun rowsFor(k: String, rows: List<Transaction>): Int {
        if (!k.startsWith("syn:")) return 1
        val parts = k.removePrefix("syn:").split('|', limit = 3)
        if (parts.size != 3) return 1
        val (day, amount, desc) = parts
        val m = KEY_AMOUNT.matchEntire(amount) ?: return 1
        val figure = m.groupValues[2].toLong() * 100 + m.groupValues[3].toLong()
        val inflow = m.groupValues[1].isEmpty()
        val n = rows.count { t ->
            t.date.toString() == day && t.payee?.take(StatementImport.KEY_TEXT)?.lowercase() == desc &&
                when (TransactionKey.parse(t.key)) {
                    TransactionKey.SavingsKey -> inflow && t.amount == figure
                    is TransactionKey.CategoryKey, is TransactionKey.GoalSpendKey, is TransactionKey.AccountKey -> !inflow && abs(t.amount) == figure
                    else -> false
                }
        }
        return maxOf(1, n)
    }

    /**
     * The payment Sci-Fin row [t] stands for, or null when it stays a ledger row (SPEC R135): a `cat:`
     * row not noted `Correction` or `Starting balance`, a goal spend, or a rise of a credit Account's
     * balance that is not a balance edit. The payment takes the row's id until [addTo] gives it its own.
     */
    private fun payment(t: Transaction, credit: Set<String>): Payment? {
        fun pay(total: Money, source: PaymentSource, category: String?) =
            Payment(t.id, t.profileId, t.date, t.loggedAt, total, source, t.payee, listOf(Part(category, total)))
        return when (val k = TransactionKey.parse(t.key)) {
            is TransactionKey.CategoryKey -> if (t.amount != 0L && t.payee !in ADJUSTMENT_NOTES) pay(t.amount, PaymentSource.Savings, k.id) else null
            // below zero, as read checks
            is TransactionKey.GoalSpendKey -> pay(-t.amount, PaymentSource.Goal(k.id), null)
            is TransactionKey.AccountKey ->
                if (t.amount > 0 && k.id in credit && t.payee !in BALANCE_NOTES) pay(t.amount, PaymentSource.Account(k.id), null) else null
            else -> null
        }
    }

    private fun expense(e: SciFinExpense, profileId: String) = RecurringExpense(
        id = e.id, profileId = profileId, name = e.name, icon = e.icon, amount = e.amount, cadence = e.cadence,
        categoryId = NO_CATEGORY, firstDueDate = e.firstDueDate, dueDate = e.dueDate, dueDate2 = e.dueDate2,
        dueDateAnchorDay = e.dueDateAnchorDay, dateMode = e.dateMode, intervalDays = e.intervalDays,
        apportionedShare = e.apportionedShare, reminderEnabled = e.reminderEnabled, reminderLeadDays = e.reminderLeadDays,
        lastSweptMonth = e.lastSweptMonth, endDate = e.endDate, finishedAt = e.finishedAt, linkedAccountId = e.linkedAccountId,
    )

    /**
     * Sci-Fin's kept key [k] in Tesoriere's form (SPEC R26, R138), or null when it is left out: a FITID
     * key as it is; a synthetic key with its description's spacing made one, its ends trimmed when the
     * description is shorter than the 40 characters a key keeps, else its start only and only when that
     * cuts nothing, since the characters past the 40th cannot be known. Kept plain, never `fsyn:`:
     * Sci-Fin's list does not say whose synthetic key follows a FITID key.
     */
    internal fun keptKey(k: String): String? = when {
        k.startsWith("fit:") -> k.takeIf { it.length > "fit:".length }
        k.startsWith("syn:") -> {
            val parts = k.removePrefix("syn:").split('|', limit = 3)
            if (parts.size != 3) null else {
                val (day, amount, desc) = parts
                val spaced = StatementImport.spacedOnce(desc)
                val text = if (desc.length < StatementImport.KEY_TEXT) spaced.trim() else spaced.trimStart().takeIf { it.length == desc.length }
                text?.let { "syn:$day|$amount|$it" }
            }
        }
        else -> null
    }

    /**
     * Sci-Fin's payee rules under their rule names (SPEC R108, R138): a rule of a category the Profile
     * does not hold is left out; rules of one rule name naming different categories keep their payees.
     */
    private fun rules(sciFin: Map<String, String>, held: Set<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((name, group) in sciFin.entries.filter { it.value in held }.groupBy { StatementImport.payeeRuleName(it.key) }) {
            // a payee of no word at all names no row
            if (name.isEmpty()) continue
            if (group.map { it.value }.distinct().size == 1) out[name] = group[0].value else group.forEach { out[it.key] = it.value }
        }
        return out
    }

    /**
     * Whether Confirm is on (SPEC R137, R139): every recurring expense in [read] has a category of its
     * own Profile, and every card with payoff rows to file ([toFile]) a payoff category of it.
     */
    fun canConfirm(read: SciFinRead.Ok): Boolean = read.profiles.all { p ->
        p.recurringExpenses.all { e -> p.categories.any { it.id == e.categoryId } } &&
            p.accounts.all { a -> toFile(p, a.id).isEmpty() || p.categories.any { it.id == a.payoffCategoryId } }
    }

    /** [read] with expense [expenseId] filed under [categoryId], a category of its own Profile; else unchanged (SPEC R137). */
    fun setExpenseCategory(read: SciFinRead.Ok, expenseId: String, categoryId: String): SciFinRead.Ok {
        val p = read.profiles.firstOrNull { p -> p.recurringExpenses.any { it.id == expenseId } } ?: return read
        if (p.categories.none { it.id == categoryId }) return read
        return withProfile(read, p.copy(recurringExpenses = p.recurringExpenses.map { if (it.id == expenseId) it.copy(categoryId = categoryId) else it }))
    }

    /**
     * [read] with a category named [name] created in the Profile of expense [expenseId], as [newId],
     * and the expense filed under it (SPEC R137); unchanged for a blank name, an unknown expense or an
     * id a category of the file has already.
     */
    fun addCategoryForExpense(read: SciFinRead.Ok, expenseId: String, name: String, newId: String): SciFinRead.Ok {
        val p = read.profiles.firstOrNull { p -> p.recurringExpenses.any { it.id == expenseId } } ?: return read
        val added = withCategory(read, p, name, newId) ?: return read
        return setExpenseCategory(added, expenseId, newId)
    }

    /**
     * [read] with card [accountId], one with payoff rows to file (SPEC R139), given [categoryId], a
     * category of its own Profile, as its payoff category, and each of those rows filed under it, as a
     * payoff of a card whose charges are not logged is written (SPEC R109); else unchanged.
     */
    fun setCardCategory(read: SciFinRead.Ok, accountId: String, categoryId: String): SciFinRead.Ok {
        val p = cardProfile(read, accountId) ?: return read
        if (p.categories.none { it.id == categoryId }) return read
        val filed = toFile(p, accountId)
        return withProfile(
            read,
            p.copy(
                accounts = p.accounts.map { if (it.id == accountId) it.copy(payoffCategoryId = categoryId) else it },
                transactions = p.transactions.map { t -> if (t.id in filed) t.copy(categoryId = categoryId) else t },
            ),
        )
    }

    /** The Profile of card [accountId] when the card has payoff rows to file (SPEC R139). */
    private fun cardProfile(read: SciFinRead.Ok, accountId: String): ProfileData? =
        read.profiles.firstOrNull { p -> p.accounts.any { it.id == accountId } }?.takeIf { toFile(it, accountId).isNotEmpty() }

    /** The day of the first charge to card [card] logged in the file, a payment charged to it (SPEC R135, R139); null for none. */
    private fun firstCharge(payments: List<Payment>, card: String): LocalDate? =
        payments.filter { (it.source as? PaymentSource.Account)?.id == card }.minOfOrNull { it.date }

    /**
     * The ids of the payoff rows of card [card] in [p] that a payoff category files (SPEC R139): those
     * dated before its first logged charge, or all of them with none; none for an Account that is no
     * credit card.
     */
    private fun toFile(p: ProfileData, card: String): Set<String> {
        if (p.accounts.none { it.id == card && it.type == AccountType.CREDIT }) return emptySet()
        val expenses = p.recurringExpenses.filter { it.linkedAccountId == card }.mapTo(HashSet()) { it.id }
        val all = payoffRows(card, p.transactions, expenses)
        val first = firstCharge(p.payments, card) ?: return all
        return p.transactions.filter { it.id in all && it.date < first }.mapTo(HashSet()) { it.id }
    }

    /** The notes of an `acct:` row below zero that is no payment by hand (SPEC R139). */
    private val NOT_PAID = setOf(StoredLabels.CORRECTION, StoredLabels.ACCOUNT_CLOSED, StoredLabels.LOAN_FINISHED)

    /** How long after a payment by hand's `acct:` row Sci-Fin's `savings` row beside it may be logged (SPEC R139). */
    private val BESIDE = 1.minutes

    /**
     * The ids of card [cardId]'s payoff rows among [rows] (SPEC R139): the rows of the [expenses]
     * linked to it, and the `savings` row Sci-Fin wrote beside each payment off it by hand: of the
     * payment's figure below zero, dated its day, logged no later than a minute after it and noted as
     * the payment is or `Payment to` the card's name as the payment names it, the payment not noted as
     * a balance edit, a closing or a Loan's end; each row taken once.
     */
    private fun payoffRows(cardId: String, rows: List<Transaction>, expenses: Set<String>): Set<String> {
        val out = HashSet<String>()
        for (t in rows) {
            val k = TransactionKey.parse(t.key)
            if (k is TransactionKey.RecurringKey && k.id in expenses) out += t.id
        }
        val key = TransactionKey.AccountKey(cardId).stored
        for (a in rows.filter { it.key == key && it.amount < 0 && it.payee !in NOT_PAID }) {
            val beside = rows.firstOrNull { s ->
                s.id !in out && s.key == TransactionKey.SavingsKey.stored && s.amount == a.amount && s.date == a.date &&
                    (s.loggedAt - a.loggedAt).let { it >= Duration.ZERO && it <= BESIDE } &&
                    (s.payee == a.payee || s.payee == StoredLabels.fill(StoredLabels.PAYMENT_TO, a.targetLabel))
            }
            if (beside != null) out += beside.id
        }
        return out
    }

    /** [read] with a category named [name] created as [newId] in the Profile of card [accountId] and given to it, as [setCardCategory] does (SPEC R139). */
    fun addCategoryForCard(read: SciFinRead.Ok, accountId: String, name: String, newId: String): SciFinRead.Ok {
        val p = cardProfile(read, accountId) ?: return read
        val added = withCategory(read, p, name, newId) ?: return read
        return setCardCategory(added, accountId, newId)
    }

    /** [read] with a category named [name] as [newId] in [p]; null for a blank name or an id a category of the file has already. */
    private fun withCategory(read: SciFinRead.Ok, p: ProfileData, name: String, newId: String): SciFinRead.Ok? {
        val n = name.trim()
        if (n.isEmpty() || newId.isBlank() || read.profiles.any { q -> q.categories.any { it.id == newId } }) return null
        return withProfile(read, p.copy(categories = p.categories + Category(newId, p.profileId, n)))
    }

    /**
     * Whether Profiles is switched on once [profiles], what [addTo] gives, are the device's: they are
     * more than one (SPEC R18, R136), as a restore of a file of more than one does (SPEC R130).
     */
    fun profilesNeeded(profiles: List<ProfileData>): Boolean = profiles.size > 1

    private fun withProfile(read: SciFinRead.Ok, p: ProfileData): SciFinRead.Ok {
        val profiles = read.profiles.map { if (it.profileId == p.profileId) p else it }
        return read.copy(review = review(profiles), profiles = profiles)
    }

    /**
     * [existing] with each Profile of [read] after them, every record under a new id from [ids] and every
     * key and link naming one following it (SPEC R19, R136), so nothing merges into a Profile on the
     * device. While Confirm is off ([canConfirm]), [existing] comes back as it was. The caller switches
     * Profiles on when the result holds more than one ([profilesNeeded], SPEC R18, R136).
     */
    fun addTo(existing: List<ProfileData>, read: SciFinRead.Ok, ids: () -> String): List<ProfileData> {
        if (!canConfirm(read)) return existing
        val map = HashMap<Pair<String, String>, String>()
        // an id named but held by no record, such as a deleted Goal's in its spend rows, takes a new one too
        fun new(kind: String, old: String): String = map.getOrPut(kind to old, ids)
        return existing + read.profiles.map { renamed(it) { kind, old -> new(kind, old) } }
    }

    private fun renamed(d: ProfileData, new: (String, String) -> String): ProfileData {
        val id = new("profile", d.profileId)
        fun goal(g: String) = new("goal", g)
        fun cat(c: String) = new("category", c)
        fun catOrNone(c: String?) = c?.let(::cat)
        fun expense(e: String) = new("recurring expense", e)
        fun account(a: String) = new("account", a)
        fun key(k: String): String = when (val t = TransactionKey.parse(k)) {
            is TransactionKey.GoalKey -> TransactionKey.GoalKey(goal(t.id)).stored
            is TransactionKey.CategoryKey -> TransactionKey.CategoryKey(cat(t.id)).stored
            is TransactionKey.RecurringKey -> TransactionKey.RecurringKey(expense(t.id)).stored
            is TransactionKey.AccountKey -> TransactionKey.AccountKey(account(t.id)).stored
            is TransactionKey.GoalSpendKey -> TransactionKey.GoalSpendKey(goal(t.id)).stored
            TransactionKey.SavingsKey, null -> k
        }
        fun source(s: PaymentSource): PaymentSource = when (s) {
            PaymentSource.Savings -> s
            is PaymentSource.Goal -> PaymentSource.Goal(goal(s.id))
            is PaymentSource.Account -> PaymentSource.Account(account(s.id))
        }
        return ProfileData(
            profile = Profile(id, d.profile.name),
            savings = d.savings.copy(profileId = id),
            settings = d.settings.copy(profileId = id),
            importState = d.importState.copy(profileId = id, payeeRules = d.importState.payeeRules.mapValues { cat(it.value) }),
            goals = d.goals.map { it.copy(id = goal(it.id), profileId = id) },
            categories = d.categories.map { it.copy(id = cat(it.id), profileId = id) },
            recurringExpenses = d.recurringExpenses.map {
                it.copy(id = expense(it.id), profileId = id, categoryId = cat(it.categoryId), linkedAccountId = it.linkedAccountId?.let(::account))
            },
            accounts = d.accounts.map {
                it.copy(
                    id = account(it.id), profileId = id, linkedRecurringExpenseId = it.linkedRecurringExpenseId?.let(::expense),
                    payoffCategoryId = catOrNone(it.payoffCategoryId), feesCategoryId = catOrNone(it.feesCategoryId), loanCategoryId = catOrNone(it.loanCategoryId),
                )
            },
            transactions = d.transactions.map {
                it.copy(
                    id = new("transaction", it.id), profileId = id, key = key(it.key), categoryId = catOrNone(it.categoryId),
                    groupId = it.groupId?.let { g -> new("transaction", g) },
                )
            },
            shifts = d.shifts.map { it.copy(id = new("shift", it.id), profileId = id) },
            payments = d.payments.map { p ->
                p.copy(id = new("payment", p.id), profileId = id, source = source(p.source), parts = p.parts.map { it.copy(categoryId = catOrNone(it.categoryId)) })
            },
            receiptSettings = null,
        )
    }

    /**
     * [po] with Profile [id] in its settings and import memory: absent import memory reads as its
     * defaults (absent settings are refused before); null or anything but an object is damage (SPEC R134).
     */
    private fun withProfileId(po: JsonObject, id: String): JsonObject {
        val out = po.toMutableMap()
        for (key in listOf("settings", "importState")) {
            val o = when (val el = po[key]) {
                null -> JsonObject(emptyMap())
                is JsonObject -> el
                else -> throw Damaged()
            }
            out[key] = JsonObject(o.toMutableMap().also { it["profileId"] = JsonPrimitive(id) })
        }
        return JsonObject(out)
    }

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}
