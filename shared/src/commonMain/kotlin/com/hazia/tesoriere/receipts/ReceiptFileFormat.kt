package com.hazia.tesoriere.receipts

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Tesoriere's own: Sci-Fin at 2892110 had no receipts. Reads a receipt file in the format of
 * Chatelaine's spec, rule 123: one UTF-8 JSON object per receipt, amounts in whole cents (SPEC
 * R27, R117). kotlinx-serialization parses the text into its JSON tree; each key rule 123 names is
 * then read from the tree by hand and checked for its kind, so a number in quotes, which the
 * library's decoder would take, is refused. Rule 123's key `format` is read into
 * ReceiptFile.formatVersion, the plan's field name. Keys rule 123 does not name are never read.
 */

object ReceiptFileFormat {

    /** The largest file read: far above any receipt (SPEC R117). */
    const val MAX_BYTES = 1_048_576

    /** The deepest nesting of arrays and objects read; rule 123's own is 3 (SPEC R117). */
    const val MAX_DEPTH = 64

    private val json = Json

    /** A JSON number written as a whole number: no sign but minus, no fraction, no exponent. */
    private val WHOLE = Regex("-?(0|[1-9][0-9]*)")

    /** One key that fails its row (SPEC R117): the file is unreadable. */
    private class Unreadable : Exception()

    /**
     * The file's [bytes] as a receipt (SPEC R117): `format` is read first, so a format above
     * [ReceiptPickup.HIGHEST_FORMAT] gives NewerFormat whatever else the file holds; any other
     * file is read whole, or is Unreadable. It never throws: a file too large or too deep is
     * refused before parsing, and any failure while reading is Unreadable.
     */
    fun parse(bytes: ByteArray): ParseResult {
        if (bytes.size > MAX_BYTES) return ParseResult.Unreadable
        return try {
            read(bytes)
        } catch (_: Throwable) {
            // a file in a synced folder must never stop a scan (SPEC R116), whatever it holds
            ParseResult.Unreadable
        }
    }

    private fun read(bytes: ByteArray): ParseResult {
        val text = bytes.decodeToString(throwOnInvalidSequence = true)
        if (tooDeep(text)) return ParseResult.Unreadable
        val root = json.parseToJsonElement(text) as? JsonObject ?: return ParseResult.Unreadable
        val format = root["format"] as? JsonPrimitive ?: return ParseResult.Unreadable
        if (format.isString || !WHOLE.matches(format.content)) return ParseResult.Unreadable
        val version = format.content.toIntOrNull()
            // a whole number too large for an Int is still above the highest format
            ?: return if (format.content.startsWith("-")) ParseResult.Unreadable else ParseResult.NewerFormat(Int.MAX_VALUE)
        if (version > ReceiptPickup.HIGHEST_FORMAT) return ParseResult.NewerFormat(version)
        if (version < 1) return ParseResult.Unreadable
        return try {
            ParseResult.Ok(receipt(root, version))
        } catch (_: Unreadable) {
            ParseResult.Unreadable
        } catch (_: IllegalArgumentException) {
            // LocalDate.parse on a day that is not one
            ParseResult.Unreadable
        }
    }

    private fun receipt(o: JsonObject, version: Int): ReceiptFile {
        val lines = (o["lines"] as? JsonArray ?: throw Unreadable()).map { line(it as? JsonObject ?: throw Unreadable()) }
        if (lines.isEmpty()) throw Unreadable()
        val total = whole(o, "totalPaid")
        if (total <= 0) throw Unreadable()
        var sum = 0L
        for (l in lines) {
            // every price is zero or above, so only an overflow past the top is possible
            if (l.pricePaid > Long.MAX_VALUE - sum) throw Unreadable()
            sum += l.pricePaid
        }
        if (sum != total) throw Unreadable()
        return ReceiptFile(
            formatVersion = version, receiptId = text(o, "receiptId"), store = text(o, "store"),
            date = LocalDate.parse(text(o, "date")), currency = text(o, "currency"), totalPaid = total, lines = lines,
        )
    }

    private fun line(o: JsonObject): ReceiptFileLine {
        val pack = o["pack"]?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: throw Unreadable() }
        val quantity = whole(o, "quantity")
        if (quantity < 1 || quantity > Int.MAX_VALUE) throw Unreadable()
        val price = whole(o, "pricePaid")
        if (price < 0) throw Unreadable()
        return ReceiptFileLine(text(o, "name"), pack, quantity.toInt(), price, text(o, "categoryId"), text(o, "categoryName"))
    }

    /** Key [key] as JSON text that is not blank. */
    private fun text(o: JsonObject, key: String): String {
        val p = o[key] as? JsonPrimitive ?: throw Unreadable()
        if (!p.isString || p.content.isBlank()) throw Unreadable()
        return p.content
    }

    /** Key [key] as a JSON number written as a whole number that a Long holds; never a number in quotes. */
    private fun whole(o: JsonObject, key: String): Long {
        val p: JsonElement = o[key] ?: throw Unreadable()
        if (p !is JsonPrimitive || p.isString || !WHOLE.matches(p.content)) throw Unreadable()
        return p.content.toLongOrNull() ?: throw Unreadable()
    }

    /** Whether [text] nests arrays and objects deeper than [MAX_DEPTH], counted outside strings. */
    private fun tooDeep(text: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '[', '{' -> if (++depth > MAX_DEPTH) return true
                ']', '}' -> depth--
            }
        }
        return false
    }
}
