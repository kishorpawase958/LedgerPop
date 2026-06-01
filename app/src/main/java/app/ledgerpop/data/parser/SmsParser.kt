package app.ledgerpop.data.parser

import app.ledgerpop.data.category.CategoryEngine
import java.util.Locale

data class ParsedSmsTransaction(
    val amount: Double,
    val type: String,
    val merchant: String,
    val accountLast4: String,
    val bank: String,
    val accountName: String,
    val category: String,
    val includeInAnalytics: Boolean = true
)

object SmsParser {

    private val amountPatterns = listOf(
        // Prefer INR Equivalent for international transactions
        Regex("""(?:Inr Equiv Approx|Inr Equiv|Equiv\.? INR)\s*(?:INR|Rs\.?|₹)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:INR|Rs\.?|₹)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:debited by|debited from|credited by|credited to|credited with|payment of|spent on|spent|spend|paid from|for|of|withdrawn at)\s*(?:INR|Rs\.?|₹)?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    private val debitKeywords = listOf("debited", "spent", "spend", "paid", "payment", "purchase", "trf to", "sent to", "withdrawal", "withdrawn", "txn of")
    private val creditKeywords = listOf("credited", "received", "refund", "added to", "deposited", "transfer from", "trf from")
    private val spamKeywords = listOf("otp","one time password","Reminder","cooling period","generated", "request", "require", "allot", "disburse", "declined", "failed", "unsuccessful", "insufficient", "will be", "due")

    fun parse(sender: String, body: String): ParsedSmsTransaction? {
        val text = body.replace("\n", " ").replace(Regex("""\s+"""), " ").trim()
        val lower = text.lowercase(Locale.getDefault())

        // Filter out spam or non-transactional messages
        if (spamKeywords.any { lower.contains(it) }) return null

        val amount = extractAmount(text) ?: return null
        val type = detectType(text) ?: return null

        // Extract Account Data
        val accountLast4 = extractAccountLast4(text)
        val bankName = detectBank(sender, text)

        // Format exactly as "Bank Name (XXXX)" - max 3 words for bank name
        val cleanBankName = bankName.split("\\s+".toRegex()).asSequence().take(3).joinToString(" ")
        val accountName = if (accountLast4.isNotBlank()) "$cleanBankName ($accountLast4)" else cleanBankName

        // Extract Merchant Name (Max 3 words, fallback to "Unknown" or "Account Credit")
        var merchant = extractMerchant(text, type)

        // Identify credit card bill payments (repayments) to exclude them from analytics (avoid double counting).
        val isRepayment = (merchant == "UPI funds transfer" || merchant == "Account Credit") &&
            lower.contains("credit card") &&
            (lower.contains("towards") || lower.contains("for your") || lower.contains("payment") || lower.contains("received") || lower.contains("successful"))

        // Fallback: If merchant is not found (UPI funds transfer/Account Credit), try to find UPI Ref No.
        if (merchant == "UPI funds transfer" || merchant == "Account Credit") {
            val upiRef = extractUpiRef(text)
            if (upiRef != null) {
                merchant = "UPI $upiRef"
            }
        }

        val cleanMerchant = if (merchant == "UPI funds transfer" || (merchant == "Account Credit")) {
            merchant
        } else {
            merchant.split("\\s+".toRegex()).asSequence().take(3).joinToString(" ")
        }

        // Auto-categorize based on the clean merchant and body
        val category = CategoryEngine.categorize(cleanMerchant, body, sender)

        return ParsedSmsTransaction(
            amount = amount,
            type = type,
            merchant = cleanMerchant,
            accountLast4 = accountLast4,
            bank = cleanBankName,
            accountName = accountName,
            category = category,
            includeInAnalytics = !isRepayment
        )
    }

    private fun extractAmount(text: String): Double? {
        for (pattern in amountPatterns) {
            val match = pattern.find(text)
            val value = match?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
            if (value != null && value > 0) return value
        }
        return null
    }

    private fun extractUpiRef(text: String): String? {
        val patterns = listOf(
            Regex("""(?i)(?:UPI\s*ref\s*no\.?|Ref\s*no\.?|UPI\s*Ref)\s*([0-9]{8,14})"""),
            Regex("""(?i)UPI/([0-9]{8,14})""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            val ref = match?.groupValues?.getOrNull(1)
            if (!ref.isNullOrBlank()) return ref
        }
        return null
    }

    private fun detectType(text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        val hasDebit = debitKeywords.any { lower.contains(it) }
        val hasCredit = creditKeywords.any { lower.contains(it) }

        return when {
            // Prioritize "withdrawn" as it's very specific to ATM/Cash withdrawals
            lower.contains("withdrawn") -> "DEBIT"

            // Prioritize clear credit indicators
            lower.contains("credited") || lower.contains("received") -> "CREDIT"

            hasDebit && !hasCredit -> "DEBIT"
            hasCredit && !hasDebit -> "CREDIT"
            else -> null
        }
    }

    private fun extractMerchant(text: String, type: String): String {
        // Stop words that indicate the end of a merchant name and the start of transaction details.
        // We avoid very short words like 'is' or 'at' here as they often appear in names (e.g., Ashish).
        val lookahead = """(?=\s+(?:on|via|with|using|ref|refno|from|upi|avl|bal|for|card|a/c|under|chk|your)\b|\s+\.|\.\s+|\.$|$)"""

        val patterns = listOf(
            // Specific formats to handle them as a single block before lookahead stops them
            Regex("""(?i)for\s+(?:payment\s+to|order\s+at|trip\s+at|purchase\s+at)\s+([A-Za-z0-9\s.&'/-]{2,40}?)$lookahead"""),

            // 4: by [METHOD] from [Merchant]
            Regex("""(?i)by\s+[A-Za-z0-9]+\s+from\s+([A-Za-z0-9\s.&'/-]{2,40}?)$lookahead"""),

            // 7: spent at [Merchant]
            Regex("""(?i)spent\s+at\s+([A-Za-z0-9\s.&'/-]{2,40}?)$lookahead"""),

            // Handle "trf to [Merchant]" specifically as it often ends with "Refno"
            Regex("""(?i)trf to\s+([A-Za-z0-9\s.&'/-]{2,40}?)(?=\s+(?:Refno|Ref|on|at|for|via|$|\.))"""),

            // New pattern for "IST [Merchant]" format (common in some multi-line SMS)
            Regex("""(?i)IST\s+([A-Za-z0-9\s.&'-]{2,40}?)$lookahead"""),

            // Pattern for Axis Bank "Info - " format and similar descriptors
            Regex("""(?i)(?:Info\s*-\s*|Info:\s*)([A-Za-z0-9\s.&'/-]{2,40}?)$lookahead"""),

            // General pattern with expanded prefixes. Negative lookahead to avoid capturing "at your", "from A/c"
            Regex("""(?i)(?:at(?!\s+your\b)|to|info\s*vpa|paid\s*to|sent\s*to|towards|transfer\s+from|trf\s+from|for|from(?!\s+(?:your|a/c|acct|my)\b))\s+([A-Za-z0-9\s.&'/-]{2,40}?)$lookahead"""),

            Regex("""(?i)paid from your .+ to\s+([A-Za-z0-9\s.&'/-]{2,40}?)$lookahead"""),
            Regex("""(?i)trf to\s+([A-Za-z0-9\s.&'/-]{2,40}?)(?:\s+Refno|\.|$)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            val merchant = match?.groupValues?.getOrNull(1)?.trim()

            if (!merchant.isNullOrBlank() && merchant.length > 2) {
                // Sanity check: Ensure we didn't accidentally capture bank jargon or currency
                val lower = merchant.lowercase()
                if (lower.startsWith("your") || lower.contains("card") || lower.contains("account") || 
                    lower.startsWith("rs") || lower.startsWith("inr") || lower.contains("a/c")) {
                    continue
                }
                // Filter out generic bank names unless it's an ATM
                if (lower.contains("bank") && !lower.contains("atm")) {
                    continue
                }
                return merchant
            }
        }

        return if (type == "DEBIT") "UPI funds transfer" else "Account Credit"
    }

    private fun extractAccountLast4(text: String): String {
        val patterns = listOf(
            Regex("""(?i)(?:a/c|ac|account|card|ending(?: in)?|no\.?|xx|\*\*)\s*x*-?\s*(\d{4})(?!\d)"""),
            Regex("""XX(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""\*\*(\d{4})""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            val last4 = match?.groupValues?.getOrNull(1)
            if (!last4.isNullOrBlank()) return last4
        }
        return ""
    }

    private fun detectBank(sender: String, text: String): String {
        val s = sender.uppercase(Locale.getDefault()).replace("-", "")
        val t = text.uppercase(Locale.getDefault())

        val knownBank = when {
            s.contains("SBI") || t.contains("SBI") -> "SBI"
            s.contains("HDFC") || t.contains("HDFC") -> "HDFC Bank"
            s.contains("ICICI") || t.contains("ICICI") -> "ICICI Bank"
            s.contains("AXIS") || t.contains("AXIS BANK") -> "Axis Bank"
            s.contains("CSB") || t.contains("CSB") -> "Edge CSB Bank"
            s.contains("SIB") || t.contains("SOUTH INDIAN") -> "SIB"
            s.contains("JUPITR") || t.contains("JUPITER") -> "Jupiter"
            s.contains("ONECRD") || t.contains("ONECARD") -> "OneCard"
            s.contains("YESBNK") || t.contains("YES BANK") -> "Yes Bank"
            s.contains("KOTAK") || t.contains("KOTAK") -> "Kotak Bank"
            s.contains("PNB") || t.contains("PNB") -> "PNB"
            s.contains("BOI") || t.contains("BANK OF INDIA") -> "Bank of India"
            s.contains("IDFC") || t.contains("IDFC") -> "IDFC First Bank"
            s.contains("INDUS") || t.contains("INDUSIND") -> "IndusInd Bank"
            else -> null
        }

        knownBank?.let { return it }

        // Match "paid from your [Bank Name] to"
        val paidFromMatch = Regex("""(?i)paid from your\s+([A-Za-z0-9\s]+?)\s+to""").find(text)
        val paidFromName = paidFromMatch?.groupValues?.getOrNull(1)?.trim()
        if (!paidFromName.isNullOrBlank()) return paidFromName

        // If sender isn't mapped, try to find "Card" or "Bank" in the text
        val cardMatch = Regex("""(?i)(?:on your|done on|from your|at)\s+([A-Za-z0-9\s]+?)\s*(?:credit|debit)?\s*(?:card|bank)""").find(text)
        return cardMatch?.groupValues?.getOrNull(1)?.trim() ?: "Unknown Bank"
    }
}
