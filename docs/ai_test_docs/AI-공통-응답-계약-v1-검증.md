# AI 공통 응답 계약 v1 — 객체 검증·후처리

기준: [공통 응답 계약 v1](AI-공통-응답-계약-v1.md).
[DTO·스키마·파서 단계](AI-공통-응답-계약-v1-구현.md)에 이어 객체 검증을 추가했다.
DB, Controller, 실제 모델 호출 및 프롬프트에는 연결하지 않았다.

## 입력과 사용

```java
AiCorrectionResponse response = new AiCorrectionResponseParser().parse(json);
AiValidationContext context = AiValidationContext.fromText(
        1,
        transcription,
        Map.of(
                AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD,
                AiArea.CONTENT_SUMMARY, AiCorrectionLevel.ADVANCED));
AiValidationResult result = new AiCorrectionResponseValidator().validate(response, context);
```

검증 패키지는 `com.ceos24.autedi.domain.generation.contract.v1.validation`이다.
한 번에 한 페이지를 검증한다. context에는 해당 이미지와 같은 페이지의 전사문·선택 설정을 전달한다.
context 생성 시 CRLF/CR을 LF로 통일하며 빈 줄·공백·마지막 빈 줄을 유지한다.
이미 줄 번호가 확정된 경우 `List<String>` 생성자를 사용한다. 첨삭 요청에도 같은 전사문을 사용해야 한다.
빈 전사문과 공백뿐인 전사문은 입력 오류로 거부한다. 정상 전사문과 `items=[]`는 첨삭 없는 성공이다.

## 결과와 보존

| 결과 | 대상 |
| --- | --- |
| `corrections()` | action·영역·anchor 검사를 통과하고 개수 제한 안에 들어온 REPLACE/GUIDANCE |
| `reviewIssues()` | 모델 REVIEW, 원문 불일치, action별 필드 조합·문제 종류 불일치 등 확인 대상 |
| `excludedItems()` | 꺼진 영역, 꺼진 문제 종류의 우회, 개수 제한 초과로 표시에서 제외한 후보 |

세 목록은 후보별로 서로 배타적이다. 꺼진 영역 등 명확한 제외 조건과 확인 조건이 함께 있으면
제외로 분류하며 모든 검증 사유를 함께 보존한다.
좌표 문제만 있는 카드는 `corrections()`에 남을 수 있다. 유효 좌표만 제공하고 `requiresReview=true`로 표시한다.
겹치는 원문 범위나 모델의 위치 불확실 표시도 경고이며 제안을 자동 적용하거나 병합하지 않는다.

- `originalResponse`: 입력 후보 전체를 그대로 보존한다.
- `context`: 페이지 번호, 전사문, 선택 설정의 불변 스냅샷이다.
- `candidates`: 모델 반환 순서의 전체 검증 결과이다. 누락하거나 잘라내지 않는다.
- 각 항목의 `candidateIndex`: 원본 `items`의 0 기반 인덱스이다.
- 각 항목의 `original`: replacement/guidance/boxes 등을 바꾸지 않은 원본 후보이다.
- `resolvedAnchor`: 찾은 줄 번호와 해당 줄 안의 UTF-16 start/end이다. end는 미포함이며 실패하면 null이다.
- `validBoxes`: 범위·양수 면적 검사를 통과한 원문 좌표이다.
- `issues`: 코드, 필드 경로, 설명을 보존한다. 잘못된 박스는 `boxes[인덱스]`로 특정한다.
- `requiresReview`: 모델 REVIEW 또는 서버 검증 사유가 있음을 표시한다. 모델의 `location_uncertain`과 구분한다.

이 보존은 반환 객체 안의 메모리 보존이다. 원문 JSON 문자열과 생성 작업에 연결한 영속 저장은 아직 구현하지 않았다.

## 검증 정책

### 위치

`AiLocationChecks`는 기존 실험의 `findOccurrence`, `parseBox` 알고리즘을 객체 입력용으로 재사용한다.
실험 소스는 그대로 두고 새 호환성 테스트에서 직접 결과를 대조한다.
한 줄의 정확한 부분문자열을 대소문자·공백 변경 없이 검색하며, 같은 원문의 겹치지 않는 등장 순서를 센다.
이모지 앞뒤 위치도 Java String의 UTF-16 인덱스로 계산한다.

박스는 숫자 4개, 유한 값, 0~1 범위, `left < right`, `top < bottom`을 검사한다.
잘못된 박스만 제거하고 원본과 사유를 보존한다. 값을 뒤집거나 clamp하지 않는다.
anchor를 찾지 못한 후보에는 임의의 문자 위치나 표시 좌표를 만들지 않는다.
원문이 맞고 좌표가 없으면 카드와 제안을 보존한다.
겹침 검사는 최종 표시 대상인 카드·확인 항목 사이에서 수행하며 제외 항목은 다른 카드에 겹침 경고를 만들지 않는다.
box가 실제 손글씨를 정확히 감싸는지는 자동 판정하지 않는다.

기존 엄격한 JSON 파서는 변경하지 않았다. 스키마 구조를 위반한 JSON은 파서에서 거부한다.
후처리는 이미 읽힌 객체를 대상으로 한다. 고정 JSON에는 파서가 읽을 수 있는 역전·면적 0 박스를 사용하고,
범위 밖·NaN·무한대·잘못된 길이 등의 방어 검사는 직접 만든 고정 객체로 검증한다.

### 동작·설정

- REPLACE: feedbackType·area·anchor·replacement 필수, guidance=null. 빈 replacement는 삭제 제안이다.
- GUIDANCE: feedbackType·CONTENT_SUMMARY·anchor·비어 있지 않은 guidance 필수, replacement=null.
- REVIEW: feedbackType·area·replacement·guidance=null. anchor가 없으면 boxes=[]와 locationUncertain=true가 필요하다.
- 일반 첨삭 종류와 확인 필요 종류를 혼용한 후보는 확인 목록으로 보낸다.
- 대표 영역은 적어도 한 문제 종류의 영역과 일치해야 한다.
- grammar/spelling/punctuation은 GRAMMAR_SPELLING, vocabulary는 VOCABULARY_EXPRESSION,
  sentence_structure는 SENTENCE_STRUCTURE, content는 CONTENT_SUMMARY에 대응한다.
- 여러 종류를 가진 카드도 하나로 유지하지만, 포함된 종류의 영역이 꺼져 있으면 다른 대표 영역으로 우회할 수 없다.

GUIDANCE는 영숫자로 된 단어를 최대 10개 허용한다. 내부 apostrophe·하이픈은 한 단어로 센다.
숫자 토큰도 한 단어로 세며 영어 알파벳이 필요하다. 영어 외 문자 언어의 글자가 있으면 확인 대상으로 보낸다.
이는 언어와 단어 수의 기계적 검사이며 영어 문법·내용의 적절성을 보장하지 않는다.
reason의 빈 값은 검사하지만 한국어 존중 표현·문장 수·의미 품질은 자동 검증하지 않는다.

### 개수·정렬

페이지별·대표 영역별로 Standard는 권장 3/최대 5, Advanced는 권장 5/최대 7이다.
권장 수는 최소 수가 아니다. REPLACE와 GUIDANCE를 합산하며 boxes·문제 종류 수는 카드 수에 영향을 주지 않는다.
REVIEW와 검증에서 확인 대상으로 분리한 후보, 꺼진 영역의 후보는 일반 카드 한도를 소모하지 않는다.

검증을 통과한 후보 중 모델 반환 순서대로 한도까지 채택한다. 초과 후보는 원본·사유와 함께 제외 목록에 남긴다.
그 후 카드·확인 목록은 줄 번호, start, 원본 인덱스 순서로 정렬한다. 위치 없는 확인 항목은 마지막에 둔다.
페이지가 바뀌면 별도 context로 검증하므로 한도도 새로 계산한다.

`summary`는 네 영역별 선택 수준, 권장·최대 수, 표시 수, 제외 수를 제공한다.
선택하지 않은 영역은 level=null이고 권장·최대 수는 0이다.
`omittedCount()`는 제외 목록 크기이며, 확인 목록으로 분리된 수나 모델이 발견하지 못한 오류 수는 아니다.

## API 없는 검증

```powershell
$env:JAVA_HOME = 'C:\Users\82108\.jdks\temurin-21.0.11-1'
.\gradlew.bat --offline test --tests "*AiCorrectionResponseValidatorTest" --tests "*AiLocationChecksCompatibilityTest"
.\gradlew.bat --offline build spotlessCheck
```

추가 고정 입력은 `src/test/resources/ai/contract/v1/postprocessing-candidates.json`이다.
세 목록 분류, 원본·사유 보존, 빈 줄·UTF-16·occurrence, 박스 개별 제외, 조합·선택 영역,
페이지·영역별 상한, 모델 순서의 우선순위, 원문 정렬과 기존 실험 알고리즘 호환성을 검사한다.

## 남은 단계

DB·생성 작업·전사 버전 식별자와의 연결, 원문 응답 문자열 영속 저장, 서비스 DTO·regions 변환,
Controller·조회·편집 연동, 실제 모델 호출, 의미적 첨삭·좌표 품질 평가는 아직 구현하지 않았다.
