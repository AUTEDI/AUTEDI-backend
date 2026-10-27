# AUTEDI API 명세서 — MVP 단순화 버전

- 작성일: 2026-10-27
- 버전: v0.1 / 구현 전 협의용 초안
- 기준: `AUTEDI AI 첨삭 서비스 기능명세서(최종).md`
- 상세 설계 참고: `AUTEDI API 명세서.md` (기존 파일은 유지)
- 목적: 1주 MVP를 위해 핵심 API 5개로 업로드·AI 첨삭·검토·저장을 연결한다.
- 이 문서는 구현 완료를 의미하지 않는다. 상세 버전과 차이가 있는 사항은 이 버전의 정책을 따른다.

## 1. 이번 버전의 범위

| 항목 | 단순화 방식 |
| --- | --- |
| 업로드 | JPG/JPEG/PNG 이미지 한 장. PDF·다중 페이지는 다음 단계로 연기하는 제안 |
| 첨삭 설정 | 업로드 요청에 함께 전달. 별도 설정 저장 API 없음 |
| 파일 교체 | 요청 전 프론트에서 교체. 서버 등록 후 변경하려면 새 작업 생성 |
| AI 생성 | 업로드와 함께 시작. 별도 생성 API·Generation 자원 없음 |
| 진행 조회 | 학습지 상세에서 PROCESSING / READY / FAILED 반환 |
| 수정·삭제·복원 | 프론트에서 변경한 뒤 저장 버튼으로 전체 반영 |
| 교사 코멘트 | 드래그·강조·내용 입력 후 전체 저장에 포함 |
| 재접속 | 상세 조회로 마지막 저장 상태 복원. 저장하지 않은 변경은 복원하지 않음 |
| 동시 수정 | 항목별 ETag 대신 검토 결과 전체의 version 하나로 충돌 방지 |
| 작업 목록 | 최근 작업을 다시 열기 위한 목록 API 추가 |
| 출력·사용자 관리 | 다운로드·인쇄·회원가입·학생 관리 API는 범위 밖 |

> 이미지 한 장 제한과 명시적 저장 방식은 기존 기능명세를 축소·구체화하는 제안이다. 제품 범위로 확정하려면 기획 합의가 필요하다. 특히 기존 명세의 PDF 지원을 완료했다고 표시하지 않는다.

인증 방식은 별도 결정하되, 모든 API에서 로그인한 사용자의 데이터만 접근하도록 한다. 인증 API 개수는 아래 5개에 포함하지 않는다. 공개 배포 시 무인증 공용 데이터로 대체하지 않는다.

## 2. API 목록과 사용 흐름

Base path: `/api/v1`

| Method | Path | 역할 | 성공 |
| --- | --- | --- | --- |
| POST | /worksheets | 이미지·설정 등록 및 생성 시작 | 202 |
| GET | /worksheets/{id} | 진행 상태 또는 전체 결과 조회 | 200 |
| POST | /worksheets/{id}/retry | 실패 작업 재시도 | 202 |
| PUT | /worksheets/{id}/review | 첨삭·코멘트 전체 저장 | 200 |
| GET | /worksheets | 작업 목록·다시 열기 | 200 |

```text
프론트에서 파일 선택·미리보기·설정 선택
  → POST /worksheets
  → GET /worksheets/{id} 폴링
  → READY: 이미지 위 첨삭 + 오른쪽 목록 표시
  → 프론트에서 수정·삭제·복원·코멘트 편집
  → PUT /worksheets/{id}/review
  → 재접속하면 마지막 저장 상태 조회

FAILED: 원본·설정 유지 → 조건에 따라 재시도 또는 새 작업
```

## 3. 공통 규칙

- JSON은 `application/json`, 업로드는 `multipart/form-data`를 사용한다.
- ID는 서버가 발급하는 불투명 문자열. 날짜는 ISO 8601 UTC 문자열이다.
- 성공 응답은 `{ "data": ... }`로 감싼다.
- 타인 소유 자원은 존재하지 않는 자원과 동일하게 404를 반환한다.
- 최대 파일 크기·이미지 픽셀 수·코멘트 길이·작업 제한은 구현 전 확정하고 서버에서 검증한다.
- 허용하지 않은 요청 필드는 400으로 거부한다. 소유자·모델명·프롬프트는 프론트가 지정하지 않는다.

### 3.1 첨삭 설정

| area | 의미 |
| --- | --- |
| GRAMMAR_MECHANICS | 문법 및 철자 |
| VOCABULARY_EXPRESSION | 어휘 및 표현 |
| SENTENCE_STRUCTURE | 문장 구성 |
| CONTENT_SUMMARY | 내용 및 요약 |

- level: `STANDARD`, `ADVANCED`.
- 선택한 영역만 areas에 포함한다. 1~4개, 중복 불가, 각 영역의 수준 필수.
- 수준은 AI 개입 강도이다. 오류 수정·개선 제안·설명 선택값은 받지 않는다.

### 3.2 이미지 좌표

- 원본 파일은 변경하지 않는다. 서버는 방향을 보정한 기준 미리보기 이미지를 만들고 OCR도 같은 이미지에서 수행한다.
- 원점은 이미지 좌상단. x는 오른쪽, y는 아래쪽이며 모든 값은 0~1 정규화 값이다.
- MVP는 사각형 `x, y, width, height`를 사용한다. 기울어진 단어는 이를 감싸는 사각형으로 표시한다.
- `x >= 0`, `y >= 0`, `width > 0`, `height > 0`, `x + width <= 1`, `y + height <= 1`이어야 한다.
- 여러 줄은 regions 배열의 여러 사각형으로 표현한다. 한 작업은 이미지 한 장이므로 pageId는 생략한다.
- 좌표는 응답의 image.url 이미지 기준이다. 프론트는 화면 표시 크기와 이동량을 반영한다.
- AI 첨삭 좌표는 서버가 OCR 결과와 연결한다. 교사 코멘트 좌표는 프론트가 드래그한 영역을 전달한다.

```json
{ "x": 0.2, "y": 0.3, "width": 0.06, "height": 0.03 }
```

### 3.3 중복 실행 방지

- 업로드 요청은 multipart metadata의 requestId, 재시도는 JSON의 requestId를 사용한다. UUID 문자열을 권장한다.
- 같은 동작을 네트워크 문제로 재전송할 때는 같은 requestId를 사용한다. 새로운 사용자 동작에는 새 값을 발급한다.
- 서버는 사용자·동작·requestId별 최초 접수 응답을 저장한다. 같은 키·같은 요청은 같은 접수 응답을 반환하고 새 작업을 실행하지 않는다.
- 같은 키에 다른 요청은 409 REQUEST_ID_REUSED. 파일 요청의 동일성은 파일 해시와 설정으로 판단한다.
- 재시도는 작업별 실행 잠금으로 중복 실행도 차단한다. 응답 재전송 여부 판단은 상태 검사보다 먼저 한다.
- 접수 기록 보관은 최소 24시간을 제안한다. 클라이언트가 자동으로 재전송하는 범위도 그 안으로 제한한다.

## 4. 이미지 업로드 및 첨삭 시작

`POST /api/v1/worksheets`

### 요청

| multipart 필드 | 타입 | 필수 | 내용 |
| --- | --- | --- | --- |
| file | binary | O | JPG/JPEG/PNG 파일 한 개 |
| metadata | application/json 파트 | O | requestId와 settings |

metadata 예시:

```json
{
  "requestId": "5cce0299-e203-42bf-9aac-64cbb4402d40",
  "settings": {
    "areas": [
      { "area": "GRAMMAR_MECHANICS", "level": "STANDARD" },
      { "area": "VOCABULARY_EXPRESSION", "level": "ADVANCED" }
    ]
  }
}
```

### 응답

`202 Accepted`, `Location: /api/v1/worksheets/ws_001`

```json
{
  "data": {
    "id": "ws_001",
    "status": "PROCESSING",
    "attempt": 1
  }
}
```

### 처리 규칙

1. 사용자·requestId·파일 형식·용량·픽셀 수·설정을 검증한다. 확장자만 확인하지 않고 실제 디코딩도 확인한다.
2. 원본·기준 미리보기·설정을 저장한 뒤 백그라운드 생성 작업을 접수한다.
3. 저장·작업 접수에 실패하면 성공 응답을 반환하지 않는다. 임시 파일을 정리한다.
4. 서버에서 OCR → LLM → 결과 검증 → 결과 저장을 수행한다.
5. 성공하면 READY, 실패하면 FAILED로 변경한다. 부분 결과를 READY로 공개하지 않는다.

모든 OCR·LLM 호출을 HTTP 업로드 응답 완료까지 기다리지 않는다. 작업 접수·DB 저장의 불일치로 PROCESSING 작업이 유실되지 않도록 한다.

## 5. 상태 및 결과 조회

`GET /api/v1/worksheets/{id}`

### 처리 중 응답

```json
{
  "data": {
    "id": "ws_001",
    "status": "PROCESSING",
    "attempt": 1,
    "fileName": "homework.jpg",
    "image": {
      "url": "https://files.example.test/preview-signed-url",
      "urlExpiresAt": "2026-10-27T10:15:00Z",
      "width": 1000,
      "height": 1400
    },
    "settings": {
      "areas": [{ "area": "GRAMMAR_MECHANICS", "level": "STANDARD" }]
    },
    "review": null,
    "error": null,
    "createdAt": "2026-10-27T10:00:00Z",
    "updatedAt": "2026-10-27T10:00:00Z"
  }
}
```

- 초기 폴링 간격은 2초를 제안한다. READY·FAILED가 되면 중지한다.
- image.url은 소유권 확인 후 발급한 만료되는 서명 URL 또는 인증된 URL이다. 공개 영구 URL을 기본으로 사용하지 않는다.
- URL 만료 시 이 API를 다시 조회한다. 원본 파일은 별도 보존하며 편집 화면은 기준 미리보기를 사용한다.

### 완료 시 review 필드

READY 응답은 같은 공통 필드에 아래 review를 포함하며 error는 null이다.

```json
{
  "version": 1,
  "corrections": [
    {
      "id": "cor_001",
      "source": "AI",
      "area": "GRAMMAR_MECHANICS",
      "level": "STANDARD",
      "type": "CORRECTION",
      "originalText": "go",
      "content": "goes",
      "explanation": "주어가 He이므로 goes를 사용합니다.",
      "explanationStale": false,
      "regions": [{ "x": 0.2, "y": 0.3, "width": 0.06, "height": 0.03 }],
      "deleted": false
    }
  ],
  "comments": [],
  "savedAt": "2026-10-27T10:00:30Z"
}
```

- type: CORRECTION 또는 SUGGESTION. explanation은 각 수정·제안의 교사용 설명이다.
- 수정할 곳이 없는 글은 corrections=[]인 READY로 처리한다. 읽을 텍스트가 없는 실패와 구분한다.
- deleted=true 항목도 반환한다. 프론트는 기본 화면에서 숨기되 삭제 취소에 활용한다.
- 코멘트 조회 객체는 7절의 저장 요청 코멘트 구조에 `source: "TEACHER"`를 추가한 형태이다.
- 원문·좌표·영역·수준·최초 설명은 서버가 관리한다. 프론트의 저장 요청으로 변경할 수 없다.
- 교사가 content를 변경하면 explanationStale=true. 설명을 AI 최초 제안 기준으로 안내한다.
- 최종 출력 기능을 추가할 경우 deleted=false의 최신 content만 반영하고 explanation은 제외한다.

### 실패 시 error 필드

FAILED 응답은 review=null이고 다음 error를 포함한다. 실패한 작업 조회 자체는 HTTP 200이다.

```json
{
  "code": "LLM_FAILED",
  "message": "첨삭 생성에 실패했습니다. 다시 시도해주세요.",
  "retryable": true
}
```

작업 실패 코드: OCR_FAILED, NO_TEXT_DETECTED, LLM_FAILED, INVALID_AI_OUTPUT, PROCESSING_TIMEOUT, WORKER_INTERRUPTED.

## 6. 실패 작업 재시도

`POST /api/v1/worksheets/{id}/retry`

```json
{
  "requestId": "17130bd4-92ec-453e-885b-efdb57b11e16",
  "expectedAttempt": 1
}
```

- FAILED이며 error.retryable=true인 경우만 가능하다.
- expectedAttempt는 상세에서 마지막으로 확인한 attempt이다. 늦게 도착한 과거 재시도가 새 실패 작업을 다시 실행하지 않도록 현재 값과 일치해야 한다.
- 같은 requestId 재전송은 기존 접수 응답을 반환한다. 다른 키의 요청은 상태·expectedAttempt를 검사한다.
- 응답: `202`, `{ "data": { "id": "ws_001", "status": "PROCESSING", "attempt": 2 } }`.
- 파일·설정은 그대로 사용한다. 유효한 OCR 결과가 있으면 재사용한다.
- PROCESSING·READY 또는 expectedAttempt 불일치면 409. 입력 변경이 필요한 실패는 새 학습지를 등록한다.
- 작업 실행 횟수·동시 실행 수를 제한하고, 중단된 작업은 타임아웃 후 FAILED로 정리한다. 이전 attempt의 늦은 결과가 최신 작업 상태를 덮어쓰지 못하게 한다.

## 7. 검토 결과 전체 저장

`PUT /api/v1/worksheets/{id}/review`

### 요청

```json
{
  "version": 1,
  "corrections": [
    { "id": "cor_001", "content": "goes", "deleted": false }
  ],
  "comments": [
    {
      "id": "adfb5b49-d9c3-43d5-9506-3027d1a378dc",
      "regions": [{ "x": 0.1, "y": 0.2, "width": 0.3, "height": 0.05 }],
      "highlightType": "HIGHLIGHT",
      "content": "이유를 한 문장 추가해보세요."
    }
  ]
}
```

### 응답

```json
{
  "data": {
    "version": 2,
    "savedAt": "2026-10-27T10:05:00Z"
  }
}
```

### 저장 규칙

- READY에서만 저장 가능하다. version·corrections·comments 모두 필수이다.
- corrections는 삭제된 항목을 포함해 서버에 존재하는 모든 AI 첨삭 ID를 정확히 한 번씩 포함한다. 누락·중복·알 수 없는 ID는 400으로 거부한다.
- 수정 가능한 AI 첨삭 필드는 content와 deleted뿐이다. content는 trim 후 빈 문자열을 허용하지 않는다.
- AI 첨삭 삭제: deleted=true. 복원: deleted=false. 삭제 직전 편집 content를 보존한다.
- comments는 저장할 전체 목록이다. 기존 코멘트를 목록에서 빼면 삭제되고 그 강조 표시도 사라진다.
- 새 코멘트 ID는 프론트가 UUID로 발급한다. 서버는 형식·목록 내 중복·다른 학습지 소속 여부를 검증하며 다른 자원의 코멘트를 수정하지 않는다.
- 새 코멘트는 regions 최소 한 개, 유효 사각형, HIGHLIGHT/UNDERLINE 중 하나, 비어 있지 않은 content가 필수이다.
- 기존 코멘트 ID는 content만 변경 가능하다. regions·highlightType 변경은 거부한다. 영역·강조 방식 변경이 필요하면 삭제 후 새 ID로 작성한다.
- version 비교와 모든 항목 저장을 하나의 트랜잭션으로 수행한다. 하나라도 오류면 전체를 저장하지 않는다.
- 성공하면 version을 1 증가시킨다. AI 초기 결과 저장 시 version=1부터 시작한다.
- 버전이 다르면 409 VERSION_CONFLICT. 마지막 저장을 덮어쓰지 않는다.

### 프론트 저장·복원 처리

- 저장 전 변경은 프론트 메모리에만 존재한다. 변경 여부 표시, 저장 버튼, 페이지 이탈 경고가 필요하다.
- 삭제 취소는 프론트에서 deleted를 되돌린 뒤 전체 저장한다. 저장된 AI 삭제 항목도 상세 조회에서 복원 가능하다.
- 저장 요청 중 추가 편집을 잠그거나, 요청 스냅샷 이후의 변경을 미저장 상태로 유지해야 한다. 늦은 저장 응답으로 새 편집을 지우지 않는다.
- 응답 유실·버전 충돌 시 상세를 다시 조회해 저장 상태와 로컬 변경을 비교한다. 최신 version으로 바꿔 무조건 덮어쓰는 자동 재시도는 하지 않는다.
- 저장 성공 후 상세를 재조회하면 explanationStale 등 서버 계산값까지 동기화할 수 있다.
- 코멘트 생성·수정·삭제 후 프론트가 여백 배치를 다시 계산한다. 상자 위치·연결선은 저장 API에 보내지 않는다.
- 고정 여백 배치 등 단순한 표현 방식을 제안하되, 원문을 가리지 않는 규칙은 디자인과 합의해야 한다.

## 8. 작업 목록

`GET /api/v1/worksheets?page=0&size=20`

- page: 기본 0, 0 이상. size: 기본 20, 1~50. 잘못된 값은 400.
- 사용자 본인 작업만 반환한다. createdAt 내림차순, 동률이면 id 내림차순.
- PROCESSING·READY·FAILED 작업을 모두 포함한다. 클릭하면 상세 API로 이동한다.

```json
{
  "data": {
    "items": [
      {
        "id": "ws_001",
        "fileName": "homework.jpg",
        "status": "READY",
        "createdAt": "2026-10-27T10:00:00Z",
        "updatedAt": "2026-10-27T10:05:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "hasNext": false
  }
}
```

목록 API는 기준 기능명세의 독립 요구는 아니지만 저장 작업을 다시 열기 위해 포함한다. 검색·학생별 필터·썸네일 생성은 이 버전에서 제외한다.

## 9. 공통 오류

```json
{
  "error": {
    "code": "VERSION_CONFLICT",
    "message": "다른 저장 내용이 있습니다. 최신 결과를 확인해주세요.",
    "requestId": "req_001"
  }
}
```

오류의 requestId는 서버 로그 추적 ID이며 업로드·재시도 중복 방지용 requestId와 구분한다.

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 필수값·영역·수준·좌표·ID·내용 오류 |
| 401 | UNAUTHORIZED | 인증 필요 |
| 404 | RESOURCE_NOT_FOUND | 자원이 없거나 타인 소유 |
| 409 | INVALID_STATE | 처리 중 저장, 완료 작업 재시도 등 |
| 409 | VERSION_CONFLICT | 오래된 검토 결과 저장 |
| 409 | REQUEST_ID_REUSED | 같은 중복 방지 키에 다른 요청 |
| 409 | ATTEMPT_CONFLICT | 재시도 대상 attempt가 현재 값과 다름 |
| 413 | FILE_TOO_LARGE | 파일 용량 초과 |
| 415 | UNSUPPORTED_FILE_TYPE | 지원하지 않는 형식. 이 초안에서는 PDF 포함 |
| 422 | INVALID_IMAGE | 손상 파일·픽셀 수 제한 초과·디코딩 불가 |
| 429 | RATE_LIMITED | 요청·사용량 제한 |
| 500 | INTERNAL_ERROR | 서버 오류 |
| 503 | SERVICE_UNAVAILABLE | 저장소·작업 접수 일시 장애 |

클라이언트에 API 키·원시 외부 오류·스택 추적을 노출하지 않는다. 비동기 OCR·LLM 실패는 5절의 작업 error로 반환한다.

## 10. 역할 분담과 구현 순서

| 백엔드 | 프론트 |
| --- | --- |
| 파일 검증·보존·기준 이미지 준비 | 선택·교체·미리보기 |
| OCR·LLM·원문 위치 연결 | 좌표 표시·확대·축소·항목 선택 |
| 처리 상태·재시도·중복 방지 | 폴링·진행·오류 안내 |
| 검토 데이터 검증·전체 저장 | 수정·삭제·복원·저장 버튼 |
| 코멘트 영역·내용 보존 | 드래그·강조·자동 배치·연결선 |
| 소유권·API 키·저장소 권한 관리 | 저장되지 않은 변경 안내 |

구현 순서:

1. 업로드·상세 조회·배포 연결.
2. 실제 OCR·LLM 결과를 상세 조회에 연결.
3. 전체 저장과 새로고침 후 복원 구현.
4. 실패 재시도·목록 구현.
5. 실제 이미지로 좌표·편집·실패·접근 제어 검증.

## 11. 배포 전 필수 확인

- 이미지가 아닌 파일과 용량·픽셀 수 초과를 서버에서 차단한다.
- 같은 업로드를 재전송해도 새 과금 작업이 중복 생성되지 않는다.
- 서버 재시작·외부 API 타임아웃 후 작업이 PROCESSING에 영구 정체되지 않는다.
- OCR 원문과 AI 첨삭이 정확한 위치에 연결된다. 이미지 확대·축소로 위치가 어긋나지 않는다.
- 선택하지 않은 영역, 없는 원문 ID, 잘못된 좌표를 포함한 AI 응답을 검증한다.
- 첨삭 수정·삭제·복원·코멘트 저장 후 상세를 다시 조회하면 상태가 유지된다.
- 잘못된 항목이 하나 있으면 전체 저장이 취소되고 이전 데이터가 보존된다.
- 오래된 version·attempt로 새 작업 상태를 덮어쓸 수 없다.
- 다른 사용자 데이터와 이미지에 접근할 수 없고 API 키는 서버에만 둔다.
- 인증 방식, 파일 제한, 작업 제한, 문자열 제한, 원본 보관 기간을 운영 전에 확정한다.

이 버전은 API 수를 줄인 설계이다. 실제 OCR·첨삭 정확도 검증, 원본 보존, 소유권 검사, 영속 저장과 실패 처리를 생략하는 설계는 아니다.
