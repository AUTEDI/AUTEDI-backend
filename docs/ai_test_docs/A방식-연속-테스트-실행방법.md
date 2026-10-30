# 전사 → 첨삭 연속 실행

## 첨삭 좌표 형식 (2026-10-30 변경)

모델은 이제 `polygons` 대신 `boxes: [[left, top, right, bottom]]`을 반환합니다.
기준은 API에 전달한 회전 보정 PNG 전체이며 값은 0~1입니다. 여러 줄은 박스를 여러 개 반환합니다.
Java는 숫자 4개, 범위, `left < right`, `top < bottom`을 검사하고 잘못된 박스를 제외합니다.
최종 `result.json`에는 `boxes`와 같은 순서의 픽셀 좌표 `pixel_boxes`가 저장됩니다.
`pixel_boxes`의 마지막 두 값은 너비·높이가 아니라 오른쪽·아래쪽 위치입니다.
위치를 찾지 못하면 빈 배열과 `location_uncertain=true`를 요청합니다.
기존 저장 결과의 `polygons`/`pixel_polygons`는 변경하지 않습니다.
박스 검증 통과는 실제 글자를 정확히 감싼다는 보장이 아니며, 원본 JPG 좌표로 역변환하지 않습니다.

## 실행

`src/test/java/com/ceos24/autedi/aiexperiments/TerraFlowExample.java`의 `main`을 IDE에서 실행하거나,
autedi 프로젝트 폴더에서 아래 명령을 실행합니다. 기본 이미지는 test7입니다.

```powershell
.\gradlew.bat terraFlow
```

다른 이미지:

```powershell
.\gradlew.bat terraFlow --args="../test_data/test8.jpg"
```

API 호출 없이 입력·키 존재 여부만 검사:

```powershell
.\gradlew.bat terraFlow --args="--check"
```

## 실행 과정

1. EXIF 회전을 보정한 PNG를 `build/ocr-results/flow-<고유번호>/image.png`에 저장합니다.
2. 전사 API를 한 번 호출하고 반환한 텍스트를 같은 폴더의 `image.png.transcription.txt`에 저장합니다.
3. 방금 저장한 전사문과 같은 PNG로 첨삭 API를 한 번 호출합니다.
4. 기존 첨삭 검증 로직을 거쳐 `build/ocr-results/correction-<고유번호>/`에 결과를 저장합니다.
   콘솔의 `Result directory`에서 경로를 확인합니다.

성공 시 총 2회 유료 요청이 발생하며 자동 재시도는 없습니다. 전사가 실패하거나 비어 있으면
첨삭 요청은 보내지 않습니다. 첨삭이 실패해도 이번 전사 파일은 flow 폴더에 남습니다.
콘솔에는 각 단계의 API 시간·토큰 사용량과 전체 흐름 시간(로컬 처리 포함)이 표시됩니다.

일반 Gradle `test`에서는 이 수동 실험을 실행하지 않습니다. IDE에서는 클래스의 `main` 옆
실행 버튼을 사용합니다. `--check`는 검사 전용 파일을 생성하지만 API 호출이나 실제 전사·첨삭은
하지 않으며, 키 유효성도 확인하지 않습니다.

기존 단독 전사 예제는 원본 이미지를 보내지만, 이 연속 실험은 두 단계 모두 회전 보정 PNG를
보냅니다. 이전 실험과 시간·인식 결과를 비교할 때 이 입력 차이를 고려해야 합니다.
본문 영역 자르기나 교정 제안 자동 적용은 포함하지 않습니다.
