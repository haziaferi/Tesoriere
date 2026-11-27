package com.hazia.tesoriere

import com.hazia.tesoriere.data.ProfileProblem
import com.hazia.tesoriere.data.Export
import com.hazia.tesoriere.data.ProfileRepository
import com.hazia.tesoriere.data.StoredLock
import com.hazia.tesoriere.data.TesoriereDatabase
import com.hazia.tesoriere.data.TrashRestore
import com.hazia.tesoriere.data.Update
import com.hazia.tesoriere.data.buildInMemoryTesoriereDatabase
import com.hazia.tesoriere.data.buildTesoriereDatabase
import com.hazia.tesoriere.engine.AutoCopy
import com.hazia.tesoriere.engine.Backup
import com.hazia.tesoriere.engine.BackupResult
import com.hazia.tesoriere.engine.CopyEvery
import com.hazia.tesoriere.engine.LockAfter
import com.hazia.tesoriere.engine.LockState
import com.hazia.tesoriere.engine.Pin
import com.hazia.tesoriere.engine.RestoreProblem
import com.hazia.tesoriere.engine.SciFinImport
import com.hazia.tesoriere.engine.SciFinRead
import com.hazia.tesoriere.engine.Trash
import com.hazia.tesoriere.engine.TrashPayload
import com.hazia.tesoriere.engine.Writes
import com.hazia.tesoriere.model.Cadence
import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.TrashItem
import com.hazia.tesoriere.receipts.ReceiptFolder
import com.hazia.tesoriere.receipts.ReceiptPickup
import com.hazia.tesoriere.receipts.ReceiptProblem
import com.hazia.tesoriere.receipts.ReceiptSettings
import com.hazia.tesoriere.receipts.ParseResult
import com.hazia.tesoriere.receipts.Receipts
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/AppStoreTest.kt and
 * SettingsStoreTest.kt at 2892110: their store cases, through the repository. Dropped: routes, tabs,
 * the tour, notification syncs, appearance and Income's settings screens (SPEC R04, R16).
 * Every name and figure here is made up.
 */
class RepositoryTest {
    private val nov26 = LocalDate(2026, 11, 26)
    private val clock = TestClock(nov26)

    private fun <T> withRepo(db: TesoriereDatabase = buildInMemoryTesoriereDatabase(), block: suspend (ProfileRepository, TesoriereDatabase) -> T): T =
        try {
            runBlocking { block(ProfileRepository(db, clock, Ids("r")::next), db) }
        } finally {
            db.close()
        }

    // SPEC:R140 SPEC:R18
    @Test fun `R140 Ana alone keeps Profiles off, Household switches them on, and each keeps its own Savings`() = withRepo { repo, _ ->
        val ana = repo.createProfile("  Ana ", "p-ana")
        assertEquals("Ana", ana.profile.name)
        assertEquals("2026-11", ana.settings.lastMonthProcessed)
        assertEquals(1, repo.profiles().size)
        assertEquals("p-ana", repo.appState()!!.activeProfileId)
        assertFalse(repo.appState()!!.profilesEnabled)

        repo.save(Writes.savingsQuickAdd(ana.copy(savings = ana.savings.copy(amount = 0)), cents("300.00"), "opening", false, clock, "t-1"))
        repo.createProfile("Household", "p-house")
        assertEquals(listOf("Ana", "Household"), repo.profiles().map { it.name })
        assertEquals("p-house", repo.appState()!!.activeProfileId)
        assertTrue(repo.appState()!!.profilesEnabled)
        assertEquals(cents("300.00"), repo.load("p-ana")!!.savings.amount)
        assertNull(repo.load("p-house")!!.savings.amount)
        assertTrue(repo.load("p-house")!!.transactions.isEmpty())
    }

    // SPEC:R140 SPEC:R126
    @Test fun `R140 switch, rename and delete keep exactly one open Profile and never delete the last`() = withRepo { repo, _ ->
        val ana = repo.createProfile("Ana", "p-ana")
        repo.save(ana.copy(savings = ana.savings.copy(amount = cents("300.00"))))
        repo.createProfile("Household", "p-house")

        assertNull(repo.switchTo("p-ana"))
        assertEquals("p-ana", repo.appState()!!.activeProfileId)
        assertEquals("p-ana", repo.open()!!.profileId)
        assertEquals(ProfileProblem.NO_SUCH_PROFILE, repo.switchTo("p-none"))
        assertEquals("p-ana", repo.appState()!!.activeProfileId)

        assertNull(repo.renameProfile("p-house", "  Joint  "))
        assertEquals("Joint", repo.load("p-house")!!.profile.name)
        assertEquals(ProfileProblem.BLANK_NAME, repo.renameProfile("p-house", "   "))
        assertEquals(ProfileProblem.NO_SUCH_PROFILE, repo.renameProfile("p-none", "X"))
        assertEquals("Joint", repo.load("p-house")!!.profile.name)

        // deleting the open one opens the first remaining, and keeps it whole in the Trash
        assertNull(repo.deleteProfile("p-ana"))
        assertEquals("p-house", repo.appState()!!.activeProfileId)
        assertEquals(listOf("Joint"), repo.profiles().map { it.name })
        assertNull(repo.load("p-ana"))
        val item = repo.trash().single()
        assertEquals(ProfileRepository.PROFILE_KIND, item.kind)
        assertEquals("p-ana", item.profileId)
        assertEquals(nov26.toString(), item.deletedOn)
        assertEquals(cents("300.00"), Trash.decode(item.payload)!!.profile!!.savings.amount)

        assertEquals(ProfileProblem.LAST_PROFILE, repo.deleteProfile("p-house"))
        assertEquals(ProfileProblem.NO_SUCH_PROFILE, repo.deleteProfile("p-ana"))
        assertEquals(listOf("Joint"), repo.profiles().map { it.name })
        assertEquals(1, repo.trash().size)
    }

    // SPEC:R141
    @Test fun `R141 a write is saved whole and read back as it was, and the open Profile never names one that is gone`() = withRepo { repo, db ->
        val d0 = repo.createProfile("Mine", "p-1")
        val d1 = Writes.savingsQuickAdd(d0.copy(savings = d0.savings.copy(amount = 0)), cents("120.00"), null, false, clock, "t-1")
        repo.save(d1)
        assertEquals(cents("120.00"), repo.load("p-1")!!.savings.amount)
        assertEquals(d1, repo.load("p-1"))
        assertNull(repo.load("p-none"))
        // the app's state naming a Profile that is gone opens the first by name
        db.dao().upsertAppState(repo.appState()!!.copy(activeProfileId = "p-gone"))
        assertEquals("p-1", repo.open()!!.profileId)
        assertEquals("p-1", repo.appState()!!.activeProfileId)
    }

    // SPEC:R141 SPEC:R122 SPEC:R131
    @Test fun `R141 a Goal deleted and restored through the Trash leaves no item behind and a backup that reads back whole`() = withRepo { repo, _ ->
        val d0 = repo.createProfile("Mine", "p-1").let { it.copy(savings = it.savings.copy(amount = cents("1,000.00")), goals = listOf(Fixture.goal("car", "Car", saved = "300.00").copy(profileId = "p-1"))) }
        repo.save(d0)
        val (after, payload) = assertNotNull(Trash.deleteGoal(d0, "car", returnSaved = true, clock, Ids("g")::next))
        val item = assertNotNull(repo.saveDeleted(after, "goal", "Car", payload))
        assertEquals(listOf(item), repo.trash())
        assertEquals(cents("1,300.00"), repo.load("p-1")!!.savings.amount)
        assertTrue(repo.load("p-1")!!.goals.isEmpty())

        val restored = assertIs<TrashRestore.Restored>(repo.restore(item.id)).data
        assertEquals(restored, repo.load("p-1"))
        assertEquals(cents("300.00"), restored.goals.single().saved)
        assertEquals(cents("1,000.00"), restored.savings.amount)
        assertTrue(repo.trash().isEmpty())
        assertIs<BackupResult.Ok>(Backup.validate(assertIs<Export.Ok>(repo.backupFile(nov26)).text))
        assertEquals(TrashRestore.NotRestorable, repo.restore(item.id))
    }

    // SPEC:R141 SPEC:R122 SPEC:R123
    @Test fun `R141 a restore the engine refuses writes nothing and keeps its item`() = withRepo { repo, _ ->
        val d0 = repo.createProfile("Mine", "p-1").let {
            it.copy(
                savings = it.savings.copy(amount = cents("1,000.00")),
                categories = listOf(Category("c1", "p-1", "Gym")),
                recurringExpenses = listOf(Fixture.recurring("gym", "30.00", Cadence.MONTHLY, LocalDate(2026, 12, 1)).copy(profileId = "p-1")),
            )
        }
        repo.save(d0)
        val (a1, p1) = assertNotNull(Trash.deleteExpense(d0, "gym", reverse = false, clock, Ids("e")::next))
        val expense = assertNotNull(repo.saveDeleted(a1, "recurringExpense", "Gym", p1))
        val (a2, p2) = assertNotNull(Trash.deleteCategory(a1, "c1"))
        repo.saveDeleted(a2, "category", "Gym", p2)
        val before = repo.load("p-1")
        assertEquals(TrashRestore.Refused(RestoreProblem.CATEGORY_MISSING), repo.restore(expense.id))
        assertEquals(before, repo.load("p-1"))
        assertEquals(2, repo.trash().size)
    }

    // SPEC:R126 SPEC:R140
    @Test fun `R126 a Profile restored from the Trash comes back as one more Profile, not opened, and switches Profiles on`() = withRepo { repo, _ ->
        repo.createProfile("Ana", "p-ana")
        repo.createProfile("Household", "p-house")
        repo.deleteProfile("p-ana")
        val item = repo.trash().single()
        val d = assertIs<TrashRestore.Restored>(repo.restore(item.id)).data
        assertEquals("p-ana", d.profileId)
        assertEquals(listOf("Ana", "Household"), repo.profiles().map { it.name })
        assertEquals("p-house", repo.appState()!!.activeProfileId)
        assertTrue(repo.appState()!!.profilesEnabled)
        assertTrue(repo.trash().isEmpty())
    }

    /** A folder holding one receipt file that the parser below reads. */
    private object OneFile : ReceiptFolder {
        override fun names() = listOf("chatelaine-receipt-rc-1.json")
        override fun read(name: String) = "rc-1".encodeToByteArray()
    }

    // SPEC:R28 SPEC:R112 SPEC:R116
    @Test fun `R28 Profile B has no receipt folder while A has one`() = withRepo { repo, _ ->
        val a = repo.createProfile("A", "p-a").copy(categories = listOf(Category("ga", "p-a", "Groceries")))
        repo.save(a)
        // B's own settings name a folder, as a damaged or hand-made store might: the app's word decides
        val b = repo.createProfile("B", "p-b").copy(
            categories = listOf(Category("gb", "p-b", "Groceries")),
            receiptSettings = ReceiptSettings("p-b", folder = "folder-handle-2"),
        )
        repo.save(b)
        assertIs<Update.Saved>(repo.updateWithApp("p-a") { app, d -> Receipts.chooseFolder(app, d, "folder-handle-1") })

        val stored = repo.appState()!!
        assertEquals("p-a", stored.receiptProfileId)
        assertTrue(Receipts.offersFolder(stored, "p-a"))
        assertFalse(Receipts.offersFolder(stored, "p-b"))
        val bStored = repo.load("p-b")!!
        assertEquals(stored to bStored, Receipts.chooseFolder(stored, bStored, "folder-handle-3"))

        val file = Fixture.receipt("rc-1", "Mercato Lume", nov26, Fixture.line("bread", "2.40", "ch-groc", "Groceries"))
        val parse: (ByteArray) -> ParseResult = { ParseResult.Ok(file) }
        assertTrue(ReceiptPickup.scan(stored, bStored, OneFile, parse).ready.isEmpty())
        assertEquals(ReceiptProblem.NOT_RECEIPT_PROFILE, Receipts.problem(stored, bStored, Receipts.open(bStored, file)))

        val aStored = repo.load("p-a")!!
        assertEquals(listOf(file), ReceiptPickup.scan(stored, aStored, OneFile, parse).ready)
        val a2 = Receipts.confirm(stored, aStored, Receipts.open(aStored, file), clock, "pay-1")
        repo.save(a2)
        assertEquals(1, repo.load("p-a")!!.payments.size)
        assertTrue(repo.load("p-b")!!.payments.isEmpty())
        assertEquals(listOf("rc-1"), repo.load("p-a")!!.receiptSettings!!.recordedReceiptIds)
    }

    private fun sciFin(name: String = "schema-10.json"): SciFinRead.Ok {
        val text = requireNotNull(javaClass.getResourceAsStream("/scifin-backups/$name")).use { it.readBytes().decodeToString() }
        val read = assertIs<SciFinRead.Ok>(SciFinImport.read(text, null))
        return read.profiles.fold(read) { r, p ->
            p.recurringExpenses.fold(r) { rr, e -> SciFinImport.setExpenseCategory(rr, e.id, p.categories.first { it.name == "Fun" }.id) }
        }
    }

    // SPEC:R136 SPEC:R140
    @Test fun `R136 a Sci-Fin Profile added beside Mine switches Profiles on, and added alone leaves them off`() {
        withRepo { repo, _ ->
            repo.createProfile("Mine", "p-mine")
            val added = repo.addSciFin(sciFin()).single()
            assertEquals(added, repo.load(added.profileId))
            assertEquals(2, repo.profiles().size)
            assertTrue(repo.appState()!!.profilesEnabled)
            assertEquals("p-mine", repo.appState()!!.activeProfileId)
        }
        withRepo { repo, _ ->
            val added = repo.addSciFin(sciFin()).single()
            assertEquals(listOf(added.profileId), repo.profiles().map { it.id })
            assertFalse(repo.appState()!!.profilesEnabled)
            assertEquals(added.profileId, repo.appState()!!.activeProfileId)
        }
    }

    // SPEC:R136 SPEC:R137
    @Test fun `R136 a Sci-Fin file whose recurring expense has no category yet adds nothing`() = withRepo { repo, _ ->
        repo.createProfile("Mine", "p-mine")
        val text = requireNotNull(javaClass.getResourceAsStream("/scifin-backups/schema-10.json")).use { it.readBytes().decodeToString() }
        val read = assertIs<SciFinRead.Ok>(SciFinImport.read(text, null))
        assertFalse(SciFinImport.canConfirm(read))
        assertTrue(repo.addSciFin(read).isEmpty())
        assertEquals(1, repo.profiles().size)
        assertFalse(repo.appState()!!.profilesEnabled)
    }

    // SPEC:R141
    @Test fun `R141 a database file holds the device after it is closed and opened again`() {
        val file = TestDb.file()
        withRepo(buildTesoriereDatabase(file)) { repo, _ ->
            repo.createProfile("Ana", "p-ana")
            repo.createProfile("Household", "p-house")
            repo.deleteProfile("p-house")
        }
        withRepo(buildTesoriereDatabase(file)) { repo, _ ->
            assertEquals(listOf("Ana"), repo.profiles().map { it.name })
            assertEquals("p-ana", repo.appState()!!.activeProfileId)
            assertEquals("p-house", repo.trash().single().profileId)
            assertNotNull(repo.load("p-ana"))
        }
    }

    // SPEC:R146 SPEC:R18
    @Test fun `R146 the lock reads OFF on a fresh install, ON once set, DAMAGED for a row whose cost reads abc, and OFF once cleared`() {
        val file = TestDb.file()
        val lock = assertNotNull(Pin.lockFor("482100", "the first flat", LockAfter.ONE_MINUTE, iterations = 1_000))
        withRepo(buildTesoriereDatabase(file)) { repo, _ ->
            assertEquals(StoredLock(LockState.OFF, null), repo.lock())
            repo.setLock(lock.copy(id = 7))
            val on = repo.lock()
            assertEquals(StoredLock(LockState.ON, lock), on, "the one row, whatever id it was given")
            assertTrue(Pin.matches(on.row!!, "482100"))
            repo.setLock(on.row!!.copy(failedAttempts = Pin.nextFailedCount(on.row!!.failedAttempts)))
            assertEquals(1, repo.lock().row!!.failedAttempts)
        }
        TestDb.damage(file, "UPDATE app_lock SET iterations = 'abc'")
        withRepo(buildTesoriereDatabase(file)) { repo, _ ->
            val damaged = repo.lock()
            assertEquals(LockState.DAMAGED, damaged.state)
            assertFalse(Pin.matches(assertNotNull(damaged.row), "482100"))
            repo.clearLock()
            assertEquals(StoredLock(LockState.OFF, null), repo.lock())
            repo.clearLock()
        }
    }

    // SPEC:R148 SPEC:R18
    @Test fun `R148 the copy schedule reads none on a fresh install, as set once set, and a row stored as fortnightly and yesterday reads weekly and due`() {
        val file = TestDb.file()
        val day = LocalDate(2026, 11, 27)
        withRepo(buildTesoriereDatabase(file)) { repo, _ ->
            assertNull(repo.copySchedule())
            assertFalse(AutoCopy.due(repo.copySchedule(), day))
            val on = AutoCopy.turnOn(repo.copySchedule(), "folder-handle-1", "Sync")
            repo.setCopySchedule(on.copy(id = 5))
            assertEquals(on, repo.copySchedule(), "the one row, whatever id it was given")
            repo.setCopySchedule(AutoCopy.afterSuccess(on, day))
            assertEquals("2026-11-27", repo.copySchedule()!!.lastCopy)
        }
        TestDb.damage(file, "UPDATE copy_schedule SET every = 'fortnightly', lastCopy = 'yesterday', failureNotified = 'abc'")
        withRepo(buildTesoriereDatabase(file)) { repo, _ ->
            val s = assertNotNull(repo.copySchedule())
            assertEquals(CopyEvery.WEEKLY, AutoCopy.every(s))
            assertTrue(AutoCopy.due(s, day))
        }
    }

    // SPEC:R121
    @Test fun `R121 an item deleted on 1 October and marked warned on 21 October is not due its notice again, and the other item still is`() = withRepo { repo, db ->
        val oct21 = LocalDate(2026, 10, 21)
        repo.createProfile("A", "p-a")
        for (id in listOf("tr-1", "tr-2")) {
            db.dao().insertTrashItem(TrashItem(id, "p-a", "category", "Fun", "2026-10-01", Trash.encode(TrashPayload(categories = listOf(Category("c-$id", "p-a", "Fun"))))))
        }
        assertEquals(setOf("tr-1", "tr-2"), Trash.tidy(repo.trash(), oct21).warn.toSet())
        repo.markWarned(listOf("tr-1", "tr-none"))
        assertEquals(listOf("tr-2"), Trash.tidy(repo.trash(), oct21).warn)
        assertEquals(mapOf("tr-1" to true, "tr-2" to false), repo.trash().associate { it.id to it.warned })
        repo.markWarned(emptyList())
        assertEquals(LocalDate(2026, 10, 1).toString(), repo.trash().first { it.id == "tr-1" }.deletedOn, "only the mark changes")
    }
}
