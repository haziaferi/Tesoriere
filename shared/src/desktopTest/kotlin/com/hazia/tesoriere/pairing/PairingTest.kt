package com.hazia.tesoriere.pairing

import com.hazia.tesoriere.Fixture
import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.goal
import com.hazia.tesoriere.Fixture.line
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Fixture.receipt
import com.hazia.tesoriere.TestClock
import com.hazia.tesoriere.cents
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.engine.savingsAmount
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.PaymentDraft
import com.hazia.tesoriere.payments.PaymentNotice
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Payments
import com.hazia.tesoriere.payments.Spent
import com.hazia.tesoriere.receipts.ReceiptFile
import com.hazia.tesoriere.receipts.Receipts
import com.hazia.tesoriere.receipts.ReviewNote
import com.hazia.tesoriere.statements.BankRow
import com.hazia.tesoriere.statements.ReviewRow
import com.hazia.tesoriere.statements.RowStatus
import com.hazia.tesoriere.statements.StatementImport
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every store, payee, product, figure and id here is made up, the store named as in R36's example. */
class PairingTest {
    private val day = LocalDate(2026, 3, 10)
    private val clock = TestClock(day)
    private val store = "Esselunga"

    private fun world(): ProfileData = profile(
        goals = listOf(goal("trip", "Trip", saved = "42.10")),
        categories = listOf(category("groc", "Groceries"), category("home", "Household"), category("fun", "Fun")),
    ).copy(
        accounts = listOf(Account("card", Fixture.PROFILE, "Card", type = AccountType.CREDIT)),
        receiptSettings = Fixture.receiptSettings(),
    )

    /** Groceries 30.00 and Household 12.30: 42.30. */
    private fun shop(id: String = "rc-1", date: LocalDate = day, groc: String = "30.00", home: String = "12.30") = receipt(
        id, store, date, line("pasta", groc, "ch-groc", "Groceries"), line("soap", home, "ch-home", "Household"),
    )

    private fun row(amount: String, date: LocalDate, description: String = "POS ESSELUNGA MI 0423", fitid: String? = "FIT-${date.day}") =
        BankRow(date, -cents(amount), description, fitid)

    private fun recordReceipt(d: ProfileData, file: ReceiptFile, source: PaymentSource = PaymentSource.Savings, id: String = "pay-r") =
        Receipts.confirm(d, Receipts.setSource(Receipts.open(d, file), source), clock, id)

    private fun reviewOf(d: ProfileData, vararg rows: BankRow): List<ReviewRow> = StatementImport.review(d, rows.toList(), day)

    private fun on(dayOfMonth: Int) = LocalDate(2026, 3, dayOfMonth)

    // --- R37: one test per row of the table ---

    // SPEC:R37
    @Test fun `R37 equal totals on the receipt's day match EXACT`() =
        assertEquals(Match.EXACT, Pairing.match(day, cents("42.30"), row("42.30", on(10))))

    // SPEC:R37
    @Test fun `R37 equal totals 3 days after the receipt match EXACT`() =
        assertEquals(Match.EXACT, Pairing.match(day, cents("42.30"), row("42.30", on(13))))

    // SPEC:R37
    @Test fun `R37 a bank row 2 days before the receipt does not match`() =
        assertNull(Pairing.match(day, cents("42.30"), row("42.30", on(8))))

    // SPEC:R37
    @Test fun `R37 a bank row 4 days after the receipt does not match`() =
        assertNull(Pairing.match(day, cents("42.30"), row("42.30", on(14))))

    // SPEC:R37 SPEC:R39
    @Test fun `R37 totals 0_50 apart match WITH_DIFFERENCE`() {
        assertEquals(Match.WITH_DIFFERENCE, Pairing.match(day, cents("42.30"), row("42.80", on(11))))
        assertEquals(Match.WITH_DIFFERENCE, Pairing.match(day, cents("42.30"), row("41.80", on(11))))
    }

    // SPEC:R37
    @Test fun `R37 totals 0_51 apart do not match`() {
        assertNull(Pairing.match(day, cents("42.30"), row("42.81", on(11))))
        assertNull(Pairing.match(day, cents("42.30"), row("41.79", on(11))))
    }

    // SPEC:R118
    @Test fun `R118 a row of money in never matches`() =
        assertNull(Pairing.match(day, cents("42.30"), BankRow(on(11), cents("42.30"), "ESSELUNGA RIMBORSO", null)))

    // --- R38: whichever arrives first ---

    // SPEC:R38 SPEC:R118 SPEC:R119
    @Test fun `R38 a 42_30 receipt then its bank row leaves one payment of 42_30 with bankKeys`() {
        val d1 = recordReceipt(world(), shop())
        val bank = row("42.30", on(12))
        val review = reviewOf(d1, bank)
        val rr = review.single()
        assertEquals(RowStatus.PAIR, rr.status)
        assertFalse(rr.include)
        assertEquals("pay-r", rr.pairPaymentId)
        // the import's Confirm records nothing for it
        val imported = StatementImport.confirm(d1, review, clock) { "imp-1" }
        assertEquals(1, imported.payments.size)
        assertEquals(d1.savings, imported.savings)

        assertTrue(Pairing.canConfirmPair(Pairing.differencePart(cents("42.30"), cents("42.30")), null))
        val d2 = Pairing.pairBankWithPayment(d1, "pay-r", rr, null, clock, "tx-1", emptyList())
        val p = d2.payments.single()
        assertEquals(cents("42.30"), p.total)
        assertEquals(day, p.date)
        assertEquals(PaymentSource.Savings, p.source)
        assertEquals(StatementImport.keys(bank), p.bankKeys)
        assertEquals("rc-1", p.receiptId)
        assertEquals(listOf(Part("groc", cents("30.00")), Part("home", cents("12.30"))), p.parts)
        assertEquals(cents("957.70"), d2.savings.amount)
        // the row is recorded now: a later import of it is a duplicate
        assertEquals(RowStatus.DUPLICATE, reviewOf(d2, bank).single().status)
        assertTrue(StatementImport.keyStrings(StatementImport.keys(bank)).all { it in d2.importState.importedKeys })
    }

    // SPEC:R38 SPEC:R119 SPEC:R35
    @Test fun `R38 a bank row filed under Groceries then its receipt leaves one payment with Groceries 30_00 and Household 12_30, dated the receipt's day, keeping bankKeys`() {
        val d0 = world()
        val bank = row("42.30", on(12))
        val filed = reviewOf(d0, bank).map { it.copy(draft = StatementImport.draft(it.row, listOf(Part("groc", cents("42.30"))))) }
        val d1 = StatementImport.confirm(d0, filed, clock) { "pay-b" }
        assertEquals(cents("957.70"), d1.savings.amount)

        val review = Receipts.setSource(Receipts.open(d1, shop(date = on(10))), PaymentSource.Account("card"))
        assertEquals(listOf("pay-b" to Match.EXACT), Pairing.paymentCandidates(d1, review.file))
        val d2 = Pairing.pairReceiptWithPayment(d1, "pay-b", review, null, clock, "tx-1")
        val p = d2.payments.single()
        assertEquals(listOf(Part("groc", cents("30.00")), Part("home", cents("12.30"))), p.parts)
        assertEquals(on(10), p.date)
        assertEquals(StatementImport.keys(bank), p.bankKeys)
        assertEquals("rc-1", p.receiptId)
        assertEquals(2, p.receiptLines.size)
        assertEquals(store, p.payee)
        assertEquals(PaymentSource.Account("card"), p.source)
        assertEquals(cents("1,000.00"), d2.savings.amount)
        assertEquals(cents("42.30"), d2.accounts.single().balance)
        assertEquals(listOf("rc-1"), d2.receiptSettings!!.recordedReceiptIds)
        assertEquals(mapOf("ch-groc" to "groc", "ch-home" to "home"), d2.receiptSettings.categoryLinks)
        assertEquals("account:card", d2.receiptSettings.storeSources[store])
        assertEquals("esselunga mi", d2.receiptSettings.storeLinks[store])
        assertEquals(cents("30.00"), Spent.of(d2, "groc", "2026-03"))
        assertEquals(cents("12.30"), Spent.of(d2, "home", "2026-03"))
    }

    // --- R39: difference part ---

    // SPEC:R39 SPEC:R119
    @Test fun `R39 a 42_10 receipt with a 42_30 bank row records 42_30 with a 0_20 part, confirm off until filed`() {
        val d1 = recordReceipt(world(), shop(home = "12.10"))
        val rr = reviewOf(d1, row("42.30", on(11))).single()
        assertEquals("pay-r", rr.pairPaymentId)
        val diff = Pairing.differencePart(cents("42.10"), cents("42.30"))
        assertEquals(cents("0.20"), diff)
        assertFalse(Pairing.canConfirmPair(diff, null))
        assertEquals(PairProblem.DIFFERENCE_UNFILED, Pairing.bankPairProblem(d1, "pay-r", rr, null, emptyList()))
        assertEquals(d1, Pairing.pairBankWithPayment(d1, "pay-r", rr, null, clock, "tx-1", emptyList()), "refused, it changes nothing, never a throw")
        assertTrue(Pairing.canConfirmPair(diff, "fun"))
        val p = Pairing.pairBankWithPayment(d1, "pay-r", rr, "fun", clock, "tx-1", emptyList()).payments.single()
        assertEquals(cents("42.30"), p.total)
        assertEquals(listOf(Part("groc", cents("30.00")), Part("home", cents("12.10")), Part("fun", cents("0.20"), difference = true)), p.parts)
    }

    // SPEC:R39 SPEC:R23
    @Test fun `R39 a bank amount 0_20 below the receipt gives a -0_20 part`() {
        val d1 = recordReceipt(world(), shop())
        val rr = reviewOf(d1, row("42.10", on(11))).single()
        assertEquals(cents("-0.20"), Pairing.differencePart(cents("42.30"), cents("42.10")))
        val d2 = Pairing.pairBankWithPayment(d1, "pay-r", rr, "fun", clock, "tx-1", emptyList())
        val p = d2.payments.single()
        assertEquals(cents("42.10"), p.total)
        assertEquals(Part("fun", cents("-0.20"), difference = true), p.parts.last())
        assertEquals(cents("957.90"), d2.savings.amount)
        assertEquals(cents("-0.20"), Spent.of(d2, "fun", "2026-03"))
    }

    // --- R40: by hand ---

    // SPEC:R40 SPEC:R119
    @Test fun `R40 a 40_00 receipt and a 41_20 bank row paired by hand give 41_20 with a 1_20 part`() {
        val d1 = recordReceipt(world(), shop(groc = "27.70"))
        val bank = row("41.20", on(11))
        assertNull(Pairing.match(day, cents("40.00"), bank))
        val rr = reviewOf(d1, bank).single()
        assertEquals(RowStatus.READY, rr.status)
        assertNull(rr.pairPaymentId)
        val p = Pairing.pairBankWithPayment(d1, "pay-r", rr, "home", clock, "tx-1", emptyList()).payments.single()
        assertEquals(cents("41.20"), p.total)
        assertEquals(Part("home", cents("1.20"), difference = true), p.parts.last())
    }

    // SPEC:R40 SPEC:R119
    @Test fun `R40 a bank row paired by hand with a 40_00 receipt from the other side gives 41_20 with a 1_20 part`() {
        val d0 = world()
        val bank = row("41.20", on(15))
        val filed = reviewOf(d0, bank).map { it.copy(draft = StatementImport.draft(it.row, listOf(Part("groc", cents("41.20"))))) }
        val d1 = StatementImport.confirm(d0, filed, clock) { "pay-b" }
        val review = Receipts.open(d1, shop(groc = "27.70"))
        assertEquals(emptyList(), Pairing.paymentCandidates(d1, review.file))
        val p = Pairing.pairReceiptWithPayment(d1, "pay-b", review, "fun", clock, "tx-1").payments.single()
        assertEquals(cents("41.20"), p.total)
        assertEquals(listOf(Part("groc", cents("27.70")), Part("home", cents("12.30")), Part("fun", cents("1.20"), difference = true)), p.parts)
    }

    // --- R36 and R120: store links ---

    // SPEC:R36 SPEC:R120 SPEC:R118
    @Test fun `R36 after pairing Esselunga with POS ESSELUNGA MI 0423, the next Esselunga receipt is suggested first with rows matching that payee and then with the others`() {
        val d0 = recordReceipt(world(), shop())
        val first = row("42.30", on(11), "POS ESSELUNGA MI 0423")
        val d1 = Pairing.pairBankWithPayment(d0, "pay-r", reviewOf(d0, first).single(), null, clock, "tx-1", emptyList())
        assertEquals("esselunga mi", d1.receiptSettings!!.storeLinks[store])

        val next = on(20)
        val rows = listOf(
            row("18.00", on(21), "POS ESSELUNGA MI 0611", "FIT-A"),
            row("18.00", on(21), "POS ESSELUNGA TO 0611", "FIT-B"),
            row("18.00", on(21), "BAR CENTRALE", "FIT-C"),
        )
        // the link ranks, never hides (owner answer of 2026-11-25)
        assertEquals(listOf(rows[0], rows[1], rows[2]), Pairing.candidates(d1, store, next, cents("18.00"), rows).map { it.first })
        assertEquals(rows[0], Pairing.candidates(d1, store, next, cents("18.00"), rows.reversed()).first().first)
        // a store with no link yet is suggested with every row that meets R37
        assertEquals(3, Pairing.candidates(d1, "Bottega Faro", next, cents("18.00"), rows).size)
    }

    // SPEC:R120 SPEC:R40
    @Test fun `R120 a later pair by hand with ESSELUNGA SPA keeps the link esselunga mi`() {
        val d0 = recordReceipt(world(), shop())
        val d1 = Pairing.pairBankWithPayment(d0, "pay-r", reviewOf(d0, row("42.30", on(11))).single(), null, clock, "tx-1", emptyList())
        val d2 = recordReceipt(d1, shop("rc-2", on(20)), id = "pay-r2")
        val other = reviewOf(d2, row("42.30", on(21), "ESSELUNGA SPA", "FIT-X")).single()
        // not matching the link, it is still named, there being no better row (R36, R120)
        assertEquals(RowStatus.PAIR, other.status)
        assertEquals("pay-r2", other.pairPaymentId)
        val d3 = Pairing.pairBankWithPayment(d2, "pay-r2", other, null, clock, "tx-2", emptyList())
        assertEquals("esselunga mi", d3.receiptSettings!!.storeLinks[store])
    }

    // --- R37 and R118: suggestions record nothing ---

    // SPEC:R37 SPEC:R118
    @Test fun `R37 no pair is recorded without a pair call`() {
        val d1 = recordReceipt(world(), shop())
        val review = reviewOf(d1, row("42.30", on(12)))
        assertEquals(recordReceipt(world(), shop()), d1)
        // even ticked by hand, a row that names a pair is not recorded by the import's Confirm
        val ticked = review.map { it.copy(include = true, draft = StatementImport.draft(it.row, listOf(Part("groc", cents("42.30"))))) }
        val imported = StatementImport.confirm(d1, ticked, clock) { "imp-1" }
        assertEquals(d1.payments, imported.payments)
        assertEquals(d1.savings, imported.savings)
        assertEquals(0, StatementImport.confirmSummary(d1, ticked).count)
        // the user turns the suggestion down: the row is recorded as its own payment
        val declined = ticked.map { it.copy(pairPaymentId = null) }
        assertEquals(2, StatementImport.confirm(d1, declined, clock) { "imp-1" }.payments.size)
    }

    // SPEC:R118
    @Test fun `R118 a refund or a payment with bank keys is never named, and of rows on 11 and 12 March only the first names the receipt`() {
        val d0 = world()
        val refund = Payments.record(d0, com.hazia.tesoriere.payments.PaymentDraft(day, cents("-42.30"), PaymentSource.Savings, store, listOf(Part("groc", cents("-42.30")))), clock, "pay-x")
        assertEquals(RowStatus.READY, reviewOf(refund, row("42.30", on(11))).single().status)

        val d1 = recordReceipt(d0, shop())
        val two = reviewOf(d1, row("42.30", on(12), fitid = "F-12"), row("42.30", on(11), fitid = "F-11"))
        assertEquals(listOf(on(12) to null, on(11) to "pay-r"), two.map { it.row.date to it.pairPaymentId })
        assertEquals(RowStatus.READY, two.first().status)
        assertEquals(RowStatus.READY, reviewOf(d1, row("42.30", on(14))).single().status)
        assertEquals(RowStatus.INCOMING, reviewOf(d1, BankRow(on(11), cents("42.30"), "POS ESSELUNGA MI 0423", "F-IN")).single().status)

        val paired = Pairing.pairBankWithPayment(d1, "pay-r", two[1], null, clock, "tx-1", emptyList())
        assertEquals(RowStatus.READY, reviewOf(paired, row("42.30", on(12), fitid = "F-12b", description = "POS ESSELUNGA MI 0424")).single().status)
        assertEquals(PairProblem.PAYMENT_NOT_PAIRABLE, Pairing.bankPairProblem(paired, "pay-r", two[0], null, emptyList()))
    }

    // SPEC:R118 SPEC:R38
    @Test fun `R118 an exact total is named before one within the tolerance`() {
        val d1 = recordReceipt(recordReceipt(world(), shop("rc-1"), id = "pay-near"), shop("rc-2", home = "12.10"), id = "pay-off")
        val named = reviewOf(d1, row("42.10", on(11), fitid = "F-1")).single()
        assertEquals("pay-off", named.pairPaymentId)
    }

    // --- R119: what cannot pair, and the payment path ---

    // SPEC:R119 SPEC:R54
    @Test fun `R119 a 42_10 receipt from a Goal holding 42_10 paired with a 42_30 row warns and takes 0_20 from Savings`() {
        val d1 = recordReceipt(world(), shop(home = "12.10"), PaymentSource.Goal("trip"))
        assertEquals(0L, d1.goals.single().saved)
        val rr = reviewOf(d1, row("42.30", on(11))).single()
        assertEquals(PaymentNotice.GOAL_SHORT, Pairing.bankPairNotice(d1, "pay-r", rr, "fun"))
        val d2 = Pairing.pairBankWithPayment(d1, "pay-r", rr, "fun", clock, "tx-1", emptyList())
        assertEquals(0L, d2.goals.single().saved)
        assertEquals(cents("999.80"), d2.savings.amount)
        assertEquals(cents("0.20"), d2.payments.single().fromSavings)
    }

    // SPEC:R119
    @Test fun `R119 a row of money in, a recorded row or a difference under a category that does not exist cannot pair`() {
        val d1 = recordReceipt(world(), shop())
        val moneyIn = reviewOf(d1, BankRow(on(11), cents("42.30"), "ESSELUNGA", "F-IN")).single()
        assertEquals(PairProblem.ROW_NOT_PAIRABLE, Pairing.bankPairProblem(d1, "pay-r", moneyIn, null, emptyList()))
        val other = row("42.30", on(11), fitid = "F-O")
        val recorded = StatementImport.confirm(d1, reviewOf(d1, other).map { it.copy(pairPaymentId = null, include = true, draft = StatementImport.draft(it.row, listOf(Part("groc", cents("42.30"))))) }, clock) { "pay-o" }
        assertEquals(PairProblem.ROW_RECORDED, Pairing.bankPairProblem(recorded, "pay-r", reviewOf(d1, other).single(), null, emptyList()))
        val off = reviewOf(d1, row("42.40", on(11))).single()
        assertEquals(PairProblem.CANNOT_SAVE, Pairing.bankPairProblem(d1, "pay-r", off, "nowhere", emptyList()))
        // a bank payment holds no receipt: it is paired from the receipt's side, not this one
        assertEquals(PairProblem.PAYMENT_NOT_PAIRABLE, Pairing.bankPairProblem(recorded, "pay-o", off, null, emptyList()))
    }

    // SPEC:R119 SPEC:R115
    @Test fun `R119 a receipt with an unlinked category cannot pair with a bank payment until it is linked`() {
        val d0 = world()
        val bank = row("16.00", on(12), "POS VIVAIO VERDE 0001")
        val d1 = StatementImport.confirm(d0, reviewOf(d0, bank).map { it.copy(draft = StatementImport.draft(it.row, listOf(Part("groc", cents("16.00"))))) }, clock) { "pay-b" }
        val r = Receipts.open(d1, receipt("rc-g", "Vivaio Verde", on(11), line("seeds", "16.00", "ch-garden", "Garden")))
        assertEquals(PairProblem.RECEIPT_PROBLEM, Pairing.receiptPairProblem(d1, "pay-b", r, null))
        val added = Receipts.addAsNew(r, "ch-garden", "c-new", Fixture.PROFILE)
        assertNull(Pairing.receiptPairProblem(d1, "pay-b", added, null))
        val d2 = Pairing.pairReceiptWithPayment(d1, "pay-b", added, null, clock, "tx-1")
        assertEquals("Garden", d2.categories.single { it.id == "c-new" }.name)
        assertEquals("c-new", d2.receiptSettings!!.categoryLinks["ch-garden"])
        assertEquals(listOf(Part("c-new", cents("16.00"))), d2.payments.single().parts)
        // paired once, it is no candidate for a second receipt
        assertEquals(emptyList(), Pairing.paymentCandidates(d2, receipt("rc-h", "Vivaio Verde", on(11), line("seeds", "16.00", "ch-garden", "Garden"))))
        assertEquals(PairProblem.PAYMENT_NOT_PAIRABLE, Pairing.receiptPairProblem(d2, "pay-b", Receipts.open(d2, receipt("rc-h", "Vivaio Verde", on(11), line("seeds", "16.00", "ch-garden", "Garden"))), null))
    }

    // --- after review of Task 17 ---

    // SPEC:R119 SPEC:R107 SPEC:R40
    @Test fun `R119 a row paired by hand while its import review is open is not recorded again by that Confirm`() {
        val d1 = recordReceipt(world(), shop())
        val bank = row("42.30", on(14))
        val review = reviewOf(d1, bank).map { it.copy(draft = StatementImport.draft(it.row, listOf(Part("groc", cents("42.30"))))) }
        assertEquals(RowStatus.READY, review.single().status)
        assertTrue(review.single().include)
        val d2 = Pairing.pairBankWithPayment(d1, "pay-r", review.single(), null, clock, "tx-1", emptyList())
        assertTrue(StatementImport.canConfirm(d2, review))
        assertEquals(0, StatementImport.confirmSummary(d2, review).count)
        val d3 = StatementImport.confirm(d2, review, clock) { "imp-1" }
        assertEquals(1, d3.payments.size)
        assertEquals(cents("957.70"), d3.savings.amount)
    }

    // SPEC:R118 SPEC:R37
    @Test fun `R118 rows of -42_40 on 11 March and -42_30 on 12 March give the 42_30 receipt to the 12 March row`() {
        val d1 = recordReceipt(world(), shop())
        val review = reviewOf(d1, row("42.40", on(11), fitid = "F-11"), row("42.30", on(12), fitid = "F-12"))
        assertEquals(listOf(on(12) to "pay-r", on(11) to null), review.map { it.row.date to it.pairPaymentId })
    }

    // SPEC:R118
    @Test fun `R118 two 42_30 receipts of one day and rows on 11 and 12 March name one receipt each, the first recorded to the 11 March row`() {
        val d1 = recordReceipt(recordReceipt(world(), shop("rc-1"), id = "pay-1"), shop("rc-2"), id = "pay-2")
        val review = reviewOf(d1, row("42.30", on(12), fitid = "F-12"), row("42.30", on(11), fitid = "F-11"))
        assertEquals(listOf(on(12) to "pay-2", on(11) to "pay-1"), review.map { it.row.date to it.pairPaymentId })
    }

    // SPEC:R118 SPEC:R114 SPEC:R115
    @Test fun `R118 a receipt that could pair with a bank payment notes a pair available and can still be confirmed`() {
        val d0 = world()
        val d1 = StatementImport.confirm(d0, reviewOf(d0, row("42.30", on(12))).map { it.copy(draft = StatementImport.draft(it.row, listOf(Part("groc", cents("42.30"))))) }, clock) { "pay-b" }
        val r = Receipts.open(d1, shop())
        assertEquals(listOf<ReviewNote>(ReviewNote.PairAvailable("pay-b")), Receipts.notes(d1, r))
        assertNull(Receipts.problem(d1, r))
        assertEquals(2, Receipts.confirm(d1, r, clock, "pay-r").payments.size)
        // with no bank payment to pair, no such note
        assertEquals(emptyList(), Receipts.notes(d0, Receipts.open(d0, shop())))
    }

    // SPEC:R118
    @Test fun `R118 a named payment removed while the review is open leaves its row unrecorded, and the next import offers it READY`() {
        val d1 = recordReceipt(world(), shop())
        val bank = row("42.30", on(12))
        val review = reviewOf(d1, bank)
        assertEquals("pay-r", review.single().pairPaymentId)
        val d2 = Payments.remove(d1, "pay-r", clock, "tx-1")
        val d3 = StatementImport.confirm(d2, review, clock) { "imp-1" }
        assertTrue(d3.payments.isEmpty())
        assertEquals(RowStatus.READY, reviewOf(d3, bank).single().status)
    }

    // SPEC:R119 SPEC:R25
    @Test fun `R119 a receipt of 30 March paired with its row of 2 April counts in March`() {
        val d1 = recordReceipt(world(), shop(date = on(30)))
        val rr = reviewOf(d1, row("42.30", LocalDate(2026, 4, 2))).single()
        assertEquals("pay-r", rr.pairPaymentId)
        val d2 = Pairing.pairBankWithPayment(d1, "pay-r", rr, null, clock, "tx-1", emptyList())
        assertEquals(on(30), d2.payments.single().date)
        assertEquals(cents("30.00"), Spent.of(d2, "groc", "2026-03"))
        assertEquals(0L, Spent.of(d2, "groc", "2026-04"))
    }

    // SPEC:R119 SPEC:R109
    @Test fun `R119 a receipt charged to a card whose charges are not logged warns when paired`() {
        val d0 = world().let { it.copy(accounts = it.accounts + Account("loose", Fixture.PROFILE, "Loose card", type = AccountType.CREDIT, chargesNotLogged = true, payoffCategoryId = "groc")) }
        val d1 = recordReceipt(d0, shop(), PaymentSource.Account("loose"))
        val rr = reviewOf(d1, row("42.30", on(11))).single()
        assertEquals(PaymentNotice.CHARGES_NOT_LOGGED, Pairing.bankPairNotice(d1, "pay-r", rr, null))
        val bankFirst = StatementImport.confirm(d0, reviewOf(d0, row("42.30", on(11))).map { it.copy(draft = StatementImport.draft(it.row, listOf(Part("groc", cents("42.30"))))) }, clock) { "pay-b" }
        val review = Receipts.setSource(Receipts.open(bankFirst, shop()), PaymentSource.Account("loose"))
        assertEquals(PaymentNotice.CHARGES_NOT_LOGGED, Pairing.receiptPairNotice(bankFirst, "pay-b", review, null))
    }

    // SPEC:R119 SPEC:R54
    @Test fun `R119 a receipt from a Goal deleted since, paired with a 42_30 row, logs 0_20 out of Savings`() {
        val d1 = recordReceipt(world(), shop(home = "12.10"), PaymentSource.Goal("trip"))
        val gone = d1.copy(goals = emptyList())
        val rr = reviewOf(gone, row("42.30", on(11))).single()
        val d2 = Pairing.pairBankWithPayment(gone, "pay-r", rr, "fun", clock, "tx-1", emptyList())
        assertEquals(cents("999.80"), d2.savings.amount)
        val t = d2.transactions.single()
        assertEquals(cents("-0.20"), t.amount)
        assertEquals("savings", t.key)
        assertEquals(PaymentSource.Goal("trip"), d2.payments.single().source)
    }

    // SPEC:R119 SPEC:R54 SPEC:R35
    @Test fun `R119 a bank payment from a Goal deleted since, paired with a receipt from Savings, returns its total in a savings row`() {
        val d0 = world()
        val d1 = StatementImport.confirm(d0, reviewOf(d0, row("42.10", on(11))).map { it.copy(draft = StatementImport.draft(it.row, listOf(Part("groc", cents("42.10"))), PaymentSource.Goal("trip"))) }, clock) { "pay-b" }
        val gone = d1.copy(goals = emptyList())
        // Savings picked: left as it opened, the pair would keep the bank payment's source (SPEC R35)
        val d2 = Pairing.pairReceiptWithPayment(gone, "pay-b", Receipts.setSource(Receipts.open(gone, shop(home = "12.10")), PaymentSource.Savings), null, clock, "tx-1")
        val p = d2.payments.single()
        assertEquals(PaymentSource.Savings, p.source)
        assertEquals(cents("1,000.00"), d2.savings.amount)
        assertEquals(cents("42.10"), d2.transactions.single { it.key == "savings" }.amount)
    }

    // SPEC:R119 SPEC:R54 SPEC:R88
    @Test fun `R119 a receipt charged to a card deleted since, paired with a 42_50 row, logs the 0_20 more charged to it`() {
        val d1 = recordReceipt(world(), shop(), PaymentSource.Account("card"))
        val gone = d1.copy(accounts = emptyList())
        val rr = reviewOf(gone, row("42.50", on(11))).single()
        val d2 = Pairing.pairBankWithPayment(gone, "pay-r", rr, "fun", clock, "tx-1", emptyList())
        assertEquals(cents("1,000.00"), d2.savings.amount)
        val t = d2.transactions.single()
        assertEquals("acct:card", t.key)
        assertEquals(cents("-0.20"), t.amount)
    }

    // SPEC:R119 SPEC:R54 SPEC:R88 SPEC:R35
    @Test fun `R119 a bank payment charged to a card deleted since, paired with a receipt from Savings, moves it to Savings and logs the charge off the card`() {
        val d0 = world()
        val d1 = StatementImport.confirm(d0, reviewOf(d0, row("42.30", on(11))).map { it.copy(draft = StatementImport.draft(it.row, listOf(Part("groc", cents("42.30"))), PaymentSource.Account("card"))) }, clock) { "pay-b" }
        val gone = d1.copy(accounts = emptyList())
        val d2 = Pairing.pairReceiptWithPayment(gone, "pay-b", Receipts.setSource(Receipts.open(gone, shop()), PaymentSource.Savings), null, clock, "tx-1")
        assertEquals(PaymentSource.Savings, d2.payments.single().source)
        assertEquals(cents("957.70"), d2.savings.amount)
        val t = d2.transactions.single { it.key == "acct:card" }
        assertEquals(cents("42.30"), t.amount)
    }

    // SPEC:R118
    @Test fun `R118 42_30 receipts of 10 and 11 March with rows on 11 and 14 March name both pairs`() {
        val d1 = recordReceipt(recordReceipt(world(), shop("rc-1", on(10)), id = "pay-10"), shop("rc-2", on(11)), id = "pay-11")
        val review = reviewOf(d1, row("42.30", on(14), fitid = "F-14"), row("42.30", on(11), fitid = "F-11"))
        assertEquals(listOf(on(14) to "pay-11", on(11) to "pay-10"), review.map { it.row.date to it.pairPaymentId })
        assertTrue(review.all { it.status == RowStatus.PAIR && !it.include })
    }

    // SPEC:R107 SPEC:R119
    @Test fun `R107 a ticked duplicate of an earlier import with no FITID is recorded again`() {
        val d0 = world()
        val coffee = BankRow(on(11), -cents("3.50"), "BAR CENTRALE", null)
        fun filed(d: ProfileData) = reviewOf(d, coffee).map { it.copy(include = true, draft = StatementImport.draft(it.row, listOf(Part("fun", cents("3.50"))))) }
        val d1 = StatementImport.confirm(d0, filed(d0), clock) { "pay-1" }
        val again = filed(d1)
        assertEquals(RowStatus.DUPLICATE, again.single().status)
        assertEquals(1, StatementImport.confirmSummary(d1, again).count)
        val d2 = StatementImport.confirm(d1, again, clock) { "pay-2" }
        assertEquals(2, d2.payments.size)
        assertEquals(cents("993.00"), d2.savings.amount)
    }

    // SPEC:R118 SPEC:R37
    @Test fun `R118 a 42_30 and a 42_50 receipt with rows of 42_30 on 10 and 12 March and 42_50 on 13 March keep both exact pairs`() {
        val d1 = recordReceipt(recordReceipt(world(), shop("rc-1", on(10)), id = "pay-p"), shop("rc-2", on(11), home = "12.50"), id = "pay-q")
        val review = reviewOf(
            d1, row("42.30", on(10), fitid = "F-A"), row("42.30", on(12), fitid = "F-B"), row("42.50", on(13), fitid = "F-C"),
        )
        assertEquals(listOf(on(13) to "pay-q", on(12) to null, on(10) to "pay-p"), review.map { it.row.date to it.pairPaymentId })
        assertEquals(RowStatus.READY, review[1].status)
    }

    // SPEC:R119
    @Test fun `R119 an unknown payment id gives no draft, no notice and PAYMENT_NOT_PAIRABLE, never a throw`() {
        val d = world()
        val rr = reviewOf(d, row("42.30", on(12))).single()
        assertNull(Pairing.bankPairDraft(d, "nope", rr, null))
        assertNull(Pairing.bankPairNotice(d, "nope", rr, null))
        assertEquals(PairProblem.PAYMENT_NOT_PAIRABLE, Pairing.bankPairProblem(d, "nope", rr, null, emptyList()))
        val review = Receipts.open(d, shop())
        assertNull(Pairing.receiptPairDraft(d, "nope", review, null))
        assertNull(Pairing.receiptPairNotice(d, "nope", review, null))
        assertEquals(PairProblem.PAYMENT_NOT_PAIRABLE, Pairing.receiptPairProblem(d, "nope", review, null))
    }

    // SPEC:R119 SPEC:R107
    @Test fun `R119 two -4_50 rows with no FITID and one 4_50 receipt under Food, the first paired and the second ticked, leave two payments and Food at 9_00`() {
        val base = world().let { it.copy(categories = it.categories + category("food", "Food")) }
        val d1 = recordReceipt(base, receipt("rc-c", "Bar Centrale", day, line("caffe", "4.50", "ch-food", "Food")))
        val coffee = BankRow(on(11), -cents("4.50"), "BAR CENTRALE", null)
        val review = reviewOf(d1, coffee, coffee)
        val pairRow = review.single { it.status == RowStatus.PAIR }
        val other = review.single { it.status != RowStatus.PAIR }
        assertTrue(other.include)
        val filed = other.copy(draft = PaymentDraft(coffee.date, cents("4.50"), PaymentSource.Savings, other.payee, listOf(Part("food", cents("4.50")))))
        val paired = Pairing.pairBankWithPayment(d1, "pay-r", pairRow, null, clock, "tx-1", emptyList())
        val rows = listOf(pairRow, filed)
        assertEquals(1, StatementImport.confirmSummary(paired, rows).count)
        var n = 0
        val after = StatementImport.confirm(paired, rows, clock) { "imp-${++n}" }
        assertEquals(2, after.payments.size)
        assertEquals(cents("991.00"), after.savingsAmount)
        assertEquals(cents("9.00"), Spent.of(after, "food", "2026-03"))
    }

    // --- after review of the receipts and pairs pull request: twins ---

    /** Two 4.50 receipts from Bar Centrale on 10 March under Food, pay-a and pay-b. */
    private fun twoCoffees(): ProfileData {
        val base = world().let { it.copy(categories = it.categories + category("food", "Food")) }
        val a = recordReceipt(base, receipt("rc-a", "Bar Centrale", day, line("caffe", "4.50", "ch-food", "Food")), id = "pay-a")
        return recordReceipt(a, receipt("rc-b", "Bar Centrale", day, line("caffe", "4.50", "ch-food", "Food")), id = "pay-b")
    }

    // SPEC:R119 SPEC:R118 SPEC:R26
    @Test fun `R119 two 4_50 receipts and two -4_50 rows with no FITID pair one each, leaving two payments and Food at 9_00`() {
        val d = twoCoffees()
        val coffee = BankRow(on(11), -cents("4.50"), "BAR CENTRALE", null)
        val review = reviewOf(d, coffee, coffee)
        assertEquals(setOf("pay-a", "pay-b"), review.mapNotNull { it.pairPaymentId }.toSet())
        assertTrue(review.all { it.status == RowStatus.PAIR })
        val r1 = review.single { it.pairPaymentId == "pay-a" }
        val r2 = review.single { it.pairPaymentId == "pay-b" }
        val d1 = Pairing.pairBankWithPayment(d, "pay-a", r1, null, clock, "tx-1", review)
        assertNull(Pairing.bankPairProblem(d1, "pay-b", r2, null, review), "the payment holding the shared key stands for one row only")
        assertEquals(PairProblem.ROW_RECORDED, Pairing.bankPairProblem(d1, "pay-b", r2, null, emptyList()), "without the review, the key held is the row")
        val d2 = Pairing.pairBankWithPayment(d1, "pay-b", r2, null, clock, "tx-2", review)
        assertTrue(StatementImport.rowRecorded(d2, r2, review), "both rows are recorded now")
        assertTrue(StatementImport.rowRecorded(d2, r1, review))
        val after = StatementImport.confirm(d2, review, clock) { "imp" }
        assertEquals(2, after.payments.size)
        assertTrue(after.payments.all { it.bankKeys == r1.keys })
        assertEquals(cents("991.00"), after.savingsAmount)
        assertEquals(cents("9.00"), Spent.of(after, "food", "2026-03"))
        assertEquals(listOf(RowStatus.DUPLICATE, RowStatus.DUPLICATE), reviewOf(after, coffee, coffee).map { it.status })
    }

    // SPEC:R119 SPEC:R26
    @Test fun `R119 two -4_50 rows with FITIDs F1 and F2 pair with two 4_50 receipts, each row compared by its FITID`() {
        val d = twoCoffees()
        val c1 = BankRow(on(11), -cents("4.50"), "BAR CENTRALE", "F1")
        val c2 = BankRow(on(11), -cents("4.50"), "BAR CENTRALE", "F2")
        val review = reviewOf(d, c1, c2)
        val r1 = review.single { it.pairPaymentId == "pay-a" }
        val r2 = review.single { it.pairPaymentId == "pay-b" }
        val d1 = Pairing.pairBankWithPayment(d, "pay-a", r1, null, clock, "tx-1", emptyList())
        assertNull(Pairing.bankPairProblem(d1, "pay-b", r2, null, emptyList()), "F2 is not recorded, though F1's payment holds the same synthetic key")
        assertEquals(PairProblem.ROW_RECORDED, Pairing.bankPairProblem(d1, "pay-b", r1, null, review), "F1 is")
        val d2 = Pairing.pairBankWithPayment(d1, "pay-b", r2, null, clock, "tx-2", emptyList())
        val after = StatementImport.confirm(d2, review, clock) { "imp" }
        assertEquals(listOf("F1", "F2"), after.payments.map { it.bankKeys?.fitid })
        assertEquals(cents("991.00"), after.savingsAmount)
        assertEquals(cents("9.00"), Spent.of(after, "food", "2026-03"))
    }

    // SPEC:R119 SPEC:R40
    @Test fun `R119 a pair its check refuses changes nothing from either side, never a throw`() {
        val d1 = recordReceipt(world(), shop())
        val recorded = StatementImport.confirm(d1, reviewOf(d1, row("42.30", on(11), fitid = "F-O")).map {
            it.copy(pairPaymentId = null, include = true, draft = StatementImport.draft(it.row, listOf(Part("groc", cents("42.30")))))
        }, clock) { "pay-o" }
        val again = reviewOf(d1, row("42.30", on(11), fitid = "F-O")).single()
        assertEquals(PairProblem.ROW_RECORDED, Pairing.bankPairProblem(recorded, "pay-r", again, null, emptyList()))
        assertEquals(recorded, Pairing.pairBankWithPayment(recorded, "pay-r", again, null, clock, "tx-1", emptyList()))
        assertEquals(recorded, Pairing.pairBankWithPayment(recorded, "nope", again, null, clock, "tx-1", emptyList()))
        // the receipt side: a receipt whose payment holds no bank keys is no bank payment to complete
        assertEquals(PairProblem.PAYMENT_NOT_PAIRABLE, Pairing.receiptPairProblem(d1, "pay-r", Receipts.open(d1, shop("rc-2")), null))
        assertEquals(d1, Pairing.pairReceiptWithPayment(d1, "pay-r", Receipts.open(d1, shop("rc-2")), null, clock, "tx-2"))
    }

    // --- R35, R119: a pair's source when the store has none kept ---

    /** A -42.30 row of 12 March charged to the card at 0.00 under Groceries, recorded first as pay-b. */
    private fun chargedRowFirst(d0: ProfileData = world()): ProfileData {
        val d1 = StatementImport.confirm(d0, reviewOf(d0, row("42.30", on(12))).map {
            it.copy(include = true, draft = StatementImport.draft(it.row, listOf(Part("groc", cents("42.30"))), PaymentSource.Account("card")))
        }, clock) { "pay-b" }
        assertEquals(cents("42.30"), d1.accounts.single().balance)
        return d1
    }

    // SPEC:R35 SPEC:R119 SPEC:R113
    @Test fun `R119 a row charged to a card, then a receipt from a store with no kept source left as it opened, keeps the card`() {
        val d1 = chargedRowFirst()
        val review = Receipts.open(d1, shop())
        assertEquals(PaymentSource.Savings, review.source, "R113 opens with Savings")
        val d2 = Pairing.pairReceiptWithPayment(d1, "pay-b", review, null, clock, "tx-1")
        assertEquals(PaymentSource.Account("card"), d2.payments.single().source)
        assertEquals(cents("42.30"), d2.accounts.single().balance)
        assertEquals(cents("1,000.00"), d2.savingsAmount)
        assertEquals("account:card", d2.receiptSettings!!.storeSources[store], "the store keeps the source the pair took")
        // a kept source that no longer exists counts as none
        val goneGoal = d1.copy(receiptSettings = d1.receiptSettings!!.copy(storeSources = mapOf(store to "goal:nope")))
        assertEquals(PaymentSource.Account("card"), Pairing.receiptPairDraft(goneGoal, "pay-b", Receipts.open(goneGoal, shop()), null)?.source)
    }

    // SPEC:R35 SPEC:R119
    @Test fun `R119 the same pair with Savings picked in the review moves it to Savings`() {
        val d1 = chargedRowFirst()
        val d2 = Pairing.pairReceiptWithPayment(d1, "pay-b", Receipts.setSource(Receipts.open(d1, shop()), PaymentSource.Savings), null, clock, "tx-1")
        assertEquals(PaymentSource.Savings, d2.payments.single().source)
        assertEquals(0L, d2.accounts.single().balance)
        assertEquals(cents("957.70"), d2.savingsAmount)
    }

    // SPEC:R35 SPEC:R119 SPEC:R54
    @Test fun `R119 the same pair for a store whose kept source is a Goal takes the Goal`() {
        val d0 = world().let { it.copy(receiptSettings = it.receiptSettings!!.copy(storeSources = mapOf(store to "goal:trip"))) }
        val d1 = chargedRowFirst(d0)
        val d2 = Pairing.pairReceiptWithPayment(d1, "pay-b", Receipts.open(d1, shop()), null, clock, "tx-1")
        assertEquals(PaymentSource.Goal("trip"), d2.payments.single().source)
        assertEquals(0L, d2.accounts.single().balance)
        assertEquals(0L, d2.goals.single().saved)
        assertEquals(cents("999.80"), d2.savingsAmount, "the Goal held 42.10; Savings pays 0.20 (R54)")
    }

    // --- R128: unpairing ---

    /** A 42.10 receipt (Groceries 30.00, Household 12.10) from Savings paired by hand with a -42.30 row, its 0.20 under Fun. */
    private fun pairedWithDifference(): Pair<ProfileData, BankRow> {
        val d1 = recordReceipt(world(), shop(home = "12.10"))
        val bank = row("42.30", on(12))
        val d2 = Pairing.pairBankWithPayment(d1, "pay-r", reviewOf(d1, bank).single(), "fun", clock, "tx-1", emptyList())
        assertEquals(cents("957.70"), d2.savingsAmount)
        return d2 to bank
    }

    // SPEC:R128 SPEC:R54 SPEC:R26
    @Test fun `R128 a 42_10 receipt paired with a -42_30 row, unpaired, is 42_10 with the receipt's parts and no keys, Savings at 957_90`() {
        val (d, bank) = pairedWithDifference()
        val paired = d.payments.single()
        assertNull(Pairing.unpairProblem(d, "pay-r"))
        val un = Pairing.unpair(d, "pay-r", clock, "tx-2", emptyList())
        val p = un.payments.single()
        assertEquals(cents("42.10"), p.total)
        assertEquals(listOf(Part("groc", cents("30.00")), Part("home", cents("12.10"))), p.parts)
        assertNull(p.bankKeys)
        assertEquals(paired.receiptId, p.receiptId)
        assertEquals(paired.receiptLines, p.receiptLines)
        assertEquals(paired.date, p.date)
        assertEquals(paired.source, p.source)
        assertEquals(cents("957.90"), un.savingsAmount)
        assertEquals(0L, Spent.of(un, "fun", "2026-03"))
        assertEquals(cents("12.10"), Spent.of(un, "home", "2026-03"))
        assertTrue(StatementImport.keyStrings(paired.bankKeys!!).none { it in un.importState.importedKeys })
        assertEquals(listOf("rc-1"), un.receiptSettings?.recordedReceiptIds)
        assertEquals(d.receiptSettings?.storeLinks, un.receiptSettings?.storeLinks, "the link only ranks (R36)")

        // the row arrives as new: suggested as the pair again, and turned down and ticked it is recorded on its own
        val again = reviewOf(un, bank).single()
        assertEquals(RowStatus.PAIR, again.status)
        assertEquals("pay-r", again.pairPaymentId)
        val declined = again.copy(pairPaymentId = null, include = true, draft = StatementImport.draft(again.row, listOf(Part("groc", cents("42.30")))))
        val after = StatementImport.confirm(un, listOf(declined), clock) { "imp-1" }
        assertEquals(2, after.payments.size)
        assertEquals(cents("915.60"), after.savingsAmount)
        // or paired again by hand
        assertNull(Pairing.bankPairProblem(un, "pay-r", again, "fun", emptyList()))
    }

    // SPEC:R128
    @Test fun `R128 unpairing a payment that is no pair, or an unknown id, changes nothing`() {
        val d1 = recordReceipt(world(), shop())
        assertEquals(UnpairProblem.NOT_A_PAIR, Pairing.unpairProblem(d1, "pay-r"))
        assertEquals(d1, Pairing.unpair(d1, "pay-r", clock, "tx-1", emptyList()))
        assertEquals(UnpairProblem.NOT_A_PAIR, Pairing.unpairProblem(d1, "nope"))
        assertEquals(d1, Pairing.unpair(d1, "nope", clock, "tx-1", emptyList()))
        val bankOnly = StatementImport.confirm(d1, reviewOf(d1, row("10.00", on(12))).map { it.copy(pairPaymentId = null, include = true, draft = StatementImport.draft(it.row, listOf(Part("fun", cents("10.00"))))) }, clock) { "pay-b" }
        assertEquals(UnpairProblem.NOT_A_PAIR, Pairing.unpairProblem(bankOnly, "pay-b"))
        assertEquals(bankOnly, Pairing.unpair(bankOnly, "pay-b", clock, "tx-2", emptyList()))
        // a line's category gone: the receipt's payment alone could not be saved, and nothing changes
        val noHome = pairedWithDifference().first.let { it.copy(categories = it.categories.filter { c -> c.id != "home" }) }
        assertEquals(UnpairProblem.CANNOT_SAVE, Pairing.unpairProblem(noHome, "pay-r"))
        assertEquals(noHome, Pairing.unpair(noHome, "pay-r", clock, "tx-3", emptyList()))
    }

    // SPEC:R128 SPEC:R54
    @Test fun `R128 a pair from a Goal holding 0_00, unpaired, returns the 0_20 Savings paid and leaves the Goal at 0_00`() {
        val d1 = recordReceipt(world(), shop(home = "12.10"), PaymentSource.Goal("trip"))
        val d2 = Pairing.pairBankWithPayment(d1, "pay-r", reviewOf(d1, row("42.30", on(11))).single(), "fun", clock, "tx-1", emptyList())
        assertEquals(cents("999.80"), d2.savingsAmount)
        val un = Pairing.unpair(d2, "pay-r", clock, "tx-2", emptyList())
        assertEquals(cents("1,000.00"), un.savingsAmount)
        assertEquals(0L, un.goals.single().saved)
        assertEquals(0L, un.payments.single().fromSavings)
    }
}
