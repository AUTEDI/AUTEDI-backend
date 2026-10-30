package com.ceos24.autedi.domain.correction.dto.response;

import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.CorrectionStatus;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "AI 첨삭 항목 상세 정보")
public record CorrectionResponse(
        @Schema(description = "첨삭 항목 고유 ID", example = "201") Long id,
        @Schema(description = "동시성 제어용 ETag", example = "\"cor_201-v1\"") String etag,
        @Schema(description = "생성 작업 ID", example = "101") Long generationId,
        @Schema(description = "생성 주체 (항상 AI)", example = "AI") String source,
        @Schema(description = "첨삭 영역", example = "GRAMMAR_SPELLING") CorrectionArea area,
        @Schema(description = "인식된 원문 텍스트 (불변)", example = "He go to school.") String originalText,
        @Schema(description = "AI 최초 제안문 (불변)", example = "He goes to school.") String aiInitialContent,
        @Schema(description = "현재 수정/제안문 (선생님 편집 대상)", example = "He goes to school every day.") String content,
        @Schema(description = "교사용 첨삭 해설 (불변)", example = "3인칭 단수 주어 뒤에는 동사원형에 -es를 붙입니다.") String explanation,
        @Schema(description = "캔버스 상의 바운딩 박스 위치 좌표 배열") List<RegionPayload> regions,
        @Schema(description = "선생님 편집 여부", example = "false") Boolean editedByTeacher,
        @Schema(description = "선생님 편집으로 인한 해설 진부화 여부", example = "false") Boolean explanationStale,
        @Schema(description = "상태 (ACTIVE: 활성, DELETED: 소프트 삭제)", example = "ACTIVE") CorrectionStatus status,
        @Schema(description = "생성 일시", example = "2026-10-30T10:00:05") LocalDateTime createdAt,
        @Schema(description = "수정 일시", example = "2026-10-30T10:05:00") LocalDateTime updatedAt) {
    public static CorrectionResponse from(Correction correction) {
        return of(correction, correction.getEtag());
    }

    public static CorrectionResponse of(Correction correction, String etag) {
        return new CorrectionResponse(
                correction.getId(),
                etag,
                correction.getGeneration() != null ? correction.getGeneration().getId() : null,
                "AI",
                correction.getArea(),
                correction.getOriginalText(),
                correction.getAiInitialContent(),
                correction.getContent(),
                correction.getExplanation(),
                correction.getRegions(),
                Boolean.TRUE.equals(correction.getIsEdited()),
                Boolean.TRUE.equals(correction.getIsEdited()),
                correction.getStatus(),
                correction.getCreatedAt(),
                correction.getUpdatedAt());
    }
}
