# A 방식 2단계 첨삭 테스트

## 하는 일

1단계에서 저장한 전사 텍스트와 같은 과제 사진을 함께 Terra에 보낸다. 한 번의 API 요청으로 첨삭 내용과 **첨삭 대상만의 좌표**를 요청한다. 전사 API를 내부에서 다시 호출하지 않으며 자동 재시도도 하지 않는다. Spring 서버와 DB는 실행하지 않는다.

모델은 `gpt-6.1-terra`, image detail은 high, 출력 한도는 12,000토큰이다. 추론 강도와 서비스 티어는 명시하지 않아 계정/모델 기본값이 적용된다. 로그와 결과 JSON에 API 시간, usage, 반환된 service_tier를 남긴다. 이 시간과 토큰은 2단계만의 수치다.

## 바로 실행

IntelliJ에서 `TerraCorrectionExample.main()`을 실행한다.

기본 사진은 `../test_data/test7.jpg`이며 전사문은 `src/test/resources/ocr/test7-transcription.txt`이다. 이 파일은 사용자가 제공한 텍스트 전용 Terra 실행 로그의 원문을 옮긴 **고정 입력**이다. 정답이나 모델의 새로운 실행 결과가 아니다. 기본 입력만으로 전사를 다시 호출하지 않고 첨삭을 시험할 수 있다.

터미널에서는 autedi 프로젝트 폴더에서 실행한다.

```powershell
.\gradlew.bat terraCorrection
```

이 명령은 실제 API를 1회 호출하므로 비용이 발생한다. 기존 `.env`의 `OPENAI_API_KEY`를 사용한다.

호출 전 로컬 점검만 하려면:

```powershell
.\gradlew.bat terraCorrection --args="--check"
```

`--check`는 키 값은 출력하지 않고 키 존재 여부만 확인한다. 키 유효성이나 모델 접근 권한은 실제 호출 전에는 확인되지 않는다.

## 다른 사진과 전사문 지정

사진 경로와 UTF-8 텍스트 경로를 순서대로 전달한다. IntelliJ Run Configuration의 Program arguments에도 같은 인수를 넣을 수 있다. 텍스트 파일에는 콘솔 로그가 아니라 전사 원문만 넣는다. 반드시 그 사진의 전사문을 사용한다.

```powershell
.\gradlew.bat terraTextOcr --args="../test_data/test8.jpg"
.\gradlew.bat terraCorrection --args="../test_data/test8.jpg build/ocr-results/test8.jpg.transcription.txt"
```

위 두 명령을 모두 실행하면 각각 1회, 총 2회의 API 호출이다. 첫 번째 명령은 이제 전사문을 `build/ocr-results/<이미지파일명>.transcription.txt`에 저장한다. 같은 파일명으로 재실행하면 그 전사 파일은 갱신된다. 기존 로그가 있다면 별도 UTF-8 파일에 전사문만 저장해 첫 번째 호출을 생략해도 된다.

## 반환 형식

모델은 `edits` 배열만 반환한다. 다음은 **설명용 가상 예시**이며 실제 test7의 API 응답이 아니다. 가정한 원문은 `He go to school yesterday.`이다.

```json
{
  "edits": [
    {
      "kind": "grammar",
      "line_id": 1,
      "original": "go",
      "occurrence": 1,
      "replacement": "went",
      "reason": "yesterday에 맞춰 과거형으로 수정합니다.",
      "location_uncertain": false,
      "boxes": [[0.30, 0.37, 0.34, 0.52]]
    }
  ]
}
```

| 필드 | 의미 |
|---|---|
| kind | grammar / spelling / punctuation / ocr_mismatch / needs_review |
| line_id | 전사문 줄 번호, 1부터 시작. 빈 줄도 번호에 포함 |
| original | 해당 줄에 실제로 존재하는 정확한 원문 부분문자열 |
| occurrence | 같은 부분문자열의 겹치지 않는 등장 순서, 1부터 시작 |
| replacement | 제안 영어 표현. 삭제는 빈 문자열, 삽입은 인접 원문을 포함한 교체 표현 |
| reason | 짧은 한국어 설명 |
| location_uncertain | 모델이 위치 판독을 불확실하게 판단했는지 |
| boxes | 좌표를 찾을 수 없으면 빈 배열. 찾으면 [left, top, right, bottom] 숫자 4개로 반환 |

사진과 전사문이 다르면 학생 오류로 확정하지 않고 `ocr_mismatch`, 판독 불가라면 `needs_review`로 요청한다. 이는 모델 지시이며 정확한 분류를 보장하지 않는다. 교사가 검토해야 한다.

텍스트 앵커는 한 전사 줄 안에 있어야 한다. 전체 문맥은 함께 보내지만 여러 전사 줄에 걸친 하나의 앵커는 현재 테스트에서 지원하지 않는다. 해당 앵커가 사진에서 여러 시각적 줄에 걸치면 사각형을 여러 개 반환할 수 있다.

## 백엔드에서 추가하는 값

- `id`: edit_001 등 로컬 식별자.
- `start`, `end`: 해당 줄에서 원문의 위치. **Java UTF-16 기준, 0부터 시작, end는 미포함**. 위 예시는 start=3, end=5. 앵커 불일치 시 -1.
- `pixel_boxes`: 정규화 좌표에 저장된 이미지 너비·높이를 곱한 픽셀 좌표.
- `warnings`, `requires_review`: 앵커 불일치, 좌표 누락/범위/도형 오류, OCR 불일치, 중복·겹치는 첨삭 등을 표시.

`requires_review=false`는 프로그램의 검증 경고가 없다는 뜻이다. 첨삭이나 좌표가 정답이라는 뜻이 아니다. 전체 수정문을 자동 적용하거나 DB에 저장하지 않는다.

## 결과 파일

호출 성공 후 매번 새로운 `build/ocr-results/correction-.../` 폴더를 만든다.

- `model-result.json`: 모델이 반환한 첨삭 JSON.
- `result.json`: 텍스트 위치·픽셀 좌표·검증 경고·모델·토큰·시간이 포함된 결과.
- `transcription.txt`: 해당 요청의 전사 입력.
- `image.png`: API에 보낸 회전 정규화 이미지.

콘솔에 결과 JSON과 저장 폴더가 출력된다. `build/`는 Git 제외 경로다.

## 좌표와 이미지 방향

이 테스트는 JPEG EXIF 및 PNG eXIf의 방향을 반영해 이미지를 바로 세우고 메타데이터 없는 PNG로 보낸다. 숫자 좌표와 픽셀 좌표는 **결과 폴더의 image.png 기준**이다. 원래 JPEG의 저장 픽셀 배열 위에 그대로 그리면 안 된다.

test7 로컬 점검에서는 EXIF=6, 변환 후 3024×4032였다. 기존 OCR 테스트는 원본 JPEG를 보냈으므로 이번 단계와는 입력 이미지 처리 조건이 다르다. 성능 비교 시 이를 기록해야 한다.

회전 정규화는 좌표 기준을 통일할 뿐 모델이 단어 위치를 정확히 찾는다는 보장은 없다. 검증 로직도 실제 이미지와의 일치 여부까지 판단하지 않는다.

## 검증

```powershell
.\gradlew.bat test --tests com.ceos24.autedi.aiexperiments.TerraCorrectionExampleTest --tests com.ceos24.autedi.aiexperiments.TerraOcrExampleTest
```

로컬 테스트는 실제 API를 호출하지 않는다. 반복 단어/UTF-16 위치, 픽셀 변환, 잘못된 좌표·앵커, 겹치는 첨삭, 빈 결과, EXIF 읽기와 8개 방향 변환을 확인한다.

실제 모델의 첨삭 품질·좌표 정확도·속도·비용은 실행 결과로 별도 평가해야 한다.
