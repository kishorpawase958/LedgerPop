package app.ledgerpop.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AccountAliasDao {
    @Query("SELECT * FROM account_aliases")
    suspend fun getAllAliases(): List<AccountAliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alias: AccountAliasEntity)

    @Query("SELECT targetAccountName FROM account_aliases WHERE alias = :alias LIMIT 1")
    suspend fun getTargetName(alias: String): String?

    @Query("DELETE FROM account_aliases WHERE alias = :alias")
    suspend fun deleteByAlias(alias: String)
}
