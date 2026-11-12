# v1.1 첨삭 프롬프트 파일

- 기준 계약: [AUTEDI-AI-CONTRACT-v1.1](AI-공통-응답-계약-v1.1.md)
- 기획 기준: [Standard / Advanced 첨삭기준 한국어본](../../../자료/Standard%20Advanced%20첨삭기준%20한국어본.txt), [첨삭결과표시 한국어본](../../../자료/첨삭결과표시%20한국어본.txt)
- 범위: 두 번째 첨삭 단계에서 사용할 영어 지시문 리소스 12개 작성
- 연결 상태: Java 조립기·모델 요청·Worker·저장·API는 변경하지 않았다. 실제 생성 경로는 기존 v1 프롬프트를 사용한다.
- 비교 후보: 같은 계약의 [압축 프롬프트](AI-첨삭-프롬프트-v1.1-압축본.md)를 별도 경로에 보관한다. 기본본 교체나 자동 선택은 아직 하지 않는다.

## 1. 파일 구성

공통 파일 4개와 네 영역 × 두 레벨의 상세 파일 8개다. 기존 v1 리소스는 보존하며 새 파일은 v1.1 디렉토리에 둔다.

| 공통 파일 | 역할 |
| --- | --- |
| [common-principles.txt](../../src/main/resources/ai/prompt/v1.1/common-principles.txt) | 원문·의미 보존, 독립 영역·레벨, 오류 우선, 최대 7개, OCR·판독 문제와 위치 불확실성 구분 |
| [output-contract.txt](../../src/main/resources/ai/prompt/v1.1/output-contract.txt) | v1.1의 12개 필드, action별 조합, 문제 종류·범위, 한국어 reason·영어 guidance, AI에게 요구하지 않는 필드 |
| [anchor-boxes.txt](../../src/main/resources/ai/prompt/v1.1/anchor-boxes.txt) | 정확한 anchors·occurrence·연속 다중 줄·문단 경계, 같은 PNG 기준 target_boxes |
| [edit-operations.txt](../../src/main/resources/ai/prompt/v1.1/edit-operations.txt) | INSERT·DELETE·REPLACE·RESTRUCTURE, replacement와 edit.text의 관계, 삽입 경계·공백과 예시 |

| 영역 | Standard | Advanced |
| --- | --- | --- |
| GRAMMAR_SPELLING | [grammar-spelling-standard.txt](../../src/main/resources/ai/prompt/v1.1/areas/grammar-spelling-standard.txt) | [grammar-spelling-advanced.txt](../../src/main/resources/ai/prompt/v1.1/areas/grammar-spelling-advanced.txt) |
| VOCABULARY_EXPRESSION | [vocabulary-expression-standard.txt](../../src/main/resources/ai/prompt/v1.1/areas/vocabulary-expression-standard.txt) | [vocabulary-expression-advanced.txt](../../src/main/resources/ai/prompt/v1.1/areas/vocabulary-expression-advanced.txt) |
| SENTENCE_STRUCTURE | [sentence-structure-standard.txt](../../src/main/resources/ai/prompt/v1.1/areas/sentence-structure-standard.txt) | [sentence-structure-advanced.txt](../../src/main/resources/ai/prompt/v1.1/areas/sentence-structure-advanced.txt) |
| CONTENT_SUMMARY | [content-summary-standard.txt](../../src/main/resources/ai/prompt/v1.1/areas/content-summary-standard.txt) | [content-summary-advanced.txt](../../src/main/resources/ai/prompt/v1.1/areas/content-summary-advanced.txt) |

지시문은 기존 v1과 같이 영어로 작성했다. 모델이 반환할 reason은 한국어 1~2문장, guidance는 영어 10단어 이내다.

## 2. v1에서 바뀐 판단 규칙

- Standard는 모든 일반 항목에서 CORRECTION만 반환한다. 올바른 표현에 더 나은 대안이 있다는 이유로 제안하지 않는다.
- Advanced는 같은 기본 오류를 먼저 검사한 뒤 문맥·정확성·관용성·뉘앙스·흐름에 의미 있는 개선을 SUGGESTION으로 추가한다. 오류를 놓치거나 선택적 개선으로 재분류하지 않는다.
- Advanced의 차이를 더 많은 개수·넓은 수정 범위·어려운 단어로 만들지 않는다. 개선이 없으면 추가 제안을 만들지 않는다.
- 영역별 레벨을 독립적으로 적용한다. 비활성 영역의 변경을 숨기지 않고, 혼합 SUGGESTION에 Standard 영역의 변경도 넣지 않는다.
- 두 레벨 모두 페이지·활성 대표 영역별 REPLACE와 GUIDANCE 합계 최대 7개다. 권장·목표·최소 개수는 없다. REVIEW는 제외한다.
- 필요한 CORRECTION을 먼저 선택하고, 남은 자리에 의미 있는 SUGGESTION을 둔다. 원문이 겹치는 중복·경쟁 제안은 만들지 않는다.
- 문법·어휘·기존 내용 수정은 WORD / PHRASE로 제한한다. 절·문장 구조 수정은 SENTENCE_STRUCTURE에서 다룬다.
- 문장 전체 교체는 RESTRUCTURE로 표시한다. Standard에서는 객관적 구조 오류를 작은 변경으로 해결할 수 없을 때만 사용한다.

## 3. 레벨 차이를 설명하는 예시

| 원문·상황 | Standard | Advanced |
| --- | --- | --- |
| `She go to school every day.` | `go → goes`, CORRECTION | 같은 국소 CORRECTION |
| `made homework` | `did homework`, CORRECTION | 같은 연어 CORRECTION |
| 올바른 `very good for me` | 고급 대안이 있다는 이유로 제안하지 않음 | 만족·성취라는 기존 문맥이 뒷받침할 때만 `highly rewarding` 같은 의미 있는 대안을 SUGGESTION으로 검토 |
| `I finished my homework. I took a shower. I went to bed.` | 구조 오류가 없으므로 제안 없음 | 원문의 순서·의미를 유지하는 국소적 결합이 흐름을 개선할 때만 하나의 SUGGESTION |
| `Volunteering is good for students.` | 근거를 더 쓰면 좋겠다는 이유만으로 제안하지 않음 | `Explain how volunteering benefits students.`라는 선택적 GUIDANCE 가능 |

예시는 판정의 차이를 설명하며 반환 개수나 무조건적인 교체 정답을 지정하지 않는다. 문맥이 부족하거나 의미·뉘앙스가 달라질 위험이 있으면 개선 제안을 생략한다.

## 4. 응답·표시 역할 분리

응답은 items와 12개 item 필드만 요구한다. `anchor`·`boxes` 대신 `anchors`·`target_boxes`를 사용하고, 여러 줄의 한 수정은 연속 anchors로 연결한다. 줄 수 자체를 언어적 범위로 취급하지 않는다.

INSERT는 기존 원문을 유지한 replacement와 추가분만 담은 edit.text를 구분한다. DELETE는 두 문자열을 비우고, REPLACE·RESTRUCTURE는 replacement와 edit.text를 정확히 같게 한다. 삽입 경계의 공백 규칙은 현재 [수정 동작 검증](AI-공통-응답-계약-v1.1-수정-동작-검증.md)에 맞춘다.

기획의 이미지 표시 요구를 모델에게 그대로 복사하여 색상·주석 ID·실제 클릭 좌표까지 생성하도록 하지 않는다. AI는 원문 위치 후보와 수정 데이터만 반환하고, 색상·도구·주석·레이아웃은 서버와 렌더러가 계약에 따라 생성한다. GUIDANCE에는 이미지 표시용 edit를 만들지 않는다.

## 5. 다음 조립 단계에서 연결할 것

예정된 조립 순서는 공통 원칙 → 선택 영역·독립 레벨 선언 → 활성 영역마다 해당 레벨의 상세 파일 → 응답 계약 → anchors·좌표 → 수정 동작이다.

선택한 영역에는 Standard 또는 Advanced 파일 하나만 포함하고 꺼진 영역의 상세 파일은 포함하지 않는다. 번호가 붙은 확정 전사문은 지시문 안에 삽입하지 않고 별도 입력 JSON으로 전달한다. 같은 페이지와 같은 전사·설정 스냅샷을 검증기에도 사용한다.

응답 Schema는 [v1.1 JSON Schema](../../src/main/resources/ai/contract/v1.1/correction-response.schema.json)를 연결한다. v1 조립기의 권장 3/5·최대 5/7 규칙을 그대로 복사하지 않고 v1.1의 두 레벨 최대 7개·권장 없음 정책을 따른다.

이번에는 이 조립기를 구현하지 않았다. 따라서 프롬프트 버전 상수·모델 설정·Schema 선택·실제 요청은 아직 변경되지 않았다. `AUTEDI-CORRECTION-PROMPT-v1.1`은 향후 조립기에서 사용할 프롬프트 버전명으로 둔다.

## 6. 확인 범위

파일 구성·UTF-8·필수 필드·enum·영역별 헤더·수정 동작 예시 JSON·안내문 길이를 정적으로 확인한다. Java 코드와 테스트 코드를 추가하지 않았으며 실제 API나 Gradle 테스트는 실행하지 않는다. 기존 계약 테스트 통과 이력과 이번 텍스트 검토는 구분한다.

조립기 구현 후 설정 조합별 파일 선택·혼입·스냅샷·Schema 연결을 테스트하고, 이후 실제 모델로 품질·규칙 준수·토큰·시간을 비교한다. 프롬프트 작성 자체는 모델이 항상 규칙을 지킨다는 보장이 아니므로 v1.1 서버 검증을 유지한다.
