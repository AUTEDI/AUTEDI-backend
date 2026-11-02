# 프론트엔드-백엔드 캔버스 연동 협의 스펙 (Notion Section 3 구현)

본 문서는 노션 문서 `AUTEDI 프론트엔드 연동 가이드 및 문의 답변`의 **3번 섹션(프론트-백 협의 및 스펙 확장 논의: 화면엔 있는데 API 없는 항목들)**에 대해 백엔드에 구현된 5가지 기능의 아키텍처 및 상세 API 연동 규격을 정리합니다.

---

## 1. 구현 항목 요약

| # | 협의 항목 | 백엔드 구현 내역 | 변경된 파일 |
|---|---|---|---|
| **1** | **피드백 "반영" 표시** | `Correction.isApplied` 필드 추가, 목록 필터(`?isApplied=`) 및 즉시 토글 API(`PATCH /corrections/{id}/apply?applied=`) 신설 | `Correction.java`, `CorrectionResponse.java`, `CorrectionController.java` |
| **2** | **교사 표시 도구 확장** | `HighlightType` ENUM에 `ARROW`(화살표), `TEXTBOX`(텍스트상자), `CARET`(삽입기호), `STRIKETHROUGH`(취소선) 추가 | `HighlightType.java`, `CommentCreateRequest.java` |
| **3** | **표시 색상 지원** | `CommentColor` ENUM (`RED`, `BLUE`, `YELLOW`, `GREEN`) 신설, `Comment.color` 컬럼 및 DTO 반영 | `CommentColor.java`, `Comment.java`, `CommentCreateRequest.java`, `CommentResponse.java` |
| **4** | **AI 표시 위치/크기 조절** | `CorrectionUpdateRequest`에 `regions: List<RegionPayload>` 필드 추가, 바운딩 박스 캔버스 드래그/리사이징 허용 | `CorrectionUpdateRequest.java`, `Correction.java`, `CorrectionService.java` |
| **5** | **문서 2페이지 이상 지원** | `POST /api/v1/worksheets`에서 다중 파일(`files: List<MultipartFile>`) 수용, 파일 순서대로 1, 2, ... 페이지 자동 생성 | `WorksheetController.java`, `WorksheetService.java` |

---

## 2. 세부 API 명세 및 변경 규격

### 2.1 [신규/확장 API 1] AI 첨삭 반영(채택) 여부 토글
- **엔드포인트**: `PATCH /api/v1/worksheets/{worksheetId}/corrections/{correctionId}/apply`
- **설명**: 교사가 캔버스 상에서 AI 첨삭 카드를 학생 최종 리포트에 '반영(채택)'할지 제외할지 즉시 토글합니다.
- **Query Parameter**:
  - `applied` (boolean, required, default: `true`): `true`(반영/채택), `false`(미반영/제외)
- **응답 (200 OK)**:
```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "첨삭 반영 여부가 변경되었습니다.",
  "result": {
    "id": 201,
    "etag": "\"cor_201-v2\"",
    "generationId": 101,
    "source": "AI",
    "area": "GRAMMAR_SPELLING",
    "feedbackType": "CORRECTION",
    "originalText": "He go to school yesterday.",
    "content": "He went to school yesterday.",
    "explanation": "과거 시점이므로 went를 사용합니다.",
    "regions": [{ "x": 0.20, "y": 0.30, "width": 0.15, "height": 0.04 }],
    "editedByTeacher": false,
    "isApplied": false,
    "status": "ACTIVE",
    "createdAt": "2026-11-02T10:00:00",
    "updatedAt": "2026-11-02T10:05:00"
  }
}
```

---

### 2.2 [확장 API 2] AI 첨삭 내용 및 바운딩 박스 위치/크기 수정
- **엔드포인트**: `PATCH /api/v1/worksheets/{worksheetId}/corrections/{correctionId}`
- **설명**: 첨삭 텍스트(`content`)뿐만 아니라, 캔버스 상에서 교사가 마우스 드래그로 조절한 바운딩 박스 좌표(`regions`) 및 반영 여부(`isApplied`)를 함께 전달할 수 있습니다.
- **Request Header**: `If-Match: "{etag}"` (동시성 제어)
- **Request Body**:
```json
{
  "content": "He went to school yesterday.",
  "regions": [
    {
      "x": 0.18,
      "y": 0.29,
      "width": 0.18,
      "height": 0.05
    }
  ],
  "isApplied": true
}
```
*모든 필드는 선택 사항(optional)이며, 생략된 필드는 기존 값을 온전히 유지합니다.*

---

### 2.3 [확장 API 3] AI 첨삭 목록 필터 조회
- **엔드포인트**: `GET /api/v1/worksheets/{worksheetId}/corrections`
- **추가된 Query Parameters**:
  - `feedbackType` (string, optional): `CORRECTION`(오류수정), `SUGGESTION`(개선제안)
  - `isApplied` (boolean, optional): `true`(반영된 항목만), `false`(미반영 항목만)
- **호출 예시**:
  - `GET /api/v1/worksheets/1/corrections?isApplied=true&feedbackType=CORRECTION`

---

### 2.4 [확장 API 4] 교사 코멘트 작성 (표시 도구 및 색상 추가)
- **엔드포인트**: `POST /api/v1/worksheets/{worksheetId}/comments`
- **설명**: 형광펜/밑줄 외에 화살표, 텍스트 상자, 삽입 기호를 생성할 수 있으며 색상(빨강/파랑 등)을 지정합니다.
- **Request Body**:
```json
{
  "pageId": 10,
  "highlightType": "ARROW",
  "color": "RED",
  "regions": [
    {
      "x": 0.25,
      "y": 0.40,
      "width": 0.10,
      "height": 0.04
    }
  ],
  "content": "이 문장과 다음 문장의 연결이 어색합니다."
}
```
- **지원 ENUM 규격**:
  - `highlightType`: `HIGHLIGHT`(형광펜), `UNDERLINE`(밑줄), `STRIKETHROUGH`(취소선), `ARROW`(화살표), `TEXTBOX`(텍스트상자), `CARET`(삽입기호 `^`)
  - `color`: `RED`(빨강), `BLUE`(파랑, 기본값), `YELLOW`(노랑), `GREEN`(초록)

---

### 2.5 [확장 API 5] 교사 코멘트 수정 (색상 변경 지원)
- **엔드포인트**: `PATCH /api/v1/worksheets/{worksheetId}/comments/{commentId}`
- **Request Body**:
```json
{
  "content": "수정된 교사 코멘트 내용",
  "color": "BLUE"
}
```

---

### 2.6 [확장 API 6] 학습지 다중 파일 업로드 (2페이지 이상 지원)
- **엔드포인트**: `POST /api/v1/worksheets`
- **Content-Type**: `multipart/form-data`
- **파라미터 규격**:
  - `files` (MultipartFile[], optional): 다중 페이지 파일 목록. 배열 순서대로 `pageNumber: 1, 2, ...` 생성.
  - `file` (MultipartFile, optional): 기존 단일 파일 업로드 호환용.
- **응답 (201 Created)**:
```json
{
  "isSuccess": true,
  "code": "COMMON201",
  "message": "학습지가 등록되었습니다.",
  "result": {
    "id": 15,
    "etag": "\"ws_15-v1\"",
    "status": "DRAFT",
    "file": {
      "name": "homework_page1.png",
      "mediaType": "image/png",
      "size": 4194304,
      "url": "https://.../worksheets/1/uuid-page1.png"
    },
    "pages": [
      {
        "id": 21,
        "pageNumber": 1,
        "width": 1200,
        "height": 1600,
        "previewImageUrl": "https://.../worksheets/1/uuid-page1.png"
      },
      {
        "id": 22,
        "pageNumber": 2,
        "width": 1200,
        "height": 1600,
        "previewImageUrl": "https://.../worksheets/1/uuid-page2.png"
      }
    ]
  }
}
```
