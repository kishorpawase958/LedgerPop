package app.ledgerpop.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.data.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val grouped = messages.groupBy { it.originatingAddress ?: "" }

        val db = LedgerPopDatabase.getInstance(context)
        val dao = db.smsTransactionDao()
        val auditDao = db.smsAuditDao()
        val aliasDao = db.accountAliasDao()
        val accountDao = db.accountDao()
        val smartRuleDao = db.smartRuleDao()

        val pendingResult = goAsync()

        scope.launch {
            try {
                grouped.forEach { (sender, parts) ->
                    if (sender.isBlank()) return@forEach

                    val fullBody = parts.joinToString("") { it.messageBody ?: "" }
                    val timestamp = parts.first().timestampMillis

                    val msg = SmsMessage(
                        sender = sender,
                        body = fullBody,
                        timestamp = timestamp
                    )

                    val importer = SmsImporter(
                        context = context,
                        smsReader = NoOpSmsReader(context),
                        dao = dao,
                        auditDao = auditDao,
                        aliasDao = aliasDao,
                        accountDao = accountDao,
                        smartRuleDao = smartRuleDao
                    )

                    val entity = importer.importSingle(msg)
                    if (entity != null) {
                        NotificationHelper.showTransactionNotification(context, entity)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class NoOpSmsReader(context: Context) : SmsReader(context) {
    override fun readTransactionSms(since: Long): List<SmsMessage> = emptyList()
}
