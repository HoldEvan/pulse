package com.evan.pulse.domain

import com.evan.pulse.domain.model.AuthConfig
import com.evan.pulse.domain.model.KeyValue

interface AuthApplier {

    fun apply(config: AuthConfig): List<KeyValue>
}
