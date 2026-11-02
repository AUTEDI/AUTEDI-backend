# test7.jpg 실제 유료 API 전체 서비스 검증

2026-11-01, 사용자가 지정한 `test_data/test7.jpg`로 실제 Terra 전사·첨삭 한 건을 실행했다.
기존 IntelliJ 서버의 local/TERRA 모드와 별도 로컬 MySQL `127.0.0.1:3306/autedi_ai_local`을 사용했다.
기존 테스트 기록과 사진, `.env`는 보존했다. 인증정보는 이 문서에 포함하지 않는다.

## 결과

| 항목 | 결과 |
|---|---|
| 전체 결과 | PASSED |
| 학습지 / 생성 / 전사 / 결과 ID | 2 / 2 / 2 / 2 |
| 모델 | gpt-6.1-terra |
| 최종 작업 상태 / 학습지 상태 | SUCCEEDED·COMPLETED / READY |
| 입력 | test7.jpg, Apple HDR gain map이 포함된 JPEG |
| 공통 보정 PNG | 3024 × 4032 px |
| 선택 영역·수준 | 문법·철자 STANDARD, 내용·요약 ADVANCED |
| 실제 유료 처리 | 새 생성 한 건, 전사 1회·첨삭 1회 |
| 유료 자동/수동 재시도 | 0회 |
| 확정 전사문 | 28줄 |
| 원본 후보 | 9개 |
| 정상 카드 | 8개: REPLACE 5개·GUIDANCE 3개 |
| 확인 필요 / 제외 | 1개 / 0개 |
| 정상 카드 경고 | 0개 |
| 동일 키 재전송 | 동일 생성 ID 반환, DB 작업 1개 |
| 교사 수정·삭제·복원 | REPLACE·GUIDANCE 각각 대표 카드 한 개 통과 |

전사·첨삭은 실제 HTTP 클라이언트와 `api.openai.com/v1/responses`를 사용했다.
계약은 AUTEDI-AI-CONTRACT-v1, 프롬프트는 AUTEDI-CORRECTION-PROMPT-v1,
검증은 AUTEDI-VALIDATION-v1이다. 전체 HTTP envelope·토큰 사용량·실제 청구액은 현재 저장하지 않는다.

## 전체 요청 흐름

로그인 → HDR 원본 업로드 → 원본·보정 PNG 조회 → 설정 저장 → 생성 접수 → 같은 키 접수 확인 →
OCR 상태 조회 → AI_CORRECTION 상태 조회 → SUCCEEDED·COMPLETED 확인 → 검토 조회 →
REPLACE·GUIDANCE 교사 수정 → 각각 소프트 삭제·복원 → 재조회 → 원본·PNG 재조회.

첫 업로드는 테스트 도구의 30초 응답 대기에서 시간 초과가 발생했지만,
로컬 DB에는 원본 해시가 일치하는 DRAFT 학습지 2번이 저장되어 있었다.
이 시점에는 유료 생성 요청을 보내지 않았다.
테스트 도구의 로컬 HTTP 대기를 180초로 늘리고 기존 업로드의 접수 기록을 복구해,
새 학습지를 만들지 않고 이어서 진행했다. 서비스 코드는 추가 변경하지 않았다.

## 데이터·좌표 검증

- 원본 다운로드가 test7.jpg의 바이트와 동일하다. 원본·생성 요청·불변 전사의 파일 해시도 일치한다.
- 서버 PNG가 앞서 로컬에서 검증한 HDR 기본 사진의 보정 PNG와 바이트 단위로 동일하다.
- 불변 전사의 PNG 해시·3024×4032 크기가 API의 화면 이미지·페이지 메타데이터와 일치한다.
- MySQL에 생성 작업·불변 전사·원본 응답/검증 아카이브가 각각 1개, 카드가 8개 저장됐다.
- DB 전사문 UTF-8 해시, 확정 줄 배열과 검증 context가 일치한다.
- 카드 8개 모두 지정 줄·occurrence·UTF-16 start/end로 전사문에서 원문을 정확히 복원했다.
- 모델 LTRB boxes와 API 정규화 XYWH, 페이지 ID가 일치한다. 좌표 기준은 보정 PNG 전체의 0~1이다.
- DB raw_response의 9개 후보가 validation_result의 originalResponse 및 각 원본 후보와 일치한다.
- 정상 카드와 확인 필요 항목의 원본·검증 사유가 아카이브에 연결되어 있다.
- 교사 편집·삭제·복원 후 현재 content는 편집 값을 유지하고,
  최초 AI 제안·원문·설명·anchor·좌표·경고·전사/결과 참조는 보존됐다.

전사 커밋을 확인하려고 시작한 직접 DB 조회는 실제로 이미 생성이 완료된 시점에 실행됐다.
따라서 실제 첨삭 대기 중 DB 커밋 시점을 이 실행에서 직접 관측했다고 주장하지 않는다.
중간 상태의 커밋과 비트랜잭션 대기는 앞선 전체 자동 검사에서 검증했다.

## 확인 필요 항목

모델은 사진 아래쪽 마지막 문장이 잘려 있어 교사가 원본의 마지막 부분을 확인해야 한다고 반환했다.
REVIEW action·anchor 없음·boxes 없음·위치 불확실을 원본 그대로 보존했다.
MODEL_REVIEW와 MODEL_LOCATION_UNCERTAIN 사유 두 개로 조회된다.
이는 API/저장 실패가 아니며, 보정 PNG에서도 하단 문장의 일부가 잘린 것을 확인했다.

예시 제안은 `went Practice → went to practice`, `droped → dropped`이며,
내용 보완에는 단서를 통해 답을 얻은 과정을 설명하라는 GUIDANCE가 포함됐다.
교사 테스트 편집은 카드 3번(REPLACE)·6번(GUIDANCE)에 `[manual service check]`를 덧붙인 상태로 남았다.
삭제한 두 카드는 복원해 모두 ACTIVE 상태이며 최초 AI 내용은 별도 필드에 보존되어 있다.

## 파일·직접 확인

이번 기록은 Git에서 제외되는 `build/manual-service-test-test7/`에 있다.

| 파일 | 역할 |
|---|---|
| receipt.json | 작업 ID·중복 방지 키·상태·검증 결과. 인증정보 없음 |
| prepared.png | 실제 전사·첨삭·화면이 공유하는 PNG |
| review-before.json / review-after.json | 교사 편집·삭제·복원 전후 API 결과 |
| db-snapshot.json | 로컬 MySQL의 전사·원본 응답·검증 결과 조회 |
| verification.json | 해시·원문·좌표·아카이브 대조 결과 |
| result.md | 이번 실행의 요약 |

로그인 후 `GET /api/v1/worksheets/2/review`, `GET /api/v1/worksheets/2/generations/2`,
`GET /api/v1/worksheets/2/pages/1/image`로 다시 확인한다. 새 생성 요청은 필요하지 않다.
기존 HTTP Client의 local 환경 ID는 이전 테스트를 가리킬 수 있으므로 경로의 ID를 2로 지정한다.

실행한 로컬 도구 명령은 다음과 같다. 완료 receipt를 삭제하면 중복 실행 방지가 사라지므로 보존한다.

```powershell
python scripts/local_service_smoke.py --authorize-one-generation --image ../test_data/test7.jpg --output-dir build/manual-service-test-test7
```

완료 후 같은 기록으로 실행하면 새 요청 없이 종료한다.

## 이번에 생략한 검증

직전 전체 자동 테스트 20개 클래스·354건과 build·spotlessCheck는 이미 통과했고,
이번에는 서비스 Java 코드를 변경하지 않아 반복하지 않았다.
새 테스트 도구의 Python 문법, 완료 기록 재실행 방지, 실제 MySQL 저장·재조회와 데이터 대조를 확인했다.
실제 유료 실패/재시도·추가 사진·실제 프론트 UI·모든 박스의 시각적 정확도·다중 서버 부하는 검증하지 않았다.
운영/공유 DB 적용, 서버 설정 변경·재시작, 커밋·push·PR·병합, 다음 기능 구현은 진행하지 않았다.
