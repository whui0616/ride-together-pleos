package ai.nunchi.engine.situation

import ai.nunchi.engine.CompanionProfile
import ai.nunchi.engine.DrivingSignals
import ai.nunchi.engine.LoadZone
import ai.nunchi.engine.Message
import ai.nunchi.engine.Persona
import ai.nunchi.engine.WiperLevel
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId

enum class RegionVisit { NONE, FIRST, REVISIT, REGULAR }

interface SituationMemory {
    fun get(key: String): Long?
    fun put(key: String, value: Long)

    class InMemory : SituationMemory {
        private val values = mutableMapOf<String, Long>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: Long) {
            values[key] = value
        }
    }
}

class SituationDetector(
    private val memory: SituationMemory = SituationMemory.InMemory(),
    private val gapMs: Long = 90_000,
) {
    private var prev: DrivingSignals? = null
    private var prevProfile: CompanionProfile? = null
    private val lastFiredMs = mutableMapOf<Situation, Long>()
    private var lastLightMs = Long.MIN_VALUE / 2

    private var lastMovingMs: Long? = null
    private var tripStartMs = 0L
    private var tripMaxKmh = 0f
    private var wiperOnSinceMs: Long? = null
    private var highSince: Long? = null
    private var onHighway = false
    private var leaveHighwaySince: Long? = null
    private var slowSince: Long? = null
    private var inJam = false
    private var clearSince: Long? = null
    private var fastSince: Long? = null
    private var wasTooFast = false
    private var stoppedSince: Long? = null
    private var longRedSaid = false
    private var hardBrakePending = false
    private var calmSince: Long? = null
    private var lastRedMs: Long? = null

    private val events = ArrayDeque<Event>()
    private var lastTickMs = 0L
    private var currentRegion: Region? = null

    private var route: Route? = null
    private var arrivedAtMs: Long? = null

    private class Route(val name: String, val km: Float, val minutes: Int, val startMs: Long) {
        var tenMinutesSaid = false
    }

    fun tick(
        s: DrivingSignals,
        zone: LoadZone,
        profile: CompanionProfile,
        time: LocalDateTime,
        nowMs: Long,
        lastSpokeAtMs: Long,
    ): List<Message> {
        lastTickMs = nowMs
        val p = prev
        val pp = prevProfile
        prev = s
        prevProfile = profile
        if (p == null || pp == null) {
            remember(profile, time)
            return emptyList()
        }

        val out = mutableListOf<Message>()
        val light = mutableListOf<Pair<Situation, Map<String, String>>>()
        val moving = !s.isParked && s.speedKmh >= MOVING_KMH
        val stopped = !s.isParked && s.speedKmh == 0f

        if (moving) {
            val last = lastMovingMs
            if (last == null || nowMs - last >= TRIP_GAP_MS) {
                tripStartMs = nowMs
                tripMaxKmh = 0f
                greeting(time)?.let { event(it, nowMs) }
            }
            lastMovingMs = nowMs
            tripMaxKmh = maxOf(tripMaxKmh, s.speedKmh)
        }

        if (moving && zone == LoadZone.GREEN) {
            val t = time.toLocalTime()
            when {
                t in LUNCH_FROM..LUNCH_TO -> light += Situation.LUNCH to emptyMap()
                t in DINNER_FROM..DINNER_TO -> light += Situation.DINNER to emptyMap()
                t >= LocalTime.of(22, 0) -> light += Situation.LATE_NIGHT to emptyMap()
                t < LocalTime.of(5, 0) -> light += Situation.DAWN to emptyMap()
                t in LocalTime.of(14, 0)..LocalTime.of(16, 0) && nowMs - tripStartMs >= 20 * MINUTE ->
                    light += Situation.AFTERNOON to emptyMap()
            }
        }

        if (p.wiper != s.wiper) {
            when {
                s.wiper == WiperLevel.HIGH -> fire(Situation.HEAVY_RAIN, nowMs, 30 * MINUTE)?.let(out::add)
                p.wiper == WiperLevel.OFF -> event(Situation.RAIN, nowMs, emptyMap())
                s.wiper == WiperLevel.OFF && (wiperOnSinceMs?.let { nowMs - it >= 5 * MINUTE } == true) ->
                    event(Situation.RAIN_STOPPED, nowMs, emptyMap())
            }
            if (p.wiper == WiperLevel.OFF) wiperOnSinceMs = nowMs
            if (s.wiper == WiperLevel.OFF) wiperOnSinceMs = null
        }

        highSince = if (s.speedKmh >= 80f) highSince ?: nowMs else null
        if (!onHighway && highSince?.let { nowMs - it >= MINUTE } == true) {
            onHighway = true
            event(Situation.HIGHWAY_IN, nowMs, emptyMap())
        }
        if (onHighway) {
            leaveHighwaySince = if (s.speedKmh < 60f) leaveHighwaySince ?: nowMs else null
            if (leaveHighwaySince?.let { nowMs - it >= MINUTE } == true) {
                onHighway = false
                leaveHighwaySince = null
                event(Situation.HIGHWAY_OUT, nowMs, emptyMap())
            }
        }

        slowSince = if (!s.isParked && s.speedKmh < 15f && tripMaxKmh >= 30f) slowSince ?: nowMs else null
        if (!inJam && slowSince?.let { nowMs - it >= 5 * MINUTE } == true) {
            inJam = true
            event(Situation.JAM, nowMs, emptyMap())
        }
        if (inJam) {
            clearSince = if (s.speedKmh >= 40f) clearSince ?: nowMs else null
            if (clearSince?.let { nowMs - it >= MINUTE } == true) {
                inJam = false
                clearSince = null
                event(Situation.JAM_CLEARED, nowMs, emptyMap())
            }
        }

        fastSince = if (s.speedKmh >= 110f) fastSince ?: nowMs else null
        if (fastSince?.let { nowMs - it >= 20_000 } == true) wasTooFast = true
        if (wasTooFast && s.speedKmh <= 30f) {
            wasTooFast = false
            event(Situation.TOO_FAST, nowMs, emptyMap())
        }

        stoppedSince = if (stopped) stoppedSince ?: nowMs else null
        if (stoppedSince == null) longRedSaid = false
        if (!longRedSaid && stoppedSince?.let { nowMs - it >= MINUTE } == true) {
            longRedSaid = true
            event(Situation.LONG_RED, nowMs, emptyMap())
        }

        if (s.decelerationMps2 >= HARD_BRAKE_MPS2 && p.decelerationMps2 < HARD_BRAKE_MPS2) hardBrakePending = true
        if (hardBrakePending && s.speedKmh == 0f) {
            hardBrakePending = false
            event(Situation.AFTER_HARD_BRAKE, nowMs, emptyMap())
        }
        if (profile.level > pp.level) event(Situation.LEVEL_UP, nowMs, mapOf("N" to "${profile.level}"))
        if (s.speedKmh == 0f || s.isParked) {
            val hundreds = (profile.distanceKm / 100).toLong()
            if (hundreds > (memory.get(KEY_KM100) ?: hundreds)) {
                memory.put(KEY_KM100, hundreds)
                event(Situation.DISTANCE, nowMs, mapOf("km" to "${hundreds * 100}"))
            }
            val persona = profile.persona.ordinal.toLong()
            if (persona != memory.get(KEY_PERSONA)) {
                memory.put(KEY_PERSONA, persona)
                when (profile.persona) {
                    Persona.EASYGOING -> event(Situation.EASYGOING, nowMs, emptyMap())
                    Persona.ADVENTURER -> event(Situation.ADVENTURER, nowMs, emptyMap())
                    Persona.BUDDY -> Unit
                }
            }
        }

        if (zone == LoadZone.RED) lastRedMs = nowMs
        calmSince = if (zone == LoadZone.GREEN) calmSince ?: nowMs else null
        val red = lastRedMs
        if (red != null && calmSince?.let { nowMs - it >= 5_000 } == true) {
            lastRedMs = null
            if (nowMs - red <= 90_000) event(Situation.AFTER_BUSY, nowMs, emptyMap())
        }

        if (moving && zone == LoadZone.GREEN && nowMs - maxOf(lastSpokeAtMs, tripStartMs) >= 30 * MINUTE) {
            light += Situation.SILENCE to emptyMap()
        }

        route?.let { r ->
            if (!r.tenMinutesSaid && r.minutes >= 20 && nowMs - r.startMs >= (r.minutes - 10) * MINUTE) {
                r.tenMinutesSaid = true
                out += Situation.TEN_MINUTES_LEFT.message(nowMs, mapOf("목적지" to r.name))
            }
        }
        arrivedAtMs?.let { at ->
            if (nowMs - at > 5 * MINUTE) {
                arrivedAtMs = null
            } else if (s.isParked && !p.isParked) {
                arrivedAtMs = null
                event(Situation.PARKED_AFTER_ARRIVAL, nowMs, emptyMap())
            }
        }

        pickLight(light, time, nowMs)?.let(out::add)
        return out
    }

    fun onRouteStarted(name: String, km: Float, minutes: Int, time: LocalDateTime, nowMs: Long): List<Message> {
        val short = if (name.length > NAME_MAX) name.take(NAME_MAX) else name
        route = Route(short, km, minutes, nowMs)
        arrivedAtMs = null
        val out = mutableListOf(Situation.ROUTE_STARTED.message(nowMs, mapOf("목적지" to short, "분" to "$minutes")))
        val arrival = time.plusMinutes(minutes.toLong()).toLocalTime()
        val meal = when (arrival) {
            in LUNCH_FROM..LUNCH_TO -> "점심"
            in DINNER_FROM..DINNER_TO -> "저녁"
            else -> null
        }
        if (meal != null && minutes >= 10) out += Situation.MEAL_AT_ARRIVAL.message(nowMs, mapOf("끼니" to meal))
        return out
    }

    fun onArrived(nowMs: Long): List<Message> {
        val r = route ?: return listOf(Situation.ARRIVED.message(nowMs))
        route = null
        arrivedAtMs = nowMs
        val out = mutableListOf(Situation.ARRIVED.message(nowMs))
        if (r.km >= LONG_TRIP_KM && memory.get(KEY_FIRST_LONG_TRIP) == null) {
            memory.put(KEY_FIRST_LONG_TRIP, 1)
            out += Situation.FIRST_LONG_TRIP.message(nowMs)
        }
        return out
    }

    fun onRouteCancelled() {
        route = null
    }

    fun onRegion(region: Region?, time: LocalDateTime): RegionVisit {
        region ?: return RegionVisit.NONE
        val previous = currentRegion
        if (region == previous) return RegionVisit.NONE
        currentRegion = region
        val nowWall = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val key = "visit.${region.key}"
        val count = memory.get("$key.count") ?: 0L
        val last = memory.get("$key.last")
        val firstEver = memory.get(KEY_ANY_VISIT) == null
        memory.put(KEY_ANY_VISIT, 1)
        memory.put("$key.last", nowWall)
        if (!firstEver && last != null && nowWall - last < REVISIT_GAP_MS) return RegionVisit.NONE
        memory.put("$key.count", count + 1)
        if (firstEver) return RegionVisit.NONE

        val name = region.spoken
        if (previous != null && previous.province != region.province) {
            event(Situation.NEW_PROVINCE, lastTickMs, mapOf("도예요" to region.province.spoken + region.province.spoken.particle("이에요", "예요")))
        }
        val named = mapOf("지역" to name, "지역은" to name + name.particle("은", "는"))
        when {
            count == 0L -> {
                event(Situation.FIRST_VISIT, lastTickMs, named)
                val fact = RegionFacts.of(region)
                fact?.sight?.let { event(Situation.FAMOUS_SIGHT, lastTickMs, named + ("명소가" to it + it.particle("이", "가"))) }
                val t = time.toLocalTime()
                if (fact?.food != null && (t in LUNCH_FROM..LUNCH_TO || t in DINNER_FROM..DINNER_TO)) {
                    event(Situation.LOCAL_FOOD, lastTickMs, named + ("음식" to fact.food))
                }
            }
            last != null && nowWall - last >= LONG_AGO_MS -> event(Situation.REVISIT, lastTickMs, named + ("날짜" to datePhrase(last, time)))
        }
        val regular = count + 1 in VISIT_MILESTONES
        if (regular) event(Situation.FREQUENT_VISIT, lastTickMs, named + ("N" to "${count + 1}"))
        return when {
            count == 0L -> RegionVisit.FIRST
            regular -> RegionVisit.REGULAR
            last != null && nowWall - last >= LONG_AGO_MS -> RegionVisit.REVISIT
            else -> RegionVisit.NONE
        }
    }

    private fun datePhrase(epochMs: Long, now: LocalDateTime): String {
        val d = java.time.Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        return when (d.year) {
            now.year -> "${d.monthValue}월 ${d.dayOfMonth}일"
            now.year - 1 -> "작년 ${d.monthValue}월"
            else -> "${d.year}년 ${d.monthValue}월"
        }
    }

    fun noAnswer(nowMs: Long): Message = Situation.NO_ANSWER.message(nowMs)

    private fun event(s: Situation, nowMs: Long, values: Map<String, String> = emptyMap()) {
        events += Event(s, values, nowMs)
    }

    private class Event(val situation: Situation, val values: Map<String, String>, val atMs: Long)

    private fun pickLight(candidates: List<Pair<Situation, Map<String, String>>>, time: LocalDateTime, nowMs: Long): Message? {
        events.removeAll { nowMs - it.atMs > it.situation.ttlMs }
        if (nowMs - lastLightMs < gapMs) return null
        while (events.isNotEmpty()) {
            val e = events.removeFirst()
            val m = make(e.situation, e.values, time, nowMs) ?: continue
            lastLightMs = nowMs
            return m
        }
        for ((situation, values) in candidates) {
            val m = make(situation, values, time, nowMs) ?: continue
            lastLightMs = nowMs
            return m
        }
        return null
    }

    private fun make(s: Situation, values: Map<String, String>, time: LocalDateTime, nowMs: Long): Message? =
        if (s in DAILY) fireDaily(s, time, nowMs, values) else fire(s, nowMs, COOLDOWN[s] ?: 10 * MINUTE, values)

    private fun fire(s: Situation, nowMs: Long, cooldownMs: Long, values: Map<String, String> = emptyMap()): Message? {
        val last = lastFiredMs[s]
        if (last != null && nowMs - last < cooldownMs) return null
        lastFiredMs[s] = nowMs
        return s.message(nowMs, values)
    }

    private fun fireDaily(s: Situation, time: LocalDateTime, nowMs: Long, values: Map<String, String>): Message? {
        val key = "day:${s.name}"
        val today = time.toLocalDate().toEpochDay()
        if (memory.get(key) == today) return null
        memory.put(key, today)
        return s.message(nowMs, values)
    }

    private fun greeting(time: LocalDateTime): Situation? {
        val d = time.toLocalDate()
        val h = time.hour
        val today = d.toEpochDay()
        val options = buildList {
            if (d.monthValue == 1 && d.dayOfMonth == 1) add(Situation.NEW_YEAR)
            if (d.month == Month.DECEMBER && d.dayOfMonth == 25) add(Situation.CHRISTMAS)
            if (d.dayOfWeek == DayOfWeek.MONDAY && h in 6..9) add(Situation.MONDAY)
            if (d.dayOfWeek == DayOfWeek.FRIDAY && h in 17..21) add(Situation.FRIDAY)
            if ((d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) && h in 7..11) add(Situation.WEEKEND)
            if ((d.month == Month.OCTOBER && d.dayOfMonth >= 15) || (d.month == Month.NOVEMBER && d.dayOfMonth <= 15)) add(Situation.AUTUMN)
            if (h in 7..8) add(Situation.MORNING)
            add(Situation.FIRST_DRIVE)
        }
        return options.firstOrNull { memory.get("day:${it.name}") != today }
    }

    private fun remember(profile: CompanionProfile, time: LocalDateTime) {
        if (memory.get(KEY_KM100) == null) memory.put(KEY_KM100, (profile.distanceKm / 100).toLong())
        if (memory.get(KEY_PERSONA) == null) memory.put(KEY_PERSONA, profile.persona.ordinal.toLong())
    }

    companion object {
        private const val MINUTE = 60_000L
        private const val MOVING_KMH = 5f
        private const val TRIP_GAP_MS = 10 * MINUTE
        private const val HARD_BRAKE_MPS2 = 3f
        private const val LONG_TRIP_KM = 100f
        private const val NAME_MAX = 12

        private val LUNCH_FROM: LocalTime = LocalTime.of(11, 30)
        private val LUNCH_TO: LocalTime = LocalTime.of(13, 0)
        private val DINNER_FROM: LocalTime = LocalTime.of(17, 30)
        private val DINNER_TO: LocalTime = LocalTime.of(19, 0)

        private const val KEY_KM100 = "km100"
        private const val KEY_PERSONA = "persona"

        private const val KEY_FIRST_LONG_TRIP = "firstLongTrip"
        private const val KEY_ANY_VISIT = "visit.any"
        private const val REVISIT_GAP_MS = 6 * 60 * MINUTE
        private const val LONG_AGO_MS = 30L * 24 * 60 * MINUTE
        private val VISIT_MILESTONES = setOf(5L, 10L, 20L, 50L, 100L)

        private val DAILY = setOf(
            Situation.NEW_YEAR, Situation.CHRISTMAS, Situation.MONDAY, Situation.FRIDAY, Situation.WEEKEND,
            Situation.AUTUMN, Situation.MORNING, Situation.FIRST_DRIVE,
            Situation.LUNCH, Situation.DINNER, Situation.AFTERNOON, Situation.LATE_NIGHT, Situation.DAWN,
        )

        private val COOLDOWN = mapOf(
            Situation.RAIN to 30 * MINUTE,
            Situation.RAIN_STOPPED to 30 * MINUTE,
            Situation.HIGHWAY_IN to 20 * MINUTE,
            Situation.HIGHWAY_OUT to 20 * MINUTE,
            Situation.JAM to 30 * MINUTE,
            Situation.JAM_CLEARED to 30 * MINUTE,
            Situation.LONG_RED to 20 * MINUTE,

            Situation.TOO_FAST to 30 * MINUTE,
            Situation.SILENCE to 60 * MINUTE,
            Situation.LEVEL_UP to 0L,
            Situation.DISTANCE to 0L,
            Situation.EASYGOING to 0L,
            Situation.ADVENTURER to 0L,
            Situation.FIRST_VISIT to 0L,
            Situation.REVISIT to 0L,
            Situation.FREQUENT_VISIT to 0L,
            Situation.FAMOUS_SIGHT to 0L,
            Situation.LOCAL_FOOD to 0L,
            Situation.NEW_PROVINCE to 0L,
        )

        fun parseRouteStarted(text: String): Triple<String, Float, Int>? {
            val m = ROUTE_STARTED.matchEntire(text.trim()) ?: return null
            return Triple(m.groupValues[1].trim(), m.groupValues[2].toFloat(), m.groupValues[3].toInt())
        }

        private val ROUTE_STARTED = Regex("""경로 안내 시작: (.+) \(([\d.]+)km, 약 (\d+)분\)""")
    }
}
