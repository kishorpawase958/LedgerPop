package app.ledgerpop.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.ledgerpop.MainActivity
import app.ledgerpop.R

open class AddTransactionWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val appContext = context.applicationContext
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(appContext, appWidgetManager, appWidgetId)
        }
    }

    open fun getLayoutId(): Int = R.layout.widget_add_txn_light

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, getLayoutId())
        
        // Intent to launch MainActivity and show Add Transaction Dialog
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "add_transaction")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            appWidgetId, // Using widget ID as request code
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

class AddTransactionWidgetLight : AddTransactionWidget() {
    override fun getLayoutId(): Int = R.layout.widget_add_txn_light
}

class AddTransactionWidgetDark : AddTransactionWidget() {
    override fun getLayoutId(): Int = R.layout.widget_add_txn_dark
}
