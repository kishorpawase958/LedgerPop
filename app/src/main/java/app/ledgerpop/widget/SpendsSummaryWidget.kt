package app.ledgerpop.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.SizeF
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

open class SpendsSummaryWidget : AppWidgetProvider() {

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

                val spendsToday = billable.filter {
                    val c = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                    c.get(Calendar.YEAR) == currentYear && c.get(Calendar.DAY_OF_YEAR) == currentDay
                }.sumOf { it.amount }

                val spendsMonth = billable.filter {
                    val c = Calendar.getInstance().apply { timeInMillis = it.transactionTime }
                    c.get(Calendar.YEAR) == currentYear && c.get(Calendar.MONTH) == currentMonth
                }.sumOf { it.amount }

                withContext(Dispatchers.Main) {
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(appContext, appWidgetManager, appWidgetId, spendsToday, spendsMonth)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    open fun getLayoutIdTall(): Int = R.layout.widget_spends_light
    open fun getLayoutIdWide(): Int = R.layout.widget_spends_horiz_light

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        today: Double,
        month: Double
    ) {
        // Create views for different sizes
        // Wide (2x1) layout
        val wideViews = RemoteViews(context.packageName, getLayoutIdWide())
        setupViews(context, wideViews, appWidgetId, today, month)

        // Tall (2x2) layout
        val tallViews = RemoteViews(context.packageName, getLayoutIdTall())
        setupViews(context, tallViews, appWidgetId, today, month)

        // Map sizes to layouts (API 31+)
        val viewMapping = mapOf(
            SizeF(110f, 40f) to wideViews,   // 2x1 approx
            SizeF(110f, 110f) to tallViews  // 2x2 approx
        )
        
        val remoteViews = RemoteViews(viewMapping)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    private fun setupViews(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        today: Double,
        month: Double
    ) {
        views.setTextViewText(R.id.tv_spends_today, AmountUtils.formatWithCurrency(today))
        views.setTextViewText(R.id.tv_spends_month, AmountUtils.formatWithCurrency(month))

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    }
}

class SpendsSummaryWidgetLight : SpendsSummaryWidget() {
    override fun getLayoutIdTall(): Int = R.layout.widget_spends_light
    override fun getLayoutIdWide(): Int = R.layout.widget_spends_horiz_light
}

class SpendsSummaryWidgetDark : SpendsSummaryWidget() {
    override fun getLayoutIdTall(): Int = R.layout.widget_spends_dark
    override fun getLayoutIdWide(): Int = R.layout.widget_spends_horiz_dark
}
