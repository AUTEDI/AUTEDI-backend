package com.ceos24.autedi.domain.teacher.service;

import com.ceos24.autedi.domain.auth.repository.RefreshTokenRepository;
import com.ceos24.autedi.domain.teacher.dto.request.TeacherUpdateRequest;
import com.ceos24.autedi.domain.teacher.dto.response.TeacherDetailResponse;
import com.ceos24.autedi.domain.teacher.dto.response.TeacherResignResponse;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.security.RevokedUserRegistry;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedUserRegistry revokedUserRegistry;

    /**
     * 같은 기관 선생님의 이름·연락처·입사일을 수정한다. 보내지 않았거나 null인 항목은 바꾸지 않는다. 연락처는 빈 문자열이면 삭제한다. 퇴사한 선생님도
     * 수정할 수 있다. 이메일은 바꾸지 않는다.
     */
    @Transactional
    public TeacherDetailResponse update(Long academyId, Long teacherId, TeacherUpdateRequest request) {
        User teacher = findTeacherInAcademy(academyId, teacherId);
        if (request.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<String> changed = new ArrayList<>();
        if (request.name() != null) {
            teacher.updateName(request.name().trim());
            changed.add("name");
        }
        if (request.phoneNumber() != null) {
            teacher.updatePhoneNumber(request.phoneNumber().isEmpty() ? null : request.phoneNumber());
            changed.add("phoneNumber");
        }
        if (request.hireDate() != null) {
            teacher.updateHireDate(request.hireDate());
            changed.add("hireDate");
        }

        log.info(
                "[teacher] 정보 수정: teacherId={}, academyId={}, changed={}",
                teacher.getId(),
                academyId,
                String.join(",", changed));
        return TeacherDetailResponse.from(teacher);
    }

    /** 같은 기관 선생님의 상세 정보를 조회한다. 퇴사한 선생님도 조회할 수 있다. */
    public TeacherDetailResponse getDetail(Long academyId, Long teacherId) {
        return TeacherDetailResponse.from(findTeacherInAcademy(academyId, teacherId));
    }

    /**
     * 같은 기관의 선생님을 퇴사 처리하고 모든 Refresh Token을 폐기한다. 이미 발급된 Access Token은 커밋 직후 차단 목록에 올려 즉시 막는다. 학생
     * 결과물과 첨삭 이력은 그대로 남는다.
     */
    @Transactional
    public TeacherResignResponse resign(Long academyId, Long teacherId) {
        User teacher = findTeacherInAcademy(academyId, teacherId);

        if (teacher.getStatus() == UserStatus.RESIGNED) {
            throw new CustomException(ErrorCode.ALREADY_RESIGNED);
        }

        teacher.resign();
        refreshTokenRepository.revokeAllByUser(teacher, LocalDateTime.now());
        revokedUserRegistry.revokeAfterCommit(teacher.getId());

        return TeacherResignResponse.from(teacher);
    }

    /**
     * 같은 기관의 선생님을 찾는다. 선생님 API가 공통으로 쓰는 대상 확인 규칙이다.
     *
     * <p>없는 ID와 다른 기관 사용자는 존재 여부를 알려주지 않도록 조회 결과가 없는 것으로 합쳐 {@code USER_NOT_FOUND}로 응답한다. 같은 기관의
     * 원장은 선생님 관리 대상이 아니므로 {@code ACCESS_DENIED}다. 계정 상태(퇴사 등)는 여기서 검사하지 않는다.
     */
    private User findTeacherInAcademy(Long academyId, Long teacherId) {
        User teacher = userRepository
                .findByIdAndAcademyId(teacherId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (teacher.getRole() != Role.TEACHER) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        return teacher;
    }
}
