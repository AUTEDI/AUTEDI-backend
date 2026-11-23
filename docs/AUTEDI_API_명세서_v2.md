# AUTEDI API 명세서 v2

- 작성일: 2026-11-23
- 문서 상태: 현재 구현 명세 + 신규·변경 설계안 통합 초안
- 코드 기준: 로컬 브랜치 `docs/92-v2-api-doc`, 커밋 `39ad0ae`
- 기능 기준: [AUTEDI 기능명세서 v2](<C:/study/CEOS/AUTEDI/autedi/docs/AUTEDI 기능명세서 v2.md>)
- 범위: 현재 애플리케이션 Controller에 구현된 API 37개, 앞선 논의의 신규 API 설계안 22개, 조건부 API 설계안 6개
- 확인 방식: Controller·Service·DTO·공통 응답·보안 설정을 정적으로 대조했다. 서버·DB·배포 환경 확인과 테스트 실행은 하지 않았다.

> 2장은 현재 호출 가능한 코드 계약이다. 5–7장은 아직 구현되지 않은 기능 또는 기존 API의 변경 목표다. 신규 메서드·URL·필드명은 개발·프런트 합의를 위한 제안이며, 기획 정책의 확정과 API 계약의 확정을 구분한다.

## 목차

1. [공통 규칙](#1-공통-규칙)
2. [현재 구현된 엔드포인트](#2-현재-구현된-엔드포인트)
3. [현재 응답 데이터 구조](#3-현재-응답-데이터-구조)
4. [현재 오류 코드](#4-현재-오류-코드)
5. [신규 엔드포인트 설계안](#5-신규-엔드포인트-설계안)
6. [기존 API 변경 설계안](#6-기존-api-변경-설계안)
7. [조건부 엔드포인트 설계안](#7-조건부-엔드포인트-설계안)
8. [확정 정책과 미정 사항](#8-확정-정책과-미정-사항)
9. [연동 흐름과 검증 범위](#9-연동-흐름과-검증-범위)

## 1. 공통 규칙

### 1.1 경로·인증·표기

| 항목 | 규칙 |
| --- | --- |
| 공통 경로 | `/api/v1`. 아래 표의 URL에는 이 접두사를 생략한다. 예: `/users/me` → `/api/v1/users/me` |
| 일반 인증 | `Authorization: Bearer {accessToken}` |
| JSON 요청 | `Content-Type: application/json` |
| 파일 요청 | `Content-Type: multipart/form-data` |
| JSON 응답 | `ApiResponse<T>` 공통 봉투. `result` 구조는 3장 참조 |
| 예외 응답 | 파일·이미지 바이너리, SSE, HTTP 204는 JSON 성공 봉투를 사용하지 않음 |
| Path | 모든 경로 변수는 필수. 별도 명시가 없으면 ID는 정수형 |
| Query / Body | `*`는 필수. 표시가 없으면 선택. 본문 전체 생략 가능 여부는 별도 명시 |
| 날짜·시간 | 현재 DTO의 `LocalDateTime`은 시간대 오프셋 없는 ISO 형태. 예: `2026-11-23T15:00:00`. 이를 UTC/KST로 임의 단정하지 않음 |
| 권한 | `DIRECTOR`: 원장, `TEACHER`: 선생님 |
| 기관 분리 | 학습지 등 기관 자원은 로그인 기관을 기준으로 접근. 기관 밖 학습지는 통상 `404 / WORKSHEET_001` |
| 요청 추적 | 응답 헤더 `X-Request-Id` 제공 |

비인증 API는 회원가입·로그인·토큰 재발급·초대 토큰 검증과 QR의 scan/status/token이다. 초대 생성은 원장만 가능하다. QR 생성·승인·거절·link와 로그아웃·본인 정보·학습지·SSE는 인증이 필요하다.

현재 기관 검증을 업로더 소유권 검사와 동일하게 해석하면 안 된다. 일반 학습지 조회·편집은 기관 기준이며, QR 대상 지정·QR 업로드에는 추가 소유권 검사가 있다. 신규 임시저장 삭제에는 D6에 따라 별도의 업로더 본인 검사를 적용해야 한다.

### 1.2 구현·정책 상태

| 표시 | 의미 |
| --- | --- |
| 구현 | 현재 코드에 존재. 2장에는 현재 요청·동작만 기록 |
| 신규안 | 필요한 기능이며 엔드포인트 형태는 제안. 현재 호출 불가 |
| 변경안 | 기존 경로에 추가할 요청·응답·권한·상태 정책. 현재 동작과 구분 |
| 조건부 | 정책 또는 구현 주체 결정 후 API 추가 여부 확정 |
| 미정 | 결정되지 않은 사항. 확정 규칙이나 현재 응답값으로 사용하지 않음 |

### 1.3 성공·실패 응답

성공 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "성공입니다.",
  "result": {},
  "error": null
}
```

실패 예시:

```json
{
  "isSuccess": false,
  "code": "WORKSHEET_001",
  "message": "학습지를 찾을 수 없거나 접근 권한이 없습니다.",
  "result": null,
  "error": null
}
```

- 현재 `ApiResponse.onSuccess`는 HTTP 201·202에서도 본문 `code`에 `COMMON_200`을 사용한다. HTTP 상태와 본문 비즈니스 코드는 별개다.
- `error`는 null, 상세 문자열, 필드 검증 오류 문자열 배열 등일 수 있다. `message`는 API별 성공 문구 또는 오류 코드의 기본 문구다.
- HTTP 204는 본문이 없다.
- 생성 상태 조회가 HTTP 200이어도 `result.status=FAILED`, `result.error`가 존재할 수 있다. 비동기 처리 실패와 HTTP 요청 실패를 구분한다.

### 1.4 ETag와 If-Match — Service의 실제 요구사항

| 대상 | 현재 If-Match 계약 |
| --- | --- |
| 학습지 파일 교체 | 선택. 지정하면 현재 학습지 ETag와 비교. `*` 허용 |
| 첨삭 설정 교체 | 선택. 학습지 ETag 또는 설정 ETag와 비교. `*` 허용 |
| AI 첨삭 수정·삭제·복원 | **필수**. 누락·불일치는 `412 / COMMON_412` |
| AI 첨삭 반영 여부 변경 | 선택. 지정된 비어 있지 않은 값은 비교 |
| 수기 첨삭 수정·삭제 | **필수**. 누락·불일치는 `412 / COMMON_412` |

예: `If-Match: "cor_201-v2"`. 학습지 `"ws_1-v1"`, 설정 `"settings_1-v1"`, 수기 첨삭 `"com_301-v1"` 등 조회 응답의 최신 값을 사용한다. 파일·이미지 바이너리 응답의 해시 ETag는 이 편집용 버전 ETag와 다르다.

일부 Controller의 Swagger 헤더 설명은 선택으로 선언되어 있지만 Service는 필수를 요구한다. 이 문서는 실제 Service 조건을 기준으로 한다. AI 첨삭·수기 첨삭의 필수 버전 검사에 `*`를 사용할 수 있다고 해석하지 않는다.

## 2. 현재 구현된 엔드포인트

이 장의 37개 메서드·URL은 현재 코드에 존재한다. 신규 학생·교사 관리, 업무 임시/최종 저장은 5장에 분리했다. 표의 성공 상태와 DTO 이름은 실제 Controller 반환 기준이다.

### 2.1 인증 — 4개 / F01·F02·F12

권한: signup/login/refresh는 비인증, logout은 인증.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| POST | 회원가입 | `/auth/signup` | Body: `token*`, `email*`, `name*`, `password*` | 유효한 초대 토큰과 이메일로 선생님 계정을 생성. `201`, `SignupResponse`. 자동 로그인하지 않음 |
| POST | 로그인 | `/auth/login` | Body: `email*`, `password*` | 이메일·비밀번호 인증 후 토큰과 본인 정보를 반환. `200`, `LoginResponse` |
| POST | 토큰 재발급 | `/auth/refresh` | Body: `refreshToken*` | Refresh Token Rotation. 사용한 Refresh Token 폐기 후 새 토큰 쌍 발급. `200`, `TokenRefreshResponse` |
| POST | 로그아웃 | `/auth/logout` | Body 전체 선택: `refreshToken` | 전달된 Refresh Token을 찾아 폐기. `200`, `result=null`. 미전달·미존재 토큰도 폐기 없이 성공할 수 있음 |

현재 검증·동작:

- 회원가입: 토큰·이메일·이름·비밀번호 공백 금지, 이메일 형식, 이름 최대 50자, 비밀번호 최소 8자. 초대 이메일과 요청 이메일 일치, 토큰 만료·수락·중복 이메일 검사. 역할은 `TEACHER`, 계정은 `ACTIVE`, 초대는 `ACCEPTED`로 변경.
- **현재 비밀번호 검증에는 최대 20자와 영문·숫자·특수문자 조합 검사가 없다.** 기획 정책과의 차이는 6장에 기록한다.
- 로그인: 비밀번호 대조, `INACTIVE`·`LOCKED` 차단, 마지막 로그인 시각 갱신.
- Access Token 기본 유효기간은 1시간, Refresh Token은 14일이며 설정으로 바뀔 수 있다.
- Refresh: 토큰 만료·폐기 및 사용자 활성 상태 확인. QR 연결 표시도 새 토큰에 유지.
- 로그아웃은 현재 Access Token 즉시 무효화나 다른 기기 세션 전체 종료를 제공하지 않는다.
- 주요 오류: `COMMON_001`, `AUTH_003`–`AUTH_009`, `INVITATION_001`–`INVITATION_004`.

### 2.2 초대 — 2개 / F10·F01

권한: 생성은 원장 전용, 검증은 비인증.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| POST | 선생님 이메일 초대 | `/invitations` | Body: `email*` | 초대 저장과 비동기 메일 발송 요청. `201`, `InvitationResponse`. 현재 이름은 받지 않음 |
| GET | 초대 토큰 검증 | `/invitations/validate` | Query: `token*` | 유효한 초대의 이메일·기관명·만료 시각 조회. `200`, `InvitationValidateResponse` |

- 현재 초대 유효기간은 생성 시각부터 24시간이다. 초대한 기관이 활성 구독 상태인지 검사한다.
- 가입된 이메일은 기관 구분 없이 차단한다. 같은 이메일의 기존 `PENDING` 초대는 무효화하고 새 초대를 만든다. 기존 대기 초대 검색 역시 기관으로 한정하지 않는 현재 동작이므로 운영 정책과 대조가 필요하다.
- 201은 메일 수신·실제 전송 완료를 보장하지 않는다. 발송은 비동기다.
- 초대 레코드만 생성하며 가입 전 `User` 계정을 만들지는 않는다.
- 주요 오류: `AUTH_002`, `AUTH_009`, `ACADEMY_002`, `INVITATION_001`–`INVITATION_004`.

### 2.3 본인 계정 — 1개 / F03·F12

권한: 인증된 원장·선생님.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 내 계정·소속 기관 조회 | `/users/me` | 없음 | 본인 ID·이메일·이름·역할·기관·구독 상태 반환. `200`, `UserResponse` |

현재 이름·기관명·비밀번호 수정 API는 없다. 주요 오류: `AUTH_001`, `AUTH_006`.

### 2.4 QR 로그인·업로드 연계 — 7개

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| POST | QR 세션 생성 | `/auth/qr-sessions` | 인증; Body 전체 선택: `worksheetId` | 첫 업로드 또는 기존 작업 교체용 QR 생성. `201`, `QrSessionCreateResponse` |
| POST | QR 스캔 알림 | `/auth/qr-sessions/scan` | 비인증; Body: `qrToken*`; Header: `User-Agent` | `PENDING → SCANNED`. 생성자에게 `qr.scanned` 이벤트. `200`, `result=null` |
| POST | QR 로그인 승인 | `/auth/qr-sessions/{sessionId}/approve` | 인증·세션 생성자; Path: `sessionId` | `SCANNED → APPROVED`. `200`, `result=null` |
| POST | QR 로그인 거절 | `/auth/qr-sessions/{sessionId}/reject` | 인증·세션 생성자; Path: `sessionId` | `PENDING / SCANNED → REJECTED`. `200`, `result=null` |
| POST | QR 상태 폴링 | `/auth/qr-sessions/status` | 비인증; Body: `qrToken*` | 토큰으로 상태 조회. 시간이 지난 활성 세션은 `EXPIRED`로 처리. `200`, `QrSessionStatusResponse` |
| POST | QR 로그인 토큰 발급 | `/auth/qr-sessions/token` | 비인증; Body: `qrToken*` | `APPROVED → COMPLETED`, 세션 사용자 토큰 발급. `200`, `QrLoginResponse` |
| POST | 로그인된 휴대폰 QR 연결 | `/auth/qr-sessions/link` | 인증; Body: `qrToken*`; Header: `User-Agent` | QR 생성자와 현재 사용자가 같을 때 `PENDING → COMPLETED`. `200`, `QrLoginResponse`, `qr.linked` 이벤트 |

- `sessionId`는 UUID 형태 문자열이며 승인·거절에 사용한다. `qrToken`은 QR 링크의 token이며 공백 불가·최대 64자다. 상태·발급도 URL Query가 아닌 Body로 전달한다.
- 기본 세션 유효기간은 3분이며 설정으로 변경 가능하다. 상태 폴링은 1–2초 간격으로 안내되어 있다.
- `worksheetId` 지정 시 같은 기관·업로더 본인 작업인지 검사한다. 생략하면 첫 사진 업로드 시 새 학습지가 만들어진다.
- QR 하나로 이미지 한 장·작업 하나만 업로드한다. 토큰 발급의 `COMPLETED`는 QR 로그인 완료이며 사진 업로드 완료나 첨삭 최종 저장 완료를 뜻하지 않는다.
- QR 발급 응답의 `worksheetId`가 있으면 해당 학습지 파일 교체, null이면 신규 업로드로 연결한다. QR 세션 정보가 들어간 새 토큰을 업로드에 사용한다.
- IP·경로별 기본 제한: scan/token/link 각 분당 10, status 분당 90. 초과 시 `429 / COMMON_429`와 `Retry-After`. 설정·토큰 버킷 기반 제한이다.
- 주요 오류: `QR_001`–`QR_007`, `AUTH_002`, `AUTH_007`, `WORKSHEET_001`.

### 2.5 학습지 파일 — 5개 / F05

권한: 인증된 원장·선생님. QR 업로드에는 추가 대상 검증 적용.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| POST | 학습지 이미지 업로드 | `/worksheets` | Form: `files[]`, `file[]`, `images[]` 중 유효 이미지 1개 이상 | 한 학습지와 페이지 생성. `201`, `WorksheetDetailResponse`, `Location`, `ETag` |
| GET | 학습지 상세 조회 | `/worksheets/{worksheetId}` | Path: `worksheetId` | 파일·페이지·설정·AI 처리 상태·최근 생성 ID 조회. `200`, `WorksheetDetailResponse`, `ETag` |
| GET | 원본 다운로드 | `/worksheets/{worksheetId}/file` | Path: `worksheetId` | 업로드 당시 원본 바이트 반환. `200`, 바이너리, 첨부 파일명·Content-Type·해시 ETag 제공 |
| GET | 페이지 보정 이미지 조회 | `/worksheets/{worksheetId}/pages/{pageNumber}/image` | Path: `worksheetId`, `pageNumber` | EXIF 방향 보정 PNG 반환. `200`, `image/png`, 해시 ETag 제공. pageNumber는 1부터 시작 |
| PUT | 학습지 파일 교체 | `/worksheets/{worksheetId}/file` | Path: `worksheetId`; Form: `file*`; Header: `If-Match` 선택 | `DRAFT / FAILED`이고 단일 페이지일 때 이미지 한 장 교체. `200`, `WorksheetDetailResponse`, `ETag` |

파일 처리 계약:

- 지원은 JPG/JPEG·PNG이며 **PDF 업로드는 지원하지 않는다.** enum에 PDF가 있어도 업로드 지원을 뜻하지 않는다.
- 업로드 시 각 별칭의 비어 있지 않은 파일을 `files → file → images` 순서로 합친다. 보통 한 별칭만 사용하고, 전달 순서가 페이지 순서가 된다.
- 다중 이미지 업로드는 가능하지만 **실제 AI 연동·고정 응답 검증의 생성은 현재 한 페이지만 지원**한다. 다중 업로드 가능과 다중 AI 처리 가능을 구분한다.
- 현재 이미지 처리 제한: 원본과 보정 PNG 각각 최대 20 MiB, 픽셀 수 최대 40,000,000. 멀티파트 기본 설정은 파일 30MB·요청 100MB이며 이미지 처리 제한을 대체하지 않는다. 작업별 최대 장수의 기획 정책은 별도 미정이다.
- 교체 요청은 멀티파트 파일 전체가 정확히 한 개여야 한다. 기존 첨삭 설정을 보존하고 이미지·OCR 캐시를 갱신하며 AI 상태는 `DRAFT`로 돌아간다.
- 신규 업로드는 활성 기관 구독을 검사한다. 모든 쓰기 API에 동일한 구독 검사가 있다는 뜻은 아니다.
- 원본·페이지 이미지는 Bearer 인증 후 바이너리로 조회하며 `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`를 제공한다. 화면의 첨삭 위치 기준 이미지는 `pages[].imageUrl`이다.
- 현재 상세 응답에는 업무 제목·학생·임시/최종 저장 상태가 없다.
- 주요 오류: `WORKSHEET_001`, `WORKSHEET_002`, `COMMON_412`, `FILE_001`–`FILE_007`, `PAGE_001`, `QR_002`, `QR_004`, `QR_006`, `QR_007`.

### 2.6 첨삭 설정 — 2개 / F05

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 첨삭 설정 조회 | `/worksheets/{worksheetId}/settings` | Path: `worksheetId` | 현재 선택 영역·수준 반환. `200`, `WorksheetSettingsResponse`, `ETag` |
| PUT | 첨삭 설정 전체 교체 | `/worksheets/{worksheetId}/settings` | Path: `worksheetId`; Body: `areas*`; Header: `If-Match` 선택 | 선택 영역 1–4개, 중복 불가, 영역·수준 필수. `DRAFT / FAILED`에서만 허용. `200`, `WorksheetSettingsResponse`, `ETag` |

```json
{
  "areas": [
    {"area": "GRAMMAR_SPELLING", "level": "STANDARD"},
    {"area": "VOCABULARY_EXPRESSION", "level": "ADVANCED"}
  ]
}
```

영역 표준값은 `GRAMMAR_SPELLING`, `VOCABULARY_EXPRESSION`, `SENTENCE_STRUCTURE`, `CONTENT_SUMMARY`, 수준은 `STANDARD / ADVANCED`다. 제목·학생은 현재 이 요청 DTO에 없다. 주요 오류: `COMMON_001`, `COMMON_412`, `WORKSHEET_001`, `WORKSHEET_002`.

### 2.7 AI 첨삭 생성 — 3개 / F06

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| POST | AI 생성 요청 접수 | `/worksheets/{worksheetId}/generations` | Path: `worksheetId`; Header: `Idempotency-Key` 선택; Body 없음 | 생성 작업을 저장하고 비동기 처리 접수. `202`, `GenerationResponse`, `Location` |
| GET | AI 생성 작업 상태 조회 | `/worksheets/{worksheetId}/generations/{generationId}` | Path: `worksheetId`, `generationId` | 대기·실행·성공·실패와 처리 단계·오류 조회. `200`, `GenerationResponse` |
| POST | 실패 생성 재시도 | `/worksheets/{worksheetId}/generations/{generationId}/retry` | Path: `worksheetId`, `generationId`; Header: `Idempotency-Key` 선택; Body 없음 | 재시도 가능한 최신 실패 작업 접수. `202`, `GenerationResponse`, `Location` |

- 요청 시 활성 사용자·기관, 선택 영역을 검사한다. 새 요청은 현재 학습지 `PROCESSING`이면 진행 중 오류, `READY`이면 생성 완료 오류다.
- 유효한 같은 멱등키로 같은 생성/재시도 요청을 반복하면 기존 작업을 반환한다. 다른 학습지 또는 다른 생성/재시도 용도로 사용한 키는 `409 / GENERATION_007`이다.
- 멱등키는 최대 100자이며 생략·공백이면 서버가 새 키를 만든다. 재전송 중복 방지가 필요하면 같은 키를 재사용한다.
- 재시도는 최신 생성이 `FAILED`이고 재시도 가능하며, 학습지 상태·파일 해시·설정이 실패 당시와 맞아야 한다. 유효한 전사문이 있으면 재사용할 수 있다.
- 서버 설정에 따라 실제 외부 AI, 고정 응답, 기존 Mock 경로가 달라진다. 요청 Body로 AI 계약·모델을 지정하는 API는 아니다.
- `GenerationResponse.finishedAt`은 AI 작업 종료 시각이며 업무 최종 저장일이 아니다.
- 주요 오류: `GENERATION_001`–`GENERATION_008`, `WORKSHEET_001`, `COMMON_001`, `AUTH_002`, `ACADEMY_002`.

### 2.8 검토·AI 첨삭 — 7개 / F06

권한: 인증된 같은 기관 사용자. 현재 학습지 `READY` 필요.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 검토 화면 통합 조회 | `/worksheets/{worksheetId}/review` | Path: `worksheetId` | 학습지·최신 생성·활성 AI 첨삭·수기 첨삭·검토 이슈·요약·결과 출처 반환. `200`, `ReviewResponse` |
| GET | AI 첨삭 목록 조회 | `/worksheets/{worksheetId}/corrections` | Path: `worksheetId`; Query: `status`, `pageId`, `area`, `feedbackType`, `isApplied` | 조건에 맞는 첨삭 목록과 건수 반환. `200`, `CorrectionListResponse` |
| GET | AI 첨삭 단건 조회 | `/worksheets/{worksheetId}/corrections/{correctionId}` | Path: `worksheetId`, `correctionId` | 첨삭·원문 연결·좌표·검증 정보 반환. `200`, `CorrectionResponse`, `ETag` |
| PATCH | AI 첨삭 반영 여부 변경 | `/worksheets/{worksheetId}/corrections/{correctionId}/apply` | Path: `worksheetId`, `correctionId`; Query: `applied` 기본 true; Header: `If-Match` 선택 | 지정한 반영 여부로 변경. `200`, `CorrectionResponse`, `ETag` |
| PATCH | AI 첨삭 수정 | `/worksheets/{worksheetId}/corrections/{correctionId}` | Path: `worksheetId`, `correctionId`; Body: `content`, `regions`, `isApplied` 중 하나 이상; Header: `If-Match*` | 현재 내용·좌표·반영 여부 수정. `200`, `CorrectionResponse`, `ETag` |
| DELETE | AI 첨삭 논리 삭제 | `/worksheets/{worksheetId}/corrections/{correctionId}` | Path: `worksheetId`, `correctionId`; Header: `If-Match*` | 첨삭을 `DELETED`로 변경하고 삭제 시각 기록. `204`, 본문 없음, `ETag` |
| POST | AI 첨삭 복원 | `/worksheets/{worksheetId}/corrections/{correctionId}/restore` | Path: `worksheetId`, `correctionId`; Header: `If-Match*` | 삭제한 첨삭을 `ACTIVE`로 복원. `200`, `CorrectionResponse`, `ETag` |

- 목록 status 생략 시 `ACTIVE`, `DELETED`는 삭제 항목, `ALL`은 전체다. 기존 목록 응답은 페이지네이션이 아닌 `{items, total}`이다.
- `feedbackType`은 `CORRECTION / SUGGESTION`, `isApplied`·`applied`는 boolean이다. 반영 여부 API는 이름에 토글이 있어도 지정값을 설정하며 자동 반전하지 않는다.
- `PATCH`의 생략한 필드는 유지한다. 내용·좌표·반영 여부 중 하나는 있어야 한다.
- `action=REPLACE`의 빈 문자열은 삭제 제안일 수 있어 허용하고 공백을 보존한다. `GUIDANCE`와 과거 미분류 자료의 공백 내용은 거부한다.
- 좌표 수정은 페이지 일치와 0–1 정규화 영역을 검증한다. 원문·최초 AI 제안·해설·생성 아카이브는 보존한다.
- 현재는 개별 AI 항목의 논리 삭제만 있다. 학습지 전체 삭제와 업무 완료 후 수정 금지는 아직 없다.
- 주요 오류: `WORKSHEET_001`, `WORKSHEET_002`, `CORRECTION_001`, `CORRECTION_002`, `COMMON_001`, `COMMON_412`.

### 2.9 선생님 수기 첨삭 — 5개 / F07

권한: 인증된 같은 기관 사용자. 현재 학습지 `READY` 필요.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 수기 첨삭 목록 조회 | `/worksheets/{worksheetId}/comments` | Path: `worksheetId`; Query: `pageId` | 삭제되지 않은 코멘트·강조 표시 조회. `200`, `CommentListResponse` |
| GET | 수기 첨삭 단건 조회 | `/worksheets/{worksheetId}/comments/{commentId}` | Path: `worksheetId`, `commentId` | 코멘트 단건 조회. `200`, `CommentResponse`, `ETag` |
| POST | 수기 첨삭 등록 | `/worksheets/{worksheetId}/comments` | Path: `worksheetId`; Body: `pageId*`, `highlightType*`, `regions*`, `content*`, `color` | 교사 코멘트·강조 영역 생성. `201`, `CommentResponse`, `Location`, `ETag` |
| PATCH | 수기 첨삭 수정 | `/worksheets/{worksheetId}/comments/{commentId}` | Path: `worksheetId`, `commentId`; Body: `content*`, `color`; Header: `If-Match*` | 내용·색상 수정. 좌표·강조 유형 수정 API는 아님. `200`, `CommentResponse`, `ETag` |
| DELETE | 수기 첨삭 논리 삭제 | `/worksheets/{worksheetId}/comments/{commentId}` | Path: `worksheetId`, `commentId`; Header: `If-Match*` | 코멘트·연결 강조 표시를 논리 삭제. `204`, 본문 없음 |

- `highlightType`: `HIGHLIGHT`, `UNDERLINE`, `STRIKETHROUGH`, `ARROW`, `TEXTBOX`, `CARET`.
- `color`: `RED`, `BLUE`, `YELLOW`, `GREEN`. 생성 시 생략하면 `BLUE`, 수정 시 생략하면 기존 색상 유지.
- `regions`는 비어 있으면 안 된다. 대상 페이지는 해당 학습지에 있어야 하며 region의 pageId를 주면 대상 페이지와 같아야 한다. 현재 Service가 모든 좌표 수치에 Correction과 동일한 검증을 한다고 해석하지 않는다.
- `content`는 공백 불가이며 저장 시 앞뒤 공백을 제거한다. 삭제한 코멘트는 조회 시 찾을 수 없음으로 처리한다.
- 학생 프로필 메모와 다른 자원이다. 기존 수기 첨삭 목록 역시 페이지네이션이 아닌 `{items, total}`이다.
- 주요 오류: `COMMENT_001`, `COMMENT_002`, `PAGE_001`, `WORKSHEET_001`, `WORKSHEET_002`, `COMMON_001`, `COMMON_412`.

### 2.10 실시간 이벤트 — 1개 / 공통

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | SSE 이벤트 구독 | `/events` | Header: `Authorization*`, `Accept: text/event-stream` 권장 | 로그인 사용자 이벤트 스트림. `200`, `text/event-stream`. JSON 봉투 없음 |

- Bearer 헤더를 전송할 수 있는 클라이언트로 연결한다. 현재 토큰 Query 인증은 제공하지 않는다.
- Access Token 만료 시 연결 종료. 기본 heartbeat 25초, emitter timeout 30분, 사용자 최대 연결 5개이며 설정으로 변경 가능하다.
- 응답은 `Cache-Control: no-cache`, `X-Accel-Buffering: no`. 시작 시 연결 주석과 heartbeat 주석을 보낼 수 있다.
- `id`, `event`, `data` 형식이며 data 공통 필드는 `eventId`, `occurredAt`이다. 재접속 시 과거 이벤트 재생을 제공하는 API는 없으므로 상태 조회로 보완한다.

| event | 추가 data 필드 | 의미 |
| --- | --- | --- |
| `qr.scanned` | `sessionId`, `deviceInfo` | QR 스캔 |
| `qr.linked` | `sessionId`, `deviceInfo` | 로그인된 휴대폰의 QR 연결 |
| `worksheet.created` | `sessionId`, `worksheetId` | QR 업로드로 학습지 생성 |
| `worksheet.file_replaced` | `sessionId`, `worksheetId` | QR 업로드로 학습지 파일 교체 |
| `generation.started` | `worksheetId`, `generationId` | 생성 시작 |
| `generation.progress` | `worksheetId`, `generationId`, `stage` | 처리 단계 변경 |
| `generation.succeeded` | `worksheetId`, `generationId` | 생성 성공 |
| `generation.failed` | `worksheetId`, `generationId`, `errorCode`, `retryable` | 생성 실패 |

QR 이벤트 수신자는 세션 생성자이고 학습지·생성 이벤트 수신자는 작업 소유자다. 현재 학습지 생성·교체 이벤트는 QR 연계 업로드일 때 발행한다. 일반 업로드에서도 같은 이벤트가 발생한다고 가정하지 않는다. 실제 결과 본문은 별도 조회 API로 읽는다.

```text
id: 00000000-0000-4000-8000-000000000001
event: generation.progress
data: {"eventId":"00000000-0000-4000-8000-000000000001","occurredAt":"2026-11-23T15:00:00","worksheetId":1,"generationId":101,"stage":"OCR"}

```

## 3. 현재 응답 데이터 구조

이 장은 2장의 JSON `result`에 들어가는 **현재 DTO**를 설명한다. 소스가 null·빈 배열을 반환할 수 있는 버전별 필드는 임의로 필수화하지 않는다. 예시는 가상 값이다.

### 3.1 인증·사용자·초대·QR

| DTO | 현재 필드 |
| --- | --- |
| `SignupResponse` | `userId: Long`, `email: String`, `name: String`, `role: TEACHER` |
| `LoginResponse` | `accessToken: String`, `refreshToken: String`, `user: UserResponse` |
| `TokenRefreshResponse` | `accessToken: String`, `refreshToken: String` |
| `UserResponse` | `userId: Long`, `email: String`, `name: String`, `role: DIRECTOR/TEACHER`, `academyId: Long`, `academyName: String`, `subscriptionStatus: ACTIVE/EXPIRED/SUSPENDED` |
| `InvitationResponse` | `invitationId: Long`, `email: String`, `status: InvitationStatus`, `expiresAt: LocalDateTime` |
| `InvitationValidateResponse` | `valid: boolean`, `email: String`, `academyName: String`, `expiresAt: LocalDateTime` |
| `QrSessionCreateResponse` | `sessionId: String`, `expiresAt: LocalDateTime`, `qrImage: String`. qrImage는 PNG data URI |
| `QrSessionStatusResponse` | `status: PENDING/SCANNED/APPROVED/COMPLETED/REJECTED/EXPIRED` |
| `QrLoginResponse` | `accessToken: String`, `refreshToken: String`, `user: UserResponse`, `worksheetId: Long 또는 null` |

초대 상태는 `PENDING / ACCEPTED / EXPIRED / REVOKED`, 사용자 DB 상태는 `ACTIVE / INACTIVE / LOCKED`다. 제품의 교사 상태인 초대 대기·활동 중·퇴사를 그대로 같은 enum으로 표현하고 있지는 않다.

로그인 응답 예시:

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "로그인에 성공했습니다.",
  "result": {
    "accessToken": "<access-token>",
    "refreshToken": "<refresh-token>",
    "user": {
      "userId": 45,
      "email": "teacher@example.com",
      "name": "김선생",
      "role": "TEACHER",
      "academyId": 1,
      "academyName": "AUTEDI 학원",
      "subscriptionStatus": "ACTIVE"
    }
  },
  "error": null
}
```

초대 검증 실패는 정상 응답의 `valid=false`로 통일되어 있지 않으며 4장의 오류 응답을 사용한다.

### 3.2 학습지·페이지·설정

| DTO | 현재 필드 |
| --- | --- |
| `WorksheetDetailResponse` | `id`, `etag`, `status`, `file`, `pages`, `settings`, `latestGenerationId`, `createdAt`, `updatedAt` |
| `FileDetailResponse` | `name: String`, `mediaType: String`, `sizeBytes: Long`, `originalUrl: String`, `urlExpiresAt: LocalDateTime 또는 null` |
| `PageDetailResponse` | `id: Long`, `pageNumber: Integer`, `width: Integer`, `height: Integer`, `imageUrl: String`, `urlExpiresAt: LocalDateTime 또는 null`, `regionCoordinateUnit` |
| `WorksheetSettingsResponse` | `etag: String`, `areas: SettingItemResponse[]` |
| `SettingItemResponse` | `area: CorrectionArea`, `level: STANDARD/ADVANCED` |

- `WorksheetDetailResponse.status`는 `DRAFT / PROCESSING / READY / FAILED`다. `latestGenerationId`는 생성 전 null일 수 있다.
- `pages`는 페이지 배열이고 `width/height`는 보정 PNG의 픽셀 크기다. `regionCoordinateUnit`은 `NORMALIZED_0_1`이다.
- 파일 크기 키는 `sizeBytes`, 원본 URL은 `originalUrl`, 페이지 표시는 `imageUrl`이다. 오래된 예시의 `size`, `url`, `previewImageUrl`을 현재 계약으로 사용하지 않는다.
- 제목·학생·업무 저장 상태는 아직 이 DTO에 없다.

### 3.3 생성 작업

| 필드 | 타입·의미 |
| --- | --- |
| `id`, `worksheetId` | Long. 생성·학습지 ID |
| `status` | `PENDING / RUNNING / SUCCEEDED / FAILED` |
| `stage` | `QUEUED / OCR / AI_CORRECTION / COMPLETED` |
| `attempt` | Integer. 최초 시도 1 |
| `retryOfGenerationId` | Long 또는 null. 재시도 대상 이전 생성 |
| `settingsSnapshot` | 요청 당시 설정 JSON. 현재 영역·수준 객체의 배열 |
| `createdAt`, `startedAt`, `finishedAt` | LocalDateTime. 시작·종료 전에는 해당 값이 null일 수 있음 |
| `error` | `{code: String, message: String, retryable: Boolean}` 또는 null |

`GenerationResponse.error.code`는 워커의 비동기 실패 코드다. 4장의 HTTP 요청 오류 enum과 동일한 목록으로 제한되지 않는다.

### 3.4 AI 첨삭

`CorrectionListResponse`는 `{items: CorrectionResponse[], total: int}`이며 `CorrectionResponse`의 필드는 다음과 같다.

| 필드 | 타입·의미 |
| --- | --- |
| `id`, `generationId` | Long. 첨삭·생성 식별자 |
| `etag` | String. 현재 항목 버전 |
| `source` | String. AI 또는 고정 예제 FIXED 등 결과 출처 |
| `area`, `feedbackType` | 첨삭 영역, `CORRECTION / SUGGESTION` |
| `originalText` | String. 보존된 원문 |
| `aiInitialContent` | String. AI 최초 제안 |
| `content` | String. 현재 교사가 편집하는 내용 |
| `explanation` | String. 최초 제안의 교사용 해설 |
| `regions` | `RegionPayload[]`. 현재 원문 표시 좌표 |
| `editedByTeacher`, `explanationStale`, `isApplied` | Boolean. 교사 편집·기존 해설 주의·반영 여부 |
| `status` | `ACTIVE / DELETED` |
| `createdAt`, `updatedAt` | LocalDateTime |
| `action` | enum은 `REPLACE / GUIDANCE / REVIEW`. 일반 정상 카드는 REPLACE/GUIDANCE, REVIEW는 확인 필요 후보용. 과거 자료는 null 가능 |
| `issueKinds` | 검증된 오류 유형 코드 배열. 세부 값은 AI 계약별 정의 참조 |
| `anchor` | `CorrectionAnchorResponse` 또는 null. 다중 anchor를 하나로 억지 축소하지 않음 |
| `locationUncertain`, `requiresReview` | Boolean. 위치 불확실·교사 확인 필요 |
| `warnings` | `{code, field, message}[]`. 과거 자료에서는 null 가능 |
| `regionCoordinateUnit` | `NORMALIZED_0_1` |
| `contractVersion` | String 또는 null. 저장된 AI 계약 버전 |
| `anchors` | `CorrectionAnchorResponse[]`. v1.1 등의 다중 원문 연결 |
| `revisionScope` | `WORD / PHRASE / CLAUSE / SENTENCE / SENTENCE_GROUP` 또는 null |
| `aiInitialEdit`, `edit` | `{operation, text, position}` 또는 null. 최초/현재 표시 수정 동작 |
| `targetRegions` | 현재 `regions`와 같은 원문 좌표 배열 |

`CorrectionAnchorResponse`는 `{transcriptionId, lineId, occurrence, start, end}`다. `start/end`는 줄 안의 UTF-16 인덱스이며 end는 미포함이다. `edit.operation`은 `INSERT / DELETE / REPLACE / RESTRUCTURE`, 삽입의 `position`은 `BEFORE / AFTER`, 그 외에는 null일 수 있다.

최초 제안과 현재 수정문을 구분하고 `explanationStale`을 확인한다. `isApplied=false`를 항목 삭제와 동일하게 다루지 않는다. 좌표를 수정해도 보존된 원문 연결과 최초 결과는 별개다.

### 3.5 좌표·수기 첨삭

`RegionPayload`는 `{pageId, x, y, width, height, polygon}`이며 null 필드는 생략될 수 있다. x/y/width/height는 전체 보정 PNG 기준 0–1 값이다. width/height는 우하단 좌표가 아닌 가로·세로 크기다. polygon은 현재 이미지 연동에서 사용하지 않는 선택 필드다.

수기 첨삭 등록 예시:

```json
{
  "pageId": 10,
  "highlightType": "UNDERLINE",
  "color": "BLUE",
  "regions": [
    {"pageId": 10, "x": 0.12, "y": 0.34, "width": 0.25, "height": 0.045}
  ],
  "content": "주어와 동사의 수를 다시 확인해 보세요."
}
```

| DTO | 현재 필드 |
| --- | --- |
| `CommentResponse` | `id`, `etag`, `source`, `pageId`, `highlightType`, `color`, `regions`, `content`, `createdAt`, `updatedAt` |
| `CommentListResponse` | `items: CommentResponse[]`, `total: int` |

`CommentResponse.source`는 현재 항상 `TEACHER`이며 사용자 역할을 다시 판정한 값이 아니라 수기 코멘트 출처 표시다.

### 3.6 검토 통합 응답

| `ReviewResponse` 필드 | 구조·의미 |
| --- | --- |
| `worksheet` | `WorksheetDetailResponse` |
| `generation` | 최신 `GenerationResponse` 또는 null |
| `corrections` | 활성 `CorrectionResponse[]` |
| `comments` | 삭제되지 않은 `CommentResponse[]` |
| `reviewIssues` | 최초 검증의 확인 필요 항목. 아래 구조 참조 |
| `summary` | 페이지·영역별 검토용 카드 수 요약 |
| `aiResults` | 저장된 결과·전사 식별자와 모델/계약/프롬프트/검증 출처 |

- `summary[]`: `generationId`, `pageId`, `pageNumber`, `area`, `level`, `recommendedCount`, `maximumCount`, `validatedCount`, `displayedCount`, `omittedCount`.
- `validatedCount`는 최초 검증 당시 수, `displayedCount`는 현재 삭제되지 않은 카드 수다. 이 값을 학생 성장 리포트의 오류 수로 바로 간주하지 않는다.
- `aiResults[]`: `id`, `generationId`, `pageId`, `transcriptionId`, `source`, `metadata`.
- `metadata`: `model`, `contractVersion`, `promptVersion`, `validationVersion`. 복원할 때 현재 서버 설정값으로 덮어쓰는 정보가 아니다.
- `reviewIssues[]`: `resultId`, `generationId`, `transcriptionId`, `pageId`, `pageNumber`, `candidateIndex`, `original`, `resolvedAnchor`, `validBoxes`, `warnings`, `requiresReview`, `contractVersion`, `originalV11`, `anchors`, `resolvedAnchors`.
- `resolvedAnchor(s)`의 항목은 `{lineId, start, end}`다. `validBoxes`는 `[left, top, right, bottom]` 배열들이며 `RegionPayload`의 x/y/width/height와 혼동하지 않는다.
- `original`은 v1 형태 원본 후보: `action`, `feedback_type`, `area`, `issue_kinds`, `anchor`, `replacement`, `guidance`, `reason`, `location_uncertain`, `boxes`.
- `originalV11`은 v1.1 원본 후보: `action`, `feedback_type`, `area`, `issue_kinds`, `anchors`, `replacement`, `guidance`, `reason`, `location_uncertain`, `target_boxes`, `revision_scope`, `edit`.
- 원본 후보의 `anchor(s)`는 `{line_id, original, occurrence}` 형태다. 일반 응답의 camelCase 키와 달리 AI 원본 후보에는 snake_case가 보존된다. v1.1에서 단일 anchor 표현은 null일 수 있으므로 배열을 사용한다.
- 확인 필요 후보를 정상 승인 첨삭과 같은 것으로 취급하지 않는다. 일반 검토 API는 원본 AI HTTP 응답 전문을 반환하는 API가 아니다.

AI 계약의 세부 오류 유형과 표시 규칙은 [AI 공통 응답 계약 v1.1](<C:/study/CEOS/AUTEDI/autedi/docs/ai_test_docs/AI-공통-응답-계약-v1.1.md>) 및 [버전 선택·프런트 호환 규칙](<C:/study/CEOS/AUTEDI/autedi/docs/ai_test_docs/AI-v1.1-버전선택-프론트호환규칙.md>)과 함께 확인한다.

## 4. 현재 오류 코드

현재 `ErrorCode` 기준이다. 신규 학생·교사·저장 상태 오류 코드는 아직 정의되지 않았다. 아래 코드는 실제 적용 조건이 있는 API에서만 사용되며, 모든 API가 모든 검증을 수행한다는 뜻은 아니다.

| HTTP | 코드 | 의미 |
| --- | --- | --- |
| 400 | `COMMON_001` | 입력값·필드·형식 오류 |
| 405 | `COMMON_002` | 허용하지 않는 HTTP 메서드 |
| 500 | `COMMON_003` | 서버 내부 오류 |
| 412 | `COMMON_412` | 필수 If-Match 누락 또는 버전 불일치 |
| 429 | `COMMON_429` | QR 경로 요청 제한 초과 |
| 401 | `AUTH_001` | 인증 필요 |
| 403 | `AUTH_002` | 접근 권한 없음 |
| 401 | `AUTH_003` | 유효하지 않은 토큰 |
| 401 | `AUTH_004` | 만료된 토큰 |
| 401 | `AUTH_005` | 이메일·비밀번호 불일치 |
| 404 | `AUTH_006` | 사용자 없음 |
| 403 | `AUTH_007` | 비활성 계정 |
| 403 | `AUTH_008` | 잠금 계정 |
| 409 | `AUTH_009` | 이미 가입한 이메일 |
| 404 | `QR_001` | QR 세션 없음 |
| 410 | `QR_002` | QR 만료 |
| 409 | `QR_003` | QR 상태 전환 불가·이미 사용됨 |
| 403 | `QR_004` | QR 업로드 대상 불일치 |
| 403 | `QR_005` | 현재 사용자와 QR 세션 사용자 불일치 |
| 409 | `QR_006` | QR로 이미 사진 업로드 |
| 400 | `QR_007` | QR 업로드의 한 장 제한 위반 |
| 404 | `ACADEMY_001` | 기관 없음 |
| 403 | `ACADEMY_002` | 기관 활성 구독 아님 |
| 404 | `INVITATION_001` | 초대 토큰 없음 |
| 410 | `INVITATION_002` | 초대 만료 |
| 400 | `INVITATION_003` | 초대 사용 완료·취소 등 |
| 400 | `INVITATION_004` | 초대와 가입 이메일 불일치 |
| 404 | `WORKSHEET_001` | 학습지 없음·다른 기관 |
| 409 | `WORKSHEET_002` | 현재 AI 처리 상태에서 작업 불가 |
| 400 | `FILE_001` | 빈 파일 |
| 415 | `FILE_002` | 지원하지 않는 형식·PDF 업로드 |
| 500 | `FILE_003` | 파일 저장 실패 |
| 400 | `FILE_004` | 이미지·EXIF 해석 실패 |
| 413 | `FILE_005` | 파일·보정 이미지·픽셀·요청 크기 제한 초과 |
| 404 | `FILE_006` | 파일 없음 |
| 409 | `FILE_007` | 기존 이미지의 보정 PNG 없음 |
| 409 | `GENERATION_001` | 생성 진행 중 |
| 409 | `GENERATION_002` | 이미 AI 생성 완료 |
| 404 | `GENERATION_003` | 생성 작업 없음 |
| 400 | `GENERATION_004` | 첨삭 영역 미선택 |
| 409 | `GENERATION_005` | 재시도 불가·파일/설정 변경 |
| 404 | `GENERATION_006` | 생성 결과 기록 없음 |
| 409 | `GENERATION_007` | 다른 요청에서 사용한 멱등키 |
| 503 | `GENERATION_008` | AI 계약 생성 경로 준비 안 됨 |
| 404 | `TRANSCRIPTION_001` | 전사문 없음 |
| 404 | `CORRECTION_001` | 첨삭 항목 없음 |
| 409 | `CORRECTION_002` | 삭제 항목 수정 불가 |
| 404 | `COMMENT_001` | 코멘트 없음·삭제됨 |
| 400 | `COMMENT_002` | 코멘트 페이지와 좌표 페이지 불일치 |
| 404 | `PAGE_001` | 페이지 없음 |

JWT 필터에서 유효한 인증을 만들지 못한 일반 보호 API 요청은 인증 진입점의 `AUTH_001`이 될 수 있다. 모든 만료 Access Token 요청이 반드시 `AUTH_004`를 반환한다고 단정하지 않는다.

## 5. 신규 엔드포인트 설계안

**아래 22개는 현재 미구현이다.** 앞선 Notion용 목록을 구체화한 경로·필드 제안이며 실제 HTTP 성공 상태·응답 JSON·검증 한계·신규 오류 코드는 개발·프런트 합의 후 확정한다. 아래 권한은 목표 정책이다. 페이지네이션은 `page/size`를 제안하되 시작 번호·기본값·응답 구조는 미정이다.

### 5.1 학생 관리 — 신규안 4개 / F08·F09

권한: 원장·선생님, 같은 기관.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 학생 목록 조회 | `/students` | Query: `keyword`, `grade`, `className`, `page`, `size` | 이름 부분·전체 검색, 학년·반 필터. 학생 ID·이름·학년·반 반환 |
| POST | 학생 등록 | `/students` | Body: `name*`, `grade*`, `className`, `studentPhone`, `guardianPhone` | 이름·학년 필수, 반·연락처 선택. 입력 연락처 형식 검증 후 기관에 등록 |
| GET | 학생 상세 조회 | `/students/{studentId}` | Path: `studentId` | 이름·학년·반·학생 연락처·보호자 연락처 조회 |
| PATCH | 학생 기본 정보 수정 | `/students/{studentId}` | Path: `studentId`; Body: `grade`, `className`, `studentPhone`, `guardianPhone` | 허용된 기본 정보 변경. 학생 이름 수정은 미정이므로 요청 필드에서 제외 |

학년 허용값·반 형식·중복 학생·미배정 필터·연락처 열람 정책은 미정이다. 반 미배정의 화면 표시 `-`를 DB·API의 실제 반 이름으로 저장할 필요는 없다. 선택 필드를 지울 때 null·빈 문자열의 의미도 정해야 한다.

### 5.2 학생 메모 — 신규안 2개 / F09

권한: 원장·선생님, 같은 기관.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 학생 메모 목록 조회 | `/students/{studentId}/memos` | Path: `studentId`; Query: `page`, `size` | 메모 ID·내용·작성자 이름·작성일을 최신순 조회 |
| POST | 학생 메모 등록 | `/students/{studentId}/memos` | Path: `studentId`; Body: `content*` | 빈 내용 금지. 작성자·작성 시각은 서버에서 기록 |

기존 학습지 `comments`와 다른 자원이다. 메모 수정·삭제는 7장의 조건부 항목이다.

### 5.3 학생 성장 리포트 — 신규안 2개 / F09

권한: 원장·선생님, 같은 기관.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 학생 성장 그래프 조회 | `/students/{studentId}/growth-report` | Path: `studentId`; Query: `period`, `area` | 완료 결과물의 영역별 시계열. 완료일·제목·오류 수·100단어당 오류 수·대표 수정·학습지 ID 제공 |
| GET | 학생 AI 인사이트 조회 | `/students/{studentId}/insights` | Path: `studentId` | 완료 이력의 반복 오류·우선 학습 영역·코칭 포인트·데이터 부족 상태 조회 |

- period는 `1M / 3M / 6M / ALL`, area는 네 표준 첨삭 영역 값을 재사용하는 안이다. 기본 기간·필수 여부·미분석 영역 표현은 미정이다.
- 최종 저장한 완료 결과물만 집계하며 삭제·임시저장은 제외한다. 날짜 기준은 업무 최종 저장 완료일이다.
- 정규화 공식은 `(영역 오류 수 ÷ 인식된 총 단어 수) × 100`. 오류·단어 수 정의와 0단어 처리는 미정이다.
- 데이터 없음과 실제 오류 0을 구분한다. 점 선택에 필요한 결과물 정보를 응답에 포함하고 별도 점 클릭 API는 만들지 않는 안이다.
- 인사이트 생성·갱신 방식, 최소 완료 건수, 근거 표시·복사 상세는 미정이다. 근거 없는 임의 인사이트를 만들지 않는다.

### 5.4 학생 결과물 목록 — 신규안 1개 / F09

권한: 원장·선생님, 같은 기관. 다른 교사의 임시저장 열람·이어쓰기 범위는 별도 미정.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 학생 첨삭 결과물 목록 조회 | `/students/{studentId}/worksheets` | Path: `studentId`; Query: `keyword`, `from`, `to`, `page`, `size` | 임시저장·완료의 ID·제목·저장일·첨삭 교사·저장 상태를 최신순 조회. 제목 검색·기간 필터, 삭제 제외 |

from/to는 `YYYY-MM-DD` 형태를 제안한다. 저장일 의미와 경계 포함 여부는 미정이다. 단건 팝업은 기존 학습지 상세·검토 API를 확장해 재사용하고 PDF는 7장을 참조한다.

### 5.5 선생님 관리 — 신규안 5개 / F10·F11

권한: **원장 전용**, 같은 기관.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 선생님 목록 조회 | `/teachers` | Query: `keyword`, `status`, `page`, `size` | 이름·이메일 검색, 활동 중·초대 대기·퇴사 필터. 이번 달 첨삭 수 포함 |
| GET | 선생님 상세 조회 | `/teachers/{teacherId}` | Path: `teacherId` | 이름·계정 이메일·연락처·입사일·상태 조회 |
| PATCH | 선생님 정보 수정 | `/teachers/{teacherId}` | Path: `teacherId`; Body: `name`, `phoneNumber`, `hireDate` | 이름·연락처·입사일 변경. 이메일 변경은 미정 |
| GET | 선생님 첨삭 이력 조회 | `/teachers/{teacherId}/worksheets` | Path: `teacherId`; Query: `keyword`, `month`, `page`, `size` | 학생 이름·결과물 제목 검색, 연·월 필터. 임시저장·완료 포함, 삭제 제외 |
| POST | 선생님 퇴사 처리 | `/teachers/{teacherId}/resign` | Path: `teacherId`; Body 없음 | 퇴사 상태로 변경하고 로그인 차단. 결과물·담당자 이력 보존. 기존 세션 차단 방식은 별도 설계 |

- month는 `YYYY-MM`, hireDate는 `YYYY-MM-DD` 형식을 제안한다. 기본 조회 월·전체 기간 제공은 미정이다.
- 월별 응답 필드명 `monthlyCorrectionCount`는 제안이다. D7에 따라 임시저장+완료를 포함하고 삭제 작업은 제외한다. 날짜·시간대·집계 담당자·상태 전환 중복 계산은 미정이다.
- 초대 대기는 User가 아닌 Invitation이므로 가입 전 행에 teacherId가 있다고 가정하지 않는다. invitationId·가입 여부를 구분하는 응답과 상세 진입 처리가 필요하다.
- 교사 목록의 원장 포함, 입사일 입력 방식, 퇴사 코드명·퇴사 취소·재입사는 미정이다.

### 5.6 계정·기관 정보 변경 — 신규안 3개 / F12

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| PATCH | 내 이름 수정 | `/users/me` | Body: `name*` | 원장·선생님 본인 이름 수정 |
| PATCH | 내 비밀번호 변경 | `/users/me/password` | Body: `currentPassword*`, `newPassword*` | 원장·선생님 현재 비밀번호 확인 후 새 해시 저장. 8–20자·영문·숫자·특수문자 정책 적용 |
| PATCH | 소속 기관명 수정 | `/academies/me` | Body: `name*` | 원장만 소속 기관명 변경. 선생님 변경 거부, 공통 화면 표시 연계 |

계정 이메일 수정, 비밀번호 변경 후 다른 기기 세션 종료는 미정이다. 대상 기관·사용자 ID를 클라이언트가 임의 지정하기보다 로그인 정보를 사용하는 안이다.

### 5.7 제목·학생 지정, 저장·삭제 — 신규안 4개 / D1–D6·F05·F07

권한: 원장·선생님. 삭제는 업로더 본인만 가능하며 다른 교사 임시저장 이어쓰기의 허용 범위는 미정이다.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| PATCH | 학습지 제목·학생 지정 | `/worksheets/{worksheetId}` | Path: `worksheetId`; Body: `title*`, `studentId*` | 업로드 후 첨삭 설정 단계에서 지정. 학생의 기관 검증, 완료 작업 변경 거부 |
| POST | 첨삭 임시저장 | `/worksheets/{worksheetId}/save-draft` | Path: `worksheetId`; Body 없음 — 아래 제안 전제 | 현재 편집 결과를 임시저장으로 기록. 홈·학생 결과물에 반영, 편집 가능 |
| POST | 첨삭 최종 저장 | `/worksheets/{worksheetId}/complete` | Path: `worksheetId`; Body 없음 — 아래 제안 전제 | 필수 데이터 검증 후 완료 상태·최종 저장일 기록. 읽기 전용, 성장 리포트 집계 대상 |
| DELETE | 임시저장 작업 삭제 | `/worksheets/{worksheetId}` | Path: `worksheetId`; Body 없음 | 업로더 본인의 임시저장에 deleted_at 기록. 데이터·이미지 보존, 모든 조회·집계에서 제외 |

- Body 없음은 개별 편집 API로 먼저 서버에 반영한 상태를 저장한다는 제안이다. 저장 시 편집 내용 전체를 전송할지, 저장본을 어떤 일관성으로 보존할지는 미정이다.
- title/studentId를 별도 PATCH 대신 기존 settings 요청에 합치는 방식도 후보이며 아직 확정되지 않았다.
- 기존 `WorksheetStatus`와 별도로 업무 저장 상태가 필요하다. 제안 필드는 `saveStatus`, `savedAt`, `completedAt` 등이며 코드명과 초기 미저장 상태는 미정이다.
- 원장은 다른 사용자의 임시저장을 삭제할 수 없다. 원장 자신이 업로드한 작업의 취급은 동일한 업로더 조건을 따른다.
- 이번 삭제 기능은 임시저장만 허용한다. 완료 결과물의 별도 삭제 정책은 미정이다.
- 생성 중 삭제 허용, 저장 필수 데이터, 반복 저장·삭제 응답, 완료와 편집의 동시 요청 처리는 추가 결정이 필요하다.

### 5.8 홈 최근 작업 — 신규안 1개 / F04

권한: 원장·선생님. 개인·기관 목록 범위와 다른 교사 임시저장 접근은 미정이다.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 최근 저장 작업 조회 | `/worksheets/recent` | Query: `page`, `size` | 임시저장·완료를 최신 수정일 순으로 조회. ID·제목·학생·저장 상태·마지막 작업일·담당 교사 제공. 삭제 제외 |

임시저장은 이어쓰기, 완료는 읽기 전용 결과물로 연결한다. 업로드만 하고 한 번도 저장하지 않은 작업의 노출 여부는 별도 미정이다.

## 6. 기존 API 변경 설계안

이 장은 현재 2장에 선언된 경로의 **향후 변경점**이다. 새 필드·검증이 이미 적용되었다고 해석하지 않는다.

| 기존 API | 현재 동작 | 변경 목표·남은 결정 |
| --- | --- | --- |
| `POST /auth/signup` | 비밀번호 최소 8자만 검사 | 공통 정책인 최대 20자와 영문·숫자·특수문자 조합 검증 추가 |
| `POST /auth/logout` | 선택 Refresh Token 폐기 | 현재 세션 종료의 범위와 Access Token 처리 정의. 필요 시 요청 필수성·서버 폐기 방식 보완 |
| `POST /invitations` | email만 입력 | 목표 요청 `name*`, `email*`. 초대 이름 저장·응답·가입 연계, 메일 전송 실패와 초대 대기 노출 정책 정의 |
| `GET /worksheets/{worksheetId}` | AI 상태·파일·설정 조회 | 제목·학생·업무 저장 상태·저장 시각 추가. 필드명은 제안 |
| `GET /worksheets/{worksheetId}/review` | READY 검토 조회 | 저장 결과물 조회로 연계하고 제목·학생·업무 저장 상태 제공 |
| 기존 학습지·하위 자원 조회 | 기관 검증, 학습지 삭제 상태 없음 | deleted_at 작업의 단건·이미지·검토·생성 상태·하위 첨삭/코멘트 접근도 제외하도록 연결 조회 점검 |
| 기존 학습지·첨삭·코멘트 쓰기 | AI 상태·항목 버전 검사 | 완료 결과물의 쓰기를 서버에서 거부. 완료/삭제/편집이 겹칠 때도 상태 정책 보장 |

### 6.1 완료 결과물 쓰기 차단 검토 대상

다음은 현재 쓰기 API에 D1을 적용할 대상 목록이다. 원문에서도 구체적인 차단 범위는 구현 시 확정 대상으로 표시했다.

| 메서드 | URL | 목표 |
| --- | --- | --- |
| PATCH | `/worksheets/{worksheetId}/corrections/{correctionId}/apply` | 완료 작업의 반영 여부 변경 거부 |
| PATCH | `/worksheets/{worksheetId}/corrections/{correctionId}` | 완료 작업의 첨삭 수정 거부 |
| DELETE | `/worksheets/{worksheetId}/corrections/{correctionId}` | 완료 작업의 첨삭 항목 삭제 거부 |
| POST | `/worksheets/{worksheetId}/corrections/{correctionId}/restore` | 완료 작업의 첨삭 항목 복원 거부 |
| POST | `/worksheets/{worksheetId}/comments` | 완료 작업의 수기 첨삭 추가 거부 |
| PATCH | `/worksheets/{worksheetId}/comments/{commentId}` | 완료 작업의 수기 첨삭 수정 거부 |
| DELETE | `/worksheets/{worksheetId}/comments/{commentId}` | 완료 작업의 수기 첨삭 삭제 거부 |
| PUT | `/worksheets/{worksheetId}/file` | 기존 AI 상태 조건과 업무 완료 조건을 함께 정리 |
| PUT | `/worksheets/{worksheetId}/settings` | 기존 AI 상태 조건과 업무 완료 조건을 함께 정리 |

AI 생성·재시도도 완료 상태와의 일관성을 검토하되, 원문에 AI 재생성 차단 세부 범위가 확인 필요로 남아 있다. 기존 READY 생성 차단을 업무 완료 정책 구현으로 간주하지 않는다. 완료 조회는 계속 허용하며 차단 HTTP 상태·에러 코드는 미정이다.

### 6.2 퇴사·세션·구독

- 현재 로그인·Refresh에는 비활성 계정 차단이 있으나 퇴사 API 자체는 없다. 기존 JWT 인증은 모든 요청에서 DB 사용자 상태·폐기 여부를 다시 확인하지 않는다.
- 사용자별 Refresh Token 전체 폐기 메서드는 재사용할 수 있지만 Access Token 즉시 차단을 대신하지 않는다. 퇴사 직후 기존 요청까지 차단하는 방식은 설계가 필요하다.
- 업로드·생성·초대 등 일부 경로는 활성 구독을 검사한다. 구독 만료 기관의 모든 쓰기 제한 정책은 미정이며 기존 구현 전체에 일관되게 적용됐다고 단정하지 않는다.

## 7. 조건부 엔드포인트 설계안

아래 6개는 기본 신규안과 구분한다. 메서드·URL·필드 역시 제안이고 성공 응답·에러 코드는 미정이다.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 학생 필터 선택지 조회 | `/students/filter-options` | 없음 | 같은 기관의 학년·반 선택지. 목록 응답에 포함할지 별도 조회할지 결정 필요. 원장·선생님 |
| PATCH | 학생 메모 수정 | `/students/{studentId}/memos/{memoId}` | Path: `studentId`, `memoId`; Body: `content*` | 수정 허용·작성자 권한·감사 이력 확정 후 추가 |
| DELETE | 학생 메모 삭제 | `/students/{studentId}/memos/{memoId}` | Path: `studentId`, `memoId` | 삭제 허용 범위·감사 이력 확정 후 추가 |
| POST | 초대 재발송 | `/invitations/{invitationId}/resend` | Path: `invitationId` | 원장 전용. 재발송 횟수·대상·만료 처리 확정 후 추가. 기존 생성 API의 재초대와 통합 가능 |
| DELETE | 초대 취소 | `/invitations/{invitationId}` | Path: `invitationId` | 원장 전용. 미가입 초대의 토큰 무효화. 취소 가능 상태·처리 방식 확정 필요 |
| GET | 첨삭 PDF 다운로드 | `/worksheets/{worksheetId}/pdf` | Path: `worksheetId` | 서버가 PDF를 생성·제공할 때 추가. 현재 저장 버전의 원본·승인된 AI·수기 첨삭 포함, 삭제 항목 제외 |

PDF 기능 자체는 요구사항이다. 서버 API를 둘지 프런트에서 생성할지와 동기/비동기 생성·실패 재시도 방식이 미정이다. 학생 선택 이동·탭 전환·팝업 열기/닫기·클립보드 복사는 별도 API로 늘리지 않는다.

## 8. 확정 정책과 미정 사항

### 8.1 D1–D7 반영 기준

| ID | 정책 | 명세 적용 |
| --- | --- | --- |
| D1 | 완료 결과물은 수정 불가 | 완료 읽기 전용, 서버 쓰기 차단 필요 |
| D2 | 임시저장 작업 삭제 가능 | 신규 삭제 기능 필요 |
| D3 | 삭제는 soft delete | deleted_at 기록, 연결 데이터 보존 |
| D4 | 제목·학생 지정과 임시/최종 저장 포함 | F05·F07 연관 백엔드도 이번 범위 |
| D5 | 완료 → 임시저장 되돌리기 없음 | 되돌리기 API를 추가하지 않는 방향. 원문에 개발 해석·확인 필요 주석이 남아 있으므로 해소된 것으로 기록하지 않음 |
| D6 | 임시저장 삭제는 업로더 본인만 | 원장 역할로 타인 작업 삭제 허용하지 않음 |
| D7 | 월별 첨삭 수에 임시저장 포함 | 임시저장+완료, 삭제 제외. 세부 집계 기준 미정 |

전달용 문서는 이번 임시저장 삭제 API의 완료 상태 거부와 완료 결과물 삭제 정책 자체의 미정을 함께 적고 있다. 이번 삭제는 임시저장 전용으로 설계하고 완료 결과물의 별도 삭제 기능은 확정하지 않는다.

### 8.2 상태별 목표 동작

| 구분 | 임시저장 | 완료 |
| --- | --- | --- |
| 편집 | 가능. 타인 작업 이어쓰기 권한은 미정 | 불가 |
| 이번 삭제 API | 업로더 본인만 가능 | 거부. 별도 완료 삭제 정책은 미정 |
| 홈·학생 결과물 | 포함 | 포함 |
| 교사 이력 | 포함 | 포함 |
| 교사 월별 첨삭 수 | 포함 | 포함 |
| 학생 성장 리포트 | 제외 | 포함 |
| 삭제된 작업 | 조회·집계 제외 | 조회·집계 제외 원칙 |

기존 AI 상태 DRAFT/PROCESSING/READY/FAILED와 이 업무 저장 상태는 별개다. QR의 COMPLETED와 생성 stage의 COMPLETED도 각각 다른 상태다.

### 8.3 추가 결정 목록

| 구분 | 미정 내용 |
| --- | --- |
| 신규 API 계약 | 메서드·경로·필드명·타입·페이지네이션·기본값·성공 상태·오류 코드 |
| 저장 | 초기 미저장 상태·목록 노출, 최종 필수 검증, 저장 요청 구조·버전 일관성, 자동 저장·동시 편집·보관 기간 |
| 삭제 | 생성 중 삭제, 완료 별도 삭제, 반복 삭제 응답, 삭제 자원 접근 응답, 파일 보관 기간 |
| 접근 | 타 교사 임시저장 열람·이어쓰기, 최근 작업의 개인/기관 범위 |
| 월별 집계 | 저장일 등 기준일, 월 시간대, 업로더/마지막 저장자, 임시→완료 상태 전환 중복 계산 |
| 학생 | 학년 범위, 반 형식·미배정 필터, 중복 등록, 이름 수정, 연락처 열람·마스킹·보관/파기 |
| 메모 | 수정·삭제 범위와 작성자 권한·감사 이력 |
| 성장 리포트 | 오류·단어 수 정의·0단어, 인사이트 최소 완료 건수·갱신·근거 표시·복사 상세 |
| 교사 | 원장 목록 포함, 입사일 입력, 이메일 수정, 이력 전체 기간, 퇴사 코드·취소·재입사·기존 요청 차단 |
| 초대 | 만료·재발송·취소·중복 이메일 정책, 초대 이름과 가입 이름, 메일 실패와 목록 상태 |
| 계정·기관 | 이메일 수정, 비밀번호 변경 시 다른 세션 종료, 로그아웃의 Access Token, 구독 만료 쓰기 제한 |
| 출력 | PDF 생성 주체·동기/비동기·저장 버전 일치·실패 재시도 |

등록 연락처의 선택 여부는 v2의 8.2.5·8.2.6에서 확인된다. 전달용 문서의 연락처 필수 여부 확인 필요 문구를 이유로 임의 필수화하지 않는다.

## 9. 연동 흐름과 검증 범위

### 9.1 현재 가능한 첨삭 흐름

1. 초대 검증 → 회원가입 → 로그인, 또는 기존 계정 로그인.
2. 일반 업로드 또는 QR 생성 → 스캔·승인·토큰 발급 / 같은 사용자 link → 업로드·교체.
3. 학습지·보정 이미지 조회 → 영역별 설정 저장.
4. 생성 요청 → 생성 상태 폴링 또는 SSE 알림 후 상태 조회.
5. READY에서 검토 조회 → AI 항목 수정·반영 여부·삭제·복원, 수기 첨삭 등록·수정·삭제.

현재 이 다음의 업무 임시저장·최종 저장·학생 이력·성장 리포트·교사 관리 연결은 아직 없다. 개별 첨삭 DB 변경이 저장된다는 사실을 F07의 명시적 저장 기능 완료로 보지 않는다.

### 9.2 구현 후 연결할 흐름

학생 등록·조회 → 업로드 → 제목·학생·영역 설정 → AI 생성·검토 → 임시/최종 저장 → 홈·학생·교사 이력 조회. 완료 결과물만 성장 리포트로 집계하며 삭제 작업은 모든 경로에서 제외한다.

### 9.3 확인한 소스

| 영역 | 소스 |
| --- | --- |
| 인증 | [AuthController](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/auth/controller/AuthController.java>), [AuthService](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/auth/service/AuthService.java>) |
| 초대 | [InvitationController](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/invitation/controller/InvitationController.java>), [InvitationService](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/invitation/service/InvitationService.java>) |
| 본인 정보 | [UserController](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/user/controller/UserController.java>) |
| QR | [QrSessionController](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/auth/controller/QrSessionController.java>), [QrSessionService](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/auth/service/QrSessionService.java>) |
| 학습지·설정 | [WorksheetController](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/worksheet/controller/WorksheetController.java>), [WorksheetService](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/worksheet/service/WorksheetService.java>) |
| 생성 | [WorksheetGenerationController](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/generation/controller/WorksheetGenerationController.java>), [WorksheetGenerationService](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/generation/service/WorksheetGenerationService.java>) |
| AI 첨삭 | [CorrectionController](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/correction/controller/CorrectionController.java>), [CorrectionService](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/correction/service/CorrectionService.java>) |
| 수기 첨삭 | [CommentController](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/comment/controller/CommentController.java>), [CommentService](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/domain/comment/service/CommentService.java>) |
| SSE | [EventController](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/global/sse/EventController.java>), [SseEventPublisher](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/global/sse/SseEventPublisher.java>) |
| 공통 | [ApiResponse](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/global/response/ApiResponse.java>), [ErrorCode](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/global/exception/ErrorCode.java>), [SecurityConfig](<C:/study/CEOS/AUTEDI/autedi/src/main/java/com/ceos24/autedi/global/config/SecurityConfig.java>) |

문서 작성 시 모든 Controller의 메서드·경로를 2장과 대조하고 DTO·주요 Service 검증을 확인했다. 런타임 성공이나 배포 버전 일치까지 검증한 문서는 아니다. 코드·정책이 변경되면 현재 명세와 변경안을 함께 갱신해야 한다.
