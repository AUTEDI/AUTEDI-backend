package com.ceos24.autedi.domain.teacher.dto.response;

import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "선생님 퇴사 처리 결과 응답")
public record TeacherResignResponse(
        @Schema(description = "퇴사 처리된 선생님 ID", example = "45") Long teacherId,
        @Schema(description = "변경된 계정 상태", example = "RESIGNED") UserStatus status) {

    public static TeacherResignResponse from(User teacher) {
        return new TeacherResignResponse(teacher.getId(), teacher.getStatus());
    }
}
