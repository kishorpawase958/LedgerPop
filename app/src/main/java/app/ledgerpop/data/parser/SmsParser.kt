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
        Regex("""(?:Inr Equiv Approx|Inr Equiv|Equiv\.? INR)\s*(?:INR|Rs\.?|₹|Rs:)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:INR|Rs\.?|₹|Rs:)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:debited by|debited from|debited for|credited by|credited to|credited with|credited for|payment of|spent on|spent|spend|paid from|for|of|withdrawn at|txn of|transaction of)\s*(?:INR|Rs\.?|₹|Rs:)?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    private val debitKeywords = listOf("debit","debited", "spent", "spend", "paid", "payment", "purchase", "trf to", "sent to", "withdrawal", "withdrawn", "txn of", "txn", "transaction")
    private val creditKeywords = listOf("credit","credited", "received", "refund", "added to", "deposited", "transfer from", "trf from")
    private val spamKeywords = listOf("kindly ignore if paid","otp is", "otp for", "is your otp", "one time password for", "Reminder","cooling period","generated", "request", "require", "allot", "disburse", "declined", "failed", "unsuccessful", "insufficient", "will be", "due")

    /**
     * Generalizes an SMS body to create a "structure" for smart learning.
     * Replaces numbers with # to ignore specific amounts, dates, or account numbers.
     */
    fun getStructure(body: String): String {
        return body.replace(Regex("""\d+"""), "#")
            .replace("\n", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase(Locale.getDefault())
    }

    fun parse(sender: String, body: String, ignoreSpamCheck: Boolean = false): ParsedSmsTransaction? {
        val text = body.replace("\n", " ").replace(Regex("""\s+"""), " ").trim()
        val lower = text.lowercase(Locale.getDefault())

        // Filter out spam or non-transactional messages
        if (!ignoreSpamCheck && spamKeywords.any { lower.contains(it) }) return null

        val amount = extractAmount(text) ?: if (ignoreSpamCheck) 0.0 else return null
        val type = detectType(text) ?: if (ignoreSpamCheck) "DEBIT" else return null

        // Extract Account Data
        val accountLast4 = extractAccountLast4(text)
        val bankName = detectBank(sender, text)

        // Format exactly as "Bank Name (XXXX)" - max 3 words for bank name
        val cleanBankName = bankName.split("\\s+".toRegex()).asSequence().take(3).joinToString(" ")
        val accountName = if (accountLast4.isNotBlank()) "$cleanBankName ($accountLast4)" else cleanBankName

        // Extract Merchant Name (Max 4 words, fallback to "Unknown" or "Account Credit")
        val merchant = extractMerchant(text)

        // Identify credit card bill payments (repayments) to exclude them from analytics (avoid double counting).
        val isRepayment = lower.contains("credit card") && (
            lower.contains("bill") ||
            lower.contains("towards") ||
            lower.contains("repayment") ||
            lower.contains("received") ||
            lower.contains("successful") ||
            (lower.contains("payment") && (lower.contains("for") || lower.contains("to")))
        )

        val cleanMerchant = if (merchant == "UPI funds transfer" || merchant.startsWith("Vehicle no.")) {
            merchant
        } else {
            merchant.split("\\s+".toRegex()).asSequence().take(4).joinToString(" ")
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

    private fun detectType(text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        val hasDebit = debitKeywords.any { lower.contains(it) }
        val hasCredit = creditKeywords.any { lower.contains(it) }

        return when {
            // High priority debit indicators
            lower.contains("debited") || lower.contains("spent") || lower.contains("withdrawn") || lower.contains("paid") || lower.contains("txn of") -> "DEBIT"

            // High priority credit indicators
            lower.contains("credited") || lower.contains("received") -> "CREDIT"

            hasDebit && !hasCredit -> "DEBIT"
            hasCredit && !hasDebit -> "CREDIT"
            else -> null
        }
    }

    private fun extractMerchant(text: String): String {
        // Stop words that indicate the end of a merchant name and the start of transaction details.
        val lookahead = """(?=\s+(?:on|via|with|using|ref|refno|from|upi|avl|bal|for|card|a/c|under|chk|your|is|at|info|info-|to|inr|rs|₹|if|to\s+dispute|to\s+raise|fvg|favoring|favouring)\b|(?<!\b(?:Mr|Ms|Dr|Mrs|Shri|Smt))\.\s*|$)"""
        val mChars = """A-Za-z0-9\s.&'/\-@*,"""

        val patterns = listOf(
            // Recipient indicator (Strong for Union Bank and others)
            Regex("""(?i)(?:fvg|favoring|favouring)[:\-]\s*([$mChars]{2,40}?)$lookahead"""),

            // SBI / Other banks "Transferred to"
            Regex("""(?i)Transferred to\s+([$mChars]{2,40}?)$lookahead"""),
            Regex("""(?i)(?:for|towards)\s+(?:payment\s+to|order\s+at|trip\s+at|purchase\s+at|subscription\s+at)\s+([$mChars]{2,40}?)$lookahead"""),
            // 7: spent at [Merchant]
            Regex("""(?i)spent\s+at\s+([$mChars]{2,40}?)$lookahead"""),

            // Handle "trf to [Merchant]" specifically as it often ends with "Refno"
            Regex("""(?i)trf to\s+([$mChars]{2,40}?)(?=\s+(?:Refno|Ref|on|at|for|via|$|\.))"""),

            // General pattern with expanded prefixes.
            Regex("""(?i)(?:at(?!\s+your\b)|to|info\s*vpa|paid\s*to|sent\s*to|towards|transfer\s+from|trf\s+from|for|from(?!\s+(?:your|a/c|acct|my|sbi|hdfc|icici|axis|kotak|pnb|boi|idfc|indus|canara|union|rbl|fed|idbi|citi|scb|hsbc|bank)\b)|by)\s+([$mChars]{2,40}?)$lookahead"""),
            Regex("""(?i)paid from your .+ to\s+([$mChars]{2,40}?)$lookahead"""),
            Regex("""(?i)trf to\s+([$mChars]{2,40}?)(?:\s+Refno|\.|$)"""),
            // Specific formats to handle them as a single block before lookahead stops them

            // 4: by [METHOD] from [Merchant]
            Regex("""(?i)by\s+[A-Za-z0-9]+\s+from\s+([$mChars]{2,40}?)$lookahead"""),
            // Union Bank "by [Merchant] ref no"
            Regex("""(?i)by\s+([$mChars]{2,40}?)\s+ref\s+no"""),
            // New pattern for "IST [Merchant]" format (avoiding time like 10:30)
            Regex("""(?i)IST\s+(?!\d{1,2}:\d{2})\s*([$mChars]{2,40}?)$lookahead"""),
            // Specifically handle Info- and Info: which may or may not have a space after the separator
            Regex("""(?i)(?:Info\s*-\s*|Info:\s*)\s*([$mChars]{2,40}?)$lookahead"""),


        )

        val candidates = mutableListOf<Pair<Int, String>>()

        for (pattern in patterns) {
            pattern.findAll(text).forEach { match ->
                val merchant = match.groupValues.getOrNull(1)?.trim()
                if (!merchant.isNullOrBlank() && merchant.length > 2) {
                    // Skip if "for" is preceded by "thanks" (common in non-transactional messages)
                    if (match.value.startsWith("for", ignoreCase = true) &&
                        text.take(match.range.first).lowercase().trimEnd().endsWith("thanks")) {
                        return@forEach
                    }

                    val lower = merchant.lowercase()
                    // Sanity check: Ensure we didn't accidentally capture bank jargon, currency, or time
                    val junkWords = setOf("payment", "transaction", "txn", "transfer", "spent", "paid", "debited", "credited", "dispute", "raise", "call")
                    val banks = setOf("sbi", "hdfc", "icici", "axis", "kotak", "pnb", "boi", "idfc", "indus", "canara", "union", "rbl", "fed", "idbi", "citi", "scb", "hsbc", "jupiter")

                    val isJunk = (lower.contains("card") && !lower.contains("credit card")) ||
                                 lower.startsWith("your") ||
                                 lower.contains("account") || lower.startsWith("rs") ||
                                 lower.startsWith("inr") || lower.contains("a/c") ||
                                 merchant.matches(Regex("""\d{1,2}:\d{2}""")) ||
                                 (lower.contains("bank") && !lower.contains("atm") && !lower.contains("cc")) ||
                                 junkWords.any { lower.startsWith(it) } ||
                                 banks.contains(lower) ||
                                 merchant.first().isDigit() ||
                                 lower.contains("raise an issue") ||
                                 lower.contains("if not u") ||
                                 lower.contains("other services") ||
                                 lower.contains("mob bk") ||
                                 lower.contains("net bk")

                    if (!isJunk) {
                        candidates.add(match.range.first to merchant)
                    }
                }
            }
        }

        // Return the one that appears first in the text
        if (candidates.isNotEmpty()) {
            return candidates.minByOrNull { it.first }?.second ?: "UPI funds transfer"
        }

        // Fallback for FASTag if no merchant was found in the text
        if (text.contains("FASTag", ignoreCase = true) && text.contains("vehicle no.", ignoreCase = true)) {
            val vMatch = Regex("""(?i)vehicle no\.\s*([A-Z0-9]{5,15})""").find(text)
            val vNo = vMatch?.groupValues?.getOrNull(1)
            if (vNo != null) return "Vehicle no. $vNo"
        }

        return "UPI funds transfer"
    }

    private fun extractAccountLast4(text: String): String {
        val patterns = listOf(
            Regex("""(?i)(?:a/c|ac|account|card|ending(?: in)?|no\.?|xx|\*\*)\s*[*xX\d-]*?(\d{4})(?!\d)"""),
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
            s.contains("UBI") || t.contains("UNION BANK") -> "Union Bank"
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

