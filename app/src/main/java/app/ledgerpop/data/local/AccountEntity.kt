package app.ledgerpop.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val icon: String = "🏦",
    val type: String = "BANK" // "BANK", "CARD", "OTHER"
)
