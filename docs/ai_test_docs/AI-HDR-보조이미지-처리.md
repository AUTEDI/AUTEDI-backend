# HDR 보조 이미지가 있는 JPEG 처리

## 변경한 동작

기존에는 ImageIO가 JPEG 안에서 두 이미지를 발견하면 무조건 FILE_004로 거부했다.
이제 기본 사진 + 확인된 HDR gain map 한 개는 학습지 한 장으로 처리한다.
원본 바이트는 보조 이미지까지 그대로 저장하고, 기본 사진만 EXIF 방향 보정 후 PNG로 저장한다.
gain map은 별도 학습지 페이지로 만들거나 기본 사진 위에 합성하지 않는다.

OCR·첨삭·화면의 공통 입력인 `WorksheetService.getPreparedImage`와 저장·조회 경로는 그대로 사용한다.
페이지 width/height는 보정 후 PNG의 px 크기, regions는 PNG 전체 기준 NORMALIZED_0_1이다.
AI 클라이언트·프롬프트·DB 구조·기존 실험 코드는 변경하지 않았다.

## 지원하는 구분 기준

`JpegGainMapMetadata`는 ImageIO가 실제로 읽은 이미지가 정확히 두 개이며,
기본 JPEG에 MPF 표식이 있는 경우에 두 번째 이미지의 APP1 XMP를 확인한다.
JPEG 안에서 임의 문자열을 검색하지 않고 XMP namespace URI와 속성 값을 읽는다.
속성의 attribute·element 표기와 다른 namespace 접두사를 지원한다.

- Apple: `http://ns.apple.com/pixeldatainfo/1.0/`의 `AuxiliaryImageType`이
  `urn:com:apple:photo:2020:aux:hdrgainmap`인 보조 이미지.
- Adobe/Ultra HDR: 기본·보조 이미지의 `http://ns.adobe.com/hdr-gain-map/1.0/`에
  `Version=1.0`이 있고 `BaseRenditionIsHDR`이 생략되거나 False인 구조.
  기본 이미지가 HDR인 경우에는 SDR 변환을 별도로 구현하지 않았으므로 거부한다.

판별은 ImageIO가 구분한 두 번째 JPEG의 메타데이터에 연결되어 있다.
MPF 표식만으로 임의의 두 사진을 허용하지 않으며, 별도의 MPF 오프셋 파서는 추가하지 않는다.
보조 이미지 픽셀은 디코딩하지 않는다. 실제 색·밝기는 기본 JPEG를 읽는 기존 ImageIO 경로를 사용한다.

기준 자료: [Apple HDR gain map 설명](https://developer.apple.com/documentation/appkit/applying-apple-hdr-effect-to-your-photos),
[Android Ultra HDR 형식](https://developer.android.com/media/platform/hdr-image-format).

## 유지하는 제한

- 일반 JPG/JPEG·PNG 한 장과 기존 EXIF 1~8 방향 보정은 동일하다.
- 여러 파일 업로드, 일반 사진을 이어 붙인 JPEG, 알 수 없는 보조 이미지, 세 개 이상의 이미지는 거부한다.
- 손상 XMP·잘못된 namespace·상충하는 속성 값도 거부한다.
- XMP의 DTD·외부 엔티티·외부 스키마 접근은 차단한다.
- 원본·보정 PNG 20MB 및 기본 사진 4천만 픽셀 제한은 그대로다.
- PDF·HEIC·ISO 메타데이터만 있는 형식·깊이 등 다른 보조 이미지·HDR 화면 합성은 지원 범위 밖이다.

## 검증과 직접 확인

고정 데이터 테스트는 작은 JPEG와 실제 구조의 MPF·XMP를 만들어 사용한다.
원본 보존, 기본 사진의 모든 픽셀과 EXIF 보정 PNG 일치, 미지원 구조와 손상 메타데이터 거부를 검사한다.
기존 H2·임시 저장소 API 테스트에도 Apple HDR 입력을 추가해 업로드 → 원본/PNG 저장 → 인증 조회,
페이지 크기·좌표 단위, 공통 처리 입력과 화면 응답의 동일 바이트를 확인한다.
기존 권한·파일 교체·실패·롤백 테스트를 함께 실행한다.

실제 사진 `test8.jpg`, `test7.jpg`, `test6.jpg`는 로컬에서만 확인한다.
사진 파일을 테스트 리소스에 복사하거나 Git에 추가하지 않는다.
보정 PNG와 로컬 검사 기록은 Git에서 제외되는 `build/hdr-image-test/`에 남긴다.

직접 확인할 때는 수정된 서버를 `AUTEDI Local - FIXED` 설정으로 다시 실행한다.
`http/http-client.env.json`의 imagePath를 `../../test_data/test8.jpg`로 지정하고
`service-flow.http`의 01 로그인 → 02 업로드 → 07 보정 PNG를 실행한다.
업로드 응답이 201이고 pages가 한 개이며, PNG의 실제 크기와 pages.width/height가 일치해야 한다.
04 Generate를 실행할 필요는 없다. 원본 다운로드는 업로드 사진과 바이트·SHA-256이 같아야 한다.

유료 AI 호출, 운영·공유 DB 적용, 실행 중인 서버의 자동 재시작은 수행하지 않는다.

## 이번 실행 결과

| 검사 | 최종 결과 |
|---|---|
| WorksheetImagePreparerCompatibilityTest | 8건 통과: 기존 JPEG/PNG·EXIF 8방향·두 바이트 순서 |
| LocalStorageServiceTest | 7건 통과: 저장소 경로·파일 제한·원본 보존 |
| WorksheetImageFlowTest | 10건 통과: HDR 포함 업로드·조회·소속 권한·교체·실패·롤백 |
| WorksheetHdrImageTest | 5건 통과: Apple/Adobe·픽셀/방향·미지원/손상/외부 엔티티/HDR 기본 이미지 거부 |
| 기존 전체 흐름 시나리오 1개 | HDR 입력으로 성공: 전사 보존 → 첨삭 실패 → 전사 재사용 재시도 → 저장·검토·교사 편집·좌표 연결 |
| spotlessCheck / assemble | 통과 |

서로 다른 자동 테스트 31개 항목의 최종 결과가 통과다.
최초 실행에서는 새 고정 JPEG의 JFIF APP0가 첫 표식이 아닌 위치에 있어 새 HDR 관련 6건이 실패했다.
이는 이번에 추가한 테스트 샘플의 문제로, 기존 실험 이미지 생성은 그대로 두고 새 HDR 샘플만 EXIF JPEG로 수정했다.
수정 후에는 실패한 HDR 5건·업로드 매개변수 4건·전체 흐름 1건의 총 10건만 실행해 모두 통과했다.
앞서 통과한 나머지 21건은 반복하지 않았다. 최신 JUnit 보고서에는 마지막 실행한 10건이 표시된다.

실제 사진 검사도 모두 통과했다. 각 원본이 바뀌지 않았고,
기본 사진 전체 픽셀에 EXIF 방향을 적용한 결과와 생성 PNG가 픽셀 단위로 동일했다.

| 원본 | 보정 PNG 크기 | 원본 EXIF 방향 |
|---|---|---|
| test8.jpg | 3024 × 4032 px | 6 |
| test7.jpg | 3024 × 4032 px | 6 |
| test6.jpg | 2211 × 2535 px | 1 |

로컬 기록: `build/hdr-image-test/result.md`, PNG: 같은 폴더의 `test*-prepared.png`.
이 세 사진의 확인은 Java 이미지 처리만 실행했으며 DB 저장·실제 AI 생성은 수행하지 않았다.
HDR의 API 저장·조회와 생성 흐름은 개인정보 없는 작은 고정 이미지 및 H2·임시 저장소로 검증했다.

Gradle wrapper가 로그 없이 시작 단계에서 대기해 중단한 뒤,
이미 설치된 동일 버전 Gradle 9.8.2를 직접 실행했다. 캐시 접근 권한 외에 외부 의존성 다운로드는 필요하지 않았다.
전체 348건 회귀 검사는 이번 업로드 변경과 무관한 범위이므로 반복하지 않았다.
실제 프론트 표시, 기종별 HDR 색·밝기 품질과 다른 보조 이미지 형식은 이번 검증 범위에 포함하지 않는다.

이후 사용자의 전체 서비스 재검증 요청으로 전체 자동 검사를 다시 실행했다.
2026-11-01 최신 결과는 **20개 클래스·354건 통과, 실패·오류·건너뜀 0건**이며,
자세한 내용은 [HDR 변경 후 전체 서비스 재검증](AI-외부API없는-전체통합검증.md#2026-11-01-hdr-변경-후-전체-서비스-재검증)에 기록했다.

관련 검사 재현 명령은 다음과 같다. Test JVM만 H2로 강제하며 유료 실험 main을 실행하지 않는다.

```powershell
.\gradlew.bat --offline -I scripts/local-h2-tests.init.gradle test --tests '*WorksheetHdrImageTest' --tests '*WorksheetImagePreparerCompatibilityTest' --tests '*WorksheetImageFlowTest' --tests '*LocalStorageServiceTest' --tests '*FixedResponseReviewApiTest.fullOfflineFlowPreservesRotatedImageCoordinatesAndAiSourceThroughFailureRetryAndTeacherEdits' spotlessCheck
.\gradlew.bat --offline assemble
```
