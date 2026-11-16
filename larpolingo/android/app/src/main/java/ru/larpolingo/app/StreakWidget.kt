package ru.larpolingo.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

/** Виджет «Ударный режим». Обновляется сразу после полуночи, чтобы стрик рос сам. */
class StreakWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = updateAll(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED -> updateAll(ctx)
            else -> super.onReceive(ctx, intent)
        }
    }

    override fun onDisabled(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(tickIntent(ctx))
    }

    companion object {
        private const val ACTION_TICK = "ru.larpolingo.app.TICK"

        fun component(ctx: Context) = ComponentName(ctx, StreakWidget::class.java)

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(component(ctx))
            if (ids.isEmpty()) return
            mgr.updateAppWidget(ids, views(ctx))
            scheduleMidnight(ctx)
        }

        private fun views(ctx: Context): RemoteViews {
            val n = Streak.value(ctx)
            val bg = Streak.bg(ctx)
            return RemoteViews(ctx.packageName, R.layout.widget).apply {
                setInt(R.id.root, "setBackgroundResource", bg.res)
                setImageViewResource(R.id.pic, Streak.pic(ctx).res)
                setTextViewText(R.id.num, n.toString())
                setTextColor(R.id.num, bg.numColor)
                setTextViewText(R.id.label, Streak.label(n))
                setTextColor(R.id.label, bg.labelColor)
                val open = PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.root, open)
            }
        }

        private fun tickIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, StreakWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /** Будильник на ближайшую полночь (+5 с). Точный не нужен — система может чуть сдвинуть. */
        private fun scheduleMidnight(ctx: Context) {
            val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli() + 5_000
            ctx.getSystemService(AlarmManager::class.java)
                .setAndAllowWhileIdle(AlarmManager.RTC, at, tickIntent(ctx))
        }
    }
}
