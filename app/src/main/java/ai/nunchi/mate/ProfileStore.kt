package ai.nunchi.mate

import ai.nunchi.engine.AchState
import ai.nunchi.engine.Background
import ai.nunchi.engine.EventGoal
import ai.nunchi.engine.EventState
import ai.nunchi.engine.FriendState
import ai.nunchi.engine.Inventory
import ai.nunchi.engine.OutfitSet
import ai.nunchi.engine.StylingState
import ai.nunchi.engine.Purchase
import ai.nunchi.engine.CompanionProfile
import ai.nunchi.engine.LobbyState
import ai.nunchi.engine.MemoryCard
import ai.nunchi.engine.ScoreState
import ai.nunchi.engine.XpEntry
import ai.nunchi.engine.XpReason
import ai.nunchi.engine.VoiceOption
import android.content.Context

class ProfileStore(context: Context) {
    companion object {
        const val DEMO_COINS = 999_999
    }

    private val prefs = context.getSharedPreferences("companion", Context.MODE_PRIVATE)

    fun load() = CompanionProfile(
        xp = prefs.getInt("xp", 0),
        distanceKm = prefs.getFloat("distanceKm", 0f),
        highwayKm = prefs.getFloat("highwayKm", 0f),
    )

    fun save(p: CompanionProfile) {
        prefs.edit()
            .putInt("xp", p.xp)
            .putFloat("distanceKm", p.distanceKm)
            .putFloat("highwayKm", p.highwayKm)
            .apply()
    }

    var score: ScoreState
        get() = ScoreState(
            earnedToday = prefs.getInt("sc.earnedToday", 0),
            earnedDay = prefs.getLong("sc.earnedDay", -1),
            ledger = prefs.getString("sc.ledger", "")!!.split(';').mapNotNull { e ->
                val p = e.split(':'); if (p.size != 3) null else runCatching { XpEntry(p[0].toLong(), XpReason.valueOf(p[1]), p[2].toInt()) }.getOrNull()
            },
            firstDriveDone = prefs.getBoolean("sc.firstDriveDone", false),
            trips = prefs.getInt("sc.trips", 0),
            tripsToday = prefs.getInt("sc.tripsToday", 0),
            tripDay = prefs.getLong("sc.tripDay", -1),
            firstDawnDone = prefs.getBoolean("sc.firstDawn", false),
            firstRainDone = prefs.getBoolean("sc.firstRain", false),
            placesVisited = prefs.getInt("sc.places", 0),
        )
        set(v) = prefs.edit()
            .putInt("sc.earnedToday", v.earnedToday).putLong("sc.earnedDay", v.earnedDay)
            .putString("sc.ledger", v.ledger.joinToString(";") { "${it.day}:${it.reason.name}:${it.xp}" })
            .putBoolean("sc.firstDriveDone", v.firstDriveDone)
            .putInt("sc.trips", v.trips).putInt("sc.tripsToday", v.tripsToday).putLong("sc.tripDay", v.tripDay)
            .putBoolean("sc.firstDawn", v.firstDawnDone).putBoolean("sc.firstRain", v.firstRainDone)
            .putInt("sc.places", v.placesVisited)
            .apply()

    var lobby: LobbyState
        get() = LobbyState(
            streak = prefs.getInt("streak", 0),
            lastVisitDay = prefs.getLong("lastVisitDay", -1),
            affinity = prefs.getInt("affinity", 0),
            petsToday = prefs.getInt("petsToday", 0),
            petDay = prefs.getLong("petDay", -1),
            coins = if (adminMode) DEMO_COINS else prefs.getInt("coins", 0).let { if (it >= DEMO_COINS / 10) 0 else it },

            mood = prefs.getInt("mood", 70),
            snacksToday = prefs.getInt("snacksToday", 0),
            snackDay = prefs.getLong("snackDay", -1),
            talksToday = prefs.getInt("talksToday", 0),
            talkDay = prefs.getLong("talkDay", -1),

            missionClaimedDay = prefs.getLong("missionClaimedDay", -1),
            rewardClaimedStreak = prefs.getInt("rewardClaimedStreak", 0),
            visitDays = prefs.getString("visitDays", "")!!.split(',').mapNotNull { it.toLongOrNull() },
        )
        set(v) = prefs.edit()
            .putInt("streak", v.streak)
            .putLong("lastVisitDay", v.lastVisitDay)
            .putInt("affinity", v.affinity)
            .putInt("petsToday", v.petsToday)
            .putLong("petDay", v.petDay)
            .putInt("coins", if (adminMode) prefs.getInt("coins", 0) else v.coins)
            .putInt("mood", v.mood)
            .putInt("snacksToday", v.snacksToday)
            .putLong("snackDay", v.snackDay)
            .putInt("talksToday", v.talksToday)
            .putLong("talkDay", v.talkDay)

            .putLong("missionClaimedDay", v.missionClaimedDay)
            .putInt("rewardClaimedStreak", v.rewardClaimedStreak)
            .putString("visitDays", v.visitDays.joinToString(","))
            .apply()

    var ach: AchState
        get() = AchState(
            counters = intMap(prefs.getString("ach.counters", "")!!),
            tiers = intMap(prefs.getString("ach.tiers", "")!!),
            unlockedDay = prefs.getString("ach.days", "")!!.split(";").mapNotNull { e -> val p = e.split(":"); if (p.size != 2) null else p[1].toLongOrNull()?.let { p[0] to it } }.toMap(),
            recent = prefs.getString("ach.recent", "")!!.split(",").filter { it.isNotEmpty() },
            album = prefs.getString("ach.album", "")!!.split('\u001E').mapNotNull { e ->
                val p = e.split('\u001F')
                if (p.size != 6) null else runCatching { MemoryCard(p[0], p[1].toInt(), p[2].toLong(), p[3], p[4], p[5]) }.getOrNull()
            },
        )
        set(v) = prefs.edit()
            .putString("ach.counters", v.counters.entries.joinToString(";") { "${it.key}:${it.value}" })
            .putString("ach.tiers", v.tiers.entries.joinToString(";") { "${it.key}:${it.value}" })
            .putString("ach.days", v.unlockedDay.entries.joinToString(";") { "${it.key}:${it.value}" })
            .putString("ach.recent", v.recent.joinToString(","))
            .putString("ach.album", v.album.joinToString("\u001E") { listOf(it.achId, it.tier, it.day, it.place, it.look, it.line).joinToString("\u001F") })
            .apply()

    private fun intMap(s: String): Map<String, Int> =
        s.split(";").mapNotNull { e -> val p = e.split(":"); if (p.size != 2) null else p[1].toIntOrNull()?.let { p[0] to it } }.toMap()

    var adminMode: Boolean
        get() = prefs.getBoolean("admin", false)
        set(v) = prefs.edit().putBoolean("admin", v).apply()

    var inventory: Inventory
        get() = Inventory(
            owned = prefs.getString("inv.owned", "")!!.split(",").filter { it.isNotEmpty() }.map(::mergeLegacyPieces).toSet(),
            purchases = prefs.getString("inv.purchases", "")!!.split(";").mapNotNull { e -> val p = e.split("|"); if (p.size != 3) null else runCatching { Purchase(p[0].toLong(), p[1], p[2].toInt()) }.getOrNull() },
            wishlist = prefs.getString("inv.wish", "")!!.split(",").filter { it.isNotEmpty() }.map(::mergeLegacyPieces).toSet(),
            firstPurchaseDone = prefs.getBoolean("inv.first", false),
            admin = adminMode,
        )
        set(v) = prefs.edit()
            .putString("inv.owned", v.owned.joinToString(","))
            .putString("inv.purchases", v.purchases.joinToString(";") { "${it.day}|${it.itemId}|${it.paid}" })
            .putString("inv.wish", v.wishlist.joinToString(","))
            .putBoolean("inv.first", v.firstPurchaseDone)
            .apply()

    private fun mergeLegacyPieces(id: String): String {
        val i = id.indexOf(':')
        return if (i > 0 && id.substring(0, i) in setOf("top", "bottom", "shoes")) "outfit:${id.substring(i + 1)}" else id
    }

    var styling: StylingState
        get() = StylingState(
            sets = (0 until StylingState.MAX_SETS).map { i -> prefs.getString("sty.set$i", null)?.let { s -> val p = s.split("|"); if (p.size != 2) null else OutfitSet(p[0], lookMap(p[1])) } },
            favorites = prefs.getString("sty.fav", "")!!.split(",").filter { it.isNotEmpty() }.map(::mergeLegacyPieces).toSet(),
            recent = prefs.getString("sty.recent", "")!!.split(";").filter { it.isNotEmpty() }.map { lookMap(it) },
            background = prefs.getString("sty.bg", null)?.let { runCatching { Background.valueOf(it) }.getOrNull() } ?: Background.AUTO,
            nameTag = prefs.getBoolean("sty.nameTag", true),
            personalityIcon = prefs.getBoolean("sty.pIcon", true),
            drivingSmall = prefs.getBoolean("sty.small", false),
        )
        set(v) = prefs.edit().apply {
            v.sets.forEachIndexed { i, s -> putString("sty.set$i", s?.let { "${it.name}|${lookStr(it.look)}" }) }
            putString("sty.fav", v.favorites.joinToString(","))
            putString("sty.recent", v.recent.joinToString(";") { lookStr(it) })
            putString("sty.bg", v.background.name)
            putBoolean("sty.nameTag", v.nameTag).putBoolean("sty.pIcon", v.personalityIcon).putBoolean("sty.small", v.drivingSmall)
        }.apply()

    var events: EventState
        get() = EventState(
            baselines = prefs.getString("evt.base", "")!!.split(";").mapNotNull { e ->
                val i = e.indexOf("="); if (i <= 0) null
                else e.substring(0, i) to intMap(e.substring(i + 1).replace(",", ";")).mapNotNull { (k, v) -> runCatching { EventGoal.valueOf(k) }.getOrNull()?.let { it to v } }.toMap()
            }.toMap(),
            done = prefs.getString("evt.done", "")!!.split(",").filter { it.isNotEmpty() }.toSet(),
            claimed = prefs.getString("evt.claimed", "")!!.split(",").filter { it.isNotEmpty() }.toSet(),
            tickets = prefs.getInt("evt.tickets", 0),
        )
        set(v) = prefs.edit()
            .putString("evt.base", v.baselines.entries.joinToString(";") { (k, m) -> "$k=" + m.entries.joinToString(",") { "${it.key.name}:${it.value}" } })
            .putString("evt.done", v.done.joinToString(","))
            .putString("evt.claimed", v.claimed.joinToString(","))
            .putInt("evt.tickets", v.tickets)
            .apply()

    var friends: FriendState
        get() = FriendState(
            myCode = prefs.getString("fr.code", "")!!,
            friends = prefs.getString("fr.list", "")!!.split(",").filter { it.isNotEmpty() },
            requests = prefs.getString("fr.req", "")!!.split(",").filter { it.isNotEmpty() },
            blocked = prefs.getString("fr.blocked", "")!!.split(",").filter { it.isNotEmpty() }.toSet(),
            favorites = prefs.getString("fr.fav", "")!!.split(",").filter { it.isNotEmpty() }.toSet(),
            points = intMap(prefs.getString("fr.points", "")!!),
            giftDay = longMap(prefs.getString("fr.gift", "")!!),
            cheerDay = longMap(prefs.getString("fr.cheer", "")!!),
            petDay = longMap(prefs.getString("fr.pet", "")!!),
            receivedDay = prefs.getLong("fr.received", -1),
        )
        set(v) = prefs.edit()
            .putString("fr.code", v.myCode)
            .putString("fr.list", v.friends.joinToString(","))
            .putString("fr.req", v.requests.joinToString(","))
            .putString("fr.blocked", v.blocked.joinToString(","))
            .putString("fr.fav", v.favorites.joinToString(","))
            .putString("fr.points", v.points.entries.joinToString(";") { "${it.key}:${it.value}" })
            .putString("fr.gift", v.giftDay.entries.joinToString(";") { "${it.key}:${it.value}" })
            .putString("fr.cheer", v.cheerDay.entries.joinToString(";") { "${it.key}:${it.value}" })
            .putString("fr.pet", v.petDay.entries.joinToString(";") { "${it.key}:${it.value}" })
            .putLong("fr.received", v.receivedDay)
            .apply()

    private fun longMap(s: String): Map<String, Long> =
        s.split(";").mapNotNull { e -> val p = e.split(":"); if (p.size != 2) null else p[1].toLongOrNull()?.let { p[0] to it } }.toMap()

    private fun lookStr(m: Map<String, String>) = m.entries.joinToString(",") { "${it.key}=${it.value}" }
    private fun lookMap(s: String): Map<String, String> = s.split(",").mapNotNull { e -> val i = e.indexOf("="); if (i <= 0) null else e.substring(0, i) to e.substring(i + 1) }.toMap()

    var starterGranted: Boolean
        get() = prefs.getBoolean("inv.starter", false)
        set(v) = prefs.edit().putBoolean("inv.starter", v).apply()

    var lastDrive: String?
        get() = prefs.getString("lastDrive", null)
        set(value) = prefs.edit().putString("lastDrive", value).apply()

    var fortuneDay: Long
        get() = prefs.getLong("fortuneDay", -1)
        set(value) = prefs.edit().putLong("fortuneDay", value).apply()
    var fortuneIndex: Int
        get() = prefs.getInt("fortuneIndex", 0)
        set(value) = prefs.edit().putInt("fortuneIndex", value).apply()

    var birthday: String?
        get() = prefs.getString("birthday", null)
        set(value) = prefs.edit().putString("birthday", value).apply()

    var voice: VoiceOption
        get() = VoiceOption.of(prefs.getString("voiceSpeaker", null))
        set(value) = prefs.edit().putString("voiceSpeaker", value.id).apply()

    var alwaysListen: Boolean
        get() = prefs.getBoolean("alwaysListen", false)
        set(value) = prefs.edit().putBoolean("alwaysListen", value).apply()
}
