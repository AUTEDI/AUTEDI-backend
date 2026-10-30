package com.ceos24.autedi.domain.worksheet.dto.response;

import com.ceos24.autedi.domain.comment.dto.response.CommentResponse;
import com.ceos24.autedi.domain.correction.dto.response.CorrectionResponse;
import com.ceos24.autedi.domain.generation.dto.response.GenerationResponse;
import java.util.List;

public record ReviewResponse(
        WorksheetDetailResponse worksheet,
        GenerationResponse generation,
        List<CorrectionResponse> corrections,
        List<CommentResponse> comments) {}
