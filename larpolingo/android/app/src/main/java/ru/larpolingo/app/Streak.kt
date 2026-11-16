package ru.larpolingo.app

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * Настройки стрика и расчёт текущего значения.
 *
 * Хранится «якорь»: значение стрика [KEY_ANCHOR] на дату [KEY_DAY] и прирост за день [KEY_PER_DAY].
 * Текущий стрик = якорь + прирост × (сколько дней прошло с даты якоря).
 */
object Streak {
    class Pic(val id: String, val res: Int, val title: String)
    class Bg(val id: String, val res: Int, val title: String, val numColor: Int, val labelColor: Int)

    val pics = listOf(
        Pic("fire", R.drawable.pic_fire, "Огонь"),
        Pic("owl_fire", R.drawable.pic_owl_fire, "Сова с огнём"),
        Pic("owl", R.drawable.pic_owl, "Сова"),
        Pic("gold", R.drawable.pic_gold, "Золотой"),
        Pic("purple", R.drawable.pic_purple, "Фиолетовый"),
        Pic("red", R.drawable.pic_red, "Красный"),
        Pic("ice", R.drawable.pic_ice, "Заморозка"),
        Pic("out", R.drawable.pic_out, "Потух"),
        Pic("owl_sad", R.drawable.pic_owl_sad, "Грустная сова"),
    )

    val bgs = listOf(
        Bg("white", R.drawable.bg_white, "Белый", 0xFFFF9600.toInt(), 0xFF777777.toInt()),
        Bg("dark", R.drawable.bg_dark, "Тёмный", 0xFFFF9600.toInt(), 0xFFDCE6EC.toInt()),
        Bg("orange", R.drawable.bg_orange, "Оранжевый", 0xFFFFFFFF.toInt(), 0xFFFFF1DC.toInt()),
        Bg("green", R.drawable.bg_green, "Зелёный", 0xFFFFFFFF.toInt(), 0xFFEAFFD9.toInt()),
    )

    const val MAX = 999_999_999L
    private const val KEY_ANCHOR = "anchor"
    private const val KEY_DAY = "day"
    private const val KEY_PER_DAY = "perDay"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("streak", Context.MODE_PRIVATE)

    fun today(): Long = LocalDate.now().toEpochDay()

    /** Значение стрика через [plusDays] дней от сегодня. */
    fun value(ctx: Context, plusDays: Long = 0): Long {
        val p = prefs(ctx)
        val anchor = p.getLong(KEY_ANCHOR, 1)
        val day = p.getLong(KEY_DAY, today())
        val perDay = p.getLong(KEY_PER_DAY, 0)
        val passed = (today() + plusDays - day).coerceAtLeast(0)
        // без переполнения: если прирост огромный, просто упираемся в максимум
        val grow = if (perDay != 0L && passed > (MAX / perDay)) MAX else perDay * passed
        return (anchor + grow).coerceIn(0, MAX)
    }

    fun perDay(ctx: Context): Long = prefs(ctx).getLong(KEY_PER_DAY, 0)

    /** Задать стрик вручную: отсчёт роста начинается заново с сегодняшнего дня. */
    fun setValue(ctx: Context, v: Long) {
        prefs(ctx).edit().putLong(KEY_ANCHOR, v.coerceIn(0, MAX)).putLong(KEY_DAY, today()).apply()
    }

    /** Сменить прирост за день, сохранив уже набранное значение. */
    fun setPerDay(ctx: Context, k: Long) {
        val cur = value(ctx)
        prefs(ctx).edit()
            .putLong(KEY_ANCHOR, cur).putLong(KEY_DAY, today())
            .putLong(KEY_PER_DAY, k.coerceIn(0, MAX)).apply()
    }

    fun pic(ctx: Context): Pic = prefs(ctx).getString("pic", null).let { id -> pics.firstOrNull { it.id == id } ?: pics[0] }
    fun setPic(ctx: Context, p: Pic) = prefs(ctx).edit().putString("pic", p.id).apply()

    fun bg(ctx: Context): Bg = prefs(ctx).getString("bg", null).let { id -> bgs.firstOrNull { it.id == id } ?: bgs[0] }
    fun setBg(ctx: Context, b: Bg) = prefs(ctx).edit().putString("bg", b.id).apply()

    /** «день / дня / дней ударного режима» по правилам русского языка. */
    fun label(n: Long): String {
        val d10 = n % 10; val d100 = n % 100
        val w = when {
            d100 in 11..14 -> "дней"
            d10 == 1L -> "день"
            d10 in 2..4 -> "дня"
            else -> "дней"
        }
        return "$w ударного режима"
    }
}
