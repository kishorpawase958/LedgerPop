package app.ledgerpop.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SmsTransactionEntity::class,
        SmsAuditEntity::class,
        CustomCategoryEntity::class
    ],
    version = 4, // bumped from 3
    exportSchema = false
)
abstract class LedgerPopDatabase : RoomDatabase() {

    abstract fun smsTransactionDao(): SmsTransactionDao
    abstract fun smsAuditDao(): SmsAuditDao
    abstract fun customCategoryDao(): CustomCategoryDao

    companion object {
        @Volatile
        private var INSTANCE: LedgerPopDatabase? = null

        fun getInstance(context: Context): LedgerPopDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerPopDatabase::class.java,
                    "ledgerpop_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}