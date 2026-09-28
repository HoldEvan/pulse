package com.pulse.apitester.domain.net

import com.pulse.apitester.domain.model.HttpCallConfig
import com.pulse.apitester.domain.model.HttpCallOutcome

interface HttpEngine {

    suspend fun execute(config: HttpCallConfig): HttpCallOutcome
}
