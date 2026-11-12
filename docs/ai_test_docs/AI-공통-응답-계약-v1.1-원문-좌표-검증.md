# v1.1 원문·좌표 검증

- 기준: [AI 공통 응답 계약 v1.1](AI-공통-응답-계약-v1.1.md)
- 범위: 같은 페이지의 확정 전사문과 파싱된 후보를 비교하는 독립 검사
- 연결 상태: 실제 AI API·Spring·DB를 사용하지 않으며 현재 v1 생성 경로도 변경하지 않는다.

## 1. 추가 파일

| 파일 | 역할 |
| --- | --- |
| [AiLocationValidator](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiLocationValidator.java) | 모든 후보의 원문 연결·UTF-16 범위·원문 좌표 검사 |
| [AiLocationContext](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiLocationContext.java) | 페이지 번호와 확정 전사문 목록. 공백·빈 줄 보존 |
| [AiResolvedAnchor](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiResolvedAnchor.java) | 해당 줄 안의 0 기반 UTF-16 start/end. end 미포함 |
| [AiLocationValidationResult](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiLocationValidationResult.java) | 원본 후보·후보 인덱스·확인된 원문·범위·유효 박스·진단 |
| [AiValidationCode](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiValidationCode.java) | 원문·좌표 및 수정 동작 단계의 진단 코드 |
| [AiValidationIssue](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiValidationIssue.java) | 진단 코드·원본 필드 위치·설명 |

## 2. 호출 방법

```java
AiCorrectionResponse candidates = new AiCorrectionResponseParser().parse(rawJson);
AiLocationContext context = AiLocationContext.fromText(pageNumber, confirmedTranscription);
List<AiLocationValidationResult> located = new AiLocationValidator().validate(candidates, context);
```

v1.1 파서는 JSON 형식을 검사하고, 이 검사는 DTO를 전사문과 비교한다. 응답 후보 수·반환 순서를 유지하며 어떤 후보도 임의로 삭제하거나 합치지 않는다. `candidateIndex`는 원본 items의 0 기반 인덱스다.

호출자는 **같은 페이지·같은 전사 버전·같은 보정 PNG**를 연결해야 한다. 모델 anchor에는 전사 ID나 이미지 해시가 없으므로 이 독립 검사만으로 DB 식별자·이미지 바이트의 동일성을 증명하지 않는다. 실제 연결 시 기존 생성 작업의 이미지 해시·전사 바인딩 검사도 유지해야 한다.

## 3. 원문 연결 규칙

1. `line_id`가 지정한 전사 줄에서만 정확한 `original`을 찾는다. 다른 줄 탐색, 대소문자·철자·공백 보정은 하지 않는다.
2. occurrence는 겹치지 않는 등장 순서다. 예를 들어 `aaaaaa`의 `aa` 3번째는 UTF-16 4~6이다.
3. Java 문자열 인덱스로 UTF-16 start/end를 계산한다. emoji 등 보조 평면 문자도 보존하고 서로게이트 쌍 중간을 자른 범위는 거절한다.
4. 여러 anchors는 입력 순서대로 줄 번호가 1씩 증가해야 한다. 중복·역순·건너뛰기는 허용하지 않는다.
5. 첫 줄은 줄 끝까지, 마지막 줄은 줄 처음부터, 중간 줄은 전체를 포함해야 한다. 빈 줄·공백 줄을 넘는 문단 병합도 허용하지 않는다.
6. 모두 확인된 경우에만 원문을 `\n`으로 연결한다. 하나라도 실패하면 `originalText=null`, `resolvedAnchors=[]`로 둔다. 확인된 일부만 교체·표시하도록 노출하지 않는다.
7. 일반 첨삭에 빈 anchors가 있으면 진단을 남긴다. 위치 없는 REVIEW는 빈 anchors를 허용한다.

확인되지 않은 원문에 연결된 좌표는 표시용 결과에서 제거하지만 `original` 후보의 anchors·박스·제안문은 그대로 보존한다.

## 4. 좌표와 불확실성

- 박스는 같은 보정 PNG 전체 기준 `[left, top, right, bottom]`이다.
- 숫자 4개, 유한한 0~1 값, `left < right`, `top < bottom`을 검사한다.
- 원래 순서를 유지하고 잘못된 박스만 유효 좌표 목록에서 제외한다. 좌표를 뒤집거나 범위 안으로 잘라 보정하지 않는다.
- anchors 수와 박스 수의 1:1 대응을 요구하지 않는다.
- 원문이 확인됐지만 유효 박스가 없으면 텍스트를 보존하고 위치 경고를 남긴다.
- 모델의 `location_uncertain=true`는 좌표가 있어도 유지하며 자동 배치 가능 상태로 취급하지 않는다.
- 원본 박스가 비어 있는데 위치 불확실 표시가 false이면 불일치 경고를 남기고 원본 flag를 변경하지 않는다.
- `GUIDANCE`와 `REVIEW`는 이 검사에서도 자동 이미지 주석 배치 대상으로 만들지 않는다.

`canAutoPlace()`는 **위치 검사 범위에서만** 원문·유효 박스가 있고 모델 위치가 확실한 REPLACE인지 알려준다. 정상 카드 승인이나 최종 레이아웃 완료를 의미하지 않는다. 이후 동작·레벨·범위 등 모든 검증을 통과한 후보에만 실제 배치를 적용해야 한다.

일부 박스만 잘못된 경우 `requiresReview=true`여도 남은 좌표가 확실하면 위치 단계의 `canAutoPlace=true`일 수 있다. 위치 자체가 불확실하거나 전체 원문 연결이 실패한 경우에는 false다. `requiresReview`는 모델 REVIEW 또는 위치 진단 유무를 반영하며, 최종 서버 검증 결과와 병합해야 한다.

이 코드는 박스가 실제 손글씨를 정확히 감싸는지 확인하지 않는다. 숫자·원문 연결의 타당성 검사이며 이미지 픽셀·OCR 품질 검증은 실제 자료로 별도 수행한다.

## 5. 위치 검사에서 수행하지 않는 정책

- action별 필드 조합과 edit·replacement 일치
- Standard의 SUGGESTION 금지, 선택 영역, revision_scope와 operation 관계
- GUIDANCE의 영어·10단어 제한
- 후보 사이 겹침, 최대 7개, 오류 우선순위, 화면 정렬
- 정상 카드·확인 목록·제외 목록의 최종 분류
- 저장·API·프론트 주석 생성·실제 AI 요청

action별 필드 조합과 edit·replacement 일치 검사는 별도 [수정 동작 검사](AI-공통-응답-계약-v1.1-수정-동작-검증.md)로 추가했다. 위치 결과를 입력으로 받아 앞 단계 진단과 함께 보존하며, 나머지 정책은 후속 단계다.

위치가 맞는 Standard SUGGESTION이나 8번째 후보도 이 단계는 그대로 돌려준다. 후속 검증에서 분리·제한해야 한다. 기존 v1 검증기를 변경하거나 v1.1 DTO를 v1으로 억지 변환하지 않는다.

## 6. 테스트

프로젝트 루트, Java 21 환경:

```powershell
.\gradlew.bat --offline test --tests "*domain.generation.contract.v1.*" --tests "*domain.generation.contract.v1_1.*" spotlessJavaCheck
```

- [AiLocationValidatorTest](../../src/test/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiLocationValidatorTest.java): 읽을 수 있는 고정 응답 26개와 추가 원문·좌표 경계 사례
- [AiLocationContextTest](../../src/test/java/com/ceos24/autedi/domain/generation/contract/v1_1/validation/AiLocationContextTest.java): 줄바꿈만 정규화, 빈 줄·공백·원문 불변성, 잘못된 입력 문맥
- 기존 파서·DTO·Schema·예제 무결성 및 v1 계약 회귀 테스트

고정 예제의 정상 카드에 해당하는 원문·UTF-16·좌표·위치 경고 필드만 이번 위치 단계의 실제 결과와 비교한다. 최종 cards/reviews/excluded 분류와 summary는 나머지 검증 정책이 구현된 뒤 비교한다. 테스트는 외부 API·Spring·DB를 실행하지 않는다.
