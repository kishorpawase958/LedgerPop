package app.ledgerpop.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SpamKeywordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(spamKeyword: SpamKeywordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(keywords: List<SpamKeywordEntity>)

    @Update
    suspend fun update(spamKeyword: SpamKeywordEntity)

    @Delete
    suspend fun delete(spamKeyword: SpamKeywordEntity)

    @Query("DELETE FROM spam_keywords WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM spam_keywords ORDER BY keyword ASC")
    fun getAllKeywords(): Flow<List<SpamKeywordEntity>>

    @Query("SELECT * FROM spam_keywords ORDER BY keyword ASC")
    suspend fun getAllKeywordsSync(): List<SpamKeywordEntity>

    @Query("SELECT keyword FROM spam_keywords")
    suspend fun getAllKeywordStrings(): List<String>

    @Query("SELECT COUNT(*) FROM spam_keywords")
    suspend fun getKeywordsCount(): Int

    @Query("SELECT COUNT(*) FROM spam_keywords WHERE LOWER(keyword) = LOWER(:keyword)")
    suspend fun exists(keyword: String): Int
}
