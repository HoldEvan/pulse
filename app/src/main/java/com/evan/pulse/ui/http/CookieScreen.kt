package com.evan.pulse.ui.http

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.di.StoredCookie
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.DialogActions
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppDialog
import com.evan.pulse.ui.design.AppIcon
import com.evan.pulse.ui.design.AppIconButton
import com.evan.pulse.ui.design.AppInfoRow
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextField
import com.evan.pulse.ui.design.AppTheme
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete

/**
 * Cookie 管理：按域名分桶列出当前项目收集到的 Cookie，
 * 支持单条删除、按域名清除、清空当前项目与手动添加。
 * 自动收集发生在 HTTP 请求成功响应后（ViewModel 负责），这里只负责查看与维护。
 */
class CookieViewModel : ViewModel() {

    private val store = ServiceLocator.cookieStore

    val currentProjectId: StateFlow<Long> = ServiceLocator.projectStore.currentProjectId
    val cookies: StateFlow<Map<Long, Map<String, List<StoredCookie>>>> = store.cookies

    var showAdd by mutableStateOf(false)
    var addHost by mutableStateOf("")
    var addName by mutableStateOf("")
    var addValue by mutableStateOf("")

    fun openAdd() {
        addHost = ""
        addName = ""
        addValue = ""
        showAdd = true
    }

    fun closeAdd() {
        showAdd = false
    }

    /** 手动添加：host 与 name 必填，path 固定为 /，会话级不过期 */
    fun confirmAdd() {
        val host = addHost.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        val name = addName.trim()
        if (host.isEmpty() || name.isEmpty()) return
        store.upsert(
            currentProjectId.value,
            StoredCookie(name = name, value = addValue, domain = host.lowercase())
        )
        closeAdd()
    }

    /** 待确认的删除动作；非空时弹出确认对话框 */
    var deleteRequest by mutableStateOf<CookieDeleteRequest?>(null)

    fun requestDelete(request: CookieDeleteRequest) {
        deleteRequest = request
    }

    fun cancelDelete() {
        deleteRequest = null
    }

    /** 用户确认后才真正落地删除，避免误触直接清掉 Cookie */
    fun confirmDelete() {
        val request = deleteRequest ?: return
        val projectId = currentProjectId.value
        when (request) {
            is CookieDeleteRequest.Single -> store.remove(projectId, request.domain, request.name)
            is CookieDeleteRequest.Host -> store.clearHost(projectId, request.domain)
            CookieDeleteRequest.Project -> store.clearProject(projectId)
        }
        deleteRequest = null
    }
}

/** Cookie 删除范围：单条、某个域名下的全部、或当前项目的全部 */
sealed interface CookieDeleteRequest {
    data class Single(val domain: String, val name: String) : CookieDeleteRequest
    data class Host(val domain: String, val count: Int) : CookieDeleteRequest
    data object Project : CookieDeleteRequest
}

@Composable
fun CookieScreen(
    modifier: Modifier = Modifier,
    viewModel: CookieViewModel = viewModel(),
) {
    val projectId by viewModel.currentProjectId.collectAsState()
    val allCookies by viewModel.cookies.collectAsState()
    val hosts = allCookies[projectId].orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        hosts.forEach { (domain, list) ->
            AppCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    AppInfoRow(
                        title = domain,
                        summary = stringResource(R.string.cookie_domain_summary, list.size),
                        endActions = {
                            AppIconButton(
                                onClick = {
                                    viewModel.requestDelete(
                                        CookieDeleteRequest.Host(domain, list.size)
                                    )
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                AppIcon(
                                    imageVector = MiuixIcons.Light.Delete,
                                    contentDescription = stringResource(R.string.cookie_menu_clear_host),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                    list.forEach { cookie ->
                        val expired = cookie.expiresAtMs <= System.currentTimeMillis()
                        AppInfoRow(
                            title = cookie.name,
                            summary = buildString {
                                append(cookie.value.ifEmpty { "-" })
                                append(" · /")
                                append(cookie.path)
                                if (expired) append(" · ")
                                if (expired) append(stringResource(R.string.cookie_expired))
                            },
                            endActions = {
                                AppIconButton(
                                    onClick = {
                                        viewModel.requestDelete(
                                            CookieDeleteRequest.Single(domain, cookie.name)
                                        )
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    AppIcon(
                                        imageVector = MiuixIcons.Light.Delete,
                                        contentDescription = stringResource(
                                            R.string.cookie_menu_delete, cookie.name
                                        ),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
        if (hosts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppIcon(
                    imageVector = Icons.Filled.Cookie,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = AppTheme.colors.outline
                )
                AppText(
                    text = stringResource(R.string.cookie_empty),
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 13.sp,
                    color = AppTheme.colors.outline
                )
            }
        }
        BottomBarClearance()
    }

    viewModel.deleteRequest?.let { request ->
        AppDialog(
            show = true,
            title = stringResource(R.string.cookie_delete_title),
            onDismissRequest = { viewModel.cancelDelete() }
        ) {
            Column {
                AppText(
                    text = when (request) {
                        is CookieDeleteRequest.Single -> stringResource(
                            R.string.cookie_delete_confirm_single,
                            request.name,
                            request.domain
                        )
                        is CookieDeleteRequest.Host -> stringResource(
                            R.string.cookie_delete_confirm_host,
                            request.domain,
                            request.count
                        )
                        CookieDeleteRequest.Project -> stringResource(
                            R.string.cookie_delete_confirm_project
                        )
                    }
                )
                DialogActions(
                    onCancel = { viewModel.cancelDelete() },
                    confirmText = stringResource(R.string.action_delete),
                    onConfirm = { viewModel.confirmDelete() }
                )
            }
        }
    }

    if (viewModel.showAdd) {
        AppDialog(
            show = true,
            title = stringResource(R.string.cookie_add_title),
            onDismissRequest = { viewModel.closeAdd() }
        ) {
            Column {
                AppTextField(
                    value = viewModel.addHost,
                    onValueChange = { viewModel.addHost = it },
                    label = stringResource(R.string.cookie_host_label),
                    singleLine = true
                )
                AppTextField(
                    value = viewModel.addName,
                    onValueChange = { viewModel.addName = it },
                    label = stringResource(R.string.cookie_name_label),
                    modifier = Modifier.padding(top = 8.dp),
                    singleLine = true
                )
                AppTextField(
                    value = viewModel.addValue,
                    onValueChange = { viewModel.addValue = it },
                    label = stringResource(R.string.cookie_value_label),
                    modifier = Modifier.padding(top = 8.dp)
                )
                DialogActions(
                    onCancel = { viewModel.closeAdd() },
                    confirmText = stringResource(R.string.action_save),
                    onConfirm = { viewModel.confirmAdd() }
                )
            }
        }
    }
}
