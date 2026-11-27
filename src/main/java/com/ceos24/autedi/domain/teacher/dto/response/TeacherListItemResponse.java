package com.ceos24.autedi.domain.teacher.dto.response;

import com.ceos24.autedi.domain.invitation.entity.UserInvitation;
import com.ceos24.autedi.domain.teacher.dto.TeacherListStatus;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "선생님 목록의 한 행. 가입한 선생님과 초대 대기를 같은 모양으로 담는다.")
public record TeacherListItemResponse(
        @Schema(description = "선생님 ID. 초대 대기 행은 null", example = "45", nullable = true) Long teacherId,
        @Schema(description = "초대 ID. 가입한 선생님 행은 null", example = "12", nullable = true) Long invitationId,
        @Schema(description = "가입 여부. 초대 대기 행은 false", example = "true") boolean registered,
        @Schema(description = "이름. 초대에 이름이 없으면 null", example = "김선생", nullable = true) String name,
        @Schema(description = "이메일(가입 계정 또는 초대 이메일)", example = "teacher@autedi.com") String email,
        @Schema(description = "연락처. 등록 전이거나 초대 대기이면 null", nullable = true) String phoneNumber,
        @Schema(description = "입사일. 등록 전이거나 초대 대기이면 null", nullable = true) LocalDate hireDate,
        @Schema(description = "상태(활동 중·초대 대기·퇴사)", example = "ACTIVE") TeacherListStatus status,
        @Schema(description = "이번 달(한국 시간) 첨삭 수. 임시저장·완료를 포함하고 삭제는 제외. 초대 대기 행은 0", example = "12")
                long monthlyCorrectionCount,
        @Schema(description = "초대 만료 시각. 가입한 선생님 행은 null. 지난 시각이면 만료된 초대", nullable = true) LocalDateTime expiresAt) {

    public static TeacherListItemResponse fromUser(User teacher) {
        TeacherListStatus status =
                teacher.getStatus() == UserStatus.RESIGNED ? TeacherListStatus.RESIGNED : TeacherListStatus.ACTIVE;
        return new TeacherListItemResponse(
                teacher.getId(),
                null,
                true,
                teacher.getName(),
                teacher.getEmail(),
                teacher.getPhoneNumber(),
                teacher.getHireDate(),
                status,
                0,
                null);
    }

    /** 이번 달 첨삭 수를 채운 복사본을 돌려준다. 목록은 페이지가 정해진 뒤에 행마다 집계 값을 채운다. */
    public TeacherListItemResponse withMonthlyCorrectionCount(long monthlyCorrectionCount) {
        return new TeacherListItemResponse(
                teacherId,
                invitationId,
                registered,
                name,
                email,
                phoneNumber,
                hireDate,
                status,
                monthlyCorrectionCount,
                expiresAt);
    }

    public static TeacherListItemResponse fromInvitation(UserInvitation invitation) {
        return new TeacherListItemResponse(
                null,
                invitation.getId(),
                false,
                invitation.getName(),
                invitation.getEmail(),
                null,
                null,
                TeacherListStatus.INVITED,
                0,
                invitation.getExpiresAt());
    }
}
