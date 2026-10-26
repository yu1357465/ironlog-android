package io.github.yu1357465.ironlog.ui.plans

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.max

object QrCodes {

    /** 生成黑白二维码；内容超出二维码容量时返回 null。 */
    fun encode(content: String, sizePx: Int): Bitmap? {
        val matrix = try {
            QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, sizePx, sizePx,
                mapOf(
                    // 分享码本身有校验，纠错用 M 级即可，码点更大更好扫
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to 1,
                ),
            )
        } catch (e: Exception) {
            return null
        }
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h) { i -> if (matrix[i % w, i / w]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /** 分享用的图片：二维码 + 计划名 + 提示文字，白底，发到聊天里也能看懂。 */
    fun poster(qr: Bitmap, title: String, subtitle: String): Bitmap {
        val width = 1080
        val pad = 72
        val qrSize = width - pad * 2
        val height = pad + qrSize + 300
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(Bitmap.createScaledBitmap(qr, qrSize, qrSize, false), pad.toFloat(), pad.toFloat(), null)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 32, 36); textSize = 60f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(104, 113, 123); textSize = 38f; textAlign = Paint.Align.CENTER
        }
        val maxWidth = (width - pad * 2).toFloat()
        val t = TextUtils.ellipsize(title, titlePaint, maxWidth, TextUtils.TruncateAt.END).toString()
        val s = TextUtils.ellipsize(subtitle, subPaint, maxWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(t, width / 2f, pad + qrSize + 110f, titlePaint)
        canvas.drawText(s, width / 2f, pad + qrSize + 180f, subPaint)
        canvas.drawText("用 IronLog App 扫码导入", width / 2f, pad + qrSize + 240f, subPaint)
        return bmp
    }

    /** 从相册图片中识别二维码文字；识别不到返回 null。在后台线程调用。 */
    fun decodeFromImage(context: Context, uri: Uri): String? {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // 大照片先缩小，识别更快也更稳
            val longest = max(info.size.width, info.size.height)
            if (longest > 1600) {
                val scale = 1600f / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return decode(bitmap)
    }

    private fun decode(bitmap: Bitmap): String? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val source = RGBLuminanceSource(w, h, pixels)
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        )
        for (binarizer in listOf(HybridBinarizer(source), GlobalHistogramBinarizer(source))) {
            try {
                return QRCodeReader().decode(BinaryBitmap(binarizer), hints).text
            } catch (_: NotFoundException) {
            } catch (_: Exception) {
            }
        }
        return null
    }
}
