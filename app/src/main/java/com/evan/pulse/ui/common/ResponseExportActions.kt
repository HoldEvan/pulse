package com.evan.pulse.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.data.export.ResponseExport
import com.evan.pulse.data.export.ResponseFileStore
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download

/**
 * 响应体的保存与分享入口。
 *
 * 保存走 SAF，用户自己选位置；分享先落到缓存再由 FileProvider 交给系统面板。
 * 两者都用原始字节（实时响应），没有原始字节时退回文本的 UTF-8 编码。
 */
@Composable
fun ResponseExportActions(
    body: String,
    bodyBytes: ByteArray?,
    contentType: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { ResponseFileStore(context) }
    val mime = remember(contentType) { ResponseExport.baseMime(contentType) }
    val payload = remember(body, bodyBytes) { ResponseExport.bytesOf(body, bodyBytes) }

    val savedText = stringResource(R.string.resp_export_saved)
    val failedText = stringResource(R.string.resp_export_failed)
    val shareFailedText = stringResource(R.string.resp_share_failed)

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mime)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching { store.writeTo(uri, payload) }.isSuccess
        AppToast.show(if (ok) savedText else failedText)
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        AppText(
            text = stringResource(R.string.resp_export_size, payload.size),
            modifier = Modifier.padding(end = 8.dp),
            fontSize = 12.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
        AppIcon(
            imageVector = MiuixIcons.Light.Download,
            contentDescription = stringResource(R.string.cd_save_response),
            modifier = Modifier
                .roundedPressable(onClick = {
                    saveLauncher.launch(ResponseExport.suggestedName(contentType))
                })
                .padding(6.dp)
        )
        AppIcon(
            imageVector = Icons.Filled.Share,
            contentDescription = stringResource(R.string.cd_share_response),
            modifier = Modifier
                .roundedPressable(onClick = {
                    val ok = runCatching {
                        val name = ResponseExport.suggestedName(contentType)
                        val uri = store.stageForShare(name, payload)
                        store.share(uri, mime, name)
                    }.isSuccess
                    if (!ok) AppToast.show(shareFailedText)
                })
                .padding(6.dp)
        )
    }
}
