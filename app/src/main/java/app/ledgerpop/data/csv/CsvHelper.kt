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

object CsvHelper {

    private const val HEADER = "DATE,TIME,MERCHANT,AMOUNT,DR/CR,ACCOUNT,EXPENSE,INCOME,CATEGORY,NOTE"

    fun exportTransactions(context: Context, uri: Uri, transactions: List<SmsTransactionEntity>) {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write("$HEADER\n")

                transactions.forEach { txn ->
                    val date = dateFormat.format(Date(txn.transactionTime))
                    val time = timeFormat.format(Date(txn.transactionTime))
                    val drCr = if (txn.type == "DEBIT") "DR" else "CR"
                    val expenseFlag = if (txn.type == "DEBIT" && txn.isBillable) "YES" else "NO"
                    val incomeFlag = if (txn.type == "CREDIT" && txn.isBillable) "YES" else "NO"

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
        val dateTimeFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val headerLine = reader.readLine() // Skip header
                if (headerLine == null || !headerLine.contains("DATE")) return emptyList()

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val tokens = parseCsvLine(line!!)
                    if (tokens.size >= 10) {
                        try {
                            val dateStr = tokens[0]
                            val timeStr = tokens[1]
                            val timestamp = dateTimeFormat.parse("$dateStr $timeStr")?.time ?: System.currentTimeMillis()

                            val type = if (tokens[4].equals("DR", ignoreCase = true)) "DEBIT" else "CREDIT"
                            val isBillable = if (type == "DEBIT") {
                                tokens[6].equals("YES", ignoreCase = true)
                            } else {
                                tokens[7].equals("YES", ignoreCase = true)
                            }

                            // Removed 'id' so Room auto-generates the Integer ID
                            val entity = SmsTransactionEntity(
                                sender = "IMPORTED_CSV",
                                body = "Imported from CSV",
                                amount = tokens[3].toDoubleOrNull() ?: 0.0,
                                type = type,
                                merchant = tokens[2],
                                accountHint = tokens[5],
                                bank = tokens[5],
                                category = tokens[8],
                                note = tokens[9], // Fixed: Using 'note' instead of 'notes'
                                isBillable = isBillable,
                                transactionTime = timestamp,
                                hashKey = "CSV_${System.currentTimeMillis()}_${UUID.randomUUID()}"
                            )
                            transactions.add(entity)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        return transactions
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