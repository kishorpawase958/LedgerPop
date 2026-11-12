package app.ledgerpop.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "spam_keywords",
    indices = [Index(value = ["keyword"], unique = true)]
)
data class SpamKeywordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val createdAt: Long = System.currentTimeMillis()
)
