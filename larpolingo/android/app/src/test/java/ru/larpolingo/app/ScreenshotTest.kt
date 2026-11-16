package ru.larpolingo.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    private fun save(v: View, name: String) {
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun shots() {
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        save(act.window.decorView, "main")
        Streak.setValue(act, 1437); Streak.setPerDay(act, 10)
        for (p in Streak.pics) {
            Streak.setPic(act, p)
            val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            save(a.findViewById(R.id.root), "w_${p.id}")
        }
        Streak.setValue(act, 123456)
        save(Robolectric.buildActivity(MainActivity::class.java).setup().get().window.decorView, "main2")
        // клавиатура: подставляем вставку IME высотой 300dp и фокусируем поле подписи
        Streak.setPic(act, Streak.pics[4])
        val k = Robolectric.buildActivity(MainActivity::class.java).setup().visible().get()
        val content = k.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val edits = mutableListOf<android.widget.EditText>()
        fun walk(v: View) { if (v is android.widget.EditText) edits += v; if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
        walk(content)
        val sub = edits.first { it.visibility == View.VISIBLE && it.inputType and android.text.InputType.TYPE_CLASS_TEXT != 0 && it.inputType != android.text.InputType.TYPE_CLASS_NUMBER }
        sub.requestFocus()
        val px = (300 * k.resources.displayMetrics.density).toInt()
        val ins = android.view.WindowInsets.Builder()
            .setInsets(android.view.WindowInsets.Type.ime(), android.graphics.Insets.of(0, 0, 0, px))
            .setVisible(android.view.WindowInsets.Type.ime(), true).build()
        content.dispatchApplyWindowInsets(ins)
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
        save(k.window.decorView, "ime")
        // виджет в вытянутой и в широкой ячейке: должен оставаться квадратным
        Streak.setPic(act, Streak.pics[6])
        for ((name, w, h) in listOf(Triple("tall", 180, 240), Triple("wide", 300, 170))) {
            val d = act.resources.displayMetrics.density
            val frame = android.widget.FrameLayout(act)
            val v = android.view.LayoutInflater.from(act).inflate(R.layout.widget, frame, false)
            frame.addView(v, android.widget.FrameLayout.LayoutParams((w * d).toInt(), (h * d).toInt()))
            frame.measure(View.MeasureSpec.makeMeasureSpec((w * d).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((h * d).toInt(), View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, (w * d).toInt(), (h * d).toInt())
            frame.setBackgroundColor(0xFF2A4A70.toInt())
            save(frame, "cell_$name")
        }
        // все значки огня на тёмном фоне
        val fl = Streak.Flame.entries.filter { it.res != 0 }
        val sheet = Bitmap.createBitmap(fl.size * 150, 150, Bitmap.Config.ARGB_8888)
        val cv = Canvas(sheet); cv.drawColor(0xFF3C2A63.toInt())
        fl.forEachIndexed { i, f -> act.getDrawable(f.res)!!.apply { setBounds(i * 150 + 10, 10, i * 150 + 140, 140); draw(cv) } }
        File(out, "flames.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val icon = act.getDrawable(R.mipmap.ic_launcher)!!
        val bmp = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 432, 432); icon.draw(Canvas(bmp))
        File(out, "icon.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
