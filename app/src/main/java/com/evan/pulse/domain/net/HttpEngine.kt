package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpCallOutcome

interface HttpEngine {

    suspend fun execute(config: HttpCallConfig): HttpCallOutcome
}
