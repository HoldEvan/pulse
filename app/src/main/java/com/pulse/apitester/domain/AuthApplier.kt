package com.pulse.apitester.domain

import com.pulse.apitester.domain.model.AuthConfig
import com.pulse.apitester.domain.model.KeyValue

interface AuthApplier {

    fun apply(config: AuthConfig): List<KeyValue>
}
