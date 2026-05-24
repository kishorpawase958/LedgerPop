package app.ledgerpop.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_aliases")
data class AccountAliasEntity(
    @PrimaryKey val alias: String,
    val targetAccountName: String
)
