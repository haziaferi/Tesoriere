package com.hazia.tesoriere.receipts

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
import com.hazia.tesoriere.model.AppState
import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.PaymentNotice
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.ReceiptLine
import com.hazia.tesoriere.payments.Spent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every store, product, category and id here is made up. */
class ReceiptReviewTest {
    private val day = LocalDate(2026, 11, 24)
    private val clock = TestClock(day)
    private val store = "Mercato Lume"

    private fun world(): ProfileData = profile(
        goals = listOf(goal("trip", "Trip", saved = "10.00")),
        categories = listOf(
            category("groc", "Groceries"), category("home", "Household"), category("fruit", "Fruit"),
            category("out", "Eating out"), category("pul", "pulizia"), category("fun", "Fun"), category("gift", "Gifts"),
        ),
    ).copy(
        accounts = listOf(
            Account("card", Fixture.PROFILE, "Card", type = AccountType.CREDIT),
            Account("loose", Fixture.PROFILE, "Loose card", type = AccountType.CREDIT, chargesNotLogged = true, payoffCategoryId = "groc"),
        ),
        receiptSettings = Fixture.receiptSettings(),
    )

    /** Five lines in two categories: Groceries 1.89 + 2.40 + 3.15 = 7.44, Household 6.90 + 2.16 = 9.06; 16.50 in all. */
    private fun fiveLines(id: String = "rc-1", date: LocalDate = day) = receipt(
        id, store, date,
        line("milk", "1.89", "ch-groc", "Groceries", pack = "1 l"),
        line("bread", "2.40", "ch-groc", "Groceries"),
        line("apples", "3.15", "ch-groc", "Groceries", pack = "1,2 kg"),
        line("soap", "6.90", "ch-home", "Household", quantity = 2),
        line("sponges", "2.16", "ch-home", "Household", pack = "3 pcs"),
    )

    // SPEC:R31 SPEC:R115
    @Test fun `R31 five lines in two categories record one payment of the total with two parts`() {
        val d0 = world()
        val r = Receipts.open(d0, fiveLines())
        assertEquals(listOf<String?>("groc", "groc", "groc", "home", "home"), r.lineCategories)
        assertEquals(listOf(Part("groc", cents("7.44")), Part("home", cents("9.06"))), Receipts.parts(r))
        assertTrue(Receipts.canConfirm(d0, r))
        val d1 = Receipts.confirm(d0, r, clock, "pay-1")
        val p = d1.payments.single()
        assertEquals(cents("16.50"), p.total)
        assertEquals(PaymentSource.Savings, p.source)
        assertEquals(store, p.payee)
        assertEquals(day, p.date)
        assertEquals(listOf(Part("groc", cents("7.44")), Part("home", cents("9.06"))), p.parts)
        assertEquals(cents("983.50"), d1.savings.amount)
        assertEquals(listOf("rc-1"), d1.receiptSettings!!.recordedReceiptIds)
        assertEquals(cents("7.44"), Spent.of(d1, "groc", "2026-11"))
    }

    // SPEC:R31 SPEC:R114
    @Test fun `R31 moving one line to a third category gives three parts and changes no link`() {
        val d0 = world()
        val r = Receipts.moveLine(Receipts.open(d0, fiveLines()), 2, "fruit")
        assertEquals(
            listOf(Part("groc", cents("4.29")), Part("fruit", cents("3.15")), Part("home", cents("9.06"))),
            Receipts.parts(r),
        )
        // a moved line is a change that warns before close (owner answer A of 2026-11-25)
        assertTrue(Receipts.hasChanges(r))
        val d1 = Receipts.confirm(d0, r, clock, "pay-1")
        assertEquals(mapOf("ch-groc" to "groc", "ch-home" to "home"), d1.receiptSettings!!.categoryLinks)
        assertEquals("fruit", d1.payments.single().receiptLines[2].categoryId)
        // the next receipt files apples under Groceries again
        assertEquals("groc", Receipts.open(d1, fiveLines("rc-2")).lineCategories[2])
    }

    // SPEC:R31 SPEC:R33
    @Test fun `R31 closing the review leaves no payment, no link and no category`() {
        val d0 = world()
        val file = receipt(
            "rc-1", store, day,
            line("seeds", "4.00", "ch-garden", "Garden"), line("milk", "1.89", "ch-groc", "Groceries"), line("crisps", "3.20", "ch-snack", "Snack"),
        )
        var r = Receipts.open(d0, file)
        r = Receipts.moveLine(r, 1, "fun")
        r = Receipts.link(r, "ch-snack", "out")
        r = Receipts.addAsNew(r, "ch-garden", "c-new", Fixture.PROFILE)
        r = Receipts.setSource(r, PaymentSource.Account("card"))
        assertTrue(Receipts.canConfirm(d0, r))
        assertTrue(Receipts.hasChanges(r))
        // what Confirm would have recorded, to show the review held something
        val confirmed = Receipts.confirm(d0, r, clock, "pay-1")
        assertEquals(1, confirmed.payments.size)
        assertEquals(setOf("ch-groc", "ch-snack", "ch-garden"), confirmed.receiptSettings!!.categoryLinks.keys)
        // closed instead: the Profile the review was opened on is as it was
        assertEquals(world(), d0)
        assertTrue(d0.payments.isEmpty())
        assertEquals(emptyMap(), d0.receiptSettings!!.categoryLinks)
        assertEquals(emptyList(), d0.receiptSettings!!.recordedReceiptIds)
        assertEquals(emptyMap(), d0.receiptSettings!!.storeSources)
        assertTrue(d0.categories.none { it.id == "c-new" || it.name == "Garden" })
        assertEquals(0L, d0.accounts.single { it.id == "card" }.balance)
        assertEquals(cents("1,000.00"), d0.savings.amount)
    }

    // SPEC:R33 SPEC:R114
    @Test fun `R33 moving one line or changing the source holds changes`() {
        val d0 = world()
        val r = Receipts.open(d0, fiveLines())
        assertFalse(Receipts.hasChanges(r))
        assertTrue(Receipts.hasChanges(Receipts.moveLine(r, 0, "fruit")))
        assertTrue(Receipts.hasChanges(Receipts.setSource(r, PaymentSource.Account("card"))))
        // the source it opened with, picked again, is no change
        assertFalse(Receipts.hasChanges(Receipts.setSource(r, PaymentSource.Savings)))
    }

    // SPEC:R114 SPEC:R33
    @Test fun `R114 a line moved to Fun and back to Groceries leaves no change and follows a later link`() {
        val d0 = world()
        val r0 = Receipts.open(d0, fiveLines())
        val back = Receipts.moveLine(Receipts.moveLine(r0, 0, "fun"), 0, "groc")
        assertEquals(r0.lineCategories, back.lineCategories)
        assertFalse(Receipts.hasChanges(back))
        val linked = Receipts.link(back, "ch-groc", "fruit")
        assertEquals(listOf<String?>("fruit", "fruit", "fruit", "home", "home"), linked.lineCategories)
        // after an answer, the line's home is the answer: moved away and back to it, it follows the next answer
        val again = Receipts.moveLine(Receipts.moveLine(linked, 1, "fun"), 1, "fruit")
        assertEquals(listOf<String?>("out", "out", "out", "home", "home"), Receipts.link(again, "ch-groc", "out").lineCategories)
    }

    // SPEC:R32 SPEC:R114 SPEC:R115
    @Test fun `R32 an unlinked Garden whose only line is moved by hand to Household can be confirmed and the next receipt asks again`() {
        val d0 = world()
        val r = Receipts.moveLine(Receipts.open(d0, receipt("rc-1", store, day, line("seeds", "4.00", "ch-garden", "Garden"))), 0, "home")
        assertEquals(listOf<ReviewNote>(ReviewNote.Unlinked("ch-garden")), Receipts.notes(d0, r))
        assertNull(Receipts.problem(d0, r))
        val d1 = Receipts.confirm(d0, r, clock, "pay-1")
        assertEquals(listOf(Part("home", cents("4.00"))), d1.payments.single().parts)
        assertTrue("ch-garden" !in d1.receiptSettings!!.categoryLinks)
        val next = Receipts.open(d1, receipt("rc-2", store, day, line("seeds", "4.00", "ch-garden", "Garden")))
        assertEquals(listOf<String?>(null), next.lineCategories)
        assertEquals(ReceiptProblem.UNLINKED, Receipts.problem(d1, next))
    }

    // SPEC:R114 SPEC:R32
    @Test fun `R114 Snack opened under Eating out and linked in the review to Groceries files its lines under Groceries`() {
        val d0 = world()
        val first = Receipts.link(Receipts.open(d0, receipt("rc-1", store, day, line("crisps", "3.20", "ch-snack", "Snack"))), "ch-snack", "out")
        val d1 = Receipts.confirm(d0, first, clock, "pay-1")
        val r0 = Receipts.open(d1, receipt("rc-2", store, day, line("crisps", "3.20", "ch-snack", "Snack"), line("nuts", "2.10", "ch-snack", "Snack")))
        assertEquals(listOf<String?>("out", "out"), r0.lineCategories)
        val moved = Receipts.moveLine(r0, 1, "fun")
        val relinked = Receipts.link(moved, "ch-snack", "groc")
        assertEquals(listOf<String?>("groc", "fun"), relinked.lineCategories)
        assertEquals(listOf(Part("groc", cents("3.20")), Part("fun", cents("2.10"))), Receipts.parts(relinked))
        assertEquals("groc", Receipts.confirm(d1, relinked, clock, "pay-2").receiptSettings!!.categoryLinks["ch-snack"])
    }

    // SPEC:R114 SPEC:R32
    @Test fun `R114 Pulizia opened under pulizia by its name and added as new files its line under the new category`() {
        val d0 = world()
        val r0 = Receipts.open(d0, receipt("rc-1", store, day, line("bleach", "2.50", "ch-pul", "Pulizia")))
        assertEquals(listOf<String?>("pul"), r0.lineCategories)
        val added = Receipts.addAsNew(r0, "ch-pul", "c-new", Fixture.PROFILE)
        assertEquals(listOf<String?>("c-new"), added.lineCategories)
        assertEquals(listOf(Part("c-new", cents("2.50"))), Receipts.parts(added))
    }

    // SPEC:R32 SPEC:R113
    @Test fun `R32 Pulizia links to Tesoriere's pulizia ignoring capitals`() {
        val d0 = world()
        val r = Receipts.open(d0, receipt("rc-1", store, day, line("bleach", "2.50", "ch-pul", "Pulizia")))
        assertEquals(listOf<String?>("pul"), r.lineCategories)
        assertEquals(emptyList(), Receipts.notes(d0, r))
        assertFalse(Receipts.hasChanges(r))
        val d1 = Receipts.confirm(d0, r, clock, "pay-1")
        assertEquals(mapOf("ch-pul" to "pul"), d1.receiptSettings!!.categoryLinks)
    }

    // SPEC:R32 SPEC:R114
    @Test fun `R32 the same id renamed Pulizie stays linked and gives NamesDiffer`() {
        val d1 = Receipts.confirm(world(), Receipts.open(world(), receipt("rc-1", store, day, line("bleach", "2.50", "ch-pul", "Pulizia"))), clock, "pay-1")
        val r = Receipts.open(d1, receipt("rc-2", store, day, line("bleach", "2.50", "ch-pul", "Pulizie")))
        assertEquals(listOf<String?>("pul"), r.lineCategories)
        assertEquals(listOf<ReviewNote>(ReviewNote.NamesDiffer("ch-pul")), Receipts.notes(d1, r))
        assertTrue(Receipts.canConfirm(d1, r))
    }

    // SPEC:R32 SPEC:R114 SPEC:R115
    @Test fun `R32 a new category with no equal name keeps Confirm off until added or linked`() {
        val d0 = world()
        val r = Receipts.open(d0, receipt("rc-1", store, day, line("seeds", "4.00", "ch-garden", "Garden"), line("milk", "1.89", "ch-groc", "Groceries")))
        assertEquals(listOf(null, "groc"), r.lineCategories)
        assertEquals(listOf<ReviewNote>(ReviewNote.Unlinked("ch-garden")), Receipts.notes(d0, r))
        assertFalse(Receipts.canConfirm(d0, r))
        assertEquals(ReceiptProblem.UNLINKED, Receipts.problem(d0, r))
        assertFailsWith<IllegalArgumentException> { Receipts.confirm(d0, r, clock, "pay-1") }

        val added = Receipts.addAsNew(r, "ch-garden", "c-new", Fixture.PROFILE)
        assertEquals(listOf(Category("c-new", Fixture.PROFILE, "Garden", planned = 0)), added.addedCategories)
        assertEquals(listOf("c-new", "groc"), added.lineCategories)
        assertEquals(emptyList(), Receipts.notes(d0, added))
        assertTrue(Receipts.canConfirm(d0, added))
        val d1 = Receipts.confirm(d0, added, clock, "pay-1")
        assertEquals("Garden", d1.categories.single { it.id == "c-new" }.name)
        assertEquals("c-new", d1.receiptSettings!!.categoryLinks["ch-garden"])
        assertEquals(cents("4.00"), Spent.of(d1, "c-new", "2026-11"))

        val linked = Receipts.link(r, "ch-garden", "home")
        assertTrue(Receipts.canConfirm(d0, linked))
    }

    // SPEC:R32 SPEC:R114
    @Test fun `R32 linking an existing category gives RenameInChatelaine`() {
        val d0 = world()
        val r = Receipts.link(Receipts.open(d0, receipt("rc-1", store, day, line("seeds", "4.00", "ch-garden", "Garden"))), "ch-garden", "home")
        assertEquals(listOf<String?>("home"), r.lineCategories)
        assertEquals(listOf<ReviewNote>(ReviewNote.RenameInChatelaine("ch-garden", "home")), Receipts.notes(d0, r))
        assertTrue(Receipts.hasChanges(r))
        val d1 = Receipts.confirm(d0, r, clock, "pay-1")
        assertEquals("home", d1.receiptSettings!!.categoryLinks["ch-garden"])
        assertEquals(d0.categories, d1.categories)
    }

    // SPEC:R114
    @Test fun `R114 a Garden line moved to Fun before the answer keeps its move and a later answer replaces the added category`() {
        val d0 = world()
        val r0 = Receipts.open(d0, receipt("rc-1", store, day, line("seeds", "4.00", "ch-garden", "Garden"), line("gloves", "6.00", "ch-garden", "Garden")))
        val moved = Receipts.moveLine(r0, 0, "fun")
        val added = Receipts.addAsNew(moved, "ch-garden", "c-new", Fixture.PROFILE)
        assertEquals(listOf("fun", "c-new"), added.lineCategories)
        // linking instead files the added category's line under the link and drops the category no line holds
        val relinked = Receipts.link(added, "ch-garden", "home")
        assertEquals(listOf("fun", "home"), relinked.lineCategories)
        assertEquals(emptyList(), relinked.addedCategories)
        assertEquals(mapOf("ch-garden" to "home"), relinked.newLinks)
        assertEquals(listOf(Part("fun", cents("4.00")), Part("home", cents("6.00"))), Receipts.parts(relinked))
    }

    // SPEC:R33 SPEC:R114
    @Test fun `R33 a review that added a category has changes and not confirming keeps no category and no link`() {
        val d0 = world()
        val r = Receipts.open(d0, receipt("rc-1", store, day, line("seeds", "4.00", "ch-garden", "Garden")))
        assertFalse(Receipts.hasChanges(r))
        val added = Receipts.addAsNew(r, "ch-garden", "c-new", Fixture.PROFILE)
        assertTrue(Receipts.hasChanges(added))
        // closed without Confirm: the Profile has no Garden and no link, and the next open asks again
        assertTrue(d0.categories.none { it.name == "Garden" })
        assertEquals(emptyMap(), d0.receiptSettings!!.categoryLinks)
        assertEquals(listOf<ReviewNote>(ReviewNote.Unlinked("ch-garden")), Receipts.notes(d0, Receipts.open(d0, receipt("rc-1", store, day, line("seeds", "4.00", "ch-garden", "Garden")))))
    }

    // SPEC:R34 SPEC:R25
    @Test fun `R34 after relinking Snack to Groceries, last month's Eating out spent is unchanged and the next receipt files Snack under Groceries`() {
        val d0 = world()
        val october = LocalDate(2026, 10, 20)
        val r1 = Receipts.link(Receipts.open(d0, receipt("rc-1", store, october, line("crisps", "3.20", "ch-snack", "Snack"))), "ch-snack", "out")
        val d1 = Receipts.confirm(d0, r1, clock, "pay-1")
        assertEquals(cents("3.20"), Spent.of(d1, "out", "2026-10"))

        val d2 = Receipts.changeLink(d1, "ch-snack", "groc")
        assertEquals(cents("3.20"), Spent.of(d2, "out", "2026-10"))
        assertEquals(0L, Spent.of(d2, "groc", "2026-10"))
        assertEquals("out", d2.payments.single().receiptLines.single().categoryId)

        val r2 = Receipts.open(d2, receipt("rc-2", store, day, line("crisps", "3.20", "ch-snack", "Snack")))
        assertEquals(listOf<String?>("groc"), r2.lineCategories)
        val d3 = Receipts.confirm(d2, r2, clock, "pay-2")
        assertEquals(cents("3.20"), Spent.of(d3, "groc", "2026-11"))
        assertEquals(0L, Spent.of(d3, "out", "2026-11"))
    }

    // SPEC:R35 SPEC:R113
    @Test fun `R35 the first receipt from a store opens with Savings and after confirming with a card the next opens with the card`() {
        val d0 = world()
        val r1 = Receipts.open(d0, fiveLines())
        assertEquals(PaymentSource.Savings, r1.source)
        val d1 = Receipts.confirm(d0, Receipts.setSource(r1, PaymentSource.Account("card")), clock, "pay-1")
        assertEquals(cents("16.50"), d1.accounts.single { it.id == "card" }.balance)
        assertEquals(d0.savings, d1.savings)
        assertEquals(PaymentSource.Account("card"), Receipts.open(d1, fiveLines("rc-2")).source)
        // another store still opens with Savings
        assertEquals(PaymentSource.Savings, Receipts.open(d1, receipt("rc-3", "Bottega Faro", day, line("bread", "2.40", "ch-groc", "Groceries"))).source)
    }

    // SPEC:R113 SPEC:R35
    @Test fun `R113 a store whose card is deleted opens with Savings`() {
        val d0 = world()
        val d1 = Receipts.confirm(d0, Receipts.setSource(Receipts.open(d0, fiveLines()), PaymentSource.Account("card")), clock, "pay-1")
        val gone = d1.copy(accounts = d1.accounts.filter { it.id != "card" })
        assertEquals(PaymentSource.Savings, Receipts.open(gone, fiveLines("rc-2")).source)
    }

    // SPEC:R113 SPEC:R35
    @Test fun `R113 a store whose Goal is deleted opens with Savings`() {
        val d0 = world().copy(goals = listOf(goal("trip", "Trip", saved = "100.00")))
        val d1 = Receipts.confirm(d0, Receipts.setSource(Receipts.open(d0, fiveLines()), PaymentSource.Goal("trip")), clock, "pay-1")
        assertEquals(PaymentSource.Goal("trip"), Receipts.open(d1, fiveLines("rc-2")).source)
        val gone = d1.copy(goals = emptyList())
        assertEquals(PaymentSource.Savings, Receipts.open(gone, fiveLines("rc-2")).source)
    }

    // SPEC:R28 SPEC:R112 SPEC:R115
    @Test fun `R28 receipts are recorded only in the receipt Profile`() {
        val a = ProfileData.fresh("p-a", "A").copy(categories = listOf(Category("ga", "p-a", "Groceries")))
        val b = ProfileData.fresh("p-b", "B").copy(categories = listOf(Category("gb", "p-b", "Groceries")))
        val app0 = AppState(activeProfileId = "p-a")
        assertTrue(Receipts.offersFolder(app0, "p-a"))
        val (app1, a1) = Receipts.chooseFolder(app0, a, "folder-handle-1")
        assertEquals("p-a", app1.receiptProfileId)
        assertEquals("folder-handle-1", a1.receiptSettings!!.folder)
        assertFalse(Receipts.offersFolder(app1, "p-b"))
        assertFailsWith<IllegalArgumentException> { Receipts.chooseFolder(app1, b, "folder-handle-2") }

        val file = receipt("rc-1", store, day, line("bread", "2.40", "ch-groc", "Groceries"))
        val inB = Receipts.open(b, file)
        assertFalse(Receipts.canConfirm(b, inB))
        assertEquals(ReceiptProblem.NOT_RECEIPT_PROFILE, Receipts.problem(b, inB))
        assertFailsWith<IllegalArgumentException> { Receipts.confirm(b, inB, clock, "pay-1") }
        val a2 = Receipts.confirm(a1, Receipts.open(a1, file), clock, "pay-1")
        assertEquals(1, a2.payments.size)
        assertTrue(b.payments.isEmpty())
    }

    // SPEC:R21 SPEC:R115
    @Test fun `R21 a recorded receipt payment holds every line`() {
        val d1 = Receipts.confirm(world(), Receipts.open(world(), fiveLines()), clock, "pay-1")
        val p = d1.payments.single()
        assertEquals("rc-1", p.receiptId)
        assertEquals(5, p.receiptLines.size)
        assertEquals(ReceiptLine("milk", "1 l", 1, cents("1.89"), "ch-groc", "Groceries", "groc"), p.receiptLines[0])
        assertEquals(ReceiptLine("soap", null, 2, cents("6.90"), "ch-home", "Household", "home"), p.receiptLines[3])
        assertNull(p.bankKeys)
    }

    // SPEC:R115 SPEC:R23
    @Test fun `R115 a 0_00 line under Gifts adds no part and is kept among the lines`() {
        val d0 = world()
        val r = Receipts.open(d0, receipt("rc-1", store, day, line("bread", "2.40", "ch-groc", "Groceries"), line("sample", "0.00", "ch-gift", "Gifts")))
        assertEquals(listOf(Part("groc", cents("2.40"))), Receipts.parts(r))
        val p = Receipts.confirm(d0, r, clock, "pay-1").payments.single()
        assertEquals(listOf(Part("groc", cents("2.40"))), p.parts)
        assertEquals(listOf("groc", "gift"), p.receiptLines.map { it.categoryId })
    }

    // SPEC:R115 SPEC:R54
    @Test fun `R115 a 16_50 receipt from a Goal holding 10_00 warns and leaves the Goal at 0_00 and Savings at 993_50`() {
        val d0 = world()
        val r = Receipts.setSource(Receipts.open(d0, fiveLines()), PaymentSource.Goal("trip"))
        assertEquals(PaymentNotice.GOAL_SHORT, Receipts.notice(d0, r))
        assertTrue(Receipts.canConfirm(d0, r))
        val d1 = Receipts.confirm(d0, r, clock, "pay-1")
        assertEquals(0L, d1.goals.single().saved)
        assertEquals(cents("993.50"), d1.savings.amount)
        assertEquals(cents("6.50"), d1.payments.single().fromSavings)
    }

    // SPEC:R115 SPEC:R109
    @Test fun `R115 a receipt charged to a card whose charges are not logged carries that warning`() {
        val d0 = world()
        assertEquals(PaymentNotice.CHARGES_NOT_LOGGED, Receipts.notice(d0, Receipts.setSource(Receipts.open(d0, fiveLines()), PaymentSource.Account("loose"))))
        assertNull(Receipts.notice(d0, Receipts.open(d0, fiveLines())))
    }

    // SPEC:R115 SPEC:R30
    @Test fun `R115 a receipt id recorded once cannot be confirmed again`() {
        val d0 = world()
        val d1 = Receipts.confirm(d0, Receipts.open(d0, fiveLines()), clock, "pay-1")
        val again = Receipts.open(d1, fiveLines())
        assertEquals(ReceiptProblem.ALREADY_RECORDED, Receipts.problem(d1, again))
        assertFailsWith<IllegalArgumentException> { Receipts.confirm(d1, again, clock, "pay-2") }
    }

    // SPEC:R115 SPEC:R30 SPEC:R47
    @Test fun `R115 a USD receipt in a EUR Profile cannot be confirmed`() {
        val d0 = world()
        val r = Receipts.open(d0, receipt("rc-1", store, day, line("bread", "2.40", "ch-groc", "Groceries"), currency = "USD"))
        assertEquals(ReceiptProblem.OTHER_CURRENCY, Receipts.problem(d0, r))
    }

    // SPEC:R115
    @Test fun `R115 a line moved to a category that does not exist keeps Confirm off`() {
        val d0 = world()
        val r = Receipts.moveLine(Receipts.open(d0, fiveLines()), 0, "nowhere")
        assertEquals(ReceiptProblem.CATEGORY_MISSING, Receipts.problem(d0, r))
    }

    // SPEC:R115 SPEC:R54
    @Test fun `R115 a source that does not exist keeps Confirm off`() {
        val d0 = world()
        val r = Receipts.setSource(Receipts.open(d0, fiveLines()), PaymentSource.Goal("gone"))
        assertEquals(ReceiptProblem.CANNOT_SAVE, Receipts.problem(d0, r))
    }
}
