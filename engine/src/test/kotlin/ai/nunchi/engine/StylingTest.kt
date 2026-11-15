package ai.nunchi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StylingTest {
    private val base = mapOf("hair" to "0", "hairColor" to "black", "outfit" to "0", "eyes" to "0", "skin" to "1")

    @Test fun sets_save_rename_delete() {
        var s = StylingState().saveSet(1, "", base)
        assertEquals("세트 2", s.sets[1]!!.name)
        assertNull(s.sets[0])
        s = s.renameSet(1, "출근룩")
        assertEquals("출근룩", s.sets[1]!!.name)
        s = s.deleteSet(1)
        assertNull(s.sets[1])
        assertEquals(StylingState.MAX_SETS, s.sets.size)
    }

    @Test fun recent_dedupes_and_caps() {
        var s = StylingState()
        for (i in 0 until 7) s = s.noteWorn(base + ("outfit" to "t$i"))
        s = s.noteWorn(base + ("outfit" to "t3"))
        assertEquals(StylingState.RECENT_KEPT, s.recent.size)
        assertEquals("t3", s.recent[0]["outfit"])
        assertEquals(1, s.recent.count { it["outfit"] == "t3" })
    }

    @Test fun random_only_uses_owned_and_keeps_hair_color() {
        val inv = Inventory()
        val r = Stylist.random(base + ("hairColor" to "pink"), inv, level = 1, rng = Random(3))
        assertEquals("pink", r["hairColor"])
        Stylist.wornItems(r).filter { it.slot != "hairColor" }.forEach { assertTrue(inv.owns(it, 1)) }
        val rich = Inventory(owned = Catalog.sellable.map { it.id }.toSet())
        val r2 = Stylist.random(base, rich, 1, Random(5))
        assertTrue(r2 != base)
    }

    @Test fun recommend_prefers_personality_then_season() {
        val rich = Inventory(owned = Catalog.sellable.map { it.id }.toSet())
        val r = Stylist.recommend(base, "ENERGETIC", Season.WINTER, rich, 1)
        assertEquals("3", r["hair"])
        assertEquals("2", r["outfit"])
        val poor = Stylist.recommend(base, "ENERGETIC", Season.WINTER, Inventory(), 1)
        assertEquals("0", poor["outfit"])
    }

    @Test fun completion_counts_defaults_and_level_unlocks() {
        val c = Stylist.completion(Inventory(), level = 1)
        assertEquals(1 to 26, c["outfit"])
        val c10 = Stylist.completion(Inventory(), level = 10)
        assertEquals(3 to 26, c10["outfit"])
        assertEquals(2 to 8, c["hairColor"])
    }

    @Test fun set_effect_needs_hair_color_and_outfit_of_same_season() {
        assertNull(Stylist.setEffect(base))
        val summer = base + mapOf("outfit" to "11", "hairColor" to "honey")
        assertEquals(Season.SUMMER, Stylist.setEffect(summer))
        assertFalse(Stylist.setEffect(base + mapOf("outfit" to "11")) == Season.SUMMER)
        assertNull(Stylist.setEffect(base + mapOf("top" to "11", "bottom" to "11", "shoes" to "11")))
        assertEquals(listOf("outfit:0"), Stylist.wornItems(mapOf("outfit" to "0", "top" to "11", "shoes" to "11")).map { it.id })
    }
}
