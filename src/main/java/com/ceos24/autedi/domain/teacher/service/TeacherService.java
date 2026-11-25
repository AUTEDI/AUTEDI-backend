package com.ceos24.autedi.domain.teacher.service;

import com.ceos24.autedi.domain.auth.repository.RefreshTokenRepository;
import com.ceos24.autedi.domain.teacher.dto.response.TeacherResignResponse;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.security.RevokedUserRegistry;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedUserRegistry revokedUserRegistry;

    /**
     * 같은 기관의 선생님을 퇴사 처리하고 모든 Refresh Token을 폐기한다. 이미 발급된 Access Token은 커밋 직후 차단 목록에 올려 즉시 막는다. 학생
     * 결과물과 첨삭 이력은 그대로 남는다.
     */
    @Transactional
    public TeacherResignResponse resign(Long academyId, Long teacherId) {
        User teacher = userRepository.findById(teacherId).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 다른 기관 사용자는 존재 여부도 알려주지 않도록 없는 사용자와 같은 오류를 쓴다.
        if (!teacher.getAcademy().getId().equals(academyId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        if (teacher.getRole() != Role.TEACHER) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (teacher.getStatus() == UserStatus.RESIGNED) {
            throw new CustomException(ErrorCode.ALREADY_RESIGNED);
        }

        teacher.resign();
        refreshTokenRepository.revokeAllByUser(teacher, LocalDateTime.now());
        revokedUserRegistry.revokeAfterCommit(teacher.getId());

        return TeacherResignResponse.from(teacher);
    }
}
