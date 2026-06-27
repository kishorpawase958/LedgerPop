package app.ledgerpop.data.repository

import app.ledgerpop.data.local.AccountDao
import app.ledgerpop.data.local.CustomCategoryDao
import app.ledgerpop.data.local.SmsTransactionDao
import app.ledgerpop.data.local.SmsTransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val dao: SmsTransactionDao,
    private val categoryDao: CustomCategoryDao,
    private val accountDao: AccountDao
) {
    fun getAllTransactions(): Flow<List<SmsTransactionEntity>> = dao.getAllTransactions()

    suspend fun getById(id: Int): SmsTransactionEntity? = dao.getById(id)

    suspend fun update(txn: SmsTransactionEntity) = dao.update(txn)

    suspend fun delete(txn: SmsTransactionEntity) {
        if (txn.type == "DEBIT") {
            dao.unlinkAllCreditsFromDebit(txn.id)
        }
        dao.delete(txn)
    }

    suspend fun insert(txn: SmsTransactionEntity) = dao.insert(txn)

    suspend fun getSimilarTransactions(merchant: String, excludeId: Int) = dao.getSimilarTransactions(merchant, excludeId)

    suspend fun updateMerchantAndCategoryForIds(ids: List<Int>, merchant: String, category: String) = 
        dao.updateMerchantAndCategoryForIds(ids, merchant, category)

    suspend fun updateMerchantForIds(ids: List<Int>, merchant: String) = 
        dao.updateMerchantForIds(ids, merchant)

    suspend fun updateCategoryForIds(ids: List<Int>, category: String) = 
        dao.updateCategoryForIds(ids, category)

    suspend fun updateBillableForIds(ids: List<Int>, isBillable: Boolean) = 
        dao.updateBillableForIds(ids, isBillable)

    suspend fun updateBulk(ids: List<Int>, merchant: String, category: String, isBillable: Boolean) = 
        dao.updateBulk(ids, merchant, category, isBillable)

    suspend fun deleteByIds(ids: List<Int>) {
        // Need to unlink credits before deleting debits
        ids.forEach { id ->
            dao.unlinkAllCreditsFromDebit(id)
        }
        dao.deleteByIds(ids)
    }

    fun getAllCustomCategories() = categoryDao.getAllCategories()
    
    fun getAllAccounts() = accountDao.getAllAccounts()

    fun getLinkedCredits(debitId: Int): Flow<List<SmsTransactionEntity>> = dao.getLinkedCredits(debitId)

    suspend fun getLinkedCreditsSync(debitId: Int): List<SmsTransactionEntity> = dao.getLinkedCreditsSync(debitId)

    fun getAvailableCredits(minTime: Long): Flow<List<SmsTransactionEntity>> = dao.getAvailableCredits(minTime)

    suspend fun linkCreditToDebit(creditId: Int, debitId: Int?) = dao.linkCreditToDebit(creditId, debitId)

    suspend fun unlinkAllCreditsFromDebit(debitId: Int) = dao.unlinkAllCreditsFromDebit(debitId)

    fun getAvailableDebits(maxTime: Long) = dao.getAvailableDebits(maxTime)
}