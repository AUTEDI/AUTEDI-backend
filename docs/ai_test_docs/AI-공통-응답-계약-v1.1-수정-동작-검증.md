# v1.1 수정 동작 검증

- 기준: [AI 공통 응답 계약 v1.1](AI-공통-응답-계약-v1.1.md) 4.1·4.3절
- 범위: action별 필드 조합과 검증된 원문·교체문·표시 문구의 일관성 검사
- 연결 상태: 독립 검사와 테스트다. 실제 AI API·Spring·DB를 사용하지 않으며 현재 v1 생성 경로는 유지한다.

## 1. 추가 파일

| 파일 | 역할 |
| --- | --- |
| [AiEditValidator](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiEditValidator.java) | 원문·좌표 결과를 받아 필드 조합과 INSERT·DELETE·REPLACE·RESTRUCTURE 검사 |
| [AiEditValidationResult](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiEditValidationResult.java) | 앞 단계 결과와 동작 진단 보존, 통합 진단·검토 필요 여부 제공 |
| [AiEditValidationStatus](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiEditValidationStatus.java) | 일관성 확인·불일치·원문 미확인 구분 |
| [AiValidationCode](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiValidationCode.java) | 기존 위치 코드에 필드·동작 불일치 진단 코드 추가 |
| [AiEditValidatorTest](../../src/test/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiEditValidatorTest.java) | 고정 응답과 동작별 경계·오류 사례 검사 |

## 2. 호출 방법과 결과

```java
AiCorrectionResponse candidates = new AiCorrectionResponseParser().parse(rawJson);
AiLocationContext context = AiLocationContext.fromText(pageNumber, confirmedTranscription);
List<AiLocationValidationResult> located = new AiLocationValidator().validate(candidates, context);
List<AiEditValidationResult> edited = new AiEditValidator().validate(located);
```

이 독립 모듈의 연결 순서는 파서 → 원문·좌표 → 수정 동작이다. 원문이 필요한 동작 검사에는 앞 단계의 `originalText`만 사용한다. 최종 서비스 연결 시에는 계약 5절의 설정·범위·겹침 등 전체 검증 순서와 함께 구성해야 한다.

- 반환 개수·순서는 입력과 같다. 후보를 수정·삭제·정렬하지 않는다.
- `location`으로 원본 후보, 페이지·후보 인덱스, 검증된 원문·범위·좌표와 기존 진단을 보존한다.
- `editIssues`는 이번 단계의 진단이며, `issues()`는 기존 위치 진단 뒤에 동작 진단을 붙인 불변 목록이다.
- `requiresReview()`는 기존 검토 상태 또는 이번 단계의 미완료·불일치를 반영한다.
- `canAutoPlace()`는 동작이 일관되고 위치 단계도 배치 가능한 경우에만 true다. 이후 설정·범위 정책과 실제 레이아웃 검사가 남으므로 최종 승인값이 아니다.

| `status()` | 의미 |
| --- | --- |
| `CONSISTENT` | 이번 단계의 필드·동작 규칙에 불일치가 없음 |
| `INCONSISTENT` | 이번 단계에서 한 가지 이상 불일치를 발견함 |
| `SOURCE_UNRESOLVED` | 독립적인 필드 불일치는 없지만 일반 첨삭의 원문이 확인되지 않음 |

원문 연결이 실패하면 원본 AI anchors를 이어 붙여 대체하지 않는다. 원문 없이 확인 가능한 필드·교체문 불일치는 계속 검사하며, 발견하면 `INCONSISTENT`와 기존 위치 진단을 함께 돌려준다.

정상적인 `REVIEW`는 필드 조합이 맞으면 `CONSISTENT`일 수 있지만 `requiresReview=true`, `canAutoPlace=false`다. 위치 경고만 있는 일반 후보도 동작 상태는 `CONSISTENT`일 수 있다. 이 상태는 정상 카드·확인 목록·제외 목록의 최종 분류가 아니다.

## 3. action별 필드 조합

모든 action에 비어 있지 않은 `reason`을 요구한다.

| action | 이번 단계의 검사 |
| --- | --- |
| `REPLACE` | feedback_type·area·replacement·revision_scope·edit 필수, guidance는 null |
| `GUIDANCE` | feedback_type 필수, area는 CONTENT_SUMMARY, 비어 있지 않은 guidance, replacement·revision_scope·edit는 null |
| `REVIEW` | feedback_type·area·replacement·guidance·revision_scope·edit는 null. anchors가 없으면 target_boxes도 빈 목록이고 location_uncertain은 true |

anchors의 필수 여부와 원문 연결은 앞 단계가 검사한다. feedback_type이 선택 레벨에 맞는지, area가 활성 영역인지 등의 설정 정책은 후속 단계다.

## 4. 수정 동작 규칙

| `edit.operation` | 검사 |
| --- | --- |
| `INSERT` | 비어 있지 않은 replacement·edit.text, BEFORE/AFTER 필수. 지정된 경계의 기존 원문을 유지하고 실제 추가분과 edit.text 비교 |
| `DELETE` | replacement와 edit.text가 정확히 빈 문자열, position은 null |
| `REPLACE` / `RESTRUCTURE` | 비어 있지 않은 replacement와 edit.text가 정확히 같고 position은 null. 검증된 원문과 완전히 같은 교체문은 변경 없음으로 진단 |

삽입에서는 표시 문구 앞뒤 공백을 허용하지 않는다. BEFORE는 교체문이 원문으로 끝나는지, AFTER는 원문으로 시작하는지 확인한 뒤 추가된 부분만 비교한다.

- `dog → a dog`, text=`a`, BEFORE: 통과. 원문과 표시 문구 사이 ASCII 공백 하나를 허용한다.
- `apples → apples.`, text=`.`, AFTER: 통과. 추가분이 표시 문구와 정확히 같다.
- `walk → walked`, text=`ed`, AFTER: 통과. 문자 단위 삽입도 같은 규칙으로 비교한다.
- 원문 경계에 이미 공백이 있다면 이를 그대로 보존하며 공백을 추가로 허용하지 않는다.
- `dog → a cat`은 원문 변경, text=`a`인데 `dog → a brown dog`는 선언하지 않은 추가분으로 진단한다.
- 구분자로 임의의 탭·줄바꿈·NBSP·중복 공백을 허용하거나 문자열을 trim하여 맞추지 않는다.

공백 하나가 언어적으로 필요한지, 접미사·문장부호를 올바르게 썼는지까지 판정하는 영어 문법 검사는 아니다. 교체문과 표시 문구가 같은 변경을 표현하는지 기계적으로 비교한다. REPLACE·RESTRUCTURE 역시 의미 보존이나 문장 품질을 증명하지 않는다.

## 5. 진단 코드와 남은 범위

| 코드 | 의미 |
| --- | --- |
| `INVALID_ACTION_FIELDS` | action에 필요한 필드가 없거나 금지된 필드가 있음 |
| `INVALID_EDIT_POSITION` | INSERT의 위치 누락 또는 다른 동작에 위치 지정 |
| `INVALID_EDIT_TEXT` | 표시 문구가 해당 동작의 빈 문자열·공백 규칙을 위반 |
| `INVALID_REPLACEMENT` | 교체문이 해당 동작의 빈 문자열 규칙을 위반 |
| `EDIT_REPLACEMENT_MISMATCH` | 교체·재구성의 replacement와 edit.text 불일치 |
| `INSERT_SOURCE_CHANGED` | 삽입 방향의 경계에서 기존 원문이 유지되지 않음 |
| `INSERT_TEXT_MISMATCH` | 실제 추가분과 선언된 표시 문구 불일치 |
| `NO_OP_REPLACEMENT` | 교체·재구성의 결과가 검증된 원문과 완전히 같음 |

Standard의 SUGGESTION 금지, 활성 영역·issue_kinds 매핑, revision_scope와 영역·operation의 관계, GUIDANCE 영어·10단어 제한, 중복·겹침·최대 7개·정렬·최종 분류는 이번 단계에 포함하지 않는다. 저장·API·프론트·프롬프트 연결도 후속 단계다. 개별 후보의 문제로 AI를 재호출하지 않는다.

## 6. 테스트

프로젝트 루트, Java 21 환경:

```powershell
.\gradlew.bat --offline test --tests "*domain.generation.contract.v1.*" --tests "*domain.generation.contract.v1_1.*" spotlessJavaCheck
```

- 읽을 수 있는 고정 응답 26개를 실제 파서 → 원문·좌표 → 수정 동작으로 연결
- 정상 후보의 일관성·위치 상태, 동작 불일치 예제의 진단 비교
- action별 null·금지 필드, 네 동작의 위치·문구·교체문 검사
- 삽입 경계 공백·문장부호·접미사, 삭제 빈 문자열, 교체문 불일치·변경 없음 검사
- 원문 미확인과 동작 오류 동시 발생, 위치 경고·원본·순서·불변성 보존
- 후속 레벨·범위·개수 정책을 조기에 적용하지 않는지 확인
- 기존 v1 계약 및 v1.1 예제·DTO·Schema·파서·위치 회귀 검사

최종 `cards/reviews/excluded`와 summary 전체 비교는 남은 정책 구현 후 수행한다. 테스트는 실제 AI API·Spring·DB를 실행하지 않는다.
