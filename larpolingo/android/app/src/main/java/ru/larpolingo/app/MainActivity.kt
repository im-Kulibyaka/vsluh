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
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Chronometer
import android.widget.EditText
import android.widget.FrameLayout
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
    private lateinit var scroll: ScrollView
    private lateinit var daysEdit: EditText
    private lateinit var tomorrow: TextView
    private lateinit var customEdit: EditText
    private lateinit var subEdit: EditText
    private val picCells = mutableListOf<View>()
    private val flameCells = mutableListOf<TextView>()
    private val subCells = mutableListOf<TextView>()
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

        // ── превью виджета (тот же макет, что и на рабочем столе)
        val frame = FrameLayout(this)
        preview = LayoutInflater.from(this).inflate(R.layout.widget, frame, false)
        frame.addView(preview, FrameLayout.LayoutParams(dp(180), dp(180), Gravity.CENTER))
        col.addView(frame, LinearLayout.LayoutParams(-1, dp(196)).apply { topMargin = dp(8) })

        // ── дни стрика
        col.addView(header("Дней в стрике"))
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        daysEdit = field(9, 26f).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setTextColor(0xFFFF9600.toInt())
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
        // сетка по 4 картинки в ряд
        val perRow = 4
        var line: LinearLayout? = null
        Streak.pics.forEachIndexed { i, p ->
            if (i % perRow == 0) line = LinearLayout(this).also { col.addView(it) }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(4), dp(4), dp(2))
                setOnClickListener { Streak.setPic(this@MainActivity, p); refresh() }
            }
            cell.addView(ImageView(this).apply {
                setImageResource(p.res)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.BLACK) }
                clipToOutline = true
            }, LinearLayout.LayoutParams(-1, dp(72)))
            cell.addView(TextView(this).apply {
                text = p.title; textSize = 10f; setTextColor(textGray); gravity = Gravity.CENTER
                setLines(2)
            })
            cell.tag = p.id
            picCells += cell
            line!!.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        // добить последний ряд пустыми ячейками, чтобы картинки не растягивались
        repeat((perRow - Streak.pics.size % perRow) % perRow) {
            line!!.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }

        // ── значок огня
        col.addView(header("Значок"))
        col.addView(chips(Streak.Flame.entries.map { it.title }, flameCells, perRow = 3) { i ->
            Streak.setFlame(this, Streak.Flame.entries[i]); refresh()
        })

        // ── подпись под числом
        col.addView(header("Подпись"))
        col.addView(chips(Streak.Sub.entries.map { it.title }, subCells) { i ->
            Streak.setSub(this, Streak.Sub.entries[i]); refresh()
        })
        subEdit = field(40, 17f).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "например, Last chance!"
            setTextColor(textDark)
        }
        subEdit.addTextChangedListener(watch {
            if (quiet) return@watch
            Streak.setText(this, it); refresh(keepSub = true)
        })
        col.addView(subEdit, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })

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
        customEdit = field(7, 18f).apply {
            hint = "сколько дней прибавлять каждый день"
            setTextColor(textDark)
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
        scroll = ScrollView(this).apply { addView(col) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val createBtn = TextView(this).apply {
            text = "СОЗДАТЬ ВИДЖЕТ"
            textSize = 17f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            letterSpacing = 0.05f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = button3d(green, greenDark)
            isClickable = true
            setOnClickListener { createWidget() }
        }
        root.addView(createBtn, LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(dp(20), dp(8), dp(20), dp(16)) })
        setContentView(root)

        // Клавиатура и системные панели. На Android 15 приложение всегда рисуется «от края до края»,
        // и adjustResize больше не сжимает экран сам — отступы ставим вручную.
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { v, ins ->
                val bars = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = ins.getInsets(WindowInsets.Type.ime())
                val imeOn = ins.isVisible(WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
                // пока открыта клавиатура, кнопку прячем, чтобы полю ввода хватило места
                createBtn.visibility = if (imeOn) View.GONE else View.VISIBLE
                if (imeOn) scroll.post { currentFocus?.let { f -> scrollToView(scroll, f) } }
                WindowInsets.CONSUMED
            }
        }
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
    private fun refresh(keepDays: Boolean = false, keepCustom: Boolean = false, keepSub: Boolean = false) {
        quiet = true
        val n = Streak.value(this)
        val pic = Streak.pic(this)
        val flame = Streak.flame(this)
        val sub = Streak.sub(this)
        val k = Streak.perDay(this)

        preview.findViewById<ImageView>(R.id.art).setImageResource(pic.res)
        preview.findViewById<ImageView>(R.id.flame).apply {
            visibility = if (flame == Streak.Flame.NONE) View.GONE else View.VISIBLE
            if (flame != Streak.Flame.NONE) setImageResource(flame.res)
            setColorFilter(if (flame == Streak.Flame.TINT) pic.numColor else 0)
        }
        preview.findViewById<TextView>(R.id.num).apply {
            text = n.toString(); textSize = Streak.numSize(n); setTextColor(pic.numColor)
        }
        preview.findViewById<TextView>(R.id.sub).apply {
            text = Streak.text(this@MainActivity); setTextColor(pic.subColor)
            visibility = if (sub == Streak.Sub.TEXT) View.VISIBLE else View.GONE
        }
        preview.findViewById<Chronometer>(R.id.timer).apply {
            setTextColor(pic.subColor)
            visibility = if (sub == Streak.Sub.TIMER) View.VISIBLE else View.GONE
            isCountDown = true
            base = SystemClock.elapsedRealtime() + Streak.msToMidnight()
            if (sub == Streak.Sub.TIMER) start() else stop()
        }

        if (!keepDays && daysEdit.text.toString() != n.toString()) {
            daysEdit.setText(n.toString()); daysEdit.setSelection(daysEdit.text.length)
        }
        for (c in picCells) c.background = if (c.tag == pic.id) box(blueLight, blue) else box(Color.WHITE, gray)
        select(flameCells, flame.ordinal)
        select(subCells, sub.ordinal)
        subEdit.visibility = if (sub == Streak.Sub.TEXT) View.VISIBLE else View.GONE
        if (!keepSub) subEdit.setText(Streak.text(this))

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

    /** Прокрутить так, чтобы поле ввода было видно над клавиатурой (с небольшим запасом). */
    private fun scrollToView(scroll: ScrollView, v: View) {
        val r = Rect(); v.getDrawingRect(r)
        scroll.offsetDescendantRectToMyCoords(v, r)
        val top = r.top - dp(80)
        val bottom = r.bottom + dp(24)
        val y = scroll.scrollY
        if (top < y) scroll.smoothScrollTo(0, top.coerceAtLeast(0))
        else if (bottom > y + scroll.height) scroll.smoothScrollTo(0, bottom - scroll.height)
    }

    // ── мелкие помощники оформления в стиле Duolingo

    private fun field(maxLen: Int, size: Float) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(maxLen))
        textSize = size
        gravity = Gravity.CENTER
        background = box(Color.WHITE, gray)
        imeOptions = EditorInfo.IME_ACTION_DONE
        setSingleLine()
        // при переходе между полями с уже открытой клавиатурой тоже докручиваем до поля
        setOnFocusChangeListener { v, has -> if (has) v.postDelayed({ scrollToView(scroll, v) }, 250) }
    }

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

    private fun chips(titles: List<String>, out: MutableList<TextView>, perRow: Int = titles.size, onPick: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var wrap = LinearLayout(this)
        titles.forEachIndexed { i, t ->
            if (i % perRow == 0) {
                wrap = LinearLayout(this)
                box.addView(wrap, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(8) })
            }
            val c = TextView(this).apply {
                text = t
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { customEdit.clearFocus(); subEdit.clearFocus(); onPick(i) }
            }
            out += c
            wrap.addView(c, LinearLayout.LayoutParams(0, dp(46), 1f).apply { if (i % perRow > 0) leftMargin = dp(8) })
        }
        // пустые места в последнем ряду, чтобы кнопки были одной ширины
        repeat((perRow - titles.size % perRow) % perRow) {
            wrap.addView(View(this), LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
        }
        return box
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
