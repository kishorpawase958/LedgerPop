package app.ledgerpop.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CustomCategoryEntity): Long

    @Query("SELECT * FROM custom_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CustomCategoryEntity>>

    @Query("SELECT * FROM custom_categories ORDER BY name ASC")
    suspend fun getAllSync(): List<CustomCategoryEntity>

    @Query("SELECT * FROM custom_categories WHERE type = :type ORDER BY name ASC")
    fun getCategoriesByType(type: String): Flow<List<CustomCategoryEntity>>

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM custom_categories WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CustomCategoryEntity?

    @Query("DELETE FROM custom_categories")
    suspend fun deleteAll()
}
