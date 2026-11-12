# v1.1 응답 파서

- 기준: [AI 공통 응답 계약 v1.1](AI-공통-응답-계약-v1.1.md)
- 계약 식별자·Schema 경로: `AiCorrectionContract` 재사용
- 범위: JSON 문자열의 엄격한 구조 검사와 v1.1 DTO 변환
- 연결 상태: 독립 파서·테스트만 추가했다. 실제 생성 경로의 v1 파서를 교체하거나 외부 API를 호출하지 않는다.

## 1. 추가 파일과 사용 방법

- [AiCorrectionResponseParser](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/AiCorrectionResponseParser.java)
- [AiCorrectionResponseParserTest](../../src/test/java/com/ceos24/autedi/domain/generation/contract/v1_1/AiCorrectionResponseParserTest.java)

```java
AiCorrectionResponse response = new AiCorrectionResponseParser().parse(rawJson);
// 이 response는 읽을 수 있는 후보이며 문맥 검증을 통과한 카드가 아니다.
```

v1.1 패키지의 DTO·enum을 사용한다. 기존 v1 파서·DTO·클라이언트·Worker·DB·API는 변경하지 않는다.

## 2. 수행하는 검사

| 범위 | 검사 |
| --- | --- |
| 전체 응답 | 비어 있지 않은 JSON 객체 하나, 최상위 `items` 배열만 허용 |
| JSON 문법 | 중복 키, 뒤에 이어진 다른 JSON, Markdown 코드 블록, API 봉투, 구버전 필드 거절 |
| item | 12개 필수 키만 허용. nullable 값도 키 생략 금지 |
| 타입 | 문자열·boolean·배열·객체를 구분. 숫자·boolean을 문자열로 강제 변환하지 않음 |
| enum | DTO enum의 실제 JSON wire 값과 정확히 비교. 대소문자·알 수 없는 값 거절 |
| issue_kinds | 1개 이상, 중복 없음, 허용된 문제 종류 |
| anchors | 배열. 각 원소는 line_id/original/occurrence만 허용 |
| anchor | 줄 번호·등장 순서는 1 이상 int 범위의 정수, 원문은 비어 있지 않은 한 줄 문자열 |
| target_boxes | 배열. 각 박스는 유한한 0~1 숫자 네 개 |
| edit | null 또는 operation/text/position 세 키를 모두 가진 객체. text의 빈 문자열은 보존 |
| null | feedback_type/area/replacement/guidance/revision_scope/edit와 edit.position에서 형식상 허용 |

Schema 파일을 범용 검증 엔진으로 실행하는 방식은 아니다. Java의 구조 검사와 DTO enum wire 값으로 검증하고, 이후 Jackson `treeToValue`로 변환한다. 기존 Schema·DTO 정합성 테스트와 파서의 필드·타입·거절 테스트를 함께 유지한다.

모든 입력 오류는 계약 식별자를 포함한 `IllegalArgumentException`으로 전달한다. 필드 검사에서 실패하면 `$.items[0].anchors[0].original` 같은 위치를 포함한다. JSON 문법 자체의 실패는 원인 예외를 보존한다.

## 3. 후속 검증기에 남기는 것

- 원문이 확정 전사에 실제 존재하는지와 occurrence 해석
- 여러 anchors의 순서·연속성·문단 경계와 UTF-16 인덱스 계산
- action별 필드 조합, edit·replacement 일관성
- Standard의 SUGGESTION 금지, 활성 영역, 수정 범위·operation 관계
- GUIDANCE의 영어·10단어 제한
- 박스의 좌우·상하 관계와 면적, 실제 이미지 위치의 정확성
- 최대 7개, 오류 우선순위, 후보 분리·경고·제외·정렬

따라서 잘못된 원문 연결이나 역전된 박스도 **필드·기본 타입이 맞으면 파싱한 그대로 보존**한다. 읽을 수 있는 후보를 파서에서 임의로 고치거나 잘라내지 않는다. `items=[]`, REVIEW의 빈 배열·null, 삭제의 빈 문자열, 다중 anchors를 지원한다.

## 4. 테스트

프로젝트 루트에서 Java 21로 실행한다.

```powershell
.\gradlew.bat --offline test --tests "*domain.generation.contract.v1.*" --tests "*domain.generation.contract.v1_1.*" spotlessJavaCheck
```

- [고정 응답 30개](../../src/test/resources/ai/contract/v1.1/README.md): 26개 ACCEPT는 원본과 DTO 왕복 결과 비교, 4개 REJECT는 실제 파서 거절 확인
- 12개 item 키, 3개 anchor 키, 3개 edit 키의 누락 검사
- 응답·item·anchor·edit의 추가 키 및 중복 키 검사
- 문자열·숫자·boolean 강제 변환 방지, enum 대소문자·알 수 없는 값 검사
- 잘못된 배열·null 원소·좌표 범위·숫자 오버플로 검사
- 정수 범위·원문의 CR/LF·후행 개행 검사
- 문맥상 잘못된 후보도 변형하지 않고 후처리로 넘기는지 확인
- 기존 v1 계약 테스트와 v1.1 DTO·Schema·자료 무결성 회귀 검사

파싱 결과와 `cases.json`의 `expected.cards/reviews/excluded`를 실제 비교하는 작업은 다음 문맥 검증기 단계다. 테스트는 Spring·DB·외부 API를 실행하지 않는다.
