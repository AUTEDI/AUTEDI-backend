# develop 충돌 해결 기록

## 기준과 Git 상태

- 작업 브랜치: `feat/connected-ai-model`, 시작 HEAD `49db8db`.
- 대상: fetch로 확인한 `origin/develop`의 `ecaf9a4`.
- 공통 조상: `8defae0`.
- 작업 시작 시 미커밋 변경·기존 충돌은 없었다.
- `git merge --no-commit --no-ff origin/develop`로 로컬 충돌만 재현했다.
  merge commit·push·PR 병합·배포는 하지 않는다.
- 해결본을 index에 등록한 뒤에도 로컬 merge 준비 상태다. 원격 PR 표시는 사용자가 해결본을 commit/push한 후 갱신된다.

## 충돌한 10개 파일과 해결 방향

| 파일 | 해결 내용 |
|---|---|
| .gitignore | 개인 HTTP 인증정보·Python 캐시 제외와 develop의 `.agents/`·업로드 제외를 모두 유지 |
| CorrectionUpdateRequest | content/regions/isApplied 부분 편집을 수용. 서비스에서 빈 요청과 action별 내용을 검증 |
| CorrectionResponse | feedbackType 중복을 없애고 하나의 필드로 반환. isApplied와 v1 action/anchor/경고/단위를 함께 제공 |
| Correction | 도메인 FeedbackType 한 개로 저장. 현재 regions·isApplied 편집과 최초 AI 메타데이터 불변성을 구분 |
| CorrectionService | feedbackType/isApplied 필터, FIXED/AI 출처, 실제 flush 기반 ETag, 부분 편집·토글을 함께 유지 |
| WorksheetGenerationService | 기존 비동기 접수·재시도·멱등키 로직 유지. 동기 mock의 새 feedbackType 전달은 백그라운드 저장부에 반영 |
| WorksheetController | files/file/images 다중 업로드와 기존 단일 이미지·인증 다운로드·PNG API를 함께 유지 |
| WorksheetService | 모든 페이지에 보정 PNG를 저장하고, 중간 파일 실패 시 앞서 만든 파일까지 롤백 정리 |
| ErrorCode | FILE_004 이미지 오류와 FILE_005 크기 초과 등 기존 오류 계약 유지 |
| LocalStorageService | StorageConfig가 빈을 선택하도록 통일하고, 원본/PNG 준비·저장·조회는 유지 |

## 표시 없는 연결 오류도 수정

자동으로 합쳐진 코드를 그대로 두면 S3 구현은 `FileStorageService.load`가 없어 컴파일되지 않고,
StoredFile에 보정 PNG URL을 전달하지 않아 AI 생성 시 IMAGE_NOT_PREPARED가 발생하는 구조였다.
따라서 S3에서도 같은 WorksheetImagePreparer로 검증·EXIF/HDR 보정을 수행하고,
원본과 PNG를 UUID가 같은 별도 객체로 저장한다. 인증된 이미지/원본 API와 워커는 같은 S3 load를 사용한다.
PNG 저장 실패 시 이번 요청의 두 UUID 키만 정리한 뒤 설정된 로컬 폴백을 사용한다.
잘못된 S3 URL은 SDK 호출 전에 거부하며, 객체 누락은 FILE_006으로 반환한다.

AI 계약의 AiFeedbackType은 외부 응답 DTO에 유지하고, 저장 경계에서 develop의 FeedbackType으로 변환한다.
JSON 값 CORRECTION/SUGGESTION은 동일하다. 레거시 카드의 action은 여전히 null이며,
feedbackType만 있는 develop 레코드를 v1의 불완전한 레코드로 잘못 거부하지 않도록 CHECK 조건을 맞췄다.
isApplied는 새 카드와 새 컬럼에 기본 true를 적용한다.

교사가 위치를 수정하면 현재 regions만 바뀐다. 원본 AI boxes와 해석 결과는
WorksheetGenerationResult의 rawResponse/validationResult에 그대로 보존된다.
좌표는 해당 카드의 페이지 기준 0~1 정규화 값으로 검사한다. pageId 생략은 그 카드의 페이지로 채운다.
REPLACE의 빈 문자열·공백 보존과 GUIDANCE의 빈 내용 거부도 유지한다.

반영 여부 전용 토글은 develop의 기존 헤더 없는 호출을 허용하며,
If-Match를 제공하면 오래된 ETag를 412로 거부한다. 내용/좌표 편집·삭제·복원은 기존 ETag 검사를 유지한다.

## 유지한 범위와 명시한 제한

- develop의 메일 발송기 선택, 코멘트 색상/하이라이트, CORS·웹 설정·문서는 유지한다.
- 세 업로드 필드의 다중 JPG/JPEG/PNG를 수용하고 모든 페이지를 보정 PNG로 준비한다.
- 이번 실제 AI 계약은 한 장을 대상으로 검증되어 있다. FIXED/TERRA에서 다중 페이지 요청을
  접수 전에 400으로 거부해 첫 페이지만 처리한 뒤 전체 성공으로 표시하거나 비용을 쓰지 않는다.
- LEGACY_MOCK의 다중 페이지 생성은 유지한다.
- 다중 페이지 학습지의 단일 파일 교체는 409로 거부한다. 일부 페이지만 바꾸고 원본 해시·페이지 수를 혼합하지 않는다.
- PDF 변환은 구현하지 않았다. S3도 로컬과 동일하게 PDF를 거부하고 가짜 1000×1400 크기를 저장하지 않는다.
- 실제 다중 페이지 AI 생성·전사 재사용 확장·PDF 처리는 별도 기능 작업이다.

## 기존 DB를 사용하는 배포 시 확인

이번에는 H2 테스트 DB만 사용하며 기존 로컬 MySQL·공유/운영 DB를 변경하지 않는다.
기존 DB에는 develop의 is_applied 컬럼과 v1 컬럼/아카이브 구조가 함께 필요하다.
새 is_applied 컬럼은 기본 true, 기존 feedback_type 값 CORRECTION/SUGGESTION은 유지되어야 한다.

이전 AI 브랜치의 `CHK_corrections_contract_v1`이 이미 적용된 DB라면,
새 엔티티와 같은 정의로 CHECK를 맞춰야 한다. 새 레거시 카드에는 action 없이 feedbackType만 있을 수 있다.
Hibernate ddl-auto=update가 기존 CHECK 정의를 자동 교체한다고 가정하지 않는다.
배포 DB의 스키마 확인/반영은 이 로컬 충돌 해결 작업에서 실행하지 않았다.

## 검증 방법

컴파일을 먼저 확인하고 S3·이미지·첨삭·생성·저장·스토리지 선택 관련 94건을 실행했다.
92건이 먼저 통과했고, 반영 여부 전용 토글의 헤더 없는 호출이 412로 거부되는 호환성 문제와
새 좌표 오류 테스트의 If-Match 누락을 수정했다. 수정 후 실패한 두 항목만 재실행해 통과했다.

추가/보강한 중요 사례는 다음과 같다.

- S3의 HDR 원본과 회전 PNG 저장·복원, 중간 PNG 실패 시 정리·폴백, 다른 도메인/객체 누락 거부
- 다중 업로드별 PNG·페이지 순서와 뒤쪽 파일 실패 시 전체 요청 롤백
- 좌표/반영 여부 부분 편집의 실제 DB 재조회, 초기 AI 아카이브 보존, 필터에서도 FIXED 출처 유지
- 토글의 기존 호출 호환성과 오래된 If-Match 거부, 다른 페이지 좌표 거부
- 다중 페이지의 FIXED/TERRA 생성 접수 차단

외부 클라이언트는 Mockito·가짜 HTTP 서버로 대체했다. 초대 API 통합 테스트에도 가짜 EmailSender를 사용한다.
유료 AI·실제 S3·실제 메일 발송은 하지 않는다. 전체 검사는 관련 항목 통과 후 한 번 실행한다.

## 최종 결과

2026-11-02 전체 검사: **26개 클래스·393건 통과, 실패·오류·건너뜀 0건**.
`build`, `spotlessCheck`, `git diff --check`도 통과했다.
생성·검토 통합 32건, 첨삭 API 9건, 이미지 API 11건, S3 15건과
스토리지/메일 빈 선택·메일 발송기 테스트가 포함된다.

전체 검사는 필터 없이 다음 옵션으로 한 번 실행했다.

```powershell
.\gradlew.bat --offline --no-daemon -I scripts/local-h2-tests.init.gradle build spotlessCheck --continue
```

이 환경에서는 기존 wrapper 시작 지연을 피하기 위해 같은 버전의 설치된 Gradle 9.8.2 실행 파일을 사용했다.
결과 HTML은 `build/reports/tests/test/index.html`, 집계는 `build/develop-merge-verification/summary.json`이다.
이번에는 병합된 코드의 H2·가짜 외부 클라이언트 검증만 했으며,
기존 test7 유료 성공 기록을 이번 변경 후의 실호출 결과로 재사용해 주장하지 않는다.
실제 MySQL 스키마 변경·S3 권한·메일 계정·프론트 UI·실제 유료 요청은 별도 환경 검증 범위다.
