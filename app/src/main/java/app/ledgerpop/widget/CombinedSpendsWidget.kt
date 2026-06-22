package app.ledgerpop.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.ledgerpop.MainActivity
import app.ledgerpop.R
import app.ledgerpop.data.local.LedgerPopDatabase
import app.ledgerpop.utils.AmountUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

open class CombinedSpendsWidget : AppWidgetProvider() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val appContext = context.applicationContext
        val db = LedgerPopDatabase.getInstance(appContext)
        val dao = db.smsTransactionDao()

        scope.launch {
            try {
                val all = dao.getAllTransactionsSync()
                val billable = all.filter { it.isBillable && it.type == "DEBIT" }

                val cal = Calendar.getInstance()
                val currentYear = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH)
                val currentDay = cal.get(Calendar.DAY_OF_YEAR)

                val today = billable.filter {
                    val c = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                    c.get(Calendar.YEAR) == currentYear && c.get(Calendar.DAY_OF_YEAR) == currentDay
                }.sumOf { it.amount }

                val month = billable.filter {
                    val c = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                    c.get(Calendar.YEAR) == currentYear && c.get(Calendar.MONTH) == currentMonth
                }.sumOf { it.amount }

                withContext(Dispatchers.Main) {
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(appContext, appWidgetManager, appWidgetId, today, month)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    open fun getLayoutId(): Int = R.layout.widget_combined_light

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        today: Double,
        month: Double
    ) {
        val views = RemoteViews(context.packageName, getLayoutId())
        
        views.setTextViewText(R.id.tv_spends_today, AmountUtils.formatWithCurrency(today))
        views.setTextViewText(R.id.tv_spends_month, AmountUtils.formatWithCurrency(month))

        // Intent to launch MainActivity and show Add Transaction Dialog
        val addIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "add_transaction")
        }
        val addPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId * 10, // Unique request code
            addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_add_txn, addPendingIntent)

        // General click to open app
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

class CombinedSpendsWidgetLight : CombinedSpendsWidget() {
    override fun getLayoutId(): Int = R.layout.widget_combined_light
}

class CombinedSpendsWidgetDark : CombinedSpendsWidget() {
    override fun getLayoutId(): Int = R.layout.widget_combined_dark
}
