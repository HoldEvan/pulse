package com.evan.pulse.domain.script

import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpCallOutcome

/** 前置/后置脚本执行器 */
interface ScriptExecutor {

    /** 发送前执行，脚本可以直接修改 [request] */
    fun runPre(script: String, request: MutableRequest, sandbox: ScriptSession): ScriptRunResult

    /** 拿到响应后执行 */
    fun runPost(
        script: String,
        request: HttpCallConfig,
        outcome: HttpCallOutcome,
        sandbox: ScriptSession
    ): ScriptRunResult
}
