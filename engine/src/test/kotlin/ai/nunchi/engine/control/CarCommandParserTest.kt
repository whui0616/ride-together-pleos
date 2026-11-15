package ai.nunchi.engine.control

import ai.nunchi.engine.chat.ChatReplyParser
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

class CarCommandParserTest {
    @Test
    fun parsesSentenceSet() {
        val misses = score("/car_commands.tsv")
        if (misses.isNotEmpty()) fail(misses.joinToString("\n"))
    }

    @Test
    fun holdoutAccuracy() {
        val total = rows("/car_commands_holdout.tsv").size
        val misses = score("/car_commands_holdout.tsv")
        misses.forEach(::println)
        assertTrue("독립 시험 문장 정확도 ${total - misses.size}/$total", (total - misses.size) * 10 >= total * 9)
    }

    @Test
    fun holdout2Accuracy() {
        val total = rows("/car_commands_holdout2.tsv").size
        val misses = score("/car_commands_holdout2.tsv")
        misses.forEach(::println)
        assertTrue("독립 시험 문장 2 정확도 ${total - misses.size}/$total", (total - misses.size) * 10 >= total * 9)
    }

    private fun rows(resource: String) = javaClass.getResource(resource)!!.readText().lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split("\t").let { (s, e) -> s to e.trim() } }

    private fun score(resource: String): List<String> {
        val rows = rows(resource)
        val misses = rows.mapNotNull { (sentence, expected) ->
            val got = CarCommandParser.parse(sentence)
            if (matches(got, expected)) null else "\"$sentence\" 기대 $expected, 결과 ${describe(got)}"
        }
        println("$resource: ${rows.size}개 중 ${rows.size - misses.size}개 맞음")
        return misses
    }

    @Test
    fun readsKoreanNumbers() {
        assertEquals(24, CarCommandParser.degrees("24도로"))
        assertEquals(24, CarCommandParser.degrees("이십사도로"))
        assertEquals(24, CarCommandParser.degrees("스물네도"))
        assertEquals(20, CarCommandParser.degrees("스무도"))
        assertEquals(18, CarCommandParser.degrees("열여덟도"))
        assertEquals(2, CarCommandParser.degrees("두도만"))
        assertEquals(null as Int?, CarCommandParser.degrees("고속도로"))
    }

    @Test
    fun linesFitDrivingLimit() {
        val lines = CarLines.all().map { it.second }
        lines.forEach { assertTrue("너무 긴 대사: $it", it.length <= ChatReplyParser.DRIVING_MAX) }
        assertTrue("빈칸이 남은 대사", lines.none { "null" in it })
        assertEquals("조수석 열선 켰어요.", CarLines.done(CarCommand(CarAction.SEAT_HEAT_ON, CarZone.PASSENGER)))
        assertEquals("온도를 26도로 올렸어요.", CarLines.done(CarCommand(CarAction.WARMER), celsius = 26))
        assertEquals("창문을 바꾸지 못했어요. 직접 해 주세요.", CarLines.failed(CarCommand(CarAction.WINDOW_OPEN)))
        assertEquals("좀 따뜻하게 해 드릴까요?", CarLines.ask(CarCommand(CarAction.WARMER)))
    }

    private fun matches(got: CarParse?, expected: String): Boolean {
        if (expected == "NONE") return got == null
        if (expected == "REFUSED") return got is CarParse.Refused
        if (got !is CarParse.Command) return false
        val m = SPEC.matchEntire(expected) ?: error("형식 오류: $expected")
        val (action, zone, amount, degrees, ask) = m.destructured
        val c = got.command
        return c.action.name == action &&
            (zone.isEmpty() || c.zone.name == zone) &&
            (amount.isEmpty() || c.amount.name == amount) &&
            (degrees.isEmpty() || c.degrees == degrees.toInt()) &&
            got.sure == ask.isEmpty()
    }

    private fun describe(p: CarParse?) = when (p) {
        null -> "NONE"
        is CarParse.Refused -> "REFUSED(${p.what})"
        is CarParse.Command -> with(p.command) { "$action@$zone~$amount=$degrees${if (p.sure) "" else "?"}" }
    }

    private companion object {
        val SPEC = Regex("([A-Z_]+)(?:@([A-Z]+))?(?:~([A-Z]+))?(?:=(\\d+))?(\\?)?")
    }
}
