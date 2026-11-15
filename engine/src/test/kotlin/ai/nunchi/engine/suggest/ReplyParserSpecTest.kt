package ai.nunchi.engine.suggest

import ai.nunchi.engine.Reply
import ai.nunchi.engine.ReplyParser

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyParserSpecTest {

    private fun assertAll(expected: Reply, inputs: List<String>) {
        for (i in inputs) assertEquals("input=\"$i\"", expected, ReplyParser.parse(i))
    }

    @Test fun yes_keywords_from_spec() = assertAll(
        Reply.YES,
        listOf("응", "어", "그래", "좋아", "좋지", "가자", "갈래", "콜", "오케이", "ok", "해줘", "넣어", "들를래", "들르자", "네", "예"),
    )

    @Test fun no_keywords_from_spec() = assertAll(
        Reply.NO,
        listOf("아니", "아냐", "싫어", "됐어", "괜찮아", "다음에", "패스", "노", "안 가", "안가", "말고", "나중에"),
    )

    @Test fun later_keywords_from_spec() = assertAll(
        Reply.LATER,
        listOf("잠깐", "잠시", "이따", "조금 있다", "기다려"),
    )

    @Test fun traps_negative_before_positive() {
        assertEquals(Reply.NO, ReplyParser.parse("안 가"))
        assertEquals(Reply.NO, ReplyParser.parse("안가"))
        assertEquals(Reply.NO, ReplyParser.parse("괜찮아"))
        assertEquals(Reply.NO, ReplyParser.parse("아니 좋아"))
        assertEquals(Reply.NO, ReplyParser.parse("거기 말고"))
    }

    @Test fun long_sentences_resolve_by_keyword() {
        assertEquals(Reply.YES, ReplyParser.parse("그래 가자"))
        assertEquals(Reply.YES, ReplyParser.parse("음, 좋아!"))
        assertEquals(Reply.LATER, ReplyParser.parse("잠깐만 기다려 봐"))
    }

    @Test fun english_ok_case_insensitive() {
        assertEquals(Reply.YES, ReplyParser.parse("OK"))
        assertEquals(Reply.YES, ReplyParser.parse("Ok."))
        assertEquals(Reply.YES, ReplyParser.parse(" o k "))
    }

    @Test fun empty_and_null_are_unknown() {
        assertEquals(Reply.UNKNOWN, ReplyParser.parse(""))
        assertEquals(Reply.UNKNOWN, ReplyParser.parse(null))
        assertEquals(Reply.UNKNOWN, ReplyParser.parse("   ..."))
    }

    @Test fun unrelated_is_unknown() {
        assertEquals(Reply.UNKNOWN, ReplyParser.parse("라디오 켜줘"))
    }

    @Test fun normalize_strips_space_punct_and_lowercases() {
        assertEquals("안가", ReplyParser.normalize("안 가!"))
        assertEquals("ok", ReplyParser.normalize("O.K."))
        assertEquals("", ReplyParser.normalize(null))
    }
}
