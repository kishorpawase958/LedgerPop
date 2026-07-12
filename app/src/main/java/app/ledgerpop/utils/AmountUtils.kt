package app.ledgerpop.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.NumberFormat
import java.util.Locale

object AmountUtils {
    private val indianLocale = Locale("en", "IN")
    private val formatter = NumberFormat.getNumberInstance(indianLocale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }

    /**
     * Formats an amount using Indian Numbering System (e.g., 10,00,000.00).
     * Returns the absolute value as a string.
     */
    fun formatAmount(amount: Double): String {
        return formatter.format(kotlin.math.abs(amount))
    }

    /**
     * Formats with currency symbol, placing negative sign before the symbol.
     */
    fun formatWithCurrency(amount: Double): String {
        val formatted = formatter.format(kotlin.math.abs(amount))
        return if (amount < 0) "-₹$formatted" else "₹$formatted"
    }

    /**
     * Formats amount in a compact way (e.g., 1.2k, 1.5L)
     */
    fun formatCompact(amount: Double): String {
        val absAmount = kotlin.math.abs(amount)
        val sign = if (amount < 0) "-" else ""
        
        val formatted = when {
            absAmount >= 10_000_000 -> "%.1fCr".format(absAmount / 10_000_000).replace(".0", "")
            absAmount >= 100_000 -> "%.1fL".format(absAmount / 100_000).replace(".0", "")
            absAmount >= 1000 -> "%.1fk".format(absAmount / 1000).replace(".0", "")
            else -> formatter.format(absAmount)
        }
        
        return "${sign}₹$formatted"
    }

    /**
     * Returns a raw string for editing, with up to 2 decimal places and no commas.
     */
    fun formatRaw(amount: Double): String {
        val res = "%.2f".format(Locale.US, amount)
        return if (res.contains(".")) {
            res.trimEnd('0').trimEnd('.')
        } else res
    }

    /**
     * A VisualTransformation for Indian Numbering System.
     * Expects a string containing digits, optional leading minus, and at most one decimal point.
     */
    val indianCurrencyTransformation = VisualTransformation { text ->
        val originalText = text.text
        if (originalText.isEmpty()) {
            return@VisualTransformation TransformedText(AnnotatedString(""), OffsetMapping.Identity)
        }

        val isNegative = originalText.startsWith("-")
        val cleanText = if (isNegative) originalText.substring(1) else originalText

        val parts = cleanText.split(".")
        val integerPart = parts[0]
        val decimalPart = if (parts.size > 1) "." + parts[1] else ""

        val formattedInt = if (integerPart.isEmpty()) "" else {
            val reversed = integerPart.reversed()
            val result = StringBuilder()
            for (i in reversed.indices) {
                if (i == 3 || (i > 3 && (i - 3) % 2 == 0)) {
                    result.append(",")
                }
                result.append(reversed[i])
            }
            result.reverse().toString()
        }

        val out = (if (isNegative) "-" else "") + formattedInt + decimalPart
        
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                
                var transformedOffset = 0
                var originalCharCount = 0
                while (transformedOffset < out.length && originalCharCount < offset) {
                    if (out[transformedOffset] != ',') {
                        originalCharCount++
                    }
                    transformedOffset++
                }
                return transformedOffset
            }

            override fun transformedToOriginal(offset: Int): Int {
                var originalOffset = 0
                var transformedCharCount = 0
                while (transformedCharCount < offset && transformedCharCount < out.length) {
                    if (out[transformedCharCount] != ',') {
                        originalOffset++
                    }
                    transformedCharCount++
                }
                return originalOffset
            }
        }

        TransformedText(AnnotatedString(out), offsetMapping)
    }
}
