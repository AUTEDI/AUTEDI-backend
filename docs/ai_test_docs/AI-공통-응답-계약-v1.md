# AUTEDI AI 공통 응답 계약 v1

- 작성일: 2026-10-30 (한국 시간)
- 계약 식별자: `AUTEDI-AI-CONTRACT-v1`
- 상태: 사용자와 합의한 기준을 반영한 **서비스 연동 설계안**
- 범위: AI 첨삭 응답, 서버 검증 결과, 저장 및 프론트 조회 응답 사이의 계약
- 주의: 이 문서를 작성했다고 운영 코드·JSON Schema·DB·API에 적용된 것은 아니다.

기존 실험 기준은 [AI 기준 버전](AI-기준버전-2026-10-30.md)이다.
기존 실험의 `edits` 형식과 이 문서의 `items` 형식은 서로 다른 버전이다.
과거 결과 파일을 새 형식으로 덮어쓰거나, 기존 파서가 새 응답을 처리할 수 있다고 가정하지 않는다.

## 1. 목적과 역할 분리

AI는 분석 결과를 반환하고, 서버는 원문 위치·좌표·설정 준수를 검증한 뒤 서비스 데이터로 변환한다.

```text
회전 보정 PNG → 전사 → 전사 저장
                        ↓
같은 PNG + 이번 전사문 + 선택 설정 → AI 첨삭 응답
                                      ↓
                          서버 검증·개수 제한·좌표 변환
                                      ↓
                         저장 → 검토 화면 조회 응답
```

- 모델과 처리 방식은 기존의 Terra 전사 → Terra 첨삭 2단계를 유지한다.
- 이 계약은 두 번째 첨삭 요청의 응답을 중심으로 정의한다.
- 전사 단계는 학생의 철자·문법·문장부호 오류와 줄바꿈을 보존한다.
- 원본 이미지와 전체 전사문에 교정 내용을 자동 적용하지 않는다.
- 유료 호출, 운영 연동, 품질·속도 검증은 이 문서 작성 범위에 포함되지 않는다.

| 계층 | 책임 |
| --- | --- |
| AI | 오류·개선 판단, 대표 영역 선택, 설명 작성, 원문 및 위치 추정 |
| JSON Schema | 필드·타입·허용값·배열 구조 제한 |
| Java 후처리 | anchor·좌표·설정·필드 조합 검사, 문자 위치 계산, 경고와 제한 처리 |
| DB | 전사 버전, 최초 AI 결과, 검증 결과, 교사 편집 내용 보존 |
| 프론트 | 첨삭·내용 안내·확인 필요 목록 구분, 위치 표시, 교사 편집 |

## 2. 합의한 기준

| 항목 | 기준 |
| --- | --- |
| 원문 범위 | 한 전사 줄 안의 정확한 부분문자열을 유지한다. |
| 분류 | 첨삭 유형과 문제 종류를 별도 정보로 관리한다. |
| 제안 동작 | 원문 교체, 내용 보완 안내, 교사 확인을 구분한다. |
| 확인 필요 정보 | 보존하고, 화면에서 별도로 표시할 수 있도록 조회 응답에 제공한다. |
| 대표 영역 | 항목마다 대표 영역 하나만 둔다. 수정별 상세 영역은 만들지 않는다. |
| 카드 수 | Standard 권장 3개·최대 5개, Advanced 권장 5개·최대 7개로 제안한다. |

카드 수는 사용자의 “권장 수에서 1~2개 정도 추가 허용”을 최대 2개 추가로 해석했다.
페이지별·대표 영역별로 적용한다. 개수 집계 및 초과 처리의 세부 계약은 8절을 따른다.

이하의 필드 이름, 좌표 단위, 저장·조회 방식은 위 기준을 구현하기 위한 제안이다.
실제 적용 시 A의 API·DB 및 프론트와 함께 맞춘다.

## 3. 결과 종류와 분류

### 3.1 action: 제안의 동작

| 값 | 의미 | 화면 용도 |
| --- | --- | --- |
| `REPLACE` | 원문 일부를 바꾸는 제안 | 원문 → 수정안 카드 |
| `GUIDANCE` | 내용을 보완하도록 안내 | 내용 보완 카드. 원문 교체에 사용하지 않음 |
| `REVIEW` | 판독·의도·OCR 문제로 교사 확인 필요 | 일반 첨삭과 구분한 확인 목록 |

`REPLACE`는 자동 교체 실행을 뜻하지 않는다. 교사가 검토할 제안이다.

### 3.2 feedback_type: 첨삭 유형

| 값 | 의미 |
| --- | --- |
| `CORRECTION` | 반드시 고쳐야 하는 명확한 오류에 대한 수정·안내 |
| `SUGGESTION` | 선택적으로 반영할 수 있는 개선 제안 |
| `null` | `REVIEW` 항목. 학생 오류나 개선 제안으로 확정하지 않음 |

내용 안내도 확인 가능한 필수 과제 요구가 빠진 경우에는 `CORRECTION`,
선택적인 근거 보완이라면 `SUGGESTION`이 될 수 있다.

### 3.3 issue_kinds: 문제 종류

| 사용 범위 | 값 |
| --- | --- |
| 일반 첨삭 | `grammar`, `spelling`, `punctuation`, `vocabulary`, `sentence_structure`, `content` |
| 확인 필요 항목 | `ocr_mismatch`, `illegible`, `unclear_intent`, `image_problem` |

- 최소 1개이며 중복을 허용하지 않는다.
- 하나의 카드에 여러 문제 종류가 포함될 수 있다.
- 예: 문법·철자를 함께 고친 카드의 `issue_kinds`는 `["grammar", "spelling"]`이다.
- 기존 실험의 `needs_review` 대신, 확인이 필요한 구체적인 이유를 구분한다.
- 문제 종류와 대표 영역은 다른 개념이다. 꺼진 영역의 수정을 다른 대표 영역으로 우회해서는 안 된다.

### 3.4 area: 대표 첨삭 영역

일반 첨삭에는 다음 네 값 중 선택된 영역 하나를 사용한다.

```text
GRAMMAR_SPELLING
VOCABULARY_EXPRESSION
SENTENCE_STRUCTURE
CONTENT_SUMMARY
```

- `REVIEW`는 `area=null`로 둔다.
- `GRAMMAR_MECHANICS`와 혼용하지 않고 계약의 표준값은 `GRAMMAR_SPELLING`으로 정규화한다.
- Standard/Advanced는 모델이 다시 반환하지 않는다. 서버가 생성 요청의 설정 스냅샷에서 확인한다.

## 4. AI 응답 형식

최상위 객체는 `items` 배열 하나를 갖는다.
모델 응답에는 DB ID, 학습지 ID, 생성 ID, 계산된 문자 위치를 요구하지 않는다.
서버가 요청 문맥과 검증 결과로 연결한다.

### 4.1 공통 필드

| 필드 | 형식·규칙 |
| --- | --- |
| `action` | `REPLACE` / `GUIDANCE` / `REVIEW` |
| `feedback_type` | `CORRECTION` / `SUGGESTION`. `REVIEW`이면 `null` |
| `area` | 선택된 대표 영역 하나. `REVIEW`이면 `null` |
| `issue_kinds` | 문제 종류 배열. 최소 1개, 중복 없음 |
| `anchor` | 원문 위치 정보. 위치 없는 `REVIEW`만 `null` 허용 |
| `replacement` | `REPLACE`의 교체 제안. 그 외에는 `null` |
| `guidance` | `GUIDANCE`의 영어 보완 안내. 그 외에는 `null` |
| `reason` | 한국어 설명 또는 확인 필요 사유 |
| `location_uncertain` | 모델이 이미지 위치를 불확실하게 판단했는지 |
| `boxes` | 전체 전달 PNG 기준 0~1 직사각형 배열 |

모든 필드는 포함하고, 해당하지 않는 값은 `null`로 받는다.
허용 필드와 기본 타입은 JSON Schema로 제한하고, 동작에 따른 필드 조합은 서버에서도 검증한다.

### 4.2 종류별 조합

| 규칙 | REPLACE | GUIDANCE | REVIEW |
| --- | --- | --- | --- |
| `feedback_type` | 오류 수정 또는 개선 제안 | 오류 수정 또는 개선 제안 | `null` |
| `area` | 선택된 영역 중 하나 | `CONTENT_SUMMARY` | `null` |
| `anchor` | 필수 | 필수 | 찾을 수 있으면 제공 |
| `replacement` | 문자열 | `null` | `null` |
| `guidance` | `null` | 영어 10단어 이내 | `null` |
| 일반 첨삭 개수에 포함 | 포함 | 포함 | 제외 |

삭제 제안은 `replacement=""`로 허용한다.
`replacement=null`은 삭제가 아니라 원문 교체 유형이 아니라는 뜻이다.

`reason`은 존중하는 한국어 1~2문장으로 무엇을 왜 바꾸는지 설명한다.
개선 제안은 틀린 문장이 아니라 선택적인 개선이라는 점을 밝힌다.
확인 필요 항목에는 무엇을 확인해야 하는지 구체적으로 적는다.

### 4.3 한 줄 anchor

```json
{
  "line_id": 1,
  "original": "go",
  "occurrence": 1
}
```

- `line_id`: 저장한 전사문의 줄 번호. 1부터 시작하며 중간 빈 줄도 포함한다.
- `original`: 해당 줄에 존재하는 정확하고 비어 있지 않은 부분문자열이다.
- `occurrence`: 같은 문자열의 겹치지 않는 등장 순서. 1부터 시작한다.
- 하나의 anchor는 여러 전사 줄에 걸칠 수 없다.
- 삽입은 인접한 기존 원문을 anchor로 잡고 그 원문을 포함한 교체문을 제공한다.
- 내용 안내는 관련 있는 한 줄 안의 구절에 연결한다. 글 전체에 관한 안내는 마지막 관련 구절을 선택한다.
- 전사문 정규화·줄 번호 부여는 서버가 한 번 확정하고, 첨삭과 검증에서 동일한 전사 버전을 사용한다.

사진 전체가 흐린 경우처럼 원문을 지정할 수 없다면 다음 조합을 사용한다.

```text
action = REVIEW
anchor = null
boxes = []
location_uncertain = true
```

### 4.4 boxes

```text
[[left, top, right, bottom], ...]
```

- 좌표 기준은 두 AI 요청에 전달한 동일한 EXIF 보정 PNG 전체이다.
- 원점은 좌상단, x는 오른쪽, y는 아래쪽이다.
- 숫자 4개이며 `0 <= left < right <= 1`, `0 <= top < bottom <= 1`을 만족해야 한다.
- 제안문이 아니라 원래 손글씨 anchor의 위치를 감싼다.
- 여러 boxes를 허용하지만 여러 전사 줄 anchor를 허용하는 것은 아니다.
- 위치를 찾지 못하면 `boxes=[]`, `location_uncertain=true`로 반환한다.
- 형식 검증 통과는 실제 글자의 위치가 정확하다는 보장이 아니다.

### 4.5 설명용 응답 예시

다음은 실제 모델 실행 결과가 아닌 가상 예시다.
전사문은 1번 줄 `He go to school yesterday.`, 2번 줄 `I like this book.`이라고 가정한다.
좌표도 형식 설명용이다.

```json
{
  "items": [
    {
      "action": "REPLACE",
      "feedback_type": "CORRECTION",
      "area": "GRAMMAR_SPELLING",
      "issue_kinds": ["grammar"],
      "anchor": {
        "line_id": 1,
        "original": "go",
        "occurrence": 1
      },
      "replacement": "went",
      "guidance": null,
      "reason": "과거 시점인 yesterday에 맞춰 과거형 went를 사용합니다.",
      "location_uncertain": false,
      "boxes": [[0.30, 0.37, 0.34, 0.42]]
    },
    {
      "action": "GUIDANCE",
      "feedback_type": "SUGGESTION",
      "area": "CONTENT_SUMMARY",
      "issue_kinds": ["content"],
      "anchor": {
        "line_id": 2,
        "original": "I like this book.",
        "occurrence": 1
      },
      "replacement": null,
      "guidance": "Add one reason you like this book.",
      "reason": "틀린 문장은 아니지만, 책을 좋아하는 이유를 덧붙이면 생각이 더 분명하게 전달됩니다.",
      "location_uncertain": false,
      "boxes": [[0.10, 0.50, 0.65, 0.56]]
    },
    {
      "action": "REVIEW",
      "feedback_type": null,
      "area": null,
      "issue_kinds": ["image_problem"],
      "anchor": null,
      "replacement": null,
      "guidance": null,
      "reason": "사진 하단이 잘려 해당 영역에 추가 글이 있는지 확인이 필요합니다.",
      "location_uncertain": true,
      "boxes": []
    }
  ]
}
```

## 5. 서버가 추가하는 정보

| 서버 정보 | 목적 |
| --- | --- |
| DB ID, 학습지·페이지·생성 ID | 실제 서비스 데이터와 연결 |
| `transcriptionId` | 어느 전사문을 기준으로 만든 결과인지 고정 |
| `start`, `end` | 해당 줄 안의 UTF-16 위치. 0부터 시작, end 미포함 |
| `warnings` | 검증에서 발견한 문제 |
| `requiresReview` | 모델 확인 사항 또는 서버 검증 경고 여부 |
| 계약·프롬프트 버전 | 결과 추적·비교 근거 |

- `start/end`는 모델이 계산하지 않고 서버가 `original/occurrence`로 찾는다.
- anchor를 찾지 못했을 때 임의의 정상 위치를 만들지 않는다.
- 모델의 `location_uncertain`과 서버의 `requiresReview`는 서로 다른 정보로 보존한다.
- `requiresReview=false`는 검증 경고가 없다는 뜻이며, AI 판단이 정답이거나 교사 검토가 불필요하다는 뜻이 아니다.
- 정상 카드도 최종적으로 교사의 검토 대상이다.
- 모델의 높음/보통/낮음 자기 확신 점수는 v1 필수 필드로 넣지 않는다.

모델 응답 필드는 snake_case, 기존 서비스 DTO의 추가 필드는 camelCase로 매핑하는 방향이다.
전사 버전 식별 방식과 실제 필드·컬럼 이름은 구현 시 확정한다.

## 6. 검증 실패와 데이터 보존

| 상황 | 처리 제안 |
| --- | --- |
| boxes 일부가 잘못됨 | 잘못된 박스를 제외하고 경고 저장 |
| anchor는 맞지만 좌표가 없음 | 카드 보존, 위치 표시 없이 검토 경고 |
| 원문 범위가 겹침 | 관련 카드에 경고. 자동 적용하지 않음 |
| anchor가 전사문과 불일치 | 일반 교체 카드로 공개하지 않고 확인 목록으로 분리. 원래 제안도 보존 |
| 꺼진 영역의 결과 | 정상 카드에서 제외하고 검증 사유·원본 항목 보존 |
| action별 필드 조합이 잘못됨 | 정상 카드에서 제외하고 검증 사유·원본 항목 보존 |
| 응답 전체가 해석 불가능 | 생성 실패 기록 |
| `items=[]` | 전사가 정상이라면 첨삭 없는 정상 성공 |

읽을 글이 없는 전사 실패와, 읽었지만 고칠 것이 없는 정상 성공을 구분한다.
개별 항목의 검증 문제만으로 유료 요청을 자동 재호출하지 않는다.

보존 대상은 최초 모델 응답, 검증 결과, 제외·분리 사유이다.
이를 생성 작업과 연결해 저장하며, 일반 첨삭 카드에서 제외했다고 분석 데이터까지 삭제하지 않는다.
구체적인 테이블 또는 JSON 저장 위치는 구현 시 결정한다.

서버는 선택 영역·좌표·anchor 등을 검사할 수 있지만,
의미 보존과 첨삭의 정답 여부를 완전히 자동 검증할 수 있다고 가정하지 않는다.

## 7. 서비스 좌표 계약 제안

서비스 조회 응답의 `regions`도 0~1 정규화 좌표로 통일하는 것을 제안한다.

```text
x = left
y = top
width = right - left
height = bottom - top
```

- `regions`에는 서버가 검증한 페이지 ID를 명시한다.
- 화면 이미지와 페이지 `width/height`는 보정 PNG 기준이어야 한다.
- 원본 JPG 저장 픽셀 기준으로 역변환한 좌표가 아니다.
- `pixel_boxes`는 디버깅·검증용으로 계산할 수 있으나 기본 조회 응답의 필수값으로 두지 않는다.
- A의 Swagger에 있는 px 설명 및 프론트 기대값은 적용 전에 이 계약과 맞춰야 한다.

예를 들어 `[0.30, 0.37, 0.34, 0.42]`는
`x=0.30`, `y=0.37`, `width=0.04`, `height=0.05`로 변환된다.
`right/bottom`을 그대로 `width/height`에 넣지 않는다.

## 8. 카드 개수와 정렬

| 수준 | 페이지별·대표 영역별 권장 | 최대 |
| --- | ---: | ---: |
| Standard | 3개 | 5개 |
| Advanced | 5개 | 7개 |

- `REPLACE`와 `GUIDANCE`를 합산한다.
- 여러 문제 종류가 있어도 카드 하나면 1개이다.
- 여러 boxes가 있어도 카드 하나면 1개이다.
- 최소 개수는 없으며, 상한을 채우기 위해 불필요한 항목을 만들지 않는다.
- `REVIEW`는 별도 확인 목록이므로 위 제한에서 제외한다.

모델은 중요한 항목부터 반환하고, 서버는 검증 및 제한 적용 후 화면 결과를 원문 순서로 정렬한다.
상한 초과 항목도 생성 결과에 보존하고, 조회 응답에 `omittedCount` 등 제외 수를 제공하는 방향이다.

`omittedCount`는 모델이 반환했지만 표시에서 제외한 수이며,
모델이 발견하지 못했거나 반환하지 않은 전체 오류 수를 뜻하지 않는다.
확인 목록의 별도 개수·응답 크기 제한과 동일 우선순위 항목의 선택 규칙은 구현 시 정한다.

## 9. 저장 및 프론트 조회 계약

### 9.1 검토 화면 응답의 구성

기존 `ApiResponse.result` 안의 검토 데이터에 다음 목록을 제공하는 방향이다.
기존 학습지·생성·코멘트 데이터는 그 역할을 유지한다.

| 목록 | 내용 |
| --- | --- |
| `corrections` | 정상적인 `REPLACE`, `GUIDANCE` 카드 |
| `reviewIssues` | 모델의 `REVIEW`, 정상 카드로 사용할 수 없었던 검증 문제 |
| `summary` | 영역별 표시 수·제한 초과 제외 수 등 |

이 절은 조회 데이터의 구성 제안이며, 완성된 API DTO 명세가 아니다.
확인 항목의 식별자·검증 사유·원본 후보 참조 등 세부 필드는 구현 시 정의한다.

### 9.2 기존 Correction 재사용

| 필드 | REPLACE | GUIDANCE |
| --- | --- | --- |
| `originalText` | anchor 원문 | 안내와 연결한 anchor 원문 |
| `aiInitialContent` | 최초 교체 제안 | 최초 영어 보완 안내 |
| `content` | 현재 교체 제안 | 현재 보완 안내 |
| `explanation` | 최초 AI 설명 | 최초 AI 설명 |
| `regions` | 검증된 원문 위치 | 검증된 연결 위치 |

추가로 `action`, `feedbackType`, `issueKinds`, anchor 연결 정보와 검토 경고를 보존해야 한다.
`content`의 의미는 반드시 `action`과 함께 해석한다.
교사 편집 시 최초 내용은 보존하고 `content`만 변경하는 기존 원칙을 유지한다.

빈 문자열 삭제 제안을 허용하려면 현재 교사 수정 API의 `@NotBlank` 검증 등을 조정해야 한다.
내용 보완 안내의 빈 문자열은 허용하지 않는다.

### 9.3 확인 필요 항목

`REVIEW`는 현재 `Correction`의 필수 영역·원문·제안 필드에 억지로 넣지 않는다.
생성 작업에 연결된 별도 확인 데이터로 저장하는 것을 제안한다.
별도 Entity 또는 생성 결과 JSON 중 실제 저장 방식은 구현 시 정한다.

이 데이터는 교사가 직접 작성하는 `Comment`와 구분한다.
`requiresReview`와 `editedByTeacher`도 서로 대체하지 않는다.

## 10. 현재 코드와 달라지는 부분

| 현재 실험·서비스 | v1 제안 |
| --- | --- |
| 최상위 `edits` | `items` |
| 단일 `kind` | `feedback_type`과 `issue_kinds` 분리 |
| 모든 항목에 `replacement` | `action`에 따라 replacement/guidance 구분 |
| 줄 정보가 항목에 직접 있음 | `anchor` 객체로 묶음 |
| `needs_review`가 edits에 포함 | 모델 결과는 REVIEW, 서비스 조회는 reviewIssues로 구분 |
| 전사·경고 정보가 실험 파일에만 존재 | 생성·전사 버전과 연결해 보존 |
| Correction 응답에 새 분류가 없음 | action·유형·문제 종류·경고 등을 확장 |

기존 한 줄 anchor 검색, occurrence 검사, UTF-16 위치 계산, boxes 검증·변환 로직은 재사용한다.
기존 실험 파서, CorrectionDraft, Entity, DTO, 조회·편집 검증 및 프롬프트는 새 계약에 맞춘 변경이 필요하다.

## 11. 적용 순서와 남은 확인 사항

1. 이 계약을 기준으로 DTO와 JSON Schema를 정의한다.
2. API 없는 고정 응답으로 필드 조합·anchor·좌표·설정·제한을 검증한다.
3. 전사·모델 응답·경고·교사 편집을 보존할 저장 구조를 맞춘다.
4. 조회 응답과 프론트의 첨삭·내용 안내·확인 목록 구분을 맞춘다.
5. 공통 프롬프트 + 선택 영역·수준 규칙 + 기술 규칙을 조합한다.
6. 사용자가 실제 호출을 요청한 시점에 전사 → 첨삭 연동을 검증한다.

적용 전에 확인할 사항:

- 정규화 regions를 기준으로 A·프론트의 좌표 설명과 표시를 일치시킬 것
- 전사 식별자와 불변 전사문 저장 형식
- 확인 필요 항목·원본 후보·초과 항목의 저장 및 조회 상세
- 확인 목록의 별도 개수·응답 크기 제한
- GUIDANCE 10단어 제한의 서버 계산 기준과 초과 처리
- 기획 예시 안의 Standard/Advanced 경계 및 불확실성 처리 표현 정리

이 문서는 프롬프트·모델 품질이나 운영 통합 완료를 선언하지 않는다.
기존 실험의 모델명·처리 방식을 바꾸거나 과거 기록을 재작성하기 위한 문서도 아니다.
