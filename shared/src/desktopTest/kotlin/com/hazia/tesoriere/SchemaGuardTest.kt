package com.hazia.tesoriere

import com.hazia.tesoriere.data.TESORIERE_MIGRATIONS
import com.hazia.tesoriere.engine.BackupProfile
import com.hazia.tesoriere.model.Account
import com.hazia.tesoriere.model.AppState
import com.hazia.tesoriere.model.Category
import com.hazia.tesoriere.model.Goal
import com.hazia.tesoriere.model.ImportState
import com.hazia.tesoriere.model.Profile
import com.hazia.tesoriere.model.RecurringExpense
import com.hazia.tesoriere.model.SCHEMA_VERSION
import com.hazia.tesoriere.model.Savings
import com.hazia.tesoriere.model.Settings
import com.hazia.tesoriere.model.Shift
import com.hazia.tesoriere.model.Transaction
import com.hazia.tesoriere.model.TrashItem
import com.hazia.tesoriere.payments.Payment
import com.hazia.tesoriere.receipts.ReceiptSettings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Ported from Sci-Fin native/shared/src/desktopTest/kotlin/app/scifin/MigrationTest.kt at 2892110:
 * the guard half. The half that builds an old database and migrates it waits for the first
 * migration (SPEC R142). Added: the exported schema holds every field of the model.
 */
class SchemaGuardTest {
    private fun schemaDir(): File =
        listOf("schemas", "shared/schemas", "../shared/schemas")
            .map { File(it, "com.hazia.tesoriere.data.TesoriereDatabase") }
            .firstOrNull { it.isDirectory }
            ?: error("exported schemas not found from ${File(".").absolutePath}")

    private fun exported(): List<Int> =
        schemaDir().listFiles { f: File -> f.name.endsWith(".json") }.orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()

    private fun tables(v: Int): Map<String, Set<String>> =
        Json.parseToJsonElement(File(schemaDir(), "$v.json").readText()).jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
            .associate { e ->
                val o = e.jsonObject
                o["tableName"]!!.jsonPrimitive.content to o["fields"]!!.jsonArray.mapTo(HashSet()) { (it as JsonObject)["columnName"]!!.jsonPrimitive.content }
            }

    // SPEC:R43 SPEC:R142
    @Test fun `R142 every schema version up to SCHEMA_VERSION has an exported schema and a migration path`() {
        val exported = exported()
        for (v in 1..SCHEMA_VERSION) assertTrue(v in exported, "no $v.json exported: build the module before trusting this")
        val edges = TESORIERE_MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        for (v in 2..SCHEMA_VERSION) assertTrue((v - 1 to v) in edges, "no migration from ${v - 1} to $v (SPEC R142)")
    }

    // SPEC:R142
    @Test fun `R142 no migration names a version with no exported schema, and version 1 has none`() {
        val exported = exported().toSet()
        for (m in TESORIERE_MIGRATIONS) {
            assertTrue(m.startVersion in exported, "migration from ${m.startVersion}, which has no schema")
            assertTrue(m.endVersion in exported, "migration to ${m.endVersion}, which has no schema")
        }
        if (SCHEMA_VERSION == 1) assertEquals(0, TESORIERE_MIGRATIONS.size)
    }

    // SPEC:R141 SPEC:R142
    @Test fun `R141 schema 1 holds every table, and every field of every stored record is a column`() {
        val t = tables(SCHEMA_VERSION)
        assertEquals(
            setOf(
                "profiles", "savings", "goals", "categories", "recurring_expenses", "accounts", "transactions", "settings",
                "import_state", "shifts", "payments", "receipt_settings", "app_state", "app_lock", "copy_schedule", "trash",
            ),
            t.keys,
        )
        fun fields(s: KSerializer<*>) = (0 until s.descriptor.elementsCount).mapTo(HashSet()) { s.descriptor.getElementName(it) }
        val stored = mapOf(
            "profiles" to Profile.serializer(), "savings" to Savings.serializer(), "goals" to Goal.serializer(),
            "categories" to Category.serializer(), "recurring_expenses" to RecurringExpense.serializer(),
            "accounts" to Account.serializer(), "transactions" to Transaction.serializer(), "settings" to Settings.serializer(),
            "import_state" to ImportState.serializer(), "shifts" to Shift.serializer(), "payments" to Payment.serializer(),
            "receipt_settings" to ReceiptSettings.serializer(), "app_state" to AppState.serializer(), "trash" to TrashItem.serializer(),
        )
        for ((table, s) in stored) assertEquals(fields(s), t.getValue(table), table)
        // the fields PRs 2 to 5 added, by name, so a rename cannot slip past
        assertTrue(t.getValue("transactions").containsAll(listOf("categoryId", "accountPayoff", "groupId", "share")))
        assertTrue(t.getValue("settings").containsAll(listOf("lastMonthProcessed", "accountsEnabled", "overtimeEnabled", "catchUpEnded")))
        assertTrue(t.getValue("accounts").containsAll(listOf("chargesNotLogged", "payoffCategoryId", "feesCategoryId", "loanCategoryId")))
        assertTrue(t.getValue("payments").containsAll(listOf("parts", "receiptLines", "bankKeys", "fromSavings", "source")))
        assertTrue(t.getValue("app_state").containsAll(listOf("receiptProfileId", "profilesEnabled")))
        assertTrue(t.getValue("recurring_expenses").containsAll(listOf("categoryId", "createdOn")))
        // a backup Profile carries the same records (SPEC R130)
        assertTrue(fields(BackupProfile.serializer()).containsAll(listOf("payments", "receiptSettings", "shifts")))
    }
}
