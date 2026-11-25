package com.hazia.tesoriere.receipts

import com.hazia.tesoriere.model.Money
import kotlinx.datetime.LocalDate

/*
 * Tesoriere's own: Sci-Fin at 2892110 had no receipts. The fields of a receipt file as
 * Chatelaine's spec, rule 123, lists them, held in memory once the file is read (SPEC R27). How
 * the file is encoded is the parser's (ReceiptFileFormat); everything else works on these.
 */

/**
 * One receipt as Chatelaine writes it (Chatelaine's spec, rule 123): [formatVersion] is the file's
 * `format` key; [totalPaid] is in cents and is the sum of the lines' [ReceiptFileLine.pricePaid].
 * [currency] is an ISO 4217 code, `EUR` in Chatelaine's v1.
 */
data class ReceiptFile(
    val formatVersion: Int,
    val receiptId: String,
    val store: String,
    val date: LocalDate,
    val currency: String,
    val totalPaid: Money,
    val lines: List<ReceiptFileLine>,
)

/**
 * One line of a receipt (Chatelaine's spec, rule 123): [pack] is null when the line has none;
 * [quantity] is a whole number of packs; [pricePaid] is the whole line's price in cents, every
 * discount applied; [categoryId] and [categoryName] are Chatelaine's category, never Tesoriere's.
 */
data class ReceiptFileLine(
    val name: String,
    val pack: String?,
    val quantity: Int,
    val pricePaid: Money,
    val categoryId: String,
    val categoryName: String,
)
