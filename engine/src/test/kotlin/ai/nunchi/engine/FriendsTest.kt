package ai.nunchi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FriendsTest {
    private val day = 20_000L
    private val seeded = FriendState().seed(Random(1))

    @Test fun directory_codes_are_8_chars_and_looks_use_catalog_options() {
        FriendDirectory.ALL.forEach { f ->
            assertEquals(8, f.code.length)
            f.look.forEach { (slot, opt) -> assertTrue("${f.name} $slot:$opt", Catalog.of(slot, opt) != null) }
        }
    }

    @Test fun seed_gives_code_starter_friends_and_requests_once() {
        assertEquals(8, seeded.myCode.length)
        assertEquals(FriendDirectory.STARTER_FRIENDS, seeded.friends)
        assertEquals(FriendDirectory.STARTER_REQUESTS, seeded.requests)
        assertEquals(seeded, seeded.seed(Random(2)))
    }

    @Test fun add_by_code_normalizes_and_reports_why() {
        val (s, r) = seeded.add("haru-5tb6")
        assertEquals(AddResult.ADDED, r)
        assertTrue(s.isFriend("HARU5TB6"))
        assertEquals(AddResult.ALREADY, s.add("HARU5TB6").second)
        assertEquals(AddResult.NOT_FOUND, s.add("ZZZZ0000").second)
        assertEquals(AddResult.SELF, s.add(s.myCode).second)
        assertEquals(AddResult.BLOCKED, s.block("HARU5TB6").add("HARU5TB6").second)
    }

    @Test fun accept_and_decline_requests() {
        val a = seeded.accept("SEOA3KP9")
        assertTrue(a.isFriend("SEOA3KP9"))
        assertFalse("SEOA3KP9" in a.requests)
        val d = seeded.decline("DOHY8MZ2")
        assertFalse(d.isFriend("DOHY8MZ2"))
        assertFalse("DOHY8MZ2" in d.requests)
    }

    @Test fun gift_cheer_pet_once_a_day_and_raise_friendship() {
        val c = "JUNH7XQ4"
        val p0 = seeded.pointsOf(c)
        val s = seeded.gift(c, day)!!.cheer(c, day)!!
        val (s2, coins) = s.pet(c, day)!!
        assertEquals(FriendState.PET_COINS, coins)
        assertEquals(p0 + 4, s2.pointsOf(c))
        assertNull(s2.gift(c, day)); assertNull(s2.cheer(c, day)); assertNull(s2.pet(c, day))
        assertTrue(s2.canGift(c, day + 1))
        assertNull(seeded.gift("HARU5TB6", day))
    }

    @Test fun friendship_level_caps() {
        val s = seeded.copy(points = mapOf("MINJ2026" to 10_000))
        assertEquals(FriendState.MAX_FRIENDSHIP, s.friendshipLevel("MINJ2026"))
        assertEquals(1, s.friendshipLevel("NOBODY"))
    }

    @Test fun incoming_gifts_claim_once_per_day_from_online_friends() {
        val all = seeded.copy(friends = FriendDirectory.ALL.map { it.code })
        val days = (day until day + 10)
        assertTrue(days.any { all.incomingGifts(it).isNotEmpty() })
        days.forEach { d -> all.incomingGifts(d).forEach { assertTrue(FriendDirectory.find(it)!!.online) } }
        val d = days.first { all.incomingGifts(it).isNotEmpty() }
        val (after, coins) = all.claimGifts(d)!!
        assertEquals(all.incomingGifts(d).size * FriendState.GIFT_COINS, coins)
        assertTrue(after.incomingGifts(d).isEmpty())
        assertNull(after.claimGifts(d))
    }

    @Test fun remove_also_clears_favorite() {
        val s = seeded.toggleFavorite("MINJ2026").remove("MINJ2026")
        assertFalse(s.isFriend("MINJ2026"))
        assertFalse("MINJ2026" in s.favorites)
    }
}
