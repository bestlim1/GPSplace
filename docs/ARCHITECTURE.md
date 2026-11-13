# 설계 문서 — GPS 머문 장소 기록 (팀 신서임당)

고급모바일프로그래밍 팀 프로젝트. 보호대상자(노인·아동) 휴대폰이 위치를 기록하고, 연결된 보호자가 최신 위치·이동 경로·체류 장소·구역 알림을 확인하는 Android 앱이다.

## 기준 문서

- `분석서_신서임당_임재윤서지민_수정본2.pdf` — 요구사항 분석서. FR-01~FR-09, NFR-01~NFR-05, UC-01.
- `UI설계보고서_신서임당_임재윤서지민.pdf` — 화면 S-01~S-17, 다이얼로그 D-01~D-04, 상태 화면, 뒤로가기, 디자인 기준표(부록 A).
- `SCREENS.md` — 위 UI 보고서를 텍스트로 옮긴 화면 명세.
- `PLAN.md` — 단계별 작업 순서와 완료 확인 기준.

문서와 코드가 어긋나면 코드를 문서에 맞춘다. 화면에 있는 기능은 분석서에도 있어야 한다(과제 규칙).

## 구현 범위

| 구현 (FR-01~06) | 하지 않음 (FR-07~09, Won't) |
|---|---|
| 기기 연결·해제, 위치 수집·경로 표시, 체류 기록, 지정 구역 알림, 날짜별 조회, 오프라인 기록·동기화 | AI 경로 예측·요약, 건강·웨어러블 연동, 경찰·소방 자동 신고·전화 |

### 구현하지 않는 기능의 인수 조건 (분석서 Won't)

- AndroidManifest(**병합된 매니페스트 포함**)에 다음 권한이 있으면 안 된다: `BODY_SENSORS`, `ACTIVITY_RECOGNITION`, `BLUETOOTH_CONNECT`, `CALL_PHONE`, `SEND_SMS`. 라이브러리가 끼워 넣으면 `tools:node="remove"`로 제거하고, 빌드 후 `app/build/intermediates/merged_manifests/`에서 확인한다.
- 예측 경로, 위험도, AI 요약, 심박수·걸음 수, 자동 전화·문자 발신 UI나 코드를 만들지 않는다. 지도에는 실제 수집 좌표로 그린 경로만 그린다.
- 위치 좌표·수집 시각 외의 개인 정보를 수집하지 않는다 (S-14 동의 화면 문구와 일치해야 함).

## 기술 스택

- Kotlin, Jetpack Compose (Material 3), Navigation Compose, ViewModel + StateFlow, Coroutines
- 위치: Google Play services `FusedLocationProviderClient` + 포그라운드 서비스(`foregroundServiceType="location"`)
- 로컬 저장: Room (KSP), 설정값: DataStore
- 동기화: WorkManager (네트워크 연결 조건, 지수 백오프)
- 지도: Google Maps SDK for Android + `maps-compose`
- 서버: Firebase — Authentication(익명 로그인), Cloud Firestore, Cloud Messaging(FCM), Cloud Functions(알림 푸시 발송)
  - 프로젝트는 Blaze 요금제로 전환됨. Cloud Functions에 서비스별 지출 한도(spend cap)를 걸어 둔다. Firestore는 한도 대상이 아니라 예산 알림만 있으므로, 아래 데이터 모델의 읽기 절약 규칙을 지킨다. 개발·시험은 가능한 한 Firebase Local Emulator Suite에서 한다.
- DI: 수동 `AppContainer` (Hilt 사용 안 함 — 2인 프로젝트 규모)
- minSdk 29 (백그라운드 위치 권한 흐름 단순화), targetSdk·라이브러리는 개발 시점의 최신 안정판을 사용.
- 시간대: 모든 날짜 구분(`dayKey`, “오늘”)은 `Asia/Seoul` 기준.

비밀값: `google-services.json`, `MAPS_API_KEY`(local.properties)는 커밋하지 않는다. `.gitignore`에 넣는다.

### 빌드 환경 (M1~M8 구현 기준, 2026-10-30)

- 프로젝트 이름 `GPSplace`, `namespace`·`applicationId` = `com.example.gpsplace`
- AGP 9.3.3, Gradle 9.5.0, Kotlin 2.4.21, compileSdk·targetSdk 37, minSdk 29, Java 17 (maps-compose 9.x가 JVM 17 바이트코드라 11로는 인라인 컴파일이 안 된다)
- Firestore 데이터베이스 이름은 기본값이 아니라 **`gpsplace`**(서울 리전 asia-northeast3)다. 앱은 `FirebaseSource.DATABASE_ID`, 규칙 배포는 `firebase.json`, Cloud Function은 `database: "gpsplace"`로 지정한다.
- 서버 배포: `firebase deploy --only firestore`(규칙·인덱스), `firebase deploy --only functions`(onAlertCreated, asia-northeast3). 규칙 테스트: `functions/`에서 `npm run test:rules`.
- 의존성은 **버전 카탈로그 `gradle/libs.versions.toml`**로만 추가한다 (build.gradle.kts에 좌표를 직접 쓰지 않음). M0에서 모든 라이브러리를 그 시점 최신 안정판으로 맞췄다.
- AGP 9는 Kotlin 지원이 내장되어 있다. `org.jetbrains.kotlin.android` 플러그인이 없는 것이 정상이며, 추가하지 않는다.
  - 내장 Kotlin의 기본 KGP는 2.2.10인데, Firebase BoM 35.x·maps-compose 9.x가 Kotlin 2.4 메타데이터로 컴파일되어 있어 2.2 컴파일러로는 빌드가 깨진다. 그래서 최상위 `build.gradle.kts`의 `buildscript { classpath(libs.kotlin.gradle.plugin) }`로 KGP를 올렸다 (AGP 9 릴리스 노트의 방법).
  - Kotlin 버전을 바꿀 때는 카탈로그의 `kotlin` 하나만 고친다. KGP와 compose 컴파일러 플러그인이 같은 값을 쓴다.
  - KSP는 2.3.x부터 Kotlin 버전과 따로 간다. AGP 9 내장 Kotlin과 함께 쓰려면 2.3.10 이상이어야 한다.
- `app/google-services.json`이 들어 있고 google-services 플러그인을 적용했다. 이 파일은 커밋하지 않는다(`.gitignore`).
- 금지 권한 검사: `./gradlew checkForbiddenPermissions`로 실행하고, `assemble*`에도 연결되어 있다. 검사 코드는 `app/build.gradle.kts`에 있다.
- 디버그 빌드에는 두 번째 아이콘 “GPSplace 화면 목록”이 있다(`app/src/debug/`). 여기서 S-01~S-17로 바로 간다. 릴리스 빌드에는 들어가지 않는다.

## 저장소 구조

```
app/src/main/java/com/example/gpsplace/
  AppContainer.kt, GPSplaceApp.kt, MainActivity.kt(진입점 분기)
  data/local/        Room: PointEntity, StayEntity, AlertEntity, ZoneCacheEntity, DAO / SettingsStore(DataStore)
  data/remote/       FirebaseSource(Auth·Firestore “gpsplace”·FCM), 문서 ↔ 모델 변환, 스냅샷 Flow
  data/repo/         LinkRepository(연결), LocationRepository(기록·체류·구역 판정), SyncRepository(전송),
                     TrackRepository(보호자 조회), ZoneRepository(구역)
  domain/            StayDetector, ZoneDetector, GapDetector, DelayPolicy, SeoulTime  ← 순수 Kotlin, 단위 테스트 필수
  location/          LocationService(포그라운드), RecordingController, PermissionState, BootReceiver
  sync/              UploadWorker
  notify/            NotificationChannels, FcmService
  ui/theme/          부록 A 색·글자·여백 토큰
  ui/components/     StateBanner, SkeletonList, EmptyState, ErrorState, StatusChip, ConfirmDialog, Buttons, AppTopBar,
                     DateBar, MapParts(지도 표기), AppIcons
  ui/screens/        s01_role … s17_sync (화면 ID로 패키지 이름), history(S-04·S-05 공통)
  nav/               AppNavHost, Routes, GuardianBottomBar
functions/           Cloud Functions (TypeScript): onAlertCreated → 보호자 FCM
firestore.rules, firestore.indexes.json
docs/
```

## 데이터 모델

### Firestore

```
users/{uid}               role: "guardian"|"protected", fcmToken, linkId?, createdAt
codes/{code}              guardianUid, status: "waiting"|"pending_consent"|"consumed"|"denied"|"cancelled",
                          protectedUid?, linkId?, createdAt, expiresAt (= createdAt + 10분)
links/{linkId}            guardianUid, protectedUid, status: "active"|"released", codeId, createdAt, releasedAt?,
                          lastPoint {lat,lng,t}, pendingCount, permission {location: "granted"|"denied", service: "on"|"off"}, reportedAt
                          ← codeId: 이 연결을 만든 코드. 규칙이 “동의한 코드로만 연결 생성”을 검사하는 데 쓴다
links/{linkId}/pointHours/{hourKey}   dayKey, hour(0~23), startAt, updatedAt,
                                      points: { "p{t}": {lat, lng, accuracy, t}, ... }
                                      ← hourKey = "YYYY-MM-DD_HH" (Asia/Seoul), 위치를 1시간 단위 문서 하나에 묶음
links/{linkId}/stays/{stayId}     startAt, endAt, lat, lng, dayKey, durationSec
links/{linkId}/zones/{zoneId}     name(1~10자), type: "safe"|"caution", lat, lng, radiusM(50~2000), updatedAt
links/{linkId}/alerts/{alertId}   zoneId, zoneName, type: "safe_exit"|"caution_enter", p1 {lat,lng,t}, p2 {lat,lng,t},
                                  zone {lat,lng,radiusM}, createdAt
                                  ← zone: 판정 당시 구역 모양. 구역을 나중에 고치거나 지워도 S-09에 그때 원을 그린다
```

- **연결 해제:** 해제하는 쪽이 `status = "released"`, `releasedAt`과 함께 `lastPoint`를 지운다. 해제된 links 문서 자체는 당사자가 읽을 수 있어(위치는 없음) 상대 기기가 해제를 알아챈다. 하위 컬렉션은 둘 다 읽을 수 없다.

- **위치는 1시간 단위 문서 (비용):** 위치 점 하나를 문서 하나로 만들지 않는다. 30초 주기면 1시간 문서에 최대 120점(약 십수 KB, 문서 한도 1MiB의 수 % 수준)이 들어간다. 보호자가 하루 경로를 읽을 때 `pointHours.where(dayKey == X)` → 최대 24건만 읽는다. 화면에서는 시간 문서들의 `points`를 펼쳐 `t` 순으로 정렬해 쓴다.
- **중복 없는 전송 (FR-06):** 점의 맵 키는 수집 시각 `"p{t}"`(epoch ms). 업로드는 `set(hourDoc, {dayKey, hour, startAt, updatedAt, points: {키: 값}}, SetOptions.merge())` → 같은 점을 다시 올려도 같은 키를 덮어쓸 뿐 점이 늘지 않는다. 한 번의 업로드에서 같은 시간대 점들은 문서 1건 쓰기로 묶는다. stays·alerts는 기기에서 만든 ID로 `set`, `alertId = "{zoneId}_{p2.t}"`.
- **읽기 아끼기:** 보호자 화면은 오늘 시간 문서와 `links/{linkId}`에 스냅샷 리스너를 붙여 바뀐 문서만 받는다. 화면에 들어올 때마다 하루치를 새로 `get()`하지 않는다. Firestore 오프라인 캐시(기본 켜짐)를 그대로 쓴다. 과거 날짜는 한 번 읽으면 캐시에서 다시 쓴다.
- **접근 제어 (NFR-04):** `firestore.rules`에서 `links/{linkId}`와 하위 컬렉션은 `status == "active"`이고 요청자가 guardianUid 또는 protectedUid일 때만 읽기 허용. pointHours·stays·alerts 쓰기는 protectedUid만(pointHours는 `hourKey`와 `dayKey`·`hour`가 일치하는지 검사), zones 쓰기는 guardianUid만. 연결 해제(`released`) 뒤에는 둘 다 읽기 불가. 규칙 테스트를 에뮬레이터로 작성한다.
- 코드는 6자리 숫자, 유효 10분. `codes`는 목록 조회 불가, 정확한 ID로만 get.

### Room (보호대상자 기기)

`points(id, lat, lng, accuracy, t, dayKey, hourKey, syncedAt?)`, `stays(...)`, `alerts(...)`, `zones_cache(...)`. 로컬에서는 점 하나가 행 하나다. `syncedAt == null`이 미전송이며, 업로드할 때 `hourKey`별로 묶어 시간 문서에 병합한다. S-17의 “대기 n건”, “오늘 전송 n건”은 이 표에서 점 단위로 센다.

## 핵심 규칙 (분석서 수치 그대로)

- 위치 수집 요청 주기 30초, 정확도 기록 (`accuracy` 저장, NFR-01 시험용).
- **체류 판정 (FR-03):** 앵커 = 구간 첫 점. 새 점이 앵커에서 50m 이내이고 직전 점과 간격 2분 이하이면 같은 후보 구간. 후보가 5분 이상 이어지면 체류로 저장(대표 좌표 = 구간 점들의 중심, 시작 = 앵커 시각, 종료 = 마지막 점 시각)하고, 계속 머무는 동안 같은 stayId로 종료 시각을 갱신한다. 간격 2분 초과면 구간을 끊고 새 앵커로 시작한다.
- **구역 판정 (FR-04):** 구역마다 inside/outside 상태를 둔다. 연속 2점이 모두 구역 안이면 inside, 모두 밖이면 outside로 확정. 안전 구역 inside→outside, 주의 구역 outside→inside로 바뀔 때만 알림 1건. 처음 두 점은 상태만 정하고 알림 없음. 판정은 보호대상자 기기에서 한다(오프라인에도 마지막 위치로 판정 — NFR-03). 구역 수정은 저장 직후부터 적용, 삭제된 구역은 알림 없음, 지난 알림은 남긴다.
- **갱신 지연 (UC-01 A4):** 보호자 화면에서 `now - lastPoint.t > 5분`이면 갱신 지연 상태.
- **미전송 표시 (UC-01 A3):** 보호대상자 기기가 업로드할 때마다 남은 미전송 건수를 `links.pendingCount`에 기록. 보호자 화면은 `pendingCount > 0`이면 “서버 미전송 기록 있음”.

## 화면 상태 규칙 (UI 보고서 4장)

모든 데이터 화면은 `sealed interface ScreenState { Loading; Empty(reason); Error(cause, action); Content(data, partial: Partial?) }`로 그린다.

- 로딩 표시는 요청 후 300ms가 지나도 결과가 없을 때부터. 10초 응답 없으면 Error로 전환. 버튼 눌림 표시는 100ms 이내.
- Error는 “무엇이 실패 + 원인 + 다음 행동 버튼”. 오류 코드만 보여 주지 않는다. 이미 보여 준 데이터가 있으면 지우지 말고 Partial 배너로 바꾼다.
- Partial(오프라인 캐시, 갱신 지연, 전송 대기, 기록 누락)은 데이터 위에 배너 + **기준 시각**.
- 문구는 `docs/SCREENS.md`의 문구를 그대로 쓴다. 특히 FR-05 빈 상태는 정확히 “기록이 없습니다”.

## 내비게이션·뒤로가기 (UI 보고서 2장)

- 보호자: 하단 탭 3개(위치·기록·알림), 시작 탭 = 위치(S-03). 탭 전환은 백스택에 쌓지 않고 탭별 상태 저장(`saveState/restoreState`). 탭 루트에서 뒤로 → S-03, S-03에서 뒤로 → 앱 백그라운드.
- 기록 탭 안 상단 탭(경로·체류)은 같은 날짜 공유, 지도 위 스와이프 전환 끔.
- 보호대상자: 탭 없음, 루트 S-16.
- 연결·권한 설정(S-01, S-02, S-13~S-15)은 완료 시 스택에서 모두 제거 (`navigateClearing` = `popUpTo(graph.id) { inclusive = true }`. `popUpTo(0)`은 Navigation에서 아무것도 지우지 않는다).
- 폼(S-11)은 변경이 있으면 뒤로가기에서 D-02. 다이얼로그·시트는 뒤로가기 = 취소.
- 구역 알림 푸시 → S-09, 백스택은 S-03 → S-08 → S-09.

## 디자인·접근성 (UI 보고서 부록 A, 6장)

- 색: Primary `#1D5BB9`, Text `#1A1A1A`, Text2 `#5C6166`, Surface `#F3F5F7`, Safe `#1B7A3A`, Caution `#A85A00`, Error `#C0282D`, Missing `#6B6F73`, Map base `#ECE9E3`(지도 바탕), 배너 주의 `#FFF3E0`, 배너 오류 `#FDECEC`, Divider `#D5D8DC`. 다른 색을 새로 만들지 않는다.
- 글자 sp만 사용(코드 32 / 큰 상태 24 / 제목 20 / 본문 16, 보호대상자 화면 18 / 보조 14 / 최소 12는 지도 라벨만). 글자가 들어가는 컨테이너에 고정 높이 금지, 200% 글자 크기에서 스크롤로 전부 보여야 함.
- 터치 영역 최소 48dp, 버튼 높이 48dp(보호대상자 56dp), 목록 행 64dp 이상·행 전체 클릭.
- 색만으로 구분 금지: 안전 구역 = 실선+방패 아이콘+“안전 구역”, 주의 구역 = 점선+삼각형+“주의 구역”, 기록 누락 = 점선+“기록 누락”, 상태 칩 = 아이콘+글자. 아이콘에는 `contentDescription`.

## 개발 방식

- `PLAN.md`의 단계를 순서대로 진행하고, 단계마다 “완료 확인” 기준을 둔다.
- `domain/`의 판정 로직은 분석서 인수 조건을 테스트 케이스로 옮겨 테스트를 먼저 작성한다 (예: 50m·5분 체류, 2분 초과 분리, 연속 2점 판정).
- 빌드: `./gradlew assembleDebug`, 단위 테스트: `./gradlew testDebugUnitTest`, Firestore 규칙 테스트: `functions/`에서 에뮬레이터로 실행.
- 커밋 메시지에 관련 요구사항 ID를 적는다 (예: `FR-03 체류 판정 구현`).
