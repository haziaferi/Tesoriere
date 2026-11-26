@file:OptIn(ExperimentalTime::class)
@file:UseSerializers(InstantMillisSerializer::class)

package com.hazia.tesoriere.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/model/Entities.kt at 2892110.
 *
 * One class per stored table, annotated for Room so the engine's working types are the stored
 * types. Every table carries a `profileId` column rather than one database per Profile: one
 * file, one schema version, one backup, and every read and write is scoped by that column. Ids
 * are UUID strings, unique across Profiles by construction (SPEC R20).
 *
 * Changed from Sci-Fin: `Category.spent` and `Category.lastResetMonth` are gone, since spent is
 * worked out per month (SPEC R25, R59); Settings' appearance fields are gone (SPEC R16); the
 * feature switches of SPEC R18 are added; a recurring expense has a category and a ledger row
 * can carry the category it was filed under (owner answer 2 of 2026-11-24); a card can be marked
 * as one whose charges are not logged, with a payoff category (SPEC R109).
 */

@Serializable
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey val id: String,
    val name: String,
)

/** Exactly one per Profile. `amount == null` is the unset state, which is not zero (SPEC R49). */
@Serializable
@Entity(tableName = "savings")
data class Savings(
    @PrimaryKey val profileId: String,
    val amount: Money?,
)

@Serializable
@Entity(tableName = "goals", indices = [Index("profileId")])
data class Goal(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val icon: String? = null,
    val saved: Money = 0,
    /** null: a running tally with no target, never reached (SPEC R55). */
    val target: Money? = null,
    /** Planned by a date only. */
    val targetDate: LocalDate? = null,
    val mode: GoalMode? = null,
    /** Planned by an amount: the monthly amount the user chose. Months are worked out (SPEC R55). */
    val monthly: Money? = null,
)

/** A category: a planned amount, read beside its spent figure for each month (SPEC R59). */
@Serializable
@Entity(tableName = "categories", indices = [Index("profileId")])
data class Category(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val icon: String? = null,
    val planned: Money = 0,
)

@Serializable
@Entity(tableName = "recurring_expenses", indices = [Index("profileId"), Index("linkedAccountId")])
data class RecurringExpense(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val icon: String? = null,
    val amount: Money,
    val cadence: Cadence,
    /** The category its sweeps are filed under (owner answer 2 of 2026-11-24). */
    val categoryId: String,
    /** The floor for walking the chain of due dates backwards: no occurrence predates it. */
    val firstDueDate: LocalDate,
    /** The live anchor: advances when the real date passes, never on a sweep. */
    val dueDate: LocalDate,
    /** Semimonthly only. */
    val dueDate2: LocalDate? = null,
    /** Fixed-date mode only: the day of the month a short month snaps back from. */
    val dueDateAnchorDay: Int? = null,
    /** Month-anchored cadences only. */
    val dateMode: DateMode? = null,
    /** Interval mode only. */
    val intervalDays: Int? = null,
    /** Bimonthly and yearly only: the share set aside each month, never recomputed from a shrinking divisor. */
    val apportionedShare: Money? = null,
    val reminderEnabled: Boolean = false,
    val reminderLeadDays: Int = 3,
    val lastSweptMonth: String? = null,
    val endDate: LocalDate? = null,
    /** Set once the final occurrence has passed; `dueDate` stops advancing. */
    val finishedAt: LocalDate? = null,
    val linkedAccountId: String? = null,
    /**
     * The day it was created (SPEC R60, R64); null for one read from a Sci-Fin backup, which
     * reads as its first due date (Recurring.createdOn). Part of schema 1, which is still edited
     * in place until the first install.
     */
    val createdOn: LocalDate? = null,
)

@Serializable
@Entity(tableName = "accounts", indices = [Index("profileId"), Index("linkedRecurringExpenseId")])
data class Account(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val icon: String? = null,
    val type: AccountType,
    val balance: Money = 0,
    /** Credit only. A rate, not money: 0.219 is 21.9 %. */
    val apr: Double? = null,
    /**
     * Credit only: the user does not log this card's charges, so its payoffs count as spending in
     * [payoffCategoryId], which must name a category that exists while this is on (SPEC R109).
     */
    val chargesNotLogged: Boolean = false,
    val payoffCategoryId: String? = null,
    /** Credit only: where a rise of the balance that is not a payment, its interest or fees, is filed unless one is picked (SPEC R110). */
    val feesCategoryId: String? = null,
    val linkedRecurringExpenseId: String? = null,
    /** Loan only. */
    val autoUpdateFromPayments: Boolean = false,
    val principal: Money? = null,
    val interestRate: Double? = null,
    val termMonths: Int? = null,
    val rateType: RateType? = null,
    val originationDate: LocalDate? = null,
    /**
     * Loan only: the category a payment off it by hand counts in while nothing is charged to it,
     * unless the payment picks another (SPEC R89). Optional; a Sci-Fin backup has none. Part of
     * schema 1, which is still edited in place until the first install.
     */
    val loanCategoryId: String? = null,
)

/**
 * A ledger row (SPEC R51). `amount` is the signed change to the target named by [key]; how
 * Savings moves follows from the key, in the engine's Ledger. The key keeps Sci-Fin's spelling
 * (`savings`, `goal:<id>`, `cat:<id>`, `rec:<id>`, `acct:<id>`, `spend:<goalId>`).
 */
@Serializable
@Entity(tableName = "transactions", indices = [Index("profileId"), Index("date"), Index("loggedAt")])
data class Transaction(
    @PrimaryKey val id: String,
    val profileId: String,
    /** The day it belongs to: places the row in its month and in reports (SPEC R51). */
    val date: LocalDate,
    /** When it was logged: orders pairs (SPEC R53) and rows of the same date. */
    val loggedAt: Instant,
    val payee: String? = null,
    val amount: Money,
    val key: String,
    /** The target's name when the row was logged. */
    val targetLabel: String,
    val isIncome: Boolean = false,
    /** Hourly paydays only. Hours are not money. */
    val hoursWorked: Double? = null,
    val rate: Money? = null,
    /**
     * The category a row counting as spending is filed under, as written (SPEC R25, R51): a
     * recurring sweep's at sweep time (owner answer 2 of 2026-11-24), and the payoff category on a
     * card's payoff rows while its charges are not logged (SPEC R109), and a card's interest or
     * fees on its `acct:` row (SPEC R110); null on every other row.
     */
    val categoryId: String? = null,
    /**
     * A `rec:` row written while its expense was linked to a card, or to a Loan with a payment
     * charged to it: an Account payoff, a transfer that moves Savings and counts in no spent
     * (SPEC R67, R93, owner answers A and F of 2026-11-25). Written once, never read from the link.
     */
    val accountPayoff: Boolean = false,
    /**
     * The group shared by the rows of one Account quick-add, so it can be undone as a whole
     * (SPEC R111): the id of its `acct:` row. Null on every other row, and on rows written before
     * groups were kept or read from a Sci-Fin backup. Part of schema 1, still edited in place.
     */
    val groupId: String? = null,
    /**
     * A `rec:` row of a bimonthly or yearly expense's monthly share, or an accepted difference of
     * one (SPEC R62, R63, R65): money set aside toward a bill still to come, which the import
     * review never takes as a bill already paid (SPEC R107). Part of schema 1, still edited in
     * place; false on every other row and on a Sci-Fin backup's rows.
     */
    val share: Boolean = false,
)

/** One per Profile. The defaults are a fresh install's: every piece SPEC R18 starts off is off. */
@Serializable
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val profileId: String,
    val currency: String = "EUR",
    /** The Spending report's trend window: 3, 6, 12 or 24 months. */
    val trendMonths: Int = 6,
    val incomeEnabled: Boolean = false,
    /** Credit and Loan accounts with their payoff calculators (SPEC R18). */
    val accountsEnabled: Boolean = false,
    /** Per-paycheck amount. */
    val income: Money = 0,
    val incomeCadence: IncomeCadence = IncomeCadence.BIWEEKLY,
    val incomePayDate: LocalDate? = null,
    val incomePayDate2: LocalDate? = null,
    val incomeAutoDeposit: Boolean = false,
    val incomeEnabledDate: LocalDate? = null,
    val lastIncomeDepositDate: LocalDate? = null,
    val lastIncomeLoggedDate: LocalDate? = null,
    val newMonthNotificationEnabled: Boolean = true,
    /** null until the platform has answered. */
    val notificationPermissionGranted: Boolean? = null,
    val savingsVsOwedEnabled: Boolean = false,
    /** The Science topics (SPEC R18). */
    val scienceEnabled: Boolean = false,
    /** The Overtime calculator (SPEC R18). */
    val overtimeEnabled: Boolean = false,
    val onboardingComplete: Boolean = false,
    val onboardingStep: Int? = null,
    val goalPromptDismissed: Boolean = false,
    /**
     * The last month the month cycle processed, `YYYY-MM` (SPEC R70): the Profile's creation month
     * on a fresh Profile, then the current month after each open. It stands for Sci-Fin's
     * `Category.lastResetMonth`, which Tesoriere drops with the stored spent figure (SPEC R25).
     */
    val lastMonthProcessed: String? = null,
    val catchUpSummaryPending: Boolean = false,
    /** The catch-up summary, kept until dismissed: deposits, months replayed, what they swept, notices. */
    val catchUpDepositCount: Int = 0,
    val catchUpDepositTotal: Money = 0,
    val catchUpMonths: Int = 0,
    val catchUpSwept: Money = 0,
    /**
     * The notices, one per line as `expenseId<TAB>code<TAB>name`, the code an `EndedKind` token and
     * the name as typed (SPEC R72, R74); a line of `expenseId<TAB>code` alone has no name.
     */
    val catchUpEnded: String = "",
    val incomeType: IncomeType = IncomeType.SALARIED,
    /** Hourly only: the rate a new shift starts from, per hour. */
    val hourlyRate: Money = 0,
    /** The last payday whose prompt was answered (hourly) or whose entry was offered (salaried, manual). */
    val lastPaydayHandled: LocalDate? = null,
    val paydayNotificationEnabled: Boolean = true,
    /** The language this Profile is shown in (`en`, `it`, SPEC R07); null follows the device. */
    val language: String? = null,
)

/** Hours logged: a shift moves nothing until the payday that covers its date is confirmed. */
@Serializable
@Entity(tableName = "shifts", indices = [Index("profileId")])
data class Shift(
    @PrimaryKey val id: String,
    val profileId: String,
    /** The day the hours were worked; a payday covers every shift dated on or before it. */
    val date: LocalDate,
    /** Hours, not money. */
    val hours: Double,
    /** Per hour, editable per shift. */
    val rate: Money,
)

/** Statement import's memory, one per Profile (SPEC R26). */
@Serializable
@Entity(tableName = "import_state")
data class ImportState(
    @PrimaryKey val profileId: String,
    /**
     * Rows already recorded: each recorded row appends `fit:<FITID>` then `fsyn:date|amount|description`
     * when it has a FITID, else `syn:date|amount|description`, so twins of one day are counted (SPEC
     * R26). Keys kept before every row appended its own may hold a synthetic key once for several
     * rows, as `syn:` after a FITID key; StatementImport.keptRows reads both.
     */
    val importedKeys: List<String> = emptyList(),
    /** Normalised payee to category id, learned from the user's filing. */
    val payeeRules: Map<String, String> = emptyMap(),
    val importReminderEnabled: Boolean = false,
    val importReminderDay: Int = 1,
    val lastImportDate: LocalDate? = null,
)

/** App-wide, above the Profiles: which Profile is open, the storage version, backup dates. */
@Serializable
@Entity(tableName = "app_state")
data class AppState(
    @PrimaryKey val id: Int = 1,
    val schemaVersion: Int = SCHEMA_VERSION,
    val activeProfileId: String,
    /** More than one Profile, with a switcher (SPEC R18: off on a fresh install). */
    val profilesEnabled: Boolean = false,
    val lastBackupExportDate: LocalDate? = null,
    val lastBackupImportDate: LocalDate? = null,
    /**
     * The Profile that holds the receipt folder, null while none does (SPEC R28, R112). Tesoriere's
     * own; part of schema 1, still edited in place.
     */
    val receiptProfileId: String? = null,
)

/**
 * The app lock, one row while a PIN is set and none otherwise. Device-only: not @Serializable,
 * so it can never reach a backup file. The PIN is kept only as a salted hash.
 */
@Entity(tableName = "app_lock")
data class AppLock(
    @PrimaryKey val id: Int = 1,
    /** Base64 of the random salt and of the derived hash. */
    val salt: String,
    val hash: String,
    /** Stored so the cost can be raised later without locking anyone out. */
    val iterations: Int,
    /** The digit count, so the gate checks once, on the last digit. */
    val pinLength: Int,
    /** The user's own hint, optional, never the PIN itself. */
    val hint: String? = null,
    val lockAfter: String = "one_minute",
    /** Wrong PINs since the last right one; kept so restarting the app cannot skip a wait. */
    val failedAttempts: Int = 0,
)

/**
 * The automatic copy, one row once it has been set up; off while `every` is `off` (SPEC R18).
 * Device-only like [AppLock]: a folder on this device and a password only this device's key
 * store can read mean nothing anywhere else.
 */
@Entity(tableName = "copy_schedule")
data class CopySchedule(
    @PrimaryKey val id: Int = 1,
    val every: String = "off",
    /** The platform's handle on the chosen folder and the name to show for it. */
    val folder: String? = null,
    val folderLabel: String? = null,
    /** The copies' password, sealed by the platform's key store; null for plain copies. */
    val sealedPassword: String? = null,
    /** Required with a password; travels in each file unencrypted. */
    val hint: String? = null,
    /** The day of the last copy written, ISO; null before the first. */
    val lastCopy: String? = null,
    /** Why the last attempt failed; null after a success or before any attempt. */
    val lastError: String? = null,
    /** The failure notice was shown for the current run of failures; cleared by the next success. */
    val failureNotified: Boolean = false,
)

/**
 * One deleted thing in the Trash, kept 30 days (SPEC R18, R121). [payload] is JSON (Trash.encode): what the delete
 * removed and what a restore needs (SPEC R122). Device-only: a backup carries what exists, not what was deleted.
 */
@Entity(tableName = "trash", indices = [Index("profileId")])
data class TrashItem(
    @PrimaryKey val id: String,
    /** The Profile the item belonged to; for a deleted Profile, its own id. */
    val profileId: String,
    val kind: String,
    val name: String,
    /** ISO day of the delete; it goes for good 30 days on. */
    val deletedOn: String,
    val payload: String,
    /** The 20-day notice has been given for it. */
    val warned: Boolean = false,
)

/**
 * The storage version, carried by the database and the backup file. Version 1 is edited in place
 * until the first install on the owner's device; from then on every change bumps it and adds a
 * migration.
 */
const val SCHEMA_VERSION = 1
