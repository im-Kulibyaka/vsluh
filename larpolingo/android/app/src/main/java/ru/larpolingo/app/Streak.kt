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
        TINT(R.drawable.flame_mono, "В цвет числа"),
        DONE(R.drawable.flame_done, "Выполнено"),
        ICE(R.drawable.flame_ice, "Ледяной"),
        FROZEN(R.drawable.flame_frozen, "Заморозка"),
        GOLD(R.drawable.flame_gold, "Золотой"),
        PURPLE(R.drawable.flame_purple, "Фиолетовый"),
        RED(R.drawable.flame_red, "Красный"),
        GRAY(R.drawable.flame_gray, "Потух"),
        NONE(0, "Без значка"),
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
        // из официального блога Duolingo (все состояния виджета)
        Pic("d01", R.drawable.art_d01, "Let's get rolling", c(0xFFFF9600), c(0xFF8A9A00), Flame.ON, Sub.TEXT, "Let's get rolling!"),
        Pic("d02", R.drawable.art_d02, "Time to practice", c(0xFFFAD2FF), c(0xFFFAD2FF), Flame.TINT, Sub.TEXT, "Time to practice!"),
        Pic("d03", R.drawable.art_d03, "Time to practice", c(0xFFFCA1FF), c(0xFFFCA1FF), Flame.TINT, Sub.TEXT, "Time to practice!"),
        Pic("d04", R.drawable.art_d04, "Time to practice", c(0xFFC5ACFF), c(0xFFC5ACFF), Flame.TINT, Sub.TEXT, "Time to practice!"),
        Pic("d05", R.drawable.art_d05, "Time to practice", c(0xFFC98DF5), c(0xFFC98DF5), Flame.TINT, Sub.TEXT, "Time to practice!"),
        Pic("d06", R.drawable.art_d06, "Don't let it break", c(0xFFBCD6FF), c(0xFFBCD6FF), Flame.ALERT, Sub.TEXT, "Don't let it break!"),
        Pic("d07", R.drawable.art_d07, "Don't let it break", c(0xFF8CCEB7), c(0xFF8CCEB7), Flame.ALERT, Sub.TEXT, "Don't let it break!"),
        Pic("d08", R.drawable.art_d08, "Don't let it break", c(0xFFFFA047), c(0xFFFFA047), Flame.ALERT, Sub.TEXT, "Don't let it break!"),
        Pic("d09", R.drawable.art_d09, "Last chance", c(0xFFFFAF64), c(0xFFFFAF64), Flame.ALERT, Sub.TEXT, "Last chance!"),
        Pic("d10", R.drawable.art_d10, "Last chance", c(0xFFFFB1D7), c(0xFFFFB1D7), Flame.ALERT, Sub.TEXT, "Last chance!"),
        Pic("d11", R.drawable.art_d11, "Праздник 1", c(0xFFFF9600), c(0xFFFF9600), Flame.ON, Sub.NONE, ""),
        Pic("d12", R.drawable.art_d12, "Праздник 2", c(0xFFFF9600), c(0xFFFF9600), Flame.ON, Sub.NONE, ""),
        Pic("d13", R.drawable.art_d13, "Праздник 3", c(0xFFFF9600), c(0xFFFF9600), Flame.ON, Sub.NONE, ""),
        Pic("d14", R.drawable.art_d14, "Праздник 4", c(0xFFFF9600), c(0xFFFF9600), Flame.ON, Sub.NONE, ""),
        Pic("d15", R.drawable.art_d15, "Practice time", c(0xFFD4F7F3), c(0xFFD4F7F3), Flame.TINT, Sub.TEXT, "Practice time?"),
        Pic("d16", R.drawable.art_d16, "Time to practice", c(0xFFCFF6F7), c(0xFFCFF6F7), Flame.TINT, Sub.TEXT, "Time to practice!"),
        Pic("d17", R.drawable.art_d17, "Ready to learn", c(0xFFCFF4FE), c(0xFFCFF4FE), Flame.TINT, Sub.TEXT, "Ready to learn?"),
        Pic("d18", R.drawable.art_d18, "Keep it going", c(0xFFC4E9F8), c(0xFFC4E9F8), Flame.TINT, Sub.TEXT, "Keep it going!"),
        Pic("d19", R.drawable.art_d19, "Got 3 minutes", c(0xFFCEECFE), c(0xFFCEECFE), Flame.TINT, Sub.TEXT, "Got 3 minutes?"),
        Pic("d20", R.drawable.art_d20, "Practice now", c(0xFFC7E0F9), c(0xFFC7E0F9), Flame.TINT, Sub.TEXT, "Practice now?"),
        Pic("d21", R.drawable.art_d21, "Time to practice", c(0xFFD5D1FF), c(0xFFD5D1FF), Flame.TINT, Sub.TEXT, "Time to practice!"),
        Pic("d22", R.drawable.art_d22, "Let's practice", c(0xFFCEBDFF), c(0xFFCEBDFF), Flame.TINT, Sub.TEXT, "Let's practice!"),
        Pic("d23", R.drawable.art_d23, "Please practice", c(0xFFDBC0FD), c(0xFFDBC0FD), Flame.TINT, Sub.TEXT, "Please practice!"),
        Pic("d24", R.drawable.art_d24, "Got 3 minutes", c(0xFFDDB9F9), c(0xFFDDB9F9), Flame.TINT, Sub.TEXT, "Got 3 minutes?"),
        Pic("d25", R.drawable.art_d25, "Time to practice", c(0xFFCAD7CD), c(0xFFCAD7CD), Flame.TINT, Sub.TEXT, "Time to practice!"),
        Pic("d26", R.drawable.art_d26, "Practice now", c(0xFFE1BDF4), c(0xFFE1BDF4), Flame.TINT, Sub.TEXT, "Practice now?"),
        Pic("d27", R.drawable.art_d27, "Let's practice", c(0xFFEEA8D5), c(0xFFEEA8D5), Flame.TINT, Sub.TEXT, "Let's practice!"),
        Pic("d28", R.drawable.art_d28, "Save your streak", c(0xFFF9BAC3), c(0xFFF9BAC3), Flame.ALERT, Sub.TEXT, "Save your streak!"),
        Pic("d29", R.drawable.art_d29, "Let's practice", c(0xFFE0A3B2), c(0xFFE0A3B2), Flame.ALERT, Sub.TEXT, "Let's practice?"),
        Pic("d30", R.drawable.art_d30, "Don't let it break", c(0xFFDBA99F), c(0xFFDBA99F), Flame.ALERT, Sub.TEXT, "Don't let it break!"),
        Pic("d31", R.drawable.art_d31, "It's getting late", c(0xFFD99DAF), c(0xFFD99DAF), Flame.ALERT, Sub.TEXT, "It's getting late!"),
        Pic("d32", R.drawable.art_d32, "Save your streak", c(0xFFD5AB9A), c(0xFFD5AB9A), Flame.ALERT, Sub.TEXT, "Save your streak!"),
        Pic("d33", R.drawable.art_d33, "Last chance", c(0xFFC49D9A), c(0xFFC49D9A), Flame.ALERT, Sub.TEXT, "Last chance!"),
        Pic("d34", R.drawable.art_d34, "Last chance", c(0xFFB79F9F), c(0xFFB79F9F), Flame.ALERT, Sub.TEXT, "Last chance!"),
        Pic("d35", R.drawable.art_d35, "Time to practice", c(0xFFB9BCCE), c(0xFFB9BCCE), Flame.TINT, Sub.TEXT, "Time to practice!"),
        Pic("d36", R.drawable.art_d36, "Time to practice", c(0xFFC1C3C3), c(0xFFC1C3C3), Flame.TINT, Sub.TEXT, "Time to practice!"),
        Pic("d37", R.drawable.art_d37, "Time to practice", c(0xFFBBBBBB), c(0xFFBBBBBB), Flame.TINT, Sub.TEXT, "Time to practice!"),
        Pic("d38", R.drawable.art_d38, "Time to practice", c(0xFFB8E0F5), c(0xFFB8E0F5), Flame.TINT, Sub.TEXT, "Time to practice!"),
        Pic("d39", R.drawable.art_d39, "Time to practice", c(0xFFFFD4FC), c(0xFFFFD4FC), Flame.TINT, Sub.TEXT, "Time to practice!"),
        // иллюстрации из блога Duolingo (юбилеи стрика, обложки статей)
        Pic("x_phoenix_gold", R.drawable.art_x_phoenix_gold, "Феникс", c(0xFFFF9600), c(0xFFFF9600), Flame.ON, Sub.TEXT, "day streak!"),
        Pic("x_phoenix_purple", R.drawable.art_x_phoenix_purple, "Феникс с крыльями", c(0xFFFF9600), c(0xFFCE82FF), Flame.ON, Sub.TEXT, "Congrats!"),
        Pic("x_balloons", R.drawable.art_x_balloons, "С шариками", c(0xFFFF9600), c(0xFF4B4B4B), Flame.ON, Sub.TEXT, "Day Streak!"),
        Pic("x_big_flame", R.drawable.art_x_big_flame, "Большой огонь", c(0xFFFF9600), c(0xFFFF9600), Flame.NONE, Sub.TEXT, "day language streak!"),
        Pic("x_lava", R.drawable.art_x_lava, "Прыжки по лаве", c(0xFFFFF1E0), c(0xFFFFF1E0), Flame.TINT, Sub.TEXT, "Keep it going!"),
        Pic("x_shocked", R.drawable.art_x_shocked, "В шоке", c(0xFFFFC9D9), c(0xFFFFC9D9), Flame.ALERT, Sub.TEXT, "It's getting late!"),
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
