package ru.larpolingo.app

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Настройки стрика и расчёт текущего значения.
 *
 * Хранится «якорь»: значение стрика на дату и прирост за день.
 * Текущий стрик = якорь + прирост × (сколько дней прошло с даты якоря).
 */
object Streak {
    enum class Flame(val res: Int, val title: String) {
        ON(R.drawable.flame_on, "Горит"),
        ALERT(R.drawable.flame_alert, "Под угрозой"),
        OUTLINE(R.drawable.flame_outline, "Контур"),
    }

    enum class Sub(val title: String) { NONE("Нет"), TIMER("Таймер"), TEXT("Текст") }

    /** Картинка виджета и её «родное» оформление, как в настоящем виджете. */
    class Pic(
        val id: String, val res: Int, val title: String,
        val numColor: Int, val subColor: Int,
        val flame: Flame, val sub: Sub, val text: String,
    )

    private fun c(v: Long) = v.toInt()

    val pics = listOf(
        Pic("teal_eyes", R.drawable.art_teal_eyes, "Огонь в глазах", c(0xFFFFA00F), c(0xFFFFFFFF), Flame.ON, Sub.NONE, ""),
        Pic("blue_ice", R.drawable.art_blue_ice, "Замёрз у костра", c(0xFFFFA00F), c(0xFFFFFFFF), Flame.ON, Sub.NONE, ""),
        Pic("red_thumbs", R.drawable.art_red_thumbs, "Till next time", c(0xFFFFA00F), c(0xFFFBE6DC), Flame.ON, Sub.TEXT, "Till next time!"),
        Pic("meteor", R.drawable.art_meteor, "Метеор", c(0xFFFFF1CF), c(0xFFFFF1CF), Flame.OUTLINE, Sub.NONE, "See you again?"),
        Pic("orange_stool", R.drawable.art_orange_stool, "Пожар", c(0xFFFCEDE6), c(0xFFFCEDE6), Flame.ALERT, Sub.TEXT, "Last chance..."),
        Pic("purple_house", R.drawable.art_purple_house, "Горит дом", c(0xFFD6CEE2), c(0xFFD6CEE2), Flame.ALERT, Sub.TEXT, "Last chance!"),
        Pic("purple_fire", R.drawable.art_purple_fire, "Фиолетовый гнев", c(0xFFD9CCE6), c(0xFFD9CCE6), Flame.ALERT, Sub.TIMER, ""),
        Pic("laser", R.drawable.art_laser, "Лазеры", c(0xFFE9DCDC), c(0xFFE9DCDC), Flame.ALERT, Sub.TIMER, ""),
    )

    const val MAX = 999_999_999L

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("streak", Context.MODE_PRIVATE)

    fun today(): Long = LocalDate.now().toEpochDay()

    /** Значение стрика через [plusDays] дней от сегодня. */
    fun value(ctx: Context, plusDays: Long = 0): Long {
        val p = prefs(ctx)
        val anchor = p.getLong("anchor", 1)
        val day = p.getLong("day", today())
        val perDay = p.getLong("perDay", 0)
        val passed = (today() + plusDays - day).coerceAtLeast(0)
        // без переполнения: если прирост огромный, просто упираемся в максимум
        val grow = if (perDay != 0L && passed > (MAX / perDay)) MAX else perDay * passed
        return (anchor + grow).coerceIn(0, MAX)
    }

    fun perDay(ctx: Context): Long = prefs(ctx).getLong("perDay", 0)

    /** Задать стрик вручную: отсчёт роста начинается заново с сегодняшнего дня. */
    fun setValue(ctx: Context, v: Long) {
        prefs(ctx).edit().putLong("anchor", v.coerceIn(0, MAX)).putLong("day", today()).apply()
    }

    /** Сменить прирост за день, сохранив уже набранное значение. */
    fun setPerDay(ctx: Context, k: Long) {
        val cur = value(ctx)
        prefs(ctx).edit()
            .putLong("anchor", cur).putLong("day", today())
            .putLong("perDay", k.coerceIn(0, MAX)).apply()
    }

    fun pic(ctx: Context): Pic = prefs(ctx).getString("pic", null).let { id -> pics.firstOrNull { it.id == id } ?: pics[0] }

    /** Выбор картинки сразу подставляет её значок и подпись — их потом можно поменять. */
    fun setPic(ctx: Context, p: Pic) = prefs(ctx).edit()
        .putString("pic", p.id).putString("flame", p.flame.name)
        .putString("sub", p.sub.name).putString("text", p.text).apply()

    fun flame(ctx: Context): Flame =
        prefs(ctx).getString("flame", null)?.let { runCatching { Flame.valueOf(it) }.getOrNull() } ?: pic(ctx).flame
    fun setFlame(ctx: Context, f: Flame) = prefs(ctx).edit().putString("flame", f.name).apply()

    fun sub(ctx: Context): Sub =
        prefs(ctx).getString("sub", null)?.let { runCatching { Sub.valueOf(it) }.getOrNull() } ?: pic(ctx).sub
    fun setSub(ctx: Context, s: Sub) = prefs(ctx).edit().putString("sub", s.name).apply()

    fun text(ctx: Context): String = prefs(ctx).getString("text", null) ?: pic(ctx).text
    fun setText(ctx: Context, t: String) = prefs(ctx).edit().putString("text", t).apply()

    /** Сколько миллисекунд осталось до полуночи — для таймера «до конца дня». */
    fun msToMidnight(): Long {
        val now = LocalDateTime.now()
        return ChronoUnit.MILLIS.between(now, now.toLocalDate().plusDays(1).atStartOfDay())
    }

    /** Размер числа: чем больше цифр, тем мельче, чтобы влезало в виджет. */
    fun numSize(n: Long): Float = when (n.toString().length) {
        in 0..4 -> 36f; 5 -> 30f; 6 -> 26f; 7 -> 22f; else -> 19f
    }
}
