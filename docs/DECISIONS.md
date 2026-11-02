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
7. [공통 응답 규격(ApiResponse) 및 학습지 도메인 엔티티 모델링](#7-공통-응답-규격apiresponse-및-학습지-도메인-엔티티-모델링)
   - 7.1 표준 공통 응답 규격 (`ApiResponse<T>`)
   - 7.2 학습지 도메인 6종 엔티티 매핑 원칙
8. [학습지 도메인(F02/F03/F04) 아키텍처 및 라이프사이클 의사결정](#8-학습지-도메인f02f03f04-아키텍처-및-라이프사이클-의사결정)
   - 8.1 학원(Academy) 단위 멀티 테넌트 데이터 격리 및 구독 검증
   - 8.2 학습지 상태 머신 (Worksheet State Machine) 및 DRAFT/FAILED 제약 원칙
   - 8.3 ETag 및 낙관적 락(@Version)을 활용한 동시성 제어 및 조건부 갱신(If-Match)
   - 8.4 파일 스토리지 추상화 및 SHA-256 해시/메타데이터 산출
   - 8.5 파일 교체(Replace) 시 OCR 캐시 무효화 및 설정(Settings) 보존 정책
   - 8.6 첨삭 설정(F03) 전체 교체(PUT) 및 중복 영역 검증 정책
   - 8.7 AI 첨삭 생성(F04) 멱등성 보장 및 재시도(Retry) 정합성 제약
   - 8.8 비동기 AI 파이프라인 및 이전 첨삭 소프트 딜리트 정책
   - 8.9 RESTful HTTP 응답 규격 (201, 202, 200, 412)
9. [검토 및 코멘트 도메인(F05/F06) 아키텍처 의사결정](#9-검토-및-코멘트-도메인f05f06-아키텍처-의사결정)
   - 9.1 검토 화면 통합 조회(Review Aggregation) 및 네트워크 워터폴 제거
   - 9.2 AI 첨삭 원본 불변성(Immutability) 및 교사 수정(is_edited) 추적
   - 9.3 첨삭 소프트 딜리트(Logical Deletion) 및 편집 보존 복원(Restore) 메커니즘
   - 9.4 삭제된 첨삭(DELETED)에 대한 수정 차단(409 Conflict) 정책
   - 9.5 교사 코멘트 영역-페이지 일치성 검증 및 불변 영역 정책
   - 9.6 코멘트 소프트 딜리트(deleted_at) 및 삭제 자원 404 은닉
   - 9.7 단위 자원별 ETag(`cor_`, `com_`) 및 If-Match 동시성 제어
10. [코드 품질 및 포맷터 표준화 의사결정 (Code Formatting & Spotless)](#10-코드-품질-및-포맷터-표준화-의사결정-code-formatting--spotless)
   - 10.1 도입 배경 및 철학 (Review Fatigue & Dirty Diff 제거)
   - 10.2 포맷터 엔진 선정 비교: Google Java Format vs Palantir Java Format
   - 10.3 2단계 품질 강제 파이프라인 (Local Hook & Remote CI)
   - 10.4 팀원 환경 설정 및 실행 명령어 가이드
11. [인프라 및 배포 아키텍처 의사결정 (Infrastructure & Deployment)](#11-인프라-및-배포-아키텍처-의사결정-infrastructure--deployment)
   - 11.1 확정 배포 기술 스택 요약 (Confirmed Tech Stack)
   - 11.2 서비스 런타임 및 CI/CD 아키텍처
   - 11.3 EC2 2GB RAM OOM 방어 및 메모리 배분 설계
   - 11.4 프로덕션 `docker-compose.yml` 및 Nginx 리버스 프록시 규격
   - 11.5 데이터 영속성 및 S3 일일 DB 백업 자동화
   - 11.6 74일 후 30분 무중단 마이그레이션 런북
   - 11.7 환경별 설정 프로파일 분리 전략 (Local / Dev / Prod)
   - 11.8 운영 DB 스키마 관리 전략 및 점진적 마이그레이션 운영 수칙 (DDL validate Policy)
   - 11.9 상용 B2B 서비스를 위한 엄격한 브랜치 보호(Protected Branch) 및 PR 의무화 정책
   - 11.10 Railway 개발 배포 운영 규격 및 트러블슈팅 런북 (Multi-stage Docker & Configuration)
   - 11.11 이메일 발송 인프라(SMTP/SES) 아키텍처 및 비동기 처리 규격 (Email Notification Architecture)
   - 11.12 AWS S3 파일 스토리지 아키텍처 및 미디어 서빙 규격 (AWS S3 Storage Architecture & Zero-Frontend-Impact Media Serving)
   - 11.13 프론트엔드 연동 캔버스 협의 스펙 확장 아키텍처 (Canvas Collaboration & Multi-page Specifications)

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
- **결정**: 원장(`Role.DIRECTOR`)은 퍼블릭 회원가입 API(`/api/v1/auth/signup`)를 통해 스스로 계정을 생성할 수 없다.
- **이유**:
  - AUTEDI는 B2B 영업 및 계약을 통해 도입되는 유료 SaaS이다. 임의 사용자가 무단으로 학원을 생성하거나 원장 권한을 획득하는 것을 원천 차단한다.
  - 원장 계정과 학원 엔티티는 사내 운영 관리자 툴, 배치 스크립트, 또는 개발 시드 데이터(`DevDataInitializer`)를 통해서만 사전 프로비저닝된다.
- **보안 효과**: 권한 승격 공격(Privilege Escalation) 벡터 자체를 제거한다.

### 2.3 선생님(TEACHER) 단건 초대 프로세스 및 재초대 무효화(REVOKED) 정책
- **단건 초대 발송**: 원장(`DIRECTOR`) 권한을 가진 사용자만이 소속 학원의 선생님을 이메일로 1건씩 초대할 수 있다 (`POST /api/v1/invitations`).
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
- **결정**: 회원가입 API(`/api/v1/auth/signup`) 성공 시 Access Token / Refresh Token을 응답에 포함하지 않고 `201 Created`와 기본 사용자 프로필 정보(`SignupResponse`)만 반환한다.
- **이유**:
  1. **자격 증명 검증 보장**: 사용자가 등록한 비밀번호로 정규 로그인(`/api/v1/auth/login`) 절차를 밟도록 유도함으로써 비밀번호 오입력에 의한 계정 고립을 즉시 인지하도록 한다.
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
    - 신규 선생님 초대장 발송 (`POST /api/v1/invitations`)
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
| **F02 테넌시 보안** | `findByIdAndAcademyId`를 통한 타 학원 학습지 접근 차단 | `WorksheetRepository.java`, `WorksheetService.java` | 타 학원 자원 404 은닉 및 스캐닝 방지 |
| **F02/F03 상태 제약** | `DRAFT` 또는 `FAILED` 상태에서만 파일 교체 및 설정 수정 허용 | `Worksheet.canModifyOrReplace()`, `WorksheetService.java` | 진행 중(`PROCESSING`) 및 완료(`READY`) 과제 변조 409 차단 |
| **동시성 제어** | ETag (`ws_`, `settings_`) & `If-Match` 조건부 요청 검증 | `WorksheetController.java`, `WorksheetService.java` | 다중 교사 동시 편집 충돌 방어 (412) |
| **OCR 무결성** | 파일 교체 시 기존 페이지 OCR 캐시(`ocrResult`, `ocrFileHash`) 무효화 | `WorksheetService.replaceWorksheetFile` | 신규 이미지에 과거 OCR 텍스트 매핑 오염 방지 |
| **설정 보존/편의성** | 파일 교체 시 기존 첨삭 영역/수준 설정(`WorksheetSetting`) 영속 유지 | `WorksheetService.replaceWorksheetFile` | 파일 재업로드 시 교사의 재설정 UX 번거로움 해소 |
| **F03 설정 교체** | 단건 추가/삭제 대신 전체 교체(Full Replacement) 및 중복 영역 방지 | `WorksheetService.updateSettings` | 설정 원자성 보장 및 1~4개 영역 중복 등록 방어 |
| **F04 생성 멱등성** | `Idempotency-Key` 헤더 기반 중복 접수 방어 및 캐시 응답 | `WorksheetGenerationService.requestGeneration` | 네트워크 재시도 시 중복 AI 호출 과금 및 중복 작업 방어 |
| **F04 재시도 검증** | 대상 작업 `FAILED` 및 원본 파일 해시 일치 시에만 재시도 승인 | `WorksheetGenerationService.retryGeneration` | 변경된 파일에 대한 잘못된 재시도 409 차단 및 실패 작업만 복구 |
| **F04 결과 불변성** | 신규 생성 시 이전 ACTIVE 첨삭 소프트 딜리트 및 새 첨삭 일괄 저장 | `WorksheetGenerationService.executeAiCorrection` | 재생성 시 기존 첨삭과 혼선 방지 및 이력 보존 |
| **F05 검토 집계** | `GET /review`를 통한 학습지+생성+첨삭+코멘트 단일 왕복 통합 반환 | `CorrectionService.getReview` | 프론트엔드 네트워크 워터폴 제거 및 초기 렌더링 가속 |
| **F05 첨삭 불변성** | 교사 수정 시 원본 및 AI 초안 보존, `content`와 `is_edited`만 갱신 | `Correction.editContent` | AI 분석 원본 데이터 추적성 확보 및 수정 경고 표시 |
| **F05 첨삭 소프트 딜리트** | 물리 삭제 대신 `status='DELETED'` 적용 및 원본 편집 보존 복원 지원 | `Correction.delete`, `restore` | 교사의 조작 실수 방어 및 이전 수정 내용 완전 복구 |
| **F05 삭제 수정 차단** | `DELETED` 상태 첨삭에 대한 `PATCH` 요청 409 Conflict 차단 | `CorrectionService.updateCorrection` | 삭제된 자원에 대한 비정상 상태 전이 방어 |
| **F06 코멘트 불변 영역** | 생성 시 `pageId`, `regions` 고정, 수정 시 오직 `content`만 변경 | `CommentService.updateComment` | 프론트엔드 캔버스 자동 배치 레이아웃 일관성 유지 |
| **F06 코멘트 일관성** | 코멘트 내부 모든 `regions.pageId`와 요청 `pageId` 일치 강제 | `CommentService.createComment` | 페이지 간 교차 좌표 참조 오염 400 차단 |
| **F05/F06 동시성 제어** | 자원별 ETag(`cor_`, `com_`) 및 `If-Match` 헤더를 통한 낙관적 락 | `CorrectionService`, `CommentService` | 다중 교사 동시 검토 및 코멘트 덮어쓰기 방지 (412) |
| **파일 스토리지** | DIP 기반 AWS S3 전환 (`S3FileStorageService`) 및 로컬 Fallback | `FileStorageService`, `S3FileStorageService`, `LocalStorageService` | 프론트 0-code <img> 렌더링 유지, 서버 트래픽 격리, 로컬 무자격증명 개발 지원 |

---
*본 문서는 AUTEDI 백엔드 개발 팀의 공식 아키텍처 의사결정 기록(ADR)이며, 변경 사항 발생 시 PR을 통한 리뷰 후 갱신됩니다.*


---

## 7. 공통 응답 규격(ApiResponse) 및 학습지 도메인 엔티티 모델링

### 7.1 표준 공통 응답 규격 (`ApiResponse<T>`)
- 프론트엔드 연동 일관성을 위해 모든 API 응답을 `ApiResponse<T>` 봉투(Envelope)로 래핑한다:
  ```json
  {
    "isSuccess": true,
    "code": "COMMON_200",
    "message": "성공입니다.",
    "result": { ... },
    "error": null
  }
  ```
- **실패 응답**: `GlobalExceptionHandler`, `CustomAuthenticationEntryPoint`(401), `CustomAccessDeniedHandler`(403)에서 `ApiResponse.onFailure(errorCode, errorDetails)` 형태로 단일화.
- **인터페이스 분리**: `BaseErrorCode` 인터페이스를 정의하여 `ErrorCode` enum이 이를 구현하게 함으로써 에러 코드 확장성 보장.

### 7.2 학습지 도메인 6종 엔티티 매핑 원칙
- **`Worksheet`**: 학습지 마스터 테이블 (`BaseTimeEntity` 상속, 낙관적 락 `@Version` 적용, `status`가 `DRAFT`/`FAILED`일 때만 파일 교체 및 설정 수정 허용)
- **`WorksheetPage`**: 페이지별 메타데이터 (`BaseCreatedTimeEntity` 상속 - `updated_at` 없음, `ocr_result` JSON 컬럼 nullable, `ocr_file_hash`를 통한 캐시 재사용 키 관리)
- **`WorksheetSetting`**: 영역(4종) x 수준(2종) 첨삭 기준 (`UK(worksheet_id, area)`)
- **`WorksheetGeneration`**: 비동기 AI 생성 요청 단위 (`idempotency_key` 중복 요청 방어, `request_settings_snapshot` JSON 스냅샷 보존)
- **`Correction`**: AI 첨삭 항목 카드 (`regions` 좌표 JSON 매핑, `is_edited` 교사 편집 플래그, `status='DELETED'` 소프트 딜리트)
- **`Comment`**: 교사 코멘트 및 강조 영역 (`highlight_type`, `regions` JSON 매핑, 공백 내용 저장 차단, `deleted_at` 소프트 딜리트)
- **영속성 원칙**: 모든 엔티티 연관관계는 단방향 `@ManyToOne(fetch = FetchType.LAZY)`으로 구축하여 불필요한 N+1 쿼리와 순환 참조를 차단.

---

## 8. 학습지 도메인(F02/F03/F04) 아키텍처 및 라이프사이클 의사결정

### 8.1 학원(Academy) 단위 멀티 테넌트 데이터 격리 및 구독 검증
- **조회 격리 (`findByIdAndAcademyId`)**:
  - 학습지 마스터 엔티티(`Worksheet`) 및 연관 자식 엔티티는 반드시 Security Context의 `academyId`를 조건절에 포함하여 조회한다.
  - 타 학원의 `worksheetId`로 접근 시 403 Forbidden 대신 `404 Not Found` (`WORKSHEET_001`)를 응답함으로써 타 테넌트의 자원 식별자 존재 유무조차 노출하지 않는 정보 은닉(Defense in Depth) 원칙을 준수한다.
- **신규 쓰기 시 유료 구독 검증**:
  - `POST /api/v1/worksheets` 업로드 시점에 소속 학원의 `academy.isActive()`를 검증하여 미납/만료 학원의 신규 과제 등록을 `403 Forbidden` (`ACADEMY_002`)으로 차단한다.
  - 반면 단순 상세 조회(`GET`)는 구독 만료 상태에서도 학생 피드백 확인 및 인쇄 등 최소한의 업무 연속성을 위해 허용한다.

### 8.2 학습지 상태 머신 (Worksheet State Machine) 및 DRAFT/FAILED 제약 원칙
- **상태 정의 (4종)**:
  - `DRAFT`: 최초 파일 업로드 완료 상태. 첨삭 설정 선택 및 파일 교체가 자유롭게 가능.
  - `PROCESSING`: AI 비동기 파이프라인(OCR/Vision LLM) 실행 중. 데이터 변조 방지를 위해 전체 수정 차단.
  - `READY`: AI 첨삭 생성 완료 상태. 교사의 첨삭 검토 및 코멘트 작업 수행 가능.
  - `FAILED`: AI 파이프라인 처리 실패 상태. 오류 복구를 위한 파일 교체, 설정 수정, 작업 재시도 가능.
- **수정/교체 가능 제약 (`canModifyOrReplace`)**:
  ```java
  public boolean canModifyOrReplace() {
      return this.status == WorksheetStatus.DRAFT || this.status == WorksheetStatus.FAILED;
  }
  ```
- **정책적 이유**:
  - 이미 AI 분석이 진행 중이거나(`PROCESSING`), 완료되어 첨삭 카드가 생성된(`READY`) 상태에서 원본 이미지가 교체되거나 첨삭 기준이 변경되면, 기존에 도출된 AI 첨삭 데이터(`corrections`) 및 교사 코멘트(`comments`)와의 정합성이 완전히 붕괴된다.
  - 따라서 이미 완료된 과제물을 수정해야 하는 경우, 기존 과제 변조를 차단하고 신규 학습지로 등록(`POST`)하도록 강제한다.
  - `PROCESSING` 또는 `READY` 상태에서 파일 교체 또는 설정 변경 시도시 `409 Conflict` (`WORKSHEET_002`)를 발생시킨다.

### 8.3 ETag 및 낙관적 락(@Version)을 활용한 동시성 제어 및 조건부 갱신(If-Match)
- **자원별 ETag 네이밍 규약**:
  - 학습지 마스터 ETag: `"\"ws_{worksheetId}-v{version}\""`
  - 첨삭 설정 ETag: `"\"settings_{worksheetId}-v{version}\""`
- **동시 편집 충돌 방지 (Lost Update Defense)**:
  - 교사 A와 교사 B가 동일한 학습지를 동시에 열어두고 파일을 교체하거나 첨삭 설정을 수정할 때, 나중에 전송된 요청이 이전 교사의 변경 사항을 무단 덮어쓰는 것을 방지한다.
  - `PUT /worksheets/{id}/file` 및 `PUT /worksheets/{id}/settings` 엔드포인트는 `If-Match` 요청 헤더를 수신하여 현재 서버의 엔티티 버전과 일치하지 않으면 `412 Precondition Failed` (`COMMON_412`)로 중단한다 (`*` 와일드카드 지원).
- **JPA 낙관적 락 연동**:
  - `Worksheet` 엔티티의 `@Version private Integer version;` 필드를 기반으로 트랜잭션 커밋 시점에 버전을 원자적으로 증가시키며 새 ETag를 발급한다.

### 8.4 파일 스토리지 추상화 및 SHA-256 해시/메타데이터 산출
- **스토리지 인터페이스 추상화 (`FileStorageService`)**:
  - 인프라 종속성을 분리하기 위해 `FileStorageService` 인터페이스(`upload`, `delete`)를 정의하였으며, 프로덕션 환경에서는 AWS S3 구현체(`S3FileStorageService`)를, AWS 자격증명이 없는 로컬 개발/테스트 환경에서는 로컬 스토리지 구현체(`LocalStorageService`)로 자동 Fallback 되도록 DIP(의존성 역전 원칙)를 적용했다. (상세 아키텍처 및 무중단 전환 규격은 Section 11.12 참조)
- **SHA-256 파일 해시 (`fileHash`)**:
  - 파일 업로드 및 교체 시 원본 파일의 바이트 스트림으로부터 SHA-256 Hex 해시를 계산하여 영속화한다.
  - 이는 향후 동일 파일 재업로드 감지 및 OCR 결과 캐싱(`ocr_file_hash`)의 키 값으로 활용된다.
- **이미지 캔버스 해상도 영속화 (`width`, `height`)**:
  - 업로드 즉시 이미지의 가로/세로 픽셀 크기를 추출하여 `WorksheetPage`에 저장한다.
  - 이는 Gemini Vision 등 AI 모델이 반환하는 정규화 좌표(0~1000)를 프론트엔드 오버레이 픽셀로 변환할 때 필수적인 기준 캔버스 규격이 된다. (PDF의 경우 기본 1000 x 1400 할당)

### 8.5 파일 교체(Replace) 시 OCR 캐시 무효화 및 설정(Settings) 보존 정책
- **OCR 캐시 완전 무효화**:
  - `PUT /worksheets/{id}/file` 성공 시 기존 첫 번째 페이지의 `previewImageUrl`, `width`, `height`를 갱신함과 동시에 `page.updateOcrResult(null, null)`을 호출하여 OCR 분석 결과와 해시를 즉시 제거한다.
  - 이유: 새 파일이 등록되었음에도 과거 이미지의 OCR 단어 좌표가 남아있으면, 이후 생성될 AI 첨삭이 엉뚱한 위치에 바인딩되는 심각한 오류가 발생하기 때문이다.
- **상태 DRAFT 리셋**:
  - 이전에 생성 실패(`FAILED`) 상태였던 학습지라도 파일 교체가 완료되면 즉시 `DRAFT` 상태로 회귀하여 새로운 생명주기를 시작한다.
- **첨삭 설정(Settings) 보존**:
  - 과제물 재촬영으로 인해 이미지를 교체하더라도, 교사가 미리 공들여 설정해 둔 첨삭 영역(문법, 어휘 등)과 수준(Standard, Advanced)은 삭제하지 않고 보존함으로써 재입력의 피로도를 제거한다.

### 8.6 첨삭 설정(F03) 전체 교체(PUT) 및 중복 영역 검증 정책
- **전체 교체 (Full Replacement) 전략**:
  - 첨삭 설정은 개별 영역을 부분 수정(PATCH)하는 복잡성 대신, 선택된 1~4개의 영역 목록 전체를 한 번에 교체(`deleteAllByWorksheetId` -> `saveAll`)하는 PUT 방식을 채택했다.
- **검증 규칙**:
  - 최소 1개 이상 (`@NotEmpty`), 최대 4개 이하 (`@Size(max = 4)`).
  - 중복된 `CorrectionArea` 지정 차단 (스트림 `distinct().count()` 검증 실패 시 `400 Bad Request` 반환).
- **영역 및 수준 Enum**:
  - `area`: `GRAMMAR_SPELLING`, `VOCABULARY_EXPRESSION`, `SENTENCE_STRUCTURE`, `CONTENT_SUMMARY`
  - `level`: `STANDARD` (오류 수정 및 기본 가이드), `ADVANCED` (심화 문장 재구성 및 어휘 확장)

### 8.7 AI 첨삭 생성(F04) 멱등성 보장 및 재시도(Retry) 정합성 제약
- **멱등성 보장 (`Idempotency-Key`)**:
  - 네트워크 단절이나 클라이언트의 다중 클릭으로 인한 중복 AI 호출을 방지하기 위해 `Idempotency-Key` 헤더를 지원한다.
  - 동일한 키로 요청이 인입될 경우 기존에 생성된 `WorksheetGeneration` 엔티티를 조회하여 중복 생성 없이 즉시 반환한다.
- **재시도(Retry) 정합성 검증**:
  - `POST /api/v1/worksheets/{worksheetId}/generations/{generationId}/retry`는 오직 상태가 `FAILED`인 작업에 대해서만 허용된다 (`CANNOT_RETRY_GENERATION` 409).
  - 또한, 실패 당시의 원본 파일 해시(`target.getRequestFileHash()`)와 현재 학습지의 `fileHash`가 일치하는지 엄격히 검증하여, 파일이 바뀐 상태에서의 잘못된 재시도를 차단한다 (파일이 바뀐 경우 신규 생성을 요청해야 함).
  - 재시도 생성 시 `retryCount`가 1 증가하며, 부모 작업 식별자(`retryOfGenerationId`)를 기록하여 추적성을 확보한다.

### 8.8 비동기 AI 파이프라인 및 이전 첨삭 소프트 딜리트 정책
- **이전 첨삭 항목 정리**:
  - 재시도나 재분석 시 기존에 활성 상태(`ACTIVE`)였던 `Correction` 항목들은 즉시 물리 삭제하지 않고 `delete()`를 호출하여 `status='DELETED'`로 소프트 딜리트 처리한다.
  - 이후 새롭게 도출된 AI 첨삭 카드 리스트를 일괄 영속화(`saveAll`)함으로써 교사의 이전 검토 이력 보존과 데이터 충돌을 동시에 해결한다.

### 8.9 RESTful HTTP 응답 규격 (201, 202, 200, 412)
- `POST /api/v1/worksheets`: 리소스 생성 완료로 `201 Created` 및 `Location: /api/v1/worksheets/{id}`, `ETag` 반환.
- `POST /api/v1/worksheets/{id}/generations`: 비동기 생성 접수로 `202 Accepted` 및 `Location: /api/v1/worksheets/{id}/generations/{id}` 반환.
- `PUT /api/v1/worksheets/{id}/file` & `PUT /settings`: 자원 갱신 완료로 `200 OK` 및 새 `ETag` 반환.
- 동시성 충돌 발생 시: `412 Precondition Failed` (`COMMON_412`) 표준 준수.

---

## 9. 검토 및 코멘트 도메인(F05/F06) 아키텍처 의사결정

### 9.1 검토 화면 통합 조회(Review Aggregation) 및 네트워크 워터폴 제거
- **배경**: AI 첨삭 검토 화면 진입 시 학습지 기본 정보, 페이지별 해상도, 최신 생성 작업 상태, AI 첨삭 목록, 교사 코멘트 목록을 각각 별도 API로 호출하면 4~5회의 HTTP 요청이 발생하여 화면 깜빡임과 렌더링 지연이 초래된다.
- **결정**: `GET /api/v1/worksheets/{worksheetId}/review` 통합 엔드포인트를 제공하여 단 1회의 왕복(Single Round-trip)으로 검토 화면에 필요한 모든 핵심 데이터를 집계(`ReviewResponse`)하여 반환한다.
- **필수 상태 제약**:
  - 검토 화면은 AI 분석이 성공적으로 종료된 시점에만 진입해야 하므로, 학습지 상태가 `READY`인지 엄격히 검증한다. `DRAFT`, `PROCESSING`, `FAILED` 상태인 경우 `409 Conflict` (`WORKSHEET_002`)를 반환하여 일관성 없는 데이터 렌더링을 차단한다.
  - 통합 조회 시 첨삭은 `ACTIVE` 상태인 항목만, 코멘트는 `deleted_at == null`인 활성 항목만 추출하여 프론트엔드의 별도 필터링 부하를 제거한다.

### 9.2 AI 첨삭 원본 불변성(Immutability) 및 교사 수정(is_edited) 추적
- **불변 원칙**:
  - AI가 최초 생성한 분석 데이터는 학술적/감사적 목적으로 영구 보존되어야 한다.
  - 교사가 첨삭 카드 내용을 수정(`PATCH /corrections/{correctionId}`)하더라도, 학생 원문(`originalText`), AI 초기 추천문(`aiInitialContent`), AI 문법/어휘 설명(`explanation`)은 DB 컬럼상에서 절대 덮어쓰거나 변경하지 않는다.
- **교사 수정 플래그 및 설명 불일치 알림**:
  - 교사의 수정 요청 시 오직 `content` 컬럼만 갱신하며 `is_edited = true`를 세팅한다.
  - 응답 DTO(`CorrectionResponse`)에서는 `editedByTeacher = true`와 `explanationStale = true`를 제공하여, 화면에서 "선생님이 직접 수정한 내용이며 AI 설명은 최초 제안 기준입니다"라는 경고 라벨을 프론트엔드가 자연스럽게 표시할 수 있도록 지원한다.

### 9.3 첨삭 소프트 딜리트(Logical Deletion) 및 편집 보존 복원(Restore) 메커니즘
- **소프트 딜리트 채택**:
  - 교사가 검토 도중 부적절한 AI 추천 카드를 삭제(`DELETE /corrections/{correctionId}`)할 때 물리적으로 DB 레코드를 제거(`DELETE FROM`)하지 않고, `status = 'DELETED'`, `deleted_at = NOW()`로 논리 삭제 처리한다.
- **편집 이력 보존 복원 (`POST /restore`)**:
  - 교사가 실수로 삭제한 항목은 언제든 `restore` 엔드포인트를 통해 `ACTIVE` 상태로 즉시 되돌릴 수 있다.
  - 특히, 삭제 이전에 교사가 이미 공들여 수정한 `content` 및 `isEdited` 플래그는 복원 시에도 온전히 보존되어 재입력의 수고를 원천 방지한다.
- **목록 필터링 파라미터 (`status`)**:
  - `GET /corrections?status=ACTIVE` (기본값): 검토 중인 유효 첨삭만 조회.
  - `GET /corrections?status=DELETED`: 교사가 삭제한 항목만 조회하여 '휴지통/삭제된 항목 보기' 탭 지원.
  - `GET /corrections?status=ALL`: 전체 첨삭 이력 조회.

### 9.4 삭제된 첨삭(DELETED)에 대한 수정 차단(409 Conflict) 정책
- **결정**: `status == DELETED`인 첨삭 항목에 대해 `PATCH /corrections/{correctionId}` 호출이 인입되면 즉시 `409 Conflict` (`CORRECTION_002`, "삭제된 첨삭 항목은 수정할 수 없습니다.")로 차단한다.
- **이유**: 삭제된 카드를 수정하는 것은 상태 전이 관점에서 비정상 동작이며, 복원(`restore`)을 먼저 명시적으로 수행한 뒤 편집하는 단방향 워크플로우를 강제하여 교사의 의도치 않은 데이터 오염을 방어한다.

### 9.5 교사 코멘트 영역-페이지 일치성 검증 및 불변 영역 정책
- **단일 페이지 귀속 원칙**:
  - 교사 코멘트(`POST /comments`) 작성 시 전달되는 모든 하이라이트/밑줄 영역(`regions`)은 반드시 단일 `pageId`에 귀속되어야 한다.
  - 요청 바디의 `pageId`와 `regions` 내부의 `region.pageId`가 다를 경우 `400 Bad Request` (`COMMENT_002`, "코멘트 영역의 페이지가 대상 페이지와 일치하지 않습니다.")로 거부한다.
- **불변 영역(Immutable Geometry) 정책**:
  - 코멘트 내용 수정(`PATCH /comments/{commentId}`) 시 오직 텍스트 내용(`content`)만 변경을 허용하며, 좌표 영역(`regions`)이나 강조 유형(`highlightType`)은 일체 변경할 수 없다.
  - 영역 좌표를 변경해야 할 경우 기존 코멘트를 삭제하고 새로 작성하도록 유도하여, 프론트엔드의 캔버스 오버레이 자동 배치 및 연결선 렌더링 엔진의 레이아웃 안정성을 보장한다.

### 9.6 코멘트 소프트 딜리트(deleted_at) 및 삭제 자원 404 은닉
- **논리 삭제 및 영구 은닉**:
  - 코멘트 삭제(`DELETE /comments/{commentId}`)는 `deleted_at` 타임스탬프를 기록하는 소프트 딜리트로 구현된다.
  - 첨삭과 달리 교사 코멘트는 '삭제 취소' 개념이 없으므로, 삭제된 코멘트에 대한 단건 조회, 수정, 재삭제 시도는 모두 `404 Not Found` (`COMMENT_001`, "코멘트를 찾을 수 없거나 이미 삭제되었습니다.")를 반환하여 자원의 부재를 명확히 처리한다.
- **공백 저장 원천 차단**:
  - 빈 문자열이나 공백만으로 구성된 코멘트 저장을 방지하기 위해 `@NotBlank` 유효성 검증과 서버단 `.trim()` 처리를 강제한다.

### 9.7 단위 자원별 ETag(`cor_`, `com_`) 및 If-Match 동시성 제어
- **세분화된 ETag 네이밍**:
  - 개별 첨삭 자원 ETag: `"\"cor_{correctionId}-v{version}\""`
  - 개별 코멘트 자원 ETag: `"\"com_{commentId}-v{version}\""`
- **단위 동시성(Fine-Grained Concurrency)**:
  - 학습지 마스터 전체를 잠그지 않고 개별 첨삭 카드 및 코멘트 단위로 JPA `@Version`을 적용한다.
  - 두 명 이상의 교사가 동일한 학습지를 검토할 때, 교사 A가 1번 첨삭을 수정하는 동안 교사 B가 2번 첨삭을 수정하거나 코멘트를 달아도 충돌 없이 병행 처리가 가능하다.
  - 단, 동일한 자원을 동시에 수정/삭제할 때만 `If-Match` 검증을 통해 `412 Precondition Failed` (`COMMON_412`)로 나중 쓰기를 차단한다.
- **204 No Content 헤더 제공**:
  - 첨삭 삭제(`204 No Content`) 시에도 응답 헤더로 다음 버전 ETag(`cor_{id}-v{next}`)를 반환하여 RESTful 완전성을 보장한다.

---

## 10. 코드 품질 및 포맷터 표준화 의사결정 (Code Formatting & Spotless)

### 10.1 도입 배경 및 철학 (Review Fatigue & Dirty Diff 제거)
- **코드 리뷰 피로도(Review Fatigue) 제거**:
  - 들여쓰기, 괄호 위치, 공백, 미사용 import 등 서식 관련 코멘트는 리뷰어와 리뷰이 모두에게 불필요한 피로를 준다.
  - 서식 검사는 100% 빌드 도구에 위임하고, 코드 리뷰는 **도메인 설계, 아키텍처, 성능, 보안, 예외 처리** 등 본질에만 집중한다.
- **Git Diff 오염(Dirty Diff) 원천 차단**:
  - 개발자별 IDE(IntelliJ, VS Code 등)나 OS(macOS, Windows) 설정 차이로 인해 단 1줄의 로직 수정 시 수백 줄의 들여쓰기가 변경되는 현상을 방지한다.
  - `git blame`과 히스토리의 신뢰성을 지키고, 불필요한 서식 충돌(Merge Conflict)을 원천 차단한다.
- **핵심 원칙**:
  > 💡 **"코드 컨벤션은 사람이 눈으로 검사하는 것이 아니라, 빌드 파이프라인이 기계적으로 강제하는 것이다."**

### 10.2 포맷터 엔진 선정 비교: Google Java Format vs Palantir Java Format
- **대안 비교**:
  | 비교 항목 | Google Java Format (GJF) | Palantir Java Format (채택) | 선정 이유 |
  | :--- | :--- | :--- | :--- |
  | **들여쓰기(Indent)** | 2칸 (2-space indent) | **4칸 (4-space indent)** | 자바 및 Spring Boot 생태계의 사실상 표준(de facto standard)인 4칸 들여쓰기 준수 |
  | **가독성** | 중첩 제네릭, 람다, 긴 메서드 체이닝 시 라인이 과도하게 접힘 | **람다, Stream, Builder 패턴, 메서드 체이닝 가독성 우수** | 도메인 엔티티 빌더 및 JPA 리포지토리/스트림 로직 가독성에 최적화 |
  | **커스텀 여부** | 설정 커스텀 일체 불가 (엄격한 구글 스타일 강제) | 설정 최소화(Opinionated) 원칙 동일 | 불필요한 설정 논쟁 없이 자바 표준 스타일에 완벽 부합 |
- **최종 결정**:
  - **Spotless 6.25.0** 기반에 **Palantir Java Format 2.50.0 (`palantirJavaFormat('2.50.0')`)** 엔진을 채택한다.
  - 추가 서식 규칙: 사용하지 않는 import문 자동 제거(`removeUnusedImports()`), 파일 끝 개행 보장(`endWithNewline()`), 후행 공백 자동 정리(`trimTrailingWhitespace()`).

### 10.3 2단계 품질 강제 파이프라인 (Local Hook & Remote CI)
서식 미준수로 인해 CI 빌드가 깨지거나 PR 머지가 차단되는 문제를 방지하기 위해 **로컬 사전 정렬**과 **원격 최종 검증** 2단계 파이프라인을 운영한다.

```mermaid
flowchart LR
    A["로컬 작업 (git commit)"] -->|"pre-commit hook<br/>(spotlessApply)"| B["자동 정렬 완료<br/>커밋 생성"]
    B -->|"git push & PR"| C["GitHub Actions CI<br/>(spotlessCheck)"]
    C -->|"Pass (100% 검증)"| D["PR 머지 승인"]
```

1. **로컬 1차 방어 (Git Pre-commit Hook)**:
   - `git commit` 실행 즉시 `.git/hooks/pre-commit` 스크립트가 동작하여 `./gradlew spotlessApply`를 백그라운드에서 자동 실행한다.
   - 개발자가 별도로 명령어를 외우거나 수동 실행하지 않아도 모든 커밋이 무조건 Palantir 표준 서식으로 자동 정렬되어 커밋된다.
2. **원격 2차 방어 (GitHub Actions CI)**:
   - PR 인입 시 CI 파이프라인에서 `./gradlew spotlessCheck`를 실행한다.
   - 로컬 훅이 우회된 커밋이 포함된 PR은 빌드가 실패하며 머지가 원천 차단된다.

### 10.4 팀원 환경 설정 및 실행 명령어 가이드
저장소를 새로 클론받은 팀원은 아래 1줄 명령어로 Git Hook을 활성화한다:

```bash
# 1. pre-commit 훅 실행 권한 부여 및 활성화 (최초 1회 실행)
chmod +x scripts/pre-commit && cp scripts/pre-commit .git/hooks/pre-commit
```

* **수동 서식 정렬이 필요할 때**:
  ```bash
  ./gradlew spotlessApply
  ```
* **서식 준수 여부만 검사할 때 (CI 환경과 동일)**:
  ```bash
  ./gradlew spotlessCheck
  ```

---

## 11. 인프라 및 배포 아키텍처 의사결정 (Infrastructure & Deployment)

### 11.1 확정 배포 기술 스택 요약 (Confirmed Tech Stack)

| 구분 | 확정 기술 / 구성 | 세부 역할 및 선정 이유 |
| --- | --- | --- |
| **Frontend** | **React + Vercel** | 프론트엔드 호스팅, 기본 HTTPS 제공, 추후 커스텀 도메인 연결 용이 ($0) |
| **Backend Domain & SSL** | **DuckDNS + Nginx + Let’s Encrypt** | 무료 동적 도메인(`*.duckdns.org`) + 무료 SSL 인증서로 Vercel↔︎EC2 간 종단간 HTTPS 암호화 통신 보장 ($0) |
| **Backend & DB** | **Spring Boot + MySQL 8.0** | 단일 EC2 내 **Docker Compose**로 통합 구동 (컨테이너 메모리 엄격 상한선 적용) |
| **Storage** | **AWS S3** | 첨삭 대상 영어 손글씨 이미지 원본/변환본 저장 및 일일 DB 백업 아카이브 보관 |
| **OCR & AI** | **Google Cloud Vision + 외부 AI API** | 외부 API 호출 방식으로 EC2 내부의 CPU/메모리 연산 부하 원천 차단 |
| **CI/CD** | **GitHub Actions + Docker Registry** | **EC2 내부 빌드 절대 금지(메모리 보호)**. Actions에서 빌드/검증 완료 후 EC2는 Pull & Run만 수행 ($0) |
| **AWS EC2 사양** | **`t4g.small` 또는 `t3.small` (2GB RAM)** | 2GB 물리 RAM + 2GB Swap 메모리 설정. 잔여 기한(74일)과 크레딧($41) 내에서 추가 과금 $0 운영 |

> [!IMPORTANT]
> **인프라 운영 핵심 목표**
> 1. **추가 과금 $0 달성**: 잔여 기한 74일 동안 AWS 크레딧 $41 내에서 무과금으로 완주한다.
> 2. **OOM(Out Of Memory) 서버 다운 원천 방지**: 단일 2GB RAM EC2 내에서 JVM, MySQL, Nginx의 상한선을 엄격히 통제하고 스왑 공간을 확보하여 서버 뻗음(OOM Kill)을 차단한다.
> 3. **30분 무중단 마이그레이션 대비**: 74일 후 크레딧 만료 시 다른 클라우드/계정으로 30분 이내에 인프라와 데이터를 즉시 이전할 수 있는 컨테이너 및 볼륨 바인드 구조를 유지한다.

---

### 11.2 서비스 런타임 및 CI/CD 아키텍처

#### 11.2.1 서비스 런타임 아키텍처

```mermaid
flowchart TB
    User(["사용자 (Web Browser)"])

    subgraph Vercel["Vercel (Frontend Hosting)"]
        React["React App\n(https://xxx.vercel.app)"]
    end

    subgraph AWS["AWS Cloud (74일간 $41 크레딧 무과금 운영)"]
        S3[("AWS S3\n손글씨 이미지 저장 &\nDB 일일 백업 보관")]

        subgraph EC2["EC2 (2GB RAM + 2GB Swap)"]
            subgraph Compose["Docker Compose (/srv/mvp)"]
                Nginx["Nginx Container\n(80/443 포트, Let's Encrypt SSL)"]
                Spring["Spring Boot Container\n(8080 포트, -Xmx512m)"]
                MySQL[("MySQL 8.0 Container\n(3306 포트, Buffer Pool 128M)")]
            end
            Volume["Host Volume (/srv/mvp-data)\nMySQL 데이터 & SSL 인증서 영속화"]
        end
    end

    subgraph External["외부 AI/ML 연산 파이프라인"]
        Vision["Google Cloud Vision API\n(OCR 텍스트 추출)"]
        LLM["외부 AI API (Gemini/Claude)\n(영어 문장 첨삭/교정)"]
    end

    User --> React
    React -- "HTTPS 요청 (CORS 통과)\n(https://autedi-api.duckdns.org)" --> Nginx
    Nginx -- "Reverse Proxy (HTTP)" --> Spring
    Spring -- "1. 이미지 저장" --> S3
    Spring -- "2. OCR 추출" --> Vision
    Spring -- "3. 첨삭/교정 요청" --> LLM
    Spring -- "4. 결과 저장" --> MySQL
    MySQL -. "데이터 바인드 마운트" .-> Volume
    Nginx -. "인증서 바인드 마운트" .-> Volume
```

#### 11.2.2 GitHub Actions 기반 경량 무중단 CI/CD 파이프라인

EC2 인스턴스에서 직접 `./gradlew build`나 Docker 이미지 빌드를 실행하면 메모리 스파이크로 인해 즉시 서버가 정지(OOM Freeze)됩니다. 따라서 **빌드 및 테스트는 100% GitHub Actions 가상머신이 전담**하며, EC2는 사전 빌드된 경량 컨테이너 이미지를 다운로드받아 재기동(Pull & Run)만 수행합니다.

```mermaid
flowchart LR
    Dev(["개발자"]) -- "1. git push (develop/main)" --> GH["GitHub Repository"]
    GH -- "2. 워크플로우 트리거" --> Actions["GitHub Actions Runner\n(SpotlessCheck + Gradle Test + Docker Build)"]
    Actions -- "3. 빌드 이미지 Push" --> Hub["Docker Hub / GHCR"]
    Actions -- "4. SSH 원격 배포 명령" --> EC2["AWS EC2 Host\n(docker compose pull && docker compose up -d)"]
    Hub -. "5. 최신 이미지 Pull" .-> EC2
```

---

### 11.3 EC2 2GB RAM OOM 방어 및 메모리 배분 설계

#### 11.3.1 Swap 메모리 (2GB) 필수 활성화

물리 메모리(2GB)가 순간적으로 고갈될 때 Linux 커널의 OOM Killer가 주요 프로세스를 강제 종료하지 않도록, 디스크(EBS)를 가상 메모리로 확장하는 Swap 메모리를 최초 프로비저닝 시 반드시 설정합니다:

```bash
# 1. 2GB 스왑 파일 생성 및 권한 설정
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile

# 2. 스왑 포맷팅 및 활성화
sudo mkswap /swapfile
sudo swapon /swapfile

# 3. 재부팅 시 자동 마운트 등록 (/etc/fstab)
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 4. 스왑 활성화 확인
free -h
```

#### 11.3.2 컨테이너별 메모리 다이어트 예산 (2GB RAM 기준)

| 계층 / 컨테이너 | 할당 목표치 | 튜닝 및 제약 옵션 |
| --- | --- | --- |
| **Host OS + Docker Daemon + Nginx** | **~ 250 MB** | Alpine 기반 Nginx 경량 이미지, 불필요한 OS 백그라운드 서비스 비활성화 |
| **MySQL 8.0 Container** | **~ 250 MB** | `--performance-schema=OFF`, `--innodb-buffer-pool-size=128M`, `--max-connections=50` |
| **Spring Boot Container** | **~ 650 MB** | JVM 옵션 `-Xms256m -Xmx512m -XX:+UseSerialGC` 강제 |
| **비상 예비 물리 메모리 (Buffer)** | **~ 850 MB** | 외부 API 응답 대기, 순간 트래픽, 파일 I/O 버퍼용으로 상시 보존 |

#### 11.3.3 Nginx 설정 시 필수 주의사항 (사진 업로드 용량 및 AI 타임아웃)

Nginx 기본값은 파일 업로드 용량 제한이 **1MB**이고 프록시 타임아웃이 **60초**입니다. 백엔드 `application.yaml`의 멀티파트 설정(`20MB`)과 조화를 이루고 대용량 사진 업로드 시 `413 Request Entity Too Large` 에러를 방지하기 위해, Nginx 가상호스트 설정에 아래 두 지시어를 반드시 명시해야 합니다:

1. `client_max_body_size 25M;`: Spring Boot의 20MB 멀티파트 한도보다 여유 있게 설정하여 Nginx 단에서의 조기 차단 방지.
2. `proxy_read_timeout 120s;`, `proxy_connect_timeout 60s;`: Google Vision OCR 및 외부 LLM 첨삭 호출 시 지연에 따른 504 Gateway Timeout 방지.

---

### 11.4 프로덕션 `docker-compose.yml` 및 Nginx 리버스 프록시 규격

#### 11.4.1 `/srv/mvp/docker-compose.yml`

```yaml
services:
  nginx:
    image: nginx:alpine
    container_name: mvp-nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - /srv/mvp-data/nginx/conf.d:/etc/nginx/conf.d:ro
      - /srv/mvp-data/certbot/conf:/etc/letsencrypt:ro
      - /srv/mvp-data/certbot/www:/var/www/certbot:ro
    depends_on:
      - spring-app
    deploy:
      resources:
        limits:
          memory: 100M

  spring-app:
    image: ${DOCKER_IMAGE_NAME}:latest
    container_name: mvp-spring
    restart: unless-stopped
    expose:
      - "8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      JAVA_TOOL_OPTIONS: "-Xms256m -Xmx512m -XX:+UseSerialGC"
      DB_URL: jdbc:mysql://mysql:3306/${DB_NAME}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8
      DB_USERNAME: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      AWS_S3_BUCKET: ${AWS_S3_BUCKET}
      AWS_ACCESS_KEY_ID: ${AWS_ACCESS_KEY_ID}
      AWS_SECRET_ACCESS_KEY: ${AWS_SECRET_ACCESS_KEY}
      SWAGGER_ENABLED: "false"
      SWAGGER_USERNAME: ${SWAGGER_USERNAME}
      SWAGGER_PASSWORD: ${SWAGGER_PASSWORD}
    depends_on:
      - mysql
    deploy:
      resources:
        limits:
          memory: 750M

  mysql:
    image: mysql:8.0
    container_name: mvp-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
      MYSQL_DATABASE: ${DB_NAME}
      MYSQL_USER: ${DB_USER}
      MYSQL_PASSWORD: ${DB_PASSWORD}
      TZ: Asia/Seoul
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --performance-schema=OFF
      - --innodb-buffer-pool-size=128M
      - --max-connections=50
    volumes:
      - /srv/mvp-data/mysql:/var/lib/mysql
    deploy:
      resources:
        limits:
          memory: 400M
```

#### 11.4.2 `/srv/mvp-data/nginx/conf.d/default.conf`

```nginx
server {
    listen 80;
    server_name your-domain.duckdns.org;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl http2;
    server_name your-domain.duckdns.org;

    ssl_certificate /etc/letsencrypt/live/your-domain.duckdns.org/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.duckdns.org/privkey.pem;

    # 필수 1: 스마트폰 고해상도 사진 업로드 허용 (Spring Boot 20MB 설정 대응)
    client_max_body_size 25M;

    # 백엔드 API 및 프록시 설정
    location / {
        proxy_pass http://spring-app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 필수 2: OCR 및 AI 첨삭 응답 대기 타임아웃 여유 확보
        proxy_read_timeout 120s;
        proxy_connect_timeout 60s;
    }
}
```

---

### 11.5 데이터 영속성 및 S3 일일 DB 백업 자동화

단일 EC2 인스턴스에 내장된 MySQL 컨테이너는 인스턴스 장애(하드웨어 결함, 디스크 손상) 시 데이터가 영구 손실될 위험이 있습니다. 별도의 유료 RDS를 사용하지 않고도 안정성을 보장하기 위해, S3로의 일일 자동 백업 스크립트를 Crontab에 등록합니다.

#### 11.5.1 일일 백업 스크립트 (`/srv/mvp/scripts/backup-db.sh`)

```bash
#!/bin/bash
set -euo pipefail

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/tmp/db_backups"
BACKUP_FILE="${BACKUP_DIR}/db_backup_${DATE}.sql.gz"

mkdir -p "${BACKUP_DIR}"

# 1. 컨테이너 내부 DB 덤프 및 압축 (메모리 버퍼 최소화 스트리밍)
docker exec mvp-mysql mysqldump -u root -p"${DB_ROOT_PASSWORD}" --single-transaction --quick --databases "${DB_NAME}" | gzip > "${BACKUP_FILE}"

# 2. S3 백업 전용 경로로 업로드
aws s3 cp "${BACKUP_FILE}" "s3://${AWS_S3_BUCKET}/backups/db_backup_${DATE}.sql.gz"

# 3. 임시 로컬 덤프 파일 즉시 삭제
rm -f "${BACKUP_FILE}"

echo "[$(date)] Database backup completed successfully: db_backup_${DATE}.sql.gz"
```

#### 11.5.2 Cron 스케줄러 등록 및 S3 수명주기 정책

* **EC2 Crontab 등록 (매일 새벽 04:00 자동 실행)**:
  ```bash
  0 4 * * * /bin/bash /srv/mvp/scripts/backup-db.sh >> /var/log/db-backup.log 2>&1
  ```
* **S3 버킷 수명주기(Lifecycle) 규칙**:
  - `backups/` 접두사 객체에 대해 30일 경과 시 자동 영구 삭제(Expiration) 규칙을 활성화하여 S3 스토리지 비용을 최소화($0.05/월 미만 유지).

---

### 11.6 74일 후 30분 무중단 마이그레이션 런북

74일 후 현재 AWS 계정의 크레딧($41)이 소진되더라도, 모든 설정과 영속 데이터가 호스트 볼륨(`/srv/mvp-data`)과 S3에 표준화되어 있어 30분 이내에 신규 인프라로 즉시 이전할 수 있습니다.

```mermaid
flowchart TD
    A["신규 인프라 준비\n(새 AWS 계정, Lightsail, NCP, 또는 타사 클라우드)"] --> B["Docker & Compose 설치 및 /srv/mvp-data 디렉토리 생성"]
    B --> C["최신 S3 DB 덤프 다운로드 및 MySQL 복원\n(또는 rsync로 /srv/mvp-data 복제)"]
    C --> D["docker compose up -d 실행 및 헬스체크 확인"]
    D --> E["DuckDNS 대시보드에서 A 레코드 IP를 신규 서버 IP로 변경"]
    E --> F["마이그레이션 완료 (DNS 전파 즉시 서비스 정상화, 다운타임 < 30분)"]
```

---

### 11.7 환경별 설정 프로파일 분리 전략 (Local / Dev / Prod)

운영 안정성과 개발 배포 QC의 엄격한 분리를 위해 Spring Boot 프로파일을 3계층(로컬, 개발 배포, 실제 운영)으로 표준화한다.

| 프로파일 | 파일명 | 대상 환경 및 용도 | Swagger 활성화 여부 | DB 연결 및 DDL 정책 | 주요 환경변수 규격 |
| --- | --- | --- | --- | --- | --- |
| **`local`** | `application-local.yaml` | 개발자 로컬 PC 개발 | **ON (`true`)** | 로컬 MySQL (또는 H2), `ddl-auto: update`, SQL 출력 | `LOCAL_DB_URL`, `LOCAL_DB_USER`, `AWS_REGION`, `AWS_S3_BUCKET` (기본값 제공) |
| **`dev`** | `application-dev.yaml` | **Railway 개발 배포 (QC 테스트베드)** | **ON (`true` - QC 검증용)** | Railway 관리형 MySQL, `ddl-auto: update`, 포트 동적 바인딩 | `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD`, `PORT`, `AWS_REGION`, `AWS_S3_BUCKET`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` |
| **`prod`** | `application-prod.yaml` | **AWS EC2 실제 운영 배포** | **OFF (`false` - 운영 보안)** | 단일 EC2 내 Docker Compose MySQL, `ddl-auto: validate`, 보안 강화 | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_NAME`, `AWS_REGION`, `AWS_S3_BUCKET` (EC2 IAM Role 연동) |
| **`test`** | `src/test/resources/application.yaml` | Gradle 빌드 및 CI 파이프라인 | **ON (`true` - 테스트 검증용)** | 인메모리 H2 (`MODE=MySQL`), `ddl-auto: create-drop` | 독립 실행 (외부 종속성 없음) |

- **시드 데이터(`DevDataInitializer`) 격리**:
  - `@Profile({"local", "dev"})`를 적용하여 로컬 및 Railway QC 환경에서만 대표원장(`director@autedi.com`) 및 선생님(`teacher@autedi.com`) 계정이 자동 생성되도록 보장하고, 실제 운영(`prod`) 및 단위 테스트(`test`) 환경은 원천 격리한다.

---

### 11.8 운영 DB 스키마 관리 전략 및 점진적 마이그레이션 운영 수칙 (DDL validate Policy)

#### 11.8.1 의사결정 배경 및 철학
MVP 개발 및 정식 런칭 전 단계에서는 개발 편의를 위해 `create` 또는 `update`를 임시로 허용할 수도 있으나, **AUTEDI 팀은 개발 초기부터 운영(`prod`) 환경의 JPA DDL 정책을 `validate`로 엄격히 고정**하기로 결정했다.

* **결정 이유**:
  1. **데이터 영구 유실 방지**: `create` 모드는 재기동 시 `DROP TABLE`을 수행하므로 운영 환경에서 절대 사용 불가하다.
  2. **운영 장애 및 부작용 차단**: `update` 모드는 편리하지만, 코드 변경 시 이전 컬럼을 삭제하지 못해 쓰레기 컬럼이 누적되고, 배포 중 대용량 테이블 DDL 자동 실행으로 인한 락(Lock) 장애를 유발할 수 있다.
  3. **실무 운영 역량 내재화**: 코드와 DB의 생명주기를 분리하고, PR 단계에서 스키마 변경을 명시하며, 배포 전 DB에 선반영하는 **프로덕션 수준의 마이그레이션 운영 프로세스를 지금부터 체화**한다.

---

#### 11.8.2 신규 DB 초기화 런북 (최초 1회 테이블 생성)
신규 인프라나 빈 MySQL 인스턴스에 처음으로 테이블을 생성할 때는 아래 1회성 오버라이드를 수행한 후 원복한다:
1. `/srv/mvp/docker-compose.yml`의 `spring-app.environment`에 임시 추가:
   `SPRING_JPA_HIBERNATE_DDL_AUTO: update`
2. `docker compose up -d` 실행 ➡️ Spring Boot 기동 중 전체 테이블 자동 생성 확인
3. 생성 완료 후 해당 환경변수 라인을 제거(또는 주석 처리)하고 다시 `docker compose up -d` 실행 ➡️ 원래의 `validate` 모드로 복귀

---

#### 11.8.3 스키마 변경 시 표준 운영 프로세스 (팀 협업 수칙)
향후 새로운 기능 개발로 인해 엔티티에 컬럼이 추가되거나 신규 테이블이 도입될 경우, 팀원 전원은 아래의 **"선(先) DDL 반영, 후(後) 코드 배포"** 절차를 준수한다:

1. **로컬/Dev 개발 및 검증**:
   - `local` 및 `dev` 환경(`ddl-auto: update`)에서 기능 구현 및 테스트 수행.
   - Hibernate 콘솔 출력 로그에서 생성된 DDL 쿼리(`ALTER TABLE ...`, `CREATE TABLE ...`)를 추출 및 검증.
2. **PR 본문에 DDL 쿼리 명시**:
   - GitHub PR 작성 시 `## 🗄️ DB 변경 사항` 섹션을 두고, 실행해야 할 SQL 쿼리를 정확히 기재.
   - 예시:
     ```sql
     ALTER TABLE users ADD COLUMN phone_number VARCHAR(20) NULL;
     ```
3. **운영 DB 선(先)반영 (EC2 터미널)**:
   - PR을 `main`에 머지(배포)하기 직전에, EC2 서버에 SSH 접속하여 MySQL 컨테이너 내에서 해당 쿼리를 수동 실행.
     ```bash
     docker exec -i mvp-mysql mysql -u root -p"${DB_ROOT_PASSWORD}" autedi << 'EOF'
     ALTER TABLE users ADD COLUMN phone_number VARCHAR(20) NULL;
     EOF
     ```
4. **`main` 머지 및 배포 (Validate 검증)**:
   - DDL 반영 완료 후 PR을 `main`에 머지 ➡️ GitHub Actions `deploy.yml` 트리거.
   - 운영 서버의 Spring Boot가 `validate` 모드로 실행되며, 새로 추가된 DB 스키마와 엔티티가 정확히 일치함을 확인하고 무중단 정상 기동.

---

#### 11.8.4 스키마 변경 시 안전 가이드라인
* **하위 호환성 보장 (Nullable / Default 필수)**:
  - 기존에 데이터가 존재하는 테이블에 컬럼을 추가할 때는 반드시 `NULL`을 허용하거나 명시적 `DEFAULT` 값을 지정해야 한다. (`NOT NULL` 기본값 미지정 금지)
* **컬럼 이름 변경/타입 변경 정책**:
  - 컬럼명을 직접 변경(`RENAME COLUMN`)하면 배포 전환 시점에 이전 버전 애플리케이션에서 에러가 발생할 수 있으므로, **[신규 컬럼 추가 ➡️ 데이터 마이그레이션 ➡️ 구 컬럼 제거]**의 점진적 배포 방식을 권장한다.
* **향후 고도화 경로**:
  - 팀 규모 확장 및 스키마 변경 빈도 증가 시, 형상관리 도구인 **Flyway**를 도입하여 Git 커밋과 DB 버전 마이그레이션을 완전 자동화한다.

---

### 11.9 상용 B2B 서비스를 위한 엄격한 브랜치 보호(Protected Branch) 및 PR 의무화 정책

#### 11.9.1 정책 수립 배경 및 목적 (상용 유료 B2B SaaS 신뢰성 보장)
AUTEDI는 교육기관(학원, 영어 도서관)을 대상으로 구독료를 청구하는 **유료 상용 B2B SaaS**를 지향한다. 단순 토이 프로젝트나 학습용 레포지토리와 달리, 실제 고객의 결제와 학생 과제물 피드백이 실시간으로 이루어지므로 단 한 번의 사소한 실수로도 서비스 중단, 데이터 오염, 고객 이탈이 발생할 수 있다.

이에 따라 팀원 편의나 속도보다 **안정성(Stability)과 추적성(Traceability)**을 최상위 가치로 두고, `main`뿐만 아니라 **`develop` 브랜치까지 예외 없이 직접 푸시(Direct Push)를 전면 금지**하고 PR 및 자동화 검증을 강제한다.

---

#### 11.9.2 브랜치 보호 규칙 (Branch Protection Rules) 상세

| 보호 대상 브랜치 | 직접 푸시(`git push`) 허용 여부 | 필수 상태 검사 (Required Status Checks) | 배포 연동 대상 |
| :--- | :---: | :--- | :--- |
| **`develop`** | **❌ 전면 금지 (Admin 포함)** | • **Spotless & Unit Tests** (코드 포맷 및 테스트 100% 통과)<br>• PR 리뷰 및 머지 승인 필수 | **Railway 개발 배포** (QC 테스트베드 자동 배포) |
| **`main`** | **❌ 전면 금지 (Admin 포함)** | • **Spotless & Unit Tests** 통과 필수<br>• `develop` 검증 완료 커밋만 인입 허용 | **AWS EC2 실제 운영 배포** (Docker Hub ➡️ EC2 무중단 배포) |

* **`develop` 브랜치까지 엄격히 통제하는 핵심 이유**:
  1. **Railway QC 환경 보호**: `develop` 브랜치는 프론트엔드 연동 및 상시 QC 테스트가 진행되는 실시간 서버이다. 깨진 코드가 직접 푸시될 경우 프론트엔드 개발팀 전체의 작업이 즉시 중단(Block)된다.
  2. **오타/단순 문서 수정도 예외 없음**: "단순한 1줄 수정이니까 괜찮겠지"라는 방심에서 시작되는 설정 오류 및 빌드 실패를 원천 봉쇄한다. 모든 변경은 CI 파이프라인의 기계적 검증을 통과해야만 브랜치에 합류할 수 있다.
  3. **완벽한 변경 이력 아카이빙**: 모든 기능, 버그 수정, 문서 및 인프라 변경이 GitHub PR과 연결되어 영구 보존되므로, 장애 발생 시 원인 커밋과 맥락을 수 분 내로 역추적할 수 있다.

---

#### 11.9.3 표준 브랜치 전략 및 작업 흐름 (Branching Workflow)

```mermaid
flowchart LR
    subgraph Feature["작업 브랜치"]
        F["feat/*, fix/*, docs/*"]
    end

    subgraph CI1["1단계 검증 (PR)"]
        C1["GitHub Actions CI\n(SpotlessCheck + Tests)"]
    end

    subgraph Develop["개발 통합 브랜치"]
        D["develop (Protected)"]
        R["Railway 배포\n(프론트 연동 & QC)"]
    end

    subgraph CI2["2단계 검증 (Release PR)"]
        C2["Release PR 검증"]
    end

    subgraph Main["운영 브랜치"]
        M["main (Protected)"]
        E["AWS EC2 운영 배포\n(Docker Compose)"]
    end

    F -->|"PR 생성"| C1
    C1 -->|"Pass 후 머지"| D
    D -->|"Auto Deploy"| R
    D -->|"PR 생성 (develop ➡️ main)"| C2
    C2 -->|"머지 승인"| M
    M -->|"GitHub Actions"| E
```

1. **작업 브랜치 생성**: 모든 작업은 최신 `develop`을 기반으로 `feat/*`, `fix/*`, `docs/*`, `chore/*` 접두사를 붙여 생성한다.
2. **로컬 커밋**: `pre-commit` 훅을 통해 Palantir Java Format이 자동 적용된다.
3. **PR 생성 및 CI 통과**: `develop`을 대상으로 PR을 열고, GitHub Actions의 `Spotless & Unit Tests`가 성공해야 머지 버튼이 활성화된다.
4. **개발 서버 검증**: `develop` 머지 즉시 Railway에 자동 배포되어 API 및 비즈니스 로직을 검증한다.
5. **운영 릴리즈**: 검증 완료된 `develop` 브랜치를 `main`으로 PR 요청하여 최종 배포를 진행한다.

---

#### 11.9.4 브랜치별 머지 전략 표준 (Merge Strategy Guidelines)
GitHub PR 머지 시 브랜치 특성에 따라 허용되는 머지 방식을 엄격히 구분한다:

| 머지 경로 | 허용 머지 방식 | 상세 이유 및 목적 |
| :--- | :---: | :--- |
| **`feature/* ➡️ develop`** | **Squash and Merge** | 기능 개발 중에 발생한 자잘한 작업 커밋(오타 수정, 시도 등)을 1개의 기능 단위 커밋으로 압축하여 `develop`의 히스토리를 선형적이고 깔끔하게 유지한다. |
| **`develop ➡️ main`** | **Create a Merge Commit<br>(일반 머지 - 필수)** | • `develop`에서 검증된 여러 기능 커밋들의 전체 타임라인과 이력을 원본 그대로 보존한다.<br>• **주의**: 만약 여기서 Squash 머지를 수행할 경우 `develop`과 `main`의 커밋 해시 트리가 분기되어, **차후 `develop ➡️ main` 머지 시 대규모 충돌(Conflict)과 히스토리 왜곡이 발생**하므로 절대 금지한다. |

---

### 11.10 Railway 개발 배포 운영 규격 및 트러블슈팅 런북 (Multi-stage Docker & Configuration)

#### 11.10.1 Dockerfile 멀티 스테이지 빌드 전환 원리
초기 런타임 전용 Dockerfile(`COPY build/libs/*SNAPSHOT.jar app.jar`)은 로컬 또는 GitHub Actions 가상머신에서 사전에 Gradle 빌드가 선행되었다는 전제 하에 작성되었다. 그러나 Railway 등 클라우드 PaaS는 소스코드를 직접 내려받아 `docker build`를 즉시 실행하므로, `build/libs` 디렉터리가 없어 배포가 중단되는 문제(`lstat /build/libs: no such file or directory`)가 발생했다.

이를 해결하기 위해 **도커 자체에서 빌드와 실행을 완전히 캡슐화하는 2단계 멀티 스테이지 빌드**를 표준으로 확정했다:
```dockerfile
# 1단계: 빌드 환경 (JDK 21) - 컨테이너 내부에서 안전하게 bootJar 생성
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x ./gradlew
COPY src src
RUN ./gradlew clean bootJar -x test

# 2단계: 실행 환경 (가벼운 JRE 21) - 최종 경량 실행 이미지 생성
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```
* **도입 효과**:
  - Railway는 물론 로컬 PC, GitHub Actions, EC2 등 Docker가 설치된 모든 환경에서 호스트 OS 종속성 없이 동일한 이미지 빌드 및 실행을 100% 보장한다.
  - `.dockerignore`를 통해 `.git`, `.gradle`, `build`, `docs`를 제외하여 빌드 컨텍스트 전송 속도를 극대화했다.

---

#### 11.10.2 Railway 대시보드 3대 필수 설정 규칙 (Troubleshooting Runbook)
Railway 대시보드 설정과 Dockerfile 설정 간의 충돌로 인해 발생하는 배포 장애를 방지하기 위해 다음 3대 규칙을 강제한다:

1. **Custom Start Command는 반드시 비워둘 것 (Empty / Clear)**:
   - **발생했던 문제**: 과거 Nixpacks 빌드 시절 대시보드에 설정된 `java -jar build/libs/autedi-0.0.1-SNAPSHOT.jar` 명령어가 Dockerfile의 `ENTRYPOINT`를 강제 덮어쓰기하여 `Error: Unable to access jarfile build/libs/...` 에러 발생.
   - **해결 원칙**: Railway는 Custom Start Command가 설정되어 있으면 Dockerfile의 `ENTRYPOINT/CMD`를 무시한다. 멀티 스테이지 컨테이너의 실행 파일은 `/app/app.jar`이므로, **Start Command를 완전히 빈 칸으로 비워두어 Dockerfile의 `ENTRYPOINT ["java", "-jar", "app.jar"]`를 그대로 따르도록 한다.**
2. **`Wait for CI` 옵션 비활성화 (OFF)**:
   - **발생했던 문제**: `Wait for CI`가 켜져 있으면 Railway가 GitHub Actions 검사 완료 웹훅을 무한 대기한다. 하지만 `ci.yml`이 `on: pull_request`로만 동작하고 `push to develop` 트리거가 없으므로, PR 머지 시 배포가 트리거되지 않고 Skip되는 현상 발생.
   - **해결 원칙**: PR 단계에서 이미 CI 검증이 100% 완료된 후 `develop`에 머지되므로, 불필요한 대기 없이 즉각 배포되도록 **`Wait for CI`는 상시 OFF로 유지**한다.
3. **`PR Environments` (임시 프리뷰) 비활성화 (OFF)**:
   - **해결 원칙**: 개발 서버 QC는 `develop` 브랜치 본체 배포 하나로 충분하며, PR이 열릴 때마다 불필요한 임시 DB/컨테이너가 복제 생성되어 자원이 낭비되는 것을 방지하기 위해 PR Environments는 OFF로 관리한다.

---

#### 11.10.3 Railway 개발 배포 환경변수 주입 규격
Railway 대시보드의 **Variables** 탭에 아래 환경변수가 누락 없이 등록되어 있어야 한다:
* **Database (Railway 관리형 MySQL)**: `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD`
* **Server Port**: `PORT` (Railway가 동적 바인딩)
* **AWS S3 자격증명 (QC 이미지 저장용)**:
  - `AWS_REGION`: `ap-northeast-2`
  - `AWS_S3_BUCKET`: `autedi-mvp-develop-bucket`
  - `AWS_ACCESS_KEY_ID`: IAM 발급 키
  - `AWS_SECRET_ACCESS_KEY`: IAM 발급 시크릿

---

### 11.11 이메일 발송 인프라(SMTP/SES) 아키텍처 및 비동기 처리 규격 (Email Notification Architecture)

#### 11.11.1 비동기 스레드 풀 격리 설계 (`@EnableAsync`, `AsyncConfig`, `mailExecutor`) 및 메인 HTTP 서블릿 스레드 블로킹 방지 원리

##### 1. 동기 메일 발송의 치명적 병목 및 결합도 문제
SMTP(Simple Mail Transfer Protocol)는 외부 통신 프로토콜로서 DNS 질의, TCP 3-way Handshake, STARTTLS 암호화 협상, SMTP AUTH 인증, MIME 데이터 송수신 등 다단계 네트워크 통신을 거친다. 외부 메일 서버(Gmail, AWS SES 등)의 상황에 따라 통상 500ms ~ 3,000ms 이상의 긴 레이턴시가 발생하며, 일시적 네트워크 지연 시에는 수십 초까지 지연될 수 있다.

만약 HTTP 서블릿 스레드(Tomcat Worker Thread)에서 메일 발송을 동기식(`Synchronous`)으로 처리할 경우 다음과 같은 심각한 장애가 유발된다:
1. **서블릿 스레드 고갈 및 연쇄 장애 (Thread Starvation & Cascading Failure)**:
   - 외부 SMTP 지연 시 톰캣 워커 스레드가 응답 대기 상태로 블로킹되어 빠르게 고갈된다. 이로 인해 메일과 무관한 일반 사용자(로그인, 학습지 조회 등)의 HTTP 요청까지 타임아웃이 발생하여 시스템 전체가 마비된다.
2. **DB 트랜잭션 및 커넥션 장기 점유 (HikariCP Connection Starvation)**:
   - `@Transactional` 범위 내에서 동기 메일 발송을 수행하면, 메일 발송이 끝날 때까지 DB 커넥션을 반환하지 못하고 락(Lock) 및 커넥션 풀을 불필요하게 점유하여 DB 병목을 가속화한다.
3. **사용자 경험(UX) 훼손 및 트랜잭션 오염**:
   - 원장 선생님이 [선생님 초대] 버튼을 누른 후 메일 발송이 끝날 때까지 수 초간 로딩 스피너를 대기해야 하며, 네트워크 순시 장애로 메일 발송이 실패하면 초대 레코드 생성 트랜잭션까지 롤백되는 부작용이 발생한다.

##### 2. 비동기 스레드 풀 격리 설계 (`AsyncConfig.java`)
이러한 문제를 원천 차단하기 위해 Spring의 `@EnableAsync` 기반 전용 `ThreadPoolTaskExecutor`(`mailExecutor`)를 도입하여, 메일 발송 작업을 메인 서블릿 스레드 및 기본 스프링 작업 풀로부터 완전 물리/논리 격리하였다.

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
```

* **스레드 풀 튜닝 사양**:
  - `corePoolSize(2)`: 서버 평상시 최소 2개의 백그라운드 스레드를 유지하여 초대 메일 발송을 즉각 처리.
  - `maxPoolSize(5)`: 특정 학원에서 다수의 교사를 일괄 초대하거나 동시 요청이 몰릴 경우 최대 5개까지 동적 확장.
  - `queueCapacity(100)`: 최대 100건의 메일 발송 작업을 메모리 큐에 대기 버퍼링하여 트래픽 버스트 수용.
  - `threadNamePrefix("mail-exec-")`: 로깅 및 APM(핀포인트, 데이터독 등) 모니터링 시 메일 전담 스레드를 즉시 식별 가능.
  - `waitForTasksToCompleteOnShutdown(true)` & `awaitTerminationSeconds(30)`: 서버 무중단 배포 및 종료(SIGTERM) 시, 이미 큐에 인입된 초대 메일 발송 작업을 최대 30초간 정상 완료한 후 안전하게 종료(Graceful Shutdown)하여 메일 유실 방지.

* **SMTP 연결 레벨 소켓 타임아웃 방어막 (`application.yaml`)**:
  ```yaml
  spring:
    mail:
      properties:
        mail:
          smtp:
            connectiontimeout: 5000  # 소켓 연결 타임아웃 (5초)
            timeout: 5000            # 응답 읽기 타임아웃 (5초)
            writetimeout: 5000       # 데이터 쓰기 타임아웃 (5초)
  ```
  - 외부 SMTP 서버가 응답하지 않는 최악의 상황에서도 스레드가 무한정 행(Hang) 상태에 빠지지 않고 최대 5초 내에 실패 처리 및 스레드 풀로 반환되도록 보장한다.

##### 3. 메인 서블릿 스레드와 비동기 워커 스레드의 상호작용 흐름

```mermaid
sequenceDiagram
    autonumber
    actor Director as 원장 (Client)
    participant Controller as InvitationController
    participant Service as InvitationService
    participant DB as MySQL (JPA)
    participant Async as Spring @Async Proxy
    participant Pool as mailExecutor Thread
    participant SMTP as SMTP Server (SES/Gmail)

    Director->>Controller: POST /api/v1/invitations (선생님 초대)
    Controller->>Service: createInvitation(...)
    activate Service
    Service->>DB: DB 토큰 저장 및 PENDING 등록 (@Transactional)
    DB-->>Service: Commit 완료
    Service->>Async: emailService.sendInvitationEmail(...) 호출
    Async-->>Pool: 비동기 발송 Task 제출 (Queue 인입)
    Service-->>Controller: InvitationResponse 반환
    deactivate Service
    Controller-->>Director: 201 CREATED (응답 소요: < 20ms)

    Note over Pool,SMTP: 백그라운드 스레드에서 비동기 격리 실행
    activate Pool
    Pool->>SMTP: STARTTLS Handshake & MimeMessage 전송 (500~2000ms)
    alt 전송 성공
        SMTP-->>Pool: 250 OK
        Pool->>Pool: log.info("초대 이메일 발송 성공: to={}")
    else 전송 실패 (SMTP 장애)
        SMTP-->>Pool: Error (Timeout / Auth Failed)
        Pool->>Pool: log.error("초대 이메일 발송 실패: to={}, error={}")
        Note over Pool: try-catch 방어로 예외 격리 (DB 롤백 없음)
    end
    deactivate Pool
```

> [!NOTE]
> **트랜잭션 및 비즈니스 예외 격리 원칙**:
> `EmailService.sendInvitationEmail` 메서드는 `@Async("mailExecutor")` 애노테이션을 통해 별도 스레드에서 수행되며, 내부 `try-catch` 블록으로 모든 메일 전송 예외(`Exception`)를 캡슐화하여 로깅한다. 따라서 외부 메일 서버 장애가 발생하더라도 이미 커밋된 초대장 발급 트랜잭션(`UserInvitation`)은 안전하게 영속되며, 클라이언트에게 500 Internal Server Error가 전파되지 않는다.

---

#### 11.11.2 환경별(Local / Dev / Prod) 다형성 메일 발송 아키텍처

AUTEDI는 개발자의 로컬 개발 편의성, 개발 서버(Railway)의 SMTP 포트(587) 방화벽 제한 회피, 그리고 운영 서버(AWS SES)의 엔터프라이즈급 신뢰도를 모두 충족하기 위해 **인터페이스 기반 다형성(DIP)과 조건부 빈 등록(`MailConfig`)**을 통한 환경별 발송 전략을 수립했다.

```mermaid
classDiagram
    class EmailService {
        -EmailSender emailSender
        -String frontendUrl
        +sendInvitationEmail(toEmail, academyName, rawToken)
    }

    class EmailSender {
        <<Interface>>
        +send(toEmail, fromAddress, fromName, subject, htmlContent) void
    }

    class SmtpEmailSender {
        -ObjectProvider~JavaMailSender~ mailSenderProvider
        +send() void
    }

    class HttpEmailSender {
        -RestClient restClient
        -String apiKey
        -String endpoint
        +send() void
    }

    EmailService --> EmailSender : 의존성 역전(DIP)
    SmtpEmailSender ..|> EmailSender : SMTP 표준 (Local/Prod)
    HttpEmailSender ..|> EmailSender : HTTPS 443 REST API (Railway Dev)
```

| 환경 (Profile) | 활성 구현체 (`EmailSender`) | 프로토콜 및 포트 | 자격증명 관리 | Mocking / Fallback 동작 | 주 목적 및 기대 효과 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Local** (`local`) | `SmtpEmailSender`<br>(Default Fallback) | SMTP (포트 587/465) | `.env` 파일<br>(미설정 허용) | `JavaMailSender` 미설정 시<br>**경고 로그 + 콘솔 링크 출력**으로 대체 | 로컬 개발 시 SMTP 계정 설정 없이도<br>터미널 로그의 초대 링크로 가입 E2E 즉시 테스트 |
| **Dev** (`dev`) | `HttpEmailSender`<br>(Railway 전용) | **HTTPS (포트 443)**<br>(REST API) | Railway 대시보드<br>`RESEND_API_KEY` | API 키 미입력 시 로깅 대체,<br>입력 시 443 포트로 실제 발송 | **Railway Hobby 플랜의 SMTP 포트(587) 차단 방화벽을 100% 회피**하여 안정적 메일 수신 |
| **Prod** (`prod`) | `SmtpEmailSender`<br>(or AWS SES SDK) | HTTPS (포트 443)<br>or SMTP | EC2 IAM Role /<br>AWS SES Credential | 예외 격리 로깅 및<br>CloudWatch 모니터링 | 대규모 발송 SLA, 높은 수신 도달률(Inbox),<br>도메인 위변조 방지 및 반송률 관리 |

##### 1. Local 환경: 유연한 무중단 로깅 대체(Graceful Mocking) 및 팀 Gmail SMTP
* **무중단 로깅 대체 (Safe Mocking Fallback)**:
  - 신규 팀원이 프로젝트를 클론한 뒤 `.env`에 SMTP 설정을 기재하지 않더라도, 초대 API 호출 시 NullPointer나 BeanCreation 에러가 발생하지 않도록 `SmtpEmailSender`에 방어 로직을 구현했다:
    ```java
    JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
    if (mailSender == null) {
        log.warn("JavaMailSender가 설정되지 않아 메일 발송을 로깅으로 대체합니다. (To: {}, Subject: {})", toEmail, subject);
        return;
    }
    if (mailSender instanceof JavaMailSenderImpl mailSenderImpl) {
        if (mailSenderImpl.getHost() == null
                || mailSenderImpl.getHost().trim().isEmpty()
                || mailSenderImpl.getUsername() == null
                || mailSenderImpl.getUsername().trim().isEmpty()) {
            log.warn("메일 발송 호스트 또는 인증 계정(MAIL_USERNAME)이 설정되지 않아 메일 발송을 로깅으로 대체합니다.");
            return;
        }
    }
    ```
  - `MAIL_USERNAME` 또는 `MAIL_HOST`가 비어있을 경우, 콘솔에 초대 URL이 즉시 출력되어 개발자가 복사-붙여넣기로 회원가입 흐름을 원활히 테스트할 수 있다.
* **로컬에서 실제 메일 발송 테스트 (Gmail SMTP)**:
  - 개인/팀 Gmail 계정의 [2단계 인증] 활성화 ➡️ [앱 비밀번호(16자리)] 생성 후 `.env`에 설정:
    ```env
    MAIL_TYPE=smtp
    MAIL_HOST=smtp.gmail.com
    MAIL_PORT=587
    MAIL_USERNAME=autedi.team@gmail.com
    MAIL_PASSWORD=xxxx xxxx xxxx xxxx
    MAIL_FROM_ADDRESS=autedi.team@gmail.com
    MAIL_FROM_NAME=AUTEDI
    FRONTEND_URL=http://localhost:3000
    ```

##### 2. Dev (Railway) 환경: HTTPS 443 포트 REST API (`HttpEmailSender`)로 방화벽 회피
* **도입 배경**:
  - Railway는 스팸 방지 보안 정책상 Hobby 플랜을 포함한 기본 티어에서 외부 SMTP 포트(25, 465, 587)로의 아웃바운드 연결을 방화벽에서 강제로 차단(`SocketTimeoutException: Connect timed out`)한다.
  - 이를 해결하기 위해 웹 표준 포트인 **HTTPS (443 포트)**를 통해 이메일을 전송하는 `HttpEmailSender`를 신설하고, `application-dev.yaml`에서 `app.mail.type: http`를 기본값으로 지정했다.
* **Resend API 연동 규격**:
  - 엔드포인트: `POST https://api.resend.com/emails` (포트 443)
  - 헤더: `Authorization: Bearer {RESEND_API_KEY}`
  - JSON 페이로드:
    ```json
    {
      "from": "AUTEDI <onboarding@resend.dev>",
      "to": ["teacher@example.com"],
      "subject": "[에이플러스 학원] 선생님 초대장",
      "html": "<!DOCTYPE html>..."
    }
    ```
  - Railway 대시보드의 **Variables** 탭에 `RESEND_API_KEY` 환경변수만 등록하면, 추가 포트 개방 없이 100% 정상 발송된다.

##### 3. Prod (AWS EC2) 환경: 정식 서비스 런칭 시 AWS SES 전용 인프라 전환
* 정식 상용화 시점에는 `autedi.com` 커스텀 도메인 등록 후 AWS SES(서울 리전: `ap-northeast-2`)를 표준 게이트웨이로 전환하여, `noreply@autedi.com` 발신 전용 체계로 무제한/고신뢰도 발송을 운영한다. AWS 환경에서는 보안 그룹 기본 규칙상 아웃바운드 포트가 차단되지 않으며, AWS SDK(HTTPS 443)로도 완벽히 호환된다.

---

#### 11.11.3 반응형 HTML 초대장 템플릿 표준 규격 및 발신자 정책

##### 1. 발신자(Sender) 및 헤더 표준 정책
* **발신 주소(From Address)**: `noreply@autedi.com`
  - 도메인 네임스페이스(`autedi.com`)를 통일하여 주요 메일 서비스(Gmail, Naver, Daum, Outlook)의 발신 도메인 불일치 스팸 분류 방지.
* **발신자 명(From Name)**: `AUTEDI`
* **메일 제목(Subject)**: `[{academyName}] 선생님 초대장`
  - 원장 선생님이 소속된 학원명(예: `[대치 정상어학원] 선생님 초대장`)을 동적으로 주입하여 수신 교사의 신뢰성 확보 및 피싱 오인 방지.
* **문자셋 인코딩**: `UTF-8` 강제 (`new MimeMessageHelper(mimeMessage, false, "UTF-8")`)
  - 한글 학원명 및 안내 본문 깨짐 현상 원천 방지.

##### 2. 반응형 HTML 템플릿 디자인 사양 (`buildInvitationHtml`)
이메일 클라이언트는 웹 브라우저와 달리 외부 CSS 파일(`<link>`) 및 `<style>` 블록을 차단하거나 제한적으로 파싱하므로, **모든 서식은 인라인 스타일(Inline CSS)**로 완결되도록 설계했다:

```html
<!DOCTYPE html>
<html lang="ko">
<head><meta charset="UTF-8"></head>
<body style="margin:0;padding:20px;background-color:#f8fafc;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
  <div style="max-width:540px;margin:0 auto;background:#ffffff;border-radius:12px;border:1px solid #e2e8f0;padding:36px;box-shadow:0 4px 6px -1px rgba(0,0,0,0.05);">
    <!-- 헤더 -->
    <div style="margin-bottom:24px;">
      <h2 style="margin:0 0 8px 0;color:#0f172a;font-size:22px;font-weight:700;">선생님 초대장</h2>
      <p style="margin:0;color:#64748b;font-size:14px;">AUTEDI 초등 영어 학습지 AI 첨삭 서비스</p>
    </div>
    <!-- 본문 안내 -->
    <div style="line-height:1.7;color:#334155;font-size:15px;margin-bottom:32px;">
      <p style="margin:0 0 16px 0;">안녕하세요.<br><strong style="color:#0f172a;">{academyName}</strong>에 선생님으로 초대되었습니다.</p>
      <p style="margin:0 0 16px 0;">아래 버튼을 클릭하여 회원가입을 완료해 주시면, 소속 학원의 학습지 첨삭 및 코멘트 작성 업무를 시작하실 수 있습니다.</p>
      <p style="margin:0;color:#ef4444;font-size:13px;">* 본 초대 링크는 발송 시점으로부터 24시간 동안만 유효합니다.</p>
    </div>
    <!-- CTA 버튼 -->
    <div style="text-align:center;margin-bottom:32px;">
      <a href="{inviteLink}" target="_blank" style="display:inline-block;padding:14px 28px;background-color:#2563eb;color:#ffffff;text-decoration:none;font-weight:600;font-size:15px;border-radius:8px;box-shadow:0 2px 4px rgba(37,99,235,0.2);">초대 수락하고 회원가입하기</a>
    </div>
    <!-- 보안 만료 및 Fallback 링크 푸터 -->
    <div style="border-top:1px solid #e2e8f0;padding-top:20px;font-size:12px;color:#94a3b8;line-height:1.6;">
      <p style="margin:0 0 8px 0;">버튼이 동작하지 않을 경우 아래 링크를 브라우저 주소창에 직접 붙여넣어 주세요:</p>
      <p style="margin:0;word-break:break-all;"><a href="{inviteLink}" style="color:#2563eb;text-decoration:underline;">{inviteLink}</a></p>
      <p style="margin:16px 0 0 0;">본 메일은 {academyName}의 원장 선생님 요청으로 발송되었습니다.</p>
    </div>
  </div>
</body>
</html>
```

* **템플릿 핵심 UX/보안 가이드라인**:
  1. **모바일 최적화 레이아웃**: 최대 너비 `540px`의 카드 컨테이너와 `-apple-system` 산세리프 폰트를 적용하여 데스크톱 및 스마트폰 화면에서 가독성을 보장.
  2. **명확한 CTA 버튼**: 고대비 블루 컬러(`#2563eb`)의 큼직한 가입 버튼을 배치하여 클릭률 극대화.
  3. **보안 유효기간 명시**: Section 2.3 및 3.1에 명시된 정책에 따라, `24시간 유효` 경고 문구를 붉은색(`#ef4444`)으로 강조 안내.
  4. **텍스트 뷰어 대비 Fallback URL**: 이미지/버튼 링크가 비활성화된 폐쇄망 환경을 대비하여 하단에 URL 원본 텍스트를 노출하고 `word-break: break-all`로 줄바꿈 처리.
  5. **발송 출처 명시**: 학원 원장의 정당한 요청으로 생성되었음을 명시하여 스팸 신고 방지.

---

#### 11.11.4 운영 환경 AWS SES 연동 및 도메인(autedi.com) DNS(DKIM, SPF, DMARC) 인증 런북

상용 B2B 서비스에서 기업 메일 서버나 대형 포털(Gmail, 네이버 등)에 이메일을 안정적으로 도달시키기 위해서는 발신 도메인에 대한 **암호학적 서명(DKIM)과 발신 서버 공인(SPF), 그리고 도메인 보호 정책(DMARC) 3대 인증**이 필수적이다. 인증이 누락될 경우 Gmail의 강력한 스팸 방어 정책(2024년 발효된 대량 발신자 의무 규정)에 의해 메일이 즉시 반송(Reject)되거나 스팸함으로 직행한다.

##### 1. AWS SES 설정 및 Production 모드 전환 (Sandbox 해제)
1. **AWS Management Console 접속**: 리전을 **`ap-northeast-2 (서울)`**로 선택 후 **Amazon Simple Email Service**로 이동.
2. **도메인 Identity 생성**:
   - [Configuration] ➡️ [Identities] ➡️ [Create identity] 클릭.
   - Identity type: **Domain** 선택.
   - Domain: `autedi.com` 입력.
   - Advanced DKIM settings: **Easy DKIM** 선택, DKIM key length: **RSA_2048_BIT** 선택.
   - [Create identity] 완료.
3. **SES Production Sandbox 해제 신청**:
   - 신규 AWS 계정의 SES는 Sandbox 모드로 시작되어 사전에 인증된 이메일 주소로만 발송이 가능하다.
   - [Account dashboard] ➡️ [Request production access] 클릭:
     - Mail type: **Transactional** (B2B SaaS 초대장 및 알림 메일)
     - Website URL: `https://autedi.com`
     - Use case description: "AUTEDI B2B SaaS 학원 선생님 초대 및 시스템 알림 메일 발송용. 모든 메일은 원장의 명시적 초대 행위로만 발송되며 마케팅 스팸 발송은 일절 없음" 기재 후 제출. (통상 24~48시간 내 승인)

##### 2. DNS 3대 인증 레코드 등록 런북 (Route 53 / 호스팅 네임서버)
도메인 네임서버 관리 콘솔(Route 53 또는 가비아/Cloudflare 등)에 아래 레코드를 정확히 등록한다:

| 인증 규격 | 레코드 타입 (Type) | 이름 / 호스트 (Host/Name) | 값 / 데이터 (Value/Content) | TTL | 설명 및 역할 |
| :--- | :---: | :--- | :--- | :---: | :--- |
| **SPF** | **TXT** | `@` (또는 `autedi.com`) | `v=spf1 include:amazonses.com ~all` | 300 | `autedi.com` 도메인 명의로 메일을 발송할 권한이 있는 공인 발송 인프라로 AWS SES를 지정. |
| **DKIM #1** | **CNAME** | `[token1]._domainkey.autedi.com` | `[token1].dkim.amazonses.com` | 300 | AWS SES가 제공하는 Easy DKIM 1번 공개키 CNAME 레코드. |
| **DKIM #2** | **CNAME** | `[token2]._domainkey.autedi.com` | `[token2].dkim.amazonses.com` | 300 | AWS SES가 제공하는 Easy DKIM 2번 공개키 CNAME 레코드. |
| **DKIM #3** | **CNAME** | `[token3]._domainkey.autedi.com` | `[token3].dkim.amazonses.com` | 300 | AWS SES가 제공하는 Easy DKIM 3번 공개키 CNAME 레코드 (키 로테이션용). |
| **DMARC** | **TXT** | `_dmarc.autedi.com` | `v=DMARC1; p=quarantine; rua=mailto:dmarc-reports@autedi.com; pct=100` | 300 | SPF/DKIM 인증 실패 메일을 스팸 격리(`quarantine`) 처리하고 집계 리포트를 수신하도록 수신 메일 서버에 지시. |

> [!IMPORTANT]
> **SPF 레코드 중복 주의**:
> DNS TXT 레코드에 `v=spf1 ...` 레코드는 도메인당 반드시 **단 1개만 존재**해야 한다. 만약 이미 구글 워크스페이스나 타 메일 서비스용 SPF가 존재한다면 두 개의 TXT 레코드를 만들지 말고 기존 레코드에 `include:amazonses.com`을 병합해야 한다:
> `v=spf1 include:_spf.google.com include:amazonses.com ~all`

##### 3. AWS SES SMTP 자격증명(Credentials) 생성
1. SES Console ➡️ [Configuration] ➡️ [SMTP settings] 메뉴로 이동.
2. SMTP Configuration 정보 확인:
   - **Server Name**: `email-smtp.ap-northeast-2.amazonaws.com`
   - **Port**: `587` (STARTTLS 지원 포트)
3. [Create SMTP credentials] 버튼 클릭:
   - IAM User Name 지정 (예: `ses-smtp-user.autedi`).
   - [Create] 클릭 후 생성된 **SMTP Username**과 **SMTP Password**를 안전하게 다운로드/보관 (비밀번호는 최초 1회만 노출됨).

##### 4. 운영 서버 환경변수 주입 규격 (`/srv/mvp/.env` or Docker Compose)
운영 EC2 인스턴스의 환경변수 파일에 아래 값을 누락 없이 주입한다:

```env
# ==============================================================================
# SMTP Mail Configuration (AWS SES Production)
# ==============================================================================
MAIL_HOST=email-smtp.ap-northeast-2.amazonaws.com
MAIL_PORT=587
MAIL_USERNAME=AKIAIOSFODNN7EXAMPLE
MAIL_PASSWORD=BJ45...[SES_SMTP_GENERATED_PASSWORD]...
MAIL_FROM_ADDRESS=noreply@autedi.com
MAIL_FROM_NAME=AUTEDI
FRONTEND_URL=https://autedi.com
```

##### 5. 발송 도메인 검증 및 수신 테스팅 체크리스트
- [ ] AWS SES Console에서 `autedi.com` 도메인의 Verification status 및 DKIM status가 모두 **"Success (Verified)"**로 표시되는지 확인.
- [ ] 운영 서버 기동 후 초대 API 호출 시 `mail-exec-*` 스레드에서 `초대 이메일 발송 성공: to=...` 로그 확인.
- [ ] Gmail, Naver 메일함으로 초대장을 발송하여 **스팸함이 아닌 일반 편지함(Inbox)**으로 수신되는지 확인.
- [ ] 수신된 메일의 원본 보기(Show Original)에서 **`SPF: PASS`, `DKIM: PASS`, `DMARC: PASS`** 3대 검증 항목이 모두 합격 판정을 받았는지 확인.

---

### 11.12 AWS S3 파일 스토리지 아키텍처 및 미디어 서빙 규격 (AWS S3 Storage Architecture & Zero-Frontend-Impact Media Serving)

#### 11.12.1 도입 배경 및 스토리지 전환 필요성

초기 개발 단계에서 채택했던 로컬 파일 스토리지(`LocalStorageService`)는 서버 인스턴스 내부의 로컬 디스크(`uploads/`)에 파일을 저장하고, 백엔드 애플리케이션의 엔드포인트(`/files/**`)를 통해 정적 리소스를 직접 서빙하는 방식을 취했다.

그러나 B2B 상용 SaaS 런칭 및 프론트엔드 연동 과정에서 다음과 같은 구조적 한계와 병목이 확인되었다:
1. **정적 리소스 인증 충돌 (`401 Unauthorized`)**:
   - 백엔드의 `SecurityConfig`는 무상태(Stateless) JWT Bearer 토큰 기반으로 보호된다.
   - 표준 HTML `<img src="...">` 태그는 브라우저 네이티브 HTTP GET 요청을 보내므로 별도의 `Authorization` 헤더를 포함할 수 없다. 로컬 서빙 방식에서는 `/files/**` 경로를 `permitAll()`로 열고 `WebMvcConfigurer` 정적 리소스 핸들러를 별도로 구성해야 하는 번거로움과 보안 구멍이 발생했다.
2. **단일 서버 디스크 용량 한계 및 인스턴스 무상태성(Stateless) 훼손**:
   - Docker 컨테이너 및 EC2 인스턴스 교체/재배포 시 로컬 볼륨 마운트가 누락되면 저장된 학생 과제물이 영구 유실될 위험이 있다.
3. **네트워크 대역폭 및 I/O 블로킹 병목**:
   - 고해상도(A4 300DPI, 5~15MB) 학습지 이미지를 Spring Boot 톰캣 스레드가 직접 읽어서 스트리밍할 경우, 교사들의 동시 조회 시 애플리케이션 스레드 풀이 고갈되고 네트워크 대역폭 비용이 가중된다.

이에 따라 백엔드 팀은 고가용성 오브젝트 스토리지인 **AWS S3 (`S3FileStorageService`)**로 스토리지 인프라를 전격 전환하였다.

---

#### 11.12.2 DIP(의존성 역전 원칙) 기반 스토리지 설계

비즈니스 도메인 계층(`WorksheetService`)이 AWS SDK나 파일 시스템의 저수준 구현에 직접 의존하지 않도록 순수 도메인 인터페이스 `FileStorageService`와 불변 DTO `StoredFile`을 중심으로 결합도를 격리하였다.

```mermaid
classDiagram
    class WorksheetService {
        -FileStorageService fileStorageService
        +uploadWorksheet()
        +replaceWorksheetFile()
    }

    class FileStorageService {
        <<Interface>>
        +upload(MultipartFile file, String subDir) StoredFile
        +delete(String fileUrl) void
    }

    class LocalStorageService {
        -Path rootLocation
        -String baseUrl
        +upload() StoredFile
        +delete() void
    }

    class S3FileStorageService {
        -S3Client s3Client
        -String bucketName
        -String region
        +upload() StoredFile
        +delete() void
    }

    class StoredFile {
        <<Record>>
        +String originalFilename
        +String storedFilename
        +String fileUrl
        +FileType fileType
        +Long fileSize
        +String fileHash
        +Integer width
        +Integer height
    }

    WorksheetService --> FileStorageService : 의존 (DIP)
    LocalStorageService ..|> FileStorageService : 구현 (로컬 Fallback)
    S3FileStorageService ..|> FileStorageService : 구현 (운영 메인)
    FileStorageService ..> StoredFile : 반환 계약
```

- **도메인 계약 불변성**:
  - `WorksheetService`는 주입된 Bean이 `S3FileStorageService`인지 `LocalStorageService`인지 알 필요가 없으며, 단 1줄의 서비스 비즈니스 로직 수정 없이 스토리지 백엔드를 교체할 수 있다 (개방-폐쇄 원칙 OCP 준수).
- **불변 메타데이터 레코드 (`StoredFile`)**:
  - 파일 업로드 시 파일 원본명, S3 고유 저장 키, 영구 접근 URL(`fileUrl`), SHA-256 해시값(`fileHash`), 이미지 가로/세로 픽셀 해상도(`width`, `height`)를 한 번에 캡슐화하여 반환한다.

---

#### 11.12.3 테넌트 격리 S3 객체 키 네이밍 및 업로드 메커니즘

1. **테넌트 격리 객체 키 경로**:
   - `worksheets/{academyId}/{UUID}.{ext}`
   - 학원(`academyId`)별 폴더 구조를 강제하여 S3 버킷 내부에서도 테넌트 간 파일 충돌을 방지하고 추후 학원 단위 데이터 아카이빙/용량 측정을 용이하게 한다.
2. **MIME 타입 보존 및 인라인 렌더링 (`Content-Type`)**:
   - S3에 파일 업로드 시 `PutObjectRequest`의 `contentType` 메타데이터(예: `image/jpeg`, `image/png`, `application/pdf`)를 정확히 지정한다.
   - MIME 타입이 누락되어 기본값(`binary/octet-stream`)으로 업로드되면, 브라우저가 이미지를 렌더링하지 않고 강제 다운로드 팝업을 띄우는 현상을 원천 방지한다.
3. **영구 퍼블릭 URL 산출**:
   - 표준 S3 가상 호스팅 URL 규격을 채택한다:
     `https://{bucketName}.s3.{region}.amazonaws.com/{s3Key}`
   - 추후 CloudFront CDN 도입 시 도메인 접두사(`https://cdn.autedi.com/{s3Key}`)만 손쉽게 교체 가능하도록 구조화한다.

---

#### 11.12.4 유연한 빈 등록 및 무중단 로컬 Fallback 메커니즘

새로운 팀원이 프로젝트를 클론하거나 CI 서버에서 테스트를 수행할 때, AWS 계정이나 S3 API 키가 없더라도 애플리케이션 기동 및 테스트가 실패하지 않도록 2단계 Fallback 빈 등록 전략을 적용한다.

```mermaid
flowchart TD
    Start[Spring Boot 애플리케이션 시작] --> CheckS3Config{cloud.aws.s3.bucket 설정 및\nAWS 자격증명 유효한가?}
    CheckS3Config -- Yes --> RegisterS3[S3FileStorageService Bean 등록\n@Primary / 활성화]
    CheckS3Config -- No --> FallbackLocal[LocalStorageService Fallback Bean 등록\n로컬 uploads/ 디렉터리 사용]
    RegisterS3 --> Complete[FileStorageService 주입 완료]
    FallbackLocal --> Complete
```

- **조건부 주입 구현 원칙**:
  - `@ConditionalOnProperty(name = "cloud.aws.s3.bucket")` 애노테이션 또는 `StorageConfig` 팩토리 클래스를 통해 S3 버킷 환경변수가 주입된 경우에만 `S3FileStorageService`를 우선 등록한다.
  - 버킷 설정이 없거나 비어있는 경우 자동으로 `LocalStorageService`가 활성화되어 로컬 개발 환경의 연속성을 보장한다.

| 환경 (Profile) | 활성 스토리지 구현체 | 자격증명 방식 | URL 형태 | 비고 |
| :--- | :--- | :--- | :--- | :--- |
| **Local** (`local`) | `S3FileStorageService`<br>(or `LocalStorageService`) | `.env`의 AWS Key<br>(미설정 시 Fallback) | S3 HTTPS URL<br>(Fallback 시 `http://localhost:8080/files/...`) | S3 키 주입 시 실제 버킷 테스트 가능,<br>미주입 시 로컬 디스크 무중단 개발 |
| **Dev** (`dev`) | `S3FileStorageService` | Railway Variables 주입 | `https://autedi-mvp-develop-bucket.s3...` | 프론트-백 간 실서버 연동 QC |
| **Prod** (`prod`) | `S3FileStorageService` | EC2 IAM Role<br>(Instance Profile) | `https://autedi-mvp-bucket.s3...` | 키 하드코딩 없는 안전한 IAM 권한 위임 |

---

#### 11.12.5 프론트엔드 호환성 (Zero-Frontend-Impact / 0 LOC)

AWS S3 전환은 프론트엔드 코드베이스에 단 1줄의 변경도 요구하지 않는다 (**0 Lines of Code Change**).

1. **완벽한 API 응답 스키마 보존**:
   - 학습지 상세 조회(`WorksheetDetailResponse`) 및 검토 집계(`ReviewAggregationResponse`)에서 반환되는 DTO 필드 구조(`previewImageUrl`, `fileUrl`)가 100% 동일하다:
     ```json
     {
       "pages": [
         {
           "pageId": 1,
           "pageNumber": 1,
           "previewImageUrl": "https://autedi-mvp-bucket.s3.ap-northeast-2.amazonaws.com/worksheets/50/a9b8c7...png"
         }
       ]
     }
     ```
2. **브라우저 `<img src="...">` 네이티브 렌더링**:
   - S3 퍼블릭 객체 URL을 제공하므로, 프론트엔드는 이전 로컬 스토리지처럼 `fetch()` + `blob` 생성 + Bearer 헤더 주입과 같은 비표준적 우회 코드를 작성할 필요가 없다.
   - `<img src={page.previewImageUrl} alt="과제물 미리보기" />` 한 줄로 즉각적인 브라우저 캐싱과 렌더링이 이루어진다.
3. **CORS 이슈 해결**:
   - 프론트엔드 호스트(Vercel, Localhost)에서 S3 이미지의 픽셀 좌표 분석이나 캔버스 오버레이(`toDataURL`, Fabric.js, Canvas 2D)를 안전하게 수행할 수 있도록 S3 버킷 레벨의 CORS 허용 정책을 사전에 완비한다.

---

#### 11.12.6 슬라이스 및 단위 테스트 전략 (Mockito 기반 외부 격리)

- **원칙**: 단위 및 슬라이스 테스트(`S3FileStorageServiceTest`, `WorksheetControllerTest`) 실행 시 **실제 AWS S3 네트워크 호출이나 라이브 자격증명을 일절 요구하지 않는다.**
- **구현 방식**:
  - AWS SDK의 `S3Client`를 Mockito의 `@Mock`으로 격리하여 `putObject`, `deleteObject` 메서드의 정상 호출 여부와 예외 처리(네트워크 단절, S3Exception 등)를 검증한다.
  - `./gradlew test`를 오프라인 상태에서도 100% 신속하고 안정적으로 통과시킨다.

```java
@ExtendWith(MockitoExtension.class)
class S3FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private S3FileStorageService s3FileStorageService;

    @BeforeEach
    void setUp() {
        s3FileStorageService = new S3FileStorageService(s3Client, "autedi-test-bucket", "ap-northeast-2");
    }

    @Test
    @DisplayName("파일 업로드 성공 시 S3 PutObject 호출 및 올바른 StoredFile 반환")
    void upload_success() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "homework.png", "image/png", "<dummy-png-bytes>".getBytes());

        // when
        StoredFile result = s3FileStorageService.upload(file, "worksheets/1");

        // then
        assertThat(result.fileUrl()).contains("https://autedi-test-bucket.s3.ap-northeast-2.amazonaws.com/worksheets/1/");
        assertThat(result.originalFilename()).isEqualTo("homework.png");
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
```

---

#### 11.12.7 S3 버킷 CORS 및 퍼블릭 읽기 권한 정책 런북

프론트엔드 웹 애플리케이션에서 캔버스 이미지 조작 및 CORS 차단을 방지하기 위해 S3 버킷 설정에 아래 JSON 정책을 적용한다:

1. **S3 버킷 CORS 설정 (Cross-Origin Resource Sharing)**:
   ```json
   [
     {
       "AllowedHeaders": ["*"],
       "AllowedMethods": ["GET", "HEAD"],
       "AllowedOrigins": [
         "http://localhost:3000",
         "https://*.vercel.app",
         "https://autedi.com",
         "https://*.autedi.com"
       ],
       "ExposeHeaders": ["ETag"],
       "MaxAgeSeconds": 3600
     }
   ]
   ```
2. **S3 버킷 읽기 정책 (Bucket Policy - GetObject Public Read)**:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Sid": "PublicReadGetObject",
         "Effect": "Allow",
         "Principal": "*",
         "Action": "s3:GetObject",
         "Resource": "arn:aws:s3:::autedi-mvp-bucket/*"
       }
     ]
   }
   ```

---

### 11.13 프론트엔드 연동 캔버스 협의 스펙 확장 아키텍처 (Canvas Collaboration & Multi-page Specifications)

#### 11.13.1 배경 및 목적
노션 연동 가이드 Section 3(프론트-백 협의 항목)에서 확인된 화면 UI 상의 미반영 요구사항 5가지를 단일 마일스톤으로 수용하여 프론트엔드 팀의 캔버스 조작(그리기, 채택, 바운딩 박스 리사이징, 다중 페이지 검토)을 완벽히 지원한다.

#### 11.13.2 5대 핵심 도메인 아키텍처 확장
1. **피드백 채택 상태 머신 (`Correction.isApplied`)**:
   - AI 첨삭 항목의 채택/반영 여부를 즉시 토글하는 경량 엔드포인트(`PATCH /corrections/{id}/apply?applied=true/false`)를 신설하여 캔버스 상의 원클릭 인터랙션을 보장.
2. **교사 캔버스 그리기 도구 확장 (`HighlightType`)**:
   - `HIGHLIGHT`, `UNDERLINE` 외에 `ARROW`(화살표), `TEXTBOX`(텍스트상자), `CARET`(삽입기호 `^`) ENUM을 추가하여 캔버스 벡터 오브젝트를 수용.
3. **교사 표시 색상 스키마 (`CommentColor`)**:
   - `Comment.color` 컬럼(`RED`, `BLUE`, `YELLOW`, `GREEN`)을 추가하여 교사의 시각적 코멘트 강조를 다채화 (기본값: `BLUE`).
4. **AI 바운딩 박스 동적 좌표 갱신 (`CorrectionUpdateRequest.regions`)**:
   - AI가 인식한 캔버스 좌표가 미세하게 어긋났을 때 교사가 드래그/리사이징한 신규 `RegionPayload` 리스트를 부분 수정(`PATCH`) 바디로 받아 영속화.
5. **다중 페이지 업로드 파이프라인 (Short-term Multi-Page Pipeline)**:
   - `POST /api/v1/worksheets`에서 `files: List<MultipartFile>`을 지원하여 업로드된 파일 순서대로 1, 2, ... 페이지로 자동 분할 생성 (기존 단일 `file` 파라미터와 100% 하위 호환).

*상세 API I/O 명세 및 JSON 예시는 [`docs/FRONTEND_COLLABORATION_SPECS.md`](./FRONTEND_COLLABORATION_SPECS.md)를 참조한다.*


