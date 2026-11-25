@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.goal
import com.hazia.tesoriere.Fixture.line
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Fixture.receipt
import com.hazia.tesoriere.Fixture.recurring
import com.hazia.tesoriere.engine.Accounts
import com.hazia.tesoriere.engine.CategoryEditing
import com.hazia.tesoriere.engine.Ledger
import com.hazia.tesoriere.engine.Movement
import com.hazia.tesoriere.engine.NewMonth
import com.hazia.tesoriere.engine.Recurring
import com.hazia.tesoriere.engine.Reports
import com.hazia.tesoriere.engine.RestoreProblem
import com.hazia.tesoriere.engine.Slice
import com.hazia.tesoriere.engine.SliceKind
import com.hazia.tesoriere.engine.TransactionKey
import com.hazia.tesoriere.engine.Trash
import com.hazia.tesoriere.engine.TrashPayload
import com.hazia.tesoriere.engine.TrashTidy
import com.hazia.tesoriere.engine.savingsAmount
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.AppState
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.model.TrashItem
import com.hazia.tesoriere.pairing.Pairing
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.PaymentDraft
import com.hazia.tesoriere.payments.PaymentNotice
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Payments
import com.hazia.tesoriere.payments.Spent
import com.hazia.tesoriere.receipts.ParseResult
import com.hazia.tesoriere.receipts.ReceiptFile
import com.hazia.tesoriere.receipts.ReceiptFolder
import com.hazia.tesoriere.receipts.ReceiptPickup
import com.hazia.tesoriere.receipts.ReceiptProblem
import com.hazia.tesoriere.receipts.Receipts
import com.hazia.tesoriere.receipts.ReviewNote
import com.hazia.tesoriere.statements.BankRow
import com.hazia.tesoriere.statements.RowStatus
import com.hazia.tesoriere.statements.StatementImport
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

/**
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/TrashTest.kt at 2892110.
 * Changed: the store and session cases (Sci-Fin's AppStore and AppSession) become engine cases,
 * since storage is a later task: the notice and the expiry through Trash.tidy, a deleted Profile
 * through Trash.deleteProfile and Trash.restoreProfile; restoring a backup, which empties the Trash
 * in Sci-Fin, waits for the backup's task. The notice's wording is not tested (SPEC R16). Every
 * delete-and-restore case checks Savings, each Goal, each Account's balance and each category's
 * spent for every month it touched. Every name, store, figure and id here is made up.
 */
class TrashTest {
    private val sep14 = LocalDate(2026, 9, 14)
    private val oct2 = LocalDate(2026, 10, 2)
    private val mar10 = LocalDate(2026, 3, 10)
    private val clock = TestClock(sep14)
    private val counter = Ids("t")
    private val ids: () -> String = { counter.next() }

    private fun world(savings: String? = "1,000.00", goals: List<Goal> = emptyList()): ProfileData = profile(
        savings = savings, goals = goals,
        categories = listOf(
            category("groc", "Groceries"), category("home", "Household"), category("fun", "Fun"), category("phone", "Phone"),
            category("net", "Internet"), category("rent", "Rent"), category("car", "Car"),
        ),
    )

    private fun pay(d: ProfileData, id: String, total: String, source: PaymentSource, vararg parts: Pair<String, String>, date: LocalDate = clock.today()) =
        Payments.record(d, PaymentDraft(date, cents(total), source, null, parts.map { Part(it.first, cents(it.second)) }), clock, id)

    private fun card(balance: String = "100.00", payoffCategoryId: String? = null) = Account(
        "card", Fixture.PROFILE, "Card", type = AccountType.CREDIT, balance = cents(balance),
        chargesNotLogged = payoffCategoryId != null, payoffCategoryId = payoffCategoryId,
    )

    /**
     * Every figure a restore must bring back: Savings, each Goal's saved money, each Account's
     * balance, and the spent of every category seen in [months], deleted ones included.
     */
    private fun figures(d: ProfileData, vararg months: String): List<Any?> {
        val cats = (d.categories.map { it.id } + d.payments.flatMap { p -> p.parts.mapNotNull { it.categoryId } } +
            d.transactions.mapNotNull { it.categoryId }).toSortedSet()
        return listOf(
            d.savings.amount,
            d.goals.associate { it.id to it.saved }.toSortedMap(),
            d.accounts.associate { it.id to it.balance }.toSortedMap(),
            cats.flatMap { c -> months.map { m -> Triple(c, m, Spent.of(d, c, m)) } },
        )
    }

    // ---- ported from Sci-Fin's TrashTest -------------------------------------------------------

    // SPEC:R122 SPEC:R58 SPEC:R121
    @Test fun `R122 a goal deleted on 14 September with its 300_00 returned comes back on 2 October holding it, Savings at 1,000_00`() {
        val d0 = world(goals = listOf(goal("trip", "Trip", saved = "300.00")))
        val (after, p) = assertNotNull(Trash.deleteGoal(d0, "trip", returnSaved = true, clock, ids))
        assertEquals(cents("1,300.00"), after.savingsAmount)
        assertEquals(cents("300.00"), Trash.moneyMoved(p))
        clock.setDay(oct2)
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(figures(d0, "2026-09", "2026-10"), figures(back, "2026-09", "2026-10"))
        val restored = back.transactions.single { it.payee == StoredLabels.RESTORED }
        assertEquals("goal:trip", restored.key)
        assertEquals(cents("300.00"), restored.amount)
        assertEquals(sep14, restored.date, "dated the day of the row it undoes")
        assertTrue(back.transactions.any { it.payee == StoredLabels.GOAL_DELETED_RETURNED && it.amount == -cents("300.00") }, "the delete's row stays")
    }

    // SPEC:R122 SPEC:R121
    @Test fun `R122 a goal deleted alone comes back as it was, with no money moved, and no payment can use it meanwhile`() {
        val d0 = world(goals = listOf(goal("trip", "Trip", saved = "300.00")))
        val (after, p) = assertNotNull(Trash.deleteGoal(d0, "trip", returnSaved = false, clock, ids))
        assertEquals(d0.savingsAmount, after.savingsAmount)
        assertEquals(0L, Trash.moneyMoved(p))
        assertFalse(Payments.canSave(after, PaymentDraft(sep14, cents("10.00"), PaymentSource.Goal("trip"), null, listOf(Part("fun", cents("10.00"))))))
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(figures(d0, "2026-09"), figures(back, "2026-09"))
        assertEquals(d0.goals, back.goals)
        assertEquals(d0.transactions, back.transactions)
    }

    /** A Loan of 4,000.00 and its 260.00 payment under Car, due the 20th, swept on creation on 10 September 2026 from 5,000.00. */
    private fun loanWorld(): ProfileData {
        clock.setDay(LocalDate(2026, 9, 10))
        val d = world(savings = "5,000.00").copy(
            accounts = listOf(Account("loan", Fixture.PROFILE, "Car loan", type = AccountType.LOAN, balance = cents("4,000.00"), linkedRecurringExpenseId = "pay")),
        )
        return Recurring.create(d, recurring("pay", "260.00", Cadence.MONTHLY, LocalDate(2026, 9, 20), categoryId = "car", linkedAccountId = "loan"), clock, ids)
    }

    // SPEC:R122 SPEC:R66 SPEC:R87
    @Test fun `R122 a Loan's 260_00 payment and the Loan come back together, its return undone and 4,000_00 logged back on`() {
        val d0 = loanWorld()
        assertEquals(cents("4,740.00"), d0.savingsAmount)
        val (after, p) = assertNotNull(Trash.deleteExpense(d0, "pay", reverse = true, clock, ids))
        assertEquals(cents("5,000.00"), after.savingsAmount)
        assertTrue(after.accounts.isEmpty())
        assertEquals(cents("260.00"), Trash.moneyMoved(p))
        clock.setDay(oct2)
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(figures(d0, "2026-09", "2026-10"), figures(back, "2026-09", "2026-10"))
        assertEquals(d0.accounts, back.accounts)
        val e = back.recurringExpenses.single()
        assertEquals("2026-10", e.lastSweptMonth, "it resumes from the month of the restore")
        assertEquals(LocalDate(2026, 10, 20), e.dueDate)
        assertEquals("loan", e.linkedAccountId)
    }

    // SPEC:R122 SPEC:R87
    @Test fun `R122 a Loan takes its payment to the Trash and brings it back`() {
        val d0 = loanWorld()
        val (after, p) = assertNotNull(Trash.deleteAccount(d0, "loan", reverse = false, clock, ids))
        assertTrue(after.recurringExpenses.isEmpty())
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(figures(d0, "2026-09"), figures(back, "2026-09"))
        assertEquals(d0.accounts, back.accounts)
        assertEquals(d0.recurringExpenses, back.recurringExpenses)
    }

    // SPEC:R122 SPEC:R53 SPEC:R123
    @Test fun `R122 a category and a cancelled pair of 12_50 come back as they were`() {
        val d0 = pay(world(), "p1", "40.00", PaymentSource.Savings, "fun" to "40.00")
        val (afterC, pc) = assertNotNull(Trash.deleteCategory(d0, "fun"))
        val backC = Trash.restore(afterC, pc, clock, ids)
        assertEquals(figures(d0, "2026-09"), figures(backC, "2026-09"))
        assertEquals(d0.categories.sortedBy { it.id }, backC.categories.sortedBy { it.id })

        val w1 = Ledger.logAndMove(d0, Movement(cents("12.50"), TransactionKey.SavingsKey, StoredLabels.SAVINGS, "slip"), clock, "x1").first
        clock.advance(1.hours)
        val w2 = Ledger.logAndMove(w1, Movement(-cents("12.50"), TransactionKey.SavingsKey, StoredLabels.SAVINGS, "slip"), clock, "x2").first
        val (afterP, pp) = assertNotNull(Trash.deletePair(w2, "x1", "x2"))
        assertTrue(afterP.transactions.isEmpty())
        assertEquals(0L, Trash.moneyMoved(pp))
        val backP = Trash.restore(afterP, pp, clock, ids)
        assertEquals(w2.transactions, backP.transactions)
        assertEquals(w2.savings, backP.savings)
    }

    // SPEC:R121 SPEC:R122
    @Test fun `R121 a 45_00 monthly expense in the Trash sweeps nothing over two crossings, and comes back without catching them up`() {
        var d0 = Recurring.create(world(), recurring("gym", "45.00", Cadence.MONTHLY, LocalDate(2026, 9, 20), categoryId = "fun"), clock, ids)
        d0 = d0.copy(settings = d0.settings.copy(lastMonthProcessed = "2026-09"))
        val (after, p) = assertNotNull(Trash.deleteExpense(d0, "gym", reverse = false, clock, ids))
        val later = TestClock(LocalDate(2026, 11, 14))
        val (aged, _) = NewMonth.runCatchUp(after, later, ids)
        assertTrue(aged.transactions.none { it.key == "rec:gym" && it.date > LocalDate(2026, 9, 30) }, "nothing swept while in the Trash")
        val back = Trash.restore(aged, p, later, ids)
        assertEquals("2026-11", back.recurringExpenses.single { it.id == "gym" }.lastSweptMonth)
        val (again, _) = NewMonth.runCatchUp(back, later, ids)
        assertTrue(again.transactions.none { it.key == "rec:gym" && it.date > LocalDate(2026, 9, 30) }, "and restoring does not sweep the missed months")
    }

    // SPEC:R121
    @Test fun `R121 deleted on 1 October 2026, the notice is due on 21 October, once, and the item goes on 31 October`() {
        val on = LocalDate(2026, 10, 1)
        val item = TrashItem("tr-1", Fixture.PROFILE, "category", "Fun", on.toString(), Trash.encode(TrashPayload(categories = listOf(category("fun", "Fun")))))
        assertEquals(LocalDate(2026, 10, 31), Trash.goesOn(on))
        assertEquals(TrashTidy(emptyList(), emptyList()), Trash.tidy(listOf(item), LocalDate(2026, 10, 20)))
        assertEquals(TrashTidy(emptyList(), listOf("tr-1")), Trash.tidy(listOf(item), LocalDate(2026, 10, 21)))
        val warned = item.copy(warned = true)
        assertEquals(TrashTidy(emptyList(), emptyList()), Trash.tidy(listOf(warned), LocalDate(2026, 10, 22)), "said once, not every day")
        assertEquals(TrashTidy(emptyList(), emptyList()), Trash.tidy(listOf(warned), LocalDate(2026, 10, 30)))
        assertEquals(TrashTidy(listOf("tr-1"), emptyList()), Trash.tidy(listOf(warned), LocalDate(2026, 10, 31)))
        assertEquals(TrashTidy(listOf("tr-1"), emptyList()), Trash.tidy(listOf(item), LocalDate(2026, 11, 2)), "gone, not warned, past 30 days")
    }

    // ---- receipts and Profiles ------------------------------------------------------------------

    private fun receiptWorld(): ProfileData = world().copy(receiptSettings = Fixture.receiptSettings())

    /** Groceries 30.00 and Household 12.30: 42.30. */
    private fun shop(id: String = "rc-1", date: LocalDate = mar10) = receipt(
        id, "Mercato Lume", date, line("pasta", "30.00", "ch-groc", "Groceries"), line("soap", "12.30", "ch-home", "Household"),
    )

    private fun recordReceipt(d: ProfileData, file: ReceiptFile, id: String = "pay-r") = Receipts.confirm(d, Receipts.open(d, file), clock, id)

    private fun folderOf(vararg names: String) = object : ReceiptFolder {
        override fun names() = names.toList()
        override fun read(name: String): ByteArray = name.encodeToByteArray()
    }

    // SPEC:R126 SPEC:R112
    @Test fun `R126 a deleted Profile with a 42_30 receipt payment waits whole in the Trash, frees the folder and comes back as it was`() {
        clock.setDay(mar10)
        val d0 = recordReceipt(receiptWorld(), shop())
        val app = AppState(activeProfileId = "p-0", receiptProfileId = Fixture.PROFILE)
        val (freed, p) = Trash.deleteProfile(app, d0)
        assertNull(freed.receiptProfileId)
        assertTrue(Receipts.offersFolder(freed, "p-2"))
        val kept = assertNotNull(assertNotNull(Trash.decode(Trash.encode(p))).profile)
        val (app2, back) = Trash.restoreProfile(freed, kept)
        assertEquals(d0, back)
        assertEquals(Fixture.PROFILE, app2.receiptProfileId)
        assertEquals("p-0", app2.activeProfileId, "the restored Profile is not opened")
    }

    // SPEC:R126 SPEC:R112
    @Test fun `R126 a Profile restored after Profile B chose a folder comes back with no folder and its recorded receipt id`() {
        clock.setDay(mar10)
        val d0 = recordReceipt(receiptWorld(), shop())
        val (freed, p) = Trash.deleteProfile(AppState(activeProfileId = Fixture.PROFILE, receiptProfileId = Fixture.PROFILE), d0)
        val (withB, _) = Receipts.chooseFolder(freed, ProfileData.fresh("p-2", "Second"), "folder-handle-2")
        val (app2, back) = Trash.restoreProfile(withB, assertNotNull(p.profile))
        assertEquals("p-2", app2.receiptProfileId)
        val s = assertNotNull(back.receiptSettings)
        assertNull(s.folder)
        assertEquals(listOf("rc-1"), s.recordedReceiptIds)
        assertEquals(d0.payments, back.payments)
    }

    // SPEC:R121 SPEC:R125 SPEC:R42
    @Test fun `R121 a payload with payments, links, rows and charges comes back from encode and decode as it was`() {
        clock.setDay(mar10)
        val withPay = pay(recordReceipt(receiptWorld().copy(goals = listOf(goal("trip", "Trip", saved = "80.00"))), shop()), "p-g", "30.00", PaymentSource.Goal("trip"), "fun" to "30.00")
        val loan = loanWorld()
        val p = TrashPayload(
            goals = withPay.goals, categories = withPay.categories, expenses = loan.recurringExpenses, accounts = loan.accounts,
            transactions = loan.transactions, payments = withPay.payments, categoryLinks = mapOf("ch-groc" to "groc"),
            written = loan.transactions.take(1), charged = mapOf("p-g" to cents("30.00")),
        )
        assertEquals(p, Trash.decode(Trash.encode(p)))
        assertNull(Trash.decode("not a payload"))
    }

    // SPEC:R41 SPEC:R116 SPEC:R125
    @Test fun `R41 a deleted receipt payment's file is not offered again`() {
        clock.setDay(mar10)
        val folder = folderOf("chatelaine-receipt-rc-1.json")
        val parse: (ByteArray) -> ParseResult = { ParseResult.Ok(shop()) }
        assertEquals(1, ReceiptPickup.waiting(receiptWorld(), folder, parse))
        val d1 = recordReceipt(receiptWorld(), shop())
        val (after, _) = assertNotNull(Trash.deletePayment(d1, "pay-r", clock, ids))
        assertTrue(after.payments.isEmpty())
        assertEquals(listOf("rc-1"), after.receiptSettings?.recordedReceiptIds)
        val pickup = ReceiptPickup.scan(after, folder, parse)
        assertEquals(emptyList(), pickup.ready)
        assertEquals(emptyList(), pickup.refused)
    }

    // SPEC:R41 SPEC:R125 SPEC:R26 SPEC:R122
    @Test fun `R41 restoring it brings back its parts and its bankKeys`() {
        clock.setDay(mar10)
        val d1 = recordReceipt(receiptWorld(), shop())
        val row = BankRow(LocalDate(2026, 3, 12), -cents("42.30"), "POS MERCATO LUME 0423", "FIT-12")
        val rr = StatementImport.review(d1, listOf(row), mar10).single()
        assertEquals(RowStatus.PAIR, rr.status)
        val d2 = Pairing.pairBankWithPayment(d1, "pay-r", rr, null, clock, "tx-pair")
        val paired = d2.payments.single()
        assertNotNull(paired.bankKeys)
        val (after, p) = assertNotNull(Trash.deletePayment(d2, "pay-r", clock, ids))
        assertEquals(cents("1,000.00"), after.savingsAmount)
        assertEquals(RowStatus.DUPLICATE, StatementImport.review(after, listOf(row), mar10).single().status)
        assertEquals(cents("42.30"), Trash.moneyMoved(p))
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(listOf(paired), back.payments)
        assertEquals(2, paired.parts.size)
        assertEquals(2, paired.receiptLines.size)
        assertEquals(cents("957.70"), back.savingsAmount)
        assertEquals(figures(d2, "2026-03"), figures(back, "2026-03"))
        assertEquals(RowStatus.DUPLICATE, StatementImport.review(back, listOf(row), mar10).single().status)
    }

    // SPEC:R42 SPEC:R123 SPEC:R32
    @Test fun `R42 after deleting Pulizia, the next receipt with Chatelaine's Pulizia keeps Confirm off`() {
        clock.setDay(mar10)
        val base = receiptWorld().let { it.copy(categories = it.categories + category("pul", "Pulizia")) }
        val d1 = recordReceipt(base, receipt("rc-1", "Mercato Lume", mar10, line("sponge", "3.20", "ch-pul", "Pulizia")))
        assertEquals("pul", d1.receiptSettings?.categoryLinks?.get("ch-pul"))
        val (after, p) = assertNotNull(Trash.deleteCategory(d1, "pul"))
        assertEquals(mapOf("ch-pul" to "pul"), p.categoryLinks)
        assertNull(after.receiptSettings?.categoryLinks?.get("ch-pul"))
        val next = Receipts.open(after, receipt("rc-2", "Mercato Lume", mar10, line("sponge", "3.20", "ch-pul", "Pulizia")))
        assertEquals(ReceiptProblem.UNLINKED, Receipts.problem(after, next))
    }

    // SPEC:R42 SPEC:R122
    @Test fun `R42 restoring Pulizia brings back its links`() {
        clock.setDay(mar10)
        val base = receiptWorld().let { it.copy(categories = it.categories + category("pul", "Pulizia")) }
        val first = Receipts.open(base, receipt("rc-1", "Mercato Lume", mar10, line("sponge", "3.20", "ch-pul", "Pulizie")))
        assertEquals(listOf<ReviewNote>(ReviewNote.Unlinked("ch-pul")), Receipts.notes(base, first))
        val d1 = Receipts.confirm(base, Receipts.link(first, "ch-pul", "pul"), clock, "pay-r")
        val (after, p) = assertNotNull(Trash.deleteCategory(d1, "pul"))
        val back = Trash.restore(after, p, clock, ids)
        assertEquals("pul", back.receiptSettings?.categoryLinks?.get("ch-pul"))
        val next = Receipts.open(back, receipt("rc-2", "Mercato Lume", mar10, line("sponge", "3.20", "ch-pul", "Pulizie")))
        assertEquals(listOf<String?>("pul"), next.lineCategories)
        assertNull(Receipts.problem(back, next))
    }

    // ---- the owner's answers of 2026-11-25 ------------------------------------------------------

    // SPEC:R122 SPEC:R54
    @Test fun `R122 a deleted Goal's 30_00 payment re-filed restores the Goal, and removed refuses it with WRITTEN_SINCE`() {
        val d0 = pay(world(goals = listOf(goal("trip", "Trip", saved = "300.00"))), "p1", "30.00", PaymentSource.Goal("trip"), "fun" to "30.00")
        val (after, p) = assertNotNull(Trash.deleteGoal(d0, "trip", returnSaved = true, clock, ids))
        assertEquals(mapOf("p1" to cents("30.00")), p.charged)

        val refiled = Payments.edit(after, "p1", PaymentDraft(sep14, cents("30.00"), PaymentSource.Goal("trip"), null, listOf(Part("groc", cents("30.00")))), clock, "tx-e")
        assertEquals(after.transactions, refiled.transactions, "re-filing writes no row")
        assertNull(Trash.restoreProblem(refiled, p))
        val back = Trash.restore(refiled, p, clock, ids)
        assertEquals(cents("270.00"), back.goals.single().saved)
        assertEquals(d0.savingsAmount, back.savingsAmount)

        val removed = Payments.remove(after, "p1", clock, "tx-r")
        assertEquals(RestoreProblem.WRITTEN_SINCE, Trash.restoreProblem(removed, p))
        assertFailsWith<IllegalArgumentException> { Trash.restore(removed, p, clock, ids) }
    }

    // SPEC:R122 SPEC:R54 SPEC:R88
    @Test fun `R122 a deleted card's 40_00 payment moved to Savings refuses the card's restore with WRITTEN_SINCE`() {
        val d0 = pay(world().copy(accounts = listOf(card())), "p1", "40.00", PaymentSource.Account("card"), "groc" to "40.00")
        val (after, p) = assertNotNull(Trash.deleteAccount(d0, "card", reverse = false, clock, ids))
        assertNull(Trash.restoreProblem(after, p))
        val moved = Payments.edit(after, "p1", PaymentDraft(sep14, cents("40.00"), PaymentSource.Savings, null, listOf(Part("groc", cents("40.00")))), clock, "tx-e")
        assertTrue(moved.transactions.size > after.transactions.size, "the move logged an acct: row")
        assertEquals(RestoreProblem.WRITTEN_SINCE, Trash.restoreProblem(moved, p))
    }

    // SPEC:R123 SPEC:R67 SPEC:R109
    @Test fun `R123 deleting Phone under a 60_00 expense needs another category, and with Internet the expense and a card move, past rows stay`() {
        clock.setDay(LocalDate(2026, 8, 1))
        var d = Recurring.create(world(), recurring("bill", "60.00", Cadence.MONTHLY, LocalDate(2026, 8, 10), categoryId = "phone"), clock, ids)
        d = d.copy(accounts = listOf(card(payoffCategoryId = "phone")))
        clock.setDay(sep14)
        assertNull(Trash.deleteCategory(d, "phone"))
        assertEquals(CategoryEditing.DeleteProblem.REPLACEMENT_NEEDED, CategoryEditing.deleteProblem(d, "phone", null))
        assertEquals(CategoryEditing.DeleteProblem.REPLACEMENT_UNKNOWN, CategoryEditing.deleteProblem(d, "phone", "nope"))
        assertEquals(CategoryEditing.DeleteProblem.REPLACEMENT_UNKNOWN, CategoryEditing.deleteProblem(d, "phone", "phone"))
        assertEquals(CategoryEditing.DeleteProblem.NO_SUCH_CATEGORY, CategoryEditing.deleteProblem(d, "nope", "net"))

        val (after, p) = assertNotNull(Trash.deleteCategory(d, "phone", replacementId = "net"))
        assertEquals("net", after.recurringExpenses.single().categoryId)
        assertEquals("net", after.accounts.single().payoffCategoryId)
        assertEquals(cents("60.00"), Spent.of(after, "phone", "2026-08"))
        assertEquals("phone", after.transactions.single { it.key == "rec:bill" }.categoryId)
        assertEquals(figures(d, "2026-08", "2026-09"), figures(after, "2026-08", "2026-09"))

        val back = Trash.restore(after, p, clock, ids)
        assertTrue(back.categories.any { it.id == "phone" })
        assertEquals("net", back.recurringExpenses.single().categoryId, "restoring Phone does not move the expense back")
        assertEquals("net", back.accounts.single().payoffCategoryId)
        assertNotNull(Trash.deleteCategory(d, "fun"), "a category nothing files under needs no other")
    }

    // SPEC:R124 SPEC:R99 SPEC:R101
    @Test fun `R124 Fun's 40_00 reads marked deleted in the Trash, Uncategorised once gone, its own slice restored, and 70_00 in all`() {
        val d0 = pay(pay(world(), "p1", "40.00", PaymentSource.Savings, "fun" to "40.00"), "p2", "30.00", PaymentSource.Savings, "groc" to "30.00")
        val (after, p) = assertNotNull(Trash.deleteCategory(d0, "fun"))
        val item = TrashItem("tr-1", Fixture.PROFILE, "category", "Fun", sep14.toString(), Trash.encode(p))
        val trashed = Trash.trashedCategories(listOf(item), Fixture.PROFILE)
        assertEquals(setOf("fun"), trashed)
        assertEquals(emptySet(), Trash.trashedCategories(listOf(item), "p-2"))

        val inTrash = Reports.spendingComposition(after, sep14, trashed)
        assertEquals(listOf(Slice(SliceKind.DELETED_CATEGORY, "fun", cents("40.00")), Slice(SliceKind.CATEGORY, "groc", cents("30.00"))), inTrash.slices)
        assertEquals(cents("70.00"), inTrash.net)
        val gone = Reports.spendingComposition(after, sep14)
        assertEquals(listOf(Slice(SliceKind.UNCATEGORISED, null, cents("40.00")), Slice(SliceKind.CATEGORY, "groc", cents("30.00"))), gone.slices)
        assertEquals(cents("70.00"), gone.net)
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(listOf(Slice(SliceKind.CATEGORY, "fun", cents("40.00")), Slice(SliceKind.CATEGORY, "groc", cents("30.00"))), Reports.spendingComposition(back, sep14).slices)
        for (x in listOf(d0, after, back)) assertEquals(cents("70.00"), Reports.incomeVsExpenses(x, sep14, 1).single().expenses)
    }

    // ---- round trips --------------------------------------------------------------------------

    // SPEC:R122 SPEC:R66 SPEC:R67
    @Test fun `R122 a 300_00 semimonthly Rent deleted on 20 September with 300_00 returned and restored on 2 October leaves 1,400_00 and September at 600_00`() {
        clock.setDay(LocalDate(2026, 9, 1))
        val d0 = Recurring.create(world(savings = "2,000.00"), recurring("r", "300.00", Cadence.SEMIMONTHLY, LocalDate(2026, 9, 15), dueDate2 = LocalDate(2026, 9, 30), categoryId = "rent"), clock, ids)
        assertEquals(cents("1,400.00"), d0.savingsAmount)
        clock.setDay(LocalDate(2026, 9, 20))
        val (after, p) = assertNotNull(Trash.deleteExpense(d0, "r", reverse = true, clock, ids))
        assertEquals(cents("1,700.00"), after.savingsAmount)
        assertEquals(cents("300.00"), Spent.of(after, "rent", "2026-09"))
        clock.setDay(oct2)
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(cents("1,400.00"), back.savingsAmount)
        assertEquals(cents("600.00"), Spent.of(back, "rent", "2026-09"))
        assertEquals(figures(d0, "2026-09", "2026-10"), figures(back, "2026-09", "2026-10"))
        assertEquals("2026-10", back.recurringExpenses.single().lastSweptMonth)
    }

    // SPEC:R122 SPEC:R66 SPEC:R67
    @Test fun `R122 a yearly share returned as -100_00 under Phone and -100_00 under Internet comes back under each, every month as before`() {
        val e = recurring("y", "1,200.00", Cadence.YEARLY, LocalDate(2026, 7, 15), dueDate = LocalDate(2027, 7, 15), share = "100.00", categoryId = "phone")
        var d0 = world(savings = "2,000.00").copy(recurringExpenses = listOf(e))
        d0 = Recurring.sweepOccurrences(d0, "y", LocalDate(2026, 8, 1), clock, ids).first
        d0 = d0.copy(recurringExpenses = d0.recurringExpenses.map { it.copy(categoryId = "net") })
        d0 = Recurring.sweepOccurrences(d0, "y", LocalDate(2026, 9, 1), clock, ids).first
        val (after, p) = assertNotNull(Trash.deleteExpense(d0, "y", reverse = true, clock, ids))
        assertEquals(listOf("phone" to -cents("100.00"), "net" to -cents("100.00")), p.written.map { it.categoryId to it.amount })
        clock.setDay(oct2)
        val back = Trash.restore(after, p, clock, ids)
        val restored = back.transactions.filter { it.payee == StoredLabels.RESTORED }
        assertEquals(listOf("phone" to cents("100.00"), "net" to cents("100.00")), restored.map { it.categoryId to it.amount })
        assertTrue(restored.all { it.date == sep14 && it.share == p.written.first().share })
        assertEquals(figures(d0, "2026-08", "2026-09", "2026-10"), figures(back, "2026-08", "2026-09", "2026-10"))
    }

    // SPEC:R122 SPEC:R88 SPEC:R100
    @Test fun `R122 a card at 140_00 with a 40_00 payment charged, deleted and restored, reads 140_00 and Savings vs Owed as before`() {
        val d0 = pay(world().copy(accounts = listOf(card())), "p1", "40.00", PaymentSource.Account("card"), "groc" to "40.00")
        assertEquals(cents("140.00"), d0.accounts.single().balance)
        val (after, p) = assertNotNull(Trash.deleteAccount(d0, "card", reverse = false, clock, ids))
        clock.setDay(oct2)
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(cents("140.00"), back.accounts.single().balance)
        assertEquals(figures(d0, "2026-09", "2026-10"), figures(back, "2026-09", "2026-10"))
        assertEquals(Reports.savingsVsOwedTrend(d0, oct2, 3), Reports.savingsVsOwedTrend(back, oct2, 3))
    }

    // SPEC:R125 SPEC:R54
    @Test fun `R125 a 42_30 payment from Savings deleted leaves 1,000_00 and restored 957_70 with its parts`() {
        val d0 = pay(world(), "p1", "42.30", PaymentSource.Savings, "groc" to "30.00", "home" to "12.30")
        val (after, p) = assertNotNull(Trash.deletePayment(d0, "p1", clock, ids))
        assertEquals(cents("1,000.00"), after.savingsAmount)
        assertEquals(0L, Spent.of(after, "groc", "2026-09"))
        assertNull(Trash.restoreNotice(after, p))
        clock.setDay(oct2)
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(d0.payments, back.payments)
        assertEquals(figures(d0, "2026-09", "2026-10"), figures(back, "2026-09", "2026-10"))
    }

    // SPEC:R125 SPEC:R54
    @Test fun `R125 a 30_00 payment restored after 70_00 was spent from its Goal warns and takes the Goal to 0_00 and 20_00 from Savings`() {
        val d0 = pay(world(goals = listOf(goal("trip", "Trip", saved = "80.00"))), "p1", "30.00", PaymentSource.Goal("trip"), "fun" to "30.00")
        assertEquals(cents("50.00"), d0.goals.single().saved)
        val (after, p) = assertNotNull(Trash.deletePayment(d0, "p1", clock, ids))
        assertEquals(cents("80.00"), after.goals.single().saved)
        val spent = pay(after, "p2", "70.00", PaymentSource.Goal("trip"), "groc" to "70.00")
        assertEquals(PaymentNotice.GOAL_SHORT, Trash.restoreNotice(spent, p))
        val back = Trash.restore(spent, p, clock, ids)
        assertEquals(0L, back.goals.single().saved)
        assertEquals(cents("980.00"), back.savingsAmount)
        assertEquals(cents("20.00"), back.payments.single { it.id == "p1" }.fromSavings)
        assertEquals(cents("30.00"), Spent.of(back, "fun", "2026-09"))
    }

    // SPEC:R125 SPEC:R54 SPEC:R122
    @Test fun `R125 a 30_00 payment restored after its Goal was deleted with 80_00 returned takes 30_00 from Savings with a row`() {
        val d0 = pay(world(goals = listOf(goal("trip", "Trip", saved = "80.00"))), "p1", "30.00", PaymentSource.Goal("trip"), "fun" to "30.00")
        val (after, p) = assertNotNull(Trash.deletePayment(d0, "p1", clock, ids))
        val (noGoal, pg) = assertNotNull(Trash.deleteGoal(after, "trip", returnSaved = true, clock, ids))
        assertEquals(cents("1,080.00"), noGoal.savingsAmount)
        assertEquals(PaymentNotice.SOURCE_GOAL_DELETED, Trash.restoreNotice(noGoal, p))
        val back = Trash.restore(noGoal, p, clock, ids)
        assertEquals(cents("1,050.00"), back.savingsAmount)
        val row = back.transactions.last()
        assertEquals(StoredLabels.DELETED_GOAL_PAYMENT, row.payee)
        assertEquals("savings", row.key)
        assertEquals(-cents("30.00"), row.amount)
        assertEquals(RestoreProblem.WRITTEN_SINCE, Trash.restoreProblem(back, pg), "a payment was restored onto the deleted Goal")
    }

    // SPEC:R125 SPEC:R54 SPEC:R122 SPEC:R88
    @Test fun `R125 a 40_00 payment of a card deleted since, restored, logs -40_00 to the card and leaves its rows and payments at zero`() {
        val opened = Ledger.logAccountEdit(world().copy(accounts = listOf(card())), "card", "Card", cents("100.00"), StoredLabels.OPENING_BALANCE, clock, "open")
        val d0 = pay(opened, "p1", "40.00", PaymentSource.Account("card"), "groc" to "40.00")
        val (after, p) = assertNotNull(Trash.deletePayment(d0, "p1", clock, ids))
        val (noCard, pc) = assertNotNull(Trash.deleteAccount(after, "card", reverse = false, clock, ids))
        assertEquals(PaymentNotice.SOURCE_ACCOUNT_DELETED, Trash.restoreNotice(noCard, p))
        val back = Trash.restore(noCard, p, clock, ids)
        assertEquals(d0.savingsAmount, back.savingsAmount)
        val row = back.transactions.last()
        assertEquals(StoredLabels.DELETED_ACCOUNT_PAYMENT, row.payee)
        assertEquals(-cents("40.00"), row.amount)
        val rows = back.transactions.filter { it.key == "acct:card" }.sumOf { it.amount }
        val charged = back.payments.filter { it.source == PaymentSource.Account("card") }.sumOf { it.total }
        assertEquals(0L, rows + charged)
        assertEquals(RestoreProblem.WRITTEN_SINCE, Trash.restoreProblem(back, pc))
    }

    // SPEC:R125 SPEC:R54 SPEC:R122
    @Test fun `R125 a payment deleted after its Goal was deleted is restored by undoing its own row`() {
        val d0 = pay(world(goals = listOf(goal("trip", "Trip", saved = "80.00"))), "p1", "30.00", PaymentSource.Goal("trip"), "fun" to "30.00")
        val (noGoal, pg) = assertNotNull(Trash.deleteGoal(d0, "trip", returnSaved = true, clock, ids))
        val (after, p) = assertNotNull(Trash.deletePayment(noGoal, "p1", clock, ids))
        assertEquals(1, p.written.size)
        assertEquals(cents("1,080.00"), after.savingsAmount)
        assertEquals(RestoreProblem.WRITTEN_SINCE, Trash.restoreProblem(after, pg))
        clock.setDay(oct2)
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(figures(noGoal, "2026-09", "2026-10"), figures(back, "2026-09", "2026-10"))
        assertNull(Trash.restoreProblem(back, pg), "the payment back as at the Goal's delete")
        val goalBack = Trash.restore(back, pg, clock, ids)
        assertEquals(figures(d0, "2026-09", "2026-10"), figures(goalBack, "2026-09", "2026-10"))
    }

    // SPEC:R122 SPEC:R111
    @Test fun `R122 a 60_00 payoff of a card from Savings, undone, comes back as it was`() {
        val d0 = Accounts.quickAdd(world().copy(accounts = listOf(card())), "card", cents("60.00"), Accounts.Direction.PAYMENT, null, Accounts.PayoffFrom.Savings, clock, ids = ids)
        assertEquals(cents("940.00"), d0.savingsAmount)
        val group = assertNotNull(d0.transactions.first().groupId)
        val (after, p) = assertNotNull(Trash.undoQuickAdd(d0, group))
        assertEquals(cents("100.00"), after.accounts.single().balance)
        assertEquals(cents("1,000.00"), after.savingsAmount)
        assertEquals(cents("60.00"), Trash.moneyMoved(p))
        val back = Trash.restore(after, p, clock, ids)
        assertEquals(d0.transactions, back.transactions)
        assertEquals(figures(d0, "2026-09"), figures(back, "2026-09"))
    }

    // SPEC:R122 SPEC:R111
    @Test fun `R122 a 60_00 payoff from a Goal, undone, is refused with GOAL_SHORT at 50_00 and TARGET_DELETED once the Goal is gone`() {
        val w = world(goals = listOf(goal("trip", "Trip", saved = "80.00"))).copy(accounts = listOf(card()))
        val d0 = Accounts.quickAdd(w, "card", cents("60.00"), Accounts.Direction.PAYMENT, null, Accounts.PayoffFrom.Goal("trip"), clock, ids = ids)
        val (after, p) = assertNotNull(Trash.undoQuickAdd(d0, assertNotNull(d0.transactions.first().groupId)))
        assertEquals(cents("80.00"), after.goals.single().saved)
        val short = pay(after, "p1", "30.00", PaymentSource.Goal("trip"), "fun" to "30.00")
        assertEquals(RestoreProblem.GOAL_SHORT, Trash.restoreProblem(short, p))
        val (gone, _) = assertNotNull(Trash.deleteGoal(after, "trip", returnSaved = true, clock, ids))
        assertEquals(RestoreProblem.TARGET_DELETED, Trash.restoreProblem(gone, p))
        val (noCard, _) = assertNotNull(Trash.deleteAccount(after, "card", reverse = false, clock, ids))
        assertEquals(RestoreProblem.TARGET_DELETED, Trash.restoreProblem(noCard, p))
    }

    // SPEC:R122 SPEC:R107 SPEC:R125
    @Test fun `R122 a deleted 12_40 payment whose bank row was ticked and recorded again is refused with RECORDED_AGAIN`() {
        clock.setDay(mar10)
        val row = BankRow(LocalDate(2026, 3, 9), -cents("12.40"), "POS MERCATO VERDE 0412", "FIT-9")
        fun ticked(d: ProfileData) = StatementImport.review(d, listOf(row), mar10).single().let {
            it.copy(include = true, draft = PaymentDraft(row.date, cents("12.40"), PaymentSource.Savings, it.payee, listOf(Part("groc", cents("12.40")))))
        }
        val first = ticked(world())
        assertEquals(RowStatus.READY, first.status)
        val d1 = StatementImport.confirm(world(), listOf(first), clock, ids)
        val (after, p) = assertNotNull(Trash.deletePayment(d1, d1.payments.single().id, clock, ids))
        assertNull(Trash.restoreProblem(after, p))
        val again = ticked(after)
        assertEquals(RowStatus.DUPLICATE, again.status)
        val d2 = StatementImport.confirm(after, listOf(again), clock, ids)
        assertEquals(1, d2.payments.size)
        assertEquals(RestoreProblem.RECORDED_AGAIN, Trash.restoreProblem(d2, p))
    }

    // SPEC:R122 SPEC:R123 SPEC:R67
    @Test fun `R122 a 60_00 expense whose category Phone was deleted since needs another category to come back`() {
        clock.setDay(LocalDate(2026, 8, 1))
        val d0 = Recurring.create(world(), recurring("bill", "60.00", Cadence.MONTHLY, LocalDate(2026, 8, 10), categoryId = "phone"), clock, ids)
        clock.setDay(sep14)
        val (a1, pe) = assertNotNull(Trash.deleteExpense(d0, "bill", reverse = false, clock, ids))
        val (a2, _) = assertNotNull(Trash.deleteCategory(a1, "phone"), "nothing files under Phone while the expense is in the Trash")
        assertEquals(RestoreProblem.CATEGORY_MISSING, Trash.restoreProblem(a2, pe))
        assertEquals(RestoreProblem.CATEGORY_MISSING, Trash.restoreProblem(a2, pe, replacementCategoryId = "nope"))
        val back = Trash.restore(a2, pe, clock, ids, replacementCategoryId = "net")
        assertEquals("net", back.recurringExpenses.single().categoryId)
        assertEquals(cents("60.00"), Spent.of(back, "phone", "2026-08"), "its past rows keep Phone")
        // Phone is gone from the categories, but its spent is read by id, so every month matches
        assertEquals(figures(d0, "2026-08", "2026-09"), figures(back, "2026-08", "2026-09"))
    }
}
