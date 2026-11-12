# v1.1 첨삭 프롬프트 조립기

- 기준: [AI 공통 응답 계약 v1.1](AI-공통-응답-계약-v1.1.md)
- 리소스: [기본본](AI-첨삭-프롬프트-v1.1-파일.md), [압축 비교 후보](AI-첨삭-프롬프트-v1.1-압축본.md)
- 범위: 확정 전사·영역별 설정으로 지시문·전사 입력·v1.1 Schema와 버전 정보를 묶는 독립 Java 코드
- 연결 상태: 실제 모델 호출·이미지 첨부·HTTP·Worker·저장·API는 변경하지 않았다. 서비스 생성 경로는 기존 v1을 유지한다.

## 1. 추가 파일

| 파일 | 역할 |
| --- | --- |
| [AiCorrectionPromptComposer](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/prompt/AiCorrectionPromptComposer.java) | 선택한 후보의 공통·영역 파일을 합치고 확정 전사 입력과 v1.1 Schema를 연결 |
| [AiCorrectionPromptVariant](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/prompt/AiCorrectionPromptVariant.java) | DEFAULT / COMPACT 리소스 루트와 정확한 프롬프트 버전명 관리 |
| [AiCorrectionPrompt](../../src/main/java/com/ceos24/autedi/domain/generation/contract/v1_1/prompt/AiCorrectionPrompt.java) | 모델·계약·버전·후보·설정·지시문·입력·Schema를 보존하는 불변 묶음 |
| [AiCorrectionPromptComposerTest](../../src/test/java/com/ceos24/autedi/domain/generation/contract/v1_1/prompt/AiCorrectionPromptComposerTest.java) | 두 후보 각각 81개 설정 조합, 입력·Schema·버전·설정 독립성, 고정 응답 검증과 미리보기 |

기존 v1 조립기나 DTO를 상속·변환하지 않는다. 계약 식별자·Schema 경로는 v1.1의 `AiCorrectionContract`, 영역·레벨·전사는 v1.1의 `AiValidationContext`를 사용한다. 모델 값은 기존과 같은 `gpt-6.1-terra`를 유지한다.

## 2. 사용 방법

```java
AiValidationContext context = AiValidationContext.fromText(
        1,
        confirmedTranscription,
        Map.of(
                AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD,
                AiArea.CONTENT_SUMMARY, AiCorrectionLevel.ADVANCED));

AiCorrectionPromptComposer composer = new AiCorrectionPromptComposer();
AiCorrectionPrompt normal = composer.compose(context);
AiCorrectionPrompt compact = composer.compose(context, AiCorrectionPromptVariant.COMPACT);
```

`compose(context)`는 DEFAULT를 선택한다. COMPACT는 명시적으로 지정해야 하며, 비교 때문에 기본값을 압축본으로 바꾸지 않았다.

| 후보 | 루트 | promptVersion |
| --- | --- | --- |
| DEFAULT | `/ai/prompt/v1.1/` | `AUTEDI-CORRECTION-PROMPT-v1.1` |
| COMPACT | `/ai/prompt/v1.1-compact/` | `AUTEDI-CORRECTION-PROMPT-v1.1-COMPACT` |

두 결과의 model·contractId·selectedAreas·inputText·responseSchema는 같다. 지시문과 후보·프롬프트 버전만 다르다. 필수 리소스가 없으면 실패하며 다른 후보나 v1으로 조용히 대체하지 않는다. 결과 객체도 후보와 버전명이 다르면 생성을 거절한다.

이 선택은 Java 조립기 API에만 있다. 실제 생성 요청이나 환경설정에 후보 선택 필드를 추가한 것은 아니다.

## 3. 지시문 조립 순서

1. 선택 후보의 `common-principles.txt`
2. 아래 AREA 블록만 활성 영역이며 레벨을 독립적으로 적용한다는 동적 선언
3. 영역 enum 순서대로 활성 영역의 `AREA / LEVEL` 제목, 한도, 해당 상세 파일 하나
4. `output-contract.txt`
5. `anchor-boxes.txt`
6. `edit-operations.txt`

영역 순서는 GRAMMAR_SPELLING → VOCABULARY_EXPRESSION → SENTENCE_STRUCTURE → CONTENT_SUMMARY다. Map 삽입 순서에 따라 결과가 달라지지 않는다. 파일명 변환에는 `Locale.ROOT`를 사용한다.

한도는 v1.1 검증기의 `AiCorrectionLevel.maximumCount()`를 사용한다. 두 레벨 모두 페이지·대표 영역별 REPLACE+GUIDANCE 최대 7개, REVIEW 제외, 목표·최소 없음이다. v1의 권장 수나 Standard 최대 5개를 복사하지 않는다.

OFF 영역의 상세 파일은 읽거나 붙이지 않는다. 공통 규칙과 Schema에 네 영역의 이름이 나오는 것은 허용값 설명이며 활성화를 의미하지 않는다. 모든 영역이 OFF라면 일반 항목을 금지하고 실제 원문 판독 문제의 REVIEW 또는 빈 items만 허용하는 선언을 추가한다.

기본본·압축본에 같은 제목·동적 설정 문구를 사용한다. 기존 길이 비교 문서의 표는 이 문구를 제외한 파일 본문 기준이며 실제 `instructions`는 그보다 길다.

## 4. 전사·Schema·검증 문맥

확정 전사문을 지시문 안에 삽입하지 않고 별도 `inputText`로 직렬화한다.

```json
{
  "page_number": 1,
  "transcription_lines": [
    {"line_id": 1, "text": "He go home."},
    {"line_id": 2, "text": ""},
    {"line_id": 3, "text": "Volunteering is good for students."},
    {"line_id": 4, "text": ""}
  ]
}
```

페이지 번호와 전사 순서는 context를 그대로 사용하고 빈 줄·앞뒤 공백·따옴표·유니코드를 보존한다. CRLF/CR의 LF 정규화는 기존 `AiLocationContext`가 담당한다. 입력에는 후보 이름·프롬프트 버전·Schema를 섞지 않아 두 후보에서 같은 입력을 사용한다.

`responseSchema`는 `/ai/contract/v1.1/correction-response.schema.json`을 읽은 문자열이다. 실제 API의 `text.format` 등 HTTP 봉투는 조립하지 않는다. `model`, `contractId`, `promptVersion`, `variant`, `selectedAreas`도 서버가 사용·보존할 메타데이터이며 AI 출력 필드로 추가하지 않는다.

추후 AI 응답은 같은 페이지·확정 전사·영역 설정 스냅샷으로 파싱·검증해야 한다.

```java
AiCorrectionResponse response = new AiCorrectionResponseParser().parse(rawJson);
AiValidationResult checked = new AiCorrectionResponseValidator().validate(response, context);
```

이는 연결 예시다. 이번 테스트에서는 사람이 만든 고정 응답을 사용하며 실제 AI 응답을 생성하지 않는다. 이미지 해시·전사 ID의 동일성은 향후 생성 경로 연결 시 기존 검증과 함께 보장해야 한다.

## 5. 테스트·미리보기

프로젝트 루트, Java 21:

```powershell
.\gradlew.bat --offline test --tests "*domain.generation.contract.v1.*" --tests "*domain.generation.contract.v1_1.*" spotlessJavaCheck
```

- DEFAULT / COMPACT 각각 OFF·Standard·Advanced의 3^4=81개 설정, 총 162개 조립 조합
- 공통 파일 4개와 영역 상세 파일의 누락·반대 레벨·다른 후보 혼입 방지
- 영역별 최대 7개와 전체 OFF 처리, 조립 순서의 결정성
- 한 영역의 레벨 변경이 다른 영역 상세 규칙·입력·Schema를 바꾸지 않는지 확인
- 기본값·후보·버전·모델·계약의 연결 및 불변 설정 스냅샷
- 빈 줄·공백·특수문자·프롬프트처럼 보이는 학생 원문을 별도 JSON으로 보존
- v1.1 Schema 선택, 단일 줄 문법 수정·다중 줄 문장 재구성 고정 응답의 기존 파서·검증기 호환성

테스트가 만드는 미리보기는 다음 네 파일이다. Git 제외 build 경로의 합성 자료이며 재실행으로 생성할 수 있다.

- `build/ai-prompt-preview/v1.1/default/mixed-settings.md`
- `build/ai-prompt-preview/v1.1/default/mixed-settings.json`
- `build/ai-prompt-preview/v1.1/compact/mixed-settings.md`
- `build/ai-prompt-preview/v1.1/compact/mixed-settings.json`

문법 Standard·내용 Advanced·어휘와 문장 구조 OFF 설정이며 두 후보의 전사·Schema는 같다. JSON에는 `requestSent=false`와 프롬프트 묶음만 들어 있다. 이미지·키·인증 헤더·전송 가능한 요청 본문은 없다. 기존 v1 미리보기 경로를 덮어쓰지 않는다.

실제 모델 품질·시간·토큰·캐시는 이 테스트로 측정하지 않는다. 다음 연결 단계에서 같은 입력을 사용해 기본본·압축본을 비교하고 실제 호출·저장 경로를 전환해야 한다.
