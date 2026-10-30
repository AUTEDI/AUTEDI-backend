package com.ceos24.autedi.domain.comment.dto.request;

import com.ceos24.autedi.domain.comment.entity.HighlightType;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CommentCreateRequest(
        @NotNull(message = "페이지 ID를 지정해 주세요.") Long pageId,
        @NotNull(message = "강조 유형을 선택해 주세요.") HighlightType highlightType,
        @NotEmpty(message = "강조할 영역을 최소 한 개 이상 지정해 주세요.") List<RegionPayload> regions,
        @NotBlank(message = "코멘트 내용을 입력해 주세요.") String content) {}
