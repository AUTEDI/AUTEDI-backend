# 로깅 컨벤션

> 상태: 초안 
> 관련 이슈 : #77
> 적용 대상: 백엔드 서버 전체. 특히 사진 업로드, 첨삭, QR 로그인 등의 주요 서비스 로직

## 1. 목적과 범위

목적: 장애 원인 파악과 구간별 성능 측정을 로그만으로 할 수 있게 하고, 핵심 로직을 요청과 작업 단위로 이어서 추적할 수 있게 한다.
정할 것들 : 로그 레벨 기준, 메시지 형식, 로그에 남기거나 남기지 않을 정보를 정하는 기준
정하지 않는 것들 : 구체적인 구현 코드, 로깅 관련 인프라 등은 이후 이슈에 결정할 예정이다.

---

## 2. 로그 레벨

| 레벨 | 기준 | 스택트레이스 |
|---|---|---|
| **ERROR** | 서버 쪽 문제로 개발자가 확인해야 하는 실패. 5xx 응답은 전부 ERROR | 포함 (원인 예외 `cause` 필수) |
| **WARN** | 요청은 거절·완화됐지만 나중에 추적할 가치가 있는 것. 정상 사용에서 보기 어려운 4xx(권한, 위변조, 남용 징후, 데이터 불일치), 사용자 입력이지만 거부 사유 추적이 필요한 것, 자동 복구된 이상 | 없음 (사유를 한 줄로) |
| **INFO** | 업무적으로 의미 있는 완료·상태 변화, 요청 완료 요약, 기동 시 설정 | 없음 |
| **DEBUG** | 개발·분석용 세부 정보. 운영에서는 끈다 | 없음 |

- 4xx(사용자 입력 오류, 요청 거절)와 서버가 스스로 복구한 이상은 WARN 또는 INFO로 남긴다 (2.1 표).
- 서버가 처리하지 못한 에러, 외부 시스템 에러, 그리고 **5xx 응답으로 끝나는 모든 경우**는 ERROR로 남긴다. 예외로 잡아 `CustomException`으로 바꿔 던졌더라도 응답이 5xx면 ERROR다 (예: `FILE_STORAGE_FAILED`).
- ERROR는 개발자가 꼭 확인해야 하는 이슈만 둔다.

### 2.1 4xx 오류의 레벨

| 레벨 | ErrorCode |
|---|---|
| INFO | `INVALID_INPUT_VALUE`(유효성), `EMPTY_FILE`, `UNSUPPORTED_FILE_TYPE`, `NO_SETTINGS_SELECTED`, `COMMENT_PAGE_MISMATCH`, `UNAUTHORIZED`, `EXPIRED_TOKEN`, `QR_SESSION_EXPIRED`, `QR_SESSION_INVALID_STATE`, `QR_ALREADY_UPLOADED`, `QR_SINGLE_FILE_ONLY`, `GENERATION_IN_PROGRESS`, `ALREADY_GENERATED`, `PRECONDITION_FAILED`, `ALREADY_REGISTERED_EMAIL`, `EXPIRED_INVITATION_TOKEN`, `ALREADY_USED_INVITATION` |
| WARN | `INVALID_IMAGE`, `FILE_TOO_LARGE`, `FILE_NOT_FOUND`, `IMAGE_NOT_PREPARED`, `ACCESS_DENIED`, `INVALID_TOKEN`, `INVALID_CREDENTIALS`, `USER_LOCKED`, `USER_INACTIVE`, `QR_USER_MISMATCH`, `QR_TARGET_MISMATCH`, `TOO_MANY_REQUESTS`, `ACADEMY_SUBSCRIPTION_INACTIVE`, `INVALID_INVITATION_TOKEN`, `INVITATION_EMAIL_MISMATCH`, `METHOD_NOT_ALLOWED`, `CANNOT_RETRY_GENERATION`, `CANNOT_MODIFY_DELETED_CORRECTION`, `INVALID_WORKSHEET_STATE`, `GENERATION_REQUEST_CONFLICT`, `*_NOT_FOUND`(작업, 페이지, 첨삭, 코멘트 등) |
| ERROR | `INTERNAL_SERVER_ERROR`, `FILE_STORAGE_FAILED`, `AI_CONTRACT_NOT_READY` |

모든 400번대 에러를 WARN으로 두는 것은 아니다. 사용자가 화면 안내만 보고 스스로 해결할 수 있고, 반복돼도 조사할 일이 없는 것들은 INFO에, 그렇지 않으면 WARN에 둔다. 새 `ErrorCode`를 추가할 때 이 표에도 함께 분류해주자.


- "S3 저장 실패 후 로컬 저장소 사용"는 WARN. 서버가 에러를 핸들링 한 경우지만 기록은 남겨야 할 것 같아 WARN으로 해뒀다.
- 5xx를 던지기 전에는 반드시 원인을 로그로 남기자. 예외를 다른 예외로 감싸 던질 때 원인 예외를 `cause`로 넘기거나 ERROR로 한 번 기록한다. 원인이 사라지는 변환을 하면 디버깅에 많은 비용이 소모된다..
- 기동 시 설정 로그(스토리지 종류, S3, 메일 방식 등)는 INFO로 두고 `[startup]` 태그를 붙인다. 어떤 설정으로 떴는지 확인하는 용도다.

---

## 3. 메시지 형식

```
[태그] 한국어 설명: key=value, key=value, ...
```

| 규칙 | 내용 |
|---|---|
| 설명 | 한국어로 짧게 |
| 키 | 영문 camelCase (`worksheetId`, `totalMs`) |
| 값 | `key=value`, 쉼표와 공백으로 구분. 공백이 들어갈 값은 쓰지 않는다 |
| 시간 | 정수 밀리초, 키 이름은 `~Ms` (`totalMs`, `s3Ms`) |
| 크기 | 키 이름에 단위를 붙인다 (`sizeKB`) |
| 태그 | 흐름 단위: `[upload]`, `[display]`, `[generation]`, `[qr]`, `[email]`, `[validation]`, `[startup]`, `[api]`(에러 응답), `[feedback]`(코멘트·첨삭 편집), `[sse]`, `[auth]` |

```
[upload] 완료: worksheetId=12, pageCount=1, ext=jpg, sizeKB=1252, totalMs=830, validateMs=620, s3Ms=130, dbMs=40
[email] 초대 발송 실패: invitationId=5, toDomain=naver.com, errorType=MailSendException, smtpCode=550
```

### 예외: `AI_API_CALL`
`TerraGenerationClient`는 LLM API 요청 1건마다 `AI_API_CALL` 로그를 남긴다. 요청당 토큰 수를 받아 가격을 비교하려고 만든 로그라서 필드가 많고, 집계하기 쉽도록 JSON을 사용한다.
프롬프트가 확정되고 비용 통계가 어느 정도 쌓이기 전까지는 형식과 필드명(snake_case)을 그대로 유지한다. 삭제하거나 바꾸는 것은 그 뒤에 판단한다.

`aiRequestId`는 `AI_API_CALL` JSON 로그의 `request_id`를 가리키며, AI 제공사 응답 헤더 `x-request-id`에서 가져온 값이다. 우리 서비스의 `requestId`(응답 헤더 `X-Request-Id`, 4.1절)와 이름이 겹치므로, 문서와 새 로그에서는 제공사 쪽 ID를 `aiRequestId`로 구분해 부른다.

---

## 4. 상관 ID

두 겹으로 흐름을 묶는다.

| 구분 | 역할 | 범위 |
|---|---|---|
| **`requestId`** | 한 HTTP 요청이 남긴 모든 로그를 묶는다 | 요청 하나 |
| **업무 키** | 요청이 나뉘는 지점(비동기, 큐, 다른 API 호출)을 이어 준다 | 작업 하나 |

### 4.1 `requestId`
- 요청 진입 시 필터가 생성한다. 인증 필터(`JwtAuthenticationFilter`)보다 앞에 두어 인증 단계 로그에도 붙도록 한다.
- 응답 헤더 `X-Request-Id`로 돌려준다. 사용자가 문제를 신고하거나 프런트 팀원이 문제를 발견했을 때 응답 헤더의 이 값을 알려 주면 해당 요청의 로그로 바로 들어가 문제를 살펴볼 수 있다.
- 클라이언트가 `X-Request-Id`를 보내더라도 그 값은 사용하지 않고(요청을 거절하지는 않는다) 항상 서버가 새로 생성한다. 외부에서 받은 값을 그대로 쓰면 줄바꿈 등을 섞은 로그 오염이나 ID 위조 가능성 때문에 검증 로직이 필요해지는데, 지금은 외부 시스템과 ID를 이어야 할 이유가 없다.
- 로그 패턴에 `%X{requestId}`로 자동 출력한다. 메시지에 직접 쓰지 않는다.
- 스레드를 계속 재사용해야하므로 요청이 끝나면 반드시 MDC를 비운다.

### 4.2 MDC에 넣는 값

| 키 | 시점 | 비고 |
|---|---|---|
| `requestId` | 요청 진입 | 항상 |
| `userId` | 인증 후 | 숫자 ID만 |
| `academyId` | 인증 후 | 숫자 ID만 |

`requestId`는 로그 설정이 모든 줄 앞에 자동으로 붙여 준다.
`worksheetId`, `generationId` 같은 업무 키는 자동으로 붙지 않는다.
요청을 처리하는 도중에야 알 수 있기 때문에, 로그를 남기는 사람이 메시지 안에 직접 적는다.

```
2026-11-19 12:00:01  [a1b2]  INFO  [upload] 완료: worksheetId=12, pageCount=1, totalMs=830
                      ↑                                 ↑
              자동으로 붙는 값                  개발자가 직접 적은 값
```

```java
log.info("[generation] 요청 접수: generationId={}, worksheetId={}", generationId, worksheetId);
```

### 4.3 업무 키

| 키 | 이어 주는 구간 |
|---|---|
| `worksheetId` | 업로드 → SSE 이벤트 → 상세 조회 → 이미지 조회 |
| `generationId` | 첨삭 요청 → 큐 → 워커 → AI 호출 → 결과 SSE |
| `qrSessionId` | QR 생성 → 스캔 → 승인 → 업로드. 로그에는 앞 8자만 남긴다 |
| `invitationId` | 초대 생성 → 이메일 발송 |

---

## 5. 비동기 구간

MDC는 (Logback 기본 구현 기준) 스레드의 ThreadLocal에 저장되므로, 작업이 비동기 처리되어 다른 스레드로 넘어가면 `requestId`가 자동으로 따라가지 않는다.
- 2초마다 DB를 훑어 대기 중인 첨삭 작업을 집어 가는 스케줄러(`dispatchPending`)
- 서버 재시작 후 복구
- 워커 대기열이 가득 차서 맡기지 못해 DB에 남겨졌다가 나중에 스케줄러가 집어 가는 경우
에서 requestId 기반 로그 흐름 추적이 끊길 수 있다.

우리 서비스에서는 크게 `sseExecutor`, `mailExecutor`, `generationExecutor`에서 발생한다.
TaskDecorator를 구현하여 작업을 맡기는 순간에 MDC를 복사해 두었다가, 워커 스레드가 일을 시작할 때 그 값을 복원하고, 끝나면 비우는 식으로 해결할 예정이다.
요청 없이 시작되는 작업은 `requestId`가 없으므로 이런 경로의 로그는 `generationId` 같은 업무 키를 메시지에 적어 연결한다.

---

## 6. 남기지 않을 정보

로그에 개인을 알아볼 수 있는 값은 남기지 않는다. `requestId`, `worksheetId`, `generationId` 같은 번호로 흐름을 묶고 요청과 작업 단위로 추적한다.

| 구분 | 항목 | 대신 남길 것 |
|---|---|---|
| **절대 금지** | 비밀번호, 토큰(JWT, 초대, QR), 초대·로그인 링크 전체, API 키·시크릿 | 없음 |
| **절대 금지** | 요청·응답 본문, 이미지와 AI 프롬프트·답변 내용 | 크기, 개수, 상태 |
| **남기지 않음** | 이메일 주소 | `userId` 또는 `invitationId`, 필요하면 `toDomain` |
| **남기지 않음** | 이름, 전화번호 | 없음 |
| **남기지 않음** | 원본 파일명 | `ext`, `sizeKB` |
| **남기지 않음** | 예외 메시지 원문 (입력 값이 포함될 수 있음) | `errorType`, 에러 코드, 필드 이름 |
| **축약** | `qrSessionId` | 앞 8자 |
| **허용** | 숫자 ID, 에러 코드, 파일 크기·형식·해상도, 개수, 상태, 소요 시간 | |

- 이메일 발송 실패는 주소가 아니라 에러 종류와 코드(`errorType`, `smtpCode`)가 원인이다. 수신자는 `invitationId`로 DB에서 찾는다. 메일 도메인(`toDomain`)은 "특정 메일 서비스에서만 실패" 같은 패턴을 보기 위해 남길 수 있다.
- SMTP 에러 메시지에 수신자 주소가 포함될 수 있으므로 메시지를 그대로 남기지 않는다.
- Validation 오류는 거부된 입력 값이 아니라 **필드 이름과 오류 코드**만 남긴다.

---

## 7. 로그 양

| 기준 | 내용 |
|---|---|
| 정상 요청의 INFO | 요청당 **완료 요약 1줄**. 업무 키, 총 시간, 큰 단계의 시간을 한 줄에 담는다 |
| 조회(GET) 성공 | INFO를 남기지 않는다 (요청 기록은 Nginx 접근 로그가 있음) |
| 스택트레이스 | **ERROR에만**. WARN과 INFO는 사유 한 줄 |
| 반복되는 정상적 이상 | SSE 연결 끊김, 하트비트 종료 등은 DEBUG |
| 환경별 기본 레벨 | 운영 INFO, 개발 INFO. DEBUG는 필요할 때 일시적으로만 켠다. `show-sql`은 운영에서 끈다 |
| 로그 보존 | 로그는 호스트 볼륨의 파일로도 보관한다. 파일은 날짜·크기 기준으로 나누고, 보관 일수가 지난 파일은 삭제하는 로테이션을 적용한다. 컨테이너를 새로 만들어도(배포) 로그가 남는다. 보관 일수와 구체 설정은 후속 인프라 작업에서 정한다 |

---

## 8. 구간 시간 기록

- 정상 요청의 완료 요약 1줄에 **업무 키 + `totalMs` + 큰 단계의 시간**(예: `validateMs`, `convertMs`, `s3Ms`, `dbMs`)을 담는다.
- 층별 세부 시간(디코딩, 방향 보정, PNG 인코딩 등)은 **DEBUG**로 각 층이 자기 자리에서 남긴다.
- 큰 단계의 시간을 요약 줄에 모으는 방식(측정 수집기 객체)과 지표(Micrometer 등)로 따로 기록하는 방식 중 무엇을 쓸지는 **모니터링 도구가 정해진 뒤** 후속 이슈에서 결정한다. 어느 쪽이든 요약 줄에는 `totalMs`가 들어간다.
- 평소에는 지표로 추세를 보고, 느린 요청이 보이면 요약 줄의 `totalMs`로 해당 시간대를 검색해 `requestId`를 찾아 그 요청의 로그를 모아 본다.

### 8.1 검토한 대안

한 요청이 여러 층(Controller → Service → S3 저장소 → 이미지 변환)을 지나는데, 시간은 각 층이 자기 구간만 안다. 이 시간을 로그로 어떻게 남길지 세 가지를 비교했다.

| 대안 | 내용 | 장점 | 단점 |
|---|---|---|---|
| **A. 1줄에 모두 담기** | 요청마다 수집기 객체에 각 층이 `record("s3", ms)`로 기록하고, 가장 바깥에서 한 줄로 출력 | 한 줄만 검색해도 업무 키와 단계별 시간이 다 나옴. 요청 1건 = 레코드 1개라 집계가 쉬움. 로그 양이 적음 | 수집기를 만들고 호출 경로(업로드, 파일 교체 등)마다 연결해야 함. 실패해도 `finally`에서 부분 시간을 출력해야 함 |
| **B. 층별로 각자 한 줄씩** | 각 클래스가 자기 시간을 그 자리에서 로그로 남김 | 구현이 가장 쉬움. 어떤 호출자가 써도 자동으로 남음 | 요청당 줄이 여러 개. `requestId`로 이어서 읽어야 하고 집계가 번거로움. 층마다 형식이 어긋나기 쉬움 |
| **C. 지표(Micrometer 등)로 기록** | 각 층이 자기 시간을 지표(통계)로 기록. 로그에는 총 시간만 | 시간을 위로 전달할 필요가 없음. 평균, 상위 95% 같은 통계가 도구에서 바로 나옴 | 개별 요청은 추적 못 함(통계라서). 모니터링 도구 도입이 전제 |

일단 A 방식으로 가되, 층별 세부 시간은 DEBUG로 남긴다. 

---

## 9. 핵심 흐름별로 남길 이벤트

| 흐름 | 이벤트 | 레벨 | 주요 키 |
|---|---|---|---|
| 업로드 | 완료 요약 | INFO | `worksheetId`, `pageCount`, `ext`, `sizeKB`, `totalMs`, 단계별 `~Ms` |
| 업로드 | 이미지 검사 거부 | WARN | `ext`, `sizeKB`, `reason` |
| 업로드 | S3 저장 실패, 로컬 폴백 | WARN | `fallback=local`, `errorType` |
| 업로드 | S3 저장 실패, 폴백 없음 | ERROR | `errorType`, cause |
| 표시 | 이미지 조회 실패 | WARN/ERROR | `worksheetId`, `pageNumber`, `reason` |
| 이벤트 | SSE 전송 실패, 직렬화 실패 | WARN | `event` |
| 첨삭 | 생성 요청 접수 | INFO | `generationId`, `worksheetId` |
| 첨삭 | 워커 완료·실패 | INFO / WARN | `generationId`, `errorCode`, `totalMs` |
| 첨삭 | AI 호출 기록 | INFO | `AI_API_CALL` JSON |
| QR | 생성, 스캔, 승인, 업로드 | INFO | `qrSessionId`(앞 8자), 상태 |
| QR | 상태 오류 | 2.1표에 따름 | `errorCode` |

---

## 10. 좋은 예 / 나쁜 예

| 나쁜 예 | 이유 | 좋은 예 |
|---|---|---|
| `log.info("[초대 링크 발송] To: {}, 링크: {}", email, link)` | 이메일과 가입 토큰이 남음 | `[email] 초대 발송: invitationId=5, toDomain=naver.com` |
| `log.warn("Validation failed: {}", e.getMessage())` | 거부된 입력 값(비밀번호 등)이 남을 수 있음 | `[validation] 거부: field=email, code=INVALID_INPUT_VALUE` |
| `log.warn("이미지 검사 실패: file={}", name, e)` | 파일명이 남고 WARN에 스택트레이스가 붙음 | `[upload] 이미지 검사 거부: ext=jpg, sizeKB=1252, reason=...` |
| `catch (RuntimeException e) { throw new CustomException(FILE_STORAGE_FAILED); }` | 원인이 사라짐 | 원인 예외를 `cause`로 넘기거나 ERROR로 한 번 기록 |
| 한 요청이 단계마다 INFO를 남김 | 로그가 늘고 한 요청을 읽기 어려움 | 완료 요약 1줄, 세부는 DEBUG |
