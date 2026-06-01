package app.ledgerpop.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SmsTransactionEntity::class,
        SmsAuditEntity::class,
        CustomCategoryEntity::class,
        AccountEntity::class,
        AccountAliasEntity::class
    ],
    version = 9, // bumped from 8
    exportSchema = false
)
abstract class LedgerPopDatabase : RoomDatabase() {

    abstract fun smsTransactionDao(): SmsTransactionDao
    abstract fun smsAuditDao(): SmsAuditDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun accountAliasDao(): AccountAliasDao

    companion object {
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the 'note' column to 'sms_transactions' table
                db.execSQL("ALTER TABLE sms_transactions ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: LedgerPopDatabase? = null

        fun getInstance(context: Context): LedgerPopDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LedgerPopDatabase::class.java,
                    "ledgerpop_db"
                )
                    .addMigrations(MIGRATION_8_9)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}