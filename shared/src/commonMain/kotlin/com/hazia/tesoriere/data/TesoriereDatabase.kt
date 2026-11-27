package com.hazia.tesoriere.data

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteDriver
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AppLock
import com.hazia.tesoriere.model.AppState
import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.CopySchedule
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.ImportState
import com.hazia.tesoriere.model.Profile
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.RecurringExpense
import com.hazia.tesoriere.model.SCHEMA_VERSION
import com.hazia.tesoriere.model.Savings
import com.hazia.tesoriere.model.Settings
import com.hazia.tesoriere.model.Shift
import com.hazia.tesoriere.model.Transaction
import com.hazia.tesoriere.model.TrashItem
import com.hazia.tesoriere.payments.Payment
import com.hazia.tesoriere.receipts.ReceiptSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import androidx.room.Transaction as RoomTransaction

/*
 * Ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/data/SciFinDatabase.kt at 2892110.
 *
 * The store (SPEC R141): one database, every table keyed by `profileId`, one schema version shared
 * with the backup file (SPEC R130, R142), exported to `shared/schemas/` so a migration can always
 * be written from it.
 *
 * Changed from Sci-Fin:
 * - Tesoriere's tables: `payments` (parts, receipt lines and bank keys as JSON columns) and
 *   `receipt_settings`, read and written with the rest of a Profile.
 * - A value that cannot be read leaves out only the record that holds it (SPEC R141): each list is
 *   read whole and, when one of its rows cannot be read, again row by row ([readable]); a Profile's
 *   settings or import memory that cannot be read take their defaults. Sci-Fin threw.
 * - A write never deletes what it could not read (SPEC R141): [TesoriereDao.save] writes again every
 *   row it can read, from the Profile given, and leaves a row it cannot read as stored; settings,
 *   import memory or receipt settings that cannot be read are kept while the Profile given holds only
 *   their defaults. Sci-Fin deleted and inserted every row.
 * - A record whose id another Profile holds refuses the write ([ProfileProblem.ID_IN_USE]); rows are
 *   inserted with ABORT, never REPLACE, so a clash cannot take over another Profile's row.
 * - Every list reads in the order it was written (`rowid`), so a Profile saved and read back is the
 *   same value, its lists in the engine's order; Sci-Fin ordered the ledger by date.
 * - Sci-Fin's destructive fallback beneath the migrations is left out: a store is never emptied to
 *   open it, and one that cannot be migrated stays as it was, beside its snapshot (SPEC R142, R145).
 *   `eraseAll`, the forgotten-PIN erase (SPEC R147), deletes every row of every table, read or not,
 *   the copy schedule and the Trash included; Sci-Fin deleted Profile by Profile, by id.
 * - The builders are internal and run behind the guard (`Integrity.open`, SPEC R144, R145), which
 *   opens the first connection itself, so the migrations run where a failure gives a code.
 * - Section references are replaced by SPEC rule numbers.
 */
@Database(
    entities = [
        Profile::class, Savings::class, Goal::class, Category::class, RecurringExpense::class, Account::class,
        Transaction::class, Settings::class, ImportState::class, Shift::class, Payment::class, ReceiptSettings::class,
        AppState::class, AppLock::class, CopySchedule::class, TrashItem::class,
    ],
    version = SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(TesoriereDatabaseConstructor::class)
abstract class TesoriereDatabase : RoomDatabase() {
    // internal: every write from outside goes through ProfileRepository and Backup.apply, which start from what is stored (SPEC R141)
    internal abstract fun dao(): TesoriereDao
}

@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object TesoriereDatabaseConstructor : RoomDatabaseConstructor<TesoriereDatabase> {
    override fun initialize(): TesoriereDatabase
}

/**
 * Shared tail of both platform builders: the driver, the IO context and every migration (SPEC R142).
 * Internal: the app reaches the store only through the guard (SPEC R144, R145).
 */
internal fun RoomDatabase.Builder<TesoriereDatabase>.finishBuilding(driver: SQLiteDriver): TesoriereDatabase =
    setDriver(driver)
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*TESORIERE_MIGRATIONS)
        .build()

/**
 * The database built and its first connection opened, which is what runs the migrations, in one
 * transaction Room rolls back when one fails (SPEC R145); on a failure the database is closed again
 * and the failure goes on to the guard, which gives its code.
 */
internal suspend fun RoomDatabase.Builder<TesoriereDatabase>.openFirstConnection(driver: SQLiteDriver): TesoriereDatabase {
    val db = finishBuilding(driver)
    try {
        db.useWriterConnection { }
        return db
    } catch (e: Throwable) {
        db.close()
        throw e
    }
}

/**
 * [all], or when one of its rows holds a value that cannot be read (SPEC R141), every row of [ids]
 * that [one] can read, in the same order.
 */
private suspend fun <T> readable(
    all: suspend () -> List<T>, ids: suspend () -> List<String>, one: suspend (String) -> T?, ok: (T) -> Boolean = { true },
): List<T> =
    try {
        all().filter(ok)
    } catch (_: UnreadableStoredValue) {
        ids().mapNotNull { id ->
            try {
                one(id)?.takeIf(ok)
            } catch (_: UnreadableStoredValue) {
                null
            }
        }
    }

/**
 * The ids of [ids] whose row [one] cannot read, or reads holding a figure [ok] refuses (SPEC R141);
 * none while [all] reads and [ok] takes every row.
 */
private suspend fun <T> unreadable(
    all: suspend () -> List<T>, ids: suspend () -> List<String>, one: suspend (String) -> T?, ok: (T) -> Boolean = { true },
): List<String> {
    val whole = try {
        all()
    } catch (_: UnreadableStoredValue) {
        null
    }
    if (whole != null && whole.all(ok)) return emptyList()
    return ids().filter { id ->
        try {
            one(id)?.let { !ok(it) } ?: false
        } catch (_: UnreadableStoredValue) {
            true
        }
    }
}

/*
 * Rates and hours a record holds as infinity cannot be read either (SPEC R141): SQLite keeps an
 * infinity, which no backup file can carry and no figure can be worked out from, and keeps no number
 * as nothing. Such a record reads as missing, is named, and is kept as stored, as one that cannot be
 * read is; a write holding one is refused ([ProfileProblem.NOT_FINITE]).
 */
private fun Shift.finite() = hours.isFinite()
private fun Transaction.finite() = hoursWorked?.isFinite() ?: true
private fun Account.finite() = (apr?.isFinite() ?: true) && (interestRate?.isFinite() ?: true)

/** Whether every rate and hours figure [d] holds is a finite number, as the store keeps them (SPEC R141). */
internal fun finiteFigures(d: ProfileData): Boolean =
    d.shifts.all { it.finite() } && d.transactions.all { it.finite() } && d.accounts.all { it.finite() }

/** [read]'s record, or null when it holds a value that cannot be read (SPEC R141). */
private suspend fun <T> readableOrNull(read: suspend () -> T?): T? =
    try {
        read()
    } catch (_: UnreadableStoredValue) {
        null
    }

/** Whether a single row is stored ([rows] above 0) that [read] cannot read (SPEC R141). */
private suspend fun <T> unreadableRow(rows: Int, read: suspend () -> T?): Boolean = rows > 0 && readableOrNull(read) == null

/** Whether another Profile holds any of [ids], asked a few hundred at a time to stay under SQLite's limit on arguments. */
private suspend fun taken(ids: List<String>, elsewhere: suspend (List<String>) -> List<String>): Boolean =
    ids.chunked(500).any { elsewhere(it).isNotEmpty() }

/**
 * One DAO, because the unit of work is a whole [ProfileData]: the engine reads a Profile in full
 * and writes it back in full. Per-table queries serve the few places that need less.
 */
@Dao
internal interface TesoriereDao {
    @Query("SELECT * FROM profiles ORDER BY name, id") suspend fun profiles(): List<Profile>
    @Query("SELECT * FROM profiles WHERE id = :id") suspend fun profile(id: String): Profile?
    @Query("SELECT * FROM app_state WHERE id = 1") suspend fun appStateRow(): AppState?
    @Upsert suspend fun upsertAppState(s: AppState)
    @Query("SELECT * FROM app_lock WHERE id = 1") suspend fun appLock(): AppLock?
    @Upsert suspend fun upsertAppLock(l: AppLock)
    @Query("SELECT * FROM copy_schedule WHERE id = 1") suspend fun copySchedule(): CopySchedule?
    @Upsert suspend fun upsertCopySchedule(c: CopySchedule)

    @Query("SELECT * FROM trash ORDER BY deletedOn DESC, id DESC") suspend fun trash(): List<TrashItem>
    @Query("SELECT * FROM trash WHERE id = :id") suspend fun trashItem(id: String): TrashItem?
    @Insert suspend fun insertTrashItem(t: TrashItem)
    @Upsert suspend fun upsertTrash(t: TrashItem)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTrash(t: List<TrashItem>)
    @Query("DELETE FROM trash WHERE id = :id") suspend fun deleteTrash(id: String)
    @Query("UPDATE trash SET warned = 1 WHERE id IN (:ids)") suspend fun markWarned(ids: List<String>)
    @Query("DELETE FROM trash") suspend fun emptyTrash()

    @Query("SELECT * FROM savings WHERE profileId = :id") suspend fun savings(id: String): Savings?
    @Query("SELECT * FROM settings WHERE profileId = :id") suspend fun settings(id: String): Settings?
    @Query("SELECT COUNT(*) FROM settings WHERE profileId = :id") suspend fun settingsRows(id: String): Int
    @Query("SELECT * FROM import_state WHERE profileId = :id") suspend fun importState(id: String): ImportState?
    @Query("SELECT COUNT(*) FROM import_state WHERE profileId = :id") suspend fun importStateRows(id: String): Int
    @Query("SELECT * FROM receipt_settings WHERE profileId = :id") suspend fun receiptSettings(id: String): ReceiptSettings?
    @Query("SELECT COUNT(*) FROM receipt_settings WHERE profileId = :id") suspend fun receiptSettingsRows(id: String): Int

    @Query("SELECT * FROM goals WHERE profileId = :id ORDER BY rowid") suspend fun goals(id: String): List<Goal>
    @Query("SELECT id FROM goals WHERE profileId = :id ORDER BY rowid") suspend fun goalIds(id: String): List<String>
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun goal(id: String): Goal?
    @Query("SELECT id FROM goals WHERE profileId != :id AND id IN (:ids)") suspend fun goalsElsewhere(id: String, ids: List<String>): List<String>
    @Query("DELETE FROM goals WHERE profileId = :id AND id NOT IN (:keep)") suspend fun deleteGoalsExcept(id: String, keep: List<String>)
    @Query("SELECT * FROM categories WHERE profileId = :id ORDER BY rowid") suspend fun categories(id: String): List<Category>
    @Query("SELECT id FROM categories WHERE profileId = :id ORDER BY rowid") suspend fun categoryIds(id: String): List<String>
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun category(id: String): Category?
    @Query("SELECT id FROM categories WHERE profileId != :id AND id IN (:ids)") suspend fun categoriesElsewhere(id: String, ids: List<String>): List<String>
    @Query("DELETE FROM categories WHERE profileId = :id AND id NOT IN (:keep)") suspend fun deleteCategoriesExcept(id: String, keep: List<String>)
    @Query("SELECT * FROM recurring_expenses WHERE profileId = :id ORDER BY rowid") suspend fun recurringExpenses(id: String): List<RecurringExpense>
    @Query("SELECT id FROM recurring_expenses WHERE profileId = :id ORDER BY rowid") suspend fun recurringExpenseIds(id: String): List<String>
    @Query("SELECT * FROM recurring_expenses WHERE id = :id") suspend fun recurringExpense(id: String): RecurringExpense?
    @Query("SELECT id FROM recurring_expenses WHERE profileId != :id AND id IN (:ids)") suspend fun recurringExpensesElsewhere(id: String, ids: List<String>): List<String>
    @Query("DELETE FROM recurring_expenses WHERE profileId = :id AND id NOT IN (:keep)") suspend fun deleteRecurringExpensesExcept(id: String, keep: List<String>)
    @Query("SELECT * FROM accounts WHERE profileId = :id ORDER BY rowid") suspend fun accounts(id: String): List<Account>
    @Query("SELECT id FROM accounts WHERE profileId = :id ORDER BY rowid") suspend fun accountIds(id: String): List<String>
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun account(id: String): Account?
    @Query("SELECT id FROM accounts WHERE profileId != :id AND id IN (:ids)") suspend fun accountsElsewhere(id: String, ids: List<String>): List<String>
    @Query("DELETE FROM accounts WHERE profileId = :id AND id NOT IN (:keep)") suspend fun deleteAccountsExcept(id: String, keep: List<String>)
    @Query("SELECT * FROM transactions WHERE profileId = :id ORDER BY rowid") suspend fun transactions(id: String): List<Transaction>
    @Query("SELECT id FROM transactions WHERE profileId = :id ORDER BY rowid") suspend fun transactionIds(id: String): List<String>
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun transaction(id: String): Transaction?
    @Query("SELECT id FROM transactions WHERE profileId != :id AND id IN (:ids)") suspend fun transactionsElsewhere(id: String, ids: List<String>): List<String>
    @Query("DELETE FROM transactions WHERE profileId = :id AND id NOT IN (:keep)") suspend fun deleteTransactionsExcept(id: String, keep: List<String>)
    @Query("SELECT * FROM shifts WHERE profileId = :id ORDER BY rowid") suspend fun shifts(id: String): List<Shift>
    @Query("SELECT id FROM shifts WHERE profileId = :id ORDER BY rowid") suspend fun shiftIds(id: String): List<String>
    @Query("SELECT * FROM shifts WHERE id = :id") suspend fun shift(id: String): Shift?
    @Query("SELECT id FROM shifts WHERE profileId != :id AND id IN (:ids)") suspend fun shiftsElsewhere(id: String, ids: List<String>): List<String>
    @Query("DELETE FROM shifts WHERE profileId = :id AND id NOT IN (:keep)") suspend fun deleteShiftsExcept(id: String, keep: List<String>)
    @Query("SELECT * FROM payments WHERE profileId = :id ORDER BY rowid") suspend fun payments(id: String): List<Payment>
    @Query("SELECT id FROM payments WHERE profileId = :id ORDER BY rowid") suspend fun paymentIds(id: String): List<String>
    @Query("SELECT * FROM payments WHERE id = :id") suspend fun payment(id: String): Payment?
    @Query("SELECT id FROM payments WHERE profileId != :id AND id IN (:ids)") suspend fun paymentsElsewhere(id: String, ids: List<String>): List<String>
    @Query("DELETE FROM payments WHERE profileId = :id AND id NOT IN (:keep)") suspend fun deletePaymentsExcept(id: String, keep: List<String>)

    @Upsert suspend fun upsertProfile(p: Profile)
    @Upsert suspend fun upsertSavings(s: Savings)
    @Upsert suspend fun upsertSettings(s: Settings)
    @Upsert suspend fun upsertImportState(s: ImportState)
    @Upsert suspend fun upsertReceiptSettings(s: ReceiptSettings)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGoals(g: List<Goal>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCategories(c: List<Category>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRecurringExpenses(e: List<RecurringExpense>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAccounts(a: List<Account>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTransactions(t: List<Transaction>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertShifts(s: List<Shift>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPayments(p: List<Payment>)

    @Query("DELETE FROM goals WHERE profileId = :id") suspend fun deleteGoals(id: String)
    @Query("DELETE FROM categories WHERE profileId = :id") suspend fun deleteCategories(id: String)
    @Query("DELETE FROM recurring_expenses WHERE profileId = :id") suspend fun deleteRecurringExpenses(id: String)
    @Query("DELETE FROM accounts WHERE profileId = :id") suspend fun deleteAccounts(id: String)
    @Query("DELETE FROM transactions WHERE profileId = :id") suspend fun deleteTransactions(id: String)
    @Query("DELETE FROM shifts WHERE profileId = :id") suspend fun deleteShifts(id: String)
    @Query("DELETE FROM payments WHERE profileId = :id") suspend fun deletePayments(id: String)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun deleteProfileRow(id: String)
    @Query("DELETE FROM savings WHERE profileId = :id") suspend fun deleteSavings(id: String)
    @Query("DELETE FROM settings WHERE profileId = :id") suspend fun deleteSettings(id: String)
    @Query("DELETE FROM import_state WHERE profileId = :id") suspend fun deleteImportState(id: String)
    @Query("DELETE FROM receipt_settings WHERE profileId = :id") suspend fun deleteReceiptSettings(id: String)

    /** The app's own state; a row that cannot be read reads as none (SPEC R141). */
    suspend fun appState(): AppState? = readableOrNull { appStateRow() }

    /**
     * The whole world of one Profile, or null when there is no such Profile. A record holding a
     * value that cannot be read is left out, and settings or import memory that cannot be read take
     * their defaults (SPEC R141); nothing stored makes it throw.
     */
    @RoomTransaction
    suspend fun load(id: String): ProfileData? {
        val p = profile(id) ?: return null
        return ProfileData(
            profile = p,
            savings = savings(id) ?: Savings(id, null),
            settings = readableOrNull { settings(id) } ?: Settings(profileId = id),
            importState = readableOrNull { importState(id) } ?: ImportState(profileId = id),
            goals = readable({ goals(id) }, { goalIds(id) }, { goal(it) }),
            categories = readable({ categories(id) }, { categoryIds(id) }, { category(it) }),
            recurringExpenses = readable({ recurringExpenses(id) }, { recurringExpenseIds(id) }, { recurringExpense(it) }),
            accounts = readable({ accounts(id) }, { accountIds(id) }, { account(it) }, { it.finite() }),
            transactions = readable({ transactions(id) }, { transactionIds(id) }, { transaction(it) }, { it.finite() }),
            shifts = readable({ shifts(id) }, { shiftIds(id) }, { shift(it) }, { it.finite() }),
            payments = readable({ payments(id) }, { paymentIds(id) }, { payment(it) }),
            receiptSettings = readableOrNull { receiptSettings(id) },
        )
    }

    /**
     * Every record of Profile [id] the store holds but cannot read (SPEC R130, R141): the lists'
     * first, by kind and in their order, then its settings, import memory and receipt settings.
     */
    suspend fun unreadableRecords(id: String): List<UnreadableRecord> {
        val out = ArrayList<UnreadableRecord>()
        fun list(kind: StoredKind, ids: List<String>) = ids.forEach { out += UnreadableRecord(id, kind, it) }
        fun row(kind: StoredKind, unreadable: Boolean) { if (unreadable) out += UnreadableRecord(id, kind, null) }
        // a Goal's and a category's fields that may fail to read hold nothing when they do (SPEC R141), so they are never named
        list(StoredKind.RECURRING_EXPENSE, unreadable({ recurringExpenses(id) }, { recurringExpenseIds(id) }, { recurringExpense(it) }))
        list(StoredKind.ACCOUNT, unreadable({ accounts(id) }, { accountIds(id) }, { account(it) }, { it.finite() }))
        list(StoredKind.TRANSACTION, unreadable({ transactions(id) }, { transactionIds(id) }, { transaction(it) }, { it.finite() }))
        list(StoredKind.SHIFT, unreadable({ shifts(id) }, { shiftIds(id) }, { shift(it) }, { it.finite() }))
        list(StoredKind.PAYMENT, unreadable({ payments(id) }, { paymentIds(id) }, { payment(it) }))
        row(StoredKind.SETTINGS, unreadableRow(settingsRows(id)) { settings(id) })
        row(StoredKind.IMPORT_MEMORY, unreadableRow(importStateRows(id)) { importState(id) })
        row(StoredKind.RECEIPT_SETTINGS, unreadableRow(receiptSettingsRows(id)) { receiptSettings(id) })
        return out
    }

    /**
     * [d] written in one transaction (SPEC R141): each list's readable rows written again from [d],
     * in its order, so a row the engine removed is gone; a row that cannot be read is left as stored,
     * unless [d] holds one of its id. Refused with ID_TWICE when [d] holds one id twice in one kind of
     * record. Settings, import memory or receipt settings that cannot be read
     * stay as stored while [d] holds only their defaults, or none. Refused with ID_IN_USE, writing
     * nothing, when another Profile holds the id of one of [d]'s records. The Profile row is written
     * too, so this also adds a new Profile; whether one may be written is the caller's.
     */
    @RoomTransaction
    suspend fun save(d: ProfileData): ProfileProblem? {
        val id = d.profileId
        val twice = listOf(
            d.goals.map { it.id }, d.categories.map { it.id }, d.recurringExpenses.map { it.id }, d.accounts.map { it.id },
            d.transactions.map { it.id }, d.shifts.map { it.id }, d.payments.map { it.id },
        ).any { it.size != it.toSet().size }
        if (twice) return ProfileProblem.ID_TWICE
        // SQLite would keep an infinity no file can carry, and no number as nothing, which a column
        // that must hold one refuses mid-write (SPEC R141)
        if (!finiteFigures(d)) return ProfileProblem.NOT_FINITE
        val clash = taken(d.goals.map { it.id }) { goalsElsewhere(id, it) } ||
            taken(d.categories.map { it.id }) { categoriesElsewhere(id, it) } ||
            taken(d.recurringExpenses.map { it.id }) { recurringExpensesElsewhere(id, it) } ||
            taken(d.accounts.map { it.id }) { accountsElsewhere(id, it) } ||
            taken(d.transactions.map { it.id }) { transactionsElsewhere(id, it) } ||
            taken(d.shifts.map { it.id }) { shiftsElsewhere(id, it) } ||
            taken(d.payments.map { it.id }) { paymentsElsewhere(id, it) }
        if (clash) return ProfileProblem.ID_IN_USE

        upsertProfile(d.profile)
        upsertSavings(d.savings.copy(profileId = id))
        val ownSettings = d.settings.copy(profileId = id)
        if (ownSettings != Settings(profileId = id) || !unreadableRow(settingsRows(id)) { settings(id) }) upsertSettings(ownSettings)
        val ownImports = d.importState.copy(profileId = id)
        if (ownImports != ImportState(profileId = id) || !unreadableRow(importStateRows(id)) { importState(id) }) upsertImportState(ownImports)
        when (val r = d.receiptSettings) {
            null -> if (!unreadableRow(receiptSettingsRows(id)) { receiptSettings(id) }) deleteReceiptSettings(id)
            else -> upsertReceiptSettings(r.copy(profileId = id))
        }

        val inGoals = d.goals.mapTo(HashSet()) { it.id }
        deleteGoalsExcept(id, unreadable({ goals(id) }, { goalIds(id) }, { goal(it) }).filter { it !in inGoals })
        insertGoals(d.goals.map { it.copy(profileId = id) })
        val inCategories = d.categories.mapTo(HashSet()) { it.id }
        deleteCategoriesExcept(id, unreadable({ categories(id) }, { categoryIds(id) }, { category(it) }).filter { it !in inCategories })
        insertCategories(d.categories.map { it.copy(profileId = id) })
        val inExpenses = d.recurringExpenses.mapTo(HashSet()) { it.id }
        deleteRecurringExpensesExcept(id, unreadable({ recurringExpenses(id) }, { recurringExpenseIds(id) }, { recurringExpense(it) }).filter { it !in inExpenses })
        insertRecurringExpenses(d.recurringExpenses.map { it.copy(profileId = id) })
        val inAccounts = d.accounts.mapTo(HashSet()) { it.id }
        deleteAccountsExcept(id, unreadable({ accounts(id) }, { accountIds(id) }, { account(it) }, { it.finite() }).filter { it !in inAccounts })
        insertAccounts(d.accounts.map { it.copy(profileId = id) })
        val inTransactions = d.transactions.mapTo(HashSet()) { it.id }
        deleteTransactionsExcept(id, unreadable({ transactions(id) }, { transactionIds(id) }, { transaction(it) }, { it.finite() }).filter { it !in inTransactions })
        insertTransactions(d.transactions.map { it.copy(profileId = id) })
        val inShifts = d.shifts.mapTo(HashSet()) { it.id }
        deleteShiftsExcept(id, unreadable({ shifts(id) }, { shiftIds(id) }, { shift(it) }, { it.finite() }).filter { it !in inShifts })
        insertShifts(d.shifts.map { it.copy(profileId = id) })
        val inPayments = d.payments.mapTo(HashSet()) { it.id }
        deletePaymentsExcept(id, unreadable({ payments(id) }, { paymentIds(id) }, { payment(it) }).filter { it !in inPayments })
        insertPayments(d.payments.map { it.copy(profileId = id) })
        return null
    }

    @Query("DELETE FROM goals") suspend fun deleteAllGoals()
    @Query("DELETE FROM categories") suspend fun deleteAllCategories()
    @Query("DELETE FROM recurring_expenses") suspend fun deleteAllRecurringExpenses()
    @Query("DELETE FROM accounts") suspend fun deleteAllAccounts()
    @Query("DELETE FROM transactions") suspend fun deleteAllTransactions()
    @Query("DELETE FROM shifts") suspend fun deleteAllShifts()
    @Query("DELETE FROM payments") suspend fun deleteAllPayments()
    @Query("DELETE FROM savings") suspend fun deleteAllSavings()
    @Query("DELETE FROM settings") suspend fun deleteAllSettings()
    @Query("DELETE FROM import_state") suspend fun deleteAllImportState()
    @Query("DELETE FROM receipt_settings") suspend fun deleteAllReceiptSettings()
    @Query("DELETE FROM profiles") suspend fun deleteAllProfiles()
    @Query("DELETE FROM app_state") suspend fun deleteAppState()
    @Query("DELETE FROM app_lock") suspend fun deleteAppLock()
    @Query("DELETE FROM copy_schedule") suspend fun deleteCopySchedule()

    /**
     * The forgotten-PIN erase's store part (SPEC R147): every row of every table, read or not, in one
     * transaction, so a row that cannot be read goes too.
     */
    @RoomTransaction
    suspend fun eraseAll() {
        deleteAllGoals(); deleteAllCategories(); deleteAllRecurringExpenses(); deleteAllAccounts()
        deleteAllTransactions(); deleteAllShifts(); deleteAllPayments()
        deleteAllSavings(); deleteAllSettings(); deleteAllImportState(); deleteAllReceiptSettings()
        deleteAllProfiles(); deleteAppState(); deleteAppLock(); deleteCopySchedule(); emptyTrash()
    }

    /** Every record of Profile [id], the Profile included, in one transaction. Its Trash items are the caller's. */
    @RoomTransaction
    suspend fun deleteProfileWhole(id: String) {
        deleteGoals(id); deleteCategories(id); deleteRecurringExpenses(id); deleteAccounts(id)
        deleteTransactions(id); deleteShifts(id); deletePayments(id)
        deleteSavings(id); deleteSettings(id); deleteImportState(id); deleteReceiptSettings(id)
        deleteProfileRow(id)
    }
}
