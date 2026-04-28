package app.ledgerpop.data.sms

object SmsFilter {

    // Suffixes that are definitely promotional
    private val PROMO_SUFFIXES = setOf("-P")

    // Suffixes we trust as transactional/service
    private val SERVICE_SUFFIXES = setOf("-S", "-T", "-G")

    // Known bank/fintech keywords in sender ID
    private val BANK_KEYWORDS = listOf(
        "HDFCBK", "HDFCBN", "SBIUPI", "SBIBNK", "SBIPSG", "ATMSBI", "SBYONO", "CBSSBI",
        "AXISBK", "AXISMR", "AXISMF", "ICICIT", "ICICIP",
        "YESBNK", "YESBAK", "ONECRD", "PAYZAP", "MOBIKW",
        "PPFAMF", "SBIMFM", "HSBCMF", "QNTAMC", "AXISMF",
        "GKIWIP", "VIJAYS", "POLBAZ"
    )

    // Transaction keywords that confirm a financial message
    private val TRANSACTION_KEYWORDS = listOf(
        "debited", "credited", "debit", "credit",
        "spent", "spend", "paid", "payment",
        "transferred", "withdrawn", "refund",
        "trf to", "inr ", "rs.", "rs ", "₹",
        "debited by", "credited to",
        "balance", "a/c", "ac no", "acct"
    )

    fun shouldProcess(sender: String, body: String): Boolean {
        val upperSender = sender.uppercase()
        val lowerBody = body.lowercase()

        // Reject personal numbers (10+ digit phone numbers)
        if (sender.matches(Regex("""^\+?91?\d{10}$"""))) return false

        // Extract TRAI suffix
        val suffix = extractSuffix(upperSender)

        // Hard block promotional
        if (suffix == "-P") return false

        // Service/transactional suffix — check body for transaction keywords
        if (suffix in SERVICE_SUFFIXES) {
            return TRANSACTION_KEYWORDS.any { lowerBody.contains(it) }
        }

        // No suffix (legacy format like "VM-SBIUPI", "AD-AXISBK") — check bank keyword OR body keywords
        val hasBankKeyword = BANK_KEYWORDS.any { upperSender.contains(it) }
        val hasTransactionKeyword = TRANSACTION_KEYWORDS.any { lowerBody.contains(it) }

        return hasBankKeyword || hasTransactionKeyword
    }

    fun skipReason(sender: String, body: String): String {
        val upperSender = sender.uppercase()
        val lowerBody = body.lowercase()

        if (sender.matches(Regex("""^\+?91?\d{10}$"""))) return "Personal number, not a service sender"

        val suffix = extractSuffix(upperSender)
        if (suffix == "-P") return "TRAI Promotional sender (-P) marketing/offers, not a transaction"

        if (suffix in SERVICE_SUFFIXES) {
            if (TRANSACTION_KEYWORDS.none { lowerBody.contains(it) }) {
                return "No transaction keywords or amount found in message body"
            }
            return "Unknown TRAI suffix"
        }

        val hasBankKeyword = BANK_KEYWORDS.any { upperSender.contains(it) }
        val hasTransactionKeyword = TRANSACTION_KEYWORDS.any { lowerBody.contains(it) }

        if (!hasBankKeyword && !hasTransactionKeyword) {
            return "Sender not in known bank list and no transaction keywords matched"
        }

        return "Unknown TRAI suffix"
    }

    private fun extractSuffix(upperSender: String): String {
        val parts = upperSender.split("-")
        return if (parts.size >= 3) "-${parts.last()}" else ""
    }
}