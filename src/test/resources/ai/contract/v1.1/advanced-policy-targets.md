# ADVANCED 정책 변경의 기대 결과

이 문서는 합의한 정책과 합성 JSON 응답의 기대 처리를 고정하고 단계별 구현 결과를 기록한다. AI 응답 형식은 `AUTEDI-AI-CONTRACT-v1.1`의 기존 12개 필드를 유지한다. 현재는 `checkSettings()`의 영역·레벨 검증과 `checkScope()`의 수정 범위·동작 조합을 구현했으며 DEFAULT/COMPACT 프롬프트도 같은 정책으로 정리했다. API·DB·실제 AI 요청 횟수는 유지한다.

`cases.json`의 `ADVANCED_POLICY_TARGET` 태그가 붙은 18개 사례를 기존 테스트 경로에 연결했다. 이 자료는 실제 모델 출력이나 첨삭 품질의 정답지가 아니다. 설명용 좌표를 사용하며, 서버의 허용·검토·제외 정책을 검증한다.

## 이번 단계의 기준

- STANDARD는 객관적 오류만 CORRECTION으로 제공한다. ADVANCED에서도 기본 CORRECTION의 범위를 확대하지 않는다.
- SUGGESTION의 대표 `area`는 선택된 ADVANCED여야 한다. 대표 영역이 STANDARD이면 계속 제외한다.
- 주된 개선에 필요한 다른 선택 영역의 STANDARD 보조 수정은 허용한다. 보조 영역이 OFF이면 계속 제외한다.
- 보조 수정의 필요성·의미 보존은 enum만으로 검증할 수 없다. 불필요한 독립 개선의 차단은 프롬프트와 별도 모델 평가의 책임이다.
- 원문 앵커, 연속 구간, 빈 줄 경계, 수정 동작의 일관성, GUIDANCE의 영어 10단어 제한은 유지한다.
- CORRECTION과 SUGGESTION은 모두 원문에 연결한다. 같은 범위가 겹치면 카드 자체를 버리지 않고 `OVERLAPPING_ANCHORS` 경고와 검토 필요·자동 배치 금지를 유지한다.
- 페이지·대표 영역별 최대 7개와 CORRECTION 우선 정책을 유지한다. 기존 개수·GUIDANCE 예제와 테스트를 계속 사용한다.
- corrected baseline은 내부 검토 기준이다. 응답·전사문을 새로 만들거나 순차 적용·의존 관계를 추가하지 않는다.

## SUGGESTION의 범위 행렬

모든 행에서 대표 영역이 ADVANCED라는 전제다. WORD/PHRASE/CLAUSE의 교체는 `edit.operation=REPLACE`, 문장 전체 교체는 `edit.operation=RESTRUCTURE`로 신고한다. INSERT/DELETE의 기존 일관성 검사는 유지한다.

| 대표 영역 | 허용 범위 | 유지할 경계 |
| --- | --- | --- |
| GRAMMAR_SPELLING | WORD, PHRASE, CLAUSE, SENTENCE | 여러 문장을 합치는 SENTENCE_GROUP은 허용하지 않는다. |
| VOCABULARY_EXPRESSION | WORD, PHRASE | 절·문장 전체 재작성은 허용하지 않는다. |
| SENTENCE_STRUCTURE | WORD, PHRASE, CLAUSE, SENTENCE, SENTENCE_GROUP | 연결된 원문 범위와 문단 경계를 유지한다. |
| CONTENT_SUMMARY | WORD, PHRASE, SENTENCE, SENTENCE_GROUP | 기존 정보의 명확화·축약·요약으로 제한한다. 초기 범위에서는 CLAUSE를 추가하지 않는다. |

CORRECTION은 두 레벨 모두 기존 행렬을 그대로 사용한다. 이 행렬은 `AiCorrectionResponseValidatorTest.appliesAdvancedSuggestionScopeTargets`의 20개 조합으로 검사한다.

## 합성 응답과 기대 결과

AI에 보내는 응답은 `responses/*.json`뿐이다. 입력·설정·기대값은 [cases.json](cases.json)에 별도로 둔다.

| ID / 응답 | 목표 처리 | 현재 코드와의 차이 |
| --- | --- | --- |
| [policy-all-standard-correction](responses/policy-all-standard-correction.json) | 네 영역 STANDARD에서 `go → went` CORRECTION 유지 | 동일 |
| [policy-structure-standard-grammar-support](responses/policy-structure-standard-grammar-support.json) | 구조 ADVANCED + 문법 STANDARD의 보조 수정 허용 | checkSettings 반영: 통과 |
| [policy-structure-standard-vocabulary-support](responses/policy-structure-standard-vocabulary-support.json) | 구조 ADVANCED + 어휘 STANDARD의 보조 수정 허용 | checkSettings 반영: 통과 |
| [policy-overlap-all-advanced](responses/policy-overlap-all-advanced.json) | 교정·개선 두 카드 유지, 둘 다 겹침 경고·검토 필요, 자동 배치 금지 | 동일 |
| [policy-overlap-standard-grammar-support](responses/policy-overlap-standard-grammar-support.json) | 문법 STANDARD여도 두 카드와 겹침 경고 유지 | checkSettings 반영: 통과 |
| [policy-grammar-sentence-suggestion](responses/policy-grammar-sentence-suggestion.json) | 능동태 전환을 문법 SENTENCE + RESTRUCTURE로 허용 | checkScope 반영: 통과 |
| [policy-grammar-clause-suggestion](responses/policy-grammar-clause-suggestion.json) | 관계절 축약을 문법 CLAUSE + REPLACE로 허용 | checkScope 반영: 통과 |
| [policy-content-summary-suggestion](responses/policy-content-summary-suggestion.json) | 기존 내용의 연속 문장 요약 허용 | checkScope 반영: 통과 |
| [policy-content-standard-structure-support](responses/policy-content-standard-structure-support.json) | 내용 요약에 필요한 구조 STANDARD 보조 수정 허용 | checkSettings/checkScope 반영: 통과 |
| [policy-standard-primary-suggestion-excluded](responses/policy-standard-primary-suggestion-excluded.json) | 대표 영역 STANDARD의 독립 제안 제외 | 동일 |
| [policy-disabled-support-excluded](responses/policy-disabled-support-excluded.json) | OFF 보조 영역을 포함한 제안 제외 | 동일 |
| [policy-grammar-sentence-correction-review](responses/policy-grammar-sentence-correction-review.json) | 문법 CORRECTION의 문장 전체 수정은 검토 대상 | 동일 |
| [policy-content-sentence-correction-review](responses/policy-content-sentence-correction-review.json) | 내용 CORRECTION의 문장 전체 수정은 검토 대상 | 동일 |
| [policy-vocabulary-sentence-suggestion-review](responses/policy-vocabulary-sentence-suggestion-review.json) | 어휘 제안의 문장 전체 수정은 검토 대상 | 동일 |
| [policy-grammar-sentence-group-suggestion-review](responses/policy-grammar-sentence-group-suggestion-review.json) | 문법 제안의 여러 문장 재구성은 검토 대상 | 동일 |
| [policy-corrected-baseline-anchor-review](responses/policy-corrected-baseline-anchor-review.json) | 교정본을 원문으로 신고하면 ANCHOR_NOT_FOUND | 동일 |
| [policy-sentence-replace-operation-review](responses/policy-sentence-replace-operation-review.json) | 문장 전체를 일반 REPLACE 동작으로 신고하면 검토 대상 | 동일한 거절 유지 |
| [policy-paragraph-boundary-review](responses/policy-paragraph-boundary-review.json) | 요약 범위를 넓혀도 빈 줄을 넘으면 검토 대상 | 동일 |

## 원문 카드와 baseline의 관계

대표 예제는 다음 세 문자열을 구분한다.

| 역할 | 문자열 |
| --- | --- |
| 실제 학생 원문 | He go home. He was tired. |
| 내부 검토용 가상 baseline | He went home. He was tired. |
| 선택적 최종 개선 예시 | He was tired when he went home. |

이 예시는 과거 시제와 같은 시점의 상황이라는 의도를 가정한 합성 자료다. 문맥 없이 `when`을 반드시 생성해야 한다는 품질 정답으로 사용하지 않는다. 서버도 시간 관계의 타당성을 의미적으로 판정하지 않는다.

- CORRECTION의 앵커는 원문의 `go`이고 교체문은 `went`다.
- SUGGESTION의 앵커는 원문의 `He go home. He was tired.` 전체다.
- 같은 줄의 전체 범위 시작점이 먼저이므로 `display_order`는 `[1, 0]`이다. CORRECTION 우선은 카드 한도 채택 순서이며 화면 정렬 순서와 구별한다.
- 두 카드 모두 최종 정책 단계에서만 겹침 경고가 생긴다. 따라서 `stage_expectations.location/edit`는 `requires_review=false`, `auto_place=true`이고 최종 카드 기대값은 반대다.
- 앵커를 baseline 문자열로 바꾸는 부정 예제도 함께 둬 원문 기준을 고정한다.

## 실행과 완료 기준

Java 21에서 실행한다. 실제 AI·DB·네트워크는 사용하지 않는다.

```powershell
.\gradlew.bat --offline test --tests "*domain.generation.contract.v1_1.*" spotlessCheck --continue
```

정책을 단계적으로 구현하는 동안 파서·자료 무결성·원문·좌표·동작 검사는 통과하고, 미구현 정책의 목표 테스트만 실패해야 한다. 실패를 피하려고 `@Disabled`를 추가하거나 새 기대값을 현재 동작으로 되돌리지 않는다.

기존 혼합 영역 단위 테스트에서 STANDARD 보조 영역을 무조건 제외하던 단언은 새 `policy-*` 허용 사례로 대체했다. 대표 STANDARD 제안 차단, OFF 보조 영역 차단, CORRECTION 범위 행렬은 유지한다.

후속 구현의 완료 기준은 7개 고정 응답 사례와 4개 범위 행렬의 정책 차이를 해결하면서 기존 검사와 나머지 경계 사례를 모두 통과시키는 것이다. 실제 모델이 기본 교정을 누락하지 않고 좋은 개선을 만드는지는 이후 별도 실험에서 확인한다.

### 테스트 작성 단계의 실행 결과

Java 21에서 위 명령으로 682개를 실행했다. 671개 통과, 11개 실패, 오류·건너뜀 0개이며 `spotlessCheck`는 통과했다.

- 실패 7개는 표의 STANDARD 보조 수정 허용·문법 및 내용 범위 확장 사례다.
- 실패 4개는 범위 행렬의 문법 CLAUSE/SENTENCE 및 내용 SENTENCE/SENTENCE_GROUP 허용 조합이다.
- 파서·Schema·DTO·자료 무결성·원문·좌표·수정 동작·프롬프트 조립 테스트는 모두 통과했다.
- 이 단계에서는 운영 검증기와 프롬프트를 변경하지 않아 정책 차이의 실패 11개를 의도적으로 남겼다.

### checkSettings 구현 단계의 실행 결과

제안의 STANDARD 차단 여부는 관련 영역 전체가 아니라 대표 영역 `area`로 판단하도록 변경했다. 대표 영역이 STANDARD이거나 관련 영역이 OFF인 제안은 계속 제외하며, 대표 영역과 문제 종류의 일치 검사도 유지한다. 보조 수정이 의미적으로 필요한지 판정하는 검사는 추가하지 않는다.

Java 21에서 같은 명령으로 682개를 실행했다. 674개 통과, 8개 실패, 오류·건너뜀 0개이며 `spotlessCheck`는 통과했다.

- 문법 STANDARD 보조 수정, 어휘 STANDARD 보조 수정, 문법 STANDARD 교정·구조 개선 카드 겹침의 3개 고정 응답 사례가 새로 통과했다.
- 대표 STANDARD의 혼합 제안 차단과 OFF 보조 영역 차단도 단위 테스트에서 확인했다.
- 내용 요약의 STANDARD 구조 보조 수정도 영역·레벨 단계는 통과하지만, 미구현 범위·동작 정책 때문에 검토 대상으로 남는다.
- 남은 실패는 문법·내용의 범위 확장 사례 4개와 범위 행렬 4개다. 따라서 전체 테스트 명령은 아직 실패한다.

### checkScope 구현 단계의 실행 결과

대표 영역이 ADVANCED이고 `feedback_type=SUGGESTION`인 경우에만 위 범위 행렬을 확장했다. CORRECTION과 STANDARD의 기존 범위, 어휘의 단어·구 제한, 문법의 문장 묶음 금지는 유지한다. 문장 재구성은 허용된 문장 범위에서만 RESTRUCTURE를 사용할 수 있고, 문장 전체 교체를 일반 REPLACE 동작으로 신고하면 계속 검토 대상으로 분류한다.

Java 21에서 v1.1 계약 테스트 689개와 라우팅 20개, HTTP 클라이언트 23개, 저장 61개를 실행했다. 총 793개 모두 통과했으며 실패·오류·건너뜀 0개다. `spotlessCheck`도 통과했다.

- 앞 단계에서 남긴 정책 차이 8개가 모두 해결됐다. 기존 18개 정책 예제와 20개 범위 행렬의 기대값은 변경하지 않았다.
- CORRECTION의 범위를 두 레벨 모두 유지하는 테스트 2개, STANDARD SUGGESTION의 범위를 확장하지 않는 테스트 2개, 허용된 범위라도 잘못된 동작을 거절하는 테스트 3개를 추가했다.
- 원문·좌표·수정 동작의 일관성·GUIDANCE·개수·겹침 검증은 변경하지 않았다.
- 프롬프트와 실제 AI 요청 횟수는 변경하지 않았으며, 프롬프트 정합성과 모델 품질 평가는 이후 단계다.

### 프롬프트 정합성 반영 단계의 실행 결과

`ai/prompt/v1.1`과 `ai/prompt/v1.1-compact`의 공통 원칙·출력 계약·수정 동작·앵커 지침과 ADVANCED 전용 4개 파일을 함께 수정했다. STANDARD 전용 8개 파일은 내용 변경 없이 유지했고, 응답 Schema·DTO·계약 ID·프로필 버전 상수·운영 Java 코드는 변경하지 않았다.

- 한 번의 응답 안에서 기본 오류 후보를 먼저 찾고 허용된 교정을 가상 적용한 baseline에서 추가 개선을 검토하도록 명시했다. baseline이나 분석 과정은 응답하지 않으며 모든 앵커는 실제 학생 원문을 사용한다.
- 대표 ADVANCED 영역의 개선을 완성하는 데 필요한 다른 선택된 STANDARD 영역의 보조 수정을 허용한다. 독립적으로 가치 있는 다른 영역의 선택적 개선은 그 영역도 ADVANCED여야 하며, OFF 영역은 계속 차단한다.
- CORRECTION은 기존 범위와 필요한 최소 수정을 유지한다. ADVANCED SUGGESTION의 범위와 RESTRUCTURE 조건은 구현된 검증기에 맞췄다.
- STANDARD와 기본 교정에서는 학생의 표현·문체 보존을 유지한다. ADVANCED SUGGESTION에서는 표현·문장 형태·문체를 바꿀 수 있으나 사실·핵심 의미·의도·근거 있는 관계는 보존한다.
- 교정 구간의 추가 SUGGESTION을 일괄 금지하던 지침을 제거했다. 교정 카드와 의미 있는 추가 개선 카드는 같은 원문에 연결할 수 있고, 겹침 경고는 서버가 처리한다.
- 완성된 개선 표현을 제공하고, 문맥 없는 어려운 동의어·강도 변화·인과관계·동시성·새로운 사건은 추가하지 않도록 구체화했다.
- 새 `primary_area`, `supporting_edits`, `corrected_baseline` 필드 없이 기존 12개 필드로 반환한다. 최대 7개와 CORRECTION 우선, GUIDANCE의 영어 10단어 제한은 유지한다.

두 프롬프트의 CORRECTION/SUGGESTION 범위 선언을 실제 검증기와 대조하고, 퇴역한 전역 금지 지침이 남아 있지 않은지 검사하는 테스트를 추가했다. 기존 81개 설정 조합 검사도 유지한다.

Java 21에서 v1.1 계약 테스트 693개와 라우팅·클라이언트·저장 회귀 테스트 104개, 총 797개가 모두 통과했다. 실패·오류·건너뜀 0개이며 `spotlessCheck`도 통과했다. 실제 모델은 호출하지 않았다.

프롬프트 조립 테스트는 다음 검토용 전문도 생성한다.

- `build/ai-prompt-preview/v1.1/default/all-advanced.txt`
- `build/ai-prompt-preview/v1.1/default/all-standard.txt`
- `build/ai-prompt-preview/v1.1/compact/all-advanced.txt`
- `build/ai-prompt-preview/v1.1/compact/all-standard.txt`

### 배포 전 버전·기존 작업 호환성 점검

[별도 점검 결과](../../../../../../docs/ai_test_docs/AI-ADVANCED-배포전-버전-호환성-점검.md)에 버전 식별자 재사용과 기존 작업의 호환 범위를 기록했다. 전체 1,347개 테스트와 포맷 검사는 통과했으나, 배포 전 신규 동작 리비전 기록과 구·신 프로필·검증 버전의 호환 처리가 필요하다. 점검에서 운영 버전 상수나 배포 로직을 임의로 변경하지 않았다.
