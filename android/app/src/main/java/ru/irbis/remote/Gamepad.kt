package ru.irbis.remote

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Аналоговый стик: палец тянет «шайбу», отпустил — вернулась в центр.
 * Отдаёт нормаль x,y в [-1..1] (y вниз — положительный). Мёртвую зону применяет вызывающий.
 */
class JoystickView(c: Context, private val onMove: (Float, Float) -> Unit) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f * d }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG)
    private var nx = 0f; private var ny = 0f
    private var active = false

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = min(cx, cy) - 2 * d
        bg.color = if (active) Term.FAINT else Term.BG; bg.alpha = 150
        cv.drawCircle(cx, cy, r, bg)
        ring.color = if (active) Term.FG else Term.LINE
        cv.drawCircle(cx, cy, r, ring)
        grid.color = Term.DIM; grid.alpha = 90; grid.strokeWidth = d; grid.style = Paint.Style.STROKE
        cv.drawLine(cx - r, cy, cx + r, cy, grid); cv.drawLine(cx, cy - r, cx, cy + r, grid)
        val kr = r * 0.42f
        knob.color = if (active) Term.FG else Term.shade(Term.FG, 0.6f)
        cv.drawCircle(cx + nx * (r - kr), cy + ny * (r - kr), kr, knob)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        val cx = width / 2f; val cy = height / 2f
        val r = min(cx, cy) - 2 * d
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { active = true; performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
            active = false; nx = 0f; ny = 0f; onMove(0f, 0f); invalidate(); return true
        }
        var dx = (e.x - cx) / r; var dy = (e.y - cy) / r
        val m = hypot(dx, dy)
        if (m > 1f) { dx /= m; dy /= m }
        nx = dx; ny = dy
        onMove(nx, ny); invalidate()
        return true
    }

    override fun onMeasure(w: Int, h: Int) {
        val s = MeasureSpec.getSize(w)
        setMeasuredDimension(s, s)
    }
}

/**
 * Крестовина (D-pad): 8 направлений. Отдаёт hat 0 центр / 1 вверх … 8 влево-вверх (как у геймпада).
 */
class DpadView(c: Context, private val onHat: (Int) -> Unit) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val arm = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f * d }
    private var hat = 0

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = min(cx, cy) - 2 * d
        val t = r * 0.42f   // полуширина плеча
        arm.color = Term.BG; arm.alpha = 150
        val box = RectF()
        // вертикальное и горизонтальное плечо
        box.set(cx - t, cy - r, cx + t, cy + r); cv.drawRoundRect(box, 8 * d, 8 * d, arm)
        box.set(cx - r, cy - t, cx + r, cy + t); cv.drawRoundRect(box, 8 * d, 8 * d, arm)
        line.color = Term.LINE
        box.set(cx - t, cy - r, cx + t, cy + r); cv.drawRoundRect(box, 8 * d, 8 * d, line)
        box.set(cx - r, cy - t, cx + r, cy + t); cv.drawRoundRect(box, 8 * d, 8 * d, line)
        // подсветка активного направления
        if (hat != 0) {
            arm.color = Term.FG; arm.alpha = 230
            val ang = (hat - 1) * 45.0 * Math.PI / 180.0   // 0 = вверх
            val px = cx + (Math.sin(ang) * (r - t)).toFloat()
            val py = cy - (Math.cos(ang) * (r - t)).toFloat()
            cv.drawCircle(px, py, t * 0.8f, arm)
        }
        line.color = Term.DIM; line.style = Paint.Style.FILL
        cv.drawCircle(cx, cy, 2.2f * d, line); line.style = Paint.Style.STROKE
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        when (e.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { set(0) }
            else -> {
                val cx = width / 2f; val cy = height / 2f; val r = min(cx, cy)
                val dx = e.x - cx; val dy = e.y - cy
                val m = hypot(dx, dy)
                if (m < r * 0.28f) set(0)
                else {
                    var a = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))   // 0 вверх, по часовой
                    if (a < 0) a += 360.0
                    set(((a + 22.5) / 45.0).toInt() % 8 + 1)
                }
            }
        }
        return true
    }

    private fun set(h: Int) {
        if (h != hat) { hat = h; onHat(h); if (h != 0) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); invalidate() }
    }

    override fun onMeasure(w: Int, h: Int) {
        val s = MeasureSpec.getSize(w)
        setMeasuredDimension(s, s)
    }
}
