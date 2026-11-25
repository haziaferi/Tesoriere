package com.hazia.tesoriere.payments

import com.hazia.tesoriere.Fixture
import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.goal
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.TestClock
import com.hazia.tesoriere.cents
import com.hazia.tesoriere.engine.Accounts
import com.hazia.tesoriere.engine.GoalEditing
import com.hazia.tesoriere.engine.Reports
import com.hazia.tesoriere.engine.Writes
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.StoredLabels
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentsTest {
    private val day = LocalDate(2026, 3, 14)
    private val clock = TestClock(day)

    private fun world(savings: String? = "1,000.00"): ProfileData = profile(
        savings = savings,
        goals = listOf(goal("trip", "Trip", saved = "50.00")),
        categories = listOf(category("groc", "Groceries"), category("home", "Household"), category("fun", "Fun")),
    ).copy(accounts = listOf(Account("card", Fixture.PROFILE, "Card", type = AccountType.CREDIT, balance = 0)))

    private fun draft(total: String, source: PaymentSource = PaymentSource.Savings, vararg parts: Pair<String?, String>, date: LocalDate = day) =
        PaymentDraft(date, cents(total), source, payee = "Corner shop", parts = parts.map { (c, a) -> Part(c, cents(a)) })

    // SPEC:R23
    @Test fun `R23 a 42_30 payment with parts 30_00 and 12_00 leaves 0_30 and cannot be saved`() {
        val d = world()
        val short = draft("42.30", PaymentSource.Savings, "groc" to "30.00", "home" to "12.00")
        assertEquals(30L, Payments.remainder(short))
        assertFalse(Payments.canSave(d, short))
        val whole = draft("42.30", PaymentSource.Savings, "groc" to "30.00", "home" to "12.00", "fun" to "0.30")
        assertEquals(0L, Payments.remainder(whole))
        assertTrue(Payments.canSave(d, whole))
        assertFailsWith<IllegalArgumentException> { Payments.record(d, short, clock, "pay-1") }
    }

    // SPEC:R22 SPEC:R54 SPEC:R25
    @Test fun `R22 a 40_00 receipt charged to a card adds its parts to spent and 40_00 to the card and leaves Savings`() {
        val d0 = world()
        val d1 = Payments.record(d0, draft("40.00", PaymentSource.Account("card"), "groc" to "25.00", "home" to "15.00"), clock, "pay-1")
        assertEquals(cents("40.00"), d1.accounts.single().balance)
        assertEquals(d0.savings, d1.savings)
        assertEquals(cents("25.00"), Spent.of(d1, "groc", "2026-03"))
        assertEquals(cents("15.00"), Spent.of(d1, "home", "2026-03"))
    }

    // SPEC:R24
    @Test fun `R24 a hand-logged payment saves with two parts in two categories`() {
        val d1 = Payments.record(world(), draft("18.00", PaymentSource.Savings, "groc" to "10.00", "fun" to "8.00"), clock, "pay-1")
        val p = d1.payments.single()
        assertEquals(listOf(Part("groc", cents("10.00")), Part("fun", cents("8.00"))), p.parts)
        assertEquals(clock.now(), p.loggedAt)
        assertEquals("Corner shop", p.payee)
    }

    // SPEC:R25
    @Test fun `R25 re-filing a payment's parts changes both categories' spent for its month and writes no transaction`() {
        val d1 = Payments.record(world(), draft("40.00", PaymentSource.Savings, "groc" to "40.00"), clock, "pay-1")
        val d2 = Payments.edit(d1, "pay-1", draft("40.00", PaymentSource.Savings, "groc" to "25.00", "home" to "15.00"), clock, "t-edit")
        assertEquals(cents("25.00"), Spent.of(d2, "groc", "2026-03"))
        assertEquals(cents("15.00"), Spent.of(d2, "home", "2026-03"))
        assertEquals(d1.transactions.size, d2.transactions.size)
        assertEquals(d1.savings, d2.savings)
    }

    // SPEC:R54
    @Test fun `R54 a 42_30 payment from Savings lowers Savings by 42_30 and writes no ledger row`() {
        val d1 = Payments.record(world(), draft("42.30", PaymentSource.Savings, "groc" to "42.30"), clock, "pay-1")
        assertEquals(cents("957.70"), d1.savings.amount)
        assertTrue(d1.transactions.isEmpty())
    }

    // SPEC:R54 SPEC:R49
    @Test fun `R49 a payment from an unset Savings ends the unset state, counting from zero`() {
        val d1 = Payments.record(world(savings = null), draft("12.00", PaymentSource.Savings, "fun" to "12.00"), clock, "pay-1")
        assertEquals(cents("-12.00"), d1.savings.amount)
    }

    // SPEC:R54
    @Test fun `R54 a 30_00 payment from a Goal holding 50_00 leaves it 20_00 and Savings unchanged`() {
        val d0 = world()
        val d1 = Payments.record(d0, draft("30.00", PaymentSource.Goal("trip"), "fun" to "30.00"), clock, "pay-1")
        assertEquals(cents("20.00"), d1.goals.single().saved)
        assertEquals(d0.savings, d1.savings)
    }

    // SPEC:R54
    @Test fun `R54 a 60_00 payment from a Goal holding 50_00 warns, then takes 50_00 and 10_00 from Savings, and removed restores both`() {
        val d0 = world()
        val over = draft("60.00", PaymentSource.Goal("trip"), "fun" to "60.00")
        assertTrue(Payments.canSave(d0, over))
        assertEquals(PaymentNotice.GOAL_SHORT, Payments.draftNotice(d0, over))
        assertEquals(cents("10.00"), Payments.fromSavings(d0, over))
        val within = draft("50.00", PaymentSource.Goal("trip"), "fun" to "50.00")
        assertNull(Payments.draftNotice(d0, within)); assertEquals(0L, Payments.fromSavings(d0, within))
        val d1 = Payments.record(d0, over, clock, "pay-1")
        assertEquals(0L, d1.goals.single().saved)
        assertEquals(cents("990.00"), d1.savings.amount)
        assertEquals(cents("10.00"), d1.payments.single().fromSavings)
        assertEquals(cents("60.00"), Spent.of(d1, "fun", "2026-03"))
        // removed after the Goal took 30.00 more, it still returns 50.00 to the Goal and 10.00 to Savings
        val d2 = Writes.goalQuickAdd(d1, "trip", cents("30.00"), null, clock, "t1")
        val d3 = Payments.remove(d2, "pay-1", clock, "t-remove")
        assertEquals(cents("80.00"), d3.goals.single().saved)
        assertEquals(cents("970.00"), d3.savings.amount)
    }

    // SPEC:R54
    @Test fun `R54 a short 60_00 Goal payment edited to 55_00 takes 5_00 from Savings, to 40_00 none and leaves the Goal 10_00`() {
        val d1 = Payments.record(world(), draft("60.00", PaymentSource.Goal("trip"), "fun" to "60.00"), clock, "pay-1")
        val to55 = draft("55.00", PaymentSource.Goal("trip"), "fun" to "55.00")
        assertEquals(cents("5.00"), Payments.fromSavings(d1, to55, editing = "pay-1"))
        val d2 = Payments.edit(d1, "pay-1", to55, clock, "t-edit")
        assertEquals(listOf(0L, cents("995.00"), cents("5.00")), listOf(d2.goals.single().saved, d2.savings.amount, d2.payments.single().fromSavings))
        val to40 = draft("40.00", PaymentSource.Goal("trip"), "fun" to "40.00")
        assertNull(Payments.draftNotice(d2, to40, editing = "pay-1"))
        val d3 = Payments.edit(d2, "pay-1", to40, clock, "t-edit2")
        assertEquals(listOf(cents("10.00"), cents("1,000.00"), 0L), listOf(d3.goals.single().saved, d3.savings.amount, d3.payments.single().fromSavings))
        assertTrue(d3.transactions.isEmpty())
    }

    // SPEC:R54 SPEC:R58 SPEC:R95
    @Test fun `R54 a 60_00 payment that took 10_00 from Savings, its Goal deleted, re-files logging nothing and removed returns 60_00`() {
        val d1 = Payments.record(world(), draft("60.00", PaymentSource.Goal("trip"), "fun" to "60.00"), clock, "pay-1")
        val d2 = GoalEditing.delete(d1, "trip", returnSaved = true, clock) { "t-del" }
        val refiled = draft("60.00", PaymentSource.Goal("trip"), "fun" to "20.00", "groc" to "40.00")
        val d3 = Payments.edit(d2, "pay-1", refiled, clock, "t-edit")
        assertEquals(d2.transactions, d3.transactions)
        assertEquals(cents("990.00"), d3.savings.amount)
        val d4 = Payments.remove(d3, "pay-1", clock, "t-remove")
        assertEquals(cents("1,050.00"), d4.savings.amount)                     // 10.00 back from its share, 50.00 in the Goal's place
        assertEquals(cents("50.00"), d4.transactions.single { it.id == "t-remove" }.amount)
        assertEquals(d3.savings.amount, replayed(d3))
        assertEquals(d4.savings.amount, replayed(d4))
    }

    // SPEC:R54
    @Test fun `R54 editing a payment undoes its old source and applies the new one`() {
        val d1 = Payments.record(world(), draft("30.00", PaymentSource.Goal("trip"), "fun" to "30.00"), clock, "pay-1")
        // the Goal can take back its own 30.00 plus the 20.00 it still holds
        assertTrue(Payments.canSave(d1, draft("50.00", PaymentSource.Goal("trip"), "fun" to "50.00"), editing = "pay-1"))
        val d2 = Payments.edit(d1, "pay-1", draft("30.00", PaymentSource.Savings, "fun" to "30.00"), clock, "t-edit")
        assertEquals(cents("50.00"), d2.goals.single().saved)
        assertEquals(cents("970.00"), d2.savings.amount)
        assertEquals("pay-1", d2.payments.single().id)
        assertEquals(d1.payments.single().loggedAt, d2.payments.single().loggedAt)
    }

    // SPEC:R54
    @Test fun `R54 removing a payment undoes its effect`() {
        val d0 = world()
        val d1 = Payments.record(d0, draft("40.00", PaymentSource.Account("card"), "groc" to "40.00"), clock, "pay-1")
        val d2 = Payments.remove(d1, "pay-1", clock, "t-remove")
        assertEquals(d0, d2)
    }

    /** Savings 1,000.00; 100.00 into Trip; a 30.00 payment from Trip; Trip deleted with its 70.00 returned. */
    private fun paidFromDeletedGoal(): ProfileData {
        var d = profile(savings = "1,000.00", goals = listOf(goal("trip", "Trip")), categories = listOf(category("fun", "Fun"), category("groc", "Groceries")))
        d = Writes.goalQuickAdd(d, "trip", cents("100.00"), null, clock, "t1")
        d = Payments.record(d, draft("30.00", PaymentSource.Goal("trip"), "fun" to "30.00"), clock, "pay-1")
        d = GoalEditing.delete(d, "trip", returnSaved = true, clock) { "t2" }
        assertEquals(cents("970.00"), d.savings.amount)
        return d
    }

    /** Savings as the reports replay it (SPEC R95): the fixture's 1,000.00 plus every movement since. */
    private fun replayed(d: ProfileData) = cents("1,000.00") + Reports.savingsMovements(d).sumOf { it.effect }

    // SPEC:R54 SPEC:R58 SPEC:R95
    @Test fun `R54 removing a payment from a deleted Goal returns its 30_00 to Savings with a savings row of 30_00`() {
        val d = Payments.remove(paidFromDeletedGoal(), "pay-1", clock, "t-remove")
        assertEquals(cents("1,000.00"), d.savings.amount)
        assertTrue(d.payments.isEmpty())
        val row = d.transactions.last()
        assertEquals(listOf("t-remove", "savings", StoredLabels.DELETED_GOAL_PAYMENT), listOf(row.id, row.key, row.payee))
        assertEquals(cents("30.00"), row.amount); assertEquals(day, row.date)
        assertEquals(d.savings.amount, replayed(d))
    }

    // SPEC:R54 SPEC:R25 SPEC:R95
    @Test fun `R54 re-filing a deleted Goal's payment keeps its source, leaves Savings at 970_00 and logs nothing`() {
        val d0 = paidFromDeletedGoal()
        val refiled = draft("30.00", PaymentSource.Goal("trip"), "fun" to "10.00", "groc" to "20.00")
        assertTrue(Payments.canSave(d0, refiled, editing = "pay-1"))
        val d1 = Payments.edit(d0, "pay-1", refiled, clock, "t-edit")
        assertEquals(cents("970.00"), d1.savings.amount)
        assertEquals(PaymentSource.Goal("trip"), d1.payments.single().source)
        assertEquals(cents("20.00"), Spent.of(d1, "groc", "2026-03"))
        assertEquals(d0.transactions, d1.transactions)
        assertEquals(d1.savings.amount, replayed(d1))
    }

    // SPEC:R54 SPEC:R95
    @Test fun `R54 a deleted Goal's 30_00 payment lowered to 25_00 gives Savings 975_00 with a savings row of 5_00`() {
        val d = Payments.edit(paidFromDeletedGoal(), "pay-1", draft("25.00", PaymentSource.Goal("trip"), "fun" to "25.00"), clock, "t-edit")
        assertEquals(cents("975.00"), d.savings.amount)
        assertEquals(cents("5.00"), d.transactions.single { it.id == "t-edit" }.amount)
        assertEquals(d.savings.amount, replayed(d))
    }

    // SPEC:R54 SPEC:R95
    @Test fun `R54 a deleted Goal's 30_00 payment moved to Savings leaves 970_00 and the replay adds up`() {
        val d = Payments.edit(paidFromDeletedGoal(), "pay-1", draft("30.00", PaymentSource.Savings, "fun" to "30.00"), clock, "t-edit")
        assertEquals(cents("970.00"), d.savings.amount)
        assertEquals(cents("30.00"), d.transactions.single { it.id == "t-edit" }.amount)
        assertEquals(d.savings.amount, replayed(d))
    }

    // SPEC:R54
    @Test fun `R54 a deleted Goal's payment carries a warning code, and a new payment from it cannot be saved`() {
        val d = paidFromDeletedGoal()
        assertEquals(PaymentNotice.SOURCE_GOAL_DELETED, Payments.notice(d, "pay-1"))
        assertFalse(Payments.canSave(d, draft("5.00", PaymentSource.Goal("trip"), "fun" to "5.00")))
        val live = Payments.record(world(), draft("5.00", PaymentSource.Goal("trip"), "fun" to "5.00"), clock, "pay-2")
        assertNull(Payments.notice(live, "pay-2"))
    }

    /** A 40.00 payment under Groceries charged to Card, then Card deleted (SPEC R88). */
    private fun chargedToDeletedCard(): ProfileData {
        val d = Payments.record(world(), draft("40.00", PaymentSource.Account("card"), "groc" to "40.00"), clock, "pay-1")
        return Accounts.deleteCascade(d, "card", clock) { "t-close" }.also { assertTrue(it.accounts.isEmpty()) }
    }

    // SPEC:R54 SPEC:R88
    @Test fun `R54 a 40_00 payment charged to a deleted card carries a warning code, and a new payment to that card cannot be saved`() {
        val d = chargedToDeletedCard()
        assertEquals(PaymentNotice.SOURCE_ACCOUNT_DELETED, Payments.notice(d, "pay-1"))
        assertFalse(Payments.canSave(d, draft("5.00", PaymentSource.Account("card"), "fun" to "5.00")))
        val live = Payments.record(world(), draft("5.00", PaymentSource.Account("card"), "fun" to "5.00"), clock, "pay-2")
        assertNull(Payments.notice(live, "pay-2"))
    }

    // SPEC:R54 SPEC:R88 SPEC:R25
    @Test fun `R54 a 40_00 payment charged to a deleted card re-files into 25_00 and 15_00 or is removed, Savings staying 1,000_00`() {
        val d0 = chargedToDeletedCard()
        val refiled = draft("40.00", PaymentSource.Account("card"), "groc" to "25.00", "home" to "15.00")
        assertTrue(Payments.canSave(d0, refiled, editing = "pay-1"))
        val d1 = Payments.edit(d0, "pay-1", refiled, clock, "t-edit")
        assertEquals(PaymentSource.Account("card"), d1.payments.single().source)
        assertEquals(cents("15.00"), Spent.of(d1, "home", "2026-03"))
        assertEquals(cents("1,000.00"), d1.savings.amount)
        assertTrue(d1.accounts.isEmpty())
        val d2 = Payments.remove(d1, "pay-1", clock, "t-remove")
        assertTrue(d2.payments.isEmpty())
        assertEquals(cents("1,000.00"), d2.savings.amount)
        // re-filing logs nothing; the removal logs +40.00 on the card, so its rows and payments add up to zero
        assertEquals(d0.transactions, d1.transactions)
        val row = d2.transactions.last()
        assertEquals(d0.transactions, d2.transactions.dropLast(1))
        assertEquals("acct:card", row.key); assertEquals(cents("40.00"), row.amount)
        assertEquals(0L, d2.transactions.filter { it.key == "acct:card" }.sumOf { it.amount })
    }

    // SPEC:R21
    @Test fun `R21 a payment needs a total other than 0_00, parts in categories that exist, and a source that exists`() {
        val d = world()
        assertFalse(Payments.canSave(d, draft("0.00", PaymentSource.Savings)))
        assertFalse(Payments.canSave(d, draft("10.00", PaymentSource.Savings, "nowhere" to "10.00")))
        assertFalse(Payments.canSave(d, draft("10.00", PaymentSource.Savings, null to "10.00")))
        assertFalse(Payments.canSave(d, draft("10.00", PaymentSource.Goal("gone"), "fun" to "10.00")))
        assertFalse(Payments.canSave(d, draft("10.00", PaymentSource.Account("gone"), "fun" to "10.00")))
    }

    // SPEC:R21 SPEC:R54 SPEC:R25
    @Test fun `R21 after a 40_00 payment under Fun, a 15_00 refund from Savings leaves Savings 975_00 and Fun's spent 25_00, with no ledger row`() {
        val d1 = Payments.record(world(), draft("40.00", PaymentSource.Savings, "fun" to "40.00"), clock, "pay-1")
        val d2 = Payments.record(d1, draft("-15.00", PaymentSource.Savings, "fun" to "-15.00"), clock, "pay-2")
        assertEquals(cents("975.00"), d2.savings.amount)
        assertEquals(cents("25.00"), Spent.of(d2, "fun", "2026-03"))
        assertTrue(d2.transactions.isEmpty())                                   // a refund is no income row
        assertEquals(cents("-15.00"), d2.payments.last().total)
        // removed, it takes the money back out
        assertEquals(d1, Payments.remove(d2, "pay-2", clock, "t-remove"))
    }

    // SPEC:R54 SPEC:R21
    @Test fun `R54 a 15_00 refund to a card at 40_00 leaves it 25_00, and one into a Goal holding 50_00 leaves it 65_00`() {
        val d1 = Payments.record(world(), draft("40.00", PaymentSource.Account("card"), "groc" to "40.00"), clock, "pay-1")
        val d2 = Payments.record(d1, draft("-15.00", PaymentSource.Account("card"), "groc" to "-15.00"), clock, "pay-2")
        assertEquals(cents("25.00"), d2.accounts.single().balance)
        assertEquals(cents("1,000.00"), d2.savings.amount)
        val d3 = Payments.record(world(), draft("-15.00", PaymentSource.Goal("trip"), "fun" to "-15.00"), clock, "pay-3")
        assertEquals(cents("65.00"), d3.goals.single().saved)
        assertEquals(cents("1,000.00"), d3.savings.amount)
    }

    // SPEC:R23 SPEC:R21
    @Test fun `R23 a -15_00 refund with parts of -20_00 and 5_00 cannot be saved, with one part of -15_00 it can`() {
        val d = world()
        assertFalse(Payments.canSave(d, draft("-15.00", PaymentSource.Savings, "fun" to "-20.00", "groc" to "5.00")))
        assertTrue(Payments.canSave(d, draft("-15.00", PaymentSource.Savings, "fun" to "-15.00")))
        // a purchase takes no negative part either, and a total of 0.00 with parts that net to it is no payment
        assertFalse(Payments.canSave(d, draft("15.00", PaymentSource.Savings, "fun" to "20.00", "groc" to "-5.00")))
        assertFalse(Payments.canSave(d, draft("0.00", PaymentSource.Savings, "fun" to "5.00", "groc" to "-5.00")))
    }

    // SPEC:R21 SPEC:R20
    @Test fun `R21 a payment round-trips through JSON with its source as goal_id and its logged instant in epoch milliseconds`() {
        val p = Payment(
            id = "pay-1", profileId = Fixture.PROFILE, date = day, loggedAt = clock.now(), total = cents("10.99"),
            source = PaymentSource.Goal("g-1"), payee = "Supermarket A",
            parts = listOf(Part("groc", cents("5.01")), Part("home", cents("5.98"))),
            receiptId = "3f6c2a9e-0000-4000-8000-000000000001",
            receiptLines = listOf(
                ReceiptLine("cookies Q", "600 g", 3, cents("3.18"), "chat-food", "Food", "groc"),
                ReceiptLine("bananas", "865 g", 1, cents("1.83"), "chat-food", "Food", "groc"),
                ReceiptLine("batteries", null, 2, cents("5.98"), "chat-home", "Household", "home"),
            ),
            bankKeys = BankKeys(null, "syn:2026-03-14|-1099|SUPERMARKET A"),
        )
        val json = Json.encodeToString(Payment.serializer(), p)
        assertTrue("\"source\":\"goal:g-1\"" in json, json)
        assertTrue("\"loggedAt\":${clock.now().toEpochMilliseconds()}" in json, json)
        assertEquals(p, Json.decodeFromString(Payment.serializer(), json))
    }

    // SPEC:R21
    @Test fun `R21 a source is stored as savings, goal or account with its id`() {
        for (s in listOf(PaymentSource.Savings, PaymentSource.Goal("g-1"), PaymentSource.Account("a-1"))) {
            assertEquals(s, PaymentSource.parse(s.stored))
        }
        assertEquals("goal:g-1", PaymentSource.Goal("g-1").stored)
        assertEquals("account:a-1", PaymentSource.Account("a-1").stored)
        assertFailsWith<IllegalArgumentException> { PaymentSource.parse("wallet:1") }
    }
}
