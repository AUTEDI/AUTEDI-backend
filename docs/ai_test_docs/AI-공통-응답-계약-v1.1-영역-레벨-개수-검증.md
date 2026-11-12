# v1.1 영역·레벨·개수 검증

- 기준: [AI 공통 응답 계약 v1.1](AI-공통-응답-계약-v1.1.md) 3·5·9절
- 범위: 영역·레벨·범위·안내문 정책, 원문 겹침 경고, 개수 제한, 최초 분류·정렬·집계
- 연결 상태: 독립 Java 검증기와 합성 응답 테스트다. 실제 AI 요청·프롬프트·Worker·DB·API·프론트는 v1을 유지한다.

## 1. 파일과 호출 방법

| 파일 | 역할 |
| --- | --- |
| [AiCorrectionResponseValidator](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiCorrectionResponseValidator.java) | 기존 위치·동작 검사를 호출하고 설정 정책·제한·분류를 연결하는 진입점 |
| [AiValidationContext](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiValidationContext.java) | 위치 문맥과 생성 당시 영역별 레벨을 불변 스냅샷으로 보존 |
| [AiCorrectionLevel](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiCorrectionLevel.java) | STANDARD / ADVANCED. 두 레벨 모두 최대 7개, 권장·최소 개수 없음 |
| [AiItemDisposition](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiItemDisposition.java) | CARD / REVIEW / EXCLUDED 판정. CARD에는 오류 수정과 선택적 제안 모두 포함 |
| [AiValidatedItem](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiValidatedItem.java) | 원본·위치·동작 검사와 정책 진단을 함께 보존 |
| [AiValidationResult](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiValidationResult.java) | 전체 후보, 화면 순서의 카드·확인 목록, 제외 목록, 영역별 집계 |
| [AiAreaSummary](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiAreaSummary.java) | 영역별 레벨·최대·최초 검증·현재 표시·제외 수 |
| [AiValidationCode](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiValidationCode.java) | 기존 코드에 영역·레벨·범위·안내문·겹침·한도 진단 추가 |
| [AiCorrectionResponseValidatorTest](../../src/test/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiCorrectionResponseValidatorTest.java) | 고정 응답 26개의 실제 분류·위치·경고·순서·집계와 추가 정책 경계 검사 |

```java
AiCorrectionResponse response = new AiCorrectionResponseParser().parse(rawJson);
AiValidationContext context = AiValidationContext.fromText(
        pageNumber,
        confirmedTranscription,
        Map.of(
                AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD,
                AiArea.SENTENCE_STRUCTURE, AiCorrectionLevel.ADVANCED));
AiValidationResult result = new AiCorrectionResponseValidator().validate(response, context);
```

이 진입점은 내부에서 `AiLocationValidator` → `AiEditValidator`를 호출한다. 호출자가 앞 단계 결과를 다시 넘기거나 v1 객체로 변환할 필요가 없다. 단독 위치·동작 검사 API는 기존대로 유지한다.

호출자는 같은 보정 PNG·같은 페이지·같은 확정 전사 버전과 생성 설정을 연결해야 한다. 이 모듈은 DB 식별자나 이미지 바이트의 동일성을 증명하지 않는다.

## 2. 영역·레벨 정책

| 검사 | 처리 |
| --- | --- |
| 비활성 대표 영역 | EXCLUDED, `AREA_NOT_SELECTED` |
| 여러 issue_kinds 중 비활성 영역 포함 | EXCLUDED, `ISSUE_AREA_NOT_SELECTED` |
| 대표 영역이 issue_kinds의 영역에 없음 | REVIEW, `AREA_ISSUE_MISMATCH` |
| 일반 action과 REVIEW용 issue_kinds가 혼합됨 | REVIEW, `INVALID_ISSUE_KINDS` |
| Standard 영역의 SUGGESTION | EXCLUDED, `STANDARD_SUGGESTION` |
| 정상적인 모델 REVIEW | REVIEW, `MODEL_REVIEW`. 일반 카드 한도·omittedCount에서 제외 |

문법·철자·문장부호 → GRAMMAR_SPELLING, 어휘 → VOCABULARY_EXPRESSION, 구조 → SENTENCE_STRUCTURE, 내용 → CONTENT_SUMMARY로 매핑한다.

레벨은 영역마다 독립적으로 검사한다. 대표 영역이 Advanced여도 혼합된 문제 종류에 Standard 영역이 포함된 SUGGESTION은 제외한다. 여러 활성 영역의 CORRECTION은 대표 영역·범위 등 다른 규칙을 만족하면 허용한다. 원본 SUGGESTION을 CORRECTION으로 바꾸지 않는다.

여러 위반이 동시에 있으면 모든 진단을 보존하고 **EXCLUDED → REVIEW → CARD** 우선순위로 하나의 목록에 분류한다. 예를 들어 원문도 틀리고 비활성 영역인 후보는 제외 목록에 남으며 원문 오류 진단도 유지한다.

## 3. 범위·안내문 정책

- 문법·어휘·기존 내용 수정의 범위는 WORD 또는 PHRASE다. 문장 구조 영역은 모든 범위를 허용한다.
- RESTRUCTURE는 SENTENCE_STRUCTURE의 SENTENCE / SENTENCE_GROUP 조합에만 허용한다.
- SENTENCE / SENTENCE_GROUP의 교체를 operation=REPLACE로 표현하면 확인 필요로 분리한다. 문장 전체 삭제와 교체는 구분한다.
- 검증된 원문에 여러 단어가 있는데 WORD라고 신고하면 `SCOPE_TEXT_MISMATCH`를 남긴다. 단어 내부 아포스트로피·하이픈은 한 단어로 센다.
- 물리적인 줄 수로 언어 범위를 추정하지 않는다. 두 줄에 걸친 짧은 구도 PHRASE로 허용한다.
- GUIDANCE는 영숫자 단위로 최대 10단어다. 내부 아포스트로피·하이픈은 한 단어, 숫자 토큰도 한 단어다.
- GUIDANCE에는 ASCII 영어 문자가 필요하며 다른 언어의 문자·악센트 결합 문자가 있으면 확인 필요로 분리한다. 숫자나 문장부호만 있는 안내도 허용하지 않는다.

이 검사는 신고된 범위·동작 조합과 분명한 다단어 WORD 불일치를 잡는다. 구·절·문장의 실제 문법적 경계를 완전히 판정하거나, 문법 오류의 객관성·사실·의미 보존·안내문의 내용 품질을 증명하지는 않는다. 숨겨진 비활성 영역 수정이나 잘못된 PHRASE 신고를 모두 잡으려면 모델 평가와 교사 검토가 필요하다. 영어 안내의 언어 검사도 문자 기반 정책 검사다.

## 4. 개수·겹침·정렬

1. 원문 연결·동작·설정·범위·안내문 검사에서 일반 카드로 남은 후보만 한도에 포함한다. 좌표 경고만 있는 텍스트 카드는 포함한다.
2. 페이지·활성 대표 영역별로 REPLACE와 GUIDANCE를 합쳐 최대 7개다. anchors·박스·문제 종류의 수는 카드 수를 늘리지 않는다.
3. CORRECTION을 먼저 선택하고 남는 자리에 SUGGESTION을 넣는다. 같은 유형 안에서는 AI 반환 순서를 유지한다.
4. 초과 후보는 `CARD_LIMIT_EXCEEDED`와 함께 EXCLUDED에 보존한다. 후보를 잘라 버리거나 AI를 재호출하지 않는다.
5. 겹침은 검증된 일반 카드 후보 사이에서 각 줄의 UTF-16 `[start,end)`로 찾는다. 실제 남은 카드끼리 겹칠 때 양쪽에 `OVERLAPPING_ANCHORS`를 붙인다.
6. 겹치는 카드·완전히 같은 범위의 중복 제안은 임의로 합치거나 삭제하지 않는다. 카드를 유지하면서 `requiresReview=true`, `canAutoPlace=false`로 둔다. 끝과 시작이 맞닿기만 하면 겹침이 아니다.
7. REVIEW·설정 위반·한도 초과로 숨겨진 후보는 표시 카드에 충돌 경고를 만들지 않는다.
8. 개수 선택이 끝난 카드·확인 목록은 첫 번째 검증 anchor의 줄·start·후보 인덱스 순서로 정렬한다. 원문을 못 찾은 확인 항목은 뒤로 둔다. 전체 candidates·제외 목록은 AI 순서를 유지한다.

겹침은 정상 카드에서 분리하는 사유가 아니라 표시 경고이므로 한도 자리를 사용한다. 후보끼리 원문이 겹치는지 검사하며 이미지 박스나 실제 렌더링 문구 사이 충돌은 검사하지 않는다.

## 5. 결과·집계

- `candidates`: 모든 원본 후보를 AI 순서로 보존. 위치·동작·정책 진단을 함께 제공한다.
- `cards()`: 정상 정책을 통과한 REPLACE / GUIDANCE. 위치·겹침 경고가 있을 수 있다.
- `reviewIssues()`: 모델 REVIEW, 원문·동작·범위·안내문 등 확인 필요 후보.
- `excludedItems()`: 비활성 영역·Standard 제안·한도 초과 후보.
- `canAutoPlace()`: CARD이며 위치·동작이 유효하고 정책 경고가 없는 원문 수정 제안만 true. 실제 배치는 렌더러에서 수행한다.

네 영역 모두 summary를 만든다. 활성 영역은 level과 maximumCount=7, 비활성 영역은 level=null과 maximumCount=0이다. recommendedCount는 없다.

최초 결과에서는 validatedCount=displayedCount=제한 후 CARD 수다. omittedCount는 원래 일반 후보 중 REVIEW·EXCLUDED로 분리된 수이며 원래 action=REVIEW는 제외한다. 영역 집계는 원본 대표 영역을 기준으로 한다. 대표 영역 자체가 null인 잘못된 일반 후보는 특정 영역 집계에 넣지 않고 전체 `omittedCount()`에는 포함한다. 교사 삭제·편집 후 displayedCount의 재계산은 서비스 연결 단계다.

## 6. 검증과 다음 연결

프로젝트 루트, Java 21:

```powershell
.\gradlew.bat --offline test --tests "*domain.generation.contract.v1.*" --tests "*domain.generation.contract.v1_1.*" spotlessJavaCheck
```

파서가 읽는 고정 응답 26개의 cards/reviews/excluded, 원문·UTF-16·유효 박스, 경고·자동 배치, display_order와 summary를 기대값과 비교한다. 추가로 독립 영역 레벨·혼합 우회·범위 조합·안내문 단어 경계·오류 우선·페이지별 한도·겹침·불변성을 검사한다. 나머지 파서 거절 응답 4개는 기존 파서 테스트에서 거절을 확인한다.

색상·주석·렌더러·저장·API 응답은 아직 생성하지 않는다. 고정 예제의 annotation 값은 이후 변환·표시 단계의 기대값으로 남긴다. 실제 프롬프트 v1.1 연결과 유료 모델 품질·토큰·시간 측정도 후속 단계다.
