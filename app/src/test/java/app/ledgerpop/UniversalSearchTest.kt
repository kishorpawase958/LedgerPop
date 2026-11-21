package app.ledgerpop

import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.ui.viewmodel.TransactionsViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalSearchTest {

    private val sampleTxn = SmsTransactionEntity(
        id = 1,
        sender = "VM-HDFCBK",
        body = "Rs 450.00 debited from a/c XX1234 to Starbucks on 12-10-2026 ref 123456789",
        amount = 450.0,
        type = "DEBIT",
        merchant = "Starbucks",
        category = "Food & Dining",
        accountHint = "HDFC (1234)",
        isBillable = true,
        transactionTime = 1760250000000L,
        hashKey = "hash1",
        bank = "HDFC Bank",
        note = "Team coffee meeting with Alex"
    )

    @Test
    fun searchByMerchantName() {
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "Starbucks"))
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "starbucks"))
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "star"))
    }

    @Test
    fun searchByTitleOrSender() {
        // Direct sender match
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "HDFCBK"))
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "vm-hdfcbk"))

        // When merchant is blank, sender is title
        val blankMerchantTxn = sampleTxn.copy(merchant = "")
        assertTrue(TransactionsViewModel.matchesUniversalSearch(blankMerchantTxn, "VM-HDFCBK"))
    }

    @Test
    fun searchBySmsBody() {
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "123456789"))
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "debited from a/c"))
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "XX1234"))
    }

    @Test
    fun searchByNotes() {
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "Team coffee"))
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "Alex"))
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "meeting"))
    }

    @Test
    fun searchByCategory() {
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "Food & Dining"))
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "Dining"))
    }

    @Test
    fun searchByAccountAndBank() {
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "HDFC (1234)"))
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "HDFC Bank"))
    }

    @Test
    fun searchByAmount() {
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "450"))
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "₹450"))
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "Rs. 450"))
    }

    @Test
    fun searchCrossFieldMultiToken() {
        // Merchant + Note
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "Starbucks Alex"))
        // Merchant + Amount
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "Starbucks 450"))
        // Note + SMS content
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "coffee 123456789"))
        // Category + Note
        assertTrue(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "Dining meeting"))
    }

    @Test
    fun searchNonMatchingQueryReturnsFalse() {
        assertFalse(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "Netflix"))
        assertFalse(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "Pizza"))
        assertFalse(TransactionsViewModel.matchesUniversalSearch(sampleTxn, "Starbucks 9999"))
    }
}
