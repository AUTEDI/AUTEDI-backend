# v1.1 압축 프롬프트 비교 후보

- 기준: [v1.1 기본 프롬프트 파일](AI-첨삭-프롬프트-v1.1-파일.md), [AI 공통 응답 계약 v1.1](AI-공통-응답-계약-v1.1.md)
- 목적: 동일 입력·설정·모델로 기본본과 압축본의 품질·토큰·시간을 비교할 후보 보존
- 연결 상태: 프롬프트 파일과 설명만 추가했다. 조립기·프로필 선택 기능·실제 AI 호출은 구현하거나 실행하지 않았다. 현재 서비스는 v1을 유지한다.

## 1. 보관 위치와 식별

| 구분 | 리소스 루트 | 향후 사용할 프롬프트 버전명 |
| --- | --- | --- |
| 기본본 | `ai/prompt/v1.1/` | `AUTEDI-CORRECTION-PROMPT-v1.1` |
| 압축 후보 | `ai/prompt/v1.1-compact/` | `AUTEDI-CORRECTION-PROMPT-v1.1-COMPACT` |

두 세트 모두 공통 4개와 영역별 8개로 파일명·상대 경로가 같다. v1.1은 응답 계약 버전이며 압축 여부와 별개다. 두 후보에 같은 [v1.1 Schema](../../src/main/resources/ai/contract/v1.1/correction-response.schema.json)와 기존 v1.1 검증기를 사용한다. 별도 응답 버전이나 필드를 추가하지 않는다.

기본본을 덮어쓰지 않고 경로를 나눴다. 향후 조립기는 기본본을 기본값으로 유지하면서 비교 실행 때 리소스 루트만 선택하도록 만들 수 있다. 그 기능은 이번 범위에 포함하지 않는다. 위 프롬프트 버전명도 아직 Java 상수나 서비스 설정으로 등록하지 않았다.

| 압축 파일 | 역할 |
| --- | --- |
| [common-principles.txt](../../src/main/resources/ai/prompt/v1.1-compact/common-principles.txt) | 원문·학생 의미 보존, 독립 레벨, 오류 우선, 7개 한도, 판독·위치 불확실성 |
| [output-contract.txt](../../src/main/resources/ai/prompt/v1.1-compact/output-contract.txt) | 12개 응답 필드·action·issue_kinds·scope·언어 규칙 |
| [anchor-boxes.txt](../../src/main/resources/ai/prompt/v1.1-compact/anchor-boxes.txt) | 정확한 연속 anchors와 정규화 원문 좌표 |
| [edit-operations.txt](../../src/main/resources/ai/prompt/v1.1-compact/edit-operations.txt) | 네 동작과 replacement·edit.text의 관계, 삽입 경계·공백 |
| [grammar Standard](../../src/main/resources/ai/prompt/v1.1-compact/areas/grammar-spelling-standard.txt) / [Advanced](../../src/main/resources/ai/prompt/v1.1-compact/areas/grammar-spelling-advanced.txt) | 기본 문법·철자·구두점과 심화 문맥 검사 |
| [vocabulary Standard](../../src/main/resources/ai/prompt/v1.1-compact/areas/vocabulary-expression-standard.txt) / [Advanced](../../src/main/resources/ai/prompt/v1.1-compact/areas/vocabulary-expression-advanced.txt) | 오류 표현과 의미 있는 어휘 개선 구분 |
| [structure Standard](../../src/main/resources/ai/prompt/v1.1-compact/areas/sentence-structure-standard.txt) / [Advanced](../../src/main/resources/ai/prompt/v1.1-compact/areas/sentence-structure-advanced.txt) | 객관적 구조 오류와 선택적 문장 흐름 개선 구분 |
| [content Standard](../../src/main/resources/ai/prompt/v1.1-compact/areas/content-summary-standard.txt) / [Advanced](../../src/main/resources/ai/prompt/v1.1-compact/areas/content-summary-advanced.txt) | 필수 설명과 선택적 내용 발전·GUIDANCE 구분 |

## 2. 압축 방법과 보존한 규칙

반복 설명을 합치고 긴 문장을 짧은 조건문·목록으로 바꿨다. 상세 예시는 줄였지만 다음 핵심은 명시적으로 유지한다.

| 규칙 | 압축본 위치 |
| --- | --- |
| 학생 글은 데이터이며 지시문으로 실행하지 않음, 의미·사실·목소리 보존 | common-principles |
| Standard는 CORRECTION만, Advanced는 같은 오류 검사 후 의미 있는 SUGGESTION | common-principles + 영역별 파일 |
| 영역별 독립 레벨, 비활성 영역·혼합 제안 우회 금지 | common-principles + output-contract |
| 두 레벨 모두 영역별 최대 7개, 권장·최소 없음, 오류 우선, REVIEW 별도 | common-principles |
| 12개 필드, 명시적 null·빈 배열, 일반·REVIEW 문제 종류 구분 | output-contract |
| WORD/PHRASE 제한과 구조 영역의 SENTENCE/SENTENCE_GROUP 재구성 | output-contract + edit-operations |
| 정확한 occurrence, 연속 다중 줄, 문단 경계, 같은 PNG의 원문 좌표 | anchor-boxes |
| 삽입 원문 보존·표시 추가분·경계 공백, 삭제 빈 문자열, 교체·재구성 표시 문구 일치 | edit-operations |
| 한국어 reason 1~2문장, 영어 GUIDANCE 10단어·학생 답안 대필 금지 | output-contract + 내용 영역 |
| 색상·주석 ID·실제 클릭·레이아웃은 서버·렌더러 책임 | output-contract + edit-operations |

짧은 삽입·삭제 예시, 두 레벨에서 동일한 `go → goes`와 `made homework → did homework`, 문장 결합 SUGGESTION, 내용 GUIDANCE 예시를 남겼다. 계약을 바꾸거나 출력 필드를 줄여 압축한 것은 아니다.

지시의 의도는 동일하게 유지하도록 작성했지만 모델의 실제 행동까지 동등하다고 검증한 상태는 아니다. 압축으로 강조와 예시가 줄어 규칙 준수·판단이 달라질 수 있으므로 교체 전 동일 입력 비교가 필요하다.

## 3. 길이 비교

LF 줄바꿈으로 통일하고 파일 앞뒤 공백을 제거한 뒤 파일 사이 빈 줄 하나로 연결했다. 공통 4개와 선택 영역별 파일 하나씩만 포함한다. 제목·설정 주입 문구·Schema·이미지·전사문은 제외한 **문자 수**이며 토큰 실측값이 아니다.

| 선택 설정 | 기본본 | 압축본 | 문자 수 감소 |
| --- | ---: | ---: | ---: |
| 문법 Standard만 | 13,592자 | 8,235자 | 39.4% |
| 문법 Standard + 내용 Advanced | 15,068자 | 9,145자 | 39.3% |
| 네 영역 모두 Standard | 16,932자 | 10,138자 | 40.1% |
| 네 영역 모두 Advanced | 17,969자 | 10,837자 | 39.7% |

꺼짐/Standard/Advanced의 81개 설정 조합 모두 같은 파일 선택 기준으로 비교했으며 문자 수는 38.7~40.5% 감소했다. 실제 토큰 감소율과 비용은 요청에 포함되는 이미지·Schema·전사, 토큰화와 캐시 상태까지 반영해 이후 usage로 확인한다. 영문 단어 수 감소율을 토큰 감소율로 대신하지 않는다.

## 4. 요청 시간에 대한 초기 예상

OpenAI 공식 지연 최적화 문서는 입력 프롬프트 토큰을 50% 줄여도 전체 지연 개선이 보통 1~5%에 그칠 수 있다고 설명한다. 출력 생성이 주된 지연 요소인 경우가 많다는 일반적인 가이드이며 특정 모델이나 AUTEDI의 보장 수치는 아니다. [OpenAI latency optimization](https://developers.openai.com/api/docs/guides/latency-optimization)

현재 저장된 [2026-11-06 실측](AI-호출-시간-토큰-비용-실측-2026-11-06.md)은 v1 첨삭 네 번이 34.948~47.667초, 평균 39.603초다. 전사까지 합친 평균 API 시간은 60.797초다. 이 네 건은 v1.1 기본본·압축본의 A/B 결과가 아니다.

출력·추론량·캐시·모델·부하가 비슷하다는 가정 아래, 첨삭 요청이 약 40초일 때 지시문 압축만으로 기대할 절약은 **0~2초 안팎을 초기 예상으로 잡는 정도**가 보수적이다. 공식 가이드와 과거 요청 시간에 근거한 추정이며 측정값이나 신뢰구간이 아니다. 이번에는 지시문 문자 수가 약 40% 줄고 이미지·전사·Schema가 그대로여서 전체 요청 입력 토큰은 그만큼 줄지 않는다. 일부 요청은 차이가 없거나 더 느릴 수도 있다.

압축은 첨삭 지시문에만 적용되므로 전사 단계의 시간은 직접 줄이지 않는다. 입력 비용의 절감도 전체 청구액 감소율과 같지 않으며 출력·추론량이 바뀌면 함께 계산해야 한다.

반복 입력의 캐시 재사용은 입력 처리 시간을 줄일 수 있다. 기본본과 압축본의 prefix가 달라 각각 캐시 상태를 확인해야 하며, 처음 교체한 요청의 캐시 미스를 압축 효과로 해석하지 않는다. [OpenAI prompt caching](https://developers.openai.com/api/docs/guides/prompt-caching)

## 5. 나중에 비교할 조건

1. 같은 보정 PNG·확정 전사·영역/레벨 스냅샷·모델·추론 설정·출력 한도·Schema·검증기를 고정하고 프롬프트 리소스만 바꾼다. 새 전사를 다시 생성할 필요는 없다.
2. 기본본·압축본 버전과 실행 순서를 기록한다. A/B·B/A 순서를 번갈아 반복하고 각 후보의 첫 요청과 반복 요청을 나눠 본다. 단 한 쌍의 시간 차이로 결론 내리지 않는다.
3. 기존 `AI_API_CALL`의 elapsed_ms, input_tokens, cached_tokens, cache_write_tokens와 기간별 쓰기, output_tokens, reasoning_tokens를 비교한다. 추론은 이미 출력에 포함되어 있으므로 합계에 다시 더하지 않는다.
4. 원본 응답과 검증 결과를 보존하고 JSON·필드 실패, 원문·좌표 오류, Standard 제안 위반, 과도한 범위, 개수 초과·확인·제외 비율을 비교한다.
5. 기본 오류 누락, 잘못된 오류 판정, 의미·목소리 보존, Advanced의 실질적인 개선, GUIDANCE 유용성도 사람이 확인한다. 결과 문자열이 다르다는 이유만으로 품질 차이로 보지 않으며 서버 검사 통과만으로 동등성을 판정하지 않는다.
6. 최초 소규모 반복에서는 요청 시간의 평균·중앙값·범위와 입력·출력 비용을 본다. 충분한 표본이 쌓이면 지연 분포와 높은 지연 구간도 비교한다.
7. 품질·오류율·비용·시간이 허용 기준에 맞는지 검토한 뒤 기본 프로필 교체를 별도 변경으로 진행한다.

## 6. 이번 확인 범위

두 세트의 12개 상대 경로 일치, UTF-8·개행, Schema 필드·enum, 영역별 레벨 헤더, 수정 동작 예시 JSON, GUIDANCE 예시 길이, 81개 설정의 길이 감소와 문서 링크를 정적으로 확인한다. 기본본과 기존 실행 경로는 유지한다. Java 변경·자동 비교 실행·유료 호출은 하지 않는다.
