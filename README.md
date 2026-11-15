# car-pet

팀 함께타요 (ride_together)

내 차에서 함께 자라는 반려 캐릭터. 운전이 복잡할 때는 조용히 있고, 여유가 생기면 먼저 말을 거는 Pleos Connect 앱입니다.

## 주요 기능

- **말할 때를 아는 캐릭터**: 내비·방향지시등·와이퍼·브레이크 신호로 운전 부하를 계산해, 복잡할 때는 침묵하고 여유로울 때만 말합니다.
- **정차 화면(로비)**: 옷장(머리·옷·눈·얼굴·배경·목소리·성격·아우라), 상점, 업적과 추억 앨범, 순위(전국·차종별), 친구.
- **함께한 기록으로 성장**: 출석, 함께 다녀온 길, 처음 가 본 지역, 같이 한 출근·퇴근이 업적과 추억 카드로 남습니다. 주행 거리·시간에는 보상이 없습니다.
- **음성 대화와 차량 조작**: "히터 틀어 줘"처럼 말하면 Pleos Vehicle SDK로 차량을 조작합니다. 대화 분류는 기기 안의 소형 모델로 처리합니다.
- **주행 중 화면**: 캐릭터와 한 줄 문장만 보여 주고 애니메이션은 없습니다.

## APK

APK가 GitHub 파일 한도(100MB)를 넘어(약 414MB) 저장소의 `apk/` 폴더 대신 GitHub Releases에 올렸습니다.

- 파일 이름: `ride_together_car-pet_release.apk`
- Release 페이지: https://github.com/whui0616/ride-together-pleos/releases/tag/v1.0.0

## 실행 방법

### APK 설치

1. Pleos Connect 에뮬레이터를 켭니다.
2. Release 페이지에서 받은 APK를 설치합니다.

   ```bash
   adb install -r ride_together_car-pet_release.apk
   ```

3. 에뮬레이터 앱 목록에서 **car-pet**을 실행합니다. 처음 실행하면 캐릭터 성별을 고르는 화면이 나옵니다.

### 소스에서 빌드

```bash
gradlew.bat :engine:test
```

```bash
gradlew.bat :app:assembleRelease
```

- 결과물: `app/build/outputs/apk/release/app-release.apk`
- `local.properties`에 Android SDK 경로(`sdk.dir`)가 있어야 합니다. Android Studio로 프로젝트를 열면 자동으로 만들어집니다.
- Pleos SDK는 `nexus-playground.pleos.ai` 저장소에서 받습니다. `gradle.properties`의 `pleos.sdk=false`로 바꾸면 SDK 없이 빌드됩니다.

## 테스트 환경 및 필요한 설정

| 항목 | 내용 |
|---|---|
| 개발 PC | Windows 11, Android Studio(내장 JDK) |
| Android SDK | compileSdk 37, targetSdk 34, minSdk 28 |
| ABI | x86_64(에뮬레이터), arm64-v8a |
| Pleos SDK | NaviHelper, Vehicle, TextToSpeech, SpeechToText |

- **알림 권한**: 처음 실행할 때 나오는 알림 권한 요청을 허용합니다.
- **마이크**: 음성 대답·항상 듣기를 쓰려면 에뮬레이터의 마이크 입력을 켭니다.
- **차량 신호**: 속도·기어·방향지시등·와이퍼·브레이크는 에뮬레이터 Extended Controls > Car data 또는 Pleos Virtual Console에서 바꿉니다.
- **기어**: P단이면 정차 화면(로비), 그 밖에는 주행 화면이 나옵니다.

## 에뮬레이터 버전과 화면 규격

| 항목 | 내용 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator `release_connect_v2.1.0` |
| OS | Android 14 (API 34), Automotive, x86_64 |
| 화면 | 2560 × 1440 px, 가로, 160 dpi |

## 구조

| 모듈 | 내용 |
|---|---|
| `engine/` | 순수 Kotlin. 운전 부하 점수, 말할 시점 판단, 제안 엔진, 성장·업적·상점·순위, 대화 분류, 차량 명령 해석. JUnit 테스트 포함 |
| `app/` | Android 앱. 정차 화면(로비)·주행 화면, 캐릭터 그리기, 음성 입출력, Pleos SDK 연동 |
| `tools/` | 실행용 배치 파일, 캐릭터 그림·대화 분류 모델을 만드는 스크립트 |

## 라이선스 고지

- 대화 분류 모델: `jhgan/ko-sroberta-multitask` (CC BY-SA 4.0). 고지는 `app/src/main/assets/topic/NOTICE.txt`에 있습니다.
