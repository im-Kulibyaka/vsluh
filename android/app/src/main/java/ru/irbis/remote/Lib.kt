package ru.irbis.remote

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Библиотека ИК-пультов (Flipper-IRDB) с сайта: index.json со списком категорий и брендов,
 * затем файл бренда [{"m":модель,"b":[кнопки]}]. Кнопка либо «parsed» (протокол + адрес/команда —
 * разворачиваем в тайминги), либо «raw» (готовый массив мкс + частота — шлём как есть).
 * Байты кадра идут младшим битом вперёд; адрес/команда в hex — младший байт первым ("0700" → байт0=0x07).
 */
object Lib {
    const val BASE = "https://im-kulibyaka.github.io/vsluh/irbis/lib/"

    sealed class Button {
        abstract val name: String
        /** Развернуть в тайминги по протоколу. */
        class Parsed(override val name: String, val protocol: String, val a: String, val c: String) : Button()
        /** Готовый паттерн мкс (mark, space, …) и несущая. */
        class Raw(override val name: String, val raw: IntArray, val freq: Int) : Button()
    }

    class Model(val name: String, val buttons: List<Button>)

    /* ---------- сеть ---------- */

    /** Простой GET без кэширования; бросает исключение при ошибке. */
    fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10000; readTimeout = 15000
            useCaches = false; setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Скачать файл целиком (например, прошивку .bin). Бросает исключение при ошибке. */
    fun getBytes(url: String): ByteArray {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10000; readTimeout = 30000
            useCaches = false; setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    /* ---------- разбор JSON ---------- */

    /** index.json → категория → список брендов (порядок сохраняется). */
    fun parseIndex(json: String): LinkedHashMap<String, List<String>> {
        val out = LinkedHashMap<String, List<String>>()
        val cats = JSONObject(json).getJSONObject("cats")
        for (k in cats.keys()) {
            val arr = cats.getJSONArray(k)
            out[k] = (0 until arr.length()).map { arr.getString(it) }
        }
        return out
    }

    /** Файл бренда → список моделей с кнопками. */
    fun parseBrand(json: String): List<Model> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val bs = o.optJSONArray("b") ?: JSONArray()
            val buttons = (0 until bs.length()).mapNotNull { j -> button(bs.getJSONObject(j)) }
            Model(o.optString("m", "?"), buttons)
        }
    }

    private fun button(o: JSONObject): Button? {
        val n = o.optString("n", "?")
        return when {
            o.has("r") -> {
                val r = o.getJSONArray("r")
                Button.Raw(n, IntArray(r.length()) { r.getInt(it) }, o.optInt("f", 38000))
            }
            o.has("p") -> Button.Parsed(n, o.getString("p"), o.optString("a", "00"), o.optString("c", "00"))
            else -> null
        }
    }

    /* ---------- разворачивание protocol → паттерн ---------- */

    /** hex младших байт, младший байт первым: "0700" → [0x07, 0x00]. */
    private fun hexBytes(h: String): IntArray {
        val s = h.trim()
        if (s.isEmpty()) return intArrayOf(0)
        val n = (s.length + 1) / 2
        return IntArray(n) { i ->
            val from = i * 2
            s.substring(from, minOf(from + 2, s.length)).toInt(16) and 0xFF
        }
    }

    /**
     * Развернуть parsed-кнопку в (паттерн мкс, несущая Гц).
     * null — неизвестный протокол.
     */
    fun encode(protocol: String, aHex: String, cHex: String): Pair<IntArray, Int>? {
        val a = hexBytes(aHex); val c = hexBytes(cHex)
        val a0 = a.getOrElse(0) { 0 }; val a1 = a.getOrElse(1) { 0 }; val c0 = c.getOrElse(0) { 0 }
        return when (protocol.trim().uppercase().replace("_", "").replace("-", "")) {
            "NEC" -> pulseDistance(intArrayOf(a0, a0 xor 0xFF, c0, c0 xor 0xFF), 9000, 4500) to 38000
            "NECEXT" -> pulseDistance(intArrayOf(a0, a1, c0, c0 xor 0xFF), 9000, 4500) to 38000
            "SAMSUNG32", "SAMSUNG" -> pulseDistance(intArrayOf(a0, a0, c0, c0 xor 0xFF), 4500, 4500) to 38000
            "SIRC", "SIRC12", "SONY", "SONY12" -> sirc(c0, a0, 0, 5) to 40000
            "SIRC15", "SONY15" -> sirc(c0, a0, 0, 8) to 40000
            "SIRC20", "SONY20" -> sirc(c0, a0, a1, 5, hasExt = true) to 40000
            else -> null
        }
    }

    /**
     * NEC-подобный кадр («pulse distance»): заголовок, байты младшим битом вперёд
     * (метка 560, «1» — пауза 1690, «0» — пауза 560), хвост метка 560.
     */
    private fun pulseDistance(bytes: IntArray, headMark: Int, headSpace: Int): IntArray {
        val d = ArrayList<Int>(4 + bytes.size * 16)
        d += headMark; d += headSpace
        for (b in bytes) for (i in 0..7) {
            d += 560
            d += if ((b shr i) and 1 == 1) 1690 else 560
        }
        d += 560
        return d.toIntArray()
    }

    /**
     * Sony SIRC: заголовок метка 2400, пауза 600; биты младшим вперёд —
     * 7 бит команды, затем [addrBits] адреса, для SIRC20 ещё 8 бит ext.
     * Бит: метка 1200 («1») или 600 («0»), затем пауза 600. Хвоста нет.
     */
    private fun sirc(cmd: Int, addr: Int, extByte: Int, addrBits: Int, hasExt: Boolean = false): IntArray {
        val d = ArrayList<Int>()
        d += 2400; d += 600
        fun bits(value: Int, count: Int) {
            for (i in 0 until count) {
                d += if ((value shr i) and 1 == 1) 1200 else 600
                d += 600
            }
        }
        bits(cmd, 7)
        bits(addr, addrBits)
        if (hasExt) bits(extByte, 8)
        return d.toIntArray()
    }
}
