# JVM 모니터링과 테스트 재현

[문서 목록](README.md) · [실험 결과](01-test-results.md) · [운영 전환](03-production-rollout.md)

이 문서의 명령은 EC2의 Ubuntu SSH 터미널용이다. SQL만 MySQL 프롬프트에서 실행한다. 실제로 실행한 모든 명령의 원본 로그가 아니라, 성공한 방법과 후속 재현 절차를 정리한 문서다.

## 1. 현재 상태부터 확인

```bash
docker stats --no-stream
free -h
docker inspect \
  -f 'Memory={{.HostConfig.Memory}} MemorySwap={{.HostConfig.MemorySwap}}' \
  mvp-spring-dev
docker exec mvp-spring-dev printenv JAVA_TOOL_OPTIONS
```

Memory는 RAM 상한, MemorySwap은 RAM+스왑 상한이며 단위는 바이트다.

| RAM 상한 | Memory | 추가 스왑 512MiB를 허용할 때 MemorySwap |
|---:|---:|---:|
| 512MiB | 536870912 | 1073741824 |
| 768MiB | 805306368 | 1342177280 |
| 896MiB | 939524096 | 1476395008 |

이 표의 MemorySwap은 유한한 추가 스왑 512MiB를 명시하는 예시다. 원래 컨테이너의 -1, 0 등 다른 정책에 임의로 적용하지 않는다.

실제로 사용한 Compose 파일 목록은 다음 명령으로 확인한다.

```bash
docker inspect \
  -f '{{index .Config.Labels "com.docker.compose.project.config_files"}}' \
  mvp-spring-dev
```

쉼표로 여러 파일이 나오면 생성 시 함께 사용한 파일 목록이다. 파일을 나중에 수정하거나 `docker update`로 상한을 바꾸면 현재 파일 내용과 실행 상태가 다를 수 있으므로 실제 상한도 따로 확인한다.

## 2. jcmd가 없는 JRE 이미지에서 내부 힙 조회

이 프로젝트의 런타임 이미지는 JRE이고 Java가 PID 1로 실행된다. 개발 컨테이너에서 `jcmd 없음`이 확인됐다.

별도 JDK 진단 컨테이너로 조회한 명령은 성공했다.

```bash
AUTEDI_TARGET=mvp-spring-dev

docker run --rm \
  --pid="container:${AUTEDI_TARGET}" \
  --cap-add=SYS_PTRACE \
  --network=none \
  --memory=128m \
  -e 'JAVA_TOOL_OPTIONS=-Xms16m -Xmx48m -XX:+UseSerialGC' \
  --entrypoint /opt/java/openjdk/bin/jcmd \
  eclipse-temurin:21-jdk-jammy \
  1 GC.heap_info
```

- 운영 조회 시 AUTEDI_TARGET을 `mvp-spring`으로 바꾼다.
- 128MiB 제한과 -Xmx48m은 진단 컨테이너 자신의 설정이다.
- --network=none도 진단 컨테이너에 적용된다. 백엔드의 네트워크를 끄는 옵션이 아니다.
- 진단 컨테이너도 호스트 RAM을 사용하지만, 그 사용량은 개발 컨테이너 stats에 합산되지 않는다.
- 최초 실행 시 JDK 이미지를 내려받을 수 있다.
- 이 방법은 현재의 Java 21·PID 1·권한 조건에서 성공했다. 다른 UID/GID나 attach 제한에서는 추가 확인이 필요하다.
- 강제 GC나 힙 덤프는 실행하지 않는다.

초기 시도에서는 `/proc/<호스트PID>/root/tmp`를 진단 컨테이너 /tmp로 bind mount하려다가 runc의 `invalid argument` 오류가 발생했다. 성공한 방법은 위처럼 이 bind mount를 제외하고 PID namespace를 공유하는 방식이었다.

## 3. 실시간 jstat 모니터

다른 SSH 창에서 실행하고 해당 창은 모니터링용으로 둔다. 운영은 AUTEDI_TARGET만 변경한다.

```bash
AUTEDI_TARGET=mvp-spring-dev

docker run --rm \
  --name "autedi-jvm-monitor-${AUTEDI_TARGET}" \
  --pid="container:${AUTEDI_TARGET}" \
  --cap-add=SYS_PTRACE \
  --network=none \
  --memory=128m \
  -e 'JAVA_TOOL_OPTIONS=-Xms16m -Xmx48m -XX:+UseSerialGC' \
  --entrypoint /opt/java/openjdk/bin/jstat \
  eclipse-temurin:21-jdk-jammy \
  -gc -t 1 500 |
awk '
function v(k) { return $(col[k]) + 0 }

$1 == "Timestamp" {
  for (i=1; i<=NF; i++) col[$i]=i
  print "   SEC  HEAP_USED  HEAP_FREE  META_USED    YGC    FGC   GC_SEC"
  fflush()
  next
}

col["EU"] && $1 ~ /^[0-9]/ {
  if (!started++) {
    t0=v("Timestamp"); y0=v("YGC"); f0=v("FGC"); g0=v("GCT")
  }

  used=(v("S0U")+v("S1U")+v("EU")+v("OU"))/1024
  spare=(v("S0C") < v("S1C") ? v("S0C") : v("S1C"))
  capacity=(v("EC")+v("OC")+v("S0C")+v("S1C")-spare)/1024

  printf "%6.1f %10.1f %10.1f %10.1f %6.0f %6.0f %8.3f\n", \
    v("Timestamp")-t0, used, capacity-used, v("MU")/1024, \
    v("YGC")-y0, v("FGC")-f0, v("GCT")-g0

  fflush()
}' | tee "autedi-jvm-${AUTEDI_TARGET}-$(date +%Y%m%d-%H%M%S).log"
```

약 0.5초 간격으로 출력하고 SSH 작업 디렉터리에 로그도 저장한다. Ctrl+C로 종료한다.

`jstat` 출력은 버전에 따라 달라질 수 있다. 이 정형화는 이번 Java 21·Serial GC용으로, survivor의 GC용 예비 공간을 현재 사용 가능 용량에서 제외한다. 다른 GC로 바꾸면 산출 방식도 확인한다.

### 지표 읽기

| 항목 | 의미 |
|---|---|
| SEC | 모니터 첫 샘플 이후 경과 초. 요청 처리 시간이 아님 |
| HEAP_USED | 현재 힙 사용량 MiB. 아직 GC하지 않은 임시 객체도 포함할 수 있음 |
| HEAP_FREE | 현재 확보한 사용 가능 힙에서 비어 있는 공간 MiB |
| META_USED | 클래스 정보 등을 담는 Metaspace 사용량 MiB |
| YGC / FGC | 모니터 첫 샘플 이후 young / full GC 횟수 증가 |
| GC_SEC | 모니터 첫 샘플 이후 누적 GC 시간 증가(초) |

GC_SEC는 이전 행 대비 증가량이나 매번의 정지 시간이 아니다. 요청 구간의 영향을 보려면 요청 시작 직전과 완료 후의 차이를 계산한다. 정확한 요청 지연은 DB 타임스탬프 등으로 따로 기록한다.

FGC 횟수가 먼저 바뀌고 다음 행에서 시간이 늘어나는 등 갱신 시점이 다를 수 있다. 정확한 정지 분석은 GC·safepoint 로그가 필요하다.

### 모니터의 수명

- 백엔드를 재생성하면 이전 모니터가 새 JVM을 따라가는 것은 아니다. 기존 모니터를 종료하고 다시 실행한다.
- `docker update`로 RAM 상한만 바꾸면 JVM은 유지되므로 기존 모니터를 계속 사용할 수 있다.
- 이름 충돌이 나면 먼저 기존 진단 컨테이너를 확인한다.

```bash
docker ps --no-trunc \
  --filter ancestor=eclipse-temurin:21-jdk-jammy
```

필요하면 자신이 실행한 개발 모니터만 종료한다.

```bash
docker stop autedi-jvm-monitor-mvp-spring-dev
```

`--rm` 방식이라 종료된 진단 컨테이너는 삭제된다. JDK 이미지가 남아 있는 것과 모니터가 실행 중인 것은 다르다.

## 4. 호스트 여유와 실제 스왑 활동

별도 터미널에서 요청 시작 전부터 종료 후까지 관찰한다.

```bash
free -m -s 2
```

`available`은 스왑 없이 추가 사용 가능한 메모리의 추정값이다. `free`만 보고 판단하지 않는다.

실제 스왑 입출력은 다음 명령으로 관찰한다.

```bash
vmstat -y 1
```

- `si`: 스왑에서 읽어오는 양
- `so`: 스왑으로 내보내는 양
- `-y`: 부팅 이후 평균인 첫 행을 제외

스왑 사용량이 남아 있는 것과 현재 입출력이 계속되는 것은 다르다. 호스트 지표이므로 다른 프로세스의 활동도 포함된다.

상한 이벤트는 같은 컨테이너에서 테스트 전후 두 번 읽어 증가량을 비교한다.

```bash
docker exec mvp-spring-dev cat /sys/fs/cgroup/memory.events
```

컨테이너별 현재 스왑 사용량은 다음과 같이 읽는다. 출력은 바이트이며 입출력 속도가 아니다.

```bash
docker exec mvp-spring-dev cat /sys/fs/cgroup/memory.swap.current
```

## 5. 테스트 Compose 공통 절차

진행 중인 첨삭이 끝난 뒤 실시한다. 현재 코드는 재시작으로 중단된 작업을 SERVER_INTERRUPTED로 처리한다.

```bash
cd /srv/mvp
AUTEDI_PROJECT=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' mvp-spring-dev)
```

아래는 RAM 896MiB·힙 최대 512MiB·워커 3개 조건이다. 테스트 파일을 통째로 다시 작성하므로 파일에 추가한 다른 설정이 있다면 먼저 확인한다.

```bash
cat > /srv/mvp/docker-compose.memory-test.yml <<'YAML'
services:
  spring-app-dev:
    mem_limit: 896m
    memswap_limit: 1408m
    environment:
      JAVA_TOOL_OPTIONS: "-Xms128m -Xmx512m -XX:+UseSerialGC -Duser.timezone=Asia/Seoul"
      APP_GENERATION_WORKERCOUNT: "3"
    deploy:
      resources:
        limits:
          memory: 896m
YAML
```

| 재현 조건 | mem_limit / deploy memory | memswap_limit | Xmx | WORKERCOUNT |
|---|---|---|---|---|
| 첫 확대 실험 | 768m | 1280m | 512m | 2 |
| 다음 상한 실험 | 896m | 1408m | 512m | 2 |
| 세 워커 실험 | 896m | 1408m | 512m | 3 |

EC2 원본의 DB·S3·AI 설정과 테스트 파일을 병합한다. .env도 다시 읽으므로 재현 중 해당 파일 및 배포 이미지가 함께 바뀌지 않게 한다.

검사:

```bash
docker compose -p "$AUTEDI_PROJECT" \
  -f /srv/mvp/docker-compose.yml \
  -f /srv/mvp/docker-compose.memory-test.yml \
  config -q
```

오류나 환경변수 누락 경고가 없을 때 적용:

```bash
docker compose -p "$AUTEDI_PROJECT" \
  -f /srv/mvp/docker-compose.yml \
  -f /srv/mvp/docker-compose.memory-test.yml \
  up -d --no-deps --force-recreate --no-build --pull never spring-app-dev
```

`--pull never`는 새 이미지를 다운로드하지 않는다는 뜻이다. 로컬 :dev 태그가 다른 작업으로 바뀌었다면 이전 실행 이미지와 완전히 같음을 보장하지 않는다. 엄밀한 비교에는 이미지 ID도 기록한다.

확인:

```bash
docker exec mvp-spring-dev printenv APP_GENERATION_WORKERCOUNT JAVA_TOOL_OPTIONS
docker stats --no-stream mvp-spring-dev
docker inspect -f '{{.Image}}' mvp-spring-dev
```

세 워커 실험의 기대값은 환경변수 3, Xmx512m, RAM 상한 896MiB다. 실제 세 작업의 동시 실행은 DB 시작·완료 시점과 generation-1/2/3 로그의 시간 겹침으로도 확인한다.

테스트 파일이 적용 파일 목록에서 빠진 시점이 있었지만 변경 주체는 확인하지 못했다. 개발 자동 배포가 원본만 사용해 재생성하는 것은 가능한 원인 중 하나다.

## 6. 상한만 768→896MiB로 올린 방법

힙 최대 512MiB와 워커 수를 유지한 상태에서 안내한 명령이다.

```bash
docker update \
  --memory=896m \
  --memory-swap=1408m \
  mvp-spring-dev
```

이 변경은 Compose 파일에 저장되지 않는다. 다음 워커 변경으로 재생성할 때는 테스트 파일에도 896m / 1408m을 반영한다.

## 7. 개발 원복

진행 중 첨삭과 진단 모니터를 종료한 후 원본만 사용한다.

```bash
cd /srv/mvp
AUTEDI_PROJECT=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' mvp-spring-dev)
```

```bash
docker compose -p "$AUTEDI_PROJECT" \
  -f /srv/mvp/docker-compose.yml \
  config -q
```

```bash
docker compose -p "$AUTEDI_PROJECT" \
  -f /srv/mvp/docker-compose.yml \
  up -d --no-deps --force-recreate --no-build --pull never spring-app-dev
```

기대값은 RAM 512MiB·Xmx384m·워커 2개다. EC2 원본에 워커 환경변수가 없으면 현재 코드의 기본값 2를 사용한다. 이번 로컬 참고 Compose에는 2를 명시했다.

실제 복원 대상은 그 시점의 EC2 원본이다. 다른 담당자가 수정했다면 그 값이 적용된다. 테스트 파일을 삭제하는 것만으로 실행 중 컨테이너의 설정이 돌아가지는 않는다.

## 8. 다음 측정의 조건

- 같은 이미지·AI 계약·이미지 수로 비교하고, 업로드를 먼저 끝낸 조건과 업로드도 겹친 조건을 구분한다.
- 재시작 직후인지 동일 JVM에서 반복한 이후인지 기록한다. 이번 시험들의 초기 상태는 완전히 같지 않았다.
- 워커 2개에는 요청 2개, 워커 3개에는 요청 3개를 동시에 보낸다.
- 작업 ID, 시작·완료 시간, 메모리 최대 관측값, GC 차이, 호스트 available 최저값을 기록한다.
- 강제 GC로 비교 조건을 바꾸지 않는다.
- 샘플 사이에 순간 피크가 있을 수 있으므로 관측 최대값을 진짜 최대값으로 단정하지 않는다.

MySQL의 개발 DB에서 대기와 실행을 구분하는 SQL:

```sql
SELECT
    id,
    status,
    current_stage,
    created_at,
    started_at,
    completed_at,
    ROUND(TIMESTAMPDIFF(MICROSECOND, created_at, started_at) / 1000000.0, 1) AS wait_sec,
    ROUND(TIMESTAMPDIFF(MICROSECOND, started_at, completed_at) / 1000000.0, 1) AS run_sec,
    error_code
FROM worksheet_generations
ORDER BY id DESC
LIMIT 10;
```

API 호출 시간 로그:

```bash
docker logs --since 10m mvp-spring-dev 2>&1 | grep 'AI_API_CALL'
```

## 참고 자료

- [JDK 21 jcmd](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html)
- [JDK 21 jstat](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jstat.html)
- [Docker update](https://docs.docker.com/reference/cli/docker/container/update/)
- [Compose 파일 병합](https://docs.docker.com/compose/how-tos/multiple-compose-files/merge/)
- [vmstat](https://man7.org/linux/man-pages/man8/vmstat.8.html)
- [free](https://man7.org/linux/man-pages/man1/free.1.html)

