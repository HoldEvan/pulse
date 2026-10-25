package com.evan.pulse.ui.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.domain.tools.QrCodec
import com.evan.pulse.domain.tools.QrDecodeResult
import com.evan.pulse.domain.tools.QrErrorCorrection
import com.evan.pulse.ui.common.AppToast
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.saveImageBytes
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.common.toPngBytes
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDropdown
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.l10n.localizedLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 识别时的最长边上限：超过则先按 2 的幂降采样，避免大图直接 OOM */
private const val MAX_DECODE_EDGE = 2000

/** 生成预览的像素边长上限，与屏幕宽度无关，交给 Image 缩放 */
private const val PREVIEW_EDGE = 720

/** 预览四周留出的模块数，取偶数以便代码里按两倍换算 */
private const val PREVIEW_MARGIN = 4

private val correctionLabels = listOf(
    R.string.qr_correction_low,
    R.string.qr_correction_medium,
    R.string.qr_correction_quartile,
    R.string.qr_correction_high
)

/**
 * 二维码工具二级页：生成与识别共用一页。
 *
 * 生成侧只接受文本，点阵由 zxing 编码后按白底黑码渲染；识别侧复用图片选择器，
 * 解码在 IO 线程完成，主线程只负责展示结果。
 */
@Composable
fun QrCodeScreen(modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        AppTabRow(
            tabs = listOf(
                stringResource(R.string.qr_tab_generate),
                stringResource(R.string.qr_tab_scan)
            ),
            selectedTabIndex = tab,
            onTabSelected = { tab = it },
            modifier = Modifier.fillMaxWidth()
        )
        if (tab == 0) GeneratePane() else ScanPane()
    }
}

@Composable
private fun GeneratePane() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var correction by remember { mutableStateOf(QrErrorCorrection.MEDIUM) }

    val encoded = remember(input, correction) { QrCodec.encode(input, correction) }
    val bitmap = remember(encoded.matrix) {
        encoded.matrix?.let { matrix ->
            val scale = (PREVIEW_EDGE / (matrix.size + PREVIEW_MARGIN)).coerceAtLeast(1)
            val side = (matrix.size + PREVIEW_MARGIN) * scale
            val pixels = matrix.toPixels(scale = scale, margin = PREVIEW_MARGIN / 2)
            Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
        }
    }

    AppTextField(
        value = input,
        onValueChange = { input = it },
        label = stringResource(R.string.qr_input_label),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        minLines = 3
    )

    AppDropdown(
        title = stringResource(R.string.qr_correction_label),
        items = correctionLabels.localizedLabels(),
        selectedIndex = correction.ordinal,
        onSelectedIndexChange = { correction = QrErrorCorrection.entries[it] },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )

    when {
        encoded.error != null -> Hint(qrErrorText(encoded.error))
        bitmap == null -> Hint(stringResource(R.string.qr_generate_empty_hint))
        else -> {
            AppCard(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.qr_preview_description),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .background(Color.White),
                    contentScale = ContentScale.Fit
                )
            }
            AppText(
                text = stringResource(R.string.qr_matrix_size, encoded.matrix!!.size),
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 12.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
            AppTextButton(
                text = stringResource(R.string.qr_save_image),
                onClick = {
                    val bytes = bitmap.toPngBytes()
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { saveImageBytes(context, bytes) }
                        AppToast.show(
                            context.getString(
                                if (ok) R.string.qr_save_success else R.string.qr_save_failed
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun ScanPane() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<QrDecodeResult?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                if (bytes == null) {
                    null to QrDecodeResult(error = "invalid")
                } else {
                    val bitmap = decodeScaledBitmap(bytes)
                    bitmap to (bitmap?.let { decodeBitmap(it) }
                        ?: QrDecodeResult(error = "invalid"))
                }
            }
            preview = decoded.first
            result = decoded.second
            loading = false
        }
    }

    AppTextButton(
        text = stringResource(R.string.qr_pick_image),
        onClick = {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        enabled = !loading
    )

    preview?.let { bitmap ->
        AppCard(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
                    .padding(8.dp),
                contentScale = ContentScale.Fit
            )
        }
    }

    when {
        loading -> Hint(stringResource(R.string.qr_scanning))
        result == null -> Hint(stringResource(R.string.qr_scan_empty_hint))
        result!!.error != null -> Hint(qrErrorText(result!!.error))
        else -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardSectionTitle(text = stringResource(R.string.qr_scan_result_title))
                CopyIconButton(
                    text = result!!.text,
                    label = stringResource(R.string.action_copy)
                )
            }
            if (result!!.format.isNotEmpty() && result!!.format != "QR_CODE") {
                AppText(
                    text = stringResource(R.string.qr_scan_other_format, result!!.format),
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 12.sp,
                    color = AppTheme.colors.onSurfaceSummary
                )
            }
            MonospaceBlock(text = result!!.text, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** 按最长边降采样解码，避免超大图片解码阶段就耗尽内存 */
private fun decodeScaledBitmap(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (
        bounds.outWidth / sample > MAX_DECODE_EDGE || bounds.outHeight / sample > MAX_DECODE_EDGE
    ) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
}

private fun decodeBitmap(bitmap: Bitmap): QrDecodeResult {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    return QrCodec.decode(pixels, width, height)
}

@Composable
private fun Hint(text: String) {
    AppText(
        text = text,
        modifier = Modifier.padding(vertical = 6.dp),
        fontSize = 13.sp,
        color = AppTheme.colors.onSurfaceSummary
    )
}

@Composable
private fun qrErrorText(error: String?): String = stringResource(
    when (error) {
        "empty" -> R.string.qr_error_empty
        "tooLong" -> R.string.qr_error_too_long
        "notFound" -> R.string.qr_error_not_found
        "invalid" -> R.string.qr_error_invalid_image
        else -> R.string.qr_error_failed
    }
)
