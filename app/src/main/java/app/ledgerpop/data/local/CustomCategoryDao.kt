package app.ledgerpop.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CustomCategoryEntity): Long

    @Query("SELECT * FROM custom_categories WHERE type = :type ORDER BY name ASC")
    fun getCategoriesByType(type: String): Flow<List<CustomCategoryEntity>>

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun deleteById(id: Int)
}