# 백그라운드 생성 작업

> 후속 단계에서 설정 선택형 Terra HTTP 어댑터를 연결했다. 현재 외부 요청 형식·오류 처리·가짜 HTTP 검증은 [Terra HTTP 클라이언트 문서](AI-Terra-HTTP-클라이언트.md)를 따른다. 이 문서의 고정 클라이언트 실행·복구 정책은 계속 적용된다.

## API와 실행 흐름

기존 URL과 응답 필드는 유지한다. `POST /api/v1/worksheets/{id}/generations`는 이제 생성 결과를 기다리지 않고 `202`, `PENDING/QUEUED`를 반환한다. 같은 멱등키 재요청은 기존 작업의 현재 상태를 반환한다. 프론트는 응답 Location 또는 `GET .../generations/{generationId}`로 상태를 조회하고, SUCCEEDED 후 기존 `/review`를 조회한다.

1. **접수 트랜잭션**: 학습지 소유권·구독·상태·선택 영역을 확인하고 학습지를 PROCESSING, 작업을 PENDING으로 저장한다. 설정·파일 해시와 FIXED/LEGACY_MOCK 실행 경로도 접수 시 고정한다. 같은 학습지의 접수는 행 잠금으로 직렬화한다.
2. **커밋 후 배정**: AFTER_COMMIT 이벤트가 전용 실행기에 작업 ID만 전달한다. 접수가 롤백되면 이벤트도 실행하지 않는다. DB의 PENDING 행을 주기적으로 확인해 커밋 직후 배정 누락과 메모리 대기열 포화를 보완한다.
3. **시작 트랜잭션**: DB 잠금으로 PENDING을 한 번만 RUNNING/OCR로 전환한다. 중복 배정은 외부 처리를 다시 실행하지 않는다.
4. **OCR 대기**: 저장된 EXIF 보정 PNG를 고정 클라이언트에 전달한다. DB 트랜잭션은 열려 있지 않다.
5. **전사 커밋**: PNG 해시·크기·원본 파일 연결을 확인하고 불변 전사문을 저장한 뒤 AI_CORRECTION으로 전환한다.
6. **첨삭 대기와 검증**: 같은 PNG 객체와 커밋된 전사를 사용한다. DB 트랜잭션 없이 고정 응답을 받고 계약 v1 검증을 수행한다. 기존 LEGACY_MOCK도 별도 워커에서 실행하며, 기존 첨삭 형식은 유지한다.
7. **결과 트랜잭션**: 정상 카드·확인 필요·제외 후보·원본 응답·검증 결과 저장, 이전 카드 논리 삭제, SUCCEEDED/COMPLETED와 READY 전환을 함께 커밋한다.

실패하면 별도 트랜잭션으로 작업 FAILED, 학습지 FAILED, 오류 코드·재시도 가능 여부·종료 시각을 저장한다. `stage`는 실패한 OCR 또는 AI_CORRECTION을 유지한다. 임의 예외 메시지나 인증정보는 로그·오류 응답에 복사하지 않는다. 읽을 수 없는 JSON은 UNREADABLE 원본 응답으로 보존하고 INVALID_AI_RESPONSE로 실패한다.

## 전사 보존과 재시도

OCR 결과는 첨삭 요청 전에 커밋되므로 첨삭 실패가 전사를 롤백하지 않는다. 첨삭 결과 저장 실패도 카드·결과 트랜잭션만 롤백한다. 교사 편집·삭제·복원과 기존 최초 내용 보존 규칙은 유지한다.

재시도는 기존 실패 작업을 덮어쓰지 않고 새 작업을 접수한다. **최신 실패 작업**, 재시도 가능 표시, 변경되지 않은 파일·설정, 학습지 FAILED 상태를 확인한다. 동일한 재시도 멱등키는 같은 새 작업을 반환한다. 후속 단계에서 유효한 전사가 있으면 첨삭만 재실행하도록 연결했다. 현재 재사용 조건과 동시 요청 방어는 [재시도·중복 실행 방지 문서](AI-재시도-중복실행-방지.md)를 따른다.

## 서버 종료·재시작 정책

- 기본 실행기는 워커 2개와 메모리 대기열 16개를 사용한다. 메모리 대기열은 영속 작업의 사본이며 DB가 대기 작업의 기준이다.
- 정상 종료에서는 실행 스레드를 중단하고 최대 5초 동안 종료를 기다린다. 중단 신호를 받은 클라이언트는 WORKER_INTERRUPTED로 실패를 남긴다. DB 종료 등으로 기록하지 못해도 다음 시작의 복구 대상이 된다.
- 시작 시 남아 있는 RUNNING은 **SERVER_INTERRUPTED / FAILED / 재시도 가능**으로 전환한다. 기존 stage와 이미 저장된 전사를 유지한다. 실행 여부가 불확실한 외부 요청을 자동 재호출하지 않고 기존 재시도 API를 사용한다.
- PENDING은 다시 배정한다. 이전 버전의 client_mode가 없는 PENDING은 실행 경로를 추측하지 않고 중단 실패로 전환해 수동 재시도를 받는다.
- 중단 처리된 작업의 이전 워커가 늦게 돌아와도 RUNNING·stage·최신 작업 검사를 통과하지 못하므로 결과를 저장하지 않는다.
- **단일 서버 실행 기준**이다. 여러 서버가 같은 DB를 사용하는 배포에서는 다른 서버의 RUNNING과 구별할 lease/heartbeat·소유자 구분을 추가한 뒤 이 시작 복구 정책을 바꿔야 한다. 이번 단계에는 다중 서버 작업 배정을 구현하지 않았다.

## 로컬 재현

실제 AI 호출은 없다. 기존 기본 경로는 LEGACY_MOCK이며 계약 v1 예제를 확인할 때 `app.ai.fixed-response.enabled=true`를 사용한다. 별도의 로컬 DB로 구성한 서버에만 다음 속성을 적용한다.

```properties
app.ai.fixed-response.enabled=true
app.ai.fixed-response.ocr-delay-ms=1500
app.ai.fixed-response.correction-delay-ms=2000
app.ai.fixed-response.fail-at=NONE
```

`fail-at`을 OCR 또는 AI_CORRECTION으로 바꾸면 해당 단계의 실패를 재현한다. 생성 요청 직후 GET 상태로 OCR, AI_CORRECTION 전환을 확인한다. AI_CORRECTION 실패 시 전사 행은 남고 카드와 정상 결과는 생성되지 않는다. 지연·실패 설정은 고정 클라이언트에만 있으며 HTTP 요청 데이터로 받지 않는다.

워커 수·대기열 크기·재배정 간격은 각각 `app.generation.worker-count`, `app.generation.queue-capacity`, `app.generation.dispatch-interval-ms`로 조정한다. 재배정 기본 간격은 2000ms다.

관련 테스트만 실행:

```powershell
$env:JAVA_HOME = 'C:\Users\82108\.jdks\temurin-21.0.11-1'
.\gradlew.bat --offline -I scripts/local-h2-tests.init.gradle test --tests '*FixedResponseReviewApiTest'
```

H2와 임시 PNG 파일만 사용한다. 고정 클라이언트의 latch로 각 단계의 지연을 제어하여 긴 sleep 없이 커밋 후 실행, 상태 조회, 대기 중 트랜잭션 없음, OCR 성공 후 첨삭 실패의 전사 보존, 재시도·중복 요청·권한·원문/좌표 연결을 검증한다. 재시작 복구는 DB에 남은 RUNNING/PENDING을 구성한 뒤 실제 시작 복구 메서드를 실행해 검증한다. 운영 프로세스 강제 종료나 다중 서버 부하 검증은 수행하지 않는다.

## 검증 결과

- 관련 통합 테스트 13건 확인. 처음 12건이 통과했고, 중복 요청도 항상 PENDING이라고 기대한 테스트 1건을 현재 상태 반환 규칙에 맞춰 수정한 뒤 해당 사례만 재실행해 통과했다.
- 단계 연결 후 전체 검사는 한 번 실행했다. H2 기반 18개 클래스, 313건 모두 통과(실패·오류·건너뜀 0). `build`, `spotlessCheck`, `git diff --check` 통과.
- 접수 롤백, 커밋 후 배정, 대기 중 비트랜잭션, 단계 상태 조회, 단계별 실패, 첨삭 실패 시 불변 전사 보존, 재시도·권한·중복 호출, 같은 PNG와 전사/좌표 연결, 미배정 작업 복구, 중단 작업의 늦은 완료 무시를 검증했다.
- 실제 API 호출, 공유·운영 DB 적용, 프로세스 강제 종료, 다중 서버·대량 대기열 부하 검증은 생략했다. 기존 실험 코드와 이미지·계약·검증·교사 편집 구현은 보존했다.

## 다음 단계에 남긴 범위

실제 OCR·첨삭 API, 요청 타임아웃·취소와 재시도 한도, 다중 서버 lease/heartbeat, 프론트 폴링 화면은 아직 연결하지 않았다. 공유·운영 DB 변경과 데이터 이관도 수행하지 않았다. `worksheet_generations.client_mode`는 nullable 새 열이며, 운영 반영 전 별도 스키마 이관이 필요하다.
