# LLM 동시 요청·메모리 테스트 기록

정리일: 2026-11-16

EC2에서 이미지 업로드와 AI 첨삭을 실행하면서 확인한 대기 시간, JVM 메모리, GC, 컨테이너 제한, 워커 수 변경 과정을 정리했다. 사용자가 공유한 SQL·로그·명령 결과와 현재 저장소 코드를 근거로 작성했다. 외부 API 로그에는 2026-11-14 기록이 포함되어 있으며, 나머지 실험의 정확한 실행 날짜는 별도로 기록되지 않았다.

## 먼저 읽을 결론

- 최초 병목에는 워커 2개의 처리 대기와 긴 외부 API 호출 시간이 함께 있었다.
- 개발 컨테이너 512MiB·힙 최대 384MiB에서는 두 요청 중 긴 GC 시간 증가가 관측됐다.
- 768MiB·힙 최대 512MiB로 변경한 뒤 제공 구간의 GC 지연이 크게 줄었다.
- 컨테이너 896MiB·힙 최대 512MiB·워커 3개에서도 세 요청이 완료됐다고 보고됐다. 종료 무렵 컨테이너 사용량은 약 781MiB였다.
- 이 결과는 운영 후보를 정할 근거다. 운영에서의 최악 조건이나 처리량 개선 배수를 보장하는 부하 시험 결과는 아니다.

## 문서 읽는 순서

| 문서 | 내용 |
|---|---|
| [01. 병목과 실험 결과](01-test-results.md) | 초기 SQL·API 로그, 하드웨어 상태, 단계별 메모리·GC 관측, 해석과 한계 |
| [02. 모니터링과 재현](02-monitoring-and-reproduction.md) | jcmd·jstat 실행, 지표 읽기, 테스트 파일, 896MiB 변경, 워커 3개 적용·개발 원복 |
| [03. 운영 전환과 배포](03-production-rollout.md) | 로컬/EC2 Compose 관계, .env 키 전달, 운영 설정, main PR 배포, 확인·복구, 로그인 진단 메모 |

## 최종 설정 목표

| 대상 | 서비스 / 컨테이너 | RAM 상한 | JVM 힙 시작 / 최대 | 워커 | RAM+스왑 상한 |
|---|---|---:|---:|---:|---:|
| 개발 원복 | spring-app-dev / mvp-spring-dev | 512MiB | 128 / 384MiB | 2 | 1024MiB |
| 운영 후보 | spring-app / mvp-spring | 896MiB | 256 / 512MiB | 3 | 1408MiB |

운영의 추가 스왑 허용량은 테스트와 동일하게 512MiB로 명시했다. 원래 운영 컨테이너의 실제 스왑 설정은 별도 출력이 없어 확인하지 못했다. 개발에서는 기존 추가 허용량 512MiB가 실제 확인됐다.

단위는 메모리 환산과 `docker stats` 출력에 맞춰 MiB로 통일한다. 대화에서 사용한 “MB”는 대부분 이 값을 가리킨다.

## 기록 시점의 진행 상태

| 항목 | 기록된 상태 |
|---|---|
| 개발에서 워커 3개·896MiB 테스트 | 세 요청 완료와 메모리 로그를 사용자가 공유함 |
| 개발 원복 및 EC2 운영 Compose 준비 | 사용자가 앞선 운영 전환 안내의 3번까지 완료했다고 보고함 |
| 운영의 새 AI 코드 main 배포 | 마지막 확인에서 배포 전이었음 |
| EC2 최종 설정 검사 | 실행 결과가 공유되지 않음 |
| PR CI·병합·운영 Actions 성공 | 완료 결과가 공유되지 않음 |
| 운영 실제 RAM 896MiB·워커 3개 적용 | 배포 후 출력이 없어 아직 미확인 |
| 부하 중 호스트 MemAvailable 최저값 | 측정하지 않았다고 답변함 |
| 워커 2개 대비 3개의 실제 처리량 개선 | 동일 조건의 완료 시간 자료가 부족함 |

“안내한 명령”, “사용자가 완료했다고 보고한 작업”, “실제 출력으로 확인한 값”을 구분해서 읽는다. 이 문서 작성 중 원격 EC2에 접속하거나 배포를 실행하지 않았다.

## 로컬 파일과 실제 배포 파일

- 로컬 참고 설정: `infra/docker-compose.yml`
- EC2 실제 배포 설정: `/srv/mvp/docker-compose.yml`
- EC2 테스트 추가 파일: `/srv/mvp/docker-compose.memory-test.yml`
- 초기 실험에서는 `/tmp/autedi-memory-test.*` 파일도 사용했다.

현재 워크플로는 EC2에 있는 파일을 사용하며 저장소의 Compose를 자동 복사하지 않는다. 로컬 저장만으로 EC2 설정은 바뀌지 않는다. 다만 `main` 병합은 자동 배포를 실행하며, `develop` push도 개발 배포를 실행할 수 있다.

로컬 Compose는 이번 메모리·워커·AI 연동 설정을 반영한 참고 설정이다. EC2 파일 전체를 복제한 것은 아니다. 앞서 공유된 EC2 파일과는 DB 환경변수 이름, 도메인, Nginx 볼륨 옵션, 로깅, MySQL 연결 수 등의 차이가 남아 있으므로 EC2 파일을 로컬 파일로 통째로 덮어쓰지 않는다.


## 근거 코드

- [워커 기본값과 풀 크기](../../src/main/java/com/ceos24/autedi/global/config/GenerationExecutionConfig.java)
- [작업 배정과 재시작 복구](../../src/main/java/com/ceos24/autedi/domain/generation/service/GenerationJobDispatcher.java)
- [전사·첨삭 처리 흐름](../../src/main/java/com/ceos24/autedi/domain/generation/service/GenerationWorker.java)
- [이미지 준비](../../src/main/java/com/ceos24/autedi/global/storage/WorksheetImagePreparer.java)
- [외부 API 요청과 대기](../../src/main/java/com/ceos24/autedi/domain/generation/client/TerraGenerationClient.java)
- [운영 배포](../../.github/workflows/deploy.yml), [개발 배포](../../.github/workflows/deploy-dev.yml)

