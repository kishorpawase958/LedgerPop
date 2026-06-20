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
        AccountAliasEntity::class,
        SmartRuleEntity::class
    ],
    version = 10, // bumped from 9
    exportSchema = false
)
abstract class LedgerPopDatabase : RoomDatabase() {

    abstract fun smsTransactionDao(): SmsTransactionDao
    abstract fun smsAuditDao(): SmsAuditDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun accountAliasDao(): AccountAliasDao
    abstract fun smartRuleDao(): SmartRuleDao

    companion object {
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the 'note' column to 'sms_transactions' table
                db.execSQL("ALTER TABLE sms_transactions ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create new tables added in v3
                db.execSQL("CREATE TABLE IF NOT EXISTS `smart_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sender` TEXT NOT NULL, `bodyStructure` TEXT NOT NULL, `ruleType` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL, `type` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `account_aliases` (`alias` TEXT NOT NULL, `targetAccountName` TEXT NOT NULL, PRIMARY KEY(`alias`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `custom_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `emoji` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sms_audit` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sender` TEXT NOT NULL, `body` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, `skipReason` TEXT NOT NULL, `parsedAmount` REAL NOT NULL, `parsedType` TEXT NOT NULL, `reportType` TEXT NOT NULL, `reportNote` TEXT NOT NULL, `hashKey` TEXT NOT NULL)")

                // 2. Update sms_transactions table (recreate to add Foreign Key and new columns)
                db.execSQL("""
                    CREATE TABLE `sms_transactions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `sender` TEXT NOT NULL, 
                        `body` TEXT NOT NULL, 
                        `amount` REAL NOT NULL, 
                        `originalAmount` REAL, 
                        `type` TEXT NOT NULL, 
                        `merchant` TEXT NOT NULL, 
                        `category` TEXT NOT NULL, 
                        `accountHint` TEXT NOT NULL, 
                        `isBillable` INTEGER NOT NULL, 
                        `transactionTime` INTEGER NOT NULL, 
                        `hashKey` TEXT NOT NULL, 
                        `bank` TEXT NOT NULL, 
                        `note` TEXT NOT NULL, 
                        `linkedTransactionId` INTEGER, 
                        FOREIGN KEY(`linkedTransactionId`) REFERENCES `sms_transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL 
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `sms_transactions_new` (`id`, `sender`, `body`, `amount`, `type`, `merchant`, `category`, `accountHint`, `isBillable`, `transactionTime`, `hashKey`, `bank`, `note`)
                    SELECT `id`, `sender`, `body`, `amount`, `type`, `merchant`, `category`, `accountHint`, `isBillable`, `transactionTime`, `hashKey`, `bank`, `note` 
                    FROM `sms_transactions`
                """)

                db.execSQL("DROP TABLE `sms_transactions`")
                db.execSQL("ALTER TABLE `sms_transactions_new` RENAME TO `sms_transactions`")
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
                    .addMigrations(MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}