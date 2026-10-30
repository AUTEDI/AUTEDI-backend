# AI 실험 문서와 실행 안내

현재 기준: [AI 실험 기준 버전 2026-10-30](AI-기준버전-2026-10-30.md).
실험 소스 패키지는 `com.ceos24.autedi.aiexperiments`다. 일반 Spring context 테스트는 원래 위치에 유지했다.
IDE에 기존 실행 설정이 남아 있으면 새 패키지의 main 클래스를 선택한다. Gradle task 이름은 그대로다.

| 파일 | 실험 목적 | 실행 |
|---|---|---|
| VisionOcrExample | Google Vision 전사·confidence·단어 좌표 비교 | visionOcr |
| TerraOcrExample | Terra 전사 + 모든 단어 8개 숫자 좌표, 초기 비교 | terraOcr |
| LunaOcrExample | Luna 전사 + 모든 단어 객체형 좌표, 초기 비교 | lunaOcr |
| TerraTextOcrExample | 좌표 없는 텍스트 전사 | terraTextOcr |
| TerraCorrectionExample | 저장된 전사문 + 이미지 → 첨삭 + 4개 숫자 boxes | terraCorrection |
| TerraFlowExample | 전사부터 첨삭까지 2회 순차 요청 | terraFlow |
| OcrTestImage | EXIF/eXIf 방향 보정 공통 도구 | 직접 API 호출 없음 |
| TerraOcrExampleTest | 초기 polygon 파싱 검증 | JUnit, API 없음 |
| TerraCorrectionExampleTest | 앵커·boxes·픽셀·겹침·EXIF 검증 | JUnit, API 없음 |

main 실험은 외부 API를 호출한다. OpenAI 예제의 --check는 호출하지 않는다.
Google Vision은 ADC 인증, OpenAI 예제는 프로젝트 루트 .env 또는 환경변수 OPENAI_API_KEY를 사용한다.
키 값·원본 사진·build 결과는 커밋하지 않는다. 테스트 리소스의 전사문은 실험 고정 입력이며 정답이 아니다.

- [연속 실행 방법](A방식-연속-테스트-실행방법.md)
- [첨삭 단독 실행·현재 응답 필드](A방식-첨삭-테스트-실행방법.md)
- [초기 실험 종합 비교](OCR-실험-종합비교-2026-10-29.md)

이전 날짜의 비교 문서는 당시 실험 조건을 기록한 자료다. 현재 응답은 polygons가 아닌 boxes이며,
최신 기준 문서와 실행 문서를 우선한다. 상대 이미지 기본 경로는 프로젝트 폴더에서 ../test_data다.
