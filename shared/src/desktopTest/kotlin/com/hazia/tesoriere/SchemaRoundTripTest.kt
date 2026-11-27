@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.hazia.tesoriere.data.TesoriereDatabase
import com.hazia.tesoriere.data.buildTesoriereDatabase
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.AppLock
import com.hazia.tesoriere.model.AppState
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.CopySchedule
import com.hazia.tesoriere.model.DateMode
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.GoalMode
import com.hazia.tesoriere.model.ImportState
import com.hazia.tesoriere.model.IncomeCadence
import com.hazia.tesoriere.model.IncomeType
import com.hazia.tesoriere.model.Profile
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.RateType
import com.hazia.tesoriere.model.RecurringExpense
import com.hazia.tesoriere.model.Savings
import com.hazia.tesoriere.model.Settings
import com.hazia.tesoriere.model.Shift
import com.hazia.tesoriere.model.Transaction
import com.hazia.tesoriere.model.TrashItem
import com.hazia.tesoriere.payments.BankKeys
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.Payment
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.ReceiptLine
import com.hazia.tesoriere.receipts.PickupMode
import com.hazia.tesoriere.receipts.ReceiptSettings
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** A database file in a fresh temp folder under the build directory, never anywhere else. */
internal object TestDb {
    private val root = File("build/tmp/sqlite-tests").apply { mkdirs() }
    fun file(): File = File(createTempDirectory(root.toPath(), "db").toFile(), "tesoriere.db")

    /** [sql] run on [file] straight through SQLite, as damage on disk would leave it, or a trigger a test sets. */
    fun damage(file: File, vararg sql: String) {
        val c = BundledSQLiteDriver().open(file.absolutePath)
        try {
            sql.forEach { c.execSQL(it) }
        } finally {
            c.close()
        }
    }

    /**
     * A store of [version] with [rows] settings rows in its write-ahead log alone and no connection
     * open, as a crash leaves it: the three files copied while their writer was still open, since
     * closing it would fold the log in.
     */
    fun leftWithRowsInLog(version: Int, rows: Int): File {
        val live = file()
        val c = BundledSQLiteDriver().open(live.absolutePath)
        val left = file()
        try {
            c.execSQL("PRAGMA journal_mode=WAL")
            c.execSQL("PRAGMA wal_autocheckpoint=0")
            c.execSQL("CREATE TABLE settings (profileId TEXT PRIMARY KEY, currency TEXT NOT NULL)")
            for (i in 0 until rows) c.execSQL("INSERT INTO settings VALUES ('p$i', 'EUR')")
            c.execSQL("PRAGMA user_version = $version")
            for (suffix in listOf("", "-wal", "-shm")) File(live.path + suffix).copyTo(File(left.path + suffix))
        } finally {
            c.close()
        }
        return left
    }
}

/**
 * A Profile holding every kind of record, written to a database file and read back (SPEC R141).
 * Every name, store, figure and key here is made up.
 */
class SchemaRoundTripTest {
    private val p = "p-rt"
    private val nov10 = LocalDate(2026, 11, 10)
    private val at = Instant.parse("2026-11-10T09:30:00Z")

    private fun everything(): ProfileData = ProfileData(
        profile = Profile(p, "Round trip"),
        savings = Savings(p, cents("1,234.56")),
        settings = Settings(
            profileId = p, currency = "EUR", trendMonths = 12, incomeEnabled = true, accountsEnabled = true, income = cents("1,450.00"),
            incomeCadence = IncomeCadence.SEMIMONTHLY, incomePayDate = LocalDate(2026, 11, 15), incomePayDate2 = LocalDate(2026, 11, 30),
            incomeAutoDeposit = true, incomeEnabledDate = LocalDate(2026, 1, 1), lastIncomeDepositDate = nov10, lastIncomeLoggedDate = nov10,
            newMonthNotificationEnabled = false, notificationPermissionGranted = true, savingsVsOwedEnabled = true, scienceEnabled = true,
            overtimeEnabled = true, onboardingComplete = true, onboardingStep = 4, goalPromptDismissed = true, lastMonthProcessed = "2026-11",
            catchUpSummaryPending = true, catchUpDepositCount = 2, catchUpDepositTotal = cents("2,900.00"), catchUpMonths = 2,
            catchUpSwept = cents("90.00"), catchUpEnded = "gym\tended\tGym", incomeType = IncomeType.HOURLY, hourlyRate = cents("12.50"),
            lastPaydayHandled = nov10, paydayNotificationEnabled = false, language = "it",
        ),
        importState = ImportState(
            p, importedKeys = listOf("fit:F1", "fsyn:2026-11-10|-42.30|MERCATO LUME"), payeeRules = mapOf("mercato lume" to "groc"),
            importReminderEnabled = true, importReminderDay = 5, lastImportDate = nov10,
        ),
        goals = listOf(
            Goal("trip", p, "Trip", icon = "plane", saved = cents("300.00"), target = cents("900.00"), targetDate = LocalDate(2027, 6, 1), mode = GoalMode.DATE),
            Goal("jar", p, "Jar", saved = cents("40.00"), mode = GoalMode.AMOUNT, monthly = cents("20.00")),
        ),
        categories = listOf(Category("groc", p, "Groceries", planned = cents("300.00")), Category("home", p, "Household", icon = "home")),
        recurringExpenses = listOf(
            RecurringExpense(
                "rent", p, "Rent", icon = "key", amount = cents("950.00"), cadence = Cadence.MONTHLY, categoryId = "home",
                firstDueDate = LocalDate(2026, 1, 1), dueDate = LocalDate(2026, 12, 1), dueDateAnchorDay = 1, dateMode = DateMode.FIXED_DATE,
                reminderEnabled = true, reminderLeadDays = 7, lastSweptMonth = "2026-11", createdOn = LocalDate(2025, 12, 20),
            ),
            RecurringExpense(
                "ins", p, "Insurance", amount = cents("600.00"), cadence = Cadence.YEARLY, categoryId = "home",
                firstDueDate = LocalDate(2026, 4, 1), dueDate = LocalDate(2027, 4, 1), dateMode = DateMode.INTERVAL, intervalDays = 365,
                apportionedShare = cents("50.00"), endDate = LocalDate(2030, 4, 1), linkedAccountId = "loan",
                dueDate2 = LocalDate(2027, 4, 15), finishedAt = LocalDate(2030, 4, 2),
            ),
        ),
        accounts = listOf(
            Account("card", p, "Card", type = AccountType.CREDIT, balance = cents("140.00"), apr = 0.219, chargesNotLogged = true,
                payoffCategoryId = "groc", feesCategoryId = "home"),
            Account("loan", p, "Loan", type = AccountType.LOAN, balance = cents("4,000.00"), linkedRecurringExpenseId = "ins",
                autoUpdateFromPayments = true, principal = cents("5,000.00"), interestRate = 0.05, termMonths = 48,
                rateType = RateType.FIXED, originationDate = LocalDate(2025, 1, 1), loanCategoryId = "home"),
        ),
        transactions = listOf(
            Transaction("t2", p, nov10, at, payee = "Shop", amount = cents("-12.00"), key = "acct:card", targetLabel = "Card",
                categoryId = "home", groupId = "t2"),
            Transaction("t1", p, LocalDate(2026, 11, 1), at, amount = cents("-950.00"), key = "rec:rent", targetLabel = "Rent",
                categoryId = "home", accountPayoff = true, share = true),
            Transaction("t3", p, nov10, at, amount = cents("150.00"), key = "savings", targetLabel = "Savings", isIncome = true,
                hoursWorked = 12.0, rate = cents("12.50")),
        ),
        shifts = listOf(Shift("s1", p, nov10, 7.5, cents("12.50"))),
        payments = listOf(
            Payment(
                "pay-r", p, nov10, at, cents("42.30"), PaymentSource.Savings, "Mercato Lume",
                listOf(Part("groc", cents("30.00")), Part("home", cents("12.30"))), receiptId = "rc-1",
                receiptLines = listOf(
                    ReceiptLine("bread", null, 1, cents("30.00"), "ch-groc", "Groceries", "groc"),
                    ReceiptLine("soap", "3 pcs", 2, cents("12.30"), "ch-home", "Household", "home"),
                ),
                bankKeys = BankKeys("F1", "syn:2026-11-10|-42.30|MERCATO LUME"),
            ),
            Payment(
                "pay-g", p, nov10, at, cents("10.00"), PaymentSource.Goal("jar"), null,
                listOf(Part("groc", cents("10.20")), Part("groc", cents("-0.20"), difference = true)), fromSavings = cents("2.00"),
            ),
            Payment(
                "pay-c", p, nov10, at, cents("-5.00"), PaymentSource.Account("card"), "Refund", listOf(Part(null, cents("-5.00"))),
                bankKeys = BankKeys(null, "syn:2026-11-10|5.00|REFUND"),
            ),
        ),
        receiptSettings = ReceiptSettings(
            p, folder = "folder-handle-1", pickup = PickupMode.DAILY_CHECK, recordedReceiptIds = listOf("rc-1"),
            categoryLinks = mapOf("ch-groc" to "groc"), storeSources = mapOf("Mercato Lume" to "savings"), storeLinks = mapOf("Mercato Lume" to "mercato lume"),
        ),
    )

    private fun <T> withDb(file: File, block: suspend (TesoriereDatabase) -> T): T {
        val db = buildTesoriereDatabase(file)
        try {
            return runBlocking { block(db) }
        } finally {
            db.close()
        }
    }

    private fun damage(file: File, vararg sql: String) = TestDb.damage(file, *sql)

    // SPEC:R141 SPEC:R21 SPEC:R112
    @Test fun `R141 a Profile holding every record, a split 42_30 receipt payment with its keys included, reads back from a file as written`() {
        val file = TestDb.file()
        val d = everything()
        val app = AppState(activeProfileId = p, profilesEnabled = true, lastBackupExportDate = nov10, receiptProfileId = p)
        val lock = AppLock(salt = "c2FsdA==", hash = "aGFzaA==", iterations = 600_000, pinLength = 6, hint = "made up", lockAfter = "immediately", failedAttempts = 2)
        val copy = CopySchedule(
            every = "weekly", folder = "copy-handle", folderLabel = "Copies", sealedPassword = "c2VhbGVk", hint = "the usual",
            lastCopy = "2026-11-09", lastError = "FOLDER_GONE", failureNotified = true,
        )
        val item = TrashItem("tr-1", p, "goal", "Car", "2026-11-12", """{"goals":[]}""", warned = true)
        withDb(file) { db ->
            db.dao().save(d)
            db.dao().upsertAppState(app); db.dao().upsertAppLock(lock); db.dao().upsertCopySchedule(copy); db.dao().upsertTrash(item)
        }
        withDb(file) { db ->
            assertEquals(d, db.dao().load(p))
            assertEquals(app, db.dao().appState())
            assertEquals(lock, db.dao().appLock())
            assertEquals(copy, db.dao().copySchedule())
            assertEquals(listOf(item), db.dao().trash())
        }
    }

    // SPEC:R141
    @Test fun `R141 a Profile saved again with fewer records and no receipt settings reads back with exactly those`() {
        val file = TestDb.file()
        val less = everything().let { it.copy(goals = it.goals.take(1), payments = emptyList(), transactions = it.transactions.drop(1), receiptSettings = null) }
        withDb(file) { db -> db.dao().save(everything()); db.dao().save(less) }
        withDb(file) { db -> assertEquals(less, db.dao().load(p)) }
    }

    // SPEC:R141
    @Test fun `R141 a mode stored as sideways reads as none, a cadence stored as sometimes leaves its expense out, and nothing throws`() {
        val file = TestDb.file()
        withDb(file) { db -> db.dao().save(everything()); db.dao().upsertAppState(AppState(activeProfileId = p, lastBackupImportDate = nov10)) }
        damage(
            file,
            "UPDATE goals SET mode = 'sideways' WHERE id = 'trip'",
            "UPDATE recurring_expenses SET cadence = 'sometimes' WHERE id = 'ins'",
            "UPDATE recurring_expenses SET endDate = 'soon' WHERE id = 'rent'",
            "UPDATE payments SET parts = '[{oops' WHERE id = 'pay-g'",
            "UPDATE payments SET source = 'pocket' WHERE id = 'pay-c'",
            "UPDATE payments SET bankKeys = 'nope' WHERE id = 'pay-r'",
            "UPDATE transactions SET date = 'yesterday' WHERE id = 't1'",
            "UPDATE settings SET incomeCadence = 'never' WHERE profileId = 'p-rt'",
            "UPDATE import_state SET importedKeys = 'not a list' WHERE profileId = 'p-rt'",
            "UPDATE receipt_settings SET pickup = 'hourly' WHERE profileId = 'p-rt'",
            "UPDATE app_state SET lastBackupImportDate = 'never'",
        )
        val d0 = everything()
        withDb(file) { db ->
            val d = db.dao().load(p)!!
            assertEquals(listOf(d0.goals[0].copy(mode = null), d0.goals[1]), d.goals)
            assertEquals(listOf(d0.recurringExpenses[0].copy(endDate = null)), d.recurringExpenses)
            assertEquals(listOf(d0.payments[0].copy(bankKeys = null)), d.payments)
            assertEquals(listOf("t2", "t3"), d.transactions.map { it.id })
            assertEquals(Settings(profileId = p), d.settings)
            assertEquals(ImportState(profileId = p), d.importState)
            assertNull(d.receiptSettings)
            assertEquals(d0.accounts, d.accounts)
            assertNull(db.dao().appState()!!.lastBackupImportDate)
        }
    }
}
