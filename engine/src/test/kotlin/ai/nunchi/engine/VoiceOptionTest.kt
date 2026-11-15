package ai.nunchi.engine

import ai.nunchi.engine.chat.ChatReplyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceOptionTest {
    @Test
    fun `stored id maps back and unknown falls back to default`() {
        VoiceOption.entries.forEach { assertEquals(it, VoiceOption.of(it.id)) }
        assertEquals(VoiceOption.DEFAULT, VoiceOption.of(null))
        assertEquals(VoiceOption.DEFAULT, VoiceOption.of("aria"))
    }

    @Test
    fun `spoken lines fit a driving utterance`() {
        VoiceOption.entries.forEach {
            listOf(it.sample, it.unlockLine).forEach { line ->
                assertTrue(line, line.length <= ChatReplyParser.DRIVING_MAX)
                assertTrue(line.contains(it.label))
            }
        }
    }

    @Test
    fun `starts with one basic female and one basic male`() {
        val basic = VoiceOption.entries.filter { it.tier == VoiceTier.BASIC }
        assertEquals(1, basic.count { it.female })
        assertEquals(1, basic.count { !it.female })
        basic.forEach { assertTrue(it.isUnlocked(1)) }
        assertEquals(VoiceTier.BASIC, VoiceOption.DEFAULT.tier)
        VoiceOption.entries.filter { it.tier != VoiceTier.BASIC }.forEach { assertFalse(it.id, it.isUnlocked(1)) }
    }

    @Test
    fun `unlock levels are distinct and come in order of tier`() {
        val locked = VoiceOption.entries.filter { it.tier != VoiceTier.BASIC }
        assertEquals(locked.size, locked.map { it.unlockLevel }.toSet().size)
        val maxYoung = locked.filter { it.tier == VoiceTier.YOUNG }.maxOf { it.unlockLevel }
        val minMature = locked.filter { it.tier == VoiceTier.MATURE }.minOf { it.unlockLevel }
        assertTrue(maxYoung < minMature)
    }

    @Test
    fun `eleven recorded voices, five young girls and two young boys`() {
        assertEquals(11, VoiceOption.entries.size)
        VoiceOption.entries.forEach { assertEquals(VoiceEngine.RECORDED, it.engine) }
        val young = VoiceOption.entries.filter { it.tier == VoiceTier.YOUNG }
        assertEquals(5, young.count { it.female })
        assertEquals(2, young.count { !it.female })
        VoiceOption.entries.forEach { assertTrue(it.id, it.id.matches(Regex("[a-z]+"))) }
        VoiceOption.entries.forEach { assertEquals(it.female, VoiceOption.of(it.fallbackSpeaker).female) }
    }

    @Test
    fun `locked saved voice falls back to default until its level`() {
        assertEquals(VoiceOption.DEFAULT, VoiceOption.of("seoyoon", level = 9))
        assertEquals(VoiceOption.SEOYOON, VoiceOption.of("seoyoon", level = 10))
    }

    @Test
    fun `level up reports only newly unlocked voices`() {
        assertEquals(listOf(VoiceOption.DOYUN), VoiceOption.unlockedBetween(1, 2))
        assertEquals(listOf(VoiceOption.DOHYUN), VoiceOption.unlockedBetween(2, 4))
        assertTrue(VoiceOption.unlockedBetween(3, 3).isEmpty())
    }
}
