package ru.larpolingo.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
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
            val pic = Streak.pic(ctx)
            val sub = Streak.sub(ctx)
            return RemoteViews(ctx.packageName, R.layout.widget).apply {
                setImageViewResource(R.id.art, pic.res)
                val flame = Streak.flame(ctx)
                setImageViewResource(R.id.flame, flame.res)
                setInt(R.id.flame, "setColorFilter", if (flame == Streak.Flame.TINT) pic.numColor else 0)
                setTextViewText(R.id.num, n.toString())
                setTextViewTextSize(R.id.num, TypedValue.COMPLEX_UNIT_SP, Streak.numSize(n))
                setTextColor(R.id.num, pic.numColor)

                setViewVisibility(R.id.sub, if (sub == Sub.TEXT) View.VISIBLE else View.GONE)
                setTextViewText(R.id.sub, Streak.text(ctx))
                setTextColor(R.id.sub, pic.subColor)

                setViewVisibility(R.id.timer, if (sub == Sub.TIMER) View.VISIBLE else View.GONE)
                setTextColor(R.id.timer, pic.subColor)
                if (sub == Sub.TIMER) {
                    setChronometer(R.id.timer, SystemClock.elapsedRealtime() + Streak.msToMidnight(), null, true)
                    setChronometerCountDown(R.id.timer, true)
                }

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

private typealias Sub = Streak.Sub
