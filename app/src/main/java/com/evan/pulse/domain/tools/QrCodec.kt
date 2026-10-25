package com.evan.pulse.domain.tools

import com.google.zxing.BarcodeFormat
import com.google.zxing.Binarizer
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** 二维码纠错级别；越高越抗污损，可容纳内容越少 */
enum class QrErrorCorrection(val label: String) {
    LOW("L"),
    MEDIUM("M"),
    QUARTILE("Q"),
    HIGH("H");

    internal fun level(): ErrorCorrectionLevel = when (this) {
        LOW -> ErrorCorrectionLevel.L
        MEDIUM -> ErrorCorrectionLevel.M
        QUARTILE -> ErrorCorrectionLevel.Q
        HIGH -> ErrorCorrectionLevel.H
    }
}

/**
 * 二维码点阵：每格一位，按行优先存放。
 *
 * 与 zxing 的 BitMatrix 解耦，界面只依赖这个结构，单测也能直接构造点阵做往返验证。
 */
class QrMatrix(val size: Int, private val bits: BooleanArray) {

    init {
        require(bits.size == size * size) { "点阵尺寸与数据长度不匹配" }
    }

    fun isDark(x: Int, y: Int): Boolean = bits[y * size + x]

    /**
     * 展开为 ARGB 像素：每格放大 [scale] 倍，四周留 [margin] 格白边。
     *
     * 白边是识别的前提，二维码紧贴边界时多数扫码器会直接失败，因此不提供 0 边距选项。
     */
    fun toPixels(
        scale: Int = 8,
        margin: Int = 2,
        dark: Int = 0xFF000000.toInt(),
        light: Int = 0xFFFFFFFF.toInt(),
    ): IntArray {
        val side = (size + margin * 2) * scale
        val pixels = IntArray(side * side) { light }
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!isDark(x, y)) continue
                val startX = (x + margin) * scale
                val startY = (y + margin) * scale
                for (dy in 0 until scale) {
                    val rowOffset = (startY + dy) * side + startX
                    java.util.Arrays.fill(pixels, rowOffset, rowOffset + scale, dark)
                }
            }
        }
        return pixels
    }
}

/** 生成结果；[error] 为语义编码（empty / tooLong），非空时 [matrix] 为 null */
data class QrEncodeResult(val matrix: QrMatrix? = null, val error: String? = null)

/** 识别结果；[format] 是条码类型名，便于提示用户扫到的其实是别的码 */
data class QrDecodeResult(
    val text: String = "",
    val format: String = "",
    val error: String? = null,
)

object QrCodec {

    private val BINARIZERS: List<(LuminanceSource) -> Binarizer> = listOf(
        { source -> HybridBinarizer(source) },
        { source -> GlobalHistogramBinarizer(source) },
    )

    /** 单个二维码的容量上限（字节模式，版本 40-L 约 2953 字节），超出时交给 zxing 报错 */
    const val MAX_INPUT_BYTES = 2953

    private fun isLatin1Encodable(text: String): Boolean =
        runCatching { Charsets.ISO_8859_1.newEncoder().canEncode(text) }.getOrDefault(false)

    fun encode(text: String, correction: QrErrorCorrection = QrErrorCorrection.MEDIUM): QrEncodeResult {
        if (text.isEmpty()) return QrEncodeResult(error = "empty")
        if (text.toByteArray(Charsets.UTF_8).size > MAX_INPUT_BYTES) {
            return QrEncodeResult(error = "tooLong")
        }
        // 只有内容超出 ISO-8859-1 时才显式要求 UTF-8。zxing 默认按 ISO-8859-1 编码，
        // 中文会被替换成问号；而对纯 ASCII 提前指定 UTF-8 又会多写一个 12 位的 ECI 头，
        // 白白把版本顶高一级
        val hints = buildMap {
            put(EncodeHintType.ERROR_CORRECTION, correction.level())
            put(EncodeHintType.MARGIN, 0)
            if (!isLatin1Encodable(text)) put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        val code = try {
            Encoder.encode(text, correction.level(), hints)
        } catch (e: Exception) {
            return QrEncodeResult(error = "tooLong")
        }
        val matrix = code.matrix
        val bits = BooleanArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                bits[y * matrix.width + x] = matrix.get(x, y) == 1.toByte()
            }
        }
        return QrEncodeResult(matrix = QrMatrix(matrix.width, bits))
    }

    /**
     * 从 ARGB 像素识别二维码。
     *
     * 只做一次常规尝试，不尝试纯反色或旋转：这两种情况在图片工具里更常见的原因是
     * 用户选错了图，直接提示「未识别到二维码」比长时间重试更有用。
     */
    fun decode(pixels: IntArray, width: Int, height: Int): QrDecodeResult {
        if (width <= 0 || height <= 0 || pixels.size != width * height) {
            return QrDecodeResult(error = "invalid")
        }
        val hints = mapOf(
            DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
        )
        // 依次尝试：局部阈值、全局直方图、以及各自的纯反色版本。
        // 截图里的二维码常带深色背景或反色，一次失败就放弃会让识别率明显偏低。
        val inverted = IntArray(pixels.size) { index ->
            val pixel = pixels[index]
            val luminance = ((pixel shr 16 and 0xFF) * 299 + (pixel shr 8 and 0xFF) * 587 +
                (pixel and 0xFF) * 114) / 1000
            if (luminance < 128) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }
        var sawNotFound = false
        for (variant in listOf(pixels, inverted)) {
            for (binarizer in BINARIZERS) {
                try {
                    val source = RGBLuminanceSource(width, height, variant)
                    val bitmap = BinaryBitmap(binarizer(source))
                    val result = QRCodeReader().decode(bitmap, hints)
                    return QrDecodeResult(text = result.text, format = result.barcodeFormat.name)
                } catch (e: NotFoundException) {
                    sawNotFound = true
                } catch (e: Exception) {
                    // 其它异常（格式错误、校验失败）继续尝试下一个组合
                }
            }
        }
        return QrDecodeResult(error = if (sawNotFound) "notFound" else "failed")
    }
}
