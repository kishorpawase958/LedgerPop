package app.ledgerpop.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms_transactions",
    indices = [Index(value = ["hashKey"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = SmsTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedTransactionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class SmsTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String,
    val body: String,
    val amount: Double,
    val originalAmount: Double? = null, // Store original if linking changes the 'amount'
    val type: String,           // DEBIT or CREDIT
    val merchant: String,
    val category: String,
    val accountHint: String = "",   // e.g. "XX1234" parsed from SMS
    val isBillable: Boolean = true, // false = greyed out, excluded from reports
    val transactionTime: Long,
    val hashKey: String,
    val bank: String,
    val note: String = "",           // ADD THIS LINE
    val linkedTransactionId: Int? = null // For CREDIT, points to DEBIT. For DEBIT, usually null.
)