package app.ledgerpop.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.ledgerpop.MainActivity
import app.ledgerpop.R
import app.ledgerpop.data.category.CategoryEngine
import app.ledgerpop.data.local.SmsTransactionEntity
import app.ledgerpop.utils.AmountUtils
import java.text.SimpleDateFormat
import java.util.*

object NotificationHelper {
    private const val CHANNEL_ID = "transaction_notifications"
    private const val CHANNEL_NAME = "Transaction Notifications"
    private const val CHANNEL_DESCRIPTION = "Notifications for auto-logged transactions"

    fun createNotificationChannel(context: Context) {
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
            description = CHANNEL_DESCRIPTION
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun showTransactionNotification(context: Context, txn: SmsTransactionEntity) {
        createNotificationChannel(context)

        val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormatter = SimpleDateFormat("d MMM", Locale.getDefault())
        val timeStr = timeFormatter.format(Date(txn.transactionTime))
        val dateStr = dateFormatter.format(Date(txn.transactionTime))

        val isDebit = txn.type == "DEBIT"
        val amountSign = if (isDebit) "-" else "+"
        val amountText = "$amountSign₹${AmountUtils.formatAmount(txn.amount)}"
        val logType = if (isDebit) "Expense Logged" else "Income Logged"
        val emoji = CategoryEngine.emoji(txn.category)

        // Custom Layout for Notification
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_transaction)
        remoteViews.setTextViewText(R.id.notification_header, logType)
        remoteViews.setTextViewText(R.id.notification_amount, amountText)
        remoteViews.setTextViewText(R.id.notification_category, "$emoji ${txn.category}")
        remoteViews.setTextViewText(R.id.notification_merchant, txn.merchant)
        remoteViews.setTextViewText(R.id.notification_time, "$timeStr, $dateStr")

        // Intent to open the app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("transaction_id", txn.id)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, txn.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action to exclude from analytics
        val excludeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_EXCLUDE
            putExtra("transaction_id", txn.id)
        }
        val excludePendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context, txn.id, excludeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.notification_btn_exclude, excludePendingIntent)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setCustomHeadsUpContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(txn.id, builder.build())
            } catch (_: SecurityException) {
                // Handle missing permission
            }
        }
    }
}
