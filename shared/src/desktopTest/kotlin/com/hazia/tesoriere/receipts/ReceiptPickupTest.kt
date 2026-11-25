package com.hazia.tesoriere.receipts

import com.hazia.tesoriere.Fixture
import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.line
import com.hazia.tesoriere.Fixture.profile
import com.hazia.tesoriere.Fixture.receipt
import com.hazia.tesoriere.model.ProfileData
import kotlinx.datetime.LocalDate
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every store, product and id here is made up. */
class ReceiptPickupTest {
    private val day = LocalDate(2026, 11, 24)

    /** A folder in memory that counts what it is asked to read. */
    private class MemoryFolder(private val files: Map<String, String>) : ReceiptFolder {
        val reads = mutableListOf<String>()
        override fun names(): List<String> = files.keys.toList()
        override fun read(name: String): ByteArray? { reads += name; return files[name]?.encodeToByteArray() }
    }

    private fun world(recorded: List<String> = emptyList()): ProfileData = profile(categories = listOf(category("groc", "Groceries")))
        .copy(receiptSettings = Fixture.receiptSettings().copy(recordedReceiptIds = recorded))

    private fun file(id: String, currency: String = "EUR") =
        receipt(id, "Mercato Lume", day, line("bread", "2.40", "ch-groc", "Groceries"), currency = currency)

    /** A fake parser: a file's text names what it parses to. */
    private val parsed = mapOf(
        "a" to ParseResult.Ok(file("rc-a")), "b" to ParseResult.Ok(file("rc-b")), "c" to ParseResult.Ok(file("rc-c")),
        "a-again" to ParseResult.Ok(file("rc-a")),
        "usd" to ParseResult.Ok(file("rc-usd", currency = "USD")),
        "v2" to ParseResult.NewerFormat(2),
        "v3-ok" to ParseResult.Ok(file("rc-v3").copy(formatVersion = 3)),
    )
    private val parse: (ByteArray) -> ParseResult = { parsed[it.decodeToString()] ?: ParseResult.Unreadable }

    private fun names(p: Pickup) = p.ready.map { it.receiptId }

    // SPEC:R29 SPEC:R116
    @Test fun `R29 three new files give three receipts to review and record nothing`() {
        val d = world()
        val folder = MemoryFolder(mapOf("chatelaine-receipt-rc-c.json" to "c", "chatelaine-receipt-rc-a.json" to "a", "chatelaine-receipt-rc-b.json" to "b"))
        val p = ReceiptPickup.scan(d, folder, parse)
        assertEquals(listOf("rc-a", "rc-b", "rc-c"), names(p))
        assertEquals(emptyList(), p.refused)
        assertEquals(3, ReceiptPickup.waiting(d, folder, parse))
        assertEquals(world(), d)
    }

    // SPEC:R30 SPEC:R116
    @Test fun `R30 a recorded receipt id is not shown again`() {
        val d = world(recorded = listOf("rc-a"))
        val p = ReceiptPickup.scan(d, MemoryFolder(mapOf("chatelaine-receipt-rc-a.json" to "a", "chatelaine-receipt-rc-b.json" to "b")), parse)
        assertEquals(listOf("rc-b"), names(p))
        assertEquals(emptyList(), p.refused)
        assertEquals(world(recorded = listOf("rc-a")), d)
    }

    // SPEC:R30 SPEC:R116
    @Test fun `R30 format version 2 is refused naming version 2`() {
        val d = world()
        val p = ReceiptPickup.scan(d, MemoryFolder(mapOf("chatelaine-receipt-rc-v2.json" to "v2", "chatelaine-receipt-rc-v3.json" to "v3-ok")), parse)
        assertEquals(emptyList(), p.ready)
        assertEquals(
            listOf<Pair<String, Refusal>>("chatelaine-receipt-rc-v2.json" to Refusal.NewerFormat(2), "chatelaine-receipt-rc-v3.json" to Refusal.NewerFormat(3)),
            p.refused,
        )
        assertEquals(world(), d)
    }

    // SPEC:R30 SPEC:R116 SPEC:R47
    @Test fun `R30 a USD receipt in a EUR Profile is refused naming both`() {
        val d = world()
        val p = ReceiptPickup.scan(d, MemoryFolder(mapOf("chatelaine-receipt-rc-usd.json" to "usd")), parse)
        assertEquals(emptyList(), p.ready)
        assertEquals(listOf<Pair<String, Refusal>>("chatelaine-receipt-rc-usd.json" to Refusal.OtherCurrency("USD", "EUR")), p.refused)
        assertEquals(0, ReceiptPickup.waiting(d, MemoryFolder(mapOf("chatelaine-receipt-rc-usd.json" to "usd")), parse))
        assertEquals(world(), d)
    }

    // SPEC:R30 SPEC:R116
    @Test fun `R30 an unreadable file is refused`() {
        val d = world()
        val p = ReceiptPickup.scan(d, MemoryFolder(mapOf("chatelaine-receipt-rc-x.json" to "not a receipt")), parse)
        assertEquals(emptyList(), p.ready)
        assertEquals(listOf<Pair<String, Refusal>>("chatelaine-receipt-rc-x.json" to Refusal.Unreadable), p.refused)
        assertEquals(world(), d)
    }

    // SPEC:R116 SPEC:R30
    @Test fun `R116 a receipt id twice in one scan is offered once`() {
        val p = ReceiptPickup.scan(world(), MemoryFolder(mapOf("copy-of-rc-a.json" to "a-again", "chatelaine-receipt-rc-a.json" to "a")), parse)
        assertEquals(listOf("rc-a"), names(p))
        assertEquals(emptyList(), p.refused)
    }

    // SPEC:R116
    @Test fun `R116 a name not ending in json is not read`() {
        val folder = MemoryFolder(mapOf("chatelaine-receipt-rc-b.part" to "b", "notes.txt" to "c", "chatelaine-receipt-rc-a.json" to "a"))
        val p = ReceiptPickup.scan(world(), folder, parse)
        assertEquals(listOf("rc-a"), names(p))
        assertEquals(emptyList(), p.refused)
        assertEquals(listOf("chatelaine-receipt-rc-a.json"), folder.reads)
    }

    // SPEC:R116 SPEC:R112
    @Test fun `R116 a Profile that does not hold the receipt folder scans nothing`() {
        val folder = MemoryFolder(mapOf("chatelaine-receipt-rc-a.json" to "a"))
        val p = ReceiptPickup.scan(profile(), folder, parse)
        assertEquals(Pickup(emptyList(), emptyList()), p)
        assertEquals(emptyList(), folder.reads)
    }

    // SPEC:R27
    @Test fun `R27 the receipt folder offers no way to write`() {
        assertEquals(setOf("names", "read"), ReceiptFolder::class.java.declaredMethods.map { it.name }.toSet())
    }

    // SPEC:R27 SPEC:R116
    @Test fun `R27 FileReceiptFolder lists and reads a temp folder and leaves its files in place`() {
        val base = File("build/tmp").apply { mkdirs() }
        val dir = Files.createTempDirectory(base.toPath(), "receipt-folder").toFile()
        try {
            File(dir, "chatelaine-receipt-rc-a.json").writeText("a")
            File(dir, "chatelaine-receipt-rc-b.part").writeText("b")
            File(dir, "inner").mkdir()
            val folder = FileReceiptFolder(dir)
            assertEquals(listOf("chatelaine-receipt-rc-a.json", "chatelaine-receipt-rc-b.part"), folder.names())
            assertContentEquals("a".encodeToByteArray(), folder.read("chatelaine-receipt-rc-a.json"))
            assertNull(folder.read("missing.json"))
            assertNull(folder.read("inner"))
            assertNull(folder.read("../outside.json"))
            assertEquals(listOf("rc-a"), names(ReceiptPickup.scan(world(), folder, parse)))
            assertEquals(setOf("chatelaine-receipt-rc-a.json", "chatelaine-receipt-rc-b.part", "inner"), dir.list()!!.toSet())
            assertEquals("a", File(dir, "chatelaine-receipt-rc-a.json").readText())
            assertTrue(File(dir, "chatelaine-receipt-rc-b.part").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
