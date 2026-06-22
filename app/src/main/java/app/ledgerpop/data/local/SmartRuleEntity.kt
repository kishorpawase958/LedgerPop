package app.ledgerpop.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores user-defined rules learned from corrections.
 * ruleType: "ALWAYS_IMPORT" | "ALWAYS_SKIP"
 */
@Entity(tableName = "smart_rules")
data class SmartRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String,
    val bodyStructure: String, // The generalized body (e.g., digits replaced with #)
    val ruleType: String,      // ALWAYS_IMPORT | ALWAYS_SKIP
    val timestamp: Long = System.currentTimeMillis()
)
