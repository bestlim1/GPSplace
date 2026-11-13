# 구현 계획 — 단계별 진행과 완료 확인 기준

순서는 제안서의 **“가장 위험한 가정”**을 따른다: 지도 화면보다 먼저, 화면을 끄거나 앱을 정리해도 위치 기록이 이어지는지 검증한다. 한 단계가 끝나면 “완료 확인”을 실제로 해 보고 다음 단계로 간다.

## 0. 개발 환경 준비

1. ~~Android Studio에서 빈 프로젝트 생성~~ (완료) — 이름 `GPSplace`, 패키지 `com.example.gpsplace`, 템플릿 Empty Activity(Compose). Minimum SDK가 29가 아니면 M0에서 `app/build.gradle(.kts)`의 `minSdk`를 29로 맞춘다.
2. `docs/`에 기준 문서(분석서 수정본 2, UI 설계 보고서, 화면 명세, 설계 문서)를 둔다.
3. **Firebase 프로젝트** 생성 → Android 앱 등록(패키지 이름 `com.example.gpsplace` 그대로) → `google-services.json`을 `app/`에 넣기 → Authentication에서 *익명* 로그인 사용, Cloud Firestore 생성. 에뮬레이터용으로 Firebase CLI 설치(Node.js 필요).
4. **Google Maps API 키** — Google Cloud 콘솔에서 *Maps SDK for Android* 사용 설정, 키를 패키지 이름·SHA-1로 제한, `local.properties`에 `MAPS_API_KEY=...`.
5. 실기기 2대(또는 실기기 1대 + 에뮬레이터). 백그라운드 위치 시험(M1)은 **실기기**로 해야 의미가 있다.
6. ~~Blaze 요금제 전환~~ (완료). Firebase 콘솔 Settings > Usage and billing에서 Cloud Functions 지출 한도와 Firestore 예산 알림을 설정해 둔다.

---

## M0. 프로젝트 뼈대

- Gradle 의존성 추가(Compose Navigation, Room+KSP, WorkManager, DataStore, play-services-location, maps-compose, Firebase BoM: auth·firestore·messaging). 버전은 최신 안정판 확인 후 사용
- `ui/theme`에 부록 A 토큰(색·글자·여백), `ui/components` 기본 컴포넌트(StateBanner, EmptyState, ErrorState, SkeletonList, StatusChip, ConfirmDialog)
- `nav/`에 S-01~S-17 route와 빈 화면(제목만), 보호자 하단 탭 3개, 디버그 빌드 전용 “화면 목록” 진입 메뉴
- Manifest 권한: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`
- Gradle 작업 `checkForbiddenPermissions`: 병합 매니페스트에 Won't 권한 5개가 있으면 빌드 실패

**완료 확인**
- [ ] `./gradlew assembleDebug` 성공
- [ ] `./gradlew checkForbiddenPermissions` 통과 (FR-08·FR-09 인수 조건)
- [ ] 디버그 메뉴로 S-01~S-17 이동 가능, 하단 탭 전환 후 뒤로 → 위치 탭

## M1. 위치 수집 프로토타입 (서버 없이) — 가장 위험한 가정 검증

- Room `points` 테이블, `LocationService`(포그라운드, type location, 30초 주기, 진행 알림 “위치 기록 중” → S-16)
- S-15 권한 흐름(앱 사용 중 → 설정의 ‘항상 허용’ → 알림 권한), S-16 기본·로딩·권한 없음·위치 서비스 꺼짐·저장 공간 부족 상태
- 디버그 전용: 오늘 점 목록과 간격(초) 표시, CSV 내보내기

**완료 확인 — 제안서 시험 A~D, 실기기 2종** (결과는 `docs/TEST_LOG.md`에 기종·OS·권한·절전 설정과 함께 기록)
- [ ] A. 홈으로 나가 화면 끄고 이동 → 수집 시각·누락 구간 확인
- [ ] B. 최근 앱 목록에서 밀어 제거 후 이동 → A와 비교
- [ ] C. 설정에서 강제 종료 후 이동 → 중단·재실행 후 재개 시점 확인(강제 종료 중 기록은 복원되지 않는 것이 정상)
- [ ] D. 위치는 켜고 데이터만 끈 채 이동 → 로컬에 계속 쌓이는지 (전송은 M4에서)
- [ ] 각 시험에서 점의 정확도 50m 이내 비율과 실제 수집 간격 분포 (NFR-01)

## M2. 판정 로직 (순수 Kotlin + 단위 테스트)

- `StayDetector`(FR-03), `ZoneDetector`(FR-04), `GapDetector`(2분 초과 → 기록 누락 구간), `DelayPolicy`(5분)
- 분석서 인수 조건을 테스트로: 50m 안 5분 이상 → 체류 1건, 4분 59초 → 없음, 49m/51m 경계, 간격 2분 1초 → 분리, 체류 중 종료 시각 갱신, 연속 2점 판정, 1점만 밖 → 알림 없음, 처음 두 점 → 알림 없음, 같은 이탈 반복 시 1건, 구역 수정 후 새 반경 적용, 삭제 후 알림 0건

**완료 확인**
- [ ] `./gradlew testDebugUnitTest` 전부 통과, 테스트 이름에 FR ID 포함

## M3. 기기 연결 (FR-01, NFR-04)

- 익명 로그인, `users`, `codes`, `links`. S-01, S-02(대기·완료·거부·만료), S-13(코드 오류), S-14, S-12, D-01, D-04
- `firestore.rules` + 규칙 테스트(에뮬레이터): 연결된 보호자 / 연결 안 된 사용자 / 연결 해제된 사용자
- 연결 완료 시 설정 화면 스택 제거, 해제 시 양쪽 화면 상태 전환, 보호대상자 기록 중지

**완료 확인**
- [ ] 두 기기에서 코드 입력·동의 → 보호자 화면 “연결 완료” (FR-01 Then)
- [ ] 거부, 10분 만료, 잘못된 코드 각각 안내 (UC-01 A1)
- [ ] 규칙 테스트 3종 통과, 해제 후 조회 차단 (NFR-04)

## M4. 업로드·동기화 (FR-06, NFR-03)

- `UploadWorker`(네트워크 조건, 백오프, `hourKey`별로 묶어 `pointHours` 문서에 merge), `links.lastPoint`·`pendingCount`·`permission` 갱신
- S-17 전 상태, S-16 오프라인 배너

**완료 확인**
- [ ] 비행기 모드로 10분 기록 → 해제 후 서버 시간 문서들의 점 합계 = 로컬 점 수, 업로드를 두 번 실행해도 점 수 그대로 (FR-06 “중복 없이”)
- [ ] 하루치 경로 조회 1회의 Firestore 읽기 수가 시간 문서 수(최대 24) 이하인지 에뮬레이터 UI 또는 콘솔 사용량으로 확인
- [ ] 네트워크 연결 실패 3회 시험 (NFR-05)

## M5. 보호자 위치·오늘 경로 (FR-02)

- S-03 지도·상태 카드·전 상태(로딩, 첫 위치 전, 연결 없음, 오류, 갱신 지연, 오프라인, 권한 꺼짐, 미전송), S-04 오늘
- 응답 시간 측정 로그(디버그): 홈 카드 표시, 경로 표시

**완료 확인**
- [ ] 최신 위치·수집 시각·오늘 경로 표시 (FR-02 Then)
- [ ] 보호대상자 기기 데이터를 끄고 5분 뒤 갱신 지연 표시 (A4)
- [ ] NFR-02: 홈 2초·경로 3초를 10회 측정해 9회 이상 충족, 결과 기록

## M6. 체류 (FR-03)

- 보호대상자 기기에서 `StayDetector` 실행 → Room `stays` → 업로드. S-05(구간 분리 띠 포함), S-06

**완료 확인**
- [ ] 한곳에 6분 머문 뒤 이동 → 체류 1건, 시작·종료·대표 좌표
- [ ] 목록 1초·체류 목록 3초 목표 측정 (NFR-02)

## M7. 날짜별 조회 (FR-05)

- `DateBar`, S-07(미래 날짜 불가), 과거 날짜 S-04/S-05, 기록 누락 점선, 오프라인 로컬 표시

**완료 확인**
- [ ] 기록 있는 날짜 → 경로·체류 표시, 없는 날짜 → “기록이 없습니다” (FR-05 예외)
- [ ] 날짜 선택 → 경로 표시 3초 목표 측정

## M8. 구역 알림 (FR-04, NFR-05)

- S-10, S-11(이름 1~10자, 반경 50m~2km, 지도 중심), D-02, D-03. 구역 → 보호대상자 기기 캐시
- 보호대상자 기기에서 `ZoneDetector` → `alerts` 생성 → Cloud Function `onAlertCreated` → 보호자 FCM
- S-08(구역 없음/알림 없음 구분, 알림 권한 꺼짐 배너), S-09, 푸시 → S-09 백스택(S-03 → S-08 → S-09)

**완료 확인**
- [ ] 안전 구역 이탈·주의 구역 진입 각각 알림 1건, 1점만 넘었을 때는 알림 없음
- [ ] 구역 수정 직후 새 반경 적용, 삭제 후 알림 0건, 이름 미입력 저장 안 됨 (FR-04 수정·삭제 인수 조건)
- [ ] 보호자 알림 권한 거부 3회 시험 → 배너 표시 (NFR-05)
- [ ] 구역 이탈 시나리오 3회: 보호자 알림만, 전화·문자 발신 0건 (FR-09)

## M9. 마무리 점검

- UI 보고서 4.8 상태 점검표의 28개 + 부분 14개 상태를 실제 화면으로 하나씩 확인(스크린샷을 `docs/screenshots/`에)
- 접근성: 글자 크기 200%에서 잘림 없음, TalkBack으로 아이콘 이름 읽힘, 터치 영역 48dp(레이아웃 검사기), 색 없이 구분(회색조 모드)
- Won't 확인: 병합 매니페스트 권한 검사, 예측·AI·건강·자동 신고 메뉴 0개
- NFR-01~NFR-05 시험 결과를 `docs/TEST_LOG.md`에 정리 (분석서 6장 추적 매트릭스 순서)

**완료 확인**
- [ ] 분석서 6장의 모든 ID에 시험 결과가 한 줄 이상 있음
