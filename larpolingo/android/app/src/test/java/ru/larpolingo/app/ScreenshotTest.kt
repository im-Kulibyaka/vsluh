package ru.larpolingo.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertEquals
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
    fun labels() {
        assertEquals("день ударного режима", Streak.label(1))
        assertEquals("дня ударного режима", Streak.label(23))
        assertEquals("дней ударного режима", Streak.label(12))
        assertEquals("дней ударного режима", Streak.label(365))
    }

    @Test
    fun shots() {
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        save(act.window.decorView, "main")
        Streak.setValue(act, 365); Streak.setPerDay(act, 10)
        Streak.setBg(act, Streak.bgs[2]); Streak.setPic(act, Streak.pics[1])
        val a2 = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        save(a2.window.decorView, "main2")
        // иконка приложения
        val icon = a2.getDrawable(R.mipmap.ic_launcher)!!
        val bmp = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 432, 432); icon.draw(Canvas(bmp))
        File(out, "icon.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
