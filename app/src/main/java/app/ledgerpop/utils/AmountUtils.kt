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
     * Always returns absolute value formatted as string.
     */
    fun formatAmount(amount: Double): String {
        return formatter.format(kotlin.math.abs(amount))
    }

    /**
     * A VisualTransformation for Indian Numbering System.
     * Expects a string containing only digits and at most one decimal point.
     */
    val indianCurrencyTransformation = VisualTransformation { text ->
        val originalText = text.text
        if (originalText.isEmpty()) {
            return@VisualTransformation TransformedText(AnnotatedString(""), OffsetMapping.Identity)
        }

        val parts = originalText.split(".")
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

        val out = formattedInt + decimalPart
        
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                
                // Calculate how many commas are added before the given offset in integerPart
                val intOffset = if (offset > integerPart.length) integerPart.length else offset
                var commasBefore = 0
                val reversedInt = integerPart.reversed()
                for (i in 0 until intOffset) {
                    val reversedIdx = integerPart.length - 1 - i
                    // Grouping: 3, 2, 2... from right
                    // i is index from left. 
                }
                
                // Simpler: iterate through the transformation and count non-comma chars
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
