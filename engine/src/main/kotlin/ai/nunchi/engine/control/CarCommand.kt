package ai.nunchi.engine.control

import ai.nunchi.engine.situation.particle

enum class CarAction(
    val noun: String,
) {
    HEATER_ON("히터"),
    AC_ON("에어컨"),
    AC_OFF("에어컨"),
    CLIMATE_OFF("냉난방"),
    WARMER("온도"),
    COOLER("온도"),
    SET_TEMPERATURE("온도"),
    FAN_UP("바람 세기"),
    FAN_DOWN("바람 세기"),
    DEFROST_ON("앞유리 김 서림 제거"),
    DEFROST_OFF("앞유리 김 서림 제거"),
    FRESH_AIR("공기 순환"),
    RECIRCULATE("공기 순환"),
    SEAT_HEAT_ON("시트 열선"),
    SEAT_HEAT_OFF("시트 열선"),
    SEAT_VENT_ON("시트 통풍"),
    SEAT_VENT_OFF("시트 통풍"),
    WHEEL_HEAT_ON("핸들 열선"),
    WHEEL_HEAT_OFF("핸들 열선"),
    WINDOW_OPEN("창문"),
    WINDOW_CLOSE("창문"),
    SUNROOF_OPEN("선루프"),
    SUNROOF_CLOSE("선루프"),
    CABIN_LIGHT_ON("실내등"),
    CABIN_LIGHT_OFF("실내등"),
    DOOR_LOCK("문 잠금"),
}

enum class CarZone(val spoken: String) {
    DRIVER("운전석"),
    PASSENGER("조수석"),
    REAR("뒷좌석"),
    ALL("모든 자리"),
}

enum class CarAmount { NORMAL, LITTLE, FULL }

data class CarCommand(
    val action: CarAction,
    val zone: CarZone = CarZone.DRIVER,
    val amount: CarAmount = CarAmount.NORMAL,
    val degrees: Int? = null,
) {
    companion object {
        const val STEP = 2

        const val MIN_CELSIUS = 16
        const val MAX_CELSIUS = 30
    }
}

data class CarResult(val ok: Boolean, val celsius: Int? = null, val detail: String = "")

object CarLines {
    fun done(cmd: CarCommand, celsius: Int? = null): String {
        val zone = cmd.zone.spoken
        return when (cmd.action) {
            CarAction.HEATER_ON -> "히터 켰어요."
            CarAction.AC_ON -> "에어컨 켰어요."
            CarAction.AC_OFF -> "에어컨 껐어요."
            CarAction.CLIMATE_OFF -> "냉난방 껐어요."
            CarAction.WARMER -> celsius?.let { "온도를 ${it}도로 올렸어요." } ?: "조금 따뜻하게 했어요."
            CarAction.COOLER -> celsius?.let { "온도를 ${it}도로 내렸어요." } ?: "조금 시원하게 했어요."
            CarAction.SET_TEMPERATURE -> "온도를 ${celsius ?: cmd.degrees}도로 맞췄어요."
            CarAction.FAN_UP -> "바람 세게 했어요."
            CarAction.FAN_DOWN -> "바람 약하게 했어요."
            CarAction.DEFROST_ON -> "앞유리 김 서림 없앨게요."
            CarAction.DEFROST_OFF -> "앞유리 김 서림 제거 껐어요."
            CarAction.FRESH_AIR -> "바깥 공기로 바꿨어요."
            CarAction.RECIRCULATE -> "실내 공기로 순환할게요."
            CarAction.SEAT_HEAT_ON -> "$zone 열선 켰어요."
            CarAction.SEAT_HEAT_OFF -> "$zone 열선 껐어요."
            CarAction.SEAT_VENT_ON -> "$zone 통풍 켰어요."
            CarAction.SEAT_VENT_OFF -> "$zone 통풍 껐어요."
            CarAction.WHEEL_HEAT_ON -> "핸들 열선 켰어요."
            CarAction.WHEEL_HEAT_OFF -> "핸들 열선 껐어요."
            CarAction.WINDOW_OPEN -> if (cmd.amount == CarAmount.LITTLE) "${windows(cmd.zone)} 조금 열었어요." else "${windows(cmd.zone)} 열었어요."
            CarAction.WINDOW_CLOSE -> "${windows(cmd.zone)} 닫았어요."
            CarAction.SUNROOF_OPEN -> if (cmd.amount == CarAmount.LITTLE) "선루프 조금 열었어요." else "선루프 열었어요."
            CarAction.SUNROOF_CLOSE -> "선루프 닫았어요."
            CarAction.CABIN_LIGHT_ON -> "실내등 켰어요."
            CarAction.CABIN_LIGHT_OFF -> "실내등 껐어요."
            CarAction.DOOR_LOCK -> "문 잠갔어요."
        }
    }

    fun ask(cmd: CarCommand): String = when (cmd.action) {
        CarAction.WARMER -> "좀 따뜻하게 해 드릴까요?"
        CarAction.COOLER -> "좀 시원하게 해 드릴까요?"
        CarAction.HEATER_ON -> "히터 켤까요?"
        CarAction.AC_ON -> "에어컨 켤까요?"
        CarAction.FRESH_AIR -> "바깥 공기로 바꿀까요?"
        CarAction.DEFROST_ON -> "앞유리 김 서림 없앨까요?"
        CarAction.SEAT_HEAT_ON -> "${cmd.zone.spoken} 열선 켤까요?"
        CarAction.SEAT_VENT_ON -> "${cmd.zone.spoken} 통풍 켤까요?"
        CarAction.WHEEL_HEAT_ON -> "핸들 열선 켤까요?"
        CarAction.WINDOW_OPEN -> "${windows(cmd.zone)} 열까요?"
        CarAction.SUNROOF_OPEN -> "선루프 열까요?"
        CarAction.CABIN_LIGHT_ON -> "실내등 켤까요?"
        else -> "${cmd.action.noun}${cmd.action.noun.particle("을", "를")} 바꿀까요?"
    }

    fun failed(cmd: CarCommand): String = "${cmd.action.noun}${cmd.action.noun.particle("을", "를")} 바꾸지 못했어요. 직접 해 주세요."

    const val REFUSED = "그건 안전을 위해 직접 해 주세요."

    const val UNCLEAR = "히터, 에어컨, 창문, 열선은 말씀만 하세요."

    private fun windows(zone: CarZone) = if (zone == CarZone.ALL) "모든 창문" else "${zone.spoken} 창문"

    fun all(): List<Pair<String, String>> = buildList {
        for (action in CarAction.entries) for (zone in CarZone.entries) for (amount in CarAmount.entries) {
            val cmd = CarCommand(action, zone, amount)
            val key = "${action.name.lowercase()}.${zone.name.lowercase()}.${amount.name.lowercase()}"
            if (action != CarAction.SET_TEMPERATURE) add("done.$key" to done(cmd))
            add("ask.$key" to ask(cmd))
            add("failed.$key" to failed(cmd))
        }
        for (c in CarCommand.MIN_CELSIUS..CarCommand.MAX_CELSIUS) {
            for (a in listOf(CarAction.WARMER, CarAction.COOLER, CarAction.SET_TEMPERATURE)) {
                add("done.${a.name.lowercase()}.$c" to done(CarCommand(a), c))
            }
        }
        add("refused" to REFUSED)
        add("unclear" to UNCLEAR)
    }.distinctBy { it.second }
}
