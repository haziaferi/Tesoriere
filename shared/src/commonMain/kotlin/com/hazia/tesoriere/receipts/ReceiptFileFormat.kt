package com.hazia.tesoriere.receipts

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/*
 * Tesoriere's own: Sci-Fin at 2892110 had no receipts. Reads a receipt file in the format of
 * Chatelaine's spec, rule 123: one UTF-8 JSON object per receipt, amounts in whole cents (SPEC
 * R27, R117). The keys are read with kotlinx-serialization into a private wire shape that mirrors
 * rule 123 key for key, then checked and turned into the engine's ReceiptFile. Rule 123's key
 * `format` maps to ReceiptFile.formatVersion through @SerialName, keeping the plan's field name.
 */

object ReceiptFileFormat {

    /** Keys rule 123 does not name are passed over (SPEC R117); nothing else is lenient. */
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The file's [bytes] as a receipt (SPEC R117): `format` is read first, so a format above
     * [ReceiptPickup.HIGHEST_FORMAT] gives NewerFormat whatever else the file holds; any other
     * file is read whole, or is Unreadable.
     */
    fun parse(bytes: ByteArray): ParseResult {
        val text = try { bytes.decodeToString(throwOnInvalidSequence = true) } catch (_: CharacterCodingException) { return ParseResult.Unreadable }
        val root = try { json.parseToJsonElement(text) } catch (_: SerializationException) { return ParseResult.Unreadable }
        if (root !is JsonObject) return ParseResult.Unreadable
        val format = (root["format"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return ParseResult.Unreadable
        if (format > ReceiptPickup.HIGHEST_FORMAT) return ParseResult.NewerFormat(format)
        if (format < 1) return ParseResult.Unreadable
        val file = try {
            val w = json.decodeFromJsonElement(WireReceipt.serializer(), root)
            ReceiptFile(
                formatVersion = w.formatVersion, receiptId = w.receiptId, store = w.store, date = LocalDate.parse(w.date),
                currency = w.currency, totalPaid = w.totalPaid,
                lines = w.lines.map { ReceiptFileLine(it.name, it.pack, it.quantity, it.pricePaid, it.categoryId, it.categoryName) },
            )
        } catch (_: IllegalArgumentException) {
            // SerializationException, a bad day and a number out of range are all IllegalArgumentExceptions
            return ParseResult.Unreadable
        }
        if (file.receiptId.isBlank() || file.lines.isEmpty()) return ParseResult.Unreadable
        if (file.totalPaid != file.lines.sumOf { it.pricePaid }) return ParseResult.Unreadable
        return ParseResult.Ok(file)
    }

    /** A receipt as rule 123 writes it; every key is required but `pack`. */
    @Serializable
    private data class WireReceipt(
        @SerialName("format") val formatVersion: Int,
        val receiptId: String,
        val store: String,
        val date: String,
        val currency: String,
        val totalPaid: Long,
        val lines: List<WireLine>,
    )

    /** A line as rule 123 writes it; `pack` is absent when the line has none. */
    @Serializable
    private data class WireLine(
        val name: String,
        val pack: String? = null,
        val quantity: Int,
        val pricePaid: Long,
        val categoryId: String,
        val categoryName: String,
    )
}
