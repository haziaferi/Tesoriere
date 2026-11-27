package com.hazia.tesoriere

import com.hazia.tesoriere.data.Export
import com.hazia.tesoriere.data.ProfileRepository
import com.hazia.tesoriere.data.TesoriereDatabase
import com.hazia.tesoriere.data.TrashRestore
import com.hazia.tesoriere.data.buildInMemoryTesoriereDatabase
import com.hazia.tesoriere.engine.Applied
import com.hazia.tesoriere.engine.Backup
import com.hazia.tesoriere.engine.BackupResult
import com.hazia.tesoriere.engine.Trash
import com.hazia.tesoriere.engine.TrashPayload
import com.hazia.tesoriere.engine.TrashedProfile
import com.hazia.tesoriere.engine.ValidatedBackup
import com.hazia.tesoriere.model.AppLock
import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.CopySchedule
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.ProfileData
import com.hazia.tesoriere.model.TrashItem
import com.hazia.tesoriere.receipts.ReceiptSettings
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * Backup.apply (SPEC R143), ported from Sci-Fin native/shared/src/commonMain/kotlin/app/scifin/engine/Backup.kt's
 * `apply` at 2892110, with the restore half of SettingsStoreTest.kt. Every name and figure here is made up.
 */
class BackupApplyTest {
    private val nov26 = LocalDate(2026, 11, 26)
    private val clock = TestClock(nov26)
    private val lock = AppLock(salt = "c2FsdA==", hash = "aGFzaA==", iterations = 600_000, pinLength = 6)
    private val copy = CopySchedule(every = "weekly", folder = "copy-handle", lastCopy = "2026-11-20")

    private fun <T> withDb(block: suspend (ProfileRepository, TesoriereDatabase) -> T): T {
        val db = buildInMemoryTesoriereDatabase()
        try {
            return runBlocking { block(ProfileRepository(db, clock, Ids("r")::next), db) }
        } finally {
            db.close()
        }
    }

    /** A device holding Profiles A and B, an app lock, an automatic copy and one Trash item of A. */
    private suspend fun device(repo: ProfileRepository, db: TesoriereDatabase, profilesOn: Boolean = true) {
        val a = repo.createProfile("A", "p-a")
        repo.createProfile("B", "p-b")
        db.dao().upsertAppLock(lock)
        db.dao().upsertCopySchedule(copy)
        db.dao().upsertAppState(repo.appState()!!.copy(activeProfileId = "p-a", profilesEnabled = profilesOn, lastBackupExportDate = LocalDate(2026, 11, 1)))
        repo.save(a.copy(categories = listOf(Category("fun-a", "p-a", "Fun"))))
        val (after, p) = Trash.deleteCategory(repo.load("p-a")!!, "fun-a")!!
        repo.saveDeleted(after, "category", "Fun", p)
    }

    private fun profile(id: String, name: String): ProfileData =
        ProfileData.fresh(id, name).let { it.copy(savings = it.savings.copy(amount = cents("500.00"))) }

    /** The file of [profiles], whose Trash holds a Goal deleted on 20 October and a category deleted on 10 November, C the receipt Profile. */
    private fun file(vararg profiles: ProfileData): ValidatedBackup {
        val c = profiles.first().profileId
        val trash = listOf(
            TrashItem("tr-old", c, "goal", "Car", "2026-10-20", Trash.encode(TrashPayload(goals = listOf(Goal("car", c, "Car"))))),
            TrashItem("tr-new", c, "category", "Gifts", "2026-11-10", Trash.encode(TrashPayload(categories = listOf(Category("gifts", c, "Gifts"))))),
        )
        val withFolder = profiles.mapIndexed { i, d -> if (i == 0) d.copy(receiptSettings = ReceiptSettings(c, folder = "folder-handle-1")) else d }
        val text = Backup.encode(Backup.build(withFolder, trash, activeProfileId = c, receiptProfileId = c, exportedAt = LocalDate(2026, 11, 20)))
        return assertIs<BackupResult.Ok>(Backup.validate(text)).backup
    }

    // SPEC:R143 SPEC:R130 SPEC:R121
    @Test fun `R143 a file of C restored on 26 November replaces A, B and the Trash, drops the Goal deleted 20 October, and leaves the lock and the copy`() = withDb { repo, db ->
        device(repo, db)
        val b = file(profile("p-c", "C"))
        Backup.apply(db, b, nov26)

        assertEquals(listOf("C"), repo.profiles().map { it.name })
        // C is new to the device, so notifications read as not yet answered (SPEC R143), as in the file
        assertEquals(b.profiles.single(), repo.load("p-c"))
        assertEquals(listOf("tr-new"), repo.trash().map { it.id })
        assertEquals(b.trash.first { it.id == "tr-new" }, repo.trash().single())
        val app = repo.appState()!!
        assertEquals("p-c", app.activeProfileId)
        assertEquals("p-c", app.receiptProfileId)
        assertEquals(nov26, app.lastBackupImportDate)
        assertEquals(LocalDate(2026, 11, 1), app.lastBackupExportDate)
        assertTrue(app.profilesEnabled, "a file of one Profile leaves the switch as the device had it")
        assertEquals(lock, db.dao().appLock())
        assertEquals(copy, db.dao().copySchedule())
    }

    // SPEC:R143 SPEC:R130 SPEC:R18
    @Test fun `R143 a file of C and D switches Profiles on, and a file of C alone leaves them off`() {
        withDb { repo, db ->
            device(repo, db, profilesOn = false)
            Backup.apply(db, file(profile("p-c", "C"), profile("p-d", "D")), nov26)
            assertEquals(listOf("C", "D"), repo.profiles().map { it.name })
            assertTrue(repo.appState()!!.profilesEnabled)
        }
        withDb { repo, db ->
            device(repo, db, profilesOn = false)
            Backup.apply(db, file(profile("p-c", "C")), nov26)
            assertFalse(repo.appState()!!.profilesEnabled)
        }
    }

    // SPEC:R143 SPEC:R130 SPEC:R126
    @Test fun `R143 notifications granted stays the device's for A and D, and C and E new to it read as not yet answered, from the Trash too`() = withDb { repo, db ->
        for ((id, name) in listOf("p-a" to "A", "p-d" to "D")) {
            val d = repo.createProfile(name, id)
            repo.save(d.copy(settings = d.settings.copy(notificationPermissionGranted = true)))
        }
        fun said(d: ProfileData, granted: Boolean?) = d.copy(settings = d.settings.copy(notificationPermissionGranted = granted))
        val fileA = said(profile("p-a", "A"), false)
        val fileC = said(profile("p-c", "C"), true)
        val trash = listOf(
            TrashItem("tr-d", "p-d", "profile", "D", "2026-11-20", Trash.encode(TrashPayload(profile = TrashedProfile.of(said(profile("p-d", "D"), false))))),
            TrashItem("tr-e", "p-e", "profile", "E", "2026-11-20", Trash.encode(TrashPayload(profile = TrashedProfile.of(said(profile("p-e", "E"), true))))),
        )
        val text = Backup.encode(Backup.build(listOf(fileA, fileC), trash, activeProfileId = "p-a", receiptProfileId = null, exportedAt = LocalDate(2026, 11, 20)))
        assertEquals(Applied.Restored, Backup.apply(db, assertIs<BackupResult.Ok>(Backup.validate(text)).backup, nov26))
        assertEquals(true, repo.load("p-a")!!.settings.notificationPermissionGranted)
        assertEquals(null, repo.load("p-c")!!.settings.notificationPermissionGranted)
        assertEquals(fileC.settings.copy(notificationPermissionGranted = null), repo.load("p-c")!!.settings, "nothing else of the settings changes")
        // restored from the Trash later, D and E never take the file's value
        assertIs<TrashRestore.Restored>(repo.restore("tr-d"))
        assertIs<TrashRestore.Restored>(repo.restore("tr-e"))
        assertEquals(true, repo.load("p-d")!!.settings.notificationPermissionGranted)
        assertEquals(null, repo.load("p-e")!!.settings.notificationPermissionGranted)
    }

    // SPEC:R143
    @Test fun `R143 a restore that fails after writing the file's Profiles leaves A, B, the Trash item and the open Profile as they were`() = withDb { repo, db ->
        device(repo, db)
        val before = listOf(repo.load("p-a"), repo.load("p-b"))
        val trash = repo.trash()
        val app = repo.appState()
        assertFailsWith<IllegalStateException> {
            Backup.apply(db, file(profile("p-c", "C")), nov26) { error("the restore stops here") }
        }
        assertEquals(listOf("A", "B"), repo.profiles().map { it.name })
        assertEquals(before, listOf(repo.load("p-a"), repo.load("p-b")))
        assertEquals(trash, repo.trash())
        assertEquals(app, repo.appState())
        assertEquals(lock, db.dao().appLock())
    }

    // SPEC:R143 SPEC:R130
    @Test fun `R143 a backup of the device restored onto a fresh one gives back the same Profiles, Trash and receipt Profile`() {
        val text = withDb { repo, db ->
            device(repo, db)
            val (app, a) = com.hazia.tesoriere.receipts.Receipts.chooseFolder(repo.appState()!!, repo.load("p-a")!!, "folder-handle-1")
            repo.save(app, a)
            assertIs<Export.Ok>(repo.backupFile(nov26)).text
        }
        withDb { repo, db ->
            Backup.apply(db, assertIs<BackupResult.Ok>(Backup.validate(text)).backup, nov26)
            assertEquals(listOf("A", "B"), repo.profiles().map { it.name })
            assertEquals("p-a", repo.appState()!!.receiptProfileId)
            assertEquals("folder-handle-1", repo.load("p-a")!!.receiptSettings!!.folder)
            assertEquals(listOf("category"), repo.trash().map { it.kind })
            assertTrue(repo.appState()!!.profilesEnabled)
        }
    }
}
