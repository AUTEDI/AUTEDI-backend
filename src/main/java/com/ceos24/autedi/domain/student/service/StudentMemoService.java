package com.ceos24.autedi.domain.student.service;

import com.ceos24.autedi.domain.student.dto.request.StudentMemoCreateRequest;
import com.ceos24.autedi.domain.student.dto.request.StudentMemoPageRequest;
import com.ceos24.autedi.domain.student.dto.response.StudentMemoResponse;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.entity.StudentMemo;
import com.ceos24.autedi.domain.student.repository.StudentMemoRepository;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import com.ceos24.autedi.global.response.PageResponse;
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
public class StudentMemoService {

    private final StudentMemoRepository studentMemoRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    /** 같은 기관 학생의 메모를 최신순으로 조회한다. 없는 학생·다른 기관 학생은 404이고, 메모가 없는 학생은 빈 목록이다. */
    public PageResponse<StudentMemoResponse> getMemos(Long academyId, Long studentId, StudentMemoPageRequest request) {
        if ((long) request.page() * request.size() > Integer.MAX_VALUE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "조회 가능한 페이지 범위를 초과했습니다.");
        }
        findStudent(academyId, studentId);

        Page<StudentMemo> memos =
                studentMemoRepository.findPageByStudentId(studentId, PageRequest.of(request.page(), request.size()));
        return new PageResponse<>(
                memos.getContent().stream().map(StudentMemoResponse::from).toList(),
                memos.getTotalElements(),
                memos.getNumber(),
                memos.getSize(),
                memos.getTotalPages());
    }

    /** 로그인한 사용자를 작성자로 메모를 저장한다. 작성 시각은 저장 시 자동으로 기록된다. */
    @Transactional
    public StudentMemoResponse createMemo(
            Long userId, Long academyId, Long studentId, StudentMemoCreateRequest request) {
        Student student = findStudent(academyId, studentId);
        User author = userRepository
                .findByIdAndAcademyId(userId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        StudentMemo memo = studentMemoRepository.save(StudentMemo.builder()
                .student(student)
                .author(author)
                .content(request.content())
                .build());

        log.info("[student] 학생 메모 등록 완료: memoId={}, studentId={}, authorId={}", memo.getId(), studentId, userId);
        return StudentMemoResponse.from(memo);
    }

    private Student findStudent(Long academyId, Long studentId) {
        return studentRepository
                .findByIdAndAcademyId(studentId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
    }
}
