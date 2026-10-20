package com.evan.pulse.ui.tools

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.evan.pulse.R
import com.evan.pulse.domain.base64DecodeBytes
import com.evan.pulse.domain.base64EncodeBytes
import com.evan.pulse.ui.common.AppToast
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.CopyIconButton
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.MonospaceBlock
import com.evan.pulse.ui.common.rememberCopyToClipboard
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppTabRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTextField
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 图片工具顶层的方向切换 */
private enum class ImageTool { ENCODE, DECODE }

/** Base64 超长时展示截断的阈值，复制仍走完整内容 */
private const val MAX_BASE64_DISPLAY_LENGTH = 100_000

/** 预览卡片的最小高度，避免小图在卡片里缩得难以辨认 */
private val IMAGE_PREVIEW_MIN_HEIGHT = 120.dp

/** 图片与 Base64 互转二级页：编码侧相册选图转 Base64，解码侧还原为图片，支持放大查看与保存 */
@Composable
fun ImageBase64Screen(modifier: Modifier = Modifier) {
    var tool by rememberSaveable { mutableStateOf(ImageTool.ENCODE) }
    var decodeInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        val toolLabels = listOf(
            stringResource(R.string.base64_encode),
            stringResource(R.string.base64_decode)
        )
        AppTabRow(
            tabs = toolLabels,
            selectedTabIndex = tool.ordinal,
            onTabSelected = { tool = ImageTool.entries[it] },
            modifier = Modifier.fillMaxWidth()
        )

        if (tool == ImageTool.ENCODE) {
            ImageEncodeSection()
        } else {
            ImageDecodeSection(
                input = decodeInput,
                onInputChange = { decodeInput = it }
            )
        }
    }
}

/** 编码：相册选图后展示预览与 Base64 文本 */
@Composable
private fun ImageEncodeSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copy = rememberCopyToClipboard()

    // 选中图片的原始字节与对应 Base64：体积大，只放在 remember，避免进 savedState
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    val pickedBitmap = remember(imageBytes) {
        imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    val imageBase64 = remember(imageBytes) { imageBytes?.let { base64EncodeBytes(it) } }
    val imageBase64Display = imageBase64?.let {
        if (it.length > MAX_BASE64_DISPLAY_LENGTH) it.take(MAX_BASE64_DISPLAY_LENGTH) + "…" else it
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            imageBytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
        }
    }

    AppTextButton(
        text = stringResource(R.string.image_pick),
        onClick = {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    )
    pickedBitmap?.let { bitmap ->
        AppCard(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(min = IMAGE_PREVIEW_MIN_HEIGHT, max = 200.dp).padding(8.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardSectionTitle(text = stringResource(R.string.base64_output_label))
        CopyIconButton(
            text = imageBase64 ?: "",
            label = stringResource(R.string.action_copy)
        )
    }
    if (imageBase64 != null) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            MonospaceBlock(text = imageBase64Display ?: "", modifier = Modifier.padding(8.dp))
        }
    } else {
        AppText(
            text = stringResource(R.string.base64_empty_hint),
            modifier = Modifier.padding(vertical = 4.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
    }
}

/** 解码：粘贴 Base64 还原图片，点击预览可放大查看，支持保存到相册 */
@Composable
private fun ImageDecodeSection(
    input: String,
    onInputChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.image_saved)
    val failedText = stringResource(R.string.image_save_failed)
    var viewerBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val decodedBytes = remember(input) {
        if (input.isBlank()) null else base64DecodeBytes(input)
    }
    val decodedBitmap = remember(decodedBytes) {
        decodedBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    val saveImage: () -> Unit = {
        val bytes = decodedBytes
        if (bytes != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { saveImageBytes(context, bytes) }
                AppToast.show(if (ok) savedText else failedText)
            }
        }
    }

    AppTextField(
        value = input,
        onValueChange = onInputChange,
        label = stringResource(R.string.base64_input_label),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        minLines = 3
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardSectionTitle(text = stringResource(R.string.base64_output_label))
    }
    when {
        input.isBlank() -> {
            AppText(
                text = stringResource(R.string.base64_empty_hint),
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.onSurfaceSummary
            )
        }

        decodedBitmap == null -> {
            AppText(
                text = stringResource(R.string.image_invalid),
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.error
            )
        }

        else -> {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Image(
                    bitmap = decodedBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = IMAGE_PREVIEW_MIN_HEIGHT, max = 200.dp)
                        .clickable { viewerBitmap = decodedBitmap }
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }
            AppTextButton(
                text = stringResource(R.string.image_save),
                onClick = saveImage,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
    }

    viewerBitmap?.let { bitmap ->
        ImageViewerDialog(
            bitmap = bitmap,
            onSaveClick = saveImage,
            onDismiss = { viewerBitmap = null }
        )
    }
}

/** 全屏查看器：黑底展示，支持捏合缩放、双击切换缩放与拖动平移，顶部提供关闭与保存 */
@Composable
private fun ImageViewerDialog(
    bitmap: android.graphics.Bitmap,
    onSaveClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transformState = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = if (scale > 1f) offset + pan else Offset.Zero
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    })
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transformState)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    AppIcon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = Color.White
                    )
                }
                Box(modifier = Modifier.weight(1f))
                AppIconButton(onClick = onSaveClick, modifier = Modifier.size(36.dp)) {
                    AppIcon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(R.string.image_save),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/** 把图片字节写入公共相册 Pictures/Pulse（API 29+），低版本写入应用私有 Pictures 目录 */
private fun saveImageBytes(context: Context, bytes: ByteArray): Boolean {
    val mime = detectImageMime(bytes) ?: return false
    val ext = when (mime) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/gif" -> "gif"
        else -> "webp"
    }
    val name = "Pulse_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "." + ext
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Pulse")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        }.getOrDefault(false)
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        written
    } else {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return false
        runCatching { File(dir, name).writeBytes(bytes) }.isSuccess
    }
}

/** 按魔数识别常见图片格式，用于确定保存的 MIME 与扩展名 */
private fun detectImageMime(bytes: ByteArray): String? = when {
    bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"

    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte() -> "image/jpeg"

    bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() -> "image/gif"

    bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
        bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() -> "image/webp"

    else -> null
}
