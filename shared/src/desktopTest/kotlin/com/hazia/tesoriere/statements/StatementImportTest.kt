package com.hazia.tesoriere.statements

import com.hazia.tesoriere.Fixture
import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.goal
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Ids
import com.hazia.tesoriere.engine.Accounts
import com.hazia.tesoriere.engine.savingsAmount
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
import com.hazia.tesoriere.payments.PaymentNotice
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

    // SPEC:R104
    @Test fun `R104 a Unicode minus, an en dash and a DR suffix read -12_40, a CR suffix 12_40`() {
        val cases = mapOf(
            "−12,40" to -1240L, "–12,40" to -1240L, "12,40−" to -1240L,
            "12,40 DR" to -1240L, "12,40DR" to -1240L, "12,40 dr" to -1240L, "12,40 CR" to 1240L, "1.234,56 Cr" to 123456L,
        )
        for ((raw, expected) in cases) assertEquals(expected, StatementImport.amount(raw), raw)
    }

    // SPEC:R104
    @Test fun `R104 a day, a time or a word is no amount, so 04_03_2026 never reads 403,202_60`() {
        for (raw in listOf("04/03/2026", "10:30", "DR 12,40", "abc", "EURO 12,40", "12,40 EUR EUR", "EUR"))
            assertNull(StatementImport.amount(raw), raw)
    }

    // SPEC:R104
    @Test fun `R104 a three-letter currency code before or after the figure is ignored, so EUR 12,40 reads 1240`() {
        val cases = mapOf("EUR 12,40" to 1240L, "12,40 USD" to 1240L, "eur -12,40" to -1240L, "1.234,56 EUR" to 123456L, "12,40 EUR DR" to -1240L)
        for ((raw, expected) in cases) assertEquals(expected, StatementImport.amount(raw), raw)
    }

    // ---- keys and duplicates ---------------------------------------------------------------------

    // SPEC:R26
    @Test fun `R26 the keys of a -12_40 row on 2026-03-03 are its FITID and syn_2026-03-03 -12_40 pos mercato verde 0412`() {
        val k = StatementImport.keys(march().first())
        assertEquals(BankKeys("TSRM0001", "syn:2026-03-03|-12.40|pos mercato verde 0412"), k)
        assertEquals(listOf("fit:TSRM0001", "syn:2026-03-03|-12.40|pos mercato verde 0412"), StatementImport.keyStrings(k))
    }

    // SPEC:R26
    @Test fun `R26 a -12_40 row spaced with two spaces, a tab or a non-breaking space keys as syn_2026-03-03 -12_40 pos mercato verde 0412`() {
        val key = "syn:2026-03-03|-12.40|pos mercato verde 0412"
        for (desc in listOf("POS  MERCATO VERDE 0412", "POS\tMERCATO VERDE 0412", "POS MERCATO VERDE 0412", " POS MERCATO VERDE 0412  ", "POS \t MERCATO VERDE 0412"))
            assertEquals(key, StatementImport.keys(BankRow(LocalDate(2026, 3, 3), -1240, desc, null)).synKey, desc)
        // 40 characters are taken after the spacing is made one
        val long = StatementImport.keys(BankRow(LocalDate(2026, 3, 3), -1240, "A    B C D E F G H I J K L M N O P Q R S T U V", null)).synKey
        assertEquals("syn:2026-03-03|-12.40|a b c d e f g h i j k l m n o p q r s t ", long)
    }

    // SPEC:R26, R107
    @Test fun `R26 a CSV row spaced differently from the recorded OFX row arrives as already recorded`() {
        val d = world()
        val after = StatementImport.confirm(d, marchFiled(d), clock, ids::next)
        val csv = StatementImport.parseCsv("Date;Description;Amount\n03/03/2026;POS  MERCATO VERDE\t0412;-12,40\n")
        val review = StatementImport.review(after, csv, clock.today)
        assertEquals(RowStatus.DUPLICATE, review.single().status)
        assertFalse(review.single().include)
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
        assertEquals(mapOf("mercato verde" to "groc"), after.importState.payeeRules)       // kept under the rule name
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
        assertEquals("groc", after.importState.payeeRules["mercato verde"])
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

    // SPEC:R108
    @Test fun `R108 POS 03_03 MERCATO VERDE 0412 and PAGAMENTO POS 12_03_2026 ORE 10_22 MERCATO VERDE CARTA star 1234 both have the rule name mercato verde`() {
        val pos = "POS 03/03 MERCATO VERDE 0412"
        val card = "PAGAMENTO POS 12/03/2026 ORE 10:22 MERCATO VERDE CARTA ****1234"
        assertEquals("mercato verde", StatementImport.payeeRuleName(pos))
        assertEquals("mercato verde", StatementImport.payeeRuleName(card))
        assertEquals("mercato verde", StatementImport.payeeRuleName("CARD PAYMENT MERCATO VERDE XXXX XXXX 1234"))
        assertEquals("farmacia san lorenzo", StatementImport.payeeRuleName("FARMACIA SAN LORENZO"))
        assertEquals("pos 0412", StatementImport.payeeRuleName("POS 0412"))                 // nothing left: the whole payee
        assertEquals("pos mercato verde 0412", StatementImport.normalisePayee("POS MERCATO VERDE 0412"))  // the payee itself keeps every word
    }

    // SPEC:R108
    @Test fun `R108 a rule learned from POS 03_03 MERCATO VERDE 0412 pre-fills PAGAMENTO POS 12_03_2026 MERCATO VERDE CARTA with 18_75 in Groceries`() {
        val d = world()
        val march = StatementImport.review(d, listOf(BankRow(LocalDate(2026, 3, 3), -1240, "POS 03/03 MERCATO VERDE 0412", null)), clock.today)
            .file("POS 03/03 MERCATO VERDE 0412", "groc" to "12.40")
        val after = StatementImport.confirm(d, march, clock, ids::next)
        assertEquals(mapOf("mercato verde" to "groc"), after.importState.payeeRules)
        val desc = "PAGAMENTO POS 12/03/2026 ORE 10:22 MERCATO VERDE CARTA ****1234"
        val next = StatementImport.review(after, listOf(BankRow(LocalDate(2026, 3, 12), -1875, desc, null)), clock.today).single()
        assertEquals(PaymentDraft(LocalDate(2026, 3, 12), 1875, PaymentSource.Savings, desc, listOf(Part("groc", 1875))), next.draft)
        // a rule kept under the whole payee still matches it
        val kept = d.copy(importState = d.importState.copy(payeeRules = mapOf("pos mercato verde 0412" to "groc")))
        assertEquals(listOf(Part("groc", 1240)), StatementImport.review(kept, march(), clock.today).row("POS MERCATO VERDE 0412").draft?.parts)
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
        assertEquals(ConfirmSummary(count = 4, moneyOut = cents("114.70"), moneyIn = cents("1,450.00")), StatementImport.confirmSummary(d, filed))
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

    // SPEC:R107 SPEC:R89 SPEC:R93
    @Test fun `R107 a -60_00 payoff of a card linked to its fixed payment leaves Savings 2,395_30 by default and 2,335_30 with the pair on`() {
        val linked = world().let { w ->
            w.copy(
                accounts = w.accounts.map { it.copy(linkedRecurringExpenseId = "orbita-pay") },
                recurringExpenses = w.recurringExpenses + Fixture.recurring(
                    "orbita-pay", "60.00", Cadence.MONTHLY, firstDue = LocalDate(2026, 4, 20), categoryId = "home",
                    name = "Orbita card payment", linkedAccountId = "orbita", lastSweptMonth = "2026-03",
                ),
            )
        }
        val filed = marchFiled(linked)
        val byDefault = StatementImport.confirm(linked, filed, clock, ids::next)
        assertEquals(cents("240.00"), byDefault.accounts.single().balance)
        assertEquals(cents("2,395.30"), byDefault.savings.amount)                     // the sweep moves Savings, not the payoff
        assertTrue(byDefault.transactions.none { it.key == "savings" && it.amount == cents("-60.00") })
        val paired = filed.map { if (it.row.description == "PAGAMENTO CARTA ORBITA") it.copy(payoffFrom = Accounts.PayoffFrom.Savings) else it }
        val after = StatementImport.confirm(linked, paired, clock, ids::next)
        assertEquals(cents("240.00"), after.accounts.single().balance)
        assertEquals(cents("2,335.30"), after.savings.amount)
        // a card linked to nothing pairs by default, and the pair can be turned off for the row
        assertEquals(cents("2,335.30"), StatementImport.confirm(world(), marchFiled(world()), clock, ids::next).savings.amount)
        val off = marchFiled(world()).map { if (it.row.description == "PAGAMENTO CARTA ORBITA") it.copy(payoffFrom = Accounts.PayoffFrom.None) else it }
        assertEquals(cents("2,395.30"), StatementImport.confirm(world(), off, clock, ids::next).savings.amount)
    }

    // SPEC:R107 SPEC:R89
    @Test fun `R107 the -60_00 payoff of the card at 300_00 from a Goal holding 110_00 leaves the card 240_00, the Goal 50_00, and from 50_00 Confirm is off`() {
        val d = world()
        val fromGoal = marchFiled(d).map { if (it.row.description == "PAGAMENTO CARTA ORBITA") it.copy(payoffFrom = Accounts.PayoffFrom.Goal("cash")) else it }
        assertTrue(StatementImport.canConfirm(d, fromGoal))
        val after = StatementImport.confirm(d, fromGoal, clock, ids::next)
        assertEquals(cents("240.00"), after.accounts.single().balance)
        assertEquals(cents("50.00"), after.goals.single().saved)
        assertEquals(cents("2,395.30"), after.savings.amount)                        // 1,000.00 + 1,450.00 - 12.40 - 42.30
        val poorer = d.copy(goals = d.goals.map { it.copy(saved = cents("50.00")) })
        assertFalse(StatementImport.canConfirm(poorer, fromGoal))
        assertFalse(StatementImport.canConfirm(d, fromGoal.map { if (it.payoffFrom != null) it.copy(payoffFrom = Accounts.PayoffFrom.Goal("gone")) else it }))
    }

    // SPEC:R107 SPEC:R61
    @Test fun `R107 TIM S_P_A -29_90 within 3 days of Telefono's due date on the 10th arrives RECURRING, 10 days off READY`() {
        val d = world().let { w ->
            w.copy(
                recurringExpenses = w.recurringExpenses + Fixture.recurring(
                    "tel", "29.90", Cadence.MONTHLY, firstDue = LocalDate(2026, 1, 10), dueDate = LocalDate(2026, 4, 10),
                    categoryId = "home", name = "Telefono", lastSweptMonth = "2026-03",
                ),
            )
        }
        val rows = listOf(
            BankRow(LocalDate(2026, 3, 12), -2990, "TIM S.P.A.", null),           // 2 days after
            BankRow(LocalDate(2026, 3, 7), -2990, "TIM S.P.A. FATTURA", null),    // 3 days before
            BankRow(LocalDate(2026, 3, 20), -2990, "TIM S.P.A. RICARICA", null),  // 10 days after
            BankRow(LocalDate(2026, 3, 11), -3000, "TIM S.P.A. ALTRO", null),     // another amount
            BankRow(LocalDate(2026, 3, 5), -95000, "AFFITTO CASA RENT", null),     // the name still matches
        )
        val review = StatementImport.review(d, rows, clock.today)
        assertEquals(
            listOf(
                LocalDate(2026, 3, 20) to RowStatus.READY, LocalDate(2026, 3, 12) to RowStatus.RECURRING,
                LocalDate(2026, 3, 11) to RowStatus.READY, LocalDate(2026, 3, 7) to RowStatus.RECURRING,
                LocalDate(2026, 3, 5) to RowStatus.RECURRING,
            ),
            review.map { it.row.date to it.status },
        )
        assertEquals(listOf(true, false, true, false, false), review.map { it.include })
    }

    // SPEC:R107 SPEC:R61 SPEC:R64
    @Test fun `R107 with Telefono first due 10 March 2026, TELEFONO -12_00 on 7 March and TIM -29_90 on 10 February arrive READY, on 10 March RECURRING`() {
        val d = world().let { w ->
            w.copy(
                recurringExpenses = w.recurringExpenses + Fixture.recurring(
                    "tel", "29.90", Cadence.MONTHLY, firstDue = LocalDate(2026, 3, 10), categoryId = "home", name = "Telefono", lastSweptMonth = "2026-03",
                ),
            )
        }
        val rows = listOf(
            BankRow(LocalDate(2026, 3, 7), -1200, "TELEFONO ABBONAMENTO", null),     // 3 days before the first due date
            BankRow(LocalDate(2026, 2, 10), -2990, "TIM S.P.A.", null),              // the amount on a day before the chain starts
        )
        val review = StatementImport.review(d, rows, clock.today)
        assertEquals(listOf(RowStatus.READY, RowStatus.READY), review.map { it.status })
        assertTrue(review.all { it.include })
        val on = StatementImport.review(d, listOf(BankRow(LocalDate(2026, 3, 10), -1200, "TELEFONO ABBONAMENTO", null)), clock.today)
        assertEquals(RowStatus.RECURRING, on.single().status)
    }

    // SPEC:R107 SPEC:R60 SPEC:R64
    @Test fun `R107 with Telefono first due 5 March but created on 14 March, TELEFONO on 10 March and TIM -29_90 on 5 March arrive READY, on 14 March RECURRING`() {
        val d = world().let { w ->
            w.copy(
                recurringExpenses = w.recurringExpenses + Fixture.recurring(
                    "tel", "29.90", Cadence.MONTHLY, firstDue = LocalDate(2026, 3, 5), dueDate = LocalDate(2026, 4, 5), categoryId = "home",
                    name = "Telefono", lastSweptMonth = "2026-03", createdOn = LocalDate(2026, 3, 14),
                ),
            )
        }
        val rows = listOf(
            BankRow(LocalDate(2026, 3, 10), -1200, "TELEFONO ABBONAMENTO", null),    // before it was created
            BankRow(LocalDate(2026, 3, 5), -2990, "TIM S.P.A.", null),               // its amount on its first due date, before it was created
        )
        assertEquals(listOf(RowStatus.READY, RowStatus.READY), StatementImport.review(d, rows, clock.today).map { it.status })
        val after = StatementImport.review(d, listOf(BankRow(LocalDate(2026, 3, 14), -1200, "TELEFONO ABBONAMENTO", null)), clock.today)
        assertEquals(RowStatus.RECURRING, after.single().status)
        // no creation day, as from a Sci-Fin backup: it reads as the first due date
        val old = d.copy(recurringExpenses = d.recurringExpenses.map { it.copy(createdOn = null) })
        assertEquals(listOf(RowStatus.RECURRING, RowStatus.RECURRING), StatementImport.review(old, rows, clock.today).map { it.status })
    }

    // SPEC:R107 SPEC:R65 SPEC:R63
    @Test fun `R107 Telefono swept on 10 March and moved on 15 March to fall due on 12 April still finds TELEFONO on 10 March and TIM -29_90 on 11 March`() {
        val c = TestClock(LocalDate(2026, 3, 1))
        var d = world().copy(recurringExpenses = emptyList())
        d = com.hazia.tesoriere.engine.Recurring.create(
            d, Fixture.recurring("tel", "29.90", Cadence.MONTHLY, firstDue = LocalDate(2026, 3, 10), categoryId = "home", name = "Telefono"), c, ids::next,
        )
        assertEquals(listOf(LocalDate(2026, 3, 10)), d.transactions.filter { it.key == "rec:tel" }.map { it.date })
        c.setDay(LocalDate(2026, 3, 15))
        val moved = d.recurringExpenses.single().let { it.copy(dueDate = LocalDate(2026, 4, 12)) }
        val (edited, _) = com.hazia.tesoriere.engine.Recurring.edit(d, moved, c.today)          // the difference is declined
        assertEquals(LocalDate(2026, 4, 12), edited.recurringExpenses.single().firstDueDate)
        val rows = listOf(
            BankRow(LocalDate(2026, 3, 11), -2990, "TIM S.P.A.", null),
            BankRow(LocalDate(2026, 3, 10), -2990, "TELEFONO ABBONAMENTO", null),
        )
        val review = StatementImport.review(edited, rows, LocalDate(2026, 3, 16))
        assertEquals(listOf(RowStatus.RECURRING, RowStatus.RECURRING), review.map { it.status })
        // a row before the sweep still is not found
        val early = StatementImport.review(edited, listOf(BankRow(LocalDate(2026, 3, 5), -2990, "TELEFONO ABBONAMENTO", null)), LocalDate(2026, 3, 16))
        assertEquals(RowStatus.READY, early.single().status)
    }

    // SPEC:R107 SPEC:R62 SPEC:R63
    @Test fun `R107 a yearly Assicurazione auto first due 15 July 2027 with shares swept on 1 October and 1 November leaves ASSICURAZIONE AUTO RCA -45_00 and BONIFICO -120_00 READY`() {
        val c = TestClock(LocalDate(2026, 9, 14))
        var d = world().copy(recurringExpenses = emptyList())
        d = com.hazia.tesoriere.engine.Recurring.create(
            d, Fixture.recurring("ins", "1,200.00", Cadence.YEARLY, firstDue = LocalDate(2027, 7, 15), categoryId = "home", name = "Assicurazione auto"), c, ids::next,
        )
        for (m in listOf(LocalDate(2026, 10, 1), LocalDate(2026, 11, 1))) {
            d = com.hazia.tesoriere.engine.Recurring.sweepOccurrences(d, "ins", m, c, ids::next).first
        }
        val shares = d.transactions.filter { it.key == "rec:ins" }
        assertEquals(listOf(cents("120.00"), cents("120.00")), shares.map { it.amount })        // the share from 14 September (SPEC R62)
        assertTrue(shares.all { it.share })
        val rows = listOf(
            BankRow(LocalDate(2026, 11, 12), -4500, "ASSICURAZIONE AUTO RCA", null),
            BankRow(LocalDate(2026, 10, 2), -12000, "BONIFICO", null),                           // the share's amount, the day after it
        )
        val review = StatementImport.review(d, rows, LocalDate(2026, 11, 14))
        assertEquals(listOf(RowStatus.READY, RowStatus.READY), review.map { it.status })
        assertTrue(review.all { it.include })
    }

    // SPEC:R107 SPEC:R65
    @Test fun `R107 -29_90 on 10 March arrives RECURRING after the swept Telefono was edited to 34_90 with the difference declined`() {
        val c = TestClock(LocalDate(2026, 3, 1))
        var d = world().copy(recurringExpenses = emptyList())
        d = com.hazia.tesoriere.engine.Recurring.create(
            d, Fixture.recurring("tel", "29.90", Cadence.MONTHLY, firstDue = LocalDate(2026, 3, 10), categoryId = "home", name = "Telefono"), c, ids::next,
        )
        c.setDay(LocalDate(2026, 3, 5))
        val (edited, offer) = com.hazia.tesoriere.engine.Recurring.edit(d, d.recurringExpenses.single().copy(amount = cents("34.90")), c.today)
        assertEquals(cents("5.00"), offer?.delta)                                       // declined: not applied
        val review = StatementImport.review(edited, listOf(BankRow(LocalDate(2026, 3, 10), -2990, "TIM S.P.A.", null)), c.today)
        assertEquals(RowStatus.RECURRING, review.single().status)
        assertFalse(review.single().include)
    }

    // SPEC:R107 SPEC:R93 SPEC:R89
    @Test fun `R107 a card's 60_00 fixed payment's bank row on its due date arrives READY, so the payoff can lower the card`() {
        val linked = world().let { w ->
            w.copy(
                accounts = w.accounts.map { it.copy(linkedRecurringExpenseId = "orbita-pay") },
                recurringExpenses = w.recurringExpenses + Fixture.recurring(
                    "orbita-pay", "60.00", Cadence.MONTHLY, firstDue = LocalDate(2026, 2, 18), categoryId = "home",
                    name = "Orbita card payment", linkedAccountId = "orbita", lastSweptMonth = "2026-03",
                ),
            )
        }
        val review = StatementImport.review(linked, march(), clock.today)
        assertEquals(RowStatus.READY, review.row("PAGAMENTO CARTA ORBITA").status)
        assertTrue(review.row("PAGAMENTO CARTA ORBITA").include)
    }

    // SPEC:R107 SPEC:R93
    @Test fun `R107 ORBITA CARD PAYMENT 0412 -60_00, holding the name of the card's fixed payment, arrives READY`() {
        val linked = world().let { w ->
            w.copy(
                accounts = w.accounts.map { it.copy(linkedRecurringExpenseId = "orbita-pay") },
                recurringExpenses = w.recurringExpenses + Fixture.recurring(
                    "orbita-pay", "60.00", Cadence.MONTHLY, firstDue = LocalDate(2026, 2, 18), categoryId = "home",
                    name = "Orbita card payment", linkedAccountId = "orbita", lastSweptMonth = "2026-03",
                ),
            )
        }
        val row = BankRow(LocalDate(2026, 3, 25), -6000, "ORBITA CARD PAYMENT 0412", null)
        val review = StatementImport.review(linked, listOf(row), clock.today).single()
        assertEquals(RowStatus.READY, review.status)
        assertTrue(review.include)
        // the same name on an expense linked to no card still finds it
        val unlinked = linked.copy(recurringExpenses = linked.recurringExpenses.map { it.copy(linkedAccountId = null) })
        assertEquals(RowStatus.RECURRING, StatementImport.review(unlinked, listOf(row), clock.today).single().status)
    }

    // SPEC:R107 SPEC:R89
    @Test fun `R107 a -60_00 payoff of a Loan linked to nothing keeps Confirm off until a category is picked, then counts 60_00 in it`() {
        val d = world().let { w -> w.copy(accounts = w.accounts + Account("loan", Fixture.PROFILE, "Bike loan", type = AccountType.LOAN, balance = cents("900.00"))) }
        val review = StatementImport.review(d, listOf(BankRow(LocalDate(2026, 3, 18), -6000, "RATA PRESTITO", null)), clock.today)
            .map { it.copy(payoffAccountId = "loan") }
        assertFalse(StatementImport.canConfirm(d, review))
        val picked = review.map { it.copy(payoffCategoryId = "books") }
        assertTrue(StatementImport.canConfirm(d, picked))
        val after = StatementImport.confirm(d, picked, clock, ids::next)
        assertEquals(cents("840.00"), after.accounts.single { it.id == "loan" }.balance)
        assertEquals(cents("60.00"), Spent.of(after, "books", "2026-03"))
        // from nowhere it needs no category
        assertTrue(StatementImport.canConfirm(d, review.map { it.copy(payoffFrom = Accounts.PayoffFrom.None) }))
        // a Loan with a category of its own needs none picked
        val own = d.copy(accounts = d.accounts.map { if (it.id == "loan") it.copy(loanCategoryId = "books") else it })
        assertTrue(StatementImport.canConfirm(own, review))
        assertEquals(cents("60.00"), Spent.of(StatementImport.confirm(own, review, clock, ids::next), "books", "2026-03"))
        // a picked category that does not exist keeps Confirm off, never passed over for the Loan's own
        assertFalse(StatementImport.canConfirm(own, review.map { it.copy(payoffCategoryId = "nowhere") }))
    }

    // SPEC:R107 SPEC:R89 SPEC:R67
    @Test fun `R107 a -500_00 charge to a Loan on 1 March is recorded before its -100_00 payoff of 20 March, so the payoff is a transfer and Confirm is on with no category`() {
        val d = world().let { w -> w.copy(accounts = w.accounts + Account("loan", Fixture.PROFILE, "Bike loan", type = AccountType.LOAN, balance = cents("900.00"))) }
        val rows = listOf(
            BankRow(LocalDate(2026, 3, 1), -50000, "NEGOZIO BICI ORSA", null),
            BankRow(LocalDate(2026, 3, 20), -10000, "RATA PRESTITO", null),
        )
        val review = StatementImport.review(d, rows, clock.today)
            .file("NEGOZIO BICI ORSA", "books" to "500.00", source = PaymentSource.Account("loan"))
            .payoff("RATA PRESTITO", "loan")
        assertEquals(listOf(20, 1), review.map { it.row.date.day })                          // the review lists the payoff first
        assertTrue(StatementImport.canConfirm(d, review))
        val after = StatementImport.confirm(d, review, clock, ids::next)
        assertEquals(cents("1,300.00"), after.accounts.single { it.id == "loan" }.balance)
        assertEquals(cents("500.00"), Spent.of(after, "books", "2026-03"))                    // the charge only
        assertNull(after.transactions.single { it.key == "savings" && it.amount == cents("-100.00") }.categoryId)
    }

    // SPEC:R107 SPEC:R50 SPEC:R54 SPEC:R89
    @Test fun `R107 1,450_00 sent into a Goal holding 110_00 on 7 March pays -200_00 from it on 8 March with no warning and a -300_00 card payoff from it on 9 March`() {
        val d = world()
        val rows = listOf(
            BankRow(LocalDate(2026, 3, 7), 145000, "STIPENDIO MARZO", null),
            BankRow(LocalDate(2026, 3, 8), -20000, "PRELIEVO SPORTELLO 0412", null),
            BankRow(LocalDate(2026, 3, 9), -30000, "PAGAMENTO CARTA ORBITA", null),
        )
        val filed = StatementImport.review(d, rows, clock.today)
            .map { if (it.row.amount > 0) it.copy(incomingGoalId = "cash") else it }
            .file("PRELIEVO SPORTELLO 0412", "groc" to "200.00", source = PaymentSource.Goal("cash"))
            .payoff("PAGAMENTO CARTA ORBITA", "orbita")
            .map { if (it.payoffAccountId != null) it.copy(payoffFrom = Accounts.PayoffFrom.Goal("cash")) else it }
        assertTrue(StatementImport.canConfirm(d, filed))
        assertEquals(listOf<PaymentNotice?>(null, null, null), StatementImport.rowNotices(d, filed))
        val after = StatementImport.confirm(d, filed, clock, ids::next)
        assertEquals(cents("1,060.00"), after.goals.single().saved)              // 110.00 + 1,450.00 - 200.00 - 300.00
        assertEquals(cents("1,000.00"), after.savings.amount)
        assertEquals(0L, after.accounts.single().balance)
    }

    // SPEC:R107, R50
    @Test fun `R107 the 1,450_00 salary sent on into a Goal holding 110_00 leaves it 1,560_00 and Savings 885_30`() {
        val d = world()
        val filed = marchFiled(d).map { if (it.row.description == "STIPENDIO MARZO") it.copy(incomingGoalId = "cash") else it }
        assertTrue(StatementImport.canConfirm(d, filed))
        assertEquals(ConfirmSummary(4, cents("114.70"), cents("1,450.00")), StatementImport.confirmSummary(d, filed))
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
    @Test fun `R107 rows of 14_00 on 7 March and 100_00 on 8 March from a Goal holding 110_00 warn on the later and leave the Goal 0_00 and Savings 996_00, the 100_00 alone 10_00`() {
        val d = world()
        val rows = listOf(
            BankRow(LocalDate(2026, 3, 8), -10000, "PRELIEVO SPORTELLO 0412", null),
            BankRow(LocalDate(2026, 3, 7), -1400, "POS EDICOLA CENTRALE", null),
        )
        // the review lists the 8 March row first; confirm takes the 7 March row first, oldest first
        val both = StatementImport.review(d, rows, clock.today)
            .file("PRELIEVO SPORTELLO 0412", "groc" to "100.00", source = PaymentSource.Goal("cash"))
            .file("POS EDICOLA CENTRALE", "books" to "14.00", source = PaymentSource.Goal("cash"))
        assertTrue(StatementImport.canConfirm(d, both))
        assertEquals(listOf(PaymentNotice.GOAL_SHORT, null), StatementImport.rowNotices(d, both))
        val paidBoth = StatementImport.confirm(d, both, clock, ids::next)
        assertEquals(0L, paidBoth.goals.single().saved)
        assertEquals(cents("996.00"), paidBoth.savings.amount)
        assertEquals(cents("4.00"), paidBoth.payments.single { it.payee == "PRELIEVO SPORTELLO 0412" }.fromSavings)
        val first = both.tick("POS EDICOLA CENTRALE", false)
        assertEquals(listOf<PaymentNotice?>(null, null), StatementImport.rowNotices(d, first))
        val after = StatementImport.confirm(d, first, clock, ids::next)
        assertEquals(cents("10.00"), after.goals.single().saved)
        assertEquals(cents("1,000.00"), after.savings.amount)
        assertEquals(cents("100.00"), Spent.of(after, "groc", "2026-03"))
    }

    // SPEC:R107 SPEC:R21 SPEC:R26
    @Test fun `R107 RIMBORSO CALZATURE ORSA 25_00 filed as a refund under Household records -25_00 with its keys and lowers March's Household by 25_00`() {
        val d = world()
        val row = BankRow(LocalDate(2026, 3, 20), 2500, "RIMBORSO CALZATURE ORSA", "TSRF0001")
        val review = StatementImport.review(d, listOf(row), clock.today)
        assertEquals(RowStatus.INCOMING, review.single().status)
        val refund = review.map { it.copy(draft = StatementImport.draft(it.row, listOf(Part("home", -2500)))) }
        assertTrue(StatementImport.canConfirm(d, refund))
        assertEquals(ConfirmSummary(1, 0, cents("25.00")), StatementImport.confirmSummary(d, refund))
        val after = StatementImport.confirm(d, refund, clock, ids::next)
        val p = after.payments.single()
        assertEquals(listOf(-2500L, -2500L), listOf(p.total, Spent.of(after, "home", "2026-03")))
        assertEquals(PaymentSource.Savings, p.source)
        assertEquals(StatementImport.keys(row), p.bankKeys)
        assertEquals(cents("1,025.00"), after.savings.amount)
        assertTrue(after.transactions.isEmpty())                                   // no income row
        assertEquals(RowStatus.DUPLICATE, StatementImport.review(after, listOf(row), clock.today).single().status)
        // to the card, it lowers the card; parts that do not add up, or a refund also sent into a Goal, keep Confirm off
        val toCard = review.map { it.copy(draft = StatementImport.draft(it.row, listOf(Part("home", -2500)), PaymentSource.Account("orbita"))) }
        assertEquals(cents("275.00"), StatementImport.confirm(d, toCard, clock, ids::next).accounts.single().balance)
        assertFalse(StatementImport.canConfirm(d, review.map { it.copy(draft = StatementImport.draft(it.row, listOf(Part("home", -2000)))) }))
        assertFalse(StatementImport.canConfirm(d, refund.map { it.copy(incomingGoalId = "cash") }))
    }

    // SPEC:R111 SPEC:R107 SPEC:R26
    @Test fun `R111 a -60_00 statement payoff undone arrives DUPLICATE and unticked on the next import, and ticked and filed again it is recorded`() {
        val d = world()
        val after = StatementImport.confirm(d, marchFiled(d), clock, ids::next)
        assertEquals(cents("240.00"), after.accounts.single().balance)
        val group = requireNotNull(after.transactions.single { it.key == "acct:orbita" }.groupId)
        val undone = Accounts.undoQuickAdd(after, group)
        assertEquals(cents("300.00"), undone.accounts.single().balance)
        val again = StatementImport.review(undone, march(), clock.today).row("PAGAMENTO CARTA ORBITA")
        assertEquals(RowStatus.DUPLICATE, again.status)
        assertFalse(again.include)
        val ticked = listOf(again.copy(include = true, payoffAccountId = "orbita"))
        assertEquals(cents("240.00"), StatementImport.confirm(undone, ticked, clock, ids::next).accounts.single().balance)
    }

    // SPEC:R109 SPEC:R107 SPEC:R21
    @Test fun `R109 a 25_00 statement refund to a card whose charges are not logged carries CHARGES_NOT_LOGGED, as a payment charged to it does`() {
        val d = world().let { w -> w.copy(accounts = w.accounts.map { it.copy(chargesNotLogged = true, payoffCategoryId = "home") }) }
        val rows = listOf(
            BankRow(LocalDate(2026, 3, 20), 2500, "RIMBORSO CALZATURE ORSA", null),
            BankRow(LocalDate(2026, 3, 19), -1240, "POS MERCATO VERDE 0412", null),
        )
        val filed = StatementImport.review(d, rows, clock.today)
            .map { if (it.row.amount > 0) it.copy(draft = StatementImport.draft(it.row, listOf(Part("home", -2500)), PaymentSource.Account("orbita"))) else it }
            .file("POS MERCATO VERDE 0412", "groc" to "12.40", source = PaymentSource.Account("orbita"))
        assertTrue(StatementImport.canConfirm(d, filed))
        assertEquals(listOf(PaymentNotice.CHARGES_NOT_LOGGED, PaymentNotice.CHARGES_NOT_LOGGED), StatementImport.rowNotices(d, filed))
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

    /** The rows paired since, worked out as before the index (SPEC R119): every payment against every row. */
    private fun pairedSinceByScan(d: ProfileData, rows: List<ReviewRow>): Set<Int> {
        val taken = HashSet<Int>()
        for (p in d.payments) {
            val held = p.bankKeys ?: continue
            val open = rows.indices.filter { it !in taken && rows[it].status != RowStatus.DUPLICATE && StatementImport.sameRow(rows[it].keys, held) }
            val pick = open.firstOrNull { rows[it].pairPaymentId == p.id } ?: open.firstOrNull { rows[it].keys == held } ?: open.firstOrNull() ?: continue
            taken += pick
        }
        return taken
    }

    // SPEC:R119 SPEC:R26
    @OptIn(ExperimentalTime::class)
    @Test fun `R119 2,000 payments against a 200-row statement give the rows paired since as every payment against every row does`() {
        val day = LocalDate(2026, 3, 1)
        // 200 rows: FITID rows, twin coffees with no FITID, rows whose FITIDs share one synthetic key, some arrived DUPLICATE
        val rows = (0 until 200).map { i ->
            val date = LocalDate(2026, 3, 1 + i % 28)
            val fitid = when (i % 4) { 0 -> "F-$i"; 1 -> null; 2 -> "T-${i / 8}-${i % 8}"; else -> null }
            val amount = -(100L + if (i % 4 == 1) 350L else i.toLong())
            val row = BankRow(date, amount, if (i % 4 == 2) "TWIN SHOP" else "SHOP ${i % 4 == 1}", fitid)
            ReviewRow(row, StatementImport.keys(row), "shop", if (i % 13 == 0) RowStatus.DUPLICATE else RowStatus.READY, true, null, null,
                if (i % 17 == 0) "pay-${i * 3}" else null)
        }
        // 2,000 payments: most hold keys of no row, some a row's keys, some a twin's, some a FITID of their own over a row's synthetic key
        val payments = (0 until 2000).map { j ->
            val keys = when {
                j % 10 == 0 -> rows[(j / 10) % 200].keys
                j % 10 == 3 -> rows[(j * 7) % 200].keys.copy(fitid = null)
                j % 10 == 6 -> rows[(j * 3) % 200].keys.copy(fitid = "X-$j")
                j % 10 == 9 -> null
                else -> BankKeys("N-$j", "syn:2025-01-01|-${j}.00|elsewhere")
            }
            Payment("pay-$j", Fixture.PROFILE, day, Instant.fromEpochMilliseconds(j.toLong()), 100, PaymentSource.Savings, "shop", listOf(Part("groc", 100)), bankKeys = keys)
        }
        val d = profile().copy(payments = payments)
        val expected = pairedSinceByScan(d, rows)
        assertTrue(expected.size > 20, "the case pairs rows: ${expected.size}")
        assertEquals(expected, StatementImport.pairedSince(d, rows))
    }
    // --- R26: twins told apart (owner ruling of 2026-11-26) ---

    private fun coffee(fitid: String?) = BankRow(LocalDate(2026, 3, 11), -cents("4.50"), "BAR CENTRALE", fitid)

    /** [rows] reviewed against [d], every row not `DUPLICATE` ticked under Groceries, and confirmed. */
    private fun recordAll(d: ProfileData, vararg rows: BankRow): ProfileData {
        val review = StatementImport.review(d, rows.toList(), clock.today).map {
            if (it.status == RowStatus.DUPLICATE || it.row.amount > 0) it
            else it.copy(include = true, draft = StatementImport.draft(it.row, listOf(Part("groc", -it.row.amount))))
        }
        return StatementImport.confirm(d, review, clock, ids::next)
    }

    // SPEC:R26 SPEC:R107
    @Test fun `R26 F1 recorded, a later statement's F2 of the same day, amount and text arrives READY and records once`() {
        val d1 = recordAll(world(), coffee("F1"))
        val later = StatementImport.review(d1, listOf(coffee("F2")), clock.today).single()
        assertEquals(RowStatus.READY, later.status)
        assertTrue(later.include)
        val d2 = recordAll(d1, coffee("F2"))
        assertEquals(listOf("F1", "F2"), d2.payments.map { it.bankKeys?.fitid })
        assertEquals(cents("991.00"), d2.savingsAmount)
        // a statement holding both: F1 is recorded, F2 too now
        assertEquals(listOf(RowStatus.DUPLICATE, RowStatus.DUPLICATE), StatementImport.review(d2, listOf(coffee("F1"), coffee("F2")), clock.today).map { it.status })
        val overlap = StatementImport.review(d1, listOf(coffee("F1"), coffee("F2")), clock.today)
        assertEquals(RowStatus.DUPLICATE, overlap.single { it.row.fitid == "F1" }.status)
        assertEquals(RowStatus.READY, overlap.single { it.row.fitid == "F2" }.status)
    }

    // SPEC:R26 SPEC:R107
    @Test fun `R26 of no-FITID twins with one recorded, a statement holding both has one DUPLICATE and one new, and records only that one`() {
        val d1 = recordAll(world(), coffee(null))
        val review = StatementImport.review(d1, listOf(coffee(null), coffee(null)), clock.today)
        assertEquals(1, review.count { it.status == RowStatus.DUPLICATE })
        assertEquals(1, review.count { it.status == RowStatus.READY })
        val d2 = recordAll(d1, coffee(null), coffee(null))
        assertEquals(2, d2.payments.size, "the payment held at the review stands for the DUPLICATE row, not the new one")
        assertEquals(cents("991.00"), d2.savingsAmount)
        assertEquals(listOf(RowStatus.DUPLICATE, RowStatus.DUPLICATE), StatementImport.review(d2, listOf(coffee(null), coffee(null)), clock.today).map { it.status })
    }

    // SPEC:R26 SPEC:R107
    @Test fun `R26 a row of money into Savings, recorded with no payment, arrives DUPLICATE on the next import`() {
        val wage = BankRow(LocalDate(2026, 3, 27), cents("1,450.00"), "STIPENDIO MARZO", null)
        val d1 = recordAll(world(), wage)
        assertEquals(cents("2,450.00"), d1.savingsAmount)
        assertTrue(d1.payments.isEmpty())
        assertEquals(RowStatus.DUPLICATE, StatementImport.review(d1, listOf(wage), clock.today).single().status)
        // and so does its twin when both were recorded, while a third of the same day arrives new
        val d2 = recordAll(d1, wage, wage)
        assertEquals(listOf(RowStatus.DUPLICATE, RowStatus.DUPLICATE, RowStatus.INCOMING), StatementImport.review(d2, listOf(wage, wage, wage), clock.today).map { it.status })
    }

    // --- R107: a review left open while the same rows are recorded elsewhere ---

    // SPEC:R107 SPEC:R26
    @Test fun `R107 two reviews of one +100_00 row, both confirmed, add it to Savings once`() {
        val wage = BankRow(LocalDate(2026, 3, 27), cents("100.00"), "BONIFICO", null)
        val d = world()
        val r1 = StatementImport.review(d, listOf(wage), clock.today)
        val r2 = StatementImport.review(d, listOf(wage), clock.today)
        val a = StatementImport.confirm(d, r2, clock, ids::next)
        assertEquals(cents("1,100.00"), a.savingsAmount)
        assertEquals(0, StatementImport.confirmSummary(a, r1).count, "the open review names nothing left to record")
        assertEquals(cents("1,100.00"), StatementImport.confirm(a, r1, clock, ids::next).savingsAmount)
    }

    // SPEC:R107 SPEC:R89 SPEC:R26
    @Test fun `R107 two reviews of one -50_00 card payoff, both confirmed, pay the card once`() {
        val po = BankRow(LocalDate(2026, 3, 27), -cents("50.00"), "PAGAMENTO ORBITA", null)
        val d = world()
        fun open() = StatementImport.review(d, listOf(po), clock.today).map { it.copy(include = true, payoffAccountId = "orbita") }
        val r1 = open(); val r2 = open()
        val a = StatementImport.confirm(d, r2, clock, ids::next)
        val b = StatementImport.confirm(a, r1, clock, ids::next)
        assertEquals(cents("250.00"), b.accounts.single().balance)
        assertEquals(cents("950.00"), b.savingsAmount)
    }

    // SPEC:R107 SPEC:R26 SPEC:R119
    @Test fun `R107 a review of two coffees left open while another records one of them records only the other`() {
        val d = world()
        fun ticked(rows: List<ReviewRow>) = rows.map { it.copy(include = true, draft = StatementImport.draft(it.row, listOf(Part("groc", cents("4.50"))))) }
        val r1 = ticked(StatementImport.review(d, listOf(coffee(null), coffee(null)), clock.today))
        val r2 = StatementImport.review(d, listOf(coffee(null), coffee(null)), clock.today)
        val a = StatementImport.confirm(d, ticked(r2).mapIndexed { i, r -> if (i == 0) r else r.copy(include = false) }, clock, ids::next)
        assertEquals(1, StatementImport.confirmSummary(a, r1).count)
        val b = StatementImport.confirm(a, r1, clock, ids::next)
        assertEquals(2, b.payments.size)
        assertEquals(cents("9.00"), Spent.of(b, "groc", "2026-03"))
        // one coffee in both reviews: recorded once
        val c = StatementImport.confirm(StatementImport.confirm(d, ticked(r2.take(1)), clock, ids::next), ticked(r1.take(1)), clock, ids::next)
        assertEquals(cents("4.50"), Spent.of(c, "groc", "2026-03"))
    }

    // --- R26: keys kept by the deduplicating code before every row kept its own ---

    // SPEC:R26
    @Test fun `R26 an old list fit F1, S, fit F2, T reads F2 alone, so a CSV row T and an OFX row T of F7 arrive recorded`() {
        val shop = BankRow(LocalDate(2026, 3, 18), -cents("12.00"), "SHOP", null)
        val s = StatementImport.keys(coffee("F1")).synKey
        val t = StatementImport.keys(shop).synKey
        val old = world().let { it.copy(importState = it.importState.copy(importedKeys = listOf("fit:F1", s, "fit:F2", t))) }
        assertEquals(RowStatus.DUPLICATE, StatementImport.review(old, listOf(shop), clock.today).single().status)
        assertEquals(RowStatus.DUPLICATE, StatementImport.review(old, listOf(shop.copy(fitid = "F7")), clock.today).single().status)
        // with a payment holding F2 and T, T is F2's: the OFX row of F7 is a row of its own
        val held = old.copy(payments = listOf(Payment("pay-2", Fixture.PROFILE, shop.date, Instant.fromEpochMilliseconds(0), 1200, PaymentSource.Savings,
            "SHOP", listOf(Part("groc", 1200)), bankKeys = BankKeys("F2", t))))
        assertEquals(RowStatus.READY, StatementImport.review(held, listOf(shop.copy(fitid = "F7")), clock.today).single().status)
    }

    // SPEC:R26 SPEC:R107
    @Test fun `R26 an OFX row of money into Savings with a FITID, recorded with no payment, keeps its synthetic key as its own`() {
        val gift = BankRow(LocalDate(2026, 3, 27), cents("20.00"), "REGALO", "G1")
        val d1 = recordAll(world(), gift)
        assertTrue(d1.payments.isEmpty())
        assertEquals(RowStatus.INCOMING, StatementImport.review(d1, listOf(gift.copy(fitid = "G2")), clock.today).single().status, "a twin with its own FITID is new")
        assertEquals(RowStatus.DUPLICATE, StatementImport.review(d1, listOf(gift.copy(fitid = null)), clock.today).single().status, "the CSV row of it is not")
        assertEquals(RowStatus.DUPLICATE, StatementImport.review(d1, listOf(gift), clock.today).single().status)
    }
}
