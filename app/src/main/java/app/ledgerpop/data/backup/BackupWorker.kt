package app.ledgerpop.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val backupManager = BackupManager(applicationContext)
            backupManager.createAutomaticBackup()
            Result.success()
        } catch (_: Exception) {
            // Log the error if needed
            Result.retry()
        }
    }
}
