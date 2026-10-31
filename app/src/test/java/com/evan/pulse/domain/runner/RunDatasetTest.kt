package com.evan.pulse.domain.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RunDatasetTest {

    private fun assertError(expected: DatasetError, text: String) {
        try {
            RunDataset.parse(text)
            fail("Expected DatasetFormatException($expected)")
        } catch (e: DatasetFormatException) {
            assertEquals(expected, e.reason)
        }
    }

    @Test
    fun parsesSimpleCsv() {
        val ds = RunDataset.parse("name,age\nalice,30\nbob,25\n")
        assertEquals(listOf("name", "age"), ds.columns)
        assertEquals(2, ds.rows.size)
        assertEquals("alice", ds.rows[0]["name"])
        assertEquals("25", ds.rows[1]["age"])
    }

    @Test
    fun handlesQuotedCsvWithCommaAndEscapedQuote() {
        val text = "name,note\n\"a,b\",\"say \"\"hi\"\"\"\n"
        val ds = RunDataset.parse(text)
        assertEquals("a,b", ds.rows[0]["name"])
        assertEquals("say \"hi\"", ds.rows[0]["note"])
    }

    @Test
    fun handlesCrlfAndTrailingBlankLine() {
        val ds = RunDataset.parse("k,v\r\n1,x\r\n2,y\r\n")
        assertEquals(2, ds.rows.size)
        assertEquals("x", ds.rows[0]["v"])
        assertEquals("y", ds.rows[1]["v"])
    }

    @Test
    fun skipsBlankRows() {
        val ds = RunDataset.parse("k,v\na,1\n\nb,2\n")
        assertEquals(2, ds.rows.size)
    }

    @Test
    fun parsesJsonArrayOfObjects() {
        val ds = RunDataset.parse("""[{"a":1,"b":"x"},{"a":2,"b":"y"}]""")
        assertEquals(listOf("a", "b"), ds.columns)
        assertEquals("1", ds.rows[0]["a"])
        assertEquals("y", ds.rows[1]["b"])
    }

    @Test
    fun jsonLeadingWhitespaceStillDetected() {
        val ds = RunDataset.parse("  \n[{\"k\":\"v\"}]")
        assertEquals("v", ds.rows[0]["k"])
    }

    @Test
    fun emptyTextFails() {
        assertError(DatasetError.EMPTY, "   \n\t ")
    }

    @Test
    fun onlyHeaderFails() {
        assertError(DatasetError.EMPTY, "a,b\n")
    }

    @Test
    fun badJsonFails() {
        assertError(DatasetError.BAD_JSON, "[{oops}]")
    }

    @Test
    fun jsonNotArrayOfObjectsFails() {
        assertError(DatasetError.NOT_ARRAY_OF_OBJECTS, "[1,2,3]")
    }

    @Test
    fun nestedJsonValueFails() {
        assertError(DatasetError.NESTED_VALUE, """[{"a":{"b":1}}]""")
        assertError(DatasetError.NESTED_VALUE, """[{"a":[1,2]}]""")
    }

    @Test
    fun csvEmptyHeaderFails() {
        assertError(DatasetError.CSV_EMPTY_HEADER, "a,,b\n1,2,3\n")
    }

    @Test
    fun csvRowMismatchFails() {
        assertError(DatasetError.CSV_ROW_MISMATCH, "a,b\n1,2,3\n")
    }

    @Test
    fun allValuesAreStrings() {
        val ds = RunDataset.parse("""[{"n":10,"b":true,"z":null}]""")
        assertEquals("10", ds.rows[0]["n"])
        assertEquals("true", ds.rows[0]["b"])
        assertEquals("", ds.rows[0]["z"])
    }
}
