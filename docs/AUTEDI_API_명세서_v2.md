# AUTEDI API 명세서 v2

- 작성일: 2026-11-23
- 문서 상태: 현재 구현 명세 + 신규·변경 설계안 통합 초안
- 코드 기준: 로컬 브랜치 `docs/92-v2-api-doc`, 커밋 `39ad0ae`
- B2 추가 반영: 2026-11-26. 학생 등록·상세 조회 2개 API와 중복 후보 확인·동명이인 확인 후 등록 계약을 5.1에 반영했다. 나머지 항목의 최초 코드 확인 기준은 위와 같다.
- B3 추가 반영: 2026-11-26. 학생 목록·검색·필터·페이지네이션 및 기본 정보 부분 수정 2개 API를 구현하여 학생 API 총 4개의 계약을 5.1에 반영했다.
- B4 추가 반영: 2026-11-26. 학습지 제목·학생 지정 PATCH 1개를 5.7에 구현하고, 기존 상세·검토 응답에 제목·학생·업무 저장 상태·저장 시각을 추가했다.
- B5 추가 반영: 2026-11-27. 임시저장과 마지막 저장본 조회·명시적 이어쓰기 API 3개를 5.7에 구현했다. READY·업로더 본인에게만 허용한다.
- B6 추가 반영: 2026-11-27. 최종 저장 API, 임시저장 없는 직접 완료, 완료 저장본 조회 및 기존 편집·파일 교체·설정·AI 생성의 공통 완료/삭제 차단을 5.7에 반영했다.
- B7 추가 반영: 2026-11-27. 학생별 저장 결과물 목록과 홈 최근 작업 API를 5.4·5.8에 구현했다. 사용자 확정에 따라 임시저장 목록·저장본은 업로더만, 완료 목록·저장본은 같은 기관 원장·선생님에게 공유한다. 쓰기 권한은 유지한다.
- B8 추가 반영: 2026-11-27. 성장 그래프 조회 1개를 5.3에 구현했다. 최종 저장본의 교사 확정 오류 피드백을 집계하며, 교사 코멘트 분류를 기존 작성·수정 API에 추가했다. 저장 JSON은 신규 v2를 기록하고 기존 v1도 조회·복원한다. AI 인사이트는 후속 B10이다.
- B10·B11 추가 반영: 2026-11-28. 사용자가 승인한 수동 인사이트 MVP를 5.3에 구현했다. 저장 결과 조회·수동 생성 2개 API, 학생별 최신 결과 보관, 동일 입력 재사용, 입력량·응답량 제한 및 첨삭과 공유하는 실행 한도를 추가했다. 자동 생성·인사이트 대기열은 없다.
- A3 추가 반영: 2026-11-27. 삭제·완료 학습지의 공통 접근 정책(삭제는 `404 / WORKSHEET_001`, 완료 쓰기는 `409 / WORKSHEET_003`), 임시저장 학습지 삭제 `DELETE /worksheets/{worksheetId}`를 구현해 5.7·6장·8장에 반영했다. 검증은 H2 통합 테스트이며 운영 DB는 포함하지 않는다.
- 기능 기준: [AUTEDI 기능명세서 v2](<C:/study/CEOS/AUTEDI/autedi/docs/AUTEDI 기능명세서 v2.md>)
- 최초 정리 범위: 기존 API 37개, 앞선 논의의 신규 API 설계안 22개, 조건부 API 설계안 6개. 신규안 중 학생 관리 4개·제목 및 학생 지정 1개·임시저장 1개·최종 저장 1개·학생 결과물 1개·홈 최근 작업 1개·성장 그래프 1개는 B2–B8 구현 계약을 반영했다. B5에서 저장본 조회·이어쓰기 2개를 별도 추가했다. 최초 설계안 개수를 현재 구현 개수로 해석하지 않는다.
- 확인 방식: 최초 문서는 Controller·Service·DTO·공통 응답·보안 설정의 정적 대조 기준이다. B2–B8 API는 로컬 H2 DB와 실제 JWT 필터를 사용하는 HTTP 통합 테스트로 검증했다. B8 반영 후 전체 97개 스위트·2,292개 테스트와 포맷 검사가 통과했다. 운영 DB·배포 환경 확인은 포함하지 않는다.
- A4 추가 반영: 2026-11-27. 학생 메모 목록 조회·등록 2개 API를 5.2에 구현으로 반영했다. 검증은 로컬 H2 HTTP 통합 테스트이며 운영 DB는 포함하지 않는다. 같은 날 선생님 목록의 월별 첨삭 수(`monthlyCorrectionCount`)와 선생님 첨삭 이력 조회 `GET /teachers/{teacherId}/worksheets`를 5.5에 구현으로 반영하고 집계 기준을 확정했다.

> 2장은 최초 정리한 기존 API의 코드 계약에 B4 상세·검토 응답 및 B8 코멘트 분류 확장을 반영했다. 5.1의 학생 API, 5.3의 성장 그래프·수동 인사이트, 5.4·5.8의 학생·홈 저장 목록과 5.7의 제목·학생 지정·임시/최종 저장·저장본 조회·이어쓰기는 B2–B11에서 구현했으며, 나머지 신규안·변경안은 각 항목의 설계 상태를 따른다. 기획 정책의 확정과 API 계약의 확정을 구분한다.

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

현재 기관 검증을 업로더 소유권 검사와 동일하게 해석하면 안 된다. 일반 학습지 조회·편집은 기관 기준이며, QR 대상 지정·QR 업로드에는 추가 소유권 검사가 있다. 임시저장 삭제는 D6에 따라 별도의 업로더 본인 검사를 적용한다(5.7).

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
| 학습지 제목·학생 지정 (B4) | 선택. 지정하면 현재 학습지 ETag와 비교. `*` 허용. 빈 값·불일치는 `412 / COMMON_412` |
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
- 주요 오류: `QR_001`–`QR_007`, `AUTH_002`, `AUTH_007`, `WORKSHEET_001`, `WORKSHEET_003`(사진 교체용 QR 생성 시 완료 학습지).

### 2.5 학습지 파일 — 5개 / F05

권한: 인증된 원장·선생님. QR 업로드에는 추가 대상 검증 적용.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| POST | 학습지 이미지 업로드 | `/worksheets` | Form: `files[]`, `file[]`, `images[]` 중 유효 이미지 1개 이상 | 한 학습지와 페이지 생성. `201`, `WorksheetDetailResponse`, `Location`, `ETag` |
| GET | 학습지 상세 조회 | `/worksheets/{worksheetId}` | Path: `worksheetId` | 파일·페이지·설정·AI 처리 상태·최근 생성 ID와 B4 제목·학생·업무 저장 정보 조회. `200`, `WorksheetDetailResponse`, `ETag`. 삭제 학습지 제외 |
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
- B4에서 상세 응답에 제목·학생 요약·업무 저장 상태·저장 시각을 추가했다. 기존 업로드는 제목·학생 없이도 가능하고 초기 saveStatus는 UNSAVED다.
- 주요 오류: `WORKSHEET_001`, `WORKSHEET_002`, `WORKSHEET_003`(파일 교체), `COMMON_412`, `FILE_001`–`FILE_007`, `PAGE_001`, `QR_002`, `QR_004`, `QR_006`, `QR_007`.

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

영역 표준값은 `GRAMMAR_SPELLING`, `VOCABULARY_EXPRESSION`, `SENTENCE_STRUCTURE`, `CONTENT_SUMMARY`, 수준은 `STANDARD / ADVANCED`다. 제목·학생은 현재 이 요청 DTO에 없다. 주요 오류: `COMMON_001`, `COMMON_412`, `WORKSHEET_001`, `WORKSHEET_002`, `WORKSHEET_003`(설정 변경).

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
- 주요 오류: `GENERATION_001`–`GENERATION_008`, `WORKSHEET_001`, `WORKSHEET_003`(생성·재시도), `COMMON_001`, `AUTH_002`, `ACADEMY_002`.

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
- 주요 오류: `WORKSHEET_001`, `WORKSHEET_002`, `CORRECTION_001`, `CORRECTION_002`, `WORKSHEET_003`(쓰기 4개), `COMMON_001`, `COMMON_412`.

### 2.9 선생님 수기 첨삭 — 5개 / F07

권한: 인증된 같은 기관 사용자. 현재 학습지 `READY` 필요.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 수기 첨삭 목록 조회 | `/worksheets/{worksheetId}/comments` | Path: `worksheetId`; Query: `pageId` | 삭제되지 않은 코멘트·강조 표시 조회. `200`, `CommentListResponse` |
| GET | 수기 첨삭 단건 조회 | `/worksheets/{worksheetId}/comments/{commentId}` | Path: `worksheetId`, `commentId` | 코멘트 단건 조회. `200`, `CommentResponse`, `ETag` |
| POST | 수기 첨삭 등록 | `/worksheets/{worksheetId}/comments` | Path: `worksheetId`; Body: `pageId*`, `highlightType*`, `regions*`, `content*`, `color`, `classification` | 교사 코멘트·강조 영역·선택 분류 생성. `201`, `CommentResponse`, `Location`, `ETag` |
| PATCH | 수기 첨삭 수정 | `/worksheets/{worksheetId}/comments/{commentId}` | Path: `worksheetId`, `commentId`; Body: `content*`, `color`, `classification`; Header: `If-Match*` | 내용·색상·분류 수정. 좌표·강조 유형 수정 API는 아님. `200`, `CommentResponse`, `ETag` |
| DELETE | 수기 첨삭 논리 삭제 | `/worksheets/{worksheetId}/comments/{commentId}` | Path: `worksheetId`, `commentId`; Header: `If-Match*` | 코멘트·연결 강조 표시를 논리 삭제. `204`, 본문 없음 |

- `highlightType`: `HIGHLIGHT`, `UNDERLINE`, `STRIKETHROUGH`, `ARROW`, `TEXTBOX`, `CARET`.
- `color`: `RED`, `BLUE`, `YELLOW`, `GREEN`. 생성 시 생략하면 `BLUE`, 수정 시 생략하면 기존 색상 유지.
- B8 `classification`은 코멘트 응답에도 포함한다. 작성 생략은 GENERAL, 수정 생략은 유지하며 종류·영역·오류 태그·보충 대상의 전체 계약은 5.3을 따른다. 잘못된 보충 대상은 `CORRECTION_001`이다.
- `regions`는 비어 있으면 안 된다. 대상 페이지는 해당 학습지에 있어야 하며 region의 pageId를 주면 대상 페이지와 같아야 한다. 현재 Service가 모든 좌표 수치에 Correction과 동일한 검증을 한다고 해석하지 않는다.
- `content`는 공백 불가이며 저장 시 앞뒤 공백을 제거한다. 삭제한 코멘트는 조회 시 찾을 수 없음으로 처리한다.
- 학생 프로필 메모와 다른 자원이다. 기존 수기 첨삭 목록 역시 페이지네이션이 아닌 `{items, total}`이다.
- 주요 오류: `COMMENT_001`, `COMMENT_002`, `PAGE_001`, `WORKSHEET_001`, `WORKSHEET_002`, `WORKSHEET_003`(쓰기 3개), `COMMON_001`, `COMMON_412`.

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
| `WorksheetDetailResponse` | `id`, `etag`, `status`, `title`, `student`, `saveStatus`, `firstSavedAt`, `lastSavedAt`, `completedAt`, `file`, `pages`, `settings`, `latestGenerationId`, `createdAt`, `updatedAt` |
| `student` (B4 학생 요약) | `studentId`, `name`, `grade`, `className`. 연락처 없음. 미지정이면 null |
| `FileDetailResponse` | `name: String`, `mediaType: String`, `sizeBytes: Long`, `originalUrl: String`, `urlExpiresAt: LocalDateTime 또는 null` |
| `PageDetailResponse` | `id: Long`, `pageNumber: Integer`, `width: Integer`, `height: Integer`, `imageUrl: String`, `urlExpiresAt: LocalDateTime 또는 null`, `regionCoordinateUnit` |
| `WorksheetSettingsResponse` | `etag: String`, `areas: SettingItemResponse[]` |
| `SettingItemResponse` | `area: CorrectionArea`, `level: STANDARD/ADVANCED` |

- `WorksheetDetailResponse.status`는 `DRAFT / PROCESSING / READY / FAILED`다. `latestGenerationId`는 생성 전 null일 수 있다.
- `pages`는 페이지 배열이고 `width/height`는 보정 PNG의 픽셀 크기다. `regionCoordinateUnit`은 `NORMALIZED_0_1`이다.
- 파일 크기 키는 `sizeBytes`, 원본 URL은 `originalUrl`, 페이지 표시는 `imageUrl`이다. 오래된 예시의 `size`, `url`, `previewImageUrl`을 현재 계약으로 사용하지 않는다.
- B4의 title/student는 미지정 시 null이며 saveStatus는 UNSAVED / TEMP_SAVED / COMPLETED다. firstSavedAt/lastSavedAt/completedAt은 업무 저장 시각으로 값이 없으면 null이다. status는 AI 처리 상태 의미를 유지한다.
- 검토 응답의 worksheet도 같은 DTO이므로 동일한 메타데이터를 제공한다. 제목·학생 지정은 임시/최종 저장을 실행하지 않으며 업무 저장 상태·시각·저장자를 변경하지 않는다.

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

최초 정리한 `ErrorCode` 목록에 B2의 학생 오류 `STUDENT_001`·`STUDENT_002`를 추가 반영했다. 아래 코드는 실제 적용 조건이 있는 API에서만 사용되며, 모든 API가 모든 검증을 수행한다는 뜻은 아니다.

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
| 404 | `STUDENT_001` | 학생 없음 또는 다른 기관 학생 |
| 409 | `STUDENT_002` | 같은 기관·같은 이름의 학생 후보 확인 필요. `error.candidates` 반환, 신규 생성 보류 |
| 403 | `ACADEMY_002` | 기관 활성 구독 아님 |
| 404 | `INVITATION_001` | 초대 토큰 없음 |
| 410 | `INVITATION_002` | 초대 만료 |
| 400 | `INVITATION_003` | 초대 사용 완료·취소 등 |
| 400 | `INVITATION_004` | 초대와 가입 이메일 불일치 |
| 404 | `WORKSHEET_001` | 학습지 없음·다른 기관·삭제됨 |
| 409 | `WORKSHEET_002` | 현재 AI 처리 상태에서 작업 불가. 삭제 API에서는 임시저장이 아닌 학습지 |
| 409 | `WORKSHEET_003` | 완료된 학습지는 수정할 수 없음 (쓰기 API 공통) |
| 409 | `WORKSHEET_004` | 저장본의 원본·생성 작업·복원 대상 또는 저장 상태 불일치 |
| 500 | `WORKSHEET_005` | 저장본 구조 버전 미지원·손상 데이터 |
| 404 | `WORKSHEET_006` | 마지막 저장본 없음. A3 통합 시 완료 차단용 `WORKSHEET_003`과 구분 |
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
| 503 | `INSIGHT_001` | 공유 AI 실행 한도 또는 인사이트 동시 1개 한도 사용 중. 수동 재시도 |
| 503 | `INSIGHT_002` | 인사이트 AI 연결 설정 없음·유효하지 않음 |
| 502 | `INSIGHT_003` | AI 통신·HTTP 실패·전체 응답 시간 초과·수신량 제한 초과 |
| 502 | `INSIGHT_004` | AI 응답 형식·근거·반복 패턴 검증 실패·거절·미완료 |
| 409 | `INSIGHT_005` | 분석 중 최신 입력 목록 변경 또는 저장본 revision 불일치 |
| 500 | `INSIGHT_006` | 저장 인사이트의 버전·구조 해석 실패 |
| 422 | `INSIGHT_007` | 분석 입력 UTF-8 96KiB 제한 초과 |

JWT 필터에서 유효한 인증을 만들지 못한 일반 보호 API 요청은 인증 진입점의 `AUTH_001`이 될 수 있다. 모든 만료 Access Token 요청이 반드시 `AUTH_004`를 반환한다고 단정하지 않는다.

## 5. 신규 엔드포인트 설계안

**최초 신규안 22개 중 학생 관리 4개·제목 및 학생 지정 1개·임시저장 1개·최종 저장 1개는 B2–B6에서, 선생님 상세·수정·목록 3개는 별도 구현했다. B5에서 저장본 조회·이어쓰기 2개를 추가했다.** 해당 계약은 5.1·5.5·5.7을 따른다. 나머지 신규안의 경로·필드·응답·검증 한계는 개발·프런트 합의 후 확정하며, 아래 권한은 구현 표시가 없는 경우 목표 정책이다. 선생님 목록(5.5)은 `page` 0부터, `size` 기본 20·최대 100, 응답 `items/total/page/size/totalPages`로 구현했으며, 나머지 목록 API의 시작 번호·기본값·응답 구조는 이 형식과 맞출지 확정되지 않았다.

### 5.1 학생 관리 — 구현 4개 / F08·F09

권한: 원장·선생님, 같은 기관.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 학생 목록 조회 | `/students` | Query: `keyword`, `grade`, `className`, `unassigned`, `page`, `size` | **구현(B3)**. 같은 기관의 이름 부분 검색·학년/반 필터·미배정 조회. HTTP 200, `StudentListResponse` 반환 |
| POST | 학생 등록 | `/students` | Body: `name*`, `grade*`, `className`, `studentPhone`, `guardianPhone`, `confirmDuplicate` | **구현(B2)**. 로그인 기관의 동명 후보가 있으면 409로 확인 요청. 동명이인 확인 후 또는 후보가 없으면 생성하여 HTTP 201, `Location`, `StudentResponse` 반환 |
| GET | 학생 상세 조회 | `/students/{studentId}` | Path: `studentId` | **구현(B2)**. 같은 기관 학생의 이름·학년·반·연락처 조회. HTTP 200, `StudentResponse` 반환 |
| PATCH | 학생 기본 정보 수정 | `/students/{studentId}` | Path: `studentId`; Body: `grade`, `className`, `studentPhone`, `guardianPhone` | **구현(B3)**. 전달한 기본 정보만 수정. 생략은 유지, 선택 필드 null·빈 값은 삭제. HTTP 200, `StudentResponse` 반환 |

학생 관리의 현재 계약:

- 인증된 `DIRECTOR`·`TEACHER`만 호출할 수 있다. 소속 기관은 로그인 정보로 결정하며 요청 Body의 기관 ID를 사용하지 않는다.
- 등록 시 이름·학년은 필수 문자열이며 각각 최대 50자다. 저장할 때 앞뒤 공백을 제거한다. 학년을 특정 enum으로 제한하지 않는다.
- 반은 최대 100자, 연락처는 각각 최대 20자다. 등록 시 선택 필드의 앞뒤 공백을 제거하며 생략·null·빈 문자열·공백만 입력한 값은 null로 저장한다. PATCH에서 생략한 값은 유지한다.
- 사용자가 선택한 연락처 입력 기준은 국내 전화번호의 숫자 또는 하이픈 형식이다. `01012345678`, `010-1234-5678`, `02-123-4567` 등을 허용하고 국제번호·문자·내부 공백·불완전한 하이픈은 거부한다. 하이픈 유무는 입력한 형식대로 보존한다.
- 등록·상세·수정 응답의 `result`는 `{studentId, name, grade, className, studentPhone, guardianPhone}`이다. 미입력 선택값은 null이다. 같은 기관의 원장·선생님에게 입력된 연락처를 반환한다. 목록은 별도의 요약 항목을 반환한다.
- 다른 기관 학생과 없는 학생은 동일한 `404 / STUDENT_001` 응답을 반환한다. 입력·JSON·ID 타입 오류는 `400 / COMMON_001`, 미인증은 `401 / AUTH_001`, 허용되지 않은 역할은 `403 / AUTH_002`다.
- 등록 시 앞뒤 공백을 제거한 이름으로 같은 기관의 기존 학생을 검색한다. 학년·반이 달라도 후보에 포함하며 학생 ID 오름차순으로 반환한다. 이름 비교는 DB의 문자열 비교 규칙을 따른다.
- `confirmDuplicate`는 선택 boolean이며 생략·null은 false다. 후보가 있고 false이면 신규 생성 없이 `409 / STUDENT_002`와 `error.candidates`를 반환한다. 후보가 없으면 바로 등록한다.
- 후보 항목은 `{studentId, name, grade, className, studentPhoneMatched, guardianPhoneMatched}`다. 연락처 원문은 후보 응답에 포함하지 않는다. 학생 ID를 기존 학생의 식별자로 사용한다.
- 연락처 일치 여부는 입력값과 기존 값에서 앞뒤 공백·하이픈을 제외해 같은 종류의 연락처끼리 비교한다. 양쪽 모두 값이 있을 때만 true이며 미입력끼리는 false다. 일치 표시는 중복 가능성 안내용이고, 연락처만 같고 이름이 다르면 후보로 처리하지 않는다.
- **기존 학생 사용**: 후보의 `studentId`로 기존 상세 API를 호출한다. 신규 생성이나 기존 정보의 자동 덮어쓰기는 없다. **다른 학생으로 등록**: 사용자가 동명이인임을 확인한 뒤 같은 POST를 `confirmDuplicate: true`로 다시 호출한다. 이름·연락처가 같아도 확인 후에는 별도의 학생 ID를 생성할 수 있다.
- 등록 트랜잭션은 소속 기관 행을 잠근 뒤 후보를 조회하고 생성 여부를 결정한다. 같은 기관의 동시 최초 등록에서도 앞선 등록을 확인할 수 있도록 순서대로 처리한다. 학년 허용값과 연락처 세부 열람·마스킹 정책은 미정이다.

중복 후보 응답 예시 (HTTP 409):

```json
{
  "isSuccess": false,
  "code": "STUDENT_002",
  "message": "같은 이름의 학생이 있습니다. 기존 학생을 확인해 주세요.",
  "result": null,
  "error": {
    "candidates": [
      {
        "studentId": 17,
        "name": "김학생",
        "grade": "초등 3",
        "className": "오후반",
        "studentPhoneMatched": true,
        "guardianPhoneMatched": false
      }
    ]
  }
}
```

학생 목록 계약 (B3):

- `keyword`는 이름 부분 검색(최대 50자), `grade`는 학년 일치(최대 50자), `className`은 반 이름 일치(최대 100자)다. 앞뒤 공백을 제거하고 빈 조건은 전체로 처리한다. 조건은 AND로 적용하며 `%`, `_`, `!`는 검색어의 일반 문자로 처리한다.
- `unassigned=true`는 반 미배정만 조회한다. null·빈 반을 포함하며 `className`과 동시에 지정하면 400이다. 화면 표시 `-`는 프런트에서 처리한다.
- `page`는 0부터, 기본 0이며 `size`는 기본 20·허용 1–100이다. 음수·잘못된 타입·JPA offset 정수 범위를 넘는 페이지 요청은 400이다. 정렬은 이름·학생 ID 오름차순이다.
- `result`는 `{items, total, page, size, totalPages, hasNext}`이며 `items`의 항목은 `{studentId, name, grade, className}`이다. 연락처는 목록에 포함하지 않는다.
- 데이터가 없으면 200과 빈 items·0건을 반환한다. 범위 밖 페이지는 빈 items와 필터에 해당하는 전체 total을 반환한다. 기관 조건은 items와 total 모두에 적용된다.

학생 기본 정보 PATCH 계약 (B3):

- `grade`, `className`, `studentPhone`, `guardianPhone` 중 최소 한 개를 전달한다. 생략한 필드는 유지한다.
- 학년을 전달하면 null·빈 값은 거부하며 최대 50자다. 선택 필드에 null·빈 문자열·공백만 입력하면 해당 값만 지운다. 앞뒤 공백 제거·길이·연락처 형식 검증은 등록과 동일하다.
- 이름·기관·학생 ID·버전 등 수정 대상이 아닌 필드와 `{}`·잘못된 JSON은 400이다. 학생 이름 수정 정책은 미정이므로 이번 API에 포함하지 않는다.
- 학생 ID와 로그인 기관 ID로 학생 행을 잠근 뒤 현재 값과 PATCH를 합친다. 기존 JPA 버전·감사 시각을 유지하며 If-Match 헤더는 요구하지 않는다. 기존 학생 정보만 변경하고 신규 생성·병합은 수행하지 않는다.
- 변경 정보는 상세·목록·등록 중복 후보 응답에 반영된다. 다른 기관·없는 학생의 수정은 동일한 `404 / STUDENT_001`이다.

PATCH 예시: 아래 요청은 학년을 바꾸고 반을 미배정으로 만들며, 생략한 연락처는 유지한다.

```json
{
  "grade": "초등 4",
  "className": null
}
```

### 5.2 학생 메모 — 구현 2개 / F09

권한: 원장·선생님, 같은 기관.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 학생 메모 목록 조회 | `/students/{studentId}/memos` | Path: `studentId`; Query: `page`, `size` | **구현(A4)**. 메모 ID·내용·작성자 이름·작성일을 최신순 조회. 200, `PageResponse<StudentMemoResponse>` |
| POST | 학생 메모 등록 | `/students/{studentId}/memos` | Path: `studentId`; Body: `content*` | **구현(A4)**. 로그인한 사용자를 작성자로 저장. 201, 등록한 `StudentMemoResponse`(`Location` 헤더 없음) |

기존 학습지 `comments`와 다른 자원이다. 메모 수정·삭제는 7장의 조건부 항목이다.

- 한 학생에게 여러 메모를 남길 수 있다. 같은 기관의 원장·선생님은 서로 쓴 메모를 모두 볼 수 있다.
- 응답 `StudentMemoResponse`는 `memoId`, `content`, `authorName`, `createdAt`이다. 작성자 이름은 조회 시점의 계정 이름이며 퇴사한 작성자의 메모도 그대로 표시한다. 작성자 이메일 등 다른 정보는 내려주지 않는다.
- 목록은 작성 시각 최신순이고 같으면 메모 ID가 큰 순이다. `page`는 0부터(기본 0), `size`는 기본 20·최대 100이며 잘못된 값은 `400 / COMMON_001`이다. 응답은 선생님 목록과 같은 `PageResponse`(`items`, `total`, `page`, `size`, `totalPages`)이다.
- `content`는 앞뒤 공백 제거 후 1–1,000자이다. 비었거나 공백뿐이면, 누락·길이 초과·JSON 형식 오류이면 `400 / COMMON_001`이다.
- 다른 기관 학생과 존재하지 않는 학생은 같은 `404 / STUDENT_001`이다. 메모가 없는 학생은 `200`과 빈 목록이다. 인증이 없으면 `401`이다.
- 메모 단건 조회, 수정, 삭제는 제공하지 않는다. 수정·삭제 정책이 정해지면 작성자·이력 보존 방식과 함께 추가한다.
- 운영 DB는 `ddl-auto: validate`이므로 배포 전에 `infra/db/2026-11-27_student_memos.sql`을 적용해야 한다.

### 5.3 학생 성장 리포트 — 그래프 1개(B8)·수동 인사이트 2개(B10·B11) 구현 / F09

권한: 원장·선생님, 같은 기관.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 학생 성장 그래프 조회 **구현(B8)** | `/students/{studentId}/growth-report` | Path: `studentId`; Query: `period`, `area` | 완료 저장본의 교사 확정 오류 피드백을 결과물별·영역별로 집계. `200`, `GrowthReportResponse` |
| GET | 학생 AI 인사이트 조회 **구현** | `/students/{studentId}/insights` | Path: `studentId`; Body·Query 없음 | LLM 호출 없이 최신 성공 결과·자료 부족·갱신 필요 여부 조회. `200`, `StudentInsightResponse` |
| POST | 학생 AI 인사이트 수동 생성·업데이트 **구현** | `/students/{studentId}/insights/generations` | Path: `studentId`; Body 없음 | 버튼 요청으로 동기 생성. 동일 입력은 재사용. 실행 한도 부족 시 즉시 503. `200`, `StudentInsightResponse` |

**조회 조건**

- 같은 기관 원장·선생님이 완료 결과물을 조회한다. 다른 기관·없는 학생은 동일한 `404 / STUDENT_001`이다. 미인증 `401`, 역할 거부 `403`, 잘못된 쿼리는 `400 / COMMON_001`이다.
- `period`: 정확히 `1M / 3M / 6M / ALL`, 기본 `3M`. KST 오늘에서 해당 개월 수를 뺀 날짜 00:00 이상, 내일 00:00 미만이다. `ALL`은 과거 시작일 제한이 없다.
- `area`: 생략하면 네 표준 영역 모두, 지정하면 해당 영역의 `areas`·`series`만 반환한다. 기존 `GRAMMAR_MECHANICS`는 `GRAMMAR_SPELLING`으로 정규화한다. 각 점의 전체 합계는 영역 필터와 무관하게 유지한다.
- `COMPLETED`·`completedAt` 존재·미삭제·저장본 존재 조건을 만족하는 해당 학생의 결과물만 조회한다. `completedAt ASC, worksheetId ASC`이며 하루의 여러 결과물을 합치지 않는다. `ALL`도 임의로 점을 생략하지 않는다.

**집계 계약 `CONFIRMED_FEEDBACK_V1`**

- 작업 테이블의 현재 값이 아닌 **최종 `snapshot.review`**를 사용한다. AI는 `ACTIVE` + `isApplied=true` + `feedbackType=CORRECTION` 한 카드당 1건이다. 삭제·미적용은 제외하며 `SUGGESTION`은 별도 제안 수다.
- 교사 코멘트 `classification.type=CORRECTION`은 새로운 오류 1건, `SUGGESTION`은 제안 1건, `GENERAL`은 일반 코멘트 1건, `SUPPLEMENT`는 기존 카드 보충 설명 1건이다. 보충 설명은 오류를 추가로 세지 않으며, 연결 카드가 나중에 삭제되어도 새로운 오류로 승격하지 않는다.
- `area`는 대표 영역 한 개, `issueKinds`는 중복 없는 복수 태그다. 정상 종류는 `grammar`, `spelling`, `punctuation`, `vocabulary`, `sentence_structure`, `content` 여섯 개다. 한 카드의 종류가 여러 개여도 전체·대표 영역 오류 수는 1이고 각 종류 빈도는 1씩 증가한다. 종류별 합은 전체 수보다 클 수 있다.
- 과거 카드의 `issueKinds`가 없으면 전체·영역 건수에는 포함하되 `unclassifiedIssueCount`로 명시한다. 종류를 추측하지 않는다. AI 원래 분류는 이번 작업에서 변경하지 않는다.
- 단어 수는 저장본 AI 작업의 결과 아카이브가 **실제 참조한 불변 전사문 전체**를 페이지마다 한 번씩 센다. 재시도로 이전 작업의 전사를 재사용한 경우도 지원한다. 수정문·카드 원문 조각·가변 OCR 캐시는 사용하지 않는다.
- 단어는 Unicode 문자·숫자 토큰이며 내부 `'`, `’`, ASCII `-`는 연결한다. `don't`, `well-known`은 각각 1단어, 숫자 토큰은 1단어, 나머지 구두점·줄바꿈은 경계다.
- 영역 수치는 `(영역 확정 오류 피드백 수 / 전체 원문 단어 수) × 100`, 소수 2자리 HALF_UP이다. 전사 누락·연결 불일치·페이지 일부 누락이면 `wordCount=null / MISSING_TRANSCRIPTION`, 0단어면 `wordCount=0 / EMPTY_TRANSCRIPTION`이며 정규화 수치는 `null`이다.
- 영역 분석은 저장된 서버 검증 `summary`의 실제 선택 수준·최대 개수가 있는 페이지로 판별한다. 모든 페이지가 분석된 `ANALYZED`만 정규화한다. 일부만 분석되면 `PARTIAL`, AI 분석 없이 교사 분류만 있으면 `TEACHER_ONLY`, 둘 다 없으면 `NOT_ANALYZED`다. `NOT_ANALYZED.errorCount=null`이며 실제 분석된 0건과 구분한다.
- 이 지표는 학생의 모든 오류를 빠짐없이 센 값이 아니다. AI 카드 개수 제한·교사 선택의 영향을 받는 **교사가 확정한 오류 수정 피드백 수**다. 낮은 값만으로 실력 향상을 단정하지 않는다.

**응답 필드**

| 위치 | 필드 | 의미 |
| --- | --- | --- |
| 최상위 | `studentId`, `period`, `selectedArea`, `from`, `to`, `metricVersion` | 조회 기준. `ALL.from=null`, `to`는 KST 오늘 |
| 최상위 | `dataStatus`, `minimumPointCount=2`, `comparablePointCount`, `unavailableResultCount` | `EMPTY` / `INSUFFICIENT_DATA` / `AVAILABLE`. 같은 영역에서 정규화 가능한 점이 최소 2개면 비교 가능. 전체 영역 조회의 비교 건수는 영역별 건수의 최댓값 |
| `series[]` | `area`, `comparablePointCount`, `dataStatus` | 영역별 데이터 충분 여부. 서로 다른 영역의 점을 합쳐 2개로 판단하지 않음 |
| `points[]` | `worksheetId`, `title`, `completedAt`, `aggregationStatus` | 학습지별 점과 결과물 이동 ID. 원문 전체·연락처 등은 노출하지 않음 |
| `points[]` | `wordCount`, `wordCountStatus` | 단어 수 및 `AVAILABLE` / `MISSING_TRANSCRIPTION` / `EMPTY_TRANSCRIPTION` |
| `points[]` | `errorCount`, `aiErrorCount`, `teacherErrorCount`, `suggestionCount`, `generalCommentCount`, `supplementCommentCount` | 전체 합계. 제안·일반·보충 설명은 오류 합계와 별도 |
| `points[]` | `unclassifiedIssueCount`, `issueKindCounts[]` | 종류 미분류 오류 건수와 여섯 종류별 `{issueKind,count}` |
| `points[].areas[]` | `area`, `analysisStatus`, `errorCount`, `aiErrorCount`, `teacherErrorCount`, `suggestionCount`, `unclassifiedIssueCount`, `errorsPer100Words`, `issueKindCounts` | 영역별 집계. 종류 수치는 해당 대표 영역에 속한 카드의 태그 빈도 |
| `points[].areas[]` | `representativeFeedback[]` | 오류 피드백 최대 3건. `{source, feedbackId, originalText, content}`. 교사 코멘트의 `originalText=null` |

저장 JSON을 해석할 수 없거나 완료 메타데이터가 불일치하면 그 점을 `aggregationStatus=UNREADABLE_SNAPSHOT`·집계 값 `null`·빈 `areas`로 반환하고 `unavailableResultCount`를 증가시킨다. 다른 정상 점은 유지한다. 집계 실패 시 `[growth]` WARN 로그에 학습지 ID·학생 ID·예외 종류와 메시지·스택 트레이스를 남기며 스냅샷 전문은 기록하지 않는다. 최소 2개는 그래프 비교 기준이며 **수동 AI 인사이트의 최소 3개 기준과 별개**다.

`period=ALL`은 현재 완료 저장본 JSON과 전사문을 전체 조회하므로 이력이 많은 학생의 요청은 힙 사용량이 커질 수 있다. 전사 IN 조건 500개 분할은 전체 자료를 메모리에 보관하는 문제를 해소하지 않는다. 현재 점을 임의로 자르지 않는 계약을 유지하며, 대량 이력 부하 측정 후 집계 데이터 분리·배치 처리 등으로 개선할 후속 항목이다.

**기존 교사 코멘트 API 확장**

`POST /worksheets/{worksheetId}/comments`, `PATCH /worksheets/{worksheetId}/comments/{commentId}`의 Body와 코멘트 응답에 `classification`을 추가했다. 별도 분류 API는 만들지 않았다.

```json
{
  "type": "CORRECTION",
  "area": "GRAMMAR_SPELLING",
  "issueKinds": ["grammar", "punctuation"],
  "correctionId": null
}
```

- 작성 시 생략·`null`이면 `GENERAL`; 수정 시 생략·`null`이면 현재 분류 유지. 지정하면 분류 전체 교체다. 기존 수정 API의 필수 `content`와 코멘트 `If-Match`는 유지한다.
- `CORRECTION`·`SUGGESTION`: 대표 `area`와 일반 `issueKinds`가 필수다. 종류는 중복·null·진단 종류를 허용하지 않고 최소 하나가 대표 영역과 대응해야 한다. `correctionId`는 지정할 수 없다.
- `GENERAL`: `area`, `correctionId` 없음, 종류는 생략 또는 `[]`다.
- `SUPPLEMENT`: 같은 학습지의 활성 `correctionId` 필수, `area` 없음, 종류는 생략 또는 `[]`다. 존재하지 않거나 타 학습지·삭제 카드이면 `404 / CORRECTION_001`이다. 보충 설명의 여러 코멘트는 오류를 중복 증가시키지 않는다.
- 분류 조합·형식 오류는 `400 / COMMON_001`, 오래된 코멘트 ETag는 기존 `412`, 완료 후 변경은 기존 `409 / WORKSHEET_003`이다. 모든 분류 쓰기는 기존 부모 학습지 잠금·기관·READY·임시저장 소유권 검사를 따른다.
- 신규 저장 행은 `schema_version=2`이며 분류를 JSON의 공개 코멘트와 복원 상태에 함께 저장한다. 기존 v1의 누락 분류는 GENERAL로 조회·복원하며 기존 최종 저장본을 현재 작업 테이블로 보충하거나 재작성하지 않는다.
- 배포·롤백: 이전 코드는 v2 및 추가 classification 필드를 읽지 못한다. 개발 서버에서 v1 조회·이어쓰기와 v2 임시/최종 저장·조회·복원을 검증한 뒤 운영 배포한다. v2 저장본 생성 후 구버전으로 단순 롤백하면 조회·복원이 실패할 수 있으며, schema_version을 1로만 바꿔도 추가 필드 거부 문제는 남는다. 롤백 후보 코드에도 v1/v2 읽기 호환이 필요하다. 이번 검증은 로컬 H2 기준이며 개발·운영 서버 배포 검증은 별도다.

**수동 AI 인사이트 MVP 계약 (2026-11-28)**

- 권한: 같은 기관 원장·선생님. 없는 학생·다른 기관 학생은 동일한 `404 / STUDENT_001`, 미인증 401, 역할 거부 403이다. 완료본의 업로더가 달라도 기관 공유 기준으로 분석한다.
- 분석 범위: KST 오늘 기준 최근 3개월 완료본 중 최신 10개(`completedAt DESC, worksheetId DESC`). 실제 외부 AI 작업 `TERRA + SUCCEEDED` 및 현재 원본 해시와 저장 당시 해시가 일치하는 저장본만 후보로 선택한다. 미저장·임시저장·삭제·기간 밖·FIXED/LEGACY_MOCK는 제외한다.
- 최소 정상 저장본 3개가 필요하다. 후보가 3개여도 손상·미지원 저장본을 제외하고 2개만 남으면 자료 부족으로 반환하며 AI를 호출하지 않는다. 전사 단어 수가 없어도 확정 피드백의 정성 분석은 가능하며 정규화 추세를 추측하지 않는다.
- 입력: 최종 공개 review에서 활성·적용된 AI 오류·제안과 교사의 CORRECTION/SUGGESTION을 추출한다. GENERAL·SUPPLEMENT는 첫 MVP 입력에서 제외한다. 교사 수정으로 오래된 AI 해설은 제외한다. 학생 프로필의 이름·연락처 필드, 파일/이미지·좌표·삭제 복원 데이터는 별도로 전송하지 않는다. 피드백 발췌 자체에는 학생이 쓴 문장이 포함된다.
- 입력량: 후보 메타데이터를 먼저 10개로 제한하고 스냅샷은 한 번에 하나씩 읽는다. 영역별·AI/교사별 후보를 고르게 선택해 결과물당 최대 6개, 총 최대 60개 근거를 보낸다. 원문 120자·현재 피드백 160자·해설 80자로 발췌하고 전체 UTF-8 입력은 96KiB 이하다. 건수는 선택한 근거 수가 아니라 해당 완료본의 전체 확정 피드백에서 계산한다.
- 출력: 인사이트 `insights` 최대 3개와 다음 수업 추천 `coachingPoints` 1~2개다. 인사이트 제목 60자, 설명·추천 문장 300자 이하이며 한국어로 생성한다. 개수를 채우기 위한 패턴 생성은 금지한다. AI가 말한 숫자를 통계로 저장하지 않는다.
- 각 항목은 입력에 존재하는 `evidenceIds` 1~3개가 필요하다. ERROR_PATTERN은 서로 다른 결과물 2개 이상의 CORRECTION 근거를 요구한다. IMPROVEMENT는 근거가 있는 지도 제안이며 실력 향상 판정이 아니다. 오류 수정과 제안을 구분하며 근거의 대표 영역도 확인한다. 응답에는 실제 사용한 근거만 보관·반환한다.
- 생성은 POST 요청에서 동기 처리한다. 프런트는 생성 중 버튼/로딩을 표시하고, 내용을 복사할 때 coachingPoints의 content를 이용한다. 조회·프로필 진입·최종 저장·시간 경과로 AI를 자동 호출하지 않는다.
- 동일 후보 ID/revision/저장 구조/해시/완료일과 분석 정책·프롬프트·모델·출력 토큰 한도면 기존 결과를 반환한다. 새 완료본 등으로 입력이 달라져도 GET은 기존 결과와 `isStale=true`를 반환한다. 갱신 중 최신 입력이 바뀌면 409로 저장을 거부하고 기존 성공 결과를 유지한다. 실패도 기존 성공 결과를 덮어쓰지 않는다.
- 실행 제한: 단일 JVM에서 첨삭과 `app.generation.worker-count` 예산을 공유하며 인사이트 자체는 최대 1개다. 자료 부족·동일 입력 재사용은 AI 슬롯을 사용하지 않는다. 생성 슬롯이 없으면 스냅샷을 읽거나 대기열에 넣지 않고 `503 / INSIGHT_001`을 반환한다. 첨삭 워커가 슬롯을 얻지 못하면 claim하지 않아 기존 PENDING/dispatcher 재배정 흐름을 유지한다. 새 워커·우선순위 큐·자동 재시도·작업 복구는 추가하지 않았다.
- 외부 HTTP 동안 DB 트랜잭션을 열지 않는다. 기본 대기 시간 60초(설정 최대 90초), 기본 출력 토큰 2400(설정 최대 4000), HTTP 수신 본문 256KiB, 모델 JSON 16000자 제한이다. 요청당 한 번만 호출하며 오류 본문·키·학생 원문은 로그에 남기지 않는다.
- 설정: `AI_INSIGHT_MODEL`, `AI_INSIGHT_REQUEST_TIMEOUT`, `AI_INSIGHT_MAX_OUTPUT_TOKENS`. 기본 모델은 기존 첨삭과 같은 gpt-6.1-terra이며 endpoint/key는 `app.ai.insight.*` → 기존 `app.ai.terra.*`/`OPENAI_API_KEY`를 재사용한다. 프런트 요청으로 모델·프롬프트를 받지 않는다.

| 응답 필드 | 의미 |
| --- | --- |
| `studentId`, `status` | EMPTY / INSUFFICIENT_DATA / NOT_GENERATED / NO_FEEDBACK / READY |
| `candidateResultCount`, `usedResultCount`, `minimumResultCount` | 후보 수·실제 사용 저장본 수·최소 3개. 아직 스냅샷을 읽지 않은 응답의 usedResultCount는 null |
| `isStale` | 저장 결과가 현재 후보·모델·분석 정책과 달라 갱신이 필요한지 |
| `reused` | POST에서 기존 동일 입력 결과를 재사용한 경우 true. GET은 false |
| `analysisFrom`, `analysisTo`, `generatedAt` | 저장된 분석 기간과 생성 시각. 미생성 응답은 현재 조회 기준 기간 |
| `insights[]` | `{title, description, area, kind, evidenceIds}`. kind는 ERROR_PATTERN / IMPROVEMENT |
| `coachingPoints[]` | `{content, evidenceIds}`. 다음 수업 추천 및 프런트 내용 복사에 사용 |
| `evidence[]` | `{id, worksheetId, feedbackId, source, feedbackType, area, completedAt, originalText, content, explanation}`. 발췌된 실제 사용 근거이며 교사 원문/해설은 null 가능 |

NO_FEEDBACK은 분석 가능한 오류·제안이 없다는 뜻으로, 오류가 없는 학생이라는 판정이 아니다. 이 결과도 같은 입력이면 재사용한다. 실패·혼잡은 HTTP 오류로 반환하고 새 FAILED/RUNNING 행을 저장하지 않는다. DB는 학생당 최신 성공 결과 한 개를 `student_insights`에 보관하며 이력은 없다. 배포 전에 `infra/db/2026-11-28_student_insights.sql`을 적용해야 한다. 동시 실행 상한은 단일 JVM 기준이며 실제 혼합 부하의 RAM 안전 한도는 별도 측정 대상이다.

### 5.4 학생 결과물 목록 — 구현 1개(B7) / F09

권한: 같은 기관 원장·선생님. **임시저장은 업로더 본인만, 완료는 같은 기관 모두 목록·저장본 조회 가능**하다. 완료 공유는 읽기 전용이며 임시/최종 저장·이어쓰기 권한을 확대하지 않는다.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 학생 첨삭 결과물 목록 조회 | `/students/{studentId}/worksheets` | Path: `studentId`; Query: `keyword`, `from`, `to`, `page`, `size` | **구현(B7)**. 학생별 저장 결과물을 최근 저장일·ID 내림차순으로 조회. 임시저장 본인만·완료 기관 공유, 미저장·삭제·저장본 없는 행 제외. 200 |

- 검색어는 앞뒤 공백 제거 후 최대 255자다. 빈 값은 전체이며 제목 부분 검색에 ASCII 대소문자를 구분하지 않는다. `%`, `_`, `!`는 와일드카드가 아닌 실제 문자로 검색한다.
- 기간은 `lastSavedAt`(최근 명시적 저장일) 기준이다. `from/to=YYYY-MM-DD` 양 끝 날짜를 포함하고 각각 단독 사용 가능하다. 시작일 00:00:00 이상·종료일 23:59:59.999999 이하이며, 시작일이 종료일보다 늦거나 잘못된 날짜는 400이다. 기존 LocalDateTime을 임의로 시간대 변환하지 않는다.
- page는 0부터 기본 0, size는 기본 20·허용 1~100이다. `page*size`가 Integer.MAX_VALUE를 초과하면 400이다. 없는/타 기관 학생은 동일한 404 STUDENT_001, 결과 없는 기존 학생은 빈 목록과 200이다.
- 대상은 같은 기관·해당 학생, TEMP_SAVED 또는 COMPLETED, deletedAt 없음·lastSavedAt 있음·저장본 행 존재다. 임시저장은 업로더 본인만 반환하며 완료는 기관 공유다. 퇴사한 담당자의 결과도 유지한다.
- result는 `{items, total, page, size, totalPages, hasNext}`다. 각 item은 `{worksheetId, title, saveStatus, savedAt, lastWorkedAt, student, teacher, canView, canResume}`이며 student는 ID·이름·학년·반, teacher는 업로더의 `{teacherId, name}`이다. 연락처·이메일·snapshot JSON은 반환하지 않는다.
- savedAt은 lastSavedAt, lastWorkedAt은 updatedAt이다. 추가 편집은 lastWorkedAt만 바꿀 수 있으므로 학생 목록 정렬·기간은 savedAt 기준을 유지한다. canView는 READY 여부, canResume은 READY·TEMP_SAVED·본인 여부다. 손상·원본 불일치 등 추가 검증은 저장본 조회에서 수행한다.
- 목록은 필요한 스칼라 필드만 조회하고 저장본은 EXISTS로 존재만 확인한다. Worksheet/저장본 엔티티나 큰 snapshot을 로딩·디코딩하지 않는다. 목록 선택 후 `/worksheets/{worksheetId}/saved-result`로 실제 저장본을 조회한다. 임시저장 복원은 현재 ETag를 확인하고 명시적으로 `/resume`을 요청해야 한다.
- 새 테이블·컬럼·DDL은 없다. PDF 연결은 7장의 후속 범위다.

### 5.5 선생님 관리 — 목록·상세·수정·첨삭 이력 구현 / F10·F11

권한: **원장 전용**, 같은 기관.

> 아래 목록·상세·수정 3개는 `feat/97-teacher-detail-list`에서 구현했다(이슈 #97). 2장 표에는 머지 후 옮긴다. 월별 첨삭 수와 첨삭 이력 조회는 이슈 #107에서 구현했으며, 업무 저장 상태와 처음 저장 시각은 B의 임시저장·최종 저장 API가 채운다.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 선생님 목록 조회 **(구현)** | `/teachers` | Query: `keyword`, `status`, `page`, `size` | 가입한 선생님과 초대 대기를 한 목록으로 이름순 조회. 이름·이메일 검색, 활동 중·초대 대기·퇴사 필터, 페이지 나누기. 행마다 이번 달 첨삭 수(`monthlyCorrectionCount`) 포함 |
| GET | 선생님 상세 조회 **(구현)** | `/teachers/{teacherId}` | Path: `teacherId` | 이름·계정 이메일·연락처·입사일·상태 조회. 퇴사한 선생님도 조회 가능 |
| PATCH | 선생님 정보 수정 **(구현)** | `/teachers/{teacherId}` | Path: `teacherId`; Body: `name`, `phoneNumber`, `hireDate` | 이름·연락처·입사일 부분 수정. 이메일은 바꾸지 않음 |
| GET | 선생님 첨삭 이력 조회 **(구현)** | `/teachers/{teacherId}/worksheets` | Path: `teacherId`; Query: `keyword`, `month`, `page`, `size` | 선생님이 올린 임시저장·완료 학습지를 처음 저장한 시각 최신순으로 조회. 학생 이름·제목 검색, 월 필터. 삭제 제외 |
| POST | 선생님 퇴사 처리 | `/teachers/{teacherId}/resign` | Path: `teacherId`; Body 없음 | 퇴사 상태로 변경하고 로그인 차단. 결과물·담당자 이력 보존. 기존 세션 차단 방식은 별도 설계 |

**구현한 3개의 공통 규칙**

- 성공은 `200`과 `ApiResponse` 봉투다. 새 오류 코드는 없다.
- 선생님이 호출하면 `403 / AUTH_002`, 토큰이 없으면 `401`이다.
- 대상 선생님은 로그인 기관 기준으로 찾는다. 없는 ID와 다른 기관 사용자는 존재 여부를 알려주지 않도록 모두 `404 / AUTH_006`이고, 같은 기관의 원장 ID는 선생님 관리 대상이 아니라서 `403 / AUTH_002`다.
- 기관은 토큰에서 가져온다. 요청으로 기관을 지정할 수 없다.

**목록 `GET /teachers`**

| Query | 규칙 |
| --- | --- |
| `keyword` | 이름·이메일 부분 일치, 대소문자 무시. 앞뒤 공백은 제거하고 비어 있으면 무시한다. `%`·`_`는 글자 그대로 찾는다 |
| `status` | `ACTIVE`(활동 중), `INVITED`(초대 대기), `RESIGNED`(퇴사). 생략하면 전체. 다른 값은 `400 / COMMON_001` |
| `page` | 0부터 시작. 기본 0. 0 미만이면 `400 / COMMON_001` |
| `size` | 기본 20, 1~100. 범위 밖이거나 숫자가 아니면 `400 / COMMON_001` |

- 대상: 같은 기관의 가입한 선생님(원장 제외)과 초대 대기. 퇴사가 아닌 가입 계정은 `INACTIVE`·`LOCKED`를 포함해 모두 `ACTIVE`다.
- 초대 대기: 상태가 `PENDING`인 초대. 만료 시각이 지나도 상태가 그대로면 포함하며 `expiresAt`으로 구분한다. 취소·수락·만료 처리된 초대는 제외한다. 초대 상세 조회 API는 없다.
- 정렬: 이름 오름차순 고정. 이름이 없는 초대는 이메일로 대신하고, 같으면 이메일, 가입 선생님, ID 순이다. 정렬 선택 파라미터는 없다.
- 구현: 기관·상태·검색어로 걸러 전체를 읽은 뒤 서버에서 합치고 정렬해 페이지로 자른다. 한 기관의 선생님·초대가 수천 건 이상으로 늘면 DB에서 자르는 방식으로 바꿔야 한다.
- 응답 `result`(`PageResponse`): `items`, `total`(조건에 맞는 전체 수), `page`, `size`, `totalPages`. 범위 밖 페이지는 `200`과 빈 `items`다.

`items`의 한 행(`TeacherListItemResponse`):

| 필드 | 가입한 선생님 | 초대 대기 |
| --- | --- | --- |
| `teacherId` | 사용자 ID | `null` |
| `invitationId` | `null` | 초대 ID |
| `registered` | `true` | `false` |
| `name` | 이름 | 초대 때 입력한 이름. 없으면 `null` |
| `email` | 계정 이메일 | 초대 이메일 |
| `phoneNumber` | 값 또는 `null` | `null` |
| `hireDate` | `YYYY-MM-DD` 또는 `null` | `null` |
| `status` | `ACTIVE` / `RESIGNED` | `INVITED` |
| `monthlyCorrectionCount` | 이번 달 첨삭 수(아래 집계 기준) | `0` |
| `expiresAt` | `null` | 초대 만료 시각 |

**월별 첨삭 수·첨삭 이력의 집계 기준 (확정)**

| 항목 | 기준 |
| --- | --- |
| 대상 | 해당 선생님이 **올린**(업로더) 학습지. 마지막 저장자가 아니다 |
| 상태 | 업무 저장 상태가 `TEMP_SAVED`(임시저장) 또는 `COMPLETED`(완료). 저장 전(`UNSAVED`)은 제외 |
| 삭제 | 삭제된 학습지(`deleted_at`이 있는 것)는 제외 |
| 월을 나누는 시각 | **처음 저장한 시각(`firstSavedAt`)**. 임시저장이든 완료든 처음 저장한 달에 한 번만 센다. 임시저장을 완료로 바꾸거나 이어서 저장해도 달이 옮겨가지 않으므로 지난달 숫자가 나중에 바뀌지 않는다 |
| 월 경계 | 한국 시간(`Asia/Seoul`) 기준 그 달 1일 00:00 이상, 다음 달 1일 00:00 미만. "이번 달"은 서버 설정과 무관하게 한국 시간의 현재 달이다 |
| 일치 | 목록의 `monthlyCorrectionCount`와 같은 달의 첨삭 이력 `total`은 같은 쿼리 조건을 사용하므로 항상 같다 |

- 저장 API(B5·B6)는 학습지를 처음 저장할 때(임시저장·최종 저장 구분 없이) `firstSavedAt`을 기록하고, 저장은 업로더 본인만 할 수 있다. 값이 없는 학습지는 집계·이력에서 빠진다.
- 학생 지정 전에 임시저장된 학습지처럼 제목·학생이 없는 학습지도 집계에 포함된다.

**목록의 `monthlyCorrectionCount`**

- 한국 시간 기준 **이번 달**만 제공한다. 달을 고르는 파라미터는 없다.
- 가입한 선생님 행에만 값이 있고 초대 대기 행은 `0`이다. 첨삭이 없으면 `0`이다. 퇴사한 선생님도 이번 달 건수를 그대로 보여준다.
- 목록의 페이지에 보이는 선생님만 한 번의 집계 쿼리로 센다.

**첨삭 이력 `GET /teachers/{teacherId}/worksheets`**

| Query | 규칙 |
| --- | --- |
| `keyword` | 학습지 제목·학생 이름 부분 일치, 대소문자 무시. 앞뒤 공백은 제거하고 비어 있으면 무시. `%`·`_`는 글자 그대로 찾는다. 검색어가 있으면 제목·학생이 없는 학습지는 나오지 않는다 |
| `month` | `yyyy-MM`(예: `2026-11`). 처음 저장한 달을 지정한다. 생략하면 **전체 기간**. `2026-13`·`abc`·`2026-1` 같은 형식 오류는 `400 / COMMON_001` |
| `page` | 0부터 시작. 기본 0. 0 미만이면 `400 / COMMON_001` |
| `size` | 기본 20, 1~100. 범위 밖이거나 숫자가 아니면 `400 / COMMON_001` |

- 권한과 대상 확인은 다른 선생님 API와 같다. 원장 전용이고(선생님은 `403 / AUTH_002`, 토큰 없음은 `401`), 없는 ID·다른 기관은 `404 / AUTH_006`, 같은 기관의 원장 ID는 `403 / AUTH_002`다. **퇴사한 선생님의 이력도 조회할 수 있다.**
- 정렬은 처음 저장한 시각 최신순, 같으면 학습지 ID가 큰 순으로 고정이다.
- 응답 `result`(`PageResponse`): `items`, `total`, `page`, `size`, `totalPages`. 범위 밖 페이지는 `200`과 빈 `items`다.

`items`의 한 행(`TeacherWorksheetHistoryItemResponse`):

| 필드 | 설명 |
| --- | --- |
| `worksheetId` | 학습지 ID |
| `title` | 제목. 지정하지 않았으면 `null` |
| `studentId`, `studentName` | 연결된 학생. 지정하지 않았으면 `null` |
| `saveStatus` | `TEMP_SAVED`(임시저장) 또는 `COMPLETED`(완료) |
| `firstSavedAt` | 처음 저장한 시각(월 집계 기준) |
| `lastSavedAt` | 마지막으로 저장한 시각. 값이 없으면 `null` |

**상세 `GET /teachers/{teacherId}`**

- 응답 `result`(`TeacherDetailResponse`): `teacherId`, `name`, `email`, `phoneNumber`, `hireDate`, `status`. 연락처·입사일은 등록 전이면 `null`이다.
- 여기의 `status`는 계정 상태(`ACTIVE`, `INACTIVE`, `LOCKED`, `RESIGNED`)이며, 목록의 `status`(`ACTIVE`, `INVITED`, `RESIGNED`)와 값의 종류가 다르다.
- 퇴사한 선생님도 조회할 수 있다.

**수정 `PATCH /teachers/{teacherId}`**

| Body | 규칙 |
| --- | --- |
| `name` | 50자 이내, 공백만 입력 불가. 앞뒤 공백은 제거해 저장 |
| `phoneNumber` | 숫자·하이픈·`+` 7~20자. 입력한 형태 그대로 저장. 빈 문자열 `""`이면 연락처를 삭제(null) |
| `hireDate` | `YYYY-MM-DD`. 미래 날짜 가능. 입사일 삭제는 지원하지 않음 |

- 부분 수정이다. 보내지 않았거나 `null`인 항목은 바꾸지 않는다. 하나 이상은 값이 있어야 하며 모두 비어 있으면(`{}` 포함) `400 / COMMON_001`이다.
- 형식 오류(잘못된 날짜 문자열 포함)는 `400 / COMMON_001`이다.
- 퇴사한 선생님도 수정할 수 있고 상태는 그대로다. 요청에 `email`이 있어도 무시하며 이메일은 바뀌지 않는다.
- 응답은 상세 조회와 같은 `TeacherDetailResponse`다.

**후속·미정**

- 월별 첨삭 수와 첨삭 이력 조회는 위 기준으로 구현했다. 저장 API 구현(B5·B6) 이전에 만들어진 학습지는 `firstSavedAt`이 없어 집계·이력에 나오지 않는다.
- 이력 항목에 다른 필드(예: 완료 시각, 담당 교사)가 필요한지와 이력 전체 기간의 페이지 방식은 화면 확정 후 조정한다.
- 초대 대기 행의 상세 진입 처리는 미정이다. 초대 행은 `teacherId`가 없고 초대 상세 API도 없다. 화면 기준의 "행을 누르면 프로필로 이동"과 어긋나므로 프런트와 정해야 한다.
- 이메일 수정, 퇴사 코드명·퇴사 취소·재입사, 오래된 만료 초대의 목록 정리 규칙은 미정이다.

### 5.6 계정·기관 정보 변경 — 신규안 3개 / F12

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| PATCH | 내 이름 수정 | `/users/me` | Body: `name*` | 원장·선생님 본인 이름 수정 |
| PATCH | 내 비밀번호 변경 | `/users/me/password` | Body: `currentPassword*`, `newPassword*` | 원장·선생님 현재 비밀번호 확인 후 새 해시 저장. 8–20자·영문·숫자·특수문자 정책 적용 |
| PATCH | 소속 기관명 수정 | `/academies/me` | Body: `name*` | 원장만 소속 기관명 변경. 선생님 변경 거부, 공통 화면 표시 연계 |

계정 이메일 수정, 비밀번호 변경 후 다른 기기 세션 종료는 미정이다. 대상 기관·사용자 ID를 클라이언트가 임의 지정하기보다 로그인 정보를 사용하는 안이다.

### 5.7 제목·학생 지정, 저장·삭제 — 구현 6개 / D1–D6·F05·F07

제목·학생 지정은 업로더 본인 또는 같은 기관 원장만 가능하다. 임시·최종 저장·이어쓰기는 READY·업로더 본인만 가능하다. B7에서 저장본 조회는 임시저장 본인만·완료는 같은 기관 원장/선생님으로 확장했다. 완료 후 임시저장·이어쓰기는 거부한다. 임시저장 삭제는 A3 구현으로 업로더 본인만 가능하다.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| PATCH | 학습지 제목·학생 지정 | `/worksheets/{worksheetId}` | Path: `worksheetId`; Body: `title*`, `studentId*`; Header: `If-Match` 선택 | **구현(B4)**. 업로더·같은 기관 원장이 DRAFT/FAILED에서 두 값을 함께 지정. 200, 최신 WorksheetDetailResponse·ETag 반환 |
| POST | 첨삭 임시저장 | `/worksheets/{worksheetId}/save-draft` | Path: `worksheetId`; Header: `If-Match` 선택; Body 없음 | **구현(B5)**. 서버에 반영한 편집 결과의 마지막 저장본 한 개와 TEMP_SAVED·저장 시각 기록. 200, 저장본·저장본 ETag 반환 |
| GET | 마지막 저장본 조회 | `/worksheets/{worksheetId}/saved-result` | Path: `worksheetId` | **구현(B5–B6)**. 마지막 임시 또는 최종 저장 당시 검토 응답을 반환. 현재 편집 내용은 변경하지 않음. 200, 저장본·저장본 ETag 반환 |
| POST | 마지막 임시저장본으로 이어쓰기 | `/worksheets/{worksheetId}/resume` | Path: `worksheetId`; Header: 현재 학습지 `If-Match` 필수; Body 없음 | **구현(B5)**. 저장 후 편집을 되돌리고 최신 ReviewResponse·학습지 ETag 반환. 200 |
| POST | 첨삭 최종 저장 | `/worksheets/{worksheetId}/complete` | Path: `worksheetId`; Header: 현재 학습지 `If-Match` 필수; Body 없음 | **구현(B6)**. 필수 데이터 검증 후 현재 편집 상태를 저장본으로 보관하고 COMPLETED·최종 저장 시각 기록. 200, 저장본·저장본 ETag 반환 |
| DELETE | 임시저장 작업 삭제 | `/worksheets/{worksheetId}` | Path: `worksheetId`; Body·If-Match 없음 | **구현(A3)**. 업로더 본인의 임시저장(`TEMP_SAVED`) 학습지에 deleted_at 기록. 204 No Content. 데이터·이미지 보존, 이후 모든 조회·쓰기는 404 |

- B5 임시·B6 최종 저장은 Body 없이 개별 편집 API로 서버에 반영한 상태를 저장한다. 프런트는 진행 중인 편집 요청이 모두 성공한 다음 현재 학습지 ETag를 조회하고 저장해야 한다.
- 제목·학생은 별도 PATCH로 구현했다. 제목은 앞뒤 공백 제거 후 1–255자, 학생 ID는 양수·같은 기관의 등록 학생이어야 한다. 두 필드 모두 필수이며 B3 학생 PATCH의 생략 유지·선택값 삭제 규칙을 적용하지 않는다.
- 사용자가 확정한 수정 정책은 **DRAFT·FAILED에서만, 업로더 본인 또는 같은 기관 원장**이다. PROCESSING·READY는 `409 / WORKSHEET_002`, 업무 COMPLETED는 `409 / WORKSHEET_003`, 다른 선생님은 `403 / AUTH_002`다.
- 없는/타 기관/삭제 학습지는 `404 / WORKSHEET_001`, 없는/타 기관 학생은 `404 / STUDENT_001`이다. 입력·JSON·ID 타입 오류는 `400 / COMMON_001`이다.
- 기관 범위 학습지 행을 잠근 뒤 삭제·완료(공통 정책)→권한→AI 상태→제공된 If-Match→학생 기관을 검사한다. If-Match는 현재 학습지 ETag 또는 *만 허용하고 빈 값·불일치는 412다. 변경을 flush한 후 응답 ETag를 생성한다.
- B1의 saveStatus는 UNSAVED / TEMP_SAVED / COMPLETED이며 B4 응답에 업무 저장 시각을 함께 노출한다. 제목·학생 지정은 업로더·AI 상태·업무 저장 상태·저장 시각·저장자를 변경하지 않는다. B5는 UNSAVED→TEMP_SAVED·반복 임시저장을, B6는 UNSAVED/TEMP_SAVED→COMPLETED를 구현했다.
- 기존 업로드·AI 생성 API에 제목·학생 필수 검증을 추가하지 않아 기존 자료의 생성 흐름을 유지한다. 신규 프런트 흐름에서는 업로드 후 B4를 호출하고 AI 생성을 요청한다.
- 원장은 다른 사용자의 임시저장을 삭제할 수 없다. 원장 자신이 업로드한 작업의 취급은 동일한 업로더 조건을 따른다.
- 삭제 API 처리 순서는 공통 정책(없음·타 기관·삭제됨 `404 / WORKSHEET_001`, 완료 `409 / WORKSHEET_003`)→업로더 본인(아니면 `403 / AUTH_002`)→임시저장 여부(저장 전 `UNSAVED`는 `409 / WORKSHEET_002`) 순이며, 학습지 행을 잠근 채 처리한다. 인증 없음은 401이다.
- 삭제는 임시저장만 허용한다. 저장하지 않은 작업은 삭제하지 않고 AI 처리 전이면 파일 교체(`PUT /worksheets/{worksheetId}/file`)로 사진을 바꾼다. 이미 삭제된 학습지를 다시 삭제하면 404이며 멱등 성공으로 취급하지 않는다. 삭제는 AI 처리 상태를 보지 않는다. 완료 결과물의 별도 삭제 정책은 미정이다.
- 저장하지 않은 작업의 만료 정리·생성 중 삭제 시 AI 작업 처리·완료 결과물의 별도 삭제·파일 보관 기간은 후속 범위다. 저장 검증과 편집·완료·삭제의 공통 부모 잠금은 A3·B5·B6에 연결했다.

#### B5 임시저장·저장본·이어쓰기 계약

> B6에서 `/saved-result`를 완료 저장본까지 확장했다. 아래 B5의 미완료 조건은 임시저장·이어쓰기에 적용하며, 완료 상태의 저장본 조회는 허용한다. 최종 저장·공통 차단은 이어지는 B6 계약을 따른다.

- **권한·상태**: AI READY·미삭제 기준이다. 임시저장·이어쓰기와 임시저장본 조회는 업로더만 가능하다. B7에서 완료 저장본 조회는 같은 기관 원장·선생님에게 공유한다. 권한 없는 임시저장 접근은 403, 없는/타 기관/삭제 학습지는 404, READY 이외 또는 완료 후 임시저장·이어쓰기는 409다. 완료 읽기 공유가 쓰기 권한을 확대하지 않는다.
- **필수 메타데이터**: 임시저장은 제목·학생을 요구한다. 기존 생성 API는 그대로이므로 메타데이터 없는 기존 READY 자료의 저장은 409다. 신규 흐름은 DRAFT/FAILED에서 B4 지정 후 생성한다. READY 메타데이터 수정 권한을 임의로 확장하지 않았다.
- **저장 응답**: 공통 `result`에 `{worksheetId, revision, etag, savedAt, savedBy, review}`. `revision`은 첫 저장 1, 이후 저장마다 증가하며 저장본 이력은 없다. `review`는 기존 ReviewResponse 구조다. HTTP ETag 및 `result.etag`는 `"saved_ws_{id}-r{revision}"`이고 `result.review.worksheet.etag`는 저장 당시 학습지 ETag다.
- **업무 시각**: `firstSavedAt`은 첫 저장에서만 지정한다. 반복 저장은 `lastSavedAt`·`lastSavedBy`와 마지막 저장본을 갱신한다. 업로더·AI 상태·completedAt은 변경하지 않는다. 날짜는 기존 애플리케이션 LocalDateTime 정책을 따르며 별도 시간대·통계 정책을 추가하지 않았다.
- **작업본/저장본 분리**: 편집 API와 `/review`는 현재 작업본을 사용한다. 개별 편집은 마지막 저장본·업무 저장 시각·revision을 바꾸지 않는다. `/saved-result`는 저장 당시 제목·학생 요약·AI 첨삭·반영 여부·좌표·코멘트·검증 결과를 반환하며 작업본을 덮어쓰지 않는다.
- **명시적 복원**: `/resume`만 현재 작업본을 마지막 저장 시점으로 복원한다. 첨삭의 내용·좌표·편집/반영 여부·삭제 상태·v1.1 현재 edit, 코멘트의 내용·색상·삭제 상태를 되돌린다. 저장 후 새로 추가한 행은 논리 삭제한다. 원본 AI 제안·설명·생성 아카이브·작성자·ID·작성 시각은 유지한다. 저장 시각·revision도 유지한다. 복원 후 최신 학습지·첨삭·코멘트 ETag가 포함된 ReviewResponse를 반환한다.
- **If-Match**: 임시저장은 생략 또는 `*`를 허용한다. 값을 제공하면 현재 학습지의 정확한 강한 ETag와 일치해야 한다. 이어쓰기는 현재 작업본의 정확한 ETag가 필수이며 생략·빈 값·`*`·약한 태그·저장본 태그·이전 태그는 412다. `/review` 또는 학습지 상세에서 현재 태그를 확보하고 사용자 복원 확인 후 요청한다. GET 저장본 조회만으로 복원하지 않는다.
- **원자성·편집 권한**: 저장·복원과 기존 첨삭·코멘트 쓰기는 먼저 같은 학습지 행을 잠근다. 자식 편집 시 학습지 ETag도 변경한다. TEMP_SAVED 이후에는 기존 편집 경로도 업로더만 허용해 다른 교사·원장의 우회 이어쓰기를 막는다. UNSAVED의 기존 같은 기관 편집 정책은 유지한다. 기존 첨삭·코멘트 조회 권한 전체를 업로더 전용으로 바꾸지는 않았다.
- **오류**: 저장본 없음 `404 / WORKSHEET_006`, 저장 당시 원본 파일 해시·최신 AI 생성 ID 또는 복원 대상 불일치 `409 / WORKSHEET_004`, 지원하지 않는 JSON 버전·손상 저장본 `500 / WORKSHEET_005`. 복원 중 오류가 나면 모든 변경을 롤백한다.
- **DB**: `worksheet_saved_results`에 학습지별 한 행만 보관한다. 기존 테이블 컬럼 추가는 없다. [변경 SQL](../infra/db/2026-11-27_worksheet_saved_results.sql), [ERD](<ERD v3 (corrections 확장).md>) 참조. 최종 완료는 B6, 홈·학생 결과물은 B7, 교사 집계는 A4 구현 계약을 따르며 PDF는 후속 범위다.

#### B6 최종 저장·완료 결과 조회·기존 쓰기 차단 계약

- **요청·응답**: `POST /worksheets/{worksheetId}/complete`, Body 없음. 현재 학습지의 정확한 강한 `If-Match`가 필수다. 200, B5와 같은 `{worksheetId, revision, etag, savedAt, savedBy, review}` 응답이다. HTTP ETag는 저장본 태그이며, 후속 학습지 요청에는 `result.review.worksheet.etag`를 사용한다.
- **권한·상태**: 같은 기관의 업로더 본인만 허용한다. 다른 교사·원장은 403, 없는/타 기관/삭제 학습지는 404, READY 이외는 409다. 원장도 본인 업로드에 한해 사용할 수 있다. AI 처리 상태 `status=READY`와 업무 상태 `saveStatus=COMPLETED`는 별개다.
- **필수 검증**: 제목·같은 기관 학생, 현재 원본 파일 해시와 일치하는 최신 AI 작업의 SUCCEEDED, 학습지 pageCount와 일치하는 1개 이상의 페이지, 1부터 연속된 페이지 번호, 양수 크기·비어 있지 않은 미리보기 경로를 검사한다. 미충족은 409 WORKSHEET_002다. 첨삭·코멘트 0개는 정상 결과로 허용하며, 검토 경고만으로 완료를 차단하는 추가 정책은 도입하지 않았다.
- **직접 완료**: UNSAVED에서 바로 COMPLETED로 전환하며 저장본을 새로 만든다. 첫 저장·최근 저장·완료 시각을 같은 시각으로 기록한다. TEMP_SAVED에서 완료하면 저장본을 현재 작업 내용으로 교체하고 최초 저장 시각은 유지한다. 두 경우 모두 저장자·최근 저장·완료 시각을 기록하고 revision을 1 증가시킨다. 이전 임시저장본을 자동 복원한 뒤 완료하지 않는다.
- **원자성**: 상태·시각·저장본은 같은 트랜잭션에서 저장한다. JSON 생성·DB 저장 중 실패하면 전부 롤백하여 기존 임시저장본도 유지한다. 기존 AI 아카이브·업로더·AI 상태는 보존한다.
- **반복 완료**: 학습지 잠금→업로더·READY→If-Match 순서로 검사한다. 이미 완료된 경우 현재 ETag로 재요청하면 기존 저장본을 200으로 반환하고 시각·revision·JPA 버전을 바꾸지 않는다. 최초 요청 전의 태그로 재시도하면 412다. 네트워크 오류 후에는 학습지 상세 또는 `/review`에서 현재 ETag를 다시 확인한다. 손상·누락된 완료 저장본은 자동 재생성하지 않는다.
- **완료 조회·되돌리기**: `/saved-result`는 B7 확정에 따라 완료 저장본을 같은 기관 원장·선생님에게 공유하며 임시저장본은 업로더만 조회한다. 조회·완료 재요청 시 현재 원본·최신 생성 ID·저장본의 학습지 ID·업무 상태 일치를 검사한다. `/review`는 기존처럼 현재 테이블을 조합한다. 완료 후 `save-draft`·`resume`은 409로 거부하며 COMPLETED→TEMP_SAVED 전환은 없다. 완료 재요청도 업로더만 허용한다.
- **공통 차단 연결(A3 통합)**: 삭제·완료 검사는 A의 `WorksheetAccessPolicy`로 통일했다. 첨삭·코멘트는 B의 `lockForEdit`가 내부에서 A의 `getForWrite`를 호출하고 READY·임시저장 업로더 검사를 추가한다. 파일 교체(일반·QR 공용)·설정·생성·재시도·삭제·QR 생성은 A의 공통 정책을 사용한다. 삭제는 `404 / WORKSHEET_001`, 완료 쓰기는 `409 / WORKSHEET_003`이다. 자식 편집의 부모 ETag 갱신과 기존 AI 단계별 제한은 유지한다. 최종 저장 재요청은 `getLockedReadable`로 완료 상태를 허용해 잠그고, 업로더·READY·If-Match 검사 후 기존 결과만 반환한다. 목록·통계는 후속 API에 같은 정책을 적용한다.
- **DB**: B6에서는 B1의 완료 관련 컬럼과 B5 저장본 테이블·schema_version=1을 재사용했다. B6의 신규 테이블·컬럼·DDL은 없다. B8부터는 코멘트 분류를 포함해 새 저장을 v2로 기록하고 v1 읽기·복원도 유지한다. 홈·학생 결과물 목록은 B7, 성장 그래프는 B8 구현이며 PDF·신규 첨삭 시작 화면은 후속 범위다.

### 5.8 홈 최근 작업 — 구현 1개(B7) / F04

권한: 같은 기관 원장·선생님. 학생 목록과 동일하게 본인 임시저장·기관 내 완료 작업을 노출한다. 원장도 다른 교사의 임시저장은 목록·저장본을 조회할 수 없다.

| 메서드 | 기능 | URL | param | 설명 |
| --- | --- | --- | --- | --- |
| GET | 최근 저장 작업 조회 | `/worksheets/recent` | Query: `page`, `size` | **구현(B7)**. 최근 작업일(updatedAt)·ID 내림차순. 학생 구분 없이 본인 임시저장·기관 완료 작업을 페이지 조회. 미저장·삭제·저장본 없는 행 제외. 200 |

- page는 0부터 기본 0, size는 기본 20·허용 1~100이다. 페이지 오프셋 초과·입력 오류는 400, 인증 없음은 401, 결과 없음은 빈 페이지와 200이다.
- 공통 목록 응답·대상 행·연락처 제외·snapshot 미로딩·canView/canResume은 5.4와 같다. 학생 목록은 최근 저장일, 홈은 추가 편집도 반영한 최근 작업일로 정렬한다.
- 저장본을 표시할 때 `/saved-result`를 호출한다. 임시저장의 저장 이후 편집을 되돌리는 작업은 현재 ETag와 사용자 확인을 거친 명시적 `/resume`에서만 수행한다. 완료는 읽기 전용이다.
- 학생 정보가 없는 과거 작업이나 저장본이 없는 행, 업로드 후 한 번도 저장하지 않은 UNSAVED는 목록에 포함하지 않는다. 새 테이블·컬럼·DDL은 없다.

## 6. 기존 API 변경 설계안

이 장은 기존 경로의 변경 현황과 목표다. B4–B6 구현 표시가 있는 상세·검토·편집·저장 연계는 적용했으며 나머지는 각 행의 남은 작업을 따른다.

| 기존 API | 현재 동작 | 변경 목표·남은 결정 |
| --- | --- | --- |
| `POST /auth/signup` | 비밀번호 최소 8자만 검사 | 공통 정책인 최대 20자와 영문·숫자·특수문자 조합 검증 추가 |
| `POST /auth/logout` | 선택 Refresh Token 폐기 | 현재 세션 종료의 범위와 Access Token 처리 정의. 필요 시 요청 필수성·서버 폐기 방식 보완 |
| `POST /invitations` | email만 입력 | 목표 요청 `name*`, `email*`. 초대 이름 저장·응답·가입 연계, 메일 전송 실패와 초대 대기 노출 정책 정의 |
| `GET /worksheets/{worksheetId}` | **B4–B6 구현**: 제목·학생·업무 상태·저장 시각 제공, 삭제 제외. 편집·명시적 저장 시 학습지 ETag 갱신 | 저장본 조회는 별도 API |
| `GET /worksheets/{worksheetId}/review` | **B4–B6 구현**: 메타데이터를 포함한 현재 작업본. 저장본 조회·이어쓰기는 별도 API | 최종 저장 당시 표현은 `/saved-result` 사용 |
| 기존 학습지·하위 자원 조회 | **A3 구현**: 상세·설정·원본 파일·이미지·검토·첨삭·코멘트·생성 상태 조회가 삭제 학습지를 `404 / WORKSHEET_001`로 숨김 | 다른 사용자의 조회 범위(홈·학생 결과물 등)는 후속 API에서 같은 정책을 사용 |
| 기존 학습지·첨삭·코멘트 쓰기 | **A3·B5·B6 구현**: 공통 정책으로 부모 행 잠금·삭제(404)·완료(`409 / WORKSHEET_003`) 차단. 임시저장 업로더 제한·자식 편집 시 부모 ETag 갱신 추가 | 완료 읽기·완료 재요청은 별도 잠금 조회로 허용 |

### 6.1 삭제·완료 공통 접근 정책 — 구현(A3)

학습지를 조회하는 모든 서비스는 `WorksheetAccessPolicy`를 거친다.

| 구분 | 메서드 | 삭제된 학습지 | 완료된 학습지 | 락 |
| --- | --- | --- | --- | --- |
| 읽기 | `getReadable` | `404 / WORKSHEET_001` | 허용 | 없음 |
| 쓰기 | `getForWrite` | `404 / WORKSHEET_001` | `409 / WORKSHEET_003` | 학습지 행 비관적 락 |

- 삭제 검사가 완료 검사보다 먼저이므로 삭제된 완료 학습지는 404다. 다른 기관 학습지도 404로 숨긴다.
- 쓰기는 같은 학습지의 삭제·최종 저장·편집이 동시에 들어와도 순서대로 처리되도록 행을 잠근 채 검사한다.
- 정책 검사는 요청 본문·하위 자원 검증보다 먼저 적용된다. 이후 기존 AI 상태(READY 등)·If-Match 검사가 이어진다.
- 새 API를 추가할 때는 학습지를 직접 조회하지 않고 이 정책의 메서드로 찾는다.

완료 결과물에 적용한 쓰기 API는 다음과 같다. 모두 `409 / WORKSHEET_003`이다.

| 메서드 | URL | 비고 |
| --- | --- | --- |
| PATCH | `/worksheets/{worksheetId}` | 제목·학생 지정 (이전에는 `WORKSHEET_002`) |
| PUT | `/worksheets/{worksheetId}/file` | 기존 DRAFT·FAILED 조건과 함께 적용 |
| PUT | `/worksheets/{worksheetId}/settings` | 기존 DRAFT·FAILED 조건과 함께 적용 |
| PATCH | `/worksheets/{worksheetId}/corrections/{correctionId}/apply` | 반영 여부 변경 |
| PATCH | `/worksheets/{worksheetId}/corrections/{correctionId}` | 첨삭 수정 |
| DELETE | `/worksheets/{worksheetId}/corrections/{correctionId}` | 첨삭 항목 삭제 |
| POST | `/worksheets/{worksheetId}/corrections/{correctionId}/restore` | 첨삭 항목 복원 |
| POST | `/worksheets/{worksheetId}/comments` | 수기 첨삭 추가 |
| PATCH | `/worksheets/{worksheetId}/comments/{commentId}` | 수기 첨삭 수정 |
| DELETE | `/worksheets/{worksheetId}/comments/{commentId}` | 수기 첨삭 삭제 |
| POST | `/worksheets/{worksheetId}/generations` | AI 생성 요청 |
| POST | `/worksheets/{worksheetId}/generations/{generationId}/retry` | AI 생성 재시도 |
| POST | `/auth/qr-sessions` (`worksheetId` 지정 시) | 사진 교체용 QR 생성 |

- 읽기 API(상세·설정·원본 파일·이미지·검토·첨삭·코멘트·생성 상태 조회)는 삭제만 거부하고 완료는 허용한다.
- 같은 요청 키를 다시 보낸 AI 생성 요청도 완료 이후에는 이전 응답 대신 409를 받는다.
- 완료 결과물의 별도 삭제 정책은 미정이다. 완료된 학습지는 삭제 API에서도 `409 / WORKSHEET_003`이다.

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
| D1 | 완료 결과물은 수정 불가 | 구현(A3). 쓰기 API 13개 서버 차단 (6.1) |
| D2 | 임시저장 작업 삭제 가능 | 구현(A3). 임시저장(`TEMP_SAVED`)만 삭제 |
| D3 | 삭제는 soft delete | 구현(A3). deleted_at 기록, 연결 데이터 보존 |
| D4 | 제목·학생 지정과 임시/최종 저장 포함 | F05·F07 연관 백엔드도 이번 범위 |
| D5 | 완료 → 임시저장 되돌리기 없음 | 되돌리기 API를 추가하지 않는 방향. 원문에 개발 해석·확인 필요 주석이 남아 있으므로 해소된 것으로 기록하지 않음 |
| D6 | 임시저장 삭제는 업로더 본인만 | 구현(A3). 원장 역할로 타인 작업 삭제 허용하지 않음 |
| D7 | 월별 첨삭 수에 임시저장 포함 | 임시저장+완료, 삭제 제외. 세부 집계 기준 미정 |

전달용 문서는 이번 임시저장 삭제 API의 완료 상태 거부와 완료 결과물 삭제 정책 자체의 미정을 함께 적고 있다. 이번 삭제는 임시저장 전용으로 설계하고 완료 결과물의 별도 삭제 기능은 확정하지 않는다.

### 8.2 상태별 목표 동작

| 구분 | 임시저장 | 완료 |
| --- | --- | --- |
| 편집 | B5 임시저장 이후 업로더 본인만 가능 | 불가 |
| 이번 삭제 API | 업로더 본인만 가능 (`UNSAVED`는 불가) | 거부. 별도 완료 삭제 정책은 미정 |
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
| 저장 | UNSAVED 목록 노출, 자동 저장·보관 기간. 임시저장·저장본·복원은 B5, 최종 저장 필수 검증·버전·완료 조회·쓰기 차단은 B6 구현 계약을 따름 |
| 삭제 | 생성 중 삭제 시 AI 작업 처리, 완료 별도 삭제, 저장하지 않은 작업의 만료 정리, 파일 보관 기간. 반복 삭제와 삭제 자원 접근은 404로 확정 |
| 접근 | B7 목록·저장본은 임시저장 업로더 전용·완료 기관 공유로 확정. 다른 일반 조회 API의 기존 기관 정책을 전면 변경하지 않음 |
| 월별 집계 | 5.5에서 확정(처음 저장한 시각, 한국 시간, 업로더, 중복 없음). 이력 항목의 추가 필드는 화면 확정 후 조정 |
| 학생 | 학년 범위, 이름 수정, 연락처 열람·마스킹·보관/파기. 중복 후보·동명이인 확인 등록, 미배정 필터·기본 정보 PATCH는 5.1에 구현 |
| 메모 | 수정·삭제 범위와 작성자 권한·감사 이력 |
| 성장 리포트 | B8 그래프 및 B10·B11 수동 AI 인사이트 계약은 5.3에 구현 반영. 자동 생성·예약 갱신·이력·우선순위 대기열·다중 인스턴스 실행 제어는 후속 범위 |
| 교사 | 이메일 수정, 이력 전체 기간, 퇴사 코드·취소·재입사·기존 요청 차단, 초대 대기 행의 상세 진입, 만료 초대 정리 (원장 목록 포함·입사일 입력은 5.5에서 정함) |
| 초대 | 만료·재발송·취소·중복 이메일 정책, 초대 이름과 가입 이름, 메일 실패와 목록 상태 |
| 계정·기관 | 이메일 수정, 비밀번호 변경 시 다른 세션 종료, 로그아웃의 Access Token, 구독 만료 쓰기 제한 |
| 출력 | PDF 생성 주체·동기/비동기·저장 버전 일치·실패 재시도 |

등록 연락처의 선택 여부는 v2의 8.2.5·8.2.6에서 확인된다. 전달용 문서의 연락처 필수 여부 확인 필요 문구를 이유로 임의 필수화하지 않는다.

## 9. 연동 흐름과 검증 범위

### 9.1 현재 가능한 첨삭 흐름

1. 초대 검증 → 회원가입 → 로그인, 또는 기존 계정 로그인.
2. 일반 업로드 또는 QR 생성 → 스캔·승인·토큰 발급 / 같은 사용자 link → 업로드·교체.
3. 학습지·보정 이미지 조회 → 제목·학생 지정(B4, 업로더 또는 같은 기관 원장) 및 영역별 설정 저장. 제목·학생 지정은 기존 생성 API의 새 필수 조건으로 강제하지 않는다.
4. 생성 요청 → 생성 상태 폴링 또는 SSE 알림 후 상태 조회.
5. READY에서 검토 조회 → AI 항목 수정·반영 여부·삭제·복원, 수기 첨삭 등록·수정·삭제.
6. 제목·학생이 지정된 READY 학습지를 업로더가 `save-draft` → TEMP_SAVED 및 마지막 저장본 보관.
7. 추가 편집과 별개로 `saved-result`에서 마지막 저장본 조회 → 필요할 때 현재 작업본 ETag를 받아 명시적 `resume` → 마지막 저장 내용으로 편집 재개.
8. 현재 학습지 ETag로 `complete`(B6) → 필수 검증·최종 저장본 기록·COMPLETED → 저장본 조회 허용, 기존 내용 변경 차단. 임시저장 없이 직접 완료도 가능하다.

9. 학생 프로필에서 `/students/{studentId}/worksheets`, 홈에서 `/worksheets/recent`(B7) 조회 → `/saved-result`로 저장본 표시. 임시저장은 본인만, 완료는 기관 공유. 복원은 명시적 resume으로만 수행한다.

성장 그래프 조회는 B8에서 구현했으며 PDF 연결은 후속 범위다. 개별 첨삭 DB 변경과 B5/B6의 명시적 임시/최종 저장은 별개다.

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
