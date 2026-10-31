package com.evan.pulse.domain.sse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseTranscriptTest {

    @Test
    fun rendersEventStreamFormat() {
        val t = SseTranscript()
        t.comment("connected", 0L)
        t.frame("7", "delta", "line1\nline2")
        t.frame(null, "", "plain")
        assertEquals(
            ": [1970-01-01T00:00:00Z] connected\n" +
                "id: 7\nevent: delta\ndata: line1\ndata: line2\n\n" +
                "data: plain\n\n",
            t.render()
        )
        assertEquals(2, t.frames)
    }

    @Test
    fun multiLineCommentOnlyStampsFirstLine() {
        val t = SseTranscript()
        t.comment("a\r\nb", 0L)
        assertEquals(": [1970-01-01T00:00:00Z] a\n: b\n", t.render())
    }

    @Test
    fun truncatesToFrameBoundaryKeepingTail() {
        val t = SseTranscript(maxChars = 100)
        repeat(50) { t.frame(it.toString(), "", "payload-$it") }
        val out = t.render()
        assertTrue(out.startsWith(": [truncated "))
        assertTrue(t.droppedChars > 0)
        // 裁剪后正文从完整帧开始，且保留最后一帧
        val body = out.substringAfter("\n\n")
        assertTrue(body.startsWith("id: "))
        assertTrue(body.endsWith("id: 49\ndata: payload-49\n\n"))
        assertEquals(50, t.frames)
    }

    @Test
    fun suggestedNameIsTimestamped() {
        assertTrue(Regex("""sse-\d{8}-\d{6}\.txt""").matches(SseTranscript.suggestedName(0L)))
    }
}
