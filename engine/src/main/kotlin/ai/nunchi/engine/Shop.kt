package ai.nunchi.engine

import kotlin.random.Random

enum class Rarity(val label: String, val badge: String) {
    COMMON("일반", "⚪"), FINE("고급", "🟢"), RARE("희귀", "🔵"), LEGEND("전설", "🟣"),
}

enum class ItemSource(val label: String) { DEFAULT("기본"), SHOP("상점"), LEVEL("레벨 해금"), ACHIEVEMENT("업적"), EVENT("이벤트") }

enum class Season(val label: String) { SPRING("봄"), SUMMER("여름"), AUTUMN("가을"), WINTER("겨울");
    companion object { fun of(month: Int) = when (month) { in 3..5 -> SPRING; in 6..8 -> SUMMER; in 9..11 -> AUTUMN; else -> WINTER } }
}

data class ShopItem(
    val slot: String,
    val option: String,
    val name: String,
    val price: Int,
    val rarity: Rarity = Rarity.COMMON,
    val source: ItemSource = ItemSource.SHOP,
    val requiredLevel: Int = 1,
    val recommendFor: Set<String> = emptySet(),
    val season: Season? = null,
) {
    val id: String get() = "$slot:$option"
    val sellable: Boolean get() = source == ItemSource.SHOP
}

object Catalog {
    private fun i(slot: String, option: String, name: String, price: Int, rarity: Rarity = Rarity.COMMON, source: ItemSource = ItemSource.SHOP, level: Int = 1, rec: Set<String> = emptySet(), season: Season? = null) =
        ShopItem(slot, option, name, price, rarity, source, level, rec, season)

    val ALL: List<ShopItem> = listOf(
        i("hair", "0", "스파이키 / 하이 포니테일", 0, source = ItemSource.DEFAULT),
        i("hair", "1", "사이드 파트 / 웨이브 단발", 0, source = ItemSource.DEFAULT),
        i("hair", "2", "웨이브 가르마 / 하프업 웨이브", 80, Rarity.FINE, rec = setOf("WARM")),
        i("hair", "3", "소프트 프린지 / 트윈테일", 120, Rarity.FINE, rec = setOf("ENERGETIC", "PLAYFUL")),
        i("hair", "4", "텍스처 크롭 / 롱 스트레이트", 120, Rarity.FINE, rec = setOf("CALM")),
        i("hair", "5", "슬릭 백 / 픽시 단발", 0, Rarity.RARE, ItemSource.LEVEL, level = 30, rec = setOf("COOL")),
        i("hair", "6", "텍스처 투블럭 / 히메컷", 100, Rarity.FINE, rec = setOf("COOL")),
        i("hair", "7", "쉐도우 펌 / 하이 번", 120, Rarity.FINE, rec = setOf("WARM", "PLAYFUL")),
        i("hair", "8", "숏 울프컷 / 비대칭 숏컷", 0, Rarity.RARE, ItemSource.LEVEL, level = 15, rec = setOf("ENERGETIC")),
        i("hair", "9", "6:4 슬릭 파트 / 사이드 브레이드", 150, Rarity.RARE, rec = setOf("CALM", "COOL")),
        i("hairColor", "black", "블랙", 0, source = ItemSource.DEFAULT),
        i("hairColor", "brown", "딥브라운", 0, source = ItemSource.DEFAULT),
        i("hairColor", "ash", "애쉬브라운", 60, rec = setOf("CALM", "COOL")),
        i("hairColor", "honey", "허니블론드", 90, Rarity.FINE, rec = setOf("ENERGETIC"), season = Season.SUMMER),
        i("hairColor", "pink", "더스티핑크", 100, Rarity.FINE, season = Season.SPRING),
        i("hairColor", "chestnut", "체스트넛", 150, Rarity.RARE, season = Season.AUTUMN),
        i("hairColor", "silver", "실버", 150, Rarity.RARE, season = Season.WINTER),
        i("hairColor", "navy", "블루블랙", 250, Rarity.LEGEND),
        i("outfit", "0", "스트리트 / 걸 스트리트", 0, source = ItemSource.DEFAULT),
        i("outfit", "1", "미니멀 니트 / 내추럴 가디건", 60, rec = setOf("CALM", "WARM"), season = Season.AUTUMN),
        i("outfit", "2", "캠퍼스 / 시티 캐주얼", 60, rec = setOf("ENERGETIC")),
        i("outfit", "3", "블랙 집업 / 데님 스커트", 80, rec = setOf("COOL")),
        i("outfit", "4", "내추럴 가디건 / 프레피 니트", 80, Rarity.FINE, rec = setOf("WARM"), season = Season.AUTUMN),
        i("outfit", "5", "라운지 후디 / 라운지 후디", 80, Rarity.FINE, rec = setOf("PLAYFUL")),
        i("outfit", "6", "데님 캐주얼 / 캠퍼스 데님", 100, Rarity.FINE, season = Season.SPRING),
        i("outfit", "7", "프레피 블레이저 / 트렌치 룩", 150, Rarity.RARE, rec = setOf("CALM", "COOL"), season = Season.AUTUMN),
        i("outfit", "8", "클린 셔츠 / 모던 수트", 0, source = ItemSource.LEVEL, level = 5, season = Season.SPRING),
        i("outfit", "9", "아웃도어 파카 / 소프트 원피스", 0, Rarity.FINE, ItemSource.LEVEL, level = 10),
        i("outfit", "10", "시티 코트 / 브라운 데님", 0, Rarity.RARE, ItemSource.LEVEL, level = 20, season = Season.WINTER),
        i("outfit", "11", "마린 쇼츠 / 마린 쇼츠", 120, Rarity.FINE, rec = setOf("ENERGETIC", "PLAYFUL"), season = Season.SUMMER),
        i("outfit", "12", "레더 봄버 / 슬림 V니트", 100, Rarity.FINE, rec = setOf("COOL")),
        i("outfit", "13", "니트 폴로 / 와인 랩 톱", 120, Rarity.FINE, rec = setOf("CALM", "WARM"), season = Season.AUTUMN),
        i("outfit", "14", "데님 트러커 / 트위드 재킷", 100, rec = setOf("ENERGETIC"), season = Season.SPRING),
        i("outfit", "15", "테크 윈드브레이커 / 데님 재킷·카고 스커트", 120, Rarity.FINE, rec = setOf("ENERGETIC", "PLAYFUL")),
        i("outfit", "16", "모카 가디건 / 오프숄더 그래픽 티", 80, rec = setOf("WARM", "PLAYFUL")),
        i("outfit", "17", "럭비 스웨터 / 파우더 블라우스", 0, Rarity.FINE, ItemSource.LEVEL, level = 15, season = Season.SPRING),
        i("outfit", "18", "캠프 셔츠 / 그레이 가디건", 100, rec = setOf("CALM")),
        i("outfit", "19", "레이어드 바시티 / 크롭 가디건", 0, Rarity.RARE, ItemSource.LEVEL, level = 25, rec = setOf("COOL", "ENERGETIC")),
        i("outfit", "20", "왕실 기사 / 장미 공주", 150, Rarity.RARE),
        i("outfit", "21", "별빛 마법사 / 숲의 요정", 150, Rarity.RARE),
        i("outfit", "22", "중세 모험가 / 은빛 여기사", 150, Rarity.RARE),
        i("outfit", "23", "세이지 토끼 잠옷 / 핑크 토끼 잠옷", 120, Rarity.FINE),
        i("outfit", "24", "초코 곰 잠옷 / 카라멜 곰 잠옷", 120, Rarity.FINE),
        i("outfit", "25", "차콜 고양이 잠옷 / 라일락 고양이 잠옷", 120, Rarity.FINE),
    )

    val byId: Map<String, ShopItem> = ALL.associateBy { it.id }
    val sellable: List<ShopItem> = ALL.filter { it.sellable }
    val exchange: List<ShopItem> = ALL.filter { it.source == ItemSource.EVENT }
    fun of(slot: String, option: String): ShopItem? = byId["$slot:$option"]

    const val DAILY_DEAL_OFF_PERCENT = 30
    const val BUNDLE_OFF_PERCENT = 20
    const val BOX_PRICE = 100
    const val BOX_DUP_REFUND_PERCENT = 50
    const val FIRST_PURCHASE_BONUS = 30
}

data class Purchase(val day: Long, val itemId: String, val paid: Int)

data class BuyResult(val inventory: Inventory, val paid: Int, val bonus: Int)

data class BoxResult(val inventory: Inventory, val item: ShopItem, val duplicate: Boolean, val refund: Int)

data class Inventory(
    val owned: Set<String> = emptySet(),
    val purchases: List<Purchase> = emptyList(),
    val wishlist: Set<String> = emptySet(),
    val firstPurchaseDone: Boolean = false,
    val admin: Boolean = false,
) {
    fun owns(item: ShopItem, level: Int): Boolean = admin || when (item.source) {
        ItemSource.DEFAULT -> true
        ItemSource.LEVEL -> level >= item.requiredLevel || item.id in owned
        else -> item.id in owned
    }

    fun ownedCount(): Int = owned.size

    fun dailyDeal(day: Long): ShopItem = Catalog.sellable[Math.floorMod(day, Catalog.sellable.size.toLong()).toInt()]

    fun priceOf(item: ShopItem, day: Long): Int =
        if (item == dailyDeal(day)) item.price * (100 - Catalog.DAILY_DEAL_OFF_PERCENT) / 100 else item.price

    fun bundlePrice(items: List<ShopItem>, level: Int): Int {
        val need = items.filter { it.sellable && !owns(it, level) }
        return need.sumOf { it.price } * (100 - Catalog.BUNDLE_OFF_PERCENT) / 100
    }

    fun missing(items: List<ShopItem>, level: Int): List<ShopItem> = items.filter { it.sellable && !owns(it, level) }

    fun buy(item: ShopItem, coins: Int, level: Int, day: Long): BuyResult? {
        if (!item.sellable || owns(item, level)) return null
        val price = priceOf(item, day)
        if (coins < price) return null
        return finish(listOf(item to price), day)
    }

    fun buyBundle(items: List<ShopItem>, coins: Int, level: Int, day: Long): BuyResult? {
        val need = missing(items, level)
        if (need.isEmpty()) return null
        val total = bundlePrice(items, level)
        if (coins < total) return null
        return finish(need.mapIndexed { idx, it -> it to if (idx == 0) total else 0 }, day)
    }

    private fun finish(bought: List<Pair<ShopItem, Int>>, day: Long): BuyResult {
        val bonus = if (!firstPurchaseDone) Catalog.FIRST_PURCHASE_BONUS else 0
        val inv = copy(
            owned = owned + bought.map { it.first.id },
            purchases = (purchases + bought.map { Purchase(day, it.first.id, it.second) }).takeLast(PURCHASES_KEPT),
            wishlist = wishlist - bought.map { it.first.id }.toSet(),
            firstPurchaseDone = true,
        )
        return BuyResult(inv, bought.sumOf { it.second }, bonus)
    }

    fun openBox(coins: Int, level: Int, day: Long, rng: Random = Random.Default): BoxResult? {
        if (coins < Catalog.BOX_PRICE) return null
        val pool = Catalog.sellable
        val item = pool[rng.nextInt(pool.size)]
        val dup = owns(item, level)
        val inv = copy(
            owned = owned + item.id,
            purchases = (purchases + Purchase(day, item.id, Catalog.BOX_PRICE)).takeLast(PURCHASES_KEPT),
            wishlist = wishlist - item.id,
            firstPurchaseDone = true,
        )
        return BoxResult(inv, item, dup, if (dup) Catalog.BOX_PRICE * Catalog.BOX_DUP_REFUND_PERCENT / 100 else 0)
    }

    fun canRefund(p: Purchase, day: Long, worn: Boolean): Boolean = p.day == day && !worn && p.paid > 0 && p.itemId in owned
    fun refund(p: Purchase, day: Long, worn: Boolean): Pair<Inventory, Int>? {
        if (!canRefund(p, day, worn)) return null
        return copy(owned = owned - p.itemId, purchases = purchases - p) to p.paid
    }

    fun exchange(item: ShopItem, day: Long): Inventory? {
        if (item.source != ItemSource.EVENT || item.id in owned) return null
        return copy(owned = owned + item.id, purchases = (purchases + Purchase(day, item.id, 0)).takeLast(PURCHASES_KEPT))
    }

    fun toggleWish(item: ShopItem): Inventory = copy(wishlist = if (item.id in wishlist) wishlist - item.id else wishlist + item.id)

    fun recommended(personality: String, level: Int, limit: Int = 3): List<ShopItem> =
        Catalog.sellable.filter { personality in it.recommendFor && !owns(it, level) }.take(limit)

    fun seasonal(season: Season): List<ShopItem> = Catalog.sellable.filter { it.season == season }

    companion object { const val PURCHASES_KEPT = 30 }
}
