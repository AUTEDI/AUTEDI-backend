# v1.1 저장·복원 확장과 DB 배포 주의점

- 작성일: 2026-11-13, 한국 시간
- 범위: **11. 저장·복원 확장**. 12 조회 API 확장·13 편집 API 확장·14 서비스 생성 연결은 후속 단계다.
- 기존 원칙: [버전 선택·프론트 호환 규칙](AI-v1.1-버전선택-프론트호환규칙.md)
- 실제 AI API나 develop/운영 DB에는 접속하지 않는다. 아래 SQL도 자동 실행하거나 적용하지 않았다.

## 1. 구현한 저장 흐름

`GenerationResultStorageService.recordValidatedResult`는 기존 v1 진입점이고, 새 `recordValidatedResultV11`은 v1.1 진입점이다. 둘 다 다음 순서로 처리한다.

1. 요청 학원·작업·학습지·페이지·원본 파일·확정 전사·모델·작업 프로필과 결과 메타데이터의 연결을 확인한다.
2. 메타데이터의 계약/검증 버전으로 원본 JSON을 파싱하고 저장된 전사·설정으로 전체 검증을 다시 수행한다.
3. 호출자가 전달한 검증 결과가 재검증 결과와 정확히 같을 때만 저장한다. 다른 버전이나 조작한 분류/개수/좌표 결과는 거절한다.
4. 일반 카드만 `corrections`에 저장한다. 확인·제외 후보도 전체 검증 JSON에는 남기지만 정상 카드로 만들지 않는다.
5. 원본 JSON·전체 검증 결과·카드 ID 연결을 불변 생성 결과로 같은 트랜잭션에서 저장한다. 카드나 결과 저장이 실패하면 함께 롤백한다. 이전 단계에서 커밋한 전사는 유지한다.

같은 작업·페이지에 완전히 같은 응답/전사/메타데이터/검증 결과를 다시 저장하면 기존 결과 ID를 반환한다. 사소한 원본 공백까지 다른 응답이나 다른 검증 결과로 최초 기록을 덮어쓸 수 없다. 재전송으로 교사 편집 카드도 초기화하지 않는다.

v1.1 저장은 작업의 `request_ai_profile`이 명시적으로 v1.1인 경우만 허용한다. 프로필 없는 과거 작업에 신규 v1.1 결과를 섞어 넣지 않는다. 기존 v1의 프로필 없는 작업과 과거 프롬프트 메타데이터는 계속 지원한다.

## 2. 복원은 저장 버전으로 역직렬화

`worksheet_generation_results.validation_result`의 SQL 타입은 기존 JSON 그대로다. Java 엔티티에서는 중립 Map으로 읽고 [GenerationValidationStorageCodec](../../src/main/java/com/ceos24/autedi/domain/generation/persistence/GenerationValidationStorageCodec.java)이 저장된 계약/검증 버전으로 해석한다.

| 저장 버전 | 복원 결과 |
| --- | --- |
| AUTEDI-AI-CONTRACT-v1 | 기존 v1 AiValidationResult |
| AUTEDI-AI-CONTRACT-v1.1 | v1.1 AiValidationResult |
| 알 수 없는 계약/검증 버전 | 명시적으로 실패. 다른 버전으로 재해석하지 않음 |

JSON 루트는 기존의 originalResponse/context/candidates/summary를 유지한다. v1 JSON 전체를 새 래퍼로 일괄 변환하지 않는다. 저장된 결과를 복원할 때 현재 검증기를 다시 실행하지 않으므로, 이후 정책 변경이나 파일 교체가 과거 판정을 바꾸지 않는다.

[StoredGenerationResult](../../src/main/java/com/ceos24/autedi/domain/generation/persistence/StoredGenerationResult.java)의 읽기 방법은 다음과 같다.

- v1은 기존 `validationResult()`를 사용한다.
- v1.1은 `validationResultV11()` 또는 `versionedValidationResult()`를 사용한다.
- 검증된 v1.1 결과에 v1 전용 getter를 호출하면 명시적으로 실패한다. 검증 결과가 없는 응답으로 오해해 확인 항목/집계를 누락하지 않는다. 12단계 조회 코드는 버전에 맞게 분기해야 한다.
- UNREADABLE은 두 검증 결과가 모두 없고 원문·메타데이터·파싱 실패 사유만 보존한다. 파싱 가능 여부도 해당 계약의 파서로 판단한다.

## 3. 첨삭 카드의 v1.1 상세 데이터

`corrections.v11_details`에 [CorrectionV11Details](../../src/main/java/com/ceos24/autedi/domain/correction/entity/CorrectionV11Details.java)를 저장한다. 기존 v1/미분류 행은 NULL이며 일괄 백필하지 않는다.

| 상세 필드 | 보존 내용 |
| --- | --- |
| anchors | 동일 확정 전사의 모든 연결: transcription_id/line_id/occurrence/start/end |
| revisionScope | 최초 AI 수정 범위 |
| aiInitialEdit | 최초 AI 수정 동작·표시 문구·삽입 방향. 교사 편집으로 덮어쓰지 않음 |
| edit | 현재 내용과 일치하는 수정 동작. 실제 내용 변경 후에는 null로 무효화 가능 |
| warnings | v1.1 원래 검증 코드·필드·메시지 전체 |

다중 줄 원문은 LF를 포함한 전체 originalText와 모든 regions를 보존한다. 정확한 단일 anchor로 나타낼 수 있으면 기존 anchor도 채우며, 여러 줄이면 기존 anchor는 NULL로 둔다. 임의의 첫 구간이나 가짜 start/end를 전체 원문처럼 저장하지 않는다.

기존 action/feedbackType/issueKinds와 원문·내용·설명·좌표 필드도 유지한다. 기존 warnings 필드는 v1 enum으로 표현 가능한 진단 투영을 사용하고 새 코드의 전체 정보는 v11_details/불변 아카이브에 보존한다. 실제 v1.1 조회 응답에서 어떤 정보를 내보낼지는 12단계 매핑을 따른다.

카드 저장 객체는 최소 일관성을 확인한다: 일반 action/문제 종류, 전체 원문의 UTF-16 길이, 동일 전사의 연속된 앵커, 단일 anchor 투영, GUIDANCE/REPLACE의 범위·동작 조합. 기존 내용 편집이 실제 content를 바꾸면 현재 edit만 무효화하며 최초 AI edit/원본 아카이브는 보존한다. 새 편집 요청 필드를 받는 API는 아직 추가하지 않았다.

## 4. DB 스키마 변경 범위

| 테이블 | 이번 변경 |
| --- | --- |
| worksheet_generations | 이번 단계 추가 없음. 10단계 request_ai_profile 컬럼이 선행 조건 |
| worksheet_generation_results | 추가 컬럼·자료형 변경 없음. 기존 validation_result JSON에 버전별 문서를 저장 |
| corrections | nullable JSON 컬럼 v11_details 추가, 기존 CHECK를 v1/v1.1 공존 규칙으로 교체 |

기존 원문·전사·첨삭·ID·외래키·원본 파일은 삭제하거나 덮어쓰지 않는다. anchor 컬럼은 기존에도 nullable이므로 NOT NULL 해제 SQL은 필요하지 않다. 기존 CHECK의 일반 카드 조건이 anchor를 필수로 요구했기 때문에 **CHECK 변경은 필요하다.**

새 CHECK 이름은 `CHK_corrections_contract_v1_v11`이다. 기존 미분류 행과 v1 행을 계속 허용하면서 v1.1 상세 데이터가 있는 다중 줄 카드만 단일 anchor 없이 저장할 수 있게 한다. REVIEW는 여전히 정상 카드 테이블에 넣을 수 없다. 실제 상세 JSON 구조와 의미는 Java 저장 검증이 담당한다.

## 5. develop 배포에서 특히 주의할 부분

**이 변경은 컬럼 추가만으로 끝나지 않는다.** 현재 develop은 `ddl-auto: update`이므로 새 엔티티 배포 시 컬럼이 자동 추가될 수 있지만, 기존 CHECK가 원하는 정의로 교체됐다고 가정하면 안 된다. 새 CHECK와 이전 CHECK가 함께 남아 있어도 이전 CHECK가 다중 줄 저장을 계속 막을 수 있다.

- [읽기 전용 preflight SQL](../../scripts/sql/2026-11-13-v11-storage-preflight.sql): 접속 DB/버전, 테이블 크기·엔진·컬럼·CHECK, 기존 데이터, 진행 중 세션을 확인한다. 읽기 전용 쿼리도 큰 테이블에서는 부하를 줄 수 있으므로 복제 DB에서 먼저 확인한다.
- [검토·수동 적용 SQL](../../scripts/sql/2026-11-13-v11-storage.sql): v11_details가 없고 기존 CHECK만 있는 상태를 전제로 한다. 실제 상태가 다르면 그대로 실행하지 않는다. 이미 적용한 컬럼을 다시 추가하거나 알 수 없는 이름의 CHECK를 임의로 제거하지 않는다.
- SQL 파일은 scripts 아래 검토 자료다. Spring/Flyway/배포 스크립트에 자동 실행 경로를 추가하지 않았다. Hibernate 자동 update는 이 파일을 실행하는 것과 별개다.

권장 적용 순서:

1. 실제 대상 DB를 확인하고 develop 백업·복구 가능 여부를 확보한다. 코드만 되돌릴 때도 필요한 기존 배포본과 스키마 정보를 보관한다.
2. 같은 MySQL 버전의 별도 복제 DB에서 현재 데이터로 SQL과 새 코드의 저장·복원을 검증한다. 이번 자동 테스트는 H2이므로 MySQL DDL의 실행 시간·잠금까지 보장하지 않는다.
3. MySQL 8.0.16 이상에서 CHECK가 실제 적용되는지, 테이블이 예상한 InnoDB인지 확인한다. 기존 데이터가 새 CHECK를 통과하는지도 검사한다. 이름이 같아도 정의가 다른 CHECK는 별도 검토한다.
4. 부하가 적은 시간에 대상 컬럼과 CHECK 변경을 통제해서 적용한다. 새 CHECK는 기존 데이터를 검사할 수 있고 메타데이터 잠금/대기가 발생할 수 있다. 컬럼 추가의 INSTANT 가능 여부와 CHECK 변경의 비용을 동일하게 가정하지 않는다.
5. 실제 SHOW CREATE TABLE로 새 CHECK와 컬럼을 확인한 뒤 코드를 배포한다. 스키마를 먼저 맞춘 뒤 validate로 검사하는 배포 방식이 자동 update보다 변경 범위를 통제하기 쉽다. 설정 자체는 이번에 바꾸지 않았다.
6. 기본 v1 쓰기와 과거 v1 복원을 확인하고, 저장·조회·편집·Worker 연결을 마친 뒤 v1.1 쓰기를 활성화한다.

일반 `BEGIN/ROLLBACK`으로 DDL을 되돌릴 수 있다고 가정하지 않는다. 적용 SQL의 두 ALTER 사이에서 실패하면 실제 DB 상태를 다시 확인하고 남은 작업만 검토한다. 무조건 처음부터 재실행하거나 추가 컬럼을 DROP하는 방식은 사용하지 않는다. [MySQL ALTER TABLE](https://dev.mysql.com/doc/refman/8.0/en/alter-table.html), [암묵적 커밋](https://dev.mysql.com/doc/refman/8.0/en/implicit-commit.html).

**v1.1 데이터를 저장한 이후에는 구버전 앱으로 단순 롤백하는 것도 별도 검토가 필요하다.** 이전 v1 전용 코드는 같은 validation_result 컬럼의 v1.1 JSON을 읽지 못한다. 먼저 두 버전을 읽을 수 있는 호환 릴리스를 배포하고, 이후 쓰기를 활성화하는 순서를 유지한다. CHECK를 이전 정의로 되돌리면 이미 저장한 다중 줄 카드가 위반할 수 있으므로 데이터 삭제·v1 강제 변환으로 해결하지 않는다.

## 6. 검증과 남은 연결

저장 테스트는 전용 메모리 H2에 실제 커밋하고 다른 트랜잭션에서 읽는다. 기존 v1 JSON, 정상 v1.1 고정 예제, 다중 줄, 삽입/삭제/재구성, GUIDANCE, 경고·확인·제외, 빈 결과, 64KB 이상 원문, 두 버전 공존, 멱등성, 교사 편집 후 원본 보존, 트랜잭션 롤백, 학원 경계와 버전/검증 위조를 검사한다.

서비스 기본값과 실행 준비 검사는 v1 그대로 유지한다. v1.1 서비스 생성은 아직 접수하지 않으며 별도 실험 호출은 기존대로 사용할 수 있다. **다음은 12 조회 응답 매핑 → 13 편집 API 호환 → 14 서비스 클라이언트/Worker 연결**이다. 이번 저장 지원이 실제 프론트 표시까지 완료됐다는 의미는 아니다.
