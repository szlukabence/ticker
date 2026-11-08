package dev.ticker.converter.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ReceiptParserTest {
    private fun fixture(name: String): List<String> =
        javaClass.classLoader!!.getResource("receipts/$name")!!.readText().lines()

    private fun amounts(r: Receipt) = r.items.map { it.amount.stripTrailingZeros().toPlainString() }
    private fun eq(e: String, a: BigDecimal?) = assertEquals(0, BigDecimal(e).compareTo(a ?: error("null, expected $e")))

    @Test fun readsItemsAndTotal_hu() {
        val r = ReceiptParser.parse(fixture("hu_tesco.txt"))
        assertEquals(listOf("499", "389", "698", "2290", "474"), amounts(r))
        assertEquals(listOf("KENYÉR FEHÉR 500G", "TEJ 2.8% 1L", "BANÁN", "CSIRKEMELL FILÉ", "PAPÍRZSEBKENDŐ"), r.items.map { it.name })
        eq("4350", r.printedTotal)
    }

    @Test fun readsItemsAndTotal_de() {
        val r = ReceiptParser.parse(fixture("de_rewe.txt"))
        assertEquals(listOf("1.29", "0.79", "2.99", "8.98", "9.42"), amounts(r))
        eq("23.47", r.printedTotal)
    }

    @Test fun readsItemsAndTotal_en() {
        val r = ReceiptParser.parse(fixture("en_cafe.txt"))
        assertEquals(listOf("Flat White", "Croissant", "Avocado Toast", "Orange Juice"), r.items.map { it.name })
        assertEquals(listOf("3.4", "2.75", "8.5", "3.2"), amounts(r))
        eq("17.85", r.printedTotal)
    }

    @Test fun noTotalGivesNull() {
        val r = ReceiptParser.parse(fixture("no_total.txt"))
        assertEquals(listOf("1.2", "2.3", "6.5"), amounts(r))
        assertNull(r.printedTotal)
    }

    @Test fun ignoresDatesTimesIdsAndPercents() {
        val r = ReceiptParser.parse(listOf(
            "2026.11.07 14:05", "07/11/2026", "ADÓSZÁM: 12345678-2-42", "VAT No. 123456789",
            "NAV KÓD: 4A7F", "Tel. 0221-1234567", "27%", "1131 Budapest, Bocskai u. 1.",
        ))
        assertTrue("got ${r.items}", r.items.isEmpty())
    }

    @Test fun skipsTaxPaymentChangeSubtotal() {
        val r = ReceiptParser.parse(listOf("Bread 2.00", "VAT 20% 0.33", "Subtotal 2.00", "Cash 5.00", "Change 3.00", "ÁFA 0,43", "Kártya 2.00"))
        assertEquals(listOf("Bread"), r.items.map { it.name })
    }

    @Test fun quantityLineTakesRightmost() {
        val r = ReceiptParser.parse(listOf("2 x 1.50   3.00"))
        eq("3.00", r.items.single().amount)
    }

    @Test fun quantityDetailWithoutTotalIsSkipped() {
        assertTrue(ReceiptParser.parse(listOf("2 x 349")).items.isEmpty())
    }

    @Test fun accentAndCaseInsensitiveKeywords() {
        eq("100", ReceiptParser.parse(listOf("osszesen 100")).printedTotal)
        eq("200", ReceiptParser.parse(listOf("Összesen: 200")).printedTotal)
        eq("300", ReceiptParser.parse(listOf("Total: 250", "TOTAL 300")).printedTotal) // the last total wins
    }

    @Test fun namesTrimmed() {
        assertEquals("KENYÉR", ReceiptParser.parse(listOf("KENYÉR ....... 499")).items.single().name)
        assertEquals("Milk", ReceiptParser.parse(listOf("Milk  1,29 A")).items.single().name) // VAT class letter after price
        assertEquals("Tea", ReceiptParser.parse(listOf("Tea 2.50 €")).items.single().name)
    }
    // Real Tesseract output on the phone (Task 11): a stray dot before the thousands group, junk after prices.
    @Test fun ocrDotBeforeThousandsGroup() = eq("2290", ReceiptParser.parse(listOf("CSIRKEMELL FILE 2. 290")).items.single().amount)

    @Test fun ocrJunkAfterPrice() {
        eq("474", ReceiptParser.parse(listOf("PAPIRZSEBKENDO 474 |")).items.single().amount)
        eq("4350", ReceiptParser.parse(listOf("OSSZESEN: 4 350 .")).printedTotal)
    }
    // Final review: keywords must be whole words, not prefixes.
    @Test fun keywordsAreWholeWords() {
        val r = ReceiptParser.parse(listOf("Cashew nuts 4.20", "Cardigan 19.99", "Gegrilltes Hähnchen 6,90", "Summer salad 7.50", "Geg. BAR 50,00"))
        assertEquals(listOf("Cashew nuts", "Cardigan", "Gegrilltes Hähnchen", "Summer salad"), r.items.map { it.name })
        assertNull(r.printedTotal)
    }

    // Final review: discounts and deposit returns subtract.
    @Test fun discountsAreNegative() {
        val r = ReceiptParser.parse(listOf("Milk 1,29", "Rabatt -0,50", "Leergut 0,25-"))
        assertEquals(listOf("Milk", "Rabatt", "Leergut"), r.items.map { it.name })
        assertEquals(listOf("1.29", "-0.5", "-0.25"), amounts(r))
    }

    // Final review: payment lines after the total ("Visa", "Girocard") are not items.
    @Test fun nothingAfterTotalIsAnItem() {
        val r = ReceiptParser.parse(listOf("Tea 2.50", "Cake 3.00", "TOTAL 5.50", "Visa 5.50", "Items 2"))
        assertEquals(listOf("Tea", "Cake"), r.items.map { it.name })
        eq("5.50", r.printedTotal)
    }
    // Real Lidl receipt photographed by the user (Tesseract output, 3000 px): total on the next line,
    // store number in the header, receipt number in the footer.
    @Test fun realLidlReceipt() {
        val r = ReceiptParser.parse(listOf(
            "LID. MAGYARORS7 Ag BT.",
            "037 BUDAPEST RADL AROK 6",
            "UZLET: 100",
            "£000 Peldavaros Minta utca 1.",
            "ADOSZAN: 21588017- 2-44",
            "| NYUGTA - Te .",
            "_bou Abronestgg' \"0517564 — 3 999",
            "coy a Imp Fogs * +0503185 6 999",
            "Coo Keresztygy: 0545353 19 999",
            "meee Forr sevéd' +0523143 2 499",
            "OSSZESEN:",
            "Bankkartya: | 33 496 Ft",
            "TID:00000000 2026. 01.01 10:00",
            "HH000000 0000000000",
            "VASARLAS 33496. Ft",
            "NYUGTASZAM: 0000/00000",
        ))
        assertEquals(listOf("3999", "6999", "19999", "2499"), amounts(r))
        eq("33496", r.printedTotal)
    }

    @Test fun totalOnTheNextLine() = eq("12.50", ReceiptParser.parse(listOf("Tea 12.50", "TOTAL:", "12.50")).printedTotal)

    @Test fun garbledCurrencyAfterPrice() = eq("33496", ReceiptParser.parse(listOf("OSSZESEN; 33 496 FE")).printedTotal)

    @Test fun storeAndReceiptNumbersAreNotItems() =
        assertTrue(ReceiptParser.parse(listOf("ÜZLET: 100", "NYUGTASZÁM: 0000/00000", "Store 42")).items.isEmpty())
    // Real OCR: a product code ending in a stray dot must not swallow the price after it.
    @Test fun codeWithStrayDotKeepsPriceSeparate() {
        eq("2499", ReceiptParser.parse(listOf("Forr seged +0523143. 2499")).items.single().amount)
        eq("2499", ReceiptParser.parse(listOf("Forr seged +0523143. 2 499")).items.single().amount)
    }
    // A real Lidl receipt photo as the app's pipeline reads it (crop, deskew, sparse words, rows).
    // Store address, date, card, bank and transaction details are replaced with dummy values.
    @Test fun realLidlReceiptPipelineOutput() {
        val r = ReceiptParser.parse(fixture("hu_lidl_ocr.txt"))
        assertEquals(listOf("3999", "6999", "19999", "2499"), amounts(r))
        eq("33496", r.printedTotal)
    }
}
