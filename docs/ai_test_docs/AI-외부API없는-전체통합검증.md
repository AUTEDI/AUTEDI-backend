# 외부 API 없는 전체 흐름 통합 검증

## 검증 범위

내부 백엔드 API는 실제 MVC·JWT 보안·서비스·JPA·커밋된 H2를 통과한다. 전사·첨삭은 고정 클라이언트를 사용하며, 새 전체 흐름 테스트에서는 Terra HTTP 클라이언트와 기존 AI 클라이언트가 호출되지 않았음을 검사한다. 다른 HTTP 회귀 테스트도 가짜 키와 loopback 서버만 사용한다. 실제 외부 API·유료 실험 main·공유/운영 DB는 사용하지 않는다.

새 시나리오는 `FixedResponseReviewApiTest.fullOfflineFlowPreservesRotatedImageCoordinatesAndAiSourceThroughFailureRetryAndTeacherEdits`다. 기존 단계별 테스트를 복제하지 않고 EXIF 이미지부터 편집까지 이어지는 연결 검증 한 건을 추가했다.

## 연결한 시나리오

1. EXIF orientation=6인 JPEG 한 장(원본 2×3)을 업로드한다. 원본 조회가 업로드 바이트와 같고, 화면용 PNG는 회전이 보정된 3×2인지 확인한다.
2. 문법 Standard·내용 Advanced 설정을 API로 저장한다.
3. 생성 API의 202/PENDING 접수 후 별도 워커에서 실행한다. OCR과 AI_CORRECTION을 latch로 잠깐 멈춰 상태 조회가 각 단계를 반환하는지 검사한다.
4. 첨삭 시작 전에 불변 전사가 DB에 커밋되는지, 전사의 원본 해시·PNG 해시·크기가 실제 업로드/조회와 같은지 확인한다.
5. 고정 AI_CORRECTION 실패를 발생시킨다. 작업과 학습지가 FAILED이고 전사는 남으며 카드·정상 결과는 저장되지 않는지 확인한다. READY 이전의 검토 조회는 409다.
6. 새 멱등키로 수동 재시도한다. 전사 클라이언트 총 호출은 1회, 첨삭은 실패·성공 총 2회다. 기존 전사 ID를 재사용한 채 SUCCEEDED/COMPLETED, 학습지 READY로 전환한다.
7. 저장 후 검토 API에서 정상 카드 4개, reviewIssues 3개, 제외 1개와 경고를 조회한다.
8. REPLACE와 GUIDANCE 각각을 교사가 API로 수정하고 다시 조회한다. 현재 content는 바뀌지만 aiInitialContent·원문·설명·anchor·좌표·경고는 유지되는지 확인한다. 최초 원본 응답·검증 결과·전사도 변하지 않는다.
9. 편집 뒤에도 원본 이미지와 화면 PNG가 처음 조회한 바이트 그대로인지 확인한다.

## 좌표·원문 연결

계약은 보정 PNG 전체를 기준으로 한 0~1 정규화 좌표다. 페이지 width/height만 px다. 서버는 모델의 `[left, top, right, bottom]`을 API의 `x, y, width, height`로 저장한다. 보정 PNG 좌표에 원본 EXIF 회전을 다시 적용하지 않는다.

| 검증 대상 | 기대값 |
|---|---|
| 모델 boxes | `[0.4, 0.1, 0.5, 0.2]` |
| API regions의 XYWH | `[0.4, 0.1, 0.1, 0.1]` |
| 보정 후 기준 이미지 | 가로 3px, 세로 2px |
| 같은 영역의 픽셀 XYWH | `[1.2, 0.2, 0.3, 0.2]` |
| 전사 첫 줄의 두 번째 `go` | occurrence=2, UTF-16 start=11, end=13(exclusive) |

위 픽셀 환산은 API 좌표와 페이지 메타데이터의 계약 검증이며, 실제 프론트 렌더링을 실행한 결과는 아니다. 소수 좌표를 반올림하거나 원본 2×3 크기로 환산하지 않는다. 거꾸로 된 잘못된 box는 정상 regions에서 제외하고 INVALID_BOX 경고와 requiresReview를 보존하는지도 확인한다.

## 기존 회귀 검증

전체 검사에는 다음 기존 사례도 포함한다.

- JPG/JPEG/PNG 원본 보존·EXIF 회전·페이지 크기, 잘못된 이미지/PDF 거부, 파일 교체·롤백
- 접수 커밋 후 실행, 외부 대기 중 비트랜잭션, 서버 재시작의 대기/중단 작업 복구
- 학원/학습지 소속·최신 실패·재시도 가능·파일/설정 검사
- 같은 키 재전송, 동시 생성/재시도, 전역 유일키 경합, 오래된 작업의 늦은 성공/실패
- 정상 카드·확인 필요·제외·경고, 전사 재사용과 손상 시 OCR 재실행
- 교사 편집·삭제·복원·ETag 충돌, 최초 AI 내용과 감사 기록 보존
- 가짜 HTTP를 통한 인증 오류·할당량·타임아웃·미완료·거부·출력 오류

## 직접 실행

`autedi` 폴더에서 실행한다. init script는 테스트 JVM에만 test 프로필과 메모리 H2를 강제한다.

```powershell
$env:JAVA_HOME = 'C:\Users\82108\.jdks\temurin-21.0.11-1'
.\gradlew.bat --offline -I scripts/local-h2-tests.init.gradle test --tests '*FixedResponseReviewApiTest.fullOfflineFlowPreservesRotatedImageCoordinatesAndAiSourceThroughFailureRetryAndTeacherEdits'
```

전체 회귀 검사는 다음과 같다.

```powershell
.\gradlew.bat --offline -I scripts/local-h2-tests.init.gradle build spotlessCheck --continue
```

결과는 `build/reports/tests/test/index.html`에서 확인한다. 이미 통과한 검사는 코드 변경이나 새로운 우려가 없으면 재실행할 필요가 없다.

## 실행 결과

- 보강한 전체 흐름 시나리오 한 건을 먼저 실행해 통과했다. 운영 코드는 수정하지 않았다.
- 연결 확인 후 전체 검사를 한 번 실행했다. 19개 클래스, **348건 통과**, 실패·오류·건너뜀 0건. 이 중 생성·검토 MVC/H2 테스트는 30건이다.
- `build`, `spotlessCheck`, `git diff --check` 통과. 실패한 검사는 없으며 반복 재실행하지 않았다.
- 전사 1회·첨삭 2회(실패와 수동 재시도)만 호출됐으며 새 시나리오에서 Terra HTTP 및 기존 AI 클라이언트 호출은 0회임을 확인했다.
- 작업 시작 시 기존 파일 233개의 해시를 기록해 대조했다. 통합 테스트 파일 하나만 변경하고 이 문서를 추가했으며, 기존 파일 누락은 없다. 공유·운영 DB 적용·커밋·push·PR·병합은 수행하지 않았다.

## 생략한 범위

실제 모델 응답 품질·API 인증/요금, 실제 MySQL 잠금 경합·다중 서버 부하·프로세스 강제 종료, 프론트 화면의 확대/축소·픽셀 반올림·캔버스 렌더링은 검증하지 않았다. 이번 작업은 통합 테스트 보완과 검증에 한정하며 다음 기능 구현은 시작하지 않는다.
