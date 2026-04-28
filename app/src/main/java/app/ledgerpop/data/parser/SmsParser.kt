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
    val category: String
)

object SmsParser {

    private val amountPatterns = listOf(
        Regex("""(?:INR|Rs\.?)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:debited by|credited by|payment of|spent on|spend)\s*(?:INR|Rs\.?)?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    private val debitKeywords = listOf("debited", "spent", "spend", "paid", "purchase", "trf to", "sent to")
    private val creditKeywords = listOf("credited", "received", "refund", "payment of", "added to")

    fun parse(sender: String, body: String): ParsedSmsTransaction? {
        val text = body.replace("\n", " ").replace(Regex("""\s+"""), " ").trim()
        val amount = extractAmount(text) ?: return null
        val type = detectType(text) ?: return null

        // Extract Account Data
        val accountLast4 = extractAccountLast4(text)
        val bankName = detectBank(sender, text)

        // Format exactly as "Bank Name (XXXX)" - max 3 words for bank name
        val cleanBankName = bankName.split("\\s+".toRegex()).take(3).joinToString(" ")
        val accountName = if (accountLast4.isNotBlank()) "$cleanBankName ($accountLast4)" else cleanBankName

        // Extract Merchant Name (Max 3 words, fallback to "Unknown")
        val merchant = extractMerchant(text, type)
        val cleanMerchant = if (merchant == "Unknown" || merchant == "Account Credit") {
            merchant
        } else {
            merchant.split("\\s+".toRegex()).take(3).joinToString(" ")
        }

        // Auto-categorize based on the clean merchant and body
        val category = CategoryEngine.categorize(cleanMerchant, body, sender)

        return ParsedSmsTransaction(
            amount = amount,
            type = type,
            merchant = cleanMerchant,
            accountLast4 = accountLast4,
            bank = bankName,
            accountName = accountName,
            category = category
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

    private fun detectType(text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        val hasDebit = debitKeywords.any { lower.contains(it) }
        val hasCredit = creditKeywords.any { lower.contains(it) }

        return when {
            hasDebit && !hasCredit -> "DEBIT"
            hasCredit && !hasDebit -> "CREDIT"
            lower.contains("payment of") && lower.contains("credited") -> "CREDIT"
            else -> null
        }
    }

    private fun extractMerchant(text: String, type: String): String {
        val patterns = listOf(
            // Negative lookahead (?!\s+your\b) ignores "at your"
            // Positive lookahead adds "with" and "using" to stop matching before card details
            Regex("""(?i)(?:at(?!\s+your\b)|to|info\s*vpa|paid\s*to|sent\s*to|spent\s*at)\s+([A-Za-z0-9\s.&'-]{2,40}?)(?=\s+(?:on|via|with|using|ref|upi|avl|bal|for|card|a/c|is|$|\.))"""),
            Regex("""(?i)(?:trf to)\s+([A-Za-z0-9\s.&'-]{2,40}?)(?:\s+Refno|\.|$)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            val merchant = match?.groupValues?.getOrNull(1)?.trim()

            if (!merchant.isNullOrBlank() && merchant.length > 2) {
                // Sanity check: Ensure we didn't accidentally capture bank jargon
                val lower = merchant.lowercase()
                if (lower.startsWith("your") || lower.contains("bank") || lower.contains("card") || lower.contains("account")) {
                    continue
                }
                return merchant
            }
        }

        return if (type == "DEBIT") "Unknown" else "Account Credit"
    }

    private fun extractAccountLast4(text: String): String {
        val patterns = listOf(
            Regex("""(?i)(?:a/c|ac|account|card|ending(?: in)?|no\.?|xx|\*\*)\s*x*-?\s*(\d{4})(?!\d)"""),
            Regex("""XX(\d{4})""", RegexOption.IGNORE_CASE)
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

        return when {
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
            else -> {
                // If sender isn't mapped, try to find "Card" or "Bank" in the text
                val cardMatch = Regex("""(?i)([A-Za-z0-9\s]+?)\s*(?:credit|debit)?\s*(?:card|bank)""").find(text)
                cardMatch?.groupValues?.getOrNull(1)?.trim() ?: "Unknown Bank"
            }
        }
    }
}