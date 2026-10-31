# 단일 이미지 첫 연동 — 원본 보존·방향 보정 PNG·조회

## 범위와 서비스 흐름

```text
JPG/JPEG·PNG 한 장 업로드
  → 실제 이미지 형식·크기 확인
  → 원본 바이트 그대로 보관
  → JPEG EXIF / PNG eXIf 1~8 방향을 반영한 PNG 생성·별도 보관
  → 원본 정보는 Worksheet, 보정 PNG URL·픽셀 크기는 WorksheetPage에 기록
  → 학습지 상세 조회에서 원본 다운로드 URL과 보정 PNG URL 제공
  → 인증 후 보정 PNG 바이트 조회
```

AI 호출과 PDF 처리는 포함하지 않는다. 새 PDF 업로드·교체는 415로 거부한다.
이전에 저장된 PDF 원본의 다운로드를 허용하는 분기는 보존했지만 페이지 변환은 하지 않는다.
DB에 새 컬럼을 추가하지 않고 기존 file_url/file_hash와 preview_image_url/width/height를 사용한다.

## 파일과 좌표의 공통 기준

`WorksheetService.getPreparedImage(academyId, worksheetId, pageNumber)`가 공통 입력 진입점이다.
반환한 `PreparedWorksheetImage`는 PNG 바이트, SHA-256, 페이지 ID, 보정 후 크기와 조회 URL을 담는다.
화면 조회 API도 이 메서드를 사용한다. 향후 전사·첨삭은 같은 스냅샷의 png를 두 단계에 전달해야 한다.
원본 URL에서 이미지를 다시 디코딩하거나 두 단계에서 각각 회전 보정하지 않는다.
배열을 복사하므로 한 호출자가 반환 바이트를 변경해도 저장 파일이나 스냅샷은 바뀌지 않는다.
실제 전사·첨삭 호출부에 연결한 것은 아니며 기존 Mock은 그대로 유지한다.

JPEG 원본의 파일 형식·크기·해시는 원본 기준으로 남는다.
PNG는 메타데이터 없이 인코딩하므로 브라우저가 EXIF 방향을 다시 적용하지 않는다.
페이지 width/height는 이 보정 PNG의 실제 픽셀 크기이며, 방향 5~8에서는 가로·세로가 바뀐다.

## 이번 좌표 단위 결정

현재 A의 `RegionPayload` 설명 및 과거 DECISIONS 문서는 px로 되어 있었지만,
현재 MockCorrectionAiClient와 기존 고정 테스트는 0~1 값의 regions를 사용하고 있었다.
프론트 소스는 작업 공간에서 확인되지 않았고, 사용자가 좌표 단위 결정을 위임했다.
따라서 계약 v1·기존 Mock과 일치하고 화면 확대·축소에 독립적인 **0~1 정규화 좌표**를 채택했다.
이는 프론트와 이미 합의됐다는 뜻이 아니며, 프론트 연결 시 아래 기준을 적용해야 한다.
이 문서의 결정은 과거 px 설명보다 이번 단일 이미지 연동에 우선한다.

| 정보 | 기준 |
| --- | --- |
| pages.width / pages.height | 보정 PNG의 실제 크기, px 정수 |
| pages.regionCoordinateUnit | NORMALIZED_0_1 |
| AI boxes | 보정 PNG 전체 기준 [left, top, right, bottom], 0~1 |
| 서비스 regions | 보정 PNG 전체 기준 x/y/width/height, 0~1 |
| 이미지 방향·원점 | 방향이 반영된 PNG 좌상단, 오른쪽이 x 증가·아래쪽이 y 증가 |

boxes→regions는 x=left, y=top, width=right-left, height=bottom-top이다.
예: [0.30,0.37,0.34,0.42] → x=0.30,y=0.37,width=0.04,height=0.05.
이 단계에서는 AI 결과를 regions로 저장하는 변환을 연결하지 않는다.

프론트는 실제 표시된 이미지의 너비·높이에 regions 비율을 곱해 그린다.
left=x×표시 너비, top=y×표시 높이, 박스 너비=width×표시 너비, 높이=height×표시 높이이다.
object-fit 등의 여백이 있으면 이미지가 실제 그려진 사각형의 크기·시작 위치를 사용해야 한다.
원본 JPG 저장 픽셀 배열이나 화면 전체 컨테이너를 좌표 기준으로 사용하지 않는다.
기존 DB의 임의 좌표를 추정해서 정규화하거나 역변환하지 않는다.

## API

- POST /api/v1/worksheets: multipart 필드 file에 이미지 한 장. 성공 201, 상태 DRAFT.
- PUT /api/v1/worksheets/{id}/file: 기존 DRAFT/FAILED·If-Match 정책을 유지한다.
- GET /api/v1/worksheets/{id}: pages.imageUrl은 보정 PNG, file.originalUrl은 원본 다운로드이다.
- GET /api/v1/worksheets/{id}/file: 원본 바이트 그대로, 원본 MIME, attachment 다운로드.
- GET /api/v1/worksheets/{id}/pages/{pageNumber}/image: image/png, 보정 PNG 바이트.

다운로드·이미지 조회는 Bearer 인증이 필요하며, 타 학원 접근은 404로 은닉한다.
캐시는 no-store이고 ETag는 해당 파일의 SHA-256이다. 공개 파일시스템 경로를 새로 노출하지 않는다.
Bearer 헤더를 넣을 수 없는 단순 img src 대신 인증 fetch/axios로 Blob을 받아 Object URL로 표시해야 한다.
원본 다운로드 URL을 화면 표시 URL로 사용하지 않는다.

파일 교체 시 보정 PNG·페이지 크기를 갱신하고 OCR 결과·해시를 비우며 선택 설정을 보존한다.
이전 원본·PNG 파일을 자동 삭제하지 않는다. DB 롤백에서는 해당 요청이 새로 만든 파일 두 개만 정리한다.
업로드 당시 DB save 반환값을 쓰지 않아 미저장 Worksheet를 Page가 참조하던 기존 문제도,
반환된 관리 객체를 사용하도록 한 줄 수정했다.

## 제한·오류

- 원본과 보정 PNG 각각 최대 20MB, 디코딩 전 최대 4천만 픽셀 검사.
- 확장자와 실제 JPEG/PNG 형식이 일치해야 한다. 손상 파일을 기본 1000×1400으로 대체하지 않는다.
- 여러 파일을 보낸 요청은 400. PDF 등 미지원 형식은 415.
- 손상 이미지·EXIF 또는 저장 PNG/페이지 크기 불일치는 FILE_004(400).
- 크기 제한 초과는 FILE_005(413), 저장 파일 누락은 FILE_006(404).
- 보정 PNG가 없는 이전 업로드의 이미지 조회는 FILE_007(409). 기존 자료를 자동 수정하지 않는다.
  DRAFT/FAILED 자료는 파일 교체로 새 경로를 만들 수 있으며, READY 등 과거 자료의 일괄 이관은 별도 단계다.
- 저장 기본 경로 uploads는 Git에서 제외했다. 파일 영속 볼륨과 운영 스토리지 설정은 배포 환경에서 확인해야 한다.

## API 없는 자동 검증

```powershell
$env:JAVA_HOME = 'C:\Users\82108\.jdks\temurin-21.0.11-1'
.\gradlew.bat --offline test --tests "*LocalStorageServiceTest" --tests "*WorksheetImagePreparerCompatibilityTest" --tests "*WorksheetImageFlowTest"
.\gradlew.bat --offline build spotlessCheck
```

고정 이미지와 H2·임시 저장소를 사용한다. JPEG/PNG와 EXIF 8방향·두 바이트 순서,
기존 실험의 픽셀 변환과 일치, 원본 SHA·바이트 보존, PNG 메타데이터 제거, 페이지 크기,
화면 응답과 공통 처리 입력의 동일 바이트, 권한, 파일 교체·OCR 무효화·설정 보존,
손상 이미지·PDF·복수 파일 거부 및 롤백 파일 정리를 검증한다. AI 호출은 없다.

## 직접 확인

실행 중인 로컬 백엔드에 로그인한 뒤, 응답의 imageUrl을 인증 조회한다.
아래 토큰은 사용자가 입력할 자리 표시자이며 .env의 키를 사용하지 않는다.

```powershell
$imageFlowBase = 'http://localhost:8080'
$imageFlowToken = '<로그인에서 받은 Access Token>'
$imageFlowJson = curl.exe --fail-with-body -sS `
  -H "Authorization: Bearer $imageFlowToken" `
  -F 'file=@C:\path\photo.jpg' "$imageFlowBase/api/v1/worksheets"
$imageFlowData = ($imageFlowJson | ConvertFrom-Json).result
curl.exe --fail-with-body -sS -H "Authorization: Bearer $imageFlowToken" `
  -o '.\build\original-download.jpg' $imageFlowData.file.originalUrl
curl.exe --fail-with-body -sS -H "Authorization: Bearer $imageFlowToken" `
  -o '.\build\prepared-download.png' $imageFlowData.pages[0].imageUrl
```

원본 다운로드와 업로드한 파일의 SHA-256이 같아야 한다.
PNG를 열어 회전 방향을 확인하고 실제 픽셀 크기가 pages.width/height와 같은지 확인한다.
메타데이터와 PNG 조회가 준비되었을 뿐, 프론트 코드의 실제 렌더링은 이번 저장소에서 실행·검증하지 않았다.

## 이번에 진행하지 않은 다음 단계

실제 전사·첨삭 요청, 생성 상태·재시도 연결, AI 결과 저장·조회 계약 변경, PDF 처리,
과거 이미지·좌표 일괄 변환, 실제 프론트 연결 및 좌표 표시 품질 평가는 아직 진행하지 않았다.
