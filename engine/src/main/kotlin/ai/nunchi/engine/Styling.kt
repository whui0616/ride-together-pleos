package ai.nunchi.engine

import kotlin.random.Random

enum class Background(val label: String, val requiredLevel: Int = 1) {
    AUTO("시간대 자동"), MORNING("아침"), DAY("낮"), EVENING("저녁"), NIGHT("밤"),
    SPRING("봄 벚꽃", 3), SUMMER("여름 바다", 3), AUTUMN("가을 단풍", 3), WINTER("겨울 눈", 3),
    GARAGE("차고", 10), ROOFTOP("옥상 야경", 20),
}

data class OutfitSet(val name: String, val look: Map<String, String>)

data class StylingState(
    val sets: List<OutfitSet?> = List(MAX_SETS) { null },
    val favorites: Set<String> = emptySet(),
    val recent: List<Map<String, String>> = emptyList(),
    val background: Background = Background.AUTO,
    val nameTag: Boolean = true,
    val personalityIcon: Boolean = true,
    val drivingSmall: Boolean = false,
) {
    fun saveSet(index: Int, name: String, look: Map<String, String>): StylingState {
        require(index in 0 until MAX_SETS)
        return copy(sets = sets.mapIndexed { i, s -> if (i == index) OutfitSet(name.ifBlank { "세트 ${i + 1}" }, look) else s })
    }

    fun renameSet(index: Int, name: String): StylingState =
        copy(sets = sets.mapIndexed { i, s -> if (i == index && s != null) s.copy(name = name.ifBlank { s.name }) else s })

    fun deleteSet(index: Int): StylingState = copy(sets = sets.mapIndexed { i, s -> if (i == index) null else s })

    fun toggleFavorite(itemId: String): StylingState =
        copy(favorites = if (itemId in favorites) favorites - itemId else favorites + itemId)

    fun noteWorn(look: Map<String, String>): StylingState =
        copy(recent = (listOf(look) + recent.filter { it != look }).take(RECENT_KEPT))

    companion object {
        const val MAX_SETS = 3
        const val RECENT_KEPT = 5
    }
}

object Stylist {
    val SLOTS = listOf("hair", "hairColor", "outfit", "eyes", "eyeColor", "brows", "mouth", "skin")

    fun ownedOptions(inv: Inventory, level: Int): Map<String, List<ShopItem>> =
        Catalog.ALL.filter { inv.owns(it, level) }.groupBy { it.slot }

    fun random(current: Map<String, String>, inv: Inventory, level: Int, rng: Random = Random.Default): Map<String, String> {
        val owned = ownedOptions(inv, level)
        val out = current.toMutableMap()
        for (slot in listOf("hair", "outfit")) {
            val opts = owned[slot] ?: continue
            out[slot] = opts[rng.nextInt(opts.size)].option
        }
        return out
    }

    fun recommend(current: Map<String, String>, personality: String, season: Season, inv: Inventory, level: Int): Map<String, String> {
        val owned = ownedOptions(inv, level)
        val out = current.toMutableMap()
        for (slot in listOf("hair", "outfit")) {
            val opts = owned[slot] ?: continue
            val pick = opts.firstOrNull { personality in it.recommendFor }
                ?: opts.firstOrNull { it.season == season }
                ?: continue
            out[slot] = pick.option
        }
        return out
    }

    fun completion(inv: Inventory, level: Int): Map<String, Pair<Int, Int>> =
        Catalog.ALL.groupBy { it.slot }.mapValues { (_, items) -> items.count { inv.owns(it, level) } to items.size }

    fun setEffect(look: Map<String, String>): Season? =
        look.mapNotNull { (slot, opt) -> Catalog.of(slot, opt)?.season }
            .groupingBy { it }.eachCount().entries.firstOrNull { it.value >= 2 }?.key

    fun wornItems(look: Map<String, String>): List<ShopItem> = look.mapNotNull { (s, o) -> Catalog.of(s, o) }
}
