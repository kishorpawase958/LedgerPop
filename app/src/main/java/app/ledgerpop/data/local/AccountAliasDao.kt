package app.ledgerpop.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountAliasDao {
    @Query("SELECT * FROM account_aliases")
    suspend fun getAllAliases(): List<AccountAliasEntity>

    @Query("SELECT * FROM account_aliases")
    fun getAllAliasesFlow(): Flow<List<AccountAliasEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alias: AccountAliasEntity)

    @Query("SELECT targetAccountName FROM account_aliases WHERE alias = :alias COLLATE NOCASE LIMIT 1")
    suspend fun getTargetName(alias: String): String?

    @Query("DELETE FROM account_aliases WHERE alias = :alias COLLATE NOCASE")
    suspend fun deleteByAlias(alias: String)

    @Query("DELETE FROM account_aliases")
    suspend fun deleteAll()
}
