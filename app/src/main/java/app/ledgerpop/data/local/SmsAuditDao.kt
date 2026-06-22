package app.ledgerpop.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsAuditDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(audit: SmsAuditEntity): Long

    @Query("SELECT * FROM sms_audit ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SmsAuditEntity>>

    @Query("SELECT * FROM sms_audit ORDER BY timestamp DESC")
    suspend fun getAllSync(): List<SmsAuditEntity>

    @Query("SELECT * FROM sms_audit WHERE status = :status ORDER BY timestamp DESC")
    fun getByStatus(status: String): Flow<List<SmsAuditEntity>>

    @Query("SELECT * FROM sms_audit WHERE reportType != '' ORDER BY timestamp DESC")
    fun getReported(): Flow<List<SmsAuditEntity>>

    // FIX: Added function for viewmodel to fetch a single entry
    @Query("SELECT * FROM sms_audit WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Int): SmsAuditEntity?

    @Query("""
        UPDATE sms_audit 
        SET reportType = :reportType, reportNote = :note 
        WHERE id = :id
    """)
    suspend fun updateReport(id: Int, reportType: String, note: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(audits: List<SmsAuditEntity>)

    @Query("SELECT COUNT(*) FROM sms_audit WHERE hashKey = :hashKey")
    suspend fun exists(hashKey: String): Int

    @Query("""
        SELECT COUNT(*) FROM sms_audit 
        WHERE sender = :sender 
        AND body = :body 
        AND ABS(timestamp - :timestamp) < 10000
    """)
    suspend fun existsDuplicate(sender: String, body: String, timestamp: Long): Int

    @Query("DELETE FROM sms_audit")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM sms_audit WHERE status = 'IMPORTED'")
    suspend fun importedCount(): Int

    @Query("SELECT COUNT(*) FROM sms_audit WHERE status = 'SKIPPED'")
    suspend fun skippedCount(): Int

    @Query("SELECT COUNT(*) FROM sms_audit WHERE reportType != ''")
    suspend fun reportedCount(): Int

    @Query("DELETE FROM sms_audit")
    suspend fun deleteAll()
}