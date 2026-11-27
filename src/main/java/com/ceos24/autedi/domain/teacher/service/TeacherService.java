package com.ceos24.autedi.domain.teacher.service;

import com.ceos24.autedi.domain.auth.repository.RefreshTokenRepository;
import com.ceos24.autedi.domain.invitation.entity.InvitationStatus;
import com.ceos24.autedi.domain.invitation.repository.UserInvitationRepository;
import com.ceos24.autedi.domain.teacher.dto.TeacherListStatus;
import com.ceos24.autedi.domain.teacher.dto.request.TeacherUpdateRequest;
import com.ceos24.autedi.domain.teacher.dto.response.TeacherDetailResponse;
import com.ceos24.autedi.domain.teacher.dto.response.TeacherListItemResponse;
import com.ceos24.autedi.domain.teacher.dto.response.TeacherResignResponse;
import com.ceos24.autedi.domain.teacher.dto.response.TeacherWorksheetHistoryItemResponse;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSaveStatus;
import com.ceos24.autedi.domain.worksheet.repository.TeacherWorksheetCount;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.response.PageResponse;
import com.ceos24.autedi.global.security.RevokedUserRegistry;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherService {

    private static final int MAX_PAGE_SIZE = 100;

    /** 첨삭 수·첨삭 이력에 포함하는 업무 저장 상태. 임시저장과 완료를 세고, 저장하지 않은 학습지는 세지 않는다. 목록과 이력이 같은 기준을 쓰도록 한 곳에 둔다. */
    static final List<WorksheetSaveStatus> COUNTED_STATUSES =
            List.of(WorksheetSaveStatus.TEMP_SAVED, WorksheetSaveStatus.COMPLETED);

    /** 목록 정렬: 이름순. 이름이 같으면 이메일, 가입 선생님, ID 순으로 정해 순서가 항상 하나로 정해지게 한다. */
    private static final Comparator<TeacherListItemResponse> LIST_ORDER = Comparator.comparing(
                    TeacherService::sortName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(TeacherListItemResponse::email, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(item -> !item.registered())
            .thenComparing(item -> item.registered() ? item.teacherId() : item.invitationId());

    private final UserRepository userRepository;
    private final UserInvitationRepository userInvitationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedUserRegistry revokedUserRegistry;
    private final WorksheetRepository worksheetRepository;

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

    /**
     * 같은 기관의 가입한 선생님과 초대 대기를 한 목록으로 합쳐 이름순으로 돌려준다. 원장은 포함하지 않는다.
     *
     * <p>DB에서는 기관·상태·검색어로 걸러 오기만 하고, 합치기·정렬·페이지 나누기는 여기서 한다. 정렬을 DB에 맡기지 않아 DB 종류에 따른 한글 정렬 차이를
     * 받지 않는다.
     *
     * @param keyword 이름·이메일 부분 일치 검색어(대소문자 무시). 비어 있으면 무시
     * @param status 상태 필터. null이면 전체
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지 크기(1~100)
     */
    public PageResponse<TeacherListItemResponse> list(
            Long academyId, String keyword, TeacherListStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String pattern = toLikePattern(keyword);

        List<TeacherListItemResponse> all = new ArrayList<>();
        if (status != TeacherListStatus.INVITED) {
            userRepository.search(academyId, Role.TEACHER, userStatusesOf(status), pattern).stream()
                    .map(TeacherListItemResponse::fromUser)
                    .forEach(all::add);
        }
        if (status == null || status == TeacherListStatus.INVITED) {
            userInvitationRepository.search(academyId, InvitationStatus.PENDING, pattern).stream()
                    .map(TeacherListItemResponse::fromInvitation)
                    .forEach(all::add);
        }
        all.sort(LIST_ORDER);

        return withMonthlyCorrectionCounts(academyId, PageResponse.fromAll(all, page, size));
    }

    /**
     * 페이지에 보이는 가입한 선생님의 이번 달 첨삭 수를 한 번에 세어 채운다. 초대 대기 행과 첨삭이 없는 선생님은 0이다. 집계 기준은 {@link
     * #COUNTED_STATUSES}와 {@link MonthRange#current()}이다.
     */
    private PageResponse<TeacherListItemResponse> withMonthlyCorrectionCounts(
            Long academyId, PageResponse<TeacherListItemResponse> page) {
        List<Long> teacherIds = page.items().stream()
                .filter(TeacherListItemResponse::registered)
                .map(TeacherListItemResponse::teacherId)
                .toList();
        if (teacherIds.isEmpty()) {
            return page;
        }

        MonthRange month = MonthRange.current();
        Map<Long, Long> counts =
                worksheetRepository
                        .countByTeacher(academyId, teacherIds, COUNTED_STATUSES, month.from(), month.to())
                        .stream()
                        .collect(Collectors.toMap(TeacherWorksheetCount::teacherId, TeacherWorksheetCount::count));

        List<TeacherListItemResponse> items = page.items().stream()
                .map(item -> item.registered()
                        ? item.withMonthlyCorrectionCount(counts.getOrDefault(item.teacherId(), 0L))
                        : item)
                .toList();
        return new PageResponse<>(items, page.total(), page.page(), page.size(), page.totalPages());
    }

    /** 목록 상태 필터를 가입 계정의 상태 목록으로 바꾼다. 퇴사가 아닌 계정(잠금·비활성 포함)은 모두 활동 중이다. */
    private static List<UserStatus> userStatusesOf(TeacherListStatus status) {
        if (status == TeacherListStatus.RESIGNED) {
            return List.of(UserStatus.RESIGNED);
        }
        if (status == TeacherListStatus.ACTIVE) {
            return List.of(UserStatus.ACTIVE, UserStatus.INACTIVE, UserStatus.LOCKED);
        }
        return List.of(UserStatus.values());
    }

    /**
     * 검색어를 LIKE 패턴으로 바꾼다. 앞뒤 공백을 지우고 소문자로 맞추며, LIKE의 특수문자({@code %}, {@code _})와 이스케이프 문자({@code !})는 글자
     * 그대로 찾도록 이스케이프한다. 검색어가 없으면 모두 일치하는 {@code %}다.
     */
    private static String toLikePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "%";
        }
        String escaped = keyword.trim()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    /** 목록 정렬에 쓰는 이름. 이름이 없는 초대는 이메일로 대신한다. */
    private static String sortName(TeacherListItemResponse item) {
        return item.name() != null && !item.name().isBlank() ? item.name() : item.email();
    }

    /**
     * 같은 기관 선생님의 첨삭 이력을 처음 저장한 시각 최신순으로 조회한다. 선생님이 올린 임시저장·완료 학습지이며 삭제된 학습지는 제외한다. 월별
     * 첨삭 수({@link #list})와 같은 기준이라 같은 달의 건수가 일치한다. 퇴사한 선생님도 조회할 수 있다.
     *
     * @param keyword 학습지 제목 또는 학생 이름 부분 일치 검색어(대소문자 무시). 비어 있으면 무시
     * @param month 처음 저장한 달(한국 시간). null이면 전체 기간
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지 크기(1~100)
     */
    public PageResponse<TeacherWorksheetHistoryItemResponse> getWorksheetHistory(
            Long academyId, Long teacherId, String keyword, YearMonth month, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        findTeacherInAcademy(academyId, teacherId);

        MonthRange range = month == null ? MonthRange.all() : MonthRange.of(month);
        String pattern = keyword == null || keyword.isBlank() ? null : toLikePattern(keyword);
        Page<Worksheet> worksheets = worksheetRepository.searchTeacherHistory(
                academyId, teacherId, COUNTED_STATUSES, range.from(), range.to(), pattern, PageRequest.of(page, size));

        return new PageResponse<>(
                worksheets.getContent().stream()
                        .map(TeacherWorksheetHistoryItemResponse::from)
                        .toList(),
                worksheets.getTotalElements(),
                worksheets.getNumber(),
                worksheets.getSize(),
                worksheets.getTotalPages());
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
