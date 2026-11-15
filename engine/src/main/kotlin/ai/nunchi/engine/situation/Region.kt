package ai.nunchi.engine.situation

data class Region(val province: Province, val district: String) {
    val key: String get() = "${province.short} $district".trim()

    val spoken: String
        get() = when {
            district.isEmpty() -> province.short
            province.metropolitan -> "${province.short} $district"
            else -> district
        }

    companion object {
        fun parse(address: String?): Region? {
            val tokens = address?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() } ?: return null
            val province = tokens.firstOrNull()?.let(Province::of) ?: return null
            val district = tokens.drop(1).firstOrNull { it.endsWith("시") || it.endsWith("군") || it.endsWith("구") }
                ?.takeIf { province != Province.SEJONG }
                .orEmpty()
            return Region(province, district)
        }

        fun locate(address: String?, lat: Double, lon: Double): Region? {
            parse(address)?.let { return it }
            val city = KoreaCities.nearest(lat, lon) ?: return null
            if (city.name.isNotEmpty()) return Region(city.province, city.name)
            val gu = address?.trim()?.split(Regex("\\s+"))?.firstOrNull { it.endsWith("구") || it.endsWith("군") }
            return Region(city.province, if (city.province.metropolitan) gu.orEmpty() else "")
        }
    }
}

enum class Province(val short: String, val spoken: String, val metropolitan: Boolean, vararg val names: String) {
    SEOUL("서울", "서울", true, "서울특별시", "서울시", "서울"),
    BUSAN("부산", "부산", true, "부산광역시", "부산시", "부산"),
    DAEGU("대구", "대구", true, "대구광역시", "대구시", "대구"),
    INCHEON("인천", "인천", true, "인천광역시", "인천시", "인천"),
    GWANGJU("광주", "광주", true, "광주광역시", "광주"),
    DAEJEON("대전", "대전", true, "대전광역시", "대전시", "대전"),
    ULSAN("울산", "울산", true, "울산광역시", "울산시", "울산"),
    SEJONG("세종", "세종", false, "세종특별자치시", "세종시", "세종"),
    GYEONGGI("경기", "경기도", false, "경기도", "경기"),
    GANGWON("강원", "강원도", false, "강원특별자치도", "강원도", "강원"),
    CHUNGBUK("충북", "충청북도", false, "충청북도", "충북"),
    CHUNGNAM("충남", "충청남도", false, "충청남도", "충남"),
    JEONBUK("전북", "전라북도", false, "전북특별자치도", "전라북도", "전북"),
    JEONNAM("전남", "전라남도", false, "전라남도", "전남"),
    GYEONGBUK("경북", "경상북도", false, "경상북도", "경북"),
    GYEONGNAM("경남", "경상남도", false, "경상남도", "경남"),
    JEJU("제주", "제주도", false, "제주특별자치도", "제주도", "제주");

    companion object {
        fun of(token: String): Province? = entries.firstOrNull { token in it.names }
    }
}

data class RegionFact(val sight: String?, val food: String?)

object RegionFacts {
    private val byDistrict = mapOf(
        "수원시" to RegionFact("수원화성", "왕갈비"),
        "용인시" to RegionFact("에버랜드", null),
        "가평군" to RegionFact("남이섬", "잣"),
        "파주시" to RegionFact("임진각", "장단콩"),
        "춘천시" to RegionFact("소양강", "닭갈비"),
        "강릉시" to RegionFact("경포대", "초당순두부"),
        "속초시" to RegionFact("설악산", "오징어순대"),
        "평창군" to RegionFact("대관령", "황태"),
        "단양군" to RegionFact("도담삼봉", "마늘"),
        "천안시" to RegionFact("독립기념관", "호두과자"),
        "보령시" to RegionFact("대천해수욕장", null),
        "전주시" to RegionFact("한옥마을", "비빔밥"),
        "남원시" to RegionFact("광한루", "추어탕"),
        "여수시" to RegionFact("여수 밤바다", "갓김치"),
        "순천시" to RegionFact("순천만 습지", null),
        "목포시" to RegionFact("유달산", "낙지"),
        "담양군" to RegionFact("죽녹원", "떡갈비"),
        "경주시" to RegionFact("불국사", "황남빵"),
        "안동시" to RegionFact("하회마을", "안동찜닭"),
        "포항시" to RegionFact("호미곶", "과메기"),
        "통영시" to RegionFact("동피랑 벽화마을", "충무김밥"),
        "제주시" to RegionFact("한라산", "흑돼지"),
        "서귀포시" to RegionFact("천지연폭포", "흑돼지"),
    )

    private val byProvince = mapOf(
        Province.SEOUL to RegionFact("경복궁", null),
        Province.BUSAN to RegionFact("해운대", "돼지국밥"),
        Province.DAEGU to RegionFact("서문시장", "막창"),
        Province.INCHEON to RegionFact("차이나타운", "짜장면"),
        Province.GWANGJU to RegionFact("무등산", "떡갈비"),
        Province.DAEJEON to RegionFact("성심당", "칼국수"),
        Province.ULSAN to RegionFact("대왕암공원", "언양불고기"),
        Province.SEJONG to RegionFact("세종호수공원", null),
        Province.JEJU to RegionFact("한라산", "흑돼지"),
    )

    fun of(region: Region): RegionFact? = byDistrict[region.district]
        ?: byProvince[region.province].takeIf { region.province.metropolitan || region.province == Province.SEJONG || region.province == Province.JEJU }
}

internal fun String.particle(withFinal: String, without: String): String {
    val c = trim().lastOrNull() ?: return without
    if (c !in '가'..'힣') return "$withFinal($without)"
    return if ((c - '가') % 28 != 0) withFinal else without
}
