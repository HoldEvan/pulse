package com.pulse.apitester.ui.http

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.apitester.data.model.HistoryEntry
import com.pulse.apitester.di.ServiceLocator
import com.pulse.apitester.domain.PlaceholderResolver
import com.pulse.apitester.domain.model.AuthType
import com.pulse.apitester.domain.model.BodyType
import com.pulse.apitester.domain.model.FormField
import com.pulse.apitester.domain.model.HttpCallConfig
import com.pulse.apitester.domain.model.HttpCallOutcome
import com.pulse.apitester.domain.model.HttpMethod
import com.pulse.apitester.domain.model.KeyValue
import kotlinx.coroutines.launch

class HttpViewModel : ViewModel() {

    private val resolver = ServiceLocator.placeholderResolver
    private val authApplier = ServiceLocator.authApplier
    private val engine = ServiceLocator.httpEngine
    private val historyStore = ServiceLocator.historyStore
    private val envStore = ServiceLocator.envStore
    private val projectStore = ServiceLocator.projectStore

    val environments = envStore.environments
    val currentProjectId = projectStore.currentProjectId

    var method by mutableStateOf(HttpMethod.GET)
    var url by mutableStateOf("{{baseUrl}}/ping")
    var params by mutableStateOf(listOf(KeyValue()))
    var headers by mutableStateOf(listOf(KeyValue()))
    var bodyType by mutableStateOf(BodyType.NONE)
    var bodyText by mutableStateOf("")
    var formFields by mutableStateOf(listOf(FormField()))
    var authType by mutableStateOf(AuthType.NONE)
    var bearerToken by mutableStateOf("")
    var basicUsername by mutableStateOf("")
    var basicPassword by mutableStateOf("")

    var activeTab by mutableIntStateOf(0)
    var showMethodDialog by mutableStateOf(false)
    var showBodyTypeDialog by mutableStateOf(false)
    var showEnvDialog by mutableStateOf(false)
    var sending by mutableStateOf(false)
    var outcome by mutableStateOf<HttpCallOutcome?>(null)
    var missingVars by mutableStateOf<List<String>?>(null)

    fun activeEnvVars(): Map<String, String> {
        val pid = currentProjectId.value
        val env = environments.value.firstOrNull { it.projectId == pid && it.isActive }
        return env?.variables?.toMap() ?: emptyMap()
    }

    fun activeEnvName(): String {
        val pid = currentProjectId.value
        val env = environments.value.firstOrNull { it.projectId == pid && it.isActive }
        return env?.name ?: "无环境"
    }

    fun envStoreActivate(envId: Long) {
        envStore.activate(currentProjectId.value, envId)
    }

    fun updateParams(index: Int, value: KeyValue) {
        params = params.mapIndexed { i, kv -> if (i == index) value else kv }
    }

    fun addParam() {
        params = params + KeyValue()
    }

    fun removeParam(index: Int) {
        params = params.filterIndexed { i, _ -> i != index }
        if (params.isEmpty()) params = listOf(KeyValue())
    }

    fun updateHeaders(index: Int, value: KeyValue) {
        headers = headers.mapIndexed { i, kv -> if (i == index) value else kv }
    }

    fun addHeader() {
        headers = headers + KeyValue()
    }

    fun removeHeader(index: Int) {
        headers = headers.filterIndexed { i, _ -> i != index }
        if (headers.isEmpty()) headers = listOf(KeyValue())
    }

    fun updateFormField(index: Int, value: FormField) {
        formFields = formFields.mapIndexed { i, f -> if (i == index) value else f }
    }

    fun addFormField() {
        formFields = formFields + FormField()
    }

    fun removeFormField(index: Int) {
        formFields = formFields.filterIndexed { i, _ -> i != index }
        if (formFields.isEmpty()) formFields = listOf(FormField())
    }

    fun dismissMissing() {
        missingVars = null
    }

    fun send() {
        if (sending) return
        viewModelScope.launch {
            sending = true
            outcome = null
            missingVars = null
            try {
                val vars = activeEnvVars()
                val appliedAuth = authApplier.apply(
                    com.pulse.apitester.domain.model.AuthConfig(
                        type = authType,
                        bearerToken = bearerToken,
                        basicUsername = basicUsername,
                        basicPassword = basicPassword
                    )
                )
                val effectiveHeaders = (headers.filter { it.enabled && it.key.isNotBlank() } + appliedAuth)
                val effectiveParams = params.filter { it.enabled && it.key.isNotBlank() }

                val missing = mutableSetOf<String>()
                fun res(text: String): String = when (val r = resolver.resolve(text, vars)) {
                    is PlaceholderResolver.ResolveResult.Ok -> r.resolved
                    is PlaceholderResolver.ResolveResult.Missing -> {
                        missing += r.keys
                        text
                    }
                }

                val resolvedUrl = res(url)
                val resolvedHeaders = effectiveHeaders.map { it.copy(value = res(it.value)) }
                val resolvedParams = effectiveParams.map { it.copy(value = res(it.value)) }
                val resolvedBody = if (bodyType == BodyType.JSON || bodyType == BodyType.RAW) res(bodyText) else bodyText
                val resolvedForm = formFields
                    .filter { it.name.isNotBlank() && !it.isFile }
                    .map { it.copy(value = res(it.value)) }

                if (missing.isNotEmpty()) {
                    missingVars = missing.sorted()
                    return@launch
                }

                val config = HttpCallConfig(
                    method = method,
                    url = resolvedUrl,
                    headers = resolvedHeaders,
                    queryParams = resolvedParams,
                    bodyType = bodyType,
                    body = resolvedBody,
                    formFields = resolvedForm
                )
                val result = engine.execute(config)
                outcome = result
                if (result is HttpCallOutcome.Success) {
                    historyStore.add(
                        HistoryEntry(
                            id = 0,
                            projectId = currentProjectId.value,
                            method = method.name,
                            url = resolvedUrl,
                            statusCode = result.statusCode,
                            durationMs = result.durationMs,
                            success = result.statusCode in 200..399,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
            } finally {
                sending = false
            }
        }
    }

    fun cancel() {
        sending = false
    }
}
