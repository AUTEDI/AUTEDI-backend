package com.ceos24.autedi.domain.student.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "학생 등록을 보류하고 사용자에게 확인할 중복 후보 목록")
public record StudentDuplicateResponse(
        @Schema(description = "같은 기관·같은 이름의 학생 목록. 학년·반과 무관하게 학생 ID 오름차순 반환")
                List<StudentDuplicateCandidateResponse> candidates) {

    public StudentDuplicateResponse {
        candidates = List.copyOf(candidates);
    }
}
