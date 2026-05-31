package app.ledgerpop.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import app.ledgerpop.data.local.LedgerPopDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_EXCLUDE = "app.ledgerpop.ACTION_EXCLUDE"
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_EXCLUDE) {
            val txnId = intent.getIntExtra("transaction_id", -1)
            if (txnId != -1) {
                // Dismiss the notification immediately for responsive UI
                NotificationManagerCompat.from(context).cancel(txnId)

                scope.launch {
                    val db = LedgerPopDatabase.getInstance(context)
                    val dao = db.smsTransactionDao()
                    val txn = dao.getById(txnId)
                    if (txn != null) {
                        dao.update(txn.copy(isBillable = false))
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Transaction excluded from analytics", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}
