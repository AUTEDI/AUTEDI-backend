# AUTEDI 백엔드 아키텍처 및 정책 의사결정 기록 (ADR / Decisions)

- **문서 버전**: v1.0
- **작성일**: 2026-10-29
- **대상 프로젝트**: `AUTEDI-backend` (Spring Boot 4.x / Java 21 / MySQL 8.0 / Hibernate 6)
- **문서 목적**: AUTEDI 백엔드 시스템 구축 과정에서 논의되고 확정된 핵심 아키텍처 원칙, 도메인 보안/비즈니스 정책, AI 파이프라인 설계 및 JPA 엔티티 모델링 기준을 단일 진실 공급원(Single Source of Truth)으로 문서화하여 개발 정합성을 유지한다.

---

## 목차 (Table of Contents)

1. [개요 및 시스템 아키텍처 철학](#1-개요-및-시스템-아키텍처-철학)
2. [테넌시 및 회원/인증 도메인 정책](#2-테넌시-및-회원인증-도메인-정책)
   - 2.1 학원(Academy) 단위 멀티 테넌트 격리 및 구독 상태 관리
   - 2.2 원장(DIRECTOR) 계정 사전 프로비저닝 및 일반 가입 차단
   - 2.3 선생님(TEACHER) 단건 초대 프로세스 및 재초대 무효화(REVOKED) 정책
   - 2.4 회원가입 위변조 방어 및 서버 강제 주입 메커니즘
   - 2.5 가입 완료 직후 JWT 미발급(로그인 유도) 정책
   - 2.6 학원 구독 만료 시 접근 제어 정책 (Read-Only 허용 vs Write 차단)
3. [토큰 및 보안 아키텍처](#3-토큰-및-보안-아키텍처)
   - 3.1 CSPRNG 256-bit Base64 URL-safe 원본 토큰 생성
   - 3.2 DB SHA-256 단방향 해시 저장 원칙
   - 3.3 Stateless Access Token 클레임 설계
   - 3.4 Refresh Token Rotation (RTR) 및 토큰 탈취 방어
4. [AI 첨삭 파이프라인 변경 시 영향도 분석](#4-ai-첨삭-파이프라인-변경-시-영향도-분석)
   - 4.1 파이프라인 후보 비교 (OCR+LLM vs Gemini+LLM vs 단일 Multi-modal LLM)
   - 4.2 도메인 I/O 기준 모델링을 통한 DB 테이블 구조 불변성
   - 4.3 단일 LLM 채택 시 컬럼별 구체적 변경점
   - 4.4 Gemini Vision 정규화 좌표(0~1000)의 픽셀(px) 변환 수학적 원리
   - 4.5 `CorrectionAiClient` 인터페이스 추상화 및 Mock 연동 전략
5. [JPA 엔티티 설계 원칙](#5-jpa-엔티티-설계-원칙)
   - 5.1 BaseCreatedTimeEntity vs BaseTimeEntity 2단계 분리 (불변/이력 테이블 3종)
   - 5.2 단방향 ManyToOne(fetch = LAZY) 원칙
   - 5.3 Hibernate 6 네이티브 JSON 매핑 (`@JdbcTypeCode(SqlTypes.JSON)`)
6. [의사결정 매트릭스 및 향후 구현 가이드](#6-의사결정-매트릭스-및-향후-구현-가이드)

---

## 1. 개요 및 시스템 아키텍처 철학

AUTEDI는 영어 교육기관(학원, 영어 도서관)을 대상으로 학생의 손글씨 글쓰기 과제를 AI로 자동 분석·첨삭하고 교사의 피드백 업무를 지원하는 B2B SaaS 플랫폼이다.

백엔드 시스템 설계의 핵심 가치는 다음과 같다:
1. **엄격한 데이터 격리 (Tenancy Security)**: B2B 학원 비즈니스의 특성상 타 학원의 학생 과제물 및 피드백 데이터가 노출되는 것은 치명적인 보안 사고이다. 모든 데이터 접근은 학원(`academy_id`) 테넌트 경계를 통과해야 한다.
2. **도메인 중심 I/O 모델링 (Domain-Centric Design)**: 외부 AI 엔진(OCR 엔진, Gemini, GPT, Claude 등)의 변화나 교체에 핵심 비즈니스 DB 스키마가 흔들리지 않도록 도메인 입출력 규격을 기반으로 영속성 계층을 캡슐화한다.
3. **심층 방어 (Defense in Depth)**: 클라이언트가 전달하는 식별자나 권한 파라미터를 일절 신뢰하지 않으며, 서버의 인증/인가 컨텍스트와 암호학적 해시 검증을 통해서만 상태 변경을 승인한다.

---

## 2. 테넌시 및 회원/인증 도메인 정책

### 2.1 학원(Academy) 단위 멀티 테넌트 격리 및 구독 상태 관리
- **격리 모델**: 풀드 데이터베이스(Shared DB, Shared Schema) 내에서 모든 테넌트 종속 엔티티(`users`, `worksheets`, `user_invitations`)에 `academy_id` 외래키를 필수 컬럼으로 설정하여 논리적 멀티 테넌시를 구현한다.
- **구독 상태 (`SubscriptionStatus`)**:
  - `ACTIVE`: 정상 유료 구독 상태. 모든 학습지 업로드, 첨삭 설정, AI 첨삭 생성, 교사 코멘트, 초대장 발송 등 전체 기능 사용 가능.
  - `EXPIRED`: 구독 기간 만료(`plan_expires_at < NOW()`). 서비스 쓰기 기능 차단 (상세 정책 2.6 참조).
  - `SUSPENDED`: 관리자(운영사)에 의한 직권 정지 상태 (미납, 악용 등). 로그인 및 모든 API 접근 차단.
- **테넌트 검증 원칙**: 모든 API 요청 처리 시 Security Context의 `academyId`와 대상 리소스의 `academy_id` 일치 여부를 서비스/레포지토리 계층에서 교차 검증하며, 불일치 시 자원 존재 여부와 무관하게 `404 NOT_FOUND` 또는 `403 ACCESS_DENIED`를 응답하여 자원 스캐닝을 방지한다.

### 2.2 원장(DIRECTOR) 계정 사전 프로비저닝 및 일반 가입 차단
- **결정**: 원장(`Role.DIRECTOR`)은 퍼블릭 회원가입 API(`/api/auth/signup`)를 통해 스스로 계정을 생성할 수 없다.
- **이유**:
  - AUTEDI는 B2B 영업 및 계약을 통해 도입되는 유료 SaaS이다. 임의 사용자가 무단으로 학원을 생성하거나 원장 권한을 획득하는 것을 원천 차단한다.
  - 원장 계정과 학원 엔티티는 사내 운영 관리자 툴, 배치 스크립트, 또는 개발 시드 데이터(`DevDataInitializer`)를 통해서만 사전 프로비저닝된다.
- **보안 효과**: 권한 승격 공격(Privilege Escalation) 벡터 자체를 제거한다.

### 2.3 선생님(TEACHER) 단건 초대 프로세스 및 재초대 무효화(REVOKED) 정책
- **단건 초대 발송**: 원장(`DIRECTOR`) 권한을 가진 사용자만이 소속 학원의 선생님을 이메일로 1건씩 초대할 수 있다 (`POST /api/invitations`).
- **유효기간 24시간**: 발급된 초대 링크는 보안 위험을 최소화하기 위해 생성 시점으로부터 정확히 24시간(`LocalDateTime.now().plusHours(24)`) 동안만 유효하다.
- **재초대 시 무효화 (Revocation on Re-invite)**:
  - 동일한 이메일로 새로운 초대를 발송하는 경우, DB에 남아 있는 해당 이메일의 기존 `PENDING` 상태 초대장은 즉시 `REVOKED`로 변경한다.
  - ```java
    List<UserInvitation> existingInvitations = userInvitationRepository
            .findAllByEmailAndStatus(request.email(), InvitationStatus.PENDING);
    existingInvitations.forEach(UserInvitation::revoke);
    ```
  - 이전 초대 링크는 즉시 무효화되며 오직 가장 최근에 수신한 초대장으로만 가입이 허용된다.

### 2.4 회원가입 위변조 방어 및 서버 강제 주입 메커니즘
- **클라이언트 페이로드 제한**: 선생님 회원가입 요청 DTO(`SignupRequest`)는 오직 다음 필드만 수신한다:
  - `token` (초대 원본 토큰), `email`, `password`, `name`
  - 클라이언트는 `role`이나 `academy_id` 필드를 요청 본문에 포함할 수 없으며, 설령 전달하더라도 DTO 역직렬화 단계에서 무시된다.
- **서버 검증 및 강제 주입**:
  1. 클라이언트가 제출한 `token`의 SHA-256 해시값으로 `user_invitations` 테이블을 조회한다.
  2. 동시성 제어 및 다중 요청에 의한 중복 가입을 방지하기 위해 **비관적 쓰기 락(Pessimistic Lock)**(`PESSIMISTIC_WRITE`)을 사용하여 초대장 레코드를 잠근다.
  3. 초대장의 상태가 `PENDING`인지, 유효기간(`expires_at`)이 지나지 않았는지 확인한다.
  4. 요청된 `email`과 초대장에 기록된 수신 이메일이 일치하는지 대소문자 무시 비교(`equalsIgnoreCase`)를 수행한다.
  5. 검증 통과 시, 신규 생성되는 `User` 엔티티의 `academy`는 초대장에 바인딩된 `invitation.getAcademy()`, `role`은 무조건 `Role.TEACHER`, `invitedBy`는 `invitation.getInvitedBy()`로 서버가 직접 강제 주입한다.
  6. 가입 완료 즉시 초대장 상태를 `ACCEPTED`로 전이시키고 `accepted_at`을 기록한다.

### 2.5 가입 완료 직후 JWT 미발급(로그인 유도) 정책
- **결정**: 회원가입 API(`/api/auth/signup`) 성공 시 Access Token / Refresh Token을 응답에 포함하지 않고 `201 Created`와 기본 사용자 프로필 정보(`SignupResponse`)만 반환한다.
- **이유**:
  1. **자격 증명 검증 보장**: 사용자가 등록한 비밀번호로 정규 로그인(`/api/auth/login`) 절차를 밟도록 유도함으로써 비밀번호 오입력에 의한 계정 고립을 즉시 인지하도록 한다.
  2. **세션/토큰 수명주기 책임 분리**: 토큰 발급 및 Refresh Token 레코드 생성, `last_login_at` 갱신 로직을 `AuthService.login` 한 곳으로 응집시켜 코드 중복 및 불일치를 방지한다.
  3. **초대 링크 탈취 대응**: 초대 URL이 제3자에게 노출되어 악의적 가입이 시도되더라도, 별도의 로그인 절차를 거치지 않는 한 즉각적인 세션 탈취가 성립되지 않는다.

### 2.6 학원 구독 만료 시 접근 제어 정책 (Read-Only 허용 vs Write 차단)
- **배경**: 학원의 유료 플랜이 만료(`EXPIRED`)되었을 때 시스템 전체 접근을 즉시 차단하면, 기존 학생들의 과제 이력 확인, 성적표 인쇄, 학부모 상담 등 필수 업무가 마비되어 고객 불만이 극대화된다.
- **정책 분리**:
  - **허용 (Read-Only)**:
    - 원장 및 교사의 시스템 로그인
    - 기존 업로드된 학습지 목록 및 상세 조회
    - 기존 생성된 AI 첨삭 결과 및 교사 코멘트 열람
  - **차단 (Write / Paid Features - `403 FORBIDDEN` / `ACADEMY_SUBSCRIPTION_INACTIVE`)**:
    - 신규 학습지 파일 업로드 (`POST /api/v1/worksheets`)
    - 첨삭 영역 및 수준 설정 변경 (`PUT /api/v1/worksheets/{id}/settings`)
    - AI 첨삭 생성 요청 (`POST /api/v1/worksheets/{id}/generations`)
    - 신규 선생님 초대장 발송 (`POST /api/invitations`)
- **구현 방식**: 쓰기/생성 API 진입 시점에 `director.getAcademy().isActive()` 또는 테넌트 구독 검증 인터셉터/AOP를 통과시켜 유료 연산 요청을 차단한다.

---

## 3. 토큰 및 보안 아키텍처

```
[클라이언트]                     [인증 서버]                     [MySQL DB]
    |                                |                               |
    |---- 1. 초대 생성 요청 --------->|                               |
    |    (email: teacher@...)        |-- SecureRandom 256-bit 생성    |
    |                                |-- SHA-256 해시 계산           |
    |                                |-- DB에 hash 저장 ------------->| token_hash: e3b0c44...
    |<--- 2. 이메일 링크 발송 --------| (원본 토큰 URL 파라미터 전달)    |
    |    (?token=raw_token_xyz)      |                               |
    |                                |                               |
    |---- 3. 회원가입 요청 ---------->|                               |
    |    (token: raw_token_xyz)      |-- SHA-256(raw_token_xyz) 계산 |
    |                                |-- SELECT ... WHERE token_hash->| 일치 레코드 확인
```

### 3.1 CSPRNG 256-bit Base64 URL-safe 원본 토큰 생성
- **난수 생성기**: 예측 불가능성을 보장하기 위해 Java의 암호학적으로 안전한 의사 난수 생성기인 `java.security.SecureRandom`을 사용한다.
- **엔트로피**: 32바이트 (256비트)의 고엔트로피 바이트 배열을 추출한다.
- **인코딩**: 웹 URL 파라미터 및 이메일 링크 전달 시 특수문자(`+`, `/`, `=`)로 인한 인코딩 깨짐을 방지하기 위해 **Base64 URL-safe without padding**(`Base64.getUrlEncoder().withoutPadding()`) 방식을 적용한다.
- **구현체**: `TokenHashUtil.generateRawToken()`

### 3.2 DB SHA-256 단방향 해시 저장 원칙
- **원칙**: 원본 토큰 문자열(초대 토큰, Refresh Token)은 **데이터베이스에 절대 평문으로 저장하지 않는다.**
- **저장 규격**: 원본 토큰을 표준 `SHA-256` 해시 함수로 다이제스트한 64자리 16진수 소문자 문자열(`HexFormat.of().formatHex(...)`)을 컬럼 타입 `CHAR(64)`에 유니크 인덱스로 저장한다.
  - `user_invitations.token_hash` (`CHAR(64) UNIQUE`)
  - `refresh_tokens.token_hash` (`CHAR(64) UNIQUE`)
- **보안 효과**:
  - 데이터베이스 덤프 유출, 내부자 유출, 또는 SQL Injection 취약점이 발생하더라도 역산이 불가능(Pre-image Resistance)하여 공격자가 초대 링크를 탈취하거나 유효한 Refresh Token으로 위장할 수 없다.

### 3.3 Stateless Access Token 클레임 설계
- **토큰 포맷**: HMAC-SHA256(`io.jsonwebtoken:jjwt`) 서명 기반의 경량 무상태(Stateless) JWT.
- **수명**: 기본 1시간 (`3,600,000 ms`).
- **클레임 명세**:
  ```json
  {
    "sub": "1001",              // userId (회원 PK)
    "academyId": 50,            // 테넌트 격리용 학원 PK
    "email": "teacher@autedi.com",
    "role": "TEACHER",          // 권한 인가용 역할
    "iat": 1730190000,
    "exp": 1730193600
  }
  ```
- **설계 이점**:
  - 매 API 요청마다 `JwtAuthenticationFilter`가 토큰을 파싱하여 `CustomUserPrincipal`을 생성한다.
  - 컨트롤러 및 서비스에서 DB 추가 조회 쿼리 없이 요청자의 `userId`, `academyId`, `role`을 즉시 참조할 수 있어 DB 커넥션 부하를 대폭 절감한다.

### 3.4 Refresh Token Rotation (RTR) 및 토큰 탈취 방어
- **단발성 원칙 (One-Time Use)**: 발급된 Refresh Token은 1회 사용 시 즉시 폐기된다.
- **회전(Rotation) 시퀀스 (`AuthService.refresh`)**:
  1. 클라이언트가 제출한 Refresh Token의 해시값으로 `refresh_tokens` 조회.
  2. 만료 여부(`expires_at`) 및 폐기 여부(`revoked_at IS NULL`) 확인.
  3. 사용된 토큰 레코드에 즉시 폐기 시각(`revoked_at = NOW()`)을 기록하여 무효화.
  4. 새로운 256-bit SecureRandom 원본 Refresh Token 생성 및 신규 해시 레코드 DB 저장.
  5. 새로운 Access Token과 신규 원본 Refresh Token 쌍을 클라이언트에 반환.
- **재사용 탐지 (Reuse Detection)**: 만약 이미 폐기(`revoked_at != NULL`)된 Refresh Token으로 재발급 요청이 인입될 경우, 이는 토큰이 제3자에게 탈취되었거나 비정상 리플레이 공격이 발생한 징후로 간주하여 즉시 `401 EXPIRED_TOKEN` 예외를 던지고 보안 경고 로그를 기록한다.

---

## 4. AI 첨삭 파이프라인 변경 시 영향도 분석

```
[클라이언트/프론트엔드]
         |
         | (1) 첨삭 생성 요청 (POST /worksheets/{id}/generations)
         v
+--------------------------------------------------------------------------+
| Spring Boot 백엔드 서비스 (`WorksheetGenerationService`)                    |
|                                                                          |
|   +------------------------------------------------------------------+   |
|   | <<Interface>> CorrectionAiClient                                 |   |
|   +------------------------------------------------------------------+   |
|             ^                                            ^               |
|             | (구현체 교체)                               | (테스트/로컬)    |
|   +--------------------------+                 +---------------------+   |
|   | GeminiVisionAiClient     |                 | MockAiClient        |   |
|   | (단일 Multi-modal LLM)    |                 | (고정 Mock 데이터)   |   |
|   +--------------------------+                 +---------------------+   |
+--------------------------------------------------------------------------+
         |
         | (2) 도메인 엔티티 변환 (Pixel 좌표 계산: 0~1000 -> WxH)
         v
[MySQL DB: corrections 테이블]
(id, worksheet_id, page_id, area, content, explanation, regions: [{"x", "y", "width", "height"}])
```

### 4.1 파이프라인 후보 비교

| 파이프라인 구조 | 장점 | 단점 | 현재 채택 방향 |
| :--- | :--- | :--- | :--- |
| **Option 1: 2-Stage (전통 OCR + 별도 LLM)**<br>(예: CLOVA OCR / GCP Vision ➔ GPT-4o) | 글자 인식 좌표 정밀도 우수, 한국어/특수서체 인식 안정 | 외부 API 2회 호출로 인한 높은 지연시간(Latency 8~15초), 2중 비용 발생, OCR 오인식 시 LLM 문맥 왜곡 전파 | 검토 단계 |
| **Option 2: 2-Stage (Gemini Vision OCR + 별도 LLM)** | Gemini의 시각 인식 활용, 단어 단위 바운딩 박스 추출 | 비용 및 네트워크 홉수 여전히 2회 | 과도기적 모델 |
| **Option 3: Single Multi-modal LLM**<br>(예: Gemini 1.5 Pro/Flash 단일 호출) | **단 1회 호출로 인식·분석·첨삭·좌표 추출 동시 완료**, 처리 속도(3~5초) 및 비용 획기적 절감, 글의 전체 맥락을 고려한 첨삭 정확도 향상 | 모델이 반환하는 좌표(Bounding Box)의 미세 오차 튜닝 필요 | **★ 최종 권장 및 메인 설계 방향** |

### 4.2 도메인 I/O 기준 모델링을 통한 DB 테이블 구조 불변성
- **결정**: AI 파이프라인이 Option 1(2-Stage)에서 Option 3(단일 Multi-modal LLM)으로 변경되더라도 **DB 스키마 구조(`worksheets`, `worksheet_pages`, `worksheet_generations`, `corrections`, `comments`)는 전혀 변경하지 않는다.**
- **이유 (Domain I/O Isolation)**:
  - 데이터베이스는 AI 파이프라인의 내부 중간 연산 과정을 저장하는 것이 아니라, **서비스의 도메인 계약(Contract)**을 저장하는 저장소이다.
  - **입력(Input)**: 학습지 원본 이미지/PDF (`worksheets`, `worksheet_pages`) 및 교사의 첨삭 설정 (`worksheet_settings`).
  - **출력(Output)**: 첨삭된 결과 텍스트, 설명, 페이지 내 대상 위치 좌표 배열 (`corrections`, `comments`).
  - 중간 과정에서 별도의 OCR 텍스트 추출 단계를 거치든, Gemini가 한 번에 첨삭 결과를 내놓든, 최종적으로 영속화되어 프론트엔드로 전달되는 데이터 포맷은 `{ area, original_text, content, explanation, regions }`로 동일하다.

### 4.3 단일 LLM 채택 시 컬럼별 구체적 변경점
테이블 DDL 스키마 변경 없이 데이터 운용 방식만 다음과 같이 최적화된다:

1. `worksheet_pages.ocr_result` (`JSON`):
   - *2-Stage*: 외부 OCR 엔진이 반환한 수천 라인의 원시 JSON 전체를 저장.
   - *단일 LLM*: Gemini가 반환한 전체 인식 텍스트 블록/좌표 요약본을 저장하거나, 별도 원시 OCR 데이터가 불필요한 경우 축약된 텍스트 맵 또는 `NULL` 처리.
2. `worksheet_pages.ocr_file_hash` (`CHAR(64)`):
   - *2-Stage*: 외부 고비용 OCR API 중복 호출을 막기 위한 파일 해시 캐시 키로 사용.
   - *단일 LLM*: 이미지 변경 감지용 또는 단일 LLM 결과 캐싱 키로 용도 전환 (스키마 변경 없음).
3. `worksheet_generations.current_stage` (`VARCHAR(50)`):
   - *2-Stage 상태 전이*: `INITIALIZING` ➔ `OCR_PROCESSING` ➔ `LLM_PROCESSING` ➔ `SAVING_RESULTS` ➔ `COMPLETED`.
   - *단일 LLM 상태 전이*: `INITIALIZING` ➔ `AI_ANALYZING` (또는 `VISION_LLM_PROCESSING`) ➔ `SAVING_RESULTS` ➔ `COMPLETED`.
   - 컬럼 타입이 문자열(`VARCHAR`)이므로 스키마 변경 없이 상태 열거형(Enum) 값 정의만으로 완벽 대응.

### 4.4 Gemini Vision 정규화 좌표(0~1000)의 픽셀(px) 변환 수학적 원리
- **Gemini 정규화 좌표계**:
  - Gemini Vision API는 이미지의 해상도나 왜곡과 무관하게 2D 바운딩 박스를 0부터 1000까지의 상대적 정규화 값(Normalized Integer)으로 반환한다.
  - 반환 포맷: `[box_2d: ymin, xmin, ymax, xmax]` (범위: 0 ~ 1000).
- **변환 필요성**:
  - 프론트엔드 웹 캔버스 또는 오버레이 SVG는 업로드된 원본 이미지의 실제 해상도(`worksheet_pages.width`, `height`)를 기준으로 정확한 픽셀 단위 렌더링 박스를 그려야 한다.
- **수학적 변환 공식**:
  $$x_{px} = \frac{xmin}{1000.0} \times width$$
  $$y_{px} = \frac{ymin}{1000.0} \times height$$
  $$width_{px} = \frac{xmax - xmin}{1000.0} \times width$$
  $$height_{px} = \frac{ymax - ymin}{1000.0} \times height$$
- **저장 형식**:
  - 변환된 픽셀 좌표는 `corrections.regions` JSON 컬럼에 다음과 같은 구조로 저장된다:
  ```json
  [
    {
      "x": 142,
      "y": 380,
      "width": 215,
      "height": 48
    }
  ]
  ```

### 4.5 `CorrectionAiClient` 인터페이스 추상화 및 Mock 연동 전략
- **DIP(의존성 역전 원칙) 적용**: 비즈니스 서비스 계층은 구체적인 AI SDK(Google GenAI, LangChain, OpenAI 등)를 직접 참조하지 않고 순수 도메인 인터페이스에 의존한다.
- **인터페이스 정의 예시**:
  ```java
  public interface CorrectionAiClient {
      CorrectionAnalysisResult analyzeWorksheet(
              WorksheetPage page, 
              List<WorksheetSetting> settings
      );
  }
  ```
- **환경별 구현체 주입**:
  - `MockCorrectionAiClient` (`@Profile({"local", "test"})`):
    - 외부 네트워크 호출 없이 사전에 정의된 더미 첨삭 목록(문법 오류 2건, 어휘 개선 1건)과 샘플 바운딩 박스 좌표를 즉시 반환.
    - 로컬 개발 속도 극대화, 외부 API 크레딧 비용 0원 유지, 신속한 통합 테스트 보장.
  - `GeminiCorrectionAiClient` (`@Profile("prod")`):
    - 실제 Google Gemini 1.5 Pro / Flash Multi-modal Vision API를 호출하여 프롬프트 실행 및 결과 파싱 수행.

---

## 5. JPA 엔티티 설계 원칙

### 5.1 BaseCreatedTimeEntity vs BaseTimeEntity 2단계 분리 (불변/이력 테이블 3종)
대부분의 엔티티는 생성 시점과 수정 시점을 모두 추적해야 하지만, **불변(Immutable) 성격을 갖거나 수정이 발생하지 않는 테이블에 `updated_at`을 무분별하게 두는 것은 도메인 의미를 왜곡하고 데이터 신뢰성을 훼손한다.**

따라서 2단계 상속 구조로 분리하여 적용한다:
- `BaseCreatedTimeEntity`: `@CreatedDate private LocalDateTime createdAt;` 만 보유.
- `BaseTimeEntity extends BaseCreatedTimeEntity`: `@LastModifiedDate private LocalDateTime updatedAt;` 추가 보유.

#### `BaseCreatedTimeEntity`를 적용하는 3개 테이블과 그 이유:
1. `user_invitations`:
   - 초대는 생성(`created_at`), 만료(`expires_at`), 수락(`accepted_at`)의 라이프사이클을 갖는다.
   - 상태 전이는 비즈니스 명시적 타임스탬프(`accepted_at`)로 추적해야 하며, ORM의 자동 `updated_at`이 갱신되면 최초 초대 발송 시점과 수락 시점의 감사(Audit) 추적이 혼란스러워진다.
2. `refresh_tokens`:
   - 리프레시 토큰은 추가(Append) 및 폐기(Revoke)만 존재하는 보안 감사 로그성 데이터이다.
   - 토큰이 사용되면 `revoked_at`이 1회 기록되고 생명주기가 종료된다. 일반적인 레코드 수정(`updated_at`) 개념이 존재하지 않는다.
3. `worksheet_pages`:
   - 업로드된 PDF/이미지 학습지를 페이지 단위로 분할 렌더링한 물리적 원본 페이지 메타데이터이다 (`page_number`, `width`, `height`, `preview_image_url`).
   - 한번 생성된 페이지 메타데이터는 변경되지 않으며, 파일이 잘못 등록된 경우 학습지 자체를 재업로드하거나 교체한다.

### 5.2 단방향 ManyToOne(fetch = LAZY) 원칙
- **원칙**: 모든 연관관계는 기본적으로 **단방향 `@ManyToOne(fetch = FetchType.LAZY)`** 로 매핑한다.
- **배경 및 금지 사항**:
  - 양방향 연관관계(`@OneToMany`)는 객체 간 순환 참조(Circular Reference), 불필요한 양방향 매핑 코드 복잡도, DTO 직렬화 시 무한 루프, 의도치 않은 자식 컬렉션 전체 로딩(N+1 문제)의 주원인이다.
  - JPA 기본 스펙에서 `@ManyToOne`의 기본 FetchType은 `EAGER`(즉시 로딩)이므로, 반드시 명시적으로 `fetch = FetchType.LAZY`를 선언해야 불필요한 연관 조인을 차단할 수 있다.
- **자식 데이터 조회 전략**:
  - 부모 엔티티의 컬렉션 getter를 통한 탐색을 지양하고, 해당 도메인의 Spring Data JPA Repository에서 `findByWorksheetId(...)` 와 같이 명시적인 JPQL / QueryDSL 쿼리로 페이징 및 필터링을 수행한다.

### 5.3 Hibernate 6 네이티브 JSON 매핑 (`@JdbcTypeCode(SqlTypes.JSON)`)
- **기술적 결정**: MySQL 8.0의 네이티브 `JSON` 컬럼을 매핑할 때, 과거 레거시 방식(문자열 직렬화 수동 변환기 `AttributeConverter`나 타사 라이브러리 `hypersistence-utils`)을 사용하지 않고, **Spring Boot 3/4 + Hibernate 6의 표준 `@JdbcTypeCode(SqlTypes.JSON)`** 애노테이션을 사용한다.
- **적용 대상 컬럼**:
  - `worksheet_pages.ocr_result` (OCR 분석 원시 데이터)
  - `worksheet_generations.request_settings_snapshot` (생성 요청 당시의 설정 스냅샷)
  - `corrections.regions` (첨삭 대상 영역 좌표 리스트: `List<RegionDto>`)
  - `comments.regions` (교사 코멘트 강조 영역 좌표 리스트: `List<RegionDto>`)
- **엔티티 코드 예시**:
  ```java
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "regions", columnDefinition = "json", nullable = false)
  private List<RegionCoordinate> regions;
  ```
- **효과**:
  - Hibernate가 내부 Jackson `ObjectMapper`와 연동되어 자바 객체(Record / List / Map)와 MySQL JSON 타입 간의 직렬화/역직렬화를 완벽하게 자동 처리한다.
  - MySQL 내부에서 JSON 유효성 검증이 작동하며, 애플리케이션 계층에서는 타입 세이프(Type-safe)한 DTO를 직접 다룰 수 있다.

---

## 6. 의사결정 매트릭스 및 향후 구현 가이드

| 영역 | 핵심 결정 사항 | 적용 파일 / 위치 | 기대 효과 / 검증 방안 |
| :--- | :--- | :--- | :--- |
| **멀티 테넌시** | 학원 단위 논리적 데이터 격리 (`academy_id`) | 모든 비즈니스 엔티티 및 리포지토리 | 타 학원 데이터 유출 원천 차단 |
| **권한/프로비저닝** | 원장(DIRECTOR) 일반 가입 금지, 시드/사내 생성 | `DevDataInitializer.java`, `AuthService.java` | 권한 승격 공격 및 불법 테넌트 생성 방지 |
| **초대 라이프사이클** | 24시간 유효, 재초대 시 기존 PENDING 즉시 REVOKED | `InvitationService.java`, `UserInvitation.java` | 무효 토큰 방치 방지 및 최신 초대장 단일성 보장 |
| **회원가입 보안** | role/academy_id 클라이언트 수신 금지, 서버 강제 주입 | `SignupRequest.java`, `InvitationService.java` | 클라이언트 파라미터 위변조 방어 |
| **세션 보안** | 가입 직후 JWT 발급 금지, 로그인 유도 | `InvitationService.signup` -> `SignupResponse` | 자격 증명 유효성 재확인 및 토큰 로직 단일화 |
| **구독 정책** | 만료 시 로그인/기존 조회 허용, 유료/쓰기 기능만 403 차단 | `WorksheetService`, `InvitationService` | 서비스 연속성 제공 및 유료 기능 보호 |
| **토큰 암호화** | CSPRNG 256-bit 생성 및 DB SHA-256 Hex 해시만 저장 | `TokenHashUtil.java`, `UserInvitation`, `RefreshToken` | DB 유출 시에도 원본 토큰 재사용 불가 |
| **세션 갱신** | Refresh Token Rotation (RTR) 1회용 폐기 및 신규 발급 | `AuthService.refresh`, `RefreshToken.java` | 리프레시 토큰 재사용 및 탈취 리플레이 방어 |
| **AI 파이프라인** | 단일 Multi-modal LLM 중심 설계, 도메인 I/O 기준 DB 불변 | `CorrectionAiClient.java`, `corrections` | 파이프라인 전환 시 DB 마이그레이션 제로 |
| **좌표 변환** | Gemini 0~1000 정규화 좌표 ➔ 실제 이미지 WxH 픽셀 변환 | AI 결과 파싱 매퍼 계층 | 프론트엔드 캔버스 오버레이 렌더링 정밀도 보장 |
| **JPA 베이스** | BaseCreatedTimeEntity vs BaseTimeEntity 2단계 분리 | `global.entity.*` | 불변/감사 테이블의 불필요한 `updated_at` 방지 |
| **JPA 연관관계** | 단방향 ManyToOne(fetch = LAZY) 엄격 적용 | 도메인 엔티티 전반 | 순환 참조 제거, N+1 쿼리 방지 |
| **JSON 매핑** | Hibernate 6 표준 `@JdbcTypeCode(SqlTypes.JSON)` | `corrections.regions` 등 JSON 컬럼 | 외부 라이브러리 없이 타입 세이프한 JSON 영속화 |

---
*본 문서는 AUTEDI 백엔드 개발 팀의 공식 아키텍처 의사결정 기록(ADR)이며, 변경 사항 발생 시 PR을 통한 리뷰 후 갱신됩니다.*
