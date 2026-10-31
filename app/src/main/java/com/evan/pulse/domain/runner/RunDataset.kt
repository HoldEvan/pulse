package com.evan.pulse.domain.runner

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 数据集解析失败的原因，界面据此给出本地化提示 */
enum class DatasetError {
    EMPTY,
    BAD_JSON,
    NOT_ARRAY_OF_OBJECTS,
    NESTED_VALUE,
    CSV_EMPTY_HEADER,
    CSV_ROW_MISMATCH
}

class DatasetFormatException(val reason: DatasetError) : IllegalArgumentException(reason.name)

/**
 * 数据驱动运行的数据集：若干列名 + 每行一组键值。
 *
 * 每行作为一个迭代注入变量作用域，键名即变量名，`{{key}}` 可直接引用。
 * 解析对 CSV / JSON 都做宽松处理：值统一转成字符串，空单元格视为空串。
 */
data class RunDataset(
    val columns: List<String>,
    val rows: List<Map<String, String>>
) {
    val isNotEmpty: Boolean get() = rows.isNotEmpty()

    companion object {
        /**
         * 自动识别格式：去掉首部空白后以 `[` 开头按 JSON 数组解析，否则按 CSV。
         */
        fun parse(text: String): RunDataset {
            val trimmed = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            if (trimmed.isBlank()) throw DatasetFormatException(DatasetError.EMPTY)
            return if (trimmed.startsWith("[")) parseJson(trimmed) else parseCsv(text)
        }

        private fun parseJson(text: String): RunDataset {
            val root = try {
                Json.parseToJsonElement(text)
            } catch (_: Exception) {
                throw DatasetFormatException(DatasetError.BAD_JSON)
            }
            val array = root as? JsonArray ?: throw DatasetFormatException(DatasetError.NOT_ARRAY_OF_OBJECTS)
            if (array.isEmpty()) throw DatasetFormatException(DatasetError.EMPTY)
            val columns = LinkedHashSet<String>()
            val rows = ArrayList<Map<String, String>>(array.size)
            for (element in array) {
                val obj = element as? JsonObject
                    ?: throw DatasetFormatException(DatasetError.NOT_ARRAY_OF_OBJECTS)
                val row = LinkedHashMap<String, String>()
                for ((key, value) in obj) {
                    if (value is JsonObject || value is JsonArray) {
                        throw DatasetFormatException(DatasetError.NESTED_VALUE)
                    }
                    columns += key
                    row[key] = jsonScalarToString(value)
                }
                rows += row
            }
            return RunDataset(columns.toList(), rows)
        }

        private fun jsonScalarToString(value: JsonElement): String = when (value) {
            is JsonPrimitive -> if (value is kotlinx.serialization.json.JsonNull) "" else value.content
            else -> ""
        }

        private fun parseCsv(text: String): RunDataset {
            val records = parseCsvRecords(text)
            if (records.isEmpty()) throw DatasetFormatException(DatasetError.EMPTY)
            val header = records.first().map { it.trim() }
            if (header.isEmpty() || header.all { it.isBlank() }) {
                throw DatasetFormatException(DatasetError.CSV_EMPTY_HEADER)
            }
            if (header.any { it.isBlank() }) throw DatasetFormatException(DatasetError.CSV_EMPTY_HEADER)
            val rows = ArrayList<Map<String, String>>(records.size - 1)
            for (index in 1 until records.size) {
                val cells = records[index]
                // 全空行（只有一个空串）跳过，方便文件末尾留空行
                if (cells.size == 1 && cells[0].isBlank()) continue
                if (cells.size != header.size) {
                    throw DatasetFormatException(DatasetError.CSV_ROW_MISMATCH)
                }
                val row = LinkedHashMap<String, String>()
                header.forEachIndexed { i, name -> row[name] = cells[i] }
                rows += row
            }
            if (rows.isEmpty()) throw DatasetFormatException(DatasetError.EMPTY)
            return RunDataset(header, rows)
        }

        /** 支持双引号包裹、内部 `""` 转义、字段内逗号与换行 */
        private fun parseCsvRecords(text: String): List<List<String>> {
            val records = ArrayList<List<String>>()
            var row = ArrayList<String>()
            val field = StringBuilder()
            var inQuotes = false
            var i = 0
            val s = text
            var sawAny = false
            while (i < s.length) {
                val c = s[i]
                when {
                    inQuotes -> when {
                        c == '"' && i + 1 < s.length && s[i + 1] == '"' -> {
                            field.append('"'); i++
                        }
                        c == '"' -> inQuotes = false
                        else -> field.append(c)
                    }
                    c == '"' -> inQuotes = true
                    c == ',' -> {
                        row.add(field.toString()); field.setLength(0); sawAny = true
                    }
                    c == '\r' -> {
                        // 交给 \n 处理；裸 \r 也视为换行
                        if (i + 1 < s.length && s[i + 1] == '\n') {
                            i++
                        }
                        row.add(field.toString()); field.setLength(0)
                        records.add(row); row = ArrayList(); sawAny = false
                    }
                    c == '\n' -> {
                        row.add(field.toString()); field.setLength(0)
                        records.add(row); row = ArrayList(); sawAny = false
                    }
                    else -> {
                        field.append(c); sawAny = true
                    }
                }
                i++
            }
            // 收尾：最后一行没有换行符时也要收进来
            if (field.isNotEmpty() || row.isNotEmpty() || sawAny) {
                row.add(field.toString())
                records.add(row)
            }
            return records
        }
    }
}
