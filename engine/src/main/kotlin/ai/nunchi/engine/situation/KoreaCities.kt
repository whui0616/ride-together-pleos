package ai.nunchi.engine.situation

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object KoreaCities {
    data class City(val province: Province, val name: String, val lat: Double, val lon: Double)

    fun nearest(lat: Double, lon: Double, maxKm: Double = 40.0): City? =
        all.minByOrNull { km(lat, lon, it.lat, it.lon) }?.takeIf { km(lat, lon, it.lat, it.lon) <= maxKm }

    internal fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    private fun c(p: Province, name: String, lat: Double, lon: Double) = City(p, name, lat, lon)

    val all: List<City> = listOf(
        c(Province.SEOUL, "", 37.5665, 126.9780),
        c(Province.BUSAN, "", 35.1796, 129.0756),
        c(Province.DAEGU, "", 35.8714, 128.6014),
        c(Province.INCHEON, "", 37.4563, 126.7052),
        c(Province.GWANGJU, "", 35.1595, 126.8526),
        c(Province.DAEJEON, "", 36.3504, 127.3845),
        c(Province.ULSAN, "", 35.5384, 129.3114),
        c(Province.SEJONG, "", 36.4801, 127.2890),
        c(Province.GYEONGGI, "수원시", 37.2636, 127.0286),
        c(Province.GYEONGGI, "성남시", 37.4200, 127.1265),
        c(Province.GYEONGGI, "용인시", 37.2411, 127.1776),
        c(Province.GYEONGGI, "고양시", 37.6584, 126.8320),
        c(Province.GYEONGGI, "부천시", 37.5034, 126.7660),
        c(Province.GYEONGGI, "안산시", 37.3219, 126.8309),
        c(Province.GYEONGGI, "안양시", 37.3943, 126.9568),
        c(Province.GYEONGGI, "남양주시", 37.6360, 127.2165),
        c(Province.GYEONGGI, "화성시", 37.1995, 126.8312),
        c(Province.GYEONGGI, "평택시", 36.9921, 127.1129),
        c(Province.GYEONGGI, "의정부시", 37.7381, 127.0337),
        c(Province.GYEONGGI, "시흥시", 37.3800, 126.8029),
        c(Province.GYEONGGI, "파주시", 37.7600, 126.7800),
        c(Province.GYEONGGI, "김포시", 37.6153, 126.7156),
        c(Province.GYEONGGI, "광명시", 37.4786, 126.8646),
        c(Province.GYEONGGI, "광주시", 37.4295, 127.2550),
        c(Province.GYEONGGI, "군포시", 37.3617, 126.9352),
        c(Province.GYEONGGI, "하남시", 37.5393, 127.2149),
        c(Province.GYEONGGI, "오산시", 37.1498, 127.0772),
        c(Province.GYEONGGI, "이천시", 37.2720, 127.4350),
        c(Province.GYEONGGI, "안성시", 37.0080, 127.2797),
        c(Province.GYEONGGI, "구리시", 37.5943, 127.1296),
        c(Province.GYEONGGI, "의왕시", 37.3448, 126.9683),
        c(Province.GYEONGGI, "포천시", 37.8949, 127.2003),
        c(Province.GYEONGGI, "양주시", 37.7853, 127.0458),
        c(Province.GYEONGGI, "여주시", 37.2984, 127.6374),
        c(Province.GYEONGGI, "동두천시", 37.9036, 127.0606),
        c(Province.GYEONGGI, "과천시", 37.4292, 126.9876),
        c(Province.GYEONGGI, "가평군", 37.8315, 127.5105),
        c(Province.GYEONGGI, "양평군", 37.4917, 127.4875),
        c(Province.GYEONGGI, "연천군", 38.0966, 127.0748),
        c(Province.GANGWON, "춘천시", 37.8813, 127.7298),
        c(Province.GANGWON, "원주시", 37.3422, 127.9202),
        c(Province.GANGWON, "강릉시", 37.7519, 128.8761),
        c(Province.GANGWON, "속초시", 38.2070, 128.5918),
        c(Province.GANGWON, "동해시", 37.5247, 129.1143),
        c(Province.GANGWON, "삼척시", 37.4500, 129.1652),
        c(Province.GANGWON, "태백시", 37.1641, 128.9856),
        c(Province.GANGWON, "평창군", 37.3705, 128.3903),
        c(Province.GANGWON, "정선군", 37.3807, 128.6608),
        c(Province.GANGWON, "홍천군", 37.6970, 127.8888),
        c(Province.GANGWON, "양양군", 38.0754, 128.6190),
        c(Province.CHUNGBUK, "청주시", 36.6424, 127.4890),
        c(Province.CHUNGBUK, "충주시", 36.9910, 127.9259),
        c(Province.CHUNGBUK, "제천시", 37.1326, 128.1910),
        c(Province.CHUNGBUK, "단양군", 36.9845, 128.3655),
        c(Province.CHUNGNAM, "천안시", 36.8151, 127.1139),
        c(Province.CHUNGNAM, "아산시", 36.7898, 127.0019),
        c(Province.CHUNGNAM, "공주시", 36.4465, 127.1190),
        c(Province.CHUNGNAM, "보령시", 36.3333, 126.6128),
        c(Province.CHUNGNAM, "서산시", 36.7845, 126.4503),
        c(Province.CHUNGNAM, "논산시", 36.1872, 127.0987),
        c(Province.CHUNGNAM, "당진시", 36.8898, 126.6459),
        c(Province.CHUNGNAM, "홍성군", 36.6013, 126.6608),
        c(Province.CHUNGNAM, "태안군", 36.7456, 126.2980),
        c(Province.CHUNGNAM, "부여군", 36.2757, 126.9098),
        c(Province.JEONBUK, "전주시", 35.8242, 127.1480),
        c(Province.JEONBUK, "군산시", 35.9676, 126.7369),
        c(Province.JEONBUK, "익산시", 35.9483, 126.9577),
        c(Province.JEONBUK, "남원시", 35.4164, 127.3904),
        c(Province.JEONBUK, "정읍시", 35.5699, 126.8559),
        c(Province.JEONBUK, "김제시", 35.8036, 126.8809),
        c(Province.JEONBUK, "무주군", 36.0068, 127.6608),
        c(Province.JEONNAM, "목포시", 34.8118, 126.3922),
        c(Province.JEONNAM, "여수시", 34.7604, 127.6622),
        c(Province.JEONNAM, "순천시", 34.9507, 127.4872),
        c(Province.JEONNAM, "광양시", 34.9407, 127.6959),
        c(Province.JEONNAM, "나주시", 35.0160, 126.7108),
        c(Province.JEONNAM, "담양군", 35.3211, 126.9882),
        c(Province.JEONNAM, "보성군", 34.7715, 127.0800),
        c(Province.JEONNAM, "해남군", 34.5734, 126.5990),
        c(Province.JEONNAM, "완도군", 34.3110, 126.7550),
        c(Province.GYEONGBUK, "포항시", 36.0190, 129.3435),
        c(Province.GYEONGBUK, "경주시", 35.8562, 129.2247),
        c(Province.GYEONGBUK, "안동시", 36.5684, 128.7294),
        c(Province.GYEONGBUK, "구미시", 36.1195, 128.3446),
        c(Province.GYEONGBUK, "김천시", 36.1398, 128.1136),
        c(Province.GYEONGBUK, "영주시", 36.8057, 128.6241),
        c(Province.GYEONGBUK, "상주시", 36.4109, 128.1590),
        c(Province.GYEONGBUK, "문경시", 36.5865, 128.1867),
        c(Province.GYEONGBUK, "경산시", 35.8251, 128.7414),
        c(Province.GYEONGBUK, "울진군", 36.9930, 129.4004),
        c(Province.GYEONGNAM, "창원시", 35.2280, 128.6811),
        c(Province.GYEONGNAM, "김해시", 35.2285, 128.8894),
        c(Province.GYEONGNAM, "진주시", 35.1800, 128.1076),
        c(Province.GYEONGNAM, "양산시", 35.3350, 129.0373),
        c(Province.GYEONGNAM, "거제시", 34.8806, 128.6211),
        c(Province.GYEONGNAM, "통영시", 34.8544, 128.4332),
        c(Province.GYEONGNAM, "사천시", 35.0036, 128.0642),
        c(Province.GYEONGNAM, "밀양시", 35.5038, 128.7467),
        c(Province.GYEONGNAM, "남해군", 34.8375, 127.8925),
        c(Province.GYEONGNAM, "거창군", 35.6866, 127.9095),
        c(Province.JEJU, "제주시", 33.4996, 126.5312),
        c(Province.JEJU, "서귀포시", 33.2541, 126.5600),
    )
}
