package app.ledgerpop.data.category

// Data class to support custom categories added by the user
data class CustomCategory(
    val name: String,
    val emoji: String,
    val type: String = "DEBIT", // "DEBIT" or "CREDIT"
    val keywords: List<String> = emptyList()
)

object CategoryEngine {

    // Debit Categories
    const val FOOD = "Food & Dining"
    const val GROCERIES = "Groceries"
    const val SHOPPING = "Shopping"
    const val TRAVEL = "Travel"
    const val FUEL = "Fuel"
    const val BILLS = "Bills & Utilities"
    const val HEALTH = "Health"
    const val INSURANCE = "Insurance"
    const val INVESTMENTS = "Investments"
    const val ENTERTAINMENT = "Entertainment"
    const val EMI = "EMI / Loans"

    // Credit & Transfer Categories
    const val SALARY = "Salary"
    const val INTEREST = "Interest"
    const val DIVIDEND = "Dividend"
    const val REFUND = "Refund"
    const val TRANSFER = "Bank Transfer" // Used for both (Self transfer, etc)

    // Fallback
    const val OTHER = "Other"

    private val RULES: List<Pair<String, List<String>>> = listOf(
        // ── DEBIT RULES ──
        FOOD to listOf(
            "swiggy", "zomato", "foodpanda", "dominos", "pizza", "mcdonald",
            "kfc", "subway", "cafe", "restaurant", "hotel", "dhaba", "bistro",
            "catere", "canteen", "bakery", "juice", "chai", "tea", "food",
            "eatery", "dine", "lunch", "dinner", "breakfast", "biryani",
            "fassos", "box8", "freshmenu", "eatsure", "barbeque", "burger king",
            "starbucks", "hungerbox", "eat"
        ),
        GROCERIES to listOf(
            "zepto", "blinkit", "grofers", "bigbasket", "dunzo", "instamart",
            "dmart", "reliance smart", "supermarket", "grocery", "star bazaar",
            "nature's basket", "more retail"
        ),
        SHOPPING to listOf(
            "flipkart", "amazon", "myntra", "ajio", "meesho", "nykaa",
            "snapdeal", "shopsy", "tatacliq", "reliancedigital", "croma",
            "ekart", "ekartl", "flpkrt", "maxfsn", "shopping", "mall",
            "retail", "store", "market", "bazaar", "snitch", "bewakoof",
            "westside", "pantaloons", "lifestyle", "shoppers", "zara",
            "h&m", "uniqlo", "decathlon"
        ),
        TRAVEL to listOf(
            "uber", "ola", "rapido", "pmpml", "irctc", "railway", "train",
            "metro", "bus", "auto", "cab", "taxi", "redbus", "abhibus",
            "makemytrip", "goibibo", "yatra", "flight", "airasia",
            "indigo", "spicejet", "vistara", "airindia", "airline", "namma metro"
        ),
        FUEL to listOf(
            "petrol", "diesel", "fuel", "hp ", "bpcl", "iocl", "indianoil",
            "shell", "hpcl", "fastag", "toll", "parking", "cng", "nayara", "jio-bp"
        ),
        BILLS to listOf(
            "electricity", "msedcl", "bescom", "tpddl", "bill", "recharge",
            "airtel", "jio", "vodafone", "vi ", "bsnl", "broadband",
            "internet", "wifi", "trunet", "hlpnet", "postpaid", "prepaid",
            "payzap", "paytm", "phonepe bill", "gas", "lpg", "water",
            "municipal", "property tax", "subscription", "netflix", "hotstar",
            "amazon prime"
        ),
        HEALTH to listOf(
            "pharma", "pharmacy", "medical", "hospital", "clinic", "doctor",
            "apollo", "medplus", "netmeds", "1mg", "tata 1mg", "healthkart",
            "lab", "diagnostic", "pathology", "chemist", "medicine",
            "health", "wellness", "ayurvedic", "dental", "eye care"
        ),
        INSURANCE to listOf(
            "insurance", "premium", "lic ", "policy", "bajaj allianz",
            "hdfc ergo", "icici lombard", "star health", "care health",
            "sbi life", "max life"
        ),
        INVESTMENTS to listOf(
            "zerodha", "groww", "upstox", "angel", "icicidirect", "hdfc sec",
            "kotak sec", "sbi mf", "sbimfm", "hsbcmf", "axismf", "qntamc",
            "sbimfd", "mutual fund", "mf ", "nse", "bse", "nsesms",
            "bseltd", "cdslev", "cdsltx", "demat", "equity", "sip",
            "portfolio", "ppfamf", "icicimf", "franklinmf", "dspimf",
            "mirae", "tata mf", "stock", "shares", "trading"
        ),
        ENTERTAINMENT to listOf(
            "pvr", "pvrvip", "inox", "inoxmo", "cinepolis", "cinpls",
            "bookmyshow", "movie", "cinema", "theatre", "concert",
            "netflix", "hotstar", "zee5", "sonyliv", "jiocinema",
            "spotify", "gaana", "wynk", "youtube premium", "gaming",
            "steam", "gamengm", "playstation", "xbox"
        ),
        EMI to listOf(
            "emi", "loan", "loanemi", "home loan", "car loan", "personal loan",
            "installment", "equated", "bajaj", "hdfc loan", "icici loan",
            "finbku", "nuloan", "cashfi", "moneyview", "cashe", "lazypay",
            "simpl", "slice", "credpay", "cred club", "onecard emi"
        ),

        // ── CREDIT & TRANSFER RULES ──
        SALARY to listOf(
            "salary", "sal ", "payroll", "credited sal", "remuneration"
        ),
        INTEREST to listOf(
            "interest", "int pd", "int. pd", "savings int", "fd int", "dep int"
        ),
        DIVIDEND to listOf(
            "dividend", "div ", "divnd", "divdend"
        ),
        REFUND to listOf(
            "refund", "reversal", "reversed", "cashback", "cash back"
        ),
        TRANSFER to listOf(
            "trf to", "upi", "neft", "rtgs", "imps", "transfer",
            "sent to", "paid to", "send money", "received from", "trf from"
        )
    )

    fun categorize(merchant: String, body: String, sender: String, customCategories: List<CustomCategory> = emptyList()): String {
        val searchText = "$merchant $body $sender".lowercase()

        for (custom in customCategories) {
            if (custom.keywords.any { searchText.contains(it.lowercase()) }) {
                return custom.name
            }
        }

        for ((category, keywords) in RULES) {
            if (keywords.any { searchText.contains(it) }) {
                return category
            }
        }
        return OTHER
    }

    fun emoji(category: String, customCategories: List<app.ledgerpop.data.local.CustomCategoryEntity> = emptyList()): String {
        customCategories.find { it.name.equals(category, ignoreCase = true) }?.let { return it.emoji }

        return when (normalize(category)) {
            FOOD -> "🍔"
            GROCERIES -> "🥦"
            SHOPPING -> "🛒"
            TRAVEL -> "✈️"
            FUEL -> "⛽"
            BILLS -> "🧾"
            HEALTH -> "💊"
            INSURANCE -> "🛡️"
            INVESTMENTS -> "💸"
            ENTERTAINMENT -> "🎬"
            EMI -> "💳"
            SALARY -> "💰"
            INTEREST -> "📈"
            DIVIDEND -> "📊"
            REFUND -> "🔄"
            TRANSFER -> "🏦"
            OTHER -> "🧹"
            else -> "⁉️"
        }
    }

    fun normalize(category: String): String {
        val cat = category.uppercase().trim()
        return when {
            cat.contains("FOOD") || cat.contains("DINING") || cat.contains("RESTAURANT") || cat.contains("EAT") -> FOOD
            cat.contains("GROCERY") || cat.contains("GROCERIES") || cat.contains("ZEPTO") || cat.contains("BLINKIT") -> GROCERIES
            cat.contains("SHOPPING") || cat.contains("AMAZON") || cat.contains("FLIPKART") -> SHOPPING
            cat.contains("TRAVEL") || cat.contains("UBER") || cat.contains("OLA") || cat.contains("TRANSPORT") -> TRAVEL
            cat.contains("FUEL") || cat.contains("PETROL") || cat.contains("DIESEL") -> FUEL
            cat.contains("BILL") || cat.contains("UTILITY") || cat.contains("RECHARGE") -> BILLS
            cat.contains("HEALTH") || cat.contains("MEDICAL") || cat.contains("HOSPITAL") -> HEALTH
            cat.contains("INSURANCE") -> INSURANCE
            cat.contains("INVESTMENT") || cat.contains("STOCK") || cat.contains("MUTUAL FUND") || cat == "CREDIT" -> INVESTMENTS
            cat.contains("MOVIE") || cat.contains("ENTERTAINMENT") -> ENTERTAINMENT
            cat.contains("EMI") || cat.contains("LOAN") -> EMI
            cat.contains("SALARY") -> SALARY
            cat.contains("INTEREST") -> INTEREST
            cat.contains("DIVIDEND") -> DIVIDEND
            cat.contains("REFUND") -> REFUND
            cat.contains("TRANSFER") -> TRANSFER
            (cat == "OTHER" || cat.isBlank()) -> OTHER
            else -> category
        }
    }

    fun debitCategories(customCategories: List<CustomCategory> = emptyList()): List<String> {
        val defaults = listOf(
            FOOD, GROCERIES, SHOPPING, TRAVEL, FUEL, BILLS, HEALTH, INSURANCE,
            INVESTMENTS, ENTERTAINMENT, EMI, TRANSFER, OTHER
        )
        return defaults + customCategories.filter { it.type == "DEBIT" }.map { it.name }
    }

    fun creditCategories(customCategories: List<CustomCategory> = emptyList()): List<String> {
        val defaults = listOf(
            SALARY, INTEREST, DIVIDEND, REFUND, INVESTMENTS, TRANSFER, OTHER
        )
        return defaults + customCategories.filter { it.type == "CREDIT" }.map { it.name }
    }
}