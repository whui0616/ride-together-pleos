package ai.nunchi.engine.speech

import ai.nunchi.engine.Persona
import ai.nunchi.engine.VoiceOption
import ai.nunchi.engine.chat.ChatTopic
import ai.nunchi.engine.chat.TopicLines
import ai.nunchi.engine.control.CarLines
import ai.nunchi.engine.situation.KoreaCities
import ai.nunchi.engine.situation.Province
import ai.nunchi.engine.situation.Region
import ai.nunchi.engine.situation.RegionFacts
import ai.nunchi.engine.situation.Situation
import ai.nunchi.engine.situation.particle

object LineCatalog {
    data class Line(val key: String, val text: String)

    const val MAX_LEVEL = 30

    const val MAX_KM = 3000

    private val VISIT_MILESTONES = listOf(5, 10, 20, 50, 100)

    fun fixed(): List<Line> = buildList {
        Situation.entries.filter { '{' !in it.text }.forEach { add(Line("sit.${it.name.lowercase()}", it.text)) }
        Situation.entries.mapNotNull { it.accept }.distinct().forEachIndexed { i, t -> add(Line("sit.accept.$i", t)) }

        for (n in 2..MAX_LEVEL) add(Line("sit.level_up.$n", fill(Situation.LEVEL_UP, "N" to "$n")))
        for (km in 100..MAX_KM step 100) add(Line("sit.distance.$km", fill(Situation.DISTANCE, "km" to "$km")))
        for (meal in listOf("점심", "저녁")) add(Line("sit.meal_at_arrival.$meal", fill(Situation.MEAL_AT_ARRIVAL, "끼니" to meal)))
        Province.entries.map { it.spoken }.distinct().forEach { p ->
            add(Line("sit.new_province.$p", fill(Situation.NEW_PROVINCE, "도예요" to p + p.particle("이에요", "예요"))))
        }

        for (n in VISIT_MILESTONES) add(Line("sit.frequent_visit.$n", fill(Situation.FREQUENT_VISIT, "N" to "$n")))

        for (region in regions()) {
            val name = region.spoken
            val named = arrayOf("지역" to name, "지역은" to name + name.particle("은", "는"))
            add(Line("sit.first_visit.$name", fill(Situation.FIRST_VISIT, *named)))
            val fact = RegionFacts.of(region) ?: continue
            fact.sight?.let { add(Line("sit.famous_sight.$name", fill(Situation.FAMOUS_SIGHT, *named, "명소가" to it + it.particle("이", "가")))) }
            fact.food?.let { add(Line("sit.local_food.$name", fill(Situation.LOCAL_FOOD, *named, "음식" to it))) }
        }

        for (topic in ChatTopic.entries) for (persona in Persona.entries) for (driving in listOf(true, false)) {
            val r = TopicLines.reply(topic, persona, driving, etaText = null)
            add(Line("chat.${topic.wire}.${persona.name.lowercase()}.${if (driving) "drive" else "park"}", r.line))
            r.shortLine?.let { add(Line("chat.${topic.wire}.short", it)) }
        }

        CarLines.all().forEach { (k, t) -> add(Line("car.$k", t)) }

        VoiceOption.entries.forEach {
            add(Line("voice.sample.${it.id}", it.sample))
            add(Line("voice.unlock.${it.id}", it.unlockLine))
        }

        DEMO.forEach { (k, t) -> add(Line("demo.$k", t)) }
    }.distinctBy { it.text }

    fun live(): List<Line> = buildList {
        Situation.entries.filter { s -> '{' in s.text && LIVE_KEYS.any { "{$it}" in s.text } }
            .forEach { add(Line("sit.${it.name.lowercase()}", it.text)) }
        add(Line("chat.route.eta", "지금 경로로 {시각}쯤 도착할 것 같아요."))
        add(Line("chat.route.eta.short", "{시각}쯤 도착해요."))
        add(Line("demo.chat.eta", "아까 민지 님이 \"몇 시쯤 도착해?\" 라고 보냈어요. 지금 경로로 {시}시 {분}분 도착 예정이에요. 답장 보낼까요?"))
        add(Line("demo.reply_sent", "\"{시}시 {분}분쯤 도착해!\" 라고 답장 보냈어요."))
        add(Line("demo.arrival_summary", "도착했어요! 오늘도 함께해서 즐거웠어요. 경험치가 {N}까지 쌓였어요."))
    }

    fun regions(): List<Region> = buildList {
        KoreaCities.all.forEach { add(Region(it.province, it.name)) }
        METRO_DISTRICTS.forEach { (p, gus) -> gus.forEach { add(Region(p, it)) } }
    }.distinctBy { it.spoken }

    private fun fill(s: Situation, vararg values: Pair<String, String>) =
        values.fold(s.text) { t, (k, v) -> t.replace("{$k}", v) }

    private val LIVE_KEYS = listOf("목적지", "분", "날짜")

    private val METRO_DISTRICTS: Map<Province, List<String>> = mapOf(
        Province.SEOUL to listOf(
            "종로구", "중구", "용산구", "성동구", "광진구", "동대문구", "중랑구", "성북구", "강북구", "도봉구", "노원구", "은평구", "서대문구",
            "마포구", "양천구", "강서구", "구로구", "금천구", "영등포구", "동작구", "관악구", "서초구", "강남구", "송파구", "강동구",
        ),
        Province.BUSAN to listOf(
            "중구", "서구", "동구", "영도구", "부산진구", "동래구", "남구", "북구", "해운대구", "사하구", "금정구", "강서구", "연제구",
            "수영구", "사상구", "기장군",
        ),
        Province.DAEGU to listOf("중구", "동구", "서구", "남구", "북구", "수성구", "달서구", "달성군", "군위군"),
        Province.INCHEON to listOf("중구", "동구", "미추홀구", "연수구", "남동구", "부평구", "계양구", "서구", "강화군", "옹진군"),
        Province.GWANGJU to listOf("동구", "서구", "남구", "북구", "광산구"),
        Province.DAEJEON to listOf("동구", "중구", "서구", "유성구", "대덕구"),
        Province.ULSAN to listOf("중구", "남구", "동구", "북구", "울주군"),
    )

    private val DEMO = listOf(
        "chat.mom" to "아까 엄마 님이 \"올 때 우유 하나만 사 와\" 라고 보냈어요.",
        "chat.boss" to "아까 팀장님 님이 \"내일 회의 10시로 당겨졌어요\" 라고 보냈어요.",
        "chat.minji.short" to "민지 님 메시지가 와 있어요.",
        "chat.mom.short" to "엄마 님 메시지가 와 있어요.",
        "chat.boss.short" to "팀장님 님 메시지가 와 있어요.",
        "drive_thru" to "꽤 막히네요. 1.2킬로미터 앞 오른쪽에 햄버거 드라이브스루가 있는데, 들렀다 갈까요?",
        "drive_thru.short" to "앞에 드라이브스루가 있어요. 들를까요?",
        "order" to "좋아요! 지난번처럼 불고기버거 세트로 미리 주문해 둘게요. 4분 뒤 도착이고 픽업 번호는 27번이에요.",
        "order.short" to "불고기버거 세트 주문했어요. 픽업 27번이에요.",
        "ack.stop" to "좋아요. 경유지 추가는 음성 비서에게 부탁해 주세요.",
        "ack.reply" to "알겠어요. 답장 보냈어요.",
        "declined" to "알겠어요. 필요하면 언제든 불러 주세요.",
        "story.seongsu" to "지금 지나는 성수동은 예전에 구두 공장과 창고가 모여 있던 곳이에요. 요즘은 오래된 공장 건물을 고친 카페와 팝업 매장이 많아져서 주말마다 사람들로 붐빈대요.",
        "story.namsan" to "저 멀리 보이는 남산서울타워는 원래 방송 전파를 보내는 송신탑으로 세워졌어요. 1980년부터 전망대가 일반에 공개되면서 서울의 대표 명소가 됐죠.",
        "safety" to "500미터 앞 어린이보호구역이에요. 속도를 조금 줄여 주세요.",
        "safety.short" to "앞에 어린이보호구역이에요.",
    )
}
