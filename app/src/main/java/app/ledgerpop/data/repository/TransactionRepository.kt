package app.ledgerpop.data.repository

import app.ledgerpop.data.local.CustomCategoryDao
import app.ledgerpop.data.local.SmsTransactionDao
import app.ledgerpop.data.local.SmsTransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val dao: SmsTransactionDao,
    private val categoryDao: CustomCategoryDao
) {
    fun getAllTransactions(): Flow<List<SmsTransactionEntity>> = dao.getAllTransactions()

    suspend fun getById(id: Int): SmsTransactionEntity? = dao.getById(id)

    suspend fun update(txn: SmsTransactionEntity) = dao.update(txn)
    suspend fun clearAll() = dao.clearAll()
    // TransactionRepository.kt
    suspend fun delete(txn: SmsTransactionEntity) = dao.delete(txn)
    suspend fun insert(txn: SmsTransactionEntity) = dao.insert(txn)

    fun getAllCustomCategories() = categoryDao.getAllCategories()


}