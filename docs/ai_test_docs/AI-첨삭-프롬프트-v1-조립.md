# 첨삭 프롬프트 v1 — 선택 영역·수준별 조립

기준 계약은 [AUTEDI-AI-CONTRACT-v1](AI-공통-응답-계약-v1.md),
프롬프트 버전은 `AUTEDI-CORRECTION-PROMPT-v1`이다.
모델명은 기존 `gpt-6.1-terra`를 유지한다. 계정의 이용 가능 여부나 실제 응답 품질을 검증한 것은 아니다.

## 이번 범위

기존의 오류 보존 전사 → 같은 EXIF 보정 PNG와 이번 전사문으로 첨삭하는 두 단계를 유지한다.
새 코드는 전사가 완료된 뒤 사용할 **두 번째 첨삭 단계의 프롬프트**를 조립한다.
전사 실험, 기존 첨삭 실험의 edits 형식·프롬프트 및 서비스 Mock은 변경하지 않았다.
새 instructions는 items 형식의 계약 v1용이므로 기존 edits 실험에 그대로 주입하면 안 된다.

HTTP 요청, 이미지 첨부, 키 로딩, API 호출, DB, Controller, 실제 생성 흐름에는 연결하지 않았다.

## 조립 순서

1. 공통 첨삭 원칙
2. 선택 영역과 독립적인 수준 적용 원칙
3. 선택한 영역마다 해당 Standard 또는 Advanced 상세 규칙과 카드 수 제한
4. 계약 v1의 출력·action별 조합·문제 종류·설명 규칙
5. 한 줄 anchor·occurrence·boxes 기술 규칙

입력 전사문은 instructions에 끼워 넣지 않고 별도 JSON 입력으로 직렬화한다.
확정된 context의 줄 순서·빈 줄·공백을 그대로 유지하고 1부터 line_id를 부여한다.
context에는 요청에 사용할 전사문과 설정을 넣고, 이후 검증에도 같은 context를 사용한다.

## 주요 파일

- `AiCorrectionPromptComposer`: 선택 설정으로 instructions·번호가 붙은 입력·기존 JSON Schema를 묶는다.
- `AiCorrectionPrompt`: 모델·계약·프롬프트 버전·선택 설정·instructions·inputText·responseSchema를 보존한다.
- `src/main/resources/ai/prompt/v1/common-principles.txt`: 의미·사실·학생 목소리 보존, 오류와 개선 구분, OCR 불일치 처리.
- `output-contract.txt`: REPLACE/GUIDANCE/REVIEW, 명시적 null, 한국어 reason, 영어 10단어 이내 guidance 등.
- `anchor-boxes.txt`: 한 줄 원문, 겹치지 않는 occurrence, 서버의 UTF-16 계산, 전체 보정 PNG 기준 사각형 좌표.
- `areas/`: 네 영역 × 두 수준, 총 8개의 상세 규칙.

## 수준 적용 정책

수준은 학생의 영어 실력 등급이 아니라 영역별 AI 개입 강도다.
[서비스 설명서](../AUTEDI%20AI%20첨삭%20서비스%20설명서.md)와
[기능명세서](../AUTEDI%20AI%20첨삭%20서비스%20기능명세서(최종).md)의 원문 보존 기준을 함께 반영했다.
Standard에서도 필요한 제한적 개선 제안을 허용하며, Advanced에서도 명확한 오류 수정과 핵심 의미 보존 원칙을 유지한다.
어느 수준도 CORRECTION 또는 SUGGESTION 한쪽에만 고정하지 않는다.

| 영역 | Standard | Advanced |
| --- | --- | --- |
| 문법·철자 | 명확한 국소 오류와 최소 변경 | 복잡한 동사 형태·일관성·범위·구두점까지 자세히 검사 |
| 어휘·표현 | 잘못된 단어·명백히 어색한 표현·반복 | 정확성·다양성·관용적 결합·문맥상 뉘앙스 개선 |
| 문장 구성 | 불완전 문장·잘못된 연결을 최소 수정 | 절 구성·문장 다양성·전환·흐름을 적극 개선 |
| 내용·요약 | 기본 명확성·필요 설명·불필요 반복 | 중심 생각·이유와 근거·논리 전개·관련성·간결성 개선 |

전역 수준으로 다른 영역의 수준을 덮어쓰지 않는다. 규칙은 enum 순서로 조립해 설정 Map 순서에 영향을 받지 않는다.
선택하지 않은 영역은 상세 규칙 파일 자체를 읽거나 붙이지 않는다.
공통 계약 설명과 기존 JSON Schema에는 네 영역의 허용값이 남지만, 실제 첨삭은 선택 영역으로 제한한다.
선택하지 않은 영역의 수정을 다른 대표 영역이나 혼합 후보로 우회하지 않도록 명시한다.

카드 수는 검증기의 `AiCorrectionLevel` 값을 재사용한다.
페이지·대표 영역별 Standard 권장 3/최대 5, Advanced 권장 5/최대 7이며 REPLACE와 GUIDANCE를 합산한다.
REVIEW는 한도에서 제외한다. 최소 수는 없고 항목 수를 채우기 위한 제안을 금지한다.
모든 영역이 꺼져 있으면 상세 규칙 없이 실제 판독 문제의 REVIEW만 허용하며, 문제가 없으면 items=[]이다.

## 사용 예

```java
AiValidationContext context = AiValidationContext.fromText(
        1, transcription,
        Map.of(
                AiArea.GRAMMAR_SPELLING, AiCorrectionLevel.STANDARD,
                AiArea.CONTENT_SUMMARY, AiCorrectionLevel.ADVANCED));
AiCorrectionPrompt prompt = new AiCorrectionPromptComposer().compose(context);
// prompt.instructions(), prompt.inputText(), prompt.responseSchema()
// 이 코드로 HTTP 요청을 보내지는 않는다.
```

## 요청 없는 검증·미리보기

프로젝트 루트에서 Java 21로 실행한다.

```powershell
$env:JAVA_HOME = 'C:\Users\82108\.jdks\temurin-21.0.11-1'
.\gradlew.bat --offline test --tests "*AiCorrectionPromptComposerTest"
.\gradlew.bat --offline build spotlessCheck
```

테스트는 3^4=81가지 OFF/Standard/Advanced 조합, 상세 규칙의 누락·혼입,
영역 한 곳의 수준 변경이 다른 영역에 미치는 영향, 고정 순서, 전사문·빈 줄 보존,
계약·좌표 규칙, 기존 v1 응답의 후처리 호환성을 확인한다.
모델을 호출하는 테스트는 없다.

테스트가 실제 조립한 혼합 설정 결과를 다음 파일로 만든다. 모두 Git 제외 경로이며 재실행으로 생성할 수 있다.

- `build/ai-prompt-preview/mixed-settings.md`: 사람이 읽는 instructions·고정 입력·스키마 미리보기.
- `build/ai-prompt-preview/mixed-settings.json`: requestSent=false 및 프롬프트 묶음.

미리보기는 문법·철자 Standard, 내용·요약 Advanced, 어휘·표현·문장 구성 꺼짐 설정이다.
입력은 고정 예시이며 원본 이미지, 키, 인증 헤더 또는 전송 가능한 HTTP 본문을 포함하지 않는다.

## 남은 단계

실제 요청 본문과 이미지 연결, 생성 작업·전사 버전·원본 응답 저장, Controller와 조회·편집 연결,
실제 모델의 규칙 준수 및 품질·속도 검증은 아직 구현하지 않았다.
프롬프트 테스트 통과는 모델이 규칙을 항상 지킨다는 뜻이 아니므로 기존 서버 후처리를 계속 사용해야 한다.
