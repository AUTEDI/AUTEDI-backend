package com.ceos24.autedi.domain.student.service;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.service.AcademySettingsService;
import com.ceos24.autedi.domain.student.dto.request.StudentCreateRequest;
import com.ceos24.autedi.domain.student.dto.request.StudentSearchRequest;
import com.ceos24.autedi.domain.student.dto.request.StudentUpdateRequest;
import com.ceos24.autedi.domain.student.dto.response.StudentDuplicateCandidateResponse;
import com.ceos24.autedi.domain.student.dto.response.StudentDuplicateResponse;
import com.ceos24.autedi.domain.student.dto.response.StudentListResponse;
import com.ceos24.autedi.domain.student.dto.response.StudentResponse;
import com.ceos24.autedi.domain.student.entity.Student;
import com.ceos24.autedi.domain.student.exception.DuplicateStudentException;
import com.ceos24.autedi.domain.student.repository.StudentRepository;
import com.ceos24.autedi.global.exception.CustomException;
import com.ceos24.autedi.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentService {

    private final StudentRepository studentRepository;
    private final AcademySettingsService settings;
    private final EntityManager entityManager;

    @Transactional
    public StudentResponse createStudent(Long academyId, StudentCreateRequest request) {
        // 후보가 아직 없는 동시 등록도 순서대로 확인하도록 소속 기관 행을 잠근다.
        Academy academy = settings.prepare(academyId);
        var grade = settings.requireGrade(academyId, request.gradeId());
        if (!grade.isEnabled()) throw new CustomException(ErrorCode.ACADEMY_GRADE_DISABLED);
        var schoolClass = request.classId() == null ? null : settings.requireClass(academyId, request.classId());

        String name = request.name().strip();
        var candidates = studentRepository.findByAcademyIdAndNameOrderByIdAsc(academyId, name);
        if (!candidates.isEmpty() && !request.confirmDuplicate()) {
            var detail = new StudentDuplicateResponse(candidates.stream()
                    .map(student -> StudentDuplicateCandidateResponse.from(
                            student, request.studentPhone(), request.guardianPhone()))
                    .toList());
            throw new DuplicateStudentException(detail);
        }

        Student student = Student.builder()
                .academy(academy)
                .name(name)
                .grade(grade.getName())
                .className(schoolClass == null ? null : schoolClass.getName())
                .studentPhone(request.studentPhone())
                .guardianPhone(request.guardianPhone())
                .build();
        student.select(grade, schoolClass);

        return StudentResponse.from(studentRepository.save(student));
    }

    public StudentResponse getStudent(Long academyId, Long studentId) {
        Student student = studentRepository
                .findByIdAndAcademyId(studentId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
        return StudentResponse.from(student);
    }

    public StudentListResponse getStudents(Long academyId, StudentSearchRequest request) {
        if (request.unassigned() && (request.className() != null || request.classId() != null)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "반 필터와 미배정 필터는 함께 사용할 수 없습니다.");
        }
        if ((request.grade() != null && request.gradeId() != null)
                || (request.className() != null && request.classId() != null))
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "같은 항목의 이름과 ID 필터는 함께 사용할 수 없습니다.");
        if ((long) request.page() * request.size() > Integer.MAX_VALUE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "조회 가능한 페이지 범위를 초과했습니다.");
        }
        String keyword = request.keyword() == null
                ? null
                : request.keyword().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        var pageable = PageRequest.of(request.page(), request.size(), Sort.by("name", "id"));
        return StudentListResponse.from(studentRepository.findPageByFilters(
                academyId,
                keyword,
                request.grade(),
                request.className(),
                request.gradeId(),
                request.classId(),
                request.unassigned(),
                pageable));
    }

    @Transactional
    public StudentResponse updateStudent(Long academyId, Long studentId, StudentUpdateRequest request) {
        // 반 삭제/학년 사용 해제와 학생 배정은 모두 학원 → 학생 순서로 잠근다.
        settings.prepare(academyId);
        Student student = studentRepository
                .findLockedByIdAndAcademyId(studentId, academyId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
        // 최초 이관의 벌크 UPDATE를 같은 컨텍스트에 이미 있던 학생에도 반영한다.
        entityManager.refresh(student);
        var grade = request.isGradeIdProvided()
                ? settings.requireGrade(academyId, request.getGradeId())
                : student.getGradeOption();
        if (request.isGradeIdProvided() && !grade.isEnabled() && !grade.getId().equals(student.getGradeId()))
            throw new CustomException(ErrorCode.ACADEMY_GRADE_DISABLED);
        var schoolClass = request.isClassIdProvided()
                ? (request.getClassId() == null ? null : settings.requireClass(academyId, request.getClassId()))
                : student.getSchoolClass();
        student.updateBasicInfo(
                grade == null ? student.getGrade() : grade.getName(),
                schoolClass == null ? null : schoolClass.getName(),
                request.isStudentPhoneProvided() ? request.getStudentPhone() : student.getStudentPhone(),
                request.isGuardianPhoneProvided() ? request.getGuardianPhone() : student.getGuardianPhone());
        if (grade != null) student.select(grade, schoolClass);
        return StudentResponse.from(student);
    }
}
