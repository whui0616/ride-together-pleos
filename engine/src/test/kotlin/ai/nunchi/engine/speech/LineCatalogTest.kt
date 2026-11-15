package ai.nunchi.engine.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LineCatalogTest {
    private val fixed = LineCatalog.fixed()

    @Test
    fun noBlanksLeftInFixedLines() {
        fixed.forEach { assertFalse(it.text, '{' in it.text || '}' in it.text) }
    }

    @Test
    fun keysAreUnique() {
        assertEquals(fixed.size, fixed.map { it.key }.toSet().size)
    }

    @Test
    fun coversRegionsAndChat() {
        val texts = fixed.map { it.text }.toSet()
        assertTrue("성남시는 처음 와 보네요! 어떤 곳일까요?" in texts)
        assertTrue("서울 강남구는 처음 와 보네요! 어떤 곳일까요?" in texts)
        assertTrue("전주시는 한옥마을이 유명하대요. 들러 볼까요?" in texts)
        assertTrue("히터, 에어컨, 창문, 열선은 말씀만 하세요." in texts)
        assertTrue("조수석 열선 켰어요." in texts)
        assertTrue("온도를 24도로 맞췄어요." in texts)
    }

    @Test
    fun writeCatalog() {
        val dir = File("build/lines").apply { mkdirs() }
        File(dir, "fixed.tsv").writeText(fixed.joinToString("\n", postfix = "\n") { "${it.key}\t${it.text.length}\t${it.text}" })
        File(dir, "live.tsv").writeText(LineCatalog.live().joinToString("\n", postfix = "\n") { "${it.key}\t${it.text}" })
    }
}
