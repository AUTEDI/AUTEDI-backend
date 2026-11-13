# AI 계약 버전 선택과 프론트 호환 규칙

- 확정일: 2026-11-13, 한국 시간
- 범위: 남은 작업의 **10. 버전 선택 경로 추가 + 프론트 호환 규칙 확정**
- 원칙: AI↔백엔드 계약은 v1/v1.1로 구분하고, 백엔드↔프론트의 기존 `/api/v1` 응답은 유지하며 필요한 필드만 추가한다.
- 구분: 아래 버전 선택·작업 스냅샷·독립 라우팅은 구현했다. 프론트 매핑과 v1.1 결과 저장·조회·편집·생성 연결은 11~14단계의 확정 규칙이며 이번에 구현한 것으로 해석하지 않는다.

## 1. 구현한 버전 선택 경로

| 파일 | 역할 |
| --- | --- |
| [GenerationContractVersion](../../src/main/java/com/ceos24/autedi/domain/generation/contract/GenerationContractVersion.java) | 설정 v1/v1.1과 AI 계약 식별자를 연결 |
| [GenerationContractProperties](../../src/main/java/com/ceos24/autedi/domain/generation/contract/GenerationContractProperties.java) | 서버 설정 바인딩·기본값·후보 조합 검사 |
| [GenerationAiProfile](../../src/main/java/com/ceos24/autedi/domain/generation/contract/GenerationAiProfile.java) | 계약 버전·후보·모델·프롬프트·검증 버전의 불변 스냅샷 |
| [GenerationClientProfiles](../../src/main/java/com/ceos24/autedi/domain/generation/client/GenerationClientProfiles.java) | 공급자 모드와 계약 버전으로 지원하는 프로필 생성·대조 |
| [GenerationContractSelector](../../src/main/java/com/ceos24/autedi/domain/generation/contract/GenerationContractSelector.java) | 신규 선택·재시도 상속·기존 작업 해석·서비스 연결 상태 확인 |
| [GenerationContractRouter](../../src/main/java/com/ceos24/autedi/domain/generation/contract/GenerationContractRouter.java) | 버전별 프롬프트·Schema·파서·전체 검증기 실행. HTTP·DB·프론트 변환은 하지 않음 |
| [WorksheetGenerationService](../../src/main/java/com/ceos24/autedi/domain/generation/service/WorksheetGenerationService.java) | 접수 시 프로필을 저장하고 재시도에서는 최초 프로필 상속 |
| [GenerationWorkStore](../../src/main/java/com/ceos24/autedi/domain/generation/service/GenerationWorkStore.java) | 저장된 프로필로 claim·모델·결과 메타데이터 처리 |

`app.ai.client=TERRA/FIXED/LEGACY_MOCK`는 공급자/실행 모드다. 새 계약 설정과 별개이며 프론트 요청에 버전 선택 필드를 추가하지 않는다.

```yaml
app:
  ai:
    correction:
      contract-version: ${AI_CORRECTION_CONTRACT_VERSION:v1}
      prompt-variant: ${AI_CORRECTION_PROMPT_VARIANT:DEFAULT}
```

| 선택 | 독립 라우터 | 서비스 생성 접수 |
| --- | --- | --- |
| v1 / DEFAULT | v1 프롬프트·Schema·파서·검증기 | 기존 경로 실행 |
| v1.1 / DEFAULT | v1.1 기본본·Schema·파서·검증기 | 11~14단계 연결 전이므로 503 / GENERATION_008 |
| v1.1 / COMPACT | v1.1 압축본과 같은 v1.1 Schema·파서·검증기 | 같은 이유로 503 / GENERATION_008 |
| v1 / COMPACT, 알 수 없는 버전·후보 | 설정 오류 | 다른 버전으로 대체하지 않음 |

v1.1은 현재 TERRA의 독립 프로필만 준비했다. FIXED/LEGACY_MOCK의 v1.1 생성은 후속 연결 대상이다. **이 단계에서 환경변수만 v1.1로 바꿔도 유료 생성이 시작되는 것은 아니다.** 아직 결과를 저장·복원할 수 없는 요청은 작업·전사를 만들거나 AI를 호출하기 전에 거절한다. 이는 anchor 품질 개선의 선행 조건이 아니라 11~14단계가 아직 구현되지 않았다는 실행 경계다.

현재 Worker/서비스 HTTP 클라이언트의 v1 실행은 유지한다. 독립 라우터의 검증 결과는 `V1Validation`/`V11Validation`으로 구분하며 v1.1 데이터를 v1 DTO로 강제 캐스팅하지 않는다. 전사 내용·선택 영역은 같은 입력을 사용하고 v1.1 실행 시 설정 enum만 이름으로 변환한다.

## 2. 작업 스냅샷과 재시도

`worksheet_generations.request_ai_profile`에 다음 JSON을 불변 저장한다. 기존 `request_settings_snapshot`과 프론트의 `settingsSnapshot` 배열 형식은 바꾸지 않는다.

```json
{
  "version": "V1",
  "promptVariant": "DEFAULT",
  "metadata": {
    "model": "gpt-6.1-terra",
    "contractVersion": "AUTEDI-AI-CONTRACT-v1",
    "promptVersion": "AUTEDI-CORRECTION-PROMPT-v1",
    "validationVersion": "AUTEDI-VALIDATION-v1"
  }
}
```

- 신규 작업은 현재 서버 설정으로 한 번 선택한다. FIXED/LEGACY_MOCK의 v1 메타데이터는 기존 fixed-response 값을 사용한다.
- 같은 멱등키의 재전송은 기존 작업을 반환하고 프로필을 다시 선택하지 않는다.
- 재시도는 기존 공급자 모드와 저장된 프로필을 상속한다. 서버 선택이 바뀌어도 재시도 계약을 바꾸지 않는다.
- 프로필이 NULL인 과거 작업만 기존 모드의 v1/DEFAULT로 해석한다. 저장된 알 수 없는 프로필을 NULL과 같이 취급하지 않는다.
- 현재 코드에서 실행할 수 없는 모델·프롬프트·검증 버전은 거절한다. 과거 스냅샷을 현재 버전명으로 덮어쓰지 않는다. 프롬프트 식별자는 내용이 바뀌면 새로 부여하고, 계속 실행할 과거 버전은 명시적으로 등록해야 한다.
- Worker가 미지원 프로필의 PENDING 작업을 발견하면 AI 호출 전 `AI_CONTRACT_NOT_READY`로 실패 처리한다. 자동 재시도하지 않는다.
- 기존 결과 기록 API도 작업에 프로필이 있으면 결과 메타데이터와 정확히 일치하는지 검사한다.

프로필 값은 내부 작업 정보다. 이번 단계에서는 `GenerationResponse`, `CorrectionResponse`, 조회·편집 요청 DTO에 필드를 추가하지 않았다.

## 3. DB 변경 적용

운영 DB에는 스키마 변경을 실행하지 않았다. 기존 배포 환경에는 새 엔티티 코드 배포 전에 [추가 컬럼 SQL](../../scripts/sql/2026-11-13-generation-ai-profile.sql)을 한 번 적용한다.

```sql
ALTER TABLE worksheet_generations ADD COLUMN request_ai_profile JSON NULL;
```

기존 컬럼 존재 여부를 먼저 확인한다. 기존 행은 NULL 그대로 두고 v1으로 해석하므로 데이터 일괄 변환은 하지 않는다. 테스트는 메모리 H2의 create-drop만 사용한다.

## 4. 프론트 호환 규칙 — 11~13단계 구현 기준

### 4.1 유지할 외부 계약

- `/api/v1` 주소, `ApiResponse.result`, 생성 접수의 202·상태 폴링 흐름을 유지한다.
- 검토 응답의 `worksheet`, `generation`, `corrections`, `comments`, `reviewIssues`, `summary`, `aiResults`를 유지한다.
- 기존 필드를 삭제·이름 변경·다른 자료형으로 교체하지 않는다. 신규 필드는 선택적으로 추가하고 기존 편집 요청에 필수로 요구하지 않는다.
- 기존 ID·ETag·출처·채택·삭제·편집 상태와 최초 AI 결과 보존 원칙을 유지한다.
- 기존 API의 null 허용값은 유지한다. 새 배열 필드는 자료가 없으면 `[]`, 새 구조 정보가 없거나 무효면 `null`로 표현한다.

### 4.2 일반 카드 매핑

| 기존 응답 필드 | v1.1 매핑 규칙 |
| --- | --- |
| `originalText` | 검증된 모든 anchors의 원문을 순서대로 LF로 연결한 전체 원문. 첫 줄만 잘라서 반환하지 않음 |
| `aiInitialContent` | REPLACE의 최초 replacement 또는 GUIDANCE의 최초 guidance. 불변 |
| `content` | 최초에는 위 값과 같고 이후에는 교사 편집본 |
| `explanation` | 최초 reason. 교사 편집으로 덮어쓰지 않음 |
| `action`, `feedbackType`, `area`, `issueKinds` | 같은 의미의 기존 값으로 변환. 색상·수정 동작을 action에 새 값으로 끼워 넣지 않음 |
| `regions` | 현재 원문 위치의 기존 pageId/x/y/width/height/polygon 배열. 초기값은 검증된 target_boxes 전체를 LTRB→XYWH 변환 |
| `anchor` | 정확한 단일 앵커로 나타낼 수 있을 때 기존 객체. 다중 줄 원문은 **null**. 첫 앵커를 전체 범위처럼 반환하거나 가짜 start/end를 만들지 않음 |
| `warnings`, `requiresReview`, `locationUncertain` | 같은 의미를 유지하고 v1.1 검증 사유를 반영 |
| `regionCoordinateUnit` | NORMALIZED_0_1 유지 |

기존 anchor는 미분류 과거 자료에서도 nullable이다. 다중 줄도 기존 regions/content로 표시할 수 있게 하되, **기존 프론트가 null anchor와 여러 regions를 처리하는지는 12단계 통합 검증에서 확인한다.** 확인 전 모든 기존 화면 동작이 검증됐다고 주장하지 않는다.

선택적으로 추가할 조회 필드는 아래로 확정한다. 추가 시점은 12단계다.

| 추가 필드 | 규칙 |
| --- | --- |
| `contractVersion` | 해당 카드 생성 결과의 AI 계약 식별자 |
| `anchors` | transcriptionId/lineId/occurrence/start/end를 가진 전체 검증 원문 연결 배열 |
| `revisionScope` | 최초 v1.1 범위. v1 결과는 null이며 추측하지 않음 |
| `aiInitialEdit` | 최초 AI edit. 불변. v1·GUIDANCE는 null |
| `edit` | 현재 content와 일치하는 유효한 수정 동작만 제공. 없거나 교사 편집으로 무효면 null |
| `targetRegions` | 기존 regions와 동일한 현재 원문 좌표. 별도 편집본이나 수정 문구 표시 위치가 아님 |

새 렌더러가 edit를 사용하기 전까지 기존 content/regions 표시를 유지한다. v1에는 실제 저장된 anchor가 있으면 anchors 한 원소로 제공할 수 있지만 revisionScope/edit/주석 배치를 만들어 넣지 않는다. annotation·레이아웃·색상·클릭 영역 UI의 전면 교체는 이번 연결의 필수 범위가 아니다.

### 4.3 확인 목록·집계·메타데이터

- REVIEW와 검증상 확인 항목은 기존 reviewIssues로, 제외 항목은 기존 집계 원칙으로 제공한다. REVIEW를 일반 카드나 교사 코멘트로 바꾸지 않는다.
- `reviewIssues[].original`의 기존 v1 키는 호환 투영으로 유지한다. v1.1의 전체 원본 후보는 새 `originalV11`에 보존한다. 다중 줄에서 기존 original.anchor/resolvedAnchor는 null로 두고 새 anchors/resolvedAnchors에 전체를 제공한다. validBoxes는 기존 LTRB 배열 형식을 유지한다.
- 호환 투영은 외부 응답 전용이다. 이를 저장용 v1 원본으로 다시 사용하거나 v1 파서에 넣지 않으며, 최초 v1.1 응답과 검증 결과는 자기 버전으로 보존한다.
- summary의 기존 필드는 유지한다. v1은 기존 recommendedCount를 유지하고 v1.1은 **recommendedCount=0(권장 개수 없음)**, 활성 영역 maximumCount=7로 제공한다. 0은 첨삭 목표 0개라는 뜻이 아니다. 기존 화면의 권장 수 표시도 12단계에서 확인한다.
- v1.1 AI/검증 계약에 권장 개수를 다시 추가하는 것은 아니다. 프론트 호환용 필드만 유지하는 결정이다.
- aiResults.metadata에는 실제 저장된 모델·계약·프롬프트·검증 버전을 제공한다. 과거 결과 버전을 현재 서버 기본값으로 바꾸지 않는다.

### 4.4 기존 교사 편집 요청

- 기존 `content`, `regions`, `isApplied` 요청을 그대로 허용한다. 새 edit/anchors 필드를 보내야만 기존 편집이 성공하는 구조로 만들지 않는다.
- 기존 클라이언트가 **실제로 달라진 content만** 보내면 최초 aiInitialEdit는 보존하고 현재 edit는 null로 무효화한다. 기존 edit.text를 새 content와 일치하는 것처럼 반환하거나 새로운 동작을 추측하지 않는다.
- 같은 content 재전송·좌표 이동·채택 토글만으로 edit를 무효화하지 않는다. regions와 targetRegions는 하나의 현재 좌표를 공유한다.
- 향후 content와 edit를 함께 보내는 확장 요청은 일관성을 검증한다. GUIDANCE는 edit=null을 유지한다.
- ETag, 동시 수정 충돌, 삭제·복원, 최초 AI 응답/원문/설명 보존은 기존대로 유지한다.

## 5. 검증과 후속 순서

테스트는 버전별 고정 응답, 로컬 가짜 HTTP, 메모리 H2/MVC를 사용하며 실제 AI·운영 DB는 호출하지 않는다.

- v1/DEFAULT와 v1.1/DEFAULT·COMPACT의 정확한 프롬프트·Schema·파서·검증기 선택
- 다른 계약의 응답에 대한 자동 대체 금지, 다중 anchors와 edit 보존
- 잘못된 버전·후보·모델·프롬프트 스냅샷 거절
- 새 작업 프로필의 실제 DB 저장·복원, 멱등키 재전송, 설정 변경 뒤 재시도, 프로필 없는 과거 작업
- 아직 연결하지 않은 v1.1의 503 응답과 작업/전사/외부 호출 부재
- 기존 v1 저장·조회·교사 편집 및 API 필드 회귀 검증

남은 순서는 **11 저장·복원 → 12 기존 형식 조회 매핑 → 13 기존 편집 호환 → 14 서비스 HTTP/Worker 연결**이다. 14단계에서 저장된 프로필로 라우터를 사용하고 실행 지원 검사를 확장한다. 프롬프트·짧은 anchor 정확도는 연결 이후 실제 서비스 응답으로 계속 개선한다. 계약 구조 통과를 교육적 품질 합격으로 해석하지 않는다.
