# v1.1 서비스 AI 생성 경로 연결 — 14단계

2026-11-13 기준. TERRA 서비스의 작업 접수·전사·첨삭 HTTP 요청·버전별 검증·DB 저장·결과 조회를 연결했다. 기본 계약은 v1 그대로이며 실제 서버 설정·운영 DB·외부 AI에는 변경이나 호출을 수행하지 않았다.

## 1. 연결한 흐름

```text
POST /api/v1/worksheets/{id}/generations
  → 작업별 AI 프로필 저장, 202와 작업 ID 반환
  → 커밋 후 Dispatcher → Worker
  → 저장된 프로필 확인
  → 공통 OCR 호출 또는 확정 전사 재사용
  → 프로필에 맞는 프롬프트·Schema로 첨삭 HTTP 요청
  → 같은 계약 버전의 파서·원문/좌표/동작/정책 검증
  → 같은 계약 버전으로 원본·검증 결과·카드 저장
  → 같은 트랜잭션에서 SUCCEEDED / READY 처리
  → 프론트 상태 조회 후 GET .../review → DB에서 응답 DTO로 매핑
```

프론트의 시작·상태·결과 조회 API와 기존 교사 편집 입력 형식은 유지한다. 생성 요청 자체가 첨삭 결과를 기다려 반환하는 동기 API로 바뀌지는 않는다.

## 2. 주요 코드

| 파일 | 연결 내용 |
| --- | --- |
| [TerraGenerationClient](../../src/main/java/com/ceos24/autedi/domain/generation/client/TerraGenerationClient.java) | 작업 프로필을 받아 라우터로 프롬프트·Schema를 선택하고 프로필의 모델로 요청. 기존 4인자 호출은 v1/DEFAULT를 유지 |
| [TerraHttpConfiguration](../../src/main/java/com/ceos24/autedi/global/config/TerraHttpConfiguration.java) | HTTP 클라이언트에 계약 라우터 주입 |
| [GenerationWorker](../../src/main/java/com/ceos24/autedi/domain/generation/service/GenerationWorker.java) | 현재 서버 설정을 다시 읽지 않고 claim된 프로필로 첨삭·파싱·검증 실행 |
| [GenerationWorkStore](../../src/main/java/com/ceos24/autedi/domain/generation/service/GenerationWorkStore.java) | V1Validation/V11Validation에 맞는 저장 진입점 선택, 저장 성공과 작업 완료를 함께 커밋 |
| [GenerationContractSelector](../../src/main/java/com/ceos24/autedi/domain/generation/contract/GenerationContractSelector.java) | 알려진 TERRA v1.1/DEFAULT·COMPACT 프로필의 서비스 실행 허용. 알 수 없는 프로필·미지원 공급자 조합은 계속 거절 |

전사 지시문·전사 Schema는 공통으로 유지한다. 최초 생성은 일반적으로 전사 1회와 첨삭 1회다. 유효한 전사를 재사용하는 재시도는 첨삭만 호출한다. HTTP 대기에는 DB 트랜잭션이 없으며 자동 유료 재시도·계약 간 자동 대체는 추가하지 않았다.

## 3. 요청 형식과 버전 보존

- 신규 작업은 서버 설정으로 프로필을 한 번 선택해 저장한다. 실행·동일 키 재전송·재시도에는 저장된 프로필을 사용한다.
- v1 요청은 기존 `autedi_correction_v1`, v1.1 요청은 `autedi_correction_v1_1` Schema 이름을 사용한다. 기본본과 압축본은 같은 v1.1 응답 Schema를 사용하며 프롬프트 버전만 구분한다.
- 전송 Schema는 해당 버전의 원본 Schema를 읽은 사본에서 `$schema`, `$id`, `issue_kinds.uniqueItems`만 제거한다. 원본 Schema·엄격한 서버 파서·검증기를 약화하지 않는다. Structured Outputs의 부분집합 사용과 strict 형식은 [공식 OpenAI documentation](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses)을 참고했다.
- 첨삭 요청에는 같은 보정 PNG, 확정 전사 전체와 줄 번호, 접수 당시 선택 영역·레벨을 사용한다.
- 로그의 `operation`은 위 Schema 이름이며 `requested_model`은 실제 요청 모델이다. 원문·이미지·키·외부 오류 본문을 로그에 추가하지 않았다.
- v1.1이 v1 형태의 응답을 받으면 v1 파서로 대체하지 않는다. 해당 버전의 UNREADABLE 아카이브에 원본을 보존하고 `INVALID_AI_RESPONSE`로 실패한다. 실패 메시지에도 해당 계약 버전을 표시한다.
- 기존 HTTP 오류·미완료·거부·타임아웃 분류는 유지한다. 이런 HTTP 실패와 계약 파싱 실패를 정상 카드로 저장하지 않는다.

## 4. 선택 설정

기존 기본값은 다음과 같다.

```properties
app.ai.correction.contract-version=v1
app.ai.correction.prompt-variant=DEFAULT
```

스키마 선행 조건을 맞춘 환경에서 v1.1을 사용하려면 서버 설정을 아래처럼 선택한다.

```properties
app.ai.client=TERRA
app.ai.correction.contract-version=v1.1
app.ai.correction.prompt-variant=DEFAULT
```

계약과 프롬프트 후보는 기존 환경변수 `AI_CORRECTION_CONTRACT_VERSION=v1.1`, `AI_CORRECTION_PROMPT_VARIANT=DEFAULT`로도 선택한다. 압축본은 `COMPACT`다. API 키 주입과 HTTP timeout/endpoint 설정은 [기존 HTTP 클라이언트 문서](AI-Terra-HTTP-클라이언트.md)를 따른다. 앱은 `.env`를 직접 읽지 않는다.

FIXED/LEGACY_MOCK는 계속 v1만 지원한다. v1.1은 TERRA로 선택해야 하며 미지원 조합은 생성 접수 전에 `GENERATION_008`로 거절한다. 알려지지 않은 프로필이 저장된 PENDING 작업도 OCR 전에 실패한다.

설정 변경은 이후 신규 작업에 적용된다. 이미 접수한 작업과 실패 작업의 재시도는 이전 프로필을 유지한다. 기본 설정을 v1으로 되돌려도 이미 저장된 v1.1 결과는 버전별 조회 코드로 읽는다. v1 전용 구버전 앱으로의 롤백과는 구분한다.

## 5. 기존 교사 편집과 배포 범위

현재 프론트의 content/regions/isApplied 편집을 유지한다. 실제 content가 바뀌면 현재 edit만 null이 되고 aiInitialEdit는 보존된다. 이 기본 호환은 기존 구현과 테스트로 확보했다. 교사가 content와 새로운 edit를 함께 보내는 13단계 확장 입력은 선택적 후속 기능으로 남겼으며 이번 연결에 필수로 요구하지 않는다.

이번 구현에서 새 DB 컬럼·SQL은 추가하지 않았다. 실제 배포에는 앞선 request_ai_profile, v11_details와 CHECK 교체가 필요하므로 [저장·복원 문서의 선행 조건](AI-공통-응답-계약-v1.1-저장-복원.md)을 확인한다. 운영 DB의 적용 상태를 확인하거나 스키마를 실행하지 않았다.

현재 계약 선택 기본값과 서버 운영 설정도 변경하지 않았다. 실제 API 키·계정 권한·외부 모델 품질·지연·비용과 MySQL DDL 동작, 실제 프론트 렌더러는 이번 로컬 테스트로 검증한 범위가 아니다.

## 6. 검증

외부 AI 대신 loopback Responses API 대역, 실제 MVC·보안·Dispatcher·Worker, 메모리 H2를 사용했다.

- 허용되는 v1.1 고정 예제 26개를 서비스 생성 경로로 실행하고 원본·메타데이터·카드·확인 목록·제외·집계·목록·상세 응답을 확인했다. 삽입·삭제·다중 줄·GUIDANCE·이미지 확인·위치 실패·부분 좌표 경고·빈 카드·정책 제외를 포함한다.
- DEFAULT/COMPACT의 정확한 지시문·입력·전송 Schema·모델, 동일 PNG·확정 전사 전달을 검사했다.
- COMPACT 작업의 동일 키 재전송이 새 작업·외부 호출을 만들거나 프로필을 다시 선택하지 않는지 검사했다.
- HTTP 503 및 잘못된 계약 응답에서 카드가 생성되지 않는지, 수동 재시도가 기존 공급자·COMPACT 프로필·전사를 유지하고 첨삭 요청 한 번만 추가하는지 검사했다.
- 미지원 공급자·알 수 없는 저장 프로필의 호출 전 거절과 기존 v1·교사 편집·동시 실행·저장 복원 회귀 검증을 유지했다.

```powershell
.\gradlew.bat --offline spotlessJavaApply test --tests '*FixedResponseReviewApiTest' --tests '*GenerationResultStorageTest' --tests '*domain.generation.contract.v1.*' --tests '*domain.generation.contract.v1_1.*' --tests '*GenerationContractRoutingTest' --tests '*TerraGenerationClientTest' --tests '*TerraV11ComparisonTest' spotlessJavaCheck
```

Java 21에서 총 1,005개 통과(실패·오류·건너뜀 0). 서비스 MVC 70개, HTTP 클라이언트 23개, 라우팅 20개, 저장 복원 43개, 별도 비교 실행기 38개와 두 버전의 계약 테스트를 포함한다. Java 포맷 검사도 통과했다. 실제 유료 API는 호출하지 않았다.
