# test11 손글씨 전사와 v1.1 실제 요청

`test_data/test11.jpg`를 직접 눈으로 읽어 [test11-transcription.txt](../../src/test/resources/ocr/test11-transcription.txt)에 저장했다. OCR API는 호출하지 않았다. 첨삭 대상으로 사용할 본문 16줄을 전사했으며 인쇄된 양식·이름·책 제목·날짜는 제외했다. 원본 사진은 그대로 사용한다.

## 전사 기준

- 원문의 철자·문법·시제를 고치지 않는다. `hims`, `advanture`, `escapes`, `flews`, `ballon`, `thems`, `everyones`, `pice`, `starts`, `scares`, `aways` 등을 그대로 남겼다.
- 본문의 실제 줄바꿈을 유지한다. 단어 사이의 수기 간격은 공백 하나로 나타낸다. 마지막 파일 개행으로 생기는 빈 전사 줄도 기존 입력 처리 방식대로 보존된다.
- 2행 `gather` 뒤의 덧쓴 획은 추가 `r`로 읽어 `gatherr`로 기록했다. 이 부분은 평가할 때 원본과 함께 확인한다.
- 이미지 안의 책 제목·이름·날짜도 모델에게 보이지만, 전사와 평가 대상은 본문으로 한정한다. 사진을 자르거나 본문을 다시 쓰지 않는다.

## 입력 설정

[test11-cases.json](../../src/test/resources/ai/experiments/v1.1/test11-cases.json)은 기존 합성 입력 목록과 분리한 실험 설정이다.

| 항목 | 값 |
| --- | --- |
| 사례 ID | test11 |
| 이미지 | 프로젝트 상위 `test_data/test11.jpg` |
| 전사 | 테스트 리소스 `ocr/test11-transcription.txt` |
| 영역 | 문법·철자, 어휘·표현, 문장 구조, 내용·요약 모두 선택 |
| 기본 비교 | 네 영역 모두 Standard인 요청 / 네 영역 모두 Advanced인 요청 |
| 기본 예정 요청 수 | 2회 |
| 전사 API 요청 | 0회 |

이미지 경로는 manifest 위치를 기준으로 프로젝트 상위 `test_data`를 참조한다. 사진을 다른 환경으로 옮기면 이 경로를 조정한다. 전사를 다시 편집하면 이후 실행의 전사 해시와 anchors 기준도 달라진다.

## 점검 후 실제 요청

PowerShell에서 프로젝트 루트로 이동하고 Java 21을 사용한다.

```powershell
Set-Location 'C:\study\CEOS\AUTEDI\autedi'
$env:JAVA_HOME = 'C:\Users\82108\.jdks\temurin-21.0.11-1'
```

먼저 입력과 두 조건의 프롬프트만 생성한다. 키를 읽거나 실제 AI 요청을 보내지 않는다.

```powershell
.\gradlew.bat --offline terraCompareV11 --args="--check --manifest src/test/resources/ai/experiments/v1.1/test11-cases.json"
```

출력 경로의 `plan.json`에서 `plannedCalls=2`, 네 영역, 입력 이미지·전사 경로를 확인한다. 각 조건의 `prompt.json`에는 동일한 전사와 해당 레벨의 지시문이 들어 있다. 정규화 PNG는 EXIF 방향 6을 보정한 세로 이미지이며 두 조건에서 동일한 바이트를 사용한다.

실제 요청을 보내려면 프로젝트 루트 `.env` 또는 환경변수에 `OPENAI_API_KEY`를 설정하고 다음 명령을 실행한다. **아래 명령은 실제 API를 호출하며 비용이 발생한다.** 이번 자료 준비 작업에서는 실행하지 않았다.

```powershell
.\gradlew.bat --offline terraCompareV11 --args="--run --manifest src/test/resources/ai/experiments/v1.1/test11-cases.json"
```

실행기는 같은 이미지·확정 전사로 Standard 1회와 Advanced 1회를 순차 요청한다. 각 응답을 v1.1 파서와 원문·좌표·수정 동작·영역·레벨·개수 검증기로 검사하고 결과를 저장한다. HTTP 실패·시간 초과·미완료·거부가 발생하면 남은 요청을 멈추며 자동 재시도하지 않는다.

`--offline`은 Gradle 의존성 다운로드 설정이다. `--run`이 붙으면 AI API 네트워크 요청은 실행된다.

## 응답 확인

콘솔에 출력되는 새 `build/ai-experiments/v1.1/run-*` 디렉터리를 연다.

| 파일 | 확인 내용 |
| --- | --- |
| `comparison.md` | 두 레벨의 요약·시간·토큰·평가 체크 목록 |
| `comparison.json` | 상태·요청 수·조건별 집계·공통/상이한 원본 CORRECTION |
| `test11/image.png`, `transcription.txt` | 두 요청에 실제로 사용한 입력 |
| `test11/r01-standard/prompt.json` | Standard 지시문·전사·Schema |
| `test11/r01-advanced/prompt.json` | Advanced 지시문·전사·Schema |
| 각 회차의 `output.txt` | 실제 AI의 첨삭 JSON 원문. 응답에서 텍스트를 추출한 경우 생성 |
| 각 회차의 `result.json` | 원본 개수·검증 후 카드/확인/제외·항목별 위반·시간·토큰 |
| 각 회차의 `envelope.json` | 파싱 가능한 HTTP 200 API 응답 봉투 |

Standard의 `rawStandardSuggestionCount`, 두 레벨의 기본 오류 누락·차이, Advanced의 선택적 개선 유용성, 의미 보존을 확인한다. `VALIDATED`는 파서와 검증기가 실행됐다는 의미이며 품질 합격을 뜻하지 않는다. 가격 자동 계산은 하지 않으며 받은 시간·토큰을 기록한다.

## 기본본·압축본 비교

레벨을 Advanced로 고정하고 후보만 비교하려면 다음 명령을 사용한다. 정상 진행 시 기본본·압축본 각 1회, 총 2회 요청한다.

```powershell
.\gradlew.bat --offline terraCompareV11 --args="--run --manifest src/test/resources/ai/experiments/v1.1/test11-cases.json --mode PROMPTS --level ADVANCED"
```

전체 옵션과 실패 처리: [v1.1 별도 비교 실험](AI-v1.1-별도-비교실험.md).
