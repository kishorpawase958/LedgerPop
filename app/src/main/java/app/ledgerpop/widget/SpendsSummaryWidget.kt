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
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfToday = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val endOfToday = cal.timeInMillis

                val monthCal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfMonth = monthCal.timeInMillis
                monthCal.add(Calendar.MONTH, 1)
                val endOfMonth = monthCal.timeInMillis

                val spendsToday = dao.getSpendsBetween(startOfToday, endOfToday)
                val spendsMonth = dao.getSpendsBetween(startOfMonth, endOfMonth)

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
