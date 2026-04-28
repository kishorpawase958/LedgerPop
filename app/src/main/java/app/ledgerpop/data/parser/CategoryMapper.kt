package app.ledgerpop.data.parser

object CategoryMapper {

    fun resolve(merchant: String, body: String, type: String): String {
        val m = merchant.lowercase()
        val b = body.lowercase()

        if (type == "CREDIT") {
            return when {
                b.contains("salary") || b.contains("sal cr") -> "Salary"
                b.contains("refund") || b.contains("cashback") -> "Refund"
                b.contains("interest") -> "Interest"
                else -> "Income"
            }
        }

        if (anyOf(m, b, "swiggy", "zomato", "domino", "pizza", "mcdonald", "kfc",
                "burger", "dunkin", "cafe", "restaurant", "biryani", "dhaba", "eat"))
            return "Food & Dining"

        if (anyOf(m, b, "bigbasket", "zepto", "blinkit", "grocer", "supermarket",
                "reliance fresh", "dmart", "more store", "vegetables", "fruits"))
            return "Groceries"

        if (anyOf(m, b, "uber", "ola", "rapido", "metro", "irctc", "railway",
                "redbus", "bus", "auto", "cab", "fuel", "petrol", "hp petrol",
                "indian oil", "bharat petroleum"))
            return "Transport"

        if (anyOf(m, b, "amazon", "flipkart", "myntra", "meesho", "ajio",
                "nykaa", "snapdeal", "tatacliq", "reliance digital"))
            return "Shopping"

        if (anyOf(m, b, "electricity", "msedcl", "bescom", "tpddl", "water bill",
                "gas", "mahanagar gas", "indane", "hp gas", "broadband", "airtel",
                "jio", "bsnl", "vodafone", "vi ", "recharge", "postpaid", "mobile bill"))
            return "Bills & Utilities"

        if (anyOf(m, b, "pharmacy", "medplus", "apollo", "1mg", "netmeds",
                "hospital", "clinic", "doctor", "medical", "health"))
            return "Healthcare"

        if (anyOf(m, b, "netflix", "hotstar", "prime video", "spotify",
                "youtube premium", "zee5", "sonyliv", "bookmyshow", "inox", "pvr"))
            return "Entertainment"

        if (anyOf(m, b, "school", "college", "university", "fees", "tuition",
                "udemy", "coursera", "byju", "unacademy"))
            return "Education"

        if (anyOf(m, b, "atm", "cash withdrawal", "cash wthdrl"))
            return "ATM Withdrawal"

        if (anyOf(m, b, "insurance", "emi", "loan", "credit card", "lic ",
                "hdfc life", "sbi life", "bajaj allianz", "mutual fund", "sip"))
            return "Finance & Insurance"

        if (anyOf(m, b, "upi", "neft", "imps", "rtgs", "transfer", "sent to", "paid to"))
            return "Transfer"

        return "Other"
    }

    private fun anyOf(merchant: String, body: String, vararg keywords: String): Boolean =
        keywords.any { kw -> merchant.contains(kw) || body.contains(kw) }
}