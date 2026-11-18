package ru.irbis.remote

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.ByteArrayOutputStream

/**
 * Прошивка ESP32-S3 / ESP32-C3 по USB-кабелю (OTG) прямо с Android — без компьютера.
 * Реализует последовательный протокол ROM-загрузчика Espressif поверх USB CDC-ACM
 * (у SuperMini-плат USB встроен в чип). Пишет полный образ (merged .bin) по адресу 0x0.
 *
 * Плату нужно перевести в режим прошивки: зажать BOOT, подключить к телефону (или нажать RST),
 * отпустить BOOT. Тогда она видна как «USB JTAG/serial» (VID 0x303A) и отвечает на SYNC.
 *
 * Внимание: бета. Если не удаётся — прошейте один раз с компьютера (ссылка в приложении),
 * дальше можно обновляться по Bluetooth (OTA).
 */
class EspFlash(private val usb: UsbManager, private val log: (String) -> Unit, private val progress: (Int, Int) -> Unit) {

    companion object {
        const val ESP_VID = 0x303A                 // Espressif
        private const val SLIP_END = 0xC0
        private const val SLIP_ESC = 0xDB
        private const val CK_MAGIC = 0xEF

        // команды ROM-загрузчика
        private const val FLASH_BEGIN = 0x02
        private const val FLASH_DATA = 0x03
        private const val FLASH_END = 0x04
        private const val SYNC = 0x08
        private const val SPI_ATTACH = 0x0D

        private const val BLOCK = 0x400            // размер блока записи (1 КБ)

        /** Подходящее USB-устройство (Espressif) среди подключённых. */
        fun find(usb: UsbManager): UsbDevice? =
            usb.deviceList.values.firstOrNull { it.vendorId == ESP_VID }
                ?: usb.deviceList.values.firstOrNull { d ->
                    (0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_CDC_DATA }
                }
    }

    private var conn: UsbDeviceConnection? = null
    private var iface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    @Volatile var cancelled = false

    /** Полный цикл: открыть, синхронизация, запись образа, перезагрузка. true — успех. */
    fun flash(device: UsbDevice, image: ByteArray): Boolean {
        try {
            if (!open(device)) { log("Не удалось открыть USB-устройство"); return false }
            setControlLines(dtr = false, rts = false)
            setLineCoding(115200)
            log("Перевод платы в режим прошивки…")
            resetToBootloader()
            if (!sync()) {
                log("Плата не ответила. Зажмите BOOT, нажмите RST (или переподключите), отпустите BOOT — и повторите.")
                return false
            }
            log("Связь есть. Подготовка флеш-памяти…")
            spiAttach()
            val img = trim(image)
            val blocks = (img.size + BLOCK - 1) / BLOCK
            if (!flashBegin(img.size, blocks)) { log("Ошибка FLASH_BEGIN"); return false }
            log("Запись ${img.size / 1024} КБ (${blocks} блоков)…")
            var seq = 0; var off = 0
            while (off < img.size) {
                if (cancelled) { log("Отменено"); return false }
                val n = minOf(BLOCK, img.size - off)
                val block = ByteArray(BLOCK) { 0xFF.toByte() }
                System.arraycopy(img, off, block, 0, n)
                if (!flashData(block, seq)) { log("Ошибка записи блока $seq"); return false }
                seq++; off += n
                progress(off, img.size)
            }
            flashEnd()
            log("Готово. Плата перезагружается.")
            return true
        } catch (e: Exception) {
            log("Сбой: ${e.message}")
            return false
        } finally {
            close()
        }
    }

    /* ---------- USB ---------- */

    private fun open(d: UsbDevice): Boolean {
        val c = usb.openDevice(d) ?: return false
        conn = c
        // ищем CDC-ACM data-интерфейс с bulk IN/OUT
        for (i in 0 until d.interfaceCount) {
            val itf = d.getInterface(i)
            var tin: UsbEndpoint? = null; var tout: UsbEndpoint? = null
            for (e in 0 until itf.endpointCount) {
                val ep = itf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) tin = ep else tout = ep
                }
            }
            if (tin != null && tout != null) {
                if (c.claimInterface(itf, true)) { iface = itf; epIn = tin; epOut = tout; return true }
            }
        }
        // запасной вариант: первый интерфейс
        return false
    }

    private fun close() {
        runCatching { iface?.let { conn?.releaseInterface(it) } }
        runCatching { conn?.close() }
        conn = null
    }

    /** CDC SET_CONTROL_LINE_STATE: биты DTR(0), RTS(1). */
    private fun setControlLines(dtr: Boolean, rts: Boolean) {
        val v = (if (dtr) 1 else 0) or (if (rts) 2 else 0)
        val itf = iface?.id ?: 0
        conn?.controlTransfer(0x21, 0x22, v, itf, null, 0, 200)
    }

    /** CDC SET_LINE_CODING: скорость (для встроенного USB не важна, но ставим). */
    private fun setLineCoding(baud: Int) {
        val itf = iface?.id ?: 0
        val d = byteArrayOf(
            (baud and 0xFF).toByte(), ((baud shr 8) and 0xFF).toByte(),
            ((baud shr 16) and 0xFF).toByte(), ((baud shr 24) and 0xFF).toByte(),
            0, 0, 8)   // 1 стоп-бит, нет чётности, 8 бит
        conn?.controlTransfer(0x21, 0x20, 0, itf, d, d.size, 200)
    }

    /** Сброс во встроенный загрузчик (USB-Serial/JTAG) через DTR/RTS. */
    private fun resetToBootloader() {
        setControlLines(dtr = false, rts = false); sleep(100)
        setControlLines(dtr = true, rts = false); sleep(100)   // IO0 = 0 (BOOT)
        setControlLines(dtr = true, rts = true); sleep(100)    // сброс
        setControlLines(dtr = false, rts = true); sleep(100)
        setControlLines(dtr = false, rts = false); sleep(400)
    }

    /* ---------- SLIP ---------- */

    private fun slipWrite(payload: ByteArray) {
        val out = ByteArrayOutputStream(payload.size + 8)
        out.write(SLIP_END)
        for (b in payload) {
            val x = b.toInt() and 0xFF
            when (x) {
                SLIP_END -> { out.write(SLIP_ESC); out.write(0xDC) }
                SLIP_ESC -> { out.write(SLIP_ESC); out.write(0xDD) }
                else -> out.write(x)
            }
        }
        out.write(SLIP_END)
        val bytes = out.toByteArray()
        epOut?.let { conn?.bulkTransfer(it, bytes, bytes.size, 2000) }
    }

    /** Прочитать один SLIP-кадр (без обрамляющих 0xC0). null — таймаут. */
    private fun slipRead(timeoutMs: Int): ByteArray? {
        val buf = ByteArray(256)
        val acc = ByteArrayOutputStream()
        var started = false; var esc = false
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val ep = epIn ?: return null
            val n = conn?.bulkTransfer(ep, buf, buf.size, 200) ?: -1
            if (n <= 0) continue
            for (i in 0 until n) {
                val x = buf[i].toInt() and 0xFF
                if (!started) { if (x == SLIP_END) started = true; continue }
                when {
                    esc -> { acc.write(if (x == 0xDC) SLIP_END else SLIP_ESC); esc = false }
                    x == SLIP_ESC -> esc = true
                    x == SLIP_END -> { if (acc.size() > 0) return acc.toByteArray() }   // конец кадра
                    else -> acc.write(x)
                }
            }
        }
        return null
    }

    /* ---------- команды ---------- */

    private fun command(op: Int, data: ByteArray, chk: Int = 0, timeoutMs: Int = 3000): ByteArray? {
        val h = ByteArrayOutputStream()
        h.write(0x00); h.write(op)
        h.write(data.size and 0xFF); h.write((data.size shr 8) and 0xFF)
        h.write(chk and 0xFF); h.write((chk shr 8) and 0xFF); h.write((chk shr 16) and 0xFF); h.write((chk shr 24) and 0xFF)
        h.write(data)
        slipWrite(h.toByteArray())
        // ответ: [0x01, op, size(2), value(4), data..., status]
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val r = slipRead((end - System.currentTimeMillis()).toInt().coerceAtLeast(50)) ?: continue
            if (r.size >= 8 && (r[0].toInt() and 0xFF) == 0x01 && (r[1].toInt() and 0xFF) == op) return r
        }
        return null
    }

    /** Статус в конце ответа: 0 — успех (у S3/C3 статус 4 байта). */
    private fun ok(resp: ByteArray?): Boolean {
        if (resp == null || resp.size < 10) return false
        val status = resp[resp.size - 4].toInt() and 0xFF   // первый байт статуса
        return status == 0
    }

    private fun checksum(data: ByteArray): Int {
        var c = CK_MAGIC
        for (b in data) c = c xor (b.toInt() and 0xFF)
        return c
    }

    private fun sync(): Boolean {
        val d = ByteArrayOutputStream()
        d.write(0x07); d.write(0x07); d.write(0x12); d.write(0x20)
        repeat(32) { d.write(0x55) }
        val payload = d.toByteArray()
        for (attempt in 0 until 10) {
            if (cancelled) return false
            val r = command(SYNC, payload, timeoutMs = 500)
            if (r != null) { drain(); return true }
            sleep(100)
        }
        return false
    }

    /** Проглотить лишние ответы после SYNC. */
    private fun drain() { repeat(6) { slipRead(60) } }

    private fun spiAttach() {
        command(SPI_ATTACH, ByteArray(8), timeoutMs = 1500)   // 4 байта аргумент + 4 нуля
    }

    private fun flashBegin(size: Int, blocks: Int): Boolean {
        val d = le32(size) + le32(blocks) + le32(BLOCK) + le32(0) + le32(0)   // последнее слово — шифрование=0
        return ok(command(FLASH_BEGIN, d, timeoutMs = 10000))
    }

    private fun flashData(block: ByteArray, seq: Int): Boolean {
        val d = le32(block.size) + le32(seq) + le32(0) + le32(0) + block
        return ok(command(FLASH_DATA, d, chk = checksum(block), timeoutMs = 3000))
    }

    private fun flashEnd() {
        command(FLASH_END, le32(0), timeoutMs = 1000)   // 0 — перезагрузиться и запустить прошивку
    }

    /* ---------- мелочи ---------- */

    private fun le32(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())
    private fun sleep(ms: Long) { try { Thread.sleep(ms) } catch (_: InterruptedException) {} }

    /** Убрать хвост из 0xFF (не пишем пустые сектора), выровнять по 4 КБ. */
    private fun trim(img: ByteArray): ByteArray {
        var end = img.size
        while (end > 0 && img[end - 1] == 0xFF.toByte()) end--
        end = ((end + 0xFFF) / 0x1000) * 0x1000
        if (end < 0x1000) end = minOf(img.size, 0x1000)
        if (end > img.size) end = img.size
        return if (end == img.size) img else img.copyOfRange(0, end)
    }
}
