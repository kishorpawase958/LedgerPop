package app.ledgerpop.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String,   // "DEBIT" or "CREDIT"
    val emoji: String = "📁"
)