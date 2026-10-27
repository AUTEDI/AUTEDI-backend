# AUTEDI API 명세서

- 작성일: 2026-10-27
- 문서 버전: v0.1 — 구현 전 협의용 초안
- 기준 문서: `AUTEDI AI 첨삭 서비스 기능명세서(최종).md`
- 범위: F02 학습지 업로드, F03 첨삭 설정, F04 AI 생성, F05 검토·편집, F06 교사 코멘트
- 이 문서는 API 설계안이며 현재 서버에 구현되어 있다는 의미는 아니다.

## 1. 범위와 설계 전제

### 1.1 기능명세에서 확정된 사항

- JPG/JPEG/PNG/PDF를 지원하고 원본 파일은 변경하지 않는다.
- 네 첨삭 영역 중 최소 한 개를 선택하고 영역마다 수준을 지정한다.
- 수준은 학생 실력 등급이 아닌 AI 개입 강도이다.
- 오류 수정·개선 제안·수정 이유는 자동 생성한다. 피드백 방식 선택 API는 없다.
- AI 수정 이유는 교사용이며 최종 출력 주석에는 포함하지 않는다.
- AI 첨삭 수정·삭제·삭제 취소와 교사 코멘트 작성·수정·삭제를 지원한다.
- 교사 코멘트는 하이라이트 또는 밑줄에 연결되며 자동 배치한다. 수동 이동·선택 영역 크기 조정은 제공하지 않는다.

### 1.2 이 문서에서 제안하는 구현 정책

다음은 기능명세에 없는 세부 설계 결정이며 프론트·백엔드 협의 후 확정한다.

| 항목 | 제안 |
| --- | --- |
| 업로드 단위 | 학습지 한 건에 파일 한 개. PDF는 여러 페이지를 표현할 수 있는 데이터 구조 사용 |
| 파일 수·페이지 수 | 파일 묶음 업로드는 이 초안에서 제외. 허용 페이지 수는 미정이며 1페이지로 확정한 것은 아님 |
| 파일 교체 | 첨삭 시작 전 또는 생성 실패 상태에서만 가능. 검토 결과가 있는 학습지는 새 학습지로 등록 |
| 설정 수정 | 생성 전·실패 상태에서만 허용. 생성 중·완료 상태에서는 잠금 |
| 생성 처리 | 비동기 작업 + 상태 폴링. OCR·LLM은 서버 내부 단계 |
| 완료 후 재생성 | 이 초안에서 제외. 기존 편집 결과 덮어쓰기 정책이 없기 때문 |
| 저장 | 수정 API 성공 시 즉시 영속 저장. 별도 전체 저장·승인 API 없음 |
| 동시 편집 | 자원별 ETag/If-Match로 오래된 수정 요청을 차단 |
| 코멘트 배치 | 프론트가 원문 영역과 코멘트 목록으로 자동 배치. 서버는 의미 데이터만 저장 |
| 인증 | 인증된 교사만 자신의 학습지에 접근. 인증 수단·로그인 API는 별도 확정 |

로그인·회원가입, 학생 관리, 홈 기본 설정, 최근 작업 목록, 다운로드·인쇄 API는 기준 기능명세에 독립 기능으로 정의되지 않아 이 문서의 엔드포인트에서 제외한다. 출력 기능 추가 시 수정 이유 제외 원칙은 반드시 적용한다.

## 2. 공통 규약

### 2.1 기본 규칙

- Base path: `/api/v1`
- JSON 요청·응답: `application/json; charset=utf-8`
- 파일 등록·교체: `multipart/form-data`
- 모든 ID는 서버가 발급하는 불투명 문자열이다. 예시 ID 형식에 클라이언트가 의존하지 않는다.
- 일시는 ISO 8601 UTC 문자열로 반환한다. 화면의 한국 시간 변환은 프론트에서 수행한다.
- 모든 엔드포인트와 파일 접근에서 소유권을 확인한다. 타인 소유 또는 존재하지 않는 자원은 동일하게 `404`를 반환한다.
- 인증 누락·만료는 `401`. JWT·세션 방식은 미정이며 특정 인증 헤더를 본문 계약으로 고정하지 않는다.
- 요청의 허용되지 않은 필드는 `400 VALIDATION_ERROR`로 거부한다. 예: 피드백 방식, 클라이언트가 지정한 소유자 ID.
- 성공 응답은 `{ "data": ... }`. 본문이 없는 `204` 응답에는 래퍼를 넣지 않는다.
- 요청마다 `X-Request-Id`를 반환하고 오류 본문에도 같은 값을 포함한다.

### 2.2 오류 형식

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "첨삭 영역을 최소 한 개 선택해주세요.",
    "details": [
      { "field": "areas", "reason": "최소 1개 항목이 필요합니다." }
    ],
    "requestId": "req_001"
  }
}
```

외부 OCR·LLM의 원시 오류, 비밀키, 내부 스택 추적은 클라이언트에 노출하지 않는다.

| HTTP | 코드 | 의미 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 필수 필드·enum·좌표·빈 문자열 검증 실패 |
| 401 | UNAUTHORIZED | 인증 필요 또는 만료 |
| 404 | RESOURCE_NOT_FOUND | 자원 없음 또는 접근할 수 없음 |
| 409 | INVALID_STATE | 현재 상태에서 허용되지 않는 작업 |
| 409 | GENERATION_IN_PROGRESS | 이미 생성 작업 실행 중 |
| 409 | ALREADY_GENERATED | 완료한 학습지에 다시 생성 요청 |
| 409 | IDEMPOTENCY_KEY_REUSED | 같은 멱등 키를 다른 요청에 사용 |
| 412 | PRECONDITION_FAILED | ETag 불일치. 최신 데이터 조회 후 재시도 필요 |
| 413 | FILE_TOO_LARGE | 업로드 용량 제한 초과 |
| 415 | UNSUPPORTED_FILE_TYPE | 지원하지 않는 파일 형식 |
| 422 | INVALID_FILE | 손상되거나 해석할 수 없는 파일, 암호화된 PDF |
| 422 | PAGE_LIMIT_EXCEEDED | 허용 페이지 수 초과 |
| 428 | PRECONDITION_REQUIRED | 필수 If-Match 헤더 누락 |
| 429 | RATE_LIMITED | 요청 제한. 가능하면 Retry-After 헤더 제공 |
| 500 | INTERNAL_ERROR | 서버 처리 오류 |
| 503 | SERVICE_UNAVAILABLE | 저장소·작업 접수 등 일시적 장애 |

### 2.3 동시 수정 방지

- 학습지 상세·설정·첨삭 단건·코멘트 단건 조회는 해당 자원의 `ETag`를 반환한다.
- 변경 API는 해당 자원의 최신 ETag를 `If-Match`로 받는다. 예: `If-Match: "cor_001-v2"`.
- 자원 변경 검증과 쓰기는 원자적으로 수행한다. 충돌 시 마지막 쓰기로 덮어쓰지 않고 `412` 반환.
- JSON에서 자원의 `etag`도 제공한다. 목록·통합 조회에서는 개별 항목의 `etag`를 사용한다.
- 성공한 변경 응답은 새로운 ETag를 반환한다. `204` 삭제 응답도 새 ETag 헤더를 반환한다.
- ETag는 불투명 값이다. 클라이언트는 직접 계산하지 않는다.

### 2.4 생성 요청 멱등성

- 생성·재시도 요청은 `Idempotency-Key` 헤더가 필수이다. 누락 시 `400`.
- 같은 사용자·메서드·경로·키에 동일 요청을 보내면 최초의 `202` 응답과 같은 작업 ID를 반환한다.
- 같은 범위의 키를 다른 본문에 사용하면 `409 IDEMPOTENCY_KEY_REUSED`.
- 멱등 응답은 최소 24시간 보관하는 안을 제안한다. 이 기간 이후에도 학습지 상태 제약으로 중복 실행을 방지한다.
- 멱등 키 확인은 상태·If-Match 재검사보다 먼저 수행한다. 최초 접수 후 응답 유실로 같은 요청을 재전송해도 기존 작업을 찾을 수 있어야 한다.
- 다른 키로 동시 요청해도 학습지별 실행 중 작업은 하나만 허용한다. DB 제약 또는 원자적 상태 변경으로 보장한다.

## 3. 공통 데이터 모델

### 3.1 첨삭 설정

| 영역 코드 | 의미 |
| --- | --- |
| GRAMMAR_MECHANICS | 문법 및 철자 |
| VOCABULARY_EXPRESSION | 어휘 및 표현 |
| SENTENCE_STRUCTURE | 문장 구성 |
| CONTENT_SUMMARY | 내용 및 요약 |

수준: `STANDARD`, `ADVANCED`.

```json
{
  "areas": [
    { "area": "GRAMMAR_MECHANICS", "level": "STANDARD" },
    { "area": "VOCABULARY_EXPRESSION", "level": "ADVANCED" }
  ]
}
```

- 선택한 영역만 전송한다. 배열에 없는 영역은 미선택이다.
- 1~4개 항목, 영역 중복 불가, 각 항목의 area·level 필수.
- 업로드 직후 아직 설정하지 않은 상태만 `areas: []`로 조회 가능하다. 설정 저장·생성에는 빈 배열을 허용하지 않는다.
- 서버가 임의의 기본 설정을 적용하지 않는다. 기본값 정책은 별도 확정한다.

### 3.2 좌표와 페이지

- `pageId`가 식별하는 미리보기 이미지가 좌표 기준이다.
- 원점은 좌상단, x는 오른쪽, y는 아래쪽이며 좌표는 모두 `0~1` 정규화 값이다.
- EXIF 회전·PDF 렌더링 등은 서버에서 미리보기 생성 시 확정한다. OCR도 같은 기준 이미지를 사용한다.
- 원본 파일은 그대로 보존하며 미리보기는 파생 파일이다. 프론트가 이미지를 별도 회전하면 오버레이에도 같은 변환을 적용해야 한다.
- 영역은 사각형 네 꼭짓점을 시계방향으로 표현한다. 꼭짓점 중복·자기 교차·면적 0은 허용하지 않는다.
- 여러 줄·여러 페이지에 걸친 첨삭은 regions 항목을 여러 개 사용한다.
- 단어 삽입 제안도 근거가 되는 인접 원문 영역에 연결한다. 원문 위치가 없는 임의 좌표를 생성하지 않는다.

```json
{
  "pageId": "page_001",
  "polygon": [
    { "x": 0.20, "y": 0.30 },
    { "x": 0.26, "y": 0.30 },
    { "x": 0.26, "y": 0.33 },
    { "x": 0.20, "y": 0.33 }
  ]
}
```

프론트 표시 좌표는 `x × 화면에 표시된 이미지 너비`, `y × 화면에 표시된 이미지 높이`에 이미지의 화면상 이동량을 더한다. 확대·축소와 패널 양방향 선택에는 추가 API 호출이 필요하지 않다.

### 3.3 학습지(Worksheet)

```json
{
  "id": "ws_001",
  "etag": "\"ws_001-v1\"",
  "status": "DRAFT",
  "file": {
    "name": "homework.jpg",
    "mediaType": "image/jpeg",
    "sizeBytes": 245123,
    "originalUrl": "https://files.example.test/original-signed-url",
    "urlExpiresAt": "2026-10-27T10:15:00Z"
  },
  "pages": [
    {
      "id": "page_001",
      "pageNumber": 1,
      "width": 1000,
      "height": 1400,
      "imageUrl": "https://files.example.test/preview-signed-url",
      "urlExpiresAt": "2026-10-27T10:15:00Z"
    }
  ],
  "settings": { "areas": [] },
  "latestGenerationId": null,
  "createdAt": "2026-10-27T10:00:00Z",
  "updatedAt": "2026-10-27T10:00:00Z"
}
```

- 파일 URL은 예시이며 실제로는 소유권 확인 후 발급한 제한 시간 서명 URL 또는 인증된 파일 조회 주소를 사용한다.
- URL 만료 시 학습지 상세를 다시 조회한다. URL 갱신만으로 학습지의 의미 데이터 ETag를 변경하지 않는다.
- 이미지 파일은 한 페이지, PDF는 렌더링된 페이지 목록을 반환한다. pageNumber는 1부터 시작한다.
- 상태: `DRAFT`, `PROCESSING`, `READY`, `FAILED`.

### 3.4 생성 작업(Generation)

```json
{
  "id": "gen_001",
  "worksheetId": "ws_001",
  "status": "RUNNING",
  "stage": "OCR",
  "attempt": 1,
  "retryOfGenerationId": null,
  "settingsSnapshot": {
    "areas": [{ "area": "GRAMMAR_MECHANICS", "level": "STANDARD" }]
  },
  "createdAt": "2026-10-27T10:01:00Z",
  "startedAt": "2026-10-27T10:01:01Z",
  "finishedAt": null,
  "error": null
}
```

- status: `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`.
- stage: `WAITING`, `OCR`, `CORRECTION`, `VALIDATION`, `DONE`. 실패 시 실패한 단계를 유지한다.
- 성공 시 finishedAt 설정, stage는 DONE. 실패 시 finishedAt과 error 설정.
- 실패 error: `{ "code": "OCR_FAILED", "message": "원문 인식에 실패했습니다.", "retryable": true }`.
- 실패 코드: `OCR_FAILED`, `NO_TEXT_DETECTED`, `LLM_FAILED`, `INVALID_AI_OUTPUT`, `PROCESSING_TIMEOUT`, `WORKER_INTERRUPTED`.
- retryable은 서버 판단값이다. 입력 변경이 필요한 실패는 false로 반환한다.
- 진행률 수치를 임의로 추정하지 않고 stage를 표시한다. 프론트 폴링 간격은 초기 2초를 제안한다.

### 3.5 AI 첨삭(Correction)

```json
{
  "id": "cor_001",
  "etag": "\"cor_001-v1\"",
  "generationId": "gen_001",
  "source": "AI",
  "area": "GRAMMAR_MECHANICS",
  "level": "STANDARD",
  "type": "CORRECTION",
  "originalText": "go",
  "content": "goes",
  "explanation": "주어가 He이므로 goes를 사용합니다.",
  "wordIds": ["word_002"],
  "regions": [
    {
      "pageId": "page_001",
      "polygon": [
        { "x": 0.20, "y": 0.30 },
        { "x": 0.26, "y": 0.30 },
        { "x": 0.26, "y": 0.33 },
        { "x": 0.20, "y": 0.33 }
      ]
    }
  ],
  "status": "ACTIVE",
  "editedByTeacher": false,
  "explanationStale": false,
  "createdAt": "2026-10-27T10:01:20Z",
  "updatedAt": "2026-10-27T10:01:20Z"
}
```

- type: `CORRECTION`(오류 수정), `SUGGESTION`(개선 제안). 설명은 별도 유형이 아닌 각 항목의 explanation이다.
- content는 실제 수정문 또는 제안 내용이다. 하나의 필드로 통일한다.
- status: `ACTIVE`, `DELETED`. 삭제는 복구 가능한 논리 삭제이다.
- wordIds는 서버가 OCR 원문과 연결한 ID이며 클라이언트 수정 불가. 영역은 반드시 같은 학습지 페이지를 참조한다.
- 교사 수정 후에도 AI explanation은 보존하되 explanationStale을 true로 표시한다. 패널은 'AI 최초 제안 기준 설명'임을 안내한다. 자동 재생성은 이 범위에 없다.
- 최종 출력에는 ACTIVE 항목의 최신 content만 반영하고 explanation은 제외한다.

### 3.6 교사 코멘트(Comment)

```json
{
  "id": "com_001",
  "etag": "\"com_001-v1\"",
  "source": "TEACHER",
  "pageId": "page_001",
  "regions": [
    {
      "pageId": "page_001",
      "polygon": [
        { "x": 0.10, "y": 0.20 },
        { "x": 0.40, "y": 0.20 },
        { "x": 0.40, "y": 0.25 },
        { "x": 0.10, "y": 0.25 }
      ]
    }
  ],
  "highlightType": "HIGHLIGHT",
  "content": "주인공이 왜 그렇게 생각했는지 설명해보세요.",
  "createdAt": "2026-10-27T10:03:00Z",
  "updatedAt": "2026-10-27T10:03:00Z"
}
```

- highlightType: `HIGHLIGHT`, `UNDERLINE`. 둘 중 하나만 허용.
- 한 코멘트의 regions는 모두 같은 pageId를 참조한다. 최소 한 개 필요.
- 선택은 이미지 영역 기반이므로 OCR 단어 ID가 없어도 코멘트 작성 가능.
- 코멘트 상자 위치·연결선·색상은 프론트 표현 데이터이며 요청에 포함하지 않는다.
- 정렬은 createdAt 오름차순, 동률이면 id 순서. 프론트는 조회·생성·편집·삭제 후 자동 배치를 다시 계산한다.

## 4. API 목록

모든 경로 앞에 `/api/v1`을 붙인다. path의 ID는 필수이다.

| 기능 | Method | Path | 성공 | 용도 |
| --- | --- | --- | --- | --- |
| F02 | POST | /worksheets | 201 | 파일 등록·미리보기 생성 |
| F02 | GET | /worksheets/{worksheetId} | 200 | 원본·페이지·상태 조회 |
| F02 | PUT | /worksheets/{worksheetId}/file | 200 | 파일 교체 |
| F03 | GET | /worksheets/{worksheetId}/settings | 200 | 설정 조회 |
| F03 | PUT | /worksheets/{worksheetId}/settings | 200 | 설정 전체 교체 |
| F04 | POST | /worksheets/{worksheetId}/generations | 202 | 생성 접수 |
| F04 | GET | /worksheets/{worksheetId}/generations/{generationId} | 200 | 진행·실패 상태 조회 |
| F04 | POST | /worksheets/{worksheetId}/generations/{generationId}/retry | 202 | 실패 작업 재시도 |
| F05·F06 | GET | /worksheets/{worksheetId}/review | 200 | 검토 화면 통합 조회 |
| F05 | GET | /worksheets/{worksheetId}/corrections | 200 | 첨삭 목록·삭제 항목 조회 |
| F05 | GET | /worksheets/{worksheetId}/corrections/{correctionId} | 200 | 첨삭 단건·ETag 조회 |
| F05 | PATCH | /worksheets/{worksheetId}/corrections/{correctionId} | 200 | 수정문·제안문 편집 |
| F05 | DELETE | /worksheets/{worksheetId}/corrections/{correctionId} | 204 | 첨삭 논리 삭제 |
| F05 | POST | /worksheets/{worksheetId}/corrections/{correctionId}/restore | 200 | 삭제 취소 |
| F06 | GET | /worksheets/{worksheetId}/comments | 200 | 코멘트 목록 조회 |
| F06 | GET | /worksheets/{worksheetId}/comments/{commentId} | 200 | 코멘트 단건·ETag 조회 |
| F06 | POST | /worksheets/{worksheetId}/comments | 201 | 코멘트 생성 |
| F06 | PATCH | /worksheets/{worksheetId}/comments/{commentId} | 200 | 코멘트 내용 수정 |
| F06 | DELETE | /worksheets/{worksheetId}/comments/{commentId} | 204 | 코멘트·강조 표시 삭제 |

## 5. F02 — 업로드와 원본 확인

### 5.1 학습지 등록

`POST /api/v1/worksheets`

요청: multipart/form-data의 `file` 필드에 파일 한 개. 추가 파일 필드·빈 파일은 400.

- 허용 MIME: image/jpeg, image/png, application/pdf.
- 확장자만 믿지 않고 파일 시그니처와 디코딩·PDF 파싱 결과를 검증한다.
- 최대 바이트·페이지 수는 정책 확정 후 서버 제한값으로 적용한다.
- 이 초안은 검증과 미리보기 준비를 끝낸 뒤 201을 반환한다. OCR은 아직 실행하지 않는다.
- 일부 페이지만 준비된 학습지를 성공으로 반환하지 않는다. 실패한 임시 파일은 정리한다.

응답: `201 Created`, `Location: /api/v1/worksheets/ws_001`, `ETag` 헤더, `data`에 3.3의 Worksheet.

주요 오류: 400, 413, 415, 422, 503.

### 5.2 학습지 상세

`GET /api/v1/worksheets/{worksheetId}`

응답: `200`, `ETag` 헤더, `data`에 Worksheet. 생성 중·실패 상태에서도 원본과 설정을 조회할 수 있다.

### 5.3 파일 교체

`PUT /api/v1/worksheets/{worksheetId}/file`

- 헤더: 학습지의 `If-Match` 필수.
- 요청: 등록과 같은 multipart `file`.
- 허용 상태: DRAFT 또는 FAILED. PROCESSING·READY는 409.
- 새 파일 검증·미리보기 생성이 모두 성공한 경우에만 교체한다. 실패하면 기존 파일·설정·상태 유지.
- 성공 시 새 pageId 발급, 기존 OCR 캐시 무효화, latestGenerationId=null, 상태 DRAFT로 변경. 첨삭 설정은 유지.
- 과거 실패 작업 기록은 유지하되 더 이상 그 작업을 재시도할 수 없다.
- 응답: `200`, 새 ETag 및 Worksheet.
- UI에서 파일을 제거하고 다른 파일을 고르는 동작은 업로드 전 로컬 상태로 처리할 수 있다. 별도 파일 제거 API는 두지 않는다.

## 6. F03 — 첨삭 설정

### 6.1 설정 조회

`GET /api/v1/worksheets/{worksheetId}/settings`

응답: `200`, 설정 자원의 ETag 헤더.

```json
{
  "data": {
    "etag": "\"settings_001-v1\"",
    "areas": []
  }
}
```

### 6.2 설정 저장

`PUT /api/v1/worksheets/{worksheetId}/settings`

- 헤더: 설정 자원의 If-Match 필수.
- 요청: 3.1의 설정 객체. 선택 목록 전체를 교체한다.
- 허용 상태: DRAFT·FAILED. 생성과 설정 저장이 동시에 오면 트랜잭션으로 순서를 결정하며, 생성 접수 후의 변경은 409.
- 응답: `200`, 새 설정 ETag 및 `{ "data": { "etag": "...", "areas": [...] } }`.
- 설정 저장 시 학습지 ETag도 변경한다. 생성 전에 학습지 상세를 다시 조회한다.
- 오류: 빈 선택·영역 중복·잘못된 enum은 400, 잠긴 상태는 409, 충돌은 412.
- 실패 후 설정 변경 시 과거 실패 작업의 스냅샷은 바꾸지 않는다. 변경한 설정으로 실행하려면 새 생성 요청을 보낸다.

## 7. F04 — AI 첨삭 생성

### 7.1 생성 접수

`POST /api/v1/worksheets/{worksheetId}/generations`

- 헤더: Idempotency-Key, 학습지 If-Match 필수.
- 요청 본문 없음. 저장된 파일·설정을 사용한다. 클라이언트가 모델명·프롬프트·원문을 직접 지정하지 않는다.
- DRAFT·FAILED에서만 접수. 설정 최소 한 영역 필요.
- 접수 시 파일 버전·설정 스냅샷을 고정하고 학습지를 PROCESSING으로 변경한다.
- 새 요청은 attempt=1, retryOfGenerationId=null.
- 응답: `202 Accepted`, `Location`에 작업 조회 경로, `data`에 QUEUED/WAITING 상태의 Generation.
- PROCESSING이면 409 GENERATION_IN_PROGRESS, READY이면 409 ALREADY_GENERATED.

### 7.2 생성 상태 조회

`GET /api/v1/worksheets/{worksheetId}/generations/{generationId}`

응답: `200`, `data`에 Generation. 작업이 실패했어도 조회 자체는 성공이므로 200이며, 실패는 status/error로 표현한다.

서버 내부 처리:

1. 기준 이미지에서 OCR 원문·단어 ID·좌표를 추출·저장한다.
2. 전체 학습지 원문과 설정으로 LLM을 호출한다. 여러 페이지면 페이지 순서를 보존한다.
3. 결과 형식, 선택 영역 준수, 원문 ID·텍스트 일치, 페이지 소속, 유효 좌표, 중복 항목을 검증한다.
4. 결과 전체를 저장하고 작업 SUCCEEDED·학습지 READY를 원자적으로 반영한다.
5. 오류가 있으면 작업 FAILED·학습지 FAILED로 변경한다. 부분 첨삭을 검토 결과로 공개하지 않는다.

오류가 없는 글은 corrections=[]여도 정상 성공이다. 읽을 텍스트가 없는 경우와 구분한다.

### 7.3 실패 작업 재시도

`POST /api/v1/worksheets/{worksheetId}/generations/{generationId}/retry`

- 헤더: Idempotency-Key, 현재 학습지 If-Match 필수. 본문 없음.
- 대상은 현재 latestGenerationId의 FAILED 작업이며 error.retryable=true여야 한다.
- 현재 파일·설정이 실패 작업의 스냅샷과 같아야 한다. 변경됐다면 409 INVALID_STATE로 응답하고 새 생성 API를 사용한다.
- 새 generationId 발급, attempt 증가, retryOfGenerationId에 직전 실패 작업 ID 저장. 과거 실패 기록은 변경하지 않는다.
- 동일 파일에 대해 검증된 OCR 결과가 있으면 재사용할 수 있다.
- 응답: `202`, Location 및 새 Generation. 접수 시 학습지는 PROCESSING.

### 7.4 상태 전이

| 현재 학습지 상태 | 동작 | 다음 상태 |
| --- | --- | --- |
| DRAFT | 유효 설정으로 생성 접수 | PROCESSING |
| PROCESSING | 모든 결과 저장 성공 | READY |
| PROCESSING | 실패·타임아웃·중단 확정 | FAILED |
| FAILED | 재시도 또는 새 생성 접수 | PROCESSING |
| FAILED | 파일 교체 성공 | DRAFT |
| READY | 첨삭·코멘트 편집 | READY |

서버 재시작·작업 유실로 PROCESSING에 영구 정체되지 않도록 작업 타임아웃 및 중단 복구를 구현한다. 구체적인 실행 시간·재시도 횟수 제한은 정책 미정이다.

## 8. F05 — 검토·편집

### 8.1 검토 화면 통합 조회

`GET /api/v1/worksheets/{worksheetId}/review`

- READY에서만 허용. 그 외는 409 INVALID_STATE.
- 응답 `data` 구조: worksheet(3.3), generation(3.4), corrections(3.5 배열), comments(3.6 배열).
- corrections는 ACTIVE만, comments는 삭제되지 않은 항목만 포함한다.
- 프론트는 correction.id로 패널과 이미지 표시의 선택 상태를 연결한다.
- source 값으로 AI 첨삭과 교사 코멘트의 스타일을 구분한다.
- 조회는 일관된 저장 상태의 스냅샷으로 제공한다. 선택·확대율 같은 일시적 UI 상태는 저장하지 않는다.

### 8.2 첨삭 목록·단건 조회

`GET /api/v1/worksheets/{worksheetId}/corrections`

| Query | 필수 | 의미 |
| --- | --- | --- |
| status | 아니오 | ACTIVE(기본), DELETED, ALL |
| pageId | 아니오 | 해당 페이지를 regions에 포함하는 항목 |
| area | 아니오 | 네 영역 코드 중 하나 |

- READY에서 허용. 잘못된 필터는 400.
- 응답: `{ "data": { "items": [...], "total": 1 } }`.
- 이 초안은 학습지 단위 전체 목록을 반환하고 페이지네이션은 하지 않는다. 실제 업로드 제한을 확정할 때 응답 크기도 검토한다.
- 정렬: 첫 대상 페이지 번호 → 첫 영역의 최소 y → 최소 x → id.

`GET /api/v1/worksheets/{worksheetId}/corrections/{correctionId}`

- 응답: `200`, ETag 헤더 및 Correction. DELETED 항목도 조회할 수 있다.
- 다른 학습지의 항목 ID를 전달하면 404.

### 8.3 수정문 편집

`PATCH /api/v1/worksheets/{worksheetId}/corrections/{correctionId}`

```json
{ "content": "goes" }
```

- 헤더: 해당 첨삭의 If-Match 필수.
- READY의 ACTIVE 항목만 허용.
- content만 수정할 수 있다. trim 후 빈 문자열 불가. 원문·좌표·영역·설명은 수정할 수 없다.
- content가 실제 변경되면 editedByTeacher=true, explanationStale=true.
- 응답: `200`, 새 ETag 및 Correction. 최대 문자열 길이는 정책 확정 필요.

### 8.4 첨삭 삭제

`DELETE /api/v1/worksheets/{worksheetId}/corrections/{correctionId}`

- 헤더: 해당 첨삭의 If-Match 필수. 본문 없음.
- READY에서 status=DELETED로 변경. 원본과 기존 편집 내용은 보존한다.
- 응답: `204`, 새 ETag. 이미 DELETED이며 최신 ETag가 맞으면 204로 처리한다.
- 프론트는 삭제 항목 ID와 응답 ETag를 보관해 즉시 되돌리기에 사용한다.

### 8.5 삭제 취소

`POST /api/v1/worksheets/{worksheetId}/corrections/{correctionId}/restore`

- 헤더: 삭제된 첨삭의 If-Match 필수. 본문 없음.
- READY에서 status=ACTIVE로 복원하고 삭제 직전 편집 내용을 유지한다.
- 응답: `200`, 새 ETag 및 Correction. 이미 ACTIVE이며 ETag가 맞으면 현재 항목을 반환한다.
- 새로고침 후 복원이 필요하면 status=DELETED 목록에서 최신 ETag를 얻는다.

별도 '유지' API는 없다. 삭제하지 않은 ACTIVE 항목이 결과에 반영된다.

## 9. F06 — 교사 코멘트

모든 코멘트 API는 READY에서 사용한다. 다른 상태에서는 409.

### 9.1 목록·단건 조회

`GET /api/v1/worksheets/{worksheetId}/comments`

- 선택 Query: pageId.
- 응답: `{ "data": { "items": [...], "total": 1 } }`.
- 정렬과 객체 형식은 3.6을 따른다. 삭제된 항목은 반환하지 않는다.

`GET /api/v1/worksheets/{worksheetId}/comments/{commentId}`

- 응답: `200`, ETag 및 Comment. 없는 항목·삭제된 항목은 404.

### 9.2 코멘트 작성

`POST /api/v1/worksheets/{worksheetId}/comments`

```json
{
  "pageId": "page_001",
  "regions": [
    {
      "pageId": "page_001",
      "polygon": [
        { "x": 0.10, "y": 0.20 },
        { "x": 0.40, "y": 0.20 },
        { "x": 0.40, "y": 0.25 },
        { "x": 0.10, "y": 0.25 }
      ]
    }
  ],
  "highlightType": "HIGHLIGHT",
  "content": "주인공이 왜 그렇게 생각했는지 설명해보세요."
}
```

- 네 필드 모두 필수. 영역은 최소 한 개, 동일 페이지, 0~1 범위의 유효 polygon.
- pageId가 해당 학습지에 없으면 404. regions와 상위 pageId가 다르면 400.
- content는 trim 후 비어 있으면 400. 내용 최대 길이는 정책 미정.
- 응답: `201`, Location에 단건 조회 경로, ETag 및 Comment.
- 프론트는 중복 클릭을 막는다. 생성 응답 유실 시 목록을 확인한 후 재입력하도록 안내한다. 생성 요청의 안전한 자동 재전송이 필요하면 코멘트에도 멱등 키 적용을 별도 확장한다.

### 9.3 코멘트 편집

`PATCH /api/v1/worksheets/{worksheetId}/comments/{commentId}`

```json
{ "content": "주인공의 생각을 뒷받침하는 이유를 한 문장 추가해보세요." }
```

- 헤더: 해당 코멘트의 If-Match 필수.
- 기능명세의 '코멘트 내용 편집'에 맞춰 content만 허용한다. 강조 방식이나 영역 변경은 삭제 후 새로 작성한다.
- 응답: `200`, 새 ETag 및 Comment.
- 프론트는 변경된 내용 길이에 맞춰 자동 배치를 재계산한다.

### 9.4 코멘트 삭제

`DELETE /api/v1/worksheets/{worksheetId}/comments/{commentId}`

- 헤더: 해당 코멘트의 If-Match 필수. 본문 없음.
- 응답: `204`. 코멘트와 연결된 강조 영역을 함께 조회 대상에서 제외한다.
- 원본 이미지와 AI 첨삭에는 영향이 없다. 이미 삭제된 ID는 404. 코멘트 삭제 취소는 현재 기능명세 범위에 없다.

### 9.5 자동 배치 책임

- 드래그, 입력 도구 표시, 하이라이트·밑줄 렌더링, 코멘트 상자 자동 배치, 연결선은 프론트 책임으로 제안한다.
- 서버는 영역·방식·내용을 저장하고 조회하므로 재접속 후 같은 의미의 코멘트를 복원할 수 있다.
- 프론트는 코멘트 생성·수정·삭제와 화면 크기 변화 시 원문을 가리지 않도록 재배치한다.
- 여백이 부족할 때의 배치 규칙은 디자인·프론트 협의가 필요하다. 본 API는 자동 배치 기능을 제외하는 것이 아니라 표시 위치 결정을 프론트에 맡긴다.
- 향후 서버에서 출력물을 생성하면 화면과 출력의 배치 규칙을 공유하거나 별도의 출력 레이아웃 계약을 정의해야 한다.

## 10. 프론트 연동 순서

1. POST /worksheets로 파일 등록 → Worksheet·미리보기·학습지 ETag 확보.
2. GET /settings로 설정 ETag 확보 → PUT /settings로 선택 영역·수준 저장.
3. GET /worksheets/{id}로 최신 학습지 ETag 확보.
4. POST /generations에 새 멱등 키와 If-Match 전달 → generationId 확보.
5. GET /generations/{id} 폴링 → SUCCEEDED면 GET /review로 검토 화면 구성.
6. 실패하면 원본·설정 유지. retryable이면 최신 학습지 ETag를 조회한 뒤 /retry 호출.
7. 첨삭 수정·삭제·복원은 항목별 ETag 사용. 성공 응답으로 프론트 상태 갱신.
8. 코멘트 작성·편집·삭제 후 프론트 자동 배치 갱신.
9. 412 발생 시 해당 자원을 다시 조회하고 사용자에게 충돌을 안내. 이전 입력을 조용히 덮어쓰지 않는다.

## 11. 기능명세 추적표

| 상세 기능 | 담당 API 또는 클라이언트 동작 |
| --- | --- |
| 2.1.1~2.1.2 파일 선택·검증 | POST /worksheets |
| 2.2.1 미리보기 | GET /worksheets/{id}의 pages |
| 2.2.2 파일 교체 | PUT /worksheets/{id}/file |
| 2.3.1·4.1.6·5.2.3 원본 보존 | 원본 불변, 파생 미리보기·별도 첨삭 자원 |
| 3.1.1~3.1.2 영역·수준 | GET·PUT /settings |
| 3.1.3 수준 안내 | 프론트 고정 안내 문구 |
| 4.1.1~4.1.5 분석·생성 | POST /generations 내부 OCR·LLM 처리 |
| 4.2.1~4.2.3 진행·완료·실패 | 생성 조회, 중복 방지, /retry |
| 5.1.1·5.1.3 오버레이·패널 | GET /review, /corrections |
| 5.1.2·5.1.4·5.3.1 확대·선택 | 정규화 좌표와 항목 ID를 사용하는 프론트 상태 |
| 5.2.1 수정 | PATCH /corrections/{id} |
| 5.2.2·5.2.4 삭제·복원 | DELETE /corrections/{id}, POST /restore |
| 6.1.1~6.1.2 드래그·입력 전환 | 프론트 영역 선택·입력 UI |
| 6.2.1~6.2.3 강조 | highlightType 저장 및 프론트 렌더링 |
| 6.3.1 작성 | POST /comments |
| 6.3.2 자동 배치 | 저장된 영역·내용 기반 프론트 자동 배치 |
| 6.3.3 편집 | PATCH /comments/{id}, 프론트 재배치 |
| 6.3.4 삭제 | DELETE /comments/{id}, 강조 동시 제거 |

## 12. 구현 전 확정할 정책

| 항목 | 현재 상태 |
| --- | --- |
| 파일 최대 용량·PDF 최대 페이지 수 | 미정. PDF·다중 페이지를 임의로 제거하지 않음 |
| 업로드·페이지 렌더링 최대 처리 시간 | 미정. 큰 PDF 허용 시 별도 비동기 준비 단계 검토 |
| 인증 수단·계정 정책 | 미정. 소유권 검사 자체는 필수 |
| 최초 첨삭 설정 기본값 | 미정. 초안은 명시적 저장 요구 |
| 첨삭·코멘트 최대 길이 | 미정 |
| OCR·LLM 제공자·모델 | 미정. 공개 API 계약에 제공자 세부사항 노출하지 않음 |
| 생성 타임아웃·동시 작업 수·재시도 제한 | 미정 |
| 파일·결과 보관 기간과 삭제 | 미정 |
| Standard 기준의 상세 예시 | 설명서와 기능명세의 표현 차이를 정리하고 기획 확정 필요 |
| 화면·출력 코멘트 배치 | 프론트 자동 배치 제안. 여백 부족 대응·출력 방식 미정 |
| 최종 다운로드·인쇄 | 별도 상세 요구가 없어 API는 미정. 설명 제외·편집값 반영 원칙 유지 |

## 13. 필수 검증 시나리오

- 손상 파일·다른 MIME·용량 초과·페이지 수 초과를 차단한다.
- 파일 교체 실패 시 기존 미리보기·설정이 유지된다.
- 빈 영역 선택·영역 중복·잘못된 수준을 차단한다.
- 생성 중 다른 키의 중복 요청은 차단하고, 같은 키의 재전송은 같은 작업을 반환한다.
- 파일·설정 변경 이후 오래된 실패 작업의 재시도를 차단한다.
- OCR 성공 후 LLM 실패 시 원본·설정·유효 OCR 결과를 보존한다.
- 원문 ID가 없는 AI 결과·다른 페이지 좌표·선택하지 않은 영역의 결과를 검증한다.
- 첨삭 0개인 정상 결과와 인식할 글이 없는 실패를 구분한다.
- 첨삭 편집 후 삭제·복원해도 교사의 편집값이 유지된다.
- 코멘트 삭제 시 강조도 사라지고 원본·AI 첨삭은 유지된다.
- 새로고침 후 검토·편집 상태를 복원하고, 만료된 이미지 URL은 재조회로 갱신한다.
- 타인 소유 worksheetId·pageId·correctionId·commentId 접근을 차단한다.
- 오래된 ETag로 수정하면 412를 반환하고 저장된 값을 덮어쓰지 않는다.
- 작업 실행 중 서버가 중단돼도 작업이 영구적으로 PROCESSING에 남지 않는다.
