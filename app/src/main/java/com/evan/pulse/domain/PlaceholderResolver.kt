package com.evan.pulse.domain

interface PlaceholderResolver {

    fun resolve(text: String, vars: Map<String, String>): ResolveResult

    sealed interface ResolveResult {
        data class Ok(val resolved: String) : ResolveResult
        data class Missing(val keys: List<String>) : ResolveResult
    }

    companion object {
        val PLACEHOLDER_REGEX = Regex("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\}\\}")
    }
}
