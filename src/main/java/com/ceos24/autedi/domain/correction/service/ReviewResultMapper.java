package com.ceos24.autedi.domain.correction.service;

import com.ceos24.autedi.domain.correction.dto.response.CorrectionResponse;
import com.ceos24.autedi.domain.generation.contract.GenerationContractRouter;
import com.ceos24.autedi.domain.generation.contract.v1.AiArea;
import com.ceos24.autedi.domain.generation.contract.v1.validation.AiCorrectionLevel;
import com.ceos24.autedi.domain.generation.persistence.StoredGenerationResult;
import com.ceos24.autedi.domain.worksheet.dto.response.ReviewAreaSummaryResponse;
import com.ceos24.autedi.domain.worksheet.dto.response.ReviewIssueResponse;
import java.util.List;

/** 저장된 계약 버전으로 확인 목록·집계만 투영한다. 조회 시 재검증하거나 원본을 수정하지 않는다. */
final class ReviewResultMapper {
    private ReviewResultMapper() {}

    static void append(
            StoredGenerationResult saved,
            List<CorrectionResponse> corrections,
            List<ReviewIssueResponse> issues,
            List<ReviewAreaSummaryResponse> summary) {
        var transcript = saved.transcription();
        switch (saved.versionedValidationResult()) {
            case null -> {
                /* UNREADABLE 결과는 메타데이터만 제공한다. */
            }
            case GenerationContractRouter.V1Validation v1 -> {
                v1.result().reviewIssues().forEach(issue -> issues.add(ReviewIssueResponse.from(saved, issue)));
                for (var area : v1.result().summary()) {
                    summary.add(new ReviewAreaSummaryResponse(
                            saved.generationId(),
                            transcript.pageId(),
                            transcript.pageNumber(),
                            area.area(),
                            area.level(),
                            area.recommendedCount(),
                            area.maximumCount(),
                            area.displayedCount(),
                            activeCount(saved, corrections, area.area().name()),
                            area.omittedCount()));
                }
            }
            case GenerationContractRouter.V11Validation v11 -> {
                v11.result().reviewIssues().forEach(issue -> issues.add(ReviewIssueResponse.from(saved, issue)));
                for (var area : v11.result().summary()) {
                    summary.add(new ReviewAreaSummaryResponse(
                            saved.generationId(),
                            transcript.pageId(),
                            transcript.pageNumber(),
                            AiArea.valueOf(area.area().name()),
                            area.level() == null
                                    ? null
                                    : AiCorrectionLevel.valueOf(area.level().name()),
                            0,
                            area.maximumCount(),
                            area.displayedCount(),
                            activeCount(saved, corrections, area.area().name()),
                            area.omittedCount()));
                }
            }
        }
    }

    private static int activeCount(StoredGenerationResult saved, List<CorrectionResponse> corrections, String area) {
        return (int) corrections.stream()
                .filter(card -> saved.generationId().equals(card.generationId())
                        && card.area().name().equals(area)
                        && card.anchors().stream()
                                .anyMatch(anchor -> saved.transcription().id().equals(anchor.transcriptionId())))
                .count();
    }
}
