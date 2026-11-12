# v1.1 고정 응답 예제

- 기준: [AI 공통 응답 계약 v1.1](../../../../../../docs/ai_test_docs/AI-공통-응답-계약-v1.1.md)
- 계약 식별자: `AUTEDI-AI-CONTRACT-v1.1`
- 출처: 모두 사람이 만든 합성 예제(`SYNTHETIC`). 실제 모델 실행 결과나 학생 자료가 아니다.
- 범위: **고정 응답과 기대 결과 작성 단계**. v1.1 서비스·DTO·Schema·파서·검증기를 구현하거나 실제 AI를 호출하지 않는다.

## 1. 파일 구성과 사용 방법

| 파일 | 용도 |
| --- | --- |
| [cases.json](cases.json) | 예제별 설명, 입력 전사문, 선택 영역·레벨, 응답 파일 참조, 기대 처리 결과 |
| `responses/*.json` | 이후 v1.1 파서에 문자열 그대로 전달할 독립적인 AI 응답 |
| [malformed-json.txt](responses/malformed-json.txt) | 의도적으로 닫히지 않은 JSON. 응답 전체 해석 실패 예제 |

총 30개다: 정상 12개, 경계·개수 처리 5개, 서버 검증 위반 9개, 파서 거절 대상 4개.

`cases.json`은 테스트 자료의 묶음이며 **AI 응답 형식이 아니다**. AI 응답은 각 `response_file`의 내용이다. 계약 버전·설정·기대 결과를 AI 응답에 합쳐 보내지 않는다.

현재 서비스의 `ai/fixed/v1`과 분리된 **테스트 리소스**다. 이 자료를 추가해도 FIXED 모드나 실제 생성 경로의 응답은 변경되지 않는다.

## 2. cases.json 형식

최상위의 `fixture_format_version=1`은 예제 묶음의 형식 버전이며 AI 계약 버전과 별개다.

| 예제 필드 | 의미 |
| --- | --- |
| `id`, `description`, `category`, `tags` | 예제 식별자·목적·분류·검사 항목 |
| `response_file` | 이 디렉토리를 기준으로 한 원본 응답 파일 경로 |
| `comparison_group` | 동일 입력으로 Standard·Advanced를 비교하는 예제 쌍. 해당하는 경우만 존재 |
| `context.page_number` | 입력 페이지 번호 |
| `context.transcription_lines` | 빈 줄·원문·줄 번호를 보존한 전사 입력 |
| `context.selected_areas` | 테스트에서 사용할 영역·레벨. 실제 프론트 요청 DTO 이름을 정의하는 필드는 아님 |
| `expected.parse_result` | 향후 v1.1 파서의 기대 결과: `ACCEPT` / `REJECT` |
| `expected.generation_status` | 해당 자료를 서비스에 연결했을 때 기대하는 생성 상태 |
| `expected.cards` | 정상 카드로 남길 후보, 정확한 원문, UTF-16 위치, 유효 박스, 경고·주석 기대값 |
| `expected.reviews` | 확인 필요 목록으로 분리할 후보 |
| `expected.excluded` | 비활성 영역·Standard 제안·개수 초과 등으로 제외할 후보 |
| `expected.display_order` | 원문 순서로 정렬한 정상 카드의 후보 인덱스 |
| `expected.summary` | 네 영역별 레벨·최대·최초 검증·현재 표시·제외 수 |
| `expected.parse_issue_tag` | 파서 거절 대상의 원인. `REJECT`일 때만 존재 |

- `candidate_index`는 원본 `items` 배열의 **0 기반 인덱스**다. DB ID가 아니다.
- 파싱을 기대하는 모든 원본 후보는 `cards`, `reviews`, `excluded` 중 정확히 한 곳에 연결된다.
- `resolved_anchors.start/end`는 각 전사 줄 안의 **UTF-16 인덱스**, end 미포함이다. Python 등의 유니코드 코드포인트 인덱스와 다를 수 있다.
- `valid_target_boxes`는 서버 검증 후 남을 것으로 기대하는 원문 박스이며 `[left, top, right, bottom]` 형식이다.
- `annotation`은 **서버가 만들 것으로 기대하는 값**이다. AI 원본에는 색상·주석 ID를 넣지 않는다.
- `auto_place`는 이 예제에서 자동 이미지 배치가 가능한지 나타내는 기대값이다. 실제 API 필드를 추가하는 정의는 아니다.
- `GUIDANCE`는 안내 카드만 남고 `annotation=null`, `auto_place=false`다.
- 일부 박스만 잘못됐다면 유효 박스를 유지한다. 위치 자체가 불확실하면 카드·경고는 남고 `auto_place=false`다.
- `summary`에는 비활성 영역도 `level=null`, `maximum_count=0`으로 포함한다. 원래 `REVIEW` 항목은 일반 카드 한도와 `omitted_count`에서 제외한다.
- 최초 상태이므로 `validated_count=displayed_count`다. 향후 교사 삭제·편집은 별도 서비스 테스트에서 검증한다.
- `issue_tags`, `parse_issue_tag`는 **의미를 표현하는 예제용 태그**다. 아직 없는 v1.1 Java enum·실제 경고 코드·경고 개수를 확정하지 않는다. 실제 검증기가 생기면 대응되는 진단에 매핑한다.

## 3. 예제 목록

### 정상 응답

| ID / 응답 | 확인 내용 |
| --- | --- |
| [standard-insert-before](responses/standard-insert-before.json) | `dog → a dog`, 표시 문구 `a`, 앞 삽입 |
| [standard-insert-after](responses/standard-insert-after.json) | 마침표만 뒤에 삽입 |
| [standard-delete-word](responses/standard-delete-word.json) | `to` 삭제. 빈 교체문·빈 표시 문구 |
| [standard-grammar-replace](responses/standard-grammar-replace.json) | Standard의 `go → goes` |
| [advanced-grammar-replace](responses/advanced-grammar-replace.json) | 같은 입력의 Advanced도 같은 오류 수정 |
| [standard-vocabulary-replace](responses/standard-vocabulary-replace.json) | 잘못된 연어 `made homework → did homework` |
| [standard-correct-structure-empty](responses/standard-correct-structure-empty.json) | 올바른 문장에 Standard는 구조 개선을 추가하지 않음 |
| [advanced-structure-multiline](responses/advanced-structure-multiline.json) | 같은 글을 Advanced에서 세 줄·카드 하나의 선택적 구조 개선으로 연결 |
| [advanced-content-guidance](responses/advanced-content-guidance.json) | 새 근거를 대신 작성하지 않고 선택적 안내 |
| [standard-required-content-guidance](responses/standard-required-content-guidance.json) | `because` 뒤의 필수 설명 누락 안내 |
| [review-image-problem](responses/review-image-problem.json) | 원문을 특정할 수 없는 이미지 문제 REVIEW |
| [grammar-phrase-across-lines](responses/grammar-phrase-across-lines.json) | 두 전사 줄이어도 문법의 수정 범위는 PHRASE |

### 경계·개수 처리

| ID / 응답 | 확인 내용 |
| --- | --- |
| [occurrence-utf16-and-blank-line](responses/occurrence-utf16-and-blank-line.json) | 두 번째 `go`, emoji 앞부분의 UTF-16 길이, 빈 줄, 페이지 번호 |
| [uncertain-location-card](responses/uncertain-location-card.json) | 텍스트 카드를 유지하고 위치 확인 전 자동 배치 금지 |
| [partial-invalid-boxes](responses/partial-invalid-boxes.json) | 역전된 박스만 제외, 유효 박스·카드·경고 보존 |
| [standard-eight-corrections-limit](responses/standard-eight-corrections-limit.json) | Standard도 7개까지 허용, 여덟 번째 제외 |
| [advanced-corrections-before-suggestion](responses/advanced-corrections-before-suggestion.json) | 먼저 반환된 개선보다 오류 7개를 우선하고 REVIEW는 한도에서 제외 |

개수 초과나 역전된 박스는 바람직한 AI 출력 예제가 아니라 **서버의 후처리를 확인하기 위해 의도적으로 만든 경계 입력**이다.

### 서버 검증 위반: JSON은 읽되 정상 카드에서 분리

| ID / 응답 | 기대 처리 |
| --- | --- |
| [standard-suggestion-excluded](responses/standard-suggestion-excluded.json) | Standard의 개선을 제외. 오류 수정으로 재분류하지 않음 |
| [disabled-area-excluded](responses/disabled-area-excluded.json) | 비활성 어휘 영역 제외 |
| [grammar-sentence-scope-review](responses/grammar-sentence-scope-review.json) | 문법 영역의 문장 전체 재작성은 확인 필요 |
| [anchor-not-found-review](responses/anchor-not-found-review.json) | 전사에 없는 원문은 확인 필요 |
| [noncontiguous-anchors-review](responses/noncontiguous-anchors-review.json) | 중간 줄을 건너뛴 원문 연결은 확인 필요 |
| [edit-replacement-mismatch-review](responses/edit-replacement-mismatch-review.json) | `goes`와 `went`의 불일치 확인 |
| [replace-with-guidance-review](responses/replace-with-guidance-review.json) | REPLACE에 guidance가 채워진 조합 오류 확인 |
| [guidance-over-ten-words-review](responses/guidance-over-ten-words-review.json) | 11단어 안내문 확인 |
| [insert-changes-source-review](responses/insert-changes-source-review.json) | 삽입으로 신고하고 기존 `dog`를 `cat`으로 바꾸는 오류 확인 |

여기서 `parse_result=ACCEPT`는 필드·기본 타입을 읽을 수 있다는 의미다. action·레벨·원문·좌표 관계는 후속 검증기의 검사 대상이다.

### 파서 거절 대상

| ID / 응답 | 기대 처리 |
| --- | --- |
| [missing-edit-field](responses/missing-edit-field.json) | 필수 키 누락 거절 |
| [legacy-v1-fields](responses/legacy-v1-fields.json) | 구버전 필드를 v1.1로 암묵 변환하지 않음 |
| [unexpected-ai-color-field](responses/unexpected-ai-color-field.json) | 추가 AI color 필드 거절 |
| [malformed-json](responses/malformed-json.txt) | JSON 문법 해석 실패 |

## 4. 현재 실행 가능한 검사와 다음 단계

Java 21 환경에서 프로젝트 루트를 기준으로 실행한다.

```powershell
.\gradlew.bat --offline test --tests "*AiCorrectionFixtureResourcesTest"
```

현재 테스트는 예제 ID·파일 참조·입력 문맥·후보 인덱스의 완전한 분류·기대 UTF-16 범위·유효 박스·개수 집계·레벨 비교 쌍의 자료 무결성을 검사한다. Spring·DB·외부 네트워크를 사용하지 않는다.

위 `AiCorrectionFixtureResourcesTest`는 자료 무결성만 검사한다. 별도로 [v1.1 파서](../../../../../../docs/ai_test_docs/AI-공통-응답-계약-v1.1-파서.md)와 `AiCorrectionResponseParserTest`를 추가해 30개 응답의 실제 파싱 결과를 `parse_result`와 비교한다.

```powershell
.\gradlew.bat --offline test --tests "*domain.generation.contract.v1_1.*AiCorrectionResponseParserTest"
```

**파싱 성공은 정상 카드 판정이나 AI 품질 검증을 뜻하지 않는다.** 다음 단계에서:

1. 추가된 [v1.1 DTO·Schema](../../../../../../docs/ai_test_docs/AI-공통-응답-계약-v1.1-DTO-Schema.md)와 파서를 유지하면서 문맥 검증기를 구현한다.
2. 파싱된 결과와 `context`를 검증기에 전달한다.
3. 정상·확인·제외 후보, 원문 연결, 좌표, 개수·정렬을 `expected`와 비교한다.
4. 저장·조회·표시 테스트에서는 `expected.cards`를 기준으로 서비스 DTO와 렌더링을 검증한다.

박스는 모두 설명용 좌표이며 실제 이미지 파일을 동봉하지 않는다. 따라서 이 자료만으로 손글씨 위치 정확도·표현 개선 품질·실제 클릭 배치를 평가할 수는 없다.
