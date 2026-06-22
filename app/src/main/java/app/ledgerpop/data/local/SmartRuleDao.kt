package app.ledgerpop.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SmartRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: SmartRuleEntity)

    @Query("SELECT * FROM smart_rules")
    suspend fun getAll(): List<SmartRuleEntity>

    @Query("SELECT * FROM smart_rules WHERE sender = :sender")
    suspend fun getBySender(sender: String): List<SmartRuleEntity>

    @Query("DELETE FROM smart_rules WHERE sender = :sender AND bodyStructure = :structure")
    suspend fun deleteRule(sender: String, structure: String)

    @Query("DELETE FROM smart_rules")
    suspend fun deleteAll()
}
