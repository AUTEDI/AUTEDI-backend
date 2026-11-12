# AUTEDI AI 공통 응답 계약 v1.1

- 작성일: 2026-11-10 (한국 시간)
- 계약 식별자: `AUTEDI-AI-CONTRACT-v1.1`
- 상태: 기획 기준과 논의한 역할 분담을 반영한 **서비스 연동 설계안**
- 범위: AI 첨삭 응답, 서버 검증·저장, 프론트 조회·편집, 이미지 주석 표시
- 적용 상태: v1.1 고정 응답 예제, DTO·enum·JSON Schema, 독립 파서, 원문·좌표·수정 동작·영역·레벨·범위·안내문 검사와 개수 제한·겹침 경고·분류·집계를 추가하고 프롬프트 텍스트 파일을 작성했다. 프롬프트 조립기와 실제 생성·저장·조회·표시 연결, 모델 품질 평가는 후속 단계다. 현재 AI 호출·DB·API·프론트 동작은 v1을 유지한다.
- 호환성: v1.1은 필드 이름과 구조가 바뀌므로 v1 파서와 호환되지 않는다. 기존 결과와 v1 문서는 보존한다.

## 1. 기준 문서와 핵심 결정

기획의 첨삭 판단 기준과 결과 표시 요구는 다음 문서를 따른다.

- [Standard / Advanced 첨삭기준 한국어본](../../../자료/Standard%20Advanced%20첨삭기준%20한국어본.txt)
- [첨삭결과표시 한국어본](../../../자료/첨삭결과표시%20한국어본.txt)
- [기존 AI 공통 응답 계약 v1](AI-공통-응답-계약-v1.md)
- [v1.1 DTO·JSON Schema 구현 범위](AI-공통-응답-계약-v1.1-DTO-Schema.md)
- [v1.1 파서 구현 범위](AI-공통-응답-계약-v1.1-파서.md)
- [v1.1 원문·좌표 검증 구현 범위](AI-공통-응답-계약-v1.1-원문-좌표-검증.md)
- [v1.1 수정 동작 검증 구현 범위](AI-공통-응답-계약-v1.1-수정-동작-검증.md)
- [v1.1 영역·레벨·개수 검증 구현 범위](AI-공통-응답-계약-v1.1-영역-레벨-개수-검증.md)
- [v1.1 프롬프트 파일 작성 범위](AI-첨삭-프롬프트-v1.1-파일.md)
- [v1.1 압축 프롬프트 비교 후보](AI-첨삭-프롬프트-v1.1-압축본.md)

이 문서의 데이터 구조와 생성 주체는 기획 요구를 현재 서비스에 연결하기 위한 설계 결정이다. 기획에서 모든 필드를 AI가 생성하도록 요구한다고 해석하지 않는다.

1. Standard는 객관적 오류만 `CORRECTION`으로 반환한다.
2. Advanced는 같은 기본 오류 검사 후 의미 있는 개선만 `SUGGESTION`으로 추가한다.
3. 두 레벨 모두 페이지·활성 영역별 일반 첨삭 최대 7개이며 목표·권장 개수는 없다.
4. AI는 수정 판단·정확한 원문 연결·원문 위치 후보·표시할 문구를 반환한다.
5. **색상과 주석 ID는 AI에게 받지 않는다. 서버가 유형과 저장 ID로 만든다.**
6. 원문 교체용 `replacement`와 이미지 표시용 `edit.text`를 구분한다.
7. `anchor`를 `anchors[]`로 확장하여 여러 전사 줄에 걸친 하나의 수정 범위를 연결한다.
8. 원문 좌표 `target_boxes`와 렌더링한 첨삭의 좌표를 구분한다.
9. 프론트가 실제 표시·문구 배치 후 클릭·강조 영역을 계산한다. 서버 출력물은 출력 렌더러가 같은 역할을 한다.
10. 원본 PNG·확정 전사문은 보존하고 교정 내용을 자동으로 덮어쓰지 않는다.

첨삭결과표시 영어본에는 한국어본의 일부 영역별 수정 범위 제한이 빠져 있다. 실제 프롬프트를 조립할 때는 이 문서의 제한을 공통 적용하고 두 언어 문서를 맞춘다.

## 2. 전체 데이터 흐름과 역할

```text
프론트: 이미지 업로드 + 영역별 area / level 설정
    ↓
서버: 원본 보존 → 방향 보정 PNG 생성 → 생성 시점 설정 스냅샷 고정
    ↓
AI 전사: 같은 PNG → {"text": "오류와 줄바꿈을 보존한 학생 원문"}
    ↓
서버: 전사 저장, line_id 부여
    ↓
AI 첨삭: 같은 PNG + 번호가 붙은 전사문 + 영역별 규칙 → v1.1 items
    ↓
서버: 원문·좌표·설정·수정 동작 검증 → 개수 제한 → 저장
    ↓
서버: ID·색상·기본 표시 도구를 붙인 검토 API 응답
    ↓
프론트: 표시 도형·문구 배치 → 필요 시 여백·화살표 → 클릭·선택 강조
```

| 주체 | 생성·처리하는 정보 |
| --- | --- |
| 프론트 입력 | 이미지, `areas[].area`, `areas[].level`, 교사 편집·채택 정보 |
| AI | `items`의 첨삭 판단, 원문 구간, 교체문·안내문, 수정 동작·표시 문구, 원문 위치 후보 |
| 서버 | 원문 위치 검증, UTF-16 인덱스, 설정 준수, ID, 색상, 기본 도구, 저장·조회 응답 |
| 프론트 렌더러 | 실제 첨삭 위치, 글자 크기·줄바꿈, 화살표, 클릭 영역, 일시적인 노란색 선택 강조 |
| 출력 렌더러 | 이미지·PDF 등 출력물의 동일한 첨삭 표시. 선택 강조는 출력하지 않음 |

### 2.1 요청에서 사용하는 값

- 이미지 업로드와 영역별 설정 API의 기본 입력 형태는 유지한다.
- 생성 요청은 저장된 이미지와 설정을 사용한다. 임의의 프론트 프롬프트 문자열을 AI 지시문으로 사용하지 않는다.
- 첨삭 단계에는 `page_number`, `transcription_lines[].line_id/text`와 같은 PNG를 전달한다.
- `area/level`은 생성 당시 스냅샷을 기준으로 프롬프트와 검증기에 동일하게 적용한다.
- 교사가 편집한 `content`, 표시 위치, `isApplied` 변경은 저장·재배치로 처리하며 AI를 자동 재호출하지 않는다.
- 사용자·학원 식별자, 인증 토큰, `If-Match`, `Idempotency-Key`는 서버가 처리하며 AI 응답 필드로 요구하지 않는다.
- 현재 실제 생성 경로의 단일 페이지 이미지 범위를 유지한다. `anchors[]`는 **같은 페이지 안의 여러 줄**을 뜻하며 여러 페이지 AI 생성을 추가하는 기능은 아니다.

학생 수준, 글의 목적·독자, 목표 영어 변형을 별도로 지정하는 기능은 현재 요청 DTO에 없다. 향후 입력받으면 선택 문맥으로 제공하고 생성 스냅샷에 보존한다. 지정되지 않은 학생 수준·독자·과제 요구를 만들어 내지 않으며 널리 통용되는 현대 표준 영어를 기준으로 판단한다.

## 3. 첨삭 판단 정책

### 3.1 레벨과 유형

| 항목 | Standard | Advanced |
| --- | --- | --- |
| 목적 | 객관적 오류 수정 | 오류 수정 후 문맥·정확성·자연스러움의 깊이 있는 개선 |
| 일반 항목의 `feedback_type` | `CORRECTION`만 허용 | `CORRECTION`, `SUGGESTION` |
| 맞지만 개선 가능한 표현 | 반환하지 않음 | 개선 효과가 분명할 때만 제안 |
| 기본 오류 검사 | 수행 | Standard와 같은 검사를 먼저 수행 |
| 목표·권장 개수 | 없음 | 없음 |
| 페이지·활성 영역별 최대 | 7개 | 7개 |

- 레벨은 학생의 실력 등급이나 수정 개수가 아니다.
- 스타일 취향 때문에 올바른 표현을 오류로 분류하지 않는다.
- Advanced의 추가 가치는 오류 수정의 과장이 아니라 의미 있는 선택적 개선에서 나온다.
- Advanced에서 Standard가 찾을 기본 오류를 누락하지 않도록 검사한다. 단일 생성 요청에서 두 레벨을 각각 유료 호출하는 방식은 요구하지 않는다.
- 단순 동의어 교체, 불필요하게 어려운 단어, 과장된 문체, 억지 관용구를 제안하지 않는다.
- 의미·사실·의도·학생의 목소리를 보존한다. 개선 근거가 불확실하거나 뉘앙스가 달라질 수 있으면 제안하지 않는다.
- 오류와 의미 있는 개선이 없으면 `{"items":[]}`로 정상 성공할 수 있다.

### 3.2 영역과 수정 범위

| `area` | 검사 내용 | 허용 `revision_scope` |
| --- | --- | --- |
| `GRAMMAR_SPELLING` | 문법·철자·대문자·문장부호. Advanced는 시제 일관성, 동사·조동사, 수식어, 대명사, 문장부호의 관계까지 검토 | `WORD`, `PHRASE` |
| `VOCABULARY_EXPRESSION` | 단어 선택·연어·표현. Advanced는 정확성·관용성·격식·어조·뉘앙스·목적 있는 표현 변화까지 검토 | `WORD`, `PHRASE` |
| `SENTENCE_STRUCTURE` | 불완전 문장·잘못 연결된 절·어순. Advanced는 흐름·전환·문장 다양성·필요한 국소적 분리·결합까지 검토 | 모든 범위 |
| `CONTENT_SUMMARY` | 의미 명확성·필수 설명 누락·관련성·반복. Advanced는 주장·근거·논리 전개·간결성·설득력까지 검토 | 기존 내용의 작은 수정은 `WORD`, `PHRASE`; 안내는 `null` |

- 문법·어휘 영역은 완성된 문장을 다시 쓰거나 문장 전체에 밑줄을 긋지 않는다.
- 절·문장 범위의 교체는 구조 자체가 수정 대상일 때 문장 구조 영역에서만 허용한다.
- Standard의 문장 전체 재작성은 객관적 구조 오류를 부분 수정으로 명확하게 해결할 수 없을 때만 허용한다.
- Advanced도 문장 전체를 바꾸는 것 자체를 목표로 하지 않는다. 작은 수정으로 같은 효과를 얻을 수 있는지 먼저 확인한다.
- 문단·에세이 전체를 대신 작성하지 않는다. 사실·경험·이유·예시·근거를 새로 만들지 않는다.
- 꺼진 영역의 수정을 다른 대표 영역이나 혼합 항목에 숨기지 않는다.

### 3.3 동작과 문제 종류

| `action` | 의미 |
| --- | --- |
| `REPLACE` | 원문 수정 제안. 실제 삽입·삭제·교체·구조 변경은 `edit.operation`으로 구분 |
| `GUIDANCE` | 학생이 직접 이유·예시·근거 등을 보충하도록 안내. 원문 교체에 사용하지 않음 |
| `REVIEW` | 판독·OCR·의도·이미지 문제로 교사 확인 필요. 일반 수정으로 확정하지 않음 |

`issue_kinds`는 최소 1개, 중복 없는 배열이다.

| 용도 | 값 |
| --- | --- |
| 일반 첨삭 | `grammar`, `spelling`, `punctuation`, `vocabulary`, `sentence_structure`, `content` |
| `REVIEW` | `ocr_mismatch`, `illegible`, `unclear_intent`, `image_problem` |

일반 항목은 문제 종류와 맞는 활성 영역 하나를 대표 영역으로 선택한다. 문법·철자·문장부호는 `GRAMMAR_SPELLING`, 어휘는 `VOCABULARY_EXPRESSION`, 구조는 `SENTENCE_STRUCTURE`, 내용은 `CONTENT_SUMMARY`에 대응한다. 여러 문제 종류가 있어도 비활성 영역의 변경을 포함하면 안 된다.

`GUIDANCE`는 `CONTENT_SUMMARY`에서만 허용한다. Standard에서는 이해에 반드시 필요한 설명의 명백한 누락이나 확인 가능한 필수 요구의 누락만 `CORRECTION`으로 안내한다. 단지 이유를 더 풍부하게 쓰면 좋겠다는 제안은 Advanced의 `SUGGESTION`이다.

## 4. AI 응답 계약

최상위는 `items` 배열만 갖는 JSON 객체다. 아래 12개 필드를 모든 항목에 포함한다. 해당하지 않는 객체·문자열은 명시적으로 `null`, 배열은 `[]`를 사용한다. 알려지지 않은 필드와 Markdown·API 응답 봉투는 허용하지 않는다.

| 필드 | 형식·규칙 |
| --- | --- |
| `action` | `REPLACE` / `GUIDANCE` / `REVIEW` |
| `feedback_type` | 일반 항목은 `CORRECTION` / `SUGGESTION`; `REVIEW`는 `null` |
| `area` | 활성 대표 영역; `GUIDANCE`는 `CONTENT_SUMMARY`; `REVIEW`는 `null` |
| `issue_kinds` | 중복 없는 문제 종류 배열 |
| `anchors` | 원문 연결 객체 배열. 일반 항목은 1개 이상; 위치 없는 `REVIEW`만 `[]` |
| `replacement` | 원문 범위 전체를 교체할 정확한 영어 문자열. 삭제는 `""`; 다른 action은 `null` |
| `guidance` | 영어 10단어 이내 내용 보완 안내. `GUIDANCE` 외에는 `null` |
| `reason` | 존중하는 한국어 1~2문장. 무엇을 왜 고치는지 또는 무엇을 확인할지 설명 |
| `location_uncertain` | 모델이 원문 이미지 위치를 불확실하게 판단했는지 나타내는 boolean |
| `target_boxes` | 원문 위치 후보. 전체 보정 PNG 기준 `[left, top, right, bottom]` 목록 |
| `revision_scope` | `WORD` / `PHRASE` / `CLAUSE` / `SENTENCE` / `SENTENCE_GROUP`; `GUIDANCE`, `REVIEW`는 `null` |
| `edit` | 수정 동작·표시 문구 객체. `REPLACE` 외에는 `null` |

모델에게 `color`, `category`, `annotation_id`, DB ID, `level`, `start/end`, 픽셀 좌표, 실제 클릭·강조 좌표, 확신 점수를 요구하지 않는다. 색상은 7절의 서버 매핑으로 생성한다.

### 4.1 action별 조합

| 필드 | REPLACE | GUIDANCE | REVIEW |
| --- | --- | --- | --- |
| `feedback_type` | 레벨에 맞는 유형 | 레벨에 맞는 유형 | `null` |
| `area` | 활성 영역 | 활성 `CONTENT_SUMMARY` | `null` |
| `anchors` | 1개 이상 | 관련 원문 1개 이상 | 찾을 수 있으면 제공, 없으면 `[]` |
| `replacement` | 문자열, 삭제는 `""` | `null` | `null` |
| `guidance` | `null` | 비어 있지 않은 영어 안내 | `null` |
| `revision_scope` | 영역에 맞는 값 | `null` | `null` |
| `edit` | 필수 | `null` | `null` |
| 일반 첨삭 개수 | 포함 | 포함 | 제외 |

위치 없는 `REVIEW`는 `anchors=[]`, `target_boxes=[]`, `location_uncertain=true`로 반환한다. 일반 항목의 원문을 특정할 수 없으면 정상 첨삭을 추측하지 않고 `REVIEW`로 반환한다.

`GUIDANCE`의 단어는 영숫자 단어 단위로 세고 내부 아포스트로피·하이픈은 한 단어로 센다. 숫자 토큰도 한 단어이며 빈 안내나 영어 외 문자 언어로 된 안내는 검토 대상으로 분리한다. `SUGGESTION`의 `reason`에는 원문도 허용되며 선택적 개선이라는 점을 설명한다.

### 4.2 anchors: 첨삭 하나가 연결된 원문 목록

```json
[
  {"line_id": 1, "original": "I finished my homework.", "occurrence": 1},
  {"line_id": 2, "original": "I took a shower.", "occurrence": 1}
]
```

- 각 원소는 v1의 anchor와 같은 구조다. `line_id`는 1부터 시작하며 빈 줄을 포함한 확정 전사의 번호를 사용한다.
- `original`은 해당 줄에 존재하는 정확하고 비어 있지 않은 부분문자열이다. 철자·대소문자·문장부호·공백을 임의로 정규화하지 않는다.
- `occurrence`는 그 문자열의 겹치지 않는 등장 순서이며 1부터 시작한다.
- 단어 하나만 수정하면 원소 하나를 담는다. 여러 원소여도 하나의 item·첨삭 카드다.
- 여러 원소는 같은 페이지·같은 전사 버전의 **연속된 원문 범위 하나**만 나타낸다. 무관한 부분들을 모아 하나의 교체문으로 덮어쓰지 않는다.
- 한 줄에는 원소 하나만 사용한다. 여러 줄일 때는 줄 번호가 연속되어야 한다. 첫 원소는 해당 줄 끝까지, 마지막 원소는 해당 줄 처음부터 포함하며 중간 줄은 전체를 포함한다.
- 문단 경계의 빈 줄을 넘는 합치기·교체는 이 계약에서 허용하지 않는다. 빈 줄 번호 자체를 재부여하거나 삭제하지 않는다.
- 서버가 선택된 부분들을 `\n`으로 연결해 정확한 `originalText`를 만든다. 모든 구간을 검증한 뒤 하나의 연속 범위로 다룬다. 부분 구간만 교체하지 않는다.
- 줄 수와 언어적 수정 범위는 다르다. 짧은 구가 두 줄에 걸쳐 적혔다면 `anchors`는 두 개여도 `revision_scope=PHRASE`일 수 있다.
- `GUIDANCE`는 관련 구절에 연결하며 글 전체 안내는 마지막 관련 구절 하나를 사용한다.
- 서버가 원소마다 줄 안의 0 기반 UTF-16 `start/end`를 계산한다. `end`는 미포함이다. 모델은 인덱스를 반환하지 않는다.

### 4.3 edit와 replacement의 차이

`replacement`는 선택한 원문 범위를 바꾸었을 때 들어갈 문자열이고, `edit.text`는 이미지에 적을 수정 문구다. 자동 적용 여부와는 무관한 제안 데이터다.

`edit`에는 `operation`, `text`, `position` 세 필드를 모두 포함한다.

| `edit.operation` | `replacement` | `edit.text` | `edit.position` |
| --- | --- | --- | --- |
| `INSERT` | 인접 원문과 새 요소가 함께 들어간 교체 문자열 | 추가할 요소만 | `BEFORE` / `AFTER` |
| `DELETE` | `""` | `""` | `null` |
| `REPLACE` | 바꿀 단어·구·절 | `replacement`와 동일 | `null` |
| `RESTRUCTURE` | 완성된 수정 문장 또는 국소적인 문장 묶음 | `replacement`와 동일 | `null` |

- `INSERT`는 삽입점 바로 옆의 가장 작은 기존 원문을 anchor로 잡는다. 예: `dog` 앞에 관사를 넣으면 `replacement="a dog"`, `edit.text="a"`, `position="BEFORE"`이다.
- `INSERT`에서 기존 원문은 그대로 남아야 한다. 서버는 지정된 앞·뒤에 표시 문구만 추가되었는지 검사한다. 단어 경계에 필요한 공백은 교체문에 포함하고 표시 문구에는 불필요한 앞뒤 공백을 넣지 않는다. 문장부호 삽입에도 같은 원칙을 적용한다.
- 삽입점이 기준 원문의 중간이라면 anchor를 더 좁혀 경계를 정확히 지정한다. 불가능하면 `INSERT`로 추측하지 말고 필요한 최소 범위의 `REPLACE`를 사용한다.
- `DELETE`는 삭제할 원문만 선택한다. 원문이 없는 빈 anchor는 허용하지 않는다.
- `REPLACE`는 비어 있지 않은 교체문을 반환한다. 전체 문장 교체는 `RESTRUCTURE`로 명시한다.
- `RESTRUCTURE`는 `SENTENCE_STRUCTURE`의 `SENTENCE` / `SENTENCE_GROUP`에만 허용하며 문장 전체 밑줄을 사용한다.
- 문장 구조 영역의 절 일부 교체는 `revision_scope=CLAUSE`, `operation=REPLACE`로 표현하고 절에만 취소선을 사용한다.
- 잘못된 위치의 단어·구를 옮길 때에는 필요한 최소 연속 원문과 교체문을 사용한다. 이동 전용 다섯 번째 첨삭 기호는 추가하지 않는다.
- 교체문과 표시 문구가 서로 다른 수정이나 서로 다른 주장을 뜻하면 정상 카드로 공개하지 않는다.

### 4.4 target_boxes: 원문 좌표

```text
[[left, top, right, bottom], ...]
```

- 두 AI 요청과 화면이 공유하는 같은 EXIF 보정 PNG 전체가 기준이다.
- 원점은 좌상단, x는 오른쪽, y는 아래쪽이며 모든 숫자는 유한해야 한다.
- `0 <= left < right <= 1`, `0 <= top < bottom <= 1`을 만족해야 한다.
- 선택한 원문 글씨를 감싸며 첨삭 문구·여백·노란색 강조 위치를 반환하지 않는다.
- 여러 박스는 읽기 순서로 제공한다. anchors 개수와 박스 개수가 같을 필요는 없다.
- 원문의 신뢰할 만한 위치를 찾지 못하면 `[]`와 `location_uncertain=true`로 반환한다.
- 서버는 임의로 좌표를 자르거나 뒤집어 보정하지 않는다. 잘못된 박스는 원본에 보존하고 표시용 목록에서 제외한다.

## 5. 서버 검증과 항목 수

검증 순서는 구조 → 동작별 필드 → 설정·범위 → 원문 연결 → 수정문 일관성 → 좌표 → 중복·겹침 → 개수 제한 → 화면 정렬이다.

| 검사 | 처리 |
| --- | --- |
| JSON 구조·필수 필드·enum 불일치 | 원본·실패 사유 보존. 응답 전체 구조를 해석할 수 없으면 생성 실패 |
| 비활성 영역 또는 해당 영역의 수정 우회 | 정상 카드에서 제외, 후보와 사유 보존 |
| Standard의 `SUGGESTION` | 정상 카드에서 제외, 후보와 위반 사유 보존. 임의로 `CORRECTION`으로 바꾸지 않음 |
| 문법·어휘의 문장 범위, 범위와 operation 불일치 | 정상 카드에서 분리하여 확인 필요 목록에 제공 |
| anchors 일부 불일치·불연속·다른 전사 버전 | 전체 항목을 확인 필요 목록으로 분리. 맞는 구간만 부분 적용하지 않음 |
| edit와 replacement 불일치 | 확인 필요 목록으로 분리 |
| 일부 좌표 오류 | 유효한 좌표만 사용하고 경고 저장 |
| 원문은 검증됐지만 좌표가 없거나 불확실함 | 텍스트 카드 보존, 위치 검토 경고. 확인 전 이미지 주석 자동 배치 금지 |
| 원문 범위가 겹치는 일반 제안 | 관련 카드에 경고하고 자동 적용하지 않음. AI는 중복·충돌 제안을 만들지 않음 |
| `REVIEW` | 일반 카드와 별도 보존·조회 |
| `items=[]` | 전사가 정상이라면 첨삭 없는 정상 성공 |

- 규칙 위반·분리·제한 초과 후보와 최초 AI 응답을 삭제하지 않는다.
- 개별 항목의 문제로 AI를 자동 재호출하지 않는다.
- 서버의 구조 검증만으로 객관적 오류 여부·의미 보존·문장 범위를 완전히 증명할 수는 없다. `revision_scope`의 자기 신고만으로 통과시키지 않고 원문·수정문과 함께 확인하며 품질 평가와 교사 검토를 유지한다.
- 모델의 `location_uncertain`과 서버의 `requiresReview`를 구분한다. 경고가 없다는 것이 첨삭의 정답을 보장하지 않는다.

### 5.1 최대 7개와 정렬

1. 페이지·활성 대표 영역별로 `REPLACE`와 `GUIDANCE`를 합쳐 최대 7개다.
2. `anchors`, 문제 종류, 박스 수와 관계없이 item 하나를 1개로 센다. `REVIEW`는 제외한다.
3. 권장·목표·최소 개수는 없다. Advanced도 개선을 억지로 만들지 않는다.
4. AI는 같은 영역에서 중요한 `CORRECTION`을 먼저, 그다음 중요한 `SUGGESTION`을 반환한다.
5. 서버는 검증을 통과한 후보 중 `CORRECTION`을 우선하여 최대 7개를 선택하고 남는 자리에 `SUGGESTION`을 넣는다. 유형 안에서는 AI 반환 순서를 유지한다.
6. 최종 카드 목록은 원문 위치 순서로 정렬한다. 제외 후보는 별도로 보존한다.
7. 한도보다 오류가 많으면 중요한 오류부터 반환·표시한다. Advanced 제안이 기존 오류 수정을 밀어내지 않도록 한다.

## 6. 서버에서 프론트로 변환하는 필드

AI 응답은 snake_case, 서비스 DTO는 camelCase를 사용한다.

| AI 또는 요청 정보 | v1.1 프론트 필드 | 변환 규칙 |
| --- | --- | --- |
| 서버 저장·요청 문맥 | `id`, `etag`, `generationId`, `source`, `contractVersion` | AI에게 받지 않음 |
| 생성 설정 스냅샷 | `area`, `level` | area는 AI 결과를 검증, level은 서버 스냅샷으로 제공 |
| `action`, `feedback_type`, `issue_kinds` | `action`, `feedbackType`, `issueKinds` | 분류값 유지, 키 이름 변환 |
| `anchors` | `originalText`, `anchors[]` | 연속 원문 보존 및 서버 계산 위치 제공 |
| `replacement` 또는 `guidance` | `aiInitialContent`, `content` | 최초값은 보존, 현재값은 교사 편집 가능 |
| `reason` | `explanation` | 최초 한국어 설명 보존 |
| `revision_scope`, `edit` | `revisionScope`, `edit` | 최초·현재 수정 데이터를 구분해 보존 |
| 검증된 `target_boxes` | `targetRegions[]` | 페이지 ID를 붙이고 x/y/width/height로 변환 |
| `location_uncertain` 및 서버 검사 | `locationUncertain`, `warnings`, `requiresReview` | 원본 판단과 검증 결과 구분 |
| 서버 유형·저장 ID | `annotation` | ID·색상·기본 도구·현재 표시 문구 |
| 렌더러 결과 | `annotationLayout` | 최초에는 `null`, 실제 배치 후 생성·선택 저장 |

`anchors[]`의 조회 원소는 다음 형태다. `start/end`는 각 줄 안의 UTF-16 인덱스다.

```json
{
  "transcriptionId": 301,
  "lineId": 1,
  "occurrence": 1,
  "start": 7,
  "end": 10
}
```

좌표 변환은 `x=left`, `y=top`, `width=right-left`, `height=bottom-top`이며 검증된 `pageId`를 명시한다. 페이지의 `width/height`는 PNG 픽셀 수이고 `targetRegions`의 단위는 `NORMALIZED_0_1`이다.

기존 관리 필드 `editedByTeacher`, `explanationStale`, `isApplied`, `status`, `createdAt`, `updatedAt`는 유지한다. `isApplied`는 채택 여부이며 선택 하이라이트 상태가 아니다.

## 7. 색상·표시 도구·주석 메타데이터

### 7.1 색상은 서버가 생성

| 검증된 `feedbackType` | `annotation.category` | `annotation.color` |
| --- | --- | --- |
| `CORRECTION` | `error` | `red` |
| `SUGGESTION` | `improvement` | `blue` |

- `annotationId`는 서버의 첨삭 ID에서 생성한다. 예: `cor_201`.
- 같은 수정 대상에 빨간색·파란색을 중복 표시하지 않는다. 겹치는 후보는 카드와 경고를 보존하되 충돌이 해결되기 전 같은 대상의 이미지 주석을 동시에 자동 표시하지 않는다. 교사가 사용할 제안을 선택하거나 충돌을 수정한 뒤 배치한다.
- 노란색은 선택 상태에만 사용하는 프론트 효과이며 AI 첨삭 색상값으로 받거나 영구 저장하지 않는다.
- `REVIEW`에는 학생 오류를 뜻하는 빨강·파랑 첨삭 주석을 만들지 않는다.

### 7.2 기본 표시 도구와 화살표

| 수정 | `annotation.baseType` | 표시 내용 |
| --- | --- | --- |
| `INSERT` | `caret` | 정확한 삽입점에 `^`, 가까운 곳에 추가할 요소만 |
| `DELETE` | `strikethrough` | 삭제 대상만 취소선, 새 문구 없음 |
| `REPLACE` | `strikethrough` | 대상 단어·구·절만 취소선, 가까운 곳에 교체 표현만 |
| `RESTRUCTURE` | `underline` | 대상 문장 전체 밑줄, 완성된 수정 문장 |

- 프론트는 원문·다른 첨삭을 가리지 않는 가까운 빈 공간을 우선 사용한다.
- 공간이 부족할 때만 왼쪽·오른쪽 여백에 문구를 배치하고 화살표로 연결한다.
- 기본 기호가 무엇인지 `baseType`에 보존한다. `annotation.type`은 최종 표시 도구로 `caret`, `strikethrough`, `underline`, `arrow` 중 하나다.
- 레이아웃 전에는 `type=baseType`, 레이아웃 후에는 검증된 `annotationLayout.type`을 따른다. 화살표 배치에서도 기본 기호·연결선·수정문은 하나의 주석 그룹이다.
- 화살표는 문장 구조 변경을 뜻하지 않는다. 삽입·교체·문장 수정 모두 공간 부족 시 사용할 수 있는 배치 방식이다.
- 그 외 첨삭 기호·영구 하이라이트를 추가하지 않는다.

### 7.3 GUIDANCE 표시 정책

기획은 `GUIDANCE`를 요구하지만 이를 네 도구 중 무엇으로 이미지에 표시할지는 정하지 않았다. **v1.1 기본안은 내용 보완 카드로 제공하고 `annotation=null`, `annotationLayout=null`로 둔다.**

안내를 원문 교체문으로 사용하거나 근거를 대신 작성하지 않는다. 안내 카드는 `feedbackType`에 따라 오류·개선을 구분한다. 이미지에도 안내를 배치하는 정책은 기획 확인 후 별도 정의하며, 문장 밑줄로 자동 변환하지 않는다. 이는 본 계약의 명시적인 추가 기획 확인 항목이다.

## 8. 클릭·하이라이트와 레이아웃 계약

### 8.1 실제 좌표의 생성 주체

기획의 `target_bbox`는 서버가 검증한 `targetRegions[]`에, `correction_bbox`는 렌더러가 계산한 `annotationLayout.correctionRegions[]`에 대응한다. 여러 줄·분리된 기호·문구를 지원하기 위해 배열을 사용한다.

AI는 실제 첨삭 글씨를 이미지에 그린 결과를 반환하지 않는다. 글꼴·글자 크기·문구 줄바꿈·화살표를 확정한 렌더러가 첨삭 표시 좌표를 계산한다. API 최초 응답에서 `annotationLayout=null`은 **아직 배치되지 않음**을 뜻한다. 임의의 클릭 좌표를 만들어 보내지 않는다.

| 레이아웃 필드 | 의미 |
| --- | --- |
| `type` | 최종 도구. `caret`, `strikethrough`, `arrow`, `underline` |
| `marks[]` | 실제 새로 그린 기호·선. 각 원소는 도구 `type`과 0~1 정규화 `points: [[x,y], ...]`를 가짐 |
| `textRegions[]` | 현재 수정 문구를 실제로 그린 영역. 삭제처럼 문구가 없으면 `[]` |
| `correctionRegions[]` | 기호·연결선·수정 문구의 영역들. 원문 연결용 영역과 구분 |
| `hitRegions[]` | 선택을 쉽게 하도록 여유를 둔 클릭 영역. 인접 첨삭·문장과 겹치지 않게 계산 |

모든 사각형은 보정 PNG 전체 기준 `x/y/width/height`, 모든 점은 같은 이미지 기준 0~1 정규화 값이다. 주석의 ID·색상·문구는 `annotation`을 사용하여 레이아웃에 중복 저장하지 않는다. 선 굵기·글꼴·화살촉 등 공통 스타일은 렌더러의 일관된 스타일 규칙을 따른다.

### 8.2 상호작용

- 기호·연결선·수정 문구를 `annotationId`로 한 그룹에 묶는다.
- 선택 시 그 그룹의 **새로 그린 표시와 수정 문구만** 반투명 노란색으로 강조한다. 학생 원본 글씨는 강조하지 않는다.
- `targetRegions`를 노란색 채우기 영역으로 사용하지 않는다.
- 사각형이 원문이나 빈 공간까지 포함할 수 있으므로 `correctionRegions`의 큰 외접 사각형을 통째로 칠하지 않는다. 실제 기호 경로·수정 텍스트를 대상으로 강조한다.
- 한 번에 하나만 선택한다. 다른 첨삭·빈 곳을 누르면 기존 선택을 해제한다.
- 선택 ID는 화면의 일시적 상태다. 기본 PNG·출력 이미지·PDF에 노란색을 굽지 않는다.
- 클릭을 지원하지 않는 출력에서도 첨삭·주석 데이터는 제공하되 선택 강조는 그리지 않는다.

### 8.3 배치 저장과 재계산

- 최초 자동 배치는 프론트에서 계산할 수 있다. 교사가 이동·조절한 배치를 재조회·출력에 유지하려면 서버에 `annotationLayout`을 저장한다.
- 레이아웃 저장은 현재 첨삭의 `If-Match`를 검사한다. 다른 페이지·범위를 벗어난 좌표·잘못된 도구·비유한 숫자는 거부한다.
- 원문 연결과 표시 위치를 서로 덮어쓰지 않는다. `targetRegions` 편집과 `annotationLayout` 편집은 별개다.
- 교사가 수정 내용·수정 동작·원문 연결·위치를 바꾸면 기존 레이아웃을 무효화하고 재계산한다. 이미지 또는 렌더링 스타일이 바뀐 경우도 재계산한다.
- `isApplied` 변경이나 클릭 선택만으로 AI를 호출하지 않는다.
- 원문 위치가 불확실하면 교사가 확인하기 전 임의 위치에 자동 주석을 그리지 않는다.

## 9. 조회·편집·저장 계약

### 9.1 검토 화면 조회

현재 `ApiResponse.result`와 검토 응답 묶음은 유지한다.

| 목록 | v1.1 내용 |
| --- | --- |
| `worksheet` | 학습지·페이지 이미지 URL·PNG 크기·설정 |
| `generation` | 생성 작업 상태·요청 설정 스냅샷 |
| `corrections` | 검증한 `REPLACE`, `GUIDANCE`와 서버가 만든 표시 메타데이터 |
| `comments` | 교사가 작성한 코멘트 |
| `reviewIssues` | 모델 REVIEW 및 정상 카드로 사용할 수 없는 검증 문제, 원본 후보·사유 |
| `summary` | 영역별 `level`, `maximumCount`, `validatedCount`, `displayedCount`, `omittedCount` |
| `aiResults` | 결과 출처·계약·프롬프트·전사 버전 연결 |

- 활성 영역의 `maximumCount`는 7, 비활성 영역은 0이다. v1.1에는 `recommendedCount`를 두지 않는다.
- `validatedCount`는 최초 검증·제한 후 일반 카드 수, `displayedCount`는 현재 삭제되지 않은 카드 수다.
- `omittedCount`는 반환된 일반 후보 중 제외·분리된 수다. REVIEW 자체는 제외하며 모델이 발견하지 못한 오류 수를 의미하지 않는다.
- 최초 모델 응답 전문을 일반 조회 API에 그대로 내보내지 않는다. 확인 필요 목록에는 해당 원본 후보와 검증 사유를 제공한다.

### 9.2 편집 요청

기존 `content`, `isApplied`의 역할을 유지하고 `edit`, `targetRegions`, `annotationLayout`을 구분하여 추가한다.

- `content`가 원문 교체안인지 안내문인지는 `action`으로 판단한다.
- `REPLACE`의 의미 변경 요청은 현재 `content`와 `edit`를 함께 보내고 서버가 일관성을 검사한다. 삭제는 `content=""`, `operation=DELETE`, `edit.text=""`다.
- `GUIDANCE`는 `content`만 수정하며 빈 안내와 `edit`·이미지 주석의 추가를 허용하지 않는다.
- 표시 문구만 수정해 실제 교체안과 다른 결과를 만들 수 없게 한다. 내용이 바뀌면 레이아웃을 재계산한다.
- `targetRegions` 변경은 연결 위치 보정이며 원문 문자 범위 자체의 재지정을 뜻하지 않는다.
- 좌표·채택 여부만 바꿀 때는 `content/edit`를 생략할 수 있다. 레이아웃을 명시적으로 비우는 요청과 필드 생략을 구분한다.
- API의 PATCH에서 명시적 `null`은 `annotationLayout` 제거에만 사용한다. 다른 편집 필드는 생략이면 유지하고 `null`은 거부한다. `isApplied=false`, `targetRegions=[]`, 삭제용 `content=""`는 유효한 명시 값이다.
- 원문 연결, 최초 `aiInitialContent`, 최초 AI edit·좌표·설명·응답은 편집으로 덮어쓰지 않는다.
- 교사 편집 후 `editedByTeacher`와 기존 설명의 진부화 여부를 기존 원칙에 따라 제공한다.

### 9.3 저장할 정보

원본 이미지, 보정 PNG와 해시, 확정 전사 버전, 설정 스냅샷, 계약·프롬프트·검증 버전, 최초 AI 응답, 검증·제외 사유, 최초 수정 동작과 제안, 교사 편집본과 레이아웃을 구분해 보존한다. `REVIEW`는 교사 `Comment`나 정상 `Correction`에 억지로 넣지 않는다.

## 10. 응답 예시

아래 값·ID·좌표는 계약 설명용 가상 예시다. 실제 모델 실행 결과가 아니다. 모든 AI 예시의 item은 12개 공통 필드를 포함한다.

### 10.1 Standard: 관사 삽입

입력 전사: 1번 줄 `I have dog.`

```json
{
  "items": [
    {
      "action": "REPLACE",
      "feedback_type": "CORRECTION",
      "area": "GRAMMAR_SPELLING",
      "issue_kinds": ["grammar"],
      "anchors": [{"line_id": 1, "original": "dog", "occurrence": 1}],
      "replacement": "a dog",
      "guidance": null,
      "reason": "단수 가산명사 dog 앞에 관사 a가 필요합니다.",
      "location_uncertain": false,
      "target_boxes": [[0.30, 0.20, 0.36, 0.23]],
      "revision_scope": "WORD",
      "edit": {"operation": "INSERT", "text": "a", "position": "BEFORE"}
    }
  ]
}
```

원문 교체안은 `a dog`이지만 이미지에는 `^`와 `a`만 표시한다. `I have a dog.` 전체를 다시 쓰지 않는다.

### 10.2 Advanced: 여러 줄의 국소적인 문장 결합

입력 전사는 1번 줄 `I finished my homework.`, 2번 줄 `I took a shower.`, 3번 줄 `I went to bed.`다. 이 예시는 인접 문장 일부를 결합하는 구조 개선이며 글 전체를 재작성하는 허용으로 확장하지 않는다.

```json
{
  "items": [
    {
      "action": "REPLACE",
      "feedback_type": "SUGGESTION",
      "area": "SENTENCE_STRUCTURE",
      "issue_kinds": ["sentence_structure"],
      "anchors": [
        {"line_id": 1, "original": "I finished my homework.", "occurrence": 1},
        {"line_id": 2, "original": "I took a shower.", "occurrence": 1},
        {"line_id": 3, "original": "I went to bed.", "occurrence": 1}
      ],
      "replacement": "After finishing my homework and taking a shower, I went to bed.",
      "guidance": null,
      "reason": "각 문장은 올바릅니다. 시간 순서를 연결하면 반복을 줄이고 흐름을 자연스럽게 만들 수 있는 선택적 개선입니다.",
      "location_uncertain": false,
      "target_boxes": [[0.10, 0.20, 0.70, 0.24], [0.10, 0.30, 0.55, 0.34], [0.10, 0.40, 0.50, 0.44]],
      "revision_scope": "SENTENCE_GROUP",
      "edit": {
        "operation": "RESTRUCTURE",
        "text": "After finishing my homework and taking a shower, I went to bed.",
        "position": null
      }
    }
  ]
}
```

서버의 `originalText`는 세 원문을 `\n`으로 연결한 문자열이다. 세 anchors와 세 박스여도 일반 첨삭은 1개다. 파란색 문장 밑줄과 수정 문장을 표시하며 공간이 부족하면 같은 주석에 화살표 배치를 사용한다. 같은 입력의 Standard는 객관적 오류가 없으므로 `{"items":[]}`다.

### 10.3 Advanced: 학생이 직접 보충할 내용

입력 전사: 1번 줄 `Volunteering is good for students.`

```json
{
  "items": [
    {
      "action": "GUIDANCE",
      "feedback_type": "SUGGESTION",
      "area": "CONTENT_SUMMARY",
      "issue_kinds": ["content"],
      "anchors": [{"line_id": 1, "original": "Volunteering is good for students.", "occurrence": 1}],
      "replacement": null,
      "guidance": "Explain how volunteering benefits students.",
      "reason": "원문은 올바릅니다. 구체적인 이유를 직접 덧붙이면 주장이 더 설득력 있게 전달됩니다.",
      "location_uncertain": false,
      "target_boxes": [[0.10, 0.20, 0.85, 0.24]],
      "revision_scope": null,
      "edit": null
    }
  ]
}
```

서버는 `content`에 안내문을 넣고 `annotation=null`로 제공한다. 학생의 이유를 대신 만들거나 원문을 안내문으로 교체하지 않는다. Standard에서는 이 선택적 안내를 반환하지 않는다.

### 10.4 원문을 지정할 수 없는 REVIEW

```json
{
  "items": [
    {
      "action": "REVIEW",
      "feedback_type": null,
      "area": null,
      "issue_kinds": ["image_problem"],
      "anchors": [],
      "replacement": null,
      "guidance": null,
      "reason": "사진 하단이 잘려 추가 글이 있는지 확인이 필요합니다.",
      "location_uncertain": true,
      "target_boxes": [],
      "revision_scope": null,
      "edit": null
    }
  ]
}
```

이 항목은 `reviewIssues`로 제공하고 7개 한도에 포함하지 않는다.

### 10.5 프론트 조회: 삽입 주석의 최초 응답

10.1을 저장한 뒤의 변경 관련 필드만 발췌했다. `id/etag/generationId/source`와 기존 관리 필드는 현재 API 원칙을 유지한다.

```json
{
  "id": 201,
  "contractVersion": "AUTEDI-AI-CONTRACT-v1.1",
  "area": "GRAMMAR_SPELLING",
  "level": "STANDARD",
  "action": "REPLACE",
  "feedbackType": "CORRECTION",
  "issueKinds": ["grammar"],
  "originalText": "dog",
  "aiInitialContent": "a dog",
  "content": "a dog",
  "explanation": "단수 가산명사 dog 앞에 관사 a가 필요합니다.",
  "anchors": [{"transcriptionId": 301, "lineId": 1, "occurrence": 1, "start": 7, "end": 10}],
  "revisionScope": "WORD",
  "edit": {"operation": "INSERT", "text": "a", "position": "BEFORE"},
  "targetRegions": [{"pageId": 10, "x": 0.30, "y": 0.20, "width": 0.06, "height": 0.03}],
  "regionCoordinateUnit": "NORMALIZED_0_1",
  "locationUncertain": false,
  "warnings": [],
  "requiresReview": false,
  "annotation": {
    "annotationId": "cor_201",
    "baseType": "caret",
    "type": "caret",
    "category": "error",
    "color": "red",
    "correctionText": "a"
  },
  "annotationLayout": null
}
```

프론트는 실제 배치 후 아래와 같은 레이아웃을 계산한다. 이 역시 설명용 좌표이며 학생 원문을 감싼 `targetRegions`와 다른 값이다.

```json
{
  "type": "caret",
  "marks": [
    {"type": "caret", "points": [[0.290, 0.220], [0.295, 0.210], [0.300, 0.220]]}
  ],
  "textRegions": [{"x": 0.285, "y": 0.165, "width": 0.020, "height": 0.025}],
  "correctionRegions": [
    {"x": 0.290, "y": 0.210, "width": 0.010, "height": 0.010},
    {"x": 0.285, "y": 0.165, "width": 0.020, "height": 0.025}
  ],
  "hitRegions": [
    {"x": 0.288, "y": 0.208, "width": 0.014, "height": 0.014},
    {"x": 0.283, "y": 0.163, "width": 0.024, "height": 0.029}
  ]
}
```

`annotationId=cor_201`의 기호와 문구만 선택·강조한다. 기본 PNG에 하이라이트를 그리지 않는다. 교사 배치를 서버에 보존할 때는 이 객체를 `annotationLayout`으로 보내고 최신 `If-Match`를 사용한다.

## 11. 현재 v1과 v1.1 변경표

| 대상 | 현재 v1 | v1.1 |
| --- | --- | --- |
| Standard 유형 | 제한적 `SUGGESTION` 허용 | `CORRECTION`만 |
| 레벨 정의 | 개입 강도 중심 | 목적·검토 깊이, 기본 오류 검사 유지 |
| 권장·최대 개수 | Standard 3/5, Advanced 5/7 | 권장 없음, 두 레벨 최대 7 |
| AI 원문 연결 | `anchor` 한 개 | `anchors[]`, 연속된 다중 줄 지원 |
| AI 좌표 | `boxes`, 원문 위치 | `target_boxes`, 원문 위치로 의미 명확화 |
| 수정 동작 | `action=REPLACE` 안에 통합 | `edit.operation`으로 삽입·삭제·교체·구조 변경 구분 |
| 표시 문구 | 교체문·안내문만 제공 | `edit.text`로 실제 표시 문구 구분 |
| 수정 범위 | 문구 중심 제한 | `revision_scope`와 영역·동작 조합 검증 |
| 색상·주석 ID | AI·조회 응답에 색상 없음 | 서버가 유형·저장 ID로 생성 |
| 프론트 원문 연결·좌표 | `anchor`, `regions` | `anchors[]`, `targetRegions[]` |
| 프론트 표시 정보 | 원문 좌표 중심 | `annotation`, `annotationLayout` 추가 |
| 클릭·노란색 강조 | 계약에 없음 | 렌더러가 계산, 실제 새 표시·수정 문구만 일시 강조 |
| 요약 응답 | `recommendedCount` 있음 | 제거, 활성 영역 `maximumCount=7` |

현재 구현 확인 지점:

- [v1 JSON Schema](../../src/main/resources/ai/contract/v1/correction-response.schema.json)
- [v1 공통 프롬프트](../../src/main/resources/ai/prompt/v1/common-principles.txt)
- [프롬프트 조립](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1/prompt/AiCorrectionPromptComposer.java)
- [v1 검증기](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1/validation/AiCorrectionResponseValidator.java)
- [현재 저장·조회 필드 변환](../../src/main/java/com/ceos24/autedi/domain/generation/service/GenerationResultStorageService.java)
- [현재 CorrectionResponse](../../src/main/java/com/ceos24/autedi/domain/correction/dto/response/CorrectionResponse.java)

## 12. 구현·이행·검증 기준

### 12.1 버전 이행

1. v1.1 프롬프트·JSON Schema·파서·검증기를 버전별로 마련한다. 모든 객체는 필수 키와 추가 키 금지 정책을 명시한다.
2. 계약 식별자는 요청·생성 결과 메타데이터에 서버가 기록한다. 모델에게 계약 버전을 재생성하게 하지 않는다.
3. 저장 결과를 읽을 때 기록된 버전으로 파서를 선택한다. 현재 v1 계약에 고정된 저장 검증도 함께 확장한다.
4. 저장된 v1 원본 응답을 v1.1로 덮어쓰지 않는다. 공통 조회 어댑터가 필요하면 `anchor → [anchor]`, `regions → targetRegions`만 명확하게 변환한다.
5. 과거 결과에 없는 `edit`·주석 배치를 추측해 채우지 않는다. v1은 기존 카드 표시를 유지하고 v1.1 렌더링 지원 여부를 구분한다.
6. 프론트 API 필드 삭제·이름 변경은 API 버전 또는 명시적인 계약 버전 협의로 반영한다. 기존 `regions`의 의미를 첨삭 표시 좌표로 바꾸는 방식은 사용하지 않는다.
7. 고정 응답으로 저장·조회·편집·표시를 검증한 뒤 실제 모델 품질을 확인한다. 이 문서 작성만으로 호출·배포·마이그레이션을 수행하지 않는다.

### 12.2 구현 시 통과해야 할 사례

- Standard의 정상 문장은 0개이고, Advanced에서만 의미 있는 파란색 개선이 생긴다.
- `She go to school.`은 두 레벨 모두 `go → goes`로 최소 수정한다.
- `I have dog.`는 `replacement="a dog"`, `edit.text="a"`로 일치하며 이미지에는 `^`와 `a`만 표시한다.
- 삭제는 빈 교체문을 허용하고, 안내문·일반 교체문의 빈 문자열과 구분한다.
- 마침표·대문자·철자 오류는 필요한 부분만 표시한다.
- 문법·어휘의 문장 전체 수정, Standard의 SUGGESTION, 비활성 영역 우회가 정상 카드로 통과하지 않는다.
- 동일 문자열의 occurrence, 줄 사이 연속 범위, 잘못된 중간 구간, 문단 경계, 여러 줄의 단일 문장 연결을 검증한다.
- 두 레벨 모두 일반 카드 최대 7개, 오류 우선, REVIEW 제외, 권장 개수 없음이 프롬프트·서버·summary에서 일치한다.
- 삽입·취소선·문장 밑줄·공간 부족 시 화살표 배치가 원문을 보존한다.
- 확대·축소해도 같은 PNG와 정규화 좌표를 사용하고 오른쪽·아래 좌표를 너비·높이로 오해하지 않는다.
- 클릭은 첨삭 그룹 하나만 선택하며 원문을 칠하지 않는다. 빈 곳 클릭·다른 항목 선택 시 해제한다.
- 내보낸 이미지·PDF와 기본 PNG에는 노란색 선택 효과가 없다.
- 교사 편집 후 교체안·표시 문구가 일치하고 레이아웃이 재계산되며 최초 AI 결과는 보존된다.
- v1 저장 결과는 계속 조회되고 v1.1을 v1 파서에 잘못 전달하지 않는다.

추가 기획 확인 항목은 GUIDANCE의 이미지 표시 방식이다. 실제 클릭 좌표를 AI가 아니라 렌더러가 생성하고, 색상·ID를 서버가 생성하는 역할 분담은 이 v1.1 설계안에 명시한다.
