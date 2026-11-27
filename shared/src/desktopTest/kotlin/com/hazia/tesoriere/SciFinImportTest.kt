@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere

import com.hazia.tesoriere.engine.Backup
import com.hazia.tesoriere.engine.BackupCrypto
import com.hazia.tesoriere.engine.BackupProblem
import com.hazia.tesoriere.engine.BackupResult
import com.hazia.tesoriere.engine.Reports
import com.hazia.tesoriere.engine.SciFinImport
import com.hazia.tesoriere.engine.SciFinProfileCounts
import com.hazia.tesoriere.engine.SciFinRead
import com.hazia.tesoriere.engine.SciFinRefusal
import com.hazia.tesoriere.engine.TransactionKey
import com.hazia.tesoriere.model.IncomeType
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.Spent
import com.hazia.tesoriere.statements.RowStatus
import com.hazia.tesoriere.statements.StatementImport
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * Ported from the import half of Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/ImportBackupTest.kt
 * at 2892110: its cases of a file with no version, of absent fields taking their defaults, and of a
 * file refused whole, read here as Sci-Fin's files (SPEC R19, R134). Changed: refusals are codes, not
 * sentences; the appearance repairs are not ported (SPEC R16); the rows' mapping, new ids, a category
 * per recurring expense and the import memory are Tesoriere's (SPEC R135-R138). The fixtures under
 * `resources/scifin-backups/` are synthetic, one Profile each, with the fields each Sci-Fin schema
 * had; `schema-10-encrypted.json` was sealed once by Sci-Fin's own BackupCrypto at 2892110.
 */
class SciFinImportTest {
    private val password = "violet staircase 42"

    private fun text(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/scifin-backups/$name")) { "missing fixture $name" }.use { it.readBytes().decodeToString() }

    private fun ok(text: String, password: String? = null): SciFinRead.Ok = assertIs<SciFinRead.Ok>(SciFinImport.read(text, password))

    private fun refused(text: String, password: String? = null): SciFinRefusal =
        assertIs<SciFinRead.Refused>(SciFinImport.read(text, password)).reason

    /** [read] with each recurring expense given its Profile's category named Fun (SPEC R137). */
    private fun confirmed(read: SciFinRead.Ok): SciFinRead.Ok = read.profiles.fold(read) { r, p ->
        p.recurringExpenses.fold(r) { rr, e -> SciFinImport.setExpenseCategory(rr, e.id, p.categories.first { it.name == "Fun" }.id) }
    }

    /** The schema-10 fixture added beside a fresh Profile with the file's own Profile id. */
    private fun added(read: SciFinRead.Ok = ok(text("schema-10.json"))): Pair<ProfileData, ProfileData> {
        val existing = ProfileData.fresh("sf-p-1", "Main")
        val all = SciFinImport.addTo(listOf(existing), confirmed(read), Ids("n")::next)
        assertEquals(2, all.size)
        assertSame(existing, all[0])
        return all[0] to all[1]
    }

    /** The schema-10 fixture with a second Profile: its own, with each id and its name made another. */
    private fun twoProfiles(): String {
        val t = text("schema-10.json")
        val end = t.lastIndexOf(']')
        val second = t.substring(t.indexOf("\"profiles\": [") + "\"profiles\": [".length, end)
            .replace(Regex("\\b(sf-p-1|g-trip|c-groc|c-fun|r-rent|a-card|t-\\d\\d|s-1)\\b")) { it.value + "-b" }
            .replace("Casa Prova", "Casa Seconda")
        return t.substring(0, end) + ", " + second + t.substring(end)
    }

    /** [t] with the first match of [pattern] replaced by [with], taken as written. */
    private fun swap(t: String, pattern: String, with: String): String {
        val m = requireNotNull(Regex(pattern).find(t)) { "no $pattern" }
        return t.replaceRange(m.range, with)
    }

    private fun counts(name: String = "Casa Prova") =
        SciFinProfileCounts(name, goals = 1, categories = 2, recurringExpenses = 1, accounts = 1, ledgerRows = 10, savings = cents("1,250.00"))

    private fun ProfileData.countsNow() = SciFinProfileCounts(
        profile.name, goals.size, categories.size, recurringExpenses.size, accounts.size, transactions.size + payments.size, savings.amount,
    )

    // SPEC:R19 SPEC:R134
    @Test fun `R19 every schema 1 to 10 reads with the review's counts`() {
        for (v in 1..10) {
            val name = "schema-%02d.json".format(v)
            val read = ok(text(name))
            assertEquals(v, read.version, name)
            assertEquals(listOf(counts()), read.review.profiles, name)
            val s = read.profiles.single().settings
            assertEquals(if (v >= 3) 12 else 6, s.trendMonths, name)
            assertEquals(if (v >= 9) IncomeType.HOURLY else IncomeType.SALARIED, s.incomeType, name)
            assertEquals(if (v >= 9) 1 else 0, read.profiles.single().shifts.size, name)
            assertEquals(if (v >= 10) "it" else null, s.language, name)
            // added, the new Profile holds what the review showed (SPEC R19)
            val all = SciFinImport.addTo(emptyList(), confirmed(read), Ids("n$v")::next)
            assertEquals(listOf(counts()), all.map { it.countsNow() }, name)
        }
    }

    // SPEC:R136 SPEC:R130
    @Test fun `R136 a Sci-Fin Profile whose file says notifications were granted reads them as not yet answered, none`() {
        assertTrue("\"notificationPermissionGranted\": true" in text("schema-10.json"))
        for (v in 1..10) {
            val name = "schema-%02d.json".format(v)
            assertNull(ok(text(name)).profiles.single().settings.notificationPermissionGranted, name)
        }
        assertNull(added().second.settings.notificationPermissionGranted)
    }

    // SPEC:R134 SPEC:R131
    @Test fun `R134 a schema-1 file with no version reads as Sci-Fin version 1, which Tesoriere's own reader refuses`() {
        val t = text("schema-01.json")
        assertFalse("\"schemaVersion\"" in t || "\"app\"" in t)
        assertEquals(1, ok(t).version)
        assertEquals(BackupProblem.OTHER_APP, assertIs<BackupResult.Refused>(Backup.validate(t)).problem)
    }

    // SPEC:R134
    @Test fun `R134 a Profile of an id, a name and settings reads with the defaults, whatever its appearance held`() {
        val read = ok("""{"profiles":[{"id":"p","name":"Mine","settings":{"profileId":"p","theme":"neon","mode":"sepia"}}]}""")
        assertEquals(1, read.version)
        val p = read.profiles.single()
        assertNull(p.savings.amount)
        assertTrue(p.goals.isEmpty() && p.transactions.isEmpty() && p.importState.importedKeys.isEmpty())
        assertEquals("p", p.settings.profileId)
        val bare = ok("""{"app":"sci-fin","schemaVersion":10,"profiles":[{"id":"p","name":"Mine","settings":{}}]}""").profiles.single()
        assertEquals("EUR", bare.settings.currency)
        assertEquals("p", bare.importState.profileId)
    }

    // SPEC:R19 SPEC:R134 SPEC:R133
    @Test fun `R19 an encrypted schema-10 file opens with its password`() {
        val sealed = text("schema-10-encrypted.json")
        assertFalse("Casa Prova" in sealed)
        val locked = assertIs<com.hazia.tesoriere.engine.LockedBackup>(BackupCrypto.locked(sealed))
        assertEquals("sci-fin", locked.app)
        assertEquals("the stair colour", locked.hint)
        val opened = ok(sealed, password)
        // the sealed copy was made once, by Sci-Fin's own code, before the plain fixtures' payee took
        // its made-up name; it holds the old one, so the plain file is read with that name put back
        val plain = text("schema-10.json").replace("bar lunafinta", "bar centrale").replace("BAR LUNAFINTA", "BAR CENTRALE")
        assertEquals(ok(plain), opened)
    }

    // SPEC:R19 SPEC:R134
    @Test fun `R19 a schema-11 file is refused and nothing is written`() {
        assertEquals(SciFinRefusal.NEWER_SCHEMA, refused(text("schema-11.json")))
        val huge = text("schema-10.json").replace("\"schemaVersion\": 10", "\"schemaVersion\": 99999999999999999999")
        assertEquals(SciFinRefusal.NEWER_SCHEMA, refused(huge))
    }

    // SPEC:R19 SPEC:R136
    @Test fun `R19 two Sci-Fin Profiles arrive as two new Profiles beside an existing one`() {
        val read = ok(twoProfiles())
        assertEquals(listOf(counts(), counts("Casa Seconda")), read.review.profiles)
        val existing = ProfileData.fresh("sf-p-1", "Main")
        val all = SciFinImport.addTo(listOf(existing), confirmed(read), Ids("n")::next)
        assertEquals(listOf("Main", "Casa Prova", "Casa Seconda"), all.map { it.profile.name })
        assertSame(existing, all[0])
        assertEquals(3, all.map { it.profileId }.toSet().size)
        assertTrue(all.drop(1).none { it.profileId == "sf-p-1" || it.profileId == "sf-p-1-b" })
        assertEquals(listOf(counts(), counts("Casa Seconda")), all.drop(1).map { it.countsNow() })
    }

    // SPEC:R134
    @Test fun `R134 a file of another app is refused`() {
        assertEquals(SciFinRefusal.OTHER_APP, refused(text("other-app.json")))
        val tesoriere = Backup.encode(Backup.build(listOf(Fixture.profile()), emptyList(), Fixture.PROFILE, null, LocalDate(2026, 11, 26)))
        assertEquals(SciFinRefusal.OTHER_APP, refused(tesoriere))
        // an envelope of Tesoriere's, refused before any password is asked for
        val sealedTesoriere = BackupCrypto.encrypt(tesoriere, password, "the stair colour", LocalDate(2026, 11, 26), iterations = 100_000)
        assertEquals(SciFinRefusal.OTHER_APP, refused(sealedTesoriere))
        // Sci-Fin's envelope around Tesoriere's file
        val inside = BackupCrypto.encrypt(tesoriere, password, "the stair colour", LocalDate(2026, 11, 26), appTag = "sci-fin", iterations = 100_000)
        assertEquals(SciFinRefusal.OTHER_APP, refused(inside, password))
    }

    // SPEC:R134
    @Test fun `R134 text that is not a JSON object is NOT_A_BACKUP`() {
        for (t in listOf("{not json", "[1, 2]", "\"sci-fin\"", "")) assertEquals(SciFinRefusal.NOT_A_BACKUP, refused(t), t)
    }

    // SPEC:R134
    @Test fun `R134 an encrypted file opened with no password is PASSWORD_NEEDED`() {
        assertEquals(SciFinRefusal.PASSWORD_NEEDED, refused(text("schema-10-encrypted.json")))
        assertEquals(SciFinRefusal.PASSWORD_NEEDED, refused(text("schema-10-encrypted.json"), ""))
    }

    // SPEC:R134 SPEC:R133
    @Test fun `R134 an encrypted file opened with violet staircase 4, or with its hint changed, is WRONG_PASSWORD`() {
        val sealed = text("schema-10-encrypted.json")
        assertEquals(SciFinRefusal.WRONG_PASSWORD, refused(sealed, "violet staircase 4"))
        assertEquals(SciFinRefusal.WRONG_PASSWORD, refused(sealed.replace("the stair colour", "the stair color"), password))
    }

    // SPEC:R134 SPEC:R133
    @Test fun `R134 an encrypted file of envelope version 2 is NEWER_FORMAT`() {
        val sealed = text("schema-10-encrypted.json").replace("\"encrypted\": 1", "\"encrypted\": 2")
        assertEquals(SciFinRefusal.NEWER_FORMAT, refused(sealed, password))
    }

    // SPEC:R134
    @Test fun `R134 a damaged Sci-Fin file is DAMAGED and never throws`() {
        fun file(version: String = "10", profiles: String = """[{"id":"p","name":"Mine","settings":{"profileId":"p"}}]""") =
            """{"app":"sci-fin","schemaVersion":$version,"profiles":$profiles}"""
        fun profile(extra: String) = """[{"id":"p","name":"Mine","settings":{"profileId":"p"},$extra}]"""
        val goal = """{"id":"g","profileId":"p","name":"Trip","saved":100}"""
        val cases = listOf(
            file(version = "\"ten\""), file(version = "0"), file(version = "-3"), file(version = "1.5"),
            file(version = "-99999999999999999999"),
            file(profiles = "[]"), """{"app":"sci-fin","schemaVersion":10}""", file(profiles = """[{"name":"x"}]"""),
            file(profiles = """[{"id":"p"}]"""), file(profiles = """[{"id":"","name":"Mine"}]"""), file(profiles = "\"oops\""),
            file(profiles = profile("\"goals\":\"oops\"")), file(profiles = profile("\"goals\":null")),
            file(profiles = profile("\"savings\":\"lots\"")), file(profiles = profile("\"savings\":12.5")),
            file(profiles = profile("\"savings\":99999999999999999999")),
            file(profiles = profile("\"goals\":[$goal,$goal]")),
            file(profiles = profile("\"goals\":[{\"id\":\"g\",\"profileId\":\"p\",\"name\":\"Trip\",\"mode\":\"sideways\"}]")),
            file(profiles = profile("\"transactions\":[{\"id\":\"t\",\"profileId\":\"p\",\"date\":\"2026-03-03\",\"loggedAt\":0,\"amount\":100,\"key\":\"bank:x\",\"targetLabel\":\"x\"}]")),
            file(profiles = profile("\"transactions\":[{\"id\":\"t\",\"profileId\":\"p\",\"date\":\"2026-02-30\",\"loggedAt\":0,\"amount\":100,\"key\":\"savings\",\"targetLabel\":\"x\"}]")),
            """{"app":"sci-fin","schemaVersion":10,"profiles":[{"id":"p","name":"Mine","settings":null}]}""",
            """{"app":"sci-fin","schemaVersion":10,"profiles":[{"id":"p","name":"Mine","settings":{},"importState":[]}]}""",
            // goal ids are global: one in two Profiles is damage
            file(profiles = """[{"id":"p","name":"A","settings":{},"goals":[$goal]},{"id":"q","name":"B","settings":{},"goals":[{"id":"g","profileId":"q","name":"Trip"}]}]"""),
            // native Sci-Fin always wrote settings; its web prototype nested the records under `data`
            file(profiles = """[{"id":"p","name":"Mine"}]"""),
            file(profiles = profile("\"data\":{\"goals\":[$goal]}")),
            // a blank id, or a key naming none
            file(profiles = profile("\"goals\":[{\"id\":\"\",\"profileId\":\"p\",\"name\":\"Trip\"}]")),
            file(profiles = profile("\"categories\":[{\"id\":\" \",\"profileId\":\"p\",\"name\":\"Fun\"}]")),
            file(profiles = profile("\"transactions\":[{\"id\":\"t\",\"profileId\":\"p\",\"date\":\"2026-03-03\",\"loggedAt\":0,\"amount\":100,\"key\":\"cat:\",\"targetLabel\":\"x\"}]")),
            // a spend row of zero or above, which Sci-Fin never wrote
            file(profiles = profile("\"transactions\":[{\"id\":\"t\",\"profileId\":\"p\",\"date\":\"2026-03-03\",\"loggedAt\":0,\"amount\":0,\"key\":\"spend:g\",\"targetLabel\":\"x\"}]")),
            file(profiles = profile("\"transactions\":[{\"id\":\"t\",\"profileId\":\"p\",\"date\":\"2026-03-03\",\"loggedAt\":0,\"amount\":500,\"key\":\"spend:g\",\"targetLabel\":\"x\"}]")),
            "{\"app\":\"sci-fin\",\"x\":" + "[".repeat(100_000) + "]".repeat(100_000) + "}",
            // an envelope with its nonce cut short
            text("schema-10-encrypted.json").replace(Regex("\"iv\": \"[^\"]*\""), "\"iv\": \"AAAA\""),
        )
        for (t in cases) assertEquals(SciFinRefusal.DAMAGED, refused(t, password), t.take(160))
    }

    // SPEC:R135 SPEC:R21 SPEC:R54
    @Test fun `R135 each Sci-Fin row becomes a payment or a ledger row as its key and note say`() {
        val p = ok(text("schema-10.json")).profiles.single()
        val byPayee = p.payments.associateBy { it.payee }
        assertEquals(5, p.payments.size)
        assertEquals(5, p.transactions.size)
        byPayee.getValue("MERCATO VERDE").let {
            assertEquals(cents("12.40"), it.total); assertEquals(PaymentSource.Savings, it.source)
            assertEquals(listOf(Part("c-groc", cents("12.40"))), it.parts); assertEquals(LocalDate(2026, 3, 3), it.date)
        }
        assertEquals(listOf(Part("c-groc", cents("40.00"))), byPayee.getValue("Groceries").parts)
        assertEquals(cents("40.00"), byPayee.getValue("Groceries").total)
        assertEquals(LocalDate(2026, 3, 10), byPayee.getValue("Groceries").date)
        assertEquals(LocalDate(2026, 2, 27), byPayee.getValue("BAR LUNAFINTA").date)
        byPayee.getValue("Spent from Trip").let {
            assertEquals(cents("60.00"), it.total); assertEquals(PaymentSource.Goal("g-trip"), it.source)
            assertEquals(listOf(Part(null, cents("60.00"))), it.parts)
        }
        byPayee.getValue("Charged").let {
            assertEquals(cents("40.00"), it.total); assertEquals(PaymentSource.Account("a-card"), it.source)
            assertEquals(listOf(Part(null, cents("40.00"))), it.parts)
        }
        // the logged instant is the row's
        assertEquals(kotlin.time.Instant.parse("2026-03-12T21:00:00Z"), byPayee.getValue("BAR LUNAFINTA").loggedAt)
        val rows = p.transactions.associateBy { it.id }
        assertEquals(StoredLabels.CORRECTION, rows.getValue("t-08").payee)
        assertEquals("cat:c-groc", rows.getValue("t-08").key)
        assertEquals("Opening balance", rows.getValue("t-01").payee)
        assertEquals("rec:r-rent", rows.getValue("t-02").key)
        assertNull(rows.getValue("t-02").categoryId)
        assertEquals(setOf("t-01", "t-02", "t-03", "t-04", "t-08"), rows.keys)
        // the file's figures, as they are
        assertEquals(cents("1,250.00"), p.savings.amount)
        assertEquals(cents("400.00"), p.goals.single().saved)
        assertEquals(cents("340.00"), p.accounts.single().balance)
    }

    // SPEC:R25 SPEC:R135
    @Test fun `R25 imported March rows give March's spent as their sum`() {
        val (_, d) = added()
        val groceries = d.categories.first { it.name == "Groceries" }.id
        // 12.40 + 40.00 + the 5.00 adjustment; Sci-Fin stored 64.90, counting the backdated 7.50 in March
        assertEquals(cents("57.40"), Spent.of(d, groceries, "2026-03"))
        assertEquals(cents("7.50"), Spent.of(d, groceries, "2026-02"))
        assertEquals(0L, Spent.of(d, d.categories.first { it.name == "Fun" }.id, "2026-03"))
    }

    // SPEC:R136 SPEC:R19
    @Test fun `R136 every record takes a new id and every key and link follows it`() {
        val fileIds = setOf("sf-p-1", "g-trip", "c-groc", "c-fun", "r-rent", "a-card", "s-1") + (1..10).map { "t-%02d".format(it) }
        val (_, d) = added()
        val id = d.profileId
        assertNotEquals("sf-p-1", id)
        val ids = listOf(id) + d.goals.map { it.id } + d.categories.map { it.id } + d.recurringExpenses.map { it.id } +
            d.accounts.map { it.id } + d.transactions.map { it.id } + d.shifts.map { it.id } + d.payments.map { it.id }
        assertTrue(ids.none { it in fileIds }, ids.toString())
        val owners = listOf(d.savings.profileId, d.settings.profileId, d.importState.profileId) +
            d.goals.map { it.profileId } + d.categories.map { it.profileId } + d.recurringExpenses.map { it.profileId } +
            d.accounts.map { it.profileId } + d.transactions.map { it.profileId } + d.shifts.map { it.profileId } +
            d.payments.map { it.profileId }
        assertTrue(owners.all { it == id })
        val goal = d.goals.single().id
        val groceries = d.categories.first { it.name == "Groceries" }.id
        val fun_ = d.categories.first { it.name == "Fun" }.id
        val rent = d.recurringExpenses.single()
        val card = d.accounts.single().id
        assertEquals(fun_, rent.categoryId)
        val keys = d.transactions.map { TransactionKey.parse(it.key) }.toSet()
        assertEquals(
            setOf(TransactionKey.AccountKey(card), TransactionKey.RecurringKey(rent.id), TransactionKey.GoalKey(goal), TransactionKey.SavingsKey, TransactionKey.CategoryKey(groceries)),
            keys,
        )
        assertEquals(
            setOf(PaymentSource.Savings, PaymentSource.Goal(goal), PaymentSource.Account(card)),
            d.payments.map { it.source }.toSet(),
        )
        assertEquals(setOf(groceries, null), d.payments.flatMap { it.parts }.map { it.categoryId }.toSet())
    }

    // SPEC:R136 SPEC:R70 SPEC:R18 SPEC:R74
    @Test fun `R136 the month mark is Fun's last reset 2026-02, Accounts are on and no catch-up summary is carried`() {
        val (_, d) = added()
        val s = d.settings
        assertEquals("2026-02", s.lastMonthProcessed)
        assertTrue(s.accountsEnabled)
        assertFalse(s.catchUpSummaryPending)
        assertEquals("", s.catchUpEnded)
        assertEquals(0, s.catchUpMonths)
        assertEquals(0L, s.catchUpSwept)
        assertEquals("it", s.language)
        assertEquals(IncomeType.HOURLY, s.incomeType)
        assertEquals(cents("12.50"), s.hourlyRate)
        assertEquals(1, d.shifts.size)
        // no Account, no switch
        val bare = ok("""{"app":"sci-fin","schemaVersion":10,"profiles":[{"id":"p","name":"Mine","settings":{}}]}""").profiles.single()
        assertFalse(bare.settings.accountsEnabled)
        assertNull(bare.settings.lastMonthProcessed)
    }

    // SPEC:R137 SPEC:R67
    @Test fun `R137 a Sci-Fin recurring expense keeps Confirm off until it has a category, picked or added`() {
        val read = ok(text("schema-10.json"))
        assertFalse(SciFinImport.canConfirm(read))
        // adding while Confirm is off adds nothing
        val existing = listOf(ProfileData.fresh("p-1", "Main"))
        assertSame(existing, SciFinImport.addTo(existing, read, Ids()::next))
        // a category that does not exist, a blank name, an id already used: nothing changes
        assertEquals(read, SciFinImport.setExpenseCategory(read, "r-rent", "c-none"))
        assertEquals(read, SciFinImport.setExpenseCategory(read, "r-none", "c-fun"))
        assertEquals(read, SciFinImport.addCategoryForExpense(read, "r-rent", "  ", "c-house"))
        assertEquals(read, SciFinImport.addCategoryForExpense(read, "r-rent", "Housing", "c-fun"))
        assertTrue(SciFinImport.canConfirm(SciFinImport.setExpenseCategory(read, "r-rent", "c-fun")))
        // created on the spot
        val housed = SciFinImport.addCategoryForExpense(read, "r-rent", "Housing", "c-house")
        assertTrue(SciFinImport.canConfirm(housed))
        assertEquals(3, housed.review.profiles.single().categories)
        val d = SciFinImport.addTo(emptyList(), housed, Ids("n")::next).single()
        val housing = d.categories.single { it.name == "Housing" }
        assertEquals(housing.id, d.recurringExpenses.single().categoryId)
        assertEquals(d.profileId, housing.profileId)
        // a category of the other Profile in the file is not this expense's
        val two = ok(twoProfiles())
        assertEquals(two, SciFinImport.setExpenseCategory(two, "r-rent", "c-fun-b"))
        val one = SciFinImport.setExpenseCategory(two, "r-rent", "c-fun")
        assertFalse(SciFinImport.canConfirm(one))
        assertTrue(SciFinImport.canConfirm(SciFinImport.setExpenseCategory(one, "r-rent-b", "c-fun-b")))
    }

    // SPEC:R138 SPEC:R26
    @Test fun `R138 Sci-Fin's kept keys catch the rows it recorded, its spacing made one`() {
        val (_, d) = added()
        assertEquals(
            listOf("fit:F-0302", "syn:2026-03-03|-12.40|mercato verde", "syn:2026-03-05|-3.20|pos caffe del molo"),
            d.importState.importedKeys,
        )
        assertEquals(LocalDate(2026, 3, 21), d.importState.lastImportDate)
        val ofx = """
            <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
            <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260302</DTPOSTED><TRNAMT>-8.00</TRNAMT><FITID>F-0302</FITID><NAME>EDICOLA STELLA</NAME></STMTTRN>
            </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
        """.trimIndent()
        val today = LocalDate(2026, 11, 26)
        assertEquals(listOf(RowStatus.DUPLICATE), StatementImport.review(d, StatementImport.parseOfx(ofx), today).map { it.status })
        val csv = StatementImport.parseCsv("Date;Description;Amount\n05/03/2026;POS  CAFFE DEL MOLO;-3,20\n03/03/2026;MERCATO VERDE;-12,40\n")
        assertEquals(listOf(RowStatus.DUPLICATE, RowStatus.DUPLICATE), StatementImport.review(d, csv, today).map { it.status })
    }

    // SPEC:R138
    @Test fun `R138 a 40-character key its spacing would shorten, and a key of no known form, are left out`() {
        val cut = "farmacia  san lorenzo e figli via roma 12".take(40)
        val whole = "farmacia san lorenzo e figli snc via rom".take(40)
        assertEquals(40, cut.length); assertEquals(40, whole.length)
        val keys = "[\"syn:2026-03-06|-9.90|$cut\", \"syn:2026-03-07|-9.90|$whole\", \"bank:x\", \"syn:2026-03-08|-1.00|  caffe\\tnord \", \"fit:\"]"
        val t = swap(text("schema-10.json"), "\"importedKeys\": \\[[^\\]]*\\]", "\"importedKeys\": $keys")
        val (_, d) = added(ok(t))
        assertEquals(listOf("syn:2026-03-07|-9.90|$whole", "syn:2026-03-08|-1.00|caffe nord"), d.importState.importedKeys)
    }

    // SPEC:R138 SPEC:R108
    @Test fun `R138 Sci-Fin payee rules come under their rule names, mapped to the new categories`() {
        val (_, d) = added()
        val groceries = d.categories.first { it.name == "Groceries" }.id
        val fun_ = d.categories.first { it.name == "Fun" }.id
        assertEquals(mapOf("mercato verde" to groceries, "bar lunafinta" to fun_), d.importState.payeeRules)
        val csv = StatementImport.parseCsv("Date;Description;Amount\n12/04/2026;POS 0999 BAR LUNAFINTA;-4,50\n13/04/2026;MERCATO VERDE;-18,75\n")
        val review = StatementImport.review(d, csv, LocalDate(2026, 11, 26)).associateBy { it.row.description }
        assertEquals(listOf(Part(fun_, cents("4.50"))), review.getValue("POS 0999 BAR LUNAFINTA").draft?.parts)
        assertEquals(listOf(Part(groceries, cents("18.75"))), review.getValue("MERCATO VERDE").draft?.parts)
    }

    // SPEC:R138 SPEC:R108
    @Test fun `R138 two rules of one rule name in two categories keep their payees, and a rule of no category held is left out`() {
        val rules = """{"pos mercato verde": "c-groc", "mercato verde 12": "c-fun", "ghost shop": "c-gone"}"""
        val t = swap(text("schema-10.json"), "\"payeeRules\": \\{[^}]*\\}", "\"payeeRules\": $rules")
        val (_, d) = added(ok(t))
        val groceries = d.categories.first { it.name == "Groceries" }.id
        val fun_ = d.categories.first { it.name == "Fun" }.id
        assertEquals(mapOf("pos mercato verde" to groceries, "mercato verde 12" to fun_), d.importState.payeeRules)
    }

    /** The schema-10 fixture with one more ledger row, [row] a Sci-Fin row's JSON. */
    private fun withRow(row: String) = swap(text("schema-10.json"), "\"transactions\": \\[", "\"transactions\": [ $row,")

    private fun row(id: String, date: String, payee: String, amount: Long, key: String, label: String = "x", loggedAt: Long = 0) =
        """{"id":"$id","profileId":"sf-p-1","date":"$date","loggedAt":$loggedAt,"payee":"$payee","amount":$amount,"key":"$key","targetLabel":"$label"}"""

    // SPEC:R135 SPEC:R25
    @Test fun `R135 a 20_00 cat row noted Starting balance stays an adjustment`() {
        val read = ok(withRow(row("t-11", "2026-03-04", "Starting balance", 2000, "cat:c-groc")))
        val p = read.profiles.single()
        assertEquals("cat:c-groc", p.transactions.single { it.id == "t-11" }.key)
        assertTrue(p.payments.none { it.payee == StoredLabels.STARTING_BALANCE })
        // 57.40 and the 20.00 adjustment
        val d = SciFinImport.addTo(emptyList(), confirmed(read), Ids()::next).single()
        assertEquals(cents("77.40"), Spent.of(d, d.categories.first { it.name == "Groceries" }.id, "2026-03"))
    }

    // SPEC:R138 SPEC:R26
    @Test fun `R138 a key Sci-Fin kept once for two equal rows of 12_40 is kept twice, and a new FITID twin arrives recorded`() {
        val today = LocalDate(2026, 11, 26)
        val twins = StatementImport.parseCsv("Date;Description;Amount\n03/03/2026;MERCATO VERDE;-12,40\n03/03/2026;MERCATO VERDE;-12,40\n")
        val (_, one) = added()
        assertEquals(listOf(RowStatus.DUPLICATE, RowStatus.READY), StatementImport.review(one, twins, today).map { it.status })
        val (_, two) = added(ok(withRow(row("t-11", "2026-03-03", "MERCATO VERDE", 1240, "cat:c-groc"))))
        assertEquals(2, two.importState.importedKeys.count { it == "syn:2026-03-03|-12.40|mercato verde" })
        assertEquals(listOf(RowStatus.DUPLICATE, RowStatus.DUPLICATE), StatementImport.review(two, twins, today).map { it.status })
        // a row of money in counts by its savings row
        val inKey = swap(text("schema-10.json"), "\"importedKeys\": \\[", "\"importedKeys\": [\"syn:2026-03-02|1450.00|stipendio\",")
        val (_, income) = added(ok(swap(inKey, "\"transactions\": \\[", "\"transactions\": [ ${row("t-11", "2026-03-02", "Stipendio", 145000, "savings")},")))
        assertEquals(2, income.importState.importedKeys.count { it == "syn:2026-03-02|1450.00|stipendio" })
        val ofx = """
            <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
            <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260303</DTPOSTED><TRNAMT>-12.40</TRNAMT><FITID>F-NEW</FITID><NAME>MERCATO VERDE</NAME></STMTTRN>
            </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
        """.trimIndent()
        assertEquals(listOf(RowStatus.DUPLICATE), StatementImport.review(one, StatementImport.parseOfx(ofx), today).map { it.status })
    }

    /** A Sci-Fin Profile with a card linked to its 40.00 fixed payment, swept on 1 March, and a 40.00 charge on 18 March when [charged]. */
    /**
     * A Sci-Fin Profile with Orbita card: when [linked], linked to its 40.00 fixed payment, swept on
     * each of [sweeps]; a 40.00 charge on [chargeDay] when [charged]; when [handPaid] is given, a
     * payment off it by hand on 20 March as Sci-Fin wrote one, its `acct:` row and the `savings` row
     * beside it; and the Sci-Fin rows [extra].
     */
    private fun cardFile(
        charged: Boolean, linked: Boolean = true, handPaid: Long? = null, chargeDay: String = "2026-03-01",
        sweeps: List<String> = listOf("2026-03-01"), extra: List<String> = emptyList(),
    ): String {
        val rows = (
            (if (linked) sweeps.mapIndexed { i, day -> row("t-s$i", day, "Recurring sweep", 4000, "rec:r-card") } else emptyList()) +
                listOfNotNull(
                    if (charged) row("t-2", chargeDay, "Charged", 4000, "acct:a-card", "Orbita card") else null,
                    handPaid?.let { row("t-3", "2026-03-20", "Paid off", -it, "acct:a-card", "Orbita card") },
                    handPaid?.let { row("t-4", "2026-03-20", "Payment to Orbita card", -it, "savings", "Savings") },
                ) + extra
            ).joinToString(",").replace("sf-p-1", "p")
        val expenses = if (!linked) "" else """{"id":"r-card","profileId":"p","name":"Orbita card payment","amount":4000,"cadence":"monthly",
              "firstDueDate":"2026-03-01","dueDate":"2026-04-01","dueDateAnchorDay":1,"dateMode":"fixedDate","lastSweptMonth":"2026-03","linkedAccountId":"a-card"}"""
        val link = if (linked) ""","linkedRecurringExpenseId":"r-card"""" else ""
        return """{"app":"sci-fin","schemaVersion":10,"profiles":[{"id":"p","name":"Mine","savings":100000,"settings":{},
            "categories":[{"id":"c-fun","profileId":"p","name":"Fun"},{"id":"c-home","profileId":"p","name":"Home"}],
            "recurringExpenses":[$expenses],
            "accounts":[{"id":"a-card","profileId":"p","name":"Orbita card","type":"credit","balance":4000$link}],
            "transactions":[$rows]}]}"""
    }

    // SPEC:R139 SPEC:R109
    @Test fun `R139 an unlinked card with no charge paid by hand 50_00 in March counts 50_00 under its category once`() {
        val read = ok(cardFile(charged = false, linked = false, handPaid = 5000))
        assertFalse(SciFinImport.canConfirm(read))
        val picked = SciFinImport.setCardCategory(read, "a-card", "c-fun")
        assertTrue(SciFinImport.canConfirm(picked))
        val d = SciFinImport.addTo(emptyList(), picked, Ids()::next).single()
        val fun_ = d.categories.single { it.name == "Fun" }.id
        assertTrue(d.accounts.single().chargesNotLogged)
        assertEquals(fun_, d.accounts.single().payoffCategoryId)
        assertEquals(fun_, d.transactions.single { it.key == "savings" }.categoryId)
        assertNull(d.transactions.single { it.key.startsWith("acct:") }.categoryId)
        assertEquals(cents("50.00"), Spent.of(d, fun_, "2026-03"))
        assertEquals(cents("50.00"), Reports.spendingComposition(d, LocalDate(2026, 3, 31), emptySet()).net)
    }

    // SPEC:R139 SPEC:R109 SPEC:R93
    @Test fun `R139 a linked card with no charge, a 40_00 sweep and a 25_00 payment by hand counts 65_00 under its category`() {
        val read = SciFinImport.setExpenseCategory(ok(cardFile(charged = false, handPaid = 2500)), "r-card", "c-home")
        val d = SciFinImport.addTo(emptyList(), SciFinImport.setCardCategory(read, "a-card", "c-fun"), Ids()::next).single()
        val fun_ = d.categories.single { it.name == "Fun" }.id
        assertEquals(cents("65.00"), Spent.of(d, fun_, "2026-03"))
        assertEquals(0L, Spent.of(d, d.categories.single { it.name == "Home" }.id, "2026-03"))
        assertEquals(cents("65.00"), Reports.spendingComposition(d, LocalDate(2026, 3, 31), emptySet()).net)
    }

    // SPEC:R139
    @Test fun `R139 a card with no charge and no payoff row does not hold Confirm`() {
        val read = ok(cardFile(charged = false, linked = false))
        assertTrue(SciFinImport.canConfirm(read))
        assertFalse(read.profiles.single().accounts.single().chargesNotLogged)
        assertEquals(read, SciFinImport.setCardCategory(read, "a-card", "c-fun"))
        // with a charge, a payment by hand stays a transfer
        val charged = SciFinImport.addTo(emptyList(), ok(cardFile(charged = true, linked = false, handPaid = 5000)), Ids()::next).single()
        assertNull(charged.transactions.single { it.key == "savings" }.categoryId)
        assertEquals(cents("40.00"), Reports.spendingComposition(charged, LocalDate(2026, 3, 31), emptySet()).net)
    }

    // SPEC:R139
    @Test fun `R139 a category of another Profile in the file, picked or as the new id, changes nothing for the card`() {
        val other = """{"id":"q","name":"Other","settings":{},"categories":[{"id":"c-other","profileId":"q","name":"Elsewhere"}]}"""
        val read = ok(cardFile(charged = false, linked = false, handPaid = 5000).trimEnd().removeSuffix("]}") + ",$other]}")
        assertEquals(2, read.profiles.size)
        assertFalse(SciFinImport.canConfirm(read))
        assertEquals(read, SciFinImport.setCardCategory(read, "a-card", "c-other"))
        assertEquals(read, SciFinImport.addCategoryForCard(read, "a-card", "Card spending", "c-other"))
        // its own Profile's category still serves
        assertTrue(SciFinImport.canConfirm(SciFinImport.setCardCategory(read, "a-card", "c-fun")))
    }

    // SPEC:R139 SPEC:R93 SPEC:R67
    @Test fun `R139 a linked card with a 40_00 charge logged gives March's Spending 40_00, not 80_00`() {
        val read = SciFinImport.setExpenseCategory(ok(cardFile(charged = true)), "r-card", "c-home")
        assertTrue(SciFinImport.canConfirm(read))
        // a card whose charges arrive as payments takes no payoff category
        assertEquals(read, SciFinImport.setCardCategory(read, "a-card", "c-fun"))
        val d = SciFinImport.addTo(emptyList(), read, Ids()::next).single()
        assertTrue(d.transactions.single { it.key.startsWith("rec:") }.accountPayoff)
        assertFalse(d.accounts.single().chargesNotLogged)
        assertEquals(cents("40.00"), Reports.spendingComposition(d, LocalDate(2026, 3, 31), emptySet()).net)
    }

    // SPEC:R139 SPEC:R109 SPEC:R137
    @Test fun `R139 a linked card with no charge logged keeps Confirm off until it has a payoff category, then its 40_00 sweep counts once`() {
        val read = SciFinImport.setExpenseCategory(ok(cardFile(charged = false)), "r-card", "c-home")
        assertFalse(SciFinImport.canConfirm(read))
        assertTrue(SciFinImport.addTo(emptyList(), read, Ids()::next).isEmpty())
        assertEquals(read, SciFinImport.setCardCategory(read, "a-card", "c-none"))
        assertEquals(read, SciFinImport.setCardCategory(read, "a-none", "c-fun"))
        assertEquals(read, SciFinImport.addCategoryForCard(read, "a-card", " ", "c-card"))
        assertEquals(read, SciFinImport.addCategoryForCard(read, "a-card", "Card spending", "c-fun"))
        for ((picked, name) in listOf(SciFinImport.setCardCategory(read, "a-card", "c-fun") to "Fun", SciFinImport.addCategoryForCard(read, "a-card", "Card spending", "c-card") to "Card spending")) {
            assertTrue(SciFinImport.canConfirm(picked), name)
            val d = SciFinImport.addTo(emptyList(), picked, Ids()::next).single()
            val payoff = d.categories.single { it.name == name }.id
            val card = d.accounts.single()
            assertTrue(card.chargesNotLogged, name)
            assertEquals(payoff, card.payoffCategoryId, name)
            val sweep = d.transactions.single { it.key.startsWith("rec:") }
            assertFalse(sweep.accountPayoff, name)
            assertEquals(payoff, sweep.categoryId, name)
            assertEquals(cents("40.00"), Spent.of(d, payoff, "2026-03"), name)
            assertEquals(0L, Spent.of(d, d.categories.single { it.name == "Home" }.id, "2026-03"), name)
            assertEquals(cents("40.00"), Reports.spendingComposition(d, LocalDate(2026, 3, 31), emptySet()).net, name)
        }
    }

    // SPEC:R139 SPEC:R109 SPEC:R93
    @Test fun `R139 charges from 1 March leave the January and February 40_00 sweeps under the category, March and April transfers`() {
        val file = cardFile(charged = true, chargeDay = "2026-03-01", sweeps = listOf("2026-01-01", "2026-02-01", "2026-03-01", "2026-04-01"))
        val read = SciFinImport.setExpenseCategory(ok(file), "r-card", "c-home")
        assertFalse(SciFinImport.canConfirm(read))
        val picked = SciFinImport.setCardCategory(read, "a-card", "c-fun")
        assertTrue(SciFinImport.canConfirm(picked))
        val d = SciFinImport.addTo(emptyList(), picked, Ids()::next).single()
        val fun_ = d.categories.single { it.name == "Fun" }.id
        val card = d.accounts.single()
        assertFalse(card.chargesNotLogged)
        assertEquals(fun_, card.payoffCategoryId)
        assertEquals(listOf(cents("40.00"), cents("40.00"), 0L, 0L), listOf("2026-01", "2026-02", "2026-03", "2026-04").map { Spent.of(d, fun_, it) })
        val sweeps = d.transactions.filter { it.key.startsWith("rec:") }.sortedBy { it.date }
        assertEquals(listOf(false, false, true, true), sweeps.map { it.accountPayoff })
        // March: the 40.00 charge alone
        assertEquals(cents("40.00"), Reports.spendingComposition(d, LocalDate(2026, 3, 31), emptySet()).net)
    }

    // SPEC:R139 SPEC:R109 SPEC:R22
    @Test fun `R139 a 40_00 sweep on 1 March before the first 40_00 charge on 18 March gives March's Spending 80_00`() {
        val read = SciFinImport.setExpenseCategory(ok(cardFile(charged = true, chargeDay = "2026-03-18")), "r-card", "c-home")
        assertFalse(SciFinImport.canConfirm(read))
        val d = SciFinImport.addTo(emptyList(), SciFinImport.setCardCategory(read, "a-card", "c-fun"), Ids()::next).single()
        val fun_ = d.categories.single { it.name == "Fun" }.id
        assertFalse(d.accounts.single().chargesNotLogged)
        // the 1 March payoff paid what was owed before the first charge logged; the charge is its own payment
        assertEquals(cents("40.00"), Spent.of(d, fun_, "2026-03"))
        assertEquals(cents("80.00"), Reports.spendingComposition(d, LocalDate(2026, 3, 31), emptySet()).net)
    }

    // SPEC:R139
    @Test fun `R139 a savings row noted otherwise, logged 61 seconds after or 1 second before, or beside a Correction is no payment by hand`() {
        val t = 1_773_996_000_000L // 20 March 2026, 08:40 UTC
        fun paid(acctNote: String, savingsNote: String, after: Long) = listOf(
            row("t-5", "2026-03-20", acctNote, -5000, "acct:a-card", "Orbita card", loggedAt = t),
            row("t-6", "2026-03-20", savingsNote, -5000, "savings", "Savings", loggedAt = t + after),
        )
        val cases = listOf(
            paid("Paid off", "Bar Lunafinta", 0),
            paid("Paid off", "Payment to Orbita card", 61_000),
            paid("Paid off", "Payment to Orbita card", -1_000),
            paid(StoredLabels.CORRECTION, StoredLabels.CORRECTION, 0),
            paid(StoredLabels.ACCOUNT_CLOSED, StoredLabels.ACCOUNT_CLOSED, 0),
            paid(StoredLabels.LOAN_FINISHED, StoredLabels.LOAN_FINISHED, 0),
        )
        for (rows in cases) {
            val read = ok(cardFile(charged = false, linked = false, extra = rows))
            assertTrue(SciFinImport.canConfirm(read), rows.toString())
            assertFalse(read.profiles.single().accounts.single().chargesNotLogged, rows.toString())
        }
        // within the minute, noted as the payment: a payment by hand
        assertFalse(SciFinImport.canConfirm(ok(cardFile(charged = false, linked = false, extra = paid("Paid off", "Paid off", 60_000)))))
    }

    // SPEC:R136 SPEC:R18
    @Test fun `R136 a Sci-Fin Profile added beside one switches Profiles on, added alone does not`() {
        val (existing, added) = added()
        assertTrue(SciFinImport.profilesNeeded(listOf(existing, added)))
        assertFalse(SciFinImport.profilesNeeded(SciFinImport.addTo(emptyList(), confirmed(ok(text("schema-10.json"))), Ids()::next)))
    }
}
