package ai.nunchi.engine.control

sealed interface CarParse {
    data class Command(val command: CarCommand, val sure: Boolean) : CarParse

    data class Refused(val what: String) : CarParse
}

object CarCommandParser {
    fun parse(utterance: String): CarParse? {
        val t = utterance.filter { it.isLetterOrDigit() }
        if (t.isEmpty() || has(t, NEGATIONS) || NEGATION.containsMatchIn(t) || has(t, MESSAGE) || isStatusQuestion(t) || isTalkAbout(t)) return null
        return device(t) ?: refused(t) ?: feeling(t)
    }

    private fun device(t: String): CarParse? {
        val v = Verbs(t)
        val zone = zone(t)
        val amount = when {
            has(t, FULL) || has(t, STRONG) -> CarAmount.FULL
            has(t, LITTLE) || has(t, WEAK) -> CarAmount.LITTLE
            else -> CarAmount.NORMAL
        }

        val askable = t.length <= TERSE || has(t, FEELS)

        fun cmd(a: CarAction, sure: Boolean = true, degrees: Int? = null): CarParse.Command? =
            if (!sure && !askable) null else CarParse.Command(CarCommand(a, zone, amount, degrees), sure)

        fun toggle(onAction: CarAction, offAction: CarAction) = when {
            v.off -> cmd(offAction)
            v.on || v.up || v.request -> cmd(onAction)
            else -> cmd(onAction, sure = false)
        }

        if (has(t, WHEEL)) return toggle(CarAction.WHEEL_HEAT_ON, CarAction.WHEEL_HEAT_OFF)
        if (has(t, VENT)) return toggle(CarAction.SEAT_VENT_ON, CarAction.SEAT_VENT_OFF)
        if (has(t, SEAT_HEAT)) return toggle(CarAction.SEAT_HEAT_ON, CarAction.SEAT_HEAT_OFF)
        if (has(t, DEFROST)) return when {
            v.off -> cmd(CarAction.DEFROST_OFF)
            v.on || v.request || has(t, REMOVE) -> cmd(CarAction.DEFROST_ON)
            else -> cmd(CarAction.DEFROST_ON, sure = false)
        }
        if (has(t, SUNROOF)) return when {
            has(t, SHUT) || v.off -> cmd(CarAction.SUNROOF_CLOSE)
            v.open || v.on -> cmd(CarAction.SUNROOF_OPEN)
            else -> cmd(CarAction.SUNROOF_OPEN, sure = v.request)
        }
        if (has(t, WINDOW) && has(t, LOCK)) return null
        if (has(t, WINDOW)) return when {
            has(t, SHUT) -> cmd(CarAction.WINDOW_CLOSE)
            v.open -> cmd(CarAction.WINDOW_OPEN)
            has(t, RAISE) -> cmd(CarAction.WINDOW_CLOSE)
            else -> cmd(CarAction.WINDOW_OPEN, sure = v.request)
        }
        if (has(t, LOCK) && !has(t, UNLOCK)) return cmd(CarAction.DOOR_LOCK)
        if (has(t, LIGHT) || LIGHT_VERB.containsMatchIn(t)) return toggle(CarAction.CABIN_LIGHT_ON, CarAction.CABIN_LIGHT_OFF)
        if (has(t, FAN)) {
            when {
                v.off -> return cmd(CarAction.CLIMATE_OFF)
                v.up -> return cmd(CarAction.FAN_UP)
                v.down -> return cmd(CarAction.FAN_DOWN)
                has(t, TOO_STRONG) -> return cmd(CarAction.FAN_DOWN, sure = false)
                has(t, TOO_WEAK) -> return cmd(CarAction.FAN_UP, sure = false)
            }
        }
        if (has(t, FRESH)) return if (v.on || v.request || v.open) cmd(CarAction.FRESH_AIR) else cmd(CarAction.FRESH_AIR, sure = false)
        if (has(t, RECIRC)) return if (v.off) cmd(CarAction.FRESH_AIR) else cmd(CarAction.RECIRCULATE)

        val degrees = degrees(t)
        val absolute = degrees?.takeIf { it in ABSOLUTE }?.coerceIn(CarCommand.MIN_CELSIUS, CarCommand.MAX_CELSIUS)
        val delta = degrees?.takeIf { it in DELTA }
        val climate = has(t, AC) || has(t, HEATER) || has(t, CLIMATE) || "온도" in t
        if (absolute != null && (climate || v.on || v.up || v.down || v.request || has(t, SETTING))) {
            return cmd(CarAction.SET_TEMPERATURE, degrees = absolute)
        }
        if ("온도" in t) return when {
            v.up -> cmd(CarAction.WARMER, degrees = delta)
            v.down -> cmd(CarAction.COOLER, degrees = delta)
            else -> null
        }
        if (has(t, AC)) return when {
            v.off -> cmd(CarAction.AC_OFF)
            has(t, STRONG) -> cmd(CarAction.COOLER)
            has(t, WEAK) || v.down -> cmd(CarAction.FAN_DOWN)
            v.on || v.request -> cmd(CarAction.AC_ON)
            has(t, COLD) -> cmd(CarAction.WARMER, sure = false)
            else -> cmd(CarAction.AC_ON, sure = false)
        }
        if (has(t, HEATER)) return when {
            v.off -> cmd(CarAction.CLIMATE_OFF)
            has(t, STRONG) || v.up -> cmd(CarAction.WARMER)
            has(t, WEAK) || v.down -> cmd(CarAction.FAN_DOWN)
            v.on || v.request -> cmd(CarAction.HEATER_ON)
            has(t, HOT) -> cmd(CarAction.COOLER, sure = false)
            else -> cmd(CarAction.HEATER_ON, sure = false)
        }
        if (has(t, CLIMATE) && v.off) return cmd(CarAction.CLIMATE_OFF)
        return null
    }

    private fun refused(t: String): CarParse? {
        val s = t.replace("창문", "")
        val v = Verbs(s)
        if (!(v.on || v.off || v.open || v.request || has(s, SHUT) || has(s, UNLOCK) || has(s, SHIFT))) return null
        val word = REFUSED.firstOrNull { it in s } ?: return null
        return CarParse.Refused(word)
    }

    private fun feeling(t: String): CarParse? {
        if (FEELING_NEGATION.containsMatchIn(t)) return null
        val asked = Verbs(t).request
        if (has(t, OUTSIDE_SMELL)) return command(CarAction.RECIRCULATE, sure = asked, t)
        if (has(t, DARK) && !has(t, OUTSIDE_DARK)) return command(CarAction.CABIN_LIGHT_ON, sure = asked, t)
        if (FEELING_NEGATION.containsMatchIn(t) || has(t, PAST_OR_OUTSIDE)) return null
        val request = Verbs(t).request
        return when {
            has(t, WARM_REQUEST) -> command(CarAction.WARMER, sure = true, t)
            has(t, COOL_REQUEST) -> command(CarAction.COOLER, sure = true, t)
            has(t, COLD) -> command(CarAction.WARMER, sure = request, t)
            has(t, HOT) -> command(CarAction.COOLER, sure = request, t)
            has(t, STUFFY) -> command(CarAction.FRESH_AIR, sure = request, t)
            else -> null
        }
    }

    private fun command(action: CarAction, sure: Boolean, t: String) =
        CarParse.Command(CarCommand(action, amount = if (has(t, LITTLE)) CarAmount.LITTLE else CarAmount.NORMAL), sure)

    private fun isStatusQuestion(t: String) = has(t, STATUS)

    private fun isTalkAbout(t: String) = has(t, ABOUT) || CONDITIONAL.containsMatchIn(t) || PAST_ACTION.containsMatchIn(t)

    private fun zone(t: String) = when {
        has(t, ZONE_ALL) -> CarZone.ALL
        has(t, ZONE_REAR) -> CarZone.REAR
        has(t, ZONE_PASSENGER) -> CarZone.PASSENGER
        else -> CarZone.DRIVER
    }

    internal fun degrees(t: String): Int? {
        DIGIT_DEGREES.find(t)?.let { return it.groupValues[1].toInt() }
        return KOREAN_DEGREES.find(t)?.let { NUMBERS.getValue(it.groupValues[1]) }
    }

    private class Verbs(t: String) {
        val on = has(t, ON)
        val off = has(t, OFF)
        val open = has(t, OPEN)
        val up = has(t, UP)
        val down = has(t, DOWN)
        val request = has(t, REQUEST) || t.endsWith("좀") || t.endsWith("좀요")
    }

    private fun has(t: String, words: List<String>) = words.any { it in t }

    private val ON = listOf("켜", "켤", "키자", "키고", "틀어", "틀자", "틀래", "틀까", "틀고", "작동", "돌려")
    private val OFF = listOf("꺼", "끄", "끌", "멈춰", "멈추", "중지", "그만", "정지", "해제")
    private val OPEN = listOf("열어", "열자", "열고", "열래", "열까", "열면", "내려", "내리")
    private val SHUT = listOf("닫")
    private val SHIFT = listOf("넣어", "넣자", "넣고", "빼줘", "빼자")
    private val RAISE = listOf("올려", "올리")
    private val UP = listOf("올려", "올리", "높여", "높이", "세게", "쎄게", "강하게", "키워", "키우", "최대", "빵빵")
    private val DOWN = listOf("내려", "내리", "낮춰", "낮추", "약하게", "줄여", "줄이", "최소")
    private val REQUEST = listOf("해줘", "해주", "해봐", "부탁", "주세요", "줄래", "줄수", "하자", "해라", "줘", "바꿔")
    private val STRONG = listOf("세게", "쎄게", "강하게", "최대", "빵빵")
    private val WEAK = listOf("약하게")
    private val FULL = listOf("끝까지", "활짝", "완전히")
    private val LITTLE = listOf("조금", "살짝", "약간", "좀만", "쪼금", "반만", "틈")
    private val REMOVE = listOf("없애", "제거", "닦", "지워", "빼줘")
    private val SETTING = listOf("맞춰", "맞추", "설정", "바꿔")

    private val WHEEL = listOf("핸들열선", "운전대열선", "스티어링", "핸들따뜻", "핸들이차", "핸들차가", "핸들이너무차", "운전대가차", "핸들이시려", "손시려", "손이시려", "손이차가", "손꽁꽁", "손이꽁꽁", "손이얼", "손얼")
    private val VENT = listOf("통풍")
    private val SEAT_HEAT = listOf("열선", "엉따", "엉뜨", "시트히터", "시트따뜻", "의자따뜻", "엉덩이가차", "엉덩이따뜻", "엉덩이시려")
    private val DEFROST = listOf("성에", "김서", "김이서", "김껴", "김이껴", "습기", "뿌옇", "뿌얘", "서리", "디프로스트", "앞이안보", "앞이하나도안보", "앞유리", "김꼈", "김이꼈", "김때문", "김차")
    private val SUNROOF = listOf("선루프", "썬루프")
    private val WINDOW = listOf("창문", "창좀", "창열", "창닫", "유리창", "윈도우")
    private val LOCK = listOf("잠가", "잠궈", "잠그", "잠금", "락걸")
    private val UNLOCK = listOf("잠금해제", "잠금풀", "풀어")
    private val LIGHT = listOf("실내등", "실내조명", "실내불", "실내라이트", "독서등")
    private val LIGHT_VERB = Regex("불(을|좀)*(켜|꺼|끄)")
    private val FAN = listOf("바람", "풍량", "송풍", "팬")
    private val TOO_STRONG = listOf("너무세", "너무강", "너무쎄")
    private val TOO_WEAK = listOf("너무약", "약해")
    private val FRESH = listOf("외기", "바깥공기", "외부공기", "환기")
    private val RECIRC = listOf("내기순환", "내기로", "내기모드", "실내순환", "공기순환")
    private val AC = listOf("에어컨", "에어콘", "에이컨", "냉방")
    private val HEATER = listOf("히터", "히타", "난방", "온풍")
    private val CLIMATE = listOf("냉난방", "공조", "에어컨히터")

    private val REFUSED = listOf(
        "문열", "문좀열", "문을열", "문닫", "문좀닫", "문을닫", "도어", "트렁크", "시동", "와이퍼", "전조등", "상향등", "하이빔",
        "헤드라이트", "라이트", "잠금해제", "잠금풀", "안개등", "비상등", "깜빡이", "기어", "브레이크", "사이드미러", "크루즈", "자율주행", "경적", "클락션", "클랙슨",
        "후드", "보닛", "본넷", "본네트", "후진", "주유구", "주입구", "충전구", "충전포트",
    )

    private val WARM_REQUEST = listOf("따뜻하게", "따듯하게", "따숩게", "뜨뜻하게", "데워")
    private val COOL_REQUEST = listOf("시원하게", "선선하게", "식혀")
    private val COLD = listOf("추워", "춥", "추운", "추웠", "쌀쌀", "시려", "으슬", "얼겠", "얼어", "서늘", "한기", "오들오들", "덜덜", "냉골", "꽁꽁")
    private val HOT = listOf("더워", "덥", "더운", "더웠", "땀", "후덥", "찜통", "찜질", "뜨거워", "뜨겁", "화끈", "후끈", "쪄죽", "쪄서", "푹푹")
    private val STUFFY = listOf("공기가답답", "답답한공기", "공기가탁", "탁해", "공기안좋", "공기가안좋", "냄새", "숨막")
    private val OUTSIDE_SMELL = listOf("매연", "터널", "배기가스", "바깥냄새", "밖에서냄새", "공사장", "미세먼지", "황사")
    private val DARK = listOf("어두워", "어둡", "깜깜", "컴컴")
    private val OUTSIDE_DARK = listOf("밖", "길", "도로", "터널", "하늘", "날이", "날씨")
    private val FEELING_NEGATION = Regex("안(추|춥|더|덥)|(춥|덥|추|더)지(는)?않")
    private val PAST_OR_OUTSIDE = listOf("날씨", "더라", "웠어", "웠다", "었어", "었다", "밖에", "어제")
    private val FEELS = listOf("차가워", "차가운", "차갑", "시려", "서렸", "서려", "서림", "뿌옇", "뿌얘", "안보", "너무") + COLD + HOT
    private const val TERSE = 6
    private val NEGATION = Regex("안[가-힣]{0,2}[어아여해]도")

    private val NEGATIONS = listOf("지마", "지말", "하지마", "안켜도", "안틀어도", "안해도", "필요없", "괜찮아")
    private val MESSAGE = listOf("문자", "카톡", "메시지", "한테")
    private val STATUS = listOf("켜져있", "꺼져있", "열려있", "닫혀있", "켜졌어", "꺼졌어", "몇도야", "몇도지", "몇도예", "몇도인", "몇도에요", "안꺼지", "안켜지", "고장", "필터")
    private val ABOUT = listOf("뭐야", "뭐지", "뭔데", "뭘까", "뭐예요", "뭐에요", "이유", "원리", "왜생기", "어떻게써", "어떻게하는", "더니", "더라", "깜빡했", "어제")
    private val CONDITIONAL = Regex("(켜|틀|열|닫|끄|꺼|내리|올리|잠그)면(?!좋)")
    private val PAST_ACTION = Regex("(켰|틀었|열었|닫았|껐|잠갔|잠궜)(어|다|는데|더니|잖)")

    private val ZONE_ALL = listOf("전체", "모든", "모두", "전부", "창문다", "다같이")
    private val ZONE_REAR = listOf("뒷좌석", "뒷자리", "뒤쪽", "뒤에", "뒷창", "뒤창", "2열", "뒷줄", "애기", "아기", "아이자리", "애들")
    private val ZONE_PASSENGER = listOf("조수석", "보조석", "옆자리", "옆좌석", "옆에", "옆사람")

    private val ABSOLUTE = 10..45
    private val DELTA = 1..6
    private val DIGIT_DEGREES = Regex("(\\d{1,2})도")

    private val NUMBERS: Map<String, Int> = buildMap {
        val sinoUnit = listOf("", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")
        val sinoTen = listOf("", "십", "이십", "삼십")
        val nativeUnit = listOf(listOf(""), listOf("한", "하나"), listOf("두", "둘"), listOf("세", "셋"), listOf("네", "넷"),
            listOf("다섯"), listOf("여섯"), listOf("일곱"), listOf("여덟"), listOf("아홉"))
        val nativeTen = listOf(listOf(""), listOf("열"), listOf("스물", "스무"), listOf("서른"))
        for (n in 1..35) {
            val tens = n / 10
            val unit = n % 10
            put(sinoTen[tens] + sinoUnit[unit], n)
            for (ten in nativeTen[tens]) for (u in nativeUnit[unit]) {
                if (ten == "스무" && unit != 0) continue
                if (ten == "스물" && unit == 0) put("스물", n)
                put(ten + u, n)
            }
        }
        remove("")
    }
    private val KOREAN_DEGREES = Regex("(" + NUMBERS.keys.sortedByDescending { it.length }.joinToString("|") + ")도")
}
