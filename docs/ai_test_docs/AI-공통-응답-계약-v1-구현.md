# AI 공통 응답 계약 v1 — DTO·스키마·파서

기준: [AI 공통 응답 계약 v1](AI-공통-응답-계약-v1.md), `AUTEDI-AI-CONTRACT-v1`.

## 이번 구현

- 패키지: `com.ceos24.autedi.domain.generation.contract.v1`.
- `AiCorrectionResponse`와 `AiCorrectionItem`은 `items` 응답과 후보 항목을 표현한다.
- `AiAnchor`는 `line_id`, `original`, `occurrence`를 표현한다.
- `AiAction`, `AiFeedbackType`, `AiArea`, `AiIssueKind`는 v1 허용값만 표현한다.
- snake_case JSON 필드가 camelCase record 필드로 매핑된다. 문제 종류는 소문자 wire 값이다.
- REVIEW의 명시적 null, 위치 없는 빈 boxes, `items=[]`, 삭제 제안 `replacement=""`를 보존한다.
- boxes는 정규화된 `[left, top, right, bottom]` 배열이며, width/height나 픽셀로 변환하지 않는다.
- 컬렉션은 복사해 외부에서 응답 후보를 변경하지 못하도록 한다.

스키마 리소스는 `src/main/resources/ai/contract/v1/correction-response.schema.json`이다.
JSON Schema Draft 2020-12 문서이며, 모든 필드의 포함, nullable 타입, enum, 추가 필드 금지,
문제 종류의 중복 금지, 한 줄 anchor, 0~1 숫자 네 개로 구성된 boxes를 정의한다.
줄 번호와 occurrence는 Java int로 표현 가능한 양의 정수이다.

## 사용

```java
AiCorrectionResponse response = new AiCorrectionResponseParser().parse(json);
```

`parse`는 AI 응답 본문의 JSON 객체를 받는다. API 응답 envelope, Markdown 코드 블록,
이전 실험의 `edits` 형식은 자동 변환하지 않는다.
이 파서는 Spring Bean으로 등록하거나 서비스에 주입하지 않았다.

파서는 JSON 문법·필수 필드·기본 타입·enum·배열 구조를 검사한다.
누락과 명시적 null을 구분하며, 숫자 문자열 등의 묵시적 타입 변환, 알 수 없는 필드,
중복 JSON 키, 후행 JSON도 허용하지 않는다. 구조가 잘못되면 `IllegalArgumentException`을 던진다.
범용 JSON Schema 검증 엔진을 실행하는 방식은 아니며 외부 의존성을 추가하지 않았다.
고정 응답 파싱 테스트와 DTO·enum 및 스키마의 필드 일치 테스트를 별도로 둔다.

파싱 성공은 서버 후처리 완료를 뜻하지 않는다. 읽을 수 있는 후보의 필드 조합을 임의로 고치거나
항목을 삭제하지 않는다. 원본 응답 보존은 향후 호출 측의 책임이다.

## 이번에 연결하지 않은 부분

- action별 필드 조합, REVIEW와 일반 issue 종류의 구분
- 실제 전사문의 anchor 일치, occurrence 검색, UTF-16 문자 위치 계산
- 좌표의 양수 면적·원문 위치·겹침 검사와 잘못된 박스의 개별 제외
- GUIDANCE의 영어 10단어 및 reason의 언어·문장 수 검사
- 선택 영역·수준 설정, 카드 개수 제한, 정렬, 검증 경고·확인 목록 분리
- DB, Controller, CorrectionDraft 변환, 프롬프트, 실제 AI 호출

예를 들어 REPLACE의 anchor가 null이거나 boxes의 면적이 0이어도 기본 타입은 읽을 수 있다.
이러한 후보는 이후 서버 검증에서 정상 카드와 구분해야 한다.
기존 `aiexperiments` 코드, `ocr` 테스트 리소스, 실험 스키마 및 과거 결과는 변경하지 않았다.

## API 없는 검증

프로젝트 루트에서 Java 21로 실행한다. main 실험은 실행하지 않는다.

```powershell
$env:JAVA_HOME = 'C:\Users\82108\.jdks\temurin-21.0.11-1'
.\gradlew.bat --offline test --tests "com.ceos24.autedi.domain.generation.contract.v1.*Test"
.\gradlew.bat --offline build spotlessCheck
```

고정 입력: `src/test/resources/ai/contract/v1/correction-response.json`.
세 action, snake_case/null 보존, 빈 성공 결과, 빈 문자열 삭제, 위치가 있는 REVIEW,
잘못된 enum·필수 필드·타입·박스 구조, 기존 edits와의 분리를 검사한다.
