# 고정 응답 → DB → 검토 API 연결

> 이 문서는 동기 연결 단계 당시의 기록이다. 현재 생성 API는 접수 커밋 후 백그라운드에서 실행하며 202/PENDING을 반환한다. 현재 실행·복구 정책과 확인 방법은 [AI-백그라운드-생성작업.md](AI-백그라운드-생성작업.md)를 따른다.

## 실행 범위

`app.ai.fixed-response.enabled=true`일 때 기존 생성 API가 아래 흐름을 **동기 실행**한다.

1. 업로드된 단일 이미지의 EXIF 보정 PNG와 선택 영역·수준을 조회한다.
2. `ai/fixed/v1/transcription.txt`를 불변 전사문으로 저장한다.
3. `ai/fixed/v1/response.json`을 계약 v1로 파싱하고 기존 anchor·UTF-16·boxes·영역·개수 검증을 수행한다.
4. 정상 REPLACE/GUIDANCE만 `Correction`으로 저장한다. reviewIssues·제외 후보·원본 JSON·검증 결과는 생성 결과에 함께 보존한다.
5. 이전 작업의 활성 카드는 논리 삭제하고 학습지를 READY로 전환한다.
6. 기존 검토·첨삭 API에서 DB에 저장된 결과를 조회·편집·삭제·복원한다.

설정의 기본값은 false이며, 기존 Mock 코드와 실험 코드는 유지한다. 고정 전사문과 좌표는 업로드 이미지에서 인식한 결과가 아닌 테스트 예제다. 출처는 `source=FIXED`, 모델은 `fixed-response`로 명시해 실제 AI 결과와 구분한다. 기존 실제 모델 설정과 프롬프트 조립기는 변경하지 않는다. 실제 AI 호출·비동기 실행은 포함하지 않는다.

## 기존 API의 추가 응답

`GET /api/v1/worksheets/{id}/review`:

- `corrections`: 활성 카드. `action`, `feedbackType`, `issueKinds`, `anchor`, `locationUncertain`, `warnings`, `requiresReview`, `regionCoordinateUnit`을 추가한다.
- `reviewIssues`: 최신 생성의 확인 필요 후보. 최초 `original`, 후보 순번, 전사문 참조, 해석된 anchor, 유효 boxes, 검증 사유(`warnings`)를 제공한다. 정상 카드와 중복하지 않는다.
- `summary`: 영역별 수준·권장/최대 수·최초 정상 카드 수(`validatedCount`)·현재 활성 수(`displayedCount`)·제외 수(`omittedCount`). 교사가 삭제한 카드는 현재 활성 수에만 반영한다.
- `aiResults`: 최신 생성 결과 ID·전사문 ID·출처·모델/계약/프롬프트/검증 버전. 원본 응답 전문은 일반 조회에 노출하지 않고 저장소에 보존한다.

목록 `GET .../corrections`, 단건 `GET .../corrections/{correctionId}` 및 편집·복원 응답에도 같은 카드 필드를 제공한다. 기존 미분류 자료의 action 등은 null이며, reviewIssues·summary·aiResults가 없으면 빈 배열이다.

anchor의 start/end는 전사문 **한 줄 내 UTF-16 인덱스**, end는 exclusive다. 카드 regions와 후보 boxes는 보정 PNG에 대한 **0~1 정규화 좌표**다. 페이지 width/height만 보정 PNG의 px 크기다.

## 교사 편집과 불변 데이터

- PATCH는 현재 `content`만 변경한다. REPLACE는 공백을 그대로 보존하고 `""`를 삭제 제안으로 허용한다. GUIDANCE·기존 미분류 카드는 빈 내용과 공백만 있는 내용을 거부한다. null은 모든 유형에서 거부한다.
- DELETE는 논리 삭제, restore는 삭제 직전 편집 내용을 유지한 활성화다. AI 제안으로 되돌리는 동작이 아니다.
- `aiInitialContent`, 원문, 설명, action, anchor, 좌표, 경고와 생성 결과의 원본 JSON·검증 결과는 편집·삭제·복원 후에도 유지된다.
- 변경 후 실제 DB 버전의 ETag를 반환한다. 같은 내용 재편집처럼 변경이 없으면 ETag도 유지된다. 이전 ETag로 수정하면 412를 반환한다.
- 재시도 결과 조회에서는 최신 생성의 reviewIssues만 보여주고 이전 생성의 감사 자료와 논리 삭제 카드는 보존한다.

## 로컬 확인

공유·운영 DB를 띄우거나 접속하지 않고, 아래 테스트만으로 업로드부터 실제 HTTP 경계·H2 커밋·재조회까지 확인할 수 있다.

```powershell
$env:JAVA_HOME = 'C:\Users\82108\.jdks\temurin-21.0.11-1'
.\gradlew.bat --offline -I scripts/local-h2-tests.init.gradle test --tests '*FixedResponseReviewApiTest'
```

테스트는 고정 모드를 자체 활성화하고 AI 클라이언트를 mock으로 교체하며, 호출이 한 번도 없음을 검사한다. 임시 이미지 폴더와 전용 메모리 H2만 사용한다. 문법 Standard + 내용 Advanced에서 정상 카드 4개(REPLACE 3개, GUIDANCE 1개), reviewIssues 3개, 제외 1개를 확인한다. 문법만 선택하면 GUIDANCE가 제외되어 정상 카드 3개, reviewIssues 3개, 제외 2개다.

별도 로컬 실행 환경에서 수동 확인하려면 고정 모드 설정을 명시하고 로그인 후 다음 순서로 호출한다. 이 단계에서는 서버를 공유·운영 DB에 연결해 실행하지 않는다.

1. `POST /api/v1/worksheets`에 JPG/JPEG 또는 PNG 한 장을 업로드한다.
2. `PUT /api/v1/worksheets/{id}/settings`에 `{"areas":[{"area":"GRAMMAR_SPELLING","level":"STANDARD"},{"area":"CONTENT_SUMMARY","level":"ADVANCED"}]}`를 보낸다.
3. `POST /api/v1/worksheets/{id}/generations`를 호출한다. 기존 202 형식을 유지하지만 동기 완료하여 status가 SUCCEEDED다.
4. `GET /api/v1/worksheets/{id}/review`에서 위 카드/확인 필요/제외 수와 FIXED 출처, 경고를 확인한다.
5. 단건 조회의 ETag를 `If-Match`로 사용해 PATCH(`{"content":"교사 편집 내용"}`), DELETE, POST restore를 순서대로 호출한다. 매번 응답 ETag를 갱신한다. content가 유지되고 aiInitialContent·anchor·warnings가 바뀌지 않는지 확인한다.

## 검증 결과

- 신규 MVC·H2 통합 테스트 7건 통과: 저장·조회, REPLACE/GUIDANCE 편집·삭제·복원, 빈 교체문과 GUIDANCE 입력 제한, 멱등 요청과 학원 경계, 꺼진 영역 제외, 재시도 시 최신 결과 조회와 이전 감사 자료 보존.
- 전체 18개 테스트 클래스의 307건(기존 300 + 신규 7) 통과. 실패·오류·건너뜀 0건.
- 로컬 H2 강제 설정으로 `spotlessApply build spotlessCheck --continue` 성공. `git diff --check` 통과.
- 작업 시작 시 기록한 기존 파일 223개의 해시를 대조해 이번 연결에 필요한 8개 파일만 변경되었고 누락 파일이 없음을 확인했다. 기존 실험 코드와 이전 단계 구현은 보존했다.
- 공유·운영 DB 접속, 유료 실험 main, 실제 AI 호출, 커밋·push·PR·병합은 수행하지 않았다.

## 다음 단계에 남긴 범위

실제 전사·첨삭 API 호출, 비동기 작업·실패 처리 확장, 검토 화면 렌더링, reviewIssues의 교사 처리 기능, PDF 및 여러 페이지 처리는 연결하지 않았다. 원본 응답 전문을 내려주는 신규 API도 추가하지 않았다.
