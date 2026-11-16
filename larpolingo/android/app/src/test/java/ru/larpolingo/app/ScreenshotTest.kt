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
        val icon = act.getDrawable(R.mipmap.ic_launcher)!!
        val bmp = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 432, 432); icon.draw(Canvas(bmp))
        File(out, "icon.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
