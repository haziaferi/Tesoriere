package com.hazia.tesoriere.receipts

import com.hazia.tesoriere.Fixture
import com.hazia.tesoriere.Fixture.category
import com.hazia.tesoriere.Fixture.profile
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The fixtures under `resources/receipts/` are synthetic: `example.json` is the made-up example of
 * Chatelaine's spec, rule 123, and every other file is that example with one thing changed.
 */
class ReceiptFileFormatTest {

    private fun bytes(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/receipts/$name")) { "missing fixture $name" }.use { it.readBytes() }

    private fun parse(name: String) = ReceiptFileFormat.parse(bytes(name))

    private fun unreadable(name: String) = assertEquals(ParseResult.Unreadable, parse(name), name)

    private val food = "b7d1e4f0-2c9a-4e63-8f15-6a0d3c7b9e21"
    private val household = "5e9a0c3d-7f24-4b18-a6e2-1d8c4f0b7a93"

    private val example = ReceiptFile(
        formatVersion = 1, receiptId = "3f6c2a9e-8b41-4d27-9c55-0e7a1b2d4f68", store = "Supermarket A",
        date = LocalDate(2026, 11, 24), currency = "EUR", totalPaid = 1099,
        lines = listOf(
            ReceiptFileLine("cookies Q", "600 g", 3, 318, food, "Food"),
            ReceiptFileLine("batteries", null, 2, 598, household, "Household"),
            ReceiptFileLine("bananas", "865 g", 1, 183, food, "Food"),
        ),
    )

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: every key, the example file
    @Test fun `R117 the rule 123 example reads as 10_99 at Supermarket A on 2026-11-24 with three lines`() {
        assertEquals(ParseResult.Ok(example), parse("example.json"))
    }

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: pack, absent when the line has none
    @Test fun `R117 a line with no pack reads pack null`() {
        val ok = assertIs<ParseResult.Ok>(parse("example.json"))
        assertEquals(null, ok.file.lines[1].pack)
        assertEquals("600 g", ok.file.lines[0].pack)
    }

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: format
    @Test fun `R117 a file with no format is unreadable`() = unreadable("missing-format.json")

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: receiptId
    @Test fun `R117 a file with no receiptId is unreadable`() = unreadable("missing-receiptId.json")

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: store
    @Test fun `R117 a file with no store is unreadable`() = unreadable("missing-store.json")

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: date
    @Test fun `R117 a file with no date is unreadable`() = unreadable("missing-date.json")

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: currency
    @Test fun `R117 a file with no currency is unreadable`() = unreadable("missing-currency.json")

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: totalPaid
    @Test fun `R117 a file with no totalPaid is unreadable`() = unreadable("missing-totalPaid.json")

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: lines
    @Test fun `R117 a file with no lines is unreadable`() = unreadable("missing-lines.json")

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: name
    @Test fun `R117 a line with no name is unreadable`() = unreadable("missing-line-name.json")

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: quantity
    @Test fun `R117 a line with no quantity is unreadable`() = unreadable("missing-line-quantity.json")

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: pricePaid
    @Test fun `R117 a line with no pricePaid is unreadable`() = unreadable("missing-line-pricePaid.json")

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: categoryId
    @Test fun `R117 a line with no categoryId is unreadable`() = unreadable("missing-line-categoryId.json")

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: categoryName
    @Test fun `R117 a line with no categoryName is unreadable`() = unreadable("missing-line-categoryName.json")

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: format, raised by a change to a key's meaning
    @Test fun `R117 format 2 with a key of its own and no lines gives NewerFormat 2`() {
        assertEquals(ParseResult.NewerFormat(2), parse("format-2.json"))
    }

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: format, a whole number starting at 1
    @Test fun `R117 format 0 or format as text is unreadable`() {
        unreadable("format-0.json")
        unreadable("format-text.json")
    }

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: totalPaid, the sum of the lines' pricePaid
    @Test fun `R117 a totalPaid of 11_00 over lines of 10_99 is unreadable`() = unreadable("total-not-sum.json")

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: quantity, a whole number
    @Test fun `R117 a quantity of 1_5 is unreadable`() = unreadable("quantity-not-whole.json")

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: pricePaid and store, by kind
    @Test fun `R117 a pricePaid written as text 3,18 or a store written as a number is unreadable`() {
        unreadable("price-as-text.json")
        unreadable("store-not-text.json")
    }

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: date, as YYYY-MM-DD
    @Test fun `R117 a date of 2026-11-31 or 24 slash 11 slash 2026 is unreadable`() {
        unreadable("date-not-a-day.json")
        unreadable("date-other-form.json")
    }

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rules 123 and 124: receiptId and lines
    @Test fun `R117 a blank receiptId or an empty lines array is unreadable`() {
        unreadable("blank-receipt-id.json")
        unreadable("no-lines.json")
    }

    // SPEC:R117 SPEC:R27 · Chatelaine's spec, rule 123: no key beyond those it lists is read
    @Test fun `R117 keys rule 123 does not name are passed over`() {
        assertEquals(ParseResult.Ok(example), parse("unknown-keys.json"))
    }

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: one JSON object, UTF-8
    @Test fun `R117 a file that is not one UTF-8 JSON object is unreadable`() {
        unreadable("not-json.json")
        unreadable("not-utf8.json")
        unreadable("array.json")
        assertEquals(ParseResult.Unreadable, ReceiptFileFormat.parse(ByteArray(0)))
    }

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: format, raised by a change to a key's meaning
    @Test fun `R117 format 2 with a key of its own and every line in place gives NewerFormat 2`() {
        assertEquals(ParseResult.NewerFormat(2), parse("format-2-new-key.json"))
    }

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: format, a whole number
    @Test fun `R117 format 99999999999 gives NewerFormat 2147483647`() {
        assertEquals(ParseResult.NewerFormat(Int.MAX_VALUE), parse("format-huge.json"))
    }

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: totalPaid, in whole cents
    @Test fun `R117 a totalPaid written as text 1099 in quotes is unreadable`() = unreadable("total-quoted.json")

    // SPEC:R117 SPEC:R115 · Chatelaine's spec, rules 123 and 124: totalPaid and pricePaid of a purchase
    @Test fun `R117 a total of 0_00 or a line of -1_00 among lines that add up is unreadable`() {
        unreadable("zero-total.json")
        unreadable("negative-line.json")
    }

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: quantity, the number of packs bought
    @Test fun `R117 a quantity of 0 is unreadable`() = unreadable("quantity-zero.json")

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: store, currency, name, categoryId, categoryName
    @Test fun `R117 a blank store, currency, line name, categoryId or categoryName is unreadable`() {
        unreadable("blank-store.json")
        unreadable("blank-currency.json")
        unreadable("blank-line-name.json")
        unreadable("blank-line-categoryId.json")
        unreadable("blank-line-categoryName.json")
    }

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: pack as text, lines as objects
    @Test fun `R117 a pack written as a number or lines that are not objects are unreadable`() {
        unreadable("pack-not-text.json")
        unreadable("lines-not-objects.json")
    }

    // SPEC:R117 SPEC:R30 · Chatelaine's spec, rule 123: totalPaid, the sum of the lines' pricePaid
    @Test fun `R117 lines whose sum is too large to hold are unreadable`() = unreadable("sum-overflow.json")

    // SPEC:R117 SPEC:R116
    @Test fun `R117 a file of 3,000 nested arrays is unreadable and does not throw`() {
        val deep = "{\"format\":1,\"x\":" + "[".repeat(3000)
        assertEquals(ParseResult.Unreadable, ReceiptFileFormat.parse(deep.encodeToByteArray()))
        val closed = "{\"format\":1,\"x\":" + "[".repeat(3000) + "]".repeat(3000) + "}"
        assertEquals(ParseResult.Unreadable, ReceiptFileFormat.parse(closed.encodeToByteArray()))
    }

    // SPEC:R117
    @Test fun `R117 a file over 1 MiB is unreadable and one just under reads`() {
        val text = bytes("example.json").decodeToString().trimEnd()
        fun padded(size: Int): ByteArray {
            val head = text.dropLast(1) + ",\"note\":\""
            val tail = "\"}"
            return (head + "x".repeat(size - head.length - tail.length) + tail).encodeToByteArray()
        }
        assertEquals(1_048_576, padded(1_048_576).size)
        assertEquals(ParseResult.Ok(example), ReceiptFileFormat.parse(padded(1_048_576)))
        assertEquals(ParseResult.Unreadable, ReceiptFileFormat.parse(padded(1_048_577)))
    }

    // SPEC:R30 SPEC:R116 SPEC:R117 · Chatelaine's spec, rule 123: file name and temporary name
    @Test fun `R30 a folder with one file per case lists each with its reason and changes no figure`() {
        val dir = File(requireNotNull(javaClass.getResource("/receipts/folder")).toURI())
        val d = profile(categories = listOf(category("food", "Food")))
            .copy(receiptSettings = Fixture.receiptSettings().copy(recordedReceiptIds = listOf("rc-old")))
        val before = d.copy()
        val p = ReceiptPickup.scan(d, FileReceiptFolder(dir), ReceiptFileFormat::parse)
        assertEquals(listOf("rc-new"), p.ready.map { it.receiptId })
        assertEquals(
            listOf<Pair<String, Refusal>>(
                "chatelaine-receipt-rc-bad.json" to Refusal.Unreadable,
                "chatelaine-receipt-rc-usd.json" to Refusal.OtherCurrency("USD", "EUR"),
                "chatelaine-receipt-rc-v2.json" to Refusal.NewerFormat(2),
            ),
            p.refused,
        )
        assertEquals(before, d)
        assertEquals(6, dir.list()!!.size)
    }
}
