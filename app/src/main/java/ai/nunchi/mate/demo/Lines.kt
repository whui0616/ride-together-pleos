package ai.nunchi.mate.demo

import ai.nunchi.engine.suggest.SuggestionKind
import ai.nunchi.mate.avatar.Personality

object Lines {

    fun suggest(kind: SuggestionKind, p: Personality, place: String, dist: String): String = when (kind) {
        SuggestionKind.MEAL -> when (p) {
            Personality.ENERGETIC -> "$dist 앞에 $place! 짬뽕 맛집이래요. 들를래요?"
            Personality.CALM -> "$dist 앞에 $place 있어요. 들를까요?"
            Personality.PLAYFUL -> "배 안 고파요? $dist 앞 $place, 지나치면 후회할걸요. 갈까요?"
            Personality.WARM -> "식사 시간이에요. $dist 앞 $place 어때요? 들를까요?"
            Personality.COOL -> "$dist 앞 $place. 들르면 10분. 갈까요?"
        }
        SuggestionKind.DRIVE_THRU -> when (p) {
            Personality.ENERGETIC -> "비 오는 날엔 따뜻한 거죠! $dist 앞 드라이브스루 들를래요?"
            Personality.CALM -> "$dist 앞에 드라이브스루 있어요. 따뜻한 거 하나 어때요?"
            Personality.PLAYFUL -> "커피 없이 버티게요? $dist 앞 드라이브스루, 갈까요?"
            Personality.WARM -> "비도 오는데 따뜻한 거 하나 어때요? $dist 앞 드라이브스루요."
            Personality.COOL -> "$dist 앞 드라이브스루. 안 내려도 돼요. 갈까요?"
        }
        SuggestionKind.REST -> when (p) {
            Personality.ENERGETIC -> "벌써 한참 달렸어요! $dist 앞 휴게소에서 잠깐 쉴래요?"
            Personality.CALM -> "오래 달렸어요. $dist 앞 휴게소에서 쉴까요?"
            Personality.PLAYFUL -> "눈 감기면 안 돼요. $dist 앞 휴게소, 들를까요?"
            Personality.WARM -> "피곤하죠. $dist 앞 휴게소에서 잠깐 쉬어 가요. 어때요?"
            Personality.COOL -> "1시간 40분 주행. $dist 앞 휴게소. 쉴까요?"
        }
        SuggestionKind.CHARGE -> when (p) {
            Personality.ENERGETIC -> "배터리가 얼마 안 남았어요! $dist 앞 충전소 넣을까요?"
            Personality.CALM -> "배터리가 낮아요. $dist 앞 충전소를 경유지에 넣을까요?"
            Personality.PLAYFUL -> "배터리 보셨어요? $dist 앞 충전소 넣어 둘까요?"
            Personality.WARM -> "배터리가 걱정돼요. $dist 앞 충전소 들렀다 가요. 괜찮죠?"
            Personality.COOL -> "배터리 22%. $dist 앞 충전소. 경유지에 넣을까요?"
        }
        SuggestionKind.STORY -> when (p) {
            Personality.ENERGETIC -> "여기 $place! 커피 거리로 유명해진 건 2000년대 안목해변부터래요."
            Personality.CALM -> "$place 지나고 있어요. 커피 거리는 안목해변에서 시작됐대요."
            Personality.PLAYFUL -> "$place 왔는데 커피 안 마시면 섭섭하죠. 안목해변이 시작이래요."
            Personality.WARM -> "$place 이에요. 커피 거리 이야기 하나 해 줄게요. 안목해변에서 시작됐대요."
            Personality.COOL -> "$place. 커피 거리의 시작은 2000년대 안목해변."
        }
        SuggestionKind.ROUTE -> when (p) {
            Personality.ENERGETIC -> "$place 까지 바로 갈까요? 경로 잡을게요!"
            Personality.CALM -> "$place 까지 경로를 잡을까요?"
            Personality.PLAYFUL -> "$place 가는 거 맞죠? 경로 잡을까요?"
            Personality.WARM -> "$place 까지 안내할게요. 경로 잡을까요?"
            Personality.COOL -> "$place. 경로 설정할까요?"
        }
    }

    fun accepted(p: Personality, place: String): String = when (p) {
        Personality.ENERGETIC -> "좋아요! $place 경유지에 넣었어요."
        Personality.CALM -> "$place, 경유지에 넣었어요."
        Personality.PLAYFUL -> "역시. $place 넣었어요."
        Personality.WARM -> "$place 넣었어요. 천천히 가요."
        Personality.COOL -> "$place 추가. 경로 갱신했어요."
    }

    fun declined(p: Personality): String = when (p) {
        Personality.ENERGETIC -> "오케이, 다음에요!"
        Personality.CALM -> "네, 다음에요."
        Personality.PLAYFUL -> "흠, 알겠어요. 다음엔 안 봐줘요."
        Personality.WARM -> "알겠어요. 가고 싶을 때 말해요."
        Personality.COOL -> "취소."
    }

    fun later(p: Personality): String = when (p) {
        Personality.ENERGETIC -> "알겠어요, 조금 있다 다시 물을게요!"
        Personality.CALM -> "네, 조금 있다 다시 말할게요."
        Personality.PLAYFUL -> "미루기 전문가네요. 이따 다시 물을게요."
        Personality.WARM -> "그래요, 이따가요. 급할 거 없어요."
        Personality.COOL -> "보류. 잠시 후 다시."
    }

    fun yellow(p: Personality): String = when (p) {
        Personality.ENERGETIC -> "주변 같이 볼게요!"
        Personality.CALM -> "천천히, 주변을 같이 볼게요"
        Personality.PLAYFUL -> "앞에 좀 복잡하네요. 조용히 있을게요"
        Personality.WARM -> "괜찮아요, 천천히 가요"
        Personality.COOL -> "주변 주시 중"
    }

    fun interrupted(p: Personality): String = when (p) {
        Personality.ENERGETIC -> "잠깐! 운전 먼저요"
        Personality.CALM -> "잠깐, 운전에 집중할게요"
        Personality.PLAYFUL -> "앗, 이따 말할게요"
        Personality.WARM -> "괜찮아요, 운전 먼저 해요"
        Personality.COOL -> "일시 중지"
    }

    fun tap(p: Personality, hour: Int): String = when (hour) {
        in 5..10 -> when (p) {
            Personality.ENERGETIC -> "좋은 아침! 오늘은 어디 가요?"
            Personality.CALM -> "좋은 아침이에요. 천천히 준비해요."
            Personality.PLAYFUL -> "벌써 일어났어요? 의외네요."
            Personality.WARM -> "잘 잤어요? 아침은 먹었고요?"
            Personality.COOL -> "아침. 출발 준비 완료."
        }
        in 11..16 -> when (p) {
            Personality.ENERGETIC -> "점심 먹었어요? 저는 배터리로 충분해요!"
            Personality.CALM -> "오후예요. 쉬었다 가도 괜찮아요."
            Personality.PLAYFUL -> "또 찔러 보는 거예요? 간지러워요."
            Personality.WARM -> "오늘 하루 어때요? 무리하지 말아요."
            Personality.COOL -> "용건 있어요?"
        }
        in 17..21 -> when (p) {
            Personality.ENERGETIC -> "퇴근이다! 오늘도 수고했어요!"
            Personality.CALM -> "저녁이에요. 오늘도 수고했어요."
            Personality.PLAYFUL -> "집에 가는 길이죠? 딴 데 새지 말고요."
            Personality.WARM -> "오늘 고생 많았어요. 얼른 쉬어요."
            Personality.COOL -> "하루 끝. 수고했어요."
        }
        else -> when (p) {
            Personality.ENERGETIC -> "우와, 이 시간에? 졸리면 꼭 쉬어요!"
            Personality.CALM -> "늦었어요. 졸리면 쉬었다 가요."
            Personality.PLAYFUL -> "으음... 자는데 깨웠어요?"
            Personality.WARM -> "늦게까지 고생해요. 졸리면 꼭 쉬어요."
            Personality.COOL -> "늦은 시간. 졸음 주의."
        }
    }

    fun setEffect(p: Personality, season: String): String = when (p) {
        Personality.ENERGETIC -> " 세트 완성! 완전 찰떡이에요!"
        Personality.CALM -> " 느낌이 나네요. 좋아요."
        Personality.PLAYFUL -> " 룩 풀세트? 오늘 좀 하네요."
        Personality.WARM -> " 옷으로 맞춰 입혀 줬네요. 고마워요."
        Personality.COOL -> " 세트. 통일감 있어요."
    }

    fun shopBought(p: Personality): String = when (p) {
        Personality.ENERGETIC -> "우와, 바로 입어 볼게요!"
        Personality.CALM -> "...잘 어울리나요?"
        Personality.PLAYFUL -> "센스 좀 있네요? 인정."
        Personality.WARM -> "고마워요. 아껴 입을게요."
        Personality.COOL -> "나쁘지 않은 선택이에요."
    }

    fun pet(p: Personality, ok: Boolean): String = if (ok) when (p) {
        Personality.ENERGETIC -> "헤헤, 기분 좋아요!"
        Personality.CALM -> "...고마워요."
        Personality.PLAYFUL -> "머리 망가져요. ...한 번만 더요."
        Personality.WARM -> "따뜻하네요. 고마워요."
        Personality.COOL -> "...나쁘지 않네요."
    } else when (p) {
        Personality.ENERGETIC -> "오늘은 충분히 받았어요! 내일 또 해 줘요!"
        Personality.CALM -> "오늘은 충분해요. 내일 또요."
        Personality.PLAYFUL -> "너무 많이 하면 닳아요. 내일요."
        Personality.WARM -> "오늘 마음 충분히 받았어요. 내일 또 봐요."
        Personality.COOL -> "오늘은 여기까지."
    }

    fun doubleTap(p: Personality): String = when (p) {
        Personality.ENERGETIC -> "우와! 깜짝이야! 한 번 더!"
        Personality.CALM -> "...놀랐잖아요."
        Personality.PLAYFUL -> "엇, 들켰다."
        Personality.WARM -> "어머, 깜짝 놀랐어요. 괜찮아요?"
        Personality.COOL -> "...뭐예요."
    }

    fun wake(p: Personality): String = when (p) {
        Personality.ENERGETIC -> "으아... 벌써 아침이에요? 아, 아니네."
        Personality.CALM -> "...깨웠어요? 괜찮아요."
        Personality.PLAYFUL -> "5분만 더... 아니, 5초만."
        Personality.WARM -> "이 시간에 운전해요? 졸리면 꼭 쉬어요."
        Personality.COOL -> "...새벽이에요."
    }

    fun bored(p: Personality): String = when (p) {
        Personality.ENERGETIC -> "심심해요! 뭐 해요?"
        Personality.CALM -> "...아직 거기 있어요?"
        Personality.PLAYFUL -> "저 혼자 두면 삐뚤어질 거예요."
        Personality.WARM -> "무슨 생각 해요? 말해 줘요."
        Personality.COOL -> "할 일 없으면 출발해요."
    }

    fun snack(p: Personality, ok: Boolean): String = if (ok) when (p) {
        Personality.ENERGETIC -> "냠냠! 이거 제일 좋아해요!"
        Personality.CALM -> "잘 먹을게요."
        Personality.PLAYFUL -> "뇌물이에요? ...맛있네요."
        Personality.WARM -> "고마워요. 같이 먹어요."
        Personality.COOL -> "...먹을게요."
    } else when (p) {
        Personality.ENERGETIC -> "배불러요! 코인도 아끼고요!"
        Personality.CALM -> "오늘은 충분해요."
        Personality.PLAYFUL -> "더 먹으면 살쪄요. 내일요."
        Personality.WARM -> "마음만 받을게요. 내일 또요."
        Personality.COOL -> "오늘은 그만."
    }

    fun moodLine(p: Personality, band: ai.nunchi.engine.MoodBand): String = when (band) {
        ai.nunchi.engine.MoodBand.GREAT -> when (p) {
            Personality.ENERGETIC -> "오늘 기분 최고예요!"
            Personality.CALM -> "기분 좋아요."
            Personality.PLAYFUL -> "오늘은 봐줄게요."
            Personality.WARM -> "같이 있어서 좋아요."
            Personality.COOL -> "컨디션 양호."
        }
        ai.nunchi.engine.MoodBand.GOOD -> when (p) {
            Personality.ENERGETIC -> "괜찮아요! 더 좋아질 수 있어요!"
            Personality.CALM -> "평소 같아요."
            Personality.PLAYFUL -> "그럭저럭이요."
            Personality.WARM -> "괜찮아요. 고마워요."
            Personality.COOL -> "보통."
        }
        ai.nunchi.engine.MoodBand.LOW -> when (p) {
            Personality.ENERGETIC -> "조금 심심했어요..."
            Personality.CALM -> "조금 조용했어요."
            Personality.PLAYFUL -> "요즘 왜 안 왔어요?"
            Personality.WARM -> "보고 싶었어요."
            Personality.COOL -> "...오랜만이네요."
        }
        ai.nunchi.engine.MoodBand.BAD -> when (p) {
            Personality.ENERGETIC -> "많이 기다렸어요..."
            Personality.CALM -> "오래 기다렸어요."
            Personality.PLAYFUL -> "잊은 줄 알았어요."
            Personality.WARM -> "괜찮아요. 와 줘서 고마워요."
            Personality.COOL -> "...왔네요."
        }
    }

    val fortunes = listOf(
        "오늘은 깜빡이를 3초 먼저. 뒤차가 고마워할 거예요.",
        "급할수록 한 박자 늦게 출발해요. 1초 차이예요.",
        "두 시간 넘게 달리면 휴게소. 캐릭터도 쉬고 싶어요.",
        "앞차와 거리를 한 대 더. 마음이 한결 편해져요.",
        "오늘 처음 가는 길이면 경로를 미리 보고 출발해요.",
        "비 오는 날엔 평소보다 20% 천천히.",
        "어린이보호구역은 30. 숫자가 아니라 약속이에요.",
        "졸리면 노래보다 창문. 그래도 졸리면 쉬어요.",
        "주차는 천천히, 출발은 더 천천히.",
        "오늘도 무사히. 그게 제일 큰 목표예요.",
    )

    fun birthday(p: Personality, name: String): String = when (p) {
        Personality.ENERGETIC -> "생일 축하해요!!! 오늘은 제가 더 신나요!"
        Personality.CALM -> "생일 축하해요. 좋은 하루 보내요."
        Personality.PLAYFUL -> "생일이네요? 선물은 안전운전으로 받을게요."
        Personality.WARM -> "생일 축하해요. 태어나 줘서 고마워요."
        Personality.COOL -> "생일. 축하해요."
    }

    fun arrival(p: Personality, xp: Int): String = when (p) {
        Personality.ENERGETIC -> "도착! 오늘도 같이 달려서 신났어요. 경험치 ${xp}까지 쌓였어요!"
        Personality.CALM -> "도착했어요. 오늘도 함께해서 좋았어요. 경험치 ${xp}이에요."
        Personality.PLAYFUL -> "도착. 저 없었으면 심심했겠죠? 경험치 ${xp}."
        Personality.WARM -> "수고했어요. 오늘도 같이 다녀와서 기뻐요. 경험치 ${xp}."
        Personality.COOL -> "도착. 경험치 ${xp}."
    }
}
