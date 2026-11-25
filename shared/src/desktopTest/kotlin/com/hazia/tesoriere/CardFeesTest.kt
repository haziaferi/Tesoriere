package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.engine.AccountEditing
import com.hazia.tesoriere.engine.Accounts
import com.hazia.tesoriere.engine.Reports
import com.hazia.tesoriere.engine.Slice
import com.hazia.tesoriere.engine.SliceKind
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.payments.Spent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tesoriere's own: a card's interest and fees, read through its balance (SPEC R110, owner answer
 * E of 2026-11-25). Every figure is made up.
 */
class CardFeesTest {
    private val day = LocalDate(2026, 9, 30)
    private val clock = TestClock(day)
    private val ids = Ids()

    private fun card(fees: String? = "fees", balance: String = "300.00", notLogged: Boolean = false) = Account(
        "cc", Fixture.PROFILE, "Orbita card", type = AccountType.CREDIT, balance = cents(balance), feesCategoryId = fees,
        chargesNotLogged = notLogged, payoffCategoryId = if (notLogged) "shop" else null,
    )

    private fun world(card: Account = card()): ProfileData = profile(
        categories = listOf(category("fees", "Fees"), category("bank", "Bank charges"), category("shop", "Shopping")),
    ).copy(accounts = listOf(card))

    private fun ProfileData.increased(amount: String, picked: String? = null) =
        Accounts.quickAdd(this, "cc", cents(amount), Accounts.Direction.INCREASE, null, Accounts.PayoffFrom.None, clock, categoryId = picked) { ids.next() }

    private fun ProfileData.edited(balance: String, picked: String? = null, rise: AccountEditing.BalanceRise? = AccountEditing.BalanceRise.FEES) =
        AccountEditing.save(this, accounts.single().copy(balance = cents(balance)), clock, rise = rise, riseCategoryId = picked) { ids.next() }

    private fun fees(d: ProfileData) = Spent.of(d, "fees", "2026-09")

    // SPEC:R110 SPEC:R89 SPEC:R99 SPEC:R101
    @Test fun `R110 a card at 300_00 increased by 12_50 reads 312_50, adds 12_50 to Fees and Spending and leaves Savings, and picked under Bank charges counts there`() {
        val d = world().increased("12.50")
        assertEquals(cents("312.50"), d.accounts.single().balance)
        assertEquals(cents("12.50"), fees(d))
        assertEquals(listOf(Slice(SliceKind.CATEGORY, "fees", cents("12.50"))), Reports.spendingComposition(d, day).slices)
        assertEquals(cents("12.50"), Reports.incomeVsExpenses(d, day).last().expenses)
        assertEquals(cents("1,000.00"), d.savings.amount)
        val row = d.transactions.single()
        assertEquals(listOf("acct:cc", "fees"), listOf(row.key, row.categoryId)); assertEquals(day, row.date)
        val picked = world().increased("12.50", picked = "bank")
        assertEquals(cents("12.50"), Spent.of(picked, "bank", "2026-09")); assertEquals(0L, fees(picked))
    }

    // SPEC:R110 SPEC:R86
    @Test fun `R110 edited from 312_50 to 320_00 adds 7_50 to Fees, down to 310_00 nothing, and a card created at 640_00 nothing`() {
        var d = world(card(balance = "312.50")).edited("320.00")
        assertEquals(cents("7.50"), fees(d))
        assertEquals("fees", d.transactions.single().categoryId)
        d = d.edited("310.00")
        assertEquals(cents("7.50"), fees(d))
        assertNull(d.transactions.last().categoryId)
        val opened = AccountEditing.save(profile(categories = listOf(category("fees", "Fees"))), card(balance = "640.00"), clock) { ids.next() }
        assertEquals(cents("640.00"), opened.accounts.single().balance)
        assertEquals(0L, fees(opened))
    }

    // SPEC:R110 SPEC:R109
    @Test fun `R110 a card with no fees category refuses both rises unless one is picked, and one whose charges are not logged takes them under none`() {
        val bare = world(card(fees = null))
        assertFailsWith<IllegalArgumentException> { bare.increased("12.50") }
        val fees = AccountEditing.BalanceRise.FEES
        assertFalse(AccountEditing.canSave(bare, bare.accounts.single().copy(balance = cents("320.00")), rise = fees))
        assertEquals(bare, bare.edited("320.00"))
        assertEquals(cents("12.50"), Spent.of(bare.increased("12.50", picked = "bank"), "bank", "2026-09"))
        assertTrue(AccountEditing.canSave(bare, bare.accounts.single().copy(balance = cents("320.00")), rise = fees, riseCategoryId = "bank"))
        assertEquals(cents("20.00"), Spent.of(bare.edited("320.00", picked = "bank"), "bank", "2026-09"))
        val notLogged = world(card(fees = null, notLogged = true)).increased("12.50").edited("320.00")
        assertEquals(cents("320.00"), notLogged.accounts.single().balance)
        assertTrue(notLogged.transactions.all { it.categoryId == null })
        assertTrue(Reports.spendingComposition(notLogged, day).slices.isEmpty())
        // a fees category that does not exist is not saved
        assertFalse(AccountEditing.canSave(bare, bare.accounts.single().copy(feesCategoryId = "nowhere")))
    }

    // SPEC:R110 SPEC:R86
    @Test fun `R110 edited from 312_50 to 320_00 as a correction adds nothing, also with no fees category, and with no choice it is not saved`() {
        val correction = AccountEditing.BalanceRise.CORRECTION
        val d = world(card(balance = "312.50")).edited("320.00", rise = correction)
        assertEquals(cents("320.00"), d.accounts.single().balance)
        assertEquals(0L, fees(d))
        assertNull(d.transactions.single().categoryId)
        val bare = world(card(fees = null, balance = "312.50"))
        assertEquals(cents("320.00"), bare.edited("320.00", rise = correction).accounts.single().balance)
        // the choice is the caller's: no choice is a code, and the edit is not saved
        val up = bare.accounts.single().copy(balance = cents("320.00"))
        assertEquals(AccountEditing.AccountProblem.RISE_CHOICE_MISSING, AccountEditing.problem(bare, up))
        assertEquals(AccountEditing.AccountProblem.RISE_CATEGORY_MISSING, AccountEditing.problem(bare, up, AccountEditing.BalanceRise.FEES))
        assertNull(AccountEditing.problem(bare, up, correction))
        assertEquals(bare, bare.edited("320.00", rise = null))
        // a fall, a Loan's rise and a new card's opening balance need no choice
        assertNull(AccountEditing.problem(bare, bare.accounts.single().copy(balance = cents("300.00"))))
        assertNull(AccountEditing.problem(bare, card(balance = "640.00").copy(id = "new")))
    }

    // SPEC:R110 SPEC:R109 SPEC:R86
    @Test fun `R110 a card whose charges are not logged raised from 300_00 to 320_00 asks no choice and files the rise under no category`() {
        val d = world(card(fees = null, notLogged = true))
        val up = d.accounts.single().copy(balance = cents("320.00"))
        assertNull(AccountEditing.problem(d, up))
        val saved = d.edited("320.00", rise = null)
        assertEquals(cents("320.00"), saved.accounts.single().balance)
        assertNull(saved.transactions.single().categoryId)
        assertTrue(Reports.spendingComposition(saved, day).slices.isEmpty())
        // every other card still asks
        val logged = world(card())
        assertEquals(AccountEditing.AccountProblem.RISE_CHOICE_MISSING, AccountEditing.problem(logged, logged.accounts.single().copy(balance = cents("320.00"))))
    }

    // SPEC:R110 SPEC:R86
    @Test fun `R110 a card with Fees as its fees category edited up as interest or fees under a picked category that does not exist is refused with its own code`() {
        val d = world(card(balance = "312.50"))
        val up = d.accounts.single().copy(balance = cents("320.00"))
        assertEquals(AccountEditing.AccountProblem.RISE_CATEGORY_UNKNOWN, AccountEditing.problem(d, up, AccountEditing.BalanceRise.FEES, "nowhere"))
        assertEquals(d, d.edited("320.00", picked = "nowhere"))
        assertNull(AccountEditing.problem(d, up, AccountEditing.BalanceRise.FEES))
    }

    // SPEC:R110 SPEC:R99
    @Test fun `R110 paying 320_00 off the card from Savings after its fees adds nothing to Fees or Spending`() {
        val d = world().increased("12.50").edited("320.00")
        val paid = Accounts.quickAdd(d, "cc", cents("320.00"), Accounts.Direction.PAYMENT, null, Accounts.PayoffFrom.Savings, clock) { ids.next() }
        assertEquals(0L, paid.accounts.single().balance)
        assertEquals(cents("20.00"), fees(paid))
        assertEquals(cents("20.00"), Reports.spendingComposition(paid, day).net)
        assertEquals(cents("680.00"), paid.savings.amount)
    }

    // SPEC:R94 SPEC:R110
    @Test fun `R94 a card with a fees category changed to a Loan has none`() {
        assertNull(Accounts.retype(card(), AccountType.LOAN).feesCategoryId)
    }
}
