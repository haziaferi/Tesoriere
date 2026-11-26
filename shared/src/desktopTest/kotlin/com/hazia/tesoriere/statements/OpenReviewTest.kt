package com.hazia.tesoriere.statements

import com.hazia.tesoriere.Fixture
import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.line
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Fixture.receipt
import com.hazia.tesoriere.Ids
import com.hazia.tesoriere.TestClock
import com.hazia.tesoriere.cents
import com.hazia.tesoriere.engine.Trash
import com.hazia.tesoriere.engine.savingsAmount
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.pairing.Pairing
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Spent
import com.hazia.tesoriere.receipts.Receipts
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a change made while an import review is open does to its Confirm (SPEC R107, R119, R125,
 * R127): a payment that takes a row's keys records that row only when the import's count of it
 * grew. Every figure and id here is made up.
 */
class OpenReviewTest {
    private val day = LocalDate(2026, 3, 10)
    private val clock = TestClock(day)

    private fun world(): ProfileData = profile(categories = listOf(category("food", "Food")))
        .copy(receiptSettings = Fixture.receiptSettings())

    private fun coffee(fitid: String? = null) = BankRow(LocalDate(2026, 3, 11), -cents("4.50"), "BAR CENTRALE", fitid)

    private fun review(d: ProfileData, vararg rows: BankRow) = StatementImport.review(d, rows.toList(), day)

    /** Every row not `DUPLICATE` turned down and ticked under Food; a `DUPLICATE` row unticked. */
    private fun ticked(rv: List<ReviewRow>) = rv.map {
        if (it.status == RowStatus.DUPLICATE) it.copy(include = false)
        else it.copy(pairPaymentId = null, include = true, draft = StatementImport.draft(it.row, listOf(Part("food", cents("4.50")))))
    }

    private fun recordReceipt(d: ProfileData, id: String) =
        Receipts.confirm(d, Receipts.setSource(Receipts.open(d, receipt("rc-$id", "Bar Centrale", day, line("caffe", "4.50", "ch-food", "Food"))), PaymentSource.Savings), clock, id)

    /** The first coffee recorded from a statement as B-1, then deleted to the Trash, which keeps its row recorded (SPEC R125). */
    private fun deletedCoffee(a: BankRow): Pair<ProfileData, com.hazia.tesoriere.engine.TrashPayload> {
        val d1 = StatementImport.confirm(world(), ticked(review(world(), a)), clock, Ids("B")::next)
        return Trash.deletePayment(d1, "B-1", clock, Ids("dp")::next)!!
    }

    // SPEC:R125 SPEC:R107 SPEC:R26
    @Test fun `R125 a coffee restored from the Trash while a two-coffee review is open leaves its ticked twin to be recorded`() {
        for (fitid in listOf(false, true)) {
            val a = coffee(if (fitid) "F1" else null); val b = coffee(if (fitid) "F2" else null)
            val (t, payload) = deletedCoffee(a)
            assertEquals(cents("1,000.00"), t.savingsAmount)
            val rv = ticked(review(t, a, b))
            assertEquals(listOf(RowStatus.DUPLICATE, RowStatus.READY), rv.map { it.status }, "fitid=$fitid")
            val back = Trash.restore(t, payload, clock, Ids("rs")::next)
            assertEquals(1, StatementImport.confirmSummary(back, rv).count, "fitid=$fitid: the restore recorded no new row")
            val out = StatementImport.confirm(back, rv, clock, Ids("C")::next)
            assertEquals(listOf("B-1", "C-1"), out.payments.map { it.id }, "fitid=$fitid")
            assertEquals(cents("991.00"), out.savingsAmount, "fitid=$fitid")
            assertEquals(listOf(RowStatus.DUPLICATE, RowStatus.DUPLICATE), review(out, a, b).map { it.status }, "fitid=$fitid")
        }
    }

    // SPEC:R127 SPEC:R107 SPEC:R26
    @Test fun `R127 a coffee restored as a receipt's pair while a two-coffee review is open leaves its ticked twin to be recorded`() {
        for (fitid in listOf(false, true)) {
            val a = coffee(if (fitid) "F1" else null); val b = coffee(if (fitid) "F2" else null)
            val (t0, payload) = deletedCoffee(a)
            // the receipt recorded since the delete: the bank payment may come back as its pair
            val t = recordReceipt(t0, "R")
            val rv = ticked(review(t, a, b))
            assertEquals(RowStatus.DUPLICATE, rv[0].status, "fitid=$fitid")
            assertNull(Trash.restoreProblem(t, payload, pairWith = "R"), "fitid=$fitid")
            val paired = Trash.restore(t, payload, clock, Ids("rs")::next, pairWith = "R")
            assertEquals(listOf("R"), paired.payments.map { it.id }, "fitid=$fitid")
            val out = StatementImport.confirm(paired, rv, clock, Ids("C")::next)
            assertEquals(listOf("R", "C-1"), out.payments.map { it.id }, "fitid=$fitid")
            assertEquals(cents("991.00"), out.savingsAmount, "fitid=$fitid")
            assertEquals(cents("9.00"), Spent.of(out, "food", "2026-03"), "fitid=$fitid")
        }
    }

    // SPEC:R107 SPEC:R26
    @Test fun `R107 a twin recorded by a second review while the first is open leaves the first review's ticked row to be recorded`() {
        for (fitid in listOf(false, true)) {
            val a = coffee(if (fitid) "F1" else null); val b = coffee(if (fitid) "F2" else null)
            val d = world()
            // review A ticks only the second coffee
            val rvA = review(d, a, b).mapIndexed { i, it ->
                it.copy(include = i == 1, draft = StatementImport.draft(it.row, listOf(Part("food", cents("4.50")))))
            }
            // review B, open at the same time, records the first
            val viaB = StatementImport.confirm(d, ticked(review(d, a)), clock, Ids("X")::next)
            assertEquals(1, StatementImport.confirmSummary(viaB, rvA).count, "fitid=$fitid")
            val out = StatementImport.confirm(viaB, rvA, clock, Ids("C")::next)
            assertEquals(listOf("X-1", "C-1"), out.payments.map { it.id }, "fitid=$fitid")
            assertEquals(cents("991.00"), out.savingsAmount, "fitid=$fitid")
            assertEquals(listOf(RowStatus.DUPLICATE, RowStatus.DUPLICATE), review(out, a, b).map { it.status }, "fitid=$fitid")
        }
    }

    // SPEC:R119 SPEC:R118 SPEC:R107
    @Test fun `R119 a hand pair confirmed with the review as it was built leaves the ticked row to the pair`() {
        for (fitid in listOf(false, true)) {
            val a = coffee(if (fitid) "F1" else null); val b = coffee(if (fitid) "F2" else null)
            val d = recordReceipt(world(), "R")
            // the first row unticked, the second ticked and paired by hand; Confirm given the review as built
            val rv = review(d, a, b).mapIndexed { i, it ->
                it.copy(pairPaymentId = null, include = i == 1, draft = StatementImport.draft(it.row, listOf(Part("food", cents("4.50")))))
            }
            val paired = Pairing.pairBankWithPayment(d, "R", rv[1], null, clock, "tx", rv).data
            assertEquals(0, StatementImport.confirmSummary(paired, rv).count, "fitid=$fitid")
            val out = StatementImport.confirm(paired, rv, clock, Ids("C")::next)
            assertEquals(listOf("R"), out.payments.map { it.id }, "fitid=$fitid")
            assertEquals(cents("995.50"), out.savingsAmount, "fitid=$fitid")
        }
    }
}
