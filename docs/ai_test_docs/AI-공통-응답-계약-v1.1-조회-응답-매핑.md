# v1.1 조회 응답 매핑 — 12단계

2026-11-13 기준. [프론트 호환 규칙](AI-v1.1-버전선택-프론트호환규칙.md)에 따라 저장된 v1/v1.1 결과를 기존 API 응답으로 제공한다. AI 응답 계약 버전과 REST API 주소 버전은 별개이며 `/api/v1`을 유지한다.

## 1. 연결한 경로

| 경로 | 제공 내용 |
| --- | --- |
| `GET /api/v1/worksheets/{worksheetId}/review` | 기존 7개 루트 필드 유지, 버전별 정상 카드·확인 목록·집계·AI 메타데이터 |
| `GET /api/v1/worksheets/{worksheetId}/corrections` | 기존 필터·items/total 유지, 추가 정보를 포함한 카드 목록 |
| `GET /api/v1/worksheets/{worksheetId}/corrections/{correctionId}` | 같은 매핑을 사용한 카드 상세 |
| 기존 첨삭 수정·채택·복원 응답 | 동일한 CorrectionResponse 사용. 기존 요청 형식 유지 |

검토 화면은 기존처럼 최신 생성 작업의 확인 목록·집계·AI 메타데이터와 현재 ACTIVE 카드들을 반환한다. 이전 작업 카드가 남아 있으면 그 카드 자신의 아카이브에서 출처와 계약 버전을 읽는다. 카드별 추가 쿼리 대신 관련 작업의 아카이브를 묶어서 조회한다.

## 2. 일반 카드

`originalText`, `aiInitialContent`, `content`, `explanation`, `regions`, `anchor`, 상태·ETag 등 기존 필드를 유지하고 아래 필드를 추가했다.

| 추가 필드 | v1 | v1.1 |
| --- | --- | --- |
| contractVersion | 저장된 계약 ID. 아카이브 없는 과거 자료는 null | 저장된 계약 ID |
| anchors | 실제 단일 anchor를 배열로 감쌈. 없으면 [] | 모든 검증 원문 연결, 각 원소는 transcriptionId/lineId/occurrence/start/end |
| revisionScope | null | 저장된 최초 범위 |
| aiInitialEdit | null | 최초 AI 수정 동작. GUIDANCE는 null |
| edit | null | 현재 내용에 유효한 수정 동작. 내용 변경으로 무효화되면 null |
| targetRegions | regions와 같은 현재 좌표 | regions와 같은 현재 좌표 |

다중 줄의 기존 `anchor`는 null이며 `anchors`에 전체 범위를 제공한다. `originalText`도 모든 원문을 LF로 연결한 값이다. `regions`/`targetRegions`는 현재 원문 위치이며 기존 pageId/x/y/width/height/polygon 구조와 NORMALIZED_0_1 단위를 유지한다. AI target_boxes의 유효한 LTRB 좌표를 저장 단계에서 XYWH로 바꾼 값을 읽는다.

`warnings`의 JSON 구조는 기존 code/field/message 그대로다. v1의 값은 유지하고 v1.1은 v11_details에 보존된 원래 코드를 제공한다. 예를 들어 INVALID_TARGET_BOX를 INVALID_BOX로 축소해 내보내지 않는다.

삽입 카드의 관련 필드 예시:

```json
{
  "contractVersion": "AUTEDI-AI-CONTRACT-v1.1",
  "action": "REPLACE",
  "originalText": "dog",
  "aiInitialContent": "a dog",
  "content": "a dog",
  "anchor": {"transcriptionId": 51, "lineId": 1, "occurrence": 1, "start": 7, "end": 10},
  "anchors": [{"transcriptionId": 51, "lineId": 1, "occurrence": 1, "start": 7, "end": 10}],
  "revisionScope": "WORD",
  "aiInitialEdit": {"operation": "INSERT", "text": "a", "position": "BEFORE"},
  "edit": {"operation": "INSERT", "text": "a", "position": "BEFORE"}
}
```

기존 화면은 content/regions로 표시할 수 있다. 새 화면은 edit로 삽입·삭제 등의 표시를 선택할 수 있지만, 실제 프론트 렌더러 변경·브라우저 검증은 이번 백엔드 구현에 포함하지 않았다.

## 3. 확인 목록과 집계

- `reviewIssues[].original`은 기존 v1 키의 호환 투영이다. `boxes`에 원래 target_boxes를 대응시키고 다중 줄 original.anchor는 null로 둔다. 저장용 원본으로 다시 사용하지 않는다.
- `originalV11`에는 전체 최초 v1.1 후보를 제공한다. v1은 null이다.
- `anchors`는 모델의 원문 연결 후보 배열 `{line_id, original, occurrence}`, `resolvedAnchors`는 검증된 범위 배열 `{lineId, start, end}`다. 검증에 실패한 연결의 범위를 만들어 넣지 않는다. 기존 단일 resolvedAnchor는 다중 줄에서 null이다.
- `validBoxes`는 기존 LTRB 배열이며 검증된 좌표만 제공한다. 경고는 원래 버전의 코드·필드·메시지 전체를 유지한다.
- `summary.recommendedCount`는 v1 값을 유지하고 v1.1은 0이다. 0은 권장 개수 개념이 없다는 호환 표현이며 목표가 0개라는 뜻이 아니다.
- `maximumCount`는 v1.1 활성 영역 7, 비활성 영역 0이다. `validatedCount`는 최초 검증·개수 제한 직후 카드 수, `displayedCount`는 해당 작업·전사·영역의 현재 ACTIVE 카드 수다. 여러 anchors가 있는 카드도 한 번만 센다. 삭제·복원은 displayedCount만 바꾸고 최초 집계·omittedCount는 유지한다.
- `aiResults.metadata`는 실제 보존된 버전이다. UNREADABLE 아카이브도 메타데이터만 제공하며 원본 응답 전문은 조회 API에 노출하지 않는다.

## 4. 기존 편집과 남은 작업

기존 content/regions/isApplied 요청은 계속 사용한다. 실제 content 변경 시 현재 edit는 null이 되고 aiInitialEdit는 보존된다. 같은 내용 재전송·좌표 이동·채택 변경만으로 edit를 무효화하지 않는다. regions와 targetRegions는 하나의 현재 좌표를 공유한다. 삭제·복원 뒤에도 최초 동작과 원문 연결은 유지한다.

새 edit/anchors를 편집 입력으로 받는 확장은 13단계다. 14단계의 서비스 AI 클라이언트·Worker 연결도 남아 있으며, 기본 v1 경로와 v1.1 서비스 생성 차단을 유지한다. 이 조회 구현만으로 실제 v1.1 생성 요청이 실행되지는 않는다.

이번 단계는 DTO·조회 매핑 변경이며 DB 컬럼·SQL 변경은 없다. 앞선 10~11단계의 스키마 선행 조건은 [저장·복원 문서](AI-공통-응답-계약-v1.1-저장-복원.md)를 따른다. 운영 DB 조회·변경과 실제 AI 호출은 수행하지 않았다.

## 5. 주요 코드와 검증

- [CorrectionService](../../src/main/java/com/ceos24/autedi/domain/correction/service/CorrectionService.java): 조회·편집 응답에 실제 작업별 출처·계약 버전 연결
- [CorrectionResponse](../../src/main/java/com/ceos24/autedi/domain/correction/dto/response/CorrectionResponse.java): 기존 카드 필드와 새 anchors/범위/동작/좌표 별칭 매핑
- [CorrectionWarningResponse](../../src/main/java/com/ceos24/autedi/domain/correction/dto/response/CorrectionWarningResponse.java): 버전별 경고를 같은 JSON 형태로 제공
- [ReviewIssueResponse](../../src/main/java/com/ceos24/autedi/domain/worksheet/dto/response/ReviewIssueResponse.java): 기존 후보 투영과 전체 v1.1 원본·연결 배열
- [ReviewResultMapper](../../src/main/java/com/ceos24/autedi/domain/correction/service/ReviewResultMapper.java): 버전별 검증 결과 분기, 확인 목록·집계 계산
- [FixedResponseReviewApiTest](../../src/test/java/com/ceos24/autedi/domain/FixedResponseReviewApiTest.java): 허용되는 v1.1 고정 예제 26개를 H2에 저장한 뒤 MVC 검토·목록·상세 응답 확인. 다중 줄 편집·좌표 이동·삭제·복원, v1/v1.1 공존, 아카이브 없는 과거 자료와 UNREADABLE도 검증

테스트는 메모리 H2와 로컬 가짜 응답을 사용한다. 프론트에서 null anchor·여러 regions·recommendedCount=0을 실제로 표시하는지는 프론트 연결 시 별도로 확인해야 한다.

검증 명령(Java 21):

```powershell
.\gradlew.bat --offline spotlessJavaApply test --tests '*FixedResponseReviewApiTest' --tests '*GenerationResultStorageTest' --tests '*GenerationContractRoutingTest' spotlessJavaCheck
```

결과: API 67개·저장 복원 43개·버전 라우팅 19개, 총 129개 통과(실패·오류·건너뜀 0). 포맷 검사도 통과했다.
