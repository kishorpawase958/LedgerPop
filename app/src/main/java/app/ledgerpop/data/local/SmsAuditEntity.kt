package app.ledgerpop.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Audit log of every SMS seen by the importer.
 * status: "IMPORTED" | "SKIPPED" | "PARSE_FAILED"
 * reportType: null | "FALSE_POSITIVE" (wrongly imported) | "FALSE_NEGATIVE" (wrongly skipped)
 */
@Entity(tableName = "sms_audit")
data class SmsAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val status: String,             // IMPORTED | SKIPPED | PARSE_FAILED
    val skipReason: String = "",    // Why it was skipped (if status = SKIPPED)
    val parsedAmount: Double = 0.0, // If imported
    val parsedType: String = "",    // DEBIT | CREDIT | "" if not imported
    val reportType: String = "",    // "" | FALSE_POSITIVE | FALSE_NEGATIVE
    val reportNote: String = "",    // User's optional note when reporting
    val hashKey: String             // Same hash as SmsTransactionEntity for linking
)