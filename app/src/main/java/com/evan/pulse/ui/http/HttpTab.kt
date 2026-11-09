package com.evan.pulse.ui.http

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.evan.pulse.R
import com.evan.pulse.domain.model.FormField
import com.evan.pulse.domain.assertion.AssertionCheck
import com.evan.pulse.domain.assertion.ResponseAssertion
import com.evan.pulse.domain.model.AuthType
import com.evan.pulse.domain.model.BodyType
import com.evan.pulse.domain.model.HttpCallOutcome
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.net.NetworkPreset
import com.evan.pulse.domain.script.TestRecord
import com.evan.pulse.ui.collection.LoadedRequestTracker
import com.evan.pulse.ui.l10n.L10n
import kotlinx.coroutines.Job

/**
 * HTTP 页面上的一个请求标签。
 *
 * 每个标签自带完整的请求配置与响应状态，切换标签即切回该标签自己的
 * 方法、URL、参数、请求体、脚本、断言与响应，互不覆盖；
 * 因此在 A 标签的请求还在飞的时候，可以切到 B 标签继续发请求。
 */
class HttpTab(
    val id: Int,
    val loadedRequest: LoadedRequestTracker
) {

    // ---- 请求配置 ----
    var method by mutableStateOf(HttpMethod.GET)
    var url by mutableStateOf(DEFAULT_URL)
    var params by mutableStateOf(listOf(KeyValue()))
    var headers by mutableStateOf(listOf(KeyValue()))
    var bodyType by mutableStateOf(BodyType.NONE)
    var bodyText by mutableStateOf("")
    var graphqlVariables by mutableStateOf("{}")
    var formFields by mutableStateOf(listOf(FormField()))
    var authType by mutableStateOf(AuthType.NONE)
    var bearerToken by mutableStateOf("")
    var basicUsername by mutableStateOf("")
    var basicPassword by mutableStateOf("")
    var apiKeyName by mutableStateOf("")
    var apiKeyValue by mutableStateOf("")
    var preScript by mutableStateOf("")
    var postScript by mutableStateOf("")

    /** 请求超时（秒），输入框直接绑文本，非法输入回落到默认 30s */
    var timeoutSec by mutableStateOf("30")

    /** 连接超时（秒）：留空沿用总超时 */
    var connectTimeoutSec by mutableStateOf("")

    /** 读取超时（秒）：留空沿用总超时 */
    var readTimeoutSec by mutableStateOf("")

    /** 原始二进制请求体：所选文件 URI 与展示名 */
    var bodyFileUri by mutableStateOf<String?>(null)
    var bodyFileName by mutableStateOf<String?>(null)

    /** 是否套用设置页里的全局代理；关闭则本次请求强制直连 */
    var useProxy by mutableStateOf(true)

    /** 跳过 TLS 证书校验；仅用于调试自签名或内网 HTTPS 服务 */
    var insecureTls by mutableStateOf(false)
    var customCaPem by mutableStateOf("")
    var clientCertPem by mutableStateOf("")
    var clientKeyPem by mutableStateOf("")

    /** 是否自动跟随 3xx 重定向；关闭后直接展示 3xx 与 Location */
    var followRedirects by mutableStateOf(true)

    /** 最大跟随跳数，输入框直接绑文本 */
    var maxRedirects by mutableStateOf("10")

    /** 弱网模拟档位；NONE 即关闭，FORCE_TIMEOUT 配合秒数生效 */
    var throttlePreset by mutableStateOf(NetworkPreset.NONE)

    /** 强制超时秒数，输入框直接绑文本，空或 0 视为不启用 */
    var forceTimeoutSec by mutableStateOf("")

    /** 响应断言：每次发送后按顺序校验响应体，结果见 [assertionResults] */
    var assertions by mutableStateOf<List<ResponseAssertion>>(emptyList())

    // ---- 响应状态 ----
    var sending by mutableStateOf(false)
    var outcome by mutableStateOf<HttpCallOutcome?>(null)
    var assertionResults by mutableStateOf<List<AssertionCheck>>(emptyList())

    /** 上一次请求里前置/后置脚本的 console 输出 */
    var scriptConsole by mutableStateOf<List<String>>(emptyList())
    var scriptTests by mutableStateOf<List<TestRecord>>(emptyList())
    var scriptError by mutableStateOf<String?>(null)

    /** 缺失变量提示：按标签记录，切到别的标签不会继续弹窗 */
    var missingVars by mutableStateOf<List<String>?>(null)

    /** 当前请求所属文件夹；为空时没有文件夹变量可用 */
    var activeCollectionId: Long? = null

    /** 正在执行的请求；关闭标签时取消，避免写回已销毁的标签 */
    var job: Job? = null

    /** 标签页标题：#序号 + 主机名；尚未填写显示「新请求」 */
    fun label(): String {
        val host = url.substringAfter("://", url)
            .substringBefore('/').substringBefore('?').substringAfterLast('@')
        return "#$id " + host.ifBlank { L10n.t(R.string.http_tab_new) }
    }

    /** 空白标签：没有响应也没有在发送，加载集合请求时可以直接复用 */
    fun isIdle(): Boolean = !sending && outcome == null

    companion object {
        const val DEFAULT_URL = "{{baseUrl}}/ping"
    }
}