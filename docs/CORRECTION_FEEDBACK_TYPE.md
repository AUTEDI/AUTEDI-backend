# AI 첨삭 피드백 유형 (FeedbackType) 설계 및 연동 가이드

## 1. 개요 및 배경

### 1.1 배경 (노션 프론트엔드 연동 가이드 2-5 요구사항)
노션 문서 `AUTEDI 프론트엔드 연동 가이드 및 문의 답변(11-02)`의 확인 요청 사항 2-5:
> **프론트엔드 질의**: "첨삭 응답에 `feedbackType`(오류수정/개선제안) 언제 들어오는지?"

* **프론트엔드 UI/UX 요구사항**:
  - 프론트엔드 캔버스 및 첨삭 상세 패널에서는 AI 첨삭 결과를 일괄적으로 표시하지 않고, **명백히 틀린 문법/철자 오류를 수정한 것(`CORRECTION`)**인지, 아니면 **문맥에 맞게 더 고급스럽거나 자연스러운 어휘/표현을 추천한 개선 제안(`SUGGESTION`)**인지를 UI 뱃지, 마커 색상, 탭 필터링으로 시각적으로 명확히 분기 처리해야 합니다.
  - 기존 백엔드 설계에서는 첨삭 영역인 `area`(`CorrectionArea`: `GRAMMAR_SPELLING`, `VOCABULARY_EXPRESSION`, `SENTENCE_STRUCTURE` 등)만 제공되었기 때문에, 프론트엔드 단독으로는 해당 첨삭이 '오류 수정'인지 '개선 제안'인지 판별할 수 없었습니다.

### 1.2 목표
1. 피드백 유형을 나타내는 도메인 ENUM `FeedbackType` (`CORRECTION`, `SUGGESTION`) 정의.
2. `Correction` 엔티티 및 DB 스키마에 `feedback_type` 컬럼 추가.
3. AI 생성 파이프라인(`CorrectionDraft`, `MockCorrectionAiClient`, `WorksheetGenerationService`)에 `FeedbackType` 매핑 연동.
4. 모든 첨삭 관련 API 응답 DTO(`CorrectionResponse`, `ReviewResponse`)에 `feedbackType` 필드 제공.
5. 첨삭 목록 조회 API(`GET /corrections`)에 `feedbackType` 쿼리 파라미터 필터링 기능 추가.

---

## 2. 도메인 모델 및 스키마 설계

### 2.1 `FeedbackType` Enum
- 패키지: `com.ceos24.autedi.domain.correction.entity.FeedbackType`

| 상수 (Value) | 한글 명칭 | 설명 | 예시 |
| :--- | :--- | :--- | :--- |
| **`CORRECTION`** | 오류 수정 | 문법, 철자, 구두점 등 명백한 오류를 바로잡는 필수 교정 | `He go` ➡️ `He goes` (수일치 오류 수정) |
| **`SUGGESTION`** | 개선 제안 | 문법적으로 틀리지는 않았으나 더 자연스럽거나 풍부한 어휘/표현 권장 | `very big` ➡️ `spacious` (어휘 다양성 추천) |

### 2.2 `Correction` 엔티티 매핑
```java
@Entity
@Table(name = "corrections")
public class Correction extends BaseTimeEntity {

    // ... 기존 필드 (id, worksheet, page, generation, area)

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", length = 30, nullable = false)
    private FeedbackType feedbackType;

    // ... 기존 필드 (originalText, aiInitialContent, content, explanation, regions, isEdited, status, version)

    @Builder
    public Correction(
            Worksheet worksheet,
            WorksheetPage page,
            WorksheetGeneration generation,
            CorrectionArea area,
            FeedbackType feedbackType,
            String originalText,
            String aiInitialContent,
            String content,
            String explanation,
            List<RegionPayload> regions) {
        // ...
        this.area = area;
        this.feedbackType = feedbackType != null ? feedbackType : FeedbackType.CORRECTION; // 하위 호환성 Fallback
        // ...
    }
}
```

### 2.3 DB DDL 변경 사항
```sql
ALTER TABLE corrections
ADD COLUMN feedback_type VARCHAR(30) NOT NULL DEFAULT 'CORRECTION' AFTER area;
```
* 로컬 및 개발 배포 환경(`application-local.yaml`, `application-dev.yaml`)은 `hibernate.ddl-auto: update`에 의해 서버 기동 시 자동으로 컬럼이 추가됩니다.

---

## 3. API 명세 및 응답 규격

### 3.1 DTO 변경: `CorrectionResponse`
`CorrectionResponse` 레코드에 `feedbackType` 필드가 추가되었습니다:
```java
@Schema(description = "AI 첨삭 항목 상세 정보")
public record CorrectionResponse(
        @Schema(description = "첨삭 항목 고유 ID", example = "201") Long id,
        @Schema(description = "동시성 제어용 ETag", example = "\"cor_201-v1\"") String etag,
        @Schema(description = "생성 작업 ID", example = "101") Long generationId,
        @Schema(description = "생성 주체 (항상 AI)", example = "AI") String source,
        @Schema(description = "첨삭 영역", example = "GRAMMAR_SPELLING") CorrectionArea area,
        @Schema(description = "피드백 유형 (CORRECTION: 오류 수정, SUGGESTION: 개선 제안)", example = "CORRECTION") FeedbackType feedbackType,
        @Schema(description = "인식된 원문 텍스트 (불변)", example = "He go to school.") String originalText,
        @Schema(description = "AI 최초 제안문 (불변)", example = "He goes to school.") String aiInitialContent,
        @Schema(description = "현재 수정/제안문 (선생님 편집 대상)", example = "He goes to school every day.") String content,
        @Schema(description = "교사용 첨삭 해설 (불변)", example = "3인칭 단수 주어 뒤에는 동사원형에 -es를 붙입니다.") String explanation,
        @Schema(description = "캔버스 상의 바운딩 박스 위치 좌표 배열") List<RegionPayload> regions,
        @Schema(description = "선생님 편집 여부", example = "false") Boolean editedByTeacher,
        @Schema(description = "선생님 편집으로 인한 해설 진부화 여부", example = "false") Boolean explanationStale,
        @Schema(description = "상태 (ACTIVE: 활성, DELETED: 소프트 삭제)", example = "ACTIVE") CorrectionStatus status,
        @Schema(description = "생성 일시", example = "2026-10-30T10:00:05") LocalDateTime createdAt,
        @Schema(description = "수정 일시", example = "2026-10-30T10:05:00") LocalDateTime updatedAt)
```

### 3.2 영향받는 API 목록
1. **`GET /api/v1/worksheets/{worksheetId}/corrections`** (첨삭 목록 필터 조회)
   - 쿼리 파라미터 `feedbackType` 추가: `CORRECTION` 또는 `SUGGESTION`으로 필터링 가능.
   - 예시: `GET /api/v1/worksheets/1/corrections?feedbackType=CORRECTION`
2. **`GET /api/v1/worksheets/{worksheetId}/corrections/{correctionId}`** (첨삭 단건 상세 조회)
3. **`PATCH /api/v1/worksheets/{worksheetId}/corrections/{correctionId}`** (교사 첨삭 수정)
4. **`POST /api/v1/worksheets/{worksheetId}/corrections/{correctionId}/restore`** (첨삭 복원)
5. **`GET /api/v1/worksheets/{worksheetId}/review`** (검토 화면 통합 왕복 조회)
   - `result.corrections` 리스트 내 각 항목에 `feedbackType` 포함.

### 3.3 응답 JSON 예시
```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "성공입니다.",
  "result": {
    "total": 2,
    "items": [
      {
        "id": 201,
        "etag": "\"cor_201-v1\"",
        "generationId": 101,
        "source": "AI",
        "area": "GRAMMAR_SPELLING",
        "feedbackType": "CORRECTION",
        "originalText": "He go to school yesterday.",
        "aiInitialContent": "He went to school yesterday.",
        "content": "He went to school yesterday.",
        "explanation": "과거 시점(yesterday)을 나타내는 문장이므로 과거형 동사 went를 사용해야 합니다.",
        "regions": [
          { "x": 0.20, "y": 0.30, "width": 0.15, "height": 0.04 }
        ],
        "editedByTeacher": false,
        "explanationStale": false,
        "status": "ACTIVE",
        "createdAt": "2026-11-02T10:00:00",
        "updatedAt": "2026-11-02T10:00:00"
      },
      {
        "id": 202,
        "etag": "\"cor_202-v1\"",
        "generationId": 101,
        "source": "AI",
        "area": "VOCABULARY_EXPRESSION",
        "feedbackType": "SUGGESTION",
        "originalText": "I have a very big house.",
        "aiInitialContent": "I have a spacious house.",
        "content": "I have a spacious house.",
        "explanation": "'very big' 대신 더 풍부한 묘사를 위해 'spacious(넓은)'라는 어휘를 추천합니다.",
        "regions": [
          { "x": 0.25, "y": 0.45, "width": 0.20, "height": 0.04 }
        ],
        "editedByTeacher": false,
        "explanationStale": false,
        "status": "ACTIVE",
        "createdAt": "2026-11-02T10:00:00",
        "updatedAt": "2026-11-02T10:00:00"
      }
    ]
  }
}
```

---

## 4. 프론트엔드 연동 가이드

1. **시각적 분기 (뱃지 & 마커 스타일링)**:
   - `CORRECTION`: 빨강/주황 계열 오류 뱃지 (`오류 수정`) 표시.
   - `SUGGESTION`: 파랑/녹색 계열 추천 뱃지 (`개선 제안`) 표시.
2. **필터 탭 구현**:
   - `전체` / `오류 수정만` / `개선 제안만` 탭 분기 시 `GET /corrections?feedbackType=CORRECTION` 또는 `SUGGESTION` 호출 가능 (혹은 프론트 메모리 상에서 `item.feedbackType`으로 즉시 필터링).
3. **교사 편집 시 주의사항**:
   - `PATCH /corrections/{id}`로 내용 수정 시 기존과 동일하게 `If-Match: "{etag}"` 헤더를 반드시 전달해야 합니다.
