package com.hazia.tesoriere.statements

import com.hazia.tesoriere.model.StoredLabels
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported from Sci-Fin's ImportHeaderTest.kt and the parser half of ImportBackupTest.kt at 2892110:
 * header matching, dates and the two formats (SPEC R103, R105, R106). Every payee is made up.
 */
class StatementHeaderTest {
    private fun text(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/statements/$name")) { "missing fixture $name" }.use { it.readBytes().decodeToString() }

    // SPEC:R105
    @Test fun `R105 a Value date column is a date and not the amount`() {
        val rows = StatementImport.parseCsv("Booking date,Value date,Description,Amount\n01/09/2026,02/09/2026,POS CAFFE DEL MOLO,-3.50\n")
        assertEquals(1, rows.size)
        assertEquals(LocalDate(2026, 9, 1), rows[0].date)
        assertEquals(-350L, rows[0].amount)
        assertEquals("POS CAFFE DEL MOLO", rows[0].description)
    }

    // SPEC:R105 SPEC:R104
    @Test fun `R105 with Debit and Credit columns a Value date column is not the amount, a debit of 3_50 gives -3_50`() {
        val rows = StatementImport.parseCsv("Booking date,Value date,Description,Debit,Credit\n01/09/2026,04/09/2026,POS CAFFE DEL MOLO,3.50,\n")
        assertEquals(1, rows.size)
        assertEquals(LocalDate(2026, 9, 1), rows[0].date)
        assertEquals(-350L, rows[0].amount)
        assertEquals("POS CAFFE DEL MOLO", rows[0].description)
    }

    // SPEC:R105
    @Test fun `R105 a Credit card number column is not the credit column, debit 800_00 and credit 12_00`() {
        val rows = StatementImport.parseCsv(
            "Date,Credit card number,Description,Debit,Credit\n2026-09-01,0000 1111,AFFITTO STUDIO,800.00,\n2026-09-02,0000 1111,RIMBORSO NEGOZIO,,12.00\n",
        )
        assertEquals(listOf(-80000L, 1200L), rows.map { it.amount })
    }

    // SPEC:R105
    @Test fun `R105 one-word compounds still match their key, -800_00`() {
        val rows = StatementImport.parseCsv("Buchungsdatum;Verwendungszweck;Umsatzbetrag\n01.09.2026;MIETE WOHNUNG;-800,00\n")
        assertEquals(1, rows.size)
        assertEquals(-80000L, rows[0].amount)
        assertEquals("MIETE WOHNUNG", rows[0].description)
        assertEquals(LocalDate(2026, 9, 1), rows[0].date)
    }

    // SPEC:R105
    @Test fun `R105 a CSV with no usable header parses nothing`() {
        assertTrue(StatementImport.parseCsv("a;b;c\n1;2;3").isEmpty())
        assertTrue(StatementImport.parseCsv(text("no-header.csv")).isEmpty())
        assertTrue(StatementImport.parseCsv("").isEmpty())
    }

    // SPEC:R106
    @Test fun `R106 day-first dates, ISO dates and two-digit years`() {
        assertEquals(LocalDate(2026, 4, 3), StatementImport.date("03/04/2026"))
        assertEquals(LocalDate(2026, 4, 3), StatementImport.date("2026-04-03"))
        assertEquals(LocalDate(2026, 4, 3), StatementImport.date("03.04.26"))
        assertEquals(LocalDate(2026, 4, 3), StatementImport.date("3-4-2026"))
        assertNull(StatementImport.date("yesterday"))
        assertNull(StatementImport.date("31/02/2026"))
        assertNull(StatementImport.date(null))
    }

    // SPEC:R106, R103
    @Test fun `R106 05_03_2026 in the March CSV is 5 March, and its 0_00 row is dropped`() {
        val rows = StatementImport.parseCsv(text("march.csv"))
        assertEquals(5, rows.size)
        assertEquals(LocalDate(2026, 3, 5), rows.single { it.description == "AFFITTO CASA RENT" }.date)
        assertEquals(145000L, rows.single { it.description == "STIPENDIO MARZO" }.amount)
        assertTrue(rows.all { it.fitid == null })
    }

    // SPEC:R103, R104
    @Test fun `R103 a semicolon CSV with Italian headers parses every readable row`() {
        val rows = StatementImport.parseCsv(text("italian-semicolon.csv"))
        assertEquals(
            listOf("STIPENDIO APRILE", "POS MERCATO VERDE 0412", "POS BAR LA PIAZZETTA", "TRENO REGIONALE", "COMMISSIONE CONTO", "ARROTONDAMENTO"),
            rows.map { it.description },
        )
        assertEquals(listOf(145000L, -1240L, -420L, -123456L, -250L, 50L), rows.map { it.amount })
        assertEquals(LocalDate(2026, 4, 10), rows[3].date)
        assertTrue(rows.all { it.fitid == null })
    }

    // SPEC:R103
    @Test fun `R103 OFX rows carry the FITID, fall back to the memo and drop a 0_00 row`() {
        val rows = StatementImport.parseOfx(text("march.ofx"))
        assertEquals(5, rows.size)
        assertEquals("TSRM0001", rows[0].fitid)
        assertEquals(LocalDate(2026, 3, 3), rows[0].date)
        assertEquals(-1240L, rows[0].amount)
        assertEquals("FARMACIA SAN LORENZO", rows.single { it.fitid == "TSRM0004" }.description)
        assertTrue(rows.none { it.fitid == "TSRM0005" })
    }

    // SPEC:R103
    @Test fun `R103 a row with neither name nor description reads as Statement entry`() {
        val ofx = StatementImport.parseOfx("<STMTTRN><DTPOSTED>20260320<TRNAMT>-5.00<FITID>TSRX0001</STMTTRN>")
        assertEquals(listOf(StoredLabels.STATEMENT_ENTRY), ofx.map { it.description })
        assertEquals(-500L, ofx.single().amount)
        val csv = StatementImport.parseCsv("Date,Description,Amount\n2026-03-20,,-5.00\n")
        assertEquals(listOf(StoredLabels.STATEMENT_ENTRY), csv.map { it.description })
    }

    // SPEC:R103, R106
    @Test fun `R103 an OFX block with no readable day gives no row`() {
        assertTrue(StatementImport.parseOfx("<STMTTRN><DTPOSTED>20260231<TRNAMT>-5.00<NAME>POS EDICOLA</STMTTRN>").isEmpty())
        assertTrue(StatementImport.parseOfx("<STMTTRN><DTPOSTED>2026<TRNAMT>-5.00<NAME>POS EDICOLA</STMTTRN>").isEmpty())
    }
}
