package com.ceos24.autedi.domain.correction.dto.response;

import com.ceos24.autedi.domain.correction.entity.Correction;
import com.ceos24.autedi.domain.correction.entity.CorrectionStatus;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import java.time.LocalDateTime;
import java.util.List;

public record CorrectionResponse(
        Long id,
        String etag,
        Long generationId,
        String source,
        CorrectionArea area,
        String originalText,
        String aiInitialContent,
        String content,
        String explanation,
        List<RegionPayload> regions,
        Boolean editedByTeacher,
        Boolean explanationStale,
        CorrectionStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
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
                correction.getUpdatedAt()
        );
    }
}
