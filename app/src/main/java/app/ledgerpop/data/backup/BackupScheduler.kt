package app.ledgerpop.data.backup

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class BackupScheduler(private val context: Context) {

    fun scheduleBackup(enabled: Boolean, frequency: String) {
        val workManager = WorkManager.getInstance(context)
        
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val interval = when (frequency) {
            "Hourly" -> 1L to TimeUnit.HOURS
            "Daily" -> 1L to TimeUnit.DAYS
            "Weekly" -> 7L to TimeUnit.DAYS
            "Monthly" -> 30L to TimeUnit.DAYS
            else -> 1L to TimeUnit.DAYS
        }

        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            interval.first, interval.second
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            backupRequest
        )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "ledgerpop_auto_backup"
    }
}
