package com.evan.pulse.ui.design

import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource
import org.eclipse.tm4e.core.registry.Registry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 校验打包进 assets 的 TextMate 资源可用。
 *
 * 语法文件来自 VSCode 扩展，YAML 语法通过 scope 引用拆成三个文件（source.yaml
 * 只做版本分发），漏掉任何一个都会让高亮整体失效，而这类错误在编译期完全看不出来。
 */
class TextMateAssetsTest {

    private val root = File("src/main/assets/textmate")

    private fun grammarSource(rel: String) =
        IGrammarSource.fromInputStream(File(root, rel).inputStream(), rel, null)

    private fun themeSource(rel: String) =
        IThemeSource.fromInputStream(File(root, rel).inputStream(), rel, null)

    @Test
    fun grammarsLoadWithExpectedScopes() {
        val registry = Registry()
        val expected = mapOf(
            "json/syntaxes/JSON.tmLanguage.json" to "source.json",
            "yaml/syntaxes/yaml.tmLanguage.json" to "source.yaml",
            "yaml/syntaxes/yaml-1.2.tmLanguage.json" to "source.yaml.1.2",
            "yaml/syntaxes/yaml-embedded.tmLanguage.json" to "source.yaml.embedded",
            "javascript/syntaxes/JavaScript.tmLanguage.json" to "source.js",
        )
        expected.forEach { (path, scope) ->
            assertEquals(path, scope, registry.addGrammar(grammarSource(path)).scopeName)
        }
    }

    @Test
    fun jsonGrammarProducesStringAndNumberTokens() {
        val registry = Registry()
        registry.addGrammar(grammarSource("json/syntaxes/JSON.tmLanguage.json"))
        val grammar = requireNotNull(registry.grammarForScopeName("source.json"))
        val tokens = grammar.tokenizeLine("{\"a\": 1}").tokens
        val scopes = tokens.flatMap { it.scopes }
        assertTrue(scopes.any { it.startsWith("string") })
        assertTrue(scopes.any { it.startsWith("constant.numeric") })
    }

    @Test
    fun yamlGrammarProducesKeyTokens() {
        val registry = Registry()
        registry.addGrammar(grammarSource("yaml/syntaxes/yaml-1.2.tmLanguage.json"))
        registry.addGrammar(grammarSource("yaml/syntaxes/yaml-embedded.tmLanguage.json"))
        registry.addGrammar(grammarSource("yaml/syntaxes/yaml.tmLanguage.json"))
        val grammar = requireNotNull(registry.grammarForScopeName("source.yaml"))
        val scopes = grammar.tokenizeLine("key: value").tokens.flatMap { it.scopes }
        assertTrue(scopes.any { it.contains("yaml") })
    }

    @Test
    fun javaScriptGrammarProducesKeywordTokens() {
        val registry = Registry()
        registry.addGrammar(grammarSource("javascript/syntaxes/JavaScript.tmLanguage.json"))
        val grammar = requireNotNull(registry.grammarForScopeName("source.js"))
        val scopes = grammar.tokenizeLine("const a = 1;").tokens.flatMap { it.scopes }
        assertTrue(scopes.any { it.contains("storage") || it.contains("keyword") })
    }

    @Test
    fun themesLoad() {
        Registry().setTheme(themeSource("themes/quietlight.json"))
        Registry().setTheme(themeSource("themes/darcula.json"))
    }

    @Test
    fun languagesJsonReferencesExistingFiles() {
        val config = File(root, "languages.json").readText()
        val entries = Regex(
            "\\{[^{}]*\"grammar\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"scopeName\"\\s*:\\s*\"([^\"]+)\"[^{}]*}"
        ).findAll(config).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue(entries.isNotEmpty())
        entries.forEach { (rel, scope) ->
            val path = File(root, rel.removePrefix("textmate/"))
            assertTrue("missing $rel", path.isFile)
            assertTrue("$rel does not declare $scope", path.readText().contains("\"$scope\""))
        }
    }
}
