# 운영 전환·.env·GitHub Actions 배포

[문서 목록](README.md) · [실험 결과](01-test-results.md) · [재현 명령](02-monitoring-and-reproduction.md)

정리 시점에 사용자는 개발 원복과 EC2 운영 설정 준비까지 완료했다고 보고했다. 이번 AI 코드의 main 배포는 아직 전이라고 확인했다. 설정 검사·PR CI·운영 배포 성공 및 실제 운영 지표는 공유되지 않았다. 아래는 재사용할 절차이며 원격 배포 완료 기록이 아니다.

## 1. 어떤 파일을 바꾸면 어디에 적용되는가

| 파일·작업 | 역할과 영향 |
|---|---|
| 로컬 infra/docker-compose.yml 저장 | 로컬 참고 설정 변경. 원격 EC2 파일과 컨테이너에 직접 영향 없음 |
| GitHub main 병합 | 운영 이미지 빌드·배포 실행. 컨테이너가 재생성될 수 있음 |
| GitHub develop push | 개발 이미지 빌드·배포가 실행될 수 있음 |
| EC2 /srv/mvp/docker-compose.yml | 실제 지속 배포 설정. 다음 Compose 재생성 시 반영 |
| EC2 docker-compose.memory-test.yml | 명령에서 -f로 추가할 때 사용하는 테스트 설정 |
| docker update | 실행 중인 제한 변경. Compose 파일에 자동 저장되지 않음 |

운영 워크플로의 핵심:

```bash
cd /srv/mvp
docker compose pull spring-app
docker compose up -d spring-app
```

현재 워크플로에는 저장소 Compose를 EC2로 복사하는 단계가 없다. 로컬 수정만으로 원격 설정이 동기화되지는 않는다. main 병합에 의한 자동 배포와 Compose 파일 자동 복사는 별개다.

로컬과 EC2 Compose는 이미 다른 부분이 있었다. 이번 로컬 수정은 메모리·워커·AI 연동 설정을 맞추는 범위다. EC2의 DB URL 환경변수, 도메인, Nginx 볼륨, 로그 제한, MySQL 연결 수 등을 보존하려면 해당 부분만 편집한다. 로컬 파일로 EC2 파일을 통째로 덮어쓰지 않는다.

## 2. LLM 키 전달 경로

```text
/srv/mvp/.env의 OPENAI_API_KEY
  → Compose environment에서 ${OPENAI_API_KEY} 값 치환
  → Spring 컨테이너의 OPENAI_API_KEY 환경변수
  → TerraHttpConfiguration에서 읽음
```

아래는 형식 예시다. 실제 키를 문서에 저장하지 않는다.

```dotenv
OPENAI_API_KEY=실제_키를_이곳에_설정
```

컨테이너로 전달하는 Compose 항목:

```yaml
environment:
  OPENAI_API_KEY: "${OPENAI_API_KEY:?OPENAI_API_KEY is missing}"
```

.env에 있다는 사실만으로 모든 값이 컨테이너에 전달되는 것은 아니다. 현재 방식에서는 environment에 연결해야 한다. Spring 서비스가 EC2의 .env를 직접 읽는 것은 아니다.

같은 이름의 변수가 Compose 실행 셸에 설정되어 있으면 .env보다 우선할 수 있다. EC2 작업 경로는 /srv/mvp로 통일한다. 로컬에서 infra/docker-compose.yml을 확인할 때는 필요한 .env를 --env-file로 명시해 혼동을 줄일 수 있다.

${...:?메시지}는 값이 없거나 비어 있으면 검사 단계에서 실패시킨다. 전체 docker compose config 출력에는 키가 포함될 수 있으므로 공유용 확인에는 config -q 또는 선택한 비민감 항목만 사용한다.

## 3. 최종 설정과 변경 범위

| 대상 | RAM | RAM+스왑 | Xms | Xmx | 워커 | AI |
|---|---:|---:|---:|---:|---:|---|
| 개발 | 512MiB | 1024MiB | 128MiB | 384MiB | 2 | TERRA / v1.1 / DEFAULT |
| 운영 | 896MiB | 1408MiB | 256MiB | 512MiB | 3 | TERRA / v1.1 / DEFAULT |

- 운영 시작 힙 256MiB는 기존 값을 유지한다. 개발 테스트의 시작 힙은 128MiB였으므로 초기 상태가 완전히 같지는 않다.
- 운영 추가 스왑 512MiB는 테스트 조건에 맞춘 명시적 설정이다. 이전 운영의 스왑 상한 측정 결과는 없다.
- 워커 환경변수는 APP_GENERATION_WORKERCOUNT다. app.generation.worker-count에 대응한다.
- Java 기본값 2를 직접 3으로 고치지 않는다. 서비스별 환경변수로 설정한다.
- queue-capacity 기본값 16과 SSE 전송용 executor는 별도다.
- 운영 896MiB 설정만으로 요청 처리 시간이 자동으로 짧아지지는 않는다. 처리량과 대기도 검증한다.

## 4. 준비와 개발 원복

진행 중인 개발·운영 첨삭이 없는 때 진행한다. 현재 코드는 재시작 중단 작업을 SERVER_INTERRUPTED로 표시하므로 먼저 작업 완료를 기다린다. JVM 모니터도 종료한다.

```bash
cd /srv/mvp
AUTEDI_DEV_PROJECT=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' mvp-spring-dev)
AUTEDI_COMPOSE_BACKUP="/srv/mvp/docker-compose.yml.bak-$(date +%Y%m%d-%H%M%S)"
cp /srv/mvp/docker-compose.yml "$AUTEDI_COMPOSE_BACKUP"
printf '백업 파일: %s\n' "$AUTEDI_COMPOSE_BACKUP"
```

.env의 OPENAI_API_KEY를 확인한다. 개발에서 사용한 키가 이미 있으면 그대로 활용할 수 있다. 키 값이나 .env 전체를 채팅·PR·로그에 올리지 않는다.

```bash
nano /srv/mvp/.env
```

원본만 검사하고 개발을 재생성한다. 오류나 변수 누락 경고가 있으면 해결 후 진행한다.

```bash
docker compose -p "$AUTEDI_DEV_PROJECT" \
  -f /srv/mvp/docker-compose.yml \
  config -q
```

```bash
docker compose -p "$AUTEDI_DEV_PROJECT" \
  -f /srv/mvp/docker-compose.yml \
  up -d --no-deps --force-recreate --no-build --pull never spring-app-dev
```

```bash
docker stats --no-stream mvp-spring-dev
docker exec mvp-spring-dev printenv JAVA_TOOL_OPTIONS
```

기대값은 512MiB·Xmx384m이다. EC2 원본에 워커 지정이 없으면 현재 코드의 기본값 2를 사용한다. 로컬 참고 설정은 2를 명시했다. 복원 대상은 그 시점의 원본이므로 다른 담당자의 변경도 확인한다.

## 5. EC2 운영 Compose 준비

```bash
nano /srv/mvp/docker-compose.yml
```

편집 대상은 `spring-app`이다. 기존 DB·S3·메일 설정은 유지하고 아래 내용을 기존 항목에 반영한다. 아래는 전체 파일을 교체하는 예시가 아니다.

spring-app 바로 아래 image·container_name과 같은 들여쓰기로 추가:

```yaml
    mem_limit: 896m
    memswap_limit: 1408m
```

기존 deploy.resources.limits.memory를 700M에서 변경:

```yaml
    deploy:
      resources:
        limits:
          memory: 896m
```

기존 environment 안에 추가 또는 수정:

```yaml
      APP_GENERATION_WORKERCOUNT: "3"
      APP_AI_CLIENT: "TERRA"
      OPENAI_API_KEY: "${OPENAI_API_KEY:?OPENAI_API_KEY is missing}"
      AI_CORRECTION_CONTRACT_VERSION: "v1.1"
      AI_CORRECTION_PROMPT_VARIANT: "DEFAULT"
```

기존 운영 JVM 설정 유지:

```yaml
      JAVA_TOOL_OPTIONS: "-Xms256m -Xmx512m -XX:+UseSerialGC -Duser.timezone=Asia/Seoul"
```

앞서 공유된 EC2 운영 서비스에는 TERRA·API 키·v1.1 항목이 없었다. 현재 코드에서 클라이언트 미지정 시 기본 경로는 LEGACY_MOCK이므로 개발과 같은 실제 API 연동에는 명시가 필요하다.

설정 검사:

```bash
cd /srv/mvp
docker compose -f /srv/mvp/docker-compose.yml config -q
```

출력 없이 정상 종료하면 구성 검사를 통과한 것이다. 앱 기동이나 실제 API 키의 유효성을 검사하는 명령은 아니다.

## 6. main PR로 적용

기록 시점에는 AI 코드가 main 배포 전이어서, 서버 설정을 준비한 뒤 기존 자동 배포를 이용하기로 했다.

1. main 대상 PR을 만든다.
2. PR의 Spotless·테스트 CI 결과를 확인한다.
3. 운영의 진행 중인 첨삭이 없을 때 병합한다.
4. GitHub Actions의 Deploy to EC2 성공을 확인한다.
5. 새 앱의 기동과 실제 설정·기능을 확인한다.

자동 배포는 새 latest 이미지와 EC2의 Compose를 사용한다. 이번 설정 변경을 위해 Java를 다시 수정할 필요는 없지만, 배포 이미지에는 앞서 확인한 워커 설정 코드가 포함되어 있어야 한다.

Actions 성공만으로 앱이 정상 기동을 마쳤다고 보장하지 않는다. 로그와 실제 사이트도 확인한다.

## 7. 배포 후 확인

```bash
docker stats --no-stream mvp-spring-dev mvp-spring
```

기대 RAM 상한은 개발 512MiB, 운영 896MiB다.

```bash
docker exec mvp-spring printenv \
  SPRING_PROFILES_ACTIVE \
  APP_GENERATION_WORKERCOUNT \
  JAVA_TOOL_OPTIONS \
  APP_AI_CLIENT \
  AI_CORRECTION_CONTRACT_VERSION
```

기대값은 순서대로 prod, 3, Xmx512m이 포함된 JVM 옵션, TERRA, v1.1이다.

키 자체를 출력하지 않고 존재만 확인:

```bash
docker exec mvp-spring sh -c '
if [ -n "${OPENAI_API_KEY:-}" ]; then
  echo "OPENAI_API_KEY=SET"
else
  echo "OPENAI_API_KEY=NOT_SET"
fi
'
```

```bash
docker logs --tail 50 mvp-spring
```

기동 확인 후 로그인과 첨삭 한 건을 시험한다. 세 요청 동시 처리 중에는 호스트 여유도 관찰한다.

```bash
free -m -s 2
```

[JVM 모니터](02-monitoring-and-reproduction.md)를 운영에 연결할 때는 AUTEDI_TARGET=mvp-spring으로 변경한다. 개발 대상으로 두면 운영 JVM을 보는 것이 아니다.

대화에서는 호스트 available을 200~300MiB 정도 남기는 목표를 제안했다. 이는 이 작은 서버에서의 임시 판단 목표이며 일반적인 안전 기준이 아니다. 현재는 부하 중 실제 최저값 자료가 없다.

## 8. 문제 발생 시 설정 복원

같은 SSH 세션에 백업 변수가 남아 있는 경우의 예시다. 새 세션이라면 실제 백업 경로를 확인해 변수를 설정한다. 먼저 진행 중 작업이 끝났는지 확인한다.

```bash
cp "$AUTEDI_COMPOSE_BACKUP" /srv/mvp/docker-compose.yml
AUTEDI_PROD_PROJECT=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' mvp-spring)
```

```bash
docker compose -p "$AUTEDI_PROD_PROJECT" \
  -f /srv/mvp/docker-compose.yml \
  config -q
```

```bash
docker compose -p "$AUTEDI_PROD_PROJECT" \
  -f /srv/mvp/docker-compose.yml \
  up -d --no-deps --force-recreate --no-build --pull never spring-app
```

이것은 Compose 설정 복원이다. 새로 배포한 앱 코드를 이전 버전으로 돌리는 작업은 별도다. 백업 이후 다른 담당자가 설정을 수정했다면 그 변경까지 덮어쓰지 않도록 차이를 확인한다.

## 9. 중간에 발생한 로그인 문제

메모리 변경 후 개발 사이트에서 로그인되지 않는다는 보고가 있었다.

- 실제 요청 URL: https://api.autedi.co.kr/api/v1/auth/login
- HTTP 상태: 401
- 응답 코드: AUTH_005
- 메시지: 이메일 또는 비밀번호가 올바르지 않습니다.

AuthService는 이메일 계정을 찾지 못한 경우와 비밀번호가 일치하지 않는 경우에 같은 오류를 반환한다. 메모리 제한 변경 자체가 비밀번호를 바꿨다는 근거는 없다. 재생성에 따른 환경 재해석, 접속 DB, 실제 Nginx 라우팅 등이 확인 후보였다.

도메인 이름만으로 운영 백엔드에 도착했다고 단정하지 않는다. 실제 로그와 연결 DB를 대응시켜야 한다. 해결 결과는 공유되지 않았으므로 원인을 확정한 사건으로 기록하지 않는다.

해당 시각의 오류가 어디에 기록됐는지 확인하는 조회 예시:

```bash
docker logs --since 5m mvp-spring-dev 2>&1 | grep 'AUTH_005'
docker logs --since 5m mvp-spring 2>&1 | grep 'AUTH_005'
```

MySQL에서 계정 존재를 확인하는 예시다. DB 이름은 실제 설정에 맞춘다. 비밀번호 해시나 토큰을 표시할 필요는 없다.

```sql
SELECT id, email, status, role
FROM autedi_dev.users
WHERE email = '로그인에_사용한_이메일';

SELECT id, email, status, role
FROM autedi.users
WHERE email = '로그인에_사용한_이메일';
```

계정이 있다는 사실만으로 비밀번호 일치나 로그인 API가 그 DB를 사용한다는 것까지 확인할 수는 없다.

## 참고 자료

- [Compose .env·치환·우선순위](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)
- [컨테이너로 전달하는 environment·env_file](https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/)
- [Compose up과 재생성](https://docs.docker.com/reference/cli/docker/compose/up/)
- [Compose restart의 환경변수 변경 제한](https://docs.docker.com/reference/cli/docker/compose/restart/)
- [Spring Boot 환경변수 이름](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables)

