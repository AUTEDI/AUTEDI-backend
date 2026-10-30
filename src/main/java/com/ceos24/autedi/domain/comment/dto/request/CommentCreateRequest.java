package com.ceos24.autedi.domain.comment.dto.request;

import com.ceos24.autedi.domain.comment.entity.HighlightType;
import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "선생님 코멘트 작성 요청")
public record CommentCreateRequest(
        @Schema(description = "코멘트를 작성할 대상 페이지 ID", example = "10") @NotNull(message = "페이지 ID를 지정해 주세요.") Long pageId,
        @Schema(description = "강조 방식 (HIGHLIGHT: 형광펜, UNDERLINE: 밑줄)", example = "HIGHLIGHT")
                @NotNull(message = "강조 유형을 선택해 주세요.")
                HighlightType highlightType,
        @Schema(description = "하이라이트/밑줄을 표시할 캔버스 영역 좌표 목록") @NotEmpty(message = "강조할 영역을 최소 한 개 이상 지정해 주세요.")
                List<RegionPayload> regions,
        @Schema(description = "교사 코멘트 내용", example = "단수 주어(He) 다음에는 3인칭 단수 동사 goes를 사용해야 합니다.")
                @NotBlank(message = "코멘트 내용을 입력해 주세요.")
                String content) {}
