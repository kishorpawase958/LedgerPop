package app.ledgerpop.data.csv

import android.content.Context
import android.net.Uri
import app.ledgerpop.data.local.SmsTransactionEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.Calendar

object CsvHelper {

    private const val HEADER = "DATE,TIME,MERCHANT,AMOUNT,DR/CR,ACCOUNT,EXPENSE,INCOME,CATEGORY,NOTE"

    fun exportTransactions(context: Context, uri: Uri, transactions: List<SmsTransactionEntity>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write("$HEADER\n")

                transactions.forEach { txn ->
                    val date = dateFormat.format(Date(txn.transactionTime))
                    val time = timeFormat.format(Date(txn.transactionTime))
                    val drCr = if (txn.type == "DEBIT") "DR" else "CR"

                    val expenseFlag = if (txn.type == "DEBIT") {
                        if (txn.isBillable) "YES" else "NO"
                    } else "-"

                    val incomeFlag = if (txn.type == "CREDIT") {
                        if (txn.isBillable) "YES" else "NO"
                    } else "-"

                    // Escape commas in strings by wrapping in quotes
                    val merchant = "\"${txn.merchant.replace("\"", "\"\"")}\""
                    val account = "\"${txn.accountHint.replace("\"", "\"\"")}\""
                    val category = "\"${txn.category.replace("\"", "\"\"")}\""

                    // Fixed: Using 'note' instead of 'notes'
                    val noteStr = txn.note ?: ""
                    val note = "\"${noteStr.replace("\"", "\"\"")}\""

                    writer.write("$date,$time,$merchant,${txn.amount},$drCr,$account,$expenseFlag,$incomeFlag,$category,$note\n")
                }
            }
        }
    }

    fun importTransactions(context: Context, uri: Uri): List<SmsTransactionEntity> {
        val transactions = mutableListOf<SmsTransactionEntity>()
        
        val datePatterns = listOf("yyyy-MM-dd", "yyyy-M-d", "dd-MM-yyyy", "d-M-yyyy", "dd/MM/yyyy", "MM/dd/yyyy")
        val timePatterns = listOf("HH:mm:ss", "H:mm:ss", "h:mm:ss a", "h:mm a", "H:mm", "HH:mm")
        
        val dateFormatters = datePatterns.map { SimpleDateFormat(it, Locale.ENGLISH) }
        val timeFormatters = timePatterns.map { SimpleDateFormat(it, Locale.ENGLISH) }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val content = inputStream.bufferedReader().readText()
            if (content.isBlank()) return emptyList()

            // Detect delimiter
            val delimiter = if (content.take(2000).count { it == '\t' } > content.take(2000).count { it == ',' }) '\t' else ','
            
            val rows = parseCsvContent(content, delimiter)
            if (rows.isEmpty()) return emptyList()

            val startIdx = if (rows[0].getOrNull(0)?.contains("DATE", ignoreCase = true) == true) 1 else 0

            for (i in startIdx until rows.size) {
                val tokens = rows[i]
                if (tokens.size >= 7) {
                    try {
                        val dateStr = tokens[0]
                        val timeStr = tokens[1]
                        
                        var timestamp: Long? = null
                        for (df in dateFormatters) {
                            try {
                                val date = df.parse(dateStr)
                                for (tf in timeFormatters) {
                                    try {
                                        val time = tf.parse(timeStr.uppercase())
                                        if (date != null && time != null) {
                                            val cal = Calendar.getInstance()
                                            cal.time = date
                                            val timeCal = Calendar.getInstance()
                                            timeCal.time = time
                                            cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                                            cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                                            cal.set(Calendar.SECOND, timeCal.get(Calendar.SECOND))
                                            timestamp = cal.timeInMillis
                                            break
                                        }
                                    } catch (_: Exception) {}
                                }
                                if (timestamp != null) break
                            } catch (_: Exception) {}
                        }

                        val type = if (tokens[4].equals("DR", ignoreCase = true)) "DEBIT" else "CREDIT"
                        val isBillable = if (type == "DEBIT") {
                            tokens.getOrNull(6)?.equals("YES", ignoreCase = true) == true
                        } else {
                            tokens.getOrNull(7)?.equals("YES", ignoreCase = true) == true
                        }

                        val entity = SmsTransactionEntity(
                            sender = "IMPORTED_CSV",
                            body = "Imported from CSV",
                            amount = tokens[3].toDoubleOrNull() ?: 0.0,
                            type = type,
                            merchant = tokens[2],
                            accountHint = tokens[5],
                            bank = tokens[5],
                            category = tokens.getOrNull(8) ?: "Other",
                            note = tokens.getOrNull(9) ?: "",
                            isBillable = isBillable,
                            transactionTime = timestamp ?: System.currentTimeMillis(),
                            hashKey = "CSV_${timestamp ?: System.currentTimeMillis()}_${tokens[3]}_${tokens[2]}_${tokens[4]}_${System.nanoTime()}_$i"
                        )
                        transactions.add(entity)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return transactions
    }

    private fun parseCsvContent(content: String, delimiter: Char): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var currentLine = mutableListOf<String>()
        var currentToken = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < content.length) {
            val c = content[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < content.length && content[i + 1] == '\"') {
                        currentToken.append('\"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    currentLine.add(currentToken.toString().trim())
                    currentToken = StringBuilder()
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && i + 1 < content.length && content[i + 1] == '\n') {
                        i++
                    }
                    currentLine.add(currentToken.toString().trim())
                    if (currentLine.any { it.isNotEmpty() }) {
                        result.add(currentLine)
                    }
                    currentLine = mutableListOf()
                    currentToken = StringBuilder()
                }
                else -> {
                    currentToken.append(c)
                }
            }
            i++
        }
        if (currentToken.isNotEmpty() || currentLine.isNotEmpty()) {
            currentLine.add(currentToken.toString().trim())
            if (currentLine.any { it.isNotEmpty() }) {
                result.add(currentLine)
            }
        }
        return result
    }

    // Handles commas inside quoted strings
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var currentToken = StringBuilder()
        var inQuotes = false

        for (char in line) {
            if (char == '\"') {
                inQuotes = !inQuotes
            } else if (char == ',' && !inQuotes) {
                result.add(currentToken.toString())
                currentToken = StringBuilder()
            } else {
                currentToken.append(char)
            }
        }
        result.add(currentToken.toString())
        return result.map { it.trim() }
    }
}