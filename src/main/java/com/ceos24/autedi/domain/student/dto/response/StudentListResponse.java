package com.ceos24.autedi.domain.student.dto.response;

import com.ceos24.autedi.domain.student.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "학생 목록 및 페이지 정보")
public record StudentListResponse(
        @Schema(description = "현재 페이지 학생 목록") List<StudentSummaryResponse> items,
        @Schema(description = "필터에 해당하는 전체 학생 수", example = "35") long total,
        @Schema(description = "현재 페이지 (0부터)", example = "0") int page,
        @Schema(description = "페이지 크기", example = "20") int size,
        @Schema(description = "전체 페이지 수", example = "2") int totalPages,
        @Schema(description = "다음 페이지가 있는지", example = "true") boolean hasNext) {

    public static StudentListResponse from(Page<Student> page) {
        return new StudentListResponse(
                page.getContent().stream().map(StudentSummaryResponse::from).toList(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                page.hasNext());
    }
}
