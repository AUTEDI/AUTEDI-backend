# v1.1 DTO·JSON Schema

기준은 [AI 공통 응답 계약 v1.1](AI-공통-응답-계약-v1.1.md)이다.
이 문서는 새 응답의 Java 자료형과 JSON 형식 추가 단계를 설명한다. 이후 [v1.1 엄격한 파서](AI-공통-응답-계약-v1.1-파서.md), [원문·좌표 검사](AI-공통-응답-계약-v1.1-원문-좌표-검증.md), [수정 동작 검사](AI-공통-응답-계약-v1.1-수정-동작-검증.md)를 별도로 추가했다. 나머지 정책 검증은 후속 단계이며, 현재 생성·저장·조회 경로는 v1을 유지한다.

## 1. 추가한 파일

| 파일 | 역할 |
| --- | --- |
| [AiCorrectionContract](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/AiCorrectionContract.java) | 계약 식별자 `AUTEDI-AI-CONTRACT-v1.1`과 Schema 리소스 경로 |
| [AiCorrectionResponse](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/AiCorrectionResponse.java) | 최상위 `items` 목록 |
| [AiCorrectionItem](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/AiCorrectionItem.java) | 첨삭 후보의 12개 필드 |
| [AiAnchor](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/AiAnchor.java) | `anchors[]`에 들어가는 한 줄의 원문 연결 |
| [AiEdit](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/AiEdit.java) | `operation`, `text`, `position` |
| `AiAction`, `AiArea`, `AiFeedbackType`, `AiIssueKind` | v1.1 패키지의 독립적인 분류 enum. 기존 v1 타입에 의존하지 않음 |
| `AiEditOperation`, `AiEditPosition`, `AiRevisionScope` | 수정 동작·삽입 위치·수정 범위의 enum |
| [correction-response.schema.json](../../src/main/resources/ai/contract/v1.1/correction-response.schema.json) | 필수 필드·타입·허용값·명시적 null·배열 구조 |

클래스는 `contract.v1_1`, 리소스는 `ai/contract/v1.1`에 둔다. 기존 v1 파일을 이동하거나 이름을 변경하지 않는다.

## 2. 형식 규칙

- AI JSON은 `feedback_type`, `issue_kinds`, `location_uncertain`, `target_boxes`, `revision_scope`, `line_id`처럼 snake_case를 쓴다. DTO는 `@JsonProperty`로 Java 이름과 연결한다.
- 모든 item은 12개 키를 포함하고, 해당하지 않는 값은 `null`이다. 배열은 null 대신 `[]`를 사용한다.
- `anchors`, `target_boxes`는 배열이며 여러 원소를 허용한다. REVIEW의 빈 배열과 정상적인 `items=[]`도 허용한다.
- 삭제는 `replacement=""`, `edit.text=""`로 보존한다. 삭제 이외의 action·operation별 제약은 후속 검증기에서 확인한다.
- `edit.position`은 `BEFORE`, `AFTER`, `null` 중 하나다. 수정 범위는 다섯 enum 값 또는 null이다.
- 각 box는 0~1 숫자 네 개다. 좌우·상하의 대소 관계는 후속 검증 대상이다.
- DTO 목록과 중첩 좌표 목록은 방어적으로 복사하여 생성 후 외부 변경을 막는다. 문자열·원문·좌표 값을 정규화하거나 잘라내지는 않는다.
- 서버가 생성할 색상·ID·문자 인덱스·클릭 영역을 AI 응답 필드에 추가하지 않는다.

## 3. 이번 단계에서 하지 않는 검사

Schema는 자료의 형태를 제한하고 DTO는 미검증 후보를 담는다. 다음 관계는 이후 파서·검증기에서 구현한다.

- 원문의 실제 존재, occurrence와 UTF-16 위치, 여러 anchors의 연속성
- action별 필드 조합, edit·replacement의 의미 일치
- Standard의 SUGGESTION 금지와 활성 영역 검사
- 수정 범위와 영역·operation의 관계
- GUIDANCE의 영어·10단어 제한
- 박스의 양수 면적, 위치 불확실성, 중복·겹침, 영역별 최대 7개

일반 Jackson 역직렬화만으로 필수 키·엄격한 JSON 응답 계약이 모두 검사된다고 가정하지 않는다. DTO를 읽을 수 있어도 정상 카드로 공개하려면 별도의 엄격한 파서와 후속 검증기를 거쳐야 한다. DTO·Schema 추가 단계는 실제 AI 요청의 Schema 선택도 변경하지 않는다.

## 4. 검증

Java 21 환경에서 프로젝트 루트를 기준으로 실행한다.

```powershell
.\gradlew.bat --offline test --tests "*domain.generation.contract.v1.*" --tests "*domain.generation.contract.v1_1.*" spotlessJavaCheck
```

- `AiCorrectionResponseSchemaTest`: Schema와 DTO의 정확한 필드·필수 키·enum·null 규칙, 참조와 배열·좌표 제약의 일치 확인
- `AiCorrectionResponseDtoTest`: 고정 예제에서 구조상 읽을 수 있다고 분류한 26개 응답의 Jackson 왕복, null·삭제·삽입·다중 anchors와 방어적 복사 확인
- 기존 `AiCorrectionFixtureResourcesTest`: 예제 묶음의 무결성 유지
- 기존 v1 계약 테스트: v1 DTO·Schema·파서·검증기·프롬프트 회귀 확인

이 문서의 DTO·Schema 테스트는 범용 JSON Schema 검증 엔진이나 엄격한 파서를 대신하지 않는다. JSON 형식 거절 사례는 별도로 추가한 `AiCorrectionResponseParserTest`에서 실제 파싱 결과와 연결했다. AI 품질·문맥 검증·DB·API·프론트 연동 검증은 후속 단계다.
