# AUTEDI 백엔드 API 명세서 (API Specifications)

- **문서 버전**: v1.3
- **작성일**: 2026-10-29
- **대상 모듈**: `AUTEDI-backend` (Spring Boot 4.x / Java 21)
- **대상 도메인**: 
  - **F02**: 학습지 파일 관리 (Worksheet File Management) — 3종
  - **F03**: 첨삭 설정 (Worksheet Settings) — 2종
  - **F04**: AI 첨삭 생성 및 재시도 (AI Generation & Retry) — 3종
  - **F05**: 검토 화면 및 AI 첨삭 편집/삭제/복원 (Review & Correction Editing) — 6종
  - **F06**: 교사 코멘트 작성/조회/수정/삭제 (Teacher Comments) — 5종
- **Base URL**: `/api/v1`
- **단일 진실 공급원(SSOT)**: 본 문서는 AUTEDI 백엔드에 실제 구현된 F02~F06 핵심 엔드포인트 19종의 정식 통합 명세서입니다.

---

## 목차 (Table of Contents)

1. [개요 및 공통 규약](#1-개요-및-공통-규약)
   - 1.1 인증 및 멀티 테넌시 격리
   - 1.2 표준 공통 응답 봉투 (`ApiResponse<T>`)
   - 1.3 동시성 제어 및 단위별 ETag 규약
   - 1.4 생성 작업 멱등성 보장 (`Idempotency-Key`)
   - 1.5 학습지 및 첨삭 생애주기 상태 머신
2. [F02 학습지 파일 관리 API 명세 (3종)](#2-f02-학습지-파일-관리-api-명세)
   - 2.1 [POST] `/api/v1/worksheets` — 학습지 파일 업로드 등록
   - 2.2 [GET] `/api/v1/worksheets/{worksheetId}` — 학습지 상세 조회
   - 2.3 [PUT] `/api/v1/worksheets/{worksheetId}/file` — 학습지 파일 교체
3. [F03 첨삭 설정 API 명세 (2종)](#3-f03-첨삭-설정-api-명세)
   - 3.1 [GET] `/api/v1/worksheets/{worksheetId}/settings` — 첨삭 설정 조회
   - 3.2 [PUT] `/api/v1/worksheets/{worksheetId}/settings` — 첨삭 설정 전체 교체/저장
4. [F04 AI 첨삭 생성 API 명세 (3종)](#4-f04-ai-첨삭-생성-api-명세)
   - 4.1 [POST] `/api/v1/worksheets/{worksheetId}/generations` — AI 첨삭 생성 접수
   - 4.2 [GET] `/api/v1/worksheets/{worksheetId}/generations/{generationId}` — 생성 작업 상태 조회
   - 4.3 [POST] `/api/v1/worksheets/{worksheetId}/generations/{generationId}/retry` — 실패 작업 재시도
5. [F05 검토 화면 및 AI 첨삭 편집 API 명세 (6종)](#5-f05-검토-화면-및-ai-첨삭-편집-api-명세)
   - 5.1 [GET] `/api/v1/worksheets/{worksheetId}/review` — 검토 화면 통합 조회
   - 5.2 [GET] `/api/v1/worksheets/{worksheetId}/corrections` — 첨삭 목록 필터 조회
   - 5.3 [GET] `/api/v1/worksheets/{worksheetId}/corrections/{correctionId}` — 첨삭 단건 조회
   - 5.4 [PATCH] `/api/v1/worksheets/{worksheetId}/corrections/{correctionId}` — 첨삭 내용(content) 수정
   - 5.5 [DELETE] `/api/v1/worksheets/{worksheetId}/corrections/{correctionId}` — 첨삭 논리 삭제
   - 5.6 [POST] `/api/v1/worksheets/{worksheetId}/corrections/{correctionId}/restore` — 첨삭 삭제 취소/복원
6. [F06 교사 코멘트 API 명세 (5종)](#6-f06-교사-코멘트-api-명세)
   - 6.1 [GET] `/api/v1/worksheets/{worksheetId}/comments` — 코멘트 목록 조회
   - 6.2 [GET] `/api/v1/worksheets/{worksheetId}/comments/{commentId}` — 코멘트 단건 조회
   - 6.3 [POST] `/api/v1/worksheets/{worksheetId}/comments` — 코멘트 신규 작성
   - 6.4 [PATCH] `/api/v1/worksheets/{worksheetId}/comments/{commentId}` — 코멘트 내용 수정
   - 6.5 [DELETE] `/api/v1/worksheets/{worksheetId}/comments/{commentId}` — 코멘트 삭제
7. [상태 머신 및 작업 허용/차단 매트릭스](#7-상태-머신-및-작업-허용차단-매트릭스)
8. [공통 데이터 모델 및 DTO 명세](#8-공통-데이터-모델-및-dto-명세)
9. [표준 에러 코드 일람 (Error Code Reference)](#9-표준-에러-코드-일람-error-code-reference)
10. [cURL 요청 예시 (Examples)](#10-curl-요청-예시-examples)

---

## 1. 개요 및 공통 규약

### 1.1 인증 및 멀티 테넌시 격리
- **인증 방식**: 모든 API는 Spring Security 기반의 JWT Bearer 토큰 인증이 필수입니다.
  - 요청 헤더: `Authorization: Bearer <access_token>`
  - 토큰 누락 또는 서명 불일치 시 `401 Unauthorized` (`AUTH_001` / `AUTH_003`)를 응답합니다.
- **테넌시 격리 (Multi-Tenant Isolation)**:
  - 서버는 요청 헤더의 JWT 클레임에서 주입된 `CustomUserPrincipal`의 `academyId`를 기반으로 모든 데이터베이스 조회를 엄격히 제한합니다 (`findByIdAndAcademyId`).
  - 요청자가 소속되지 않은 타 학원의 `worksheetId`에 접근 시도 시, 보안상 자원의 존재 여부를 은닉하기 위해 `404 Not Found` (`WORKSHEET_001`)를 반환합니다.
- **유료 구독 상태 검증**:
  - 학습지 파일 업로드(`POST /api/v1/worksheets`) 및 AI 생성 요청(`POST /generations`)과 같은 주요 쓰기/연산 작업 시 학원의 구독 상태(`Academy.isActive()`)를 검증합니다. 만료(`EXPIRED`) 또는 정지(`SUSPENDED`)된 학원인 경우 `403 Forbidden` (`ACADEMY_002`)으로 차단됩니다. (기존 학습지 조회 및 검토는 업무 연속성을 위해 허용)

### 1.2 표준 공통 응답 봉투 (`ApiResponse<T>`)
AUTEDI의 모든 RESTful API 응답은 일관된 JSON 봉투(Envelope) 구조를 준수합니다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청이 성공적으로 처리되었습니다.",
  "result": { ... },
  "error": null
}
```

- **성공 응답 필드**: `isSuccess: true`, `code: "COMMON_200"`, `message: 안내문구`, `result: DTO 객체`, `error: null`
- **실패 응답 필드**: `isSuccess: false`, `code: 에러식별코드`, `message: 에러원인설명`, `result: null`, `error: 상세에러목록 또는 null`

### 1.3 동시성 제어 및 단위별 ETag 규약
자원의 특성에 따라 개별적인 ETag 네이밍 및 JPA 낙관적 락(`@Version`)을 적용하여 다중 사용자의 동시 편집 충돌(Lost Update)을 완벽히 방어합니다.
- **학습지 마스터 ETag**: `"\"ws_{worksheetId}-v{version}\""`
- **첨삭 설정 ETag**: `"\"settings_{worksheetId}-v{version}\""`
- **단일 첨삭 자원 ETag**: `"\"cor_{correctionId}-v{version}\""`
- **단일 코멘트 자원 ETag**: `"\"com_{commentId}-v{version}\""`

> [!IMPORTANT]
> 자원 갱신(`PUT`, `PATCH`, `DELETE`) 요청 시 이전 조회 시 발급받은 ETag 문자열을 `If-Match` 헤더에 담아 전송해야 합니다. 서버 버전과 불일치하거나 누락된 경우 `412 Precondition Failed` (`COMMON_412`)가 발생합니다.

### 1.4 생성 작업 멱등성 보장 (`Idempotency-Key`)
- AI 생성 접수(`POST /generations`) 및 재시도(`POST /retry`) 호출 시 네트워크 순단으로 인한 재전송이나 교사의 더블 클릭 시 중복 AI 호출을 방지하기 위해 `Idempotency-Key` 헤더를 지원합니다.
- 이미 접수된 멱등 키가 존재할 경우 중복 실행하지 않고 기존 작업(`GenerationResponse`)을 즉시 반환(Cache Hit)합니다.

### 1.5 학습지 및 첨삭 생애주기 상태 머신
- **WorksheetStatus**: `DRAFT` ➔ `PROCESSING` ➔ `READY` / `FAILED`
  - `DRAFT`: 최초 업로드 직후. 첨삭 설정 선택 및 파일 교체 가능.
  - `PROCESSING`: AI 비동기 파이프라인 실행 중. 전체 변경 잠금.
  - `READY`: 첨삭 생성 완료. **검토(F05) 및 코멘트(F06) 작업 필수 전제 조건**. 파일 교체/설정 수정 불가.
  - `FAILED`: AI 파이프라인 처리 실패. 파일 교체, 설정 수정, 동일 파일 재시도 가능.
- **CorrectionStatus**: `ACTIVE` ➔ `DELETED` ➔ `ACTIVE` (소프트 딜리트 및 복원 가능)

---

## 2. F02 학습지 파일 관리 API 명세

---

### 2.1 [POST] `/api/v1/worksheets` — 학습지 파일 업로드 등록

신규 학생 손글씨 과제 파일(이미지 또는 PDF)을 업로드하고, 학습지 마스터 레코드 및 기본 1페이지 정보를 생성합니다.

- **Method / URL**: `POST /api/v1/worksheets`
- **Content-Type**: `multipart/form-data`
- **Headers**: `Authorization: Bearer <token>` (필수)
- **Request Body**:
  - `file`: `MultipartFile` (필수, 허용: JPG, JPEG, PNG, PDF)
- **Response**: `201 Created`
  - **Headers**: `Location: /api/v1/worksheets/{id}`, `ETag: "\"ws_{id}-v1\""`
  - **Body Example**:
```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "학습지가 등록되었습니다.",
  "result": {
    "id": 1,
    "etag": "\"ws_1-v1\"",
    "status": "DRAFT",
    "file": {
      "name": "homework.png",
      "mediaType": "image/png",
      "sizeBytes": 1542380,
      "originalUrl": "http://localhost:8080/files/worksheets/1/a1b2c3d4-e5f6.png",
      "urlExpiresAt": null
    },
    "pages": [
      {
        "id": 1,
        "pageNumber": 1,
        "width": 1200,
        "height": 1600,
        "imageUrl": "http://localhost:8080/files/worksheets/1/a1b2c3d4-e5f6.png",
        "urlExpiresAt": null
      }
    ],
    "settings": {
      "etag": "\"settings_1-v1\"",
      "areas": []
    },
    "latestGenerationId": null,
    "createdAt": "2026-10-29T14:20:00",
    "updatedAt": "2026-10-29T14:20:00"
  },
  "error": null
}
```
- **주요 에러**: 400 (`FILE_001`), 401 (`AUTH_001`), 403 (`ACADEMY_002`), 415 (`FILE_002`), 500 (`FILE_003`)

---

### 2.2 [GET] `/api/v1/worksheets/{worksheetId}` — 학습지 상세 조회

학습지 원본 파일 정보, 페이지 목록, 현재 설정된 첨삭 영역 목록, 최신 AI 생성 작업 ID를 한 번에 조회합니다.

- **Method / URL**: `GET /api/v1/worksheets/{worksheetId}`
- **Headers**: `Authorization: Bearer <token>`
- **Path Variable**: `worksheetId` (Long, 필수)
- **Response**: `200 OK`, `ETag: "\"ws_{id}-v{version}\""`, Body: `ApiResponse<WorksheetDetailResponse>`
- **주요 에러**: 401 (`AUTH_001`), 404 (`WORKSHEET_001`)

---

### 2.3 [PUT] `/api/v1/worksheets/{worksheetId}/file` — 학습지 파일 교체

등록된 학습지의 원본 이미지/PDF 파일을 신규 파일로 교체합니다.

- **Method / URL**: `PUT /api/v1/worksheets/{worksheetId}/file`
- **Content-Type**: `multipart/form-data`
- **Headers**: `Authorization: Bearer <token>`, `If-Match: String` (선택적)
- **Path Variable**: `worksheetId` (Long, 필수)
- **Request Body**: `file`: `MultipartFile` (필수)
- **상태 제약**: `DRAFT` 또는 `FAILED`에서만 허용. `PROCESSING`/`READY` 시 `409 Conflict` (`WORKSHEET_002`).
- **정합성 처리**: 파일 교체 시 기존 페이지 OCR 캐시(`ocrResult`, `ocrFileHash`) 무조건 `null` 무효화, 상태는 `DRAFT`로 리셋, 첨삭 설정(`WorksheetSetting`)은 보존.
- **Response**: `200 OK`, `ETag: "\"ws_{id}-v{version+1}\""`, Body: `ApiResponse<WorksheetDetailResponse>`
- **주요 에러**: 400 (`FILE_001`), 404 (`WORKSHEET_001`), 409 (`WORKSHEET_002`), 412 (`COMMON_412`), 415 (`FILE_002`)

---

## 3. F03 첨삭 설정 API 명세

---

### 3.1 [GET] `/api/v1/worksheets/{worksheetId}/settings` — 첨삭 설정 조회

- **Method / URL**: `GET /api/v1/worksheets/{worksheetId}/settings`
- **Headers**: `Authorization: Bearer <token>`
- **Response**: `200 OK`, `ETag: "\"settings_{id}-v{version}\""`, Body: `ApiResponse<WorksheetSettingsResponse>`

---

### 3.2 [PUT] `/api/v1/worksheets/{worksheetId}/settings` — 첨삭 설정 전체 교체/저장

- **Method / URL**: `PUT /api/v1/worksheets/{worksheetId}/settings`
- **Headers**: `Authorization: Bearer <token>`, `If-Match: String` (선택적)
- **Request Body**:
```json
{
  "areas": [
    { "area": "GRAMMAR_SPELLING", "level": "STANDARD" },
    { "area": "VOCABULARY_EXPRESSION", "level": "ADVANCED" }
  ]
}
```
- **제약 조건**: 1~4개 영역 (`@NotEmpty`, `@Size(max=4)`), 중복 영역 불가, `DRAFT`/`FAILED` 상태 필수.
- **Response**: `200 OK`, `ETag: "\"settings_{id}-v{version+1}\""`, Body: `ApiResponse<WorksheetSettingsResponse>`

---

## 4. F04 AI 첨삭 생성 API 명세

---

### 4.1 [POST] `/api/v1/worksheets/{worksheetId}/generations` — AI 첨삭 생성 접수

- **Method / URL**: `POST /api/v1/worksheets/{worksheetId}/generations`
- **Headers**: `Authorization: Bearer <token>`, `Idempotency-Key: String` (선택/권장)
- **사전 조건**: `DRAFT` 또는 `FAILED` 상태, 설정 1개 이상 존재, 학원 구독 `ACTIVE`.
- **Response**: `202 Accepted`, `Location: /api/v1/worksheets/{id}/generations/{genId}`, Body: `ApiResponse<GenerationResponse>`

---

### 4.2 [GET] `/api/v1/worksheets/{worksheetId}/generations/{generationId}` — 생성 작업 상태 조회

- **Method / URL**: `GET /api/v1/worksheets/{worksheetId}/generations/{generationId}`
- **Response**: `200 OK`, Body: `ApiResponse<GenerationResponse>` (status, stage, attempt, error 등)

---

### 4.3 [POST] `/api/v1/worksheets/{worksheetId}/generations/{generationId}/retry` — 실패 작업 재시도

- **Method / URL**: `POST /api/v1/worksheets/{worksheetId}/generations/{generationId}/retry`
- **Headers**: `Authorization: Bearer <token>`, `Idempotency-Key: String` (선택)
- **사전 조건**: 대상 작업 `FAILED` 상태 필수, 원본 파일 해시 일치 필수.
- **Response**: `202 Accepted`, `Location: /api/v1/worksheets/{id}/generations/{newGenId}`, Body: `ApiResponse<GenerationResponse>`

---

## 5. F05 검토 화면 및 AI 첨삭 편집 API 명세

---

### 5.1 [GET] `/api/v1/worksheets/{worksheetId}/review` — 검토 화면 통합 조회

AI 첨삭 완료 후 교사 검토 화면에서 필요한 학습지 정보, 최신 생성 작업 상태, 유효 첨삭 목록, 활성 교사 코멘트 목록을 1회의 요청으로 일괄 반환합니다.

#### 요청 개요
- **HTTP Method**: `GET`
- **Request URL**: `/api/v1/worksheets/{worksheetId}/review`
- **Headers**: `Authorization: Bearer <token>` (필수)
- **Path Variable**: `worksheetId` (Long, 필수)

#### 비즈니스 로직 및 상태 제약
- **READY 상태 필수**: 학습지의 상태가 `WorksheetStatus.READY`가 아닌 경우 `409 Conflict` (`WORKSHEET_002`, "학습지가 검토 준비 완료(READY) 상태가 아닙니다.") 에러를 반환합니다.
- **유효 데이터만 집계**:
  - `corrections`: 논리 삭제되지 않은 `ACTIVE` 상태의 첨삭 카드만 포함.
  - `comments`: `deleted_at == null`인 활성 교사 코멘트만 포함 (`createdAt` 오름차순).

#### 응답 바디 (Response Body) — `200 OK`
```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "worksheet": {
      "id": 1,
      "etag": "\"ws_1-v2\"",
      "status": "READY",
      "file": {
        "name": "student_hw.png",
        "mediaType": "image/png",
        "sizeBytes": 1542380,
        "originalUrl": "http://localhost:8080/files/worksheets/1/a1b2c3d4-e5f6.png",
        "urlExpiresAt": null
      },
      "pages": [
        {
          "id": 1,
          "pageNumber": 1,
          "width": 1200,
          "height": 1600,
          "imageUrl": "http://localhost:8080/files/worksheets/1/a1b2c3d4-e5f6.png",
          "urlExpiresAt": null
        }
      ],
      "settings": {
        "etag": "\"settings_1-v2\"",
        "areas": [
          { "area": "GRAMMAR_SPELLING", "level": "STANDARD" }
        ]
      },
      "latestGenerationId": 10,
      "createdAt": "2026-10-29T14:20:00",
      "updatedAt": "2026-10-29T14:30:10"
    },
    "generation": {
      "id": 10,
      "worksheetId": 1,
      "status": "SUCCEEDED",
      "stage": "COMPLETED",
      "attempt": 1,
      "retryOfGenerationId": null,
      "settingsSnapshot": [
        { "area": "GRAMMAR_SPELLING", "level": "STANDARD" }
      ],
      "createdAt": "2026-10-29T14:30:00",
      "startedAt": "2026-10-29T14:30:01",
      "finishedAt": "2026-10-29T14:30:08",
      "error": null
    },
    "corrections": [
      {
        "id": 101,
        "etag": "\"cor_101-v1\"",
        "generationId": 10,
        "source": "AI",
        "area": "GRAMMAR_SPELLING",
        "originalText": "He go to school.",
        "aiInitialContent": "He goes to school.",
        "content": "He goes to school.",
        "explanation": "주어가 3인칭 단수이므로 goes를 사용해야 합니다.",
        "regions": [
          {
            "pageId": 1,
            "x": 0.25,
            "y": 0.35,
            "width": 0.08,
            "height": 0.03,
            "polygon": null
          }
        ],
        "editedByTeacher": false,
        "explanationStale": false,
        "status": "ACTIVE",
        "createdAt": "2026-10-29T14:30:05",
        "updatedAt": "2026-10-29T14:30:05"
      }
    ],
    "comments": [
      {
        "id": 201,
        "etag": "\"com_201-v1\"",
        "source": "TEACHER",
        "pageId": 1,
        "highlightType": "HIGHLIGHT",
        "regions": [
          {
            "pageId": 1,
            "x": 0.15,
            "y": 0.40,
            "width": 0.30,
            "height": 0.04,
            "polygon": null
          }
        ],
        "content": "도입부 구성이 훌륭합니다.",
        "createdAt": "2026-10-29T14:35:00",
        "updatedAt": "2026-10-29T14:35:00"
      }
    ]
  },
  "error": null
}
```

---

### 5.2 [GET] `/api/v1/worksheets/{worksheetId}/corrections` — 첨삭 목록 필터 조회

학습지의 AI 첨삭 목록을 상태, 페이지 번호, 영역별로 필터링하여 조회합니다.

#### 요청 개요
- **HTTP Method**: `GET`
- **Request URL**: `/api/v1/worksheets/{worksheetId}/corrections`
- **Headers**: `Authorization: Bearer <token>`
- **Query Parameters**:
  | 파라미터 | 필수 | 타입 | 기본값 | 설명 |
  | :--- | :---: | :--- | :--- | :--- |
  | `status` | 선택 | String | `ACTIVE` | 조회할 첨삭 상태 (`ACTIVE`, `DELETED`, `ALL`) |
  | `pageId` | 선택 | Long | - | 특정 페이지의 첨삭 항목만 필터링 |
  | `area` | 선택 | Enum | - | `GRAMMAR_SPELLING`, `VOCABULARY_EXPRESSION`, `SENTENCE_STRUCTURE`, `CONTENT_SUMMARY` |

#### 비즈니스 로직
- 학습지 `READY` 상태 필수 검증.
- `status` 파라미터가 없으면 활성 항목(`ACTIVE`)만 반환되며, `DELETED` 전달 시 삭제 항목(휴지통 뷰) 조회 가능.

#### 응답 바디 (Response Body) — `200 OK`
```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "items": [
      {
        "id": 101,
        "etag": "\"cor_101-v1\"",
        "generationId": 10,
        "source": "AI",
        "area": "GRAMMAR_SPELLING",
        "originalText": "He go to school.",
        "aiInitialContent": "He goes to school.",
        "content": "He goes to school.",
        "explanation": "주어가 3인칭 단수이므로 goes를 사용해야 합니다.",
        "regions": [
          { "pageId": 1, "x": 0.25, "y": 0.35, "width": 0.08, "height": 0.03, "polygon": null }
        ],
        "editedByTeacher": false,
        "explanationStale": false,
        "status": "ACTIVE",
        "createdAt": "2026-10-29T14:30:05",
        "updatedAt": "2026-10-29T14:30:05"
      }
    ],
    "total": 1
  },
  "error": null
}
```

---

### 5.3 [GET] `/api/v1/worksheets/{worksheetId}/corrections/{correctionId}` — 첨삭 단건 조회

단일 첨삭 카드의 상세 정보와 전용 ETag를 조회합니다. (DELETED 상태의 항목도 조회 가능)

- **Method / URL**: `GET /api/v1/worksheets/{worksheetId}/corrections/{correctionId}`
- **Headers**: `Authorization: Bearer <token>`
- **Response**: `200 OK`
  - **Headers**: `ETag: "\"cor_{id}-v{version}\""`
  - **Body**: `ApiResponse<CorrectionResponse>`

---

### 5.4 [PATCH] `/api/v1/worksheets/{worksheetId}/corrections/{correctionId}` — 첨삭 내용 수정

교사가 AI 추천 수정문(`content`)을 직접 편집합니다.

#### 요청 개요
- **HTTP Method**: `PATCH`
- **Request URL**: `/api/v1/worksheets/{worksheetId}/corrections/{correctionId}`
- **Content-Type**: `application/json`
- **Headers**: 
  - `Authorization: Bearer <token>` (필수)
  - `If-Match: "\"cor_{id}-v{version}\""` (**필수**, 동시 편집 충돌 방지)

#### Request Body (`application/json`)
```json
{
  "content": "He goes to school every morning."
}
```

#### 비즈니스 로직 및 정책
1. **READY 상태 검증**: 학습지가 `READY` 상태여야 함.
2. **If-Match 검증**: 누락 또는 버전 불일치 시 `412 Precondition Failed` (`COMMON_412`).
3. **삭제 상태 수정 차단**: 첨삭 항목이 `DELETED` 상태인 경우 `409 Conflict` (`CORRECTION_002`, "삭제된 첨삭 항목은 수정할 수 없습니다.") 차단. (복원 후 수정 가능)
4. **원본 불변 및 플래그 갱신**:
   - `originalText`, `aiInitialContent`, `explanation`은 영구 보존.
   - `content`만 변경되며 `isEdited = true` 설정.
   - 응답 DTO에서 `editedByTeacher = true`, `explanationStale = true`로 반환.
   - 낙관적 락 버전이 증가하며 새 ETag 발급.

#### 응답 바디 (Response Body) — `200 OK`
```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "첨삭 내용이 수정되었습니다.",
  "result": {
    "id": 101,
    "etag": "\"cor_101-v2\"",
    "generationId": 10,
    "source": "AI",
    "area": "GRAMMAR_SPELLING",
    "originalText": "He go to school.",
    "aiInitialContent": "He goes to school.",
    "content": "He goes to school every morning.",
    "explanation": "주어가 3인칭 단수이므로 goes를 사용해야 합니다.",
    "regions": [
      { "pageId": 1, "x": 0.25, "y": 0.35, "width": 0.08, "height": 0.03, "polygon": null }
    ],
    "editedByTeacher": true,
    "explanationStale": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-29T14:30:05",
    "updatedAt": "2026-10-29T14:40:00"
  },
  "error": null
}
```

---

### 5.5 [DELETE] `/api/v1/worksheets/{worksheetId}/corrections/{correctionId}` — 첨삭 논리 삭제

부적절한 AI 첨삭 카드를 논리 삭제(소프트 딜리트)합니다.

- **Method / URL**: `DELETE /api/v1/worksheets/{worksheetId}/corrections/{correctionId}`
- **Headers**: `Authorization: Bearer <token>`, `If-Match: String` (**필수**)
- **비즈니스 로직**:
  - `status = DELETED`, `deletedAt = NOW()` 설정.
  - 이미 `DELETED`인 경우 멱등하게 `204 No Content` 반환.
- **Response**: `204 No Content`
  - **Headers**: `ETag: "\"cor_{id}-v{version+1}\""` (바디 없음)

---

### 5.6 [POST] `/api/v1/worksheets/{worksheetId}/corrections/{correctionId}/restore` — 첨삭 삭제 취소/복원

삭제된 첨삭 카드를 다시 `ACTIVE` 상태로 복원합니다.

- **Method / URL**: `POST /api/v1/worksheets/{worksheetId}/corrections/{correctionId}/restore`
- **Headers**: `Authorization: Bearer <token>`, `If-Match: String` (**필수**)
- **비즈니스 로직**:
  - `status = ACTIVE`, `deletedAt = null` 설정.
  - **이전에 교사가 수정한 내용(`content`) 및 `isEdited` 플래그는 온전히 유지**됨.
- **Response**: `200 OK`, `ETag: "\"cor_{id}-v{version+1}\""`, Body: `ApiResponse<CorrectionResponse>` ("첨삭 삭제가 취소되었습니다.")

---

## 6. F06 교사 코멘트 API 명세

---

### 6.1 [GET] `/api/v1/worksheets/{worksheetId}/comments` — 코멘트 목록 조회

삭제되지 않은 활성 코멘트 목록을 작성 일시(`createdAt`) 오름차순으로 조회합니다.

- **Method / URL**: `GET /api/v1/worksheets/{worksheetId}/comments`
- **Query Parameter**: `pageId` (Long, 선택 — 특정 페이지 코멘트만 필터링)
- **Response**: `200 OK`, Body: `ApiResponse<CommentListResponse>`
```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {
    "items": [
      {
        "id": 201,
        "etag": "\"com_201-v1\"",
        "source": "TEACHER",
        "pageId": 1,
        "highlightType": "HIGHLIGHT",
        "regions": [
          { "pageId": 1, "x": 0.15, "y": 0.40, "width": 0.30, "height": 0.04, "polygon": null }
        ],
        "content": "도입부 구성이 훌륭합니다.",
        "createdAt": "2026-10-29T14:35:00",
        "updatedAt": "2026-10-29T14:35:00"
      }
    ],
    "total": 1
  },
  "error": null
}
```

---

### 6.2 [GET] `/api/v1/worksheets/{worksheetId}/comments/{commentId}` — 코멘트 단건 조회

- **Method / URL**: `GET /api/v1/worksheets/{worksheetId}/comments/{commentId}`
- **제약**: 삭제된 코멘트(`deleted_at != null`) 조회 시 `404 Not Found` (`COMMENT_001`).
- **Response**: `200 OK`, `ETag: "\"com_{id}-v{version}\""`, Body: `ApiResponse<CommentResponse>`

---

### 6.3 [POST] `/api/v1/worksheets/{worksheetId}/comments` — 코멘트 신규 작성

손글씨 과제물의 특정 위치에 하이라이트/밑줄 영역을 지정하고 교사 코멘트를 등록합니다.

#### 요청 개요
- **HTTP Method**: `POST`
- **Request URL**: `/api/v1/worksheets/{worksheetId}/comments`
- **Content-Type**: `application/json`
- **Headers**: `Authorization: Bearer <token>` (필수)

#### Request Body (`application/json`)
```json
{
  "pageId": 1,
  "highlightType": "HIGHLIGHT",
  "regions": [
    {
      "pageId": 1,
      "x": 0.15,
      "y": 0.40,
      "width": 0.30,
      "height": 0.04
    }
  ],
  "content": "이 문장의 표현이 매우 참신하고 좋습니다. 훌륭해요!"
}
```

#### 파라미터 제약 및 검증
- `pageId`: `@NotNull`, 학습지에 존재하는 유효 페이지 ID (`PAGE_001` 404).
- `highlightType`: `HIGHLIGHT`, `UNDERLINE` 둘 중 하나 필수.
- `regions`: 최소 1개 이상 필수. 내부의 `region.pageId`는 상위 `pageId`와 반드시 일치해야 함 (불일치 시 `400 Bad Request`, `COMMENT_002`).
- `content`: `@NotBlank`, 공백만 있는 문자열 차단.
- 작성자(`user`)는 Security Context에서 서버가 자동 주입.

#### 응답 헤더 및 바디 — `201 Created`
- **Headers**: `Location: /api/v1/worksheets/{worksheetId}/comments/{commentId}`, `ETag: "\"com_{id}-v1\""`
- **Body**: `ApiResponse<CommentResponse>` ("코멘트가 작성되었습니다.")

---

### 6.4 [PATCH] `/api/v1/worksheets/{worksheetId}/comments/{commentId}` — 코멘트 내용 수정

등록된 코멘트의 텍스트 내용(`content`)을 수정합니다.

- **Method / URL**: `PATCH /api/v1/worksheets/{worksheetId}/comments/{commentId}`
- **Headers**: `Authorization: Bearer <token>`, `If-Match: String` (**필수**)
- **Request Body**:
```json
{
  "content": "이 문장의 표현이 매우 참신하고 논리적입니다."
}
```
- **정책**: 텍스트 내용(`content`)만 수정 가능하며, 영역 좌표(`regions`)나 강조 유형(`highlightType`)은 불변.
- **Response**: `200 OK`, `ETag: "\"com_{id}-v{version+1}\""`, Body: `ApiResponse<CommentResponse>`

---

### 6.5 [DELETE] `/api/v1/worksheets/{worksheetId}/comments/{commentId}` — 코멘트 삭제

코멘트를 논리 삭제 처리합니다.

- **Method / URL**: `DELETE /api/v1/worksheets/{worksheetId}/comments/{commentId}`
- **Headers**: `Authorization: Bearer <token>`, `If-Match: String` (**필수**)
- **비즈니스 로직**: `deleted_at = NOW()` 설정. 삭제된 코멘트는 이후 목록 및 단건 조회에서 배제됨.
- **Response**: `204 No Content` (바디 없음)

---

## 7. 상태 머신 및 작업 허용/차단 매트릭스

| 엔드포인트 / 작업 | DRAFT | PROCESSING | READY | FAILED |
| :--- | :---: | :---: | :---: | :---: |
| **상세 조회 (`GET /worksheets/{id}`)** | 허용 | 허용 | 허용 | 허용 |
| **파일 교체 (`PUT /worksheets/{id}/file`)** | **허용** | **차단 (`WORKSHEET_002`)** | **차단 (`WORKSHEET_002`)** | **허용** |
| **설정 조회/교체 (F03 Settings)** | **허용** | **차단 (`WORKSHEET_002`)** | **차단 (`WORKSHEET_002`)** | **허용** |
| **AI 생성 접수 (`POST /generations`)** | **허용** | **차단 (`GENERATION_001`)** | **차단 (`GENERATION_002`)** | **허용** |
| **작업 재시도 (`POST /retry`)** | 차단 | 차단 | 차단 | **허용 (`GENERATION_005`)** |
| **검토 화면 통합 조회 (`GET /review`)** | **차단 (409)** | **차단 (409)** | **허용 (200)** | **차단 (409)** |
| **첨삭 목록/단건 조회 (F05)** | 차단 | 차단 | **허용** | 차단 |
| **첨삭 수정/삭제/복원 (F05)** | 차단 | 차단 | **허용** | 차단 |
| **교사 코멘트 작성/조회/수정/삭제 (F06)** | 차단 | 차단 | **허용** | 차단 |

---

## 8. 공통 데이터 모델 및 DTO 명세

### 8.1 `CorrectionResponse`
| 필드명 | 타입 | Nullable | 설명 |
| :--- | :--- | :---: | :--- |
| `id` | Long | No | 첨삭 항목 식별자 PK |
| `etag` | String | No | 첨삭 카드 ETag (예: `"\"cor_101-v1\""`) |
| `generationId` | Long | Yes | 생성 작업 ID |
| `source` | String | No | 고정값 `"AI"` |
| `area` | Enum | No | `GRAMMAR_SPELLING`, `VOCABULARY_EXPRESSION`, `SENTENCE_STRUCTURE`, `CONTENT_SUMMARY` |
| `originalText` | String | No | 학생 작성 원본 텍스트 (불변) |
| `aiInitialContent` | String | No | AI 최초 추천 수정문 (불변) |
| `content` | String | No | 현재 적용된 최종 수정문 (교사 수정 시 갱신) |
| `explanation` | String | No | AI 문법/어휘 설명문 (불변) |
| `regions` | Array | No | 대상 영역 좌표 리스트 (`pageId`, `x`, `y`, `width`, `height`) |
| `editedByTeacher` | Boolean | No | 교사가 직접 수정한 경우 `true` |
| `explanationStale`| Boolean | No | 교사 수정으로 AI 설명과 불일치할 수 있음을 나타내는 경고 플래그 |
| `status` | Enum | No | `ACTIVE`, `DELETED` |
| `createdAt` / `updatedAt` | String | No | ISO-8601 일시 |

### 8.2 `CommentResponse`
| 필드명 | 타입 | Nullable | 설명 |
| :--- | :--- | :---: | :--- |
| `id` | Long | No | 코멘트 식별자 PK |
| `etag` | String | No | 코멘트 ETag (예: `"\"com_201-v1\""`) |
| `source` | String | No | 고정값 `"TEACHER"` |
| `pageId` | Long | No | 귀속 페이지 ID |
| `highlightType` | Enum | No | `HIGHLIGHT`, `UNDERLINE` |
| `regions` | Array | No | 강조 영역 좌표 리스트 |
| `content` | String | No | 교사 피드백 코멘트 내용 |
| `createdAt` / `updatedAt` | String | No | ISO-8601 일시 |

---

## 9. 표준 에러 코드 일람 (Error Code Reference)

| HTTP Status | 에러 코드 | 응답 메시지 예시 | 발생 상황 |
| :---: | :--- | :--- | :--- |
| **400** | `COMMON_001` | 요청 값이 올바르지 않습니다. | DTO 검증 실패, 공백 전송, 파라미터 타입 불일치 |
| **400** | `FILE_001` | 업로드할 파일이 비어 있습니다. | 0바이트 파일 업로드 |
| **400** | `GENERATION_004` | 첨삭 영역을 최소 한 개 선택해 주세요. | 설정 없이 생성 요청 |
| **400** | `COMMENT_002` | 코멘트 영역의 페이지가 대상 페이지와 일치하지 않습니다. | `regions` 내부 `pageId` 불일치 |
| **401** | `AUTH_001` | 인증이 필요합니다. | 토큰 누락 또는 서명 오류 |
| **403** | `ACADEMY_002` | 학원 유료 구독이 활성 상태가 아닙니다. | 만료된 학원의 쓰기 요청 |
| **404** | `WORKSHEET_001` | 학습지를 찾을 수 없거나 접근 권한이 없습니다. | 학습지 미존재 또는 타 학원 자원 |
| **404** | `GENERATION_003` | 생성 작업을 찾을 수 없습니다. | 생성 작업 ID 미존재 |
| **404** | `CORRECTION_001` | 첨삭 항목을 찾을 수 없습니다. | 첨삭 ID 미존재 또는 타 학습지 첨삭 |
| **404** | `COMMENT_001` | 코멘트를 찾을 수 없거나 이미 삭제되었습니다. | 코멘트 미존재 또는 삭제된 코멘트 |
| **404** | `PAGE_001` | 해당 페이지를 찾을 수 없습니다. | 유효하지 않은 `pageId` |
| **409** | `WORKSHEET_002` | 현재 학습지 상태에서는 요청한 작업을 수행할 수 없습니다. | 검토 화면/수정 요청 시 READY가 아니거나 교체 시 DRAFT가 아님 |
| **409** | `CORRECTION_002` | 삭제된 첨삭 항목은 수정할 수 없습니다. | `DELETED` 상태의 첨삭에 `PATCH` 호출 |
| **409** | `GENERATION_001` | 이미 생성 작업이 진행 중입니다. | 진행 중인 학습지에 생성 요청 |
| **409** | `GENERATION_002` | 이미 생성이 완료된 학습지입니다. | 완료된 학습지에 생성 요청 |
| **409** | `GENERATION_005` | 재시도할 수 없는 작업 상태이거나 파일/설정이 변경되었습니다. | 실패 작업이 아니거나 파일 해시 불일치 |
| **412** | `COMMON_412` | 데이터 버전이 변경되었습니다. 최신 데이터를 조회한 후 다시 시도해 주세요. | `If-Match` 헤더 누락 또는 ETag 불일치 |
| **415** | `FILE_002` | 지원하지 않는 파일 형식입니다. JPG, PNG, PDF 파일만 허용됩니다. | 비허용 파일 확장자 |
| **500** | `COMMON_003` | 서버 내부 오류가 발생했습니다. | 미처리 런타임 예외 |

---

## 10. cURL 요청 예시 (Examples)

### 10.1 검토 화면 통합 조회 (GET)
```bash
curl -X GET "http://localhost:8080/api/v1/worksheets/1/review" \
  -H "Authorization: Bearer <access_token>"
```

### 10.2 첨삭 내용 수정 (PATCH)
```bash
curl -X PATCH "http://localhost:8080/api/v1/worksheets/1/corrections/101" \
  -H "Authorization: Bearer <access_token>" \
  -H "If-Match: \"cor_101-v1\"" \
  -H "Content-Type: application/json" \
  -d '{
    "content": "He goes to school every morning."
  }'
```

### 10.3 첨삭 논리 삭제 (DELETE)
```bash
curl -X DELETE "http://localhost:8080/api/v1/worksheets/1/corrections/101" \
  -H "Authorization: Bearer <access_token>" \
  -H "If-Match: \"cor_101-v1\""
```

### 10.4 첨삭 삭제 취소/복원 (POST)
```bash
curl -X POST "http://localhost:8080/api/v1/worksheets/1/corrections/101/restore" \
  -H "Authorization: Bearer <access_token>" \
  -H "If-Match: \"cor_101-v2\""
```

### 10.5 교사 코멘트 작성 (POST)
```bash
curl -X POST "http://localhost:8080/api/v1/worksheets/1/comments" \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "pageId": 1,
    "highlightType": "HIGHLIGHT",
    "regions": [
      {
        "pageId": 1,
        "x": 0.15,
        "y": 0.40,
        "width": 0.30,
        "height": 0.04
      }
    ],
    "content": "이 문장의 표현이 매우 참신하고 좋습니다. 훌륭해요!"
  }'
```

### 10.6 교사 코멘트 수정 (PATCH)
```bash
curl -X PATCH "http://localhost:8080/api/v1/worksheets/1/comments/201" \
  -H "Authorization: Bearer <access_token>" \
  -H "If-Match: \"com_201-v1\"" \
  -H "Content-Type: application/json" \
  -d '{
    "content": "이 문장의 표현이 매우 참신하고 논리적입니다."
  }'
```

### 10.7 교사 코멘트 삭제 (DELETE)
```bash
curl -X DELETE "http://localhost:8080/api/v1/worksheets/1/comments/201" \
  -H "Authorization: Bearer <access_token>" \
  -H "If-Match: \"com_201-v1\""
```
