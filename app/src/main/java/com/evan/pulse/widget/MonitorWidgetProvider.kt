package com.evan.pulse.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.evan.pulse.MainActivity
import com.evan.pulse.R
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.di.ServiceLocator

class MonitorWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { render(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> refresh(context)
            ACTION_RUN -> {
                val targetId = intent.getLongExtra(EXTRA_TARGET_ID, -1L)
                if (targetId >= 0L) {
                    ServiceLocator.monitorScheduler.runOnce(targetId) {
                        refresh(context.applicationContext)
                    }
                }
            }
        }
    }

    private fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, MonitorWidgetProvider::class.java))
        ids.forEach { render(context, manager, it) }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.monitor_widget)
        views.setOnClickPendingIntent(R.id.widget_refresh, broadcast(context, ACTION_REFRESH, 0))
        views.setOnClickPendingIntent(R.id.widget_title, activity(context))
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_monitor_title))
        val targets = ServiceLocator.monitorStore.targets.value.filter { it.enabled }.take(MAX_TARGETS)
        views.removeAllViews(R.id.widget_targets)
        if (targets.isEmpty()) {
            val empty = RemoteViews(context.packageName, R.layout.monitor_widget_target)
            empty.setTextViewText(R.id.widget_target_name, context.getString(R.string.widget_monitor_empty))
            empty.setTextViewText(R.id.widget_target_status, "")
            empty.setViewVisibility(R.id.widget_target_run, android.view.View.GONE)
            views.addView(R.id.widget_targets, empty)
        } else {
            targets.forEach { target -> views.addView(R.id.widget_targets, targetRow(context, target)) }
        }
        manager.updateAppWidget(widgetId, views)
    }

    private fun targetRow(context: Context, target: MonitorTarget): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.monitor_widget_target)
        row.setTextViewText(R.id.widget_target_name, target.name)
        val latest = ServiceLocator.monitorStore.historyFor(target.id).firstOrNull()
        val status = when {
            latest == null -> context.getString(R.string.widget_status_unknown)
            latest.ok && latest.slow -> context.getString(R.string.widget_status_slow)
            latest.ok -> context.getString(R.string.widget_status_up)
            else -> context.getString(R.string.widget_status_down)
        }
        row.setTextViewText(R.id.widget_target_status, status)
        row.setOnClickPendingIntent(R.id.widget_target_run, broadcast(context, ACTION_RUN, target.id))
        row.setOnClickPendingIntent(R.id.widget_target_name, activity(context))
        return row
    }

    private fun broadcast(context: Context, action: String, id: Long): PendingIntent {
        val intent = Intent(context, MonitorWidgetProvider::class.java).setAction(action)
            .putExtra(EXTRA_TARGET_ID, id)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode() + id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun activity(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val ACTION_REFRESH = "com.evan.pulse.widget.REFRESH"
        const val ACTION_RUN = "com.evan.pulse.widget.RUN"
        const val EXTRA_TARGET_ID = "com.evan.pulse.widget.TARGET_ID"
        private const val MAX_TARGETS = 4
    }
}
