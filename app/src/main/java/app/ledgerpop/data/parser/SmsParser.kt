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
    val includeInAnalytics: Boolean = true,
    val refNo: String = ""
)

object SmsParser {

    private val amountPatterns = listOf(
        // Prefer INR Equivalent for international transactions
        Regex("""(?:Inr Equiv Approx|Inr Equiv|Equiv\.? INR)\s*(?:INR|Rs\.?|₹|Rs:)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:INR|Rs\.?|₹|Rs:)\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:Dr.|Cr.|debited by|debited from|debited for|credited by|credited to|credited with|credited for|payment of|spent on|spent|spent at|spend|paid|paid from|for|of|withdrawn at|txn of|transaction of)\s*(?:INR|Rs\.?|₹|Rs:)?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    private val debitKeywords = listOf("dr.","debit", "debited", "spent", "spend", "paid", "payment", "purchase", "trf to", "sent to", "withdrawal", "withdrawn", "txn of", "txn", "transaction")
    private val creditKeywords = listOf("cr.","credit", "credited", "received", "refund", "added to", "deposited", "transfer from", "trf from")
    
    private val spamKeywords = listOf(
        "recharge of","dear client","pay with points","to be","eligibility","upto","instant","start now","instant credit","instant cash","instant payment","instant loan","sale","bonus","schedule","voucher","activating","activate","expire","offer","coupon","shortlisted","kindly ignore if paid","code:","otp:","otp is", "otp for", "is your otp", "one time password for","password",
        "reminder", "cooling period", "generated", "request", "require", "allot", "disburse", "disbursal","claim your","free higher limit","limit increased","upgraded",
        "declined", "failed", "unsuccessful", "insufficient", "will be", "due", "free cash","top offer","best deal","exclusive offer","shortlisted","upi pin","pin setup","account setup","lenskart account","continue to","offer end","use code","signed up","get approved","get free","claim now","welcome bonus","welcome offer","set pin","set code",
        // Mutual Funds / SIP / Demat / Corporate action blocks
        "sip transaction", "sip purchase", "units", "nav of", "folio", "processed at nav", "cdsl:", "nsdl:","processingfee","processing fee","avail your","apply now","card application","card applied","convert spends",
        // Marketing / Loan offers / Rewards blocks
        "confirm your tenure", "tenure", "redeem your", "edge reward", "pre-approved", "eligible for loan","get winning","prize pool","credit score","increase your",
        // Balance alerts / Summaries
        "view your last"
    )

    fun getStructure(body: String): String {
        return body.replace(Regex("""\d+"""), "#")
            .replace("\n", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase(Locale.getDefault())
    }

    fun buildHashKey(
        sender: String,
        timestamp: Long,
        amount: Double,
        type: String,
        refNo: String = ""
    ): String {
        if (refNo.isNotBlank()) {
            return "${sender}_${refNo}_${amount}_${type}"
        }
        // Use a 5-second window to handle slight discrepancies between receiver and content provider
        val rounded = (timestamp / 5000) * 5000
        return "${sender}_${rounded}_${amount}_${type}"
    }

    fun parse(sender: String, body: String, ignoreSpamCheck: Boolean = false): ParsedSmsTransaction? {
        val text = body.replace("\n", " ").replace(Regex("""\s+"""), " ").trim()
        val lower = text.lowercase(Locale.getDefault())

        // Ignore SMS from policy related senders
        if (sender.uppercase(Locale.getDefault()).contains("POLICY")) return null

        // Filter out spam or non-transactional messages
        if (!ignoreSpamCheck && spamKeywords.any { lower.contains(it) }) return null

        // Strictly determine transaction direction type first to validate intent
        val type = detectType(text) ?: if (ignoreSpamCheck) "DEBIT" else return null
        
        // Extract Amount
        val amount = extractAmount(text) ?: if (ignoreSpamCheck) 0.0 else return null

        val refNo = extractRefNo(text)

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
        val category = CategoryEngine.categorize(cleanMerchant, body, sender, type = type)

        return ParsedSmsTransaction(
            amount = amount,
            type = type,
            merchant = cleanMerchant,
            accountLast4 = accountLast4,
            bank = cleanBankName,
            accountName = accountName,
            category = category,
            includeInAnalytics = !isRepayment,
            refNo = refNo
        )
    }

    private fun extractRefNo(text: String): String {
        val patterns = listOf(
            Regex("""(?i)(?:ref\s*no|ref|utr|txn\s*id|id|trans\s*id)[:\-\s]+([A-Z0-9]{6,20})"""),
            Regex("""(?i)ref[:\-\s]*([A-Z0-9]{6,20})"""),
            Regex("""(?i)[-\s]([A-Z]{4}N\d{7,15})(?:\.|\s|$)"""),
            Regex("""(?i)No\.\s*([A-Z0-9]{8,20})"""),
            Regex("""(?i)(?:NEFT|IMPS|RTGS|UPI)/([A-Z0-9]{8,22})/(?:[A-Z0-9\s]{2,40})?"""),
            Regex("""(?i)(?:NEFT|IMPS|RTGS|UPI|UTR|REF)[:\-\s/]+([A-Z0-9]{10,22})""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            val ref = match?.groupValues?.getOrNull(1)
            if (!ref.isNullOrBlank()) return ref
        }
        return ""
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
        
        // CRITICAL FIX: Strip descriptive terms like "credit card" or "debit card"
        // so they don't accidentally match actionable financial keywords.
        val analyticalText = lower
            .replace("credit card", "")
            .replace("debit card", "")
            .replace("credit limit", "")

        // Explicit High-Priority Action Verbs
        if (analyticalText.contains("debited") || analyticalText.contains("spent") || 
            analyticalText.contains("withdrawn") || analyticalText.contains("paid from")
        ) return "DEBIT"
        
        if (analyticalText.contains("credited") || analyticalText.contains("received")) return "CREDIT"

        val hasDebit = debitKeywords.any { analyticalText.contains(it) }
        val hasCredit = creditKeywords.any { analyticalText.contains(it) }

        return when {
            hasDebit && !hasCredit -> "DEBIT"
            hasCredit && !hasDebit -> "CREDIT"
            else -> null
        }
    }

    private val lookahead = """(?=\s+(?:on|via|with|using|ref|refno|from|upi|avl|bal|for any queries|more info|any query|card|a/c|under|chk|your|is|at|info|info-|to|inr|rs|₹|if|not|not\s+u|not\s+you|if\s+not|to\s+dispute|to\s+raise|fvg|favoring|favouring|sms|block|call|report)\b|(?<!\b(?:Mr|Ms|Dr|Mrs|Shri|Smt))[.?!;:]\s*|$)"""
    private val mChars = """A-Za-z0-9\s.&'/\-@*,"""

    private val merchantPatterns = listOf(
        // 1. High-Priority: Extract trailing payee/merchant from raw slash routing strings (e.g. UPI/P2M/Id/Merchant)
        Regex("""(?i)(?:UPI|NEFT|IMPS)/(?:P2M|P2P|P2A)/\d+/([$mChars]{2,100}?)$lookahead"""),

        // 2. High-Priority: Extract clean merchant segments trapped inside semicolon updates (e.g. ; ZERODHA... credited)
        Regex("""(?i);\s*([A-Z0-9\s.&'\-]{2,100}?)\s+credited"""),

        // 3. High-Priority: Intercept institutional banking updates to prevent slash punctuation lookahead drops
        Regex("""(?i)Info:\s*(?:NEFT|IMPS|RTGS|UPI)/([$mChars]{2,100}?)$lookahead"""),

        // 4. High-Priority: Extract from common bank transfer formats via/NEFT/REF/Merchant
        Regex("""(?i)(?:via\s+)?(?:NEFT|IMPS|RTGS|UPI)/(?:[^/]+/)?([$mChars]{2,100}?)$lookahead"""),
        
        // --- Legacy patterns continue standard extraction without regressions ---
        Regex("""(?i)(?:fvg|favoring|favouring)[:\-]\s*([$mChars]{2,100}?)$lookahead"""),
        Regex("""(?i)Transferred to\s+([$mChars]{2,100}?)$lookahead"""),
        Regex("""(?i)(?:for|towards)\s+(?:payment\s+to|order\s+at|trip\s+at|purchase\s+at|subscription\s+at)\s+([$mChars]{2,100}?)$lookahead"""),
        Regex("""(?i)spent\s+at\s+([$mChars]{2,100}?)$lookahead"""),
        Regex("""(?i)trf to\s+([$mChars]{2,100}?)(?=\s+(?:Refno|Ref|on|at|for|via|$|\.))"""),
        Regex("""(?i)(?:at(?!\s+your\b)|to|via|in|info\s*vpa|paid\s*to|sent\s*to|towards|on(?!\s+(?:[0-9\-:]+)\b)|transfer\s+from|trf\s+from|for|from(?!\s+(?:your|a/c|acct|my|sbi|hdfc|icici|axis|kotak|pnb|boi|idfc|indus|canara|union|rbl|fed|idbi|citi|scb|hsbc|bank)\b)|by)\s+([$mChars]{2,100}?)$lookahead"""),
        Regex("""(?i)paid from your .+ to\s+([$mChars]{2,100}?)$lookahead"""),
        Regex("""(?i)trf to\s+([$mChars]{2,100}?)(?:\s+Refno|\.|$)"""),
        Regex("""(?i)by\s+[A-Za-z0-9]+\s+from\s+([$mChars]{2,100}?)$lookahead"""),
        Regex("""(?i)by\s+([$mChars]{2,100}?)\s+ref\s+no"""),
        Regex("""(?i)IST\s+(?!\d{1,2}:\d{2})\s*([$mChars]{2,100}?)$lookahead"""),
        Regex("""(?i)(?:Info\s*-\s*|Info:\s*)\s*([$mChars]{2,100}?)$lookahead""")
    )

    private val leadingDigitsRegex = Regex("""^\d+\s+""")
    private val timeRegex = Regex("""\d{1,2}:\d{2}""")
    private val dateRegex = Regex("""\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|[0-9]{1,2})[-/]\d{2,4}""", RegexOption.IGNORE_CASE)
    private val bankNames = setOf("sbi", "hdfc", "icici", "axis", "kotak", "pnb", "boi", "idfc", "indus", "canara", "union", "rbl", "fed", "idbi", "citi", "scb", "hsbc", "jupiter")
    private val junkWords = setOf("payment", "transaction", "txn", "transfer", "spent", "paid", "debited", "credited", "dispute", "raise", "call", "using", "thanks", "clearing", "subject", "cheque")

    private fun extractMerchant(text: String): String {
        val candidates = mutableListOf<Pair<Int, String>>()

        for (pattern in merchantPatterns) {
            val matches = pattern.findAll(text)
            for (match in matches) {
                var merchant = match.groupValues.getOrNull(1)?.trim()?.trimEnd(';', '.', ':', '-') ?: ""
                if (merchant.length <= 2) continue

                // Clean up technical prefixes like NEFT Cr-IFSC- or IMPS-
                if (merchant.startsWith("NEFT", true) || merchant.startsWith("IMPS", true) || 
                    merchant.startsWith("RTGS", true) || merchant.startsWith("UPI", true)) {
                    val parts = merchant.split("-")
                    merchant = if (parts.size > 2 && (parts[0].contains("Cr", true) || parts[0].contains("Dr", true))) {
                        parts.drop(2).joinToString(" ").trim()
                    } else if (parts.size > 1) {
                        parts.drop(1).joinToString(" ").trim()
                    } else {
                        merchant
                    }
                    merchant = merchant.replace(leadingDigitsRegex, "").trim()
                }

                if (match.value.startsWith("for", ignoreCase = true)) {
                    val before = text.take(match.range.first).lowercase().trimEnd().trimEnd(',', '.', '!', ';').trimEnd()
                    if (before.endsWith("thanks") || before.endsWith("thank you") || before.endsWith("thnx")) continue
                }

                val lower = merchant.lowercase()
                
                val isJunk = (lower.contains("card") && !lower.contains("credit card")) ||
                             lower.startsWith("your") ||
                             lower.contains("account") || lower.startsWith("rs") ||
                             lower.startsWith("inr") || lower.contains("a/c") ||
                             merchant.matches(timeRegex) ||
                             merchant.contains(dateRegex) ||
                             (lower.contains("bank") && !lower.contains("atm") && !lower.contains("cc")) ||
                             junkWords.any { lower.startsWith(it) || lower == it } ||
                             bankNames.contains(lower) ||
                             merchant.first().isDigit() ||
                             lower.contains("raise an issue") ||
                             lower.contains("if not u") ||
                             lower.contains("if not you") ||
                             lower.contains("other services") ||
                             lower.contains("more info") ||
                             lower.contains("any query") ||
                             lower.contains("any queries") ||
                             lower.contains("further details") ||
                             lower.contains("help") ||
                             lower.contains("mob bk") ||
                             lower.contains("net bk") ||
                             lower.contains("any help") ||
                             lower.contains("you,") ||
                             lower.contains("you") ||
                             lower.contains("clearing") ||
                             lower.contains("subject to") ||
                             lower.contains("view your") ||
                             lower.contains("any assistance") ||
                             lower.contains("whatsapp bal") ||
                             lower.contains("block") ||
                             lower.contains("confirm") ||
                             lower.contains("check") ||
                             lower.contains("upgrade") ||
                             lower.contains("update") ||
                             lower.contains("assistance")


                if (!isJunk) {
                    candidates.add(match.range.first to merchant)
                }
            }
            // Optimization: If we find a high-priority merchant (first 4 patterns), we stop searching legacy patterns
            if (candidates.isNotEmpty() && merchantPatterns.indexOf(pattern) < 4) break
        }

        if (candidates.isNotEmpty()) {
            return candidates.minByOrNull { it.first }?.second ?: "UPI funds transfer"
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

        val paidFromMatch = Regex("""(?i)paid from your\s+([A-Za-z0-9\s]+?)\s+to""").find(text)
        val paidFromName = paidFromMatch?.groupValues?.getOrNull(1)?.trim()
        if (!paidFromName.isNullOrBlank()) return paidFromName

        val cardMatch = Regex("""(?i)(?:on your|done on|from your|at)\s+([A-Za-z0-9\s]+?)\s*(?:credit|debit)?\s*(?:card|bank)""").find(text)
        return cardMatch?.groupValues?.getOrNull(1)?.trim() ?: "Unknown Bank"
    }
}
