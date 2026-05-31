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

    @Query("UPDATE sms_transactions SET accountHint = :targetName WHERE accountHint = :sourceName")
    suspend fun updateAccountName(sourceName: String, targetName: String)

    @Query("UPDATE sms_transactions SET category = :targetName WHERE category = :sourceName")
    suspend fun updateCategoryName(sourceName: String, targetName: String)

    @Query("SELECT * FROM sms_transactions WHERE linkedTransactionId = :debitId")
    fun getLinkedCredits(debitId: Int): Flow<List<SmsTransactionEntity>>

    @Query("SELECT * FROM sms_transactions WHERE linkedTransactionId = :debitId")
    suspend fun getLinkedCreditsSync(debitId: Int): List<SmsTransactionEntity>

    @Query("SELECT * FROM sms_transactions WHERE type = 'CREDIT' AND linkedTransactionId IS NULL AND transactionTime >= :minTime")
    fun getAvailableCredits(minTime: Long): Flow<List<SmsTransactionEntity>>

    @Query("UPDATE sms_transactions SET linkedTransactionId = :debitId WHERE id = :creditId")
    suspend fun linkCreditToDebit(creditId: Int, debitId: Int?)

    @Query("UPDATE sms_transactions SET linkedTransactionId = NULL WHERE linkedTransactionId = :debitId")
    suspend fun unlinkAllCreditsFromDebit(debitId: Int)

    @Query("SELECT * FROM sms_transactions WHERE type = 'DEBIT' AND transactionTime <= :maxTime ORDER BY transactionTime DESC")
    fun getAvailableDebits(maxTime: Long): Flow<List<SmsTransactionEntity>>

    @Query("SELECT category FROM sms_transactions WHERE merchant = :merchant COLLATE NOCASE AND category != '' ORDER BY transactionTime DESC LIMIT 1")
    suspend fun getLastCategoryForMerchant(merchant: String): String?

    @Query("""
        SELECT category FROM sms_transactions 
        WHERE (:merchant LIKE merchant || '%' OR merchant LIKE :merchant || '%') 
        AND category != '' 
        AND LENGTH(merchant) >= 3 
        AND LENGTH(:merchant) >= 3
        ORDER BY transactionTime DESC LIMIT 1
    """)
    suspend fun getLastCategoryForMerchantFuzzy(merchant: String): String?
}