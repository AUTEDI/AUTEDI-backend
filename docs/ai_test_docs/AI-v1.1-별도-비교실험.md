# v1.1 별도 AI 응답 비교 실험

서비스 기본 경로를 v1으로 유지하면서 v1.1 프롬프트·Schema·파서·검증기를 연결하는 수동 실험이다. 실행 코드는 모두 `src/test`에 있고 일반 `test` 작업에서는 실제 AI를 호출하지 않는다. 이번 구현 검증도 로컬 가짜 HTTP 서버와 `--check`로만 수행했다. 실제 모델 품질·시간·토큰 비교는 아직 실행하지 않았다.

## 파일과 역할

| 파일 | 역할 |
| --- | --- |
| [TerraV11Comparison](../../src/test/java/com/ceos24/autedi/aiexperiments/TerraV11Comparison.java) | 옵션·비교 순서·입력 스냅샷·요청 기록·결과 저장을 관리하는 수동 main |
| [V11ComparisonInput](../../src/test/java/com/ceos24/autedi/aiexperiments/V11ComparisonInput.java) | manifest 검증, EXIF 방향 보정 PNG·확정 전사 읽기, 동일 입력·해시 보존 |
| [V11ComparisonClient](../../src/test/java/com/ceos24/autedi/aiexperiments/V11ComparisonClient.java) | Responses API에 이미지·전사·조립된 지시문·v1.1 Schema를 보내는 단일 호출기 |
| [V11ComparisonReport](../../src/test/java/com/ceos24/autedi/aiexperiments/V11ComparisonReport.java) | v1.1 파싱·전체 검증, 원본/검증 후 개수·위반·시간·토큰·수정 차이 보고 |
| [TerraV11ComparisonTest](../../src/test/java/com/ceos24/autedi/aiexperiments/TerraV11ComparisonTest.java) | loopback 가짜 HTTP 서버로 두 비교 모드·실패·시간 제한·기록·비호출 동작 검사 |
| [cases.json](../../src/test/resources/ai/experiments/v1.1/cases.json) | 문법 오류·개선 여지·좋은 글의 입력 목록과 사람 평가 기준 |
| [build.gradle](../../build.gradle) | 수동 실행 작업 `terraCompareV11` 등록 |

기존 서비스의 `TerraGenerationClient`, Worker, 저장·조회 API는 수정하지 않는다. 기존 실험의 `TerraCorrectionExample`도 그대로 둔다. 전사 API를 호출하거나 프론트 응답으로 변환하는 경로는 추가하지 않았다.

## 비교 조건

| 모드 | 바꾸는 값 | 고정하는 값 |
| --- | --- | --- |
| `LEVELS` 기본 | Standard / Advanced | 프롬프트 후보, 이미지, 확정 전사, 선택 영역, 모델, 추론 강도, 출력 한도, Schema, 검증기 |
| `PROMPTS` | DEFAULT / COMPACT | 레벨과 나머지 입력·호출 설정 |

`LEVELS`는 선택된 모든 영역을 Standard로 설정한 요청과 Advanced로 설정한 요청을 비교한다. 선택하지 않은 영역은 OFF다. 한 쌍 안에서는 정규화 PNG 바이트와 전사 입력이 동일하며 레벨별 상세 지시문만 달라진다. 두 모드 모두 사례·반복 회차에 따라 A/B, B/A 순서를 번갈아 배치한다. `plan.json`과 각 회차 기록에 실제 순서·버전·해시를 남긴다.

기본 모델은 조립기의 `gpt-6.1-terra`, 추론 강도 `medium`, 출력 한도 12,000, 전체 응답 제한 시간 180초, 이미지 `detail=high`, `service_tier=default`다. 모델·Schema·이미지 상세도는 이 실험 안에서 고정한다. 한 실험의 계획은 최대 40회이며 사례는 1~10개, 반복은 1~5회다.

## 실행

프로젝트 루트에서 Java 21로 실행한다. `--run`을 지정하지 않으면 항상 점검만 한다. `--check`에서는 키를 읽거나 HTTP 호출을 하지 않는다.

```powershell
# 입력·조립·Schema·비교 계획만 점검: 실제 호출 0회
.\gradlew.bat --offline terraCompareV11 --args="--check"

# 기본본·압축본 비교 계획, Advanced 고정: 실제 호출 0회
.\gradlew.bat --offline terraCompareV11 --args="--check --mode PROMPTS --level ADVANCED"

# 압축본에서 Standard·Advanced를 2회씩 비교하는 계획: 실제 호출 0회
.\gradlew.bat --offline terraCompareV11 --args="--check --variant COMPACT --repeats 2 --case grammar-error"
```

다음은 **추후 실제 호출용 예시**이며 이번 작업에서는 실행하지 않았다. `OPENAI_API_KEY`를 환경변수 또는 프로젝트 루트 `.env`에서 읽는다. `--run`을 붙이면 유료 요청이 실행된다.

```powershell
# 같은 문법 오류 입력으로 Standard / Advanced 2회 요청
.\gradlew.bat --offline terraCompareV11 --args="--run --case grammar-error"

# 확정된 별도 입력 세트로 레벨 비교
.\gradlew.bat --offline terraCompareV11 --args="--run --manifest C:/data/v11/cases.json"

# Advanced 고정, 기본본 / 압축본을 각 2회 요청
.\gradlew.bat --offline terraCompareV11 --args="--run --mode PROMPTS --level ADVANCED --case grammar-error --repeats 2"
```

| 옵션 | 의미 |
| --- | --- |
| `--manifest PATH` | 입력 목록. 기본값은 제공한 `cases.json` |
| `--case ID` | 한 사례만 선택. 생략하면 전체 |
| `--mode LEVELS\|PROMPTS` | 기본은 LEVELS |
| `--variant DEFAULT\|COMPACT` | LEVELS에서 고정할 후보. 기본 DEFAULT |
| `--level STANDARD\|ADVANCED` | PROMPTS에서 고정할 레벨. 기본 ADVANCED |
| `--repeats N` | 사례별 비교 쌍 반복 횟수. 기본 1, 최대 5 |
| `--reasoning VALUE` | none / low / medium / high / xhigh / max |
| `--max-output-tokens N` | 256~12,000. 기본 12,000 |
| `--timeout-seconds N` | 1~180. 본문 수신까지 포함 |
| `--output PATH` | 새 결과 디렉터리. 부모는 미리 있어야 하고 대상은 없어야 함 |

## 입력 세트

제공한 PNG 3개는 텍스트와 일치하도록 소프트웨어로 만든 **합성 인쇄체 점검 자료**다. 학생 손글씨 품질 검증 자료나 모델의 실제 정답 결과가 아니다. 실제 평가에는 같은 이미지에 대한 확인된 전사를 준비해 별도 manifest로 지정한다. OCR을 다시 실행하지 않는다.

manifest 예시:

```json
{
  "format_version": 1,
  "description": "확인된 손글씨 입력의 레벨 비교",
  "cases": [
    {
      "id": "grammar-error",
      "category": "GRAMMAR_ERROR",
      "image": "images/grammar-error.png",
      "transcription": "texts/grammar-error.txt",
      "page_number": 1,
      "areas": ["GRAMMAR_SPELLING"],
      "rubric": ["두 레벨 모두 기본 문법 오류를 수정한다."]
    }
  ]
}
```

경로는 manifest 디렉터리 기준이며 절대 경로도 허용한다. 필드는 예시와 정확히 일치해야 한다. `category`는 `GRAMMAR_ERROR`, `IMPROVABLE`, `CLEAN`, `areas`는 중복 없이 네 영역 enum에서 선택한다. ID는 영숫자로 시작하고 영숫자·`_`·`-`만 사용한다. 이미지 처리는 기존 `OcrTestImage`를 사용하며 입력 이미지 한도는 20MB, manifest·전사는 각각 1MB다.

UTF-8 BOM만 제거하고 전사의 앞뒤 공백·빈 줄·마지막 개행을 보존한다. CRLF/CR은 LF로 통일한다. EXIF 방향을 보정한 PNG와 정규화 전사를 한 번 준비하고 모든 비교 요청에 재사용한다. SHA-256 해시는 원본 업로드 파일이 아니라 **실제 비교에 사용한 PNG·전사** 기준이다.

## 결과와 판정

기본 출력은 Git에서 제외되는 `build/ai-experiments/v1.1/check-*` 또는 `run-*` 새 디렉터리다.

| 결과 파일 | 내용 |
| --- | --- |
| `plan.json` | 조건·사례·입력 경로·해시·버전·예정 요청 수 |
| `comparison.json` / `comparison.md` | 전체 상태, 시도 수, 각 회차, 수정 차이, 조건별 시간·사용량, 사람 평가 체크 목록 |
| `사례/image.png`, `transcription.txt` | 실제 비교 입력 스냅샷 |
| `사례/r01-standard/prompt.json` | 조립한 지시문·전사 입력·원본 Schema·버전 |
| `.../transport-schema.json` | Structured Outputs 전송용 Schema |
| `.../receipt.json` | 실행 전후 상태·순서·해시·시각·시도 여부 |
| `.../envelope.json`, `output.txt` | HTTP 200의 파싱 가능한 API 봉투, 추출한 AI 텍스트. 수신한 경우에만 생성 |
| `.../result.json` | 원본 개수, 서버 분류·집계, 항목별 검증 사유·원문·좌표·표시 순서, 시간·토큰 |

전송용 Schema에서는 루트 `$schema`·`$id`와 `issue_kinds.uniqueItems`를 제거한다. 원본 v1.1 Schema를 덮어쓰지 않으며 중복 종류는 서버 파서가 계속 검사한다. 요청 형식은 [Responses API](https://developers.openai.com/api/reference/resources/responses/methods/create)와 [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)를 따른다.

`VALIDATED`는 파서와 검증기가 실행됐다는 뜻이며 모든 항목이 유효하거나 품질이 합격했다는 뜻이 아니다. 다음을 함께 본다.

- `rawCounts`, `rawStandardSuggestionCount`: 서버에서 제외하기 전 실제 AI 판단. Standard의 SUGGESTION 위반을 숨기지 않는다.
- `validatedCounts`, `issueCounts`, `candidates`: 검증 후 일반 카드·확인·제외, 위반 종류, 각 항목의 원문·좌표·수정 동작 문제.
- `comparisons`: 두 응답이 파싱·검증된 경우 CORRECTION의 동작·영역·anchors·replacement·guidance·edit가 정확히 같은지 비교. 사유·좌표는 비교에서 제외하며 의미가 같은 다른 수정안은 사람이 판단한다.
- `timingByLabel`, `usageByLabel`: API 완료 응답의 평균·중앙값·최소·최대 시간과 토큰 집계. 파싱 실패도 API 완료 응답에는 포함하며 API 실패는 제외한다. 개별 실패의 시간·수신 사용량은 개별 결과에 남는다.

개별 결과에는 입력·출력·합계·추론·캐시 읽기·캐시 쓰기와 기간별 쓰기 토큰을 수신한 범위에서 기록한다. 누락은 `null`이며 0으로 추정하지 않는다. 집계의 `knownSamples`·`sumKnown`은 받은 값만 센다. `total`은 해당 조건의 모든 완료 응답에서 값이 있을 때만 기록한다. 추론은 출력에 포함되므로 다시 더하지 않는다. 달러 비용 자동 계산은 추가하지 않았다. 실제 가격·캐시 조건에 맞춰 수신 토큰으로 계산해야 한다.

최종 품질은 manifest의 기준으로 확인한다. 기본 오류의 공통 수정, Standard의 불필요한 제안 억제, Advanced의 실질적인 개선, 이미 좋은 글의 유지, 의미·목소리 보존을 검토한다. `qualityVerdict=NOT_AUTOMATICALLY_EVALUATED`를 유지한다. 짧은 1회 비교만으로 속도나 품질 동등성을 판정하지 않는다. 캐시·순서·출력 길이도 함께 확인한다.

## 실패와 재실행

자동 재시도는 없다. 호출 직전 `STARTED` 기록을 저장한다. HTTP 실패·시간 초과·미완료·거부 등이 발생하면 원인을 기록하고 남은 요청을 `NOT_RUN`으로 멈춘다. CLI는 API 실패 후 오류 종료한다. AI 텍스트의 v1.1 파싱 실패는 `PARSE_FAILED`로 원문을 남기고 다음 비교 요청을 진행한다.

기존 출력 디렉터리는 덮어쓰거나 재개하지 않는다. 중단된 실행은 기록을 확인하고 새 디렉터리로 실행한다. 키·인증 헤더·base64 이미지 요청 본문·HTTP 오류 원문은 보고서에 저장하지 않는다. 이미지·전사·AI 응답은 지정한 결과 디렉터리에 저장된다.

로컬 검증:

```powershell
.\gradlew.bat --offline test --tests "*TerraV11ComparisonTest" --tests "*domain.generation.contract.v1.*" --tests "*domain.generation.contract.v1_1.*" spotlessJavaCheck
```

서비스 버전 전환·저장·프론트 매핑은 별도 후속 작업이다. 조립과 계약은 [v1.1 조립기](AI-첨삭-프롬프트-v1.1-조립.md), [v1.1 응답 계약](AI-공통-응답-계약-v1.1.md)을 따른다.
