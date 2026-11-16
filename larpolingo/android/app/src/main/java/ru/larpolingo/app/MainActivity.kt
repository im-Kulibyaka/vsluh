package ru.larpolingo.app

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Один экран: превью виджета, настройки и кнопка «Создать виджет». */
class MainActivity : Activity() {

    private val green = 0xFF58CC02.toInt()
    private val greenDark = 0xFF46A302.toInt()
    private val blue = 0xFF1CB0F6.toInt()
    private val blueLight = 0xFFDDF4FF.toInt()
    private val gray = 0xFFE5E5E5.toInt()
    private val textDark = 0xFF4B4B4B.toInt()
    private val textGray = 0xFF777777.toInt()

    private lateinit var preview: View
    private lateinit var daysEdit: EditText
    private lateinit var tomorrow: TextView
    private lateinit var customEdit: EditText
    private val picCells = mutableListOf<View>()
    private val bgCells = mutableListOf<TextView>()
    private val growCells = mutableListOf<TextView>()
    private var quiet = false

    private fun dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        col.addView(TextView(this).apply {
            text = "larpolingo"
            textSize = 30f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setTextColor(green)
            gravity = Gravity.CENTER
        })

        // ── превью виджета
        val frame = FrameLayout(this)
        preview = LayoutInflater.from(this).inflate(R.layout.widget, frame, false)
        frame.addView(preview, FrameLayout.LayoutParams(dp(170), dp(170), Gravity.CENTER))
        col.addView(frame, LinearLayout.LayoutParams(-1, dp(190)).apply { topMargin = dp(8) })

        // ── дни стрика
        col.addView(header("Дней в стрике"))
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        daysEdit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(9))
            textSize = 26f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setTextColor(0xFFFF9600.toInt())
            gravity = Gravity.CENTER
            background = box(Color.WHITE, gray)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine()
        }
        row.addView(stepButton("−", -1), LinearLayout.LayoutParams(dp(56), dp(56)))
        row.addView(daysEdit, LinearLayout.LayoutParams(0, dp(56), 1f).apply { leftMargin = dp(10); rightMargin = dp(10) })
        row.addView(stepButton("+", +1), LinearLayout.LayoutParams(dp(56), dp(56)))
        col.addView(row)
        daysEdit.addTextChangedListener(watch {
            if (quiet) return@watch
            val v = it.toLongOrNull() ?: return@watch
            Streak.setValue(this, v); refresh(keepDays = true)
        })

        // ── картинка
        col.addView(header("Картинка"))
        val pics = LinearLayout(this)
        for (p in Streak.pics) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(6), dp(8), dp(6), dp(6))
                setOnClickListener { Streak.setPic(this@MainActivity, p); refresh() }
            }
            cell.addView(ImageView(this).apply { setImageResource(p.res) }, LinearLayout.LayoutParams(dp(56), dp(56)))
            cell.addView(TextView(this).apply {
                text = p.title; textSize = 11f; setTextColor(textGray); gravity = Gravity.CENTER
                setLines(2); maxWidth = dp(72)
            })
            cell.tag = p.id
            picCells += cell
            pics.addView(cell, LinearLayout.LayoutParams(dp(80), -2).apply { rightMargin = dp(8) })
        }
        col.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(pics)
        })

        // ── фон
        col.addView(header("Фон виджета"))
        col.addView(chips(Streak.bgs.map { it.title }, bgCells) { i -> Streak.setBg(this, Streak.bgs[i]); refresh() })

        // ── авторост
        col.addView(header("Автоматический рост"))
        col.addView(chips(listOf("Выкл", "+1 в день", "+10 в день", "Своё"), growCells) { i ->
            when (i) {
                0 -> Streak.setPerDay(this, 0)
                1 -> Streak.setPerDay(this, 1)
                2 -> Streak.setPerDay(this, 10)
                3 -> {
                    val k = Streak.perDay(this).takeIf { it !in listOf(0L, 1L, 10L) } ?: 5
                    Streak.setPerDay(this, k)
                }
            }
            refresh()
        })
        customEdit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(7))
            hint = "сколько дней прибавлять каждый день"
            textSize = 18f
            setTextColor(textDark)
            gravity = Gravity.CENTER
            background = box(Color.WHITE, gray)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine()
        }
        customEdit.addTextChangedListener(watch {
            if (quiet) return@watch
            val k = it.toLongOrNull() ?: return@watch
            Streak.setPerDay(this, k); refresh(keepCustom = true)
        })
        col.addView(customEdit, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })
        tomorrow = TextView(this).apply { textSize = 14f; setTextColor(textGray); setPadding(0, dp(8), 0, 0) }
        col.addView(tomorrow)

        // ── экран: прокручиваемые настройки + кнопка внизу
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        root.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "СОЗДАТЬ ВИДЖЕТ"
            textSize = 17f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            letterSpacing = 0.05f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = button3d(green, greenDark)
            isClickable = true
            setOnClickListener { createWidget() }
        }, LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(dp(20), dp(8), dp(20), dp(16)) })
        setContentView(root)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh() // мог наступить новый день, пока приложение было свёрнуто
    }

    private fun createWidget() {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(StreakWidget.component(this), null, null)
        } else {
            AlertDialog.Builder(this)
                .setTitle("Добавьте виджет вручную")
                .setMessage("Ваш рабочий стол не умеет добавлять виджеты из приложений.\n\n" +
                    "Удерживайте палец на пустом месте рабочего стола → «Виджеты» → larpolingo → «Ударный режим».")
                .setPositiveButton("Понятно", null)
                .show()
        }
    }

    /** Перерисовать превью, выбранные пункты и все виджеты на рабочем столе. */
    private fun refresh(keepDays: Boolean = false, keepCustom: Boolean = false) {
        quiet = true
        val n = Streak.value(this)
        val pic = Streak.pic(this)
        val bg = Streak.bg(this)
        val k = Streak.perDay(this)

        preview.setBackgroundResource(bg.res)
        preview.findViewById<ImageView>(R.id.pic).setImageResource(pic.res)
        preview.findViewById<TextView>(R.id.num).apply { text = n.toString(); setTextColor(bg.numColor) }
        preview.findViewById<TextView>(R.id.label).apply { text = Streak.label(n); setTextColor(bg.labelColor) }

        if (!keepDays && daysEdit.text.toString() != n.toString()) {
            daysEdit.setText(n.toString()); daysEdit.setSelection(daysEdit.text.length)
        }
        for (c in picCells) c.background = if (c.tag == pic.id) box(blueLight, blue) else box(Color.WHITE, gray)
        select(bgCells, Streak.bgs.indexOf(bg))
        val growIdx = when (k) { 0L -> 0; 1L -> 1; 10L -> 2; else -> 3 }
        val customShown = growIdx == 3 || customEdit.hasFocus()
        select(growCells, if (customEdit.hasFocus()) 3 else growIdx)
        customEdit.visibility = if (customShown) View.VISIBLE else View.GONE
        if (!keepCustom && customShown) customEdit.setText(k.toString())

        tomorrow.text = if (k == 0L) "Стрик не меняется сам — только вручную."
        else "Каждый день в полночь +$k. Завтра будет ${Streak.value(this, 1)}, через неделю ${Streak.value(this, 7)}."
        quiet = false
        StreakWidget.updateAll(this)
    }

    // ── мелкие помощники оформления в стиле Duolingo

    private fun header(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        setTextColor(textDark)
        setPadding(0, dp(22), 0, dp(10))
    }

    private fun stepButton(t: String, sign: Int) = TextView(this).apply {
        text = t
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textGray)
        gravity = Gravity.CENTER
        background = button3d(Color.WHITE, gray, stroke = gray)
        isClickable = true
        setOnClickListener {
            Streak.setValue(this@MainActivity, Streak.value(this@MainActivity) + sign)
            refresh()
        }
    }

    private fun chips(titles: List<String>, out: MutableList<TextView>, onPick: (Int) -> Unit): View {
        val wrap = LinearLayout(this)
        titles.forEachIndexed { i, t ->
            val c = TextView(this).apply {
                text = t
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { customEdit.clearFocus(); onPick(i) }
            }
            out += c
            wrap.addView(c, LinearLayout.LayoutParams(0, dp(46), 1f).apply { if (i > 0) leftMargin = dp(8) })
        }
        return wrap
    }

    private fun select(cells: List<TextView>, idx: Int) = cells.forEachIndexed { i, c ->
        c.background = if (i == idx) box(blueLight, blue) else box(Color.WHITE, gray)
        c.setTextColor(if (i == idx) blue else textGray)
    }

    private fun box(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(2), stroke); cornerRadius = dp(14).toFloat()
    }

    /** «Объёмная» кнопка: тёмная подложка снизу, при нажатии кнопка «опускается». */
    private fun button3d(face: Int, shadow: Int, stroke: Int? = null): Drawable {
        fun layer(pressed: Boolean): Drawable {
            val r = dp(16).toFloat()
            val under = GradientDrawable().apply { setColor(shadow); cornerRadius = r }
            val top = GradientDrawable().apply { setColor(face); cornerRadius = r; stroke?.let { setStroke(dp(2), it) } }
            return LayerDrawable(arrayOf(under, top)).apply {
                if (pressed) setLayerInset(1, 0, dp(4), 0, 0) else setLayerInset(1, 0, 0, 0, dp(4))
            }
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), layer(true))
            addState(intArrayOf(), layer(false))
        }
    }

    private fun watch(f: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) = f(s.toString())
    }
}
