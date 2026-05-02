package app.ledgerpop.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsTransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: SmsTransactionEntity): Long

    @Update
    suspend fun update(transaction: SmsTransactionEntity)

    @Delete
    suspend fun delete(txn: SmsTransactionEntity)

    @Query("SELECT * FROM sms_transactions ORDER BY transactionTime DESC")
    fun getAllTransactions(): Flow<List<SmsTransactionEntity>>

    @Query("SELECT * FROM sms_transactions WHERE id = :id")
    suspend fun getById(id: Int): SmsTransactionEntity?

    // FIX: Added function for viewmodel to find a transaction by its hash
    @Query("SELECT * FROM sms_transactions WHERE hashKey = :hashKey LIMIT 1")
    suspend fun getTransactionByHash(hashKey: String): SmsTransactionEntity?

    @Query("SELECT COUNT(*) FROM sms_transactions WHERE hashKey = :hashKey")
    suspend fun exists(hashKey: String): Int

    @Query("DELETE FROM sms_transactions")
    suspend fun clearAll()

    @Query("SELECT * FROM sms_transactions ORDER BY transactionTime DESC")
    suspend fun getAllTransactionsSync(): List<SmsTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<SmsTransactionEntity>)

    @Query("DELETE FROM sms_transactions")
    suspend fun deleteAll()

    @Query("SELECT DISTINCT accountHint FROM sms_transactions WHERE accountHint != ''")
    suspend fun getAllAccounts(): List<String>
}