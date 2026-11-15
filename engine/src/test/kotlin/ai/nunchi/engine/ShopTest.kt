package ai.nunchi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShopTest {
    private val tee = Catalog.of("outfit", "1")!!
    private val heart = Catalog.of("outfit", "7")!!
    private val cap = Catalog.of("outfit", "8")!!

    @Test fun catalog_ids_unique_and_defaults_free() {
        assertEquals(Catalog.ALL.size, Catalog.byId.size)
        Catalog.ALL.filter { it.source == ItemSource.DEFAULT }.forEach { assertEquals(0, it.price) }
        Catalog.sellable.forEach { assertTrue(it.price > 0) }
    }

    @Test fun default_and_level_items_are_owned_without_buying() {
        val inv = Inventory()
        assertTrue(inv.owns(Catalog.of("hair", "0")!!, 1))
        assertFalse(inv.owns(cap, 4))
        assertTrue(inv.owns(cap, 5))
        assertFalse(inv.owns(tee, 99))
    }

    @Test fun clothes_are_one_outfit_slot_of_26() {
        assertEquals((0..25).map { "$it" }, Catalog.ALL.filter { it.slot == "outfit" }.map { it.option })
        assertTrue(Catalog.ALL.none { it.slot in setOf("top", "bottom", "shoes") })
        assertNull(Catalog.of("top", "3"))
        assertNull(Catalog.byId["shoes:11"])
    }

    @Test fun legacy_piece_purchase_is_ignored_not_refundable() {
        val p = Purchase(day = 3, itemId = "top:3", paid = 40)
        val inv = Inventory(owned = setOf("outfit:3"), purchases = listOf(p))
        assertTrue(inv.owns(Catalog.of("outfit", "3")!!, 1))
        assertFalse(inv.canRefund(p, 3, worn = false))
        assertNull(inv.refund(p, 3, worn = false))
        assertNull(Catalog.byId[p.itemId])
        val r = inv.buy(Catalog.of("outfit", "1")!!, 100, 1, day = 3)!!
        assertEquals(2, r.inventory.purchases.size)
    }

    @Test fun admin_owns_everything_at_level_1() {
        val admin = Inventory(admin = true)
        assertTrue(Catalog.ALL.all { admin.owns(it, 1) })
        assertNull(admin.buy(tee, 1000, 1, 0))
    }

    @Test fun buy_deducts_price_and_gives_first_bonus_once() {
        val r1 = Inventory().buy(tee, coins = 100, level = 1, day = 10)!!
        assertEquals(60, r1.paid)
        assertEquals(Catalog.FIRST_PURCHASE_BONUS, r1.bonus)
        assertTrue(r1.inventory.owns(tee, 1))
        val r2 = r1.inventory.buy(Catalog.of("outfit", "2")!!, 100, 1, 10)!!
        assertEquals(0, r2.bonus)
        assertEquals(2, r2.inventory.purchases.size)
    }

    @Test fun cannot_buy_without_coins_or_twice_or_unsellable() {
        val inv = Inventory()
        assertNull(inv.buy(tee, 59, 1, 0))
        assertNull(inv.buy(cap, 1000, 1, 0))
        val owned = inv.buy(tee, 60, 1, 0)!!.inventory
        assertNull(owned.buy(tee, 1000, 1, 0))
    }

    @Test fun daily_deal_is_deterministic_and_discounted() {
        val inv = Inventory()
        val deal = inv.dailyDeal(7)
        assertEquals(deal, inv.dailyDeal(7))
        assertEquals(deal.price * 70 / 100, inv.priceOf(deal, 7))
        val other = Catalog.sellable.first { it != deal }
        assertEquals(other.price, inv.priceOf(other, 7))
    }

    @Test fun bundle_counts_only_missing_sellables_with_discount() {
        val set = listOf(Catalog.of("hair", "0")!!, tee, Catalog.of("outfit", "3")!!, cap)
        val inv = Inventory()
        assertEquals((60 + 80) * 80 / 100, inv.bundlePrice(set, 1))
        val r = inv.buyBundle(set, 200, 1, 0)!!
        assertEquals(112, r.paid)
        assertTrue(r.inventory.owns(tee, 1) && r.inventory.owns(Catalog.of("outfit", "3")!!, 1))
        assertNull(r.inventory.buyBundle(set, 1000, 1, 0))
    }

    @Test fun box_gives_item_and_refunds_half_on_duplicate() {
        val rng = Random(1)
        val first = Inventory().openBox(100, 1, 0, rng)!!
        assertFalse(first.duplicate)
        assertEquals(0, first.refund)
        val all = Inventory(owned = Catalog.sellable.map { it.id }.toSet())
        val dup = all.openBox(100, 1, 0, Random(2))!!
        assertTrue(dup.duplicate)
        assertEquals(50, dup.refund)
        assertNull(Inventory().openBox(99, 1, 0))
    }

    @Test fun refund_same_day_not_worn_only() {
        val r = Inventory().buy(heart, 500, 1, day = 3)!!
        val p = r.inventory.purchases.single()
        assertNull(r.inventory.refund(p, 4, worn = false))
        assertNull(r.inventory.refund(p, 3, worn = true))
        val (inv, back) = r.inventory.refund(p, 3, worn = false)!!
        assertEquals(150, back)
        assertFalse(inv.owns(heart, 1))
    }

    @Test fun wishlist_toggles_and_clears_on_buy() {
        val inv = Inventory().toggleWish(tee)
        assertTrue(tee.id in inv.wishlist)
        assertFalse(tee.id in inv.toggleWish(tee).wishlist)
        assertFalse(tee.id in inv.buy(tee, 100, 1, 0)!!.inventory.wishlist)
    }

    @Test fun recommendations_skip_owned() {
        val inv = Inventory()
        val rec = inv.recommended("ENERGETIC", 1)
        assertTrue(rec.isNotEmpty() && rec.all { "ENERGETIC" in it.recommendFor })
        val after = inv.buy(rec[0], 1000, 1, 0)!!.inventory
        assertFalse(rec[0] in after.recommended("ENERGETIC", 1))
        assertNotNull(Season.of(12))
        assertEquals(Season.WINTER, Season.of(1))
    }
}
