package ai.nunchi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingTest {
    private val me = RankEntry(null, "나", level = 40, distanceKm = 100, hearts = 3, achTiers = 200)

    @Test fun every_ranker_wears_an_aura() {
        val ids = setOf("water", "fire", "electric", "rainbow", "ice", "wind", "nature", "shadow", "light", "cosmic")
        assertTrue(Ranking.ACCOUNTS.all { it.look["aura"] in ids })
    }

    @Test fun every_board_is_descending_and_includes_me_once() {
        RankBoard.entries.forEach { b ->
            val rows = Ranking.board(b, me)
            assertEquals(Ranking.ACCOUNTS.size + 1, rows.size)
            assertEquals(1, rows.count { it.isMe })
            assertTrue(rows.zipWithNext().all { (x, y) -> b.value(x) >= b.value(y) })
        }
    }

    @Test fun me_ranks_by_the_chosen_board() {
        assertTrue(Ranking.board(RankBoard.ACHIEVEMENTS, me).first().isMe)
        assertTrue(Ranking.board(RankBoard.DISTANCE, me).last().isMe)
    }

    @Test fun my_heart_adds_one() {
        val a = Ranking.ACCOUNTS.first()
        val row = Ranking.board(RankBoard.HEARTS, me, setOf(a.code)).first { it.code == a.code }
        assertEquals(a.hearts + 1, row.hearts)
    }

    @Test fun account_codes_unique_and_backgrounds_valid() {
        assertEquals(Ranking.ACCOUNTS.size, Ranking.ACCOUNTS.map { it.code }.toSet().size)
        Ranking.ACCOUNTS.forEach { Background.valueOf(it.background) }
    }

    @Test fun same_car_board_keeps_only_that_car_and_me() {
        val rows = Ranking.board(RankBoard.LEVEL, me, car = Ranking.MY_CAR)
        val same = Ranking.ACCOUNTS.filter { it.car == Ranking.MY_CAR }
        assertTrue(same.size in 2 until Ranking.ACCOUNTS.size)
        assertEquals(same.map { it.code }.toSet() + null, rows.map { it.code }.toSet())
        assertTrue(Ranking.ACCOUNTS.all { it.car.isNotEmpty() })
    }

    @Test fun by_car_sections_cover_everyone_once_with_my_car_first() {
        val sections = Ranking.byCar(RankBoard.LEVEL, me)
        assertEquals(Ranking.MY_CAR, sections.first().first)
        assertEquals(1, sections.sumOf { s -> s.second.count { it.isMe } })
        assertTrue(sections.first().second.any { it.isMe })
        assertEquals(Ranking.ACCOUNTS.map { it.code }.sorted(), sections.flatMap { it.second }.mapNotNull { it.code }.sorted())
        sections.forEach { (_, rows) -> assertEquals(rows.sortedByDescending { it.level }.map { it.level }, rows.map { it.level }) }
    }
}
