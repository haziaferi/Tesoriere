package com.hazia.tesoriere.pairing

import com.hazia.tesoriere.Fixture
import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.goal
import com.hazia.tesoriere.Fixture.line
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Fixture.receipt
import com.hazia.tesoriere.Ids
import com.hazia.tesoriere.TestClock
import com.hazia.tesoriere.cents
import com.hazia.tesoriere.engine.Accounts
import com.hazia.tesoriere.engine.GoalEditing
import com.hazia.tesoriere.engine.Reports
import com.hazia.tesoriere.engine.RestoreProblem
import com.hazia.tesoriere.engine.TransactionKey
import com.hazia.tesoriere.engine.Trash
import com.hazia.tesoriere.engine.Writes
import com.hazia.tesoriere.engine.savingsAmount
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.PaymentDraft
import com.hazia.tesoriere.payments.PaymentNotice
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Payments
import com.hazia.tesoriere.payments.Spent
import com.hazia.tesoriere.receipts.ReceiptFile
import com.hazia.tesoriere.receipts.Receipts
import com.hazia.tesoriere.statements.BankRow
import com.hazia.tesoriere.statements.RowStatus
import com.hazia.tesoriere.statements.StatementImport
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every store, payee, product, figure and id here is made up. */
class MergeTest {
    private val day = LocalDate(2026, 3, 10)
    private val clock = TestClock(day)

    private fun world(tripSaved: String = "0.00"): ProfileData = profile(
        goals = listOf(goal("trip", "Trip", saved = tripSaved)),
        categories = listOf(category("food", "Food"), category("groc", "Groceries"), category("fun", "Fun")),
    ).copy(
        accounts = listOf(
            Account("card", Fixture.PROFILE, "Card", type = AccountType.CREDIT),
            Account("loose", Fixture.PROFILE, "Loose card", type = AccountType.CREDIT, chargesNotLogged = true, payoffCategoryId = "fun"),
        ),
        receiptSettings = Fixture.receiptSettings(),
    )

    private fun on(dayOfMonth: Int) = LocalDate(2026, 3, dayOfMonth)

    /** A 4.50 coffee under Food at Bar Centrale. */
    private fun coffeeReceipt(price: String = "4.50") = receipt("rc-a", "Bar Centrale", day, line("caffe", price, "ch-food", "Food"))

    private val coffee = BankRow(on(11), -cents("4.50"), "BAR CENTRALE", null)

    private fun recordReceipt(d: ProfileData, file: ReceiptFile, source: PaymentSource = PaymentSource.Savings, id: String = "pay-r") =
        Receipts.confirm(Fixture.receiptApp, d, Receipts.setSource(Receipts.open(d, file), source), clock, id)

    /** [rows] recorded on their own, as after a pair suggestion turned down: each under [category], from [source]. */
    private fun recordRows(
        d: ProfileData, vararg rows: BankRow, category: String = "food", source: PaymentSource = PaymentSource.Savings,
        ids: () -> String = { "pay-b" },
    ): ProfileData {
        val review = StatementImport.review(d, rows.toList(), day).map {
            it.copy(pairPaymentId = null, include = true, draft = StatementImport.draft(it.row, listOf(Part(category, -it.row.amount)), source))
        }
        return StatementImport.confirm(d, review, clock, ids).also { assertEquals(d.payments.size + rows.size, it.payments.size, "every row recorded") }
    }

    /** The coffee recorded twice: its receipt (Savings 995.50), then its row on its own (991.00). */
    private fun twice(): ProfileData {
        val d1 = recordReceipt(world(), coffeeReceipt())
        assertEquals(cents("995.50"), d1.savingsAmount)
        val d2 = recordRows(d1, coffee)
        assertEquals(cents("991.00"), d2.savingsAmount)
        assertEquals(cents("9.00"), Spent.of(d2, "food", "2026-03"))
        return d2
    }

    private fun merge(d: ProfileData, r: String = "pay-r", b: String = "pay-b", difference: String? = null, replacement: String? = null) =
        Pairing.merge(d, r, b, difference, clock, "tx-rm", "tx-ed", replacement)

    private fun replayed(d: ProfileData) = cents("1,000.00") + Reports.savingsMovements(d).sumOf { it.effect }

    // SPEC:R129 SPEC:R26 SPEC:R119
    @Test fun `R129 a 4_50 coffee recorded from its receipt and from its row, merged, is one payment with the row's keys, Savings 995_50`() {
        val d = twice()
        val receiptSide = d.payments.single { it.id == "pay-r" }
        assertNull(Pairing.mergeProblem(d, "pay-r", "pay-b", null))
        assertEquals(MergeNotice(null, null), Pairing.mergeNotice(d, "pay-r", "pay-b", null))
        val m = merge(d)
        val p = m.payments.single()
        assertEquals("pay-r", p.id)
        assertEquals(cents("4.50"), p.total)
        assertEquals(receiptSide.receiptId, p.receiptId)
        assertEquals(receiptSide.receiptLines, p.receiptLines)
        assertEquals(receiptSide.date, p.date)
        assertEquals(PaymentSource.Savings, p.source)
        assertEquals("Bar Centrale", p.payee)
        assertEquals(listOf(Part("food", cents("4.50"))), p.parts)
        assertEquals(StatementImport.keys(coffee), p.bankKeys)
        assertEquals(cents("995.50"), m.savingsAmount)
        assertEquals(cents("4.50"), Spent.of(m, "food", "2026-03"))
        assertEquals(d.importState.importedKeys, m.importState.importedKeys, "the row stays kept once")
        assertEquals(d.transactions, m.transactions, "Savings sources write no row")
        assertEquals(RowStatus.DUPLICATE, StatementImport.review(m, listOf(coffee), day).single().status)
        assertEquals("bar centrale", m.receiptSettings?.storeLinks?.get("Bar Centrale"), "a hand pair links the store (R120)")
    }

    // SPEC:R129 SPEC:R39
    @Test fun `R129 a 42_10 receipt and a -42_30 bank payment merge only with the 0_20 filed, into one 42_30 payment`() {
        val d1 = recordReceipt(world(), receipt("rc-s", "Granvolta", day, line("pasta", "42.10", "ch-groc", "Groceries")))
        val d = recordRows(d1, BankRow(on(12), -cents("42.30"), "POS GRANVOLTA MI 0423", "F1"), category = "groc")
        assertEquals(cents("915.60"), d.savingsAmount)
        assertEquals(MergeProblem.DIFFERENCE_UNFILED, Pairing.mergeProblem(d, "pay-r", "pay-b", null))
        assertEquals(d, merge(d))
        assertEquals(MergeProblem.DIFFERENCE_UNFILED, Pairing.mergeProblem(d, "pay-r", "pay-b", "nope"), "the category picked must exist")
        assertEquals(d, merge(d, difference = "nope"))
        val m = merge(d, difference = "fun")
        val p = m.payments.single()
        assertEquals(cents("42.30"), p.total)
        assertEquals(listOf(Part("groc", cents("42.10")), Part("fun", cents("0.20"), difference = true)), p.parts)
        assertEquals("F1", p.bankKeys?.fitid)
        assertEquals(cents("957.70"), m.savingsAmount, "the bank payment's 42.30 given back once, 0.20 more taken")
        assertEquals(cents("0.20"), Spent.of(m, "fun", "2026-03"))
        assertEquals(cents("42.10"), Spent.of(m, "groc", "2026-03"))
    }

    // SPEC:R129 SPEC:R40
    @Test fun `R129 a merge joins totals and days R37 would not suggest`() {
        val d1 = recordReceipt(world(), receipt("rc-s", "Granvolta", day, line("pasta", "40.00", "ch-groc", "Groceries")))
        val d = recordRows(d1, BankRow(on(20), -cents("41.20"), "GRANVOLTA SPA", null), category = "groc")
        assertNull(Pairing.match(day, cents("40.00"), BankRow(on(20), -cents("41.20"), "GRANVOLTA SPA", null)))
        val m = merge(d, difference = "fun")
        assertEquals(cents("41.20"), m.payments.single().total)
        assertEquals(cents("958.80"), m.savingsAmount)
    }

    // SPEC:R129 SPEC:R54
    @Test fun `R129 a bank payment charged to a card merged into a receipt from Savings takes the card back down and Savings pays`() {
        val d1 = recordReceipt(world(), receipt("rc-s", "Granvolta", day, line("pasta", "42.10", "ch-groc", "Groceries")))
        val d = recordRows(d1, BankRow(on(12), -cents("42.30"), "POS GRANVOLTA MI 0423", null), category = "groc", source = PaymentSource.Account("card"))
        assertEquals(cents("42.30"), d.accounts.single { it.id == "card" }.balance)
        assertEquals(cents("957.90"), d.savingsAmount)
        val m = merge(d, difference = "fun")
        assertEquals(0L, m.accounts.single { it.id == "card" }.balance)
        assertEquals(PaymentSource.Savings, m.payments.single().source, "the receipt's source")
        assertEquals(cents("957.70"), m.savingsAmount)
    }

    // SPEC:R129 SPEC:R54
    @Test fun `R129 a bank payment from a Goal short of it, merged, gives the Goal and Savings back their shares once`() {
        val d1 = recordReceipt(world(tripSaved = "42.10"), receipt("rc-s", "Granvolta", day, line("pasta", "42.30", "ch-groc", "Groceries")))
        val d = recordRows(d1, BankRow(on(12), -cents("42.30"), "POS GRANVOLTA MI 0423", null), category = "groc", source = PaymentSource.Goal("trip"))
        assertEquals(0L, d.goals.single().saved)
        assertEquals(cents("957.50"), d.savingsAmount, "Savings paid the 0.20 the Goal lacked")
        val m = merge(d)
        assertEquals(cents("42.10"), m.goals.single().saved)
        assertEquals(cents("957.70"), m.savingsAmount)
        assertEquals(0L, m.payments.single().fromSavings)
    }

    // SPEC:R129 SPEC:R54
    @Test fun `R129 a receipt from a Goal that the merged total leaves short warns GOAL_SHORT and Savings pays the 0_20`() {
        val d1 = recordReceipt(world(tripSaved = "42.10"), receipt("rc-s", "Granvolta", day, line("pasta", "42.10", "ch-groc", "Groceries")), PaymentSource.Goal("trip"))
        val d = recordRows(d1, BankRow(on(12), -cents("42.30"), "POS GRANVOLTA MI 0423", null), category = "groc")
        assertEquals(MergeNotice(PaymentNotice.GOAL_SHORT, null), Pairing.mergeNotice(d, "pay-r", "pay-b", "fun"))
        val m = merge(d, difference = "fun")
        val p = m.payments.single()
        assertEquals(PaymentSource.Goal("trip"), p.source)
        assertEquals(cents("0.20"), p.fromSavings)
        assertEquals(0L, m.goals.single().saved)
        assertEquals(cents("999.80"), m.savingsAmount)
    }

    // SPEC:R129 SPEC:R54
    @Test fun `R129 both payments from one Goal - the bank payment's share returns before the merged payment takes its own, no warning`() {
        val d1 = recordReceipt(world(tripSaved = "50.00"), receipt("rc-s", "Granvolta", day, line("pasta", "42.10", "ch-groc", "Groceries")), PaymentSource.Goal("trip"))
        val d = recordRows(d1, BankRow(on(12), -cents("42.30"), "POS GRANVOLTA MI 0423", null), category = "groc", source = PaymentSource.Goal("trip"))
        assertEquals(0L, d.goals.single().saved)
        assertEquals(cents("965.60"), d.savingsAmount)
        assertEquals(MergeNotice(null, null), Pairing.mergeNotice(d, "pay-r", "pay-b", "fun"), "the Goal holds 50.00 once the bank payment is removed")
        val m = merge(d, difference = "fun")
        assertEquals(cents("7.70"), m.goals.single().saved)
        assertEquals(cents("1,000.00"), m.savingsAmount)
        assertEquals(0L, m.payments.single().fromSavings)
    }

    // SPEC:R129 SPEC:R109
    @Test fun `R129 a receipt charged to a card whose charges are not logged warns CHARGES_NOT_LOGGED`() {
        val d1 = recordReceipt(world(), coffeeReceipt(), PaymentSource.Account("loose"))
        val d = recordRows(d1, coffee)
        assertEquals(MergeNotice(PaymentNotice.CHARGES_NOT_LOGGED, null), Pairing.mergeNotice(d, "pay-r", "pay-b", null))
        val m = merge(d)
        assertEquals(cents("4.50"), m.accounts.single { it.id == "loose" }.balance)
        assertEquals(cents("1,000.00"), m.savingsAmount)
    }

    // SPEC:R129 SPEC:R54 SPEC:R95
    @Test fun `R129 a bank payment from a Goal deleted since, merged, returns its share to Savings with one savings row`() {
        val d0 = Writes.goalQuickAdd(world(), "trip", cents("100.00"), null, clock, "t-1")
        val d1 = recordReceipt(d0, coffeeReceipt())
        val d2 = recordRows(d1, coffee, source = PaymentSource.Goal("trip"))
        val d = GoalEditing.delete(d2, "trip", returnSaved = true, clock) { "t-del" }
        assertEquals(cents("991.00"), d.savingsAmount)
        assertEquals(MergeNotice(null, PaymentNotice.SOURCE_GOAL_DELETED), Pairing.mergeNotice(d, "pay-r", "pay-b", null))
        val m = merge(d)
        assertEquals(cents("995.50"), m.savingsAmount)
        val row = m.transactions.single { it.id !in d.transactions.map { t -> t.id } }
        assertEquals("tx-rm" to cents("4.50"), row.id to row.amount)
        assertEquals(m.savingsAmount, replayed(m))
    }

    // SPEC:R129 SPEC:R54 SPEC:R122
    @Test fun `R129 a bank payment whose Goal is in the Trash, merged, settles it - the Goal's restore is refused, Unpair notwithstanding`() {
        val d0 = Writes.goalQuickAdd(world(), "trip", cents("100.00"), null, clock, "t-1")
        val d1 = recordReceipt(d0, coffeeReceipt())
        val d2 = recordRows(d1, coffee, source = PaymentSource.Goal("trip"))
        val (d, payload) = Trash.deleteGoal(d2, "trip", true, clock, Ids("del")::next)!!
        assertNull(Trash.restoreProblem(d, payload))
        val m = merge(d)
        assertEquals(d.savingsAmount + cents("4.50"), m.savingsAmount, "one savings row in the Goal's place (R54)")
        assertEquals(RestoreProblem.WRITTEN_SINCE, Trash.restoreProblem(m, payload))
        val un = Pairing.unpair(m, "pay-r", clock, "tx-u", emptyList())
        assertEquals(RestoreProblem.WRITTEN_SINCE, Trash.restoreProblem(un, payload))
    }

    // SPEC:R129 SPEC:R54 SPEC:R88
    @Test fun `R129 a receipt from a Goal deleted since and a bank payment charged to a card deleted since write one row each`() {
        val d0 = Writes.goalQuickAdd(world(), "trip", cents("100.00"), null, clock, "t-1")
        val d1 = recordReceipt(d0, coffeeReceipt(), PaymentSource.Goal("trip"))
        val d2 = recordRows(d1, BankRow(on(11), -cents("4.70"), "BAR CENTRALE", null), source = PaymentSource.Account("card"))
        val d3 = GoalEditing.delete(d2, "trip", returnSaved = true, clock) { "t-del" }
        val d = Accounts.deleteCascade(d3, "card", clock) { "t-close" }
        assertEquals(MergeNotice(null, PaymentNotice.SOURCE_ACCOUNT_DELETED), Pairing.mergeNotice(d, "pay-r", "pay-b", "fun"))
        val m = merge(d, difference = "fun")
        val written = m.transactions.filter { it.id !in d.transactions.map { t -> t.id } }
        assertEquals(setOf("tx-rm", "tx-ed"), written.map { it.id }.toSet())
        assertEquals(cents("-0.20"), written.single { it.id == "tx-ed" }.amount, "the deleted Goal's share grows by 0.20, taken from Savings")
        assertEquals(d.savingsAmount - cents("0.20"), m.savingsAmount)
        val card = TransactionKey.AccountKey("card").stored
        assertEquals(0L, m.transactions.filter { it.key == card }.sumOf { it.amount }, "the deleted card's rows add up to zero once nothing is charged to it")
        assertEquals(m.savingsAmount, replayed(m))
    }

    // SPEC:R129 SPEC:R26
    @Test fun `R129 of two identical coffee payments of one day, one merged, the other stays, and the two-row statement is DUPLICATE twice`() {
        val d1 = recordReceipt(world(), coffeeReceipt())
        val ids = Ids("pay-b")
        val d = recordRows(d1, coffee, coffee, ids = ids::next)
        assertEquals(cents("986.50"), d.savingsAmount)
        val twin = d.payments.single { it.id == "pay-b-2" }
        val m = merge(d, b = "pay-b-1")
        assertEquals(listOf("pay-r", "pay-b-2"), m.payments.map { it.id })
        assertEquals(twin, m.payments.single { it.id == "pay-b-2" })
        assertEquals(cents("991.00"), m.savingsAmount)
        assertEquals(cents("9.00"), Spent.of(m, "food", "2026-03"))
        assertEquals(d.importState.importedKeys, m.importState.importedKeys)
        assertEquals(listOf(RowStatus.DUPLICATE, RowStatus.DUPLICATE), StatementImport.review(m, listOf(coffee, coffee), day).map { it.status })
    }

    // SPEC:R129 SPEC:R119 SPEC:R107 SPEC:R26
    @Test fun `R129 a merge while an import review is open leaves its ticked twin row to be recorded at Confirm`() {
        val d = twice()
        val opened = StatementImport.review(d, listOf(coffee, coffee), day)
        assertEquals(listOf(RowStatus.DUPLICATE, RowStatus.PAIR), opened.map { it.status })
        // the suggestion turned down, the second coffee ticked under Food
        val review = opened.map {
            if (it.status == RowStatus.DUPLICATE) it.copy(include = false)
            else it.copy(pairPaymentId = null, include = true, draft = StatementImport.draft(it.row, listOf(Part("food", cents("4.50")))))
        }
        val m = merge(d)
        assertEquals(1, StatementImport.confirmSummary(m, review).count, "the merge recorded no new row: it only moved the keys")
        val out = StatementImport.confirm(m, review, clock) { "pay-c" }
        assertEquals(listOf("pay-r", "pay-c"), out.payments.map { it.id })
        assertEquals(cents("991.00"), out.savingsAmount)
        assertEquals(cents("9.00"), Spent.of(out, "food", "2026-03"))
        assertEquals(listOf(RowStatus.DUPLICATE, RowStatus.DUPLICATE), StatementImport.review(out, listOf(coffee, coffee), day).map { it.status })
    }

    // SPEC:R129 SPEC:R125
    @Test fun `R129 a merged pair deleted and restored comes back whole, its row still recorded once`() {
        val m = merge(twice())
        val (after, payload) = Trash.deletePayment(m, "pay-r", clock) { "t-x" }!!
        assertEquals(cents("1,000.00"), after.savingsAmount)
        assertEquals(RowStatus.DUPLICATE, StatementImport.review(after, listOf(coffee), day).single().status, "its keys stay kept (R125)")
        assertNull(Trash.restoreProblem(after, payload))
        val back = Trash.restore(after, payload, clock, Ids("r")::next)
        assertEquals(m.payments, back.payments)
        assertEquals(m.savingsAmount, back.savingsAmount)
        assertEquals(m.importState.importedKeys, back.importState.importedKeys)
    }

    // SPEC:R129 SPEC:R128
    @Test fun `R129 a merge unpaired leaves the receipt payment alone and its row arrives as new`() {
        val m = merge(twice())
        val un = Pairing.unpair(m, "pay-r", clock, "tx-u", emptyList())
        val p = un.payments.single()
        assertEquals("pay-r", p.id)
        assertNull(p.bankKeys)
        assertEquals(cents("4.50"), p.total)
        assertEquals(cents("995.50"), un.savingsAmount)
        assertTrue(StatementImport.keyStrings(StatementImport.keys(coffee)).none { it in un.importState.importedKeys })
        assertNotEquals(RowStatus.DUPLICATE, StatementImport.review(un, listOf(coffee), day).single().status)
    }

    // SPEC:R129
    @Test fun `R129 each refusal is its code and changes nothing`() {
        val d = twice()
        fun refused(code: MergeProblem, r: String, b: String, data: ProfileData = d, difference: String? = null) {
            assertEquals(code, Pairing.mergeProblem(data, r, b, difference), "$r with $b")
            assertEquals(data, merge(data, r, b, difference), "$r with $b changes nothing")
        }
        refused(MergeProblem.PAYMENT_UNKNOWN, "nope", "pay-b")
        refused(MergeProblem.PAYMENT_UNKNOWN, "pay-r", "nope")
        assertNull(Pairing.mergeNotice(d, "pay-r", "nope", null))

        // a refund recorded from a row of money in, on either side
        val withRefund = recordRows(d, BankRow(on(12), cents("4.50"), "BAR CENTRALE RIMBORSO", null)) { "pay-in" }
        refused(MergeProblem.REFUND, "pay-r", "pay-in", withRefund)
        val refundReceipt = withRefund.copy(payments = withRefund.payments.map { if (it.id == "pay-in") it.copy(receiptId = "rc-z", bankKeys = null) else it })
        refused(MergeProblem.REFUND, "pay-in", "pay-b", refundReceipt)

        refused(MergeProblem.NOT_A_RECEIPT_PAYMENT, "pay-b", "pay-b")
        val pair = merge(d)
        val again = recordRows(pair, BankRow(on(12), -cents("4.50"), "BAR CENTRALE", null)) { "pay-c" }
        refused(MergeProblem.NOT_A_RECEIPT_PAYMENT, "pay-r", "pay-c", again)

        refused(MergeProblem.NOT_A_BANK_PAYMENT, "pay-r", "pay-r")
        val byHand = Payments.record(d, PaymentDraft(day, cents("4.50"), PaymentSource.Savings, "Bar", listOf(Part("food", cents("4.50")))), clock, "pay-h")
        refused(MergeProblem.NOT_A_BANK_PAYMENT, "pay-r", "pay-h", byHand)

        // a stray difference part on the receipt side: its parts no longer add up to the merged total
        val stray = d.copy(payments = d.payments.map {
            if (it.id == "pay-r") it.copy(parts = listOf(Part("food", cents("4.70")), Part("fun", cents("-0.20"), difference = true))) else it
        })
        refused(MergeProblem.CANNOT_SAVE, "pay-r", "pay-b", stray)
    }

    // SPEC:R129 SPEC:R119 SPEC:R123
    @Test fun `R129 a receipt payment whose Food part's category was deleted is refused CATEGORY_MISSING, and with Fun given merges under Fun`() {
        val d = twice().let { it.copy(categories = it.categories.filter { c -> c.id != "food" }) }
        assertEquals(MergeProblem.CATEGORY_MISSING, Pairing.mergeProblem(d, "pay-r", "pay-b", null))
        assertEquals(d, merge(d))
        assertEquals(MergeProblem.CATEGORY_MISSING, Pairing.mergeProblem(d, "pay-r", "pay-b", null, replacementCategoryId = "nope"))
        assertNull(Pairing.mergeProblem(d, "pay-r", "pay-b", null, replacementCategoryId = "fun"))
        val m = merge(d, replacement = "fun")
        assertEquals(listOf(Part("fun", cents("4.50"))), m.payments.single().parts)
        assertEquals(listOf("food"), m.payments.single().receiptLines.map { it.categoryId }, "the line keeps its recorded category")
        assertEquals(cents("995.50"), m.savingsAmount)
    }
}
