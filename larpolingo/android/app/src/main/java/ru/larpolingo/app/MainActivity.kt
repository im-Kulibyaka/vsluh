package ru.larpolingo.app

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.widget.Chronometer
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Один экран: закреплённое превью виджета сверху, настройки в карточках и кнопка «Создать виджет». */
class MainActivity : Activity() {

    private val green = 0xFF58CC02.toInt()
    private val greenDark = 0xFF46A302.toInt()
    private val greenLight = 0xFF89E219.toInt()
    private val blue = 0xFF1CB0F6.toInt()
    private val blueLight = 0xFFDDF4FF.toInt()
    private val orange = 0xFFFF9600.toInt()
    private val gray = 0xFFE5E5E5.toInt()
    private val pageBg = 0xFFF7F7F7.toInt()
    private val textDark = 0xFF3C3C3C.toInt()
    private val textGray = 0xFF777777.toInt()
    private val black = Typeface.create("sans-serif-black", Typeface.NORMAL)

    private lateinit var hero: LinearLayout
    private lateinit var heroHint: TextView
    private lateinit var preview: View
    private lateinit var scroll: ScrollView
    private lateinit var daysEdit: EditText
    private lateinit var daysLabel: TextView
    private lateinit var picTitle: TextView
    private lateinit var picGrid: LinearLayout
    private lateinit var moreBtn: TextView
    private lateinit var tomorrow: TextView
    private lateinit var customEdit: EditText
    private lateinit var subEdit: EditText
    private val picCells = mutableListOf<View>()
    private val tabCells = mutableListOf<TextView>()
    private val flameCells = mutableListOf<View>()
    private val subCells = mutableListOf<TextView>()
    private val growCells = mutableListOf<TextView>()
    private var quiet = false
    private var tab = 0
    private var showAll = false
    private var previewSize = 0

    /** Вкладки картинок: все, «горит» (спокойные) и «тревога» (огонь под угрозой). */
    private val tabs = listOf("Все", "Горит", "Тревога")
    private fun picsFor(t: Int) = when (t) {
        1 -> Streak.pics.filter { it.flame != Streak.Flame.ALERT }
        2 -> Streak.pics.filter { it.flame == Streak.Flame.ALERT }
        else -> Streak.pics
    }

    private fun dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── шапка: зелёная плашка с названием и живым превью виджета (не прокручивается)
        hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(green, greenLight)).apply {
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat())
            }
            setPadding(dp(20), dp(14), dp(20), dp(18))
        }
        hero.addView(TextView(this).apply {
            text = "larpolingo"
            gravity = Gravity.CENTER
            textSize = 24f
            typeface = black
            setTextColor(Color.WHITE)
        })
        previewSize = dp(150)
        val frame = FrameLayout(this)
        preview = LayoutInflater.from(this).inflate(R.layout.widget, frame, false).apply { elevation = dp(10).toFloat() }
        frame.addView(preview, FrameLayout.LayoutParams(previewSize, previewSize, Gravity.CENTER))
        hero.addView(frame, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        heroHint = TextView(this).apply {
            text = "Так виджет будет выглядеть на рабочем столе"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(0xE6FFFFFF.toInt())
            setPadding(0, dp(10), 0, 0)
        }
        hero.addView(heroHint)

        // ── настройки в карточках
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }

        // дни стрика
        val days = card(col, "Дней в стрике")
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        daysEdit = field(9, 28f).apply { typeface = black; setTextColor(orange) }
        row.addView(stepButton("−") { Streak.value(this) - 1 }, LinearLayout.LayoutParams(dp(54), dp(56)))
        row.addView(daysEdit, LinearLayout.LayoutParams(0, dp(56), 1f).apply { leftMargin = dp(10); rightMargin = dp(10) })
        row.addView(stepButton("+") { Streak.value(this) + 1 }, LinearLayout.LayoutParams(dp(54), dp(56)))
        days.addView(row)
        daysLabel = TextView(this).apply { textSize = 13f; setTextColor(textGray); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(10)) }
        days.addView(daysLabel)
        // быстрые прибавки
        val quick = LinearLayout(this)
        listOf(10L, 100L, 365L, 1000L).forEachIndexed { i, d ->
            quick.addView(TextView(this).apply {
                text = "+$d"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(orange)
                background = pressable(0xFFFFF4E5.toInt(), 0xFFFFE0B2.toInt())
                setOnClickListener { daysEdit.clearFocus(); Streak.setValue(this@MainActivity, Streak.value(this@MainActivity) + d); refresh() }
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (i > 0) leftMargin = dp(8) })
        }
        days.addView(quick)
        daysEdit.addTextChangedListener(watch {
            if (quiet) return@watch
            val v = it.toLongOrNull() ?: return@watch
            Streak.setValue(this, v); refresh(keepDays = true)
        })

        // картинка
        val pics = card(col, "Картинка")
        picTitle = TextView(this).apply { textSize = 13f; setTextColor(textGray); setPadding(0, 0, 0, dp(10)) }
        pics.addView(picTitle)
        pics.addView(segmented(tabs, tabCells) { i -> tab = i; showAll = false; buildPicGrid(); refresh() })
        picGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pics.addView(picGrid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        moreBtn = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(blue)
            gravity = Gravity.CENTER
            background = pressable(Color.WHITE, blueLight)
            setOnClickListener { showAll = !showAll; buildPicGrid(); refresh() }
        }
        pics.addView(moreBtn, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(6) })
        buildPicGrid()

        // значок огня: плитки с самими иконками
        val icons = card(col, "Значок")
        val perRow = 4
        var line = LinearLayout(this)
        Streak.Flame.entries.forEachIndexed { i, f ->
            if (i % perRow == 0) {
                line = LinearLayout(this)
                icons.addView(line, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(8) })
            }
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(8), dp(2), dp(6))
                setOnClickListener { clearFields(); Streak.setFlame(this@MainActivity, f); refresh() }
            }
            tile.addView(FrameLayout(this).apply {
                // тёмная подложка, чтобы были видны светлые значки
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF3C2A63.toInt()) }
                if (f.res != 0) addView(ImageView(context).apply {
                    setImageResource(f.res)
                    if (f == Streak.Flame.TINT) setColorFilter(0xFFFFC9F0.toInt())
                }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
                else addView(TextView(context).apply { text = "—"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(-1, -1))
            }, LinearLayout.LayoutParams(dp(38), dp(38)))
            tile.addView(TextView(this).apply {
                text = f.title; textSize = 11f; setTextColor(textGray); gravity = Gravity.CENTER
                maxLines = 2; setPadding(0, dp(4), 0, 0)
            })
            flameCells += tile
            line.addView(tile, LinearLayout.LayoutParams(0, dp(84), 1f).apply { if (i % perRow > 0) leftMargin = dp(8) })
        }
        repeat((perRow - Streak.Flame.entries.size % perRow) % perRow) {
            line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dp(8) })
        }

        // подпись под числом
        val subCard = card(col, "Подпись")
        subCard.addView(segmented(Streak.Sub.entries.map { it.title }, subCells) { i ->
            Streak.setSub(this, Streak.Sub.entries[i]); refresh()
        })
        subEdit = field(40, 17f).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = "например, Last chance!"
            setTextColor(textDark)
        }
        subEdit.addTextChangedListener(watch {
            if (quiet) return@watch
            Streak.setText(this, it); refresh(keepSub = true)
        })
        subCard.addView(subEdit, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })

        // авторост
        val grow = card(col, "Автоматический рост")
        grow.addView(segmented(listOf("Выкл", "+1", "+10", "Своё"), growCells) { i ->
            when (i) {
                0 -> Streak.setPerDay(this, 0)
                1 -> Streak.setPerDay(this, 1)
                2 -> Streak.setPerDay(this, 10)
                3 -> Streak.setPerDay(this, Streak.perDay(this).takeIf { it !in listOf(0L, 1L, 10L) } ?: 5)
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
        grow.addView(customEdit, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })
        tomorrow = TextView(this).apply {
            textSize = 14f
            setTextColor(textDark)
            setLineSpacing(0f, 1.15f)
            background = GradientDrawable().apply { setColor(0xFFF1FBE6.toInt()); cornerRadius = dp(12).toFloat() }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        grow.addView(tomorrow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // ── экран целиком
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pageBg) }
        root.addView(hero, LinearLayout.LayoutParams(-1, -2))
        scroll = ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false; clipToPadding = false }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }
        val createBtn = TextView(this).apply {
            text = "СОЗДАТЬ ВИДЖЕТ"
            textSize = 17f
            typeface = black
            letterSpacing = 0.05f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = button3d(green, greenDark)
            isClickable = true
            setOnClickListener { createWidget() }
        }
        bottom.addView(createBtn, FrameLayout.LayoutParams(-1, dp(56)).apply { setMargins(dp(16), dp(12), dp(16), dp(12)) })
        root.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        // Системные панели и клавиатура. На Android 15 экран всегда «от края до края»,
        // поэтому отступы ставим сами: шапка заезжает под строку состояния, низ — над клавиатурой.
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = Color.TRANSPARENT
            window.insetsController?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
            root.setOnApplyWindowInsetsListener { v, ins ->
                val bars = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = ins.getInsets(WindowInsets.Type.ime())
                val imeOn = ins.isVisible(WindowInsets.Type.ime())
                v.setPadding(bars.left, 0, bars.right, maxOf(bars.bottom, ime.bottom))
                hero.setPadding(dp(20), bars.top + dp(10), dp(20), dp(if (imeOn) 10 else 18))
                // при открытой клавиатуре превью уменьшаем, а кнопку прячем — полю ввода нужно место
                setPreviewSize(if (imeOn) dp(92) else dp(150))
                heroHint.visibility = if (imeOn) View.GONE else View.VISIBLE
                bottom.visibility = if (imeOn) View.GONE else View.VISIBLE
                if (imeOn) scroll.post { currentFocus?.let { f -> scrollToView(scroll, f) } }
                WindowInsets.CONSUMED
            }
        } else {
            window.statusBarColor = green
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh() // мог наступить новый день, пока приложение было свёрнуто
    }

    private fun setPreviewSize(px: Int) {
        if (px == previewSize) return
        previewSize = px
        preview.layoutParams = (preview.layoutParams as FrameLayout.LayoutParams).apply { width = px; height = px }
        // на маленьком превью и шрифты меньше, чтобы число влезало
        refresh()
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

    /** Сетка картинок текущей вкладки: 4 в ряд, свёрнута до двух рядов, пока не нажали «Показать все». */
    private fun buildPicGrid() {
        picGrid.removeAllViews(); picCells.clear()
        val all = picsFor(tab)
        val perRow = 4
        val list = if (showAll) all else all.take(perRow * 2)
        var line = LinearLayout(this)
        list.forEachIndexed { i, p ->
            if (i % perRow == 0) {
                line = LinearLayout(this)
                picGrid.addView(line, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(8) })
            }
            val cell = FrameLayout(this).apply {
                setPadding(dp(3), dp(3), dp(3), dp(3))
                setOnClickListener { clearFields(); Streak.setPic(this@MainActivity, p); refresh() }
                tag = p.id
            }
            cell.addView(SquareImage(this).apply {
                setImageResource(p.res)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.BLACK) }
                clipToOutline = true
            }, FrameLayout.LayoutParams(-1, -2))
            // галочка у выбранной картинки
            cell.addView(TextView(this).apply {
                text = "✓"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(blue); setStroke(dp(2), Color.WHITE) }
                visibility = View.GONE
                tag = "check"
            }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(5), dp(5), 0) })
            picCells += cell
            line.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i % perRow > 0) leftMargin = dp(6) })
        }
        repeat((perRow - list.size % perRow) % perRow) {
            line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dp(6) })
        }
        moreBtn.visibility = if (all.size > perRow * 2) View.VISIBLE else View.GONE
        moreBtn.text = if (showAll) "СВЕРНУТЬ" else "ПОКАЗАТЬ ВСЕ (${all.size})"
    }

    /** Перерисовать превью, выбранные пункты и все виджеты на рабочем столе. */
    private fun refresh(keepDays: Boolean = false, keepCustom: Boolean = false, keepSub: Boolean = false) {
        quiet = true
        val n = Streak.value(this)
        val pic = Streak.pic(this)
        val flame = Streak.flame(this)
        val sub = Streak.sub(this)
        val k = Streak.perDay(this)
        val scale = previewSize / dp(150).toFloat()

        preview.findViewById<ImageView>(R.id.art).setImageResource(pic.res)
        preview.findViewById<ImageView>(R.id.flame).apply {
            visibility = if (flame == Streak.Flame.NONE) View.GONE else View.VISIBLE
            if (flame != Streak.Flame.NONE) setImageResource(flame.res)
            setColorFilter(if (flame == Streak.Flame.TINT) pic.numColor else 0)
            layoutParams = layoutParams.apply { width = (dp(30) * scale).toInt(); height = width }
        }
        preview.findViewById<TextView>(R.id.num).apply {
            text = n.toString(); textSize = Streak.numSize(n) * 0.85f * scale; setTextColor(pic.numColor)
        }
        preview.findViewById<TextView>(R.id.sub).apply {
            text = Streak.text(this@MainActivity); setTextColor(pic.subColor); textSize = 13f * scale
            visibility = if (sub == Streak.Sub.TEXT && scale > 0.8f) View.VISIBLE else View.GONE
        }
        preview.findViewById<Chronometer>(R.id.timer).apply {
            setTextColor(pic.subColor); textSize = 14f * scale
            visibility = if (sub == Streak.Sub.TIMER && scale > 0.8f) View.VISIBLE else View.GONE
            isCountDown = true
            base = SystemClock.elapsedRealtime() + Streak.msToMidnight()
            if (sub == Streak.Sub.TIMER) start() else stop()
        }

        if (!keepDays && daysEdit.text.toString() != n.toString()) {
            daysEdit.setText(n.toString()); daysEdit.setSelection(daysEdit.text.length)
        }
        daysLabel.text = Streak.label(n)

        picTitle.text = "Выбрано: ${pic.title}"
        for (c in picCells) {
            val on = c.tag == pic.id
            c.background = GradientDrawable().apply {
                cornerRadius = dp(15).toFloat(); setColor(Color.TRANSPARENT)
                setStroke(dp(3), if (on) blue else Color.TRANSPARENT)
            }
            c.findViewWithTag<View>("check").visibility = if (on) View.VISIBLE else View.GONE
        }
        select(tabCells, tab)
        flameCells.forEachIndexed { i, c ->
            val on = i == flame.ordinal
            c.background = if (on) box(blueLight, blue) else box(Color.WHITE, gray)
            ((c as ViewGroup).getChildAt(1) as TextView).setTextColor(if (on) blue else textGray)
        }
        select(subCells, sub.ordinal)
        subEdit.visibility = if (sub == Streak.Sub.TEXT) View.VISIBLE else View.GONE
        if (!keepSub) subEdit.setText(Streak.text(this))

        val growIdx = when (k) { 0L -> 0; 1L -> 1; 10L -> 2; else -> 3 }
        val customShown = growIdx == 3 || customEdit.hasFocus()
        select(growCells, if (customEdit.hasFocus()) 3 else growIdx)
        customEdit.visibility = if (customShown) View.VISIBLE else View.GONE
        if (!keepCustom && customShown) customEdit.setText(k.toString())

        tomorrow.text = if (k == 0L) "🔒  Стрик не меняется сам — только вручную."
        else "🔥  Каждый день в полночь +$k\nЗавтра: ${Streak.value(this, 1)}  ·  через неделю: ${Streak.value(this, 7)}"
        quiet = false
        StreakWidget.updateAll(this)
    }

    private fun clearFields() { daysEdit.clearFocus(); customEdit.clearFocus(); subEdit.clearFocus() }

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

    /** Белая карточка с заголовком; возвращает контейнер для содержимого. */
    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.WHITE); setStroke(dp(2), gray); cornerRadius = dp(20).toFloat() }
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        c.addView(TextView(this).apply {
            text = title
            textSize = 18f
            typeface = black
            setTextColor(textDark)
            setPadding(0, 0, 0, dp(10))
        })
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { if (parent.childCount > 0) topMargin = dp(12) })
        return c
    }

    private fun field(maxLen: Int, size: Float) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(maxLen))
        textSize = size
        gravity = Gravity.CENTER
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), box(Color.WHITE, blue))
            addState(intArrayOf(), box(0xFFF7F7F7.toInt(), gray))
        }
        imeOptions = EditorInfo.IME_ACTION_DONE
        setSingleLine()
        // при переходе между полями с уже открытой клавиатурой тоже докручиваем до поля
        setOnFocusChangeListener { v, has -> if (has) v.postDelayed({ scrollToView(scroll, v) }, 250) }
    }

    private fun stepButton(t: String, next: () -> Long) = TextView(this).apply {
        text = t
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textGray)
        gravity = Gravity.CENTER
        background = button3d(Color.WHITE, gray, stroke = gray)
        isClickable = true
        setOnClickListener { daysEdit.clearFocus(); Streak.setValue(this@MainActivity, next()); refresh() }
    }

    /** Сегментированный переключатель: кнопки в одной серой «таблетке». */
    private fun segmented(titles: List<String>, out: MutableList<TextView>, onPick: (Int) -> Unit): View {
        val wrap = LinearLayout(this).apply {
            background = GradientDrawable().apply { setColor(0xFFF0F0F0.toInt()); cornerRadius = dp(14).toFloat() }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        titles.forEachIndexed { i, t ->
            val c = TextView(this).apply {
                text = t
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setOnClickListener { clearFields(); onPick(i) }
            }
            out += c
            wrap.addView(c, LinearLayout.LayoutParams(0, dp(40), 1f))
        }
        return wrap
    }

    private fun select(cells: List<TextView>, idx: Int) = cells.forEachIndexed { i, c ->
        val on = i == idx
        c.background = if (on) GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(11).toFloat(); setStroke(dp(2), blue) } else null
        c.setTextColor(if (on) blue else textGray)
    }

    private fun box(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(2), stroke); cornerRadius = dp(14).toFloat()
    }

    private fun pressable(fill: Int, pressed: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply { setColor(pressed); cornerRadius = dp(12).toFloat() })
        addState(intArrayOf(), GradientDrawable().apply { setColor(fill); cornerRadius = dp(12).toFloat() })
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

/** Картинка, у которой высота всегда равна ширине. */
private class SquareImage(ctx: Context) : ImageView(ctx) {
    override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, w)
}
