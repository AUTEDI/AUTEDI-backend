package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.comment.dto.response.CommentResponse;
import com.ceos24.autedi.domain.correction.dto.response.CorrectionResponse;
import com.ceos24.autedi.domain.generation.dto.response.GenerationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "검토 화면 1회 왕복 통합 데이터 응답 (네트워크 워터폴 제거)")
public record ReviewResponse(
        @Schema(description = "학습지 메타데이터 및 페이지 목록") WorksheetDetailResponse worksheet,
        @Schema(description = "가장 최근 생성 작업 상태") GenerationResponse generation,
        @Schema(description = "활성(ACTIVE) 상태의 AI 첨삭 항목 목록") List<CorrectionResponse> corrections,
        @Schema(description = "활성 상태의 교사 코멘트 목록") List<CommentResponse> comments) {}
