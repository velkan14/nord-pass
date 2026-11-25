package dev.damado.nordpass.data.repository

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.damado.nordpass.data.network.MiclubApi
import dev.damado.nordpass.data.settings.QrMode
import dev.damado.nordpass.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.EnumMap
import kotlin.math.ceil

sealed interface QrRenderResult {
    data class Success(val bitmap: Bitmap) : QrRenderResult
    data class Failure(val message: String) : QrRenderResult
}

class CarnetRepository(
    private val api: MiclubApi,
    private val settings: SettingsStore,
) {
    suspend fun render(tokenUrl: String): QrRenderResult = withContext(Dispatchers.IO) {
        runCatching {
            when (settings.qrMode.first()) {
                QrMode.SERVER -> {
                    val bytes = api.fetchQrPng(tokenUrl)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: error("Could not decode QR PNG")
                    QrRenderResult.Success(bitmap)
                }
                QrMode.LOCAL -> QrRenderResult.Success(generateLocally(tokenUrl))
            }
        }.getOrElse { QrRenderResult.Failure(it.message ?: "Could not render QR") }
    }

    private fun generateLocally(tokenUrl: String): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 0)
        }
        val matrix = QRCodeWriter().encode(tokenUrl, BarcodeFormat.QR_CODE, 1, 1, hints)
        val modules = matrix.width
        val quietZoneModules = 4
        val totalModules = modules + quietZoneModules * 2

        // The UI draws this at 50% of the screen width. Render at the device's
        // physical pixel density so the QR stays crisp instead of being upscaled.
        val targetPx = (Resources.getSystem().displayMetrics.widthPixels * 0.5f)
            .toInt()
            .coerceAtMost(2048)
        val scale = ceil(targetPx.toFloat() / totalModules).toInt().coerceAtLeast(1)
        val size = totalModules * scale

        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val moduleX = x / scale - quietZoneModules
                    val moduleY = y / scale - quietZoneModules
                    val dark = moduleX in 0 until modules &&
                        moduleY in 0 until modules &&
                        matrix[moduleX, moduleY]
                    setPixel(x, y, if (dark) Color.BLACK else Color.WHITE)
                }
            }
        }
    }

    @Suppress("unused")
    private fun Bitmap.toPngBytes(): ByteArray = ByteArrayOutputStream().use { stream ->
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    }
}
