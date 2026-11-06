# AI 호출 사용량 로그

서비스의 Terra 클라이언트는 외부 API 호출마다 `INFO` 수준의 `AI_API_CALL` 로그를 한 줄 남긴다.
메시지 뒤에는 JSON이 붙는다. DB 저장이나 금액 계산은 하지 않는다.

아래는 설명용 예시이며 실제 측정값이 아니다. 실제 로그에는 캐시 쓰기 등 추가 필드도 포함된다.

```text
AI_API_CALL {"generation_id":101,"worksheet_id":1,"page_id":2,"operation":"autedi_transcription_v1","elapsed_ms":17648,"http_status":200,"error_code":null,"request_id":"req_example","response_id":"resp_example","requested_model":"gpt-6.1-terra","model":"gpt-6.1-terra","service_tier":"default","response_status":"completed","input_tokens":1000,"cached_tokens":400,"output_tokens":200,"reasoning_tokens":50,"total_tokens":1200}
```

## 확인 방법

서버 콘솔 또는 보관 중인 서버 로그에서 `AI_API_CALL`을 검색한다.
현재 Docker 구성의 운영·개발 컨테이너는 각각 다음 명령으로 확인할 수 있다.

```sh
docker logs --since 1h mvp-spring
docker logs --since 1h mvp-spring-dev
```

같은 `generation_id`로 인식·첨삭 로그를 묶는다.

| 필드 | 의미 |
|---|---|
| `generation_id` | 이번 생성 작업 ID. 수동 재시도는 새 작업 ID를 사용한다. |
| `worksheet_id`, `page_id` | 대상 학습지·페이지 ID |
| `operation` | 인식: `autedi_transcription_v1`, 첨삭: `autedi_correction_v1` |
| `elapsed_ms` | 요청 전송 직전부터 응답 본문 수신 완료 또는 대기 실패까지 걸린 밀리초. 대기열·이미지 준비·DB 저장 시간은 제외한다. |
| `http_status` | HTTP 응답 코드. 응답을 끝까지 받지 못했으면 `null` |
| `error_code` | 클라이언트의 HTTP·통신·응답 추출 실패 코드. 이후 전사·첨삭 내용 검증 및 DB 저장의 성공 여부와는 별개다. |
| `request_id`, `response_id` | 응답 헤더 `x-request-id`와 응답 본문 `id` |
| `requested_model`, `model` | 요청 모델과 실제 응답에 기재된 모델 |
| `service_tier`, `response_status` | 실제 응답의 처리 티어와 완료 상태 |
| `input_tokens`, `output_tokens`, `total_tokens` | 응답에 기재된 입력·출력·전체 토큰 수 |
| `cached_tokens` | 입력 중 캐시에서 읽은 토큰 수 |
| `cache_write_tokens` | 입력 중 캐시에 쓴 토큰 수 |
| `cache_write_30m_tokens`, `cache_write_12h_tokens` | 캐시 쓰기의 보존 기간별 세부 토큰 수 |
| `reasoning_tokens` | 출력에 포함된 추론 토큰 수. 출력에 다시 합산하지 않는다. |

## 집계 시 해석

- 새 생성은 보통 인식·첨삭 로그가 각각 하나씩 생긴다. 인식 실패 등으로 첨삭 요청을 보내지 않으면 첨삭 로그는 없다.
- 재사용할 전사가 있으면 이번 작업에는 첨삭 호출 로그만 남는다. 동일 키 재전송으로 새 외부 호출이 없으면 새 호출 로그도 없다.
- `incomplete`, 거부 응답, 결과 텍스트 추출 실패에도 응답에서 읽을 수 있는 사용량을 기록한다.
- 없는 값이나 형식이 잘못된 토큰 수는 `null`이다. 확인된 `0`과 구분하며, 시간 초과의 미확인 사용량을 0으로 계산하지 않는다.
- 사용량 로그가 존재해도 생성 작업이 최종 성공했다는 뜻은 아니다. 최종 결과는 생성 상태 및 작업 실패 로그와 함께 확인한다.
- API 키, 이미지, 전사문, 첨삭 본문, 외부 오류 메시지는 기록하지 않는다. 메타데이터는 허용한 식별자 형식과 0 이상의 정수 토큰 수만 기록한다.

비용 계산에는 응답의 모델·티어와 호출 당시의 토큰 종류별 단가를 적용한다.
서로 다른 재시도 작업까지 합칠 때는 DB의 `retry_of_generation_id` 연결을 사용한다.

## 검증

`TerraGenerationClientTest`는 로컬 대역 서버만 사용한다. 두 단계의 사용량·작업 ID·시간 기록,
미완료·HTTP 오류·잘못된 응답·시간 초과, 누락된 사용량과 원문·키 비노출을 검증한다.
일반 `test` 작업은 실제 유료 API를 호출하지 않는다.
