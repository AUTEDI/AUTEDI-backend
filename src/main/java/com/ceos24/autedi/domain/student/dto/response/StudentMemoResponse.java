package com.ceos24.autedi.domain.student.dto.response;

import com.ceos24.autedi.domain.student.entity.StudentMemo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "학생 메모")
public record StudentMemoResponse(
        @Schema(description = "메모 고유 ID", example = "1") Long memoId,
        @Schema(description = "메모 내용", example = "다음 주 시험 범위를 안내함") String content,
        @Schema(description = "작성자 이름. 퇴사한 작성자도 그대로 표시", example = "김선생") String authorName,
        @Schema(description = "작성 시각") LocalDateTime createdAt) {

    public static StudentMemoResponse from(StudentMemo memo) {
        return new StudentMemoResponse(
                memo.getId(), memo.getContent(), memo.getAuthor().getName(), memo.getCreatedAt());
    }
}
