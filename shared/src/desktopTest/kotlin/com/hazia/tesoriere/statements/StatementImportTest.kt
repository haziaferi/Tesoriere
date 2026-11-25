package com.hazia.tesoriere.statements

import com.hazia.tesoriere.Fixture
import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.goal
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Ids
import com.hazia.tesoriere.TestClock
import com.hazia.tesoriere.cents
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.payments.BankKeys
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.Payment
import com.hazia.tesoriere.payments.PaymentDraft
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Spent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Ported from the import half of Sci-Fin's ImportBackupTest.kt at 2892110, with the plan's named
 * tests for Task 13 (SPEC R22-R24, R26, R104, R107, R108). The statements under
 * `resources/statements/` are made up: payees, amounts and FITIDs alike.
 */
class StatementImportTest {
    private val clock = TestClock(LocalDate(2026, 3, 31))
    private val ids = Ids()

    private fun text(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/statements/$name")) { "missing fixture $name" }.use { it.readBytes().decodeToString() }

    private fun march() = StatementImport.parseOfx(text("march.ofx"))
    private fun april() = StatementImport.parseOfx(text("april.ofx"))

    /** Savings 1,000.00; a Cash goal holding 110.00; a card at 300.00; Rent, swept monthly under Housing. */
    private fun world(): ProfileData = profile(
        savings = "1,000.00",
        goals = listOf(goal("cash", "Cash", saved = "110.00")),
        categories = listOf(
            category("groc", "Groceries"), category("pharm", "Pharmacy"), category("home", "Household"),
            category("housing", "Housing"), category("books", "Books"),
        ),
    ).copy(
        accounts = listOf(Account("orbita", Fixture.PROFILE, "Orbita card", type = AccountType.CREDIT, balance = cents("300.00"))),
        recurringExpenses = listOf(
            Fixture.recurring(
                "rent", "950.00", Cadence.MONTHLY, firstDue = LocalDate(2026, 1, 5), dueDate = LocalDate(2026, 4, 5),
                categoryId = "housing", name = "Rent", lastSweptMonth = "2026-03",
            ),
        ),
    )

    private fun List<ReviewRow>.row(desc: String) = single { it.row.description == desc }

    /** Files the row described [desc] as a payment with [parts] from [source]. */
    private fun List<ReviewRow>.file(desc: String, vararg parts: Pair<String, String>, source: PaymentSource = PaymentSource.Savings) =
        map { if (it.row.description == desc) it.copy(draft = StatementImport.draft(it.row, parts.map { (c, a) -> Part(c, cents(a)) }, source)) else it }

    private fun List<ReviewRow>.payoff(desc: String, accountId: String) =
        map { if (it.row.description == desc) it.copy(payoffAccountId = accountId) else it }

    private fun List<ReviewRow>.tick(desc: String, include: Boolean) =
        map { if (it.row.description == desc) it.copy(include = include) else it }

    /** The March statement filed as the plan's review figures: Groceries, a split pharmacy bill, a card payoff. */
    private fun marchFiled(d: ProfileData) = StatementImport.review(d, march(), clock.today)
        .file("POS MERCATO VERDE 0412", "groc" to "12.40")
        .file("FARMACIA SAN LORENZO", "pharm" to "30.00", "home" to "12.30")
        .payoff("PAGAMENTO CARTA ORBITA", "orbita")

    // ---- amounts ---------------------------------------------------------------------------------

    // SPEC:R104, R20
    @Test fun `R104 amounts parse to whole cents`() {
        val cases = mapOf(
            "1.234,56" to 123456L, "1,234.56" to 123456L, "-1.234,56" to -123456L, "(12,40)" to -1240L, "12,40-" to -1240L,
            "0,5" to 50L, "12" to 1200L, "-12,40" to -1240L, "1.450,00" to 145000L, "1,450" to 145000L, "€ 9.5" to 950L,
            "+12.40" to 1240L, "12,345.678" to 1234568L, "0,1249" to 12L, "1.234.567" to 123456700L,
        )
        for ((raw, expected) in cases) assertEquals(expected, StatementImport.amount(raw), raw)
        for (raw in listOf("", "abc", "-", "1.234.56", ".")) assertNull(StatementImport.amount(raw), raw)
        assertNull(StatementImport.amount(null))
    }

    // ---- keys and duplicates ---------------------------------------------------------------------

    // SPEC:R26
    @Test fun `R26 the keys of a -12_40 row on 2026-03-03 are its FITID and syn_2026-03-03 -12_40 pos mercato verde 0412`() {
        val k = StatementImport.keys(march().first())
        assertEquals(BankKeys("TSRM0001", "syn:2026-03-03|-12.40|pos mercato verde 0412"), k)
        assertEquals(listOf("fit:TSRM0001", "syn:2026-03-03|-12.40|pos mercato verde 0412"), StatementImport.keyStrings(k))
    }

    // SPEC:R26, R107
    @Test fun `R26 the same OFX statement twice records nothing the second time`() {
        val d = world()
        val first = marchFiled(d).tick("AFFITTO CASA RENT", true).file("AFFITTO CASA RENT", "housing" to "950.00")
        val after = StatementImport.confirm(d, first, clock, ids::next)
        assertEquals(10, after.importState.importedKeys.size)
        val again = StatementImport.review(after, march(), clock.today)
        assertEquals(5, again.size)
        assertTrue(again.all { it.status == RowStatus.DUPLICATE && !it.include })
        assertEquals(after, StatementImport.confirm(after, again, clock, ids::next))
    }

    // SPEC:R26, R106
    @Test fun `R26 a CSV export of the same period records nothing after the OFX`() {
        val d = world()
        val first = marchFiled(d).tick("AFFITTO CASA RENT", true).file("AFFITTO CASA RENT", "housing" to "950.00")
        val after = StatementImport.confirm(d, first, clock, ids::next)
        val csv = StatementImport.review(after, StatementImport.parseCsv(text("march.csv")), clock.today)
        assertEquals(5, csv.size)
        assertTrue(csv.all { it.status == RowStatus.DUPLICATE && !it.include })
        // and the other way round: a CSV first, then the OFX of the same rows
        val csvFirst = StatementImport.review(d, StatementImport.parseCsv(text("march.csv")), clock.today)
            .file("POS MERCATO VERDE 0412", "groc" to "12.40")
            .file("FARMACIA SAN LORENZO", "pharm" to "42.30")
            .payoff("PAGAMENTO CARTA ORBITA", "orbita")
        val afterCsv = StatementImport.confirm(d, csvFirst, clock, ids::next)
        val ofx = StatementImport.review(afterCsv, march(), clock.today)
        assertEquals(0, ofx.count { it.include })
        assertEquals(4, ofx.count { it.status == RowStatus.DUPLICATE })
        assertEquals(RowStatus.RECURRING, ofx.row("AFFITTO CASA RENT").status)
    }

    // SPEC:R26
    @OptIn(ExperimentalTime::class)
    @Test fun `R26 a row in a payment's bank keys arrives as a duplicate though never imported`() {
        fun paid(id: String, keys: BankKeys) = Payment(
            id, Fixture.PROFILE, LocalDate(2026, 3, 3), Instant.parse("2026-03-03T10:00:00Z"), 1240, PaymentSource.Savings,
            "Mercato Verde", listOf(Part("groc", 1240)), receiptId = "rcpt-1", bankKeys = keys,
        )
        val d = world().copy(
            payments = listOf(
                paid("p1", BankKeys(null, "syn:2026-03-03|-12.40|pos mercato verde 0412")),
                paid("p2", BankKeys("TSRM0004", "syn:2026-03-10|-42.30|another description")),
            ),
        )
        assertTrue(d.importState.importedKeys.isEmpty())
        val review = StatementImport.review(d, march(), clock.today)
        assertEquals(RowStatus.DUPLICATE, review.row("POS MERCATO VERDE 0412").status)
        assertEquals(RowStatus.DUPLICATE, review.row("FARMACIA SAN LORENZO").status)
        assertEquals(RowStatus.READY, review.row("PAGAMENTO CARTA ORBITA").status)
    }

    // ---- payee rules -----------------------------------------------------------------------------

    // SPEC:R26, R108
    @Test fun `R26 a payee filed under a category once is pre-filled next time`() {
        val d = world()
        val first = StatementImport.review(d, march(), clock.today)
        assertNull(first.row("POS MERCATO VERDE 0412").draft)                       // never guessed on first sight
        val after = StatementImport.confirm(d, marchFiled(d), clock, ids::next)
        assertEquals(mapOf("pos mercato verde 0412" to "groc"), after.importState.payeeRules)
        clock.setDay(LocalDate(2026, 4, 30))
        val next = StatementImport.review(after, april(), clock.today)
        val mercato = next.row("POS MERCATO VERDE 0412")
        assertEquals(RowStatus.READY, mercato.status)
        assertEquals(PaymentDraft(LocalDate(2026, 4, 2), 1875, PaymentSource.Savings, "POS MERCATO VERDE 0412", listOf(Part("groc", 1875))), mercato.draft)
        assertNull(next.row("POS LIBRERIA DEL PORTO").draft)
        assertEquals(RowStatus.DUPLICATE, next.row("PAGAMENTO CARTA ORBITA").status) // the March row sent again
    }

    // SPEC:R108
    @Test fun `R108 a row split into Pharmacy 30_00 and Household 12_30 teaches no payee rule`() {
        val d = world()
        val after = StatementImport.confirm(d, marchFiled(d), clock, ids::next)
        assertFalse("farmacia san lorenzo" in after.importState.payeeRules)
        assertNull(StatementImport.review(after, april(), clock.today).row("FARMACIA SAN LORENZO").draft)
        // a split row leaves an earlier rule as it was
        val taught = d.copy(importState = d.importState.copy(payeeRules = mapOf("farmacia san lorenzo" to "pharm")))
        val kept = StatementImport.confirm(taught, marchFiled(taught), clock, ids::next)
        assertEquals("pharm", kept.importState.payeeRules["farmacia san lorenzo"])
    }

    // SPEC:R108
    @Test fun `R108 a row filed under one category from a Goal teaches its payee too`() {
        val d = world()
        val review = StatementImport.review(d, march(), clock.today)
            .map { if (it.row.amount < 0) it.copy(include = false) else it }
            .tick("POS MERCATO VERDE 0412", true)
            .file("POS MERCATO VERDE 0412", "groc" to "12.40", source = PaymentSource.Goal("cash"))
        val after = StatementImport.confirm(d, review, clock, ids::next)
        assertEquals("groc", after.importState.payeeRules["pos mercato verde 0412"])
    }

    // SPEC:R108
    @Test fun `R108 a rule whose category was deleted pre-fills nothing`() {
        val d = world().let { it.copy(importState = it.importState.copy(payeeRules = mapOf("pos mercato verde 0412" to "gone"))) }
        assertNull(StatementImport.review(d, march(), clock.today).row("POS MERCATO VERDE 0412").draft)
    }

    // SPEC:R108
    @Test fun `R108 the payee of POS star MERCATO-VERDE 0412 is pos mercato verde 0412`() {
        assertEquals("pos mercato verde 0412", StatementImport.normalisePayee("POS*MERCATO-VERDE  0412"))
        assertEquals("caffè äöü 12", StatementImport.normalisePayee("  Caffè/ÄÖÜ   12 "))
    }

    // ---- the review --------------------------------------------------------------------------------

    // SPEC:R107, R22, R54, R89
    @Test fun `R107 the March review names 4 rows, 114_70 out and 1,450_00 in, and leaves Savings at 2,335_30 and the card at 240_00`() {
        val d = world()
        val review = StatementImport.review(d, march(), clock.today)
        assertEquals(listOf(18, 10, 7, 5, 3), review.map { it.row.date.day })   // newest first
        assertEquals(RowStatus.RECURRING, review.row("AFFITTO CASA RENT").status)
        assertFalse(review.row("AFFITTO CASA RENT").include)
        assertEquals(RowStatus.INCOMING, review.row("STIPENDIO MARZO").status)
        assertTrue(review.row("STIPENDIO MARZO").include)
        assertNull(review.row("STIPENDIO MARZO").draft)
        assertEquals(RowStatus.READY, review.row("FARMACIA SAN LORENZO").status)
        assertTrue(review.all { it.pairPaymentId == null && it.payoffAccountId == null && it.status != RowStatus.PAIR })
        assertFalse(StatementImport.canConfirm(d, review))

        val filed = marchFiled(d)
        assertTrue(StatementImport.canConfirm(d, filed))
        assertEquals(ConfirmSummary(count = 4, moneyOut = cents("114.70"), moneyIn = cents("1,450.00")), StatementImport.confirmSummary(filed))
        val after = StatementImport.confirm(d, filed, clock, ids::next)

        assertEquals(cents("2,335.30"), after.savings.amount)
        assertEquals(cents("240.00"), after.accounts.single().balance)
        assertEquals(2, after.payments.size)
        assertEquals(1240L, Spent.of(after, "groc", "2026-03"))
        assertEquals(3000L, Spent.of(after, "pharm", "2026-03"))
        assertEquals(1230L, Spent.of(after, "home", "2026-03"))
        assertEquals(0L, Spent.of(after, "housing", "2026-03"))
        val mercato = after.payments.single { it.payee == "POS MERCATO VERDE 0412" }
        assertEquals(LocalDate(2026, 3, 3), mercato.date)
        assertEquals(PaymentSource.Savings, mercato.source)
        assertEquals(BankKeys("TSRM0001", "syn:2026-03-03|-12.40|pos mercato verde 0412"), mercato.bankKeys)
        val salary = after.transactions.single { it.payee == "STIPENDIO MARZO" }
        assertEquals(LocalDate(2026, 3, 7), salary.date)
        assertFalse(salary.isIncome)
        assertEquals(LocalDate(2026, 3, 18), after.transactions.first { it.key == "acct:orbita" }.date)
        assertEquals(8, after.importState.importedKeys.size)
        assertEquals(clock.today, after.importState.lastImportDate)
    }

    // SPEC:R107, R50
    @Test fun `R107 the 1,450_00 salary sent on into a Goal holding 110_00 leaves it 1,560_00 and Savings 885_30`() {
        val d = world()
        val filed = marchFiled(d).map { if (it.row.description == "STIPENDIO MARZO") it.copy(incomingGoalId = "cash") else it }
        assertTrue(StatementImport.canConfirm(d, filed))
        assertEquals(ConfirmSummary(4, cents("114.70"), cents("1,450.00")), StatementImport.confirmSummary(filed))
        val after = StatementImport.confirm(d, filed, clock, ids::next)
        assertEquals(cents("1,560.00"), after.goals.single().saved)
        assertEquals(cents("885.30"), after.savings.amount)
        val salary = after.transactions.filter { it.payee == "STIPENDIO MARZO" }
        assertEquals(listOf("savings" to cents("1,450.00"), "goal:cash" to cents("1,450.00")), salary.map { it.key to it.amount })
        assertTrue(salary.all { it.date == LocalDate(2026, 3, 7) && !it.isIncome })
        assertEquals(8, after.importState.importedKeys.size)
    }

    // SPEC:R107
    @Test fun `R107 money in sent to a Goal deleted before Confirm stays in Savings, and an outflow sent into a Goal keeps Confirm off`() {
        val d = world()
        val filed = marchFiled(d).map { if (it.row.description == "STIPENDIO MARZO") it.copy(incomingGoalId = "gone") else it }
        assertTrue(StatementImport.canConfirm(d, filed))
        val after = StatementImport.confirm(d, filed, clock, ids::next)
        assertEquals(cents("2,335.30"), after.savings.amount)
        assertEquals(cents("110.00"), after.goals.single().saved)
        val wrong = marchFiled(d).map { if (it.row.description == "POS MERCATO VERDE 0412") it.copy(incomingGoalId = "cash") else it }
        assertFalse(StatementImport.canConfirm(d, wrong))
    }

    // SPEC:R23, R107
    @Test fun `R23 confirm stays off while an included outflow has parts that do not add up`() {
        val d = world()
        val short = marchFiled(d).file("FARMACIA SAN LORENZO", "pharm" to "30.00", "home" to "12.00")
        assertFalse(StatementImport.canConfirm(d, short))
        assertFails { StatementImport.confirm(d, short, clock, ids::next) }
        val full = short.file("FARMACIA SAN LORENZO", "pharm" to "30.00", "home" to "12.00", "home" to "0.30")
        assertTrue(StatementImport.canConfirm(d, full))
        // unticked, a row that does not add up no longer holds Confirm back
        assertTrue(StatementImport.canConfirm(d, short.tick("FARMACIA SAN LORENZO", false)))
    }

    // SPEC:R107
    @Test fun `R107 confirm stays off while an included outflow has nowhere filed or a payoff of no Account`() {
        val d = world()
        val review = StatementImport.review(d, march(), clock.today)
        assertFalse(StatementImport.canConfirm(d, review))
        val unticked = review.map { if (it.row.amount < 0) it.copy(include = false) else it }
        assertTrue(StatementImport.canConfirm(d, unticked))
        assertFalse(StatementImport.canConfirm(d, marchFiled(d).payoff("POS MERCATO VERDE 0412", "nowhere").map {
            if (it.row.description == "POS MERCATO VERDE 0412") it.copy(draft = null) else it
        }))
        // a draft and a payoff on one row is neither
        assertFalse(StatementImport.canConfirm(d, marchFiled(d).payoff("POS MERCATO VERDE 0412", "orbita")))
    }

    // SPEC:R107, R54
    @Test fun `R107 rows of 100_00 and 14_00 from a Goal holding 110_00 keep confirm off, the first alone leaves 10_00`() {
        val d = world()
        val rows = listOf(
            BankRow(LocalDate(2026, 3, 8), -10000, "PRELIEVO SPORTELLO 0412", null),
            BankRow(LocalDate(2026, 3, 7), -1400, "POS EDICOLA CENTRALE", null),
        )
        val both = StatementImport.review(d, rows, clock.today)
            .file("PRELIEVO SPORTELLO 0412", "groc" to "100.00", source = PaymentSource.Goal("cash"))
            .file("POS EDICOLA CENTRALE", "books" to "14.00", source = PaymentSource.Goal("cash"))
        assertFalse(StatementImport.canConfirm(d, both))
        val first = both.tick("POS EDICOLA CENTRALE", false)
        assertTrue(StatementImport.canConfirm(d, first))
        val after = StatementImport.confirm(d, first, clock, ids::next)
        assertEquals(cents("10.00"), after.goals.single().saved)
        assertEquals(cents("1,000.00"), after.savings.amount)
        assertEquals(cents("100.00"), Spent.of(after, "groc", "2026-03"))
        // one row above what the Goal holds is off on its own
        val over = StatementImport.review(d, listOf(BankRow(LocalDate(2026, 3, 8), -12000, "PRELIEVO SPORTELLO 0412", null)), clock.today)
            .file("PRELIEVO SPORTELLO 0412", "groc" to "120.00", source = PaymentSource.Goal("cash"))
        assertFalse(StatementImport.canConfirm(d, over))
    }

    // SPEC:R22, R107
    @Test fun `R22 a -12_40 bank row charged to a card raises it to 312_40, counts in Groceries and leaves Savings`() {
        val d = world()
        val review = StatementImport.review(d, march(), clock.today)
            .map { it.copy(include = false) }
            .tick("POS MERCATO VERDE 0412", true)
            .file("POS MERCATO VERDE 0412", "groc" to "12.40", source = PaymentSource.Account("orbita"))
        val after = StatementImport.confirm(d, review, clock, ids::next)
        assertEquals(cents("312.40"), after.accounts.single().balance)
        assertEquals(cents("1,000.00"), after.savings.amount)
        assertEquals(1240L, Spent.of(after, "groc", "2026-03"))
        assertEquals(PaymentSource.Account("orbita"), after.payments.single().source)
    }

    // SPEC:R24, R23
    @Test fun `R24 a bank row saves with two parts in two categories`() {
        val d = world()
        val after = StatementImport.confirm(d, marchFiled(d), clock, ids::next)
        val bill = after.payments.single { it.payee == "FARMACIA SAN LORENZO" }
        assertEquals(4230L, bill.total)
        assertEquals(listOf(Part("pharm", 3000), Part("home", 1230)), bill.parts)
        assertEquals("TSRM0004", bill.bankKeys?.fitid)
    }

    // SPEC:R107
    @Test fun `R107 the review row's draft takes the bank row's day and amount whatever it holds`() {
        val d = world()
        val odd = marchFiled(d).map {
            if (it.row.description == "POS MERCATO VERDE 0412") it.copy(draft = it.draft!!.copy(date = LocalDate(2026, 1, 1), total = 99)) else it
        }
        val after = StatementImport.confirm(d, odd, clock, ids::next)
        val mercato = after.payments.single { it.payee == "POS MERCATO VERDE 0412" }
        assertEquals(LocalDate(2026, 3, 3), mercato.date)
        assertEquals(1240L, mercato.total)
    }
}
