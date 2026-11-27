@file:OptIn(ExperimentalTime::class)

package com.hazia.tesoriere

import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.goal
import com.hazia.tesoriere.Fixture.recurring
import com.hazia.tesoriere.engine.Backup
import com.hazia.tesoriere.engine.BackupCrypto
import com.hazia.tesoriere.engine.BackupProblem
import com.hazia.tesoriere.engine.BackupResult
import com.hazia.tesoriere.engine.Trash
import com.hazia.tesoriere.engine.TrashPayload
import com.hazia.tesoriere.engine.TrashTidy
import com.hazia.tesoriere.engine.ValidatedBackup
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AccountType
import com.hazia.tesoriere.model.AppLock
import com.hazia.tesoriere.model.AppState
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.CopySchedule
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.SCHEMA_VERSION
import com.hazia.tesoriere.model.Savings
import com.hazia.tesoriere.model.Shift
import com.hazia.tesoriere.model.StoredLabels
import com.hazia.tesoriere.model.Transaction
import com.hazia.tesoriere.model.TrashItem
import com.hazia.tesoriere.payments.BankKeys
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.Payment
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.payments.ReceiptLine
import com.hazia.tesoriere.receipts.PickupMode
import com.hazia.tesoriere.receipts.ReceiptSettings
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Ported from the backup half of Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/ImportBackupTest.kt
 * at 2892110, with the backup cases of its CleanupEngineTest.kt and HourlyIncomeTest.kt. Changed:
 * refusals are codes, not sentences (SPEC R131); a file names its app and its schema version, so
 * the Sci-Fin cases of a file with neither are refused as another app's (Sci-Fin's files are read as
 * SPEC R19 says); the appearance repairs are not ported (SPEC R16); the file carries payments,
 * receipt settings and the Trash (SPEC R130). `Backup.apply`, the restore into the database, comes
 * with storage, and its test with it. Every name, store and figure is made up.
 */
class BackupTest {
    private val nov26 = LocalDate(2026, 11, 26)
    private val nov10 = LocalDate(2026, 11, 10)
    private val logged = Instant.parse("2026-11-10T10:00:00Z")

    /** A 42.30 receipt split 30.00 Groceries and 12.30 Household, paired with its bank row. */
    private val receiptPayment = Payment(
        id = "pay-r", profileId = Fixture.PROFILE, date = nov10, loggedAt = logged, total = cents("42.30"),
        source = PaymentSource.Savings, payee = "Bottega Inventata",
        parts = listOf(Part("groceries", cents("30.00")), Part("household", cents("12.30"))),
        receiptId = "rcpt-0001",
        receiptLines = listOf(
            ReceiptLine("Oat milk", "1 l", 2, cents("30.00"), "ch-food", "Food", "groceries"),
            ReceiptLine("Dish soap", null, 1, cents("12.30"), "ch-clean", "Pulizia", "household"),
        ),
        bankKeys = BankKeys("F1", "fsyn:2026-11-11|-42.30|bottega inventata"),
    )

    /** From a Goal short of its total, with a difference part from its pair. */
    private val goalPayment = Payment(
        id = "pay-g", profileId = Fixture.PROFILE, date = nov10, loggedAt = logged, total = cents("20.20"),
        source = PaymentSource.Goal("trip"), payee = "Caffè Nord",
        parts = listOf(Part("fun", cents("20.00")), Part("fun", cents("0.20"), difference = true)),
        receiptId = "rcpt-0002", bankKeys = BankKeys(null, "syn:2026-11-10|-20.20|caffe nord"), fromSavings = cents("5.00"),
    )

    private val chargedPayment = Payment(
        id = "pay-c", profileId = Fixture.PROFILE, date = nov10, loggedAt = logged, total = cents("9.90"),
        source = PaymentSource.Account("card"), payee = null, parts = listOf(Part(null, cents("9.90"))),
    )

    private val receiptSettings = ReceiptSettings(
        Fixture.PROFILE, folder = "folder-handle-1", pickup = PickupMode.DAILY_CHECK,
        recordedReceiptIds = listOf("rcpt-0001", "rcpt-0002"),
        categoryLinks = mapOf("ch-food" to "groceries", "ch-clean" to "household"),
        storeSources = mapOf("Bottega Inventata" to "savings"),
        storeLinks = mapOf("Bottega Inventata" to "BOTTEGA INVENTATA SRL"),
    )

    /** One Profile with something in every field ProfileData has. */
    private fun world(): ProfileData = Fixture.profile(
        savings = "1,000.00",
        goals = listOf(goal("trip", "Trip", saved = "300.00", target = "900.00")),
        categories = listOf(category("groceries", "Groceries"), category("household", "Household"), category("fun", "Fun")),
    ).let { d ->
        d.copy(
            settings = d.settings.copy(incomeEnabled = true, accountsEnabled = true, lastMonthProcessed = "2026-11"),
            importState = d.importState.copy(
                importedKeys = listOf("fit:F1", "fsyn:2026-11-11|-42.30|bottega inventata", "syn:2026-11-10|-20.20|caffe nord"),
                payeeRules = mapOf("caffe nord" to "fun"), importReminderEnabled = true, lastImportDate = nov10,
            ),
            recurringExpenses = listOf(recurring("rent", "650.00", Cadence.MONTHLY, LocalDate(2026, 1, 1), categoryId = "household")),
            accounts = listOf(Account("card", Fixture.PROFILE, "Card", type = AccountType.CREDIT, balance = cents("140.00"), apr = 0.219)),
            transactions = listOf(
                Transaction("tx-1", Fixture.PROFILE, nov10, logged, "Payroll", cents("1,450.00"), "savings", StoredLabels.SAVINGS, isIncome = true),
                Transaction("tx-2", Fixture.PROFILE, nov10, logged, null, cents("-650.00"), "rec:rent", "Rent", categoryId = "household"),
            ),
            shifts = listOf(Shift("s-1", Fixture.PROFILE, nov10, 6.5, cents("20.00"))),
            payments = listOf(receiptPayment, goalPayment, chargedPayment),
            receiptSettings = receiptSettings,
        )
    }

    private fun other(): ProfileData = ProfileData.fresh("p-2", "Second", createdOn = nov10)

    /** [d] after its payment `pay-c` is deleted on 10 November, and that payment's Trash item. */
    private fun trashedPayment(d: ProfileData): Pair<ProfileData, TrashItem> {
        val clock = TestClock(nov10)
        val (after, p) = requireNotNull(Trash.deletePayment(d, "pay-c", clock, Ids("del")::next))
        return after to TrashItem("tr-1", Fixture.PROFILE, "payment", "Card", nov10.toString(), Trash.encode(p))
    }

    /** A deleted Profile in the Trash: its item carries its own id (SPEC R126). */
    private fun trashedProfile(): TrashItem {
        val gone = ProfileData.fresh("p-9", "Old")
        val (_, p) = Trash.deleteProfile(AppState(activeProfileId = Fixture.PROFILE), gone)
        return TrashItem("tr-2", "p-9", "profile", "Old", LocalDate(2026, 11, 1).toString(), Trash.encode(p), warned = true)
    }

    /** The file of [profiles]; the receipt Profile is the one holding a folder, as the app keeps it (SPEC R112). */
    private fun text(
        profiles: List<ProfileData>, trash: List<TrashItem> = emptyList(), active: String = profiles.first().profileId,
        receipt: String? = profiles.firstOrNull { it.receiptSettings?.folder != null }?.profileId,
    ) = Backup.encode(Backup.build(profiles, trash, active, receipt, nov26))

    /** Profile B with Goal `car`, which is deleted on 10 November: its Trash item, and B after it. */
    private fun goalOfB(): Pair<ProfileData, TrashItem> {
        val b = other().copy(savings = Savings("p-2", cents("100.00")), goals = listOf(goal("car", "Car", saved = "40.00").copy(profileId = "p-2")))
        val (after, p) = requireNotNull(Trash.deleteGoal(b, "car", returnSaved = true, clock = TestClock(nov10), ids = Ids("g")::next))
        return after to TrashItem("tr-g", "p-2", "goal", "Car", nov10.toString(), Trash.encode(p))
    }

    /** Profile [d] deleted on 12 November: its Trash item, which carries its own id (SPEC R126). */
    private fun profileItem(d: ProfileData, id: String = "tr-p", itemProfile: String = d.profileId): TrashItem {
        val (_, p) = Trash.deleteProfile(AppState(activeProfileId = Fixture.PROFILE), d)
        return TrashItem(id, itemProfile, "profile", d.profile.name, LocalDate(2026, 11, 12).toString(), Trash.encode(p))
    }

    /** A Trash item of the main Profile holding [payload]. */
    private fun item(payload: TrashPayload, id: String = "tr-x") =
        TrashItem(id, Fixture.PROFILE, "payment", "Item", nov10.toString(), Trash.encode(payload))

    private fun ok(text: String): ValidatedBackup = assertIs<BackupResult.Ok>(Backup.validate(text)).backup

    private fun refused(text: String): BackupProblem = assertIs<BackupResult.Refused>(Backup.validate(text)).problem

    /** The file of one Profile [p], given as JSON object text, around the app's own header. */
    private fun file(vararg profiles: String, extra: String = "") =
        """{"app":"tesoriere","schemaVersion":$SCHEMA_VERSION$extra,"profiles":[${profiles.joinToString(",")}]}"""

    // ---- what the file holds (R130) -------------------------------------------------------------

    // SPEC:R130
    @Test fun `R130 a backup round-trips payments with their parts, lines and bankKeys`() {
        val d = world()
        val back = ok(text(listOf(d))).profiles.single()
        assertEquals(d.payments, back.payments)
        val r = back.payments.first { it.id == "pay-r" }
        assertEquals(listOf(cents("30.00"), cents("12.30")), r.parts.map { it.amount })
        assertEquals(2, r.receiptLines.size)
        assertEquals(BankKeys("F1", "fsyn:2026-11-11|-42.30|bottega inventata"), r.bankKeys)
        val g = back.payments.first { it.id == "pay-g" }
        assertEquals(cents("5.00"), g.fromSavings); assertTrue(g.parts.last().difference)
        assertEquals(d, back, "the whole Profile, every field of it, as written")
    }

    // SPEC:R130
    @Test fun `R130 receipt settings and the import's kept keys round-trip`() {
        val d = world()
        val back = ok(text(listOf(d))).profiles.single()
        assertEquals(receiptSettings, back.receiptSettings)
        assertEquals(d.importState, back.importState)
        assertEquals(listOf("fit:F1", "fsyn:2026-11-11|-42.30|bottega inventata", "syn:2026-11-10|-20.20|caffe nord"), back.importState.importedKeys)
        assertEquals(mapOf("caffe nord" to "fun"), back.importState.payeeRules)
    }

    // SPEC:R130, R121
    @Test fun `R130 a Trash item deleted on 10 November 2026 reads back so, due its notice on 30 November and going on 10 December`() {
        val (d, paymentItem) = trashedPayment(world())
        val trash = listOf(paymentItem, trashedProfile())
        val back = ok(text(listOf(d), trash))
        assertEquals(trash, back.trash)
        assertEquals(TrashTidy(emptyList(), emptyList()), Trash.tidy(back.trash.take(1), LocalDate(2026, 11, 29)))
        assertEquals(TrashTidy(emptyList(), listOf("tr-1")), Trash.tidy(back.trash.take(1), LocalDate(2026, 11, 30)))
        assertEquals(TrashTidy(listOf("tr-1"), emptyList()), Trash.tidy(back.trash.take(1), LocalDate(2026, 12, 10)))
        assertEquals(chargedPayment, Trash.decode(back.trash[0].payload)?.payments?.single())
        assertTrue(back.trash[1].warned, "the notice already given stays given")
    }

    // SPEC:R130
    @Test fun `R130 the file holds seven keys, and nothing of the app lock, the copy schedule or the Profiles switch`() {
        val (d, item) = trashedPayment(world())
        val t = text(listOf(d, other()), listOf(item))
        val root = Json.parseToJsonElement(t).jsonObject
        assertEquals(setOf("app", "schemaVersion", "exportedAt", "activeProfileId", "profiles", "trash", "receiptProfileId"), root.keys)
        val keys = HashSet<String>().also { collectKeys(root, it) }
        // what the file holds legitimately under the same name: an id, the receipt folder and Profile, the open Profile, the version
        val shared = setOf("id", "folder", "activeProfileId", "schemaVersion", "receiptProfileId")
        val deviceOnly = (fieldsOf(AppLock::class.java) + fieldsOf(CopySchedule::class.java) + fieldsOf(AppState::class.java)) - shared
        assertTrue("pinLength" in deviceOnly && "sealedPassword" in deviceOnly && "profilesEnabled" in deviceOnly)
        assertEquals(emptySet(), keys intersect deviceOnly)
        for (word in listOf("pinLength", "failedAttempts", "sealedPassword", "lastCopy", "profilesEnabled", "lastBackupExportDate")) {
            assertTrue(word !in t, word)
        }
    }

    // SPEC:R130, R18
    @Test fun `R130 a file of two Profiles switches Profiles on at the restore, and a file of one does not`() {
        assertTrue(ok(text(listOf(world(), other()))).profilesNeeded)
        assertFalse(ok(text(listOf(world()))).profilesNeeded)
        // a Profile in the Trash is not one the restore brings back
        assertFalse(ok(text(listOf(world()), listOf(profileItem(other())))).profilesNeeded)
    }

    // SPEC:R130, R112
    @Test fun `R130 the file names the receipt Profile, and its folder travels with it`() {
        val b = ok(text(listOf(world(), other())))
        assertEquals(Fixture.PROFILE, b.receiptProfileId)
        assertEquals("folder-handle-1", b.profiles[0].receiptSettings?.folder)
        assertNull(ok(text(listOf(other()))).receiptProfileId)
        // a receipt Profile whose folder is not chosen yet
        assertEquals("p-2", ok(text(listOf(world().copy(receiptSettings = receiptSettings.copy(folder = null)), other()), receipt = "p-2")).receiptProfileId)
    }

    // SPEC:R130
    @Test fun `R130 two Profiles read back as two, the open one kept, and the file names its app and schema version`() {
        val t = text(listOf(world(), other()), active = "p-2")
        assertTrue("\"app\": \"tesoriere\"" in t); assertTrue("\"schemaVersion\": $SCHEMA_VERSION" in t)
        val b = ok(t)
        assertEquals(listOf(Fixture.PROFILE, "p-2"), b.profiles.map { it.profileId })
        assertEquals(other(), b.profiles[1])
        assertEquals("p-2", b.activeProfileId); assertEquals(nov26, b.exportedAt); assertEquals(SCHEMA_VERSION, b.version)
        assertEquals(2, b.transactionCount)
    }

    // SPEC:R130, R131
    @Test fun `R130 a backup carries the logged hours, and a file without them reads with none`() {
        val d = world()
        val t = text(listOf(d))
        assertEquals(d.shifts, ok(t).profiles.single().shifts)
        val older = t.replace(Regex(""",\s*"shifts": \[[^\]]*\]"""), "")
        assertTrue("\"shifts\"" !in older)
        assertTrue(ok(older).profiles.single().shifts.isEmpty())
    }

    // ---- reading a file (R131) ------------------------------------------------------------------

    // SPEC:R131
    @Test fun `R131 a file of schema version plus one, or of 99999999999999999999, is NEWER_SCHEMA`() {
        val t = text(listOf(world()))
        // relative to the app's own version, so the next schema bump does not quietly make this case valid
        assertEquals(BackupProblem.NEWER_SCHEMA, refused(t.replace("\"schemaVersion\": $SCHEMA_VERSION", "\"schemaVersion\": ${SCHEMA_VERSION + 1}")))
        assertEquals(BackupProblem.NEWER_SCHEMA, refused(t.replace("\"schemaVersion\": $SCHEMA_VERSION", "\"schemaVersion\": 99999999999999999999")))
    }

    // SPEC:R131
    @Test fun `R131 text that is not a JSON object is NOT_A_BACKUP`() {
        for (t in listOf("{not json", "", "   ", "[]", "42", "\"tesoriere\"", "null", "{\"app\":\"tesoriere\"")) {
            assertEquals(BackupProblem.NOT_A_BACKUP, refused(t), t)
        }
    }

    // SPEC:R131, R19
    @Test fun `R131 a file of sci-fin, of another app or naming no app is OTHER_APP`() {
        val p = """{"id":"p","name":"Mine"}"""
        assertEquals(BackupProblem.OTHER_APP, refused("""{"app":"sci-fin","schemaVersion":10,"profiles":[$p]}"""))
        assertEquals(BackupProblem.OTHER_APP, refused("""{"app":"other","schemaVersion":1,"profiles":[$p]}"""))
        // a Sci-Fin file of schema 1 names neither app nor version
        assertEquals(BackupProblem.OTHER_APP, refused("""{"profiles":[$p]}"""))
        assertEquals(BackupProblem.OTHER_APP, refused("""{"app":5,"schemaVersion":1,"profiles":[$p]}"""))
        assertIs<BackupResult.Ok>(Backup.validate(file(p)))
    }

    // SPEC:R131, R133
    @Test fun `R131 an encrypted file is ENCRYPTED until it is opened with its password`() {
        val plain = text(listOf(world()))
        val sealed = BackupCrypto.encrypt(plain, "correct horse", "the stable", nov26, iterations = 100_000)
        assertEquals(BackupProblem.ENCRYPTED, refused(sealed))
        val opened = assertIs<BackupCrypto.Opened.Plain>(BackupCrypto.decrypt(sealed, "correct horse")).text
        assertEquals(world(), ok(opened).profiles.single())
    }

    // SPEC:R131
    @Test fun `R131 a Profile whose goals are oops, or whose Savings is lots, 12_5 or 99999999999999999999, is DAMAGED`() {
        for (field in listOf(""""goals":"oops"""", """"savings":"lots"""", """"savings":12.5""", """"savings":99999999999999999999""", """"savings":{}""")) {
            assertEquals(BackupProblem.DAMAGED, refused(file("""{"id":"p","name":"Mine",$field}""")), field)
        }
    }

    // SPEC:R131, R23
    @Test fun `R131 a 42_30 payment with parts of 30_00 and 12_00 is DAMAGED, and so are parts that add up only past the top of a Long`() {
        val d = world()
        val short = d.copy(payments = listOf(receiptPayment.copy(parts = listOf(Part("groceries", cents("30.00")), Part("household", cents("12.00"))))))
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(short))))
        val none = d.copy(payments = listOf(receiptPayment.copy(total = 0, parts = emptyList())))
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(none))))
        // Long.MAX_VALUE twice wraps to -2
        val wrapped = d.copy(payments = listOf(chargedPayment.copy(total = -2, parts = listOf(Part(null, Long.MAX_VALUE), Part(null, Long.MAX_VALUE)))))
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(wrapped))))
    }

    // SPEC:R131
    @Test fun `R131 an id used twice by one kind of record across the file is DAMAGED`() {
        val a = world()
        val b = other().copy(goals = listOf(goal("trip", "Car").copy(profileId = "p-2")))
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(a, b))), "a goal id in two Profiles")
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(a, a))), "one Profile twice")
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(a, other().copy(payments = listOf(chargedPayment.copy(profileId = "p-2")))))), "a payment id twice")
        val (afterDelete, item) = trashedPayment(a)
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(afterDelete), listOf(item, item))), "a Trash item twice")
        assertIs<BackupResult.Ok>(Backup.validate(text(listOf(a, other()))))
    }

    // SPEC:R131
    @Test fun `R131 damaged content is DAMAGED, every case, and none of them throws`() {
        val ok = """{"id":"p","name":"Mine"}"""
        val cases = mapOf(
            "no profiles key" to """{"app":"tesoriere","schemaVersion":1}""",
            "no Profile" to file(),
            "profiles not a list" to """{"app":"tesoriere","schemaVersion":1,"profiles":{"id":"p"}}""",
            "a Profile that is a number" to file("5"),
            "no id" to file("""{"name":"Mine"}"""),
            "a blank id" to file("""{"id":" ","name":"Mine"}"""),
            "no name" to file("""{"id":"p"}"""),
            "a name that is a number" to file("""{"id":"p","name":7}"""),
            "no schema version" to """{"app":"tesoriere","profiles":[$ok]}""",
            "a schema version in quotes" to """{"app":"tesoriere","schemaVersion":"1","profiles":[$ok]}""",
            "schema version 0" to """{"app":"tesoriere","schemaVersion":0,"profiles":[$ok]}""",
            "schema version 1.5" to """{"app":"tesoriere","schemaVersion":1.5,"profiles":[$ok]}""",
            "a huge negative schema version" to """{"app":"tesoriere","schemaVersion":-99999999999999999999,"profiles":[$ok]}""",
            "settings that are a number" to file("""{"id":"p","name":"Mine","settings":5}"""),
            "an unknown cadence" to file("""{"id":"p","name":"Mine","recurringExpenses":[{"id":"r","profileId":"p","name":"Rent","amount":100,"cadence":"hourly","categoryId":"c","firstDueDate":"2026-01-01","dueDate":"2026-01-01"}]}"""),
            "a day that is not one" to file("""{"id":"p","name":"Mine","shifts":[{"id":"s","profileId":"p","date":"2026-02-30","hours":1.0,"rate":100}]}"""),
            "a ledger key the ledger cannot read" to file("""{"id":"p","name":"Mine","transactions":[{"id":"t","profileId":"p","date":"2026-11-10","loggedAt":0,"amount":500,"key":"wallet:1","targetLabel":"Wallet"}]}"""),
            "a payment source that is none" to file("""{"id":"p","name":"Mine","payments":[{"id":"x","profileId":"p","date":"2026-11-10","loggedAt":0,"total":100,"source":"wallet","payee":null,"parts":[{"categoryId":"c","amount":100}]}]}"""),
            "a goal with no id" to file("""{"id":"p","name":"Mine","goals":[{"profileId":"p","name":"Trip"}]}"""),
            "a goal of null" to file("""{"id":"p","name":"Mine","goals":[null]}"""),
            "receipt settings that are a list" to file("""{"id":"p","name":"Mine","receiptSettings":[]}"""),
            "an open Profile that is a number" to file(ok, extra = ""","activeProfileId":5"""),
            "an export day that is not one" to file(ok, extra = ""","exportedAt":"2026-13-01""""),
            "a Trash that is not a list" to file(ok, extra = ""","trash":"none""""),
            "a Trash item with no payload" to file(ok, extra = ""","trash":[{"id":"t","profileId":"p","kind":"goal","name":"Trip","deletedOn":"2026-11-10"}]"""),
            "a Trash item whose day is not one" to file(ok, extra = ""","trash":[{"id":"t","profileId":"p","kind":"goal","name":"Trip","deletedOn":"10 Nov","payload":"{}"}]"""),
            "a Trash item whose payload is not one" to file(ok, extra = ""","trash":[{"id":"t","profileId":"p","kind":"goal","name":"Trip","deletedOn":"2026-11-10","payload":"[[1]]"}]"""),
        )
        for ((case, t) in cases) assertEquals(BackupProblem.DAMAGED, refused(t), case)
    }

    // SPEC:R131
    @Test fun `R131 100000 nested arrays are DAMAGED and do not throw, while brackets inside a text are only text`() {
        val deep = "[".repeat(100_000) + "]".repeat(100_000)
        assertEquals(BackupProblem.DAMAGED, refused(file("""{"id":"p","name":"Mine","goals":$deep}""")))
        assertEquals(BackupProblem.NOT_A_BACKUP, refused("[".repeat(100_000)))
        val bracketed = "[".repeat(200)
        val b = ok(file("""{"id":"p","name":"$bracketed"}"""))
        assertEquals(bracketed, b.profiles.single().profile.name)
    }

    // SPEC:R131
    @Test fun `R131 an absent field takes its default - a Profile with no goals key reads with no Goals`() {
        val b = ok(file("""{"id":"p","name":"Mine"}"""))
        val d = b.profiles.single()
        assertTrue(d.goals.isEmpty()); assertNull(d.savings.amount); assertNull(d.receiptSettings)
        assertEquals("p", d.settings.profileId); assertEquals("p", d.importState.profileId); assertTrue(d.importState.importedKeys.isEmpty())
        assertEquals("p", b.activeProfileId); assertNull(b.exportedAt); assertTrue(b.trash.isEmpty())
    }

    // SPEC:R131
    @Test fun `R131 each record takes its Profile's id, and an open Profile not in the file gives way to the first`() {
        val p = """{"id":"p","name":"Mine","settings":{"profileId":"elsewhere"},"goals":[{"id":"g","profileId":"elsewhere","name":"Trip"}],""" +
            """"receiptSettings":{"profileId":"elsewhere","folder":"folder-handle-2"},""" +
            """"payments":[{"id":"x","profileId":"elsewhere","date":"2026-11-10","loggedAt":0,"total":100,"source":"savings","payee":null,"parts":[{"categoryId":"c","amount":100}]}]}"""
        val b = ok(file(p, extra = ""","activeProfileId":"gone","receiptProfileId":"p""""))
        val d = b.profiles.single()
        assertEquals("p", d.settings.profileId); assertEquals("p", d.goals.single().profileId)
        assertEquals("p", d.receiptSettings?.profileId); assertEquals("p", d.payments.single().profileId)
        assertEquals("p", b.activeProfileId)
    }

    // SPEC:R131, R126
    @Test fun `R131 a Goal deleted from Profile B, then B deleted, reads back as two items`() {
        val (b, goalItem) = goalOfB()
        val trash = listOf(goalItem, profileItem(b))
        val back = ok(text(listOf(world()), trash))
        assertEquals(trash, back.trash); assertEquals(0, back.trashLeftOut)
    }

    // SPEC:R131, R126
    @Test fun `R131 with B's own item gone for good, the Goal's item is left out and the rest of the file reads`() {
        val (_, goalItem) = goalOfB()
        val (d, paymentItem) = trashedPayment(world())
        val back = ok(text(listOf(d), listOf(paymentItem, goalItem)))
        assertEquals(listOf("tr-1"), back.trash.map { it.id }); assertEquals(1, back.trashLeftOut)
        assertEquals(d, back.profiles.single())
    }

    // SPEC:R131, R126
    @Test fun `R131 a deleted Profile whose id is a Profile in the file, or whose records share an id with the file's, is DAMAGED`() {
        val d = world()
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d), listOf(profileItem(Fixture.profile())))), "its id is live")
        val twin = ProfileData.fresh("p-9", "Old").copy(goals = listOf(goal("trip", "Trip").copy(profileId = "p-9")))
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d), listOf(profileItem(twin)))), "a goal id of the file's")
        val pays = ProfileData.fresh("p-9", "Old").copy(payments = listOf(chargedPayment.copy(profileId = "p-9")))
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d), listOf(profileItem(pays)))), "a payment id of the file's")
        val old = ProfileData.fresh("p-9", "Old")
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d), listOf(profileItem(old), profileItem(old, id = "tr-q")))), "one Profile deleted twice")
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d), listOf(profileItem(old, itemProfile = "p-8")))), "an item naming another Profile")
        val bad = old.copy(payments = listOf(chargedPayment.copy(id = "pay-9", profileId = "p-9", total = cents("10.00"))))
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d), listOf(profileItem(bad)))), "its payment does not add up")
        assertIs<BackupResult.Ok>(Backup.validate(text(listOf(d), listOf(profileItem(old)))))
    }

    // SPEC:R131, R23
    @Test fun `R131 a 0_00 payment of 5_00 and -5_00, a 10_00 one of 12_00 and -2_00, and two difference parts are DAMAGED`() {
        val d = world()
        fun with(p: Payment) = text(listOf(d.copy(payments = listOf(p))))
        assertEquals(BackupProblem.DAMAGED, refused(with(chargedPayment.copy(total = 0, parts = listOf(Part("fun", cents("5.00")), Part("fun", cents("-5.00")))))))
        assertEquals(BackupProblem.DAMAGED, refused(with(chargedPayment.copy(total = cents("10.00"), parts = listOf(Part("fun", cents("12.00")), Part("fun", cents("-2.00")))))))
        val twoDifferences = listOf(Part("fun", cents("10.00")), Part("fun", cents("0.20"), difference = true), Part("fun", cents("-0.10"), difference = true))
        assertEquals(BackupProblem.DAMAGED, refused(with(chargedPayment.copy(total = cents("10.10"), parts = twoDifferences))))
        assertEquals(BackupProblem.DAMAGED, refused(with(chargedPayment.copy(total = cents("10.00"), parts = listOf(Part("fun", cents("10.00")), Part("fun", 0L))))), "a part of zero")
        // a pair's one difference part may have the other sign (SPEC R39)
        val pair = chargedPayment.copy(total = cents("42.10"), parts = listOf(Part("groceries", cents("30.00")), Part("household", cents("12.30")), Part("fun", cents("-0.20"), difference = true)))
        assertEquals(pair, ok(with(pair)).profiles.single().payments.single())
        // a refund's parts all negative
        val refund = chargedPayment.copy(total = cents("-15.00"), parts = listOf(Part("fun", cents("-15.00"))))
        assertEquals(refund, ok(with(refund)).profiles.single().payments.single())
    }

    // SPEC:R131, R23, R51, R122
    @Test fun `R131 payments and rows held in the Trash are checked as the file's own`() {
        val d = world()
        val unpaired = chargedPayment.copy(id = "pay-z", total = cents("10.00"))
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d), listOf(item(TrashPayload(payments = listOf(unpaired)))))), "a deleted payment that does not add up")
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d), listOf(item(TrashPayload(payments = listOf(receiptPayment)))))), "a deleted payment whose id is the file's")
        val row = d.transactions.first()
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d), listOf(item(TrashPayload(transactions = listOf(row)))))), "a removed row whose id is the file's")
        val wallet = row.copy(id = "tx-9", key = "wallet:1")
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d), listOf(item(TrashPayload(transactions = listOf(wallet)))))), "a removed row of no key")
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d), listOf(item(TrashPayload(written = listOf(wallet)))))), "a written row of no key")
        val two = item(TrashPayload(payments = listOf(unpaired.copy(total = cents("9.90")))))
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d), listOf(two, two.copy(id = "tr-y")))), "one payment held in two items")
        // the rows a delete wrote stay in the ledger, so they share ids with it (SPEC R122)
        assertIs<BackupResult.Ok>(Backup.validate(text(listOf(d), listOf(item(TrashPayload(written = listOf(row)))))))
    }

    // SPEC:R131, R112
    @Test fun `R131 a receipt Profile not in the file, or a folder held by a Profile not named as it, or by two, is DAMAGED`() {
        val d = world()
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d), receipt = null)), "the folder's Profile not named")
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d, other()), receipt = "p-2")), "another Profile named")
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(other()), receipt = "p-7")), "a Profile not in the file")
        val second = other().copy(receiptSettings = ReceiptSettings("p-2", folder = "folder-handle-2"))
        assertEquals(BackupProblem.DAMAGED, refused(text(listOf(d, second), receipt = Fixture.PROFILE)), "two folders")
        assertEquals(BackupProblem.DAMAGED, refused(file("""{"id":"p","name":"Mine"}""", extra = ""","receiptProfileId":5""")), "a number")
    }

    // SPEC:R131
    @Test fun `R131 null stands for nothing only where nothing may be held, and an encrypted mark of false is a plain file`() {
        val p = """{"id":"p","name":"Mine"}"""
        for (field in listOf(""""goals":null""", """"settings":null""", """"importState":null""", """"payments":null""")) {
            assertEquals(BackupProblem.DAMAGED, refused(file("""{"id":"p","name":"Mine",$field}""")), field)
        }
        for (extra in listOf(""","trash":null""", ""","activeProfileId":null""", ""","exportedAt":null""")) {
            assertEquals(BackupProblem.DAMAGED, refused(file(p, extra = extra)), extra)
        }
        val d = ok(file("""{"id":"p","name":"Mine","savings":null,"receiptSettings":null}""", extra = ""","receiptProfileId":null""")).profiles.single()
        assertNull(d.savings.amount); assertNull(d.receiptSettings)
        assertEquals("Mine", ok(file(p, extra = ""","encrypted":false""")).profiles.single().profile.name)
        assertEquals(BackupProblem.ENCRYPTED, refused(file(p, extra = ""","encrypted":1""")))
    }

    private fun collectKeys(e: JsonElement, into: MutableSet<String>) {
        when (e) {
            is JsonObject -> for ((k, v) in e) { into += k; collectKeys(v, into) }
            is JsonArray -> e.forEach { collectKeys(it, into) }
            is JsonPrimitive -> {}
        }
    }

    private fun fieldsOf(c: Class<*>): Set<String> = c.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }.map { it.name }.toSet()
}
