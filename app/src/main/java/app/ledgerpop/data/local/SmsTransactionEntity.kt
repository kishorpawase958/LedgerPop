package app.ledgerpop.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_transactions")
data class SmsTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String,
    val body: String,
    val amount: Double,
    val type: String,           // DEBIT or CREDIT
    val merchant: String,
    val category: String,
    val accountHint: String = "",   // e.g. "XX1234" parsed from SMS
    val isBillable: Boolean = true, // false = greyed out, excluded from reports
    val transactionTime: Long,
    val hashKey: String,
    val bank: String,
    val note: String = ""           // ADD THIS LINE
)