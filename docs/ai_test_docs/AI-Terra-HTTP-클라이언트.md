# Terra 운영용 HTTP 클라이언트 연결

## 연결 범위

검증한 A 방식의 `gpt-6.1-terra` 두 요청을 기존 백그라운드 워커에 연결했다. 실험 main·기존 실험 응답 형식은 수정하지 않는다.

1. 저장된 EXIF 보정 PNG를 한 번 조회한다.
2. Responses API에 PNG를 보내 원문 전사 `{"text":"..."}`를 받는다. 기존 실험처럼 철자·문법을 고치지 않고 반복·줄바꿈을 보존한다. CRLF/CR만 LF로 통일한다.
3. 모델명·PNG 해시·크기와 이번 전사를 불변 전사로 먼저 커밋한다.
4. **같은 PNG**와 이 전사의 줄 번호·텍스트를 첨삭 요청에 보낸다. `AiCorrectionPromptComposer`의 공통 원칙·선택 영역·독립적인 Standard/Advanced 규칙·anchor/boxes·계약 v1을 사용한다. 꺼진 영역의 상세 규칙을 추가하지 않는다.
5. completed 응답에서 계약 JSON을 꺼내 기존 파서·검증기를 통과한 결과만 저장한다. REPLACE/GUIDANCE, reviewIssues, 경고는 기존 검토 API로 조회한다.

외부 대기에는 DB 트랜잭션이 없다. 전사 후 첨삭이 실패해도 전사는 보존한다. 수동 재시도는 파일·설정·모델·PNG·전사 무결성이 맞으면 OCR 없이 첨삭만 실행한다. 재시도는 접수 당시 실행 경로를 유지한다.

## 설정 선택

`app.ai.client`는 `LEGACY_MOCK`, `FIXED`, `TERRA` 중 하나다. 명시하면 예전 `app.ai.fixed-response.enabled`보다 우선한다. 지정하지 않으면 이전 동작을 유지한다: fixed-response.enabled=true이면 FIXED, 그 외에는 LEGACY_MOCK이다. 기본 설정 변경이나 실제 모드 활성화는 이번 작업에서 수행하지 않았다.

실제 모드를 선택할 때 사용하는 속성 예:

```properties
app.ai.client=TERRA
app.ai.terra.endpoint=https://api.openai.com/v1/responses
app.ai.terra.connect-timeout=20s
app.ai.terra.request-timeout=3m
app.ai.terra.max-output-tokens=12000
```

키는 배포 환경의 `OPENAI_API_KEY` 또는 `APP_AI_TERRA_API_KEY`로 주입한다. 후자는 `app.ai.terra.api-key` 속성에 대응하며 우선한다. 클라이언트는 `.env`를 직접 읽지 않고 키를 출력하지 않는다. 모드 선택만으로 HTTP를 호출하지 않으며, 실제 요청은 커밋된 TERRA 작업을 워커가 실행할 때 전송한다.

요청은 store=false, stream=false, background=false이며 별도의 SDK나 재시도 라이브러리를 추가하지 않았다. 요청당 한 번의 HTTP 교환만 수행한다. HTTP 리다이렉트를 따라가지 않는다. 연결 타임아웃 외에 **응답 본문 완료까지의 전체 대기 시간**을 제한하고, 타임아웃·스레드 중단 시 대기 중인 HTTP 요청을 취소한다. 타임아웃은 외부 처리 여부를 확정하지 못하므로 자동 재호출하지 않는다.

## 계약과 오류 처리

전사와 첨삭 모두 `text.format`의 strict JSON Schema를 사용한다. 첨삭의 전송 스키마는 기존 v1에서 `$schema`, `$id` 메타 필드와 `uniqueItems`를 제외한 사본이다. 원본 스키마를 수정하지 않았고, issue_kinds 중복 여부 등 전체 규칙은 기존 서버 파서·검증기가 계속 검사한다.

| 상황 | 코드 | 수동 재시도 가능 |
|---|---|---|
| 키·HTTP 설정 누락 | TERRA_NOT_CONFIGURED | 아니오 |
| 401/403 | TERRA_AUTHENTICATION | 아니오 |
| 429 insufficient_quota | TERRA_QUOTA | 아니오 |
| 일반 429 | TERRA_RATE_LIMIT | 예 |
| 408/5xx | TERRA_HTTP_ERROR | 예 |
| 기타 HTTP 오류·리다이렉트 | TERRA_HTTP_ERROR | 아니오 |
| 연결 오류·타임아웃 | TERRA_CONNECTION_FAILED / TERRA_TIMEOUT | 예 |
| incomplete / 완료되지 않은 message | TERRA_INCOMPLETE | 예 |
| queued/in_progress 등 완료되지 않은 envelope | TERRA_NOT_COMPLETED | 예 |
| content_filter / refusal | TERRA_CONTENT_FILTER / TERRA_REFUSAL | 아니오 |
| 빈 글·공백만 있는 전사 | TERRA_NO_TEXT | 아니오 |
| 잘못된 envelope·전사 JSON / 출력 없음 | TERRA_INVALID_RESPONSE / TERRA_EMPTY_OUTPUT | 예 |

미완료 응답은 내부 JSON이 완전해 보여도 성공으로 사용하지 않는다. 인증·오류 본문, refusal 텍스트, 키, 임의 예외 메시지는 로그·API 오류 응답에 복사하지 않는다. 기존 작업의 error.code와 retryable에 분류 결과를 저장한다. 자동 유료 재시도는 없다. 재시도 불가 오류를 해결한 뒤에는 새 생성 요청이 필요하다.

completed 첨삭 출력의 계약 JSON이 잘못된 경우 기존 UNREADABLE 아카이브로 보존한다. HTTP 오류·미완료 envelope는 카드/정상 결과로 저장하지 않고 실패 상태를 남긴다. `raw_response`는 모델의 내부 계약 JSON이며, HTTP envelope 전문·사용량·request-id 별도 저장은 이번에 추가하지 않았다.

## 외부 호출 없는 검증

```powershell
$env:JAVA_HOME = 'C:\Users\82108\.jdks\temurin-21.0.11-1'
.\gradlew.bat --offline -I scripts/local-h2-tests.init.gradle test --tests '*TerraGenerationClientTest' --tests '*FixedResponseReviewApiTest'
```

HTTP 테스트는 loopback 가짜 서버와 가짜 키만 사용한다. 운영 기본 endpoint를 테스트에서 사용하지 않는다. 가짜 응답으로 요청 본문·동일 PNG·이번 전사문·선택 규칙·JSON Schema·인증 오류·사용량 제한·타임아웃·미완료·거부·잘못된 출력·리다이렉트·요청 횟수를 검사한다. 헤더 이전 지연과 헤더 이후 본문 정지도 각각 타임아웃으로 확인한다.

MVC·H2 테스트는 TERRA 설정 선택부터 실제 HTTP 어댑터, 전사 커밋, 계약 검증, 카드/검토 조회를 연결한다. 인증 실패와 미완료 응답에서 전사만 보존되는지, 일시적 첨삭 실패 후 수동 재시도가 첨삭 요청 하나만 추가하는지 확인한다. 기존 권한·동시 요청·중복 방지·늦은 완료·편집 보존 테스트도 유지한다.

## 검증 결과

- HTTP 클라이언트 테스트 18건, 기존 기능을 포함한 MVC·H2 연결 테스트 29건 통과.
- 빈 전사를 성공으로 기대한 새 테스트 1건은 기존 검증 정책과 맞지 않아 TERRA_NO_TEXT 분류·기대값을 수정했다. 이후 HTTP 관련 범위만 재실행했고, 통과한 MVC·DB 사례는 전체 검사 전 반복하지 않았다.
- 연결 완료 후 전체 검사를 한 번 실행했다. 19개 클래스, 347건 모두 통과(실패·오류·건너뜀 0). `build`, `spotlessCheck`, `git diff --check`도 통과했다.
- 실제 외부 API·유료 실험 main은 실행하지 않았다. 전송은 가짜 키와 loopback 서버로 한정했고 H2·임시 이미지 파일만 사용했다.
- 기존 파일 226개를 작업 시작 시 해시와 대조했다. 이번 연결에 필요한 파일만 수정했고 누락 파일이 없으며 실험 코드를 보존했다. 커밋·push·PR·병합도 수행하지 않았다.

## 근거와 남긴 범위

요청 형식과 모델은 [Terra 공식 문서](https://developers.openai.com/api/docs/models/gpt-6.1-terra), [Responses 이미지 입력](https://developers.openai.com/api/docs/guides/images-vision), [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)와 대조했다.

실제 외부 API 호출·키 유효성·계정 권한·실제 모델 품질·요금은 검증하지 않았다. 공유·운영 DB 적용, 실제 모드 활성화, PDF·다중 서버 lease, HTTP envelope 전문·사용량 저장도 포함하지 않았다. 다음 단계 구현은 시작하지 않는다.
