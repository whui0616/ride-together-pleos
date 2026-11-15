package ai.nunchi.engine

import java.time.LocalDate
import java.time.MonthDay

enum class EventGoal(val label: String, val unit: String) {
    VISIT("출석", "일"), TRIP("함께 다녀오기", "번"), PLACE("새 지역", "곳"),
    TALK("캐릭터와 대화", "번"), PET("쓰다듬기", "번"),
}

enum class EventKind(val label: String) { SEASON("계절"), SPECIAL("기념일"), GUIDE("신규 가이드") }

data class EventMission(val goal: EventGoal, val target: Int) {
    val title: String get() = "${goal.label} $target${goal.unit}"
}

data class EventDef(
    val id: String,
    val emoji: String,
    val title: String,
    val desc: String,
    val kind: EventKind,
    val start: MonthDay,
    val end: MonthDay,
    val missions: List<EventMission>,
    val coins: Int,
    val tickets: Int,
)

enum class EventStatus(val label: String) { ACTIVE("진행 중"), UPCOMING("예정"), ENDED("종료") }

data class EventWindow(val def: EventDef, val start: Long, val end: Long) {
    val key: String get() = "${def.id}@$start"
    fun status(today: Long): EventStatus = when {
        today < start -> EventStatus.UPCOMING
        today > end -> EventStatus.ENDED
        else -> EventStatus.ACTIVE
    }
    fun daysLeft(today: Long): Long = end - today
    fun daysUntil(today: Long): Long = start - today
}

object EventCalendar {
    private fun md(m: Int, d: Int) = MonthDay.of(m, d)
    private fun e(id: String, emoji: String, title: String, desc: String, kind: EventKind, s: MonthDay, end: MonthDay, coins: Int, tickets: Int, vararg ms: Pair<EventGoal, Int>) =
        EventDef(id, emoji, title, desc, kind, s, end, ms.map { EventMission(it.first, it.second) }, coins, tickets)

    val ALL: List<EventDef> = listOf(
        e("new_year", "🌅", "새해 첫 주행", "올해도 함께. 첫 주 출석으로 시작해요", EventKind.SPECIAL, md(1, 1), md(1, 7), 30, 1, EventGoal.VISIT to 3, EventGoal.TALK to 3),
        e("spring", "🌸", "봄 벚꽃 나들이", "꽃길 따라 새로운 곳으로", EventKind.SEASON, md(3, 20), md(4, 15), 60, 3, EventGoal.VISIT to 7, EventGoal.PLACE to 1),
        e("school_zone", "🧒", "어린이날 나들이", "가족과 함께 나서는 주간. 같이 다녀와요", EventKind.SPECIAL, md(5, 1), md(5, 10), 60, 2, EventGoal.VISIT to 3, EventGoal.TRIP to 3),
        e("summer_rest", "🏖️", "여름 휴가철 나들이", "휴가철엔 새로운 곳으로. 쉬엄쉬엄 다녀와요", EventKind.SEASON, md(7, 20), md(8, 20), 60, 3, EventGoal.VISIT to 7, EventGoal.PLACE to 1),
        e("autumn", "🍁", "가을 단풍 축제", "단풍 구경 가기 좋은 계절이에요", EventKind.SEASON, md(9, 1), md(11, 30), 80, 3, EventGoal.VISIT to 10, EventGoal.PLACE to 1),
        e("signal_week", "💙", "매일 함께 주간", "이번 주는 매일 같이 출발해요", EventKind.SPECIAL, md(11, 1), md(11, 14), 50, 2, EventGoal.VISIT to 5, EventGoal.TALK to 5),
        e("winter", "❄️", "겨울 눈꽃", "추운 날에도 함께 다녀와요", EventKind.SEASON, md(12, 1), md(2, 28), 80, 3, EventGoal.VISIT to 10, EventGoal.TRIP to 10),
        e("christmas", "🎄", "크리스마스", "캐릭터와 이야기하고 쓰다듬어 주세요", EventKind.SPECIAL, md(12, 20), md(12, 26), 30, 1, EventGoal.TALK to 5, EventGoal.PET to 5),
    )

    val GUIDE = e("guide", "🔰", "신규 7일 가이드", "처음 일주일, 하나씩 해 봐요", EventKind.GUIDE, md(1, 1), md(1, 7), 50, 1,
        EventGoal.VISIT to 3, EventGoal.TALK to 3, EventGoal.PET to 3, EventGoal.TRIP to 1)

    const val UPCOMING_DAYS = 60
    const val ARCHIVE_DAYS = 120

    fun window(def: EventDef, year: Int): EventWindow {
        val s = def.start.atYear(year)
        val e = if (def.end.isBefore(def.start)) def.end.atYear(year + 1) else def.end.atYear(year)
        return EventWindow(def, s.toEpochDay(), e.toEpochDay())
    }

    fun windows(today: LocalDate, createdDay: Long? = null): List<EventWindow> {
        val t = today.toEpochDay()
        val all = ALL.flatMap { d -> (today.year - 1..today.year + 1).map { window(d, it) } } +
            listOfNotNull(createdDay?.let { EventWindow(GUIDE, it, it + 6) })
        return all.filter { w ->
            when (w.status(t)) {
                EventStatus.ACTIVE -> true
                EventStatus.UPCOMING -> w.daysUntil(t) <= UPCOMING_DAYS
                EventStatus.ENDED -> t - w.end <= ARCHIVE_DAYS
            }
        }.sortedBy { it.start }
    }
}

data class EventState(
    val baselines: Map<String, Map<EventGoal, Int>> = emptyMap(),
    val done: Set<String> = emptySet(),
    val claimed: Set<String> = emptySet(),
    val tickets: Int = 0,
) {
    fun joined(w: EventWindow): Boolean = w.key in baselines

    fun update(windows: List<EventWindow>, today: Long, counters: Map<EventGoal, Int>, visitDays: Collection<Long>): EventState {
        var s = this
        for (w in windows.filter { it.status(today) == EventStatus.ACTIVE }) {
            if (!s.joined(w)) s = s.copy(baselines = s.baselines + (w.key to counters))
            if (w.key !in s.done && w.def.missions.all { s.liveProgress(w, it, counters, visitDays) >= it.target }) s = s.copy(done = s.done + w.key)
        }
        return s
    }

    private fun liveProgress(w: EventWindow, m: EventMission, counters: Map<EventGoal, Int>, visitDays: Collection<Long>): Int {
        if (m.goal == EventGoal.VISIT) return if (joined(w)) visitDays.count { it in w.start..w.end } else 0
        val base = baselines[w.key] ?: return 0
        return ((counters[m.goal] ?: 0) - (base[m.goal] ?: 0)).coerceAtLeast(0)
    }

    fun progress(w: EventWindow, m: EventMission, today: Long, counters: Map<EventGoal, Int>, visitDays: Collection<Long>): Int = when {
        w.key in done -> m.target
        w.status(today) != EventStatus.ACTIVE -> 0
        else -> liveProgress(w, m, counters, visitDays)
    }

    fun canClaim(w: EventWindow): Boolean = w.key in done && w.key !in claimed

    fun claim(w: EventWindow): Pair<EventState, Int>? {
        if (!canClaim(w)) return null
        return copy(claimed = claimed + w.key, tickets = tickets + w.def.tickets) to w.def.coins
    }

    fun spendTickets(n: Int): EventState? = if (n in 1..tickets) copy(tickets = tickets - n) else null
}
