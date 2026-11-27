package com.hazia.tesoriere

import com.hazia.tesoriere.data.Export
import com.hazia.tesoriere.data.ProfileDelete
import com.hazia.tesoriere.data.ProfileProblem
import com.hazia.tesoriere.data.TrashRestore
import com.hazia.tesoriere.data.ProfileRepository
import com.hazia.tesoriere.data.StoredKind
import com.hazia.tesoriere.data.UnreadableRecord
import com.hazia.tesoriere.data.Update
import com.hazia.tesoriere.data.TesoriereDatabase
import com.hazia.tesoriere.data.buildTesoriereDatabase
import com.hazia.tesoriere.engine.Applied
import com.hazia.tesoriere.engine.ApplyProblem
import com.hazia.tesoriere.engine.Backup
import com.hazia.tesoriere.engine.BackupResult
import com.hazia.tesoriere.engine.Trash
import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.ImportState
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.Settings
import com.hazia.tesoriere.payments.Part
import com.hazia.tesoriere.payments.Payment
import com.hazia.tesoriere.payments.PaymentSource
import com.hazia.tesoriere.receipts.ReceiptSettings
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * What a write may and may not do to what is stored (SPEC R141, R143): damage is never deleted, a
 * write from a stale copy or of an id another Profile holds is refused, and a step and its Trash
 * change land together or not at all. Every name and figure here is made up.
 */
@OptIn(ExperimentalTime::class)
class StorageSafetyTest {
    private val nov26 = LocalDate(2026, 11, 26)
    private val clock = TestClock(nov26)
    private val at = Instant.parse("2026-11-20T09:00:00Z")

    private fun <T> withRepo(file: File, block: suspend (ProfileRepository, TesoriereDatabase) -> T): T {
        val db = buildTesoriereDatabase(file)
        try {
            return runBlocking { block(ProfileRepository(db, clock, Ids("s")::next), db) }
        } finally {
            db.close()
        }
    }

    private fun payment(id: String, amount: String) =
        Payment(id, "p-a", nov26, at, cents(amount), PaymentSource.Savings, "Shop", listOf(Part("groc", cents(amount))))

    /** Profile A with Savings of 1,000.00, Groceries, a Goal, two payments, import memory and receipt settings. */
    private fun profileA(d: ProfileData) = d.copy(
        savings = d.savings.copy(amount = cents("1,000.00")),
        settings = d.settings.copy(currency = "CHF", accountsEnabled = true),
        importState = ImportState("p-a", importedKeys = listOf("syn:2026-11-20|-12.00|SHOP"), payeeRules = mapOf("shop" to "groc")),
        categories = listOf(Category("groc", "p-a", "Groceries")),
        goals = listOf(Goal("car", "p-a", "Car", saved = cents("300.00"))),
        payments = listOf(payment("pay-1", "12.00"), payment("pay-2", "30.00").copy(receiptId = "rc-1")),
        receiptSettings = ReceiptSettings("p-a", recordedReceiptIds = listOf("rc-1")),
    )

    // SPEC:R141
    @Test fun `R141 a payment whose parts cannot be read is kept by the next write and reads back once mended`() {
        val file = TestDb.file()
        val a0 = withRepo(file) { repo, _ -> profileA(repo.createProfile("A", "p-a")).also { repo.save(it) } }
        TestDb.damage(file, "UPDATE payments SET parts = '[{oops' WHERE id = 'pay-2'")
        withRepo(file) { repo, _ ->
            val read = repo.load("p-a")!!
            assertEquals(listOf("pay-1"), read.payments.map { it.id })
            repo.save(read.copy(savings = read.savings.copy(amount = cents("990.00"))))
        }
        TestDb.damage(file, """UPDATE payments SET parts = '[{"categoryId":"groc","amount":3000}]' WHERE id = 'pay-2'""")
        withRepo(file) { repo, _ ->
            val d = repo.load("p-a")!!
            assertEquals(a0.payments, d.payments.sortedBy { it.id }, "the mended row reads back; its place in the list is not kept")
            assertEquals(cents("990.00"), d.savings.amount)
        }
    }

    // SPEC:R141 SPEC:R26 SPEC:R112
    @Test fun `R141 settings, import memory and receipt settings that cannot be read are kept by a write that holds only their defaults`() {
        val file = TestDb.file()
        val a0 = withRepo(file) { repo, _ -> profileA(repo.createProfile("A", "p-a")).also { repo.save(it) } }
        TestDb.damage(
            file,
            "UPDATE settings SET incomeCadence = 'never' WHERE profileId = 'p-a'",
            "UPDATE import_state SET importedKeys = 'not a list' WHERE profileId = 'p-a'",
            "UPDATE receipt_settings SET pickup = 'hourly' WHERE profileId = 'p-a'",
        )
        withRepo(file) { repo, _ ->
            val read = repo.load("p-a")!!
            assertEquals(Settings(profileId = "p-a"), read.settings)
            assertEquals(ImportState(profileId = "p-a"), read.importState)
            assertNull(read.receiptSettings)
            repo.save(read.copy(savings = read.savings.copy(amount = cents("990.00"))))
        }
        TestDb.damage(
            file,
            "UPDATE settings SET incomeCadence = 'biweekly' WHERE profileId = 'p-a'",
            """UPDATE import_state SET importedKeys = '["syn:2026-11-20|-12.00|SHOP"]' WHERE profileId = 'p-a'""",
            "UPDATE receipt_settings SET pickup = 'onOpen' WHERE profileId = 'p-a'",
        )
        withRepo(file) { repo, _ ->
            val d = repo.load("p-a")!!
            assertEquals(a0.settings, d.settings)
            assertEquals(a0.importState, d.importState)
            assertEquals(a0.receiptSettings, d.receiptSettings)
        }
    }

    // SPEC:R141 SPEC:R20
    @Test fun `R141 Profile B written with a Goal whose id Profile A holds is refused and A keeps its Goal`() {
        withRepo(TestDb.file()) { repo, _ ->
            repo.save(profileA(repo.createProfile("A", "p-a")))
            val b = repo.createProfile("B", "p-b")
            assertEquals(ProfileProblem.ID_IN_USE, repo.save(b.copy(savings = b.savings.copy(amount = 1), goals = listOf(Goal("car", "p-b", "Not mine")))))
            assertEquals(listOf("Car"), repo.load("p-a")!!.goals.map { it.name })
            assertEquals(b, repo.load("p-b"), "nothing of the refused write landed")
            val (_, payload) = assertNotNull(Trash.deleteCategory(repo.load("p-a")!!, "groc"))
            assertNull(repo.saveDeleted(b.copy(goals = listOf(Goal("car", "p-b", "Not mine"))), "category", "Groceries", payload))
            assertTrue(repo.trash().isEmpty())
        }
    }

    // SPEC:R141 SPEC:R126 SPEC:R131
    @Test fun `R141 Profile B deleted to the Trash and written from a copy read before is refused and stays in the Trash alone`() {
        withRepo(TestDb.file()) { repo, _ ->
            repo.createProfile("A", "p-a")
            val stale = repo.createProfile("B", "p-b")
            repo.deleteProfile("p-b")
            assertEquals(ProfileProblem.NO_SUCH_PROFILE, repo.save(stale))
            assertEquals(ProfileProblem.NO_SUCH_PROFILE, repo.save(repo.appState()!!, stale))
            assertEquals(Update.Refused(ProfileProblem.NO_SUCH_PROFILE), repo.update("p-b") { it })
            assertNull(repo.load("p-b"))
            assertEquals(listOf("A"), repo.profiles().map { it.name })
            assertEquals(listOf("p-b"), repo.trash().map { it.profileId })
            assertIs<Export.Ok>(repo.backupFile(nov26))
        }
    }

    // SPEC:R141 SPEC:R121
    @Test fun `R141 a step run through update on the Profile as stored never brings back a Goal deleted since a copy was read`() {
        withRepo(TestDb.file()) { repo, _ ->
            val stale = profileA(repo.createProfile("A", "p-a")).also { repo.save(it) }
            val item = assertNotNull(repo.deleteToTrash("p-a", "goal", "Car") { d -> Trash.deleteGoal(d, "car", returnSaved = true, clock, Ids("g")::next) })
            assertEquals("p-a", item.profileId)
            assertEquals(cents("1,300.00"), repo.load("p-a")!!.savings.amount)
            val saved = assertIs<Update.Saved>(repo.update("p-a") { it.copy(savings = it.savings.copy(amount = it.savings.amount!! - cents("10.00"))) }).data
            assertTrue(saved.goals.isEmpty())
            assertEquals(saved, repo.load("p-a"))
            assertEquals(cents("1,290.00"), saved.savings.amount)
            assertIs<Export.Ok>(repo.backupFile(nov26))
            // a delete that deletes nothing writes nothing
            assertNull(repo.deleteToTrash("p-a", "goal", "Car") { d -> Trash.deleteGoal(d, "car", returnSaved = true, clock, Ids("h")::next) })
            assertEquals(1, repo.trash().size)
            assertTrue(stale.goals.isNotEmpty())
        }
    }

    // SPEC:R130 SPEC:R131
    @Test fun `R130 a device holding Profile B both in the Trash and among its Profiles exports B and leaves its Trash item out, counted`() {
        withRepo(TestDb.file()) { repo, db ->
            repo.createProfile("A", "p-a")
            val stale = repo.createProfile("B", "p-b")
            repo.deleteProfile("p-b")
            db.dao().save(stale)
            val ok = assertIs<Export.Ok>(repo.backupFile(nov26))
            assertEquals(1, ok.leftOut)
            assertEquals(0, ok.repaired)
            assertEquals(listOf("A", "B"), ok.file.profiles.map { it.name })
            assertTrue(ok.file.trash.isEmpty())
        }
    }

    // SPEC:R141 SPEC:R122
    @Test fun `R141 a restore whose removal of its item fails leaves the Goal in the Trash and out of the Profile`() {
        val file = TestDb.file()
        val itemId = withRepo(file) { repo, _ ->
            val a = profileA(repo.createProfile("A", "p-a")).also { repo.save(it) }
            val (after, p) = assertNotNull(Trash.deleteGoal(a, "car", returnSaved = true, clock, Ids("g")::next))
            repo.saveDeleted(after, "goal", "Car", p)!!.id
        }
        TestDb.damage(file, "CREATE TRIGGER keep_trash BEFORE DELETE ON trash BEGIN SELECT RAISE(ABORT, 'x'); END")
        withRepo(file) { repo, _ ->
            assertFails { repo.restore(itemId) }
            assertTrue(repo.load("p-a")!!.goals.isEmpty())
            assertEquals(cents("1,300.00"), repo.load("p-a")!!.savings.amount)
            assertEquals(listOf(itemId), repo.trash().map { it.id })
        }
    }

    // SPEC:R141 SPEC:R121
    @Test fun `R141 a delete whose Trash item cannot be written leaves the Goal and the Profile as they were`() {
        val file = TestDb.file()
        val a = withRepo(file) { repo, _ -> profileA(repo.createProfile("A", "p-a")).also { repo.save(it); repo.createProfile("B", "p-b") } }
        TestDb.damage(file, "CREATE TRIGGER no_trash BEFORE INSERT ON trash BEGIN SELECT RAISE(ABORT, 'x'); END")
        withRepo(file) { repo, _ ->
            val (after, p) = assertNotNull(Trash.deleteGoal(a, "car", returnSaved = true, clock, Ids("g")::next))
            assertFails { repo.saveDeleted(after, "goal", "Car", p) }
            assertEquals(a, repo.load("p-a"))
            assertFails { repo.deleteProfile("p-a") }
            assertEquals(a, repo.load("p-a"))
            assertEquals(listOf("A", "B"), repo.profiles().map { it.name })
            assertTrue(repo.trash().isEmpty())
        }
    }

    // SPEC:R143 SPEC:R20
    @Test fun `R143 a file of C holding a Goal id that device Profile A holds restores, C holding the Goal and A gone`() {
        withRepo(TestDb.file()) { repo, db ->
            repo.save(profileA(repo.createProfile("A", "p-a")))
            val c = ProfileData.fresh("p-c", "C").copy(goals = listOf(Goal("car", "p-c", "C's car", saved = cents("50.00"))))
            val text = Backup.encode(Backup.build(listOf(c), emptyList(), "p-c", null, nov26))
            Backup.apply(db, assertIs<BackupResult.Ok>(Backup.validate(text)).backup, nov26)
            assertEquals(listOf("C"), repo.profiles().map { it.name })
            assertEquals(listOf("C's car"), repo.load("p-c")!!.goals.map { it.name })
            assertNull(repo.load("p-a"))
        }
    }

    // SPEC:R141 SPEC:R122
    @Test fun `R141 a Goal restored after a screen read its Profile stays when that screen then takes 10_00 from Savings`() {
        withRepo(TestDb.file()) { repo, _ ->
            repo.save(profileA(repo.createProfile("A", "p-a")))
            val item = assertNotNull(repo.deleteToTrash("p-a", "goal", "Car") { d -> Trash.deleteGoal(d, "car", returnSaved = true, clock, Ids("g")::next) })
            val screen = repo.load("p-a")!!
            assertIs<TrashRestore.Restored>(repo.restore(item.id))
            // the screen's edit goes through update, from the Profile as stored, so the restored Goal stays (SPEC R141)
            assertTrue(screen.goals.isEmpty())
            assertIs<Update.Saved>(repo.update("p-a") { it.copy(savings = it.savings.copy(amount = it.savings.amount!! - cents("10.00"))) })
            val d = repo.load("p-a")!!
            assertEquals(listOf("car"), d.goals.map { it.id })
            assertEquals(cents("990.00"), d.savings.amount)
        }
    }

    // SPEC:R141 SPEC:R126 SPEC:R130
    @Test fun `R141 Profile A's copy naming the folder, written after A came back from the Trash without it, never stops an export`() {
        withRepo(TestDb.file()) { repo, _ ->
            repo.createProfile("B", "p-b")
            val a = repo.createProfile("A", "p-a")
            val (app, withFolder) = com.hazia.tesoriere.receipts.Receipts.chooseFolder(repo.appState()!!, a, "folder-handle-1")
            repo.save(app, withFolder)
            val screen = repo.load("p-a")!!
            repo.deleteProfile("p-a")
            val (app2, b) = com.hazia.tesoriere.receipts.Receipts.chooseFolder(repo.appState()!!, repo.load("p-b")!!, "folder-handle-2")
            repo.save(app2, b)
            assertIs<TrashRestore.Restored>(repo.restore(repo.trash().single().id))
            assertNull(repo.load("p-a")!!.receiptSettings!!.folder)
            assertNotNull(screen.receiptSettings!!.folder)
            assertIs<Update.Saved>(repo.update("p-a") { it.copy(profile = it.profile.copy(name = "A again")) })
            assertIs<Export.Ok>(repo.backupFile(nov26))
            assertNull(repo.load("p-a")!!.receiptSettings!!.folder)
        }
    }

    // SPEC:R141 SPEC:R122 SPEC:R20
    @Test fun `R141 a Goal car deleted from A, then a Goal car given to B, refuses the restore with ID_IN_USE and the item stays`() {
        withRepo(TestDb.file()) { repo, _ ->
            repo.save(profileA(repo.createProfile("A", "p-a")))
            repo.createProfile("B", "p-b")
            val item = assertNotNull(repo.deleteToTrash("p-a", "goal", "Car") { d -> Trash.deleteGoal(d, "car", returnSaved = true, clock, Ids("g")::next) })
            assertIs<Update.Saved>(repo.update("p-b") { it.copy(goals = listOf(Goal("car", "p-b", "B's car"))) })
            val a = repo.load("p-a")
            assertEquals(TrashRestore.WriteRefused(ProfileProblem.ID_IN_USE), repo.restore(item.id))
            assertEquals(listOf(item.id), repo.trash().map { it.id })
            assertEquals(a, repo.load("p-a"))
            assertEquals(listOf("B's car"), repo.load("p-b")!!.goals.map { it.name })
        }
    }

    // SPEC:R141
    @Test fun `R141 a step giving Profile B two Goals of one id is refused with a code and writes nothing`() {
        withRepo(TestDb.file()) { repo, _ ->
            val b = repo.createProfile("B", "p-b")
            val twice = listOf(Goal("g1", "p-b", "One"), Goal("g1", "p-b", "Two"))
            assertEquals(Update.Refused(ProfileProblem.ID_TWICE), repo.update("p-b") { it.copy(goals = twice) })
            assertEquals(ProfileProblem.ID_TWICE, repo.save(b.copy(goals = twice)))
            assertEquals(b, repo.load("p-b"))
        }
    }

    // SPEC:R130 SPEC:R131 SPEC:R141
    @Test fun `R130 an export leaves out a row whose key no longer reads and three Trash items, repairs parts that do not add up, and gives the rest`() {
        val file = TestDb.file()
        withRepo(file) { repo, db ->
            repo.createProfile("B", "p-b")
            val a = profileA(repo.createProfile("A", "p-a")).let { it.copy(transactions = listOf(
                com.hazia.tesoriere.model.Transaction("t-ok", "p-a", nov26, at, amount = cents("5.00"), key = "savings", targetLabel = "Savings"),
                com.hazia.tesoriere.model.Transaction("t-bad", "p-a", nov26, at, amount = cents("6.00"), key = "savings", targetLabel = "Savings"),
            )) }
            repo.save(a)
            val stale = repo.load("p-b")!!
            repo.deleteProfile("p-b")
            db.dao().save(stale)
            val (after, p) = assertNotNull(Trash.deleteCategory(repo.load("p-a")!!, "groc"))
            repo.saveDeleted(after, "category", "Groceries", p)
            repo.saveDeleted(repo.load("p-a")!!, "category", "Groceries", p)
        }
        TestDb.damage(
            file,
            "UPDATE transactions SET key = 'nowhere:at:all' WHERE id = 't-bad'",
            """UPDATE payments SET parts = '[{"categoryId":"groc","amount":100}]' WHERE id = 'pay-2'""",
            "UPDATE trash SET payload = '{oops' WHERE id = (SELECT id FROM trash WHERE kind = 'category' ORDER BY id LIMIT 1)",
            "UPDATE trash SET deletedOn = 'someday' WHERE id = (SELECT id FROM trash WHERE kind = 'category' ORDER BY id DESC LIMIT 1)",
        )
        withRepo(file) { repo, _ ->
            val ok = assertIs<Export.Ok>(repo.backupFile(nov26))
            assertEquals(4, ok.leftOut)
            assertEquals(1, ok.repaired)
            val back = assertIs<BackupResult.Ok>(Backup.validate(ok.text)).backup
            assertEquals(listOf("A", "B"), back.profiles.map { it.profile.name })
            val a = back.profiles.first { it.profileId == "p-a" }
            assertEquals(listOf("t-ok"), a.transactions.map { it.id })
            assertEquals(listOf("pay-1", "pay-2"), a.payments.map { it.id })
            assertEquals(listOf(Part(null, cents("30.00"))), a.payments[1].parts)
            assertEquals("rc-1", a.payments[1].receiptId)
            assertTrue(back.trash.isEmpty())
        }
    }

    // SPEC:R130 SPEC:R143 SPEC:R99
    @Test fun `R130 a 30_00 payment whose parts do not add up comes back from export and restore with Savings and every total as on the device`() {
        val file = TestDb.file()
        withRepo(file) { repo, _ -> repo.save(profileA(repo.createProfile("A", "p-a"))) }
        TestDb.damage(file, """UPDATE payments SET parts = '[{"categoryId":"groc","amount":100,"difference":true}]' WHERE id = 'pay-2'""")
        val (text, before) = withRepo(file) { repo, _ -> assertIs<Export.Ok>(repo.backupFile(nov26)).text to repo.load("p-a")!! }
        withRepo(TestDb.file()) { repo, db ->
            Backup.apply(db, assertIs<BackupResult.Ok>(Backup.validate(text)).backup, nov26)
            val d = repo.load("p-a")!!
            assertEquals(before.savings, d.savings)
            assertEquals(before.goals, d.goals)
            assertEquals(before.payments.map { it.id to it.total }, d.payments.map { it.id to it.total })
            assertEquals(listOf(Part(null, cents("30.00"))), d.payments.single { it.id == "pay-2" }.parts)
            assertEquals(d.payments.sumOf { it.total }, d.payments.sumOf { p -> p.parts.sumOf { it.amount } })
            assertEquals(listOf("rc-1"), d.receiptSettings!!.recordedReceiptIds)
        }
    }

    // SPEC:R130 SPEC:R143 SPEC:R126 SPEC:R23
    @Test fun `R130 a deleted Profile whose Trash item holds a 30_00 payment that does not add up exports, restores and comes back with it as one uncategorised part`() {
        val b = ProfileData.fresh("p-b", "B").let { d ->
            d.copy(
                categories = listOf(Category("b-groc", "p-b", "Groceries")),
                payments = listOf(
                    Payment("pay-b", "p-b", nov26, at, cents("30.00"), PaymentSource.Savings, "Shop", listOf(Part("b-groc", cents("1.00")))),
                    Payment("pay-b2", "p-b", nov26, at, cents("8.00"), PaymentSource.Savings, "Shop", listOf(Part("b-groc", cents("8.00")))),
                ),
            )
        }
        val item = com.hazia.tesoriere.model.TrashItem(
            "tr-b", "p-b", ProfileRepository.PROFILE_KIND, "B", "2026-11-20",
            Trash.encode(com.hazia.tesoriere.engine.TrashPayload(profile = com.hazia.tesoriere.engine.TrashedProfile.of(b))), warned = true,
        )
        val ok = withRepo(TestDb.file()) { repo, db ->
            repo.createProfile("A", "p-a")
            db.dao().insertTrashItem(item)
            assertIs<Export.Ok>(repo.backupFile(nov26))
        }
        assertEquals(0, ok.leftOut)
        assertEquals(1, ok.repaired)
        val kept = ok.file.trash.single()
        assertEquals(listOf("tr-b", "2026-11-20", "true"), listOf(kept.id, kept.deletedOn, kept.warned.toString()))
        val read = assertIs<BackupResult.Ok>(Backup.validate(ok.text)).backup
        withRepo(TestDb.file()) { repo, db ->
            assertEquals(Applied.Restored, Backup.apply(db, read, nov26))
            assertEquals(listOf("tr-b"), repo.trash().map { it.id })
            val back = assertIs<TrashRestore.Restored>(repo.restore("tr-b")).data
            assertEquals(listOf(Part(null, cents("30.00"))), back.payments.single { it.id == "pay-b" }.parts)
            assertEquals(listOf(Part("b-groc", cents("8.00"))), back.payments.single { it.id == "pay-b2" }.parts)
            assertEquals(listOf("A", "B"), repo.profiles().map { it.name })
            assertTrue(repo.trash().isEmpty())
        }
    }

    // SPEC:R130 SPEC:R112
    @Test fun `R130 a Profile that is not the receipt Profile but names a folder exports with no folder`() {
        withRepo(TestDb.file()) { repo, db ->
            val a = profileA(repo.createProfile("A", "p-a"))
            db.dao().save(a.copy(receiptSettings = a.receiptSettings!!.copy(folder = "folder-handle-9")))
            val ok = assertIs<Export.Ok>(repo.backupFile(nov26))
            assertNull(ok.file.profiles.single().receiptSettings!!.folder)
            assertEquals(0, ok.leftOut)
            assertEquals(1, ok.repaired)
            assertEquals("folder-handle-9", repo.load("p-a")!!.receiptSettings!!.folder, "the export writes nothing to the device")
        }
    }

    // SPEC:R130 SPEC:R131
    @Test fun `R130 a Trash item of a Profile gone for good is left out of the export and counted`() {
        withRepo(TestDb.file()) { repo, db ->
            repo.createProfile("A", "p-a")
            db.dao().insertTrashItem(com.hazia.tesoriere.model.TrashItem("tr-x", "p-gone", "category", "Fun", "2026-11-20",
                Trash.encode(com.hazia.tesoriere.engine.TrashPayload(categories = listOf(Category("fun", "p-gone", "Fun"))))))
            val ok = assertIs<Export.Ok>(repo.backupFile(nov26))
            assertTrue(ok.file.trash.isEmpty())
            assertEquals(1, ok.leftOut)
            assertEquals(0, ok.repaired)
        }
    }

    // SPEC:R130 SPEC:R141
    @Test fun `R130 a store of B and A whose app state row is gone exports both, A first by name as the open one, with no receipt Profile`() {
        val file = TestDb.file()
        withRepo(file) { repo, _ -> repo.createProfile("B", "p-b"); repo.save(profileA(repo.createProfile("A", "p-a"))) }
        TestDb.damage(file, "DELETE FROM app_state")
        withRepo(file) { repo, _ ->
            assertNull(repo.appState())
            val ok = assertIs<Export.Ok>(repo.backupFile(nov26))
            assertEquals("p-a", ok.file.activeProfileId)
            assertNull(ok.file.receiptProfileId)
            assertEquals(listOf("A", "B"), ok.file.profiles.map { it.name })
            assertEquals(0, ok.leftOut)
            assertNull(repo.appState(), "the export writes nothing to the device")
        }
    }

    // SPEC:R130 SPEC:R141 SPEC:R26
    @Test fun `R130 a store whose payment pay-2 and import memory cannot be read exports the rest, naming 2 records it could not read`() {
        val file = TestDb.file()
        withRepo(file) { repo, _ -> repo.save(profileA(repo.createProfile("A", "p-a"))) }
        TestDb.damage(
            file,
            "UPDATE payments SET parts = '[{oops' WHERE id = 'pay-2'",
            "UPDATE import_state SET importedKeys = 'not a list' WHERE profileId = 'p-a'",
        )
        withRepo(file) { repo, _ ->
            val ok = assertIs<Export.Ok>(repo.backupFile(nov26))
            assertEquals(
                listOf(UnreadableRecord("p-a", StoredKind.PAYMENT, "pay-2"), UnreadableRecord("p-a", StoredKind.IMPORT_MEMORY, null)),
                ok.unreadable,
            )
            assertEquals(0, ok.leftOut)
            assertEquals(0, ok.repaired)
            val a = assertIs<BackupResult.Ok>(Backup.validate(ok.text)).backup.profiles.single()
            assertEquals(listOf("pay-1"), a.payments.map { it.id }, "the file lacks the payment it could not read")
            assertTrue(a.importState.importedKeys.isEmpty())
        }
    }

    // SPEC:R143 SPEC:R141
    @Test fun `R143 a restore onto a store whose payment pay-2 cannot be read is refused with UNREADABLE_ON_DEVICE, changing nothing, until the user agrees`() {
        val file = TestDb.file()
        withRepo(file) { repo, _ ->
            repo.save(profileA(repo.createProfile("A", "p-a")))
            repo.createProfile("B", "p-b")
            assertNotNull(repo.deleteToTrash("p-a", "goal", "Car") { d -> Trash.deleteGoal(d, "car", returnSaved = true, clock, Ids("g")::next) })
        }
        TestDb.damage(file, "UPDATE payments SET parts = '[{oops' WHERE id = 'pay-2'")
        val c = Backup.encode(Backup.build(listOf(ProfileData.fresh("p-c", "C")), emptyList(), "p-c", null, nov26))
        val b = assertIs<BackupResult.Ok>(Backup.validate(c)).backup
        withRepo(file) { repo, db ->
            val before = listOf(repo.load("p-a"), repo.load("p-b"))
            val trash = repo.trash()
            val app = repo.appState()
            val refused = assertIs<Applied.Refused>(Backup.apply(db, b, nov26))
            assertEquals(ApplyProblem.UNREADABLE_ON_DEVICE, refused.problem)
            assertEquals(listOf(UnreadableRecord("p-a", StoredKind.PAYMENT, "pay-2")), refused.unreadable)
            assertEquals(listOf("A", "B"), repo.profiles().map { it.name })
            assertEquals(before, listOf(repo.load("p-a"), repo.load("p-b")))
            assertEquals(trash, repo.trash())
            assertEquals(app, repo.appState())
            assertEquals(listOf("pay-1", "pay-2"), db.dao().paymentIds("p-a"), "the payment it cannot read is still stored")

            assertEquals(Applied.Restored, Backup.apply(db, b, nov26, agreedToDelete = refused.unreadable))
            assertEquals(listOf("C"), repo.profiles().map { it.name })
            assertTrue(db.dao().paymentIds("p-a").isEmpty())
        }
    }

    // SPEC:R140 SPEC:R141 SPEC:R126
    @Test fun `R140 deleting Profile A whose payment pay-2 cannot be read is refused naming it, and goes ahead only with the agreement to what the store holds then`() {
        val file = TestDb.file()
        withRepo(file) { repo, _ -> repo.save(profileA(repo.createProfile("A", "p-a"))); repo.createProfile("B", "p-b") }
        TestDb.damage(file, "UPDATE payments SET parts = '[{oops' WHERE id = 'pay-2'")
        val pay2 = listOf(UnreadableRecord("p-a", StoredKind.PAYMENT, "pay-2"))
        withRepo(file) { repo, db ->
            val before = repo.load("p-a")
            val app = repo.appState()
            assertEquals(ProfileDelete.Refused(ProfileProblem.UNREADABLE_RECORDS, pay2), repo.deleteProfile("p-a"))
            assertEquals(listOf("A", "B"), repo.profiles().map { it.name })
            assertEquals(before, repo.load("p-a"))
            assertEquals(app, repo.appState())
            assertTrue(repo.trash().isEmpty())
            assertEquals(listOf("pay-1", "pay-2"), db.dao().paymentIds("p-a"))
        }
        TestDb.damage(file, "UPDATE payments SET parts = '[{oops' WHERE id = 'pay-1'")
        withRepo(file) { repo, db ->
            val both = listOf(UnreadableRecord("p-a", StoredKind.PAYMENT, "pay-1"), UnreadableRecord("p-a", StoredKind.PAYMENT, "pay-2"))
            assertEquals(ProfileDelete.Refused(ProfileProblem.UNREADABLE_RECORDS, both), repo.deleteProfile("p-a", agreedToDelete = pay2))
            assertTrue(repo.trash().isEmpty())
            assertEquals(ProfileDelete.Deleted, repo.deleteProfile("p-a", agreedToDelete = both))
            assertEquals(listOf("B"), repo.profiles().map { it.name })
            assertEquals("p-a", repo.trash().single().profileId)
            assertTrue(db.dao().paymentIds("p-a").isEmpty(), "the records agreed to go with the delete, held by no Trash item")
        }
    }

    /** [d] with a 0.20 APR card, a row of 4 hours worked and an 8-hour shift. */
    private fun withFigures(d: ProfileData) = d.copy(
        accounts = listOf(com.hazia.tesoriere.model.Account("card", d.profileId, "Card", type = com.hazia.tesoriere.model.AccountType.CREDIT, apr = 0.2)),
        transactions = listOf(com.hazia.tesoriere.model.Transaction("t-h", d.profileId, nov26, at, amount = cents("48.00"), key = "savings", targetLabel = "Savings", isIncome = true, hoursWorked = 4.0, rate = cents("12.00"))),
        shifts = listOf(com.hazia.tesoriere.model.Shift("s-1", d.profileId, nov26, 8.0, cents("12.00"))),
    )

    // SPEC:R141 SPEC:R130
    @Test fun `R141 an infinity stored in a card's APR, a row's hours worked and a shift's hours is named unreadable, never stops the export, and is kept by the next write`() {
        val file = TestDb.file()
        withRepo(file) { repo, _ -> repo.save(withFigures(profileA(repo.createProfile("A", "p-a")))) }
        TestDb.damage(
            file,
            "UPDATE accounts SET apr = 9e999 WHERE id = 'card'",
            "UPDATE transactions SET hoursWorked = -9e999 WHERE id = 't-h'",
            "UPDATE shifts SET hours = 9e999 WHERE id = 's-1'",
        )
        withRepo(file) { repo, db ->
            val d = repo.load("p-a")!!
            assertTrue(d.accounts.isEmpty() && d.transactions.isEmpty() && d.shifts.isEmpty())
            val ok = assertIs<Export.Ok>(repo.backupFile(nov26))
            assertEquals(
                listOf(
                    UnreadableRecord("p-a", StoredKind.ACCOUNT, "card"), UnreadableRecord("p-a", StoredKind.TRANSACTION, "t-h"),
                    UnreadableRecord("p-a", StoredKind.SHIFT, "s-1"),
                ),
                ok.unreadable,
            )
            assertIs<BackupResult.Ok>(Backup.validate(ok.text))
            assertIs<Update.Saved>(repo.update("p-a") { it.copy(savings = it.savings.copy(amount = cents("999.00"))) })
            assertEquals(listOf("card"), db.dao().accountIds("p-a"))
            assertEquals(listOf("t-h"), db.dao().transactionIds("p-a"))
            assertEquals(listOf("s-1"), db.dao().shiftIds("p-a"))
        }
    }

    // SPEC:R141
    @Test fun `R141 a write holding a shift of no number of hours, or a card of infinite APR, is refused with NOT_FINITE and writes nothing`() {
        withRepo(TestDb.file()) { repo, db ->
            repo.save(withFigures(profileA(repo.createProfile("A", "p-a"))))
            val before = repo.load("p-a")
            val nan = repo.update("p-a") { it.copy(shifts = it.shifts.map { s -> s.copy(hours = Double.NaN) }) }
            assertEquals(Update.Refused(ProfileProblem.NOT_FINITE), nan)
            val inf = repo.update("p-a") { it.copy(accounts = it.accounts.map { a -> a.copy(apr = Double.POSITIVE_INFINITY) }) }
            assertEquals(Update.Refused(ProfileProblem.NOT_FINITE), inf)
            assertEquals(before, repo.load("p-a"))
            assertEquals(8.0, db.dao().shift("s-1")!!.hours)
        }
    }

    // SPEC:R141 SPEC:R130
    @Test fun `R141 a recurring expense, settings and receipt settings that cannot be read are each named by their kind`() {
        val file = TestDb.file()
        withRepo(file) { repo, _ ->
            repo.save(profileA(repo.createProfile("A", "p-a")).let { d ->
                d.copy(recurringExpenses = listOf(com.hazia.tesoriere.model.RecurringExpense("r-1", "p-a", "Rent", amount = cents("500.00"),
                    cadence = com.hazia.tesoriere.model.Cadence.MONTHLY, categoryId = "groc", firstDueDate = nov26, dueDate = nov26)))
            })
        }
        TestDb.damage(
            file,
            "UPDATE recurring_expenses SET cadence = 'sometimes' WHERE id = 'r-1'",
            "UPDATE settings SET incomeCadence = 'often' WHERE profileId = 'p-a'",
            "UPDATE receipt_settings SET pickup = 'by owl' WHERE profileId = 'p-a'",
        )
        withRepo(file) { repo, _ ->
            val ok = assertIs<Export.Ok>(repo.backupFile(nov26))
            assertEquals(
                listOf(
                    UnreadableRecord("p-a", StoredKind.RECURRING_EXPENSE, "r-1"), UnreadableRecord("p-a", StoredKind.SETTINGS, null),
                    UnreadableRecord("p-a", StoredKind.RECEIPT_SETTINGS, null),
                ),
                ok.unreadable,
            )
        }
    }

    // SPEC:R121
    @Test fun `R121 delete forever takes the item out of the Trash, moves no money and leaves the other items`() {
        withRepo(TestDb.file()) { repo, _ ->
            repo.save(profileA(repo.createProfile("A", "p-a")))
            val car = assertNotNull(repo.deleteToTrash("p-a", "goal", "Car") { d -> Trash.deleteGoal(d, "car", returnSaved = true, clock, Ids("g")::next) })
            val groc = assertNotNull(repo.deleteToTrash("p-a", "category", "Groceries") { d -> Trash.deleteCategory(d, "groc") })
            val before = repo.load("p-a")
            repo.deleteForever(car.id)
            assertEquals(listOf(groc.id), repo.trash().map { it.id })
            assertEquals(before, repo.load("p-a"))
            repo.deleteForever("tr-none")
            assertEquals(listOf(groc.id), repo.trash().map { it.id })
            assertEquals(TrashRestore.NotRestorable, repo.restore(car.id))
        }
    }

    // SPEC:R143 SPEC:R141
    @Test fun `R143 an agreement naming pay-2 alone, once pay-1 too cannot be read, is refused naming both and writes nothing`() {
        val file = TestDb.file()
        withRepo(file) { repo, _ -> repo.save(profileA(repo.createProfile("A", "p-a"))) }
        TestDb.damage(file, "UPDATE payments SET parts = '[{oops' WHERE id = 'pay-2'")
        val b = assertIs<BackupResult.Ok>(Backup.validate(Backup.encode(Backup.build(listOf(ProfileData.fresh("p-c", "C")), emptyList(), "p-c", null, nov26)))).backup
        val agreed = withRepo(file) { _, db -> assertIs<Applied.Refused>(Backup.apply(db, b, nov26)).unreadable }
        assertEquals(listOf(UnreadableRecord("p-a", StoredKind.PAYMENT, "pay-2")), agreed)
        TestDb.damage(file, "UPDATE payments SET parts = '[{oops' WHERE id = 'pay-1'")
        withRepo(file) { repo, db ->
            val before = repo.load("p-a")
            val both = listOf(UnreadableRecord("p-a", StoredKind.PAYMENT, "pay-1"), UnreadableRecord("p-a", StoredKind.PAYMENT, "pay-2"))
            assertEquals(Applied.Refused(ApplyProblem.UNREADABLE_ON_DEVICE, both), Backup.apply(db, b, nov26, agreedToDelete = agreed))
            assertEquals(listOf("A"), repo.profiles().map { it.name })
            assertEquals(before, repo.load("p-a"))
            assertEquals(listOf("pay-1", "pay-2"), db.dao().paymentIds("p-a"))
            // agreeing to what the store holds now, the restore goes ahead
            assertEquals(Applied.Restored, Backup.apply(db, b, nov26, agreedToDelete = both))
            assertEquals(listOf("C"), repo.profiles().map { it.name })
        }
    }
}
