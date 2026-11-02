package com.ceos24.autedi.domain.correction.dto.request;

import com.ceos24.autedi.domain.correction.entity.RegionPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI 첨삭 내용/위치/반영여부 수정 요청")
public record CorrectionUpdateRequest(
        @Schema(description = "선생님이 직접 수정한 수정문 또는 제안문 내용 (선택 사항)", example = "He goes to school every day.")
                String content,
        @Schema(description = "선생님이 이동/조절한 바운딩 박스 캔버스 좌표 목록 (선택 사항, 생략 시 기존 좌표 유지)") List<RegionPayload> regions,
        @Schema(description = "선생님 피드백 채택/반영 여부 (선택 사항, 생략 시 기존 상태 유지)", example = "true") Boolean isApplied) {
    public CorrectionUpdateRequest(String content) {
        this(content, null, null);
    }
}
