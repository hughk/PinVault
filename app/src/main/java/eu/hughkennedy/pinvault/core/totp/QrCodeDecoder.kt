package eu.hughkennedy.pinvault.core.totp

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

/**
 * Decodes QR codes from photographed Bitmaps and real-time CameraX ImageProxy frames.
 * Uses multi-pass binarization (Hybrid, Global Histogram, and Inversion) for maximum
 * resilience against screen glare, angles, and dark themes.
 */
object QrCodeDecoder {

    private val reader = MultiFormatReader()
    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
    )

    /**
     * Decodes a QR code from a captured or selected [Bitmap].
     */
    fun decodeBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)

        // Pass 1: Standard Hybrid Binarizer
        try {
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(binaryBitmap)
            return result.text
        } catch (_: Exception) {
            reader.reset()
        }

        // Pass 2: Global Histogram Binarizer (for photographed computer screens / glare)
        try {
            val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
            val result = reader.decodeWithState(binaryBitmap)
            return result.text
        } catch (_: Exception) {
            reader.reset()
        }

        // Pass 3: Inverted luminance (for dark-mode QR codes)
        try {
            val invertedSource = source.invert()
            val binaryBitmap = BinaryBitmap(HybridBinarizer(invertedSource))
            val result = reader.decodeWithState(binaryBitmap)
            return result.text
        } catch (_: Exception) {
            reader.reset()
        }

        return null
    }

    /**
     * Decodes a QR code from a CameraX [ImageProxy] frame without full Bitmap allocation.
     */
    fun decodeImageProxy(image: ImageProxy): String? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        val width = image.width
        val height = image.height

        val source = PlanarYUVLuminanceSource(
            data, width, height,
            0, 0, width, height, false
        )

        // Try standard Hybrid
        try {
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(binaryBitmap)
            return result.text
        } catch (_: Exception) {
            reader.reset()
        }

        // Try Global Histogram fallback
        try {
            val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
            val result = reader.decodeWithState(binaryBitmap)
            return result.text
        } catch (_: Exception) {
            reader.reset()
        }

        return null
    }
}
