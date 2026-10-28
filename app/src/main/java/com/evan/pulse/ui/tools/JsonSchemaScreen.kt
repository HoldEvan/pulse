package com.evan.pulse.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evan.pulse.R
import com.evan.pulse.domain.tools.JsonSchemaValidator
import com.evan.pulse.domain.tools.SchemaReport
import com.evan.pulse.domain.tools.SchemaViolation
import com.evan.pulse.ui.common.CardSectionTitle
import com.evan.pulse.ui.common.LocalTopBarInset
import com.evan.pulse.ui.common.subPageOverScroll
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.design.AppCard
import com.evan.pulse.ui.design.AppText
import com.evan.pulse.ui.design.AppTextButton
import com.evan.pulse.ui.design.AppTheme
import com.evan.pulse.ui.design.CodeEditorField
import com.evan.pulse.ui.design.CodeLanguage

private const val SAMPLE_SCHEMA = """{
  "type": "object",
  "required": ["id", "name"],
  "properties": {
    "id": { "type": "integer", "minimum": 1 },
    "name": { "type": "string", "minLength": 2 },
    "tags": { "type": "array", "items": { "type": "string" } }
  },
  "additionalProperties": false
}"""

private const val SAMPLE_DATA = """{
  "id": 0,
  "name": "a",
  "tags": ["x", 2],
  "extra": true
}"""

/**
 * JSON Schema 校验二级页：左侧 Schema、右侧实例，一次校验给出全部不匹配项。
 *
 * 校验在点击时触发，输入变化不会实时重算，避免长文档在输入过程中反复遍历。
 */
@Composable
fun JsonSchemaScreen(modifier: Modifier = Modifier) {
    var schemaText by remember { mutableStateOf("") }
    var dataText by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<SchemaReport?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(start = 12.dp, top = LocalTopBarInset.current, end = 12.dp)
            .padding(bottom = 12.dp)
    ) {
        CardSectionTitle(
            text = stringResource(R.string.json_schema_schema_label),
            modifier = Modifier.padding(top = 12.dp)
        )
        CodeEditorField(
            text = schemaText,
            onValueChange = {
                schemaText = it
                report = null
            },
            language = CodeLanguage.JSON,
            dark = AppTheme.colors.background.luminance() < 0.5f,
            modifier = Modifier.fillMaxWidth().height(180.dp).padding(top = 6.dp)
        )
        CardSectionTitle(
            text = stringResource(R.string.json_schema_data_label),
            modifier = Modifier.padding(top = 12.dp)
        )
        CodeEditorField(
            text = dataText,
            onValueChange = {
                dataText = it
                report = null
            },
            language = CodeLanguage.JSON,
            dark = AppTheme.colors.background.luminance() < 0.5f,
            modifier = Modifier.fillMaxWidth().height(180.dp).padding(top = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTextButton(
                text = stringResource(R.string.json_schema_validate),
                onClick = {
                    report = JsonSchemaValidator.validate(schemaText, dataText)
                },
                enabled = schemaText.isNotBlank() && dataText.isNotBlank(),
                filled = true
            )
            AppTextButton(
                text = stringResource(R.string.json_schema_sample),
                onClick = {
                    schemaText = SAMPLE_SCHEMA
                    dataText = SAMPLE_DATA
                    report = null
                },
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        val current = report
        when {
            current == null -> Hint(stringResource(R.string.json_schema_empty_hint))
            current.schemaError != null -> AppText(
                text = schemaErrorText(current.schemaError),
                modifier = Modifier.padding(top = 12.dp),
                fontSize = 13.sp,
                color = AppTheme.colors.error
            )
            current.valid -> AppText(
                text = stringResource(R.string.json_schema_valid),
                modifier = Modifier.padding(top = 12.dp),
                fontSize = 14.sp,
                color = AppTheme.colors.primary
            )
            else -> {
                CardSectionTitle(
                    text = stringResource(
                        R.string.json_schema_invalid_count,
                        current.violations.size
                    ),
                    modifier = Modifier.padding(top = 14.dp)
                )
                current.violations.forEach { violation ->
                    ViolationCard(violation)
                }
                if (current.truncated) {
                    AppText(
                        text = stringResource(
                            R.string.json_schema_truncated,
                            JsonSchemaValidator.MAX_VIOLATIONS
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                        fontSize = 12.sp,
                        color = AppTheme.colors.onSurfaceSummary
                    )
                }
            }
        }
    }
}

@Composable
private fun ViolationCard(violation: SchemaViolation) {
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
    ) {
        AppText(
            text = violation.path,
            fontSize = 13.sp,
            color = AppTheme.colors.primary
        )
        AppText(
            text = violationText(violation),
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 13.sp,
            color = AppTheme.colors.onSurfaceSummary
        )
    }
}

@Composable
private fun schemaErrorText(error: String?): String = stringResource(
    when (error) {
        "schemaInvalid" -> R.string.json_schema_error_schema_invalid
        "schemaType" -> R.string.json_schema_error_schema_type
        "dataInvalid" -> R.string.json_schema_error_data_invalid
        "refNotFound" -> R.string.json_schema_error_ref_not_found
        "pattern" -> R.string.json_schema_error_pattern
        "depth" -> R.string.json_schema_error_depth
        else -> R.string.json_schema_error_generic
    }
)

@Composable
private fun violationText(violation: SchemaViolation): String = when (violation.keyword) {
    "type" -> stringResource(
        R.string.json_schema_violation_type,
        violation.expected,
        violation.actual
    )
    "required" -> stringResource(R.string.json_schema_violation_required, violation.expected)
    "enum" -> stringResource(
        R.string.json_schema_violation_enum,
        violation.expected,
        violation.actual
    )
    "const" -> stringResource(
        R.string.json_schema_violation_const,
        violation.expected,
        violation.actual
    )
    "additionalProperties" -> stringResource(
        R.string.json_schema_violation_additional,
        violation.expected
    )
    "minimum" -> stringResource(
        R.string.json_schema_violation_minimum,
        violation.expected,
        violation.actual
    )
    "maximum" -> stringResource(
        R.string.json_schema_violation_maximum,
        violation.expected,
        violation.actual
    )
    "exclusiveMinimum" -> stringResource(
        R.string.json_schema_violation_exclusive_minimum,
        violation.expected,
        violation.actual
    )
    "exclusiveMaximum" -> stringResource(
        R.string.json_schema_violation_exclusive_maximum,
        violation.expected,
        violation.actual
    )
    "multipleOf" -> stringResource(
        R.string.json_schema_violation_multiple_of,
        violation.expected,
        violation.actual
    )
    "minLength" -> stringResource(
        R.string.json_schema_violation_min_length,
        violation.expected,
        violation.actual
    )
    "maxLength" -> stringResource(
        R.string.json_schema_violation_max_length,
        violation.expected,
        violation.actual
    )
    "pattern" -> stringResource(
        R.string.json_schema_violation_pattern,
        violation.expected,
        violation.actual
    )
    "minItems" -> stringResource(
        R.string.json_schema_violation_min_items,
        violation.expected,
        violation.actual
    )
    "maxItems" -> stringResource(
        R.string.json_schema_violation_max_items,
        violation.expected,
        violation.actual
    )
    "uniqueItems" -> stringResource(R.string.json_schema_violation_unique_items)
    "anyOf" -> stringResource(R.string.json_schema_violation_any_of, violation.expected)
    "oneOf" -> stringResource(
        R.string.json_schema_violation_one_of,
        violation.expected,
        violation.actual
    )
    "not" -> stringResource(R.string.json_schema_violation_not)
    else -> violation.keyword
}

@Composable
private fun Hint(text: String) {
    AppText(
        text = text,
        modifier = Modifier.padding(vertical = 8.dp),
        fontSize = 13.sp,
        color = AppTheme.colors.onSurfaceSummary
    )
}
